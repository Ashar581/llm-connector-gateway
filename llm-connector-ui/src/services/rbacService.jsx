// ── Route-level RBAC config ─────────────────────────────────────────────
// Backed by the real /v1/settings endpoints (see settingsService.jsx).
//
// Two sources of truth, reconciled on load:
//   - The frontend's code (MANAGEABLE_ROUTES) knows which pages actually
//     exist right now.
//   - The backend (SettingsEntity rows) knows which roles can access each
//     one — that's admin-configured and must survive across deploys.
//
// reconcileRouteSettings() below keeps the backend's rows in step with
// whatever pages the frontend currently ships, per three rules:
//   1. A route in code but missing in the backend  → bulk-create it
//      (roles: [] = public by default, same as the "unset = public" rule
//      everywhere else in this system).
//   2. A row in the backend with no matching route in code  → delete it
//      (the page was removed from the app).
//   3. A route in both, but the code's label differs from the backend's
//      → update just the label. Roles are left untouched — those are an
//      admin decision, not something a code change should ever overwrite.

import {
    getAllSettings,
    addSettingsBulk,
    updateSetting,
    deleteSetting,
} from "./settingsService";

// How often RbacContext polls the backend for changes made by other users.
// This is the practical way to get "live" updates out of a plain REST GET
// endpoint without needing WebSocket/SSE infrastructure.
export const ROUTE_ACCESS_POLL_INTERVAL_MS = 30000;

// Every top-level, RBAC-manageable route in the app. `path` doubles as the
// backend's unique `routePath` key, so add a route here and it appears in
// the backend (public by default) after the next reconciliation; delete
// one here and its backend row gets cleaned up the same way.
export const MANAGEABLE_ROUTES = [
    { path: "/", label: "Dashboard" },
    { path: "/agents", label: "Agents" },
    { path: "/stats", label: "Stats" },
    { path: "/playground", label: "Playground" },
];

/**
 * Config shape consumed by the rest of the app: { [routePath]: string[] roleCodes }
 * An absent key, or an empty array, means the route is public.
 */
export async function getRouteAccessConfig() {
    const settings = await getAllSettings();
    const config = {};
    (settings ?? []).forEach((s) => {
        config[s.routePath] = Array.from(s.roles ?? []);
    });
    return config;
}

export function isRouteOpenToRoles(config, routePath, userRoleCodes = []) {
    const allowed = config?.[routePath];
    if (!allowed || allowed.length === 0) return true;
    const upper = new Set(Array.from(userRoleCodes, (r) => String(r).toUpperCase()));
    return allowed.some((r) => upper.has(String(r).toUpperCase()));
}

/**
 * Persists role selections from the Access panel. By the time this runs,
 * reconciliation has already guaranteed every MANAGEABLE_ROUTES entry
 * exists as a backend row, so this is always an update, never a create.
 */
export async function saveRouteAccessConfig(config) {
    await Promise.all(
        MANAGEABLE_ROUTES.map((route) =>
            updateSetting({
                routePath: route.path,
                label: route.label,
                roles: Array.from(config?.[route.path] ?? []),
            })
        )
    );
    return config;
}

/**
 * Scans MANAGEABLE_ROUTES against the backend's current rows and reconciles
 * the three cases described above. Intended to run once per session, for
 * admins only (creating/deleting settings rows is an admin-level action —
 * see RbacProvider). Returns the backend's post-reconciliation list.
 */
export async function reconcileRouteSettings() {
    const backendSettings = await getAllSettings();
    const backendByPath = new Map((backendSettings ?? []).map((s) => [s.routePath, s]));
    const codePaths = new Set(MANAGEABLE_ROUTES.map((r) => r.path));

    const toCreate = MANAGEABLE_ROUTES
        .filter((r) => !backendByPath.has(r.path))
        .map((r) => ({ label: r.label, routePath: r.path, roles: [] }));

    const toDelete = (backendSettings ?? []).filter((s) => !codePaths.has(s.routePath));

    const toRelabel = MANAGEABLE_ROUTES.filter((r) => {
        const existing = backendByPath.get(r.path);
        return existing && existing.label !== r.label;
    });

    let changed = false;

    if (toCreate.length > 0) {
        await addSettingsBulk(toCreate);
        changed = true;
    }
    if (toDelete.length > 0) {
        await Promise.all(toDelete.map((s) => deleteSetting(s.routePath)));
        changed = true;
    }
    if (toRelabel.length > 0) {
        // Deliberately omit `roles` here — see the module doc above.
        await Promise.all(toRelabel.map((r) => updateSetting({ routePath: r.path, label: r.label })));
        changed = true;
    }

    return changed ? getAllSettings() : backendSettings;
}
