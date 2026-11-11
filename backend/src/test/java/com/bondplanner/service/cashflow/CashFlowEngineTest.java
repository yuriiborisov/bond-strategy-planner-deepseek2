package com.bondplanner.service.cashflow;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CashFlowEngineTest {

    private final CashFlowEngine engine = new CashFlowEngine();
    private final LocalDate settlement = LocalDate.of(2026, 1, 1);

    @Test
    void ytmOfParBondEqualsCouponRate() {
        List<DatedCashFlow> flows = List.of(
                DatedCashFlow.coupon(LocalDate.of(2027, 1, 1), BigDecimal.valueOf(100)),
                DatedCashFlow.maturity(LocalDate.of(2027, 1, 1), BigDecimal.valueOf(1000)));
        CashFlowEngine.YieldResult result = engine.yield(BigDecimal.valueOf(1000), flows, settlement);
        assertThat(result).isNotNull();
        assertThat(result.ytmPct().doubleValue()).isBetween(9.9, 10.1);
        assertThat(result.modifiedYears().doubleValue()).isBetween(0.85, 0.95);
    }

    @Test
    void ytmOfDiscountedBondIsHigherThanCoupon() {
        List<DatedCashFlow> flows = List.of(
                DatedCashFlow.coupon(LocalDate.of(2027, 1, 1), BigDecimal.valueOf(100)),
                DatedCashFlow.maturity(LocalDate.of(2027, 1, 1), BigDecimal.valueOf(1000)));
        CashFlowEngine.YieldResult result = engine.yield(BigDecimal.valueOf(950), flows, settlement);
        assertThat(result).isNotNull();
        assertThat(result.ytmPct().doubleValue()).isGreaterThan(15.0);
    }

    @Test
    void returnsNullForEmptyFlows() {
        assertThat(engine.yield(BigDecimal.valueOf(1000), List.of(), settlement)).isNull();
    }

    @Test
    void durationIsWeightedAverageOfTimes() {
        List<DatedCashFlow> flows = List.of(
                DatedCashFlow.coupon(LocalDate.of(2027, 1, 1), BigDecimal.valueOf(100)),
                DatedCashFlow.maturity(LocalDate.of(2028, 1, 1), BigDecimal.valueOf(100)),
                DatedCashFlow.maturity(LocalDate.of(2028, 1, 1), BigDecimal.valueOf(1000)));
        CashFlowEngine.YieldResult result = engine.yield(BigDecimal.valueOf(1000), flows, settlement);
        assertThat(result).isNotNull();
        assertThat(result.macaulayYears().doubleValue()).isBetween(1.8, 2.0);
        assertThat(result.modifiedYears().doubleValue()).isLessThan(result.macaulayYears().doubleValue());
    }

    @Test
    void pmtForZeroRateIsSimpleDivision() {
        BigDecimal pmt = engine.pmt(BigDecimal.valueOf(1_000_000), BigDecimal.ZERO, BigDecimal.ZERO, 10);
        assertThat(pmt).isEqualByComparingTo("100000.00");
    }

    @Test
    void pmtForTwelvePercentYear() {
        BigDecimal pmt = engine.pmt(BigDecimal.valueOf(1_000_000), BigDecimal.ZERO,
                BigDecimal.valueOf(12), 12);
        assertThat(pmt.doubleValue()).isBetween(78800.0, 79000.0);
    }

    @Test
    void pmtConsidersStartCapital() {
        BigDecimal withCapital = engine.pmt(BigDecimal.valueOf(1_000_000), BigDecimal.valueOf(500_000),
                BigDecimal.valueOf(12), 12);
        BigDecimal withoutCapital = engine.pmt(BigDecimal.valueOf(1_000_000), BigDecimal.ZERO,
                BigDecimal.valueOf(12), 12);
        assertThat(withCapital).isLessThan(withoutCapital);
    }

    @Test
    void xirrOfSimpleInvestment() {
        BigDecimal xirr = engine.xirr(List.of(
                new DatedCashFlow(settlement, DatedCashFlow.CashFlowKind.BUY, BigDecimal.valueOf(-1000)),
                new DatedCashFlow(settlement.plusDays(365), DatedCashFlow.CashFlowKind.MATURITY,
                        BigDecimal.valueOf(1100))));
        assertThat(xirr).isNotNull();
        assertThat(xirr.doubleValue()).isBetween(9.5, 10.5);
    }

    @Test
    void xirrReturnsNullWithoutSignChange() {
        BigDecimal xirr = engine.xirr(List.of(
                new DatedCashFlow(settlement, DatedCashFlow.CashFlowKind.BUY, BigDecimal.valueOf(-1000)),
                new DatedCashFlow(settlement.plusDays(365), DatedCashFlow.CashFlowKind.COUPON,
                        BigDecimal.valueOf(-100))));
        assertThat(xirr).isNull();
    }

    @Test
    void cagrCalculation() {
        BigDecimal cagr = engine.cagr(BigDecimal.valueOf(1000), BigDecimal.valueOf(1210), 2);
        assertThat(cagr.doubleValue()).isBetween(9.9, 10.1);
    }

    @Test
    void dirtyPriceIncludesNkdAndCommission() {
        BigDecimal dirty = engine.dirtyPrice(BigDecimal.valueOf(95), BigDecimal.valueOf(1000),
                BigDecimal.valueOf(15), BigDecimal.valueOf(0.05));
        assertThat(dirty).isEqualByComparingTo("965.475");
    }

    @Test
    void accruedInterestProRata() {
        BigDecimal nkd = engine.accruedInterest(BigDecimal.valueOf(50),
                settlement.minusDays(45), settlement, 90);
        assertThat(nkd).isEqualByComparingTo("25.000000");
    }
}
