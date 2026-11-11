package com.bondplanner.service.ai;

import com.bondplanner.config.AppProperties;
import com.bondplanner.domain.entity.Calculation;
import com.bondplanner.domain.entity.Portfolio;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Сбор контекста для LLM: только проверенные факты, без PII (FR-AI-07, §20.4).
 */
@Component
@RequiredArgsConstructor
public class AiContextBuilder {

    public static final String DISCLAIMER =
            "Сгенерировано AI на основе доступных данных. Не является индивидуальной инвестиционной рекомендацией. "
                    + "Проверяйте первоисточники.";

    private final AppProperties properties;
    private final ObjectMapper objectMapper;

    public record Context(String json, List<String> knownSecids, List<String> knownRefs, String summary) {
    }

    public Context portfolio(Portfolio portfolio, Calculation calculation, JsonNode result) {
        boolean percentOnly = "PERCENT_ONLY".equalsIgnoreCase(properties.getLlm().getPrivacyMode());
        ObjectNode root = objectMapper.createObjectNode();
        root.put("type", "PORTFOLIO_REVIEW");
        root.put("privacyMode", percentOnly ? "PERCENT_ONLY" : "FULL");
        ObjectNode meta = root.putObject("portfolio");
        meta.put("id", portfolio.getId());
        meta.put("name", portfolio.getName());
        meta.put("strategy", calculation.getStrategyCode());
        meta.put("snapshotTs", calculation.getSnapshotTs().toString());
        meta.put("asOf", LocalDate.now().toString());

        BigDecimal total = result.path("summary").path("totalAmountRub").decimalValue();
        if (total.signum() == 0) {
            total = result.path("summary").path("initialAmountRub").decimalValue();
        }
        ObjectNode summary = root.putObject("summary");
        copySummary(result.path("summary"), summary, total, percentOnly);

        ArrayNode positions = root.putArray("positions");
        List<String> secids = new java.util.ArrayList<>();
        List<String> refs = new java.util.ArrayList<>();
        for (JsonNode position : result.path("positions")) {
            JsonNode bond = position.path("bond");
            String secid = bond.path("secid").asText(null);
            if (secid == null) {
                continue;
            }
            secids.add(secid);
            refs.add("bond:" + secid);
            ObjectNode item = positions.addObject();
            item.put("secid", secid);
            item.put("name", bond.path("name").asText(""));
            item.put("issuer", bond.path("issuer").asText(""));
            item.put("maturityDate", bond.path("maturityDate").asText(""));
            item.put("ytmPct", bond.path("ytmPct").decimalValue());
            item.put("durationYears", bond.path("durationYears").decimalValue());
            if (percentOnly) {
                item.put("amountPct", percent(position.path("amountRub").decimalValue(), total));
            } else {
                item.put("amountRub", position.path("amountRub").decimalValue());
            }
            item.put("quantity", position.path("quantity").asInt());
            JsonNode checks = position.path("checks");
            if (checks.isObject()) {
                item.put("verdict", checks.path("verdict").asText("UNKNOWN"));
                item.put("checkScore", checks.path("score").asDouble());
            }
        }
        ArrayNode cashflows = root.putArray("cashflowsNext90Days");
        LocalDate limit = LocalDate.now().plusDays(90);
        for (JsonNode point : result.path("cashFlowSchedule")) {
            String date = point.path("date").asText("");
            if (date.isBlank()) {
                continue;
            }
            LocalDate flowDate = LocalDate.parse(date);
            if (flowDate.isAfter(limit)) {
                continue;
            }
            ObjectNode item = cashflows.addObject();
            item.put("date", date);
            item.put("type", point.path("type").asText(""));
            if (percentOnly) {
                item.put("amountPct", percent(point.path("amountRub").decimalValue(), total));
            } else {
                item.put("amountRub", point.path("amountRub").decimalValue());
            }
        }
        ArrayNode warnings = root.putArray("warnings");
        for (JsonNode warning : result.path("warnings")) {
            warnings.addObject()
                    .put("code", warning.path("code").asText(""))
                    .put("severity", warning.path("severity").asText(""))
                    .put("message", warning.path("message").asText(""));
        }
        ArrayNode ratings = root.putArray("ratings");
        for (JsonNode position : result.path("positions")) {
            JsonNode bond = position.path("bond");
            if (bond.path("rating").isMissingNode() || bond.path("rating").isNull()) {
                continue;
            }
            ObjectNode rating = ratings.addObject();
            rating.put("secid", bond.path("secid").asText(""));
            rating.put("agency", bond.path("ratingAgency").asText(""));
            rating.put("rating", bond.path("rating").asText(""));
            rating.put("outlook", bond.path("ratingOutlook").asText(""));
            rating.put("ratingDate", bond.path("ratingDate").asText(""));
            refs.add("rating:" + bond.path("secid").asText(""));
        }
        refs.add("cashflow:portfolio:" + portfolio.getId());
        ArrayNode sources = root.putArray("availableSources");
        for (String ref : refs) {
            ObjectNode source = sources.addObject();
            source.put("ref", ref);
            if (ref.startsWith("bond:")) {
                source.put("title", "MOEX ISS — карточка бумаги " + ref.substring(5));
                source.put("url", "https://www.moex.com/ru/issue.aspx?code=" + ref.substring(5));
            } else if (ref.startsWith("rating:")) {
                source.put("title", "Кредитный рейтинг эмитента (" + ref.substring(7) + ")");
                source.put("url", "https://www.acra-ratings.ru/");
            } else {
                source.put("title", "Купонный календарь портфеля");
                source.put("url", "https://www.moex.com/");
            }
        }
        root.put("notes", "Суммы в процентах от капитала (privacy mode)" );
        String json = toJson(root);
        return new Context(json, secids, refs, deterministicSummary(result));
    }

