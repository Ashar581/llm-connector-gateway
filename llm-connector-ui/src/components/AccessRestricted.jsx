export default function AccessRestricted({ routeLabel }) {
    return (
        <div className="flex flex-col items-center justify-center text-center py-24 px-6">
            <div
                className="w-14 h-14 rounded-full flex items-center justify-center mb-5"
                style={{ backgroundColor: "rgba(248,113,113,0.1)", border: "1px solid rgba(248,113,113,0.25)" }}
            >
                <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="#f87171" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                    <rect x="3" y="11" width="18" height="10" rx="2" />
                    <path d="M7 11V7a5 5 0 0 1 10 0v4" />
                </svg>
            </div>
            <p className="text-xs tracking-[0.25em] uppercase mb-2" style={{ color: "#f87171" }}>
                Access Restricted
            </p>
            <h2 className="text-lg font-black tracking-tight mb-2" style={{ color: "var(--text-primary)" }}>
                {routeLabel ? `You don't have access to ${routeLabel}` : "You don't have access to this page"}
            </h2>
            <p className="text-sm max-w-sm" style={{ color: "var(--text-muted)" }}>
                Your account's role doesn't have permission to view this section. Contact your administrator if you think this is a mistake.
            </p>
        </div>
    );
}
