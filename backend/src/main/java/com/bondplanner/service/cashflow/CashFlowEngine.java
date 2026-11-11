package com.bondplanner.service.cashflow;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;

/**
 * Приведение денежных потоков: YTM, дюрация, XIRR, PMT, CAGR.
 *
 * <p>Доходность решается из уравнения dirtyPrice = Σ CF_i / (1 + y)^(t_i), t_i в годах (365 дней).
 * Newton-Raphson с fallback на бисекцию; при некорректных потоках возвращается {@code null}.</p>
 */
@Service
public class CashFlowEngine {

    private static final int MAX_ITERATIONS = 100;
    private static final double TOLERANCE = 1e-8;

    public record YieldResult(BigDecimal ytmPct, BigDecimal macaulayYears, BigDecimal modifiedYears) {
    }

    /** Грязная цена одной бумаги: clean% от номинала + НКД + комиссия. */
    public BigDecimal dirtyPrice(BigDecimal cleanPricePct, BigDecimal faceValue, BigDecimal nkd,
                                 BigDecimal commissionPct) {
        if (cleanPricePct == null || faceValue == null) {
            return null;
        }
        BigDecimal clean = cleanPricePct.multiply(faceValue).divide(BigDecimal.valueOf(100), MathContext.DECIMAL64);
        BigDecimal commission = clean.multiply(nz(commissionPct)).divide(BigDecimal.valueOf(100), MathContext.DECIMAL64);
        return clean.add(nz(nkd)).add(commission);
    }

    public BigDecimal accruedInterest(BigDecimal couponValue, LocalDate previousCouponDate,
                                      LocalDate settlement, int couponPeriodDays) {
        if (couponValue == null || previousCouponDate == null || couponPeriodDays <= 0) {
            return BigDecimal.ZERO;
        }
        long days = ChronoUnit.DAYS.between(previousCouponDate, settlement);
        if (days <= 0) {
            return BigDecimal.ZERO;
        }
        return couponValue.multiply(BigDecimal.valueOf(days))
                .divide(BigDecimal.valueOf(couponPeriodDays), 6, RoundingMode.HALF_UP);
    }

    /**
     * YTM из денежных потоков на одну бумагу. {@code cashFlows} не должны содержать покупку.
     */
    public YieldResult yield(BigDecimal dirtyPrice, List<DatedCashFlow> cashFlows, LocalDate settlement) {
        if (dirtyPrice == null || dirtyPrice.signum() <= 0 || cashFlows == null || cashFlows.isEmpty()) {
            return null;
        }
        List<DatedCashFlow> flows = cashFlows.stream()
                .filter(f -> f.amount() != null)
                .filter(f -> f.kind() != DatedCashFlow.CashFlowKind.BUY
                        && f.kind() != DatedCashFlow.CashFlowKind.TAX
                        && f.kind() != DatedCashFlow.CashFlowKind.COMMISSION)
                .filter(f -> f.date().isAfter(settlement))
                .sorted(Comparator.comparing(DatedCashFlow::date))
                .toList();
        if (flows.isEmpty()) {
            return null;
        }
        double[] t = new double[flows.size()];
        double[] cf = new double[flows.size()];
        for (int i = 0; i < flows.size(); i++) {
            t[i] = ChronoUnit.DAYS.between(settlement, flows.get(i).date()) / 365.0;
            cf[i] = flows.get(i).amount().doubleValue();
        }
        double price = dirtyPrice.doubleValue();
        double y = solveYield(price, t, cf);
        if (!Double.isFinite(y)) {
            return null;
        }
        double macaulay = 0;
        double pvSum = 0;
        for (int i = 0; i < t.length; i++) {
            double pv = cf[i] / Math.pow(1 + y, t[i]);
            macaulay += t[i] * pv;
            pvSum += pv;
        }
        double mac = pvSum == 0 ? 0 : macaulay / pvSum;
        double modified = mac / (1 + y);
        return new YieldResult(
                BigDecimal.valueOf(y * 100).setScale(4, RoundingMode.HALF_UP),
                BigDecimal.valueOf(mac).setScale(4, RoundingMode.HALF_UP),
                BigDecimal.valueOf(modified).setScale(4, RoundingMode.HALF_UP));
    }

    private double solveYield(double price, double[] t, double[] cf) {
        double y = couponApproximation(price, t, cf);
        for (int i = 0; i < MAX_ITERATIONS; i++) {
            double f = 0;
            double df = 0;
            double base = 1 + y;
            if (base <= 0) {
                break;
            }
            for (int k = 0; k < t.length; k++) {
                double disc = Math.pow(base, t[k]);
                f += cf[k] / disc - (k == 0 ? price : 0);
                df -= t[k] * cf[k] / (disc * base);
            }
            if (Math.abs(f) < 1e-7) {
                return y;
            }
            if (df == 0 || !Double.isFinite(df)) {
                break;
            }
            double next = y - f / df;
            if (!Double.isFinite(next) || next <= -0.999) {
                break;
            }
            if (Math.abs(next - y) < 1e-10) {
                return next;
            }
            y = next;
        }
        return bisection(price, t, cf);
    }

