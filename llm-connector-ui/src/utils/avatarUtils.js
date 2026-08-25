// ── Avatar helpers ──────────────────────────────────────────────────────

export function getInitials(name) {
    if (!name) return "?";
    const parts = String(name).trim().split(/\s+/).filter(Boolean);
    if (parts.length === 0) return "?";
    if (parts.length === 1) return parts[0].slice(0, 2).toUpperCase();
    return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase();
}

// A handful of theme-matched gradient pairs. Each user gets a stable one,
// picked deterministically from a seed (their username/email/id) so the
// same person always sees the same identity color, without any backend
// support needed for it.
const PALETTE = [
    ["#f59e0b", "#f43f5e"], // amber → rose
    ["#a78bfa", "#6366f1"], // violet → indigo
    ["#34d399", "#0ea5e9"], // emerald → sky
    ["#f472b6", "#a78bfa"], // pink → violet
    ["#22d3ee", "#34d399"], // cyan → emerald
    ["#fb923c", "#f59e0b"], // orange → amber
];

function hashString(str) {
    let hash = 0;
    for (let i = 0; i < str.length; i++) {
        hash = (hash << 5) - hash + str.charCodeAt(i);
        hash |= 0;
    }
    return Math.abs(hash);
}

export function getAvatarGradient(seed) {
    const [from, to] = PALETTE[hashString(String(seed ?? "user")) % PALETTE.length];
    return `linear-gradient(135deg, ${from}, ${to})`;
}
