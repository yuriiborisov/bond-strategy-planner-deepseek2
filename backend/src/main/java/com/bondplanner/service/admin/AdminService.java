package com.bondplanner.service.admin;

import com.bondplanner.api.error.ApiException;
import com.bondplanner.config.AppProperties;
import com.bondplanner.domain.entity.Exchange;
import com.bondplanner.domain.entity.Issuer;
import com.bondplanner.domain.entity.IssuerAlias;
import com.bondplanner.domain.entity.LlmAnalysis;
import com.bondplanner.domain.entity.RatingSource;
import com.bondplanner.domain.entity.SyncRun;
import com.bondplanner.domain.model.AnalysisStatus;
import com.bondplanner.domain.model.SyncStatus;
import com.bondplanner.repository.BondQuoteRepository;
import com.bondplanner.repository.BondRepository;
import com.bondplanner.repository.CalculationRepository;
import com.bondplanner.repository.ExchangeRepository;
import com.bondplanner.repository.IssuerAliasRepository;
import com.bondplanner.repository.IssuerRatingRepository;
import com.bondplanner.repository.IssuerRepository;
import com.bondplanner.repository.LlmAnalysisRepository;
import com.bondplanner.repository.PortfolioRepository;
import com.bondplanner.repository.RatingSourceRepository;
import com.bondplanner.repository.SyncRunRepository;
import com.bondplanner.service.ai.AiOrchestrator;
import com.bondplanner.service.rating.IssuerNames;
import com.bondplanner.service.rating.RatingImportService;
import com.bondplanner.service.sync.SyncService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminService {

    private final SyncService syncService;
    private final SyncRunRepository syncRunRepository;
    private final ExchangeRepository exchangeRepository;
    private final com.bondplanner.repository.StrategyRepository strategyRepository;
    private final BondRepository bondRepository;
    private final BondQuoteRepository quoteRepository;
    private final IssuerRepository issuerRepository;
    private final IssuerRatingRepository ratingRepository;
    private final IssuerAliasRepository aliasRepository;
    private final RatingSourceRepository ratingSourceRepository;
    private final RatingImportService ratingImportService;
    private final LlmAnalysisRepository analysisRepository;
    private final PortfolioRepository portfolioRepository;
    private final CalculationRepository calculationRepository;
    private final IssuerNames issuerNames;
    private final AiOrchestrator aiOrchestrator;
    private final AppProperties properties;
    private final ObjectMapper objectMapper;

    @Transactional
    public Map<String, Object> sync(String exchange, String kind) {
        SyncRun run = syncService.run(exchange == null ? "MOEX" : exchange,
                kind == null ? "FULL" : kind.toUpperCase());
        return syncRunDto(run);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> syncRuns() {
        return syncRunRepository.findTop50ByOrderByStartedAtDesc().stream().map(this::syncRunDto).toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> stats() {
        Map<String, Object> map = new LinkedHashMap<>();
        long bonds = bondRepository.count();
        long issuers = issuerRepository.count();
        long ratedIssuers = ratingRepository.count() > 0 ? issuerRepository.findAll().stream()
                .filter(i -> ratingRepository.countByIssuerId(i.getId()) > 0).count() : 0;
        map.put("bonds", bonds);
        map.put("issuers", issuers);
        map.put("ratings", ratingRepository.count());
        map.put("ratedIssuers", ratedIssuers);
        map.put("ratingCoveragePct", issuers == 0 ? 0 : Math.round(ratedIssuers * 1000.0 / issuers) / 10.0);
        map.put("calculations", calculationRepository.count());
        map.put("portfolios", portfolioRepository.count());
        map.put("aiAnalyses", analysisRepository.count());
        quoteRepository.findLatestSnapshotTs().ifPresentOrElse(ts -> {
            map.put("latestSnapshotTs", ts);
            map.put("snapshotAgeMinutes", ChronoUnit.MINUTES.between(ts, Instant.now()));
        }, () -> map.put("latestSnapshotTs", null));
        map.put("syncRuns24h", syncRunRepository.countByStartedAtAfter(Instant.now().minus(24, ChronoUnit.HOURS)));
        map.put("lastSync", syncRunRepository.findTop50ByOrderByStartedAtDesc().stream().findFirst()
                .map(this::syncRunDto).orElse(null));
        map.put("llmEnabled", properties.getLlm().isEnabled());
        map.put("llmProvider", properties.getLlm().getProvider());
        return map;
    }

    public Map<String, Object> importRatings(java.io.InputStream inputStream) {
        return ratingImportService.importCsv(inputStream);
    }

    @Transactional
    public Map<String, Object> syncRatingSource(String code) {
        RatingSource source = ratingSourceRepository.findByCode(code)
                .orElseThrow(() -> ApiException.notFound("Источник рейтингов не найден: " + code));
        if (!source.isEnabled()) {
            throw new ApiException(HttpStatus.CONFLICT, "CONFLICT", "Источник отключён: " + code);
        }
        SyncRun run = new SyncRun();
        run.setProviderCode(source.getCode());
        run.setKind("RATINGS");
        run.setStartedAt(Instant.now());
        run.setStatus(SyncStatus.RUNNING);
        syncRunRepository.save(run);
        try {
            JsonNode config = objectMapper.readTree(source.getParserConfig());
            String listUrl = config.path("listUrl").asText(null);
            if (listUrl == null || listUrl.isBlank()) {
                throw new IllegalStateException("В parser_config не задан listUrl");
            }
            String html = fetch(listUrl);
            Document document = Jsoup.parse(html, listUrl);
            String rowSelector = config.path("rowSelector").asText("table tbody tr");
            Elements rows = document.select(rowSelector);
            if (rows.isEmpty()) {
                throw new IllegalStateException("Селектор строк не нашёл данных: " + rowSelector);
            }
            JsonNode fields = config.path("fields");
            StringBuilder csv = new StringBuilder("issuer,inn,agency,rating,outlook,date\n");
            for (Element row : rows) {
                String issuer = cell(row, fields.path("issuer").asText(""));
                String rating = cell(row, fields.path("rating").asText(""));
                String outlook = cell(row, fields.path("outlook").asText(""));
                String date = cell(row, fields.path("date").asText(""));
                if (issuer == null || rating == null) {
                    continue;
                }
                csv.append(csvValue(issuer)).append(',')
                        .append(csvValue("")).append(',')
                        .append(csvValue(source.getCode())).append(',')
                        .append(csvValue(rating)).append(',')
                        .append(csvValue(outlook)).append(',')
                        .append(csvValue(date)).append('\n');
            }
            Map<String, Object> imported = ratingImportService.importCsv(
                    new ByteArrayInputStream(csv.toString().getBytes(StandardCharsets.UTF_8)));
            source.setLastRunAt(Instant.now());
            source.setLastStatus("SUCCESS");
            ratingSourceRepository.save(source);
            run.setStatus(SyncStatus.SUCCESS);
            run.setRowsUpserted((Integer) imported.getOrDefault("imported", 0));
            run.setMeta(objectMapper.writeValueAsString(imported));
            return imported;
        } catch (Exception ex) {
            source.setLastRunAt(Instant.now());
            source.setLastStatus("FAILED");
            ratingSourceRepository.save(source);
            run.setStatus(SyncStatus.FAILED);
            run.setErrorMessage(ex.getMessage());
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "RATING_SOURCE_UNAVAILABLE",
                    "Источник рейтингов недоступен: " + ex.getMessage());
        } finally {
            run.setFinishedAt(Instant.now());
            syncRunRepository.save(run);
        }
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> unmatchedIssuers() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Issuer issuer : issuerRepository.findAll()) {
            if (ratingRepository.countByIssuerId(issuer.getId()) == 0) {
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("issuerId", issuer.getId());
                map.put("name", issuer.getName());
                map.put("inn", issuer.getInn());
                map.put("reason", "NO_RATING");
                result.add(map);
            }
        }
        return result;
    }

    @Transactional
    public Map<String, Object> addAlias(Long issuerId, String alias) {
        if (alias == null || alias.isBlank()) {
            throw ApiException.badRequest("VALIDATION_ERROR", "alias не может быть пустым");
        }
        Issuer issuer = issuerRepository.findById(issuerId)
                .orElseThrow(() -> ApiException.notFound("Эмитент не найден: " + issuerId));
        String normalized = issuerNames.normalize(alias);
        if (aliasRepository.existsByIssuerIdAndNormalizedAlias(issuerId, normalized)) {
            throw new ApiException(HttpStatus.CONFLICT, "CONFLICT", "Такой алиас уже существует");
        }
        IssuerAlias entity = new IssuerAlias();
        entity.setIssuer(issuer);
        entity.setAlias(alias.trim());
        entity.setNormalizedAlias(normalized);
        entity.setSource("MANUAL");
        entity.setConfidence(java.math.BigDecimal.valueOf(100));
        aliasRepository.save(entity);
        return Map.of("id", entity.getId(), "issuerId", issuerId, "alias", entity.getAlias());
    }

    @Transactional
    public void deleteAlias(Long id) {
        aliasRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> ratingSources() {
        return ratingSourceRepository.findAllByOrderByPriorityAsc().stream().map(source -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", source.getId());
            map.put("code", source.getCode());
            map.put("name", source.getName());
            map.put("baseUrl", source.getBaseUrl());
            map.put("sourceType", source.getSourceType());
            map.put("enabled", source.isEnabled());
            map.put("priority", source.getPriority());
            map.put("cron", source.getCron());
            map.put("lastRunAt", source.getLastRunAt());
            map.put("lastStatus", source.getLastStatus());
            return map;
        }).toList();
    }

    @Transactional
    public Map<String, Object> updateRatingSource(Long id, Boolean enabled, Integer priority, String cron) {
        RatingSource source = ratingSourceRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Источник не найден: " + id));
        if (enabled != null) {
            source.setEnabled(enabled);
        }
        if (priority != null) {
            source.setPriority(priority);
        }
        if (cron != null) {
            source.setCron(cron);
        }
        ratingSourceRepository.save(source);
        return Map.of("id", source.getId(), "enabled", source.isEnabled(), "priority", source.getPriority());
    }

    @Transactional
    public Map<String, Object> updateExchange(Long id, Boolean enabled, Boolean isDefault) {
        Exchange exchange = exchangeRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Биржа не найдена: " + id));
        if (enabled != null) {
            exchange.setEnabled(enabled);
        }
        if (isDefault != null && isDefault) {
            exchangeRepository.findAll().forEach(item -> {
                if (!item.getId().equals(id) && item.isDefaultExchange()) {
                    item.setDefaultExchange(false);
                    exchangeRepository.save(item);
                }
            });
            exchange.setDefaultExchange(true);
        }
        exchangeRepository.save(exchange);
        return Map.of("id", exchange.getId(), "code", exchange.getCode(),
                "enabled", exchange.isEnabled(), "isDefault", exchange.isDefaultExchange());
    }

    @Transactional
    public Map<String, Object> updateStrategy(String code, Boolean enabled) {
        var strategy = strategyRepository.findById(code.toUpperCase())
                .orElseThrow(() -> ApiException.notFound("Стратегия не найдена: " + code));
        if (enabled != null) {
            strategy.setEnabled(enabled);
            strategyRepository.save(strategy);
        }
        return Map.of("code", strategy.getCode(), "enabled", strategy.isEnabled());
    }

    @Transactional(readOnly = true)
    public Map<String, Object> aiAnalyses(String status, int page, int size) {
        var pageable = PageRequest.of(Math.max(0, page), Math.min(Math.max(size, 1), 100));
        var analyses = analysisRepository.findByOrderByCreatedAtDesc(pageable);
        List<Map<String, Object>> content = analyses.getContent().stream()
                .filter(a -> status == null || status.isBlank() || a.getStatus().name().equalsIgnoreCase(status))
                .map(this::aiDto).toList();
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("content", content);
        response.put("page", analyses.getNumber());
        response.put("size", analyses.getSize());
        response.put("totalElements", analyses.getTotalElements());
        response.put("usage", aiOrchestrator.usage(0L));
        return response;
    }

    @Transactional
    public Map<String, Object> updateAiConfig(Boolean enabled, Integer dailyLimit) {
        if (enabled != null) {
            if (enabled && (properties.getLlm().getBaseUrl() == null || properties.getLlm().getBaseUrl().isBlank())) {
                throw ApiException.badRequest("LLM_CONFIG_INVALID",
                        "Нельзя включить AI без APP_LLM_BASE_URL");
            }
            properties.getLlm().setEnabled(enabled);
        }
        if (dailyLimit != null) {
            properties.getLlm().setDailyLimit(Math.max(0, dailyLimit));
        }
        return Map.of("enabled", properties.getLlm().isEnabled(),
                "dailyLimit", properties.getLlm().getDailyLimit(),
                "provider", properties.getLlm().getProvider(),
                "model", properties.getLlm().getModel());
    }

    private Map<String, Object> aiDto(LlmAnalysis analysis) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", analysis.getId());
        map.put("type", analysis.getAnalysisType().name());
        map.put("status", analysis.getStatus().name());
        map.put("provider", analysis.getProvider());
        map.put("model", analysis.getModel());
        map.put("tokensIn", analysis.getTokensIn());
        map.put("tokensOut", analysis.getTokensOut());
        map.put("costUsd", analysis.getCostUsd());
        map.put("latencyMs", analysis.getLatencyMs());
        map.put("createdAt", analysis.getCreatedAt());
        map.put("error", analysis.getErrorMessage());
        return map;
    }

    private Map<String, Object> syncRunDto(SyncRun run) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", run.getId());
        map.put("kind", run.getKind());
        map.put("provider", run.getProviderCode());
        map.put("exchange", run.getExchange() == null ? null : run.getExchange().getCode());
        map.put("status", run.getStatus().name());
        map.put("startedAt", run.getStartedAt());
        map.put("finishedAt", run.getFinishedAt());
        map.put("rowsUpserted", run.getRowsUpserted());
        map.put("error", run.getErrorMessage());
        return map;
    }

    private String fetch(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .header("User-Agent", "BondStrategyPlanner/1.0 (+https://bondplanner.local)")
                .GET()
                .build();
        HttpResponse<String> response = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build()
                .send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 300) {
            throw new IllegalStateException("HTTP " + response.statusCode());
        }
        return response.body();
    }

    private String cell(Element row, String selector) {
        if (selector == null || selector.isBlank()) {
            return null;
        }
        String attr = null;
        String pureSelector = selector;
        if (selector.contains("@")) {
            String[] parts = selector.split("@", 2);
            pureSelector = parts[0];
            attr = parts[1];
        }
        Element element = row.selectFirst(pureSelector);
        if (element == null) {
            return null;
        }
        String value = attr == null ? element.text() : element.attr(attr);
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String csvValue(String value) {
        if (value == null) {
            return "";
        }
        return '"' + value.replace("\"", "\"\"") + '"';
    }
}
