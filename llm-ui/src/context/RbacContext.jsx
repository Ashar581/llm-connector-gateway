import { createContext, useCallback, useContext, useEffect, useRef, useState } from "react";
import toast from "react-hot-toast";
import {
    getRouteAccessConfig,
    subscribeToRouteAccessChanges,
    ROUTE_ACCESS_POLL_INTERVAL_MS,
} from "../services/rbacService";

const RbacContext = createContext(undefined);

/**
 * RbacProvider
 * ─────────────
 * Loads the route-access config and keeps it "live" across three channels,
 * so a change an admin makes shows up for everyone else without a manual
 * refresh or re-login:
 *
 *  1. Initial load on mount.
 *  2. Same-browser cross-tab — instant, via the native `storage` event
 *     (works today because the config is localStorage-backed).
 *  3. Cross-device / cross-user — a periodic poll of the GET endpoint,
 *     plus an immediate re-check whenever the tab regains focus. This is
 *     the practical way to get "live" behaviour out of a plain REST GET
 *     without standing up WebSocket/SSE infrastructure. Once a push
 *     channel exists, swap `subscribeToRouteAccessChanges` in
 *     rbacService.jsx and this provider keeps working unchanged.
 *
 * Background refreshes are silent (no loading flicker) and only touch
 * state when the config actually changed, and — once past the first
 * load — surface a small toast so people understand why a nav item just
 * appeared or disappeared.
 */
export function RbacProvider({ children }) {
    const [config, setConfig] = useState({});
    const [loading, setLoading] = useState(true);
    const [lastSyncedAt, setLastSyncedAt] = useState(null);
    const hasLoadedOnce = useRef(false);

    const refresh = useCallback(async ({ silent = false, notifyOnChange = false } = {}) => {
        if (!silent) setLoading(true);
        try {
            const cfg = (await getRouteAccessConfig()) ?? {};
            setConfig((prev) => {
                const changed = JSON.stringify(prev) !== JSON.stringify(cfg);
                if (changed && notifyOnChange && hasLoadedOnce.current) {
                    toast("Route access rules were just updated by an administrator.", { icon: "🔐" });
                }
                return changed ? cfg : prev;
            });
            hasLoadedOnce.current = true;
            setLastSyncedAt(Date.now());
        } catch (e) {
            console.error("Failed to load route access config", e);
            if (!silent) setConfig({}); // fail open on the initial load only
        } finally {
            if (!silent) setLoading(false);
        }
    }, []);

    // 1. Initial load.
    useEffect(() => { refresh(); }, [refresh]);

    // 2. Same-browser cross-tab — instant.
    useEffect(
        () => subscribeToRouteAccessChanges(() => refresh({ silent: true, notifyOnChange: true })),
        [refresh]
    );

    // 3. Cross-device / cross-user — periodic poll + refresh-on-focus.
    useEffect(() => {
        const interval = setInterval(
            () => refresh({ silent: true, notifyOnChange: true }),
            ROUTE_ACCESS_POLL_INTERVAL_MS
        );
        const onVisible = () => {
            if (document.visibilityState === "visible") {
                refresh({ silent: true, notifyOnChange: true });
            }
        };
        document.addEventListener("visibilitychange", onVisible);
        return () => {
            clearInterval(interval);
            document.removeEventListener("visibilitychange", onVisible);
        };
    }, [refresh]);

    return (
        <RbacContext.Provider value={{ config, loading, refresh, lastSyncedAt }}>
            {children}
        </RbacContext.Provider>
    );
}

export function useRbac() {
    const ctx = useContext(RbacContext);
    if (!ctx) throw new Error("useRbac must be used within an RbacProvider");
    return ctx;
}
