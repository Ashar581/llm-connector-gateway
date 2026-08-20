export default function PageHeader({ tag, title, highlight, description }) {
  return (
    <div className="mb-10">
      <p className="text-xs tracking-[0.3em] text-amber-500 uppercase mb-3">◆ {tag}</p>
      <h1 className="font-display text-3xl md:text-4xl font-bold tracking-tight leading-none mb-4"
        style={{ color: "var(--text-primary)" }}>
        {title}{" "}
        {highlight && (
          <span className="text-transparent bg-clip-text bg-gradient-to-r from-amber-400 to-violet-400">
            {highlight}
          </span>
        )}
      </h1>
      {description && (
        <p className="text-sm max-w-md leading-relaxed" style={{ color: "var(--text-muted)" }}>
          {description}
        </p>
      )}
    </div>
  );
}