    public Context discovery(Calculation calculation, JsonNode result, JsonNode params) {
        boolean percentOnly = "PERCENT_ONLY".equalsIgnoreCase(properties.getLlm().getPrivacyMode());
        ObjectNode root = objectMapper.createObjectNode();
        root.put("type", "ASSET_DISCOVERY");
        root.put("privacyMode", percentOnly ? "PERCENT_ONLY" : "FULL");
        root.put("strategy", calculation.getStrategyCode());
        root.set("parameters", params);
        ArrayNode candidates = root.putArray("candidates");
        List<String> secids = new java.util.ArrayList<>();
        List<String> refs = new java.util.ArrayList<>();
        JsonNode list = result.has("candidates") ? result.path("candidates") : result.path("positions");
        BigDecimal total = result.path("summary").path("totalAmountRub").decimalValue();
        for (JsonNode node : list) {
            JsonNode bond = node.has("bond") ? node.path("bond") : node.path("bond");
            String secid = bond.path("secid").asText(null);
            if (secid == null) {
                continue;
            }
            secids.add(secid);
            refs.add("bond:" + secid);
            ObjectNode item = candidates.addObject();
            item.put("secid", secid);
            item.put("name", bond.path("name").asText(""));
            item.put("issuer", bond.path("issuer").asText(""));
            item.put("maturityDate", bond.path("maturityDate").asText(""));
            item.put("ytmPct", bond.path("ytmPct").decimalValue());
            item.put("durationYears", bond.path("durationYears").decimalValue());
            item.put("rating", bond.path("rating").asText(""));
            item.put("ratingAgency", bond.path("ratingAgency").asText(""));
            item.put("ratingOutlook", bond.path("ratingOutlook").asText(""));
            if (!bond.path("rating").asText("").isBlank()) {
                refs.add("rating:" + secid);
            }
            JsonNode checks = node.path("checks");
            if (checks.isObject()) {
                item.put("verdict", checks.path("verdict").asText("UNKNOWN"));
                item.put("checkScore", checks.path("score").asDouble());
                item.put("explanation", checks.path("explanation").asText(""));
                ArrayNode items = item.putArray("checks");
                for (JsonNode check : checks.path("checks")) {
                    ObjectNode checkNode = items.addObject();
                    checkNode.put("code", check.path("code").asText(""));
                    checkNode.put("status", check.path("status").asText(""));
                    checkNode.put("value", String.valueOf(check.path("value")));
                    checkNode.put("threshold", check.path("threshold").asText(""));
                    checkNode.put("source", check.path("source").asText(""));
                }
            }
            if (percentOnly) {
                item.put("recommendedAmountPct", percent(node.path("amountRub").decimalValue(), total));
            } else {
                item.put("recommendedAmountRub", node.path("amountRub").decimalValue());
            }
        }
        ArrayNode sources = root.putArray("availableSources");
        for (String ref : refs) {
            ObjectNode source = sources.addObject();
            source.put("ref", ref);
            if (ref.startsWith("bond:")) {
                String secid = ref.substring("bond:".length());
                source.put("title", "MOEX ISS — карточка бумаги " + secid);
                source.put("url", "https://www.moex.com/ru/issue.aspx?code=" + secid);
            } else if (ref.startsWith("rating:")) {
                String secid = ref.substring("rating:".length());
                source.put("title", "Кредитный рейтинг эмитента (" + secid + ")");
                source.put("url", "https://www.acra-ratings.ru/");
            }
        }
        root.put("notes", "Используй только ref из availableSources для поля sources");
        String json = toJson(root);
        return new Context(json, secids, refs, deterministicDiscoverySummary(result, list.size()));
    }

