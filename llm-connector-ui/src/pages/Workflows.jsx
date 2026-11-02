import { useState, useEffect } from "react";
import PageHeader from "../components/PageHeader";
import Card from "../components/Card";
import Button from "../components/Button";
import { useAuth } from "../context/AuthContext";
import { getApiRoot } from "../services/apiService";

const ExternalLinkIcon = () => (
  <svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24"
    fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <path d="M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6" />
    <polyline points="15 3 21 3 21 9" />
    <line x1="10" y1="14" x2="21" y2="3" />
  </svg>
);

const RefreshIcon = () => (
  <svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24"
    fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <polyline points="23 4 23 10 17 10" />
    <path d="M20.49 15a9 9 0 1 1-2.12-9.36L23 10" />
  </svg>
);

/**
 * Checks if a URL is accessible (server is reachable).
 * Uses no-cors mode to avoid CORS issues; just detects reachability.
 */
async function checkN8nAccessible(url) {
  const controller = new AbortController();
  const timeoutId = setTimeout(() => controller.abort(), 2000);

  try {
    await fetch(url, {
      method: "GET",
      signal: controller.signal,
      mode: "no-cors",
    });
    clearTimeout(timeoutId);
    return true;
  } catch (error) {
    clearTimeout(timeoutId);
    // AbortError = timeout (server not reachable in time)
    // Other errors = connection refused or CORS block (still not usable)
    return false;
  }
}

/**
 * Dynamically resolves the n8n base URL with full automation.
 *
 * This function probes multiple possible n8n locations in order of likelihood,
 * ensuring the iframe works whether accessed via localhost, domain, or public IP.
 *
 * Detection priority (fully automatic — no manual config needed):
 *   1. VITE_N8N_BASE_URL env var — explicit build-time override (highest priority)
 *   2. localStorage "N8N_BASE_URL" — user's manual override from Settings
 *   3. Current hostname:5678 — if UI is on a domain/IP, try that same host on port 5678
 *   4. localhost:5678 — for local development (browser on same machine as n8n)
 *   5. Same-origin /n8n proxy path — Spring Boot reverse proxy mode
 *   6. Public IP:5678 — when server has n8n on 0.0.0.0 and domain maps to it
 *   7. Fallback to /n8n proxy path — last resort for embedded deployments
 *
 * The detection is async because it probes real servers; the component
 * that uses this waits for the result before rendering the iframe.
 */
async function getN8nBaseUrl() {
  // 1. Build-time env var (explicit override — always wins)
  const viteEnv = import.meta.env.VITE_N8N_BASE_URL;
  if (viteEnv) {
    return viteEnv.replace(/\/+$/, "");
  }

  // 2. User override from localStorage (manual setting in UI)
  const stored = localStorage.getItem("N8N_BASE_URL");
  if (stored) {
    return stored.replace(/\/+$/, "");
  }

  // Determine if we're on localhost for branching logic
  const isLocalhost =
    window.location.hostname === "localhost" ||
    window.location.hostname === "127.0.0.1";

  // 3. Try current hostname:5678 — works when UI is served from a domain/IP
  //    and n8n is bound to 0.0.0.0 on the same server
  if (!isLocalhost) {
    const currentHostUrl = `${window.location.protocol}//${window.location.hostname}:5678`;
    const hostOk = await checkN8nAccessible(currentHostUrl);
    if (hostOk) return currentHostUrl;
  }

  // 4. Try localhost:5678 — standard local development setup
  if (isLocalhost) {
    const localOk = await checkN8nAccessible("http://localhost:5678");
    if (localOk) return "http://localhost:5678";
  }

  // 5. Try same-origin /n8n path — Spring Boot reverse proxy integration
  const proxyUrl = `${window.location.origin}/n8n`;
  const proxyOk = await checkN8nAccessible(proxyUrl);
  if (proxyOk) return proxyUrl;

  // 6. Try public IP:5678 — when server has external IP and n8n on 0.0.0.0
  if (!isLocalhost) {
    try {
      const ipRes = await fetch("https://api.ipify.org?format=json", {
        signal: AbortSignal.timeout(3000),
      });
      const ipData = await ipRes.json();
      const publicIp = ipData.ip;
      const publicUrl = `http://${publicIp}:5678`;
      const publicOk = await checkN8nAccessible(publicUrl);
      if (publicOk) return publicUrl;
    } catch {
      // public IP detection failed — fall through to proxy fallback
    }
  }

  // 7. Final fallback — assume reverse proxy path for embedded deployments
  return isLocalhost ? "http://localhost:5678" : `${window.location.origin}/n8n`;
}

