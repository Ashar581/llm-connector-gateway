// ── App-wide constants ──────────────────────────────────────────────────

/**
 * The router's basename (see routers/routes.jsx). React Router's own APIs
 * (<Link>, <NavLink>, <Navigate>, useNavigate()) already account for this
 * automatically — paths used with those should NOT include it.
 *
 * This constant only matters for the handful of places that navigate via
 * raw `window.location` *outside* the React Router tree (e.g. apiService's
 * 401 redirect-to-login, which can fire from a plain axios interceptor
 * with no component context) — those need it prepended manually, or they
 * end up at the wrong URL entirely.
 */
export const APP_BASENAME = "/ui";
