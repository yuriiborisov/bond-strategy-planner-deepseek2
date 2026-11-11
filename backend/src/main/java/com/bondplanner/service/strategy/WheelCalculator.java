package com.bondplanner.service.strategy;

import com.bondplanner.api.error.ApiException;
import com.bondplanner.domain.model.OfferTreatment;
import com.bondplanner.service.calc.BondMetricsService;
import com.bondplanner.service.calc.CalculationResult;
import com.bondplanner.service.calc.CashFlowPoint;
import com.bondplanner.service.calc.FilterParams;
import com.bondplanner.service.calc.Params;
import com.bondplanner.service.calc.PositionResult;
import com.bondplanner.service.calc.WarningItem;
import com.bondplanner.service.cashflow.CashFlowEngine;
import com.bondplanner.service.cashflow.DatedCashFlow;
import com.bondplanner.service.rating.RatingView;
import com.bondplanner.service.tax.TaxCalculator;
import com.bondplanner.service.universe.BondCandidate;
import com.bondplanner.util.Decimals;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;

/**
 * WHEEL — «Колесо»: реинвестирование купонов, сравнение сценариев и капитализация (§3.6).
 */
@Component
@RequiredArgsConstructor
public class WheelCalculator implements StrategyCalculator {

    private final StrategySupport support;
    private final LadderCalculator ladderCalculator;
    private final BulletCalculator bulletCalculator;

    @Override
    public String code() {
        return "WHEEL";
    }

