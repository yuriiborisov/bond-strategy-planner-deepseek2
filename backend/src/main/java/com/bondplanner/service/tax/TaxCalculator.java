package com.bondplanner.service.tax;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Налоги с купонов (решение D-1).
 *
 * <p>По умолчанию — плоская ставка 13% ({@code RU_FLAT_13}). Профиль
 * {@code RU_PROGRESSIVE_2025} применяет прогрессивную шкалу к совокупному
 * годовому доходу; НКД и сальдирование убытков в v1 не моделируются.</p>
 */
@Service
public class TaxCalculator {

    public static final String RU_FLAT_13 = "RU_FLAT_13";
    public static final String RU_PROGRESSIVE_2025 = "RU_PROGRESSIVE_2025";

    private static final BigDecimal[][] PROGRESSIVE_BRACKETS = {
            {BigDecimal.valueOf(2_400_000), BigDecimal.valueOf(0.13)},
            {BigDecimal.valueOf(5_000_000), BigDecimal.valueOf(0.15)},
            {BigDecimal.valueOf(20_000_000), BigDecimal.valueOf(0.18)},
            {BigDecimal.valueOf(50_000_000), BigDecimal.valueOf(0.20)},
            {new BigDecimal("1E+18"), BigDecimal.valueOf(0.22)},
    };

    public String normalizeProfile(String profile) {
        if (profile == null || profile.isBlank()) {
            return RU_FLAT_13;
        }
        return RU_PROGRESSIVE_2025.equalsIgnoreCase(profile.trim()) ? RU_PROGRESSIVE_2025 : RU_FLAT_13;
    }

    /**
     * Налог с купонного дохода за год. Для прогрессивной шкалы {@code otherAnnualIncome}
     * — прочий годовой доход физлица (например, зарплата), чтобы корректно попасть в ставку.
     */
    public BigDecimal taxOnCoupons(BigDecimal couponIncome, String profile, BigDecimal otherAnnualIncome) {
        if (couponIncome == null || couponIncome.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        if (normalizeProfile(profile).equals(RU_FLAT_13)) {
            return couponIncome.multiply(BigDecimal.valueOf(0.13)).setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal other = otherAnnualIncome == null ? BigDecimal.ZERO : otherAnnualIncome;
        BigDecimal taxTotal = progressiveTax(other.add(couponIncome));
        BigDecimal taxOther = progressiveTax(other);
        return taxTotal.subtract(taxOther).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal marginalRate(BigDecimal annualIncome) {
        BigDecimal income = annualIncome == null ? BigDecimal.ZERO : annualIncome;
        BigDecimal lower = BigDecimal.ZERO;
        for (BigDecimal[] bracket : PROGRESSIVE_BRACKETS) {
            if (income.compareTo(bracket[0]) <= 0) {
                return bracket[1];
            }
            lower = bracket[0];
        }
        return BigDecimal.valueOf(0.22);
    }

    private BigDecimal progressiveTax(BigDecimal income) {
        if (income == null || income.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal tax = BigDecimal.ZERO;
        BigDecimal lower = BigDecimal.ZERO;
        for (BigDecimal[] bracket : PROGRESSIVE_BRACKETS) {
            BigDecimal upper = bracket[0];
            BigDecimal rate = bracket[1];
            if (income.compareTo(lower) <= 0) {
                break;
            }
            BigDecimal taxable = income.min(upper).subtract(lower);
            if (taxable.signum() > 0) {
                tax = tax.add(taxable.multiply(rate));
            }
            lower = upper;
        }
        return tax.setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal effectiveRate(BigDecimal couponIncome, String profile, BigDecimal otherAnnualIncome) {
        if (couponIncome == null || couponIncome.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        return taxOnCoupons(couponIncome, profile, otherAnnualIncome)
                .divide(couponIncome, 6, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);
    }

    public List<String> describedProfiles() {
        return List.of(RU_FLAT_13, RU_PROGRESSIVE_2025);
    }
}
