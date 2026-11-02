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
 * Requirement: Check if port 5678 is mapped with any domain/reverse proxy by
 * the deployed system. If a mapped domain/proxy is found (e.g. /n8n or
 * custom domain mapping), use that. Else (or if unmapped), fall back to
 * localhost:5678.
 *
 * Detection Priority:
 *   1. VITE_N8N_BASE_URL env var — explicit build-time override
 *   2. localStorage "N8N_BASE_URL" — user's manual override from Settings
 *   3. Smart derived domain URLs (n8n.<domain>, n8n-<hostname>, <hostname>-n8n.ngrok-free.dev)
 *   4. Same-origin proxy /n8n (mapped domain path on the deployed system)
 *   5. http://localhost:5678 — default local n8n instance
 *   6. Current hostname:5678 (if remote domain maps port 5678 directly)
 *   7. Public IP:5678 (server deployment with n8n on 0.0.0.0)
 *   8. Fallback to http://localhost:5678
 *
 * The detection is async because it probes real servers; the component
 * that uses this waits for the result before rendering the iframe.
 */
async function getN8nBaseUrl() {
  // 1. Build-time env var (explicit override)
  const viteEnv = import.meta.env.VITE_N8N_BASE_URL;
  if (viteEnv) {
    return viteEnv.replace(/\/+$/, "");
  }

  // 2. User override from localStorage
  const stored = localStorage.getItem("N8N_BASE_URL");
  if (stored) {
    return stored.replace(/\/+$/, "");
  }

  const hostname = window.location.hostname;
  const protocol = window.location.protocol;
  const isLocalhost = hostname === "localhost" || hostname === "127.0.0.1";

  // 3. Automatically derive and probe potential mapped n8n domains/subdomains
  //    (Handles cases where n8n is mapped to a different URL/subdomain than the main UI)
  if (!isLocalhost) {
    const potentialUrls = [];

    // Pattern A: n8n.<domain> (e.g. https://n8n.mycompany.com or https://n8n.ngrok-free.dev)
    const parts = hostname.split(".");
    if (parts.length >= 2) {
      const rootDomain = parts.slice(-2).join(".");
      potentialUrls.push(`${protocol}//n8n.${rootDomain}`);
      potentialUrls.push(`${protocol}//n8n.${hostname}`);
    }

    // Pattern B: <hostname-without-extension>-n8n.<domain> or ngrok prefix pattern
    potentialUrls.push(`${protocol}//n8n-${hostname}`);
    if (hostname.includes("ngrok-free.dev") || hostname.includes("ngrok.io")) {
      const baseName = hostname.split(".")[0];
      potentialUrls.push(`${protocol}//${baseName}-n8n.ngrok-free.dev`);
      potentialUrls.push(`${protocol}//n8n.${hostname}`);
    }

    // Probe each derived potential URL
    for (const url of potentialUrls) {
      const ok = await checkN8nAccessible(url);
      if (ok) return url;
    }
  }

  // 4. Try same-origin /n8n proxy path
  const proxyUrl = `${window.location.origin}/n8n`;
  const proxyOk = await checkN8nAccessible(proxyUrl);
  if (proxyOk) return proxyUrl;

  // 5. Try http://localhost:5678 — default local n8n instance
  const localOk = await checkN8nAccessible("http://localhost:5678");
  if (localOk) return "http://localhost:5678";

  // 6. Try current hostname:5678
  if (!isLocalhost) {
    const currentHostUrl = `${protocol}//${hostname}:5678`;
    const hostOk = await checkN8nAccessible(currentHostUrl);
    if (hostOk) return currentHostUrl;
  }

  // 7. Try public IP:5678
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
      // public IP detection failed — fall through
    }
  }

  // 8. Final fallback to localhost:5678
  return "http://localhost:5678";
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