import { Link, Outlet, useLocation } from "react-router-dom";

const tabs = [
  { to: "/admin/sync", label: "Синхронизация" },
  { to: "/admin/ratings", label: "Рейтинги" },
  { to: "/admin/ai", label: "AI и лимиты" },
];

export function AdminLayout() {
  const location = useLocation();
  return (
    <div className="space-y-5">
      <div>
        <h1 className="text-2xl font-semibold text-white">Администрирование</h1>
        <p className="mt-1 text-sm text-slate-400">
          Синхронизация биржи, источники рейтингов, алиасы эмитентов и параметры AI-модуля.
        </p>
      </div>
      <nav className="flex flex-wrap gap-1 rounded-xl border border-white/10 bg-ink-950/50 p-1">
        {tabs.map((tab) => {
          const active = location.pathname.startsWith(tab.to);
          return (
            <Link
              key={tab.to}
              to={tab.to}
              className={
                active
                  ? "rounded-lg bg-white/10 px-3 py-2 text-sm font-medium text-white"
                  : "rounded-lg px-3 py-2 text-sm font-medium text-slate-400 hover:text-slate-200"
              }
            >
              {tab.label}
            </Link>
          );
        })}
      </nav>
      <Outlet />
    </div>
  );
}