    private double couponApproximation(double price, double[] t, double[] cf) {
        double total = 0;
        for (double v : cf) {
            total += v;
        }
        if (total <= 0) {
            return 0.1;
        }
        double years = t.length == 0 ? 1 : Math.max(t[t.length - 1], 0.01);
        double approx = (total / price - 1) / years;
        if (!Double.isFinite(approx)) {
            return 0.1;
        }
        return Math.max(-0.5, Math.min(approx, 2.0));
    }

    private double bisection(double price, double[] t, double[] cf) {
        double lo = -0.99;
        double hi = 10.0;
        double flo = npv(lo, price, t, cf);
        double fhi = npv(hi, price, t, cf);
        if (!Double.isFinite(flo) || !Double.isFinite(fhi) || flo * fhi > 0) {
            return Double.NaN;
        }
        for (int i = 0; i < 200; i++) {
            double mid = (lo + hi) / 2;
            double fm = npv(mid, price, t, cf);
            if (Math.abs(fm) < 1e-9 || (hi - lo) < 1e-12) {
                return mid;
            }
            if (flo * fm <= 0) {
                hi = mid;
                fhi = fm;
            } else {
                lo = mid;
                flo = fm;
            }
        }
        return (lo + hi) / 2;
    }

    private double npv(double y, double price, double[] t, double[] cf) {
        double sum = -price;
        double base = 1 + y;
        if (base <= 0) {
            return Double.NaN;
        }
        for (int i = 0; i < t.length; i++) {
            sum += cf[i] / Math.pow(base, t[i]);
        }
        return sum;
    }

    /** XIRR по произвольному денежному потоку (включая отрицательные инвестиции). */
    public BigDecimal xirr(List<DatedCashFlow> flows) {
        if (flows == null || flows.size() < 2) {
            return null;
        }
        LocalDate start = flows.stream().map(DatedCashFlow::date).min(LocalDate::compareTo).orElseThrow();
        List<DatedCashFlow> sorted = flows.stream().sorted(Comparator.comparing(DatedCashFlow::date)).toList();
        boolean hasPos = sorted.stream().anyMatch(f -> f.amount().signum() > 0);
        boolean hasNeg = sorted.stream().anyMatch(f -> f.amount().signum() < 0);
        if (!hasPos || !hasNeg) {
            return null;
        }
        double[] t = new double[sorted.size()];
        double[] cf = new double[sorted.size()];
        for (int i = 0; i < sorted.size(); i++) {
            t[i] = ChronoUnit.DAYS.between(start, sorted.get(i).date()) / 365.0;
            cf[i] = sorted.get(i).amount().doubleValue();
        }
        double lo = -0.99;
        double hi = 10.0;
        double flo = xnpv(lo, t, cf);
        double fhi = xnpv(hi, t, cf);
        if (!Double.isFinite(flo) || !Double.isFinite(fhi) || flo * fhi > 0) {
            return null;
        }
        for (int i = 0; i < 200; i++) {
            double mid = (lo + hi) / 2;
            double fm = xnpv(mid, t, cf);
            if (Math.abs(fm) < 1e-7 || (hi - lo) < 1e-12) {
                return BigDecimal.valueOf(mid * 100).setScale(4, RoundingMode.HALF_UP);
            }
            if (flo * fm <= 0) {
                hi = mid;
            } else {
                lo = mid;
                flo = fm;
            }
        }
        return BigDecimal.valueOf((lo + hi) / 2 * 100).setScale(4, RoundingMode.HALF_UP);
    }

    private double xnpv(double rate, double[] t, double[] cf) {
        double sum = 0;
        double base = 1 + rate;
        if (base <= 0) {
            return Double.NaN;
        }
        for (int i = 0; i < t.length; i++) {
            sum += cf[i] / Math.pow(base, t[i]);
        }
        return sum;
    }

    /**
     * Аннуитетный платёж: PMT = (FV - PV*(1+r)^n) * r / ((1+r)^n - 1).
     */
    public BigDecimal pmt(BigDecimal targetAmount, BigDecimal startCapital, BigDecimal annualRatePct, int months) {
        if (months <= 0) {
            return BigDecimal.ZERO;
        }
        double fv = targetAmount.doubleValue();
        double pv = startCapital.doubleValue();
        double monthly = annualRatePct.doubleValue() / 100 / 12;
        double pmt;
        if (Math.abs(monthly) < 1e-12) {
            pmt = (fv - pv) / months;
        } else {
            double growth = Math.pow(1 + monthly, months);
            pmt = (fv - pv * growth) * monthly / (growth - 1);
        }
        return BigDecimal.valueOf(Math.max(pmt, 0)).setScale(2, RoundingMode.HALF_UP);
    }

    /** CAGR = (final / initial)^(1/years) - 1, в процентах. */
    public BigDecimal cagr(BigDecimal initial, BigDecimal fin, double years) {
        if (initial == null || fin == null || initial.signum() <= 0 || years <= 0) {
            return null;
        }
        double ratio = fin.doubleValue() / initial.doubleValue();
        if (ratio <= 0) {
            return null;
        }
        double value = Math.pow(ratio, 1.0 / years) - 1;
        return BigDecimal.valueOf(value * 100).setScale(4, RoundingMode.HALF_UP);
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
