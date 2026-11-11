package com.bondplanner.service.calc;

import com.bondplanner.api.dto.CalculationRequest;
import com.bondplanner.api.error.ApiException;
import com.bondplanner.config.AppProperties;
import com.bondplanner.domain.entity.Calculation;
import com.bondplanner.domain.entity.Exchange;
import com.bondplanner.domain.entity.Strategy;
import com.bondplanner.domain.entity.User;
import com.bondplanner.repository.CalculationRepository;
import com.bondplanner.repository.ExchangeRepository;
import com.bondplanner.repository.StrategyRepository;
import com.bondplanner.repository.UserRepository;
import com.bondplanner.service.checks.AssetCheckService;
import com.bondplanner.service.rating.RatingNormalizer;
import com.bondplanner.service.strategy.CalculationContext;
import com.bondplanner.service.strategy.CalculationServices;
import com.bondplanner.service.strategy.StrategyCalculator;
import com.bondplanner.service.tax.TaxCalculator;
import com.bondplanner.service.universe.UniverseService;
import com.bondplanner.service.universe.UniverseSnapshot;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Оркестрация расчёта: валидация, снимок, стратегия, сохранение результата (§5.3).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CalculationService {

    private final StrategyRepository strategyRepository;
    private final ExchangeRepository exchangeRepository;
    private final CalculationRepository calculationRepository;
    private final UserRepository userRepository;
    private final UniverseService universeService;
    private final BondMetricsService metricsService;
    private final RankingService rankingService;
    private final UniverseFilter universeFilter;
    private final AssetCheckService assetCheckService;
    private final com.bondplanner.service.cashflow.CashFlowEngine cashFlowEngine;
    private final TaxCalculator taxCalculator;
    private final RatingNormalizer ratingNormalizer;
    private final List<StrategyCalculator> calculators;
    private final ObjectMapper objectMapper;
    private final AppProperties properties;

    private final Map<String, StrategyCalculator> registry = new HashMap<>();

    @PostConstruct
    void init() {
        for (StrategyCalculator calculator : calculators) {
            registry.put(calculator.code().toUpperCase(), calculator);
        }
    }

    @Transactional
    public JsonNode calculate(Long userId, String sessionId, CalculationRequest request) {
        String strategyCode = request.strategy().trim().toUpperCase();
        Strategy strategy = strategyRepository.findByCodeAndEnabledTrue(strategyCode)
                .orElseThrow(() -> ApiException.notFound("Стратегия не найдена или отключена: " + strategyCode));
        StrategyCalculator calculator = registry.get(strategyCode);
        if (calculator == null) {
            throw ApiException.notFound("Калькулятор стратегии не зарегистрирован: " + strategyCode);
        }
        String exchangeCode = request.exchange() == null || request.exchange().isBlank()
                ? defaultExchangeCode()
                : request.exchange().trim().toUpperCase();
        Exchange exchange = exchangeRepository.findByCodeIgnoreCase(exchangeCode)
                .orElseThrow(() -> ApiException.notFound("Биржа не найдена: " + exchangeCode));

        JsonNode params = request.params() == null || request.params().isNull()
                ? objectMapper.createObjectNode()
                : request.params();
        FilterParams filters = FilterParams.from(Params.of(params));

        UniverseSnapshot snapshot = universeService.load(exchangeCode, filters.maxRatingAgeDays());
        if (snapshot.bonds().isEmpty()) {
            throw ApiException.noCandidates("В базе нет бумаг по бирже " + exchangeCode,
                    List.of("Запустить синхронизацию в админке", "Проверить демо-данные APP_SEED_DEMO=true"));
        }
        if ("EXPIRED".equals(snapshot.dataFreshness()) && !Boolean.TRUE.equals(request.allowStale())) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "DATA_STALE",
                    "Снимок рыночных данных старше 7 дней — расчёт остановлен",
                    List.of("Запустить синхронизацию", "Разрешить расчёт на устаревших данных (allowStale=true)"));
        }

        CalculationServices services = new CalculationServices(metricsService, rankingService, universeFilter,
                assetCheckService, cashFlowEngine, taxCalculator, ratingNormalizer);
        CalculationContext context = new CalculationContext(snapshot, services);

        User user = userId == null ? null : userRepository.findById(userId).orElse(null);
        String effectiveSession = sessionId == null || sessionId.isBlank()
                ? UUID.randomUUID().toString() : sessionId.trim();

        CalculationResult result;
        try {
            result = calculator.calculate(context, Params.of(params));
        } catch (ApiException ex) {
            persistFailure(user, effectiveSession, strategyCode, exchange, params, snapshot.snapshotTs(), ex);
            throw ex;
        } catch (RuntimeException ex) {
            persistFailure(user, effectiveSession, strategyCode, exchange, params, snapshot.snapshotTs(),
                    new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "CALCULATION_FAILED", ex.getMessage()));
            throw ex;
        }

        JsonNode resultNode = objectMapper.valueToTree(result);
        Calculation calculation = new Calculation();
        calculation.setUser(user);
        calculation.setAnonymousSessionId(effectiveSession);
        calculation.setStrategyCode(strategyCode);
        calculation.setExchange(exchange);
        calculation.setParamsJson(toJson(params));
        calculation.setResultJson(toJson(resultNode));
        calculation.setSnapshotTs(snapshot.snapshotTs() == null ? Instant.now() : snapshot.snapshotTs());
        calculation.setAlgorithmVersion("1.0.0");
        calculation.setStatus("DONE");
        calculation.setCreatedAt(Instant.now());
        calculationRepository.save(calculation);

        ObjectNode response = resultNode.deepCopy();
        response.put("calculationId", calculation.getId());
        response.put("strategy", strategyCode);
        response.put("exchange", exchangeCode);
        response.put("algorithmVersion", calculation.getAlgorithmVersion());
        response.put("anonymousSessionId", effectiveSession);
        response.set("strategyMeta", objectMapper.valueToTree(Map.of(
                "name", strategy.getName(),
                "riskLevel", strategy.getRiskLevel(),
                "description", strategy.getShortDescription())));
        return response;
    }

    @Transactional(readOnly = true)
    public JsonNode get(Long id, Long userId, String sessionId) {
        Calculation calculation = calculationRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Расчёт не найден: " + id));
        if (calculation.getUser() != null) {
            if (userId == null || !calculation.getUser().getId().equals(userId)) {
                throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Нет доступа к расчёту");
            }
        } else if (sessionId == null || !sessionId.equals(calculation.getAnonymousSessionId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Анонимный расчёт доступен только в своей сессии");
        }
        ObjectNode node = readResult(calculation);
        node.put("calculationId", calculation.getId());
        node.put("strategy", calculation.getStrategyCode());
        node.put("exchange", calculation.getExchange().getCode());
        node.put("snapshotTs", calculation.getSnapshotTs().toString());
        node.put("algorithmVersion", calculation.getAlgorithmVersion());
        node.put("createdAt", calculation.getCreatedAt().toString());
        node.put("anonymousSessionId", calculation.getAnonymousSessionId());
        return node;
    }

    @Transactional(readOnly = true)
    public JsonNode history(Long userId, String sessionId, int page, int size) {
        int safeSize = Math.min(Math.max(size, 1), 200);
        var pageable = org.springframework.data.domain.PageRequest.of(Math.max(page, 0), safeSize);
        var calculations = userId != null
                ? calculationRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable)
                : calculationRepository.findByAnonymousSessionIdOrderByCreatedAtDesc(sessionId, pageable);
        List<Map<String, Object>> items = calculations.getContent().stream().map(c -> {
            Map<String, Object> map = new java.util.LinkedHashMap<>();
            map.put("id", c.getId());
            map.put("strategy", c.getStrategyCode());
            map.put("exchange", c.getExchange().getCode());
            map.put("snapshotTs", c.getSnapshotTs());
            map.put("createdAt", c.getCreatedAt());
            map.put("status", c.getStatus());
            try {
                JsonNode result = objectMapper.readTree(c.getResultJson());
                if (result.has("summary")) {
                    map.put("summary", result.get("summary"));
                }
            } catch (Exception ignored) {
                // история не должна падать из-за одного битого результата
            }
            return map;
        }).toList();
        Map<String, Object> response = new java.util.LinkedHashMap<>();
        response.put("content", items);
        response.put("page", calculations.getNumber());
        response.put("size", calculations.getSize());
        response.put("totalElements", calculations.getTotalElements());
        return objectMapper.valueToTree(response);
    }

    @Transactional(readOnly = true)
    public Calculation requireCalculation(Long id, Long userId, String sessionId) {
        Calculation calculation = calculationRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Расчёт не найден: " + id));
        if (calculation.getUser() != null && (userId == null || !calculation.getUser().getId().equals(userId))) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Нет доступа к расчёту");
        }
        return calculation;
    }

    public ObjectNode readResult(Calculation calculation) {
        try {
            return (ObjectNode) objectMapper.readTree(calculation.getResultJson());
        } catch (Exception ex) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "RESULT_CORRUPTED",
                    "Не удалось прочитать сохранённый результат расчёта");
        }
    }

    private void persistFailure(User user, String sessionId, String strategyCode, Exchange exchange,
                                JsonNode params, Instant snapshotTs, ApiException ex) {
        try {
            Calculation calculation = new Calculation();
            calculation.setUser(user);
            calculation.setAnonymousSessionId(sessionId);
            calculation.setStrategyCode(strategyCode);
            calculation.setExchange(exchange);
            calculation.setParamsJson(toJson(params));
            calculation.setResultJson(toJson(Map.of(
                    "error", Map.of("code", ex.getCode(), "detail", ex.getMessage()))));
            calculation.setSnapshotTs(snapshotTs == null ? Instant.now() : snapshotTs);
            calculation.setAlgorithmVersion("1.0.0");
            calculation.setStatus("FAILED");
            calculation.setErrorMessage(ex.getMessage());
            calculation.setCreatedAt(Instant.now());
            calculationRepository.save(calculation);
        } catch (Exception persistError) {
            log.warn("Не удалось сохранить неуспешный расчёт: {}", persistError.getMessage());
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new IllegalStateException("Не удалось сериализовать расчёт", ex);
        }
    }

    private String defaultExchangeCode() {
        return exchangeRepository.findFirstByDefaultExchangeTrueAndEnabledTrue()
                .map(Exchange::getCode)
                .orElse("MOEX");
    }

}
