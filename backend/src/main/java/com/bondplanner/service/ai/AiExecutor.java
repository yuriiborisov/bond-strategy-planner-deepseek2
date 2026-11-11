package com.bondplanner.service.ai;

import com.bondplanner.config.AppProperties;
import com.bondplanner.domain.entity.LlmAnalysis;
import com.bondplanner.domain.model.AnalysisStatus;
import com.bondplanner.repository.LlmAnalysisRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Выполнение AI-анализа: промпт → провайдер → JSON Schema → grounding → сохранение (§20.3).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiExecutor {

    private final LlmAnalysisRepository analysisRepository;
    private final List<LlmProvider> providers;
    private final AppProperties properties;
    private final ObjectMapper objectMapper;

    public void execute(Long analysisId) {
        LlmAnalysis analysis = analysisRepository.findById(analysisId).orElse(null);
        if (analysis == null) {
            return;
        }
        analysis.setStatus(AnalysisStatus.RUNNING);
        analysisRepository.save(analysis);
        long started = System.currentTimeMillis();
        try {
            JsonNode request = objectMapper.readTree(analysis.getRequestJson());
            String systemPrompt = request.path("systemPrompt").asText("");
            String userPrompt = request.path("userPrompt").asText("");
            List<String> knownSecids = objectMapper.convertValue(request.path("knownSecids"),
                    objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
            List<String> knownRefs = objectMapper.convertValue(request.path("knownRefs"),
                    objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));

            LlmProvider provider = selectProvider();
            LlmProvider.LlmResponse response = provider.complete(new LlmProvider.LlmRequest(
                    systemPrompt, userPrompt, analysis.getModel(), 0.2, 1500, true));

            JsonNode parsed = parse(response.content());
            if (!isValid(parsed)) {
                LlmProvider.LlmResponse repair = provider.complete(new LlmProvider.LlmRequest(
                        systemPrompt + "\n\nПредыдущий ответ не соответствовал JSON-схеме. Верни ТОЛЬКО корректный JSON.",
                        userPrompt + "\n\nПредыдущий ответ: " + truncate(response.content(), 2000),
                        analysis.getModel(), 0.1, 1500, true));
                parsed = parse(repair.content());
                response = new LlmProvider.LlmResponse(repair.content(),
                        response.tokensIn() + repair.tokensIn(), response.tokensOut() + repair.tokensOut(),
                        response.costUsd() + repair.costUsd(), repair.model(), repair.raw());
            }
            if (!isValid(parsed)) {
                throw new IllegalStateException("LLM вернул невалидный JSON после repair-запроса");
            }
            ObjectNode cleaned = ground(parsed, knownSecids, knownRefs);
            cleaned.put("disclaimer", AiContextBuilder.DISCLAIMER);
            cleaned.put("model", response.model());
            cleaned.put("promptVersion", analysis.getPromptVersion());
            analysis.setResponseJson(objectMapper.writeValueAsString(cleaned));
            analysis.setStatus(AnalysisStatus.DONE);
            analysis.setTokensIn(response.tokensIn());
            analysis.setTokensOut(response.tokensOut());
            analysis.setCostUsd(java.math.BigDecimal.valueOf(response.costUsd()));
            analysis.setLatencyMs((int) (System.currentTimeMillis() - started));
            analysis.setFinishedAt(Instant.now());
        } catch (Exception ex) {
            log.warn("AI-анализ {} не выполнен: {}", analysisId, ex.getMessage());
            ObjectNode fallback = fallback(analysis, ex);
            try {
                analysis.setResponseJson(objectMapper.writeValueAsString(fallback));
            } catch (Exception ignored) {
                analysis.setResponseJson("{}");
            }
            analysis.setStatus(AnalysisStatus.DONE);
            analysis.setErrorMessage(ex.getMessage());
            analysis.setLatencyMs((int) (System.currentTimeMillis() - started));
            analysis.setFinishedAt(Instant.now());
        }
        analysisRepository.save(analysis);
    }

    private LlmProvider selectProvider() {
        String code = properties.getLlm().getProvider();
        return providers.stream().filter(p -> p.supports(code)).findFirst()
                .orElseThrow(() -> new IllegalStateException("LLM-провайдер не поддерживается: " + code));
    }

    private JsonNode parse(String content) throws Exception {
        String cleaned = content == null ? "" : content.trim();
        if (cleaned.startsWith("```")) {
            cleaned = cleaned.replaceFirst("^```(?:json)?", "").replaceFirst("```$", "").trim();
        }
        int start = cleaned.indexOf('{');
        int end = cleaned.lastIndexOf('}');
        if (start >= 0 && end > start) {
            cleaned = cleaned.substring(start, end + 1);
        }
        return objectMapper.readTree(cleaned);
    }

    private boolean isValid(JsonNode node) {
        if (node == null || !node.isObject()) {
            return false;
        }
        String summary = node.path("summary").asText("");
        if (summary.isBlank()) {
            return false;
        }
        String health = node.path("portfolioHealth").asText("");
        return health.isEmpty() || List.of("OK", "WATCH", "ACTION_REQUIRED").contains(health);
    }

    /**
     * Grounding: удаляем выдуманные тикеры и ссылки, которых нет в контексте (FR-AI-04/05).
     */
    private ObjectNode ground(JsonNode parsed, List<String> knownSecids, List<String> knownRefs) {
        ObjectNode clean = parsed.deepCopy();
        int removed = 0;
        List<String> issues = new java.util.ArrayList<>();
        ArrayNode actions = clean.has("nextActions") && clean.get("nextActions").isArray()
                ? (ArrayNode) clean.get("nextActions") : objectMapper.createArrayNode();
        for (JsonNode action : actions) {
            ObjectNode node = (ObjectNode) action;
            ArrayNode secids = node.has("relatedSecids") ? (ArrayNode) node.get("relatedSecids") : null;
            if (secids != null) {
                ArrayNode filtered = objectMapper.createArrayNode();
                for (JsonNode secid : secids) {
                    if (knownSecids.contains(secid.asText())) {
                        filtered.add(secid);
                    } else {
                        removed++;
                        issues.add("unknown secid: " + secid.asText());
                    }
                }
                node.set("relatedSecids", filtered);
            }
            ArrayNode refs = node.has("sourceRefs") ? (ArrayNode) node.get("sourceRefs") : null;
            if (refs != null) {
                ArrayNode filtered = objectMapper.createArrayNode();
                for (JsonNode ref : refs) {
                    if (knownRefs.contains(ref.asText())) {
                        filtered.add(ref);
                    } else {
                        removed++;
                        issues.add("unknown ref: " + ref.asText());
                    }
                }
                node.set("sourceRefs", filtered);
            }
        }
        if (clean.has("watchlist") && clean.get("watchlist").isArray()) {
            ArrayNode filtered = objectMapper.createArrayNode();
            for (JsonNode item : clean.get("watchlist")) {
                if (knownSecids.contains(item.path("secid").asText())) {
                    filtered.add(item);
                } else {
                    removed++;
                    issues.add("unknown watchlist secid: " + item.path("secid").asText());
                }
            }
            clean.set("watchlist", filtered);
        }
        if (clean.has("sources") && clean.get("sources").isArray()) {
            ArrayNode filtered = objectMapper.createArrayNode();
            for (JsonNode source : clean.get("sources")) {
                if (knownRefs.contains(source.path("ref").asText())) {
                    filtered.add(source);
                } else {
                    removed++;
                    issues.add("unknown source ref: " + source.path("ref").asText());
                }
            }
            clean.set("sources", filtered);
        }
        ObjectNode grounding = clean.putObject("grounding");
        grounding.put("removed", removed);
        grounding.put("checkedSecids", knownSecids.size());
        ArrayNode issueNodes = grounding.putArray("issues");
        issues.stream().distinct().limit(20).forEach(issueNodes::add);
        return clean;
    }

    private ObjectNode fallback(LlmAnalysis analysis, Exception ex) {
        ObjectNode node = objectMapper.createObjectNode();
        String summary = "Детерминированное резюме (LLM недоступен).";
        try {
            JsonNode request = objectMapper.readTree(analysis.getRequestJson());
            summary = request.path("fallbackSummary").asText(summary);
        } catch (Exception ignored) {
            // fallback уже деградировал — оставляем базовый текст
        }
        node.put("summary", summary);
        node.put("portfolioHealth", "WATCH");
        ArrayNode actions = node.putArray("nextActions");
        ObjectNode action = actions.addObject();
        action.put("date", java.time.LocalDate.now().plusDays(14).toString());
        action.put("type", "UPDATE_DATA");
        action.put("priority", "MEDIUM");
        action.put("title", "Обновить данные и повторить AI-анализ");
        action.put("details", "Провайдер LLM недоступен: " + truncate(ex.getMessage(), 200));
        action.put("relatedSecids", objectMapper.createArrayNode());
        action.put("sourceRefs", objectMapper.createArrayNode());
        node.putArray("risks");
        node.putArray("watchlist");
        node.putArray("questions");
        node.putArray("sources");
        node.put("fallback", true);
        node.put("error", truncate(ex.getMessage(), 500));
        node.put("disclaimer", AiContextBuilder.DISCLAIMER);
        return node;
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max) + "…";
    }

    public Map<String, Object> usageSummary(LlmAnalysis analysis) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("tokensIn", analysis.getTokensIn());
        map.put("tokensOut", analysis.getTokensOut());
        map.put("costUsd", analysis.getCostUsd());
        map.put("latencyMs", analysis.getLatencyMs());
        return map;
    }

    public String providerCode() {
        return properties.getLlm().getProvider().toUpperCase(Locale.ROOT);
    }
}
