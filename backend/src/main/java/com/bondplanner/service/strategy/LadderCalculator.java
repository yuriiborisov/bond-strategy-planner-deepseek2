package com.bondplanner.service.strategy;

import com.bondplanner.api.error.ApiException;
import com.bondplanner.domain.model.OfferTreatment;
import com.bondplanner.service.calc.BondMetricsService;
import com.bondplanner.service.calc.CalculationResult;
import com.bondplanner.service.calc.CashFlowPoint;
import com.bondplanner.service.calc.FilterParams;
import com.bondplanner.service.calc.Params;
import com.bondplanner.service.calc.PositionResult;
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
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * LADDER — «Лестница»: ступени с разными датами погашения, перенос остатков,
 * запрет дубликатов и rolling-симуляция (§3.5).
 */
@Component
@RequiredArgsConstructor
public class LadderCalculator implements StrategyCalculator {

    private final StrategySupport support;

    @Override
    public String code() {
        return "LADDER";
    }

    @Override
    public CalculationResult calculate(CalculationContext context, Params params) {
        LocalDate today = support.today();
        FilterParams filters = FilterParams.from(params);
        BigDecimal amount = params.decimal("amount", null);
        if (amount == null || amount.signum() <= 0) {
            throw ApiException.badRequest("VALIDATION_ERROR", "amount должен быть больше 0");
        }
        int rungsCount = params.integer("rungs", 5);
        if (rungsCount < 1 || rungsCount > 10) {
            throw ApiException.badRequest("VALIDATION_ERROR", "rungs должен быть в диапазоне 1..10");
        }
        int stepMonths = params.integer("stepMonths", 12);
        if (stepMonths < 1 || stepMonths > 60) {
            throw ApiException.badRequest("VALIDATION_ERROR", "stepMonths должен быть в диапазоне 1..60");
        }
        LocalDate startDate = support.parseDate(params, "startDate", today);
        if (startDate.isBefore(today)) {
            throw ApiException.badRequest("VALIDATION_ERROR", "startDate не может быть в прошлом");
        }
        int tolerance = params.integer("maturityToleranceDays", 45);
        if (tolerance < 0 || tolerance > 180) {
            throw ApiException.badRequest("VALIDATION_ERROR", "maturityToleranceDays должен быть в диапазоне 0..180");
        }
        OfferTreatment treatment = OfferTreatment.parse(params.text("offerTreatment", "OPTION"), OfferTreatment.OPTION);
        boolean allowDuplicates = params.bool("allowDuplicates", false);
        boolean rolling = params.bool("rolling", false);
        int rollingYears = params.integer("rollingHorizonYears", 10);
        boolean reinvestCoupons = params.bool("reinvestCoupons", true);
        BigDecimal fallbackRate = params.child("reinvest").decimal("fallbackRatePct", BigDecimal.valueOf(12.0));
        List<BigDecimal> weights = resolveWeights(params, rungsCount);

        List<BondCandidate> eligible = context.snapshot().bonds().stream()
                .filter(c -> context.services().filter().matchesStructural(c, filters, support.rating(context, c)))
                .filter(c -> context.services().filter().notMatured(c, treatment, OfferTreatment.OPTION, today))
                .toList();
        if (eligible.isEmpty()) {
            throw ApiException.noCandidates("Ни одна бумага не прошла структурные фильтры",
                    List.of("Ослабить фильтры рейтинга/ликвидности", "Запустить синхронизацию"));
        }

        List<Map<String, Object>> rungs = new ArrayList<>();
        List<PositionResult> positions = new ArrayList<>();
        List<List<DatedCashFlow>> flowsPerPosition = new ArrayList<>();
        List<WarningItem> warnings = new ArrayList<>();
        Set<Long> usedBonds = new HashSet<>();
        BigDecimal carry = BigDecimal.ZERO;
        BigDecimal invested = BigDecimal.ZERO;
        int skipped = 0;

        for (int i = 0; i < rungsCount; i++) {
            LocalDate rungDate = startDate.plusMonths((long) i * stepMonths);
            BigDecimal budget = amount.multiply(weights.get(i)).add(carry);
            LocalDate from = rungDate.minusDays(tolerance);
            LocalDate to = rungDate.plusDays(tolerance);

            List<BondCandidate> inWindow = eligible.stream()
                    .filter(c -> {
                        LocalDate mat = c.effectiveMaturity(treatment, OfferTreatment.OPTION);
                        return mat != null && !mat.isBefore(from) && !mat.isAfter(to);
                    })
                    .toList();
            boolean expanded = false;
            List<BondCandidate> candidates = inWindow;
            if (candidates.isEmpty()) {
                candidates = eligible.stream()
                        .filter(c -> {
                            LocalDate mat = c.effectiveMaturity(treatment, OfferTreatment.OPTION);
                            return mat != null && !mat.isBefore(rungDate);
                        })
                        .sorted(Comparator.comparing(c -> c.effectiveMaturity(treatment, OfferTreatment.OPTION)))
                        .limit(20)
                        .toList();
                expanded = true;
                warnings.add(WarningItem.warn("RUNG_WINDOW_EXPANDED",
                        "Для ступени " + i + " нет бумаг в допуске ±" + tolerance
                                + " дней — окно расширено до ближайших погашений"));
            }
            if (candidates.isEmpty()) {
                skipped++;
                warnings.add(WarningItem.warn("RUNG_SKIPPED",
                        "Для ступени " + i + " нет подходящих бумаг — бюджет перенесён"));
                rungs.add(skippedRung(i, rungDate, budget, "NO_CANDIDATES"));
                carry = budget;
                continue;
            }

            List<RankedRung> scored = new ArrayList<>();
            for (BondCandidate candidate : candidates) {
                BondMetricsService.BondMetrics metrics = support.metrics(candidate, filters, treatment, today);
                if (metrics == null || metrics.ytmPct() == null) {
                    continue;
                }
                long diff = Math.abs(ChronoUnit.DAYS.between(metrics.effectiveMaturity(), rungDate));
                scored.add(new RankedRung(candidate, metrics, diff));
            }
            scored.sort(Comparator.comparingLong(RankedRung::dayDiff)
                    .thenComparing((RankedRung r) -> r.metrics().ytmPct(), Comparator.reverseOrder())
                    .thenComparing(r -> r.candidate().volumeRub(), Comparator.reverseOrder()));
            if (scored.isEmpty()) {
                skipped++;
                rungs.add(skippedRung(i, rungDate, budget, "NO_PRICED_CANDIDATES"));
                carry = budget;
                continue;
            }

            BigDecimal medianYtm = support.median(scored.stream().map(r -> r.metrics().ytmPct()).toList());
            RankedRung chosen = null;
            AssetCheckView chosenChecks = null;
            for (RankedRung option : scored) {
                if (!allowDuplicates && usedBonds.contains(option.candidate().bondId())
                        && scored.stream().anyMatch(r -> !usedBonds.contains(r.candidate().bondId()))) {
                    continue;
                }
                RatingView rating = support.rating(context, option.candidate());
                AssetCheckView checks = support.check(context, option.candidate(), option.metrics(), rating,
                        filters, "LADDER", rungDate, tolerance,
                        expanded ? null : rungDate.minusDays(tolerance),
                        expanded ? null : rungDate.plusDays(tolerance), medianYtm);
                if (checks != null && checks.rejected()) {
                    continue;
                }
                chosen = option;
                chosenChecks = checks;
                break;
            }
            if (chosen == null) {
                skipped++;
                warnings.add(WarningItem.warn("RUNG_SKIPPED",
                        "Все бумаги ступени " + i + " отклонены проверками — бюджет перенесён"));
                rungs.add(skippedRung(i, rungDate, budget, "ASSET_CHECK_FAILED"));
                carry = budget;
                continue;
            }

            BigDecimal lotPrice = chosen.metrics().lotPrice(filters.commissionPct(), chosen.candidate().lotSize());
            if (lotPrice.signum() <= 0 || budget.compareTo(lotPrice) < 0) {
                skipped++;
                warnings.add(WarningItem.warn("RUNG_INSUFFICIENT_BUDGET",
                        "Бюджета ступени " + i + " не хватает на один лот — остаток перенесён"));
                rungs.add(skippedRung(i, rungDate, budget, "SKIPPED_INSUFFICIENT_BUDGET"));
                carry = budget;
                continue;
            }
            int lots = budget.divide(lotPrice, 0, RoundingMode.DOWN).intValue();
            int quantity = lots * chosen.candidate().lotSize();
            BigDecimal cost = lotPrice.multiply(BigDecimal.valueOf(lots));
            BigDecimal residual = budget.subtract(cost);
            RatingView rating = support.rating(context, chosen.candidate());
            BigDecimal couponPerBond = support.metricsCouponIncome(chosen.candidate(), today,
                    chosen.metrics().effectiveMaturity());
            String reason = reason(chosen, rungDate, medianYtm);

            PositionResult position = support.position(chosen.candidate(), chosen.metrics(), rating, chosenChecks,
                    quantity, cost, support.share(cost, amount), reason);
            position.setRungIndex(i);
            position.setTargetDate(rungDate);
            position.setLots(BigDecimal.valueOf(lots));
            position.setCouponIncomeRub(Decimals.money(couponPerBond.multiply(BigDecimal.valueOf(quantity))));
            position.setCostRub(Decimals.money(cost));
            position.setResidualRub(Decimals.money(residual));
            positions.add(position);
            flowsPerPosition.add(chosen.metrics().bondFlows());
            usedBonds.add(chosen.candidate().bondId());
            invested = invested.add(cost);
            carry = residual;

            Map<String, Object> rung = new LinkedHashMap<>();
            rung.put("index", i);
            rung.put("targetDate", rungDate);
            rung.put("budgetRub", Decimals.money(budget));
            rung.put("bond", position.getBond());
            rung.put("quantity", quantity);
            rung.put("lots", lots);
            rung.put("costRub", Decimals.money(cost));
            rung.put("residualRub", Decimals.money(residual));
            rung.put("couponIncomeRub", position.getCouponIncomeRub());
            rung.put("ytmPct", chosen.metrics().ytmPct());
            rung.put("durationYears", chosen.metrics().modifiedDurationYears());
            rung.put("weight", support.share(cost, amount));
            rung.put("reason", reason);
            rung.put("status", "FILLED");
            rung.put("checks", chosenChecks);
            rungs.add(rung);
        }

        BigDecimal cashResidual = carry;
        LocalDate horizon = positions.stream()
                .map(p -> p.getBond().effectiveMaturity())
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(startDate);
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

        BigDecimal longestYtm = positions.stream()
                .filter(p -> p.getTargetDate() != null)
                .max(Comparator.comparing(PositionResult::getTargetDate))
                .map(PositionResult::getYtmPct)
                .orElse(fallbackRate);
        BigDecimal reinvestRate = longestYtm == null ? fallbackRate : longestYtm;

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalAmountRub", Decimals.money(amount));
        summary.put("investedRub", Decimals.money(invested));
        summary.put("cashResidualRub", Decimals.money(cashResidual));
        summary.put("rungsCount", rungsCount);
        summary.put("filledRungs", rungsCount - skipped);
        summary.put("weightedYtmPct", support.weightedYtm(positions));
        summary.put("weightedDurationYears", support.weightedDuration(positions));
        summary.put("totalCouponIncomeRub", couponIncome);
        summary.put("totalTaxRub", tax);
        summary.put("projectedValueRub", Decimals.money(invested.add(couponIncome).subtract(tax).add(cashResidual)));

        warnings.addAll(support.concentrationWarnings(positions, amount, filters));
        warnings.addAll(support.residualWarning(cashResidual, amount));
        warnings.addAll(support.dataFreshnessWarning(context.snapshot().dataFreshness()));
        for (PositionResult position : positions) {
            if (position.getChecks() != null && "WATCH".equals(position.getChecks().verdict())) {
                warnings.add(WarningItem.warn("ASSET_CHECK_WATCH",
                        "Бумага " + position.getBond().secid() + " — WATCH: " + position.getChecks().explanation()));
            }
        }

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("rungs", rungs);
        details.put("cashResidualRub", Decimals.money(cashResidual));
        details.put("startDate", startDate);
        details.put("stepMonths", stepMonths);
        details.put("rolling", rolling);
        details.put("offerTreatment", treatment.name());
        details.put("reinvestCoupons", reinvestCoupons);
        details.put("reinvestRatePct", Decimals.percent(reinvestRate));
        if (rolling) {
            details.put("rollingProjection", rollingProjection(positions, flowsPerPosition, invested,
                    reinvestRate, rollingYears, today, filters));
        } else {
            details.put("rollingProjection", null);
        }

        return CalculationResult.builder()
                .strategy(code())
                .exchange(context.snapshot().exchangeCode())
                .snapshotTs(context.snapshot().snapshotTs())
                .dataFreshness(context.snapshot().dataFreshness())
                .summary(summary)
                .positions(positions)
                .cashFlowSchedule(schedule)
                .warnings(warnings)
                .assumptions(support.assumptions(filters, reinvestRate))
                .details(details)
                .build();
    }

