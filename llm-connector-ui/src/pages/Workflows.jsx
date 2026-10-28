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
 * Gets the n8n URL to use.
 * - In production/deployment: respects VITE_N8N_BASE_URL env var (set by Spring Boot build)
 * - In local dev: defaults to http://localhost:5678 and relies on bootstrap n8n.sh CSP fix
 *
 * For iframe embedding to work with Spring Boot reverse proxy:
 * - Set VITE_N8N_BASE_URL=/n8n (or whatever proxy path) in the Vite config
 * - Spring Boot must proxy that path to http://localhost:5678
 * - The iframe src becomes relative (/n8n) which resolves to same origin
 */
function getN8nBaseUrl() {
  // Check for Vite env var first (set during production build)
  const viteEnv = import.meta.env.VITE_N8N_BASE_URL;
  if (viteEnv) {
    // Ensure it ends without trailing slash for consistent handling
    return viteEnv.replace(/\/+$/, "");
  }
  // Local development default
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
  const n8nBaseUrl = getN8nBaseUrl();
  // For local dev we use full URL; for production with proxy we use path only
  const n8nUrl = n8nBaseUrl.startsWith("http") ? n8nBaseUrl : `/${n8nBaseUrl.replace(/^\/+/, "")}`;
  const [retryKey, setRetryKey] = useState(0);

  useEffect(() => {
    setTimeout(() => setLoaded(true), 80);
  }, []);

  const handleRefresh = () => {
    setIframeLoaded(false);
    setRetryKey((prev) => prev + 1);
  };

  // Detect if we're in a proxy/local-dev mode by checking the URL format
  const isLocalDevMode = !n8nBaseUrl.startsWith("http");

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
        {!iframeLoaded && (
          <div className="absolute inset-0 flex flex-col items-center justify-center gap-3 z-10 bg-[var(--bg-primary)] bg-opacity-80 backdrop-blur-sm">
            <div className="w-6 h-6 border-2 border-amber-400 border-t-transparent rounded-full animate-spin" />
            <p className="text-xs font-mono" style={{ color: "var(--text-muted)" }}>
              Connecting to n8n instance...
            </p>
            {isLocalDevMode && (
              <p className="text-[11px]" style={{ color: "var(--text-faint)" }}>
                Local development mode. Make sure n8n is running via <code className="text-amber-400">./bootstrap/n8n.sh start</code>
              </p>
            )}
            {!isLocalDevMode && (
              <p className="text-[11px]" style={{ color: "var(--text-faint)" }}>
                Production mode - using reverse proxy integration
              </p>
            )}
          </div>
        )}

        <iframe
          key={retryKey}
          src={n8nUrl}
          title="n8n Workflow Automation"
          className="w-full border-0 rounded-lg"
          style={{ height: "75vh", backgroundColor: "var(--bg-primary)" }}
          onLoad={() => setIframeLoaded(true)}
          allow="clipboard-read; clipboard-write"
        />
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