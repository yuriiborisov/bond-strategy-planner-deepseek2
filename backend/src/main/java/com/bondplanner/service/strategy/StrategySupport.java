package com.bondplanner.service.strategy;

import com.bondplanner.domain.entity.IssuerFactor;
import com.bondplanner.domain.model.OfferTreatment;
import com.bondplanner.service.calc.BondInfo;
import com.bondplanner.service.calc.BondMetricsService;
import com.bondplanner.service.calc.CashFlowPoint;
import com.bondplanner.service.calc.FilterParams;
import com.bondplanner.service.calc.Params;
import com.bondplanner.service.calc.PositionResult;
import com.bondplanner.service.calc.WarningItem;
import com.bondplanner.service.cashflow.DatedCashFlow;
import com.bondplanner.service.checks.AssetCheckService;
import com.bondplanner.service.checks.AssetCheckView;
import com.bondplanner.service.rating.RatingNormalizer;
import com.bondplanner.service.rating.RatingView;
import com.bondplanner.service.tax.TaxCalculator;
import com.bondplanner.service.universe.BondCandidate;
import com.bondplanner.util.Decimals;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Общие операции стратегий: метрики, чек-листы, агрегаты, предупреждения.
 */
@Service
@RequiredArgsConstructor
public class StrategySupport {

    public static final String ALGORITHM_VERSION = "1.0.0";
    public static final ZoneId MSK = ZoneId.of("Europe/Moscow");

    private final BondMetricsService metricsService;
    private final RatingNormalizer ratingNormalizer;

    public LocalDate today() {
        return LocalDate.now(MSK);
    }

