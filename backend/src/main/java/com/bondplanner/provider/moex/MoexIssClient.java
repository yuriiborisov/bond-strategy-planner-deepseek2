package com.bondplanner.provider.moex;

import com.bondplanner.config.AppProperties;
import com.bondplanner.domain.model.CouponType;
import com.bondplanner.provider.ExchangeProvider;
import com.bondplanner.provider.RawBond;
import com.bondplanner.provider.RawCashflow;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Клиент MOEX ISS (§8.2): securities + marketdata, bondization, пагинация, retry, rate limit.
 */
@Slf4j
@Component
public class MoexIssClient implements ExchangeProvider {

    private static final List<String> BOARDS = List.of("TQOB", "TQCB", "TQMB");
    private static final int PAGE_SIZE = 100;

    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final HttpClient httpClient;
    private final long minIntervalMs;
    private long lastRequestAt = 0;

    public MoexIssClient(AppProperties properties, ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.baseUrl = properties.getMoexBaseUrl();
        this.minIntervalMs = Math.max(0, properties.getRatingsMinIntervalMs());
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @Override
    public String code() {
        return "MOEX_ISS";
    }

    @Override
    public List<RawBond> fetchBonds() {
        List<RawBond> result = new ArrayList<>();
        for (String board : BOARDS) {
            try {
                result.addAll(fetchBoard(board));
            } catch (Exception ex) {
                log.warn("MOEX board {} недоступна: {}", board, ex.getMessage());
            }
        }
        return result;
    }

    /**
     * MOEX ISS может игнорировать limit/start для блока securities и возвращать весь рынок
     * на каждой странице. Поэтому останавливаемся, когда страница не добавляет новых бумаг.
     */
    private List<RawBond> fetchBoard(String board) {
        List<RawBond> bonds = new ArrayList<>();
        java.util.Set<String> seenSecids = new java.util.HashSet<>();
        int start = 0;
        for (int page = 0; page < 100; page++) {
            String url = baseUrl + "/engines/stock/markets/bonds/boards/" + board
                    + "/securities.json?iss.meta=off&iss.only=securities,marketdata&limit=" + PAGE_SIZE
                    + "&start=" + start;
            JsonNode root = getJson(url);
            JsonNode securities = root.path("securities");
            JsonNode marketdata = root.path("marketdata");
            List<String> secColumns = columns(securities);
            List<String> mdColumns = columns(marketdata);
            if (!securities.has("data") || securities.get("data").isEmpty()) {
                break;
            }
            Map<String, Map<String, Object>> marketBySecid = new HashMap<>();
            for (JsonNode row : marketdata.path("data")) {
                Map<String, Object> map = toMap(mdColumns, row);
                Object secid = map.get("SECID");
                if (secid != null) {
                    marketBySecid.put(String.valueOf(secid), map);
                }
            }
            int added = 0;
            for (JsonNode row : securities.get("data")) {
                Map<String, Object> map = toMap(secColumns, row);
                String secid = str(map.get("SECID"));
                if (secid == null || !seenSecids.add(secid)) {
                    continue;
                }
                added++;
                Map<String, Object> md = marketBySecid.getOrDefault(secid, Map.of());
                bonds.add(mapBond(board, map, md));
            }
            if (added == 0) {
                break;
            }
            start += Math.max(PAGE_SIZE, securities.get("data").size());
        }
        return bonds;
    }

    private RawBond mapBond(String board, Map<String, Object> s, Map<String, Object> md) {
        String matDateRaw = str(s.get("MATDATE"));
        String bondType = switch (board) {
            case "TQOB" -> "OFZ";
            case "TQCB" -> "CORPORATE";
            case "TQMB" -> "MUNICIPAL";
            default -> "OTHER";
        };
        String couponType = str(s.get("COUPONTYPE"));
        CouponType type = couponType == null ? CouponType.FIXED
                : couponType.toLowerCase().contains("плава") || couponType.toLowerCase().contains("float")
                        ? CouponType.FLOATING : CouponType.FIXED;
        BigDecimal price = decimal(firstNonNull(md.get("LAST"), md.get("PREVPRICE"), s.get("PREVPRICE")));
        BigDecimal nkd = decimal(firstNonNull(md.get("ACCRUEDINT"), s.get("ACCRUEDINT")));
        BigDecimal ytm = decimal(firstNonNull(md.get("YIELD"), s.get("YIELD")));
        BigDecimal duration = decimal(firstNonNull(md.get("DURATION"), s.get("DURATION")));
        BigDecimal volume = decimal(md.get("VALTODAY"));
        Integer trades = integer(md.get("NUMTRADES"));
        Integer lotSize = integer(s.get("LOTSIZE"));
        return new RawBond(
                str(s.get("SECID")),
                str(s.get("BOARDID")) == null ? board : str(s.get("BOARDID")),
                str(s.get("ISIN")),
                str(s.get("SHORTNAME")),
                str(s.get("EMITTER_TITLE")),
                bondType,
                type.name(),
                "RUB",
                decimal(s.get("FACEVALUE")),
                decimal(s.get("COUPONVALUE")),
                integer(s.get("COUPONPERIOD")),
                parseDate(matDateRaw),
                parseDate(str(s.get("OFFERDATE"))),
                integer(s.get("LISTLEVEL")),
                lotSize == null ? 1 : Math.max(1, lotSize),
                decimal(s.get("ISSUESIZE")),
                false,
                matDateRaw == null || matDateRaw.isBlank(),
                price,
                nkd,
                ytm,
                decimal(md.get("YIELDTOOFFER")),
                duration,
                volume,
                trades);
    }

    @Override
    public List<RawCashflow> fetchCashflows(String secid) {
        String url = baseUrl + "/statistics/engines/stock/markets/bonds/bondization/" + secid + ".json?iss.meta=off";
        JsonNode root = getJson(url);
        List<RawCashflow> flows = new ArrayList<>();
        for (JsonNode row : root.path("coupons").path("data")) {
            List<String> columns = columns(root.path("coupons"));
            Map<String, Object> map = toMap(columns, row);
            LocalDate date = parseDate(str(map.get("COUPONDATE")));
            BigDecimal value = decimal(map.get("COUPONVALUE"));
            if (date != null && value != null) {
                flows.add(new RawCashflow(secid, "COUPON", date, value));
            }
        }
        for (JsonNode row : root.path("amortizations").path("data")) {
            List<String> columns = columns(root.path("amortizations"));
            Map<String, Object> map = toMap(columns, row);
            LocalDate date = parseDate(str(map.get("AMORTIZATIONDATE")));
            BigDecimal value = decimal(map.get("AMORTIZATIONVALUE"));
            if (date != null && value != null) {
                flows.add(new RawCashflow(secid, "AMORTIZATION", date, value));
            }
        }
        for (JsonNode row : root.path("offers").path("data")) {
            List<String> columns = columns(root.path("offers"));
            Map<String, Object> map = toMap(columns, row);
            LocalDate date = parseDate(str(map.get("OFFERDATE")));
            if (date != null) {
                flows.add(new RawCashflow(secid, "OFFER", date, null));
            }
        }
        return flows;
    }

    private JsonNode getJson(String url) {
        Exception last = null;
        for (int attempt = 0; attempt < 4; attempt++) {
            try {
                throttle();
                HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                        .timeout(Duration.ofSeconds(15))
                        .header("User-Agent", "BondStrategyPlanner/1.0 (+https://bondplanner.local)")
                        .header("Accept", "application/json")
                        .GET()
                        .build();
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) {
                    return objectMapper.readTree(response.body());
                }
                if (response.statusCode() == 429 || response.statusCode() >= 500) {
                    sleep(1000L * (1L << attempt));
                    last = new IllegalStateException("MOEX ISS status " + response.statusCode());
                    continue;
                }
                throw new IllegalStateException("MOEX ISS status " + response.statusCode() + " для " + url);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Запрос к MOEX ISS прерван", ex);
            } catch (Exception ex) {
                last = ex;
                sleep(1000L * (1L << attempt));
            }
        }
        throw new IllegalStateException("MOEX ISS недоступен: " + (last == null ? "unknown" : last.getMessage()), last);
    }

