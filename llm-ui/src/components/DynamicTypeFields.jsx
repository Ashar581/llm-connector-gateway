// Renders whatever `fields` a type declares in `typeFieldConfigs.js`.
// This is the piece that makes new types "free" to add: as long as a type's
// extra config is a flat list of text/number/select/toggle fields, this
// component draws it — no new JSX needs to be written in Playground.jsx.
//
// Types with a genuinely different shape of config (e.g. classification's
// repeatable document-type list) use a CustomPanel instead — see
// ClassificationConfigPanel.jsx for that pattern.

function FieldLabel({ children }) {
    return (
        <div className="flex justify-between items-center text-xs mb-1.5">
            <span className="uppercase tracking-wider" style={{ color: "var(--text-muted)" }}>{children}</span>
        </div>
    );
}

function TypeField({ field, value, onChange }) {
    const commit = (raw) => {
        if (field.validate && !field.validate(raw)) return;
        onChange(raw);
    };

    switch (field.type) {
        case "select":
            return (
                <div>
                    <FieldLabel>{field.label}</FieldLabel>
                    <div className="flex flex-wrap gap-1.5">
                        {field.options.map((opt) => (
                            <button
                                key={opt}
                                type="button"
                                onClick={() => commit(opt)}
                                className="px-2.5 py-1.5 text-xs rounded-lg transition-all duration-200"
                                style={value === opt
                                    ? { backgroundColor: "rgba(217,119,6,0.1)", border: "1px solid rgba(217,119,6,0.3)", color: "rgb(245,158,11)" }
                                    : { border: "1px solid var(--border)", color: "var(--text-muted)" }
                                }
                            >
                                {opt}
                            </button>
                        ))}
                    </div>
                </div>
            );

        case "toggle":
            return (
                <div className="flex items-center justify-between">
                    <div>
                        <div className="text-xs uppercase tracking-wider" style={{ color: "var(--text-muted)" }}>{field.label}</div>
                        {field.description && <div className="text-xs mt-0.5" style={{ color: "var(--text-faint)" }}>{field.description}</div>}
                    </div>
                    <button
                        type="button"
                        onClick={() => commit(!value)}
                        className={`relative w-10 h-5 rounded-full transition-all duration-300 ${value ? "bg-amber-500" : ""}`}
                        style={value ? {} : { backgroundColor: "var(--bg-subtle)", border: "1px solid var(--border)" }}
                    >
                        <span className={`absolute top-0.5 w-4 h-4 bg-white rounded-full shadow transition-all duration-300 ${value ? "left-5" : "left-0.5"}`} />
                    </button>
                </div>
            );

        case "textarea":
            return (
                <div>
                    <FieldLabel>{field.label}</FieldLabel>
                    <textarea
                        value={value ?? ""}
                        onChange={(e) => commit(e.target.value)}
                        placeholder={field.placeholder}
                        rows={field.rows || 3}
                        className="input-theme w-full rounded-lg px-3 py-2 text-xs outline-none resize-none"
                    />
                </div>
            );

        case "number":
            return (
                <div>
                    <FieldLabel>{field.label}</FieldLabel>
                    <input
                        type="number"
                        value={value ?? ""}
                        min={field.min}
                        max={field.max}
                        step={field.step}
                        placeholder={field.placeholder}
                        onChange={(e) => commit(e.target.value === "" ? "" : Number(e.target.value))}
                        className="input-theme w-full rounded-lg px-3 py-2 text-xs outline-none"
                    />
                </div>
            );

        case "text":
        default:
            return (
                <div>
                    <FieldLabel>{field.label}</FieldLabel>
                    <input
                        type="text"
                        inputMode={field.inputMode}
                        value={value ?? ""}
                        placeholder={field.placeholder}
                        onChange={(e) => commit(e.target.value)}
                        className="input-theme w-full rounded-lg px-3 py-2 text-xs outline-none"
                    />
                </div>
            );

        case "range":
            return (
                <div>
                    <FieldLabel>{field.label}</FieldLabel>

                    <div className="space-y-2">
                        <input
                            type="range"
                            min={field.min}
                            max={field.max}
                            step={field.step ?? 0.1}
                            value={value ?? field.default}
                            onChange={(e) => commit(Number(e.target.value))}
                            className="w-full accent-amber-500"
                        />

                        <div
                            className="flex justify-between items-center text-xs"
                            style={{ color: "var(--text-muted)" }}
                        >
                            <span>{field.min}</span>

                            <span
                                className="px-2 py-0.5 rounded"
                                style={{
                                    backgroundColor: "var(--bg-subtle)",
                                    border: "1px solid var(--border)",
                                    color: "var(--text-primary)",
                                }}
                            >
                                {(value ?? field.default).toFixed(1)}
                            </span>

                            <span>{field.max}</span>
                        </div>
                    </div>
                </div>
            );
    }
}

export default function DynamicTypeFields({ fields, values, onChange }) {
    if (!fields || fields.length === 0) return null;
    return (
        <div className="rounded-xl p-4 space-y-4" style={{ backgroundColor: "var(--bg-card)", border: "1px solid var(--border)" }}>
            <div className="text-xs uppercase tracking-widest" style={{ color: "var(--text-faint)" }}>Type Options</div>
            {fields.map((f) => (
                <TypeField key={f.key} field={f} value={values[f.key]} onChange={(v) => onChange(f.key, v)} />
            ))}
        </div>
    );
}