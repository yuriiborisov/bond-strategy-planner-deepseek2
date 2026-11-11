import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { api } from "../../shared/api/endpoints";
import type { CalculationResult } from "../../shared/api/types";
import { fmtDate, fmtDateTime, fmtMoney, fmtPct, strategyLabel } from "../../shared/lib/format";
import {
  Badge,
  Button,
  Card,
  Disclaimer,
  ErrorState,
  Field,
  LoadingBlock,
  Modal,
  SectionCard,
  TextInput,
} from "../../shared/ui";
import { AiSection } from "../../features/ai/AiSection";
import { AllocationChart, CashFlowChart } from "../../features/results/Charts";
import { LadderSection, WheelSection } from "../../features/results/LadderWheelSections";
import { PositionsTable } from "../../features/results/PositionsTable";
import { SummaryMetrics } from "../../features/results/SummaryMetrics";
import { BarbellSection, BulletSection } from "../../features/results/StrategySections";

export function PortfolioDetailPage() {
  const { id } = useParams<{ id: string }>();
  const portfolioId = Number(id);
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [renameOpen, setRenameOpen] = useState(false);
  const [deleteOpen, setDeleteOpen] = useState(false);
  const [name, setName] = useState("");
  const [actionError, setActionError] = useState<string | null>(null);

  const query = useQuery({
    queryKey: ["portfolio", portfolioId],
    queryFn: () => api.portfolio(portfolioId),
    enabled: Number.isFinite(portfolioId),
  });

  const rename = useMutation({
    mutationFn: () => api.renamePortfolio(portfolioId, name.trim()),
    onSuccess: () => {
      setRenameOpen(false);
      queryClient.invalidateQueries({ queryKey: ["portfolio", portfolioId] });
      queryClient.invalidateQueries({ queryKey: ["portfolios"] });
    },
    onError: (error: Error) => setActionError(error.message),
  });

  const remove = useMutation({
    mutationFn: () => api.deletePortfolio(portfolioId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["portfolios"] });
      navigate("/portfolios");
    },
    onError: (error: Error) => setActionError(error.message),
  });

  if (query.isLoading) return <LoadingBlock label="Открываем портфель…" />;
  if (query.isError || !query.data) return <ErrorState error={query.error} onRetry={() => query.refetch()} />;

  const portfolio = query.data;
  const result = portfolio.result as CalculationResult | undefined;

  return (
    <div className="space-y-5">
      <div className="flex items-center gap-2 text-sm text-slate-500">
        <Link to="/portfolios" className="hover:text-slate-300">
          Портфели
        </Link>
        <span>/</span>
        <span className="truncate text-slate-300">{portfolio.name}</span>
      </div>

      <Card className="card-pad">
        <div className="flex flex-wrap items-start justify-between gap-4">
          <div>
            <div className="flex flex-wrap items-center gap-2">
              <h1 className="text-2xl font-semibold text-white">{portfolio.name}</h1>
              <Badge tone="info">{strategyLabel(portfolio.strategy)}</Badge>
            </div>
            <p className="mt-1 text-xs text-slate-500">
              Создан {fmtDateTime(portfolio.createdAt)} · снимок {fmtDateTime(portfolio.snapshotTs)} · расчёт #
              {portfolio.calculationId}
            </p>
          </div>
          <div className="flex flex-wrap gap-2">
            <Button
              variant="secondary"
              size="sm"
              onClick={() => api.exportPortfolio(portfolioId, "csv").catch((error: Error) => setActionError(error.message))}
            >
              CSV
            </Button>
            <Button
              variant="secondary"
              size="sm"
              onClick={() => api.exportPortfolio(portfolioId, "xlsx").catch((error: Error) => setActionError(error.message))}
            >
              XLSX
            </Button>
            <Button
              variant="ghost"
              size="sm"
              onClick={() => {
                setName(portfolio.name);
                setRenameOpen(true);
              }}
            >
              Переименовать
            </Button>
            <Button variant="danger" size="sm" onClick={() => setDeleteOpen(true)}>
              Удалить
            </Button>
          </div>
        </div>
        {actionError ? <p className="mt-3 text-sm text-rose-300">{actionError}</p> : null}
      </Card>

      {result ? (
        <>
          <SummaryMetrics result={result} />
          {result.strategy === "BULLET" ? <BulletSection result={result} /> : null}
          {result.strategy === "BARBELL" ? <BarbellSection result={result} /> : null}
          {result.strategy === "LADDER" ? <LadderSection result={result} /> : null}
          {result.strategy === "WHEEL" ? <WheelSection result={result} /> : null}

          <SectionCard title="Позиции" subtitle="Проверки и обоснование доступны в раскрытии строки">
            <PositionsTable positions={result.positions} />
          </SectionCard>

          <div className="grid gap-4 lg:grid-cols-2">
            <SectionCard title="Денежный поток" subtitle="Купоны, погашения и налоги">
              <CashFlowChart points={result.cashFlowSchedule} />
            </SectionCard>
            <SectionCard title="Распределение" subtitle="По эмитентам">
              <AllocationChart positions={result.positions} />
            </SectionCard>
          </div>

          {result.warnings.length ? (
            <SectionCard title="Предупреждения" subtitle="Актуально на дату снимка">
              <ul className="space-y-2 text-sm">
                {result.warnings.map((warning, index) => (
                  <li key={`${warning.code}-${index}`} className="rounded-xl border border-white/10 bg-white/[0.02] px-3 py-2">
                    <span className="mr-2 font-mono text-xs text-slate-500">{warning.code}</span>
                    <span className="text-slate-300">{warning.message}</span>
                  </li>
                ))}
              </ul>
            </SectionCard>
          ) : null}
        </>
      ) : (
        <SectionCard title="Позиции" subtitle="Результат расчёта недоступен, показаны сохранённые позиции">
          <div className="table-wrap">
            <table className="data-table">
              <thead>
                <tr>
                  <th>Бумага</th>
                  <th>Погашение</th>
                  <th>Кол-во</th>
                  <th>Сумма</th>
                  <th>Доля</th>
                </tr>
              </thead>
              <tbody>
                {portfolio.positions.map((position, index) => (
                  <tr key={`${position.secid}-${index}`}>
                    <td>
                      <div className="text-white">{position.name ?? position.secid}</div>
                      <div className="font-mono text-xs text-slate-500">{position.secid}</div>
                    </td>
                    <td>{fmtDate(position.maturityDate)}</td>
                    <td>{position.quantity}</td>
                    <td>{fmtMoney(position.amountRub)}</td>
                    <td>{fmtPct((position.weight ?? 0) * 100)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </SectionCard>
      )}

      <SectionCard title="AI-разбор портфеля" subtitle="Состояние, ближайшие события, следующие действия и риски">
        <AiSection kind="portfolio" portfolioId={portfolioId} />
      </SectionCard>

      <Disclaimer />

      <Modal
        open={renameOpen}
        title="Переименовать портфель"
        onClose={() => setRenameOpen(false)}
        footer={
          <>
            <Button variant="ghost" onClick={() => setRenameOpen(false)}>
              Отмена
            </Button>
            <Button onClick={() => rename.mutate()} disabled={rename.isPending}>
              Сохранить
            </Button>
          </>
        }
      >
        <Field label="Название" htmlFor="portfolio-rename">
          <TextInput id="portfolio-rename" value={name} onChange={(event) => setName(event.target.value)} />
        </Field>
      </Modal>

      <Modal
        open={deleteOpen}
        title="Удалить портфель?"
        onClose={() => setDeleteOpen(false)}
        footer={
          <>
            <Button variant="ghost" onClick={() => setDeleteOpen(false)}>
              Отмена
            </Button>
            <Button variant="danger" onClick={() => remove.mutate()} disabled={remove.isPending}>
              Удалить
            </Button>
          </>
        }
      >
        <p className="text-sm text-slate-300">
          Портфель будет скрыт из списка (мягкое удаление). Расчёт и история сохранятся для аудита. Действие можно
          повторить позже через поддержку.
        </p>
      </Modal>
    </div>
  );
}
