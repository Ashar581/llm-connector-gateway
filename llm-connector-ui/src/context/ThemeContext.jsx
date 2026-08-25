import { createContext, useContext, useEffect, useState } from "react";

// Default value prevents "cannot destructure undefined" if used outside provider
const ThemeContext = createContext({ theme: "dark", toggleTheme: () => { } });

export function ThemeProvider({ children }) {
	const [theme, setTheme] = useState(
		() => localStorage.getItem("theme") ?? "dark"
	);

	// Apply/remove "dark" class on <html> whenever theme changes
	useEffect(() => {
		const root = document.documentElement;
		if (theme === "dark") {
			root.classList.add("dark");
		} else {
			root.classList.remove("dark");
		}
		localStorage.setItem("theme", theme);
	}, [theme]);

	const toggleTheme = () => setTheme((t) => (t === "dark" ? "light" : "dark"));

	return (
		<ThemeContext.Provider value={{ theme, toggleTheme }}>
			{children}
		</ThemeContext.Provider>
	);
}

export function useTheme() {
	const context = useContext(ThemeContext);
	if (!context) {
		throw new Error("useTheme must be used within a ThemeProvider");
	}
	return context;
}