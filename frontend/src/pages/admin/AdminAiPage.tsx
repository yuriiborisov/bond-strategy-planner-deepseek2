import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useEffect, useState } from "react";
import { api } from "../../shared/api/endpoints";
import { fmtDateTime, fmtNumber } from "../../shared/lib/format";
import { Badge, Button, Card, Checkbox, ErrorState, Field, LoadingBlock, SectionCard, TextInput } from "../../shared/ui";

export function AdminAiPage() {
  const queryClient = useQueryClient();
  const analyses = useQuery({ queryKey: ["admin-ai-analyses"], queryFn: () => api.admin.aiAnalyses() });
  const health = useQuery({ queryKey: ["ai-health"], queryFn: api.aiHealth });
  const [enabled, setEnabled] = useState(false);
  const [dailyLimit, setDailyLimit] = useState("0");
  const [message, setMessage] = useState<string | null>(null);

  useEffect(() => {
    if (health.data) {
      setEnabled(health.data.enabled);
    }
  }, [health.data]);

  const patch = useMutation({
    mutationFn: () =>
      api.admin.aiConfig({ enabled, dailyLimit: Number(dailyLimit) || 0 }),
    onSuccess: () => {
      setMessage("Настройки AI обновлены (runtime-часть)");
      queryClient.invalidateQueries({ queryKey: ["ai-health"] });
    },
    onError: (error: Error) => setMessage(error.message),
  });

  return (
    <div className="space-y-5">
      <SectionCard
        title="Параметры AI-модуля"
        subtitle="Провайдер, модель, base URL и ключи задаются через env; лимиты можно менять на ходу"
      >
        {health.isLoading ? <LoadingBlock /> : null}
        {health.isError ? <ErrorState error={health.error} onRetry={() => health.refetch()} /> : null}
        {health.data ? (
          <div className="space-y-4">
            <div className="flex flex-wrap gap-2">
              <Badge tone={health.data.enabled ? "success" : "neutral"}>{health.data.enabled ? "включён" : "выключен"}</Badge>
              <Badge tone="info">{health.data.provider}</Badge>
              <Badge tone="neutral">{health.data.model}</Badge>
              <Badge tone={health.data.healthy ? "success" : "warn"}>
                {health.data.healthy ? "провайдер доступен" : "провайдер недоступен"}
              </Badge>
              <Badge tone="neutral">{health.data.privacyMode}</Badge>
            </div>
            <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
              <Checkbox
                label="AI включён"
                hint="ENV APP_LLM_ENABLED; тумблер — runtime-часть"
                checked={enabled}
                onChange={(event) => setEnabled(event.target.checked)}
              />
              <Field label="Дневной лимит анализов" hint="0 — без лимита">
                <TextInput
                  type="number"
                  min="0"
                  value={dailyLimit}
                  onChange={(event) => setDailyLimit(event.target.value)}
                />
              </Field>
              <div className="flex items-end">
                <Button onClick={() => patch.mutate()} disabled={patch.isPending}>
                  {patch.isPending ? "Сохраняем…" : "Сохранить"}
                </Button>
              </div>
            </div>
            {message ? <p className="text-sm text-slate-300">{message}</p> : null}
            <p className="text-xs text-slate-500">
              Квоты на MVP отключены; при включении лимита превышение возвращает 429 AI_QUOTA_EXCEEDED. Таймаут запроса
              60 секунд, один retry, circuit breaker на провайдера.
            </p>
          </div>
        ) : null}
      </SectionCard>

      <SectionCard
        title="История AI-анализов"
        subtitle="Токены, стоимость, задержка и ошибки по каждому запросу"
        actions={
          <Button variant="ghost" size="sm" onClick={() => analyses.refetch()}>
            Обновить
          </Button>
        }
      >
        {analyses.isLoading ? <LoadingBlock /> : null}
        {analyses.isError ? <ErrorState error={analyses.error} onRetry={() => analyses.refetch()} /> : null}
        <div className="table-wrap">
          <table className="w-full min-w-[820px] text-sm">
            <thead>
              <tr className="border-b border-white/10 text-left text-xs uppercase tracking-wide text-slate-500">
                <th className="px-3 py-2">#</th>
                <th className="px-3 py-2">Тип</th>
                <th className="px-3 py-2">Статус</th>
                <th className="px-3 py-2">Модель</th>
                <th className="px-3 py-2">Токены in/out</th>
                <th className="px-3 py-2">Стоимость</th>
                <th className="px-3 py-2">Задержка</th>
                <th className="px-3 py-2">Создан</th>
                <th className="px-3 py-2">Ошибка</th>
              </tr>
            </thead>
            <tbody>
              {(analyses.data?.content ?? []).map((analysis) => (
                <tr key={analysis.analysisId} className="border-b border-white/5 last:border-0">
                  <td className="px-3 py-2 text-slate-500">{analysis.analysisId}</td>
                  <td className="px-3 py-2">{analysis.type}</td>
                  <td className="px-3 py-2">
                    <Badge
                      tone={
                        analysis.status === "DONE"
                          ? "success"
                          : analysis.status === "FAILED" || analysis.status === "REJECTED"
                            ? "danger"
                            : "warn"
                      }
                    >
                      {analysis.status}
                    </Badge>
                  </td>
                  <td className="px-3 py-2 text-slate-400">{analysis.model}</td>
                  <td className="px-3 py-2 text-slate-400">
                    {fmtNumber(analysis.usage?.tokensIn)} / {fmtNumber(analysis.usage?.tokensOut)}
                  </td>
                  <td className="px-3 py-2 text-slate-400">{analysis.usage?.costUsd ?? 0} $</td>
                  <td className="px-3 py-2 text-slate-400">{analysis.usage?.latencyMs ?? 0} мс</td>
                  <td className="px-3 py-2 text-slate-400">{fmtDateTime(analysis.createdAt)}</td>
                  <td className="max-w-[220px] truncate px-3 py-2 text-rose-300" title={analysis.error ?? ""}>
                    {analysis.error ?? "—"}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        {analyses.data?.content.length === 0 ? (
          <Card className="mt-3 p-4 text-sm text-slate-400">
            Анализов пока нет. Включите AI и запросите разбор сохранённого портфеля.
          </Card>
        ) : null}
      </SectionCard>
    </div>
  );
}
