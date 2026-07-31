export const DEFAULT_DOC_TYPES = [
    {
        id: "PURCHASE_ORDER",
        description: "Purchase order issued by buyer to supplier for ordering goods or services.",
        keywords: ["purchase order", "po number", "buyer", "ordered quantity", "delivery schedule"],
    },
];

export const withKeywordsRaw = (docTypes) => docTypes.map((d) => ({ ...d, keywordsRaw: d.keywords.join(", ") }));

export const buildDocTypesPayload = (docTypes) =>
    docTypes
        .filter((d) => d.id.trim())
        .map((d) => ({
            id: d.id.trim().toUpperCase().replace(/\s+/g, "_"),
            description: d.description.trim(),
            keywords: d.keywordsRaw.split(",").map((k) => k.trim()).filter(Boolean),
        }));

// Classification's config is a repeatable list, not a flat field schema, so
// it's rendered by this dedicated component rather than DynamicTypeFields.
// Registered against the "classification" type via `CustomPanel` in
// typeFieldConfigs.js.
export default function ClassificationConfigPanel({
    classifyMode,
    onClassifyModeChange,
    docTypes,
    onAddDocType,
    onRemoveDocType,
    onUpdateDocType,
    onResetDocTypes,
}) {
    return (
        <div className="rounded-xl p-4 space-y-4" style={{ backgroundColor: "var(--bg-card)", border: "1px solid rgba(139,92,246,0.25)" }}>
            <div>
                <div className="text-xs uppercase tracking-widest text-violet-500 mb-2">Mode</div>
                <div className="flex gap-1.5">
                    {["SINGLE", "PAGE", "AUTO"].map((m) => (
                        <button
                            key={m}
                            onClick={() => onClassifyModeChange(m)}
                            className="flex-1 py-1.5 text-xs uppercase tracking-wider rounded-lg transition-all duration-200"
                            style={classifyMode === m
                                ? { backgroundColor: "rgba(139,92,246,0.12)", border: "1px solid rgba(139,92,246,0.35)", color: "rgb(167,139,250)", fontWeight: "bold" }
                                : { border: "1px solid var(--border)", color: "var(--text-muted)" }
                            }
                        >
                            {m}
                        </button>
                    ))}
                </div>
                <p className="text-xs mt-1.5" style={{ color: "var(--text-faint)" }}>
                    {classifyMode === "SINGLE" && "Classify entire document as one type"}
                    {classifyMode === "PAGE" && "Classify each page independently"}
                    {classifyMode === "AUTO" && "Auto-detect best classification strategy"}
                </p>
            </div>
            <div style={{ borderTop: "1px solid var(--border)" }} />
            <div>
                <div className="flex items-center justify-between mb-2">
                    <div className="text-xs uppercase tracking-widest text-violet-500">Document Types</div>
                    <button onClick={onResetDocTypes} className="text-xs hover:text-amber-400 transition-colors" style={{ color: "var(--text-faint)" }}>↺ Reset</button>
                </div>
                <div className="space-y-3 max-h-72 overflow-y-auto pr-0.5">
                    {docTypes.map((dt, i) => (
                        <div key={i} className="rounded-lg p-3 space-y-2" style={{ border: "1px solid var(--border)", backgroundColor: "var(--bg-subtle)" }}>
                            <div className="flex items-center gap-2">
                                <span className="w-4 h-4 rounded flex items-center justify-center text-violet-500 text-xs font-bold flex-shrink-0" style={{ backgroundColor: "rgba(139,92,246,0.12)" }}>{i + 1}</span>
                                <input type="text" value={dt.id} onChange={(e) => onUpdateDocType(i, "id", e.target.value)} placeholder="TYPE_ID" className="input-theme flex-1 min-w-0 rounded px-2 py-1 text-xs font-mono outline-none uppercase" />
                                <button onClick={() => onRemoveDocType(i)} disabled={docTypes.length === 1} className="flex-shrink-0 hover:text-red-400 transition-colors disabled:opacity-30 disabled:cursor-not-allowed text-sm leading-none" style={{ color: "var(--text-faint)" }}>✕</button>
                            </div>
                            <textarea value={dt.description} onChange={(e) => onUpdateDocType(i, "description", e.target.value)} placeholder="Description of this document type..." rows={2} className="input-theme w-full rounded px-2 py-1 text-xs outline-none resize-none" />
                            <div>
                                <input type="text" value={dt.keywordsRaw} onChange={(e) => onUpdateDocType(i, "keywordsRaw", e.target.value)} placeholder="keyword1, keyword2, keyword3" className="input-theme w-full rounded px-2 py-1 text-xs font-mono outline-none" />
                                <p className="text-xs mt-0.5" style={{ color: "var(--text-faint)" }}>Comma-separated keywords</p>
                            </div>
                        </div>
                    ))}
                </div>
                <button onClick={onAddDocType} className="mt-2.5 w-full py-2 text-xs rounded-lg border-dashed text-violet-500 hover:text-violet-400 transition-all duration-200" style={{ border: "1px dashed rgba(139,92,246,0.3)" }}>
                    + Add Document Type
                </button>
            </div>
        </div>
    );
}