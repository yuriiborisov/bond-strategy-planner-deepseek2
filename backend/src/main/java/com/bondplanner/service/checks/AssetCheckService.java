package com.bondplanner.service.checks;

import com.bondplanner.domain.entity.IssuerFactor;
import com.bondplanner.domain.model.CheckStatus;
import com.bondplanner.domain.model.Outlook;
import com.bondplanner.service.calc.BondMetricsService;
import com.bondplanner.service.calc.FilterParams;
import com.bondplanner.service.rating.RatingNormalizer;
import com.bondplanner.service.rating.RatingView;
import com.bondplanner.service.universe.BondCandidate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Детерминированная предпроверка бумаги (§19): жёсткие гейты, WATCH-причины,
 * score и объяснение. LLM не может изменить verdict.
 */
@Service
@RequiredArgsConstructor
public class AssetCheckService {

    private static final Map<String, Double> WEIGHTS = new LinkedHashMap<>();

    static {
        WEIGHTS.put("CREDIT_RATING", 0.20);
        WEIGHTS.put("RATING_OUTLOOK", 0.05);
        WEIGHTS.put("RATING_AGE", 0.05);
        WEIGHTS.put("LIST_LEVEL", 0.05);
        WEIGHTS.put("ISSUE_SIZE", 0.05);
        WEIGHTS.put("LIQUIDITY", 0.15);
        WEIGHTS.put("YTM_VS_PEER", 0.10);
        WEIGHTS.put("DURATION_FIT", 0.10);
        WEIGHTS.put("PRICE_MOMENTUM", 0.05);
        WEIGHTS.put("REVENUE_GROWTH", 0.05);
        WEIGHTS.put("DEBT_EBITDA", 0.05);
        WEIGHTS.put("INTEREST_COVERAGE", 0.05);
        WEIGHTS.put("EVENT_RISK", 0.05);
    }

    private final RatingNormalizer normalizer;

    public record CheckContext(
            FilterParams filters,
            String strategy,
            LocalDate settlement,
            LocalDate targetDate,
            int toleranceDays,
            LocalDate minMaturity,
            LocalDate maxMaturity,
            BigDecimal peerMedianYtm,
            BigDecimal minRatingScore,
            Instant snapshotTs,
            List<IssuerFactor> factors) {
    }

    public AssetCheckView check(BondCandidate candidate, BondMetricsService.BondMetrics metrics,
                                RatingView rating, CheckContext ctx) {
        List<AssetCheckView.Item> items = new ArrayList<>();
        FilterParams filters = ctx.filters();
        List<IssuerFactor> factors = ctx.factors() == null ? List.of() : ctx.factors();

        items.add(creditRating(rating, ctx));
        items.add(outlookCheck(rating));
        items.add(ratingAge(rating, ctx));
        items.add(listLevel(candidate, filters));
        items.add(issueSize(candidate, filters));
        items.add(liquidity(candidate, filters));
        items.add(ytmVsPeer(metrics, ctx));
        items.add(durationFit(metrics, ctx));
        items.add(priceMomentum(factors));
        items.add(factorCheck(factors, "REVENUE_GROWTH", "REVENUE_GROWTH", "≥ 0% г/г", v -> v >= 0, false));
        items.add(factorCheck(factors, "DEBT_EBITDA", "DEBT_EBITDA", "≤ 3.0", v -> v <= 3.0, true));
        items.add(factorCheck(factors, "INTEREST_COVERAGE", "INTEREST_COVERAGE", "≥ 2.0", v -> v >= 2.0, true));
        items.add(eventRisk(rating));

        boolean gateFailed = items.stream()
                .filter(i -> i.status() == CheckStatus.FAIL)
                .anyMatch(i -> isHardGate(i.code()));
        boolean hasWarn = items.stream().anyMatch(i -> i.status() == CheckStatus.WARN);
        boolean unknownCritical = items.stream().anyMatch(i -> i.status() == CheckStatus.UNKNOWN
                && ("CREDIT_RATING".equals(i.code()) || "RATING_AGE".equals(i.code()) || "EVENT_RISK".equals(i.code())));

        double score = weightedScore(items);
        String verdict;
        if (gateFailed) {
            verdict = "REJECTED";
        } else if (hasWarn || unknownCritical || score < 70) {
            verdict = "WATCH";
        } else {
            verdict = "APPROVED";
        }
        return new AssetCheckView(verdict, round(score), items, explanation(items, verdict), ctx.snapshotTs());
    }

    private boolean isHardGate(String code) {
        return switch (code) {
            case "CREDIT_RATING", "DURATION_FIT", "LIST_LEVEL", "ISSUE_SIZE", "EVENT_RISK" -> true;
            case "RATING_AGE" -> true;
            default -> false;
        };
    }

