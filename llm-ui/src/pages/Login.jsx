import { useState, useEffect } from "react";
import { useNavigate, useLocation } from "react-router";
import { useAuth } from "../context/AuthContext";
import { loginRequest } from "../services/authService";
import toast from "react-hot-toast";

// ── Inline icons (matches the pattern used in App.jsx) ───────────────────────
const LockIcon = () => (
    <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24"
        fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
        <rect x="3" y="11" width="18" height="11" rx="2" ry="2" />
        <path d="M7 11V7a5 5 0 0 1 10 0v4" />
    </svg>
);

const MailIcon = () => (
    <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24"
        fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
        <path d="M4 4h16c1.1 0 2 .9 2 2v12c0 1.1-.9 2-2 2H4c-1.1 0-2-.9-2-2V6c0-1.1.9-2 2-2z" />
        <polyline points="22,6 12,13 2,6" />
    </svg>
);

const EyeIcon = () => (
    <svg xmlns="http://www.w3.org/2000/svg" width="15" height="15" viewBox="0 0 24 24"
        fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
        <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z" />
        <circle cx="12" cy="12" r="3" />
    </svg>
);

const EyeOffIcon = () => (
    <svg xmlns="http://www.w3.org/2000/svg" width="15" height="15" viewBox="0 0 24 24"
        fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
        <path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94" />
        <path d="M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19" />
        <line x1="1" y1="1" x2="23" y2="23" />
    </svg>
);

// ── Validation helpers ────────────────────────────────────────────────────────
const validateEmail = (v) => /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(v.trim());
const validatePassword = (v) => v.length >= 6;

