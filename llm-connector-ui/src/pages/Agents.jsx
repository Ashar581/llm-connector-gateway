import { useState, useEffect } from "react";
import PageHeader from "../components/PageHeader";
import Card from "../components/Card";
import Badge from "../components/Badge";
import Button from "../components/Button";
import StatusDot from "../components/StatusDot";
import AgentModal from "../components/AgentModal";
import AgentPlayground from "../components/AgentPlayground";
import apiSvc from "../services/apiService";
import toast from "react-hot-toast";
import {
  getAgents,
  getAgentTypes,
  createNewAgent,
  updateAgent,
  deleteAgent
} from "../services/agentService";
// const providers = ["All", "OpenAI", "Anthropic", "Google", "Mistral AI", "Meta", "xAI"];
import Skeleton, { SkeletonTheme } from "react-loading-skeleton";
import "react-loading-skeleton/dist/skeleton.css";

export default function Agents() {
  const [loaded, setLoaded] = useState(false);
  const [loading, setLoading] = useState(false);
  const [filter, setFilter] = useState("All".toLowerCase());
  const [search, setSearch] = useState("");
  const [agents, setAgents] = useState([]);
  const [providers, setProviders] = useState([]);
  const [modalOpen, setModalOpen] = useState(false);
  const [selectedAgent, setSelectedAgent] = useState(null);
  const [playgroundOpen, setPlaygroundOpen] = useState(false);
  const [playgroundAgent, setPlaygroundAgent] = useState(null);
  const [deleteModalOpen, setDeleteModalOpen] = useState(false);
  const [agentToDelete, setAgentToDelete] = useState(null);

  // Silent (no full-page loading skeleton) re-fetch of the whole list — used
  // after actions that mutate something the list needs to reflect but that
  // don't already return the authoritative updated agent (e.g. file
  // add/remove, which has no single-agent GET to pull fresh state from).
  const refreshAgents = async () => {
    try {
      const data = await getAgents();
      setAgents(data);
    } catch (e) {
      console.error("Failed to refresh agents", e);
    }
  };

  useEffect(() => {
    setTimeout(() => setLoaded(true), 80);
    const controller = new AbortController();
    const fetchAgents = async () => {
      try {
        setLoading(true);
        const data = await getAgents(
          controller.signal
        );
        setAgents(data);
        setLoading(false);
      } catch (e) {
        if (e.name === "AbortError") return;
        console.error("Something went wrong", e);
      }
    };
    const fetchProviders = async () => {
      try {
        const data =
          await getAgentTypes(
            controller.signal
          );
        setProviders(data);
      } catch (e) {
        console.error("Something went wrong in types", e);
      }
    };
    fetchAgents();
    fetchProviders();
    return () => controller.abort();
  }, []);

  const openCreate = () => {
    setSelectedAgent(null);
    setModalOpen(true);
  };
  const openEdit = (agent) => {
    console.log(agent)
    setSelectedAgent(agent);
    setModalOpen(true);
  };
  const openPlayground = (agent) => {
    setPlaygroundAgent(agent);
    setPlaygroundOpen(true);
  };
  const openDeleteModal = (agent) => {
    setAgentToDelete(agent);
    setDeleteModalOpen(true);
  };

  const confirmDelete = async () => {
    if (!agentToDelete) return;

    await handleDeleteAgent(agentToDelete);

    setDeleteModalOpen(false);
    setAgentToDelete(null);
  };

  const closeDeleteModal = () => {
    setDeleteModalOpen(false);
    setAgentToDelete(null);
  };

  const handleSave = async (formData, newFiles, agentId) => {
    try {
      // Only allow classificationMode when type === "classification"
      const payload = { ...formData };

      if ((payload.type ?? "").toLowerCase() !== "classification") {
        delete payload.classificationMode;
      }

      if (agentId) {
        const toastId = toast.loading("Updating agent...");
        console.log(payload);
        if (payload.documentTypeDefinitions) {
          payload.documentTypes = JSON.parse(payload.documentTypeDefinitions)
        }
        await updateAgent(
          agentId,
          payload
        );

        setAgents((prev) =>
          prev.map((a) =>
            a.name === agentId
              ? {
                ...a,
                ...payload,
              }
              : a,
          ),
        );

        toast.success("Agent updated successfully", {
          id: toastId,
        });
      } else {
        const toastId = toast.loading("Creating agent...");
        const createdAgent = await createNewAgent(payload, newFiles);

        setAgents((prev) => [...prev, createdAgent]);
        toast.success("Agent created successfully", {
          id: toastId,
        });

      }
    } catch (e) {
      console.error("Save failed", e);

      toast.error(e?.response?.data?.message || "Something went wrong");

      throw e;
    }
  };
  const handleDeleteAgent = async (agent) => {
    const toastId = toast.loading(
      "Deleting agent..."
    );

    try {
      await deleteAgent(agent.name);

      setAgents((prev) =>
        prev.filter(
          (a) => a.name !== agent.name
        )
      );

      toast.success(
        "Agent deleted successfully",
        {
          id: toastId,
        }
      );
    } catch (e) {
      console.error(
        "Failed to delete agent",
        e
      );
      toast.error(
        e?.response?.data?.message ||
        "Failed to delete agent",
        {
          id: toastId,
        }
      );
    }
  };

  const filtered = agents.filter((a) => {
    // Filter by TYPE
    const matchProvider =
      filter.toLowerCase() === "all" ||
      (a.type ?? "").toLowerCase() === filter.toLowerCase();

    // Search by name or type
    const matchSearch =
      (a.name ?? "").toLowerCase().includes(search.toLowerCase()) ||
      (a.type ?? "").toLowerCase().includes(search.toLowerCase());

    return matchProvider && matchSearch;
  });

  const toggleAgentStatus = async (agent) => {
    try {
      const updatedActive = !agent.active;

      await handleSave(
        {
          ...agent,
          active: updatedActive,
        },
        [],
        agent.name,
      );

      setAgents((prev) =>
        prev.map((a) =>
          a.id === agent.id
            ? {
              ...a,
              active: updatedActive,
            }
            : a,
        ),
      );
    } catch (e) {
      console.error("Failed to update agent status", e);

      toast.error(
        e?.response?.data?.message || "Failed to update agent status",
      );
    }
  };
  const copyAgentName = async (name) => {
    try {
      await navigator.clipboard.writeText(name);
      toast.success("Agent name copied");
    } catch {
      toast.error("Failed to copy");
    }
  };
  const AgentSkeleton = () => (
    <Card hoverable>
      <div className="flex items-start justify-between mb-4">
        <div>
          <Skeleton width={180} height={18} />
          <Skeleton width={120} height={12} className="mt-2" />
        </div>

        <Skeleton circle width={24} height={24} />
      </div>

      <Skeleton count={2} />

      <div className="flex flex-wrap gap-2 my-5">
        <Skeleton width={70} height={28} borderRadius={999} />
        <Skeleton width={100} height={28} borderRadius={999} />
        <Skeleton width={80} height={28} borderRadius={999} />
      </div>

      <div className="flex flex-wrap gap-2">
        <Skeleton height={36} width={100} />
        <Skeleton height={36} width={100} />
        <Skeleton height={36} width={100} />
        <Skeleton height={36} width={100} />
      </div>
    </Card>
  );
  return (
    <>
      <SkeletonTheme
        baseColor="var(--bg-subtle)"
        highlightColor="var(--border)"
      >
        {/* Your entire page */}
        <AgentModal
          open={modalOpen}
          onClose={() => setModalOpen(false)}
          agent={selectedAgent}
          onSave={handleSave}
          onFilesSaved={refreshAgents}
        />
        <AgentPlayground
          open={playgroundOpen}
          onClose={() => setPlaygroundOpen(false)}
          agent={playgroundAgent}
        />

        <div
          className={`transition-all duration-700 ${loaded ? "opacity-100 translate-y-0" : "opacity-0 translate-y-4"}`}
        >
          <div className="flex flex-col sm:flex-row sm:items-start sm:justify-between gap-4 mb-10">
            <PageHeader
              tag="Agent Registry"
              title="All"
              highlight="Agents"
              description="Browse, configure, and manage all connected AI agents."
            />
            <Button
              variant="primary"
              onClick={openCreate}
              className="sm:mt-6 flex-shrink-0"
            >
              + New Agent
            </Button>
          </div>
        </div>

        {/* Search + filters */}
        <div
          className={`flex flex-col md:flex-row gap-4 mb-8 transition-all duration-700 delay-100 ${loaded ? "opacity-100 translate-y-0" : "opacity-0 translate-y-4"}`}
        >
          <input
            type="text"
            placeholder="Search agents..."
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            className="input-theme rounded px-4 py-2 text-sm w-full md:w-64"
          />
          <div className="flex gap-2 flex-wrap">
            {providers.map((p) => (
              <Button
                key={p}
                onClick={() => setFilter(p)}
                variant={filter === p ? "outline" : "ghost"}
                size="sm"
              >
                {p}
              </Button>
            ))}
          </div>
        </div>

        {/* Agent cards */}
        <div
          className={`grid grid-cols-1 md:grid-cols-2 gap-4 transition-all duration-700 delay-200 ${loaded ? "opacity-100 translate-y-0" : "opacity-0 translate-y-4"}`}
        >
          {loading ? (
            [...Array(6)].map((_, i) => (
              <AgentSkeleton key={i} />
            ))
          ) : (
            filtered.map((agent) => (
              <Card key={agent.id} hoverable>
                <div className="flex items-start justify-between mb-4">
                  <div className="flex items-center gap-3">
                    <StatusDot status={agent.active ? "active" : "idle"} />
                    <div>
                      <div className="flex items-center gap-2">
                        <div
                          className="text-sm font-bold"
                          style={{ color: "var(--text-primary)" }}
                        >
                          {agent.name}
                        </div>

                        <button
                          onClick={() => copyAgentName(agent.name)}
                          title="Copy Agent Name"
                          className="p-1 rounded hover:bg-slate-100 dark:hover:bg-white/10 transition-colors"
                        >
                          <svg
                            xmlns="http://www.w3.org/2000/svg"
                            width="12"
                            height="12"
                            viewBox="0 0 24 24"
                            fill="none"
                            stroke="currentColor"
                            strokeWidth="2"
                            className="text-slate-400 hover:text-amber-500"
                          >
                            <rect x="9" y="9" width="13" height="13" rx="2" ry="2" />
                            <path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1" />
                          </svg>
                        </button>
                      </div>

                      <div
                        className="text-xs mt-0.5"
                        style={{ color: "var(--text-muted)" }}
                      >
                        {agent.model}
                      </div>
                    </div>
                  </div>
                  <Badge status={agent.active ? "active" : "idle"} />
                </div>

                {(agent.description?.trim() || agent.instructions?.trim()) && (
                  <p
                    className="text-xs mb-4 line-clamp-2 leading-relaxed"
                    style={{ color: "var(--text-faint)" }}
                  >
                    {agent.description?.trim() || agent.instructions?.trim()}
                  </p>
                )}

                <div className="flex flex-wrap gap-2 mb-5">
                  {[
                    { label: "Type", value: agent.type },
                    { label: "Max Tokens", value: agent.maxTokens ?? "Default" },
                    { label: "Temp", value: agent.temperature },
                  ].map(({ label, value }) => (
                    <div
                      key={label}
                      className="inline-flex items-center gap-1.5 rounded-full px-3 py-1.5"
                      style={{
                        backgroundColor: "var(--bg-subtle)",
                        border: "1px solid var(--border)",
                      }}
                    >
                      <span
                        className="text-[10px] uppercase tracking-wider"
                        style={{ color: "var(--text-faint)" }}
                      >
                        {label}
                      </span>
                      <span
                        className="text-xs font-bold"
                        style={{ color: "var(--text-secondary)" }}
                      >
                        {value}
                      </span>
                    </div>
                  ))}
                </div>

                {agent.files?.length > 0 && (
                  <div
                    className="text-xs mb-4"
                    style={{ color: "var(--text-muted)" }}
                  >
                    📎 {agent.files.length} file{agent.files.length > 1 ? "s" : ""}{" "}
                    attached
                  </div>
                )}

                <div className="flex flex-wrap gap-2">
                  <button
                    onClick={() => openPlayground(agent)}
                    className="flex items-center gap-2 flex-1 basis-full sm:basis-auto justify-center text-xs py-2 bg-amber-400/10 border border-amber-400/25 text-amber-500 rounded uppercase tracking-wider hover:bg-amber-400/20 hover:border-amber-400/50 transition-all duration-200"
                  >
                    <svg
                      xmlns="http://www.w3.org/2000/svg"
                      width="11"
                      height="11"
                      viewBox="0 0 24 24"
                      fill="currentColor"
                    >
                      <polygon points="5 3 19 12 5 21 5 3" />
                    </svg>
                    Test Agent
                  </button>
                  <Button variant="outline" onClick={() => openEdit(agent)} className="flex-1 min-w-[84px]">
                    Configure
                  </Button>
                  <Button
                    variant={agent.active ? "ghost" : "success"}
                    onClick={() => toggleAgentStatus(agent)}
                    className="flex-1 min-w-[84px]"
                  >
                    {agent.active ? "Pause" : "Enable"}
                  </Button>
                  <Button
                    variant="danger"
                    onClick={() => openDeleteModal(agent)}
                    className="flex-1 min-w-[84px]"
                  >
                    Delete
                  </Button>
                </div>
              </Card>
            ))
          )}

        </div>

        {filtered.length === 0 && agents.length > 0 && (
          <div
            className="text-center py-20 text-sm uppercase tracking-widest"
            style={{ color: "var(--text-faint)" }}
          >
            No agents found
          </div>
        )}

        {agents.length === 0 && loaded && (
          <div className="text-center py-20">
            <p
              className="text-sm uppercase tracking-widest mb-6"
              style={{ color: "var(--text-faint)" }}
            >
              No agents configured yet
            </p>
            <Button variant="outline" onClick={openCreate}>
              + Create your first agent
            </Button>
          </div>
        )}
        {deleteModalOpen && (
          <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60">
            <div
              className="w-full max-w-md rounded-xl p-6"
              style={{
                backgroundColor: "var(--bg-primary)",
                border: "1px solid var(--border)",
              }}
            >
              <h3
                className="text-lg font-semibold mb-3"
                style={{ color: "var(--text-primary)" }}
              >
                Delete Agent
              </h3>

              <p
                className="text-sm mb-6"
                style={{ color: "var(--text-secondary)" }}
              >
                Are you sure you want to delete{" "}
                <span className="font-semibold">
                  {agentToDelete?.name}
                </span>
                ? This action cannot be undone.
              </p>

              <div className="flex justify-end gap-3">
                <Button
                  variant="ghost"
                  onClick={closeDeleteModal}
                >
                  Cancel
                </Button>

                <Button
                  variant="danger"
                  onClick={confirmDelete}
                >
                  Delete
                </Button>
              </div>
            </div>
          </div>
        )}
      </SkeletonTheme>

    </>
  );
}
