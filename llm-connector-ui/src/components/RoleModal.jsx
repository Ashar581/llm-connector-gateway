import { useEffect, useState } from "react";
import Button from "./Button";

const defaultForm = { name: "", description: "" };

function extractApiError(e) {
    const data = e?.response?.data;
    if (data?.message) return data.message;
    if (typeof data === "string" && data.length) return data;
    return e?.message ?? "An unexpected error occurred.";
}

export default function RoleModal({ open, onClose, role, onSave, loading = false }) {
    const [form, setForm] = useState(defaultForm);
    const [saving, setSaving] = useState(false);
    const [errors, setErrors] = useState({});

    // Derived strictly from whether the record we were handed has an id —
    // this is what decides add vs. update, so keep it unambiguous.
    const isEdit = Boolean(role?.id);

    useEffect(() => {
        if (role) {
            setForm({ name: role.name ?? "", description: role.description ?? "" });
        } else {
            setForm(defaultForm);
        }
        setErrors({});
    }, [role, open]);

    useEffect(() => {
        const handler = (e) => { if (e.key === "Escape") onClose(); };
        window.addEventListener("keydown", handler);
        return () => window.removeEventListener("keydown", handler);
    }, [onClose]);

    const set = (key, value) => {
        setForm((prev) => ({ ...prev, [key]: value }));
        setErrors((prev) => ({ ...prev, [key]: null }));
    };

    const validate = () => {
        const errs = {};
        if (!form.name?.trim()) errs.name = "Role name is mandatory.";
        setErrors(errs);
        return Object.keys(errs).length === 0;
    };

    const handleSubmit = async () => {
        if (!validate()) return;
        setSaving(true);
        try {
            const payload = {
                ...(isEdit ? { id: role.id, code: role.code } : {}),
                name: form.name.trim(),
                description: form.description.trim(),
            };
            await onSave(payload, isEdit);
            onClose();
        } catch (e) {
            setErrors((prev) => ({ ...prev, form: extractApiError(e) }));
        } finally {
            setSaving(false);
        }
    };

    if (!open) return null;

    return (
        <div
            className="fixed inset-0 z-50 flex items-center justify-center p-4"
            onClick={(e) => { if (e.target === e.currentTarget) onClose(); }}
        >
            <div className="absolute inset-0" style={{ backgroundColor: "rgba(0,0,0,0.6)", backdropFilter: "blur(4px)" }} />

            <div
                className="relative z-10 w-full max-w-lg rounded-xl shadow-2xl"
                style={{ backgroundColor: "var(--bg-secondary)", border: "1px solid var(--border)" }}
            >
                <div
                    className="flex items-center justify-between px-6 py-5"
                    style={{ borderBottom: "1px solid var(--border)" }}
                >
                    <div>
                        <p className="text-xs tracking-[0.25em] text-violet-400 uppercase mb-1">
                            ◆ {isEdit ? "Edit Role" : "New Role"}
                        </p>
                        <h2 className="text-lg font-black tracking-tight" style={{ color: "var(--text-primary)" }}>
                            {isEdit ? role.name : "Configure Role"}
                        </h2>
                        {isEdit && role.code && (
                            <span
                                className="inline-block mt-1.5 text-[10px] font-mono px-2 py-0.5 rounded"
                                style={{ backgroundColor: "var(--bg-subtle)", color: "var(--text-faint)" }}
                            >
                                {role.code}
                            </span>
                        )}
                        {loading && (
                            <p className="text-[11px] mt-1.5 animate-pulse" style={{ color: "var(--text-faint)" }}>
                                Refreshing latest data…
                            </p>
                        )}
                    </div>
                    <button
                        onClick={onClose}
                        className="transition-colors text-xl leading-none hover:text-violet-400"
                        style={{ color: "var(--text-faint)" }}
                    >
                        ✕
                    </button>
                </div>

                <div className="px-6 py-6 space-y-5">
                    {errors.form && (
                        <div
                            className="rounded px-4 py-3 text-xs border"
                            style={{ backgroundColor: "rgba(248,113,113,0.08)", borderColor: "rgba(248,113,113,0.25)", color: "#f87171" }}
                        >
                            {errors.form}
                        </div>
                    )}

                    <Field label="Role Name" error={errors.name} required>
                        <input
                            type="text"
                            value={form.name}
                            onChange={(e) => set("name", e.target.value)}
                            placeholder="e.g. Billing Manager"
                            className={`input-theme w-full rounded px-4 py-2.5 text-sm ${errors.name ? "border-red-400/40" : ""}`}
                        />
                    </Field>

                    <Field label="Description">
                        <textarea
                            value={form.description}
                            onChange={(e) => set("description", e.target.value)}
                            placeholder="What this role is for…"
                            rows={3}
                            className="input-theme w-full rounded px-4 py-2.5 text-sm resize-none"
                        />
                    </Field>

                    {isEdit && Array.isArray(role.groups) && role.groups.length > 0 && (
                        <Field label="Mapped Groups" hint="Read-only">
                            <div className="flex flex-wrap gap-1.5">
                                {role.groups.map((g, i) => {
                                    const label = typeof g === "string" ? g : (g.name ?? g.code ?? String(g));
                                    return (
                                        <span
                                            key={i}
                                            className="text-[10px] px-2 py-0.5 rounded-full uppercase tracking-wider"
                                            style={{ backgroundColor: "rgba(52,211,153,0.1)", border: "1px solid rgba(52,211,153,0.25)", color: "rgb(52,211,153)" }}
                                        >
                                            {label}
                                        </span>
                                    );
                                })}
                            </div>
                        </Field>
                    )}

                    {!isEdit && (
                        <p className="text-[11px]" style={{ color: "var(--text-faint)" }}>
                            The role code is generated automatically once created.
                        </p>
                    )}
                </div>

                <div
                    className="flex items-center justify-between px-6 py-4"
                    style={{ borderTop: "1px solid var(--border)" }}
                >
                    <Button variant="ghost" onClick={onClose}>Cancel</Button>
                    <Button variant="primary" onClick={handleSubmit} disabled={saving}>
                        {saving ? (isEdit ? "Saving..." : "Creating...") : isEdit ? "Save Changes" : "Create Role"}
                    </Button>
                </div>
            </div>
        </div>
    );
}

function Field({ label, error, hint, required, children }) {
    return (
        <div>
            <div className="flex items-center justify-between mb-1.5">
                <label className="text-xs uppercase tracking-wider" style={{ color: "var(--text-muted)" }}>
                    {label}{required && <span className="text-violet-400 ml-0.5">*</span>}
                </label>
                {hint && <span className="text-xs" style={{ color: "var(--text-faint)" }}>{hint}</span>}
            </div>
            {children}
            {error && <p className="text-xs text-red-400 mt-1">{error}</p>}
        </div>
    );
}
