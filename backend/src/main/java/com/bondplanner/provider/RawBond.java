package com.bondplanner.provider;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Нормализованная бумага от провайдера биржи (до сохранения в БД).
 */
public record RawBond(
        String secid,
        String boardId,
        String isin,
        String shortName,
        String issuerName,
        String bondType,
        String couponType,
        String currency,
        BigDecimal faceValue,
        BigDecimal couponValue,
        Integer couponPeriodDays,
        LocalDate maturityDate,
        LocalDate offerDate,
        Integer listLevel,
        Integer lotSize,
        BigDecimal issueSize,
        boolean amortizing,
        boolean perpetual,
        BigDecimal pricePct,
        BigDecimal nkd,
        BigDecimal ytm,
        BigDecimal ytmToOffer,
        BigDecimal durationYears,
        BigDecimal volumeRub,
        Integer numTrades) {
}
