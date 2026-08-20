// ── Route-level RBAC config ─────────────────────────────────────────────
// The backend endpoint for this hasn't shipped yet, so this reads/writes
// localStorage — same pattern Settings.jsx already uses for BASE_URL.
//
// Every function here is async and shaped like a future API call on
// purpose (getConfig ≈ GET, saveConfig ≈ PUT) so that once the DB-backed
// endpoint exists, only the bodies of getRouteAccessConfig /
// saveRouteAccessConfig need to change — every call site already awaits
// them and treats the config as opaque JSON.
//
// Config shape: { [routeKey]: string[] roleCodes }
// An absent key, or an empty array, means the route is public — open to
// any signed-in user. SUPER_ADMIN / SYSTEM_ADMIN always bypass this
// entirely (enforced by the caller, not stored here).

const STORAGE_KEY = "llm_route_access_config";

// Keys must match the `routeKey` passed to <RbacRoute> in routers/routes.jsx
// and the `key` on each nav link in App.jsx. Keep this list in sync if a
// new top-level, RBAC-manageable route is added.
export const MANAGEABLE_ROUTES = [
    { key: "dashboard", label: "Dashboard", path: "/" },
    { key: "agents", label: "Agents", path: "/agents" },
    { key: "stats", label: "Stats", path: "/stats" },
    { key: "playground", label: "Playground", path: "/playground" },
];

function readLocalConfig() {
    try {
        const raw = localStorage.getItem(STORAGE_KEY);
        return raw ? JSON.parse(raw) : {};
    } catch (e) {
        console.error("Failed to parse stored route access config", e);
        return {};
    }
}

// TODO(API): once the backend endpoint ships, replace the body with:
//   const response = await apiSvc.get("v1/route-access");
//   return response.data.data ?? {};
export async function getRouteAccessConfig() {
    return readLocalConfig();
}

// TODO(API): once the backend endpoint ships, replace the body with:
//   const response = await apiSvc.put("v1/route-access", config);
//   return response.data.data ?? config;
export async function saveRouteAccessConfig(config) {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(config ?? {}));
    return config;
}

/**
 * Whether `userRoleCodes` satisfies the access rule set for `routeKey`.
 * No rule (or an empty rule) means the route is public.
 */
export function isRouteOpenToRoles(config, routeKey, userRoleCodes = []) {
    const allowed = config?.[routeKey];
    if (!allowed || allowed.length === 0) return true;
    const upper = new Set(Array.from(userRoleCodes, (r) => String(r).toUpperCase()));
    return allowed.some((r) => upper.has(String(r).toUpperCase()));
}
