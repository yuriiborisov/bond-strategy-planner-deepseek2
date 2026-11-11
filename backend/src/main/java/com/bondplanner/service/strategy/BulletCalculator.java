package com.bondplanner.service.strategy;

import com.bondplanner.api.error.ApiException;
import com.bondplanner.domain.model.OfferTreatment;
import com.bondplanner.service.calc.BondMetricsService;
import com.bondplanner.service.calc.CalculationResult;
import com.bondplanner.service.calc.CashFlowPoint;
import com.bondplanner.service.calc.FilterParams;
import com.bondplanner.service.calc.Params;
import com.bondplanner.service.calc.PositionResult;
import com.bondplanner.service.calc.RankingService;
import com.bondplanner.service.calc.WarningItem;
import com.bondplanner.service.cashflow.DatedCashFlow;
import com.bondplanner.service.checks.AssetCheckView;
import com.bondplanner.service.rating.RatingView;
import com.bondplanner.service.tax.TaxCalculator;
import com.bondplanner.service.universe.BondCandidate;
import com.bondplanner.util.Decimals;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * BULLET — «Пуля»: капитал в бумаги с погашением в одну целевую дату (§3.3).
 */
@Component
@RequiredArgsConstructor
public class BulletCalculator implements StrategyCalculator {

    private static final int MAX_POSITIONS = 10;

    private final StrategySupport support;

    @Override
    public String code() {
        return "BULLET";
    }

