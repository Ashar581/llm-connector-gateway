import { useState, useEffect, useRef } from "react";
import toast from "react-hot-toast";
import Button from "./Button";
import apiSvc from "../services/apiService";
import DynamicTypeFields from "./DynamicTypeFields";
import { getTypeConfig, getDefaultFieldValues } from "../configs/Typefieldconfigs";

const SOURCES = ["free", "paid"];
const CLASSIFY_MODES = ["SINGLE", "PAGE", "AUTO"];

const DEFAULT_DOC_TYPES = [
  {
    id: "PURCHASE_ORDER",
    description: "Purchase order issued by buyer to supplier for ordering goods or services.",
    keywords: ["purchase order", "po number", "buyer", "ordered quantity", "delivery schedule"],
  }
];

const defaultDocTypes = () =>
  DEFAULT_DOC_TYPES.map((d) => ({ ...d, keywordsRaw: d.keywords.join(", ") }));

const defaultForm = {
  name: "",
  model: "",
  instructions: "",
  description: "",
  temperature: 0.7,
  maxTokens: "",
  isPrivate: false,
  source: "free",
  type: "",
  pageChunk: 1,
  classificationMode: "auto",
  active: true,
};

function extractApiError(e) {
  const data = e?.response?.data;
  if (data?.message) return data.message;
  if (typeof data === "string" && data.length) return data;
  return e?.message ?? "An unexpected error occurred.";
}

