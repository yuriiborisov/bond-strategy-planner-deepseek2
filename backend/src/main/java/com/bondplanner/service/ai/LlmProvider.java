package com.bondplanner.service.ai;

import java.util.Map;

/**
 * Провайдер LLM. Провайдер-агностичный контракт: промпты и схема ответа
 * не зависят от реализации (D-5, D-6).
 */
public interface LlmProvider {

    String code();

    boolean supports(String providerCode);

    LlmResponse complete(LlmRequest request);

    boolean healthy();

    record LlmRequest(String systemPrompt, String userPrompt, String model, double temperature,
                      int maxTokens, boolean jsonMode) {
    }

    record LlmResponse(String content, int tokensIn, int tokensOut, double costUsd, String model,
                       Map<String, Object> raw) {
    }
}
