export default function Badge({ status }) {
  return (
    <span
      className={`text-xs px-3 py-1 rounded border uppercase tracking-wider ${
        status === "active"
          ? "border-emerald-400/30 text-emerald-500 bg-emerald-400/10"
          : "border-[var(--border-strong)] text-[var(--text-muted)] bg-[var(--bg-subtle)]"
      }`}
    >
      {status}
    </span>
  );
}