    private Map<String, Object> rollingProjection(List<PositionResult> positions,
                                                  List<List<DatedCashFlow>> flowsPerPosition,
                                                  BigDecimal invested, BigDecimal reinvestRatePct,
                                                  int horizonYears, LocalDate today, FilterParams filters) {
        int years = Math.max(1, Math.min(30, horizonYears));
        double rate = reinvestRatePct.doubleValue() / 100;
        BigDecimal[] couponGross = new BigDecimal[years + 1];
        BigDecimal[] maturities = new BigDecimal[years + 1];
        for (int i = 0; i <= years; i++) {
            couponGross[i] = BigDecimal.ZERO;
            maturities[i] = BigDecimal.ZERO;
        }
        for (int i = 0; i < positions.size(); i++) {
            PositionResult position = positions.get(i);
            for (DatedCashFlow flow : flowsPerPosition.get(i)) {
                int year = flow.date().getYear() - today.getYear() + 1;
                if (year < 1 || year > years) {
                    continue;
                }
                BigDecimal amount = flow.amount().multiply(BigDecimal.valueOf(position.getQuantity()));
                if (flow.kind() == DatedCashFlow.CashFlowKind.COUPON) {
                    couponGross[year] = couponGross[year].add(amount);
                } else if (flow.kind() == DatedCashFlow.CashFlowKind.MATURITY
                        || flow.kind() == DatedCashFlow.CashFlowKind.AMORTIZATION) {
                    maturities[year] = maturities[year].add(amount);
                }
            }
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        BigDecimal totalCoupons = BigDecimal.ZERO;
        BigDecimal totalTax = BigDecimal.ZERO;
        BigDecimal totalReinvested = BigDecimal.ZERO;
        BigDecimal finalCapital = invested;
        for (int year = 1; year <= years; year++) {
            BigDecimal gross = couponGross[year];
            BigDecimal tax = filters.taxOnCoupons()
                    ? new TaxCalculator().taxOnCoupons(gross, filters.taxProfile(), BigDecimal.ZERO)
                    : BigDecimal.ZERO;
            BigDecimal net = gross.subtract(tax).add(maturities[year]);
            BigDecimal capital = BigDecimal.valueOf(invested.doubleValue() * Math.pow(1 + rate, year));
            for (int y = 1; y <= year; y++) {
                BigDecimal yearNet = couponGross[y]
                        .subtract(filters.taxOnCoupons()
                                ? new TaxCalculator().taxOnCoupons(couponGross[y], filters.taxProfile(), BigDecimal.ZERO)
                                : BigDecimal.ZERO)
                        .add(maturities[y]);
                capital = capital.add(BigDecimal.valueOf(yearNet.doubleValue() * Math.pow(1 + rate, year - y)));
            }
            totalCoupons = totalCoupons.add(gross);
            totalTax = totalTax.add(tax);
            totalReinvested = totalReinvested.add(net);
            finalCapital = capital;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("year", year);
            row.put("date", today.plusYears(year));
            row.put("couponGrossRub", Decimals.money(gross));
            row.put("taxRub", Decimals.money(tax));
            row.put("maturitiesRub", Decimals.money(maturities[year]));
            row.put("reinvestedRub", Decimals.money(net));
            row.put("projectedCapitalRub", Decimals.money(capital));
            rows.add(row);
        }
        Map<String, Object> projection = new LinkedHashMap<>();
        projection.put("horizonYears", years);
        projection.put("reinvestRatePct", Decimals.percent(reinvestRatePct));
        projection.put("rows", rows);
        projection.put("totalCouponsRub", Decimals.money(totalCoupons));
        projection.put("totalTaxRub", Decimals.money(totalTax));
        projection.put("totalReinvestedRub", Decimals.money(totalReinvested));
        projection.put("finalCapitalRub", Decimals.money(finalCapital));
        return projection;
    }

    private List<BigDecimal> resolveWeights(Params params, int rungsCount) {
        List<BigDecimal> provided = params.decimalList("weights", null);
        if (provided == null || provided.size() != rungsCount) {
            List<BigDecimal> equal = new ArrayList<>();
            BigDecimal share = BigDecimal.ONE.divide(BigDecimal.valueOf(rungsCount), 10, RoundingMode.HALF_UP);
            for (int i = 0; i < rungsCount; i++) {
                equal.add(share);
            }
            return equal;
        }
        BigDecimal sum = provided.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        if (sum.subtract(BigDecimal.ONE).abs().compareTo(new BigDecimal("0.01")) > 0) {
            throw ApiException.badRequest("VALIDATION_ERROR", "Сумма weights должна быть равна 1");
        }
        return provided;
    }

    private Map<String, Object> skippedRung(int index, LocalDate targetDate, BigDecimal budget, String status) {
        Map<String, Object> rung = new LinkedHashMap<>();
        rung.put("index", index);
        rung.put("targetDate", targetDate);
        rung.put("budgetRub", Decimals.money(budget));
        rung.put("bond", null);
        rung.put("quantity", 0);
        rung.put("costRub", BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        rung.put("residualRub", Decimals.money(budget));
        rung.put("couponIncomeRub", BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        rung.put("status", status);
        return rung;
    }

    private String reason(RankedRung ranked, LocalDate rungDate, BigDecimal medianYtm) {
        long days = ChronoUnit.DAYS.between(ranked.metrics().effectiveMaturity(), rungDate);
        StringBuilder sb = new StringBuilder("Погашение ");
        if (days == 0) {
            sb.append("в дату ступени");
        } else if (days > 0) {
            sb.append("за ").append(days).append(" дн. до даты ступени");
        } else {
            sb.append("через ").append(-days).append(" дн. после даты ступени");
        }
        if (ranked.metrics().ytmPct() != null && medianYtm != null) {
            BigDecimal diff = ranked.metrics().ytmPct().subtract(medianYtm);
            sb.append("; YTM ").append(ranked.metrics().ytmPct().setScale(2, RoundingMode.HALF_UP)).append("%");
            if (diff.signum() >= 0) {
                sb.append(" — выше медианы по окну на ").append(diff.setScale(2, RoundingMode.HALF_UP)).append(" п.п.");
            } else {
                sb.append(" — ниже медианы на ").append(diff.abs().setScale(2, RoundingMode.HALF_UP)).append(" п.п.");
            }
        }
        return sb.toString();
    }

    private BigDecimal tax(FilterParams filters, BigDecimal couponIncome) {
        if (!filters.taxOnCoupons() || couponIncome == null || couponIncome.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        return new TaxCalculator().taxOnCoupons(couponIncome, filters.taxProfile(), BigDecimal.ZERO);
    }

    private record RankedRung(BondCandidate candidate, BondMetricsService.BondMetrics metrics, long dayDiff) {
    }
}
