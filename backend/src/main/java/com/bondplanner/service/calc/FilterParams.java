package com.bondplanner.service.calc;

import com.bondplanner.service.tax.TaxCalculator;

import java.math.BigDecimal;
import java.util.List;

/**
 * Общие фильтры вселенной и издержки расчёта (§3.2).
 */
public record FilterParams(
        List<String> bondTypes,
        int minListLevel,
        BigDecimal minIssueSizeRub,
        BigDecimal minAvgDailyVolumeRub,
        boolean allowFloatingCoupon,
        boolean allowAmortizing,
        BigDecimal maxPerIssuerShare,
        BigDecimal maxPerRatingShare,
        String minRating,
        List<String> ratingAgencies,
        int maxRatingAgeDays,
        boolean requireRating,
        boolean allowUnrated,
        BigDecimal minFactorScore,
        BigDecimal commissionPct,
        boolean taxOnCoupons,
        String taxProfile,
        boolean assetChecks) {

    public static FilterParams from(Params params) {
        Params filters = params.child("filters");
        Params costs = params.child("costs");
        Params enrichment = params.child("enrichment");
        return new FilterParams(
                filters.stringList("bondTypes", List.of("OFZ", "CORPORATE")),
                filters.integer("minListLevel", 2),
                filters.decimal("minIssueSizeRub", BigDecimal.valueOf(1_000_000_000L)),
                filters.decimal("minAvgDailyVolumeRub", BigDecimal.valueOf(1_000_000L)),
                filters.bool("allowFloatingCoupon", false),
                filters.bool("allowAmortizing", true),
                filters.decimal("maxPerIssuerShare", new BigDecimal("0.25")),
                filters.decimal("maxPerRatingShare", new BigDecimal("0.5")),
                filters.text("minRating", "A-"),
                filters.stringList("ratingAgencies", List.of("ACRA", "EXPERT_RA", "NKR")),
                filters.integer("maxRatingAgeDays", 180),
                filters.bool("requireRating", true),
                filters.bool("allowUnrated", false),
                filters.decimal("minFactorScore", BigDecimal.valueOf(60)),
                costs.decimal("commissionPct", new BigDecimal("0.05")),
                costs.bool("taxOnCoupons", true),
                TaxCalculator.RU_FLAT_13.equalsIgnoreCase(costs.text("taxProfile", TaxCalculator.RU_FLAT_13))
                        ? TaxCalculator.RU_FLAT_13 : TaxCalculator.RU_PROGRESSIVE_2025,
                enrichment.bool("assetChecks", true));
    }
}
