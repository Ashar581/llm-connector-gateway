import { useState, useEffect, useRef } from "react";

/**
 * InstructionsModal
 * Props:
 *  - open         {boolean}  – whether the modal is visible
 *  - value        {string}   – current instructions text
 *  - onChange     {fn}       – called with new string value
 *  - onClose      {fn}       – called when modal should close
 */
export default function InstructionsModal({ open, value, onChange, onClose }) {
    const [draft, setDraft] = useState(value);
    const textareaRef = useRef(null);

    // Sync draft when modal opens
    useEffect(() => {
        if (open) {
            setDraft(value);
            setTimeout(() => {
                textareaRef.current?.focus();
                const len = textareaRef.current?.value?.length ?? 0;
                textareaRef.current?.setSelectionRange(len, len);
            }, 80);
        }
    }, [open]);

    // Close on Escape
    useEffect(() => {
        if (!open) return;
        const handler = (e) => { if (e.key === "Escape") handleDiscard(); };
        window.addEventListener("keydown", handler);
        return () => window.removeEventListener("keydown", handler);
    }, [open, draft]);

    const handleSave = () => {
        onChange(draft);
        onClose();
    };

    const handleDiscard = () => {
        setDraft(value); // revert
        onClose();
    };

    if (!open) return null;

    const charCount = draft.length;

    return (
        <div
            className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 backdrop-blur-sm"
            onClick={(e) => { if (e.target === e.currentTarget) handleDiscard(); }}
        >
            <div className="relative bg-white dark:bg-slate-900 border border-slate-200 dark:border-white/[0.08] rounded-xl shadow-2xl w-[1000px] max-w-[95vw] h-[85vh] flex flex-col">
                {/* Header */}
                <div className="flex items-center justify-between px-5 py-4 border-b border-slate-200 dark:border-white/[0.08] flex-shrink-0">
                    <div className="flex items-center gap-2.5">
                        <div className="w-7 h-7 rounded-lg bg-amber-50 dark:bg-amber-400/10 border border-amber-200 dark:border-amber-400/20 flex items-center justify-center">
                            <svg xmlns="http://www.w3.org/2000/svg" width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className="text-amber-500 dark:text-amber-400">
                                <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" />
                                <polyline points="14 2 14 8 20 8" />
                                <line x1="16" y1="13" x2="8" y2="13" />
                                <line x1="16" y1="17" x2="8" y2="17" />
                                <polyline points="10 9 9 9 8 9" />
                            </svg>
                        </div>
                        <div>
                            <h2 className="text-sm font-bold text-slate-800 dark:text-white leading-none">System Instructions</h2>
                            <p className="text-xs text-slate-400 dark:text-white/30 mt-0.5">System prompt / behaviour override</p>
                        </div>
                    </div>
                    <button
                        onClick={handleDiscard}
                        className="text-slate-400 dark:text-white/20 hover:text-slate-600 dark:hover:text-white/50 transition-colors text-lg leading-none"
                    >
                        ✕
                    </button>
                </div>

                {/* Textarea */}
                <div className="flex-1 scrollbar-thin scrollbar-track-transparent scrollbar-thumb-amber-400/30 hover:scrollbar-thumb-amber-400/50 p-5 min-h-0">
                    <textarea
                        ref={textareaRef}
                        value={draft}
                        onChange={(e) => setDraft(e.target.value)}
                        placeholder="Enter system prompt instructions here…&#10;&#10;Example:&#10;You are a helpful assistant that always responds in bullet points.&#10;Keep answers concise and well-structured."
                        className="
                        w-full h-full
                        bg-slate-50 dark:bg-white/[0.03]
                        border border-slate-200 dark:border-white/[0.08]
                        rounded-lg
                        px-4 py-3
                        text-sm text-slate-700 dark:text-white/70
                        placeholder-slate-400 dark:placeholder-white/20
                        outline-none
                        focus:border-amber-400/50 dark:focus:border-amber-400/30
                        transition-colors
                        resize-none
                        leading-relaxed
                        font-mono
                    "
                    />
                </div>

                {/* Footer */}
                <div className="flex items-center justify-between px-5 py-3 border-t border-slate-200 dark:border-white/[0.08] flex-shrink-0">
                    <span className="text-xs text-slate-400 dark:text-white/20 font-mono">
                        {charCount > 0 ? `${charCount.toLocaleString()} chars` : "No instructions"}
                    </span>
                    <div className="flex gap-2">
                        <button
                            onClick={handleDiscard}
                            className="px-4 py-1.5 text-xs uppercase tracking-wider border border-slate-200 dark:border-white/10 text-slate-500 dark:text-white/40 rounded-lg hover:text-slate-700 dark:hover:text-white/60 transition-colors"
                        >
                            Discard
                        </button>
                        <button
                            onClick={handleSave}
                            className="px-4 py-1.5 text-xs uppercase tracking-wider bg-amber-500 hover:bg-amber-400 text-white rounded-lg transition-colors"
                        >
                            Apply
                        </button>
                    </div>
                </div>
            </div>
        </div>
    );
}