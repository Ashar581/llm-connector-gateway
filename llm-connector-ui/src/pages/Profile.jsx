import { useEffect, useState } from "react";
import PageHeader from "../components/PageHeader";
import Card from "../components/Card";
import Badge from "../components/Badge";
import StatCard from "../components/StatCard";
import { useAuth } from "../context/AuthContext";
import { getUser } from "../services/userService";
import { getInitials, getAvatarGradient } from "../utils/avatarUtils";

function formatDate(value) {
    if (!value) return "—";
    try {
        return new Date(value).toLocaleString(undefined, {
            year: "numeric", month: "long", day: "2-digit", hour: "2-digit", minute: "2-digit",
        });
    } catch {
        return "—";
    }
}

function daysSince(value) {
    if (!value) return null;
    const then = new Date(value).getTime();
    if (Number.isNaN(then)) return null;
    return Math.max(0, Math.floor((Date.now() - then) / 86400000));
}

function ChipList({ items, accent, accentBg, accentBorder, emptyLabel = "None assigned" }) {
    if (!items || items.length === 0) {
        return <span className="text-xs" style={{ color: "var(--text-faint)" }}>{emptyLabel}</span>;
    }
    return (
        <div className="flex flex-wrap gap-1.5">
            {items.map((item, i) => (
                <span
                    key={i}
                    className="text-[10px] px-2.5 py-1 rounded-full uppercase tracking-wider font-bold"
                    style={{ backgroundColor: accentBg, border: `1px solid ${accentBorder}`, color: accent }}
                >
                    {item}
                </span>
            ))}
        </div>
    );
}

function InfoRow({ label, value }) {
    return (
        <div className="flex items-center justify-between py-2.5" style={{ borderBottom: "1px solid var(--border)" }}>
            <span className="text-[11px] uppercase tracking-wider" style={{ color: "var(--text-faint)" }}>{label}</span>
            <span className="text-xs font-medium text-right" style={{ color: "var(--text-secondary)" }}>{value}</span>
        </div>
    );
}

