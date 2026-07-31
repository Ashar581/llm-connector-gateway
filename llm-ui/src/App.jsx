import { NavLink, Outlet } from "react-router";
import { useState, useEffect } from "react";
import { useTheme } from "./context/ThemeContext";
import "./App.css";

// ── Brand mark — a signal passing through an open gate ─────
const GatewayMark = ({ size = 30 }) => (
  <svg width={size} height={size} viewBox="0 0 32 32" fill="none" xmlns="http://www.w3.org/2000/svg">
    <defs>
      <linearGradient id="gatewayGrad" x1="2" y1="4" x2="30" y2="28" gradientUnits="userSpaceOnUse">
        <stop offset="0" stopColor="var(--accent)" />
        <stop offset="1" stopColor="var(--accent-2)" />
      </linearGradient>
    </defs>
    <path d="M9 5.5H5.5C4.67 5.5 4 6.17 4 7v18c0 .83.67 1.5 1.5 1.5H9"
      stroke="url(#gatewayGrad)" strokeWidth="2.6" strokeLinecap="round" strokeLinejoin="round" />
    <path d="M23 5.5h3.5c.83 0 1.5.67 1.5 1.5v18c0 .83-.67 1.5-1.5 1.5H23"
      stroke="url(#gatewayGrad)" strokeWidth="2.6" strokeLinecap="round" strokeLinejoin="round" />
    <circle cx="16" cy="16" r="3.4" fill="url(#gatewayGrad)" />
    <circle cx="16" cy="16" r="7" stroke="url(#gatewayGrad)" strokeWidth="1.4" strokeOpacity="0.35" />
  </svg>
);

// ── Icons ─────────────────────────────────────────────────
const SunIcon = () => (
  <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24"
    fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <circle cx="12" cy="12" r="5" />
    <line x1="12" y1="1" x2="12" y2="3" /><line x1="12" y1="21" x2="12" y2="23" />
    <line x1="4.22" y1="4.22" x2="5.64" y2="5.64" /><line x1="18.36" y1="18.36" x2="19.78" y2="19.78" />
    <line x1="1" y1="12" x2="3" y2="12" /><line x1="21" y1="12" x2="23" y2="12" />
    <line x1="4.22" y1="19.78" x2="5.64" y2="18.36" /><line x1="18.36" y1="5.64" x2="19.78" y2="4.22" />
  </svg>
);

const MoonIcon = () => (
  <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24"
    fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <path d="M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z" />
  </svg>
);

const LayoutGridIcon = () => (
  <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24"
    fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <rect x="3" y="3" width="7" height="7" /><rect x="14" y="3" width="7" height="7" />
    <rect x="3" y="14" width="7" height="7" /><rect x="14" y="14" width="7" height="7" />
  </svg>
);

const CpuIcon = () => (
  <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24"
    fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <rect x="4" y="4" width="16" height="16" rx="2" /><rect x="9" y="9" width="6" height="6" />
    <line x1="9" y1="1" x2="9" y2="4" /><line x1="15" y1="1" x2="15" y2="4" />
    <line x1="9" y1="20" x2="9" y2="23" /><line x1="15" y1="20" x2="15" y2="23" />
    <line x1="20" y1="9" x2="23" y2="9" /><line x1="20" y1="14" x2="23" y2="14" />
    <line x1="1" y1="9" x2="4" y2="9" /><line x1="1" y1="14" x2="4" y2="14" />
  </svg>
);
const AnalyticsIcon = () => (
  <svg
    xmlns="http://www.w3.org/2000/svg"
    width="20"
    height="20"
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    strokeWidth="2"
    strokeLinecap="round"
    strokeLinejoin="round"
  >
    <path d="M3 3v18h18" />
    <path d="M7 16V9" />
    <path d="M12 16V5" />
    <path d="M17 16v-4" />
    <path d="M7 9h0" />
    <path d="M12 5h0" />
    <path d="M17 12h0" />
  </svg>
);


const PlayIcon = () => (
  <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24"
    fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <polygon points="5 3 19 12 5 21 5 3" />
  </svg>
);

const SettingsIcon = () => (
  <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24"
    fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <circle cx="12" cy="12" r="3" />
    <path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83-2.83l.06-.06A1.65 1.65 0 0 0 4.68 15a1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 2.83-2.83l.06.06A1.65 1.65 0 0 0 9 4.68a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 2.83l-.06.06A1.65 1.65 0 0 0 19.4 9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z" />
  </svg>
);

const desktopNavLinks = [
  { to: "/", label: "Dashboard", end: true },
  { to: "/agents", label: "Agents" },
  { to: "/stats", label: "Stats" },
  { to: "/playground", label: "Playground" },
  { to: "/settings", label: "Settings" },
];

const mobileNavLinks = [
  { to: "/", label: "Home", Icon: LayoutGridIcon, end: true },
  { to: "/agents", label: "Agents", Icon: CpuIcon },
  { to: "/stats", label: "Stats", Icon: AnalyticsIcon },
  { to: "/playground", label: "Play", Icon: PlayIcon },
  { to: "/settings", label: "Settings", Icon: SettingsIcon },
];

