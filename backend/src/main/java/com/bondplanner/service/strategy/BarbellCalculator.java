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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * BARBELL — «Штанга»: короткое плечо (0…shortMaxYears) + длинное (longMinYears+), §3.4.
 */
@Component
@RequiredArgsConstructor
public class BarbellCalculator implements StrategyCalculator {

    private final StrategySupport support;

    @Override
    public String code() {
        return "BARBELL";
    }

    @Override
    public CalculationResult calculate(CalculationContext context, Params params) {
        LocalDate today = support.today();
        FilterParams filters = FilterParams.from(params);
        BigDecimal amount = params.decimal("amount", null);
        if (amount == null || amount.signum() <= 0) {
            throw ApiException.badRequest("VALIDATION_ERROR", "amount должен быть больше 0");
        }
        BigDecimal shortShare = params.decimal("shortShare", new BigDecimal("0.4"));
        int shortMaxYears = params.integer("shortMaxYears", 2);
        int longMinYears = params.integer("longMinYears", 10);
        int maxPerLeg = params.integer("maxBondsPerLeg", 5);
        String weighting = params.text("weighting", "EQUAL").toUpperCase();
        if (shortMaxYears >= longMinYears) {
            throw ApiException.inconsistent("Короткое плечо должно заканчиваться раньше длинного: shortMaxYears < longMinYears");
        }
        if (shortShare.signum() <= 0 || shortShare.compareTo(BigDecimal.ONE) >= 0) {
            throw ApiException.badRequest("VALIDATION_ERROR", "shortShare должен быть в диапазоне (0, 1)");
        }

        LocalDate shortEnd = today.plusYears(shortMaxYears);
        LocalDate longStart = today.plusYears(longMinYears);
        BigDecimal shortBudget = amount.multiply(shortShare);
        BigDecimal longBudget = amount.subtract(shortBudget);

        List<BondCandidate> eligible = context.snapshot().bonds().stream()
                .filter(c -> context.services().filter().matchesStructural(c, filters, support.rating(context, c)))
                .filter(c -> context.services().filter().notMatured(c, OfferTreatment.AUTO, OfferTreatment.OPTION, today))
                .toList();

        List<BondCandidate> shortCandidates = eligible.stream()
                .filter(c -> {
                    LocalDate mat = c.effectiveMaturity(OfferTreatment.OPTION, OfferTreatment.OPTION);
                    return mat != null && !mat.isAfter(shortEnd);
                })
                .toList();
        List<BondCandidate> longCandidates = eligible.stream()
                .filter(c -> {
                    LocalDate mat = c.effectiveMaturity(OfferTreatment.OPTION, OfferTreatment.OPTION);
                    return mat != null && !mat.isBefore(longStart);
                })
                .toList();

        if (shortCandidates.isEmpty() || longCandidates.isEmpty()) {
            throw ApiException.noCandidates(
                    "Не хватает бумаг: коротких " + shortCandidates.size() + ", длинных " + longCandidates.size(),
                    List.of("Увеличить shortMaxYears или уменьшить longMinYears",
                            "Ослабить фильтры ликвидности/рейтинга"));
        }

        List<RankingService.ScoredBond> shortRanked = context.services().ranking()
                .rank(shortCandidates, context.snapshot().ratings(), filters, OfferTreatment.OPTION,
                        OfferTreatment.OPTION, today, shortEnd, 365);
        List<RankingService.ScoredBond> longRanked = context.services().ranking()
                .rank(longCandidates, context.snapshot().ratings(), filters, OfferTreatment.OPTION,
                        OfferTreatment.OPTION, today, longStart, 365);
        if (shortRanked.isEmpty() || longRanked.isEmpty()) {
            throw ApiException.noCandidates("Нет бумаг с корректной ценой в одном из плеч",
                    List.of("Запустить синхронизацию котировок", "Ослабить фильтры"));
        }

        List<PositionResult> positions = new ArrayList<>();
        List<List<DatedCashFlow>> flowsPerPosition = new ArrayList<>();
        List<WarningItem> warnings = new ArrayList<>();
        BigDecimal medianShort = support.median(shortRanked.stream().map(r -> r.metrics().ytmPct()).toList());
        BigDecimal medianLong = support.median(longRanked.stream().map(r -> r.metrics().ytmPct()).toList());

        BigDecimal shortSpent = allocateLeg(context, shortRanked.subList(0, Math.min(maxPerLeg, shortRanked.size())),
                shortBudget, "SHORT", filters, shortEnd, 365, medianShort, weighting, today,
                positions, flowsPerPosition, warnings);
        BigDecimal longSpent = allocateLeg(context, longRanked.subList(0, Math.min(maxPerLeg, longRanked.size())),
                longBudget, "LONG", filters, longStart, 365, medianLong, weighting, today,
                positions, flowsPerPosition, warnings);

        BigDecimal invested = shortSpent.add(longSpent);
        BigDecimal residual = amount.subtract(invested);
        LocalDate horizon = positions.stream()
                .map(p -> p.getBond().effectiveMaturity())
                .filter(java.util.Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(today.plusYears(longMinYears));
        BigDecimal couponIncome = support.couponIncome(positions, flowsPerPosition, today, horizon);
        BigDecimal tax = tax(filters, couponIncome);

        List<CashFlowPoint> schedule = new ArrayList<>();
        for (int i = 0; i < positions.size(); i++) {
            schedule.addAll(support.points(flowsPerPosition.get(i), positions.get(i).getQuantity()));
        }
        schedule.sort(Comparator.comparing(CashFlowPoint::date));
        if (filters.taxOnCoupons()) {
            schedule = support.withTaxes(schedule, filters);
        }

        List<Map<String, Object>> stress = stressTest(positions);
        BigDecimal weightedDuration = support.weightedDuration(positions);

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalAmountRub", Decimals.money(amount));
        summary.put("investedRub", Decimals.money(invested));
        summary.put("cashResidualRub", Decimals.money(residual));
        summary.put("shortPartRub", Decimals.money(shortSpent));
        summary.put("longPartRub", Decimals.money(longSpent));
        summary.put("weightedYtmPct", support.weightedYtm(positions));
        summary.put("weightedDurationYears", weightedDuration);
        summary.put("totalCouponIncomeRub", couponIncome);
        summary.put("totalTaxRub", tax);
        summary.put("horizonDate", horizon);

        warnings.addAll(support.concentrationWarnings(positions, amount, filters));
        warnings.addAll(support.residualWarning(residual, amount));
        warnings.addAll(support.dataFreshnessWarning(context.snapshot().dataFreshness()));
        if (weightedDuration != null && weightedDuration.compareTo(BigDecimal.valueOf(4)) > 0) {
            warnings.add(WarningItem.warn("LONG_LEG_DURATION_RISK",
                    "Длинное плечо даёт дюрацию " + weightedDuration + " лет: при росте ставки просадка будет выше, чем у короткого плеча"));
        }
        addCheckWarnings(positions, warnings);

        List<Map<String, Object>> legs = new ArrayList<>();
        legs.add(legInfo("SHORT", shortShare, shortBudget, shortSpent, positions));
        legs.add(legInfo("LONG", BigDecimal.ONE.subtract(shortShare), longBudget, longSpent, positions));

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("legs", legs);
        details.put("stressTest", stress);
        details.put("shortMaxYears", shortMaxYears);
        details.put("longMinYears", longMinYears);
        details.put("weighting", weighting);
        details.put("offerTreatment", "OPTION");

        return CalculationResult.builder()
                .strategy(code())
                .exchange(context.snapshot().exchangeCode())
                .snapshotTs(context.snapshot().snapshotTs())
                .dataFreshness(context.snapshot().dataFreshness())
                .summary(summary)
                .positions(positions)
                .cashFlowSchedule(schedule)
                .warnings(warnings)
                .assumptions(support.assumptions(filters, null))
                .details(details)
                .build();
    }

    private BigDecimal allocateLeg(CalculationContext context, List<RankingService.ScoredBond> ranked,
                                   BigDecimal budget, String leg, FilterParams filters, LocalDate target,
                                   int tolerance, BigDecimal medianYtm, String weighting, LocalDate today,
                                   List<PositionResult> positions, List<List<DatedCashFlow>> flows,
                                   List<WarningItem> warnings) {
        if (ranked.isEmpty()) {
            warnings.add(WarningItem.warn("EMPTY_LEG", "Плечо " + leg + " осталось пустым"));
            return BigDecimal.ZERO;
        }
        double scoreSum = ranked.stream().mapToDouble(RankingService.ScoredBond::score).sum();
        BigDecimal spent = BigDecimal.ZERO;
        for (RankingService.ScoredBond scored : ranked) {
            BondCandidate candidate = scored.candidate();
            BondMetricsService.BondMetrics metrics = scored.metrics();
            RatingView rating = support.rating(context, candidate);
            AssetCheckView checks = support.check(context, candidate, metrics, rating, filters, "BARBELL",
                    target, tolerance, null, null, medianYtm);
            if (checks != null && checks.rejected()) {
                continue;
            }
            BigDecimal share = "SCORE".equals(weighting) && scoreSum > 0
                    ? BigDecimal.valueOf(scored.score() / scoreSum)
                    : BigDecimal.ONE.divide(BigDecimal.valueOf(ranked.size()), 6, RoundingMode.HALF_UP);
            BigDecimal legBudget = budget.multiply(share);
            BigDecimal lotPrice = metrics.lotPrice(filters.commissionPct(), candidate.lotSize());
            if (lotPrice.signum() <= 0 || legBudget.compareTo(lotPrice) < 0) {
                continue;
            }
            int lots = legBudget.divide(lotPrice, 0, RoundingMode.DOWN).intValue();
            if (lots < 1) {
                continue;
            }
            int quantity = lots * candidate.lotSize();
            BigDecimal cost = lotPrice.multiply(BigDecimal.valueOf(lots));
            if (spent.add(cost).compareTo(budget) > 0) {
                continue;
            }
            String reason = leg + ": " + (leg.equals("SHORT")
                    ? "короткая бумага с низкой дюрацией"
                    : "длинная бумага с максимальной доходностью в плече")
                    + "; YTM " + (metrics.ytmPct() == null ? "—" : metrics.ytmPct().setScale(2, RoundingMode.HALF_UP)) + "%"
                    + "; погашение " + metrics.effectiveMaturity();
            PositionResult position = support.position(candidate, metrics, rating, checks, quantity, cost,
                    support.share(cost, budget), reason);
            position.setLeg(leg);
            position.setLots(BigDecimal.valueOf(lots));
            positions.add(position);
            flows.add(metrics.bondFlows());
            spent = spent.add(cost);
        }
        return spent;
    }

    private Map<String, Object> legInfo(String leg, BigDecimal targetShare, BigDecimal budget,
                                        BigDecimal invested, List<PositionResult> positions) {
        List<PositionResult> legPositions = positions.stream().filter(p -> leg.equals(p.getLeg())).toList();
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("leg", leg);
        info.put("targetShare", targetShare);
        info.put("amountRub", Decimals.money(budget));
        info.put("investedRub", Decimals.money(invested));
        info.put("weightedYtmPct", support.weightedYtm(legPositions));
        info.put("weightedDurationYears", support.weightedDuration(legPositions));
        info.put("bondsCount", legPositions.size());
        return info;
    }

    private List<Map<String, Object>> stressTest(List<PositionResult> positions) {
        List<Map<String, Object>> rows = new ArrayList<>();
        BigDecimal portfolioValue = support.marketValue(positions);
        for (int deltaBp : new int[]{-300, -200, -100, 100, 200, 300}) {
            double dy = deltaBp / 10000.0;
            BigDecimal longChange = BigDecimal.ZERO;
            BigDecimal portfolioChange = BigDecimal.ZERO;
            for (PositionResult position : positions) {
                if (position.getDurationYears() == null || position.getAmountRub() == null) {
                    continue;
                }
                double d = position.getDurationYears().doubleValue();
                double p = position.getAmountRub().doubleValue();
                double change = -d * dy * p + 0.5 * d * d * dy * dy * p;
                BigDecimal value = BigDecimal.valueOf(change);
                portfolioChange = portfolioChange.add(value);
                if ("LONG".equals(position.getLeg())) {
                    longChange = longChange.add(value);
                }
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("deltaBp", deltaBp);
            row.put("longLegChangeRub", Decimals.money(longChange));
            row.put("portfolioChangeRub", Decimals.money(portfolioChange));
            row.put("portfolioChangePct", portfolioValue.signum() == 0 ? null
                    : portfolioChange.divide(portfolioValue, 6, RoundingMode.HALF_UP)
                            .multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP));
            rows.add(row);
        }
        return rows;
    }

    private void addCheckWarnings(List<PositionResult> positions, List<WarningItem> warnings) {
        for (PositionResult position : positions) {
            if (position.getChecks() != null && "WATCH".equals(position.getChecks().verdict())) {
                warnings.add(WarningItem.warn("ASSET_CHECK_WATCH",
                        "Бумага " + position.getBond().secid() + " — WATCH: " + position.getChecks().explanation()));
            }
        }
    }

    private BigDecimal tax(FilterParams filters, BigDecimal couponIncome) {
        if (!filters.taxOnCoupons() || couponIncome == null || couponIncome.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        return new TaxCalculator().taxOnCoupons(couponIncome, filters.taxProfile(), filters.annualOtherIncome());
    }
}
