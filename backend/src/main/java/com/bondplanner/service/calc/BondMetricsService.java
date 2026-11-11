package com.bondplanner.service.calc;

import com.bondplanner.domain.entity.BondCashflow;
import com.bondplanner.domain.model.FlowType;
import com.bondplanner.domain.model.OfferTreatment;
import com.bondplanner.service.cashflow.CashFlowEngine;
import com.bondplanner.service.cashflow.DatedCashFlow;
import com.bondplanner.service.universe.BondCandidate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Расчёт доходности и дюрации конкретной бумаги на дату расчёта.
 */
@Service
@RequiredArgsConstructor
public class BondMetricsService {

    private final CashFlowEngine cashFlowEngine;

    public record BondMetrics(
            BigDecimal cleanPricePct,
            BigDecimal nkd,
            BigDecimal dirtyPriceNoFee,
            BigDecimal dirtyPriceWithFee,
            BigDecimal ytmPct,
            BigDecimal macaulayYears,
            BigDecimal modifiedDurationYears,
            LocalDate effectiveMaturity,
            List<DatedCashFlow> bondFlows) {

        public BigDecimal lotPrice(BigDecimal commissionPct, int lotSize) {
            BigDecimal fee = commissionPct == null ? BigDecimal.ZERO
                    : commissionPct.divide(BigDecimal.valueOf(100), MathContext.DECIMAL64);
            return dirtyPriceNoFee
                    .multiply(BigDecimal.valueOf(lotSize))
                    .multiply(BigDecimal.ONE.add(fee));
        }
    }

    public BondMetrics metrics(BondCandidate candidate,
                               OfferTreatment treatment,
                               OfferTreatment autoDefault,
                               LocalDate settlement,
                               BigDecimal commissionPct) {
        if (!candidate.hasQuote()) {
            return null;
        }
        LocalDate effective = candidate.effectiveMaturity(treatment, autoDefault);
        if (effective == null || !effective.isAfter(settlement)) {
            return null;
        }
        BigDecimal face = candidate.faceValue();
        BigDecimal clean = candidate.cleanPricePct();
        BigDecimal nkd = candidate.nkd();
        BigDecimal dirtyNoFee = clean.multiply(face).divide(BigDecimal.valueOf(100), MathContext.DECIMAL64).add(nkd);
        BigDecimal feeMultiplier = BigDecimal.ONE.add(
                (commissionPct == null ? BigDecimal.ZERO : commissionPct)
                        .divide(BigDecimal.valueOf(100), MathContext.DECIMAL64));
        BigDecimal dirtyWithFee = dirtyNoFee.multiply(feeMultiplier);

        List<DatedCashFlow> flows = bondFlows(candidate, effective, settlement);
        BigDecimal ytm = null;
        BigDecimal macaulay = null;
        BigDecimal modified = null;
        if (!flows.isEmpty()) {
            CashFlowEngine.YieldResult result = cashFlowEngine.yield(dirtyWithFee, flows, settlement);
            if (result != null) {
                ytm = result.ytmPct();
                macaulay = result.macaulayYears();
                modified = result.modifiedYears();
            }
        }
        if (ytm == null && candidate.quote() != null) {
            ytm = candidate.quote().getYtm();
            modified = candidate.quote().getDurationYears();
        }
        return new BondMetrics(clean, nkd, dirtyNoFee, dirtyWithFee, ytm, macaulay, modified, effective, flows);
    }

    /**
     * Денежные потоки на одну бумагу до эффективной даты погашения:
     * купоны, амортизация и остаток номинала в дату погашения/оферты.
     */
    public List<DatedCashFlow> bondFlows(BondCandidate candidate, LocalDate effectiveMaturity, LocalDate settlement) {
        List<DatedCashFlow> flows = new ArrayList<>();
        BigDecimal amortized = BigDecimal.ZERO;
        for (BondCashflow flow : candidate.cashflows()) {
            LocalDate date = flow.getFlowDate();
            BigDecimal amount = flow.getAmountPerBond();
            if (date == null || amount == null || date.isAfter(effectiveMaturity) || !date.isAfter(settlement)) {
                continue;
            }
            if (flow.getFlowType() == FlowType.COUPON) {
                flows.add(DatedCashFlow.coupon(date, amount));
            } else if (flow.getFlowType() == FlowType.AMORTIZATION) {
                if (date.isBefore(effectiveMaturity)) {
                    flows.add(DatedCashFlow.amortization(date, amount));
                    amortized = amortized.add(amount);
                }
            }
        }
        BigDecimal redemption = candidate.faceValue().subtract(amortized).max(BigDecimal.ZERO);
        if (redemption.signum() > 0) {
            flows.add(DatedCashFlow.maturity(effectiveMaturity, redemption));
        }
        flows.sort(Comparator.comparing(DatedCashFlow::date));
        return flows;
    }

    /** Суммарный купонный доход на одну бумагу в интервале [from, to]. */
    public BigDecimal couponIncome(BondCandidate candidate, LocalDate from, LocalDate to) {
        BigDecimal sum = BigDecimal.ZERO;
        for (BondCashflow flow : candidate.coupons()) {
            if (flow.getFlowDate() != null && flow.getAmountPerBond() != null
                    && !flow.getFlowDate().isBefore(from) && !flow.getFlowDate().isAfter(to)) {
                sum = sum.add(flow.getAmountPerBond());
            }
        }
        return sum;
    }
}
