import { useMemo } from "react";
import {
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  Legend,
  Pie,
  PieChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import type { CashFlowPoint, Position } from "../../shared/api/types";
import { fmtCompact, fmtMoney } from "../../shared/lib/format";

const TOOLTIP_STYLE = {
  background: "#0b1220",
  border: "1px solid rgba(255,255,255,0.12)",
  borderRadius: 12,
  color: "#e2e8f0",
  fontSize: 12,
};

export function CashFlowChart({ points }: { points: CashFlowPoint[] }) {
  const data = useMemo(() => {
    if (!points.length) return [];
    const dates = points.map((point) => new Date(point.date).getTime());
    const spanDays = (Math.max(...dates) - Math.min(...dates)) / 86_400_000;
    const byYear = spanDays > 1100;
    const map = new Map<string, { label: string; coupon: number; maturity: number; tax: number }>();
    for (const point of points) {
      const date = new Date(point.date);
      const label = byYear
        ? String(date.getFullYear())
        : `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}`;
      const bucket = map.get(label) ?? { label, coupon: 0, maturity: 0, tax: 0 };
      if (point.type === "COUPON") bucket.coupon += Number(point.amountRub);
      else if (point.type === "TAX") bucket.tax += Math.abs(Number(point.amountRub));
      else if (point.type === "MATURITY" || point.type === "AMORTIZATION") bucket.maturity += Number(point.amountRub);
      map.set(label, bucket);
    }
    return [...map.values()].sort((a, b) => a.label.localeCompare(b.label));
  }, [points]);

  if (!data.length) {
    return <p className="text-sm text-slate-400">Денежный поток не сформирован.</p>;
  }

  return (
    <div className="h-72 w-full">
      <ResponsiveContainer width="100%" height="100%">
        <BarChart data={data} margin={{ top: 8, right: 8, bottom: 0, left: 0 }}>
          <CartesianGrid stroke="rgba(255,255,255,0.06)" strokeDasharray="3 3" vertical={false} />
          <XAxis dataKey="label" tick={{ fill: "#94a3b8", fontSize: 11 }} tickLine={false} axisLine={false} />
          <YAxis
            tick={{ fill: "#94a3b8", fontSize: 11 }}
            tickFormatter={(value) => fmtCompact(Number(value))}
            tickLine={false}
            axisLine={false}
            width={86}
          />
          <Tooltip
            contentStyle={TOOLTIP_STYLE}
            formatter={(value: number | string) => fmtMoney(Number(value))}
            cursor={{ fill: "rgba(255,255,255,0.04)" }}
          />
          <Legend wrapperStyle={{ fontSize: 12, color: "#cbd5e1" }} />
          <Bar dataKey="coupon" name="Купоны" stackId="income" fill="#2dd4bf" radius={[3, 3, 0, 0]} />
          <Bar dataKey="maturity" name="Погашения" stackId="income" fill="#60a5fa" radius={[3, 3, 0, 0]} />
          <Bar dataKey="tax" name="НДФЛ" fill="#fb7185" radius={[3, 3, 0, 0]} />
        </BarChart>
      </ResponsiveContainer>
    </div>
  );
}

const COLORS = ["#2dd4bf", "#60a5fa", "#c084fc", "#fbbf24", "#f472b6", "#34d399", "#f97316", "#94a3b8"];

export function AllocationChart({ positions }: { positions: Position[] }) {
  const byIssuer = useMemo(() => {
    const map = new Map<string, number>();
    for (const position of positions) {
      const issuer = position.bond.issuer ?? "—";
      map.set(issuer, (map.get(issuer) ?? 0) + Number(position.amountRub ?? 0));
    }
    return [...map.entries()]
      .map(([name, value]) => ({ name, value }))
      .sort((a, b) => b.value - a.value);
  }, [positions]);

  if (!byIssuer.length) {
    return <p className="text-sm text-slate-400">Нет данных для распределения.</p>;
  }

  return (
    <div className="grid gap-4 lg:grid-cols-[1.2fr_1fr]">
      <div className="h-64">
        <ResponsiveContainer width="100%" height="100%">
          <PieChart>
            <Pie data={byIssuer} dataKey="value" nameKey="name" innerRadius={52} outerRadius={88} paddingAngle={2}>
              {byIssuer.map((entry, index) => (
                <Cell key={entry.name} fill={COLORS[index % COLORS.length]} stroke="rgba(0,0,0,0.2)" />
              ))}
            </Pie>
            <Tooltip contentStyle={TOOLTIP_STYLE} formatter={(value: number | string) => fmtMoney(Number(value))} />
          </PieChart>
        </ResponsiveContainer>
      </div>
      <ul className="space-y-2 text-sm">
        {byIssuer.slice(0, 8).map((entry, index) => (
          <li key={entry.name} className="flex items-center justify-between gap-3">
            <span className="flex min-w-0 items-center gap-2">
              <span
                className="h-2.5 w-2.5 shrink-0 rounded-full"
                style={{ background: COLORS[index % COLORS.length] }}
                aria-hidden="true"
              />
              <span className="truncate text-slate-300" title={entry.name}>
                {entry.name}
              </span>
            </span>
            <span className="shrink-0 text-slate-400">{fmtMoney(entry.value)}</span>
          </li>
        ))}
      </ul>
    </div>
  );
}
