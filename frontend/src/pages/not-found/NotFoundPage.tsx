import { Link } from "react-router-dom";
import { Card } from "../../shared/ui";

export function NotFoundPage() {
  return (
    <div className="mx-auto max-w-lg py-12">
      <Card className="card-pad text-center">
        <p className="text-5xl font-semibold text-white">404</p>
        <h1 className="mt-3 text-xl font-semibold text-white">Страница не найдена</h1>
        <p className="mt-2 text-sm text-slate-400">
          Возможно, ссылка устарела. Вернитесь к каталогу стратегий или к сохранённым портфелям.
        </p>
        <div className="mt-5 flex justify-center gap-2">
          <Link to="/" className="btn-primary">
            К стратегиям
          </Link>
          <Link to="/portfolios" className="btn-secondary">
            Портфели
          </Link>
        </div>
      </Card>
    </div>
  );
}
