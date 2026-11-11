package com.bondplanner.service.calc;

import java.math.BigDecimal;
import java.time.LocalDate;

public record BondInfo(
        String secid,
        String isin,
        String name,
        String issuer,
        LocalDate maturityDate,
        LocalDate offerDate,
        LocalDate effectiveMaturity,
        BigDecimal pricePct,
        BigDecimal nkdRub,
        BigDecimal ytmPct,
        BigDecimal durationYears,
        int lotSize,
        Integer listLevel,
        BigDecimal issueSizeRub,
        String rating,
        String ratingAgency,
        LocalDate ratingDate,
        String ratingOutlook) {
}
