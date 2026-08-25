import axios from "axios";
import { getApiRoot } from "./apiService";

/**
 * authService
 * ───────────
 * Thin wrappers around the authentication endpoints.
 *
 * Login/logout deliberately use bare `axios` (not apiSvc) — a wrong
 * password legitimately returns 401, and apiSvc's response interceptor
 * would otherwise mistake that for an expired-token situation and try to
 * silently refresh instead of surfacing "invalid credentials" to the form.
 *
 * They still resolve against the exact same base URL as everything else
 * though (override → same-origin auto-detect → VITE_BASE_URL — see
 * apiService.jsx), via getApiRoot(). Nothing here is hardcoded to a fixed
 * host, so this works whether the UI is hosted standalone or embedded in
 * Java's static resources.
 */

/**
 * POST /api/llm/v1/users/auth/login
 * Expected request:  { userid, password }
 * Expected response: { access_token, refresh_token?, user: { id, email, name, ... } }
 */
export const loginRequest = (email, password) =>
    axios.post(`${getApiRoot()}/api/llm/v1/users/auth/login`, { userid: email, password });

/**
 * POST /auth/logout  (optional — call if your backend invalidates tokens)
 */
export const logoutRequest = () =>
    axios.post(`${getApiRoot()}/auth/logout`).catch(() => {
        // Swallow errors — local session is cleared regardless.
    });

/**
 * GET /auth/me  — fetch the currently authenticated user profile.
 * Useful for rehydrating user metadata after a page refresh
 * if you don't want to rely on localStorage alone.
 */
// export const getMeRequest = () => apiSvc.get("/auth/me");
