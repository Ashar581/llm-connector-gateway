import { createContext, useCallback, useContext, useEffect, useState } from "react";
import { getRouteAccessConfig } from "../services/rbacService";

const RbacContext = createContext(undefined);

/**
 * RbacProvider
 * ─────────────
 * Loads the route-access config once (currently from localStorage — see
 * rbacService's TODO(API) notes) and exposes it app-wide so both the nav
 * (App.jsx) and the route guards (RbacRoute) agree on the same rules
 * without each re-reading storage independently. Call `refresh()` after
 * the admin saves changes so both update immediately.
 */
export function RbacProvider({ children }) {
    const [config, setConfig] = useState({});
    const [loading, setLoading] = useState(true);

    const refresh = useCallback(async () => {
        setLoading(true);
        try {
            const cfg = await getRouteAccessConfig();
            setConfig(cfg ?? {});
        } catch (e) {
            console.error("Failed to load route access config", e);
            setConfig({}); // fail open — see isRouteOpenToRoles
        } finally {
            setLoading(false);
        }
    }, []);

    useEffect(() => { refresh(); }, [refresh]);

    return (
        <RbacContext.Provider value={{ config, loading, refresh }}>
            {children}
        </RbacContext.Provider>
    );
}

export function useRbac() {
    const ctx = useContext(RbacContext);
    if (!ctx) throw new Error("useRbac must be used within an RbacProvider");
    return ctx;
}
