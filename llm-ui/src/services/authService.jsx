import apiSvc from "./apiService";
import axios from "axios";

const authApi = axios.create({
    baseURL: "https://auth-dev.ds-iflow.com",
});

/**
 * authService
 * ───────────
 * Thin wrappers around the authentication endpoints.
 * All network calls go through apiSvc so NProgress and the
 * Authorization interceptor stay consistent.
 */

/**
 * POST /auth/login
 * Expected request:  { email, password }
 * Expected response: { access_token, refresh_token?, user: { id, email, name, ... } }
 */
export const loginRequest = (email, password) =>
    authApi.post("/api/signin", { userid: email, password: password });

/**
 * POST /auth/logout  (optional — call if your backend invalidates tokens)
 */
export const logoutRequest = () =>
    authApi.post("/auth/logout").catch(() => {
        // Swallow errors — local session is cleared regardless.
    });

/**
 * GET /auth/me  — fetch the currently authenticated user profile.
 * Useful for rehydrating user metadata after a page refresh
 * if you don't want to rely on localStorage alone.
 */
// export const getMeRequest = () => apiSvc.get("/auth/me");