    private AssetCheckView.Item creditRating(RatingView rating, CheckContext ctx) {
        BigDecimal threshold = ctx.minRatingScore() == null ? BigDecimal.valueOf(70) : ctx.minRatingScore();
        if (rating == null) {
            CheckStatus status = ctx.filters().requireRating() ? CheckStatus.FAIL : CheckStatus.UNKNOWN;
            return new AssetCheckView.Item("CREDIT_RATING", status, "NO_RATING", "≥ " + ctx.filters().minRating(),
                    "—", null);
        }
        if (rating.invalid()) {
            return new AssetCheckView.Item("CREDIT_RATING", CheckStatus.FAIL, rating.rating(), "≥ " + ctx.filters().minRating(),
                    rating.agency(), rating.ratingDate());
        }
        CheckStatus status = rating.score().compareTo(threshold) >= 0 ? CheckStatus.PASS : CheckStatus.FAIL;
        return new AssetCheckView.Item("CREDIT_RATING", status, rating.rating(),
                "≥ " + ctx.filters().minRating(), rating.agency(), rating.ratingDate());
    }

    private AssetCheckView.Item outlookCheck(RatingView rating) {
        if (rating == null) {
            return new AssetCheckView.Item("RATING_OUTLOOK", CheckStatus.UNKNOWN, "NO_RATING", "не NEGATIVE", "—", null);
        }
        Outlook outlook = rating.outlook();
        if (outlook == Outlook.NEGATIVE) {
            return new AssetCheckView.Item("RATING_OUTLOOK", CheckStatus.WARN, outlook.name(), "не NEGATIVE",
                    rating.agency(), rating.ratingDate());
        }
        if (outlook == null) {
            return new AssetCheckView.Item("RATING_OUTLOOK", CheckStatus.UNKNOWN, "UNKNOWN", "не NEGATIVE",
                    rating.agency(), rating.ratingDate());
        }
        return new AssetCheckView.Item("RATING_OUTLOOK", CheckStatus.PASS, outlook.name(), "не NEGATIVE",
                rating.agency(), rating.ratingDate());
    }

    private AssetCheckView.Item ratingAge(RatingView rating, CheckContext ctx) {
        if (rating == null) {
            return new AssetCheckView.Item("RATING_AGE", CheckStatus.UNKNOWN, "NO_RATING",
                    "≤ " + ctx.filters().maxRatingAgeDays() + " дней", "—", null);
        }
        boolean stale = rating.stale();
        CheckStatus status;
        if (stale) {
            status = ctx.filters().requireRating() ? CheckStatus.FAIL : CheckStatus.WARN;
        } else {
            status = CheckStatus.PASS;
        }
        return new AssetCheckView.Item("RATING_AGE", status, rating.ratingDate(),
                "≤ " + ctx.filters().maxRatingAgeDays() + " дней", rating.agency(), rating.ratingDate());
    }

    private AssetCheckView.Item listLevel(BondCandidate candidate, FilterParams filters) {
        Integer level = candidate.bond().getListLevel();
        if (level == null) {
            return new AssetCheckView.Item("LIST_LEVEL", CheckStatus.UNKNOWN, null,
                    "≤ " + filters.minListLevel(), "MOEX ISS", null);
        }
        CheckStatus status = level <= filters.minListLevel() ? CheckStatus.PASS : CheckStatus.FAIL;
        return new AssetCheckView.Item("LIST_LEVEL", status, level, "≤ " + filters.minListLevel(), "MOEX ISS", null);
    }

    private AssetCheckView.Item issueSize(BondCandidate candidate, FilterParams filters) {
        BigDecimal size = candidate.bond().getIssueSize();
        if (size == null) {
            return new AssetCheckView.Item("ISSUE_SIZE", CheckStatus.UNKNOWN, null,
                    "≥ " + filters.minIssueSizeRub().toPlainString() + " ₽", "MOEX ISS", null);
        }
        CheckStatus status = size.compareTo(filters.minIssueSizeRub()) >= 0 ? CheckStatus.PASS : CheckStatus.FAIL;
        return new AssetCheckView.Item("ISSUE_SIZE", status, size,
                "≥ " + filters.minIssueSizeRub().toPlainString() + " ₽", "MOEX ISS", null);
    }

    private AssetCheckView.Item liquidity(BondCandidate candidate, FilterParams filters) {
        BigDecimal volume = candidate.volumeRub();
        CheckStatus status = volume.compareTo(filters.minAvgDailyVolumeRub()) >= 0 ? CheckStatus.PASS : CheckStatus.WARN;
        return new AssetCheckView.Item("LIQUIDITY", status, volume,
                "≥ " + filters.minAvgDailyVolumeRub().toPlainString() + " ₽/день", "MOEX ISS", null);
    }

    private AssetCheckView.Item ytmVsPeer(BondMetricsService.BondMetrics metrics, CheckContext ctx) {
        if (metrics == null || metrics.ytmPct() == null || ctx.peerMedianYtm() == null) {
            return new AssetCheckView.Item("YTM_VS_PEER", CheckStatus.UNKNOWN, null, "≥ медианы", "расчёт", null);
        }
        CheckStatus status = metrics.ytmPct().compareTo(ctx.peerMedianYtm()) >= 0 ? CheckStatus.PASS : CheckStatus.WARN;
        return new AssetCheckView.Item("YTM_VS_PEER", status, metrics.ytmPct(), "≥ медианы", "расчёт", null);
    }

