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
        String model = request.model() == null || request.model().isBlank() ? llm.getModel() : request.model();
        try {
            Map<String, Object> body = new java.util.LinkedHashMap<>();
            body.put("model", model);
            body.put("temperature", request.temperature());
            body.put("max_tokens", request.maxTokens());
            String systemPrompt = request.systemPrompt();
            // Qwen3 — reasoning-модель: без /no_think она тратит весь лимит токенов
            // на reasoning_content и возвращает пустой content.
            if (llm.isDisableThinking() && model.toLowerCase().contains("qwen3")) {
                systemPrompt = systemPrompt + "\n/no_think";
            }
            body.put("messages", List.of(
                    Map.of("role", "system", "content", systemPrompt),
                    Map.of("role", "user", "content", request.userPrompt())));
            boolean jsonMode = request.jsonMode();
            if (jsonMode) {
                body.put("response_format", Map.of("type", "json_object"));
            }
            HttpResponse<String> response = send(url, body, llm);
            // Некоторые LM Studio / локальные серверы не поддерживают response_format.
            if (response.statusCode() == 400 && jsonMode) {
                log.warn("LLM не поддерживает response_format, повторяем без него");
                body.remove("response_format");
                response = send(url, body, llm);
            }
            if (response.statusCode() >= 300) {
                throw new IllegalStateException("LLM status " + response.statusCode() + ": "
                        + response.body().substring(0, Math.min(300, response.body().length())));
            }
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode message = root.path("choices").path(0).path("message");
            String content = message.path("content").asText("");
            if (content.isBlank()) {
                String reasoning = message.path("reasoning_content").asText("");
                if (!reasoning.isBlank()) {
                    throw new IllegalStateException("Модель вернула только reasoning_content без финального JSON: "
                            + reasoning.substring(0, Math.min(200, reasoning.length())));
                }
                throw new IllegalStateException("LLM вернул пустой content");
            }
            int tokensIn = root.path("usage").path("prompt_tokens").asInt(0);
            int tokensOut = root.path("usage").path("completion_tokens").asInt(0);
            log.info("LLM ответ за {} мс, model={}, tokens in/out {}/{}",
                    System.currentTimeMillis() - started, root.path("model").asText(model), tokensIn, tokensOut);
            return new LlmResponse(content, tokensIn, tokensOut, 0.0,
                    root.path("model").asText(model), Map.of("latencyMs", System.currentTimeMillis() - started));
        } catch (Exception ex) {
            throw new IllegalStateException("LLM недоступен: " + ex.getMessage(), ex);
        }
    }

    private HttpResponse<String> send(String url, Map<String, Object> body, AppProperties.Llm llm) throws Exception {
        HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(url))
                .timeout(llm.getTimeout() == null ? Duration.ofSeconds(60) : llm.getTimeout())
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + (llm.getApiKey() == null ? "" : llm.getApiKey()))
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                .build();
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build()
                .send(httpRequest, HttpResponse.BodyHandlers.ofString());
    }

    private String normalizeBase(String baseUrl) {
        String value = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return value.endsWith("/v1") ? value : value + "/v1";
    }
}
