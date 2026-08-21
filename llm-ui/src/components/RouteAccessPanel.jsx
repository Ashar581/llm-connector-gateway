import { useEffect, useMemo, useState } from "react";
import toast from "react-hot-toast";
import Card from "./Card";
import Button from "./Button";
import MultiSelectChips from "./MultiSelectChips";
import { useRbac } from "../context/RbacContext";
import { MANAGEABLE_ROUTES, saveRouteAccessConfig } from "../services/rbacService";

export default function RouteAccessPanel({ allRoles = [] }) {
    const { config, loading, refresh, lastSyncedAt } = useRbac();
    const [draft, setDraft] = useState({});
    const [saving, setSaving] = useState(false);
    const [dirty, setDirty] = useState(false);
    const [syncedLabel, setSyncedLabel] = useState("");

    useEffect(() => {
        // Skip re-syncing from `config` while the admin has unsaved edits in
        // progress — otherwise a background poll picking up someone else's
        // change would silently wipe out what they're mid-way through typing.
        if (!loading && !dirty) {
            setDraft(config ?? {});
        }
    }, [config, loading, dirty]);

    // Small "synced Ns ago" ticker, refreshed independently of the poll
    // itself so the label stays current between polls.
    useEffect(() => {
        const formatAgo = () => {
            if (!lastSyncedAt) return "";
            const secs = Math.max(0, Math.round((Date.now() - lastSyncedAt) / 1000));
            if (secs < 5) return "Synced just now";
            if (secs < 60) return `Synced ${secs}s ago`;
            return `Synced ${Math.round(secs / 60)}m ago`;
        };
        setSyncedLabel(formatAgo());
        const tick = setInterval(() => setSyncedLabel(formatAgo()), 5000);
        return () => clearInterval(tick);
    }, [lastSyncedAt]);

    const roleOptions = useMemo(
        () => allRoles.map((r) => ({ value: r.code, label: r.name, sublabel: r.code })),
        [allRoles]
    );

    const setRouteRoles = (routePath, codes) => {
        setDraft((prev) => ({ ...prev, [routePath]: codes }));
        setDirty(true);
    };

    const handleManualRefresh = async () => {
        await refresh();
        setDirty(false); // explicit refresh — pull the latest and drop any unsaved local edits
    };

    const handleSave = async () => {
        setSaving(true);
        try {
            await saveRouteAccessConfig(draft);
            await refresh();
            setDirty(false);
            toast.success("Access rules saved");
        } catch (e) {
            console.error("Failed to save route access config", e);
            toast.error("Could not save access rules.");
        } finally {
            setSaving(false);
        }
    };

    return (
        <Card>
            <div className="flex items-start justify-between gap-4 flex-wrap mb-2">
                <div>
                    <div className="text-xs uppercase tracking-widest" style={{ color: "var(--text-muted)" }}>
                        Route Access Control
                    </div>
                    <p className="text-[11px] mt-1.5 max-w-lg" style={{ color: "var(--text-faint)" }}>
                        Choose which roles can open each section below. Leave a route with no roles selected to
                        keep it open to any signed-in user. SUPER_ADMIN and SYSTEM_ADMIN always have full access
                        regardless of these settings.
                    </p>
                </div>
                <div className="flex flex-col items-end gap-2">
                    <span
                        className="flex items-center gap-1.5 text-[10px] px-2 py-1 rounded-full uppercase tracking-wider whitespace-nowrap"
                        style={{ backgroundColor: "rgba(52,211,153,0.1)", border: "1px solid rgba(52,211,153,0.3)", color: "rgb(52,211,153)" }}
                        title="Pages are auto-discovered from the app and kept in sync with the backend on load."
                    >
                        <span className="w-1.5 h-1.5 rounded-full bg-emerald-400 shadow-[0_0_6px_rgba(52,211,153,0.7)]" />
                        Synced with server
                    </span>
                    <div className="flex items-center gap-2">
                        {syncedLabel && (
                            <span className="text-[10px]" style={{ color: "var(--text-faint)" }}>
                                {syncedLabel}
                            </span>
                        )}
                        <button
                            type="button"
                            onClick={handleManualRefresh}
                            disabled={loading}
                            title="Check for updates now"
                            className="text-[10px] uppercase tracking-wider px-2 py-1 rounded transition-colors hover:text-amber-500 disabled:opacity-50"
                            style={{ border: "1px solid var(--border)", color: "var(--text-muted)" }}
                        >
                            ↻ Refresh
                        </button>
                    </div>
                </div>
            </div>

            {dirty && (
                <p className="text-[11px] mb-2" style={{ color: "var(--text-faint)" }}>
                    Live updates from other admins are paused while you have unsaved changes — save or refresh to resync.
                </p>
            )}

            {loading ? (
                <div className="space-y-3 py-6">
                    {[...Array(4)].map((_, i) => (
                        <div key={i} className="h-16 rounded animate-pulse" style={{ backgroundColor: "var(--bg-subtle)" }} />
                    ))}
                </div>
            ) : (
                <div className="space-y-4 mt-5">
                    {MANAGEABLE_ROUTES.map((route) => {
                        const selected = draft[route.path] ?? [];
                        const isPublic = selected.length === 0;
                        return (
                            <div
                                key={route.path}
                                className="rounded-lg p-4"
                                style={{ backgroundColor: "var(--bg-subtle)", border: "1px solid var(--border)" }}
                            >
                                <div className="flex items-center justify-between mb-3 gap-3">
                                    <div>
                                        <div className="text-sm font-bold" style={{ color: "var(--text-primary)" }}>{route.label}</div>
                                        <div className="text-[11px] font-mono" style={{ color: "var(--text-faint)" }}>{route.path}</div>
                                    </div>
                                    <span
                                        className="text-[10px] px-2 py-0.5 rounded-full uppercase tracking-wider flex-shrink-0"
                                        style={isPublic
                                            ? { backgroundColor: "rgba(52,211,153,0.1)", border: "1px solid rgba(52,211,153,0.3)", color: "rgb(52,211,153)" }
                                            : { backgroundColor: "rgba(167,139,250,0.1)", border: "1px solid rgba(167,139,250,0.3)", color: "rgb(167,139,250)" }
                                        }
                                    >
                                        {isPublic ? "Public" : "Restricted"}
                                    </span>
                                </div>
                                <MultiSelectChips
                                    options={roleOptions}
                                    selected={selected}
                                    onChange={(codes) => setRouteRoles(route.path, codes)}
                                    placeholder="Search roles…"
                                    emptyMessage="No roles created yet — create one in the Roles tab first."
                                    accent="rgb(167,139,250)"
                                    accentBg="rgba(167,139,250,0.12)"
                                    accentBorder="rgba(167,139,250,0.4)"
                                />
                            </div>
                        );
                    })}
                </div>
            )}

            <div className="flex items-center justify-end gap-3 mt-6 pt-5" style={{ borderTop: "1px solid var(--border)" }}>
                {dirty && <span className="text-[11px]" style={{ color: "var(--text-faint)" }}>Unsaved changes</span>}
                <Button variant="primary" onClick={handleSave} disabled={saving || loading}>
                    {saving ? "Saving..." : "Save Changes"}
                </Button>
            </div>
        </Card>
    );
}
