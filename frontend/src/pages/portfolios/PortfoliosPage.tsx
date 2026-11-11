import { useQuery } from "@tanstack/react-query";
import { Link } from "react-router-dom";
import { api } from "../../shared/api/endpoints";
import { fmtDate, fmtMoney, strategyLabel } from "../../shared/lib/format";
import { Badge, Button, Card, EmptyState, ErrorState, LoadingBlock, SectionCard } from "../../shared/ui";

export function PortfoliosPage() {
  const query = useQuery({ queryKey: ["portfolios"], queryFn: () => api.portfolios() });

  return (
    <div className="space-y-5">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h1 className="text-2xl font-semibold text-white">Мои портфели</h1>
          <p className="mt-1 text-sm text-slate-400">
            Сохранённые расчёты с параметрами и снимком данных. Откройте портфель, чтобы увидеть проверки и экспорт.
          </p>
        </div>
        <Link to="/" className="btn-primary">
          Новый расчёт
        </Link>
      </div>

      {query.isLoading ? <LoadingBlock /> : null}
      {query.isError ? <ErrorState error={query.error} onRetry={() => query.refetch()} /> : null}

      {query.data && query.data.content.length === 0 ? (
        <EmptyState
          title="Пока нет сохранённых портфелей"
          description="Выберите стратегию, рассчитайте портфель и сохраните его — он появится здесь вместе с историей и проверками."
          action={
            <Link to="/" className="btn-primary">
              Выбрать стратегию
            </Link>
          }
        />
      ) : null}

      <div className="grid gap-4 md:grid-cols-2">
        {query.data?.content.map((portfolio) => (
          <Card key={portfolio.id} className="p-5 transition hover:border-accent-500/40">
            <div className="flex items-start justify-between gap-3">
              <div>
                <Link to={`/portfolios/${portfolio.id}`} className="text-lg font-semibold text-white hover:text-accent-300">
                  {portfolio.name}
                </Link>
                <p className="mt-1 text-xs text-slate-500">
                  {strategyLabel(portfolio.strategy)} · создан {fmtDate(portfolio.createdAt)}
                </p>
              </div>
              <Badge tone="neutral">{portfolio.positionsCount} позиций</Badge>
            </div>
            <div className="mt-4 grid grid-cols-2 gap-3 text-sm">
              <div>
                <p className="stat-label">Сумма</p>
                <p className="mt-0.5 font-semibold text-white">{fmtMoney(portfolio.totalAmountRub)}</p>
              </div>
              <div>
                <p className="stat-label">Снимок</p>
                <p className="mt-0.5 text-slate-300">{fmtDate(portfolio.snapshotTs)}</p>
              </div>
            </div>
            <div className="mt-4 flex gap-2">
              <Link to={`/portfolios/${portfolio.id}`} className="btn-secondary btn-sm">
                Открыть
              </Link>
              <Button
                variant="ghost"
                size="sm"
                onClick={() => api.exportPortfolio(portfolio.id, "csv").catch(() => undefined)}
              >
                CSV
              </Button>
            </div>
          </Card>
        ))}
      </div>

      <SectionCard title="Как хранятся расчёты" subtitle="Воспроизводимость и приватность">
        <ul className="space-y-2 text-sm text-slate-400">
          <li>• Каждый расчёт хранит параметры, результат, версию алгоритма и `snapshotTs`.</li>
          <li>• Портфели привязаны к пользователю; удаление — мягкое, данные остаются для аудита.</li>
          <li>• Экспорт CSV и XLSX включает позиции, суммы, YTM и обоснование выбора.</li>
        </ul>
      </SectionCard>
    </div>
  );
}