    private void throttle() {
        long now = System.currentTimeMillis();
        long waitMs = lastRequestAt + minIntervalMs - now;
        if (waitMs > 0) {
            sleep(waitMs);
        }
        lastRequestAt = System.currentTimeMillis();
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private List<String> columns(JsonNode node) {
        List<String> columns = new ArrayList<>();
        for (JsonNode column : node.path("columns")) {
            columns.add(column.asText());
        }
        return columns;
    }

    private Map<String, Object> toMap(List<String> columns, JsonNode row) {
        Map<String, Object> map = new HashMap<>();
        for (int i = 0; i < columns.size() && i < row.size(); i++) {
            JsonNode value = row.get(i);
            map.put(columns.get(i), value.isNull() ? null : value.isNumber() ? value.numberValue() : value.asText());
        }
        return map;
    }

    private Object firstNonNull(Object... values) {
        for (Object value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private String str(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private BigDecimal decimal(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal bigDecimal) {
            return bigDecimal;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return new BigDecimal(text);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private Integer integer(Object value) {
        BigDecimal decimal = decimal(value);
        return decimal == null ? null : decimal.intValue();
    }

    private LocalDate parseDate(String value) {
        if (value == null || value.isBlank() || "0000-00-00".equals(value)) {
            return null;
        }
        try {
            return LocalDate.parse(value.substring(0, 10));
        } catch (Exception ex) {
            return null;
        }
    }
}
