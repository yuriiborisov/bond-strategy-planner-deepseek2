package com.bondplanner.service.calc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * График формирования портфеля: пополнения, покупки активов и остаток кэша —
 * аналог графика погашения кредита, но «в обратную сторону».
 *
 * <p>Для BULLET моделируются ежемесячные взносы и покупки целыми лотами по мере
 * накопления. Для остальных стратегий показываются начальные покупки и, для WHEEL,
 * реинвестирование купонов.</p>
 */
@Service
@RequiredArgsConstructor
public class FundingScheduleService {

    private static final ZoneId MSK = ZoneId.of("Europe/Moscow");
    private static final BigDecimal DEFAULT_FACE = BigDecimal.valueOf(1000);

    private final ObjectMapper objectMapper;

    public ObjectNode build(String strategy, ObjectNode result) {
        try {
            LocalDate start = startDate(result);
            BigDecimal initialContribution = decimal(result.path("summary").path("totalAmountRub"));
            if (initialContribution.signum() == 0) {
                initialContribution = decimal(result.path("summary").path("startCapitalRub"));
            }
            if (initialContribution.signum() == 0) {
                initialContribution = decimal(result.path("summary").path("initialAmountRub"));
            }
            if (initialContribution.signum() == 0) {
                initialContribution = decimal(result.path("summary").path("investedRub"));
            }
            List<ObjectNode> purchases = new ArrayList<>();
            BigDecimal initialPurchases = BigDecimal.ZERO;
            for (JsonNode position : result.path("positions")) {
                BigDecimal amount = decimal(position.path("amountRub"));
                if (amount.signum() <= 0) {
                    continue;
                }
                initialPurchases = initialPurchases.add(amount);
                ObjectNode purchase = objectMapper.createObjectNode();
                purchase.put("date", start.toString());
                purchase.put("secid", position.path("bond").path("secid").asText(""));
                purchase.put("name", position.path("bond").path("name").asText(""));
                purchase.put("quantity", position.path("quantity").asInt(0));
                purchase.put("amountRub", money(amount));
                purchase.put("kind", "INITIAL");
                purchases.add(purchase);
            }

            BigDecimal cash = initialContribution.subtract(initialPurchases).max(BigDecimal.ZERO);
            BigDecimal invested = initialPurchases;
            List<ObjectNode> rows = new ArrayList<>();
            rows.add(row(start, initialContribution, initialPurchases, cash, invested, "Старт"));

            LocalDate end = start;
            BigDecimal monthly = BigDecimal.ZERO;
            if ("BULLET".equals(strategy)) {
                monthly = decimal(result.path("monthlyContributionRub"));
                if (monthly.signum() == 0) {
                    monthly = decimal(result.path("summary").path("requiredMonthlyContributionRub"));
                }
                LocalDate target = date(result.path("targetDate"), start);
                end = target;
                List<Candidate> candidates = candidates(result);
                int cursor = 0;
                int months = (int) Math.max(0, Math.min(360, ChronoUnit.MONTHS.between(start, target)));
                for (int i = 1; i <= months; i++) {
                    LocalDate monthDate = start.plusMonths(i);
                    cash = cash.add(monthly);
                    BigDecimal purchasesThisMonth = BigDecimal.ZERO;
                    if (!candidates.isEmpty()) {
                        for (int pass = 0; pass < 10; pass++) {
                            boolean bought = false;
                            for (int c = 0; c < candidates.size(); c++) {
                                Candidate candidate = candidates.get((cursor + c) % candidates.size());
                                if (cash.compareTo(candidate.lotPrice()) >= 0) {
                                    cash = cash.subtract(candidate.lotPrice());
                                    purchasesThisMonth = purchasesThisMonth.add(candidate.lotPrice());
                                    invested = invested.add(candidate.lotPrice());
                                    purchases.add(purchase(monthDate, candidate, "PLANNED"));
                                    cursor = (cursor + c + 1) % candidates.size();
                                    bought = true;
                                }
                            }
                            if (!bought) {
                                break;
                            }
                        }
                    }
                    rows.add(row(monthDate, monthly, purchasesThisMonth, cash, invested,
                            purchasesThisMonth.signum() > 0 ? "Покупка" : "Накопление"));
                }
            } else {
                JsonNode reinvestments = result.path("reinvestments");
                if ("WHEEL".equals(strategy) && reinvestments.isArray()) {
                    for (JsonNode item : reinvestments) {
                        LocalDate date = date(item.path("date"), start);
                        BigDecimal amount = decimal(item.path("amountRub"));
                        if (amount.signum() <= 0) {
                            continue;
                        }
                        invested = invested.add(amount);
                        purchases.add(purchase(date, item, "REINVEST"));
                        rows.add(row(date, BigDecimal.ZERO, amount, cash, invested, "Реинвест купона"));
                        end = date.isAfter(end) ? date : end;
                    }
                }
                for (JsonNode position : result.path("positions")) {
                    LocalDate maturity = date(position.path("bond").path("effectiveMaturity"), null);
                    if (maturity != null && maturity.isAfter(end)) {
                        end = maturity;
                    }
                }
            }
            rows.sort(Comparator.comparing(row -> row.path("date").asText()));
            if (end.isAfter(start)) {
                ObjectNode last = rows.get(rows.size() - 1).deepCopy();
                last.put("date", end.toString());
                last.put("period", end.toString());
                last.put("contributionRub", BigDecimal.ZERO);
                last.put("purchasesRub", BigDecimal.ZERO);
                last.put("label", "Горизонт");
                if (!rows.get(rows.size() - 1).path("date").asText().equals(end.toString())) {
                    rows.add(last);
                }
            }

            BigDecimal totalContributions = rows.stream()
                    .map(row -> decimal(row.path("contributionRub")))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal totalPurchases = rows.stream()
                    .map(row -> decimal(row.path("purchasesRub")))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            ObjectNode schedule = objectMapper.createObjectNode();
            schedule.put("startDate", start.toString());
            schedule.put("endDate", end.toString());
            schedule.put("monthlyContributionRub", money(monthly));
            schedule.put("initialContributionRub", money(initialContribution));
            schedule.put("totalContributionsRub", money(totalContributions));
            schedule.put("totalPurchasesRub", money(totalPurchases));
            schedule.put("cashResidualRub", money(cash));
            schedule.put("investedRub", money(invested));
            ArrayNode rowsNode = schedule.putArray("rows");
            rows.forEach(rowsNode::add);
            ArrayNode purchasesNode = schedule.putArray("purchases");
            purchases.sort(Comparator.comparing(p -> p.path("date").asText()));
            purchases.forEach(purchasesNode::add);
            schedule.put("note", "BULLET: взносы и покупки целыми лотами по мере накопления; "
                    + "остальные стратегии: стартовые покупки и реинвест купонов. "
                    + "Стоимость бумаг в графике — по цене покупки, без переоценки.");
            return schedule;
        } catch (Exception ex) {
            return null;
        }
    }

