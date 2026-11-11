import { useEffect } from "react";
import { Link, Outlet, useLocation, useNavigate } from "react-router-dom";
import { isAdmin, useAuthStore } from "../store";
import { api } from "../../shared/api/endpoints";
import { getMessage } from "../../shared/lib/i18n";
import { Button, Disclaimer } from "../../shared/ui";

function NavLink({ to, children }: { to: string; children: React.ReactNode }) {
  const location = useLocation();
  const active = to === "/" ? location.pathname === "/" : location.pathname.startsWith(to);
  return (
    <Link
      to={to}
      className={
        active
          ? "rounded-lg bg-white/10 px-3 py-2 text-sm font-medium text-white"
          : "rounded-lg px-3 py-2 text-sm font-medium text-slate-400 transition hover:bg-white/5 hover:text-slate-100"
      }
    >
      {children}
    </Link>
  );
}

export function AppLayout() {
  const { user, accessToken, setUser, logout } = useAuthStore();
  const navigate = useNavigate();
  const location = useLocation();

  useEffect(() => {
    if (!accessToken || user) return;
    let cancelled = false;
    api
      .me()
      .then((me) => {
        if (!cancelled) setUser(me);
      })
      .catch(() => {
        if (!cancelled) logout();
      });
    return () => {
      cancelled = true;
    };
  }, [accessToken, user, setUser, logout]);

  return (
    <div className="flex min-h-screen flex-col">
      <header className="sticky top-0 z-40 border-b border-white/5 bg-ink-950/80 backdrop-blur">
        <div className="mx-auto flex max-w-7xl flex-wrap items-center gap-3 px-4 py-3 sm:px-6">
          <Link to="/" className="flex items-center gap-2.5 text-white hover:text-white">
            <span className="flex h-9 w-9 items-center justify-center rounded-xl bg-accent-500/15 text-accent-400 ring-1 ring-accent-500/30">
              <svg viewBox="0 0 24 24" className="h-5 w-5" fill="none" stroke="currentColor" strokeWidth="2">
                <path d="M4 19V11m5 8V5m5 14v-6m5 6V8" strokeLinecap="round" />
              </svg>
            </span>
            <span>
              <span className="block text-sm font-semibold leading-tight">{getMessage("appName")}</span>
              <span className="hidden text-[11px] text-slate-500 sm:block">облигации · стратегии · расчёты</span>
            </span>
          </Link>

          <nav className="ml-auto flex items-center gap-1" aria-label="Основная навигация">
            <NavLink to="/">{getMessage("nav.strategies")}</NavLink>
            {user ? <NavLink to="/portfolios">{getMessage("nav.portfolios")}</NavLink> : null}
            {isAdmin(user) ? <NavLink to="/admin/sync">{getMessage("nav.admin")}</NavLink> : null}
          </nav>

          <div className="flex items-center gap-2 border-l border-white/10 pl-3">
            {user ? (
              <>
                <span className="hidden text-xs text-slate-400 sm:block" title={user.email}>
                  {user.email}
                </span>
                <Button
                  variant="ghost"
                  size="sm"
                  onClick={() => {
                    logout();
                    navigate("/");
                  }}
                >
                  {getMessage("nav.logout")}
                </Button>
              </>
            ) : (
              <>
                <Button variant="ghost" size="sm" onClick={() => navigate(`/login?next=${encodeURIComponent(location.pathname)}`)}>
                  {getMessage("nav.login")}
                </Button>
                <Button variant="secondary" size="sm" onClick={() => navigate("/register")}>
                  {getMessage("nav.register")}
                </Button>
              </>
            )}
          </div>
        </div>
      </header>

      <main className="mx-auto w-full max-w-7xl flex-1 px-4 py-6 sm:px-6 sm:py-8">
        <Outlet />
      </main>

      <footer className="border-t border-white/5 bg-ink-950/60">
        <div className="mx-auto max-w-7xl px-4 py-6 sm:px-6">
          <Disclaimer />
          <p className="mt-3 text-xs text-slate-500">
            Данные MOEX ISS предоставляются по условиям Московской биржи. Рейтинги — по данным рейтинговых агентств,
            с указанием агентства и даты. {new Date().getFullYear()} © Bond Strategy Planner.
          </p>
        </div>
      </footer>
    </div>
  );
}
