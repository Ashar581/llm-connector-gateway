import { createBrowserRouter } from "react-router";
import App from "../App";
import Home from "../pages/Home";
import Agents from "../pages/Agents";
import Playground from "../pages/Playground";
import Settings from "../pages/Settings";
import Stats from "../pages/Stats";
import Login from "../pages/Login";
import Admin from "../pages/Admin";
import ProtectedRoute from "../components/ProtectedRoute";
import AdminRoute from "../components/AdminRoute";

const router = createBrowserRouter([
  // ── Public route ───────────────────────────────────────────────────────
  {
    path: "/login",
    element: <Login />,
  },

  // ── Protected shell — every child requires authentication ──────────────
  {
    path: "/",
    element: (
      <ProtectedRoute>
        <App />
      </ProtectedRoute>
    ),
    children: [
      { index: true, element: <Home /> },
      { path: "agents", element: <Agents /> },
      { path: "playground", element: <Playground /> },
      { path: "settings", element: <Settings /> },
      { path: "stats", element: <Stats /> },
      {
        path: "admin",
        element: (
          <AdminRoute>
            <Admin />
          </AdminRoute>
        ),
      },
    ],
  },
],
    {
      basename: "/ui",
    }
);

export default router;