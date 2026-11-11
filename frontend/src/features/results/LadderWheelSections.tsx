import { useMemo } from "react";
import {
  CartesianGrid,
  Legend,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import type { CalculationResult, Rung } from "../../shared/api/types";
import { fmtDate, fmtMoney, fmtPct } from "../../shared/lib/format";
import { Badge, SectionCard } from "../../shared/ui";

const TOOLTIP_STYLE = {
  background: "#0b1220",
  border: "1px solid rgba(255,255,255,0.12)",
  borderRadius: 12,
  color: "#e2e8f0",
  fontSize: 12,
};

function rungStatus(status: string): { tone: "success" | "warn" | "danger" | "neutral"; label: string } {
  switch (status) {
    case "FILLED":
      return { tone: "success", label: "Заполнена" };
    case "SKIPPED_INSUFFICIENT_BUDGET":
      return { tone: "warn", label: "Не хватило бюджета" };
    case "ASSET_CHECK_FAILED":
      return { tone: "danger", label: "Не прошла проверки" };
    case "NO_CANDIDATES":
    case "NO_PRICED_CANDIDATES":
      return { tone: "danger", label: "Нет бумаг" };
    default:
      return { tone: "neutral", label: status };
  }
}

export function LadderSection({ result }: { result: CalculationResult }) {
  const rungs = result.rungs ?? [];
  const rolling = result.rollingProjection;
  return (
    <div className="space-y-4">
      <SectionCard title="Лестница" subtitle="Ступени, бюджеты и выбранные выпуски">
        <div className="scrollbar-thin -mx-1 flex gap-2 overflow-x-auto px-1 pb-2">
          {rungs.map((rung) => (
            <div
              key={rung.index}
              className="min-w-[190px] flex-1 rounded-2xl border border-white/10 bg-white/[0.02] p-3"
            >
              <div className="flex items-center justify-between">
                <span className="text-xs uppercase tracking-wide text-slate-500">Ступень {rung.index + 1}</span>
                <Badge tone={rungStatus(rung.status).tone}>{rungStatus(rung.status).label}</Badge>
              </div>
              <p className="mt-2 text-sm font-medium text-white">{fmtDate(rung.targetDate)}</p>
              <p className="mt-2 text-sm text-slate-300">{rung.bond?.name ?? rung.bond?.secid ?? "—"}</p>
              <p className="text-xs text-slate-500">
                {rung.bond ? `${rung.bond.secid} · ${rung.quantity} шт.` : "бумага не выбрана"}
              </p>
              <p className="mt-2 text-sm text-white">{fmtMoney(rung.costRub)}</p>
              <p className="text-xs text-slate-500">остаток {fmtMoney(rung.residualRub)}</p>
            </div>
          ))}
        </div>

        <div className="table-wrap mt-4">
          <table className="data-table">
            <thead>
              <tr>
                <th>#</th>
                <th>Дата ступени</th>
                <th>Бюджет</th>
                <th>Бумага</th>
                <th>Кол-во</th>
                <th>Стоимость</th>
                <th>Остаток</th>
                <th>YTM</th>
                <th>Статус</th>
              </tr>
            </thead>
            <tbody>
              {rungs.map((rung) => (
                <tr key={rung.index}>
                  <td className="text-slate-500">{rung.index + 1}</td>
                  <td className="whitespace-nowrap">{fmtDate(rung.targetDate)}</td>
                  <td className="whitespace-nowrap">{fmtMoney(rung.budgetRub)}</td>
                  <td>
                    <div className="text-slate-200">{rung.bond?.name ?? "—"}</div>
                    <div className="font-mono text-xs text-slate-500">{rung.bond?.secid ?? ""}</div>
                  </td>
                  <td>{rung.quantity}</td>
                  <td className="whitespace-nowrap">{fmtMoney(rung.costRub)}</td>
                  <td className="whitespace-nowrap">{fmtMoney(rung.residualRub)}</td>
                  <td className="whitespace-nowrap text-emerald-300">{fmtPct(rung.ytmPct)}</td>
                  <td>
                    <Badge tone={rungStatus(rung.status).tone}>{rungStatus(rung.status).label}</Badge>
                    {rung.reason ? <p className="mt-1 max-w-sm text-xs text-slate-500">{rung.reason}</p> : null}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </SectionCard>

      {rolling ? (
        <SectionCard
          title="Что будет при реинвестировании"
          subtitle={`Rolling ladder на ${rolling.horizonYears} лет по ставке ${fmtPct(rolling.reinvestRatePct)}`}
        >
          <div className="grid gap-3 sm:grid-cols-4">
            <MetricBox label="Итоговый капитал" value={fmtMoney(rolling.finalCapitalRub)} accent />
            <MetricBox label="Купоны" value={fmtMoney(rolling.totalCouponsRub)} />
            <MetricBox label="Налоги" value={fmtMoney(rolling.totalTaxRub)} />
            <MetricBox label="Реинвестировано" value={fmtMoney(rolling.totalReinvestedRub)} />
          </div>
          <div className="table-wrap mt-4">
            <table className="w-full min-w-[520px] text-sm">
              <thead>
                <tr className="border-b border-white/10 text-left text-xs uppercase tracking-wide text-slate-500">
                  <th className="px-3 py-2">Год</th>
                  <th className="px-3 py-2">Купоны</th>
                  <th className="px-3 py-2">Налог</th>
                  <th className="px-3 py-2">Погашения</th>
                  <th className="px-3 py-2">Реинвест</th>
                  <th className="px-3 py-2">Капитал</th>
                </tr>
              </thead>
              <tbody>
                {rolling.rows.map((row) => (
                  <tr key={row.year} className="border-b border-white/5 last:border-0">
                    <td className="px-3 py-2">{row.year}</td>
                    <td className="px-3 py-2">{fmtMoney(row.couponGrossRub)}</td>
                    <td className="px-3 py-2 text-rose-300">−{fmtMoney(row.taxRub)}</td>
                    <td className="px-3 py-2">{fmtMoney(row.maturitiesRub)}</td>
                    <td className="px-3 py-2">{fmtMoney(row.reinvestedRub)}</td>
                    <td className="px-3 py-2 text-white">{fmtMoney(row.projectedCapitalRub)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </SectionCard>
      ) : null}
    </div>
  );
}

export function WheelSection({ result }: { result: CalculationResult }) {
  const comparison = result.comparison ?? [];
  const series = result.capitalizationSeries ?? [];
  const reinvestments = result.reinvestments ?? [];

  const chartData = useMemo(() => {
    const byDate = new Map<string, Record<string, number | string>>();
    for (const point of series) {
      const key = point.date.slice(0, 10);
      const row = byDate.get(key) ?? { date: key };
      row[point.scenario] = Number(point.valueRub);
      byDate.set(key, row);
    }
    return [...byDate.values()].sort((a, b) => String(a.date).localeCompare(String(b.date)));
  }, [series]);

  return (
    <div className="space-y-4">
      <SectionCard
        title="Сравнение сценариев"
        subtitle="Купоны реинвестируются, выводятся или копятся на счёте"
      >
        <div className="grid gap-3 lg:grid-cols-3">
          {comparison.map((row) => (
            <div
              key={row.scenario}
              className={
                row.scenario === "REINVEST"
                  ? "rounded-2xl border border-accent-500/40 bg-accent-500/[0.08] p-4"
                  : "rounded-2xl border border-white/10 bg-white/[0.02] p-4"
              }
            >
              <div className="flex items-center justify-between">
                <p className="text-sm font-semibold text-white">
                  {row.scenario === "REINVEST" ? "С реинвестом" : row.scenario === "CASH_OUT" ? "С выводом" : "Кэш на счёте"}
                </p>
                <Badge tone={row.scenario === "REINVEST" ? "success" : "neutral"}>
                  {row.scenario === "REINVEST" ? "сложный процент" : "без реинвеста"}
                </Badge>
              </div>
              <p className="mt-3 text-2xl font-semibold text-white">{fmtMoney(row.finalCapitalRub)}</p>
              <dl className="mt-3 space-y-1 text-sm">
                <div className="flex justify-between">
                  <dt className="text-slate-400">XIRR</dt>
                  <dd className="text-emerald-300">{fmtPct(row.xirrPct)}</dd>
                </div>
                <div className="flex justify-between">
                  <dt className="text-slate-400">Купоны</dt>
                  <dd>{fmtMoney(row.totalCouponsGrossRub)}</dd>
                </div>
                <div className="flex justify-between">
                  <dt className="text-slate-400">Налог</dt>
                  <dd>{fmtMoney(row.totalTaxRub)}</dd>
                </div>
                <div className="flex justify-between">
                  <dt className="text-slate-400">Реинвестировано</dt>
                  <dd>{fmtMoney(row.totalReinvestedRub)}</dd>
                </div>
                <div className="flex justify-between">
                  <dt className="text-slate-400">Остаток</dt>
                  <dd>{fmtMoney(row.cashResidualRub)}</dd>
                </div>
              </dl>
            </div>
          ))}
        </div>
      </SectionCard>

      <SectionCard title="Капитализация" subtitle="Стоимость портфеля по годам в трёх сценариях">
        <div className="h-72 w-full">
          <ResponsiveContainer width="100%" height="100%">
            <LineChart data={chartData} margin={{ top: 8, right: 12, bottom: 0, left: 0 }}>
              <CartesianGrid stroke="rgba(255,255,255,0.06)" strokeDasharray="3 3" vertical={false} />
              <XAxis dataKey="date" tick={{ fill: "#94a3b8", fontSize: 11 }} tickLine={false} axisLine={false} />
              <YAxis
                tick={{ fill: "#94a3b8", fontSize: 11 }}
                tickFormatter={(value) => `${Math.round(Number(value) / 1_000_000)} млн`}
                tickLine={false}
                axisLine={false}
                width={70}
              />
              <Tooltip contentStyle={TOOLTIP_STYLE} formatter={(value: number | string) => fmtMoney(Number(value))} />
              <Legend wrapperStyle={{ fontSize: 12, color: "#cbd5e1" }} />
              <Line type="monotone" dataKey="REINVEST" name="С реинвестом" stroke="#2dd4bf" strokeWidth={2.5} dot={false} isAnimationActive={false} />
              <Line type="monotone" dataKey="CASH_OUT" name="С выводом" stroke="#60a5fa" strokeWidth={2} dot={false} isAnimationActive={false} />
              <Line type="monotone" dataKey="CASH_BUFFER" name="Кэш" stroke="#c084fc" strokeWidth={2} strokeDasharray="5 4" dot={false} isAnimationActive={false} />
            </LineChart>
          </ResponsiveContainer>
        </div>
      </SectionCard>

      <SectionCard
        title="Реинвестиции"
        subtitle={`${reinvestments.length} сделок с учётом комиссии и лотов — показаны первые 20`}
      >
        {reinvestments.length === 0 ? (
          <p className="text-sm text-slate-400">
            Реинвестиций не было: вероятно, задана фиксированная ставка или купоны не покрывают лот.
          </p>
        ) : (
          <div className="table-wrap">
            <table className="w-full min-w-[520px] text-sm">
              <thead>
                <tr className="border-b border-white/10 text-left text-xs uppercase tracking-wide text-slate-500">
                  <th className="px-3 py-2">Дата</th>
                  <th className="px-3 py-2">Бумага</th>
                  <th className="px-3 py-2">Кол-во</th>
                  <th className="px-3 py-2">Цена</th>
                  <th className="px-3 py-2">Сумма</th>
                </tr>
              </thead>
              <tbody>
                {reinvestments.slice(0, 20).map((item, index) => (
                  <tr key={`${item.date}-${item.bond.secid}-${index}`} className="border-b border-white/5 last:border-0">
                    <td className="px-3 py-2 whitespace-nowrap">{fmtDate(item.date)}</td>
                    <td className="px-3 py-2">
                      <div className="text-slate-200">{item.bond.name}</div>
                      <div className="font-mono text-xs text-slate-500">{item.bond.secid}</div>
                    </td>
                    <td className="px-3 py-2">{item.quantity}</td>
                    <td className="px-3 py-2">{fmtPct(item.pricePct)}</td>
                    <td className="px-3 py-2">{fmtMoney(item.amountRub)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </SectionCard>
    </div>
  );
}

function MetricBox({ label, value, accent }: { label: string; value: string; accent?: boolean }) {
  return (
    <div className="rounded-xl border border-white/10 bg-white/[0.02] p-3">
      <p className="stat-label">{label}</p>
      <p className={accent ? "mt-1 text-lg font-semibold text-accent-300" : "mt-1 text-lg font-semibold text-white"}>
        {value}
      </p>
    </div>
  );
}

export function RungsMobileHint({ rungs }: { rungs: Rung[] }) {
  return <p className="text-xs text-slate-500">{rungs.length} ступеней</p>;
}
