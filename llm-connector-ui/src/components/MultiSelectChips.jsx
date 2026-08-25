import { useEffect, useMemo, useRef, useState } from "react";

/**
 * MultiSelectChips (searchable dropdown)
 * ───────────────────────────────────────
 * Closed state: a single control showing up to `summaryLimit` selected
 * items as small pills, then "+N more" for the rest — e.g. "FIN, OPS +4 more".
 * Open state: a dropdown panel with a search box and a checkbox-style list.
 *
 * options: [{ value, label, sublabel? }]
 * selected: string[] (values)
 */
export default function MultiSelectChips({
    options = [],
    selected = [],
    onChange,
    placeholder = "Search…",
    emptyMessage = "Nothing available yet",
    hintWhenRestricted,
    accent = "rgb(56,189,248)",
    accentBg = "rgba(56,189,248,0.12)",
    accentBorder = "rgba(56,189,248,0.4)",
    maxHeight = "240px",
    summaryLimit = 2,
}) {
    const [open, setOpen] = useState(false);
    const [query, setQuery] = useState("");
    const containerRef = useRef(null);

    useEffect(() => {
        const handleOutside = (e) => {
            if (containerRef.current && !containerRef.current.contains(e.target)) {
                setOpen(false);
            }
        };
        const handleEsc = (e) => { if (e.key === "Escape") setOpen(false); };
        document.addEventListener("mousedown", handleOutside);
        document.addEventListener("keydown", handleEsc);
        return () => {
            document.removeEventListener("mousedown", handleOutside);
            document.removeEventListener("keydown", handleEsc);
        };
    }, []);

    // Close (and clear search) whenever the option set changes underneath us,
    // e.g. a group selection narrowed the available roles.
    useEffect(() => {
        setQuery("");
    }, [options]);

    const toggle = (value) => {
        if (selected.includes(value)) onChange(selected.filter((v) => v !== value));
        else onChange([...selected, value]);
    };

    const selectedOptions = useMemo(
        () =>
            selected.map(
                (v) => options.find((o) => o.value === v) ?? { value: v, label: v }
            ),
        [selected, options]
    );

    const filtered = useMemo(() => {
        const q = query.trim().toLowerCase();
        if (!q) return options;
        return options.filter(
            (o) =>
                o.label.toLowerCase().includes(q) ||
                (o.sublabel ?? "").toLowerCase().includes(q)
        );
    }, [options, query]);

    const visibleSummary = selectedOptions.slice(0, summaryLimit);
    const extraCount = selectedOptions.length - visibleSummary.length;
    // Only disable the control when there's truly nothing to show or pick —
    // if the catalog is empty but the record already has selections (e.g. a
    // role assigned outside any group mapping), keep it open so those stay
    // visible and removable.
    const isEmpty = options.length === 0 && selectedOptions.length === 0;

    return (
        <div className="relative" ref={containerRef}>
            <button
                type="button"
                onClick={() => !isEmpty && setOpen((o) => !o)}
                disabled={isEmpty}
                className="w-full flex items-center justify-between gap-2 rounded px-3 py-2.5 text-xs transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
                style={{
                    backgroundColor: "var(--bg-primary)",
                    border: `1px solid ${open ? accentBorder : "var(--border)"}`,
                    color: "var(--text-secondary)",
                }}
            >
                <span className="flex flex-wrap items-center gap-1.5 min-h-[18px] text-left">
                    {selectedOptions.length === 0 ? (
                        <span style={{ color: "var(--text-faint)" }}>
                            {isEmpty ? emptyMessage : "Select…"}
                        </span>
                    ) : (
                        <>
                            {visibleSummary.map((opt) => (
                                <span
                                    key={opt.value}
                                    className="inline-flex items-center gap-1 pl-2 pr-1 py-0.5 rounded-full text-[10px] uppercase tracking-wider font-bold"
                                    style={{ backgroundColor: accentBg, border: `1px solid ${accentBorder}`, color: accent }}
                                >
                                    {opt.sublabel || opt.label}
                                    <span
                                        role="button"
                                        tabIndex={-1}
                                        onClick={(e) => {
                                            e.stopPropagation();
                                            onChange(selected.filter((v) => v !== opt.value));
                                        }}
                                        className="w-3 h-3 rounded-full flex items-center justify-center leading-none hover:opacity-70"
                                        style={{ backgroundColor: accent, color: "var(--bg-secondary)" }}
                                        title="Remove"
                                    >
                                        ✕
                                    </span>
                                </span>
                            ))}
                            {extraCount > 0 && (
                                <span
                                    className="text-[10px] px-1.5 py-0.5 rounded-full"
                                    style={{ backgroundColor: "var(--bg-subtle)", color: "var(--text-muted)" }}
                                >
                                    +{extraCount} more
                                </span>
                            )}
                        </>
                    )}
                </span>
                <svg
                    width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5"
                    style={{ color: "var(--text-faint)", transform: open ? "rotate(180deg)" : "none", transition: "transform 0.2s", flexShrink: 0 }}
                >
                    <polyline points="6 9 12 15 18 9" />
                </svg>
            </button>

            {open && !isEmpty && (
                <div
                    className="absolute z-30 mt-1.5 w-full rounded-lg shadow-2xl overflow-hidden"
                    style={{ backgroundColor: "var(--bg-secondary)", border: "1px solid var(--border)" }}
                >
                    <div className="p-2" style={{ borderBottom: "1px solid var(--border)" }}>
                        <input
                            autoFocus
                            type="text"
                            value={query}
                            onChange={(e) => setQuery(e.target.value)}
                            placeholder={placeholder}
                            className="input-theme w-full rounded px-3 py-2 text-xs"
                        />
                    </div>
                    <div
                        className="overflow-y-auto scrollbar-thin scrollbar-track-transparent"
                        style={{ maxHeight }}
                    >
                        {filtered.length === 0 ? (
                            <p className="text-xs px-3 py-3" style={{ color: "var(--text-faint)" }}>
                                {options.length === 0 ? emptyMessage : `No matches for "${query}"`}
                            </p>
                        ) : (
                            filtered.map((opt) => {
                                const isSelected = selected.includes(opt.value);
                                return (
                                    <button
                                        type="button"
                                        key={opt.value}
                                        onClick={() => toggle(opt.value)}
                                        className="w-full flex items-center gap-2 px-3 py-2 text-left text-xs transition-colors"
                                        style={{ backgroundColor: isSelected ? accentBg : "transparent" }}
                                        onMouseEnter={(e) => { if (!isSelected) e.currentTarget.style.backgroundColor = "var(--bg-subtle)"; }}
                                        onMouseLeave={(e) => { if (!isSelected) e.currentTarget.style.backgroundColor = isSelected ? accentBg : "transparent"; }}
                                    >
                                        <span
                                            className="w-3.5 h-3.5 rounded flex items-center justify-center flex-shrink-0"
                                            style={isSelected ? { backgroundColor: accent } : { border: "1px solid var(--border-strong)" }}
                                        >
                                            {isSelected && (
                                                <svg width="8" height="8" viewBox="0 0 24 24" fill="none" stroke="black" strokeWidth="3.5">
                                                    <polyline points="20 6 9 17 4 12" />
                                                </svg>
                                            )}
                                        </span>
                                        <span style={{ color: "var(--text-secondary)" }}>{opt.label}</span>
                                        {opt.sublabel && (
                                            <span style={{ color: "var(--text-faint)" }}>· {opt.sublabel}</span>
                                        )}
                                    </button>
                                );
                            })
                        )}
                    </div>
                </div>
            )}

            {hintWhenRestricted && (
                <p className="text-[11px] mt-1.5" style={{ color: "var(--text-faint)" }}>
                    {hintWhenRestricted}
                </p>
            )}
        </div>
    );
}