    @Override
    public CalculationResult calculate(CalculationContext context, Params params) {
        LocalDate today = support.today();
        FilterParams filters = FilterParams.from(params);
        String baseStrategy = params.text("baseStrategy", "LADDER").toUpperCase();
        int horizonYears = Math.min(30, Math.max(1, params.integer("horizonYears", 5)));
        String reinvestRule = params.text("reinvestRule", "BEST_SCORE").toUpperCase();
        BigDecimal minCash = Decimals.nz(params.decimal("minCashToReinvestRub", BigDecimal.ZERO));
        BigDecimal fixedRate = params.decimal("reinvestRatePct", null);
        String taxProfile = params.text("taxProfile", filters.taxProfile());
        BigDecimal annualOtherIncome = params.decimal("annualOtherIncome", filters.annualOtherIncome());

        Params baseParams = baseParams(params, baseStrategy, horizonYears);
        CalculationResult base = "BULLET".equals(baseStrategy)
                ? bulletCalculator.calculate(context, baseParams)
                : ladderCalculator.calculate(context, baseParams);
        if (base.getPositions().isEmpty()) {
            throw ApiException.noCandidates("Базовая стратегия не сформировала позиции",
                    List.of("Изменить параметры базовой стратегии", "Ослабить фильтры"));
        }

        LocalDate horizonEnd = today.plusYears(horizonYears);
        Map<String, BondCandidate> bySecid = new HashMap<>();
        for (BondCandidate candidate : context.snapshot().bonds()) {
            bySecid.putIfAbsent(candidate.secid(), candidate);
        }

        Map<String, BigDecimal> ytmBySecid = new HashMap<>();
        Map<String, BigDecimal> durationBySecid = new HashMap<>();
        List<PositionResult> positions = base.getPositions();
        for (PositionResult position : positions) {
            ytmBySecid.put(position.getBond().secid(), position.getYtmPct());
            durationBySecid.put(position.getBond().secid(), position.getDurationYears());
        }

        BigDecimal invested = support.marketValue(positions);
        List<Map<String, Object>> reinvestments = new ArrayList<>();
        Map<String, ScenarioResult> scenarios = new LinkedHashMap<>();
        List<Map<String, Object>> series = new ArrayList<>();

        for (String scenario : List.of("REINVEST", "CASH_OUT", "CASH_BUFFER")) {
            ScenarioResult result = simulate(scenario, context, positions, bySecid, filters, taxProfile,
                    reinvestRule, minCash, fixedRate, annualOtherIncome,
                    today, horizonEnd, horizonYears, invested, reinvestments);
            scenarios.put(scenario, result);
            for (CapitalPoint point : result.capitalization()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("date", point.date());
                row.put("year", point.year());
                row.put("scenario", scenario);
                row.put("valueRub", Decimals.money(point.value()));
                series.add(row);
            }
        }
        series.sort(Comparator.comparing((Map<String, Object> m) -> (LocalDate) m.get("date"))
                .thenComparing(m -> (String) m.get("scenario")));

        ScenarioResult reinvest = scenarios.get("REINVEST");
        ScenarioResult cashOut = scenarios.get("CASH_OUT");
        ScenarioResult cashBuffer = scenarios.get("CASH_BUFFER");

        List<Map<String, Object>> comparison = new ArrayList<>();
        for (String name : List.of("REINVEST", "CASH_OUT", "CASH_BUFFER")) {
            ScenarioResult result = scenarios.get(name);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("scenario", name);
            row.put("finalCapitalRub", Decimals.money(result.finalCapital()));
            row.put("xirrPct", result.xirrPct());
            row.put("totalCouponsGrossRub", Decimals.money(result.totalCoupons()));
            row.put("totalTaxRub", Decimals.money(result.totalTax()));
            row.put("totalReinvestedRub", Decimals.money(result.totalReinvested()));
            row.put("cashResidualRub", Decimals.money(result.cash()));
            comparison.add(row);
        }

        BigDecimal totalContributions = Decimals.money(invested);
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("initialAmountRub", totalContributions);
        summary.put("finalCapitalRub", Decimals.money(reinvest.finalCapital()));
        summary.put("totalContributionsRub", totalContributions);
        summary.put("totalCouponsGrossRub", Decimals.money(reinvest.totalCoupons()));
        summary.put("totalTaxRub", Decimals.money(reinvest.totalTax()));
        summary.put("totalReinvestedRub", Decimals.money(reinvest.totalReinvested()));
        summary.put("cashResidualRub", Decimals.money(reinvest.cash()));
        summary.put("xirrPct", reinvest.xirrPct());
        summary.put("cagrPct", reinvest.cagrPct());
        summary.put("effectiveAnnualYieldPct", reinvest.xirrPct());
        summary.put("reinvestmentCount", reinvest.reinvestments());
        summary.put("avgReinvestmentDelayDays", reinvest.avgReinvestmentDelayDays());
        summary.put("horizonYears", horizonYears);
        summary.put("scenarioDeltaVsCashOutRub", Decimals.money(reinvest.finalCapital().subtract(cashOut.finalCapital())));

        List<WarningItem> warnings = new ArrayList<>(base.getWarnings());
        warnings.addAll(support.dataFreshnessWarning(context.snapshot().dataFreshness()));
        if (reinvest.totalCoupons().signum() == 0) {
            warnings.add(WarningItem.warn("NO_COUPONS", "По базовому портфелю нет купонных выплат в горизонте — эффект реинвеста отсутствует"));
        }
        if (reinvest.finalCapital().compareTo(cashOut.finalCapital()) < 0) {
            warnings.add(WarningItem.warn("REINVEST_UNDERPERFORMS",
                    "Сценарий реинвестирования уступает выводу купонов — проверьте допущения"));
        }
        if (cashBuffer.finalCapital().compareTo(cashOut.finalCapital()) != 0) {
            warnings.add(WarningItem.info("CASH_SCENARIOS_DIFF",
                    "Сценарии CASH_OUT и CASH_BUFFER отличаются из-за момента вывода средств"));
        }

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("comparison", comparison);
        details.put("capitalizationSeries", series);
        details.put("reinvestments", reinvestments);
        details.put("baseStrategy", baseStrategy);
        details.put("baseSummary", base.getSummary());
        details.put("horizonYears", horizonYears);
        details.put("reinvestRule", reinvestRule);
        details.put("taxProfile", taxProfile);
        details.put("annualOtherIncomeRub", Decimals.money(annualOtherIncome));
        details.put("minCashToReinvestRub", Decimals.money(minCash));
        details.put("reinvestRatePct", fixedRate);
        details.put("horizonEnd", horizonEnd);
        details.put("assumptions", Map.of(
                "valuation", "Остаточная стоимость бумаг к горизонту — по цене покупки; купоны учтены отдельно",
                "tax", "НДФЛ с купонов по профилю " + taxProfile,
                "commission", filters.commissionPct() + "% на каждый оборот"));

        List<CashFlowPoint> schedule = base.getCashFlowSchedule() == null ? List.of() : base.getCashFlowSchedule();
        return CalculationResult.builder()
                .strategy(code())
                .exchange(context.snapshot().exchangeCode())
                .snapshotTs(context.snapshot().snapshotTs())
                .dataFreshness(context.snapshot().dataFreshness())
                .summary(summary)
                .positions(positions)
                .cashFlowSchedule(schedule)
                .warnings(warnings)
                .assumptions(support.assumptions(filters, fixedRate))
                .details(details)
                .build();
    }