    public LocalDate parseDate(Params params, String field, LocalDate fallback) {
        String raw = params.text(field, null);
        if (raw == null) {
            return fallback;
        }
        try {
            if (raw.matches("\\d{2}\\.\\d{2}\\.\\d{4}")) {
                return LocalDate.parse(raw, java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy"));
            }
            return LocalDate.parse(raw);
        } catch (java.time.format.DateTimeParseException ex) {
            throw com.bondplanner.api.error.ApiException.badRequest("VALIDATION_ERROR",
                    "Некорректная дата в поле " + field + ": " + raw);
        }
    }

    public BondMetricsService.BondMetrics metrics(BondCandidate candidate, FilterParams filters,
                                                  OfferTreatment treatment, LocalDate settlement) {
        return metricsService.metrics(candidate, treatment, OfferTreatment.AUTO, settlement, filters.commissionPct());
    }

    public BondInfo bondInfo(BondCandidate candidate, BondMetricsService.BondMetrics metrics, RatingView rating) {
        return new BondInfo(
                candidate.secid(),
                candidate.bond().getIsin(),
                candidate.bond().getShortName(),
                candidate.bond().getIssuerName(),
                candidate.bond().getMaturityDate(),
                candidate.bond().getOfferDate(),
                metrics == null ? null : metrics.effectiveMaturity(),
                metrics == null ? null : metrics.cleanPricePct(),
                metrics == null ? null : metrics.nkd(),
                metrics == null ? null : metrics.ytmPct(),
                metrics == null ? null : metrics.modifiedDurationYears(),
                candidate.lotSize(),
                candidate.bond().getListLevel(),
                candidate.bond().getIssueSize(),
                rating == null ? null : rating.rating(),
                rating == null ? null : rating.agency(),
                rating == null ? null : rating.ratingDate(),
                rating == null || rating.outlook() == null ? null : rating.outlook().name());
    }

    public RatingView rating(CalculationContext context, BondCandidate candidate) {
        if (candidate.bond().getIssuer() == null) {
            return null;
        }
        return context.snapshot().ratings().get(candidate.bond().getIssuer().getId());
    }

    public List<IssuerFactor> factors(CalculationContext context, BondCandidate candidate) {
        if (candidate.bond().getIssuer() == null) {
            return List.of();
        }
        return context.snapshot().factors().getOrDefault(candidate.bond().getIssuer().getId(), List.of());
    }

    public AssetCheckView check(CalculationContext context, BondCandidate candidate,
                                BondMetricsService.BondMetrics metrics, RatingView rating,
                                FilterParams filters, String strategy, LocalDate targetDate, int toleranceDays,
                                LocalDate minMaturity, LocalDate maxMaturity, BigDecimal peerMedianYtm) {
        if (!filters.assetChecks() || context.services().checks() == null) {
            return null;
        }
        AssetCheckService.CheckContext checkContext = new AssetCheckService.CheckContext(
                filters, strategy, today(), targetDate, toleranceDays, minMaturity, maxMaturity,
                peerMedianYtm, ratingNormalizer.minimumScore(filters.minRating()),
                context.snapshot().snapshotTs(), factors(context, candidate));
        return context.services().checks().check(candidate, metrics, rating, checkContext);
    }

    public List<CashFlowPoint> points(List<DatedCashFlow> flows, int quantity) {
        if (flows == null) {
            return List.of();
        }
        return flows.stream()
                .map(f -> new CashFlowPoint(f.date(), f.kind().name(),
                        Decimals.money(f.amount().multiply(BigDecimal.valueOf(quantity)))))
                .sorted(Comparator.comparing(CashFlowPoint::date))
                .toList();
    }

    public List<CashFlowPoint> withTaxes(List<CashFlowPoint> points, FilterParams filters) {
        if (!filters.taxOnCoupons()) {
            return points;
        }
        List<CashFlowPoint> result = new ArrayList<>(points);
        for (CashFlowPoint point : points) {
            if (!"COUPON".equals(point.type())) {
                continue;
            }
            BigDecimal tax = point.amountRub().multiply(BigDecimal.valueOf(0.13))
                    .setScale(2, java.math.RoundingMode.HALF_UP);
            result.add(new CashFlowPoint(point.date(), "TAX", tax.negate()));
        }
        result.sort(Comparator.comparing(CashFlowPoint::date).thenComparing(CashFlowPoint::type));
        return result;
    }

    public PositionResult position(BondCandidate candidate, BondMetricsService.BondMetrics metrics,
                                   RatingView rating, AssetCheckView checks, int quantity,
                                   BigDecimal amountRub, BigDecimal weight, String reason) {
        PositionResult position = new PositionResult();
        position.setBond(bondInfo(candidate, metrics, rating));
        position.setQuantity(quantity);
        position.setAmountRub(Decimals.money(amountRub));
        position.setWeight(weight);
        position.setYtmPct(metrics == null ? null : metrics.ytmPct());
        position.setDurationYears(metrics == null ? null : metrics.modifiedDurationYears());
        position.setReason(reason);
        position.setChecks(checks);
        return position;
    }

    public BigDecimal weightedYtm(List<PositionResult> positions) {        BigDecimal sum = BigDecimal.ZERO;
        BigDecimal weight = BigDecimal.ZERO;
        for (PositionResult position : positions) {
            if (position.getAmountRub() == null || position.getYtmPct() == null) {
                continue;
            }
            sum = sum.add(position.getAmountRub().multiply(position.getYtmPct()));
            weight = weight.add(position.getAmountRub());
        }
        if (weight.signum() == 0) {
            return null;
        }
        return sum.divide(weight, 4, java.math.RoundingMode.HALF_UP);
    }

    public BigDecimal weightedDuration(List<PositionResult> positions) {
        BigDecimal sum = BigDecimal.ZERO;
        BigDecimal weight = BigDecimal.ZERO;
        for (PositionResult position : positions) {
            if (position.getAmountRub() == null || position.getDurationYears() == null) {
                continue;
            }
            sum = sum.add(position.getAmountRub().multiply(position.getDurationYears()));
            weight = weight.add(position.getAmountRub());
        }
        if (weight.signum() == 0) {
            return null;
        }
        return sum.divide(weight, 4, java.math.RoundingMode.HALF_UP);
    }

    public List<DatedCashFlow> metricsServiceFlows(BondCandidate candidate, LocalDate effectiveMaturity,
                                                   LocalDate settlement) {
        return metricsService.bondFlows(candidate, effectiveMaturity, settlement);
    }

    public BigDecimal metricsCouponIncome(BondCandidate candidate, LocalDate from, LocalDate to) {
        return metricsService.couponIncome(candidate, from, to);
    }

    public BigDecimal couponIncome(List<PositionResult> positions, List<List<DatedCashFlow>> flowsPerPosition,                                   LocalDate from, LocalDate to) {
        BigDecimal total = BigDecimal.ZERO;
        for (int i = 0; i < positions.size(); i++) {
            PositionResult position = positions.get(i);
            List<DatedCashFlow> flows = flowsPerPosition.get(i);
            if (flows == null) {
                continue;
            }
            for (DatedCashFlow flow : flows) {
                if (flow.kind() == DatedCashFlow.CashFlowKind.COUPON
                        && !flow.date().isBefore(from) && !flow.date().isAfter(to)) {
                    total = total.add(flow.amount().multiply(BigDecimal.valueOf(position.getQuantity())));
                }
            }
        }
        return Decimals.money(total);
    }

    public BigDecimal marketValue(List<PositionResult> positions) {
        return positions.stream()
                .map(p -> Decimals.nz(p.getAmountRub()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public List<WarningItem> concentrationWarnings(List<PositionResult> positions, BigDecimal totalAmount,
                                                   FilterParams filters) {
        List<WarningItem> warnings = new ArrayList<>();
        if (totalAmount == null || totalAmount.signum() == 0 || positions.isEmpty()) {
            return warnings;
        }
        Map<String, BigDecimal> byIssuer = new LinkedHashMap<>();
        Map<String, BigDecimal> byRating = new LinkedHashMap<>();
        for (PositionResult position : positions) {
            if (position.getAmountRub() == null) {
                continue;
            }
            String issuer = position.getBond().issuer() == null ? "—" : position.getBond().issuer();
            byIssuer.merge(issuer, position.getAmountRub(), BigDecimal::add);
            String rating = position.getBond().rating() == null ? "NO_RATING" : position.getBond().rating();
            byRating.merge(rating, position.getAmountRub(), BigDecimal::add);
            BigDecimal share = position.getAmountRub().divide(totalAmount, 6, java.math.RoundingMode.HALF_UP);
            if (share.compareTo(new BigDecimal("0.35")) > 0) {
                warnings.add(WarningItem.warn("CONCENTRATION_BOND",
                        "Доля бумаги " + position.getBond().secid() + " " + percent(share)
                                + " — выше 35% капитала"));
            }
        }
        for (Map.Entry<String, BigDecimal> entry : byIssuer.entrySet()) {
            BigDecimal share = entry.getValue().divide(totalAmount, 6, java.math.RoundingMode.HALF_UP);
            if (share.compareTo(filters.maxPerIssuerShare()) > 0) {
                warnings.add(WarningItem.warn("CONCENTRATION_ISSUER",
                        "Доля эмитента «" + entry.getKey() + "» " + percent(share)
                                + " — выше лимита " + percent(filters.maxPerIssuerShare())));
            }
        }
        for (Map.Entry<String, BigDecimal> entry : byRating.entrySet()) {
            BigDecimal share = entry.getValue().divide(totalAmount, 6, java.math.RoundingMode.HALF_UP);
            if (share.compareTo(filters.maxPerRatingShare()) > 0) {
                warnings.add(WarningItem.warn("CONCENTRATION_RATING",
                        "Доля рейтинговой группы " + entry.getKey() + " " + percent(share)
                                + " — выше лимита " + percent(filters.maxPerRatingShare())));
            }
        }
        return warnings;
    }

    public List<WarningItem> residualWarning(BigDecimal residual, BigDecimal total) {
        if (residual == null || total == null || total.signum() == 0) {
            return List.of();
        }
        BigDecimal share = residual.divide(total, 6, java.math.RoundingMode.HALF_UP);
        if (share.compareTo(new BigDecimal("0.05")) > 0) {
            return List.of(WarningItem.warn("CASH_RESIDUAL",
                    "Свободный остаток " + Decimals.money(residual).toPlainString() + " ₽ ("
                            + percent(share) + ") — выше 5% капитала"));
        }
        return List.of();
    }

    public List<WarningItem> dataFreshnessWarning(String freshness) {
        if ("STALE".equals(freshness)) {
            return List.of(WarningItem.warn("DATA_STALE", "Данные старше 24 часов — расчёт выполнен на устаревшем снимке"));
        }
        if ("EXPIRED".equals(freshness) || "UNKNOWN".equals(freshness)) {
            return List.of(WarningItem.warn("DATA_EXPIRED", "Нет свежего снимка рыночных данных — запустите синхронизацию"));
        }
        return List.of();
    }

    public Map<String, Object> assumptions(FilterParams filters, BigDecimal reinvestRatePct) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("commissionPct", filters.commissionPct());
        map.put("taxProfile", filters.taxProfile());
        map.put("taxOnCoupons", filters.taxOnCoupons());
        map.put("reinvestRatePct", reinvestRatePct);
        map.put("currency", "RUB");
        map.put("rounding", "BigDecimal(18, HALF_UP)");
        map.put("algorithmVersion", ALGORITHM_VERSION);
        return map;
    }

    public String percent(BigDecimal fraction) {
        return fraction.multiply(BigDecimal.valueOf(100)).setScale(1, java.math.RoundingMode.HALF_UP) + "%";
    }

    public BigDecimal median(List<BigDecimal> values) {
        List<BigDecimal> sorted = values.stream().filter(Objects::nonNull).sorted().toList();
        if (sorted.isEmpty()) {
            return null;
        }
        int mid = sorted.size() / 2;
        if (sorted.size() % 2 == 0) {
            return sorted.get(mid - 1).add(sorted.get(mid)).divide(BigDecimal.valueOf(2), MathContext.DECIMAL64);
        }
        return sorted.get(mid);
    }

    public BigDecimal share(BigDecimal part, BigDecimal total) {
        if (total == null || total.signum() == 0) {
            return BigDecimal.ZERO;
        }
        return part.divide(total, 6, java.math.RoundingMode.HALF_UP);
    }
}