    private List<Candidate> candidates(ObjectNode result) {
        List<Candidate> candidates = new ArrayList<>();
        BigDecimal commission = decimal(result.path("assumptions").path("commissionPct"))
                .divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP);
        for (JsonNode item : result.path("candidates")) {
            JsonNode bond = item.path("bond");
            JsonNode checks = item.path("checks");
            if (checks.isObject() && "REJECTED".equals(checks.path("verdict").asText())) {
                continue;
            }
            BigDecimal price = decimal(bond.path("pricePct"));
            BigDecimal nkd = decimal(bond.path("nkdRub"));
            int lotSize = Math.max(1, bond.path("lotSize").asInt(1));
            if (price.signum() <= 0) {
                price = decimal(bond.path("quote").path("pricePct"));
            }
            if (price.signum() <= 0) {
                continue;
            }
            BigDecimal dirty = price.multiply(DEFAULT_FACE).divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP).add(nkd);
            BigDecimal lotPrice = dirty.multiply(BigDecimal.valueOf(lotSize)).multiply(BigDecimal.ONE.add(commission));
            candidates.add(new Candidate(bond.path("secid").asText(""), bond.path("name").asText(""),
                    lotPrice, lotSize));
            if (candidates.size() >= 8) {
                break;
            }
        }
        return candidates;
    }

    private ObjectNode row(LocalDate date, BigDecimal contribution, BigDecimal purchases, BigDecimal cash,
                           BigDecimal invested, String label) {
        ObjectNode row = objectMapper.createObjectNode();
        row.put("date", date.toString());
        row.put("period", date.toString());
        row.put("contributionRub", money(contribution));
        row.put("purchasesRub", money(purchases));
        row.put("cashRub", money(cash));
        row.put("investedRub", money(invested));
        row.put("portfolioValueRub", money(cash.add(invested)));
        row.put("label", label);
        return row;
    }

    private ObjectNode purchase(LocalDate date, Candidate candidate, String kind) {
        ObjectNode purchase = objectMapper.createObjectNode();
        purchase.put("date", date.toString());
        purchase.put("secid", candidate.secid());
        purchase.put("name", candidate.name());
        purchase.put("quantity", candidate.lotSize());
        purchase.put("amountRub", money(candidate.lotPrice()));
        purchase.put("kind", kind);
        return purchase;
    }

    private ObjectNode purchase(LocalDate date, JsonNode item, String kind) {
        ObjectNode purchase = objectMapper.createObjectNode();
        purchase.put("date", date.toString());
        purchase.put("secid", item.path("bond").path("secid").asText(""));
        purchase.put("name", item.path("bond").path("name").asText(""));
        purchase.put("quantity", item.path("quantity").asInt(0));
        purchase.put("amountRub", money(decimal(item.path("amountRub"))));
        purchase.put("kind", kind);
        return purchase;
    }

    private LocalDate startDate(ObjectNode result) {
        JsonNode snapshot = result.path("snapshotTs");
        if (!snapshot.isMissingNode() && !snapshot.isNull()) {
            try {
                return Instant.parse(snapshot.asText()).atZone(MSK).toLocalDate();
            } catch (Exception ignored) {
                // fallback ниже
            }
        }
        return LocalDate.now(MSK);
    }

    private BigDecimal decimal(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull() || !node.isNumber()) {
            return BigDecimal.ZERO;
        }
        return node.decimalValue();
    }

    private BigDecimal money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }

    private LocalDate date(JsonNode node, LocalDate fallback) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return fallback;
        }
        try {
            return LocalDate.parse(node.asText());
        } catch (Exception ex) {
            return fallback;
        }
    }

    private record Candidate(String secid, String name, BigDecimal lotPrice, int lotSize) {
    }
}
