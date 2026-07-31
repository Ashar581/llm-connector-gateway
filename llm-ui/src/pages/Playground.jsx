import { useState, useEffect, useRef } from "react";
import ReactMarkdown from "react-markdown";
import { Prism as SyntaxHighlighter } from "react-syntax-highlighter";
import { oneDark } from "react-syntax-highlighter/dist/esm/styles/prism";
import apiSvc from "../services/apiService";
import toast from "react-hot-toast";
import { streamAsk, streamFileAsk } from "../services/streamService";
import { sendMessage, sendFileRequest, sendEmbedding } from "../services/chatService";
import { createAgent, createNewAgent, getModels } from "../services/agentService";
import InstructionsModal from "../components/InstructionsModal";
import DynamicTypeFields from "../components/DynamicTypeFields";
import ClassificationConfigPanel, { DEFAULT_DOC_TYPES, withKeywordsRaw, buildDocTypesPayload as buildDocTypesPayloadFor } from "../components/Classificationconfigpanel";
import { getTypeConfig, getDefaultFieldValues, appendFieldValuesToFormData } from "../configs/Typefieldconfigs";


const SOURCES = ["free", "paid"];

// ── Markdown components — use CSS vars for theme-aware colors ─
const markdownComponents = {
  code({ node, inline, className, children, ...props }) {
    const match = /language-(\w+)/.exec(className || "");
    const language = match ? match[1] : "";
    const codeString = String(children).replace(/\n$/, "");
    if (!inline && language) return <CodeBlock language={language} code={codeString} />;
    return (
      <code
        className="px-1.5 py-0.5 rounded text-xs font-mono text-amber-500"
        style={{ backgroundColor: "var(--bg-subtle)" }}
        {...props}
      >
        {children}
      </code>
    );
  },
  h1: ({ children }) => <h1 className="text-lg font-black mt-4 mb-2 pb-1" style={{ color: "var(--text-primary)", borderBottom: "1px solid var(--border)" }}>{children}</h1>,
  h2: ({ children }) => <h2 className="text-base font-bold mt-4 mb-2" style={{ color: "var(--text-primary)" }}>{children}</h2>,
  h3: ({ children }) => <h3 className="text-sm font-bold mt-3 mb-1" style={{ color: "var(--text-secondary)" }}>{children}</h3>,
  p: ({ children }) => <p className="text-sm leading-relaxed mb-2 last:mb-0" style={{ color: "var(--text-secondary)" }}>{children}</p>,
  ul: ({ children }) => <ul className="list-disc list-outside pl-5 space-y-1 my-2 text-sm" style={{ color: "var(--text-secondary)" }}>{children}</ul>,
  ol: ({ children }) => <ol className="list-decimal list-outside pl-5 space-y-1 my-2 text-sm" style={{ color: "var(--text-secondary)" }}>{children}</ol>,
  li: ({ children }) => <li className="leading-relaxed">{children}</li>,
  blockquote: ({ children }) => <blockquote className="border-l-2 border-amber-400/40 pl-4 my-2 italic text-sm" style={{ color: "var(--text-muted)" }}>{children}</blockquote>,
  strong: ({ children }) => <strong className="font-bold" style={{ color: "var(--text-primary)" }}>{children}</strong>,
  em: ({ children }) => <em className="italic" style={{ color: "var(--text-secondary)" }}>{children}</em>,
  hr: () => <hr className="my-4" style={{ borderColor: "var(--border)" }} />,
  a: ({ href, children }) => <a href={href} target="_blank" rel="noopener noreferrer" className="text-amber-500 underline underline-offset-2 hover:text-amber-400 transition-colors">{children}</a>,
  table: ({ children }) => <div className="overflow-x-auto my-3"><table className="w-full text-xs rounded" style={{ border: "1px solid var(--border)" }}>{children}</table></div>,
  thead: ({ children }) => <thead className="uppercase tracking-wider text-xs" style={{ backgroundColor: "var(--bg-subtle)", color: "var(--text-muted)" }}>{children}</thead>,
  tbody: ({ children }) => <tbody>{children}</tbody>,
  tr: ({ children }) => <tr style={{ borderTop: "1px solid var(--border)" }}>{children}</tr>,
  th: ({ children }) => <th className="px-3 py-2 text-left font-bold">{children}</th>,
  td: ({ children }) => <td className="px-3 py-2" style={{ color: "var(--text-muted)" }}>{children}</td>,
};

// ── Code block with copy button ───────────────────────────
function CodeBlock({ language, code }) {
  const [copied, setCopied] = useState(false);
  const handleCopy = () => {
    navigator.clipboard.writeText(code);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };
  return (
    <div className="my-3 rounded-lg overflow-hidden" style={{ border: "1px solid var(--border)" }}>
      <div className="flex items-center justify-between px-4 py-2" style={{ backgroundColor: "var(--bg-subtle)", borderBottom: "1px solid var(--border)" }}>
        <span className="text-xs uppercase tracking-wider font-mono" style={{ color: "var(--text-faint)" }}>{language}</span>
        <button
          onClick={handleCopy}
          className={`text-xs uppercase tracking-wider transition-colors ${copied ? "text-emerald-400" : "hover:text-amber-400"}`}
          style={copied ? {} : { color: "var(--text-faint)" }}
        >
          {copied ? "✓ Copied" : "Copy"}
        </button>
      </div>
      <SyntaxHighlighter
        language={language}
        style={oneDark}
        customStyle={{ margin: 0, padding: "1rem", background: "rgba(0,0,0,0.35)", fontSize: "0.75rem", lineHeight: "1.6" }}
        showLineNumbers
        lineNumberStyle={{ color: "rgba(255,255,255,0.15)", minWidth: "2rem" }}
      >
        {code}
      </SyntaxHighlighter>
    </div>
  );
}


