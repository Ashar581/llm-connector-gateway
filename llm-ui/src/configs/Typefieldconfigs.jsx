// ─────────────────────────────────────────────────────────────────────────
// Central registry for "type"-specific behavior in the Playground.
//
// WHY THIS FILE EXISTS
// Every time a new `type` (chat, vision, classification, rag, ...) needs its
// own extra fields in the right panel + its own slice of the outgoing
// payload, that used to mean touching Playground.jsx in ~5 different spots:
// FILE_TYPES array, a `isX` boolean, a JSX block, buildPayload/buildFormData,
// and handleCreateAgent. That's why it kept getting harder to maintain.
//
// Now: to add a new type, add ONE entry below. Nothing else in
// Playground.jsx needs to change (unless the type needs a truly bespoke
// UI, like `classification`'s repeatable doc-type list — see CustomPanel).
//
// ── Field schema ───────────────────────────────────────────────────────
// {
//   key:        string   -> payload key + form field name
//   label:      string   -> shown in the UI
//   type:       "text" | "textarea" | "number" | "select" | "toggle"
//   default:    initial value
//   placeholder?: string
//   options?:   string[]              (required for type: "select")
//   min/max/step?: number             (used by type: "number")
//   description?: string              (shown under toggles)
//   validate?:  (rawValue) => boolean (reject keystroke if it returns false)
// }
// ─────────────────────────────────────────────────────────────────────────

export const TYPE_FIELD_CONFIGS = {
    vision: {
        isFileType: true,
        supportsStream: false, // vision handles files but never streams
        icon: "👁",
        endpoint: "/v2/vl",
        accept: "image/*,application/pdf",
        fileValidator: (f) => f.type.startsWith("image/") || f.type === "application/pdf" || f.name.toLowerCase().endsWith(".pdf"),
        fileRejectMessage: "Vision only accepts images and PDFs",
        dropHint: "Vision accepts images and PDFs",
        modeLabel: "Vision mode",
        fields: [
            {
                key: "pageChunk",
                label: "Page Chunk",
                type: "text",
                inputMode: "numeric",
                default: "1",
                validate: (v) => /^[1-4]?$/.test(v),
            },
        ],
    },

    // Classification's config (mode + repeatable document types) isn't a flat
    // field list, so it opts out of the generic renderer via CustomPanel.
    // `fields` is left empty on purpose — its state/payload is still handled
    // by Playground.jsx's existing docTypes/classifyMode logic.
    classification: {
        isFileType: true,
        supportsStream: false, // classification handles files but never streams
        icon: "🗂",
        endpoint: "/v2/vl/classify",
        accept: "*/*",
        dropHint: "Classification accepts documents and files",
        modeLabel: "Classification mode",
        fields: [],
        CustomPanel: "ClassificationConfigPanel", // resolved in Playground.jsx
    },

    embedding: {
        isFileType: false,
        fields: [],
    },

    // ── New requirement: RAG type with its own 10 fields, sent in the payload ──
    rag: {
        isFileType: true,
        supportsStream: true, // RAG handles files AND can stream its response
        icon: "📚",
        endpoint: "/v3/rag/ask", // TODO: confirm the actual RAG ingestion/query endpoint with backend
        streamEndpoint: "/v3/rag/stream/ask", // TODO: confirm the actual RAG streaming endpoint with backend
        accept: ".pdf,.doc,.docx,.txt,.md,.csv,.pptx,.ppt",
        fileValidator: (f) => /\.(pdf|docx?|txt|md|csv|ppt|pptx)$/i.test(f.name),
        fileRejectMessage: "RAG only accepts PDF, DOC/DOCX, TXT, MD, PPT or CSV files",
        dropHint: "RAG accepts documents to index (PDF, DOCX, TXT, MD, CSV)",
        modeLabel: "RAG mode",
        fields: [
            { key: "vectorStore", label: "Vector Store", type: "select", options: [], default: "" },
            {
                key: "encodingType", label: "Encoding Type", type: "select", options: ["cl100k_base", "r50k_base", "p50k_base", "p50k_edit", "o200k_base"], default: ""
            },
            { key: "chunkSize", label: "Chunk Size", type: "number", min: 50, max: 4000, default: "" },
            { key: "minChunkLengthToEmbed", label: "Min Chunk Length", type: "number", min: 0, max: 1000, default: "" },
            { key: "minChunkSizeChars", label: "Min Chunk Size Chars", type: "number", min: 0, max: 1000, default: "" },
            {
                key: "similarityThreshold",
                label: "Similarity Threshold",
                type: "range",
                min: 0.1,
                max: 1.0,
                step: 0.1,
                default: 0.3,
            }, { key: "maxNumChunks", label: "Max Num Chunk", type: "number", min: 0, max: 1000, default: "" },
            { key: "topK", label: "Top K", type: "number", min: 1, default: "" },
            { key: "seperator", label: "Seperator", type: "toggle", default: true },
            { key: "enablePrivateMode", label: "Private Mode", type: "toggle", default: true },

        ],
    },

    // Fallback used for "chat" and any type without a registry entry.
    default: {
        isFileType: false,
        fields: [],
    },
};

export const getTypeConfig = (type, models) => {
    const config = TYPE_FIELD_CONFIGS[type] || TYPE_FIELD_CONFIGS.default;

    if (type !== "rag" || !models) {
        return config;
    }

    const embeddingModels = getEmbeddingModels(models);

    return {
        ...config,
        fields: config.fields.map(field =>
            field.key === "vectorStore"
                ? {
                    ...field,
                    options: embeddingModels,
                }
                : field
        ),
    };
};

const getEmbeddingModels = (models) => {
    const embeddingModelIds = [
        ...models.free,
        ...models.paid,
    ].filter(model => model.type.includes("embedding")).map(model => model.id);
    return embeddingModelIds;


}

export const isFileType = (type) => getTypeConfig(type).isFileType;

export const getDefaultFieldValues = (type) => {
    const { fields } = getTypeConfig(type);
    if (!fields || fields.length === 0) return {};
    return fields.reduce((acc, f) => ({ ...acc, [f.key]: f.default }), {});
};

// Appends type-specific field values onto a FormData instance (used by
// file-based types like vision/classification/rag-with-files in the future).
// Primitives are appended as-is; objects/arrays are JSON.stringify'd so the
// backend gets a predictable shape regardless of field type.
export const appendFieldValuesToFormData = (fd, values) => {
    Object.entries(values || {}).forEach(([key, value]) => {
        if (value === undefined || value === null || value === "") return;
        fd.append(key, typeof value === "object" ? JSON.stringify(value) : value);
    });
};