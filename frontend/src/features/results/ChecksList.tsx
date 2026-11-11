import type { AssetCheck, CheckItem } from "../../shared/api/types";
import { checkClass, checkLabel, checkName, fmtDate, fmtNumber, fmtPct, verdictClass, verdictLabel } from "../../shared/lib/format";
import { Badge } from "../../shared/ui";

function formatValue(item: CheckItem): string {
  const value = item.value;
  if (value === null || value === undefined) return "—";
  if (typeof value === "number") {
    if (item.code.includes("RATING") && !item.code.includes("AGE")) return String(value);
    if (item.code === "YTM_VS_PEER" || item.code === "PRICE_MOMENTUM" || item.code === "REVENUE_GROWTH") {
      return fmtPct(value);
    }
    return fmtNumber(value);
  }
  if (typeof value === "object") return JSON.stringify(value);
  return String(value);
}

export function ChecksList({ checks }: { checks?: AssetCheck | null }) {
  if (!checks) {
    return <p className="text-sm text-slate-500">Проверки не запрошены для этого расчёта.</p>;
  }
  return (
    <div className="space-y-3">
      <div className="flex flex-wrap items-center gap-3">
        <Badge className={verdictClass(checks.verdict)}>{verdictLabel(checks.verdict)}</Badge>
        <span className="text-sm text-slate-400">
          Score: <span className="font-semibold text-white">{checks.score}</span>
        </span>
        {checks.asOf ? <span className="text-xs text-slate-500">на {fmtDate(checks.asOf)}</span> : null}
      </div>
      {checks.explanation ? <p className="text-sm text-slate-400">{checks.explanation}</p> : null}
      <div className="table-wrap">
        <table className="w-full min-w-[560px] text-sm">
          <thead>
            <tr className="border-b border-white/10 text-left text-xs uppercase tracking-wide text-slate-500">
              <th className="px-3 py-2">Проверка</th>
              <th className="px-3 py-2">Статус</th>
              <th className="px-3 py-2">Значение</th>
              <th className="px-3 py-2">Порог</th>
              <th className="px-3 py-2">Источник</th>
              <th className="px-3 py-2">Дата</th>
            </tr>
          </thead>
          <tbody>
            {checks.checks.map((item) => (
              <tr key={item.code} className="border-b border-white/5 last:border-0">
                <td className="px-3 py-2 text-slate-200">{checkName(item.code)}</td>
                <td className="px-3 py-2">
                  <span className={checkClass(item.status)}>{checkLabel(item.status)}</span>
                </td>
                <td className="px-3 py-2 text-slate-300">{formatValue(item)}</td>
                <td className="px-3 py-2 text-slate-400">{item.threshold ?? "—"}</td>
                <td className="px-3 py-2 text-slate-400">{item.source ?? "—"}</td>
                <td className="px-3 py-2 text-slate-500">{fmtDate(item.asOf ?? undefined)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