// ── Main component ────────────────────────────────────────
export default function Playground() {
  const [loaded, setLoaded] = useState(false);
  const [modelMap, setModelMap] = useState({ free: [], paid: [] });
  const [modelsLoading, setModelsLoading] = useState(true);

  const [source, setSource] = useState("free");
  const [selectedModel, setSelectedModel] = useState(null);
  const [selectedType, setSelectedType] = useState("");
  const [instructions, setInstructions] = useState("");
  const [typeFieldValues, setTypeFieldValues] = useState(() => getDefaultFieldValues(""));
  const [temperature, setTemperature] = useState(0.7);
  const [maxToken, setMaxToken] = useState(null);
  const [stream, setStream] = useState(false);
  const [history, setHistory] = useState(false);

  const [messages, setMessages] = useState([]);
  const [input, setInput] = useState("");
  const [loading, setLoading] = useState(false);

  const [showCreateModal, setShowCreateModal] = useState(false);
  const [agentDescription, setAgentDescription] = useState("");
  const [creating, setCreating] = useState(false);

  const [showInstructionsModal, setShowInstructionsModal] = useState(false);

  const [attachedFiles, setAttachedFiles] = useState([]);
  const [dragActive, setDragActive] = useState(false);
  const [classifyMode, setClassifyMode] = useState("AUTO");
  const [docTypes, setDocTypes] = useState(() => withKeywordsRaw(DEFAULT_DOC_TYPES));

  const [agentFiles, setAgentFiles] = useState([]);
  const agentFileInputRef = useRef(null);

  const bottomRef = useRef(null);
  const inputRef = useRef(null);
  const abortRef = useRef(null);
  const fileInputRef = useRef(null);

  const availableModels = modelMap[source] ?? [];
  const availableTypes = selectedModel?.type ?? [];
  const typeConfig = getTypeConfig(selectedType, modelMap);
  const isFileType = typeConfig.isFileType;
  const isClassification = selectedType === "classification";
  const isEmbedding = selectedType === 'embedding';
  // File types that ALSO support streaming (currently only RAG). Vision and
  // classification are file types but never stream, per typeConfig.supportsStream.
  const supportsFileStream = isFileType && Boolean(typeConfig.supportsStream);
  const canStream = !isFileType || supportsFileStream;
  const buildDocTypesPayload = () => buildDocTypesPayloadFor(docTypes);

  useEffect(() => {
    setTimeout(() => setLoaded(true), 80);
    const controller = new AbortController();
    const fetchModels = async () => {
      try {
        const res = await getModels();
        console.log(res);
        const data = res;
        setModelMap({
          free: (data.free?.models ?? []).filter(model => model.active === true),
          paid: (data.paid?.models ?? []).filter(model => model.active === true)
        });
      } catch (e) {
        if (e.name === "AbortError") return;
        console.error("Failed to fetch models", e);
      } finally {
        setModelsLoading(false);
      }
    };
    fetchModels();
    return () => controller.abort();
  }, []);

  useEffect(() => { bottomRef.current?.scrollIntoView({ behavior: "smooth" }); }, [messages, loading]);
  useEffect(() => { return () => abortRef.current?.abort(); }, []);
  useEffect(() => { setTypeFieldValues(getDefaultFieldValues(selectedType)); }, [selectedType]);
  useEffect(() => { if (!isFileType) setAttachedFiles([]); }, [selectedType]);
  useEffect(() => { if (!isFileType || loading) setDragActive(false); }, [isFileType, loading]);
  useEffect(() => {
    if (isEmbedding) {
      setStream(false);
    }
  }, [isEmbedding]);
  useEffect(() => {
    if (isFileType && !typeConfig.supportsStream) setStream(false);
  }, [isFileType, typeConfig.supportsStream]);

  const handleAgentFileSelect = (e) => {
    const files = Array.from(e.target.files || []);
    setAgentFiles(files);
    e.target.value = "";
  };

  const removeAgentFile = (index) => {
    setAgentFiles((prev) => prev.filter((_, i) => i !== index));
  };

  const handleCreateAgent = async () => {
    if (!instructions.trim()) { toast.error("Instructions are mandatory"); return; }
    if (!agentDescription.trim()) { toast.error("Agent Description is mandatory"); return; }
    setCreating(true);
    try {
      const payload = {
        description: agentDescription.trim(),
        model: selectedModel.id,
        source,
        type: selectedType,
        ...typeFieldValues,
        temperature,
        maxTokens: maxToken,
        ...(instructions.trim() && { instructions: instructions.trim() }),
        ...(isClassification && { mode: classifyMode, documentTypes: buildDocTypesPayload() }),
        active: true
      };
      let response;
      if (selectedType === 'rag') {
        response = await createNewAgent(payload, agentFiles);
      } else {
        response = await createAgent(payload);
      }

      if (response.status) {
        setShowCreateModal(false);
        setAgentDescription("");
        toast.success("Agent Created Successfully");
      } else {
        toast.error(response.message);
      }
    } catch (error) {
      console.error("Failed to create agent", error);
      toast.error(error?.response?.data?.message || "Failed to create agent");
    } finally { setCreating(false); }
  };

  const handleSourceChange = (s) => { setSource(s); setSelectedModel(null); setSelectedType(""); setMessages([]); setAttachedFiles([]); };
  const handleModelSelect = (model) => {
    setSelectedModel(model);
    setSelectedType(model.type?.length === 1 ? model.type[0] : "");
    setMessages([]); setInput(""); setAttachedFiles([]);
    setTimeout(() => inputRef.current?.focus(), 100);
  };

  const addFiles = (files) => {
    if (!files.length) return;
    const acceptedFiles = typeConfig.fileValidator ? files.filter(typeConfig.fileValidator) : files;
    if (acceptedFiles.length !== files.length) toast.error(typeConfig.fileRejectMessage || "Some files were rejected for this type");
    if (!acceptedFiles.length) return;
    setAttachedFiles((prev) => [...prev, ...acceptedFiles]);
  };
  const handleFileSelect = (e) => { addFiles(Array.from(e.target.files)); e.target.value = ""; };
  const removeFile = (index) => setAttachedFiles((prev) => prev.filter((_, i) => i !== index));

  const canDropFiles = isFileType && !loading;
  const handleDragEnter = (e) => { if (!canDropFiles) return; e.preventDefault(); e.stopPropagation(); if (Array.from(e.dataTransfer?.types ?? []).includes("Files")) setDragActive(true); };
  const handleDragOver = (e) => { if (!canDropFiles) return; e.preventDefault(); e.stopPropagation(); e.dataTransfer.dropEffect = "copy"; setDragActive(true); };
  const handleDragLeave = (e) => { if (!canDropFiles) return; e.preventDefault(); e.stopPropagation(); if (!e.currentTarget.contains(e.relatedTarget)) setDragActive(false); };
  const handleDrop = (e) => { if (!canDropFiles) return; e.preventDefault(); e.stopPropagation(); setDragActive(false); addFiles(Array.from(e.dataTransfer.files ?? [])); };

  const addDocType = () => setDocTypes((prev) => [...prev, { id: "", description: "", keywordsRaw: "" }]);
  const removeDocType = (i) => setDocTypes((prev) => prev.filter((_, idx) => idx !== i));
  const updateDocType = (i, field, value) => setDocTypes((prev) => prev.map((d, idx) => idx === i ? { ...d, [field]: value } : d));

  const buildFormData = () => {
    const chatHistory = messages.map((msg) => ({
      role: msg.role === "user" ? "USER" : "ASSISTANT", content: msg.text.replace(
        /\n?📎\s+\d+\s+file[s]?:.*$/m,
        ""
      ).trim()
    }));

    const fd = new FormData();
    fd.append("source", source);
    fd.append("type", selectedType);
    fd.append("model", selectedModel.id);
    fd.append("chatHistoryEnabled", history);
    fd.append('chatHistoryForForm', JSON.stringify(chatHistory));
    fd.append("temprature", temperature);
    // fd.append("maxTokens", maxToken);
    if (instructions.trim()) fd.append("instructions", instructions.trim());
    attachedFiles.forEach((f) => fd.append("files", f));
    appendFieldValuesToFormData(fd, typeFieldValues);
    if (isClassification) {
      fd.append("documentTypes",
        JSON.stringify(buildDocTypesPayload()));
      fd.append("mode", classifyMode);
    }
    if (!isClassification) fd.append("query", input);
    return fd;
  };

  const buildPayload = (query) => {
    const chatHistory = messages.map((msg) => ({ role: msg.role === "user" ? "USER" : "ASSISTANT", content: msg.text }));
    const payload = { model: selectedModel.id, source, type: selectedType, query, temperature, maxTokens: maxToken, chatHistoryEnabled: history, chatHistory, ...typeFieldValues };
    if (instructions.trim()) payload.instructions = instructions.trim();
    return payload;
  };

  const handleFileRequest = async () => {
    const fd = buildFormData();
    const endpoint = typeConfig.endpoint || "/v2/vl";
    try {
      const result = await sendFileRequest(endpoint, fd);
      setMessages((prev) => [...prev, { role: "assistant", text: result.formatted, id: crypto.randomUUID() }]);
    } catch (e) {
      setMessages((prev) => [...prev, { role: "error", text: e?.response?.data?.message || "Something went wrong", id: crypto.randomUUID() }]);
    } finally { setLoading(false); }
  };

  // Streaming counterpart to handleFileRequest — same FormData payload
  // (files + fields), but reads the response as a stream instead of a single
  // JSON blob. Only used when the type is a file type AND supports streaming
  // (currently just RAG); vision/classification keep using handleFileRequest.
  const sendFileStream = async () => {
    const controller = new AbortController();
    abortRef.current = controller;
    const accumulated = { current: "" };
    const assistantId = crypto.randomUUID();
    await new Promise((resolve) => { setMessages((prev) => { resolve(); return [...prev, { role: "assistant", text: "", id: assistantId, streaming: true }]; }); });
    const endpoint = typeConfig.streamEndpoint || typeConfig.endpoint || "/v2/vl";
    try {
      await streamFileAsk({
        endpoint, formData: buildFormData(), signal: controller.signal,
        onChunk: (chunk) => {
          accumulated.current += chunk;
          const snapshot = accumulated.current;
          setMessages((prev) => prev.map((m) => m.id === assistantId ? { ...m, text: snapshot } : m));
        },
      });
      setMessages((prev) => prev.map((m) => m.id === assistantId ? { ...m, streaming: false } : m));
    } catch (e) {
      if (e.name === "AbortError") { setMessages((prev) => prev.map((m) => m.id === assistantId ? { ...m, text: accumulated.current || "Stopped.", streaming: false } : m)); return; }
      setMessages((prev) => prev.map((m) => m.id === assistantId ? { ...m, role: "error", text: "Stream failed. Please try again.", streaming: false } : m));
      console.error("File stream failed", e);
    } finally { setLoading(false); abortRef.current = null; }
  };

  const sendNormal = async (query) => {
    try {
      let answer;
      if (isEmbedding) {
        answer = await sendEmbedding(buildPayload(query));
      } else {
        answer = await sendMessage(buildPayload(query));
      }
      setMessages((prev) => [...prev, { role: "assistant", text: answer, id: crypto.randomUUID() }]);
    } catch (e) {
      setMessages((prev) => [...prev, { role: "error", text: "Something went wrong. Please try again.", id: crypto.randomUUID() }]);
    } finally { setLoading(false); }
  };

  const sendStream = async (query) => {
    const controller = new AbortController();
    abortRef.current = controller;
    const accumulated = { current: "" };
    const assistantId = crypto.randomUUID();
    await new Promise((resolve) => { setMessages((prev) => { resolve(); return [...prev, { role: "assistant", text: "", id: assistantId, streaming: true }]; }); });
    try {
      await streamAsk({
        payload: buildPayload(query), signal: controller.signal,
        onChunk: (chunk) => {
          accumulated.current += chunk;
          const snapshot = accumulated.current;
          setMessages((prev) => prev.map((m) => m.id === assistantId ? { ...m, text: snapshot } : m));
        },
      });
      setMessages((prev) => prev.map((m) => m.id === assistantId ? { ...m, streaming: false } : m));
    } catch (e) {
      if (e.name === "AbortError") { setMessages((prev) => prev.map((m) => m.id === assistantId ? { ...m, text: accumulated.current || "Stopped.", streaming: false } : m)); return; }
      setMessages((prev) => prev.map((m) => m.id === assistantId ? { ...m, role: "error", text: "Stream failed. Please try again.", streaming: false } : m));
      console.error("Stream failed", e);
    } finally { setLoading(false); abortRef.current = null; }
  };

  const canSend = selectedModel && selectedType && !loading && (isFileType ? attachedFiles.length > 0 : input.trim());
  const handleSend = async () => {
    if (!canSend) return;
    const query = input.trim();
    const bubbleText = [query, attachedFiles.length > 0 && `📎 ${attachedFiles.length} file${attachedFiles.length > 1 ? "s" : ""}: ${attachedFiles.map((f) => f.name).join(", ")}`].filter(Boolean).join("\n");
    setMessages((prev) => [...prev, { role: "user", text: bubbleText, id: crypto.randomUUID() }]);
    setInput(""); setLoading(true);
    if (isFileType) {
      if (stream && supportsFileStream) { await sendFileStream(); }
      else { await handleFileRequest(); }
      setAttachedFiles([]);
    }
    else if (stream) { await sendStream(query); }
    else { await sendNormal(query); }
    setTimeout(() => inputRef.current?.focus(), 50);
  };

  const handleStop = () => { abortRef.current?.abort(); setLoading(false); };
  const handleKeyDown = (e) => { if (e.key === "Enter" && !e.shiftKey) { e.preventDefault(); handleSend(); } };
  const isStreaming = loading && stream && canStream;
  const canSaveAsAgent = Boolean(selectedModel && selectedType && instructions.trim());
  const maxTokenLimit = selectedModel?.context / selectedModel?.parallelExecution || null;

  // ── Reusable style helpers ────────────────────────────
  const cardStyle = { backgroundColor: "var(--bg-card)", border: "1px solid var(--border)" };
  const borderStyle = { borderColor: "var(--border)" };
  const inputStyle = { backgroundColor: "var(--bg-input)", border: "1px solid var(--border)", color: "var(--text-secondary)" };

  return (
    <>
      <div className="relative left-1/2 w-[calc(100vw-2rem)] md:w-[calc(100vw-4rem)] -translate-x-1/2">
        {/* Header */}
        <div className={`transition-all duration-700 mb-3 flex flex-col gap-2 sm:flex-row sm:items-end sm:justify-between ${loaded ? "opacity-100 translate-y-0" : "opacity-0 translate-y-4"}`}>
          <div className="text-xs tracking-[0.24em] text-amber-500 uppercase">Testing Ground</div>
          <p className="text-xs sm:text-right" style={{ color: "var(--text-faint)" }}>
            Select a model, tune parameters, and test responses.
          </p>
        </div>

        <div className={`flex flex-col lg:flex-row gap-4 h-auto lg:h-[calc(100vh-155px)] min-h-0 lg:min-h-[640px] transition-all duration-700 delay-100 ${loaded ? "opacity-100 translate-y-0" : "opacity-0 translate-y-4"}`}>

          {/* ── Chat area ──────────────────────────────── */}
          <div
            onDragEnter={handleDragEnter}
            onDragOver={handleDragOver}
            onDragLeave={handleDragLeave}
            onDrop={handleDrop}
            className="relative flex-1 h-[72vh] min-h-[540px] lg:h-auto lg:min-h-0 flex flex-col rounded-xl overflow-hidden min-w-0 transition-colors"
            style={{
              backgroundColor: "var(--bg-card)",
              border: dragActive ? "1px solid rgba(167,139,250,0.7)" : "1px solid var(--border)",
            }}
          >
            {/* Drag overlay */}
            {dragActive && (
              <div className="absolute inset-0 z-20 flex items-center justify-center backdrop-blur-sm pointer-events-none" style={{ backgroundColor: "rgba(139,92,246,0.08)" }}>
                <div className="rounded-xl px-6 py-5 text-center shadow-lg" style={{ border: "1px dashed rgba(167,139,250,0.5)", backgroundColor: "var(--bg-card)" }}>
                  <div className="text-sm font-bold text-violet-500">Drop files to attach</div>
                  <div className="text-xs text-violet-400/70 mt-1">
                    {typeConfig.dropHint || "Drop files to attach"}
                  </div>
                </div>
              </div>
            )}

            {/* Chat header */}
            <div className="flex items-center justify-between px-5 py-3 flex-shrink-0" style={{ borderBottom: "1px solid var(--border)", backgroundColor: "var(--bg-card)" }}>
              <div className="flex items-center gap-2 min-w-0">
                {selectedModel ? (
                  <>
                    <div className="w-1.5 h-1.5 rounded-full bg-emerald-500 shadow-[0_0_6px_rgba(52,211,153,0.5)] flex-shrink-0" />
                    <span className="text-xs font-bold truncate" style={{ color: "var(--text-secondary)" }}>{selectedModel.id}</span>
                    {selectedType && (
                      <>
                        <span className="mx-1 flex-shrink-0" style={{ color: "var(--border-strong)" }}>·</span>
                        <span className={`text-xs flex-shrink-0 ${isFileType ? "text-violet-500" : "text-amber-500"}`}>{selectedType}</span>
                      </>
                    )}
                    <span className="mx-1 flex-shrink-0" style={{ color: "var(--border-strong)" }}>·</span>
                    <span className="text-xs flex-shrink-0" style={{ color: "var(--text-faint)" }}>{source}</span>
                  </>
                ) : (
                  <span className="text-xs" style={{ color: "var(--text-faint)" }}>No model selected</span>
                )}
              </div>
              <div className="flex items-center gap-3 flex-shrink-0 ml-3">
                {messages.length > 0 && !loading && (
                  <button
                    onClick={() => setMessages([])}
                    className="text-xs uppercase tracking-wider transition-colors hover:text-amber-400"
                    style={{ color: "var(--text-faint)" }}
                  >
                    Clear
                  </button>
                )}
                {selectedModel && selectedType && !canSaveAsAgent && (
                  <span className="hidden sm:inline text-xs text-amber-500/70">Add instructions first</span>
                )}
                {selectedModel && selectedType && (
                  <button
                    onClick={() => canSaveAsAgent && setShowCreateModal(true)}
                    disabled={!canSaveAsAgent}
                    title={!canSaveAsAgent ? "Add instructions before saving as agent" : "Save this configuration as an agent"}
                    className="flex items-center gap-1.5 text-xs font-medium px-3 py-1.5 rounded-lg transition-colors"
                    style={canSaveAsAgent
                      ? { border: "1px solid rgba(139,92,246,0.35)", backgroundColor: "rgba(139,92,246,0.08)", color: "rgb(167,139,250)" }
                      : { border: "1px solid var(--border)", backgroundColor: "var(--bg-subtle)", color: "var(--text-faint)", cursor: "not-allowed" }
                    }
                  >
                    <svg xmlns="http://www.w3.org/2000/svg" width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M12 2l3.09 6.26L22 9.27l-5 4.87 1.18 6.88L12 17.77l-6.18 3.25L7 14.14 2 9.27l6.91-1.01L12 2z" /></svg>
                    Save as agent
                  </button>
                )}
              </div>
            </div>

            {/* Messages */}
            <div
              className="flex-1 overflow-y-scroll scrollbar-thin scrollbar-track-transparent scrollbar-thumb-amber-400/30 hover:scrollbar-thumb-amber-400/50 px-5 py-5 space-y-5"
              style={{ backgroundColor: "var(--bg-primary)" }}
            >
              {messages.length === 0 && (
                <div className="h-full flex flex-col items-center justify-center text-center">
                  <div className="w-12 h-12 rounded-xl flex items-center justify-center mb-4" style={{ backgroundColor: "rgba(217,119,6,0.08)", border: "1px solid rgba(217,119,6,0.15)" }}>
                    <span className="text-amber-500 text-xl">◆</span>
                  </div>
                  {selectedModel && selectedType ? (
                    <>
                      <p className="text-sm font-bold mb-1" style={{ color: "var(--text-secondary)" }}>{selectedModel.id}</p>
                      <p className="text-xs uppercase tracking-widest" style={{ color: "var(--text-faint)" }}>
                        {isFileType ? "Attach files to get started" : "Ask anything to start"}
                      </p>
                      {stream && canStream && <p className="text-amber-500/60 text-xs mt-2">⚡ Streaming on</p>}
                      {isFileType && <p className="text-violet-500/60 text-xs mt-2">{typeConfig.icon} {typeConfig.modeLabel || "File mode"}{supportsFileStream && stream ? " · streaming" : ""}</p>}
                      {!isFileType && (
                        <div className="mt-6 flex flex-wrap gap-2 justify-center max-w-sm">
                          {["What can you help me with?", "Write a code example", "Explain a concept"].map((p) => (
                            <button
                              key={p}
                              onClick={() => { setInput(p); inputRef.current?.focus(); }}
                              className="text-xs px-3 py-1.5 rounded-full transition-all duration-200 hover:text-amber-400 hover:border-amber-400/30"
                              style={{ border: "1px solid var(--border)", color: "var(--text-muted)" }}
                            >
                              {p}
                            </button>
                          ))}
                        </div>
                      )}
                    </>
                  ) : (
                    <p className="text-xs uppercase tracking-widest" style={{ color: "var(--text-faint)" }}>
                      {!selectedModel ? "Select a model from the right panel" : "Select a type to continue"}
                    </p>
                  )}
                </div>
              )}

              {messages.map((msg) => (
                <div key={msg.id} className={`flex gap-3 ${msg.role === "user" ? "flex-row-reverse" : "flex-row"}`}>
                  {/* Avatar */}
                  <div
                    className="w-7 h-7 rounded-lg flex items-center justify-center flex-shrink-0 text-xs font-bold mt-0.5"
                    style={
                      msg.role === "user"
                        ? { backgroundColor: "rgba(217,119,6,0.12)", color: "rgb(245,158,11)", border: "1px solid rgba(217,119,6,0.2)" }
                        : msg.role === "error"
                          ? { backgroundColor: "rgba(239,68,68,0.08)", color: "rgb(248,113,113)", border: "1px solid rgba(239,68,68,0.2)" }
                          : { backgroundColor: "var(--bg-subtle)", color: "var(--text-muted)", border: "1px solid var(--border)" }
                    }
                  >
                    {msg.role === "user" ? "U" : msg.role === "error" ? "!" : "A"}
                  </div>
                  {/* Bubble */}
                  <div
                    className={`max-w-[80%] rounded-xl px-4 py-3 text-sm leading-relaxed ${msg.role === "user" ? "rounded-tr-sm whitespace-pre-wrap" : msg.role === "error" ? "rounded-tl-sm whitespace-pre-wrap" : "rounded-tl-sm"}`}
                    style={
                      msg.role === "user"
                        ? { backgroundColor: "rgba(217,119,6,0.08)", border: "1px solid rgba(217,119,6,0.15)", color: "var(--text-primary)" }
                        : msg.role === "error"
                          ? { backgroundColor: "rgba(239,68,68,0.06)", border: "1px solid rgba(239,68,68,0.15)", color: "rgb(248,113,113)" }
                          : { backgroundColor: "var(--bg-card)", border: "1px solid var(--border)", color: "var(--text-primary)" }
                    }
                  >
                    {msg.role === "assistant" ? (
                      <>
                        <ReactMarkdown components={markdownComponents}>{msg.text}</ReactMarkdown>
                        {msg.streaming && <span className="inline-block w-0.5 h-3.5 bg-amber-500 ml-0.5 align-middle animate-pulse" />}
                      </>
                    ) : msg.text}
                  </div>
                </div>
              ))}

              {loading && !isStreaming && (
                <div className="flex gap-3">
                  <div className="w-7 h-7 rounded-lg flex items-center justify-center flex-shrink-0 text-xs font-bold" style={{ backgroundColor: "var(--bg-subtle)", border: "1px solid var(--border)", color: "var(--text-muted)" }}>A</div>
                  <div className="rounded-xl rounded-tl-sm px-4 py-3" style={{ backgroundColor: "var(--bg-card)", border: "1px solid var(--border)" }}>
                    <div className="flex gap-1.5 items-center h-4">
                      {[0, 1, 2].map((i) => (
                        <div key={i} className="w-1.5 h-1.5 bg-amber-400/60 rounded-full animate-bounce" style={{ animationDelay: `${i * 180}ms` }} />
                      ))}
                    </div>
                  </div>
                </div>
              )}
              <div ref={bottomRef} />
            </div>

            {/* Attached files strip */}
            {isFileType && attachedFiles.length > 0 && (
              <div className="flex-shrink-0 px-5 pt-3 flex flex-wrap gap-2" style={{ borderTop: "1px solid var(--border)", backgroundColor: "var(--bg-card)" }}>
                {attachedFiles.map((f, i) => (
                  <div key={i} className="flex items-center gap-1.5 rounded-lg px-2.5 py-1.5 text-xs" style={{ backgroundColor: "rgba(139,92,246,0.08)", border: "1px solid rgba(139,92,246,0.2)" }}>
                    <span className="text-violet-500">{f.type.startsWith("image/") ? "🖼" : "📄"}</span>
                    <span className="max-w-[120px] truncate" style={{ color: "var(--text-secondary)" }}>{f.name}</span>
                    <span style={{ color: "var(--text-faint)" }}>{(f.size / 1024).toFixed(0)}KB</span>
                    <button onClick={() => removeFile(i)} className="ml-0.5 hover:text-red-400 transition-colors" style={{ color: "var(--text-faint)" }}>✕</button>
                  </div>
                ))}
              </div>
            )}

            {/* Input bar */}
            <div className="flex-shrink-0 px-5 py-4" style={{ borderTop: "1px solid var(--border)", backgroundColor: "var(--bg-card)" }}>
              <div className="flex gap-3 items-center">
                {isFileType && (
                  <>
                    <button
                      onClick={() => fileInputRef.current?.click()}
                      disabled={loading}
                      title="Attach files"
                      className="flex-shrink-0 h-11 w-11 rounded-xl flex items-center justify-center transition-all duration-200 disabled:opacity-40 disabled:cursor-not-allowed hover:text-violet-500 hover:border-violet-400/40"
                      style={{ border: "1px solid var(--border)", backgroundColor: "var(--bg-subtle)", color: "var(--text-muted)" }}
                    >
                      <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                        <path d="M21.44 11.05l-9.19 9.19a6 6 0 0 1-8.49-8.49l9.19-9.19a4 4 0 0 1 5.66 5.66l-9.2 9.19a2 2 0 0 1-2.83-2.83l8.49-8.48" />
                      </svg>
                    </button>
                    <input ref={fileInputRef} type="file" multiple accept={typeConfig.accept || "*/*"} onChange={handleFileSelect} className="hidden" />
                  </>
                )}

                <div className="flex-1 relative flex items-center">
                  <textarea
                    ref={inputRef}
                    value={input}
                    onChange={(e) => {
                      setInput(e.target.value);
                      e.target.style.height = "44px";
                      if (e.target.scrollHeight > 44) e.target.style.height = Math.min(e.target.scrollHeight, 120) + "px";
                    }}
                    onKeyDown={handleKeyDown}
                    placeholder={
                      !selectedModel ? "Select a model first..."
                        : !selectedType ? "Select a type first..."
                          : isFileType ? `Optional message for ${selectedType}...`
                            : `Message ${selectedModel.id}...`
                    }
                    rows={1}
                    disabled={loading || !selectedModel || !selectedType}
                    className="input-theme w-full h-11 rounded-xl px-4 py-3 text-sm resize-none leading-relaxed disabled:opacity-50 scrollbar-hide outline-none"
                    style={{ maxHeight: "120px" }}
                  />
                  <span className="absolute right-3 text-xs pointer-events-none" style={{ color: "var(--text-faint)" }}>↵ send</span>
                </div>

                {isStreaming ? (
                  <button
                    onClick={handleStop}
                    className="flex-shrink-0 h-11 w-11 rounded-xl flex items-center justify-center transition-colors hover:bg-red-400/20"
                    style={{ backgroundColor: "rgba(239,68,68,0.08)", border: "1px solid rgba(239,68,68,0.2)" }}
                  >
                    <svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" className="fill-red-400">
                      <rect x="4" y="4" width="16" height="16" rx="2" />
                    </svg>
                  </button>
                ) : (
                  <button
                    onClick={handleSend}
                    disabled={!canSend}
                    className={`flex-shrink-0 h-11 w-11 rounded-xl flex items-center justify-center transition-colors disabled:opacity-30 disabled:cursor-not-allowed ${isFileType ? "bg-violet-500 hover:bg-violet-400" : "bg-amber-500 hover:bg-amber-400"}`}
                  >
                    {loading
                      ? <span className="w-4 h-4 border-2 border-white/30 border-t-white rounded-full animate-spin" />
                      : <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="white" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round"><line x1="22" y1="2" x2="11" y2="13" /><polygon points="22 2 15 22 11 13 2 9 22 2" /></svg>
                    }
                  </button>
                )}
              </div>
              <p className="text-xs mt-2 text-center" style={{ color: "var(--text-faint)" }}>
                Shift+Enter for new line
                {stream && canStream && <span className="text-amber-500/60 ml-2">· ⚡ Streaming</span>}
                {isFileType && <span className="text-violet-500/60 ml-2">· {typeConfig.icon} {typeConfig.modeLabel || "File mode"} · Escape to clear files</span>}
              </p>
            </div>
          </div>

          {/* ── Right sidebar ──────────────────────────── */}
          <div className="w-full lg:w-72 xl:w-80 flex-shrink-0 flex flex-col gap-4 overflow-visible lg:overflow-y-scroll scrollbar-thin scrollbar-track-transparent scrollbar-thumb-amber-400/30 hover:scrollbar-thumb-amber-400/50 bg-transparent">

            {/* Model card */}
            <div className="rounded-xl p-4 space-y-4" style={cardStyle}>
              <div className="text-xs uppercase tracking-widest" style={{ color: "var(--text-faint)" }}>Model</div>
              <div className="flex gap-2">
                {SOURCES.map((s) => (
                  <button
                    key={s}
                    onClick={() => handleSourceChange(s)}
                    className="flex-1 py-2 text-xs uppercase tracking-wider rounded-lg transition-all duration-200"
                    style={source === s
                      ? { backgroundColor: "rgba(217,119,6,0.1)", border: "1px solid rgba(217,119,6,0.3)", color: "rgb(245,158,11)" }
                      : { border: "1px solid var(--border)", color: "var(--text-muted)" }
                    }
                  >
                    {s}
                  </button>
                ))}
              </div>
              {modelsLoading ? (
                <div className="space-y-2">{[1, 2, 3].map((i) => <div key={i} className="h-9 rounded animate-pulse" style={{ backgroundColor: "var(--bg-subtle)" }} />)}</div>
              ) : availableModels.length === 0 ? (
                <p className="text-xs text-center py-4" style={{ color: "var(--text-faint)" }}>No models for {source}</p>
              ) : (
                <div className="space-y-1.5 max-h-52 overflow-y-auto">
                  {availableModels.map((model) => (
                    <button
                      key={model.id}
                      onClick={() => handleModelSelect(model)}
                      className="w-full text-left px-3 py-2.5 rounded-lg transition-all duration-200"
                      style={selectedModel?.id === model.id
                        ? { backgroundColor: "rgba(217,119,6,0.1)", border: "1px solid rgba(217,119,6,0.3)", color: "rgb(245,158,11)" }
                        : { border: "1px solid transparent", color: "var(--text-muted)" }
                      }
                    >
                      <div className="text-xs font-bold truncate">{model.id}</div>
                    </button>
                  ))}
                </div>
              )}
            </div>

            {/* Type selector */}
            {selectedModel && (
              <div className="rounded-xl p-4 space-y-3" style={cardStyle}>
                <div className="text-xs uppercase tracking-widest" style={{ color: "var(--text-faint)" }}>Type</div>
                <div className="flex flex-wrap gap-2">
                  {availableTypes.map((t) => {
                    const tConfig = getTypeConfig(t);
                    return (
                      <button
                        key={t}
                        onClick={() => setSelectedType(t)}
                        className="px-3 py-1.5 text-xs uppercase tracking-wider rounded-lg transition-all duration-200"
                        style={selectedType === t
                          ? tConfig.isFileType
                            ? { backgroundColor: "rgba(139,92,246,0.1)", border: "1px solid rgba(139,92,246,0.3)", color: "rgb(167,139,250)" }
                            : { backgroundColor: "rgba(217,119,6,0.1)", border: "1px solid rgba(217,119,6,0.3)", color: "rgb(245,158,11)" }
                          : { border: "1px solid var(--border)", color: "var(--text-muted)" }
                        }
                      >
                        {t}{tConfig.icon && <span className="ml-1 opacity-60">{tConfig.icon}</span>}
                      </button>
                    );
                  })}
                </div>
              </div>
            )}
            {/* Type-specific extra config — driven entirely by typeFieldConfigs.js.
                Adding a new type (e.g. "rag") with its own fields needs ZERO
                changes here: register it in the config and it renders itself. */}
            {typeConfig.CustomPanel === "ClassificationConfigPanel" ? (
              <ClassificationConfigPanel
                classifyMode={classifyMode}
                onClassifyModeChange={setClassifyMode}
                docTypes={docTypes}
                onAddDocType={addDocType}
                onRemoveDocType={removeDocType}
                onUpdateDocType={updateDocType}
                onResetDocTypes={() => setDocTypes(withKeywordsRaw(DEFAULT_DOC_TYPES))}
              />
            ) : (
              <DynamicTypeFields
                fields={typeConfig.fields}
                values={typeFieldValues}
                onChange={(key, value) => setTypeFieldValues((prev) => ({ ...prev, [key]: value }))}
              />

            )}

            {/* Parameters */}
            <div className="rounded-xl p-4 space-y-5" style={cardStyle}>
              <div className="text-xs uppercase tracking-widest" style={{ color: "var(--text-faint)" }}>Parameters</div>

              {/* Stream — hidden for file types that can't stream (vision, classification) */}
              {canStream && (
                <div className="flex items-center justify-between">
                  <div>
                    <div className="text-xs uppercase tracking-wider" style={{ color: "var(--text-muted)" }}>Stream</div>
                    <div className="text-xs mt-0.5" style={{ color: "var(--text-faint)" }}>{stream ? "Real-time" : "Full response"}</div>
                  </div>
                  <button onClick={() => setStream((v) => !v)} disabled={loading || isEmbedding} className={`relative w-10 h-5 rounded-full transition-all duration-300 disabled:opacity-40 ${stream ? "bg-amber-500" : ""}`} style={stream ? {} : { backgroundColor: "var(--bg-subtle)", border: "1px solid var(--border)" }}>
                    <span className={`absolute top-0.5 w-4 h-4 bg-white rounded-full shadow transition-all duration-300 ${stream ? "left-5" : "left-0.5"}`} />
                  </button>
                </div>
              )}

              {/* History */}
              <div className="flex items-center justify-between">
                <div>
                  <div className="text-xs uppercase tracking-wider" style={{ color: "var(--text-muted)" }}>History</div>
                  <div className="text-xs mt-0.5" style={{ color: "var(--text-faint)" }}>{history ? "Conversation memory enabled" : "No conversation history"}</div>
                </div>
                <button onClick={() => setHistory((v) => !v)} disabled={loading} className={`relative w-10 h-5 rounded-full transition-all duration-300 disabled:opacity-40 ${history ? "bg-amber-500" : ""}`} style={history ? {} : { backgroundColor: "var(--bg-subtle)", border: "1px solid var(--border)" }}>
                  <span className={`absolute top-0.5 w-4 h-4 bg-white rounded-full shadow transition-all duration-300 ${history ? "left-5" : "left-0.5"}`} />
                </button>
              </div>

              {/* Max Tokens */}
              <div>
                <div className="flex justify-between items-center text-xs mb-2">
                  <span className="uppercase tracking-wider" style={{ color: "var(--text-muted)" }}>Max Tokens</span>
                  <input
                    type="number" value={maxToken ?? ""} min={1} max={maxTokenLimit ?? undefined}
                    placeholder={maxTokenLimit ? `Max ${maxTokenLimit}` : "Default"}
                    onChange={(e) => { const v = e.target.value; if (v === "" || Number(v) === 0) { setMaxToken(null); return; } const n = Number(v); setMaxToken(maxTokenLimit ? Math.min(n, maxTokenLimit) : n); }}
                    className="input-theme w-28 rounded px-2 py-1 text-xs font-mono outline-none text-right"
                  />
                </div>
                {maxToken > maxTokenLimit && <p className="text-red-400 text-xs mt-1">Max token cannot exceed {maxTokenLimit}</p>}
              </div>

              {/* Temperature */}
              <div>
                <div className="flex justify-between text-xs mb-2">
                  <span className="uppercase tracking-wider" style={{ color: "var(--text-muted)" }}>Temperature</span>
                  <span className="text-amber-500 font-bold">{temperature}</span>
                </div>
                <input type="range" min="0" max="2" step="0.1" value={temperature} onChange={(e) => setTemperature(parseFloat(e.target.value))} className="w-full accent-amber-500" />
                <div className="flex justify-between text-xs mt-1" style={{ color: "var(--text-faint)" }}><span>Precise</span><span>Creative</span></div>
              </div>

              {/* Instructions */}
              <div>
                <div className="flex justify-between items-center text-xs mb-1.5">
                  <span className="uppercase tracking-wider" style={{ color: "var(--text-muted)" }}>Instructions</span>
                  <button
                    onClick={() => setShowInstructionsModal(true)}
                    title="Open full editor"
                    className="flex items-center justify-center w-5 h-5 rounded transition-all duration-200 hover:text-amber-400 hover:border-amber-400/30"
                    style={{ border: "1px solid var(--border)", color: "var(--text-faint)" }}
                  >
                    <svg xmlns="http://www.w3.org/2000/svg" width="10" height="10" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
                      <polyline points="15 3 21 3 21 9" /><polyline points="9 21 3 21 3 15" />
                      <line x1="21" y1="3" x2="14" y2="10" /><line x1="3" y1="21" x2="10" y2="14" />
                    </svg>
                  </button>
                </div>
                <textarea
                  value={instructions}
                  onChange={(e) => setInstructions(e.target.value)}
                  placeholder="System prompt override..."
                  rows={3}
                  className="input-theme w-full rounded-lg px-3 py-2 text-xs outline-none resize-none"
                />
              </div>
            </div>

            {/* Payload preview */}
            {selectedModel && selectedType && (
              <div className="rounded-xl p-4" style={cardStyle}>
                <div className="text-xs uppercase tracking-widest mb-3" style={{ color: "var(--text-faint)" }}>Payload Preview</div>
                <pre className="text-xs font-mono leading-relaxed overflow-x-auto whitespace-pre-wrap" style={{ color: "var(--text-muted)" }}>
                  {JSON.stringify(
                    isFileType
                      ? {
                        source, type: selectedType, ...typeFieldValues,
                        model: selectedModel.id, files: attachedFiles.map((f) => f.name), ...(instructions.trim() ? { instructions: instructions.trim() } : {}), ...(isClassification ? { mode: classifyMode, documentTypes: buildDocTypesPayload().map((d) => d.id) } : {}), ...(supportsFileStream ? { stream } : {})
                      }
                      : { model: selectedModel.id, source, type: selectedType, ...typeFieldValues, ...(instructions.trim() ? { instructions: instructions.trim() } : {}), query: "...", temperature, maxTokens: maxToken },
                    null, 2
                  )}
                </pre>
              </div>
            )}
          </div>
        </div>
      </div>

      {/* Save as Agent modal */}
      {showCreateModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center" style={{ backgroundColor: "rgba(0,0,0,0.5)" }}>
          <div className="rounded-xl p-6 w-[420px] shadow-2xl" style={cardStyle}>
            <div className="flex items-start justify-between mb-4">
              <div className="w-9 h-9 rounded-lg flex items-center justify-center text-violet-500 text-lg" style={{ backgroundColor: "rgba(139,92,246,0.1)", border: "1px solid rgba(139,92,246,0.2)" }}>◆</div>
              <button onClick={() => setShowCreateModal(false)} className="text-xl leading-none hover:text-amber-400 transition-colors" style={{ color: "var(--text-faint)" }}>✕</button>
            </div>
            <h2 className="text-sm font-bold mb-1" style={{ color: "var(--text-primary)" }}>Create agent from this config</h2>
            <p className="text-xs mb-5" style={{ color: "var(--text-muted)" }}>Save this configuration as a reusable agent.</p>

            <div className="flex flex-wrap gap-2 mb-5 p-3 rounded-lg" style={{ backgroundColor: "var(--bg-subtle)" }}>
              <span className="text-xs px-2.5 py-1 rounded-full text-amber-400" style={{ backgroundColor: "rgba(217,119,6,0.1)", border: "1px solid rgba(217,119,6,0.2)" }}>{selectedModel.id}</span>
              <span className="text-xs px-2.5 py-1 rounded-full text-violet-400" style={{ backgroundColor: "rgba(139,92,246,0.1)", border: "1px solid rgba(139,92,246,0.2)" }}>{selectedType}</span>
              <span className="text-xs px-2.5 py-1 rounded-full" style={{ border: "1px solid var(--border)", color: "var(--text-muted)" }}>{source}</span>
              <span className="text-xs px-2.5 py-1 rounded-full text-emerald-400" style={{ backgroundColor: "rgba(52,211,153,0.1)", border: "1px solid rgba(52,211,153,0.2)" }}>temp {temperature}</span>
              {instructions.trim() && <span className="text-xs px-2.5 py-1 rounded-full" style={{ border: "1px solid var(--border)", color: "var(--text-muted)" }}>custom instructions</span>}
            </div>

            <div className="space-y-3 mb-5">
              {!instructions.trim() && (
                <div className="rounded-lg px-3 py-2 text-xs text-red-400" style={{ border: "1px solid rgba(239,68,68,0.2)", backgroundColor: "rgba(239,68,68,0.06)" }}>
                  Instructions are required before this configuration can be saved as an agent.
                </div>
              )}
              <div>
                <label className="text-xs uppercase tracking-widest block mb-1.5" style={{ color: "var(--text-muted)" }}>Description<span className="normal-case tracking-normal text-red-400">*</span></label>
                <input
                  type="text" value={agentDescription} onChange={(e) => setAgentDescription(e.target.value)}
                  placeholder="What does this agent do?"
                  className="input-theme w-full rounded-lg px-3 py-2 text-sm outline-none"
                />
              </div>

              {selectedType === "rag" && (
                <div className="space-y-2">
                  <label
                    className="text-xs uppercase tracking-widest block"
                    style={{ color: "var(--text-muted)" }}
                  >
                    Knowledge Files
                    <span className="text-red-400">*</span>
                  </label>

                  <button
                    type="button"
                    onClick={() => agentFileInputRef.current?.click()}
                    className="w-full rounded-lg border border-dashed py-4 text-sm hover:border-violet-400"
                    style={{ borderColor: "var(--border)" }}
                  >
                    Upload Knowledge Files
                  </button>

                  <input
                    ref={agentFileInputRef}
                    type="file"
                    multiple
                    hidden
                    onChange={handleAgentFileSelect}
                  />

                  {agentFiles.map((file, index) => (
                    <div
                      key={index}
                      className="flex justify-between items-center rounded px-2 py-1"
                      style={{ background: "var(--bg-subtle)" }}
                    >
                      <span className="text-xs">{file.name}</span>

                      <button
                        onClick={() => removeAgentFile(index)}
                        className="text-red-400"
                      >
                        ✕
                      </button>
                    </div>
                  ))}
                </div>
              )}
            </div>

            <div className="flex gap-2 justify-end">
              <button onClick={() => setShowCreateModal(false)} className="px-4 py-2 text-xs uppercase tracking-wider rounded-lg hover:text-amber-400 transition-colors" style={{ border: "1px solid var(--border)", color: "var(--text-muted)" }}>
                Cancel
              </button>
              <button onClick={handleCreateAgent} disabled={creating} className="px-4 py-2 text-xs uppercase tracking-wider bg-violet-500 hover:bg-violet-400 text-white rounded-lg disabled:opacity-40 disabled:cursor-not-allowed transition-colors flex items-center gap-1.5">
                {creating ? <span className="w-3.5 h-3.5 border-2 border-white/30 border-t-white rounded-full animate-spin" /> : "✦"}
                {creating ? "Creating..." : "Create agent"}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Instructions modal */}
      <InstructionsModal
        open={showInstructionsModal}
        value={instructions}
        onChange={setInstructions}
        onClose={() => setShowInstructionsModal(false)}
      />
    </>
  );
}