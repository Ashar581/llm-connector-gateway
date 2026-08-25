import { Navigate } from "react-router";
import { useAuth } from "../context/AuthContext";

/**
 * AdminRoute
 * ──────────
 * Wraps the /admin routes. Assumes the surrounding ProtectedRoute has
 * already confirmed the user is authenticated — this only adds the
 * SUPER_ADMIN / SYSTEM_ADMIN role check on top, bouncing anyone else
 * back to the dashboard.
 */
export default function AdminRoute({ children }) {
    const { isAdmin } = useAuth();

    if (!isAdmin) {
        return <Navigate to="/" replace />;
    }

    return children;
}
