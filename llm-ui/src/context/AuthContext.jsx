import { createContext, useContext, useState, useCallback } from "react";

/**
 * AuthContext
 * ───────────
 * Stores the logged-in user and exposes login / logout helpers.
 * Tokens are kept in localStorage (access) and a dedicated key (refresh).
 * The token keys are centralised here so apiService.jsx can import them.
 */

export const TOKEN_KEY = "access_token";
export const REFRESH_TOKEN_KEY = "refresh_token";
export const USER_KEY = "auth_user";

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
    // Rehydrate from localStorage so a page refresh keeps the session alive.
    const [user, setUser] = useState(() => {
        try {
            const raw = localStorage.getItem(USER_KEY);
            return raw ? JSON.parse(raw) : null;
        } catch {
            return null;
        }
    });

    const isAuthenticated = Boolean(user && localStorage.getItem(TOKEN_KEY));

    /**
     * Call this after a successful /login API response.
     * Expected shape: { access_token, refresh_token?, user: { id, email, name, ... } }
     */
    const login = useCallback(({ access_token, refresh_token, user: userData }) => {
        localStorage.setItem(TOKEN_KEY, access_token);
        if (refresh_token) {
            localStorage.setItem(REFRESH_TOKEN_KEY, refresh_token);
        }
        localStorage.setItem(USER_KEY, JSON.stringify(userData));
        setUser(userData);
    }, []);

    const logout = useCallback(() => {
        localStorage.removeItem(TOKEN_KEY);
        localStorage.removeItem(REFRESH_TOKEN_KEY);
        localStorage.removeItem(USER_KEY);
        setUser(null);
    }, []);

    return (
        <AuthContext.Provider value={{ user, isAuthenticated, login, logout }}>
            {children}
        </AuthContext.Provider>
    );
}

export function useAuth() {
    const ctx = useContext(AuthContext);
    if (!ctx) throw new Error("useAuth must be used within an AuthProvider");
    return ctx;
}