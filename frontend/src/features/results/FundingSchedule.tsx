import { useMemo } from "react";
import {
  Bar,
  CartesianGrid,
  ComposedChart,
  Legend,
  Line,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import type { FundingSchedule } from "../../shared/api/types";
import { fmtCompact, fmtDate, fmtMoney } from "../../shared/lib/format";
import { Badge, SectionCard } from "../../shared/ui";

const TOOLTIP_STYLE = {
  background: "#0b1220",
  border: "1px solid rgba(255,255,255,0.12)",
  borderRadius: 12,
  color: "#e2e8f0",
  fontSize: 12,
};

export function FundingScheduleSection({ schedule }: { schedule?: FundingSchedule }) {
  const data = useMemo(() => {
    if (!schedule?.rows?.length) return [];
    const byDate = new Map<
      string,
      { date: string; contribution: number; purchases: number; cash: number; invested: number; labels: string[] }
    >();
    for (const row of schedule.rows) {
      const key = row.date.slice(0, 10);
      const current = byDate.get(key) ?? {
        date: key,
        contribution: 0,
        purchases: 0,
        cash: 0,
        invested: 0,
        labels: [],
      };
      current.contribution += Number(row.contributionRub ?? 0);
      current.purchases += Number(row.purchasesRub ?? 0);
      current.cash = Number(row.cashRub ?? 0);
      current.invested = Number(row.investedRub ?? 0);
      if (row.label) current.labels.push(row.label);
      byDate.set(key, current);
    }
    return [...byDate.values()].sort((a, b) => a.date.localeCompare(b.date));
  }, [schedule]);

  const chartData = useMemo(() => {
    if (data.length <= 80) return data;
    const byMonth = new Map<string, (typeof data)[number]>();
    for (const row of data) {
      const key = row.date.slice(0, 7) + "-01";
      const current = byMonth.get(key) ?? {
        date: key,
        contribution: 0,
        purchases: 0,
        cash: 0,
        invested: 0,
        labels: [],
      };
      current.contribution += row.contribution;
      current.purchases += row.purchases;
      current.cash = row.cash;
      current.invested = row.invested;
      byMonth.set(key, current);
    }
    return [...byMonth.values()].sort((a, b) => a.date.localeCompare(b.date));
  }, [data]);

  if (!schedule || data.length === 0) {
    return null;
  }

  const isBulletPlan = Number(schedule.monthlyContributionRub ?? 0) > 0;
  const showChart = data.length > 2;

  return (
    <SectionCard
      title="Покупки активов и пополнения"
      subtitle={
        isBulletPlan
          ? "Как график погашения кредита, только наоборот: взносы, покупки целыми лотами и остаток кэша"
          : "Стартовые покупки активов и реинвестирование купонов"
      }
      actions={<Badge tone="neutral">план формирования</Badge>}
    >
      <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
        {isBulletPlan ? (
          <Metric label="Взнос в месяц" value={fmtMoney(schedule.monthlyContributionRub)} hint="по плану" />
        ) : (
          <Metric label="Стартовое пополнение" value={fmtMoney(schedule.initialContributionRub)} hint={fmtDate(schedule.startDate)} />
        )}
        <Metric label="Всего пополнений" value={fmtMoney(schedule.totalContributionsRub)} />
        <Metric label={isBulletPlan ? "Покупки по плану" : "Куплено активов"} value={fmtMoney(schedule.totalPurchasesRub)} accent />
        <Metric label="Остаток кэша" value={fmtMoney(schedule.cashResidualRub)} hint={`горизонт ${fmtDate(schedule.endDate)}`} />
      </div>

      {showChart ? (
        <div className="mt-5 h-80 w-full">
          <ResponsiveContainer width="100%" height="100%">
            <ComposedChart data={chartData} margin={{ top: 8, right: 8, bottom: 0, left: 0 }}>
              <CartesianGrid stroke="rgba(255,255,255,0.06)" strokeDasharray="3 3" vertical={false} />
              <XAxis
                dataKey="date"
                tick={{ fill: "#94a3b8", fontSize: 11 }}
                tickLine={false}
                axisLine={false}
                minTickGap={22}
                tickFormatter={(value: string) => {
                  const date = new Date(value);
                  return Number.isNaN(date.getTime())
                    ? String(value)
                    : date.toLocaleDateString("ru-RU", { month: "2-digit", year: "2-digit" });
                }}
              />
              <YAxis
                yAxisId="left"
                tick={{ fill: "#94a3b8", fontSize: 11 }}
                tickFormatter={(value) => fmtCompact(Number(value))}
                tickLine={false}
                axisLine={false}
                width={78}
              />
              <YAxis
                yAxisId="right"
                orientation="right"
                tick={{ fill: "#94a3b8", fontSize: 11 }}
                tickFormatter={(value) => fmtCompact(Number(value))}
                tickLine={false}
                axisLine={false}
                width={82}
              />
              <Tooltip
                contentStyle={TOOLTIP_STYLE}
                labelFormatter={(label) => fmtDate(String(label))}
                formatter={(value: number | string) => fmtMoney(Number(value))}
                cursor={{ fill: "rgba(255,255,255,0.04)" }}
              />
              <Legend wrapperStyle={{ fontSize: 12, color: "#cbd5e1" }} />
              <Bar yAxisId="left" dataKey="contribution" name="Пополнение" fill="#2dd4bf" radius={[3, 3, 0, 0]} isAnimationActive={false} />
              <Bar yAxisId="left" dataKey="purchases" name="Покупка активов" fill="#60a5fa" radius={[3, 3, 0, 0]} isAnimationActive={false} />
              <Line yAxisId="right" type="monotone" dataKey="cash" name="Остаток кэша" stroke="#fbbf24" strokeWidth={2} dot={false} isAnimationActive={false} />
              <Line yAxisId="right" type="monotone" dataKey="invested" name="Вложено" stroke="#c084fc" strokeWidth={2} dot={false} isAnimationActive={false} />
            </ComposedChart>
          </ResponsiveContainer>
        </div>
      ) : (
        <p className="mt-4 rounded-xl border border-white/10 bg-white/[0.02] px-3 py-2 text-sm text-slate-400">
          Все активы куплены на старте; далее пополнений нет. Подробности — в таблице ниже.
        </p>
      )}

      <details className="mt-4 rounded-2xl border border-white/10 bg-ink-950/40 p-4">
        <summary className="cursor-pointer text-sm font-semibold text-slate-200">
          Таблица графика ({data.length} периодов)
          <span className="ml-2 text-xs font-normal text-slate-500">
            дата · пополнение · покупки · остаток · вложено
          </span>
        </summary>
        <div className="mt-3 max-h-96 overflow-auto rounded-xl border border-white/10">
          <table className="w-full min-w-[680px] text-sm">
            <thead className="sticky top-0 bg-ink-800/95 text-left text-xs uppercase tracking-wide text-slate-400">
              <tr>
                <th className="px-3 py-2">Дата</th>
                <th className="px-3 py-2">Пополнение</th>
                <th className="px-3 py-2">Покупки</th>
                <th className="px-3 py-2">Остаток кэша</th>
                <th className="px-3 py-2">Вложено</th>
                <th className="px-3 py-2">Кэш + вложено</th>
                <th className="px-3 py-2">Комментарий</th>
              </tr>
            </thead>
            <tbody>
              {schedule.rows.map((row, index) => (
                <tr key={`${row.date}-${index}`} className="border-t border-white/5">
                  <td className="whitespace-nowrap px-3 py-2 text-slate-300">{fmtDate(row.date)}</td>
                  <td className="whitespace-nowrap px-3 py-2">{fmtMoney(row.contributionRub)}</td>
                  <td className="whitespace-nowrap px-3 py-2 text-sky-300">{fmtMoney(row.purchasesRub)}</td>
                  <td className="whitespace-nowrap px-3 py-2">{fmtMoney(row.cashRub)}</td>
                  <td className="whitespace-nowrap px-3 py-2">{fmtMoney(row.investedRub)}</td>
                  <td className="whitespace-nowrap px-3 py-2 text-white">{fmtMoney(row.portfolioValueRub)}</td>
                  <td className="px-3 py-2 text-xs text-slate-500">{row.label ?? "—"}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </details>

      <details className="mt-3 rounded-2xl border border-white/10 bg-ink-950/40 p-4">
        <summary className="cursor-pointer text-sm font-semibold text-slate-200">
          Список покупок ({schedule.purchases.length})
          <span className="ml-2 text-xs font-normal text-slate-500">дата · бумага · количество · сумма</span>
        </summary>
        <div className="mt-3 max-h-72 overflow-auto rounded-xl border border-white/10">
          <table className="w-full min-w-[560px] text-sm">
            <thead className="sticky top-0 bg-ink-800/95 text-left text-xs uppercase tracking-wide text-slate-400">
              <tr>
                <th className="px-3 py-2">Дата</th>
                <th className="px-3 py-2">Бумага</th>
                <th className="px-3 py-2">Кол-во</th>
                <th className="px-3 py-2">Сумма</th>
                <th className="px-3 py-2">Тип</th>
              </tr>
            </thead>
            <tbody>
              {schedule.purchases.map((purchase, index) => (
                <tr key={`${purchase.secid}-${purchase.date}-${index}`} className="border-t border-white/5">
                  <td className="whitespace-nowrap px-3 py-2 text-slate-300">{fmtDate(purchase.date)}</td>
                  <td className="px-3 py-2">
                    <div className="text-slate-200">{purchase.name || purchase.secid}</div>
                    <div className="font-mono text-xs text-slate-500">{purchase.secid}</div>
                  </td>
                  <td className="px-3 py-2">{purchase.quantity}</td>
                  <td className="px-3 py-2 text-slate-200">{fmtMoney(purchase.amountRub)}</td>
                  <td className="px-3 py-2 text-xs text-slate-500">
                    {purchase.kind === "INITIAL" ? "старт" : purchase.kind === "REINVEST" ? "реинвест" : "по плану"}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </details>

      {schedule.note ? <p className="mt-3 text-xs text-slate-500">{schedule.note}</p> : null}
    </SectionCard>
  );
}

function Metric({
  label,
  value,
  hint,
  accent,
}: {
  label: string;
  value: string;
  hint?: string;
  accent?: boolean;
}) {
  return (
    <div className="rounded-xl border border-white/10 bg-white/[0.02] p-3">
      <p className="stat-label">{label}</p>
      <p className={accent ? "mt-1 text-lg font-semibold text-sky-300" : "mt-1 text-lg font-semibold text-white"}>
        {value}
      </p>
      {hint ? <p className="mt-0.5 text-xs text-slate-500">{hint}</p> : null}
    </div>
  );
}
