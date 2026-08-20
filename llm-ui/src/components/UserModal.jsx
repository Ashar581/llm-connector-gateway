import { useEffect, useMemo, useState } from "react";
import Button from "./Button";
import MultiSelectChips from "./MultiSelectChips";

const defaultForm = {
    firstName: "",
    lastName: "",
    email: "",
    phoneNumber: "",
    countryCode: "",
    active: true,
};

function extractApiError(e) {
    const data = e?.response?.data;
    if (data?.message) return data.message;
    if (typeof data === "string" && data.length) return data;
    return e?.message ?? "An unexpected error occurred.";
}

export default function UserModal({ open, onClose, user, allRoles = [], allGroups = [], onSave, loading = false }) {
    const [form, setForm] = useState(defaultForm);
    const [selectedRoleCodes, setSelectedRoleCodes] = useState([]);
    const [selectedGroupCodes, setSelectedGroupCodes] = useState([]);
    const [saving, setSaving] = useState(false);
    const [errors, setErrors] = useState({});

    // Derived strictly from whether the record we were handed has an id —
    // this is what decides add vs. update, so keep it unambiguous.
    const isEdit = Boolean(user?.id);

    useEffect(() => {
        if (user) {
            setForm({
                firstName: user.firstName ?? "",
                lastName: user.lastName ?? "",
                email: user.email ?? "",
                phoneNumber: user.phoneNumber ?? "",
                countryCode: user.countryCode ?? "",
                active: user.active ?? true,
            });
            setSelectedRoleCodes(Array.from(user.roles ?? []));
            setSelectedGroupCodes(Array.from(user.groups ?? []));
        } else {
            setForm(defaultForm);
            setSelectedRoleCodes([]);
            setSelectedGroupCodes([]);
        }
        setErrors({});
    }, [user, open]);

    useEffect(() => {
        const handler = (e) => { if (e.key === "Escape") onClose(); };
        window.addEventListener("keydown", handler);
        return () => window.removeEventListener("keydown", handler);
    }, [onClose]);

    // Roles are restricted to whatever the currently-selected group(s) map
    // to. No group selected → every role is fair game (roles can be given
    // to a user directly, independent of any group). As soon as one or more
    // groups are picked, only the union of their mapped roles is offered,
    // and any previously-picked role that falls outside that union is
    // dropped so the selection can never point at a role invisible to it.
    const groupsByCode = useMemo(() => {
        const map = new Map();
        allGroups.forEach((g) => map.set(g.code, g));
        return map;
    }, [allGroups]);

    // Roles mapped to the selected group(s) are *suggested* — offered as
    // easy picks — but this is deliberately non-destructive: a role a user
    // already holds directly (independent of any group) is never hidden or
    // silently removed just because it isn't mapped to a currently-selected
    // group. Only brand-new picks are steered toward the group's mapped set.
    const allowedRoleCodes = useMemo(() => {
        if (selectedGroupCodes.length === 0) return null; // null = unrestricted
        const codes = new Set();
        selectedGroupCodes.forEach((gc) => {
            const group = groupsByCode.get(gc);
            (group?.roles ?? []).forEach((r) => codes.add(r.code));
        });
        return codes;
    }, [selectedGroupCodes, groupsByCode]);

    const set = (key, value) => {
        setForm((prev) => ({ ...prev, [key]: value }));
        setErrors((prev) => ({ ...prev, [key]: null }));
    };

    const validate = () => {
        const errs = {};
        if (!form.firstName?.trim()) errs.firstName = "First name is mandatory.";
        if (!form.lastName?.trim()) errs.lastName = "Last name is mandatory.";
        if (!form.email?.trim()) {
            errs.email = "Email is mandatory.";
        } else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email.trim())) {
            errs.email = "Enter a valid email address.";
        }
        setErrors(errs);
        return Object.keys(errs).length === 0;
    };

    const handleSubmit = async () => {
        if (!validate()) return;
        setSaving(true);
        try {
            const payload = {
                ...(isEdit ? { id: user.id } : {}),
                firstName: form.firstName.trim(),
                lastName: form.lastName.trim(),
                email: form.email.trim(),
                phoneNumber: form.phoneNumber.trim() || undefined,
                countryCode: form.countryCode.trim() || undefined,
                active: form.active,
                groups: selectedGroupCodes,
                roles: selectedRoleCodes,
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

    const groupOptions = allGroups.map((g) => ({ value: g.code, label: g.name, sublabel: g.code }));
    const roleOptions = allRoles
        .filter((r) => !allowedRoleCodes || allowedRoleCodes.has(r.code) || selectedRoleCodes.includes(r.code))
        .map((r) => ({ value: r.code, label: r.name, sublabel: r.code }));

    return (
        <div
            className="fixed inset-0 z-50 flex items-center justify-center p-4"
            onClick={(e) => { if (e.target === e.currentTarget) onClose(); }}
        >
            <div className="absolute inset-0" style={{ backgroundColor: "rgba(0,0,0,0.6)", backdropFilter: "blur(4px)" }} />

            <div
                className="relative z-10 w-full max-w-2xl max-h-[90vh] overflow-y-auto scrollbar-thin scrollbar-track-transparent rounded-xl shadow-2xl"
                style={{ backgroundColor: "var(--bg-secondary)", border: "1px solid var(--border)" }}
            >
                <div
                    className="sticky top-0 z-10 flex items-center justify-between px-6 py-5"
                    style={{ borderBottom: "1px solid var(--border)", backgroundColor: "var(--bg-secondary)" }}
                >
                    <div>
                        <p className="text-xs tracking-[0.25em] text-amber-500 uppercase mb-1">
                            ◆ {isEdit ? "Edit User" : "New User"}
                        </p>
                        <h2 className="text-lg font-black tracking-tight" style={{ color: "var(--text-primary)" }}>
                            {isEdit ? (user.fullname || `${user.firstName} ${user.lastName}`) : "Configure User"}
                        </h2>
                        {isEdit && user.username && (
                            <span
                                className="inline-block mt-1.5 text-[10px] font-mono px-2 py-0.5 rounded"
                                style={{ backgroundColor: "var(--bg-subtle)", color: "var(--text-faint)" }}
                            >
                                @{user.username}
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
                        className="transition-colors text-xl leading-none hover:text-amber-500"
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

                    <div className="grid grid-cols-2 gap-4">
                        <Field label="First Name" error={errors.firstName} required>
                            <input
                                type="text"
                                value={form.firstName}
                                onChange={(e) => set("firstName", e.target.value)}
                                placeholder="Jane"
                                className={`input-theme w-full rounded px-4 py-2.5 text-sm ${errors.firstName ? "border-red-400/40" : ""}`}
                            />
                        </Field>
                        <Field label="Last Name" error={errors.lastName} required>
                            <input
                                type="text"
                                value={form.lastName}
                                onChange={(e) => set("lastName", e.target.value)}
                                placeholder="Doe"
                                className={`input-theme w-full rounded px-4 py-2.5 text-sm ${errors.lastName ? "border-red-400/40" : ""}`}
                            />
                        </Field>
                    </div>

                    <Field label="Email" error={errors.email} required>
                        <input
                            type="email"
                            value={form.email}
                            onChange={(e) => set("email", e.target.value)}
                            placeholder="jane.doe@example.com"
                            className={`input-theme w-full rounded px-4 py-2.5 text-sm ${errors.email ? "border-red-400/40" : ""}`}
                        />
                    </Field>

                    <div className="grid grid-cols-3 gap-4">
                        <div className="col-span-1">
                            <Field label="Country Code">
                                <input
                                    type="text"
                                    value={form.countryCode}
                                    onChange={(e) => set("countryCode", e.target.value)}
                                    placeholder="+1"
                                    className="input-theme w-full rounded px-4 py-2.5 text-sm"
                                />
                            </Field>
                        </div>
                        <div className="col-span-2">
                            <Field label="Phone Number">
                                <input
                                    type="text"
                                    value={form.phoneNumber}
                                    onChange={(e) => set("phoneNumber", e.target.value)}
                                    placeholder="5550123456"
                                    className="input-theme w-full rounded px-4 py-2.5 text-sm"
                                />
                            </Field>
                        </div>
                    </div>

                    <Field label="Groups" hint="Optional">
                        <MultiSelectChips
                            options={groupOptions}
                            selected={selectedGroupCodes}
                            onChange={setSelectedGroupCodes}
                            placeholder="Search groups…"
                            emptyMessage="No groups created yet."
                            accent="rgb(52,211,153)"
                            accentBg="rgba(52,211,153,0.12)"
                            accentBorder="rgba(52,211,153,0.4)"
                        />
                    </Field>

                    <Field label="Roles" hint="Optional">
                        <MultiSelectChips
                            options={roleOptions}
                            selected={selectedRoleCodes}
                            onChange={setSelectedRoleCodes}
                            placeholder="Search roles…"
                            emptyMessage={
                                allowedRoleCodes
                                    ? "The selected group(s) don't have any roles mapped yet."
                                    : "No roles created yet."
                            }
                            accent="rgb(167,139,250)"
                            accentBg="rgba(167,139,250,0.12)"
                            accentBorder="rgba(167,139,250,0.4)"
                            hintWhenRestricted={
                                allowedRoleCodes
                                    ? "New picks are suggested from the selected group(s)' mapped roles — roles already assigned directly stay untouched."
                                    : undefined
                            }
                        />
                    </Field>

                    <Toggle
                        label="Active User"
                        hint="Inactive users cannot sign in"
                        value={form.active}
                        onChange={(v) => set("active", v)}
                    />

                    {!isEdit && (
                        <p className="text-[11px]" style={{ color: "var(--text-faint)" }}>
                            Username and password are generated automatically once the user is created.
                        </p>
                    )}
                </div>

                <div
                    className="sticky bottom-0 flex items-center justify-between px-6 py-4"
                    style={{ borderTop: "1px solid var(--border)", backgroundColor: "var(--bg-secondary)" }}
                >
                    <Button variant="ghost" onClick={onClose}>Cancel</Button>
                    <Button variant="primary" onClick={handleSubmit} disabled={saving}>
                        {saving ? (isEdit ? "Saving..." : "Creating...") : isEdit ? "Save Changes" : "Create User"}
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
                    {label}{required && <span className="text-amber-500 ml-0.5">*</span>}
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
                className={`w-10 h-5 rounded-full transition-all duration-300 relative flex-shrink-0 ${value ? "bg-amber-400" : ""}`}
                style={value ? {} : { backgroundColor: "var(--border)" }}
            >
                <span className={`absolute top-0.5 w-4 h-4 bg-white rounded-full shadow transition-all duration-300 ${value ? "left-5" : "left-0.5"}`} />
            </button>
        </div>
    );
}