    @Override
    public CalculationResult calculate(CalculationContext context, Params params) {
        LocalDate today = support.today();
        FilterParams filters = FilterParams.from(params);
        BigDecimal targetAmount = params.decimal("targetAmount", null);
        if (targetAmount == null || targetAmount.signum() <= 0) {
            throw ApiException.badRequest("VALIDATION_ERROR", "targetAmount должен быть больше 0");
        }
        LocalDate targetDate = support.parseDate(params, "targetDate", null);
        if (targetDate == null) {
            throw ApiException.badRequest("VALIDATION_ERROR", "targetDate обязателен");
        }
        if (!targetDate.isAfter(today)) {
            throw ApiException.badRequest("VALIDATION_ERROR", "targetDate должна быть в будущем");
        }
        BigDecimal startCapital = Decimals.nz(params.decimal("startCapital", BigDecimal.ZERO));
        BigDecimal monthlyContribution = params.decimal("monthlyContribution", null);
        int tolerance = params.integer("maturityToleranceDays", 30);
        boolean allowLater = params.bool("allowLaterMaturity", false);
        OfferTreatment treatment = OfferTreatment.parse(params.text("offerTreatment", "MATURITY"), OfferTreatment.MATURITY);
        boolean reinvestCoupons = params.bool("reinvestCoupons", true);
        BigDecimal fallbackRate = params.child("reinvest").decimal("fallbackRatePct", BigDecimal.valueOf(12.0));

        LocalDate windowStart = targetDate.minusDays(tolerance);
        LocalDate windowEnd = allowLater ? targetDate.plusDays(tolerance) : targetDate;

        List<BondCandidate> window = context.snapshot().bonds().stream()
                .filter(c -> support.rating(context, c) != null || !filters.requireRating())
                .filter(c -> context.services().filter().matchesStructural(c, filters, support.rating(context, c)))
                .filter(c -> {
                    LocalDate mat = c.effectiveMaturity(treatment, OfferTreatment.MATURITY);
                    return mat != null && mat.isAfter(today) && !mat.isBefore(windowStart) && !mat.isAfter(windowEnd);
                })
                .toList();

        if (window.isEmpty()) {
            throw ApiException.noCandidates(
                    "В окне " + windowStart + " — " + windowEnd + " нет бумаг, проходящих фильтры",
                    List.of("Увеличить maturityToleranceDays", "Разрешить CORPORATE/OFZ",
                            "Снизить minRating или отключить requireRating", "Разрешить allowLaterMaturity"));
        }

        List<RankingService.ScoredBond> ranked = context.services().ranking()
                .rank(window, context.snapshot().ratings(), filters, treatment, OfferTreatment.MATURITY,
                        today, targetDate, tolerance);
        if (ranked.isEmpty()) {
            throw ApiException.noCandidates("Нет бумаг с корректной ценой и купонным календарём",
                    List.of("Запустить синхронизацию котировок", "Ослабить фильтры ликвидности"));
        }
        BigDecimal medianYtm = support.median(ranked.stream().map(r -> r.metrics().ytmPct()).toList());
        BigDecimal baseRate = weightedRate(ranked);

        List<PositionResult> positions = new ArrayList<>();
        List<List<DatedCashFlow>> flowsPerPosition = new ArrayList<>();
        List<WarningItem> warnings = new ArrayList<>();
        int rejected = 0;
        BigDecimal budget = startCapital;
        Map<Long, BigDecimal> issuerSpend = new LinkedHashMap<>();

        if (budget.signum() > 0) {
            for (RankingService.ScoredBond scored : ranked) {
                if (positions.size() >= MAX_POSITIONS) {
                    break;
                }
                BondCandidate candidate = scored.candidate();
                BondMetricsService.BondMetrics metrics = scored.metrics();
                RatingView rating = support.rating(context, candidate);
                AssetCheckView checks = support.check(context, candidate, metrics, rating, filters, "BULLET",
                        targetDate, tolerance, null, windowEnd, medianYtm);
                if (checks != null && checks.rejected()) {
                    rejected++;
                    continue;
                }
                BigDecimal lotPrice = metrics.lotPrice(filters.commissionPct(), candidate.lotSize());
                if (lotPrice.signum() <= 0 || budget.compareTo(lotPrice) < 0) {
                    continue;
                }
                Long issuerId = candidate.bond().getIssuer() == null ? candidate.bond().getId()
                        : candidate.bond().getIssuer().getId();
                BigDecimal issuerLimit = startCapital.multiply(filters.maxPerIssuerShare());
                BigDecimal already = issuerSpend.getOrDefault(issuerId, BigDecimal.ZERO);
                BigDecimal available = issuerLimit.subtract(already).min(budget);
                if (available.compareTo(lotPrice) < 0) {
                    continue;
                }
                int lots = available.divide(lotPrice, 0, RoundingMode.DOWN).intValue();
                if (lots < 1) {
                    continue;
                }
                int quantity = lots * candidate.lotSize();
                BigDecimal cost = lotPrice.multiply(BigDecimal.valueOf(lots));
                String reason = reason(candidate, metrics, targetDate, medianYtm, treatment);
                PositionResult position = support.position(candidate, metrics, rating, checks, quantity, cost,
                        support.share(cost, startCapital), reason);
                position.setLots(BigDecimal.valueOf(lots));
                positions.add(position);
                flowsPerPosition.add(metrics.bondFlows());
                issuerSpend.merge(issuerId, cost, BigDecimal::add);
                budget = budget.subtract(cost);
            }
        }

        if (positions.isEmpty() && rejected > 0) {
            throw new ApiException(org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY, "ASSET_CHECK_FAILED",
                    "Все кандидаты не прошли жёсткие гейты asset checks", List.of("Изменить фильтры рейтинга", "Загрузить рейтинги"));
        }
        if (startCapital.signum() > 0 && positions.isEmpty()) {
            warnings.add(WarningItem.warn("NO_LOTS",
                    "Стартового капитала не хватает на целый лот ни одной бумаги"));
        }

        int months = (int) Math.max(1, ChronoUnit.MONTHS.between(today, targetDate));
        BigDecimal couponIncome = support.couponIncome(positions, flowsPerPosition, today, targetDate);
        BigDecimal totalTax = tax(filters, couponIncome);
        List<CashFlowPoint> schedule = new ArrayList<>();
        for (int i = 0; i < positions.size(); i++) {
            schedule.addAll(support.points(flowsPerPosition.get(i), positions.get(i).getQuantity()));
        }
        schedule.sort(java.util.Comparator.comparing(CashFlowPoint::date));
        if (filters.taxOnCoupons() && couponIncome.signum() > 0) {
            schedule = support.withTaxes(schedule, filters);
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("targetAmountRub", Decimals.money(targetAmount));
        summary.put("startCapitalRub", Decimals.money(startCapital));
        summary.put("totalAmountRub", Decimals.money(startCapital));
        summary.put("investedRub", Decimals.money(support.marketValue(positions)));
        summary.put("cashResidualRub", Decimals.money(budget));
        summary.put("horizonMonths", months);
        summary.put("weightedYtmPct", support.weightedYtm(positions));
        summary.put("weightedDurationYears", support.weightedDuration(positions));
        summary.put("totalCouponIncomeRub", couponIncome);
        summary.put("totalTaxRub", totalTax);

        List<Map<String, Object>> scenarios = new ArrayList<>();
        BigDecimal basePayment = monthlyContribution != null
                ? monthlyContribution
                : context.services().cashFlow().pmt(targetAmount, startCapital, baseRate, months);
        BigDecimal baseProjected = futureValue(startCapital, basePayment, baseRate, months);
        for (String name : List.of("pessimistic", "base", "optimistic")) {
            BigDecimal rate = switch (name) {
                case "pessimistic" -> baseRate.subtract(BigDecimal.valueOf(2));
                case "optimistic" -> baseRate.add(BigDecimal.valueOf(2));
                default -> baseRate;
            };
            rate = rate.max(BigDecimal.ZERO);
            BigDecimal requiredForRate = monthlyContribution != null
                    ? monthlyContribution
                    : context.services().cashFlow().pmt(targetAmount, startCapital, rate, months);
            BigDecimal projected = futureValue(startCapital, basePayment, rate, months);
            Map<String, Object> scenario = new LinkedHashMap<>();
            scenario.put("scenario", name);
            scenario.put("ytmPct", Decimals.percent(rate));
            scenario.put("monthlyContributionRub", Decimals.money(requiredForRate));
            scenario.put("projectedValueRub", Decimals.money(projected));
            scenario.put("surplusOrGapRub", Decimals.money(projected.subtract(targetAmount)));
            scenarios.add(scenario);
        }
        BigDecimal requiredMonthly = basePayment;
        summary.put("requiredMonthlyContributionRub", Decimals.money(requiredMonthly));
        summary.put("projectedValueRub", Decimals.money(baseProjected));
        summary.put("surplusOrGapRub", Decimals.money(baseProjected.subtract(targetAmount)));
        summary.put("achievement", baseProjected.compareTo(targetAmount) >= 0);

        warnings.addAll(support.concentrationWarnings(positions, startCapital, filters));
        warnings.addAll(support.residualWarning(budget, startCapital));
        warnings.addAll(support.dataFreshnessWarning(context.snapshot().dataFreshness()));
        if (baseProjected.compareTo(targetAmount) < 0) {
            warnings.add(WarningItem.warn("TARGET_NOT_REACHED",
                    "При базовом сценарии цель не достигается: дефицит "
                            + Decimals.money(targetAmount.subtract(baseProjected)).toPlainString() + " ₽"));
        }
        if (positions.stream().anyMatch(p -> p.getBond().offerDate() != null
                && p.getBond().offerDate().isBefore(targetDate))) {
            warnings.add(WarningItem.warn("OFFER_BEFORE_TARGET",
                    "У части бумаг оферта наступает раньше целевой даты — доходность считается к оферте"));
        }
        addCheckWarnings(positions, warnings);

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("targetDate", targetDate);
        details.put("monthlyContributionRub", Decimals.money(requiredMonthly));
        details.put("contributionScenarios", scenarios);
        details.put("candidates", candidates(context, ranked, filters, targetDate, tolerance, windowEnd, medianYtm));
        details.put("reinvestCoupons", reinvestCoupons);
        details.put("offerTreatment", treatment.name());

        return CalculationResult.builder()
                .strategy(code())
                .exchange(context.snapshot().exchangeCode())
                .snapshotTs(context.snapshot().snapshotTs())
                .dataFreshness(context.snapshot().dataFreshness())
                .summary(summary)
                .positions(positions)
                .cashFlowSchedule(schedule)
                .warnings(warnings)
                .assumptions(support.assumptions(filters, baseRate))
                .details(details)
                .build();
    }

    private List<Map<String, Object>> candidates(CalculationContext context, List<RankingService.ScoredBond> ranked,
                                                 FilterParams filters, LocalDate targetDate, int tolerance,
                                                 LocalDate windowEnd, BigDecimal medianYtm) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (RankingService.ScoredBond scored : ranked.size() > 10 ? ranked.subList(0, 10) : ranked) {
            BondCandidate candidate = scored.candidate();
            RatingView rating = support.rating(context, candidate);
            AssetCheckView checks = support.check(context, candidate, scored.metrics(), rating, filters, "BULLET",
                    targetDate, tolerance, null, windowEnd, medianYtm);
            if (checks != null && checks.rejected()) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("bond", support.bondInfo(candidate, scored.metrics(), rating));
            item.put("score", Math.round(scored.score() * 1000) / 1000.0);
            item.put("reason", reason(candidate, scored.metrics(), targetDate, medianYtm, OfferTreatment.MATURITY));
            item.put("checks", checks);
            list.add(item);
        }
        return list;
    }

