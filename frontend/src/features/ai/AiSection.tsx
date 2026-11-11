import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { Link, useLocation } from "react-router-dom";
import { useAuthStore } from "../../app/store";
import { api } from "../../shared/api/endpoints";
import type { AiAnalysis, AiResult } from "../../shared/api/types";
import { fmtDateTime, strategyLabel } from "../../shared/lib/format";
import { Badge, Button, Modal, Spinner } from "../../shared/ui";

export function AiSection({
  kind,
  portfolioId,
  calculationId,
}: {
  kind: "portfolio" | "discovery";
  portfolioId?: number;
  calculationId?: number;
}) {
  const user = useAuthStore((state) => state.user);
  const location = useLocation();
  const queryClient = useQueryClient();
  const [consentOpen, setConsentOpen] = useState(false);
  const [analysisId, setAnalysisId] = useState<number | null>(null);
  const [startError, setStartError] = useState<string | null>(null);

  const health = useQuery({ queryKey: ["ai-health"], queryFn: api.aiHealth, staleTime: 60_000 });
  const consent = useQuery({
    queryKey: ["ai-consent"],
    queryFn: api.aiConsent,
    enabled: Boolean(user),
  });

  const grantConsent = useMutation({
    mutationFn: api.grantConsent,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["ai-consent"] });
      setConsentOpen(false);
      start();
    },
  });

  const startMutation = useMutation({
    mutationFn: async () => {
      if (kind === "portfolio" && portfolioId) return api.startPortfolioReview(portfolioId);
      if (kind === "discovery" && calculationId) return api.startDiscovery(calculationId);
      throw new Error("Не указан объект для AI-анализа");
    },
    onSuccess: (response) => {
      setAnalysisId(response.analysisId);
      setStartError(null);
    },
    onError: (error: Error) => setStartError(error.message),
  });

  const analysis = useQuery({
    queryKey: ["ai-analysis", analysisId],
    queryFn: () => api.aiAnalysis(analysisId!),
    enabled: analysisId !== null,
    refetchInterval: (query) => {
      const data = query.state.data as AiAnalysis | undefined;
      return data && (data.status === "PENDING" || data.status === "RUNNING") ? 1500 : false;
    },
  });

  function start() {
    if (!user) return;
    if (!consent.data?.granted) {
      setConsentOpen(true);
      return;
    }
    startMutation.mutate();
  }

  if (health.isLoading) {
    return (
      <div className="flex items-center gap-2 text-sm text-slate-400">
        <Spinner /> Проверяем доступность AI…
      </div>
    );
  }

  if (!health.data?.enabled) {
    return (
      <div className="space-y-2 text-sm text-slate-400">
        <p>AI-модуль выключен в конфигурации. Расчёты, проверки и портфель работают без него.</p>
        <p className="text-xs text-slate-500">
          Включение: APP_LLM_ENABLED=true и APP_LLM_BASE_URL/APP_LLM_MODEL; провайдеры — LM Studio, OpenAI-совместимый,
          YandexGPT, GigaChat, Ollama.
        </p>
      </div>
    );
  }

  if (!user) {
    return (
      <div className="space-y-3 text-sm text-slate-400">
        <p>AI-разбор доступен после входа: перед первым запросом вы подтверждаете согласие на обработку данных.</p>
        <Link className="btn-secondary btn-sm" to={`/login?next=${encodeURIComponent(location.pathname)}`}>
          Войти
        </Link>
      </div>
    );
  }

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center gap-2">
        <Button size="sm" onClick={start} disabled={startMutation.isPending}>
          {startMutation.isPending ? "Запускаем…" : kind === "portfolio" ? "AI-разбор портфеля" : "AI-объяснение кандидатов"}
        </Button>
        <Badge tone={health.data.healthy ? "success" : "warn"}>
          {health.data.provider} · {health.data.model}
        </Badge>
        <Badge tone="neutral">{health.data.privacyMode}</Badge>
      </div>
      <p className="text-xs text-slate-500">
        AI не считает доходность и не меняет вердикты проверок. Ответ проходит JSON-схему и grounding-валидацию;
        выдуманные тикеры удаляются.
      </p>
      {startError ? <p className="text-sm text-rose-300">{startError}</p> : null}

      {analysis.isLoading ? (
        <div className="flex items-center gap-2 text-sm text-slate-400">
          <Spinner /> Загружаем анализ…
        </div>
      ) : null}

      {analysis.data ? <AnalysisCard analysis={analysis.data} /> : null}

      <Modal open={consentOpen} title="Согласие на обработку данных AI" onClose={() => setConsentOpen(false)}
        footer={
          <>
            <Button variant="ghost" onClick={() => setConsentOpen(false)}>
              Отмена
            </Button>
            <Button onClick={() => grantConsent.mutate()} disabled={grantConsent.isPending}>
              {grantConsent.isPending ? "Сохраняем…" : "Согласен"}
            </Button>
          </>
        }
      >
        <div className="space-y-3 text-sm text-slate-300">
          <p>
            В AI передаются только агрегаты и проверенные факты: состав портфеля, доли, YTM, дюрация, рейтинги и
            ближайшие события. Email, пароли и токены не передаются.
          </p>
          <p>
            Ответ модели сохраняется для аудита с указанием провайдера, модели, версии промпта, токенов и стоимости.
            Согласие можно отозвать в настройках.
          </p>
          <p className="text-xs text-slate-500">Провайдер: {health.data.provider}. Режим приватности: {health.data.privacyMode}.</p>
        </div>
      </Modal>
    </div>
  );
}