    private Params baseParams(Params params, String baseStrategy, int horizonYears) {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        var baseNode = params.node().get("baseParams");
        if (baseNode != null && baseNode.isObject()) {
            node.setAll((ObjectNode) baseNode);
        }
        if (!node.has("filters") && params.node().has("filters")) {
            node.set("filters", params.node().get("filters"));
        }
        if (!node.has("costs") && params.node().has("costs")) {
            node.set("costs", params.node().get("costs"));
        }
        if (!node.has("enrichment") && params.node().has("enrichment")) {
            node.set("enrichment", params.node().get("enrichment"));
        }
        if ("BULLET".equals(baseStrategy)) {
            if (!node.has("targetAmount")) {
                node.put("targetAmount", params.decimal("amount", BigDecimal.valueOf(1_000_000)));
            }
            if (!node.has("targetDate")) {
                node.put("targetDate", support.today().plusYears(horizonYears).toString());
            }
            if (!node.has("startCapital")) {
                node.put("startCapital", params.decimal("amount", BigDecimal.valueOf(1_000_000)));
            }
        }
        return Params.of(node);
    }

    private ScenarioResult simulate(String scenario, CalculationContext context, List<PositionResult> positions,
                                    Map<String, BondCandidate> bySecid, FilterParams filters, String taxProfile,
                                    String rule, BigDecimal minCash, BigDecimal fixedRate, BigDecimal annualOtherIncome,
                                    LocalDate today, LocalDate horizonEnd, int horizonYears,
                                    BigDecimal invested, List<Map<String, Object>> reinvestments) {
        BigDecimal cash = BigDecimal.ZERO;
        BigDecimal withdrawn = BigDecimal.ZERO;
        BigDecimal book = BigDecimal.ZERO;
        BigDecimal totalCoupons = BigDecimal.ZERO;
        BigDecimal totalTax = BigDecimal.ZERO;
        BigDecimal totalReinvested = BigDecimal.ZERO;
        int reinvestCount = 0;
        long delaySum = 0;
        Map<String, Holding> holdings = new HashMap<>();
        PriorityQueue<SimEvent> queue = new PriorityQueue<>(Comparator.comparing(SimEvent::date));
        List<DatedCashFlow> external = new ArrayList<>();
        external.add(DatedCashFlow.buy(today, invested));

        for (PositionResult position : positions) {
            BondCandidate candidate = bySecid.get(position.getBond().secid());
            if (candidate == null) {
                continue;
            }
            LocalDate effective = position.getBond().effectiveMaturity() == null ? horizonEnd
                    : position.getBond().effectiveMaturity();
            BigDecimal cost = Decimals.nz(position.getAmountRub());
            holdings.put(candidate.secid() + "@" + position.getQuantity(),
                    new Holding(candidate, position.getQuantity(), cost));
            book = book.add(cost);
            for (DatedCashFlow flow : support.metricsServiceFlows(candidate, effective, today)) {
                if (flow.date().isAfter(today) && !flow.date().isAfter(horizonEnd)) {
                    queue.add(new SimEvent(flow.date(), flow.kind().name(), flow.amount(),
                            position.getQuantity(), candidate.secid()));
                }
            }
        }

        List<CapitalPoint> capitalization = new ArrayList<>();
        int nextYear = 1;
        while (true) {
            while (!queue.isEmpty() && !queue.peek().date().isAfter(horizonEnd)) {
                SimEvent event = queue.poll();
                LocalDate eventDate = event.date();
                while (nextYear <= horizonYears && !eventDate.isBefore(today.plusYears(nextYear))) {
                    capitalization.add(new CapitalPoint(nextYear, today.plusYears(nextYear),
                            cash.add(withdrawn).add(book)));
                    nextYear++;
                }
                BigDecimal gross = event.amountPerBond().multiply(BigDecimal.valueOf(event.quantity()));
                if ("COUPON".equals(event.type())) {
                    totalCoupons = totalCoupons.add(gross);
                }
                BigDecimal tax = "COUPON".equals(event.type())
                        ? new TaxCalculator().taxOnCoupons(gross, taxProfile, annualOtherIncome)
                        : BigDecimal.ZERO;
                totalTax = totalTax.add(tax);
                BigDecimal net = gross.subtract(tax);

                if ("MATURITY".equals(event.type()) || "AMORTIZATION".equals(event.type())) {
                    Holding holding = findHolding(holdings, event.secid(), event.quantity());
                    BigDecimal released = holding == null ? BigDecimal.ZERO
                            : holding.costBasis().multiply(BigDecimal.valueOf(event.quantity()))
                                    .divide(BigDecimal.valueOf(Math.max(1, holding.quantity())), 2, RoundingMode.HALF_UP);
                    if (holding != null) {
                        holding = holding.reduce(event.quantity(), released);
                        if (holding.quantity() <= 0) {
                            holdings.remove(holding.key());
                        } else {
                            holdings.put(holding.key(), holding);
                        }
                        book = book.subtract(released);
                    }
                    cash = cash.add(net);
                    book = book.max(BigDecimal.ZERO);
                } else {
                    cash = cash.add(net);
                }

                if ("CASH_OUT".equals(scenario)) {
                    withdrawn = withdrawn.add(cash);
                    external.add(new DatedCashFlow(eventDate, DatedCashFlow.CashFlowKind.COUPON, cash));
                    cash = BigDecimal.ZERO;
                } else if ("REINVEST".equals(scenario)) {
                    if (fixedRate == null && cash.compareTo(minCash) >= 0) {
                        Purchase purchase = purchase(context, filters, rule, eventDate, event.secid(), cash, minCash);
                        if (purchase != null) {
                            cash = cash.subtract(purchase.cost());
                            book = book.add(purchase.cost());
                            totalReinvested = totalReinvested.add(purchase.cost());
                            reinvestCount++;
                            delaySum += Math.max(0, ChronoUnit.DAYS.between(eventDate, eventDate));
                            holdings.put(purchase.key(), new Holding(purchase.candidate(), purchase.quantity(), purchase.cost()));
                            Map<String, Object> item = new LinkedHashMap<>();
                            item.put("date", eventDate);
                            item.put("amountRub", Decimals.money(purchase.cost()));
                            item.put("bond", Map.of(
                                    "secid", purchase.candidate().secid(),
                                    "name", purchase.candidate().bond().getShortName() == null
                                            ? purchase.candidate().secid() : purchase.candidate().bond().getShortName(),
                                    "maturityDate", purchase.candidate().bond().getMaturityDate()));
                            item.put("quantity", purchase.quantity());
                            item.put("pricePct", purchase.metrics().cleanPricePct());
                            reinvestments.add(item);
                            for (DatedCashFlow flow : purchase.metrics().bondFlows()) {
                                if (flow.date().isAfter(eventDate) && !flow.date().isAfter(horizonEnd)) {
                                    queue.add(new SimEvent(flow.date(), flow.kind().name(), flow.amount(),
                                            purchase.quantity(), purchase.candidate().secid()));
                                }
                            }
                        }
                    }
                }
            }
            break;
        }
        while (nextYear <= horizonYears) {
            capitalization.add(new CapitalPoint(nextYear, today.plusYears(nextYear), cash.add(withdrawn).add(book)));
            nextYear++;
        }

        BigDecimal finalCapital = cash.add(withdrawn).add(book);
        if (fixedRate != null && "REINVEST".equals(scenario)) {
            BigDecimal compounded = BigDecimal.ZERO;
            for (DatedCashFlow flow : external) {
                if (flow.kind() == DatedCashFlow.CashFlowKind.BUY) {
                    continue;
                }
            }
            compounded = invested;
            for (int year = 1; year <= horizonYears; year++) {
                compounded = compounded.multiply(BigDecimal.ONE.add(fixedRate.divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_UP)));
            }
            finalCapital = compounded.add(totalCoupons.subtract(totalTax));
            for (CapitalPoint ignored : capitalization) {
                // значения в серии для модельного режима заменяются ниже
            }
            capitalization.clear();
            BigDecimal value = invested;
            BigDecimal yearly = fixedRate.divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_UP);
            for (int year = 1; year <= horizonYears; year++) {
                value = value.multiply(BigDecimal.ONE.add(yearly)).add(totalCoupons.subtract(totalTax)
                        .divide(BigDecimal.valueOf(horizonYears), 10, RoundingMode.HALF_UP));
                capitalization.add(new CapitalPoint(year, today.plusYears(year), value));
            }
        }

        BigDecimal terminalForXirr = "CASH_OUT".equals(scenario) ? cash.add(book) : finalCapital;
        external.add(new DatedCashFlow(horizonEnd, DatedCashFlow.CashFlowKind.MATURITY, terminalForXirr));
        BigDecimal xirr = new CashFlowEngine().xirr(external);
        BigDecimal cagr = new CashFlowEngine().cagr(invested, finalCapital, horizonYears);
        BigDecimal avgDelay = reinvestCount == 0 ? null
                : BigDecimal.valueOf(delaySum / (double) reinvestCount).setScale(1, RoundingMode.HALF_UP);
        return new ScenarioResult(finalCapital, cash, totalCoupons, totalTax, totalReinvested,
                reinvestCount, avgDelay, xirr, cagr, capitalization);
    }

    private Holding findHolding(Map<String, Holding> holdings, String secid, int quantity) {
        for (Holding holding : holdings.values()) {
            if (holding.candidate().secid().equals(secid)) {
                return holding;
            }
        }
        return null;
    }

    private Purchase purchase(CalculationContext context, FilterParams filters, String rule, LocalDate date,
                              String sourceSecid, BigDecimal cash, BigDecimal minCash) {
        List<BondCandidate> candidates = context.snapshot().bonds().stream()
                .filter(BondCandidate::hasQuote)
                .filter(c -> c.bond().getMaturityDate() != null && c.bond().getMaturityDate().isAfter(date.plusDays(30)))
                .filter(c -> context.services().filter().matchesStructural(c, filters, support.rating(context, c)))
                .toList();
        if (candidates.isEmpty()) {
            return null;
        }
        Map<String, BondMetricsService.BondMetrics> metrics = new LinkedHashMap<>();
        for (BondCandidate candidate : candidates) {
            BondMetricsService.BondMetrics m = support.metrics(candidate, filters, OfferTreatment.OPTION, date);
            if (m != null && m.ytmPct() != null) {
                metrics.put(candidate.secid(), m);
            }
        }
        if (metrics.isEmpty()) {
            return null;
        }
        BondCandidate chosen = null;
        switch (rule) {
            case "SAME_BOND" -> chosen = candidates.stream()
                    .filter(c -> c.secid().equals(sourceSecid) && metrics.containsKey(c.secid()))
                    .findFirst().orElse(null);
            case "SHORTEST" -> chosen = candidates.stream()
                    .filter(c -> metrics.containsKey(c.secid()))
                    .min(Comparator.comparing(c -> nullSafeDuration(metrics.get(c.secid())))).orElse(null);
            case "LONGEST" -> chosen = candidates.stream()
                    .filter(c -> metrics.containsKey(c.secid()))
                    .max(Comparator.comparing(c -> nullSafeDuration(metrics.get(c.secid())))).orElse(null);
            default -> chosen = candidates.stream()
                    .filter(c -> metrics.containsKey(c.secid()))
                    .max(Comparator.comparing(c -> metrics.get(c.secid()).ytmPct())).orElse(null);
        }
        if (chosen == null) {
            chosen = candidates.stream().filter(c -> metrics.containsKey(c.secid())).findFirst().orElse(null);
        }
        if (chosen == null) {
            return null;
        }
        BondMetricsService.BondMetrics m = metrics.get(chosen.secid());
        BigDecimal lotPrice = m.lotPrice(filters.commissionPct(), chosen.lotSize());
        if (lotPrice.signum() <= 0) {
            return null;
        }
        int lots = cash.subtract(minCash).max(BigDecimal.ZERO).divide(lotPrice, 0, RoundingMode.DOWN).intValue();
        if (lots < 1) {
            return null;
        }
        int quantity = lots * chosen.lotSize();
        BigDecimal cost = lotPrice.multiply(BigDecimal.valueOf(lots));
        return new Purchase(chosen, m, quantity, cost);
    }

    private BigDecimal nullSafeDuration(BondMetricsService.BondMetrics metrics) {
        return metrics.modifiedDurationYears() == null ? BigDecimal.ZERO : metrics.modifiedDurationYears();
    }

    private record SimEvent(LocalDate date, String type, BigDecimal amountPerBond, int quantity, String secid) {
    }

    private record Holding(BondCandidate candidate, int quantity, BigDecimal costBasis) {
        String key() {
            return candidate.secid() + "@" + quantity;
        }

        Holding reduce(int qty, BigDecimal released) {
            return new Holding(candidate, quantity - qty, costBasis.subtract(released));
        }
    }

    private record Purchase(BondCandidate candidate, BondMetricsService.BondMetrics metrics, int quantity,
                            BigDecimal cost) {
        String key() {
            return candidate.secid() + "@" + quantity;
        }
    }

    private record CapitalPoint(int year, LocalDate date, BigDecimal value) {
    }

    private record ScenarioResult(
            BigDecimal finalCapital,
            BigDecimal cash,
            BigDecimal totalCoupons,
            BigDecimal totalTax,
            BigDecimal totalReinvested,
            int reinvestments,
            BigDecimal avgReinvestmentDelayDays,
            BigDecimal xirrPct,
            BigDecimal cagrPct,
            List<CapitalPoint> capitalization) {
    }
}

