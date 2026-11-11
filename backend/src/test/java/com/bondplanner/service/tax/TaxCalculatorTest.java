package com.bondplanner.service.tax;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class TaxCalculatorTest {

    private final TaxCalculator calculator = new TaxCalculator();

    @Test
    void flatProfileIsThirteenPercent() {
        assertThat(calculator.taxOnCoupons(BigDecimal.valueOf(100_000), TaxCalculator.RU_FLAT_13,
                BigDecimal.ZERO)).isEqualByComparingTo("13000.00");
    }

    @Test
    void flatProfileIgnoresOtherIncome() {
        assertThat(calculator.taxOnCoupons(BigDecimal.valueOf(100_000), TaxCalculator.RU_FLAT_13,
                BigDecimal.valueOf(10_000_000))).isEqualByComparingTo("13000.00");
    }

    @Test
    void progressiveProfileUsesBrackets() {
        assertThat(calculator.taxOnCoupons(BigDecimal.valueOf(3_000_000),
                TaxCalculator.RU_PROGRESSIVE_2025, BigDecimal.ZERO))
                .isEqualByComparingTo("402000.00");
    }

    @Test
    void progressiveProfileAccountsForOtherIncome() {
        BigDecimal tax = calculator.taxOnCoupons(BigDecimal.valueOf(1_000_000),
                TaxCalculator.RU_PROGRESSIVE_2025, BigDecimal.valueOf(2_000_000));
        assertThat(tax).isEqualByComparingTo("142000.00");
    }

    @Test
    void zeroIncomeHasNoTax() {
        assertThat(calculator.taxOnCoupons(BigDecimal.ZERO, TaxCalculator.RU_FLAT_13, BigDecimal.ZERO))
                .isEqualByComparingTo("0");
    }

    @Test
    void unknownProfileFallsBackToFlat() {
        assertThat(calculator.normalizeProfile("SOMETHING")).isEqualTo(TaxCalculator.RU_FLAT_13);
    }
}
