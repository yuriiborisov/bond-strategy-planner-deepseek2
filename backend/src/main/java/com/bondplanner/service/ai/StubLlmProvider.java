package com.bondplanner.service.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Детерминированный провайдер для тестов и демонстрации без внешней модели.
 * Включается APP_LLM_PROVIDER=STUB.
 */
@Component
@RequiredArgsConstructor
public class StubLlmProvider implements LlmProvider {

    private final ObjectMapper objectMapper;

    @Override
    public String code() {
        return "STUB";
    }

    @Override
    public boolean supports(String providerCode) {
        return "STUB".equalsIgnoreCase(providerCode);
    }

    @Override
    public boolean healthy() {
        return true;
    }

    @Override
    public LlmResponse complete(LlmRequest request) {
        Map<String, Object> payload = Map.of(
                "summary", "Демонстрационный ответ LLM (провайдер STUB). Проверьте портфель по чек-листу и источникам.",
                "portfolioHealth", "WATCH",
                "nextActions", List.of(Map.of(
                        "date", java.time.LocalDate.now().plusDays(30).toString(),
                        "type", "CHECK_RATING",
                        "priority", "MEDIUM",
                        "title", "Проверить актуальность рейтингов эмитентов",
                        "details", "Рейтинги обновляются еженедельно; проверьте прогноз и дату.",
                        "relatedSecids", List.of(),
                        "sourceRefs", List.of("rating:1"))),
                "risks", List.of(Map.of(
                        "code", "CONCENTRATION",
                        "severity", "MEDIUM",
                        "message", "Проверьте концентрацию на отдельных эмитентов")),
                "watchlist", List.of(),
                "questions", List.of("Планируете ли выводить купоны или реинвестировать их?"),
                "sources", List.of(Map.of(
                        "ref", "rating:1",
                        "title", "Кредитные рейтинги эмитентов",
                        "url", "https://www.acra-ratings.ru/")));
        String content;
        try {
            content = objectMapper.writeValueAsString(payload);
        } catch (Exception ex) {
            content = "{}";
        }
        int tokensIn = (request.systemPrompt().length() + request.userPrompt().length()) / 4;
        return new LlmResponse(content, tokensIn, 300, 0.0, "stub", Map.of("latencyMs", 5));
    }
}
