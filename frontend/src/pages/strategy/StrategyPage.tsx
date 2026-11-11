import { useMutation, useQuery } from "@tanstack/react-query";
import { useEffect, useRef, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { api } from "../../shared/api/endpoints";
import type { CalculationResult, StrategyCode } from "../../shared/api/types";
import { riskClass, riskLabel } from "../../shared/lib/format";
import { Badge, Button, Card, ErrorState, LoadingBlock, RiskDots, Select } from "../../shared/ui";
import { StrategyForm } from "../../features/calculation-form/StrategyForm";
import { ResultsView } from "../../features/results/ResultsView";

export function StrategyPage() {
  const { code } = useParams<{ code: string }>();
  const strategyCode = (code ?? "").toUpperCase() as StrategyCode;
  const strategy = useQuery({
    queryKey: ["strategy", strategyCode],
    queryFn: () => api.strategy(strategyCode),
    enabled: ["BULLET", "BARBELL", "LADDER", "WHEEL"].includes(strategyCode),
  });
  const exchanges = useQuery({ queryKey: ["exchanges"], queryFn: api.exchanges });
  const [exchange, setExchange] = useState("MOEX");
  const [result, setResult] = useState<CalculationResult | null>(null);
  const [allowStale, setAllowStale] = useState(false);
  const resultsRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    setResult(null);
    setAllowStale(false);
  }, [strategyCode]);

  useEffect(() => {
    if (exchanges.data?.length) {
      const preferred = exchanges.data.find((item) => item.isDefault)?.code ?? exchanges.data[0].code;
      setExchange((current) => (exchanges.data.some((item) => item.code === current) ? current : preferred));
    }
  }, [exchanges.data]);

  const mutation = useMutation({
    mutationFn: (params: Record<string, unknown>) =>
      api.calculate({ strategy: strategyCode, exchange, params, allowStale }),
    onSuccess: (data) => {
      setResult(data);
      window.setTimeout(() => resultsRef.current?.scrollIntoView({ behavior: "smooth", block: "start" }), 60);
    },
  });

  if (!["BULLET", "BARBELL", "LADDER", "WHEEL"].includes(strategyCode)) {
    return <ErrorState error={new Error("Неизвестная стратегия")} />;
  }

  if (strategy.isLoading) {
    return <LoadingBlock label="Загружаем стратегию…" />;
  }

  if (strategy.isError || !strategy.data) {
    return <ErrorState error={strategy.error} onRetry={() => strategy.refetch()} />;
  }

  const data = strategy.data;

  return (
    <div className="space-y-6">
      <div className="flex items-center gap-2 text-sm text-slate-500">
        <Link to="/" className="hover:text-slate-300">
          Стратегии
        </Link>
        <span>/</span>
        <span className="text-slate-300">{data.name}</span>
      </div>

      <Card className="card-pad">
        <div className="flex flex-wrap items-start justify-between gap-4">
          <div className="max-w-3xl">
            <div className="flex flex-wrap items-center gap-2">
              <h1 className="text-2xl font-semibold text-white">{data.name}</h1>
              <span className={riskClass(data.riskLevel)}>{riskLabel(data.riskLevel)} риск</span>
              <Badge tone="neutral">
                горизонт {data.horizonMinYears ?? "—"}–{data.horizonMaxYears ?? "—"} лет
              </Badge>
            </div>
            <p className="mt-3 text-sm leading-relaxed text-slate-300">{data.shortDescription}</p>
            <div className="mt-3 flex items-center gap-3">
              <RiskDots level={data.riskLevel} />
              <span className="text-xs text-slate-500">код стратегии: {data.code}</span>
            </div>
          </div>
          <div className="w-full sm:w-56">
            <label className="label" htmlFor="exchange">
              Биржа
            </label>
            <Select id="exchange" value={exchange} onChange={(event) => setExchange(event.target.value)}>
              {(exchanges.data ?? [{ code: "MOEX", name: "Московская биржа" }]).map((item) => (
                <option key={item.code} value={item.code}>
                  {item.name}
                </option>
              ))}
            </Select>
            <p className="hint">По умолчанию MOEX ISS; данные — со снимком на момент расчёта.</p>
          </div>
        </div>

        <div className="mt-5 grid gap-3 sm:grid-cols-2">
          <div className="rounded-xl border border-emerald-500/20 bg-emerald-500/[0.04] p-3">
            <p className="text-xs font-semibold uppercase tracking-wide text-emerald-300">Кому подходит</p>
            <ul className="mt-2 space-y-1 text-sm text-slate-300">
              {data.bestFor.map((item) => (
                <li key={item} className="flex gap-2">
                  <span className="text-emerald-400">✓</span>
                  <span>{item}</span>
                </li>
              ))}
            </ul>
          </div>
          <div className="rounded-xl border border-amber-500/20 bg-amber-500/[0.04] p-3">
            <p className="text-xs font-semibold uppercase tracking-wide text-amber-300">Кому не подходит</p>
            <ul className="mt-2 space-y-1 text-sm text-slate-300">
              {data.notFor.map((item) => (
                <li key={item} className="flex gap-2">
                  <span className="text-amber-400">—</span>
                  <span>{item}</span>
                </li>
              ))}
            </ul>
          </div>
        </div>
      </Card>

      <div className="grid gap-4">
        <StrategyForm
          strategy={data}
          submitting={mutation.isPending}
          onSubmit={(params) => mutation.mutate(params)}
        />

        {mutation.isPending ? (
          <Card className="card-pad">
            <LoadingBlock label="Считаем портфель: фильтруем вселенную, проверяем рейтинги и собираем лоты…" />
          </Card>
        ) : null}

        {mutation.isError ? (
          <div className="space-y-3">
            <ErrorState error={mutation.error} onRetry={() => mutation.reset()} />
            {(mutation.error as any)?.status === 503 ? (
              <Button
                variant="secondary"
                size="sm"
                onClick={() => {
                  setAllowStale(true);
                  const form = document.querySelector("form");
                  form?.requestSubmit();
                }}
              >
                Рассчитать на устаревших данных
              </Button>
            ) : null}
          </div>
        ) : null}
      </div>

      <div ref={resultsRef}>{result ? <ResultsView result={result} onRecalculate={() => mutation.reset()} /> : null}</div>
    </div>
  );
}
