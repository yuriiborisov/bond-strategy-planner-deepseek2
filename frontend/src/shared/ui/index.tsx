import { clsx } from "clsx";
import { forwardRef, useEffect, type ButtonHTMLAttributes, type InputHTMLAttributes, type ReactNode } from "react";
import type { ApiError } from "../api/client";
import { riskLabel } from "../lib/format";

export function Button({
  variant = "primary",
  size,
  className,
  ...props
}: ButtonHTMLAttributes<HTMLButtonElement> & {
  variant?: "primary" | "secondary" | "ghost" | "danger";
  size?: "sm";
}) {
  return (
    <button
      className={clsx(
        variant === "primary" && "btn-primary",
        variant === "secondary" && "btn-secondary",
        variant === "ghost" && "btn-ghost",
        variant === "danger" && "btn-danger",
        size === "sm" && "btn-sm",
        className,
      )}
      {...props}
    />
  );
}

export function Card({ className, children }: { className?: string; children?: ReactNode }) {
  return <div className={clsx("card", className)}>{children}</div>;
}

export function SectionCard({
  title,
  subtitle,
  actions,
  className,
  children,
}: {
  title: string;
  subtitle?: ReactNode;
  actions?: ReactNode;
  className?: string;
  children: ReactNode;
}) {
  return (
    <Card className={className}>
      <div className="flex flex-wrap items-start justify-between gap-3 border-b border-white/5 px-5 py-4">
        <div>
          <h2 className="section-title">{title}</h2>
          {subtitle ? <p className="mt-1 text-sm text-slate-400">{subtitle}</p> : null}
        </div>
        {actions ? <div className="flex flex-wrap items-center gap-2">{actions}</div> : null}
      </div>
      <div className="card-pad">{children}</div>
    </Card>
  );
}

export function Field({
  label,
  hint,
  error,
  htmlFor,
  children,
  className,
}: {
  label: string;
  hint?: string;
  error?: string;
  htmlFor?: string;
  children: ReactNode;
  className?: string;
}) {
  return (
    <div className={className}>
      <label className="label" htmlFor={htmlFor}>
        {label}
      </label>
      {children}
      {error ? (
        <p className="mt-1 text-xs text-rose-300" role="alert">
          {error}
        </p>
      ) : hint ? (
        <p className="hint">{hint}</p>
      ) : null}
    </div>
  );
}

export const TextInput = forwardRef<HTMLInputElement, InputHTMLAttributes<HTMLInputElement>>(
  function TextInput({ className, ...props }, ref) {
    return <input ref={ref} className={clsx("input", className)} {...props} />;
  },
);

export const Select = forwardRef<HTMLSelectElement, React.SelectHTMLAttributes<HTMLSelectElement>>(
  function Select({ className, children, ...props }, ref) {
    return (
      <select ref={ref} className={clsx("input", className)} {...props}>
        {children}
      </select>
    );
  },
);

export function Checkbox({
  label,
  hint,
  ...props
}: InputHTMLAttributes<HTMLInputElement> & { label: string; hint?: string }) {
  return (
    <label className="flex cursor-pointer items-start gap-2.5 rounded-xl border border-white/10 bg-ink-950/40 px-3 py-2.5">
      <input type="checkbox" className="mt-0.5 h-4 w-4 accent-teal-400" {...props} />
      <span>
        <span className="block text-sm text-slate-200">{label}</span>
        {hint ? <span className="block text-xs text-slate-500">{hint}</span> : null}
      </span>
    </label>
  );
}

export function Badge({
  tone = "neutral",
  children,
  className,
}: {
  tone?: "neutral" | "success" | "warn" | "danger" | "info";
  children: ReactNode;
  className?: string;
}) {
  const map = {
    neutral: "badge-neutral",
    success: "badge-success",
    warn: "badge-warn",
    danger: "badge-danger",
    info: "badge-info",
  } as const;
  return <span className={clsx(map[tone], className)}>{children}</span>;
}

export function Spinner({ className }: { className?: string }) {
  return (
    <span
      className={clsx(
        "inline-block h-4 w-4 animate-spin rounded-full border-2 border-current border-r-transparent",
        className,
      )}
      aria-hidden="true"
    />
  );
}

export function LoadingBlock({ label = "Загрузка…" }: { label?: string }) {
  return (
    <div className="flex items-center justify-center gap-3 py-12 text-sm text-slate-400">
      <Spinner />
      {label}
    </div>
  );
}

