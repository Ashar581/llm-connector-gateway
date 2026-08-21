import { createContext, useCallback, useContext, useEffect, useRef, useState } from "react";
import toast from "react-hot-toast";
import { useAuth } from "./AuthContext";
import {
    getRouteAccessConfig,
    reconcileRouteSettings,
    ROUTE_ACCESS_POLL_INTERVAL_MS,
} from "../services/rbacService";

const RbacContext = createContext(undefined);

/**
 * RbacProvider
 * ─────────────
 * Loads the route-access config from the backend and keeps it "live":
 *
 *  1. On mount, if the signed-in user is an admin, reconcile the
 *     frontend's known routes (MANAGEABLE_ROUTES) against the backend
 *     first — create rows for new pages, delete rows for removed ones,
 *     and sync any label drift — so the config that loads right after is
 *     already accurate. Non-admins skip this (creating/deleting settings
 *     rows is an admin-level action) and just read what's there.
 *  2. From then on, a periodic poll of the GET endpoint (plus an
 *     immediate re-check whenever the tab regains focus) picks up
 *     changes made by other users. This is the practical way to get
 *     "live" behaviour out of a plain REST GET without standing up
 *     WebSocket/SSE infrastructure — if a push channel is added later,
 *     this polling loop is the only thing that needs to change.
 *
 * Background refreshes are silent (no loading flicker) and only touch
 * state when the config actually changed, surfacing a small toast once
 * past the first load so people understand why a nav item just
 * appeared or disappeared.
 */
export function RbacProvider({ children }) {
    const { isAdmin } = useAuth();
    const [config, setConfig] = useState({});
    const [loading, setLoading] = useState(true);
    const [lastSyncedAt, setLastSyncedAt] = useState(null);
    const hasLoadedOnce = useRef(false);
    const hasReconciled = useRef(false);

    const load = useCallback(async ({ silent = false, notifyOnChange = false } = {}) => {
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

    const refresh = useCallback((opts) => load(opts), [load]);

    // Initial load — admins reconcile first.
    useEffect(() => {
        let active = true;
        (async () => {
            if (isAdmin && !hasReconciled.current) {
                hasReconciled.current = true;
                try {
                    await reconcileRouteSettings();
                } catch (e) {
                    console.error("Failed to reconcile route access settings", e);
                }
            }
            if (active) await load();
        })();
        return () => { active = false; };
    }, [isAdmin, load]);

    // Periodic poll + refresh-on-focus.
    useEffect(() => {
        const interval = setInterval(
            () => load({ silent: true, notifyOnChange: true }),
            ROUTE_ACCESS_POLL_INTERVAL_MS
        );
        const onVisible = () => {
            if (document.visibilityState === "visible") {
                load({ silent: true, notifyOnChange: true });
            }
        };
        document.addEventListener("visibilitychange", onVisible);
        return () => {
            clearInterval(interval);
            document.removeEventListener("visibilitychange", onVisible);
        };
    }, [load]);

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
