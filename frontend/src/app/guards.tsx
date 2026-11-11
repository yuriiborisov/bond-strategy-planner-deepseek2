import { Navigate, Outlet, useLocation } from "react-router-dom";
import { isAdmin, useAuthStore } from "./store";
import { EmptyState } from "../shared/ui";

export function AuthGuard() {
  const user = useAuthStore((state) => state.user);
  const location = useLocation();
  if (!user) {
    return <Navigate to={`/login?next=${encodeURIComponent(location.pathname)}`} replace />;
  }
  return <Outlet />;
}

export function AdminGuard() {
  const user = useAuthStore((state) => state.user);
  const location = useLocation();
  if (!user) {
    return <Navigate to={`/login?next=${encodeURIComponent(location.pathname)}`} replace />;
  }
  if (!isAdmin(user)) {
    return (
      <EmptyState
        title="Недостаточно прав"
        description="Раздел доступен только администратору. Войдите под учётной записью с ролью ADMIN."
      />
    );
  }
  return <Outlet />;
}
