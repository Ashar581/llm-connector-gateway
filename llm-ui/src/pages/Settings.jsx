import { useState, useEffect } from "react";
import PageHeader from "../components/PageHeader";
import Card from "../components/Card";
import Button from "../components/Button";
import { useNavigate } from "react-router";
import { useAuth } from "../context/AuthContext";

export default function Settings() {
  const [loaded, setLoaded] = useState(false);
  const [saved, setSaved] = useState(false);
  const [baseUrl, setBaseUrl] = useState(
    localStorage.getItem("BASE_URL") ||
    import.meta.env.VITE_BASE_URL ||
    ""
  );

  const { logout } = useAuth();
  const { navigate } = useNavigate

  useEffect(() => { setTimeout(() => setLoaded(true), 80); }, []);

  const handleSave = () => {
    // Save base URL locally
    localStorage.setItem("BASE_URL", baseUrl);

    setSaved(true);

    setTimeout(() => { setSaved(false); window.location.reload(); }, 1500);
  };
  const handleLogout = () => {
    logout();
    navigate('/ui/login')
  }



  const inputCls = "input-theme w-full rounded px-4 py-2.5 text-sm font-mono";
  const labelCls = "text-xs uppercase tracking-wider block mb-1.5";

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
              <label
                className={labelCls}
                style={{ color: "var(--text-muted)" }}
              >
                Base URL
              </label>

              <input
                type="text"
                placeholder="https://api.example.com"
                value={baseUrl}
                onChange={(e) => setBaseUrl(e.target.value)}
                className={inputCls}
              />

              <p
                className="text-xs mt-2"
                style={{ color: "var(--text-faint)" }}
              >
                Overrides VITE_BASE_URL locally
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