export default function Login() {
    const navigate = useNavigate();
    const location = useLocation();
    const { login, isAuthenticated } = useAuth();

    const from = "/";

    // Redirect already-authenticated users immediately.
    useEffect(() => {
        if (isAuthenticated) navigate(from, { replace: true });
    }, [isAuthenticated, navigate, from]);

    // ── Form state ──────────────────────────────────────────────────────────
    const [email, setEmail] = useState("");
    const [password, setPassword] = useState("");
    const [showPwd, setShowPwd] = useState(false);
    const [loading, setLoading] = useState(false);
    const [errors, setErrors] = useState({});
    const [loaded, setLoaded] = useState(false);

    useEffect(() => { setTimeout(() => setLoaded(true), 60); }, []);

    // ── Client-side validation ──────────────────────────────────────────────
    const validateEmailOrUsername = (value) => {
        const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

        // Username: letters, numbers, underscore, minimum 3 chars
        const usernameRegex = /^[a-zA-Z][a-zA-Z0-9_]{2,}$/;

        return emailRegex.test(value) || usernameRegex.test(value);
    };

    const validate = () => {
        const e = {};

        if (!email.trim()) {
            e.email = "Email or username is required.";
        } else if (!validateEmailOrUsername(email.trim())) {
            e.email = "Enter a valid email address or username.";
        }

        if (!password) {
            e.password = "Password is required.";
        } else if (!validatePassword(password)) {
            e.password = "Password must be at least 6 characters.";
        }

        setErrors(e);
        return Object.keys(e).length === 0;
    };

    // Clear a field error as soon as the user starts correcting it.
    const clearError = (field) =>
        setErrors((prev) => { const next = { ...prev }; delete next[field]; return next; });

    // ── Submit ──────────────────────────────────────────────────────────────
    const handleSubmit = async (e) => {
        e.preventDefault();
        if (!validate()) return;

        setLoading(true);
        try {
            let data = await loginRequest(email.trim(), password);
            console.log(data);
            data = {
                access_token: data.data.data.token,
                refresh_token: data.data.data.refreshToken,
                user: data.data.data.user
            };
            login(data); // persist tokens + user in AuthContext / localStorage
            toast.success("Logged in successfully");
            navigate(from, { replace: true });
        } catch (err) {
            const msg =
                err.response?.data?.message ||
                err.response?.data?.detail ||
                "Invalid credentials. Please try again.";
            toast.error(msg);
            setErrors({ form: msg });
        } finally {
            setLoading(false);
        }
    };

    // ── Shared style helpers (mirrors Settings.jsx) ──────────────────────────
    const inputCls =
        "input-theme w-full rounded px-4 py-2.5 text-sm font-mono pl-10";

    const errorInputCls =
        "input-theme w-full rounded px-4 py-2.5 text-sm font-mono pl-10 border-red-400/50 focus:border-red-400/70";

    return (
        <div
            className="min-h-screen font-mono flex items-center justify-center relative overflow-hidden transition-colors duration-300"
            style={{ backgroundColor: "var(--bg-primary)", color: "var(--text-primary)" }}
        >
            {/* Grid background */}
            <div
                className="pointer-events-none fixed inset-0 z-0 opacity-20"
                style={{
                    backgroundImage: `
            linear-gradient(var(--grid-color) 1px, transparent 1px),
            linear-gradient(90deg, var(--grid-color) 1px, transparent 1px)
          `,
                    backgroundSize: "40px 40px",
                }}
            />

            {/* Ambient glow */}
            <div
                className="pointer-events-none fixed inset-0 z-0"
                style={{
                    background:
                        "radial-gradient(600px circle at 50% 40%, var(--glow-color), transparent 70%)",
                }}
            />

            {/* Card */}
            <div
                className={`relative z-10 w-full max-w-md mx-4 transition-all duration-700 ${loaded ? "opacity-100 translate-y-0" : "opacity-0 translate-y-6"
                    }`}
            >
                {/* Header */}
                <div className="mb-8 text-center">
                    {/* Logo mark */}
                    <div className="inline-flex items-center justify-center w-10 h-10 rounded-lg bg-amber-400 mb-5">
                        <span className="text-black font-black text-sm">AI</span>
                    </div>

                    <p className="text-xs tracking-[0.3em] text-amber-500 uppercase mb-2">
                        ◆ Authentication
                    </p>
                    <h1 className="text-3xl font-black tracking-tight leading-none">
                        Sign{" "}
                        <span className="text-transparent bg-clip-text bg-gradient-to-r from-amber-400 to-violet-400">
                            In
                        </span>
                    </h1>
                    <p className="text-xs mt-2" style={{ color: "var(--text-muted)" }}>
                        Enter your credentials to access the dashboard
                    </p>
                </div>

                {/* Form card */}
                <div
                    className="card-theme rounded-lg p-8 backdrop-blur-sm"
                >
                    {/* Form-level error banner */}
                    {errors.form && (
                        <div
                            className="mb-5 rounded px-4 py-3 text-xs border"
                            style={{
                                backgroundColor: "rgba(248,113,113,0.08)",
                                borderColor: "rgba(248,113,113,0.25)",
                                color: "#f87171",
                            }}
                        >
                            {errors.form}
                        </div>
                    )}

                    <form onSubmit={handleSubmit} noValidate className="space-y-5">
                        {/* Email */}
                        <div>
                            <label
                                className="text-xs uppercase tracking-wider block mb-1.5"
                                style={{ color: "var(--text-muted)" }}
                            >
                                Username/Email Address
                            </label>
                            <div className="relative">
                                <span
                                    className="absolute left-3 top-1/2 -translate-y-1/2"
                                    style={{ color: "var(--text-faint)" }}
                                >
                                    <MailIcon />
                                </span>
                                <input
                                    id="email"
                                    type="email"
                                    autoComplete="email"
                                    placeholder="you@example.com"
                                    value={email}
                                    onChange={(e) => { setEmail(e.target.value); clearError("email"); }}
                                    className={errors.email ? errorInputCls : inputCls}
                                    disabled={loading}
                                />
                            </div>
                            {errors.email && (
                                <p className="text-xs mt-1.5" style={{ color: "#f87171" }}>
                                    {errors.email}
                                </p>
                            )}
                        </div>

                        {/* Password */}
                        <div>
                            <label
                                className="text-xs uppercase tracking-wider block mb-1.5"
                                style={{ color: "var(--text-muted)" }}
                            >
                                Password
                            </label>
                            <div className="relative">
                                <span
                                    className="absolute left-3 top-1/2 -translate-y-1/2"
                                    style={{ color: "var(--text-faint)" }}
                                >
                                    <LockIcon />
                                </span>
                                <input
                                    id="password"
                                    type={showPwd ? "text" : "password"}
                                    autoComplete="current-password"
                                    placeholder="••••••••"
                                    value={password}
                                    onChange={(e) => { setPassword(e.target.value); clearError("password"); }}
                                    className={`${errors.password ? errorInputCls : inputCls} pr-10`}
                                    disabled={loading}
                                />
                                <button
                                    type="button"
                                    onClick={() => setShowPwd((p) => !p)}
                                    className="absolute right-3 top-1/2 -translate-y-1/2 transition-colors duration-200 hover:text-amber-400"
                                    style={{ color: "var(--text-faint)" }}
                                    tabIndex={-1}
                                    aria-label={showPwd ? "Hide password" : "Show password"}
                                >
                                    {showPwd ? <EyeOffIcon /> : <EyeIcon />}
                                </button>
                            </div>
                            {errors.password && (
                                <p className="text-xs mt-1.5" style={{ color: "#f87171" }}>
                                    {errors.password}
                                </p>
                            )}
                        </div>

                        {/* Submit */}
                        <button
                            type="submit"
                            disabled={loading}
                            className="uppercase tracking-wider font-bold transition-all duration-200 rounded disabled:opacity-30 disabled:cursor-not-allowed text-xs px-5 py-2.5 w-full mt-2 bg-amber-400 text-black hover:bg-amber-300"
                        >
                            {loading ? (
                                <span className="flex items-center justify-center gap-2">
                                    <span className="w-3.5 h-3.5 border-2 border-black/30 border-t-black rounded-full animate-spin" />
                                    Signing in…
                                </span>
                            ) : (
                                "Sign In"
                            )}
                        </button>
                    </form>
                </div>

                {/* Footer */}
                <p
                    className="text-center text-xs mt-6"
                    style={{ color: "var(--text-faint)" }}
                >
                    Developer - Ashar581
                </p>
            </div>
        </div>
    );
}