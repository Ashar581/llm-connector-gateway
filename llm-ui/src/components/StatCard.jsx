import Card from "./Card";

// ── Small line-icon set for KPI cards — kept inline, no new deps ──
const StatIcons = {
  requests: (p) => <path {...p} d="M4 4h11l5 5v11H4z M14 4v6h6 M9 13h6 M9 17h6" />,
  tokens: (p) => <path {...p} d="M3 10l9-6 9 6-9 6-9-6z M3 10v6l9 6 9-6v-6" />,
  prompt: (p) => <path {...p} d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z" />,
  completion: (p) => <path {...p} d="M4 4h16v12H8l-4 4z M8 9h8 M8 12h5" />,
  avgTokens: (p) => <path {...p} d="M3 3v18h18 M7 15l4-6 3 4 5-8" />,
  avgTime: (p) => <><circle cx="12" cy="13" r="8" {...p} /><path {...p} d="M12 9v4l3 2 M9 2h6" /></>,
  avgPrompt: (p) => <path {...p} d="M12 3v4 M12 17v4 M3 12h4 M17 12h4 M6 6l3 3 M15 15l3 3 M18 6l-3 3 M9 15l-3 3" />,
  avgCompletion: (p) => <path {...p} d="M20 6L9 17l-5-5" />,
};

function StatIcon({ name, color }) {
  const draw = StatIcons[name];
  if (!draw) return null;
  return (
    <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke={color} strokeWidth="2"
      strokeLinecap="round" strokeLinejoin="round">
      {draw({ fill: "none" })}
    </svg>
  );
}

// ── Stat card — uses CSS variables, no hardcoded dark colors ──
export default function StatCard({ label, value, sub, accent = "#f59e0b", icon }) {
  return (
    <Card className="group">
      <div className="relative overflow-hidden">
        {/* accent glow blob */}
        <div
          className="absolute -top-6 -right-6 w-24 h-24 rounded-full pointer-events-none transition-transform duration-300 group-hover:scale-110"
          style={{ background: accent, opacity: 0.1, filter: "blur(26px)" }}
        />
        <div className="flex items-center gap-2 mb-2.5">
          {icon && (
            <span
              className="w-6 h-6 rounded-md flex items-center justify-center flex-shrink-0"
              style={{ background: `${accent}1a` }}
            >
              <StatIcon name={icon} color={accent} />
            </span>
          )}
          <p className="text-[11px] font-semibold tracking-widest uppercase truncate"
            style={{ color: "var(--text-muted)" }}>
            {label}
          </p>
        </div>
        <p className="font-mono text-3xl font-bold leading-none mb-1"
          style={{ color: "var(--text-primary)" }}>
          {value}
        </p>
        {sub && <p className="text-xs mt-1" style={{ color: "var(--text-faint)" }}>{sub}</p>}
        <div
          className="absolute bottom-0 left-0 right-0 h-0.5 rounded"
          style={{ background: `linear-gradient(90deg, ${accent}, transparent)`, opacity: 0.6 }}
        />
      </div>
    </Card>
  );
}
