const moneyFormatter = new Intl.NumberFormat("ru-RU", {
  style: "currency",
  currency: "RUB",
  maximumFractionDigits: 0,
});

const money2Formatter = new Intl.NumberFormat("ru-RU", {
  style: "currency",
  currency: "RUB",
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
});

const numberFormatter = new Intl.NumberFormat("ru-RU");

export function fmtMoney(value: number | string | null | undefined): string {
  if (value === null || value === undefined || Number.isNaN(Number(value))) return "—";
  return moneyFormatter.format(Number(value));
}

export function fmtMoney2(value: number | string | null | undefined): string {
  if (value === null || value === undefined || Number.isNaN(Number(value))) return "—";
  return money2Formatter.format(Number(value));
}

export function fmtNumber(value: number | string | null | undefined): string {
  if (value === null || value === undefined || Number.isNaN(Number(value))) return "—";
  return numberFormatter.format(Number(value));
}

export function fmtPct(value: number | string | null | undefined, digits = 2): string {
  if (value === null || value === undefined || Number.isNaN(Number(value))) return "—";
  return `${Number(value).toFixed(digits).replace(".", ",")}%`;
}

export function fmtYears(value: number | string | null | undefined): string {
  if (value === null || value === undefined || Number.isNaN(Number(value))) return "—";
  return `${Number(value).toFixed(2).replace(".", ",")} лет`;
}

export function fmtDate(value: string | null | undefined): string {
  if (!value) return "—";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "—";
  return date.toLocaleDateString("ru-RU", { day: "2-digit", month: "2-digit", year: "numeric" });
}

export function fmtDateTime(value: string | null | undefined): string {
  if (!value) return "—";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "—";
  return date.toLocaleString("ru-RU", {
    day: "2-digit",
    month: "2-digit",
    year: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  });
}

export function fmtCompact(value: number | null | undefined): string {
  if (value === null || value === undefined || Number.isNaN(Number(value))) return "—";
  const abs = Math.abs(Number(value));
  if (abs >= 1_000_000_000_000) return `${(Number(value) / 1_000_000_000_000).toFixed(2)} трлн ₽`;
  if (abs >= 1_000_000_000) return `${(Number(value) / 1_000_000_000).toFixed(1)} млрд ₽`;
  if (abs >= 1_000_000) return `${(Number(value) / 1_000_000).toFixed(1)} млн ₽`;
  return fmtMoney(value);
}

export function riskLabel(level: number): string {
  return ["", "Низкий", "Умеренный", "Средний", "Повышенный", "Высокий"][level] ?? "—";
}

export function riskClass(level: number): string {
  if (level <= 2) return "badge-success";
  if (level === 3) return "badge-warn";
  return "badge-danger";
}

export function verdictClass(verdict: string | null | undefined): string {
  switch (verdict) {
    case "APPROVED":
      return "badge-success";
    case "WATCH":
      return "badge-warn";
    case "REJECTED":
      return "badge-danger";
    default:
      return "badge-neutral";
  }
}

export function verdictLabel(verdict: string | null | undefined): string {
  switch (verdict) {
    case "APPROVED":
      return "Одобрено";
    case "WATCH":
      return "Наблюдать";
    case "REJECTED":
      return "Отклонено";
    default:
      return "Нет данных";
  }
}

export function checkClass(status: string): string {
  switch (status) {
    case "PASS":
      return "badge-success";
    case "WARN":
      return "badge-warn";
    case "FAIL":
      return "badge-danger";
    default:
      return "badge-neutral";
  }
}

export function checkLabel(status: string): string {
  switch (status) {
    case "PASS":
      return "PASS";
    case "WARN":
      return "WARN";
    case "FAIL":
      return "FAIL";
    default:
      return "UNKNOWN";
  }
}

export function checkName(code: string): string {
  const names: Record<string, string> = {
    CREDIT_RATING: "Кредитный рейтинг",
    RATING_OUTLOOK: "Прогноз рейтинга",
    RATING_AGE: "Актуальность рейтинга",
    LIST_LEVEL: "Уровень листинга",
    ISSUE_SIZE: "Объём выпуска",
    LIQUIDITY: "Ликвидность",
    YTM_VS_PEER: "YTM против медианы",
    DURATION_FIT: "Соответствие сроку",
    PRICE_MOMENTUM: "Моментум цены",
    REVENUE_GROWTH: "Рост выручки",
    DEBT_EBITDA: "Debt/EBITDA",
    INTEREST_COVERAGE: "Покрытие процентов",
    EVENT_RISK: "Событийный риск",
  };
  return names[code] ?? code;
}

export function warningClass(severity: string): string {
  switch (severity) {
    case "ERROR":
      return "border-rose-500/30 bg-rose-500/10 text-rose-100";
    case "WARN":
      return "border-amber-500/30 bg-amber-500/10 text-amber-100";
    default:
      return "border-sky-500/30 bg-sky-500/10 text-sky-100";
  }
}

export function strategyLabel(code: string): string {
  const labels: Record<string, string> = {
    BULLET: "Пуля",
    BARBELL: "Штанга",
    LADDER: "Лестница",
    WHEEL: "Колесо",
  };
  return labels[code] ?? code;
}

export function freshnessLabel(value: string): string {
  switch (value) {
    case "FRESH":
      return "Данные актуальны";
    case "STALE":
      return "Данные старше 24 часов";
    case "EXPIRED":
      return "Данные устарели";
    default:
      return "Свежесть неизвестна";
  }
}

export function freshnessClass(value: string): string {
  switch (value) {
    case "FRESH":
      return "badge-success";
    case "STALE":
      return "badge-warn";
    default:
      return "badge-danger";
  }
}

export function todayIso(plusDays = 0): string {
  const date = new Date();
  date.setDate(date.getDate() + plusDays);
  return date.toISOString().slice(0, 10);
}

export function addYearsIso(years: number): string {
  const date = new Date();
  date.setFullYear(date.getFullYear() + years);
  return date.toISOString().slice(0, 10);
}
