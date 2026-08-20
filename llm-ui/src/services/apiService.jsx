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
const AuthBaseUrl = 'http://localhost:6969/'; // Replace with your actual auth server URL
const apiSvc = axios.create({
    baseURL:
        localStorage.getItem("BASE_URL") ||
        import.meta.env.VITE_BASE_URL,
    headers: {
        "ngrok-skip-browser-warning": "true",
    },
});

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
                    `${AuthBaseUrl}api/llm/v1/users/auth/refresh-token`,
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