export default function AgentModal({ open, onClose, agent, onSave }) {
  const [form, setForm] = useState(defaultForm);
  const [saving, setSaving] = useState(false);
  const [errors, setErrors] = useState({});

  const [modelMap, setModelMap] = useState({ free: [], paid: [] });
  const [modelsLoading, setModelsLoading] = useState(false);

  const [existingFiles, setExistingFiles] = useState([]);
  const [removedFileIds, setRemovedFileIds] = useState([]);
  const [newFiles, setNewFiles] = useState([]);
  const [fileSaving, setFileSaving] = useState(false);

  // Classification document types
  const [docTypes, setDocTypes] = useState(defaultDocTypes());
  // Type-specific fields (currently RAG's 10 config keys — vectorStore, chunkSize, etc.)
  const [typeFieldValues, setTypeFieldValues] = useState({});

  const fileInputRef = useRef();
  const isEdit = !!agent;

  const availableModels = modelMap[form.source] ?? [];
  const selectedModelData = availableModels.find((m) => m.id === form.model);
  const availableTypes = selectedModelData?.type ?? [];
  const hasFileChanges = isEdit && (removedFileIds.length > 0 || newFiles.length > 0);
  const isClassification = form.type === "classification";
  const isVision = form.type === 'vision';
  const isRag = form.type === 'rag';
  const ragConfig = getTypeConfig("rag", modelMap);

  // ── Fetch models ───────────────────────────────────────
  useEffect(() => {
    if (!open) return;
    const controller = new AbortController();
    const fetchModels = async () => {
      setModelsLoading(true);
      try {
        const res = await apiSvc.get("v1/config/model", { signal: controller.signal });
        const data = res.data.data;
        setModelMap({
          free: data.free?.models ?? [],
          paid: data.paid?.models ?? [],
        });
      } catch (e) {
        if (e.name === "AbortError") return;
        toast.error(`Failed to load models: ${extractApiError(e)}`);
      } finally {
        setModelsLoading(false);
      }
    };
    fetchModels();
    return () => controller.abort();
  }, [open]);

  // ── Populate / reset form ──────────────────────────────
  useEffect(() => {
    if (agent) {
      setForm({
        name: agent.name ?? "",
        model: agent.model ?? "",
        instructions: agent.instructions ?? "",
        description: agent.description ?? "",
        temperature: agent.temperature ?? 0.7,
        maxTokens: agent.maxTokens ?? "",
        isPrivate: agent.isPrivate ?? false,
        source: agent.source ?? "free",
        type: agent.type ?? "",
        pageChunk: agent.pageChunk ?? '',
        classificationMode: agent.classificationMode ?? "AUTO",
        active: agent.active ?? true,
      });
      setExistingFiles(agent.files ?? []);
      // Restore saved doc types if present, else use defaults
      if (agent.documentTypes?.length) {
        setDocTypes(
          agent.documentTypes.map((d) => ({
            ...d,
            keywordsRaw: Array.isArray(d.keywords) ? d.keywords.join(", ") : (d.keywordsRaw ?? ""),
          }))
        );
      } else {
        setDocTypes(defaultDocTypes());
      }
      // Restore RAG-specific fields from the saved agent, falling back to defaults for anything missing
      setTypeFieldValues(
        agent.type === "rag"
          ? getTypeConfig("rag").fields.reduce(
            (acc, f) => ({ ...acc, [f.key]: agent[f.key] ?? f.default }),
            {}
          )
          : {}
      );
    } else {
      setForm(defaultForm);
      setExistingFiles([]);
      setDocTypes(defaultDocTypes());
      setTypeFieldValues({});
    }
    setNewFiles([]);
    setRemovedFileIds([]);
    setErrors({});
  }, [agent, open]);

  // ── Escape key ─────────────────────────────────────────
  useEffect(() => {
    const handler = (e) => { if (e.key === "Escape") onClose(); };
    window.addEventListener("keydown", handler);
    return () => window.removeEventListener("keydown", handler);
  }, [onClose]);

  // ── Field helpers ──────────────────────────────────────
  const set = (key, value) => {
    setForm((prev) => ({ ...prev, [key]: value }));
    setErrors((prev) => ({ ...prev, [key]: null }));
  };

  const handleSourceChange = (source) => {
    setForm((prev) => ({ ...prev, source, model: "", type: "" }));
    setTypeFieldValues({});
    setErrors((prev) => ({ ...prev, model: null, type: null }));
  };

  const handleModelChange = (modelId) => {
    const modelData = (modelMap[form.source] ?? []).find((m) => m.id === modelId);
    const types = modelData?.type ?? [];
    const newType = types.length === 1 ? types[0] : "";
    setForm((prev) => ({
      ...prev,
      model: modelId,
      type: newType,
    }));
    setTypeFieldValues(newType === "rag" ? getDefaultFieldValues("rag") : {});
    setErrors((prev) => ({ ...prev, model: null, type: null }));
  };

  // ── Doc type helpers ───────────────────────────────────
  const addDocType = () =>
    setDocTypes((prev) => [...prev, { id: "", description: "", keywordsRaw: "" }]);

  const removeDocType = (i) =>
    setDocTypes((prev) => prev.filter((_, idx) => idx !== i));

  const updateDocType = (i, field, value) =>
    setDocTypes((prev) => prev.map((d, idx) => idx === i ? { ...d, [field]: value } : d));

  const buildDocTypesPayload = () =>
    docTypes
      .filter((d) => d.id.trim())
      .map((d) => ({
        id: d.id.trim().toUpperCase().replace(/\s+/g, "_"),
        description: d.description.trim(),
        keywords: d.keywordsRaw.split(",").map((k) => k.trim()).filter(Boolean),
      }));

  // ── File handlers ──────────────────────────────────────
  const handleFileSelect = (e) => {
    setNewFiles((prev) => [...prev, ...Array.from(e.target.files)]);
  };

  const markFileRemoved = (fileId) =>
    setRemovedFileIds((prev) => [...prev, fileId]);

  const unmarkFileRemoved = (fileId) =>
    setRemovedFileIds((prev) => prev.filter((id) => id !== fileId));

  const removeNewFile = (index) =>
    setNewFiles((prev) => prev.filter((_, i) => i !== index));

  // ── Save Files ─────────────────────────────────────────
  const handleSaveFiles = async () => {
    setFileSaving(true);
    const toastId = toast.loading("Saving files...");
    try {
      await Promise.all(
        removedFileIds.map((fileId) => apiSvc.delete(`v1/agent-file/${fileId}`))
      );
      if (newFiles.length > 0) {
        const fd = new FormData();
        newFiles.forEach((f) => fd.append("files", f));
        await apiSvc.post(`v1/agent-file/${agent.name}`, fd, {
          headers: { "Content-Type": "multipart/form-data" },
        });
      }
      setExistingFiles((prev) => prev.filter((f) => !removedFileIds.includes(f.id)));
      setRemovedFileIds([]);
      setNewFiles([]);
      toast.success("Files saved successfully.", { id: toastId });
    } catch (e) {
      console.error("File save failed", e);
      toast.error(extractApiError(e), { id: toastId });
    } finally {
      setFileSaving(false);
    }
  };

  // ── Validation ─────────────────────────────────────────
  const validate = () => {
    const errs = {};
    if (!form.model) errs.model = "Model is required";
    if (!form.type) errs.type = "Type is required";
    if (!form.instructions?.trim()) errs.instructions = "Instructions are required";
    if (!form.description?.trim()) errs.description = "Description is required";
    if (form.temperature < 0 || form.temperature > 2)
      errs.temperature = "Must be between 0 and 2";
    if (form.maxTokens !== "" && (isNaN(form.maxTokens) || Number(form.maxTokens) < 1))
      errs.maxTokens = "Must be a positive number";
    setErrors(errs);
    return Object.keys(errs).length === 0;
  };

  // ── Submit ─────────────────────────────────────────────
  const handleSubmit = async () => {
    if (!validate()) {
      toast.error("Please fix the validation errors before saving.");
      return;
    }
    setSaving(true);
    try {
      const payload = {
        ...form,
        maxTokens: form.maxTokens === "" ? null : Number(form.maxTokens),
        temperature: Number(form.temperature),
        ...(isClassification ? { documentTypeDefinitions: JSON.stringify(buildDocTypesPayload()) } : {}),
        ...(isRag ? typeFieldValues : {}),
      };
      await onSave(payload, newFiles, isEdit ? agent.name : null);
      onClose();
    } catch (e) {
      console.error("Save failed", e);
      toast.error(extractApiError(e));
    } finally {
      setSaving(false);
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

  if (!open) return null;

  const visibleExistingFiles = existingFiles.filter((f) => !removedFileIds.includes(f.id));
  const removedFiles = existingFiles.filter((f) => removedFileIds.includes(f.id));

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center p-4"
      onClick={(e) => { if (e.target === e.currentTarget) onClose(); }}
    >
      <div className="absolute inset-0" style={{ backgroundColor: "rgba(0,0,0,0.6)", backdropFilter: "blur(4px)" }} />

      <div
        className="relative z-10 w-full max-w-2xl max-h-[90vh] overflow-y-scroll scrollbar-thin scrollbar-track-transparent scrollbar-thumb-sky-400/30 hover:scrollbar-thumb-sky-400/50 rounded-xl shadow-2xl"
        style={{ backgroundColor: "var(--bg-secondary)", border: "1px solid var(--border)" }}
      >

        {/* Header */}
        <div
          className="sticky top-0 z-10 flex items-center justify-between px-6 py-5"
          style={{ borderBottom: "1px solid var(--border)", backgroundColor: "var(--bg-secondary)" }}
        >
          <div>
            <p className="text-xs tracking-[0.25em] text-sky-500 uppercase mb-1">
              ◆ {isEdit ? "Edit Agent" : "New Agent"}
            </p>
            <h2 className="text-lg font-black tracking-tight" style={{ color: "var(--text-primary)" }}>
              {isEdit ? agent.name : "Configure Agent"}
              {isEdit && <button
                onClick={() => copyAgentName(agent.name)}
                title="Copy Agent Name"
                className="p-1 rounded hover:bg-sky-400/10 transition-colors"
              >
                <svg
                  xmlns="http://www.w3.org/2000/svg"
                  width="12"
                  height="12"
                  viewBox="0 0 24 24"
                  fill="none"
                  stroke="currentColor"
                  strokeWidth="2"
                  style={{ color: "var(--text-faint)" }}
                  className="hover:text-sky-500"
                >
                  <rect x="9" y="9" width="13" height="13" rx="2" ry="2" />
                  <path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1" />
                </svg>
              </button>}
            </h2>
          </div>
          <button
            onClick={onClose}
            className="transition-colors text-xl leading-none hover:text-sky-500"
            style={{ color: "var(--text-faint)" }}
          >
            ✕
          </button>
        </div>

        {/* Body */}
        <div className="px-6 py-6 space-y-6">

          {/* Name */}
          {isEdit && <Field label="Agent Name">
            <input
              type="text"
              value={form.name}
              onChange={(e) => set("name", e.target.value)}
              placeholder="e.g. my-rag-agent"
              className={`input-theme ${inputCls(errors.name)}`}
              readOnly={isEdit}
            />
          </Field>}

          {/* Source */}
          <Field label="Source" required>
            <div className="flex gap-3">
              {SOURCES.map((s) => (
                <button
                  key={s}
                  onClick={() => handleSourceChange(s)}
                  className="flex-1 py-2.5 text-xs uppercase tracking-widest rounded transition-all duration-200"
                  style={form.source === s
                    ? { backgroundColor: "rgba(56,189,248,0.1)", border: "1px solid rgba(56,189,248,0.4)", color: "rgb(56,189,248)" }
                    : { border: "1px solid var(--border)", color: "var(--text-muted)" }
                  }
                >
                  {s}
                </button>
              ))}
            </div>
          </Field>

          {/* Model */}
          <Field
            label="Model"
            error={errors.model}
            required
            hint={modelsLoading ? "Loading..." : `${availableModels.length} available`}
          >
            <select
              value={form.model}
              onChange={(e) => handleModelChange(e.target.value)}
              disabled={modelsLoading || availableModels.length === 0}
              className={`input-theme ${inputCls(errors.model)} disabled:opacity-40`}
            >
              <option value="">
                {modelsLoading ? "Loading models..." : "Select a model"}
              </option>
              {availableModels.map((m) => (
                <option key={m.id} value={m.id}>
                  {m.id}
                </option>
              ))}
            </select>
            {selectedModelData && (
              <div className="mt-2 grid grid-cols-3 gap-2">
                {[
                  {
                    label: "Context",
                    value: selectedModelData.context
                      ? `${(selectedModelData.context / 1000).toFixed(0)}k`
                      : "—",
                  },
                  { label: "Provider", value: selectedModelData.provider ?? "local" },
                  { label: "Port", value: selectedModelData.port ?? "—" },
                ].map(({ label, value }) => (
                  <div key={label} className="rounded px-3 py-2" style={{ backgroundColor: "var(--bg-subtle)" }}>
                    <div className="text-xs uppercase mb-0.5" style={{ color: "var(--text-faint)" }}>{label}</div>
                    <div className="text-xs font-bold" style={{ color: "var(--text-muted)" }}>{value}</div>
                  </div>
                ))}
              </div>
            )}
          </Field>

          {/* Type */}
          {form.model && (
            <Field label="Type" error={errors.type} required hint="Based on selected model">
              <div className="flex gap-2 flex-wrap">
                {availableTypes.map((t) => (
                  <button
                    key={t}
                    onClick={() => {
                      set("type", t);
                      setTypeFieldValues(t === "rag" ? getDefaultFieldValues("rag") : {});
                    }}
                    className="px-4 py-2 text-xs uppercase tracking-wider rounded transition-all duration-200"
                    style={form.type === t
                      ? { backgroundColor: "rgba(56,189,248,0.1)", border: "1px solid rgba(56,189,248,0.4)", color: "rgb(56,189,248)" }
                      : { border: "1px solid var(--border)", color: "var(--text-muted)" }
                    }
                  >
                    {t}
                  </button>
                ))}
              </div>
              {errors.type && <p className="text-xs text-red-400 mt-1">{errors.type}</p>}
            </Field>
          )}
          {isVision && (
            <Field
              label="Page Chunk"
              error={errors.pageChunk}
              required
              hint="Maximum value is 4"
            >
              <input
                type="text"
                inputMode="numeric"
                pattern="[0-9]*"
                value={form.pageChunk}
                onChange={(e) => {
                  const value = e.target.value;

                  if (/^[1-4]?$/.test(value)) {
                    set("pageChunk", value);
                  }
                }}
                className={`input-theme ${inputCls(errors.pageChunk)}`}
              />
            </Field>
          )}

          {/* RAG config block — same fields as the Playground's RAG panel */}
          {isRag && (
            <DynamicTypeFields
              fields={ragConfig.fields}
              values={typeFieldValues}
              onChange={(key, value) => setTypeFieldValues((prev) => ({ ...prev, [key]: value }))}
            />
          )}

          {/* Classification config block — mode + document types */}
          {isClassification && (
            <div className="rounded-lg border border-violet-400/20 bg-violet-400/[0.03] p-4 space-y-4">
              <p className="text-xs text-violet-400/70 uppercase tracking-widest">Classification Config</p>

              {/* Mode */}
              <div>
                <div className="text-xs uppercase tracking-wider mb-2" style={{ color: "var(--text-faint)" }}>Mode</div>
                <div className="flex gap-2">
                  {CLASSIFY_MODES.map((m) => (
                    <button
                      key={m}
                      onClick={() => set("classificationMode", m)}
                      className="flex-1 py-2 text-xs uppercase tracking-wider rounded transition-all duration-200"
                      style={form.classificationMode === m
                        ? { backgroundColor: "rgba(167,139,250,0.15)", border: "1px solid rgba(167,139,250,0.4)", color: "rgb(167,139,250)", fontWeight: 700 }
                        : { border: "1px solid var(--border)", color: "var(--text-muted)" }
                      }
                    >
                      {m}
                    </button>
                  ))}
                </div>
                <p className="text-xs mt-1.5" style={{ color: "var(--text-faint)" }}>
                  {form.classificationMode === "single" && "Classify entire document as one type"}
                  {form.classificationMode === "page" && "Classify each page independently"}
                  {form.classificationMode === "auto" && "Auto-detect best classification strategy"}
                </p>
              </div>

              <div style={{ borderTop: "1px solid var(--border)" }} />

              {/* Document Types */}
              <div>
                <div className="flex items-center justify-between mb-2">
                  <div className="text-xs uppercase tracking-wider" style={{ color: "var(--text-faint)" }}>Document Types</div>
                  <button
                    onClick={() => setDocTypes(defaultDocTypes())}
                    className="text-xs hover:text-sky-500 transition-colors"
                    style={{ color: "var(--text-faint)" }}
                    title="Reset to defaults"
                  >
                    ↺ Reset
                  </button>
                </div>

                <div className="space-y-3 max-h-72 overflow-y-auto pr-0.5">
                  {docTypes.map((dt, i) => (
                    <div
                      key={i}
                      className="rounded-lg p-3 space-y-2"
                      style={{ backgroundColor: "var(--bg-subtle)", border: "1px solid var(--border)" }}
                    >
                      {/* Header row */}
                      <div className="flex items-center gap-2">
                        <span className="w-5 h-5 rounded bg-violet-400/15 flex items-center justify-center text-violet-400 text-xs font-bold flex-shrink-0">
                          {i + 1}
                        </span>
                        <input
                          type="text"
                          value={dt.id}
                          onChange={(e) => updateDocType(i, "id", e.target.value)}
                          placeholder="TYPE_ID"
                          className={`input-theme ${inputCls(null)} flex-1 min-w-0 py-1 text-xs uppercase`}
                        />
                        <button
                          onClick={() => removeDocType(i)}
                          disabled={docTypes.length === 1}
                          className="flex-shrink-0 hover:text-red-400 transition-colors disabled:opacity-30 disabled:cursor-not-allowed text-sm leading-none"
                          style={{ color: "var(--text-faint)" }}
                        >
                          ✕
                        </button>
                      </div>
                      {/* Description */}
                      <textarea
                        value={dt.description}
                        onChange={(e) => updateDocType(i, "description", e.target.value)}
                        placeholder="Description of this document type..."
                        rows={2}
                        className={`input-theme ${inputCls(null)} py-1 text-xs resize-none`}
                      />
                      {/* Keywords */}
                      <div>
                        <input
                          type="text"
                          value={dt.keywordsRaw}
                          onChange={(e) => updateDocType(i, "keywordsRaw", e.target.value)}
                          placeholder="keyword1, keyword2, keyword3"
                          className={`input-theme ${inputCls(null)} py-1 text-xs`}
                        />
                        <p className="text-xs mt-0.5" style={{ color: "var(--text-faint)" }}>Comma-separated keywords</p>
                      </div>
                    </div>
                  ))}
                </div>

                <button
                  onClick={addDocType}
                  className="mt-2.5 w-full py-2 text-xs border border-dashed border-violet-400/20 text-violet-400/60 rounded-lg hover:border-violet-400/40 hover:text-violet-400 hover:bg-violet-400/5 transition-all duration-200"
                >
                  + Add Document Type
                </button>
              </div>
            </div>
          )}

          {/* Instructions */}
          <Field label="Instructions" error={errors.instructions} required hint="System prompt">
            <textarea
              value={form.instructions}
              onChange={(e) => set("instructions", e.target.value)}
              placeholder="You are a helpful agent that..."
              rows={4}
              className={`${inputCls(errors.instructions)}`}
              style={{ border: `1px solid ${errors.instructions ? "rgba(248,113,113,0.4)" : "var(--border)"}` }}

            />
          </Field>
          {/* Description */}
          <Field label="Description" error={errors.description} required hint="Agent Description">
            <textarea
              value={form.description}
              onChange={(e) => set("description", e.target.value)}
              placeholder="Describe Agent..."
              rows={2}
              className={`${inputCls(errors.description)} resize-none`}
              style={{ border: `1px solid ${errors.description ? "rgba(248,113,113,0.4)" : "var(--border)"}` }}
            />
          </Field>

          {/* Temperature + Max Tokens */}
          <div className="grid grid-cols-2 gap-4">
            <Field label={`Temperature — ${form.temperature}`} error={errors.temperature}>
              <input
                type="range" min="0" max="2" step="0.1"
                value={form.temperature}
                onChange={(e) => set("temperature", parseFloat(e.target.value))}
                className="w-full accent-sky-400 mt-1"
              />
              <div className="flex justify-between text-xs mt-1" style={{ color: "var(--text-faint)" }}>
                <span>Precise</span><span>Creative</span>
              </div>
            </Field>
            <Field label="Max Tokens" error={errors.maxTokens} hint="Leave empty for default">
              <input
                type="number" value={form.maxTokens}
                onChange={(e) => set("maxTokens", e.target.value)}
                placeholder="e.g. 2048" min={1}
                className={inputCls(errors.maxTokens)}
              />
            </Field>
          </div>

          {/* Toggles */}
          <Toggle
            label="Private Agent" hint="Only visible to you"
            value={form.isPrivate} onChange={(v) => set("isPrivate", v)}
          />
          <Toggle
            label="Active Agent" hint="Enable or disable this agent"
            value={form.active} onChange={(v) => set("active", v)}
          />

          {/* ── Files Section ─────────────────────────────── */}
          <div
            className="rounded-lg transition-all duration-200"
            style={{ border: `1px solid ${isEdit && hasFileChanges ? "rgba(56,189,248,0.2)" : "var(--border)"}`, backgroundColor: isEdit && hasFileChanges ? "rgba(56,189,248,0.03)" : "transparent" }}
          >
            <div className="flex items-center justify-between px-4 pt-4 pb-3" style={{ borderBottom: "1px solid var(--border)" }}>
              <div>
                <span className="text-xs uppercase tracking-wider" style={{ color: "var(--text-muted)" }}>Attached Files</span>
                <span className="text-xs ml-2" style={{ color: "var(--text-faint)" }}>PDF files for RAG</span>
              </div>
              {isEdit && hasFileChanges && (
                <button
                  onClick={handleSaveFiles}
                  disabled={fileSaving}
                  className="flex items-center gap-2 text-xs px-4 py-1.5 rounded uppercase tracking-wider transition-all duration-200 disabled:opacity-50"
                  style={{ backgroundColor: "rgba(56,189,248,0.1)", border: "1px solid rgba(56,189,248,0.3)", color: "rgb(56,189,248)" }}
                >
                  {fileSaving ? (
                    <>
                      <span className="w-3 h-3 border border-sky-400/50 border-t-sky-400 rounded-full animate-spin" />
                      Saving...
                    </>
                  ) : "Save Files"}
                </button>
              )}
            </div>

            <div className="p-4 space-y-2">
              {visibleExistingFiles.map((f) => (
                <div key={f.id} className="flex items-center justify-between rounded px-3 py-2" style={{ backgroundColor: "var(--bg-subtle)", border: "1px solid var(--border)" }}>
                  <div className="flex items-center gap-2">
                    <span className="text-xs text-sky-500">📄</span>
                    <span className="text-xs" style={{ color: "var(--text-muted)" }}>{f.fileName}</span>
                    {f.metadata?.size && (
                      <span className="text-xs" style={{ color: "var(--text-faint)" }}>{(f.metadata.size / 1024).toFixed(1)} KB</span>
                    )}
                  </div>
                  {isEdit ? (
                    <button onClick={() => markFileRemoved(f.id)}
                      className="text-xs hover:text-red-400 transition-colors px-2 py-0.5 rounded hover:bg-red-400/10" style={{ color: "var(--text-faint)" }}>
                      Remove
                    </button>
                  ) : (
                    <button onClick={() => setExistingFiles((prev) => prev.filter((x) => x.id !== f.id))}
                      className="hover:text-red-400 transition-colors text-xs" style={{ color: "var(--text-faint)" }}>✕</button>
                  )}
                </div>
              ))}

              {isEdit && removedFiles.map((f) => (
                <div key={f.id} className="flex items-center justify-between bg-red-400/5 border border-red-400/15 rounded px-3 py-2">
                  <div className="flex items-center gap-2">
                    <span className="text-xs text-red-400/50">🗑</span>
                    <span className="text-xs line-through" style={{ color: "var(--text-faint)" }}>{f.fileName}</span>
                    <span className="text-xs text-red-400/50 uppercase tracking-wider">will be removed</span>
                  </div>
                  <button onClick={() => unmarkFileRemoved(f.id)}
                    className="text-xs hover:text-sky-500 transition-colors px-2 py-0.5 rounded hover:bg-sky-400/10" style={{ color: "var(--text-faint)" }}>
                    Undo
                  </button>
                </div>
              ))}

              {newFiles.map((f, i) => (
                <div key={i} className="flex items-center justify-between rounded px-3 py-2" style={{ backgroundColor: "rgba(56,189,248,0.05)", border: "1px solid rgba(56,189,248,0.2)" }}>
                  <div className="flex items-center gap-2">
                    <span className="text-xs text-sky-500">📎</span>
                    <span className="text-xs" style={{ color: "var(--text-muted)" }}>{f.name}</span>
                    <span className="text-xs" style={{ color: "var(--text-faint)" }}>{(f.size / 1024).toFixed(1)} KB</span>
                    {isEdit && <span className="text-xs text-sky-500/70 uppercase tracking-wider">pending upload</span>}
                  </div>
                  <button onClick={() => removeNewFile(i)}
                    className="hover:text-red-400 transition-colors text-xs" style={{ color: "var(--text-faint)" }}>✕</button>
                </div>
              ))}

              <button
                onClick={() => fileInputRef.current?.click()}
                className="w-full border border-dashed rounded px-4 py-3 text-xs hover:border-sky-400/30 hover:text-sky-500 transition-all duration-200 text-center"
                style={{ borderColor: "var(--border)", color: "var(--text-faint)" }}
              >
                + Upload PDF Files
              </button>
              <input ref={fileInputRef} type="file" multiple accept=".pdf" onChange={handleFileSelect} className="hidden" />
            </div>
          </div>
        </div>

        {/* Footer */}
        <div
          className="sticky bottom-0 flex items-center justify-between px-6 py-4"
          style={{ borderTop: "1px solid var(--border)", backgroundColor: "var(--bg-secondary)" }}
        >
          <Button variant="ghost" onClick={onClose}>Cancel</Button>
          <Button variant="primary" onClick={handleSubmit} disabled={saving}>
            {saving ? (
              <span className="flex items-center gap-2">
                <span className="w-3 h-3 border-2 border-black/20 border-t-black/70 rounded-full animate-spin" />
                {isEdit ? "Saving..." : "Creating..."}
              </span>
            ) : isEdit ? "Save Changes" : "Create Agent"}
          </Button>
        </div>
      </div>
    </div>
  );
}

// ── Sub-components ────────────────────────────────────────

function Field({ label, error, hint, required, children }) {
  return (
    <div>
      <div className="flex items-center justify-between mb-1.5">
        <label className="text-xs uppercase tracking-wider" style={{ color: "var(--text-muted)" }}>
          {label}{required && <span className="text-sky-500 ml-0.5">*</span>}
        </label>
        {hint && <span className="text-xs" style={{ color: "var(--text-faint)" }}>{hint}</span>}
      </div>
      {children}
      {error && <p className="text-xs text-red-400 mt-1">{error}</p>}
    </div>
  );
}

function Toggle({ label, hint, value, onChange }) {
  return (
    <div className="flex items-center justify-between rounded px-4 py-3" style={{ backgroundColor: "var(--bg-subtle)", border: "1px solid var(--border)" }}>
      <div>
        <div className="text-xs uppercase tracking-wider" style={{ color: "var(--text-muted)" }}>{label}</div>
        {hint && <div className="text-xs mt-0.5" style={{ color: "var(--text-faint)" }}>{hint}</div>}
      </div>
      <button
        onClick={() => onChange(!value)}
        className={`w-10 h-5 rounded-full transition-all duration-300 relative flex-shrink-0 ${value ? "bg-sky-400" : ""}`}
        style={value ? {} : { backgroundColor: "var(--border)" }}
      >
        <span className={`absolute top-0.5 w-4 h-4 bg-white rounded-full shadow transition-all duration-300 ${value ? "left-5" : "left-0.5"}`} />
      </button>
    </div>
  );
}

const inputCls = (error) =>
  `w-full rounded px-4 py-2.5 text-sm outline-none transition-colors font-mono ${error ? "border-red-400/40 focus:border-red-400/60" : ""
  }`;
