import { useState } from "react";
import type { Position } from "../../shared/api/types";
import {
  fmtDate,
  fmtMoney,
  fmtPct,
  fmtYears,
  verdictClass,
  verdictLabel,
} from "../../shared/lib/format";
import { Badge } from "../../shared/ui";
import { ChecksList } from "./ChecksList";

export function PositionsTable({ positions }: { positions: Position[] }) {
  const [expanded, setExpanded] = useState<number | null>(null);

  if (!positions.length) {
    return <p className="text-sm text-slate-400">Позиции не сформированы — смотрите кандидатов и предупреждения.</p>;
  }

  return (
    <div className="table-wrap">
      <table className="data-table">
        <thead>
          <tr>
            <th>#</th>
            <th>Бумага</th>
            <th>Погашение</th>
            <th>Кол-во</th>
            <th>Цена</th>
            <th>Сумма</th>
            <th>Доля</th>
            <th>YTM</th>
            <th>Дюрация</th>
            <th>Вердикт</th>
            <th aria-label="Детали" />
          </tr>
        </thead>
        <tbody>
          {positions.map((position, index) => {
            const open = expanded === index;
            return (
              <FragmentRow
                key={`${position.bond.secid}-${position.rungIndex ?? index}-${index}`}
                position={position}
                index={index}
                open={open}
                onToggle={() => setExpanded(open ? null : index)}
              />
            );
          })}
        </tbody>
      </table>
    </div>
  );
}

function FragmentRow({
  position,
  index,
  open,
  onToggle,
}: {
  position: Position;
  index: number;
  open: boolean;
  onToggle: () => void;
}) {
  const bond = position.bond;
  return (
    <>
      <tr>
        <td className="text-slate-500">{position.rungIndex ?? index + 1}</td>
        <td>
          <div className="font-medium text-white">{bond.name ?? bond.secid}</div>
          <div className="mt-0.5 flex flex-wrap items-center gap-2 text-xs text-slate-500">
            <span className="font-mono">{bond.secid}</span>
            {bond.issuer ? <span>· {bond.issuer}</span> : null}
            {bond.rating ? (
              <Badge tone={bond.ratingOutlook === "NEGATIVE" ? "warn" : "neutral"}>
                {bond.rating}
                {bond.ratingAgency ? ` · ${bond.ratingAgency}` : ""}
              </Badge>
            ) : (
              <Badge tone="danger">NO_RATING</Badge>
            )}
            {position.leg ? <Badge tone="info">{position.leg}</Badge> : null}
          </div>
        </td>
        <td className="whitespace-nowrap text-slate-300">
          {fmtDate(bond.effectiveMaturity ?? bond.maturityDate)}
          {bond.offerDate ? <div className="text-xs text-amber-300/80">оферта {fmtDate(bond.offerDate)}</div> : null}
        </td>
        <td className="whitespace-nowrap">{position.quantity}</td>
        <td className="whitespace-nowrap">{fmtPct(bond.pricePct)}</td>
        <td className="whitespace-nowrap font-medium text-white">{fmtMoney(position.amountRub)}</td>
        <td className="whitespace-nowrap">{fmtPct((position.weight ?? 0) * 100)}</td>
        <td className="whitespace-nowrap text-emerald-300">{fmtPct(position.ytmPct)}</td>
        <td className="hidden whitespace-nowrap lg:table-cell">{fmtYears(position.durationYears)}</td>
        <td>
          <span className={verdictClass(position.checks?.verdict)}>{verdictLabel(position.checks?.verdict)}</span>
        </td>
        <td>
          <button
            className="btn-ghost btn-sm"
            aria-expanded={open}
            onClick={onToggle}
            title={open ? "Свернуть" : "Показать проверки и обоснование"}
          >
            {open ? "▴" : "▾"}
          </button>
        </td>
      </tr>
      {open ? (
        <tr className="bg-ink-950/40">
          <td colSpan={11} className="px-4 py-4">
            <div className="grid gap-4 lg:grid-cols-[1fr_1.6fr]">
              <div className="space-y-2 text-sm">
                <h4 className="font-semibold text-white">Обоснование выбора</h4>
                <p className="text-slate-300">{position.reason}</p>
                <dl className="mt-3 grid grid-cols-2 gap-2 text-xs text-slate-400">
                  <div>
                    <dt className="text-slate-500">ISIN</dt>
                    <dd className="font-mono text-slate-300">{bond.isin ?? "—"}</dd>
                  </div>
                  <div>
                    <dt className="text-slate-500">Листинг</dt>
                    <dd>{bond.listLevel ?? "—"}</dd>
                  </div>
                  <div>
                    <dt className="text-slate-500">НКД</dt>
                    <dd>{fmtMoney(bond.nkdRub)}</dd>
                  </div>
                  <div>
                    <dt className="text-slate-500">Лот</dt>
                    <dd>{bond.lotSize ?? 1}</dd>
                  </div>
                  <div>
                    <dt className="text-slate-500">Объём выпуска</dt>
                    <dd>{bond.issueSizeRub ? fmtMoney(bond.issueSizeRub) : "—"}</dd>
                  </div>
                  <div>
                    <dt className="text-slate-500">Рейтинг</dt>
                    <dd>
                      {bond.rating ?? "NO_RATING"}
                      {bond.ratingDate ? ` · ${fmtDate(bond.ratingDate)}` : ""}
                      {bond.ratingOutlook ? ` · ${bond.ratingOutlook}` : ""}
                    </dd>
                  </div>
                  {position.couponIncomeRub ? (
                    <div>
                      <dt className="text-slate-500">Купонный доход</dt>
                      <dd>{fmtMoney(position.couponIncomeRub)}</dd>
                    </div>
                  ) : null}
                  {position.costRub ? (
                    <div>
                      <dt className="text-slate-500">Стоимость</dt>
                      <dd>{fmtMoney(position.costRub)}</dd>
                    </div>
                  ) : null}
                </dl>
              </div>
              <div>
                <h4 className="mb-2 font-semibold text-white">Чек-лист актива</h4>
                <ChecksList checks={position.checks} />
              </div>
            </div>
          </td>
        </tr>
      ) : null}
    </>
  );
}
