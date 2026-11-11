package com.bondplanner.service.cashflow;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Денежный поток (на одну облигацию или на позицию — задаётся вызывающим кодом).
 */
public record DatedCashFlow(LocalDate date, CashFlowKind kind, BigDecimal amount) {

    public enum CashFlowKind {
        COUPON, AMORTIZATION, MATURITY, OFFER, BUY, COMMISSION, TAX, REINVEST
    }

    public static DatedCashFlow coupon(LocalDate date, BigDecimal amount) {
        return new DatedCashFlow(date, CashFlowKind.COUPON, amount);
    }

    public static DatedCashFlow maturity(LocalDate date, BigDecimal amount) {
        return new DatedCashFlow(date, CashFlowKind.MATURITY, amount);
    }

    public static DatedCashFlow amortization(LocalDate date, BigDecimal amount) {
        return new DatedCashFlow(date, CashFlowKind.AMORTIZATION, amount);
    }

    public static DatedCashFlow tax(LocalDate date, BigDecimal amount) {
        return new DatedCashFlow(date, CashFlowKind.TAX, amount.negate());
    }

    public static DatedCashFlow buy(LocalDate date, BigDecimal amount) {
        return new DatedCashFlow(date, CashFlowKind.BUY, amount.negate());
    }

    public static List<DatedCashFlow> sorted(List<DatedCashFlow> flows) {
        return flows.stream().sorted((a, b) -> a.date().compareTo(b.date())).toList();
    }
}
