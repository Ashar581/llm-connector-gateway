import axios from "axios";
import NProgress from "nprogress";
import { TOKEN_KEY, REFRESH_TOKEN_KEY } from "../context/AuthContext";

// ── NProgress config ────────────────────────────────────────────────────────
NProgress.configure({
    showSpinner: false,
    minimum: 0.15,
    speed: 300,
    trickleSpeed: 200,
});

// ── Base URL resolution ──────────────────────────────────────────────────
// This same build has to work in two very different deployments:
//
//   1. Standalone — the UI is hosted on its own (e.g. this repo's nginx
//      config) and talks to a Java backend on a *different* host/port.
//      There's nothing at this origin to detect, so this always needs an
//      explicit base URL: either VITE_BASE_URL baked in at build time, or
//      a manual override saved from Settings.
//
//   2. Embedded — the built dist is copied into the Java app's
//      src/main/resources/static/ui and served BY that same app. Here the
//      API lives at this exact browser origin (under /api/llm) no matter
//      which host/port Java happens to be deployed on, so hardcoding
//      anything (like the old "http://localhost:6969") breaks the moment
//      it's deployed anywhere else.
//
// Priority, resolved once at startup (see bootstrapApiBaseUrl in main.jsx):
//   1. localStorage "BASE_URL" — an explicit override from Settings. Always
//      wins; no detection needed once someone has set this.
//   2. Same-origin auto-detection — probe `${location.origin}/api/llm` and
//      see whether a real API answers there (case 2) or whether we just
//      get the SPA's own index.html back (case 1 — that's what a static
//      file server does with try_files for any unmapped path, which is
//      exactly what this repo's nginx.conf does).
//   3. VITE_BASE_URL — the build-time default, for local dev and as the
//      standalone fallback when nothing is detected.
const BASE_URL_STORAGE_KEY = "BASE_URL";

function getStoredBaseUrl() {
    return localStorage.getItem(BASE_URL_STORAGE_KEY);
}

function getDefaultBaseUrl() {
    return import.meta.env.VITE_BASE_URL;
}

async function detectSameOriginApiBase() {
    // Vite's dev server never doubles as the Java API host, so there's
    // nothing to detect in local dev — skip the extra round trip.
    if (import.meta.env.DEV) return null;
    try {
        const candidate = `${window.location.origin}/api/llm`;
        const res = await fetch(`${candidate}/v1/settings/all`, {
            method: "GET",
            headers: { Accept: "application/json" },
        });
        const contentType = res.headers.get("content-type") || "";
        // A static file server's SPA fallback (try_files ... /index.html)
        // always comes back as text/html regardless of status code. Any
        // real backend response — 200 JSON, or a 401/403 from Spring
        // Security — won't be HTML. That's the one reliable signal here.
        return contentType.includes("text/html") ? null : candidate;
    } catch {
        return null; // network error / connection refused → nothing here
    }
}

const apiSvc = axios.create({
    baseURL: getStoredBaseUrl() || getDefaultBaseUrl(),
    headers: {
        "ngrok-skip-browser-warning": "true",
    },
});

let resolvedSource = getStoredBaseUrl() ? "override" : "default";

/**
 * Runs the resolution chain above and — if there's no explicit override
 * and a same-origin API is detected — repoints apiSvc at it. Call this
 * once, as early as possible (see main.jsx), before the app renders.
 */
export async function bootstrapApiBaseUrl() {
    if (getStoredBaseUrl()) {
        resolvedSource = "override";
        return apiSvc.defaults.baseURL;
    }
    const detected = await detectSameOriginApiBase();
    if (detected) {
        apiSvc.defaults.baseURL = detected;
        resolvedSource = "auto-detected";
    } else {
        resolvedSource = "default";
    }
    return apiSvc.defaults.baseURL;
}

/** For Settings — shows exactly where API calls are currently going and why. */
export function getApiBaseUrlInfo() {
    return { url: apiSvc.defaults.baseURL, source: resolvedSource };
}

/**
 * The bare host root (no /api/llm suffix), for the couple of auth endpoints
 * that live outside the versioned API prefix. Always derived from apiSvc's
 * currently-resolved base, so it follows the exact same
 * override/auto-detect/default chain — nothing hardcoded.
 */
export function getApiRoot() {
    return apiSvc.defaults.baseURL.replace(/\/?api\/llm\/?$/, "");
}

// ── Progress bar tracking ────────────────────────────────────────────────────
let activeRequests = 0;

const onDone = () => {
    activeRequests = Math.max(0, activeRequests - 1);
    if (activeRequests === 0) NProgress.done();
};

// ── REQUEST interceptor — attach Bearer token ────────────────────────────────
apiSvc.interceptors.request.use((config) => {
    activeRequests++;
    NProgress.start();

    const token = localStorage.getItem(TOKEN_KEY);
    if (token) {
        config.headers["Authorization"] = `Bearer ${token}`;
    }

    return config;
});

// ── Token-refresh state (prevents multiple simultaneous refresh calls) ────────
let isRefreshing = false;
let refreshSubscribers = []; // callbacks waiting for the new token

const subscribeTokenRefresh = (cb) => refreshSubscribers.push(cb);

const onTokenRefreshed = (newToken) => {
    refreshSubscribers.forEach((cb) => cb(newToken));
    refreshSubscribers = [];
};

// ── RESPONSE interceptor — handle success and 401 ────────────────────────────
apiSvc.interceptors.response.use(
    (response) => {
        onDone();
        return response;
    },
    async (error) => {
        onDone();

        const originalRequest = error.config;

        // ── 401 Unauthorized handling ──────────────────────────────────────────
        if (error.response?.status === 401 && !originalRequest._retry) {
            const refreshToken = localStorage.getItem(REFRESH_TOKEN_KEY);

            // If we have no refresh token, bounce straight to login.
            if (!refreshToken) {
                redirectToLogin();
                return Promise.reject(error);
            }

            if (isRefreshing) {
                // Another request already kicked off a refresh — queue this one.
                return new Promise((resolve) => {
                    subscribeTokenRefresh((newToken) => {
                        originalRequest.headers["Authorization"] = `Bearer ${newToken}`;
                        resolve(apiSvc(originalRequest));
                    });
                });
            }

            originalRequest._retry = true;
            isRefreshing = true;

            try {
                const { data } = await axios.post(
                    `${getApiRoot()}/api/llm/v1/users/auth/refresh-token`,
                    { token: refreshToken }
                );

                const newAccessToken = data.data.token;
                localStorage.setItem(TOKEN_KEY, newAccessToken);

                if (data.data.refreshToken) {
                    localStorage.setItem(REFRESH_TOKEN_KEY, data.data.refreshToken);
                }

                apiSvc.defaults.headers["Authorization"] = `Bearer ${newAccessToken}`;
                onTokenRefreshed(newAccessToken);

                originalRequest.headers["Authorization"] = `Bearer ${newAccessToken}`;
                return apiSvc(originalRequest);
            } catch (refreshError) {
                // Refresh itself failed — clear session and go to login.
                redirectToLogin();
                return Promise.reject(refreshError);
            } finally {
                isRefreshing = false;
            }
        }

        return Promise.reject(error);
    }
);

/**
 * Clears all auth data from storage and navigates to /login.
 * Uses window.location so this works outside React component trees.
 */
function redirectToLogin() {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(REFRESH_TOKEN_KEY);
    localStorage.removeItem("auth_user");

    if (window.location.pathname !== "/login") {
        window.location.href = "/login";
    }
}

export default apiSvc;