// Tab component for the workflows page
const Tab = ({ isActive, onClick, children }) => (
  <button
    onClick={onClick}
    className={`px-4 py-2 text-xs tracking-wider uppercase transition-all duration-200 rounded-t ${isActive
      ? "text-amber-400 bg-amber-400/10 border border-amber-400/30"
      : "hover:text-amber-400"
    }`}
    style={isActive ? {} : { color: "var(--text-muted)" }}
  >
    {children}
  </button>
);

// Workflows list view component
const WorkflowsList = () => {
  const [workflows, setWorkflows] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const { user } = useAuth();
  const apiRoot = getApiRoot();

  useEffect(() => {
    const fetchWorkflows = async () => {
      try {
        const apiUrl = apiRoot;
        const response = await fetch(`${apiUrl}/api/llm/v1/workflows`, {
          headers: {
            'Authorization': `Bearer ${user?.token}`,
            'Content-Type': 'application/json',
          },
        });

        if (!response.ok) {
          throw new Error(`HTTP error! status: ${response.status}`);
        }

        const data = await response.json();
        setWorkflows(data);
      } catch (err) {
        setError(err.message);
      } finally {
        setLoading(false);
      }
    };

    fetchWorkflows();
  }, [user?.token]);

  if (loading) {
    return (
      <div className="flex items-center justify-center py-12">
        <div className="w-6 h-6 border-2 border-amber-400 border-t-transparent rounded-full animate-spin" />
      </div>
    );
  }

  if (error) {
    return (
      <div className="p-6 text-xs font-mono" style={{ color: "var(--text-faint)" }}>
        Error loading workflows: {error}
      </div>
    );
  }

  return (
    <div className="space-y-4">
      {workflows.length === 0 ? (
        <div className="p-6 text-xs font-mono" style={{ color: "var(--text-faint)" }}>
          No workflows available yet.
        </div>
      ) : (
        workflows.map((workflow) => (
          <Card key={workflow.id} className="p-4">
            <div className="flex items-start gap-4">
              <div className="flex-shrink-0 w-8 h-8 rounded-lg flex items-center justify-center bg-amber-400/10">
                <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24"
                  fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                  <rect x="3" y="3" width="7" height="7" />
                  <rect x="14" y="3" width="7" height="7" />
                  <rect x="3" y="14" width="7" height="7" />
                  <rect x="14" y="14" width="7" height="7" />
                </svg>
              </div>
              <div className="flex-1">
                <h3 className="text-sm font-mono font-bold" style={{ color: "var(--text-primary)" }}>
                  {workflow.name}
                </h3>
                <p className="text-xs mt-1" style={{ color: "var(--text-muted)" }}>
                  {workflow.description || "No description provided"}
                </p>
                <div className="flex items-center gap-2 mt-3">
                  <span className="text-[10px] uppercase tracking-wider px-2 py-0.5 rounded-full"
                    style={{ backgroundColor: "var(--bg-subtle)", color: "var(--text-faint)" }}>
                    {workflow.status || "draft"}
                  </span>
                  <span className="text-[10px] uppercase tracking-wider px-2 py-0.5 rounded-full"
                    style={{ backgroundColor: "var(--bg-subtle)", color: "var(--text-faint)" }}>
                    {workflow.createdAt ? new Date(workflow.createdAt).toLocaleDateString() : "-"}
                  </span>
                </div>
              </div>
            </div>
          </Card>
        ))
      )}
    </div>
  );
};

