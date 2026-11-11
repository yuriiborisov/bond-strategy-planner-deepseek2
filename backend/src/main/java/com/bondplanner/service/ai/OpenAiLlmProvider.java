package com.bondplanner.service.ai;

import com.bondplanner.config.AppProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * OpenAI-совместимый провайдер: LM Studio, vLLM, OpenRouter, Azure OpenAI, Ollama (/v1).
 * YandexGPT и GigaChat используют тот же JSON-формат ответа, но свои схемы авторизации;
 * для них адаптер настраивается через baseUrl/apiKey и заголовок Authorization.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OpenAiLlmProvider implements LlmProvider {

    private final AppProperties properties;
    private final ObjectMapper objectMapper;

    @Override
    public String code() {
        return "OPENAI_COMPATIBLE";
    }

    @Override
    public boolean supports(String providerCode) {
        return List.of("LMSTUDIO", "OPENAI_COMPATIBLE", "OLLAMA", "OPENROUTER", "AZURE", "YANDEX")
                .contains(providerCode == null ? "" : providerCode.toUpperCase());
    }

    @Override
    public boolean healthy() {
        if (!properties.getLlm().isEnabled()) {
            return false;
        }
        AppProperties.Llm llm = properties.getLlm();
        if (llm.getBaseUrl() == null || llm.getBaseUrl().isBlank()) {
            return false;
        }
        try {
            String url = normalizeBase(llm.getBaseUrl()) + "/models";
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(5))
                    .header("Authorization", "Bearer " + (llm.getApiKey() == null ? "" : llm.getApiKey()))
                    .GET()
                    .build();
            HttpResponse<String> response = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(3))
                    .build()
                    .send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() < 500;
        } catch (Exception ex) {
            return false;
        }
    }

    @Override
    public LlmResponse complete(LlmRequest request) {
        AppProperties.Llm llm = properties.getLlm();
        String url = normalizeBase(llm.getBaseUrl()) + "/chat/completions";
        long started = System.currentTimeMillis();
        try {
            Map<String, Object> body = new java.util.LinkedHashMap<>();
            body.put("model", request.model() == null || request.model().isBlank() ? llm.getModel() : request.model());
            body.put("temperature", request.temperature());
            body.put("max_tokens", request.maxTokens());
            body.put("messages", List.of(
                    Map.of("role", "system", "content", request.systemPrompt()),
                    Map.of("role", "user", "content", request.userPrompt())));
            if (request.jsonMode()) {
                body.put("response_format", Map.of("type", "json_object"));
            }
            HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(url))
                    .timeout(llm.getTimeout() == null ? Duration.ofSeconds(60) : llm.getTimeout())
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + (llm.getApiKey() == null ? "" : llm.getApiKey()))
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();
            HttpResponse<String> response = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .build()
                    .send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) {
                throw new IllegalStateException("LLM status " + response.statusCode() + ": "
                        + response.body().substring(0, Math.min(300, response.body().length())));
            }
            JsonNode root = objectMapper.readTree(response.body());
            String content = root.path("choices").path(0).path("message").path("content").asText("");
            int tokensIn = root.path("usage").path("prompt_tokens").asInt(0);
            int tokensOut = root.path("usage").path("completion_tokens").asInt(0);
            log.info("LLM ответ за {} мс, tokens in/out {}/{}", System.currentTimeMillis() - started, tokensIn, tokensOut);
            return new LlmResponse(content, tokensIn, tokensOut, 0.0,
                    root.path("model").asText(request.model()), Map.of("latencyMs", System.currentTimeMillis() - started));
        } catch (Exception ex) {
            throw new IllegalStateException("LLM недоступен: " + ex.getMessage(), ex);
        }
    }

    private String normalizeBase(String baseUrl) {
        String value = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return value.endsWith("/v1") ? value : value + "/v1";
    }
}
