export default function StatusDot({ status }) {
  return (
    <div
      className={`w-2 h-2 rounded-full flex-shrink-0 ${
        status === "active"
          ? "bg-emerald-400 shadow-[0_0_6px_rgba(52,211,153,0.7)]"
          : "bg-[var(--border-strong)]"
      }`}
    />
  );
}