export default function Profile() {
    const { user } = useAuth();
    const [profile, setProfile] = useState(user);
    const [loading, setLoading] = useState(true);
    const [loaded, setLoaded] = useState(false);

    useEffect(() => { setTimeout(() => setLoaded(true), 80); }, []);

    useEffect(() => {
        let active = true;
        const identifier = user?.id ?? user?.email ?? user?.username;
        if (identifier == null) { setLoading(false); return; }
        setLoading(true);
        getUser(identifier)
            .then((fresh) => {
                if (active && fresh) setProfile({ ...fresh, id: fresh.id ?? user?.id ?? null });
            })
            .catch((e) => {
                console.error("Failed to load profile", e);
                // Keep showing whatever AuthContext already has cached.
            })
            .finally(() => { if (active) setLoading(false); });
        return () => { active = false; };
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, []);

    const displayName = profile?.fullname || `${profile?.firstName ?? ""} ${profile?.lastName ?? ""}`.trim() || profile?.username || "—";
    const gradient = getAvatarGradient(profile?.username ?? profile?.email ?? displayName);
    const roles = Array.from(profile?.roles ?? []);
    const groups = Array.from(profile?.groups ?? []);
    const accountAge = daysSince(profile?.createdAt);

    return (
        <>
            <div className={`transition-all duration-700 ${loaded ? "opacity-100 translate-y-0" : "opacity-0 translate-y-4"}`}>
                <PageHeader
                    tag="My Account"
                    title="Profile"
                    highlight="Overview"
                    description="A quick look at your identity, access, and account activity on the gateway."
                />
            </div>

            {loading ? (
                <div className="space-y-4">
                    <div className="h-40 rounded-lg animate-pulse" style={{ backgroundColor: "var(--bg-subtle)" }} />
                    <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
                        {[...Array(4)].map((_, i) => (
                            <div key={i} className="h-24 rounded-lg animate-pulse" style={{ backgroundColor: "var(--bg-subtle)" }} />
                        ))}
                    </div>
                </div>
            ) : (
                <>
                    {/* Hero identity card */}
                    <div className={`transition-all duration-700 delay-75 ${loaded ? "opacity-100 translate-y-0" : "opacity-0 translate-y-4"}`}>
                        <Card className="mb-6 relative overflow-hidden">
                            <div
                                className="absolute -top-16 -right-16 w-56 h-56 rounded-full pointer-events-none"
                                style={{ background: gradient, opacity: 0.12, filter: "blur(50px)" }}
                            />
                            <div className="relative flex flex-col sm:flex-row items-start sm:items-center gap-5">
                                <div
                                    className="w-20 h-20 rounded-2xl flex items-center justify-center flex-shrink-0 shadow-lg"
                                    style={{ background: gradient }}
                                >
                                    <span className="text-2xl font-black text-white tracking-tight">
                                        {getInitials(displayName)}
                                    </span>
                                </div>
                                <div className="flex-1 min-w-0">
                                    <div className="flex flex-wrap items-center gap-3 mb-1">
                                        <h2 className="text-xl font-black tracking-tight truncate" style={{ color: "var(--text-primary)" }}>
                                            {displayName}
                                        </h2>
                                        <Badge status={profile?.active ? "active" : "idle"} />
                                    </div>
                                    <p className="text-sm mb-2" style={{ color: "var(--text-muted)" }}>{profile?.email ?? "—"}</p>
                                    <div className="flex flex-wrap items-center gap-3 text-[11px]" style={{ color: "var(--text-faint)" }}>
                                        {profile?.username && (
                                            <span className="font-mono px-2 py-0.5 rounded" style={{ backgroundColor: "var(--bg-subtle)" }}>
                                                @{profile.username}
                                            </span>
                                        )}
                                        {accountAge !== null && <span>Member for {accountAge} {accountAge === 1 ? "day" : "days"}</span>}
                                    </div>
                                </div>
                            </div>
                        </Card>
                    </div>

                    {/* Quick stats */}
                    <div className={`grid grid-cols-2 md:grid-cols-4 gap-4 mb-6 transition-all duration-700 delay-100 ${loaded ? "opacity-100 translate-y-0" : "opacity-0 translate-y-4"}`}>
                        <StatCard label="Roles" value={roles.length} accent="#a78bfa" icon="tokens" />
                        <StatCard label="Groups" value={groups.length} accent="#34d399" icon="completion" />
                        <StatCard label="Account Age" value={accountAge ?? "—"} sub={accountAge !== null ? "days" : undefined} accent="#f59e0b" icon="avgTime" />
                        <StatCard label="Status" value={profile?.active ? "Active" : "Inactive"} accent={profile?.active ? "#34d399" : "#f43f5e"} icon="avgCompletion" />
                    </div>

                    {/* Details grid */}
                    <div className={`grid grid-cols-1 lg:grid-cols-2 gap-6 transition-all duration-700 delay-150 ${loaded ? "opacity-100 translate-y-0" : "opacity-0 translate-y-4"}`}>
                        <Card>
                            <div className="text-xs uppercase tracking-widest mb-4" style={{ color: "var(--text-muted)" }}>
                                Contact Information
                            </div>
                            <InfoRow label="First Name" value={profile?.firstName || "—"} />
                            <InfoRow label="Last Name" value={profile?.lastName || "—"} />
                            <InfoRow label="Email" value={profile?.email || "—"} />
                            <InfoRow
                                label="Phone"
                                value={profile?.phoneNumber ? `${profile?.countryCode ?? ""} ${profile.phoneNumber}` : "—"}
                            />
                        </Card>

                        <Card>
                            <div className="text-xs uppercase tracking-widest mb-4" style={{ color: "var(--text-muted)" }}>
                                Access
                            </div>
                            <div className="mb-4">
                                <p className="text-[11px] uppercase tracking-wider mb-2" style={{ color: "var(--text-faint)" }}>Roles</p>
                                <ChipList
                                    items={roles}
                                    accent="rgb(167,139,250)"
                                    accentBg="rgba(167,139,250,0.1)"
                                    accentBorder="rgba(167,139,250,0.25)"
                                    emptyLabel="No roles assigned"
                                />
                            </div>
                            <div>
                                <p className="text-[11px] uppercase tracking-wider mb-2" style={{ color: "var(--text-faint)" }}>Groups</p>
                                <ChipList
                                    items={groups}
                                    accent="rgb(52,211,153)"
                                    accentBg="rgba(52,211,153,0.1)"
                                    accentBorder="rgba(52,211,153,0.25)"
                                    emptyLabel="No groups assigned"
                                />
                            </div>
                        </Card>

                        <Card className="lg:col-span-2">
                            <div className="text-xs uppercase tracking-widest mb-4" style={{ color: "var(--text-muted)" }}>
                                Account Activity
                            </div>
                            <div className="grid grid-cols-1 sm:grid-cols-2 gap-x-8">
                                <div>
                                    <InfoRow label="Created" value={formatDate(profile?.createdAt)} />
                                    <InfoRow label="Created By" value={profile?.createdBy || "—"} />
                                </div>
                                <div>
                                    <InfoRow label="Last Updated" value={formatDate(profile?.updatedAt)} />
                                    <InfoRow label="Updated By" value={profile?.updatedBy || "—"} />
                                </div>
                            </div>
                        </Card>
                    </div>
                </>
            )}
        </>
    );
}
