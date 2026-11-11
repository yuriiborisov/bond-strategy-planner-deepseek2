import { createBrowserRouter, Navigate } from "react-router-dom";
import { AppLayout } from "./layout/AppLayout";
import { AdminGuard, AuthGuard } from "./guards";
import { HomePage } from "../pages/home/HomePage";
import { StrategyPage } from "../pages/strategy/StrategyPage";
import { LoginPage, RegisterPage } from "../pages/auth/AuthPages";
import { PortfoliosPage } from "../pages/portfolios/PortfoliosPage";
import { PortfolioDetailPage } from "../pages/portfolios/PortfolioDetailPage";
import { AiAnalysisPage } from "../pages/ai/AiAnalysisPage";
import { AdminLayout } from "../pages/admin/AdminLayout";
import { AdminSyncPage } from "../pages/admin/AdminSyncPage";
import { AdminRatingsPage } from "../pages/admin/AdminRatingsPage";
import { AdminAiPage } from "../pages/admin/AdminAiPage";
import { NotFoundPage } from "../pages/not-found/NotFoundPage";

export const router = createBrowserRouter([
  {
    path: "/",
    element: <AppLayout />,
    children: [
      { index: true, element: <HomePage /> },
      { path: "strategies/:code", element: <StrategyPage /> },
      { path: "login", element: <LoginPage /> },
      { path: "register", element: <RegisterPage /> },
      {
        element: <AuthGuard />,
        children: [
          { path: "portfolios", element: <PortfoliosPage /> },
          { path: "portfolios/:id", element: <PortfolioDetailPage /> },
          { path: "ai/analyses/:id", element: <AiAnalysisPage /> },
        ],
      },
      {
        element: <AdminGuard />,
        children: [
          {
            path: "admin",
            element: <AdminLayout />,
            children: [
              { index: true, element: <Navigate to="/admin/sync" replace /> },
              { path: "sync", element: <AdminSyncPage /> },
              { path: "ratings", element: <AdminRatingsPage /> },
              { path: "ai", element: <AdminAiPage /> },
            ],
          },
        ],
      },
      { path: "*", element: <NotFoundPage /> },
    ],
  },
]);
