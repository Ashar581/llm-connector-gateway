export default function Card({ children, className = "", hoverable = false }) {
  return (
    <div
      className={`card-theme rounded-lg p-6 backdrop-blur-sm
        ${hoverable ? "card-theme-hoverable cursor-pointer" : ""}
        ${className}`}
    >
      {children}
    </div>
  );
}