export function AnalysisCard({ analysis }: { analysis: AiAnalysis }) {
  const result: AiResult | null = analysis.result ?? null;
  if (analysis.status === "PENDING" || analysis.status === "RUNNING") {
    return (
      <div className="flex items-center gap-2 rounded-xl border border-white/10 bg-white/[0.02] px-3 py-3 text-sm text-slate-300">
        <Spinner /> Анализ выполняется… (статус {analysis.status})
      </div>
    );
  }
  if (!result) {
    return (
      <div className="rounded-xl border border-rose-500/30 bg-rose-500/10 px-3 py-2 text-sm text-rose-200">
        Анализ не вернул результат: {analysis.error ?? "неизвестная ошибка"}
      </div>
    );
  }
  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center gap-2">
        <Badge tone={result.portfolioHealth === "OK" ? "success" : result.portfolioHealth === "ACTION_REQUIRED" ? "danger" : "warn"}>
          {result.portfolioHealth === "OK" ? "Состояние: OK" : result.portfolioHealth === "ACTION_REQUIRED" ? "Требует действий" : "Наблюдать"}
        </Badge>
        {result.fallback ? <Badge tone="warn">fallback: провайдер недоступен</Badge> : <Badge tone="info">AI-резюме</Badge>}
        {result.grounding?.removed ? <Badge tone="neutral">grounding: удалено {result.grounding.removed}</Badge> : null}
        <span className="text-xs text-slate-500">{fmtDateTime(analysis.finishedAt ?? analysis.createdAt)}</span>
      </div>
      <p className="text-sm leading-relaxed text-slate-300">{result.summary}</p>

      {result.nextActions?.length ? (
        <div>
          <h4 className="text-sm font-semibold text-white">Следующие действия</h4>
          <ul className="mt-2 space-y-2">
            {result.nextActions.map((action, index) => (
              <li key={`${action.type}-${index}`} className="rounded-xl border border-white/10 bg-white/[0.02] px-3 py-2">
                <div className="flex flex-wrap items-center gap-2 text-xs text-slate-500">
                  <Badge tone={action.priority === "HIGH" ? "danger" : action.priority === "MEDIUM" ? "warn" : "neutral"}>
                    {action.priority}
                  </Badge>
                  <span>{action.type}</span>
                  <span>{fmtDateTime(action.date)}</span>
                </div>
                <p className="mt-1 text-sm font-medium text-slate-200">{action.title}</p>
                {action.details ? <p className="mt-1 text-sm text-slate-400">{action.details}</p> : null}
                {action.relatedSecids?.length ? (
                  <p className="mt-1 font-mono text-xs text-slate-500">{action.relatedSecids.join(", ")}</p>
                ) : null}
              </li>
            ))}
          </ul>
        </div>
      ) : null}

      {result.risks?.length ? (
        <div>
          <h4 className="text-sm font-semibold text-white">Риски</h4>
          <ul className="mt-2 space-y-1 text-sm">
            {result.risks.map((risk, index) => (
              <li key={`${risk.code}-${index}`} className="text-slate-400">
                <span className={risk.severity === "HIGH" ? "text-rose-300" : risk.severity === "MEDIUM" ? "text-amber-300" : "text-slate-400"}>
                  {risk.severity}
                </span>{" "}
                · {risk.message}
              </li>
            ))}
          </ul>
        </div>
      ) : null}

      {result.candidates?.length ? (
        <div>
          <h4 className="text-sm font-semibold text-white">Кандидаты</h4>
          <ul className="mt-2 space-y-2">
            {result.candidates.map((candidate) => (
              <li key={candidate.secid} className="rounded-xl border border-white/10 bg-white/[0.02] px-3 py-2">
                <p className="font-mono text-xs text-accent-300">{candidate.secid}</p>
                <p className="mt-1 text-sm text-slate-200">{candidate.why}</p>
                <p className="mt-1 text-xs text-amber-200/80">Риски: {candidate.risks}</p>
                {candidate.checkBeforeBuy ? (
                  <p className="mt-1 text-xs text-slate-400">Проверить: {candidate.checkBeforeBuy}</p>
                ) : null}
              </li>
            ))}
          </ul>
        </div>
      ) : null}

      {result.watchlist?.length ? (
        <div>
          <h4 className="text-sm font-semibold text-white">Watchlist</h4>
          <ul className="mt-2 space-y-1 text-sm text-slate-400">
            {result.watchlist.map((item) => (
              <li key={item.secid}>
                <span className="font-mono text-xs">{item.secid}</span> — {item.reason}
              </li>
            ))}
          </ul>
        </div>
      ) : null}

      {result.sources?.length ? (
        <div>
          <h4 className="text-sm font-semibold text-white">Источники</h4>
          <ul className="mt-2 space-y-1 text-sm">
            {result.sources.map((source) => (
              <li key={source.ref}>
                {source.url ? (
                  <a href={source.url} target="_blank" rel="noreferrer" className="text-accent-300 hover:underline">
                    {source.title}
                  </a>
                ) : (
                  <span className="text-slate-300">{source.title}</span>
                )}
                <span className="ml-2 font-mono text-xs text-slate-600">{source.ref}</span>
              </li>
            ))}
          </ul>
        </div>
      ) : null}

      <p className="rounded-xl border border-white/10 bg-white/[0.02] px-3 py-2 text-xs text-slate-400">
        {result.disclaimer ?? analysis.disclaimer}
      </p>
      {analysis.usage ? (
        <p className="text-xs text-slate-600">
          Токены: {analysis.usage.tokensIn ?? 0} / {analysis.usage.tokensOut ?? 0} · задержка {analysis.usage.latencyMs ?? 0} мс ·
          промпт {analysis.promptVersion} · {strategyLabel(analysis.type)}
        </p>
      ) : null}
    </div>
  );
}
