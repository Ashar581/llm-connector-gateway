// ── Role helpers ─────────────────────────────────────────────────────────
// Centralises the "who is an admin" check so App.jsx (nav visibility),
// AdminRoute (route guard), and any page can agree on the same rule.

const ADMIN_CODES = ["SUPER_ADMIN", "SYSTEM_ADMIN"];

/**
 * Normalises whatever shape `user.roles` happens to be (Set serialised as
 * array, array of strings, array of {code,name} objects, or a single
 * `user.role` string) into a flat array of upper-cased role codes.
 */
function extractRoleCodes(user) {
    if (!user) return [];

    const raw = [];

    if (Array.isArray(user.roles)) raw.push(...user.roles);
    else if (user.roles && typeof user.roles === "object") raw.push(...Object.values(user.roles));

    if (user.role) raw.push(user.role);
    if (user.userType) raw.push(user.userType);

    return raw
        .map((r) => (typeof r === "string" ? r : r?.code ?? r?.name ?? ""))
        .filter(Boolean)
        .map((r) => String(r).toUpperCase());
}

export function isAdminUser(user) {
    const codes = extractRoleCodes(user);
    return codes.some((c) => ADMIN_CODES.includes(c));
}

export { ADMIN_CODES };