    private void copySummary(JsonNode source, ObjectNode target, BigDecimal total, boolean percentOnly) {
        target.put("positionsCount", source.path("positionsCount").asInt(0));
        target.put("weightedYtmPct", source.path("weightedYtmPct").decimalValue());
        target.put("weightedDurationYears", source.path("weightedDurationYears").decimalValue());
        target.put("totalTaxRub", source.path("totalTaxRub").decimalValue());
        if (percentOnly) {
            target.put("totalAmountPct", 100);
            if (source.hasNonNull("cashResidualRub") && total.signum() > 0) {
                target.put("cashResidualPct", percent(source.path("cashResidualRub").decimalValue(), total));
            }
        } else {
            target.set("totalAmountRub", source.path("totalAmountRub"));
            target.set("cashResidualRub", source.path("cashResidualRub"));
            target.set("totalCouponIncomeRub", source.path("totalCouponIncomeRub"));
            target.set("projectedValueRub", source.path("projectedValueRub"));
        }
    }

    private BigDecimal percent(BigDecimal amount, BigDecimal total) {
        if (total == null || total.signum() == 0 || amount == null) {
            return BigDecimal.ZERO;
        }
        return amount.divide(total, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);
    }

    private String deterministicSummary(JsonNode result) {
        JsonNode summary = result.path("summary");
        StringBuilder sb = new StringBuilder("Портфель: ");
        sb.append(summary.path("positionsCount").asInt(result.path("positions").size())).append(" позиций. ");
        if (summary.hasNonNull("weightedYtmPct")) {
            sb.append("Средневзвешенная YTM ").append(summary.get("weightedYtmPct").asText()).append("%. ");
        }
        if (summary.hasNonNull("weightedDurationYears")) {
            sb.append("Дюрация ").append(summary.get("weightedDurationYears").asText()).append(" лет. ");
        }
        int warnings = result.path("warnings").size();
        if (warnings > 0) {
            sb.append("Предупреждений: ").append(warnings).append('.');
        }
        return sb.toString();
    }

    private String deterministicDiscoverySummary(JsonNode result, int candidates) {
        return "Проверенных кандидатов: " + candidates + ". Средневзвешенная YTM: "
                + result.path("summary").path("weightedYtmPct").asText("—") + "%.";
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return "{}";
        }
    }
}
