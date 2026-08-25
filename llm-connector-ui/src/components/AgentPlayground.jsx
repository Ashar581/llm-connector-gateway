import { useState, useEffect, useRef } from "react";
import apiSvc from "../services/apiService";
import ReactMarkdown from "react-markdown";
import { Prism as SyntaxHighlighter } from "react-syntax-highlighter";
import { oneDark } from "react-syntax-highlighter/dist/esm/styles/prism";
import { streamAsk } from "../services/streamService";

const FILE_TYPES = ["vision", "classification"];

export default function AgentPlayground({ open, onClose, agent }) {
	const [messages, setMessages] = useState([]);
	const [input, setInput] = useState("");
	const [loading, setLoading] = useState(false);
	const [stream, setStream] = useState(false);
	const [history, setHistory] = useState(false);
	const [attachedFiles, setAttachedFiles] = useState([]);
	const bottomRef = useRef(null);
	const inputRef = useRef(null);
	const abortRef = useRef(null);
	const fileInputRef = useRef(null);

	const isFileType = FILE_TYPES.includes(agent?.type);
	const streamLocked = isFileType;
	// History (conversation memory) makes no sense for vision/classification —
	// each request there is a one-off document, not a back-and-forth chat.
	const historyLocked = isFileType;

	// Reset on open / agent change
	useEffect(() => {
		if (open) {
			setMessages([]);
			setInput("");
			setAttachedFiles([]);
			setTimeout(() => inputRef.current?.focus(), 100);
		}
		return () => abortRef.current?.abort();
	}, [open, agent?.uniqueId]);

	// Turns the current message list into the {role, content} pairs the
	// backend expects when chat history is enabled — same shape as Playground.
	const buildChatHistory = () =>
		messages.map((m) => ({
			role: m.role === "user" ? "USER" : "ASSISTANT",
			content: m.text,
		}));

	// Auto-scroll
	useEffect(() => {
		bottomRef.current?.scrollIntoView({ behavior: "smooth" });
	}, [messages, loading]);

	// Escape to close
	useEffect(() => {
		const handler = (e) => { if (e.key === "Escape") onClose(); };
		window.addEventListener("keydown", handler);
		return () => window.removeEventListener("keydown", handler);
	}, [onClose]);

	// ── Non-stream send (FormData always) ─────────────────
	const sendNormal = async (query, filesSnapshot) => {
		try {
			const fd = new FormData();
			fd.append("query", query);
			fd.append("agent", agent.name);
			fd.append("chatHistoryEnabled", history && !historyLocked);
			fd.append("chatHistoryForForm", JSON.stringify(buildChatHistory()));
			filesSnapshot.forEach((f) => fd.append("files", f));

			const response = await apiSvc.post("/v1/agent", fd, {
				headers: { "Content-Type": "multipart/form-data" },
			});
			const answer = response.data.data;
			console.log(answer)
			let formattedAnswer = answer;

			if (typeof answer === "object") {
				formattedAnswer =
					"```json\n" +
					JSON.stringify(answer, null, 2) +
					"\n```";
			} else if (typeof answer === "string") {
				try {
					const parsed = JSON.parse(answer);

					if (typeof parsed === "object") {
						formattedAnswer =
							"```json\n" +
							JSON.stringify(parsed, null, 2) +
							"\n```";
					}
				} catch {
					// normal text response
				}
			}
			setMessages((prev) => [
				...prev,
				{ role: "assistant", text: formattedAnswer, id: crypto.randomUUID() },
			]);
		} catch (e) {
			if (e.name === "AbortError" || e.name === "CanceledError") return;
			setMessages((prev) => [
				...prev,
				{ role: "error", text: "Something went wrong. Please try again.", id: crypto.randomUUID() },
			]);
			console.error("Agent query failed", e);
		} finally {
			setLoading(false);
		}
	};


	// ── Stream send ───────────────────────────────────────
	const sendStream = async (query) => {
		const controller = new AbortController();
		abortRef.current = controller;

		const accumulated = { current: "" };
		const assistantId = crypto.randomUUID();

		await new Promise((resolve) => {
			setMessages((prev) => {
				resolve();
				return [...prev, { role: "assistant", text: "", id: assistantId, streaming: true }];
			});
		});

		try {
			await streamAsk({
				testAgent: true,
				payload: ({
					query,
					agent: agent.name,
					stream: true,
					chatHistoryEnabled: history && !historyLocked,
					chatHistory: buildChatHistory(),
				}),

				signal: controller.signal,

				onChunk: (chunk) => {
					accumulated.current += chunk;

					const snapshot =
						accumulated.current;

					setMessages((prev) =>
						prev.map((m) =>
							m.id === assistantId
								? {
									...m,
									text: snapshot,
								}
								: m
						)
					);
				},
			});

			setMessages((prev) =>
				prev.map((m) =>
					m.id === assistantId
						? {
							...m,
							streaming: false,
						}
						: m
				)
			);
		} catch (e) {
			if (e.name === "AbortError") {
				setMessages((prev) =>
					prev.map((m) =>
						m.id === assistantId
							? { ...m, text: accumulated.current || "Stopped.", streaming: false }
							: m
					)
				);
				return;
			}
			setMessages((prev) =>
				prev.map((m) =>
					m.id === assistantId
						? { ...m, role: "error", text: "Stream failed. Please try again.", streaming: false }
						: m
				)
			);
			console.error("Stream failed", e);
		} finally {
			setLoading(false);
			abortRef.current = null;
		}
	};

	// ── Main send ─────────────────────────────────────────
	const handleSend = async () => {
		const query = input.trim();

		// For file agents: allow sending if files exist
		// For other agents: require a message
		const canSend = isFileType
			? attachedFiles.length > 0 || query
			: query;

		if (!canSend || loading) return;

		const filesSnapshot = [...attachedFiles];

		setMessages((prev) => [
			...prev,
			{
				role: "user",
				text: query,
				id: crypto.randomUUID(),
				files: filesSnapshot.map((f) => f.name),
			},
		]);

		setInput("");
		setAttachedFiles([]);
		setLoading(true);

		if (stream && !streamLocked) {
			await sendStream(query);
		} else {
			await sendNormal(query, filesSnapshot);
		}

		setTimeout(() => inputRef.current?.focus(), 50);
	};

	const handleStop = () => {
		abortRef.current?.abort();
		setLoading(false);
	};

	const handleKeyDown = (e) => {
		if (e.key === "Enter" && !e.shiftKey) {
			e.preventDefault();
			handleSend();
		}
	};

	if (!open || !agent) return null;

	const isStreaming = loading && stream && !streamLocked;

	return (
		<div
			className="fixed inset-0 z-50 flex items-center justify-center p-4"
			onClick={(e) => { if (e.target === e.currentTarget) onClose(); }}
		>
			<div className="absolute inset-0" style={{ backgroundColor: "rgba(0,0,0,0.7)", backdropFilter: "blur(4px)" }} />

			<div
				className="relative z-10 w-full max-w-3xl h-[85vh] flex flex-col rounded-xl shadow-2xl overflow-hidden"
				style={{ backgroundColor: "var(--bg-secondary)", border: "1px solid var(--border)" }}
			>

				{/* ── Header ── */}
				<div
					className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3 px-4 sm:px-6 py-4 flex-shrink-0"
					style={{ borderBottom: "1px solid var(--border)", backgroundColor: "var(--bg-secondary)" }}
				>
					<div className="flex items-center gap-3 min-w-0">
						<div className={`w-2 h-2 rounded-full flex-shrink-0 ${agent.active
							? "bg-emerald-400 shadow-[0_0_6px_rgba(52,211,153,0.7)]"
							: ""
							}`} style={agent.active ? {} : { backgroundColor: "var(--border-strong)" }} />
						<div className="min-w-0">
							<h2 className="text-sm font-black tracking-tight truncate" style={{ color: "var(--text-primary)" }}>{agent.name}</h2>
							<p className="text-xs mt-0.5 truncate" style={{ color: "var(--text-faint)" }}>
								{agent.model}
								<span className="mx-1.5" style={{ color: "var(--border-strong)" }}>·</span>
								{agent.type}
								<span className="mx-1.5" style={{ color: "var(--border-strong)" }}>·</span>
								{agent.source}
							</p>
						</div>
					</div>

					<div className="flex items-center gap-3 sm:gap-4 flex-wrap">
						{/* Stream toggle — locked off for file-type agents */}
						<div className="flex items-center gap-2">
							<span className="hidden sm:inline text-xs uppercase tracking-wider" style={{ color: "var(--text-faint)" }}>Stream</span>
							<button
								onClick={() => !streamLocked && setStream((v) => !v)}
								disabled={loading || streamLocked}
								title={streamLocked ? "Streaming unavailable for file-based agents" : undefined}
								className={`relative w-10 h-5 rounded-full transition-all duration-300 disabled:opacity-40 flex-shrink-0 ${!streamLocked && stream ? "bg-sky-400" : ""} ${streamLocked ? "cursor-not-allowed" : ""}`}
								style={!streamLocked && stream ? {} : { backgroundColor: "var(--border)" }}
							>
								<span className={`absolute top-0.5 w-4 h-4 bg-white rounded-full shadow transition-all duration-300 ${!streamLocked && stream ? "left-5" : "left-0.5"}`} />
							</button>
							<span
								className={`text-xs font-bold uppercase tracking-wider transition-colors ${!streamLocked && stream ? "text-sky-400" : ""}`}
								style={!streamLocked && stream ? {} : { color: "var(--text-faint)" }}
							>
								{streamLocked ? "N/A" : stream ? "On" : "Off"}
							</span>
						</div>

						{/* History toggle — locked off for vision/classification, which have no conversation memory */}
						<div className="flex items-center gap-2">
							<span className="hidden sm:inline text-xs uppercase tracking-wider" style={{ color: "var(--text-faint)" }}>History</span>
							<button
								onClick={() => !historyLocked && setHistory((v) => !v)}
								disabled={loading || historyLocked}
								title={historyLocked ? "Chat history unavailable for file-based agents" : undefined}
								className={`relative w-10 h-5 rounded-full transition-all duration-300 disabled:opacity-40 flex-shrink-0 ${!historyLocked && history ? "bg-violet-400" : ""} ${historyLocked ? "cursor-not-allowed" : ""}`}
								style={!historyLocked && history ? {} : { backgroundColor: "var(--border)" }}
							>
								<span className={`absolute top-0.5 w-4 h-4 bg-white rounded-full shadow transition-all duration-300 ${!historyLocked && history ? "left-5" : "left-0.5"}`} />
							</button>
							<span
								className={`text-xs font-bold uppercase tracking-wider transition-colors ${!historyLocked && history ? "text-violet-400" : ""}`}
								style={!historyLocked && history ? {} : { color: "var(--text-faint)" }}
							>
								{historyLocked ? "N/A" : history ? "On" : "Off"}
							</span>
						</div>

						{messages.length > 0 && !loading && (
							<button
								onClick={() => setMessages([])}
								className="text-xs uppercase tracking-wider transition-colors hover:text-sky-400"
								style={{ color: "var(--text-faint)" }}
							>
								Clear
							</button>
						)}


						<button onClick={onClose} className="transition-colors text-xl leading-none hover:text-sky-400" style={{ color: "var(--text-faint)" }}>
							✕
						</button>
					</div>
				</div>

				{/* ── System instructions strip ── */}
				{agent.instructions && (
					<div className="px-6 py-2.5 flex-shrink-0" style={{ borderBottom: "1px solid var(--border)", backgroundColor: "var(--bg-subtle)" }}>
						<p className="text-xs line-clamp-1" style={{ color: "var(--text-faint)" }}>
							<span className="text-sky-500/80 mr-2 uppercase tracking-wider text-[10px]">System</span>
							{agent.instructions}
						</p>
					</div>
				)}


				{/* ── Messages ── */}
				<div
					className="flex-1 overflow-y-auto scrollbar-thin scrollbar-track-transparent scrollbar-thumb-sky-400/30 hover:scrollbar-thumb-sky-400/50 px-6 py-6 space-y-6"
					style={{ backgroundColor: "var(--bg-primary)" }}
				>

					{messages.length === 0 && (
						<div className="h-full flex flex-col items-center justify-center text-center">
							<div className="w-12 h-12 rounded-xl flex items-center justify-center mb-4" style={{ backgroundColor: "rgba(14,165,233,0.1)", border: "1px solid rgba(14,165,233,0.2)" }}>
								<span className="text-sky-500 text-xl">◆</span>
							</div>
							<p className="text-xs uppercase tracking-widest mb-1" style={{ color: "var(--text-faint)" }}>
								Ask anything to start the conversation
							</p>
							{isFileType ? (
								<p className="text-xs mt-1 text-amber-400/40">
									{agent.type === "classification" ? "🗂 Classification" : "👁 Vision"} — attach files in the bar above, then send your query
								</p>
							) : (
								<p className="text-xs mt-1" style={stream ? { color: "rgb(56,189,248)" } : { color: "var(--text-faint)" }}>
									{stream ? "⚡ Streaming mode on" : "Stream off — toggle above to enable"}
								</p>
							)}
							{!isFileType && (
								<div className="mt-8 flex flex-wrap gap-2 justify-center max-w-md">
									{["What can you help me with?", "Give me a quick summary", "Tell me about your capabilities"].map((prompt) => (
										<button
											key={prompt}
											onClick={() => { setInput(prompt); inputRef.current?.focus(); }}
											className="text-xs px-4 py-2 rounded-full transition-all duration-200 hover:text-sky-500 hover:border-sky-400/30"
											style={{ border: "1px solid var(--border)", color: "var(--text-muted)" }}
										>
											{prompt}
										</button>
									))}
								</div>
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
										? { backgroundColor: "rgba(14,165,233,0.15)", color: "rgb(56,189,248)", border: "1px solid rgba(14,165,233,0.2)" }
										: msg.role === "error"
											? { backgroundColor: "rgba(239,68,68,0.1)", color: "rgb(248,113,113)", border: "1px solid rgba(239,68,68,0.2)" }
											: { backgroundColor: "var(--bg-subtle)", color: "var(--text-muted)", border: "1px solid var(--border)" }
								}
							>
								{msg.role === "user" ? "U" : msg.role === "error" ? "!" : "A"}
							</div>

							{/* Bubble */}
							<div
								className="max-w-[78%] rounded-xl px-4 py-3 text-sm leading-relaxed"
								style={
									msg.role === "user"
										? { backgroundColor: "rgba(14,165,233,0.1)", border: "1px solid rgba(14,165,233,0.15)", color: "var(--text-primary)", borderTopRightRadius: "0.125rem" }
										: msg.role === "error"
											? { backgroundColor: "rgba(239,68,68,0.08)", border: "1px solid rgba(239,68,68,0.15)", color: "rgb(248,113,113)", borderTopLeftRadius: "0.125rem" }
											: { backgroundColor: "var(--bg-card)", border: "1px solid var(--border)", color: "var(--text-secondary)", borderTopLeftRadius: "0.125rem" }
								}
							>

								{/* Attached file chips inside user bubble */}
								{msg.role === "user" && msg.files?.length > 0 && (
									<div className="flex flex-wrap gap-1.5 mb-2">
										{msg.files.map((name, i) => (
											<span key={i} className="inline-flex items-center gap-1 rounded px-2 py-0.5" style={{ backgroundColor: "rgba(14,165,233,0.15)", border: "1px solid rgba(14,165,233,0.25)" }}>
												<svg xmlns="http://www.w3.org/2000/svg" width="9" height="9" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className="text-sky-500">
													<path d="M14 2H6a2 2 0 00-2 2v16a2 2 0 002 2h12a2 2 0 002-2V8z" /><polyline points="14 2 14 8 20 8" />
												</svg>
												<span className="text-[11px] font-mono truncate max-w-[120px] text-sky-500/90">{name}</span>
											</span>
										))}
									</div>
								)}

								<ReactMarkdown
									components={{
										code({ inline, className, children, ...props }) {
											const match = /language-(\w+)/.exec(className || "");

											return !inline ? (
												<SyntaxHighlighter
													style={oneDark}
													language={match?.[1] || "json"}
													PreTag="div"
													customStyle={{
														borderRadius: "12px",
														padding: "16px",
														fontSize: "13px",
														background: "#111827",
													}}
													{...props}
												>
													{String(children).replace(/\n$/, "")}
												</SyntaxHighlighter>
											) : (
												<code className="px-1 py-0.5 rounded text-sky-500" style={{ backgroundColor: "var(--bg-subtle)" }}>
													{children}
												</code>
											);
										},
									}}
								>
									{msg.text || ""}
								</ReactMarkdown>
								{msg.streaming && (
									<span className="inline-block w-0.5 h-3.5 bg-sky-400 ml-0.5 align-middle animate-pulse" />
								)}
							</div>
						</div>
					))}

					{/* Typing indicator — non-stream only */}
					{loading && !isStreaming && (
						<div className="flex gap-3">
							<div className="w-7 h-7 rounded-lg flex items-center justify-center flex-shrink-0 text-xs font-bold" style={{ backgroundColor: "var(--bg-subtle)", border: "1px solid var(--border)", color: "var(--text-muted)" }}>A</div>
							<div className="rounded-xl px-4 py-3" style={{ backgroundColor: "var(--bg-card)", border: "1px solid var(--border)", borderTopLeftRadius: "0.125rem" }}>
								<div className="flex gap-1.5 items-center h-4">
									{[0, 1, 2].map((i) => (
										<div key={i} className="w-1.5 h-1.5 bg-sky-400/60 rounded-full animate-bounce" style={{ animationDelay: `${i * 180}ms` }} />
									))}
								</div>
							</div>
						</div>
					)}

					<div ref={bottomRef} />
				</div>

				{/* ── Input ── */}
				<div className="flex-shrink-0 px-6 py-4" style={{ borderTop: "1px solid var(--border)", backgroundColor: "var(--bg-secondary)" }}>

					{/* Attached Files Preview */}
					{isFileType && attachedFiles.length > 0 && (
						<div className="flex flex-wrap gap-2 mb-3">
							{attachedFiles.map((file, index) => (
								<div
									key={index}
									className="flex items-center gap-2 rounded-lg px-3 py-2"
									style={{ backgroundColor: "rgba(14,165,233,0.1)", border: "1px solid rgba(14,165,233,0.2)" }}
								>
									<svg
										xmlns="http://www.w3.org/2000/svg"
										width="12"
										height="12"
										viewBox="0 0 24 24"
										fill="none"
										stroke="currentColor"
										strokeWidth="2"
										strokeLinecap="round"
										strokeLinejoin="round"
										className="text-sky-500"
									>
										<path d="M14 2H6a2 2 0 00-2 2v16a2 2 0 002 2h12a2 2 0 002-2V8z" />
										<polyline points="14 2 14 8 20 8" />
									</svg>

									<span className="text-xs truncate max-w-[140px] text-sky-500/90">
										{file.name}
									</span>

									<button
										type="button"
										onClick={() =>
											setAttachedFiles((prev) =>
												prev.filter((_, i) => i !== index)
											)
										}
										className="text-red-400 hover:text-red-300 text-xs"
									>
										✕
									</button>
								</div>
							))}
						</div>
					)}

					<div className="flex gap-3 items-center">

						{/* Attach Button */}
						{isFileType && (
							<>
								<button
									type="button"
									onClick={() => fileInputRef.current?.click()}
									disabled={loading}
									className="flex-shrink-0 w-11 h-11 rounded-xl flex items-center justify-center hover:text-sky-500 hover:border-sky-400/30 transition-all duration-200"
									style={{ border: "1px solid var(--border)", color: "var(--text-muted)" }}
								>
									<svg
										xmlns="http://www.w3.org/2000/svg"
										width="16"
										height="16"
										viewBox="0 0 24 24"
										fill="none"
										stroke="currentColor"
										strokeWidth="2"
										strokeLinecap="round"
										strokeLinejoin="round"
									>
										<path d="M21.44 11.05l-9.19 9.19a6 6 0 01-8.49-8.49l9.19-9.19a4 4 0 015.66 5.66l-9.2 9.19a2 2 0 01-2.83-2.83l8.49-8.48" />
									</svg>
								</button>

								<input
									ref={fileInputRef}
									type="file"
									multiple
									accept={
										agent.type === "vision"
											? "image/*,application/pdf"
											: "*/*"
									}
									className="hidden"
									onChange={(e) => {
										const files = Array.from(e.target.files || []);

										if (files.length > 0) {
											setAttachedFiles((prev) => [...prev, ...files]);
										}

										e.target.value = "";
									}}
								/>
							</>
						)}

						{/* Textarea */}
						<div className="flex-1 relative flex items-center">
							<textarea
								ref={inputRef}
								value={input}
								onChange={(e) => {
									setInput(e.target.value);

									e.target.style.height = "44px";
									e.target.style.height =
										Math.min(e.target.scrollHeight, 120) + "px";
								}}
								onKeyDown={handleKeyDown}
								placeholder={`Message ${agent.name}...`}
								rows={1}
								disabled={loading}
								className="input-theme w-full rounded-xl px-4 py-3 text-sm outline-none resize-none overflow-y-auto scrollbar-hide leading-relaxed disabled:opacity-50"
								style={{
									minHeight: "44px",
									maxHeight: "120px",
									border: "1px solid var(--border)",
								}}
							/>

							<span className="absolute right-3 top-1/2 -translate-y-1/2 text-xs pointer-events-none" style={{ color: "var(--text-faint)" }}>
								↵ send
							</span>
						</div>

						{/* Send / Stop Button */}
						{isStreaming ? (
							<button
								onClick={handleStop}
								className="flex-shrink-0 w-11 h-11 rounded-xl flex items-center justify-center transition-colors hover:bg-red-400/20"
								style={{ backgroundColor: "rgba(239,68,68,0.1)", border: "1px solid rgba(239,68,68,0.2)" }}
							>
								<svg
									xmlns="http://www.w3.org/2000/svg"
									width="14"
									height="14"
									viewBox="0 0 24 24"
									fill="#f87171"
								>
									<rect x="4" y="4" width="16" height="16" rx="2" />
								</svg>
							</button>
						) : (
							<button
								onClick={handleSend}
								disabled={
									loading ||
									(!input.trim() && attachedFiles.length === 0)
								}
								className="flex-shrink-0 w-11 h-11 bg-sky-400 rounded-xl flex items-center justify-center hover:bg-sky-300 transition-colors disabled:opacity-30 disabled:cursor-not-allowed"
							>
								{loading ? (
									<span className="w-4 h-4 border-2 border-black/30 border-t-black rounded-full animate-spin" />
								) : (
									<svg
										xmlns="http://www.w3.org/2000/svg"
										width="16"
										height="16"
										viewBox="0 0 24 24"
										fill="none"
										stroke="black"
										strokeWidth="2.5"
										strokeLinecap="round"
										strokeLinejoin="round"
									>
										<line x1="22" y1="2" x2="11" y2="13" />
										<polygon points="22 2 15 22 11 13 2 9 22 2" />
									</svg>
								)}
							</button>
						)}
					</div>

					<p className="text-xs mt-2 text-center" style={{ color: "var(--text-faint)" }}>
						Shift+Enter for new line · Escape to close
						{!streamLocked && stream && (
							<span className="ml-2 text-sky-500/70">
								· ⚡ Streaming
							</span>
						)}
						{!historyLocked && history && (
							<span className="text-violet-400/40 ml-2">
								· 🧠 History on
							</span>
						)}
					</p>
				</div>
			</div>
		</div>
	);
}
