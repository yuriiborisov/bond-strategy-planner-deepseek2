import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api } from "../../shared/api/endpoints";
import { fmtDateTime, fmtNumber } from "../../shared/lib/format";
import { Badge, Button, Card, ErrorState, LoadingBlock, SectionCard } from "../../shared/ui";

const KINDS = [
  { kind: "FULL", label: "Полная", hint: "reference + quotes + cashflows" },
  { kind: "REFERENCE", label: "Справочники", hint: "бумаги, типы, даты" },
  { kind: "QUOTES", label: "Котировки", hint: "цены, YTM, дюрация" },
  { kind: "CASHFLOWS", label: "Купоны", hint: "bondization MOEX" },
];

export function AdminSyncPage() {
  const queryClient = useQueryClient();
  const stats = useQuery({ queryKey: ["admin-stats"], queryFn: api.admin.stats, refetchInterval: 30_000 });
  const runs = useQuery({ queryKey: ["admin-sync-runs"], queryFn: api.admin.syncRuns, refetchInterval: 30_000 });

  const sync = useMutation({
    mutationFn: (kind: string) => api.admin.sync("MOEX", kind),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["admin-stats"] });
      queryClient.invalidateQueries({ queryKey: ["admin-sync-runs"] });
    },
  });

  if (stats.isLoading) return <LoadingBlock />;
  if (stats.isError) return <ErrorState error={stats.error} onRetry={() => stats.refetch()} />;
  const data = stats.data!;

  return (
    <div className="space-y-5">
      <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
        <StatCard label="Бумаги" value={fmtNumber(data.bonds)} hint={`эмитентов: ${fmtNumber(data.issuers)}`} />
        <StatCard label="Покрытие рейтингами" value={`${data.ratingCoveragePct}%`} hint={`${data.ratedIssuers} из ${data.issuers}`} />
        <StatCard
          label="Снимок"
          value={data.snapshotAgeMinutes !== undefined ? `${fmtNumber(data.snapshotAgeMinutes)} мин назад` : "нет данных"}
          hint={data.latestSnapshotTs ? fmtDateTime(data.latestSnapshotTs) : "запустите синхронизацию"}
        />
        <StatCard label="AI-модуль" value={data.llmEnabled ? "включён" : "выключен"} hint={`провайдер: ${data.llmProvider}`} />
      </div>

      <SectionCard
        title="Синхронизация MOEX ISS"
        subtitle="Данные сохраняются снимками; при старте с пустой БД выполняется полная синхронизация"
      >
        <div className="flex flex-wrap gap-2">
          {KINDS.map((item) => (
            <Button
              key={item.kind}
              variant={item.kind === "FULL" ? "primary" : "secondary"}
              onClick={() => sync.mutate(item.kind)}
              disabled={sync.isPending}
              title={item.hint}
            >
              {sync.isPending && sync.variables === item.kind ? "Выполняется…" : item.label}
            </Button>
          ))}
        </div>
        <p className="mt-3 text-xs text-slate-500">
          Полная синхронизация может занимать несколько минут: купонные календари загружаются по каждой бумаге с
          ограничением 1 запрос/сек и лимитом 600 бумаг за запуск.
        </p>
        {sync.isError ? <p className="mt-3 text-sm text-rose-300">{(sync.error as Error).message}</p> : null}
        {sync.isSuccess ? (
          <p className="mt-3 text-sm text-emerald-300">
            Запуск {sync.data.kind}: {sync.data.status}, обновлено записей: {sync.data.rowsUpserted}.
          </p>
        ) : null}
      </SectionCard>

      <SectionCard
        title="История запусков"
        subtitle={`${data.syncRuns24h} запусков за 24 часа`}
        actions={
          <Button variant="ghost" size="sm" onClick={() => runs.refetch()}>
            Обновить
          </Button>
        }
      >
        {runs.isLoading ? <LoadingBlock /> : null}
        {runs.isError ? <ErrorState error={runs.error} onRetry={() => runs.refetch()} /> : null}
        <div className="table-wrap">
          <table className="w-full min-w-[720px] text-sm">
            <thead>
              <tr className="border-b border-white/10 text-left text-xs uppercase tracking-wide text-slate-500">
                <th className="px-3 py-2">#</th>
                <th className="px-3 py-2">Тип</th>
                <th className="px-3 py-2">Провайдер</th>
                <th className="px-3 py-2">Статус</th>
                <th className="px-3 py-2">Строк</th>
                <th className="px-3 py-2">Начало</th>
                <th className="px-3 py-2">Завершение</th>
                <th className="px-3 py-2">Ошибка</th>
              </tr>
            </thead>
            <tbody>
              {(runs.data ?? []).map((run) => (
                <tr key={run.id} className="border-b border-white/5 last:border-0">
                  <td className="px-3 py-2 text-slate-500">{run.id}</td>
                  <td className="px-3 py-2">{run.kind}</td>
                  <td className="px-3 py-2 text-slate-400">{run.provider}</td>
                  <td className="px-3 py-2">
                    <Badge
                      tone={
                        run.status === "SUCCESS"
                          ? "success"
                          : run.status === "RUNNING"
                            ? "info"
                            : run.status === "PARTIAL"
                              ? "warn"
                              : "danger"
                      }
                    >
                      {run.status}
                    </Badge>
                  </td>
                  <td className="px-3 py-2">{run.rowsUpserted}</td>
                  <td className="px-3 py-2 text-slate-400">{fmtDateTime(run.startedAt)}</td>
                  <td className="px-3 py-2 text-slate-400">{fmtDateTime(run.finishedAt)}</td>
                  <td className="max-w-[240px] truncate px-3 py-2 text-rose-300" title={run.error ?? ""}>
                    {run.error ?? "—"}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </SectionCard>
    </div>
  );
}

function StatCard({ label, value, hint }: { label: string; value: string; hint?: string }) {
  return (
    <Card className="p-4">
      <p className="stat-label">{label}</p>
      <p className="stat-value">{value}</p>
      {hint ? <p className="mt-1 text-xs text-slate-500">{hint}</p> : null}
    </Card>
  );
}
