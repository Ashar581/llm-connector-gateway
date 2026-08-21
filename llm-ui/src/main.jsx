import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { RouterProvider } from "react-router";
import { Toaster } from "react-hot-toast";
import { ThemeProvider } from "./context/ThemeContext";
import { AuthProvider } from "./context/AuthContext";   // ← NEW
import { RbacProvider } from "./context/RbacContext";
import router from "./routers/routes";
import "./index.css";

createRoot(document.getElementById("root")).render(
  <StrictMode>
    <ThemeProvider>
      {/*
        AuthProvider sits inside ThemeProvider so login/logout toasts
        can inherit the themed Toaster below. It wraps RouterProvider
        so every route (including the Login page) can call useAuth().
      */}
      <AuthProvider>
        {/*
          RbacProvider sits inside AuthProvider so it can be paired with
          useAuth() wherever route access is checked (RbacRoute, App.jsx
          nav, and the admin-only reconciliation on load). It loads the
          route-access config from the real /v1/settings backend — see
          services/rbacService.jsx.
        */}
        <RbacProvider>
          <RouterProvider router={router} />

        <Toaster
          position="bottom-right"
          gutter={10}
          toastOptions={{
            duration: 4000,
            style: {
              background: "var(--bg-secondary)",
              border: "1px solid var(--border-strong)",
              boxShadow: "0 8px 32px rgba(0,0,0,0.18)",
              borderRadius: "10px",
              padding: "12px 16px",
              color: "var(--text-secondary)",
              fontFamily: "ui-monospace, monospace",
              fontSize: "12px",
              letterSpacing: "0.03em",
              maxWidth: "360px",
            },
            success: {
              duration: 3000,
              iconTheme: { primary: "#34d399", secondary: "transparent" },
              style: {
                background: "var(--bg-secondary)",
                border: "1px solid rgba(52, 211, 153, 0.25)",
                color: "var(--text-secondary)",
              },
            },
            error: {
              duration: 5000,
              iconTheme: { primary: "#f87171", secondary: "transparent" },
              style: {
                background: "var(--bg-secondary)",
                border: "1px solid rgba(248, 113, 113, 0.25)",
                color: "var(--text-secondary)",
              },
            },
            loading: {
              iconTheme: { primary: "#f59e0b", secondary: "transparent" },
              style: {
                background: "var(--bg-secondary)",
                border: "1px solid rgba(245, 158, 11, 0.20)",
                color: "var(--text-secondary)",
              },
            },
          }}
        />
        </RbacProvider>
      </AuthProvider>
    </ThemeProvider>
  </StrictMode>
);