    private void addCheckWarnings(List<PositionResult> positions, List<WarningItem> warnings) {
        for (PositionResult position : positions) {
            if (position.getChecks() != null && "WATCH".equals(position.getChecks().verdict())) {
                warnings.add(WarningItem.warn("ASSET_CHECK_WATCH",
                        "Бумага " + position.getBond().secid() + " прошла гейты с замечаниями (WATCH): "
                                + position.getChecks().explanation()));
            }
        }
    }

    private BigDecimal weightedRate(List<RankingService.ScoredBond> ranked) {
        List<RankingService.ScoredBond> top = ranked.size() > 5 ? ranked.subList(0, 5) : ranked;
        BigDecimal sum = BigDecimal.ZERO;
        for (RankingService.ScoredBond scored : top) {
            sum = sum.add(scored.metrics().ytmPct());
        }
        return sum.divide(BigDecimal.valueOf(top.size()), 4, RoundingMode.HALF_UP);
    }

    private BigDecimal futureValue(BigDecimal start, BigDecimal payment, BigDecimal annualRatePct, int months) {
        double r = annualRatePct.doubleValue() / 100 / 12;
        double pv = start.doubleValue();
        double pmt = payment.doubleValue();
        double fv;
        if (Math.abs(r) < 1e-12) {
            fv = pv + pmt * months;
        } else {
            double growth = Math.pow(1 + r, months);
            fv = pv * growth + pmt * (growth - 1) / r;
        }
        return Decimals.money(BigDecimal.valueOf(fv));
    }

