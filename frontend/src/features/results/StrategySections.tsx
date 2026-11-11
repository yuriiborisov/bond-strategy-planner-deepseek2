import type { CalculationResult } from "../../shared/api/types";
import { fmtDate, fmtMoney, fmtPct } from "../../shared/lib/format";
import { Badge, SectionCard } from "../../shared/ui";

export function BulletSection({ result }: { result: CalculationResult }) {
  const scenarios = result.contributionScenarios ?? [];
  const candidates = result.candidates ?? [];
  return (
    <div className="grid gap-4 lg:grid-cols-2">
      <SectionCard
        title="Взнос и сценарии"
        subtitle="Пессимистичный / базовый / оптимистичный сценарий — ±200 б.п. к YTM"
      >
        <div className="rounded-2xl border border-accent-500/30 bg-accent-500/[0.07] p-4">
          <p className="text-xs uppercase tracking-wide text-accent-300">Необходимый ежемесячный взнос</p>
          <p className="mt-1 text-3xl font-semibold text-white">{fmtMoney(result.monthlyContributionRub)}</p>
          <p className="mt-1 text-xs text-slate-400">
            Цель {fmtMoney(result.summary.targetAmountRub as number)} к {fmtDate(result.targetDate)}
          </p>
        </div>
        <div className="mt-4 space-y-2">
          {scenarios.map((scenario) => (
            <div
              key={scenario.scenario}
              className="flex items-center justify-between gap-3 rounded-xl border border-white/10 bg-white/[0.02] px-3 py-2.5"
            >
              <div>
                <p className="text-sm font-medium capitalize text-slate-200">
                  {scenario.scenario === "pessimistic"
                    ? "Пессимистичный"
                    : scenario.scenario === "optimistic"
                      ? "Оптимистичный"
                      : "Базовый"}
                </p>
                <p className="text-xs text-slate-500">YTM {fmtPct(scenario.ytmPct)}</p>
              </div>
              <div className="text-right">
                <p className="text-sm text-white">{fmtMoney(scenario.projectedValueRub)}</p>
                <p className={`text-xs ${scenario.surplusOrGapRub >= 0 ? "text-emerald-300" : "text-rose-300"}`}>
                  {scenario.surplusOrGapRub >= 0 ? "+" : ""}
                  {fmtMoney(scenario.surplusOrGapRub)} к цели
                </p>
              </div>
              <div className="hidden text-right text-xs text-slate-500 sm:block">
                взнос
                <div className="text-slate-300">{fmtMoney(scenario.monthlyContributionRub)}</div>
              </div>
            </div>
          ))}
        </div>
      </SectionCard>

      <SectionCard
        title="Бумаги в окне цели"
        subtitle={`Кандидаты с погашением около ${fmtDate(result.targetDate)} и прошедшие проверки`}
      >
        {candidates.length === 0 ? (
          <p className="text-sm text-slate-400">Бумаги не найдены — попробуйте увеличить допуск или ослабить фильтры.</p>
        ) : (
          <div className="table-wrap">
            <table className="w-full min-w-[480px] text-sm">
              <thead>
                <tr className="border-b border-white/10 text-left text-xs uppercase tracking-wide text-slate-500">
                  <th className="px-3 py-2">Бумага</th>
                  <th className="px-3 py-2">Погашение</th>
                  <th className="px-3 py-2">YTM</th>
                  <th className="px-3 py-2">Вердикт</th>
                </tr>
              </thead>
              <tbody>
                {candidates.map((candidate) => (
                  <tr key={candidate.bond.secid} className="border-b border-white/5 last:border-0">
                    <td className="px-3 py-2">
                      <div className="text-slate-200">{candidate.bond.name ?? candidate.bond.secid}</div>
                      <div className="text-xs text-slate-500">
                        {candidate.bond.secid}
                        {candidate.bond.rating ? ` · ${candidate.bond.rating}` : ""}
                      </div>
                    </td>
                    <td className="px-3 py-2 text-slate-300">{fmtDate(candidate.bond.maturityDate)}</td>
                    <td className="px-3 py-2 text-emerald-300">{fmtPct(candidate.bond.ytmPct)}</td>
                    <td className="px-3 py-2">
                      <Badge tone={candidate.checks?.verdict === "APPROVED" ? "success" : "warn"}>
                        {candidate.checks?.verdict ?? "—"}
                      </Badge>
                    </td>
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

export function BarbellSection({ result }: { result: CalculationResult }) {
  const legs = result.legs ?? [];
  const stress = result.stressTest ?? [];
  return (
    <div className="grid gap-4 lg:grid-cols-2">
      <SectionCard title="Плечи портфеля" subtitle="Короткое плечо — ликвидность, длинное — доходность">
        <div className="grid gap-3 sm:grid-cols-2">
          {legs.map((leg) => (
            <div key={leg.leg} className="rounded-2xl border border-white/10 bg-white/[0.02] p-4">
              <div className="flex items-center justify-between">
                <p className="text-sm font-semibold text-white">{leg.leg === "SHORT" ? "Короткое" : "Длинное"} плечо</p>
                <Badge tone="info">{fmtPct(leg.targetShare * 100)}</Badge>
              </div>
              <p className="mt-3 text-2xl font-semibold text-white">{fmtMoney(leg.investedRub)}</p>
              <p className="text-xs text-slate-500">из {fmtMoney(leg.amountRub)}</p>
              <dl className="mt-3 space-y-1 text-sm">
                <div className="flex justify-between">
                  <dt className="text-slate-400">YTM</dt>
                  <dd className="text-emerald-300">{fmtPct(leg.weightedYtmPct)}</dd>
                </div>
                <div className="flex justify-between">
                  <dt className="text-slate-400">Дюрация</dt>
                  <dd>{leg.weightedDurationYears?.toFixed(2) ?? "—"} лет</dd>
                </div>
                <div className="flex justify-between">
                  <dt className="text-slate-400">Бумаг</dt>
                  <dd>{leg.bondsCount}</dd>
                </div>
              </dl>
            </div>
          ))}
        </div>
      </SectionCard>

      <SectionCard
        title="Стресс-тест ставки"
        subtitle="ΔP ≈ −D_mod·Δy·P + 0.5·C·Δy²·P, C ≈ D_mod²"
      >
        <div className="table-wrap">
          <table className="w-full min-w-[420px] text-sm">
            <thead>
              <tr className="border-b border-white/10 text-left text-xs uppercase tracking-wide text-slate-500">
                <th className="px-3 py-2">Δy, б.п.</th>
                <th className="px-3 py-2">Длинное плечо</th>
                <th className="px-3 py-2">Портфель</th>
                <th className="px-3 py-2">%</th>
              </tr>
            </thead>
            <tbody>
              {stress.map((row) => (
                <tr key={row.deltaBp} className="border-b border-white/5 last:border-0">
                  <td className="px-3 py-2 font-mono text-slate-300">{row.deltaBp > 0 ? `+${row.deltaBp}` : row.deltaBp}</td>
                  <td className={`px-3 py-2 ${row.longLegChangeRub >= 0 ? "text-emerald-300" : "text-rose-300"}`}>
                    {fmtMoney(row.longLegChangeRub)}
                  </td>
                  <td className={`px-3 py-2 ${row.portfolioChangeRub >= 0 ? "text-emerald-300" : "text-rose-300"}`}>
                    {fmtMoney(row.portfolioChangeRub)}
                  </td>
                  <td className="px-3 py-2 text-slate-400">{row.portfolioChangePct?.toFixed(2) ?? "—"}%</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        <p className="mt-3 text-xs text-slate-500">
          Оценка чувствительности к параллельному сдвигу кривой; фактическая реакция зависит от формы кривой и ликвидности.
        </p>
      </SectionCard>
    </div>
  );
}