export function ErrorState({ error, onRetry }: { error: unknown; onRetry?: () => void }) {
  const apiError = error as ApiError | null;
  const message = apiError?.message ?? "Не удалось загрузить данные";
  const suggestions = apiError?.suggestions ?? [];
  return (
    <Card className="border-rose-500/30 bg-rose-500/[0.06]">
      <div className="card-pad">
        <div className="flex items-start gap-3">
          <span className="mt-0.5 text-lg" aria-hidden="true">
            ⚠
          </span>
          <div className="min-w-0">
            <h3 className="text-base font-semibold text-rose-100">{apiError?.code ?? "Ошибка"}</h3>
            <p className="mt-1 text-sm text-rose-100/80">{message}</p>
            {suggestions.length ? (
              <ul className="mt-3 list-disc space-y-1 pl-5 text-sm text-rose-100/70">
                {suggestions.map((item) => (
                  <li key={item}>{item}</li>
                ))}
              </ul>
            ) : null}
            {onRetry ? (
              <Button variant="secondary" size="sm" className="mt-4" onClick={onRetry}>
                Повторить
              </Button>
            ) : null}
          </div>
        </div>
      </div>
    </Card>
  );
}

export function EmptyState({
  title,
  description,
  action,
}: {
  title: string;
  description?: string;
  action?: ReactNode;
}) {
  return (
    <div className="flex flex-col items-center justify-center rounded-2xl border border-dashed border-white/15 px-6 py-12 text-center">
      <p className="text-base font-medium text-slate-200">{title}</p>
      {description ? <p className="mt-2 max-w-md text-sm text-slate-400">{description}</p> : null}
      {action ? <div className="mt-4">{action}</div> : null}
    </div>
  );
}

export function Modal({
  open,
  title,
  onClose,
  children,
  footer,
}: {
  open: boolean;
  title: string;
  onClose: () => void;
  children: ReactNode;
  footer?: ReactNode;
}) {
  useEffect(() => {
    if (!open) return;
    const handler = (event: KeyboardEvent) => {
      if (event.key === "Escape") onClose();
    };
    document.addEventListener("keydown", handler);
    return () => document.removeEventListener("keydown", handler);
  }, [open, onClose]);

  if (!open) return null;
  return (
    <div className="fixed inset-0 z-50 flex items-end justify-center bg-ink-950/80 p-0 backdrop-blur-sm sm:items-center sm:p-6">
      <div
        role="dialog"
        aria-modal="true"
        aria-label={title}
        className="card max-h-[90vh] w-full max-w-xl overflow-y-auto rounded-b-none rounded-t-2xl sm:rounded-2xl"
      >
        <div className="flex items-center justify-between border-b border-white/5 px-5 py-4">
          <h2 className="text-base font-semibold text-white">{title}</h2>
          <button className="btn-ghost btn-sm" onClick={onClose} aria-label="Закрыть">
            ✕
          </button>
        </div>
        <div className="card-pad">{children}</div>
        {footer ? <div className="flex justify-end gap-2 border-t border-white/5 px-5 py-4">{footer}</div> : null}
      </div>
    </div>
  );
}

export function RiskDots({ level }: { level: number }) {
  return (
    <span className="inline-flex items-center gap-2" title={`Риск: ${riskLabel(level)}`}>
      <span className="flex gap-1" aria-hidden="true">
        {[1, 2, 3, 4, 5].map((index) => (
          <span
            key={index}
            className={clsx(
              "h-1.5 w-3 rounded-full",
              index <= level ? (level <= 2 ? "bg-emerald-400" : level === 3 ? "bg-amber-400" : "bg-rose-400") : "bg-white/15",
            )}
          />
        ))}
      </span>
      <span className="text-xs text-slate-400">{riskLabel(level)}</span>
    </span>
  );
}

export function Tabs<T extends string>({
  tabs,
  active,
  onChange,
}: {
  tabs: Array<{ id: T; label: string; count?: number }>;
  active: T;
  onChange: (id: T) => void;
}) {
  return (
    <div className="flex flex-wrap gap-1 rounded-xl border border-white/10 bg-ink-950/50 p-1" role="tablist">
      {tabs.map((tab) => (
        <button
          key={tab.id}
          role="tab"
          aria-selected={active === tab.id}
          className={clsx(
            "rounded-lg px-3 py-1.5 text-sm font-medium transition",
            active === tab.id ? "bg-white/10 text-white" : "text-slate-400 hover:text-slate-200",
          )}
          onClick={() => onChange(tab.id)}
        >
          {tab.label}
          {tab.count !== undefined ? <span className="ml-1.5 text-xs text-slate-500">{tab.count}</span> : null}
        </button>
      ))}
    </div>
  );
}

export function Disclaimer({ ai = false }: { ai?: boolean }) {
  return (
    <p className="rounded-xl border border-white/10 bg-white/[0.02] px-4 py-3 text-xs leading-relaxed text-slate-400">
      {ai
        ? "Сгенерировано AI на основе доступных данных. Не является индивидуальной инвестиционной рекомендацией. Проверяйте первоисточники."
        : "Сервис носит информационный характер и не является индивидуальной инвестиционной рекомендацией. Расчёты оценочные, проверяйте первоисточники данных."}
    </p>
  );
}
