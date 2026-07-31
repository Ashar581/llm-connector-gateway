export default function Button({
  children,
  onClick,
  variant = "primary",
  size = "md",
  disabled = false,
  className = "",
}) {
  const base = "uppercase tracking-wider font-bold transition-all duration-200 rounded disabled:opacity-30 disabled:cursor-not-allowed";

  const sizes = {
    sm: "text-xs px-3 py-1.5",
    md: "text-xs px-5 py-2.5",
    lg: "text-xs px-8 py-3",
  };

  const variants = {
    primary: "bg-amber-400 text-black hover:bg-amber-300",
    outline: "border border-amber-400/40 text-amber-500 hover:bg-amber-400/10",
    ghost:   "border border-[var(--border-strong)] text-[var(--text-muted)] hover:border-amber-400/40 hover:text-amber-500",
    danger:  "border border-red-400/30 text-red-500 hover:bg-red-400/10",
    success: "border border-emerald-400/30 text-emerald-500 hover:bg-emerald-400/10",
  };

  return (
    <button
      onClick={onClick}
      disabled={disabled}
      className={`${base} ${sizes[size]} ${variants[variant]} ${className}`}
    >
      {children}
    </button>
  );
}
