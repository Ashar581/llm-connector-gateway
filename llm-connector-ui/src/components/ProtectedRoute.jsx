import { Navigate, useLocation } from "react-router";
import { useAuth } from "../context/AuthContext";

/**
 * ProtectedRoute
 * ──────────────
 * Wraps any route that requires authentication.
 * Unauthenticated visitors are redirected to /login, and the
 * original destination is preserved in location state so the
 * login page can redirect back after a successful sign-in.
 *
 * Usage in routes.jsx:
 *   { path: "/", element: <ProtectedRoute><App /></ProtectedRoute> }
 */
export default function ProtectedRoute({ children }) {
    const { isAuthenticated } = useAuth();
    const location = useLocation();

    if (!isAuthenticated) {
        return <Navigate to="/login" state={{ from: location }} replace />;
    }

    return children;
}