    private AssetCheckView.Item durationFit(BondMetricsService.BondMetrics metrics, CheckContext ctx) {
        if (metrics == null || metrics.effectiveMaturity() == null) {
            return new AssetCheckView.Item("DURATION_FIT", CheckStatus.FAIL, null, "в окне стратегии", "расчёт", null);
        }
        LocalDate maturity = metrics.effectiveMaturity();
        if (ctx.minMaturity() != null && maturity.isBefore(ctx.minMaturity())) {
            return new AssetCheckView.Item("DURATION_FIT", CheckStatus.FAIL, maturity,
                    "≥ " + ctx.minMaturity(), "расчёт", null);
        }
        if (ctx.maxMaturity() != null && maturity.isAfter(ctx.maxMaturity())) {
            return new AssetCheckView.Item("DURATION_FIT", CheckStatus.FAIL, maturity,
                    "≤ " + ctx.maxMaturity(), "расчёт", null);
        }
        return new AssetCheckView.Item("DURATION_FIT", CheckStatus.PASS, maturity, "в окне стратегии", "расчёт", null);
    }

    private AssetCheckView.Item priceMomentum(List<IssuerFactor> factors) {
        BigDecimal value = factorValue(factors, "PRICE_MOMENTUM_6M");
        if (value == null) {
            return new AssetCheckView.Item("PRICE_MOMENTUM", CheckStatus.UNKNOWN, null, "≥ 0%", "MOEX history", null);
        }
        CheckStatus status = value.signum() >= 0 ? CheckStatus.PASS : CheckStatus.WARN;
        return new AssetCheckView.Item("PRICE_MOMENTUM", status, value, "≥ 0%", "MOEX history", null);
    }

    private AssetCheckView.Item factorCheck(List<IssuerFactor> factors, String code, String label, String threshold,
                                            java.util.function.Predicate<Double> predicate, boolean warnOnly) {
        BigDecimal value = factorValue(factors, code);
        if (value == null) {
            return new AssetCheckView.Item(code, CheckStatus.UNKNOWN, null, threshold, "отчётность", null);
        }
        boolean ok = predicate.test(value.doubleValue());
        CheckStatus status = ok ? CheckStatus.PASS : (warnOnly ? CheckStatus.WARN : CheckStatus.FAIL);
        return new AssetCheckView.Item(code, status, value, threshold, "отчётность", null);
    }

    private AssetCheckView.Item eventRisk(RatingView rating) {
        if (rating == null) {
            return new AssetCheckView.Item("EVENT_RISK", CheckStatus.UNKNOWN, "NO_DATA", "нет дефолта/реструктуризации",
                    "—", null);
        }
        String code = rating.ratingNormalized();
        String numeric = rating.rating() == null ? "" : rating.rating().toUpperCase();
        boolean isDefault = "D".equals(code) || numeric.startsWith("D(") || numeric.contains("DEFAULT")
                || numeric.contains("RESTRUCT");
        if (isDefault) {
            return new AssetCheckView.Item("EVENT_RISK", CheckStatus.FAIL, rating.rating(),
                    "нет дефолта/реструктуризации", rating.agency(), rating.ratingDate());
        }
        return new AssetCheckView.Item("EVENT_RISK", CheckStatus.PASS, "OK", "нет дефолта/реструктуризации",
                rating.agency(), rating.ratingDate());
    }

    private BigDecimal factorValue(List<IssuerFactor> factors, String code) {
        for (IssuerFactor factor : factors) {
            if (code.equals(factor.getFactorCode()) && factor.getValueNum() != null) {
                return factor.getValueNum();
            }
        }
        return null;
    }

    private double weightedScore(List<AssetCheckView.Item> items) {
        double weighted = 0;
        double totalWeight = 0;
        for (AssetCheckView.Item item : items) {
            Double weight = WEIGHTS.get(item.code());
            if (weight == null) {
                continue;
            }
            totalWeight += weight;
            double factor = switch (item.status()) {
                case PASS -> 1.0;
                case WARN -> 0.6;
                case UNKNOWN -> 0.5;
                case FAIL -> 0.0;
            };
            weighted += weight * factor;
        }
        return totalWeight == 0 ? 0 : weighted / totalWeight * 100;
    }

    private double round(double value) {
        return BigDecimal.valueOf(value).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }

    private String explanation(List<AssetCheckView.Item> items, String verdict) {
        StringBuilder sb = new StringBuilder();
        sb.append(switch (verdict) {
            case "APPROVED" -> "Все жёсткие гейты пройдены. ";
            case "REJECTED" -> "Есть не пройденные жёсткие гейты. ";
            default -> "Бумага прошла гейты, но есть замечания. ";
        });
        for (AssetCheckView.Item item : items) {
            if (item.status() == CheckStatus.FAIL || item.status() == CheckStatus.WARN) {
                sb.append(item.code()).append(": ").append(item.value()).append(" (порог ").append(item.threshold()).append("). ");
            }
        }
        return sb.toString().trim();
    }
}
