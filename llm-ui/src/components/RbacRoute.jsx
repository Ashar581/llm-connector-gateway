import { useAuth } from "../context/AuthContext";
import { useRbac } from "../context/RbacContext";
import { isRouteOpenToRoles, MANAGEABLE_ROUTES } from "../services/rbacService";
import AccessRestricted from "./AccessRestricted";

/**
 * RbacRoute
 * ──────────
 * Gates a top-level page (Dashboard / Agents / Stats / Playground) behind
 * the admin-configured, per-route role list from RbacContext. Renders
 * inline instead of redirecting, so multiple gated routes can never bounce
 * a user in a loop.
 *
 * SUPER_ADMIN / SYSTEM_ADMIN always pass — this dynamic config never
 * touches the hardcoded admin-level access already enforced by AdminRoute.
 * A route with no rule configured (or an empty one) is public to any
 * signed-in user, per the default-allow requirement.
 */
export default function RbacRoute({ routeKey, children }) {
    const { user, isAdmin } = useAuth();
    const { config, loading } = useRbac();

    if (isAdmin) return children;

    if (loading) {
        return (
            <div className="flex items-center justify-center py-24">
                <span className="text-xs" style={{ color: "var(--text-faint)" }}>Checking access…</span>
            </div>
        );
    }

    const userRoleCodes = Array.from(user?.roles ?? []);
    const allowed = isRouteOpenToRoles(config, routeKey, userRoleCodes);

    if (!allowed) {
        const label = MANAGEABLE_ROUTES.find((r) => r.key === routeKey)?.label;
        return <AccessRestricted routeLabel={label} />;
    }

    return children;
}
