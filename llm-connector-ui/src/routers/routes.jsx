import { createBrowserRouter } from "react-router";
import { APP_BASENAME } from "../constants/app";
import App from "../App";
import Home from "../pages/Home";
import Agents from "../pages/Agents";
import Playground from "../pages/Playground";
import Settings from "../pages/Settings";
import Stats from "../pages/Stats";
import Profile from "../pages/Profile";
import Login from "../pages/Login";
import Admin from "../pages/Admin";
import ProtectedRoute from "../components/ProtectedRoute";
import AdminRoute from "../components/AdminRoute";
import RbacRoute from "../components/RbacRoute";

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
      { index: true, element: <RbacRoute routePath="/"><Home /></RbacRoute> },
      { path: "agents", element: <RbacRoute routePath="/agents"><Agents /></RbacRoute> },
      { path: "playground", element: <RbacRoute routePath="/playground"><Playground /></RbacRoute> },
      { path: "settings", element: <Settings /> },
      { path: "profile", element: <Profile /> },
      { path: "stats", element: <RbacRoute routePath="/stats"><Stats /></RbacRoute> },
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
      basename: APP_BASENAME,
    }
);

export default router;