import type { CalculationResult } from "../../shared/api/types";
import { fmtMoney, fmtNumber, fmtPct, fmtYears } from "../../shared/lib/format";
import { Badge } from "../../shared/ui";

interface Metric {
  label: string;
  value: string;
  hint?: string;
  tone?: "default" | "success" | "warn" | "danger";
}

function metric(label: string, value: string, hint?: string, tone: Metric["tone"] = "default"): Metric {
  return { label, value, hint, tone };
}

export function SummaryMetrics({ result }: { result: CalculationResult }) {
  const s = result.summary ?? {};
  const metrics: Metric[] = [];

  if (result.strategy === "BULLET") {
    metrics.push(
      metric("Необходимый взнос/мес", fmtMoney(s.requiredMonthlyContributionRub as number), "при базовой YTM"),
      metric("Прогноз капитала", fmtMoney(s.projectedValueRub as number), "к дате цели"),
      metric(
        "Цель достижима",
        s.achievement ? "Да" : "Нет",
        fmtMoney(s.surplusOrGapRub as number),
        s.achievement ? "success" : "warn",
      ),
      metric("Стартовый капитал", fmtMoney(s.startCapitalRub as number), "инвестировано сейчас"),
    );
  } else if (result.strategy === "BARBELL") {
    metrics.push(
      metric("Сумма", fmtMoney(s.totalAmountRub as number)),
      metric("Инвестировано", fmtMoney(s.investedRub as number), "с комиссией"),
      metric("Средневзвешенная YTM", fmtPct(s.weightedYtmPct as number), undefined, "success"),
      metric("Дюрация", fmtYears(s.weightedDurationYears as number)),
      metric("Короткое плечо", fmtMoney(s.shortPartRub as number)),
      metric("Длинное плечо", fmtMoney(s.longPartRub as number)),
    );
  } else if (result.strategy === "LADDER") {
    metrics.push(
      metric("Сумма", fmtMoney(s.totalAmountRub as number)),
      metric("Инвестировано", fmtMoney(s.investedRub as number), "с комиссией"),
      metric("Остаток", fmtMoney(s.cashResidualRub as number), "в т.ч. перенос между ступенями"),
      metric("Средневзвешенная YTM", fmtPct(s.weightedYtmPct as number), undefined, "success"),
      metric("Дюрация", fmtYears(s.weightedDurationYears as number)),
      metric("Заполнено ступеней", `${fmtNumber(s.filledRungs as number)} из ${fmtNumber(s.rungsCount as number)}`),
    );
  } else {
    metrics.push(
      metric("Итоговый капитал", fmtMoney(s.finalCapitalRub as number), "сценарий реинвеста", "success"),
      metric("XIRR", fmtPct(s.xirrPct as number), "денежный поток с учётом дат"),
      metric("CAGR", fmtPct(s.cagrPct as number), "среднегодовой рост"),
      metric("Купоны (gross)", fmtMoney(s.totalCouponsGrossRub as number)),
      metric("Налог", fmtMoney(s.totalTaxRub as number)),
      metric("Реинвестировано", fmtMoney(s.totalReinvestedRub as number), `${fmtNumber(s.reinvestmentCount as number)} сделок`),
    );
  }

  metrics.push(
    metric("Купоны", fmtMoney(s.totalCouponIncomeRub as number)),
    metric("Налог с купонов", fmtMoney(s.totalTaxRub as number)),
    metric("Остаток", fmtMoney(s.cashResidualRub as number)),
  );

  return (
    <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
      {metrics.slice(0, 8).map((item) => (
        <div key={item.label} className="rounded-2xl border border-white/10 bg-ink-900/60 p-4">
          <p className="stat-label">{item.label}</p>
          <p
            className={
              item.tone === "success"
                ? "stat-value text-emerald-300"
                : item.tone === "warn"
                  ? "stat-value text-amber-300"
                  : item.tone === "danger"
                    ? "stat-value text-rose-300"
                    : "stat-value"
            }
          >
            {item.value}
          </p>
          {item.hint ? <p className="mt-1 text-xs text-slate-500">{item.hint}</p> : null}
        </div>
      ))}
      {s.achievement !== undefined ? (
        <div className="rounded-2xl border border-white/10 bg-ink-900/60 p-4">
          <p className="stat-label">Проверка цели</p>
          <div className="mt-1">
            <Badge tone={s.achievement ? "success" : "warn"}>
              {s.achievement ? "Цель достижима" : "Есть дефицит"}
            </Badge>
          </div>
        </div>
      ) : null}
    </div>
  );
}
