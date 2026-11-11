import { z } from "zod";
import type { StrategyCode } from "../../shared/api/types";
import { addYearsIso, todayIso } from "../../shared/lib/format";

const filtersShape = {
  bondTypes: z.array(z.string()).min(1, "Выберите хотя бы один тип бумаг"),
  minListLevel: z.coerce.number().int().min(1).max(3),
  minIssueSizeRub: z.coerce.number().min(0),
  minAvgDailyVolumeRub: z.coerce.number().min(0),
  maxPerIssuerShare: z.coerce.number().min(0.05).max(1),
  maxPerRatingShare: z.coerce.number().min(0.1).max(1),
  minRating: z.string().min(1),
  maxRatingAgeDays: z.coerce.number().int().min(30).max(730),
  requireRating: z.boolean(),
  allowAmortizing: z.boolean(),
  allowFloatingCoupon: z.boolean(),
};

const costsShape = {
  commissionPct: z.coerce.number().min(0, "Не меньше 0").max(3, "Не больше 3%"),
  taxOnCoupons: z.boolean(),
  taxProfile: z.string(),
  annualOtherIncome: z.coerce.number().min(0).optional(),
};

const filters = z.object(filtersShape);
const costs = z.object(costsShape);

const dateString = z.string().min(1, "Укажите дату");

export const bulletSchema = z.object({
  targetAmount: z.coerce.number().min(1000, "Минимум 1 000 ₽"),
  targetDate: dateString,
  startCapital: z.coerce.number().min(0),
  monthlyContribution: z.coerce.number().min(0).optional(),
  maturityToleranceDays: z.coerce.number().int().min(0).max(180),
  allowLaterMaturity: z.boolean(),
  offerTreatment: z.string(),
  reinvestCoupons: z.boolean(),
  filters,
  costs,
});

export const barbellSchema = z.object({
  amount: z.coerce.number().min(1000, "Минимум 1 000 ₽"),
  shortShare: z.coerce.number().min(0.1).max(0.9),
  shortMaxYears: z.coerce.number().int().min(1).max(5),
  longMinYears: z.coerce.number().int().min(5).max(30),
  maxBondsPerLeg: z.coerce.number().int().min(1).max(15),
  weighting: z.string(),
  filters,
  costs,
});

export const ladderSchema = z.object({
  amount: z.coerce.number().min(1000, "Минимум 1 000 ₽"),
  rungs: z.coerce.number().int().min(1).max(10),
  stepMonths: z.coerce.number().int().min(1).max(60),
  startDate: dateString,
  maturityToleranceDays: z.coerce.number().int().min(0).max(180),
  rolling: z.boolean(),
  rollingHorizonYears: z.coerce.number().int().min(1).max(30),
  allowDuplicates: z.boolean(),
  offerTreatment: z.string(),
  reinvestCoupons: z.boolean(),
  filters,
  costs,
});

export const wheelSchema = z.object({
  baseStrategy: z.enum(["LADDER", "BULLET"]),
  amount: z.coerce.number().min(1000),
  rungs: z.coerce.number().int().min(1).max(10),
  stepMonths: z.coerce.number().int().min(1).max(60),
  startDate: dateString,
  horizonYears: z.coerce.number().int().min(1).max(30),
  reinvestRule: z.string(),
  reinvestRatePct: z.coerce.number().min(0).max(100).optional(),
  minCashToReinvestRub: z.coerce.number().min(0),
  taxProfile: z.string(),
  annualOtherIncome: z.coerce.number().min(0).optional(),
  filters,
  costs,
});

export const schemas: Record<StrategyCode, z.ZodTypeAny> = {
  BULLET: bulletSchema,
  BARBELL: barbellSchema,
  LADDER: ladderSchema,
  WHEEL: wheelSchema,
};

export type FormValues = Record<string, any>;

const defaultFilters = {
  bondTypes: ["OFZ", "CORPORATE"],
  minListLevel: 2,
  minIssueSizeRub: 1_000_000_000,
  minAvgDailyVolumeRub: 1_000_000,
  maxPerIssuerShare: 0.25,
  maxPerRatingShare: 0.5,
  minRating: "A-",
  maxRatingAgeDays: 180,
  requireRating: true,
  allowAmortizing: true,
  allowFloatingCoupon: false,
};

const defaultCosts = {
  commissionPct: 0.05,
  taxOnCoupons: true,
  taxProfile: "RU_FLAT_13",
  annualOtherIncome: 0,
};