// N8n iframe view component
const N8nView = () => {
  const [loaded, setLoaded] = useState(false);
  const [iframeLoaded, setIframeLoaded] = useState(false);
  const [n8nBaseUrl, setN8nBaseUrl] = useState("http://localhost:5678");
  const [urlResolving, setUrlResolving] = useState(true);
  const [retryKey, setRetryKey] = useState(0);

  useEffect(() => {
    setTimeout(() => setLoaded(true), 80);
  }, []);

  useEffect(() => {
    let active = true;
    setUrlResolving(true);
    getN8nBaseUrl().then((url) => {
      if (active) {
        setN8nBaseUrl(url);
        setUrlResolving(false);
      }
    });
    return () => {
      active = false;
    };
  }, [retryKey]);

  const handleRefresh = () => {
    setIframeLoaded(false);
    setRetryKey((prev) => prev + 1);
  };

  const n8nUrl = n8nBaseUrl.startsWith("http") ? n8nBaseUrl : `/${n8nBaseUrl.replace(/^\/+/, "")}`;
  const isLocalDevMode = n8nBaseUrl.includes("localhost") || n8nBaseUrl.includes("127.0.0.1");

  return (
    <div className={`transition-all duration-700 delay-100 ${loaded ? "opacity-100 translate-y-0" : "opacity-0 translate-y-4"}`}>
      <div className="flex items-center justify-between mb-4">
        <div className="flex items-center gap-2">
          <Button
            onClick={handleRefresh}
            variant="secondary"
            size="sm"
            className="flex items-center gap-1.5 text-xs"
          >
            <RefreshIcon />
            Reload Frame
          </Button>
          <a
            href={n8nBaseUrl}
            target="_blank"
            rel="noopener noreferrer"
            className="inline-flex items-center gap-1.5 px-3 py-1.5 text-xs font-mono uppercase tracking-wider rounded border transition-all duration-200 hover:border-amber-400 hover:text-amber-400"
            style={{
              borderColor: "var(--border)",
              backgroundColor: "var(--bg-secondary)",
              color: "var(--text-secondary)",
            }}
          >
            <span>Open in Tab</span>
            <ExternalLinkIcon />
          </a>
        </div>
      </div>

      <Card className="p-0 overflow-hidden relative" style={{ minHeight: "75vh" }}>
        {(!iframeLoaded || urlResolving) && (
          <div className="absolute inset-0 flex flex-col items-center justify-center gap-3 z-10 bg-[var(--bg-primary)] bg-opacity-80 backdrop-blur-sm">
            <div className="w-6 h-6 border-2 border-amber-400 border-t-transparent rounded-full animate-spin" />
            <p className="text-xs font-mono" style={{ color: "var(--text-muted)" }}>
              {urlResolving ? "Detecting n8n instance URL..." : "Connecting to n8n instance..."}
            </p>
            {isLocalDevMode && (
              <p className="text-[11px]" style={{ color: "var(--text-faint)" }}>
                Local development mode. Make sure n8n is running via <code className="text-amber-400">./bootstrap/n8n.sh start</code>
              </p>
            )}
            {!isLocalDevMode && (
              <p className="text-[11px]" style={{ color: "var(--text-faint)" }}>
                Targeting <code className="text-amber-400">{n8nBaseUrl}</code>
              </p>
            )}
          </div>
        )}

        {!urlResolving && (
          <iframe
            key={`${n8nUrl}-${retryKey}`}
            src={n8nUrl}
            title="n8n Workflow Automation"
            className="w-full border-0 rounded-lg"
            style={{ height: "75vh", backgroundColor: "var(--bg-primary)" }}
            onLoad={() => setIframeLoaded(true)}
            allow="clipboard-read; clipboard-write"
          />
        )}
      </Card>
    </div>
  );
};

export default function Workflows() {
  const [activeTab, setActiveTab] = useState("list");

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <PageHeader
          tag="Automation Workspace"
          title=""
          highlight="Workflows"
          description="Build, test, and manage local AI workflows and integrations directly inside the gateway."
        />
      </div>

      <div className="flex border-b border-[var(--border)]">
        <Tab isActive={activeTab === "list"} onClick={() => setActiveTab("list")}>
          Available Workflows
        </Tab>
        <Tab isActive={activeTab === "editor"} onClick={() => setActiveTab("editor")}>
          Workflow Editor
        </Tab>
      </div>

      <div className="mt-4">
        {activeTab === "list" && <WorkflowsList />}
        {activeTab === "editor" && <N8nView />}
      </div>
    </div>
  );
}