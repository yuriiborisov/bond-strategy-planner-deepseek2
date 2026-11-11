import { useState } from "react";
import { Link } from "react-router-dom";
import type { CalculationResult } from "../../shared/api/types";
import { fmtDate, fmtDateTime, freshnessClass, freshnessLabel, strategyLabel } from "../../shared/lib/format";
import { Badge, Button, Card, Disclaimer, SectionCard } from "../../shared/ui";
import { SavePortfolioDialog } from "../save-portfolio/SavePortfolioDialog";
import { AiSection } from "../ai/AiSection";
import { AllocationChart, CashFlowChart } from "./Charts";
import { ChecksList } from "./ChecksList";
import { LadderSection, WheelSection } from "./LadderWheelSections";
import { PositionsTable } from "./PositionsTable";
import { SummaryMetrics } from "./SummaryMetrics";
import { BarbellSection, BulletSection } from "./StrategySections";

export function ResultsView({ result, onRecalculate }: { result: CalculationResult; onRecalculate?: () => void }) {
  const [saveOpen, setSaveOpen] = useState(false);
  const [saved, setSaved] = useState<{ id: number; name: string } | null>(null);

  const downloadCsv = () => {
    const header = ["secid", "name", "issuer", "maturity", "quantity", "amount_rub", "weight", "ytm_pct", "reason"];
    const lines = result.positions.map((position) =>
      [
        position.bond.secid,
        position.bond.name ?? "",
        position.bond.issuer ?? "",
        position.bond.effectiveMaturity ?? position.bond.maturityDate ?? "",
        position.quantity,
        position.amountRub,
        position.weight ?? "",
        position.ytmPct ?? "",
        `"${(position.reason ?? "").replace(/"/g, '""')}"`,
      ].join(","),
    );
    const blob = new Blob(["\uFEFF" + [header.join(","), ...lines].join("\n")], {
      type: "text/csv;charset=utf-8",
    });
    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = url;
    link.download = `calculation-${result.calculationId}.csv`;
    link.click();
    URL.revokeObjectURL(url);
  };

  return (
    <div className="space-y-5">
      <Card className="card-pad">
        <div className="flex flex-wrap items-start justify-between gap-4">
          <div>
            <div className="flex flex-wrap items-center gap-2">
              <h2 className="text-xl font-semibold text-white">{strategyLabel(String(result.strategy))}</h2>
              <Badge tone="neutral">{result.exchange}</Badge>
              <span className={freshnessClass(result.dataFreshness)}>{freshnessLabel(result.dataFreshness)}</span>
            </div>
            <p className="mt-1 text-xs text-slate-500">
              Расчёт #{result.calculationId} · снимок {fmtDateTime(result.snapshotTs)} · версия алгоритма{" "}
              {result.algorithmVersion ?? "1.0.0"}
            </p>
          </div>
          <div className="flex flex-wrap gap-2">
            {onRecalculate ? (
              <Button variant="ghost" size="sm" onClick={onRecalculate}>
                Пересчитать
              </Button>
            ) : null}
            <Button variant="secondary" size="sm" onClick={downloadCsv}>
              Экспорт CSV
            </Button>
            <Button variant="primary" size="sm" onClick={() => setSaveOpen(true)}>
              Сохранить портфель
            </Button>
          </div>
        </div>
        {saved ? (
          <p className="mt-3 rounded-xl border border-emerald-400/30 bg-emerald-400/10 px-3 py-2 text-sm text-emerald-200">
            Портфель «{saved.name}» сохранён.{" "}
            <Link to={`/portfolios/${saved.id}`} className="font-semibold text-emerald-100 underline">
              Открыть
            </Link>
          </p>
        ) : null}
      </Card>

      <SummaryMetrics result={result} />

      {result.strategy === "BULLET" ? <BulletSection result={result} /> : null}
      {result.strategy === "BARBELL" ? <BarbellSection result={result} /> : null}
      {result.strategy === "LADDER" ? <LadderSection result={result} /> : null}
      {result.strategy === "WHEEL" ? <WheelSection result={result} /> : null}

      <SectionCard
        title="Позиции портфеля"
        subtitle="Раскройте строку, чтобы увидеть обоснование и чек-лист проверок"
        actions={<Badge tone="neutral">{result.positions.length} позиций</Badge>}
      >
        <PositionsTable positions={result.positions} />
      </SectionCard>

      <div className="grid gap-4 lg:grid-cols-2">
        <SectionCard title="Денежный поток" subtitle="Купоны, погашения и НДФЛ по датам">
          <CashFlowChart points={result.cashFlowSchedule} />
        </SectionCard>
        <SectionCard title="Распределение" subtitle="По эмитентам, с учётом лимитов концентрации">
          <AllocationChart positions={result.positions} />
        </SectionCard>
      </div>

      {result.warnings.length ? (
        <SectionCard title="Предупреждения" subtitle="Что стоит проверить перед решением">
          <ul className="space-y-2">
            {result.warnings.map((warning, index) => (
              <li
                key={`${warning.code}-${index}`}
                className={
                  warning.severity === "ERROR"
                    ? "rounded-xl border border-rose-500/30 bg-rose-500/10 px-3 py-2 text-sm text-rose-100"
                    : warning.severity === "WARN"
                      ? "rounded-xl border border-amber-500/30 bg-amber-500/10 px-3 py-2 text-sm text-amber-100"
                      : "rounded-xl border border-sky-500/30 bg-sky-500/10 px-3 py-2 text-sm text-sky-100"
                }
              >
                <span className="mr-2 font-mono text-xs opacity-70">{warning.code}</span>
                {warning.message}
              </li>
            ))}
          </ul>
        </SectionCard>
      ) : null}

      <div className="grid gap-4 lg:grid-cols-2">
        <SectionCard title="Допущения расчёта" subtitle="Комиссии, налоги, округление и снимок данных">
          <dl className="grid grid-cols-2 gap-3 text-sm">
            {Object.entries(result.assumptions ?? {}).map(([key, value]) => (
              <div key={key} className="rounded-xl border border-white/10 bg-white/[0.02] px-3 py-2">
                <dt className="text-xs text-slate-500">{assumptionLabel(key)}</dt>
                <dd className="mt-0.5 break-words text-slate-200">
                  {value === null || value === undefined ? "—" : String(value)}
                </dd>
              </div>
            ))}
            <div className="rounded-xl border border-white/10 bg-white/[0.02] px-3 py-2">
              <dt className="text-xs text-slate-500">Снимок данных</dt>
              <dd className="mt-0.5 text-slate-200">{fmtDateTime(result.snapshotTs)}</dd>
            </div>
          </dl>
        </SectionCard>

        <SectionCard title="AI-обзор" subtitle="Опциональный слой объяснений; не влияет на расчёт и вердикты">
          <AiSection kind="discovery" calculationId={result.calculationId} />
        </SectionCard>
      </div>

      {result.positions.length === 0 ? (
        <SectionCard title="Проверки" subtitle="Проверки выполняются для рекомендованных бумаг">
          <p className="text-sm text-slate-400">
            Позиции не сформированы. Изучите кандидатов и предупреждения — возможно, бюджет меньше одного лота
            либо фильтры слишком строгие.
          </p>
          <div className="mt-3">
            <ChecksList checks={null} />
          </div>
        </SectionCard>
      ) : null}

      <div className="flex flex-wrap items-center justify-between gap-3">
        <p className="text-xs text-slate-500">
          Дата расчёта {fmtDate(new Date().toISOString())} · все суммы в рублях, проценты — до 2 знаков в UI.
        </p>
        <Badge tone="neutral">не является инвестиционной рекомендацией</Badge>
      </div>
      <Disclaimer />

      <SavePortfolioDialog
        open={saveOpen}
        calculationId={result.calculationId}
        defaultName={`${strategyLabel(String(result.strategy))} · ${fmtDate(result.snapshotTs)}`}
        onClose={() => setSaveOpen(false)}
        onSaved={(portfolio) => {
          setSaved(portfolio);
          setSaveOpen(false);
        }}
      />
    </div>
  );
}

function assumptionLabel(key: string): string {
  const labels: Record<string, string> = {
    commissionPct: "Комиссия, %",
    taxProfile: "Налоговый профиль",
    taxOnCoupons: "НДФЛ с купонов",
    reinvestRatePct: "Ставка реинвеста, %",
    currency: "Валюта",
    rounding: "Округление",
    algorithmVersion: "Версия алгоритма",
    valuation: "Оценка остатка",
    tax: "Налоги",
    commission: "Комиссии",
  };
  return labels[key] ?? key;
}