    private BigDecimal tax(FilterParams filters, BigDecimal couponIncome) {
        if (!filters.taxOnCoupons() || couponIncome == null || couponIncome.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        TaxCalculator calculator = new TaxCalculator();
        return calculator.taxOnCoupons(couponIncome, filters.taxProfile(), filters.annualOtherIncome());
    }

    private String reason(BondCandidate candidate, BondMetricsService.BondMetrics metrics, LocalDate targetDate,
                          BigDecimal medianYtm, OfferTreatment treatment) {
        LocalDate maturity = metrics.effectiveMaturity();
        long days = ChronoUnit.DAYS.between(maturity, targetDate);
        StringBuilder sb = new StringBuilder();
        if (days == 0) {
            sb.append("Погашение в целевую дату");
        } else if (days > 0) {
            sb.append("Погашение за ").append(days).append(" дн. до целевой даты");
        } else {
            sb.append("Погашение на ").append(-days).append(" дн. позже целевой даты (в допуске)");
        }
        if (metrics.ytmPct() != null && medianYtm != null) {
            BigDecimal diff = metrics.ytmPct().subtract(medianYtm);
            sb.append("; YTM ").append(metrics.ytmPct().setScale(2, RoundingMode.HALF_UP)).append("%");
            if (diff.signum() >= 0) {
                sb.append(" — выше медианы на ").append(diff.setScale(2, RoundingMode.HALF_UP)).append(" п.п.");
            } else {
                sb.append(" — ниже медианы на ").append(diff.abs().setScale(2, RoundingMode.HALF_UP)).append(" п.п.");
            }
        }
        if (treatment == OfferTreatment.MATURITY && candidate.bond().getOfferDate() != null
                && candidate.bond().getOfferDate().equals(maturity)) {
            sb.append("; оферта ").append(maturity).append(" учтена как дата выхода");
        }
        if (candidate.bond().getListLevel() != null) {
            sb.append("; листинг ").append(candidate.bond().getListLevel());
        }
        return sb.toString();
    }
}