export default function App() {
  const { theme, toggleTheme } = useTheme();
  const isDark = theme === "dark";

  const [glowPos, setGlowPos] = useState({ x: 50, y: 50 });

  useEffect(() => {
    const handleMouseMove = (e) => {
      setGlowPos({
        x: (e.clientX / window.innerWidth) * 100,
        y: (e.clientY / window.innerHeight) * 100,
      });
    };
    window.addEventListener("mousemove", handleMouseMove);
    return () => window.removeEventListener("mousemove", handleMouseMove);
  }, []);

  return (
    <div className="min-h-screen font-mono overflow-x-hidden relative transition-colors duration-300"
      style={{ backgroundColor: "var(--bg-primary)", color: "var(--text-primary)" }}>

      {/* Ambient glow */}
      <div className="pointer-events-none fixed inset-0 z-0 transition-all duration-700"
        style={{
          background: `radial-gradient(600px circle at ${glowPos.x}% ${glowPos.y}%, var(--glow-color), transparent 70%)`,
        }}
      />

      {/* Dot-grid background — the routing lattice a request travels through */}
      <div className="dot-grid pointer-events-none fixed inset-0 z-0 opacity-70" />

      {/* Navbar */}
      <nav
        className="relative z-10 flex items-center justify-between px-8 border-b backdrop-blur-sm sticky top-0"
        style={{
          borderColor: "var(--border)",
          backgroundColor: "var(--bg-nav)",
        }}
      >
        {/* Logo */}
        <div className="flex items-center gap-2.5 py-3">
          <GatewayMark />
          <span className="font-display text-[15px] sm:text-base font-bold tracking-tight leading-none whitespace-nowrap"
            style={{ color: "var(--text-primary)" }}>
            LLM<span style={{ color: "var(--text-faint)" }}>-</span
            ><span className="brand-gradient-text">GATEWAY</span
            ><span style={{ color: "var(--text-faint)" }}>-</span>UI
          </span>
        </div>

        {/* Desktop nav links */}
        <div className="hidden md:flex items-center gap-1 absolute left-1/2 -translate-x-1/2">
          {desktopNavLinks.map(({ to, label, end }) => (
            <NavLink
              key={to}
              to={to}
              end={end}
              className={({ isActive }) =>
                `px-4 py-2 text-xs tracking-wider uppercase transition-all duration-200 rounded ${isActive
                  ? "text-amber-400 bg-amber-400/10 border border-amber-400/30"
                  : "hover:text-amber-400"
                }`
              }
              style={({ isActive }) =>
                isActive ? {} : { color: "var(--text-muted)" }
              }
            >
              {label}
            </NavLink>
          ))}
        </div>

        {/* Right side */}
        <div className="flex items-center gap-3">
          <button
            onClick={toggleTheme}
            className="w-8 h-8 rounded-lg border flex items-center justify-center transition-all duration-200 hover:border-amber-400/40 hover:text-amber-400"
            style={{
              borderColor: "var(--border)",
              color: "var(--text-muted)",
            }}
            title={isDark ? "Switch to light mode" : "Switch to dark mode"}
          >
            {isDark ? <SunIcon /> : <MoonIcon />}
          </button>
        </div>
      </nav>

      {/* Page content */}
      <main className="relative z-10 px-4 md:px-8 py-8 md:py-12 max-w-6xl mx-auto pb-28 md:pb-16">
        <Outlet />
      </main>

      {/* Desktop status bar */}
      <div className="hidden md:flex fixed bottom-0 left-0 right-0 z-10 border-t backdrop-blur px-8 py-2 items-center justify-between"
        style={{ borderColor: "var(--border)", backgroundColor: "var(--bg-nav)" }}>
        <div className="flex items-center gap-2">
          <div className="w-1.5 h-1.5 rounded-full bg-emerald-400 shadow-[0_0_6px_rgba(52,211,153,0.7)]" />
          <span className="text-xs" style={{ color: "var(--text-faint)" }}>All systems operational</span>
        </div>
        <span className="text-xs" style={{ color: "var(--text-faint)" }}>v1.0.0</span>
      </div>

      {/* Mobile bottom tab bar */}
      <nav className="md:hidden fixed bottom-0 left-0 right-0 z-20 border-t backdrop-blur-md"
        style={{ borderColor: "var(--border)", backgroundColor: "var(--bg-nav)" }}>
        <div className="flex items-center justify-around px-2 py-2">
          {mobileNavLinks.map(({ to, label, Icon, end }) => (
            <NavLink key={to} to={to} end={end}
              className={({ isActive }) =>
                `flex flex-col items-center gap-1 px-4 py-2 rounded-lg transition-all duration-200 ${isActive ? "text-amber-400" : ""
                }`
              }
              style={({ isActive }) => isActive ? {} : { color: "var(--text-muted)" }}
            >
              {({ isActive }) => (
                <>
                  <div className={`p-1.5 rounded-lg transition-all duration-200 ${isActive ? "bg-amber-400/10" : ""}`}>
                    <Icon />
                  </div>
                  <span className="text-[10px] uppercase tracking-wider">{label}</span>
                </>
              )}
            </NavLink>
          ))}
        </div>
      </nav>

    </div>
  );
}