export function buildDefaults(code: StrategyCode, defaultParams: Record<string, any>): FormValues {
  const filters = { ...defaultFilters, ...(defaultParams.filters ?? {}) };
  const costs = { ...defaultCosts, ...(defaultParams.costs ?? {}) };
  const base = {
    filters,
    costs,
  };
  switch (code) {
    case "BULLET":
      return {
        ...base,
        targetAmount: defaultParams.targetAmount ?? 3_000_000,
        targetDate: addYearsIso(5),
        startCapital: defaultParams.startCapital ?? 0,
        monthlyContribution: defaultParams.monthlyContribution,
        maturityToleranceDays: Math.max(Number(defaultParams.maturityToleranceDays ?? 30), 180),
        allowLaterMaturity: false,
        offerTreatment: defaultParams.offerTreatment ?? "MATURITY",
        reinvestCoupons: defaultParams.reinvestCoupons ?? true,
      };
    case "BARBELL":
      return {
        ...base,
        amount: defaultParams.amount ?? 1_000_000,
        shortShare: defaultParams.shortShare ?? 0.4,
        shortMaxYears: defaultParams.shortMaxYears ?? 2,
        longMinYears: defaultParams.longMinYears ?? 10,
        maxBondsPerLeg: defaultParams.maxBondsPerLeg ?? 5,
        weighting: "EQUAL",
      };
    case "LADDER":
      return {
        ...base,
        amount: defaultParams.amount ?? 1_000_000,
        rungs: defaultParams.rungs ?? 5,
        stepMonths: defaultParams.stepMonths ?? 12,
        startDate: todayIso(20),
        maturityToleranceDays: defaultParams.maturityToleranceDays ?? 45,
        rolling: false,
        rollingHorizonYears: defaultParams.rollingHorizonYears ?? 10,
        allowDuplicates: false,
        offerTreatment: defaultParams.offerTreatment ?? "OPTION",
        reinvestCoupons: true,
      };
    case "WHEEL":
      return {
        ...base,
        baseStrategy: defaultParams.baseStrategy ?? "LADDER",
        amount: defaultParams.baseParams?.amount ?? 1_000_000,
        rungs: defaultParams.baseParams?.rungs ?? 5,
        stepMonths: defaultParams.baseParams?.stepMonths ?? 12,
        startDate: todayIso(20),
        horizonYears: defaultParams.horizonYears ?? 5,
        reinvestRule: defaultParams.reinvestRule ?? "BEST_SCORE",
        reinvestRatePct: undefined,
        minCashToReinvestRub: defaultParams.minCashToReinvestRub ?? 1000,
        taxProfile: defaultParams.taxProfile ?? "RU_FLAT_13",
        annualOtherIncome: defaultParams.annualOtherIncome ?? 0,
      };
  }
}

export function buildCalculationsPayload(code: StrategyCode, values: FormValues): Record<string, unknown> {
  const filters = { ...values.filters };
  const costs = { ...values.costs };
  switch (code) {
    case "BULLET":
      return {
        targetAmount: values.targetAmount,
        targetDate: values.targetDate,
        startCapital: values.startCapital,
        monthlyContribution: values.monthlyContribution || undefined,
        maturityToleranceDays: values.maturityToleranceDays,
        allowLaterMaturity: values.allowLaterMaturity,
        offerTreatment: values.offerTreatment,
        reinvestCoupons: values.reinvestCoupons,
        filters,
        costs,
      };
    case "BARBELL":
      return {
        amount: values.amount,
        shortShare: values.shortShare,
        shortMaxYears: values.shortMaxYears,
        longMinYears: values.longMinYears,
        maxBondsPerLeg: values.maxBondsPerLeg,
        weighting: values.weighting,
        filters,
        costs,
      };
    case "LADDER":
      return {
        amount: values.amount,
        rungs: values.rungs,
        stepMonths: values.stepMonths,
        startDate: values.startDate,
        maturityToleranceDays: values.maturityToleranceDays,
        rolling: values.rolling,
        rollingHorizonYears: values.rollingHorizonYears,
        allowDuplicates: values.allowDuplicates,
        offerTreatment: values.offerTreatment,
        reinvestCoupons: values.reinvestCoupons,
        filters,
        costs,
      };
    case "WHEEL":
      return {
        baseStrategy: values.baseStrategy,
        baseParams: {
          amount: values.amount,
          rungs: values.rungs,
          stepMonths: values.stepMonths,
          startDate: values.startDate,
          maturityToleranceDays: 45,
          offerTreatment: "OPTION",
        },
        horizonYears: values.horizonYears,
        reinvestRule: values.reinvestRule,
        reinvestRatePct: values.reinvestRatePct || undefined,
        minCashToReinvestRub: values.minCashToReinvestRub,
        taxProfile: values.taxProfile,
        annualOtherIncome: values.annualOtherIncome,
        filters,
        costs: { ...costs, taxProfile: values.taxProfile, annualOtherIncome: values.annualOtherIncome },
      };
  }
}
