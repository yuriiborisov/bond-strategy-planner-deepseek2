package com.bondplanner.service.universe;

import com.bondplanner.domain.entity.Bond;
import com.bondplanner.domain.entity.BondCashflow;
import com.bondplanner.domain.entity.BondQuote;
import com.bondplanner.domain.model.FlowType;
import com.bondplanner.domain.model.OfferTreatment;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Бумага вместе с последней котировкой и купонным календарём — единица вселенной расчёта.
 */
public record BondCandidate(Bond bond, BondQuote quote, List<BondCashflow> cashflows) {

    public BondCandidate {
        cashflows = cashflows == null ? List.of() : List.copyOf(cashflows);
    }

    public Long bondId() {
        return bond.getId();
    }

    public String secid() {
        return bond.getSecid();
    }

    public boolean hasQuote() {
        return quote != null && quote.getPricePct() != null && quote.getPricePct().signum() > 0;
    }

    public BigDecimal cleanPricePct() {
        return quote == null ? null : quote.getPricePct();
    }

    public BigDecimal nkd() {
        return quote == null || quote.getNkd() == null ? BigDecimal.ZERO : quote.getNkd();
    }

    public BigDecimal volumeRub() {
        return quote == null || quote.getVolumeRub() == null ? BigDecimal.ZERO : quote.getVolumeRub();
    }

    public BigDecimal faceValue() {
        return bond.getFaceValue() == null ? BigDecimal.valueOf(1000) : bond.getFaceValue();
    }

    public int lotSize() {
        return Math.max(1, bond.getLotSize());
    }

    public boolean hasFixedCoupon() {
        return bond.getCouponType() == com.bondplanner.domain.model.CouponType.FIXED;
    }

    public LocalDate maturityDate() {
        return bond.getMaturityDate();
    }

    public List<BondCashflow> coupons() {
        return cashflows.stream().filter(f -> f.getFlowType() == FlowType.COUPON).toList();
    }

    public List<BondCashflow> amortizations() {
        return cashflows.stream().filter(f -> f.getFlowType() == FlowType.AMORTIZATION).toList();
    }

    public LocalDate effectiveMaturity(OfferTreatment treatment, OfferTreatment autoDefault) {
        OfferTreatment mode = treatment == null || treatment == OfferTreatment.AUTO ? autoDefault : treatment;
        LocalDate maturity = bond.getMaturityDate();
        LocalDate offer = bond.getOfferDate();
        if (mode == OfferTreatment.MATURITY && offer != null && (maturity == null || offer.isBefore(maturity))) {
            return offer;
        }
        return maturity != null ? maturity : offer;
    }

    public boolean hasOfferBefore(LocalDate date) {
        return bond.getOfferDate() != null && bond.getOfferDate().isBefore(date);
    }

    /** Купонный доход за один купон (на бумагу) — из календаря или из couponValue. */
    public BigDecimal couponAmountPerPeriod() {
        List<BondCashflow> coupons = coupons();
        if (!coupons.isEmpty()) {
            BigDecimal last = coupons.get(coupons.size() - 1).getAmountPerBond();
            if (last != null) {
                return last;
            }
        }
        return bond.getCouponValue();
    }
}
