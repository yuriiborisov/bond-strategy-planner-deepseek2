package com.bondplanner.util;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * Денежные и процентные вычисления. Внутри — BigDecimal, наружу — фиксированная шкала.
 */
public final class Decimals {

    public static final MathContext MC = new MathContext(18, RoundingMode.HALF_UP);
    public static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private Decimals() {
    }

    public static BigDecimal money(BigDecimal value) {
        return value == null ? null : value.setScale(2, RoundingMode.HALF_UP);
    }

    public static BigDecimal percent(BigDecimal value) {
        return value == null ? null : value.setScale(4, RoundingMode.HALF_UP);
    }

    public static BigDecimal of(double value) {
        return BigDecimal.valueOf(value);
    }

    public static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    public static BigDecimal divide(BigDecimal a, BigDecimal b) {
        if (b == null || b.signum() == 0) {
            return BigDecimal.ZERO;
        }
        return a.divide(b, MC);
    }

    public static double toDouble(BigDecimal value) {
        return value == null ? 0d : value.doubleValue();
    }

    public static BigDecimal fromDouble(double value, int scale) {
        if (!Double.isFinite(value)) {
            return null;
        }
        return BigDecimal.valueOf(value).setScale(scale, RoundingMode.HALF_UP);
    }

    public static BigDecimal clamp(BigDecimal value, BigDecimal min, BigDecimal max) {
        if (value == null) {
            return min;
        }
        if (value.compareTo(min) < 0) {
            return min;
        }
        if (value.compareTo(max) > 0) {
            return max;
        }
        return value;
    }

    public static BigDecimal fractionOf(BigDecimal part, BigDecimal total) {
        if (total == null || total.signum() == 0) {
            return BigDecimal.ZERO;
        }
        return part.divide(total, new MathContext(8, RoundingMode.HALF_UP));
    }
}
