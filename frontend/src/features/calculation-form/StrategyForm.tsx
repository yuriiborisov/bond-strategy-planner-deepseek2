import { zodResolver } from "@hookform/resolvers/zod";
import { useForm } from "react-hook-form";
import type { StrategyDto } from "../../shared/api/types";
import { Badge, Button, Card, Checkbox, Field, Select, TextInput } from "../../shared/ui";
import { buildCalculationsPayload, buildDefaults, schemas, type FormValues } from "./formConfig";

const RATINGS = ["AAA", "AA+", "AA", "AA-", "A+", "A", "A-", "BBB+", "BBB", "BBB-", "BB+", "BB", "BB-", "B+", "B"];
const OFFER_TREATMENTS = [
  { value: "AUTO", label: "AUTO — по стратегии" },
  { value: "MATURITY", label: "MATURITY — оферта сокращает срок" },
  { value: "OPTION", label: "OPTION — оферта как событие" },
];

export function StrategyForm({
  strategy,
  submitting,
  onSubmit,
}: {
  strategy: StrategyDto;
  submitting: boolean;
  onSubmit: (params: Record<string, unknown>) => void;
}) {
  const defaults = buildDefaults(strategy.code, strategy.defaultParams);
  const form = useForm<FormValues>({
    resolver: zodResolver(schemas[strategy.code]) as any,
    defaultValues: defaults,
    mode: "onBlur",
  });
  const {
    register,
    handleSubmit,
    watch,
    formState: { errors },
  } = form;
  const errorsAny = errors as any;

  const errorOf = (path: string): string | undefined => {
    const parts = path.split(".");
    let current: any = errorsAny;
    for (const part of parts) {
      if (!current) return undefined;
      current = current[part];
    }
    return current?.message as string | undefined;
  };

  const submit = (values: FormValues) => {
    onSubmit(buildCalculationsPayload(strategy.code, values));
  };

  const bondTypes = (watch("filters.bondTypes") as string[] | undefined) ?? [];
  const requireRating = watch("filters.requireRating");

  return (
    <Card className="card-pad">
      <form onSubmit={handleSubmit(submit)} className="space-y-6" noValidate>
        <div className="grid gap-4 sm:grid-cols-2">
          {strategy.code === "BULLET" ? (
            <>
              <Field label="Целевая сумма, ₽" error={errorOf("targetAmount")} htmlFor="targetAmount">
                <TextInput id="targetAmount" type="number" step="1000" min="1000" {...register("targetAmount")} />
              </Field>
              <Field label="Дата цели" error={errorOf("targetDate")} htmlFor="targetDate">
                <TextInput id="targetDate" type="date" {...register("targetDate")} />
              </Field>
              <Field label="Стартовый капитал, ₽" error={errorOf("startCapital")} htmlFor="startCapital">
                <TextInput id="startCapital" type="number" step="1000" min="0" {...register("startCapital")} />
              </Field>
              <Field
                label="Ежемесячный взнос, ₽"
                hint="Оставьте пустым — рассчитаем необходимый взнос"
                error={errorOf("monthlyContribution")}
                htmlFor="monthlyContribution"
              >
                <TextInput id="monthlyContribution" type="number" step="100" min="0" {...register("monthlyContribution")} />
              </Field>
              <Field label="Допуск по дате погашения, дней" error={errorOf("maturityToleranceDays")}>
                <TextInput type="number" min="0" max="180" {...register("maturityToleranceDays")} />
              </Field>
              <Field label="Учёт оферты" error={errorOf("offerTreatment")}>
                <Select {...register("offerTreatment")}>
                  {OFFER_TREATMENTS.map((option) => (
                    <option key={option.value} value={option.value}>
                      {option.label}
                    </option>
                  ))}
                </Select>
              </Field>
              <Checkbox label="Разрешить бумаги с погашением позже цели" {...register("allowLaterMaturity")} />
              <Checkbox label="Реинвестировать купоны" {...register("reinvestCoupons")} />
            </>
          ) : null}

          {strategy.code === "BARBELL" ? (
            <>
              <Field label="Сумма, ₽" error={errorOf("amount")} htmlFor="amount">
                <TextInput id="amount" type="number" step="1000" min="1000" {...register("amount")} />
              </Field>
              <Field label="Доля короткого плеча" hint="0.4 = 40% капитала" error={errorOf("shortShare")}>
                <TextInput type="number" step="0.05" min="0.1" max="0.9" {...register("shortShare")} />
              </Field>
              <Field label="Максимальный срок короткого плеча, лет" error={errorOf("shortMaxYears")}>
                <TextInput type="number" min="1" max="5" {...register("shortMaxYears")} />
              </Field>
              <Field label="Минимальный срок длинного плеча, лет" error={errorOf("longMinYears")}>
                <TextInput type="number" min="5" max="30" {...register("longMinYears")} />
              </Field>
              <Field label="Бумаг в каждом плече" error={errorOf("maxBondsPerLeg")}>
                <TextInput type="number" min="1" max="15" {...register("maxBondsPerLeg")} />
              </Field>
              <Field label="Распределение внутри плеча" error={errorOf("weighting")}>
                <Select {...register("weighting")}>
                  <option value="EQUAL">Равные доли</option>
                  <option value="SCORE">Пропорционально score</option>
                </Select>
              </Field>
            </>
          ) : null}

          {strategy.code === "LADDER" ? (
            <>
              <Field label="Сумма, ₽" error={errorOf("amount")}>
                <TextInput type="number" step="1000" min="1000" {...register("amount")} />
              </Field>
              <Field label="Ступеней" hint="1–10" error={errorOf("rungs")}>
                <TextInput type="number" min="1" max="10" {...register("rungs")} />
              </Field>
              <Field label="Шаг, месяцев" error={errorOf("stepMonths")}>
                <TextInput type="number" min="1" max="60" {...register("stepMonths")} />
              </Field>
              <Field label="Дата старта" error={errorOf("startDate")}>
                <TextInput type="date" {...register("startDate")} />
              </Field>
              <Field label="Допуск по дате погашения, дней" error={errorOf("maturityToleranceDays")}>
                <TextInput type="number" min="0" max="180" {...register("maturityToleranceDays")} />
              </Field>
              <Field label="Учёт оферты" error={errorOf("offerTreatment")}>
                <Select {...register("offerTreatment")}>
                  {OFFER_TREATMENTS.map((option) => (
                    <option key={option.value} value={option.value}>
                      {option.label}
                    </option>
                  ))}
                </Select>
              </Field>
              <Checkbox label="Разрешить повторное использование бумаги" {...register("allowDuplicates")} />
              <Checkbox label="Реинвестировать купоны" {...register("reinvestCoupons")} />
              <Checkbox label="Симулировать rolling ladder" {...register("rolling")} />
              <Field label="Горизонт симуляции, лет" error={errorOf("rollingHorizonYears")}>
                <TextInput type="number" min="1" max="30" {...register("rollingHorizonYears")} />
              </Field>
            </>
          ) : null}

          {strategy.code === "WHEEL" ? (
            <>
              <Field label="Базовая стратегия" error={errorOf("baseStrategy")}>
                <Select {...register("baseStrategy")}>
                  <option value="LADDER">Лестница</option>
                  <option value="BULLET">Пуля</option>
                </Select>
              </Field>
              <Field label="Сумма, ₽" error={errorOf("amount")}>
                <TextInput type="number" step="1000" min="1000" {...register("amount")} />
              </Field>
              <Field label="Ступеней" error={errorOf("rungs")}>
                <TextInput type="number" min="1" max="10" {...register("rungs")} />
              </Field>
              <Field label="Шаг, месяцев" error={errorOf("stepMonths")}>
                <TextInput type="number" min="1" max="60" {...register("stepMonths")} />
              </Field>
              <Field label="Дата старта" error={errorOf("startDate")}>
                <TextInput type="date" {...register("startDate")} />
              </Field>
              <Field label="Горизонт, лет" error={errorOf("horizonYears")}>
                <TextInput type="number" min="1" max="30" {...register("horizonYears")} />
              </Field>
              <Field label="Правило реинвестирования" error={errorOf("reinvestRule")}>
                <Select {...register("reinvestRule")}>
                  <option value="BEST_SCORE">Лучший score</option>
                  <option value="SHORTEST">Самая короткая дюрация</option>
                  <option value="LONGEST">Самая длинная дюрация</option>
                  <option value="SAME_BOND">Та же бумага</option>
                </Select>
              </Field>
              <Field label="Ставка реинвестирования, %" hint="Пусто — подбираем бумагу" error={errorOf("reinvestRatePct")}>
                <TextInput type="number" step="0.1" min="0" max="100" {...register("reinvestRatePct")} />
              </Field>
              <Field label="Порог реинвестирования, ₽" error={errorOf("minCashToReinvestRub")}>
                <TextInput type="number" step="100" min="0" {...register("minCashToReinvestRub")} />
              </Field>
              <Field label="Налоговый профиль" error={errorOf("taxProfile")}>
                <Select {...register("taxProfile")}>
                  <option value="RU_FLAT_13">RU_FLAT_13 — 13% с купонов</option>
                  <option value="RU_PROGRESSIVE_2025">RU_PROGRESSIVE_2025 — прогрессивная шкала</option>
                </Select>
              </Field>
              <Field
                label="Совокупный годовой доход, ₽"
                hint="Нужен только для прогрессивной шкалы"
                error={errorOf("annualOtherIncome")}
              >
                <TextInput type="number" step="100000" min="0" {...register("annualOtherIncome")} />
              </Field>
            </>
          ) : null}
        </div>

        <details className="rounded-2xl border border-white/10 bg-ink-950/40 p-4">
          <summary className="cursor-pointer text-sm font-semibold text-slate-200">
            Расширенные фильтры и издержки
            <span className="ml-2 text-xs font-normal text-slate-500">
              {(bondTypes as string[]).join(", ") || "типы не выбраны"} · рейтинг от {watch("filters.minRating")}
              {requireRating ? " · только с рейтингом" : ""}
            </span>
          </summary>

          <div className="mt-4 grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
            <div className="sm:col-span-2 lg:col-span-3">
              <span className="label">Типы бумаг</span>
              <div className="flex flex-wrap gap-2">
                {[
                  { value: "OFZ", label: "ОФЗ" },
                  { value: "CORPORATE", label: "Корпоративные" },
                  { value: "MUNICIPAL", label: "Муниципальные" },
                ].map((type) => (
                  <label
                    key={type.value}
                    className="inline-flex cursor-pointer items-center gap-2 rounded-xl border border-white/10 bg-white/[0.03] px-3 py-2 text-sm"
                  >
                    <input type="checkbox" value={type.value} className="accent-teal-400" {...register("filters.bondTypes")} />
                    {type.label}
                  </label>
                ))}
              </div>
              {errorOf("filters.bondTypes") ? (
                <p className="mt-1 text-xs text-rose-300">{errorOf("filters.bondTypes")}</p>
              ) : null}
            </div>

            <Field label="Минимальный рейтинг" error={errorOf("filters.minRating")}>
              <Select {...register("filters.minRating")}>
                {RATINGS.map((rating) => (
                  <option key={rating} value={rating}>
                    {rating}
                  </option>
                ))}
              </Select>
            </Field>
            <Field label="Максимальный уровень листинга" hint="1 — лучший" error={errorOf("filters.minListLevel")}>
              <Select {...register("filters.minListLevel")}>
                <option value={1}>1</option>
                <option value={2}>2</option>
                <option value={3}>3</option>
              </Select>
            </Field>
            <Field label="Макс. возраст рейтинга, дней" error={errorOf("filters.maxRatingAgeDays")}>
              <TextInput type="number" min="30" max="730" {...register("filters.maxRatingAgeDays")} />
            </Field>
            <Field label="Мин. объём выпуска, ₽" error={errorOf("filters.minIssueSizeRub")}>
              <TextInput type="number" step="100000000" min="0" {...register("filters.minIssueSizeRub")} />
            </Field>
            <Field label="Мин. дневной оборот, ₽" error={errorOf("filters.minAvgDailyVolumeRub")}>
              <TextInput type="number" step="100000" min="0" {...register("filters.minAvgDailyVolumeRub")} />
            </Field>
            <Field label="Лимит на эмитента" hint="0.25 = 25% капитала" error={errorOf("filters.maxPerIssuerShare")}>
              <TextInput type="number" step="0.05" min="0.05" max="1" {...register("filters.maxPerIssuerShare")} />
            </Field>
            <Field label="Лимит на рейтинговую группу" hint="0.5 = 50%" error={errorOf("filters.maxPerRatingShare")}>
              <TextInput type="number" step="0.05" min="0.1" max="1" {...register("filters.maxPerRatingShare")} />
            </Field>

            <Checkbox label="Только с рейтингом" hint="Исключить NO_RATING" {...register("filters.requireRating")} />
            <Checkbox label="Разрешить флоатеры" {...register("filters.allowFloatingCoupon")} />
            <Checkbox label="Разрешить амортизируемые" {...register("filters.allowAmortizing")} />

            <Field label="Комиссия, % от сделки" error={errorOf("costs.commissionPct")}>
              <TextInput type="number" step="0.01" min="0" max="3" {...register("costs.commissionPct")} />
            </Field>
            <Field label="Налог с купонов" error={errorOf("costs.taxProfile")}>
              <Select {...register("costs.taxProfile")}>
                <option value="RU_FLAT_13">RU_FLAT_13 — 13%</option>
                <option value="RU_PROGRESSIVE_2025">RU_PROGRESSIVE_2025</option>
              </Select>
            </Field>
            <Field
              label="Совокупный годовой доход, ₽"
              hint="Для прогрессивной шкалы 2025"
              error={errorOf("costs.annualOtherIncome")}
            >
              <TextInput type="number" step="100000" min="0" {...register("costs.annualOtherIncome")} />
            </Field>
            <div className="flex items-end">
              <Checkbox label="Учитывать НДФЛ с купонов" {...register("costs.taxOnCoupons")} />
            </div>
          </div>
        </details>

        <div className="flex flex-wrap items-center justify-between gap-3">
          <div className="flex flex-wrap gap-2">
            <Badge tone="neutral">снимок MOEX ISS</Badge>
            <Badge tone="neutral">лоты и остатки</Badge>
            <Badge tone="neutral">налоги</Badge>
          </div>
          <Button type="submit" disabled={submitting} className="min-w-40">
            {submitting ? "Считаем…" : "Рассчитать"}
          </Button>
        </div>
      </form>
    </Card>
  );
}
