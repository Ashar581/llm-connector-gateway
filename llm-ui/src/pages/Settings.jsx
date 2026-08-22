import { useState, useEffect } from "react";
import PageHeader from "../components/PageHeader";
import Card from "../components/Card";
import Button from "../components/Button";
import { useNavigate } from "react-router";
import { useAuth } from "../context/AuthContext";
import { getApiBaseUrlInfo } from "../services/apiService";

const SOURCE_LABELS = {
  override: { text: "Manual override", color: "#f59e0b" },
  "auto-detected": { text: "Auto-detected (same origin)", color: "rgb(52,211,153)" },
  default: { text: "Build default (VITE_BASE_URL)", color: "var(--text-faint)" },
};

export default function Settings() {
  const [loaded, setLoaded] = useState(false);
  const [saved, setSaved] = useState(false);
  const [activeInfo] = useState(getApiBaseUrlInfo());
  const [baseUrl, setBaseUrl] = useState(
    localStorage.getItem("BASE_URL") || activeInfo.url || ""
  );

  const { logout } = useAuth();
  const navigate = useNavigate();

  useEffect(() => { setTimeout(() => setLoaded(true), 80); }, []);

  const handleSave = () => {
    localStorage.setItem("BASE_URL", baseUrl);
    setSaved(true);
    setTimeout(() => { setSaved(false); window.location.reload(); }, 1500);
  };

  const handleClearOverride = () => {
    localStorage.removeItem("BASE_URL");
    window.location.reload();
  };

  const handleLogout = () => {
    logout();
    navigate("/login");
  };

  const inputCls = "input-theme w-full rounded px-4 py-2.5 text-sm font-mono";
  const labelCls = "text-xs uppercase tracking-wider block mb-1.5";
  const sourceMeta = SOURCE_LABELS[activeInfo.source] ?? SOURCE_LABELS.default;

  return (
    <>
      <div className={`transition-all duration-700 ${loaded ? "opacity-100 translate-y-0" : "opacity-0 translate-y-4"}`}>
        <PageHeader tag="Configuration" title="" highlight="Settings" description="Manage BASE_URL." />
      </div>

      <div className={`space-y-6 max-w-2xl transition-all duration-700 delay-100 ${loaded ? "opacity-100 translate-y-0" : "opacity-0 translate-y-4"}`}>

        {/* General */}
        <Card>
          <div className="text-xs uppercase tracking-widest mb-6" style={{ color: "var(--text-muted)" }}>General</div>
          <div className="space-y-5">
            <div>
              <div className="flex items-center justify-between mb-1.5">
                <label className={labelCls} style={{ color: "var(--text-muted)", marginBottom: 0 }}>
                  Base URL
                </label>
                <span
                  className="flex items-center gap-1.5 text-[10px] uppercase tracking-wider"
                  style={{ color: sourceMeta.color }}
                  title="How the currently active API base URL was determined"
                >
                  <span className="w-1.5 h-1.5 rounded-full" style={{ backgroundColor: sourceMeta.color }} />
                  {sourceMeta.text}
                </span>
              </div>

              <input
                type="text"
                placeholder="https://api.example.com"
                value={baseUrl}
                onChange={(e) => setBaseUrl(e.target.value)}
                className={inputCls}
              />

              <p className="text-xs mt-2" style={{ color: "var(--text-faint)" }}>
                Overrides auto-detection and VITE_BASE_URL, saved locally in this browser.
              </p>

              <div
                className="mt-3 rounded px-3 py-2.5 text-xs font-mono break-all"
                style={{ backgroundColor: "var(--bg-subtle)", border: "1px solid var(--border)", color: "var(--text-secondary)" }}
              >
                Currently calling: {activeInfo.url}
              </div>

              {activeInfo.source === "override" && (
                <button
                  type="button"
                  onClick={handleClearOverride}
                  className="text-xs mt-2 underline transition-colors hover:text-amber-500"
                  style={{ color: "var(--text-faint)" }}
                >
                  Clear override — go back to auto-detection / build default
                </button>
              )}

              <p className="text-[11px] mt-4" style={{ color: "var(--text-faint)" }}>
                When this UI is hosted standalone, it needs this set (or VITE_BASE_URL at build time)
                to know where the API lives. When it's built into the Java app's own static resources,
                it automatically finds the API at whatever origin it's being served from — no
                configuration needed there.
              </p>
            </div>
          </div>
        </Card>

        <div className="flex gap-2 justify-between">
          <Button onClick={handleLogout} variant="primary" size="lg" className={saved ? "!bg-emerald-400" : ""}>
            Logout
          </Button>
          <Button onClick={handleSave} variant="primary" size="lg" className={saved ? "!bg-emerald-400" : ""}>
            {saved ? "✓ Saved!" : "Save Changes"}
          </Button>
        </div>
      </div>
    </>
  );
}
