package com.bondplanner.service.rating;

import com.bondplanner.domain.model.Outlook;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Нормализация национальных рейтинговых шкал (§18.4).
 */
@Component
public class RatingNormalizer {

    private static final Map<String, BigDecimal> SCALE = new LinkedHashMap<>();
    private static final Pattern RATING_PATTERN = Pattern.compile(
            "(AAA|AA\\+|AA-|AA|A\\+|A-|A|BBB\\+|BBB-|BBB|BB\\+|BB-|BB|B\\+|B-|B|CCC|CC|C|D)");

    static {
        SCALE.put("AAA", BigDecimal.valueOf(100));
        SCALE.put("AA+", BigDecimal.valueOf(95));
        SCALE.put("AA", BigDecimal.valueOf(90));
        SCALE.put("AA-", BigDecimal.valueOf(85));
        SCALE.put("A+", BigDecimal.valueOf(80));
        SCALE.put("A", BigDecimal.valueOf(75));
        SCALE.put("A-", BigDecimal.valueOf(70));
        SCALE.put("BBB+", BigDecimal.valueOf(65));
        SCALE.put("BBB", BigDecimal.valueOf(60));
        SCALE.put("BBB-", BigDecimal.valueOf(55));
        SCALE.put("BB+", BigDecimal.valueOf(50));
        SCALE.put("BB", BigDecimal.valueOf(45));
        SCALE.put("BB-", BigDecimal.valueOf(40));
        SCALE.put("B+", BigDecimal.valueOf(35));
        SCALE.put("B", BigDecimal.valueOf(30));
        SCALE.put("B-", BigDecimal.valueOf(25));
        SCALE.put("CCC", BigDecimal.valueOf(15));
        SCALE.put("CC", BigDecimal.valueOf(10));
        SCALE.put("C", BigDecimal.valueOf(5));
        SCALE.put("D", BigDecimal.ZERO);
    }

    /** Извлекает буквенный код из строки вида "AA(RU)", "ruAA-", "A+.ru", "AAA". */
    public String extractCode(String rawRating) {
        if (rawRating == null) {
            return null;
        }
        String cleaned = rawRating.toUpperCase(Locale.ROOT)
                .replace("RU", "")
                .replace("RU)", ")")
                .replace("(", " ")
                .replace(")", " ");
        Matcher matcher = RATING_PATTERN.matcher(cleaned);
        return matcher.find() ? matcher.group(1) : null;
    }

    public BigDecimal scoreOf(String normalizedRating) {
        if (normalizedRating == null) {
            return null;
        }
        return SCALE.get(normalizedRating);
    }

    public BigDecimal minimumScore(String minRating) {
        String code = extractCode(minRating);
        BigDecimal score = code == null ? null : scoreOf(code);
        return score == null ? BigDecimal.valueOf(70) : score;
    }

    public Outlook parseOutlook(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.trim().toUpperCase(Locale.ROOT);
        if (value.startsWith("ПОЗ") || value.contains("POS")) {
            return Outlook.POSITIVE;
        }
        if (value.startsWith("НЕГ") || value.contains("NEG")) {
            return Outlook.NEGATIVE;
        }
        if (value.startsWith("РАЗ") || value.contains("DEV")) {
            return Outlook.DEVELOPING;
        }
        if (value.startsWith("СТАБ") || value.contains("STAB")) {
            return Outlook.STABLE;
        }
        try {
            return Outlook.valueOf(value);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    /** Прогноз меняет только ранжирование: POSITIVE +3, NEGATIVE −7, DEVELOPING −3. */
    public BigDecimal applyOutlook(BigDecimal baseScore, Outlook outlook) {
        if (baseScore == null) {
            return null;
        }
        BigDecimal delta = switch (outlook == null ? Outlook.STABLE : outlook) {
            case POSITIVE -> BigDecimal.valueOf(3);
            case NEGATIVE -> BigDecimal.valueOf(-7);
            case DEVELOPING -> BigDecimal.valueOf(-3);
            case STABLE -> BigDecimal.ZERO;
        };
        BigDecimal result = baseScore.add(delta);
        if (result.compareTo(BigDecimal.ZERO) < 0) {
            return BigDecimal.ZERO;
        }
        if (result.compareTo(BigDecimal.valueOf(100)) > 0) {
            return BigDecimal.valueOf(100);
        }
        return result;
    }

    public String category(BigDecimal score) {
        if (score == null) {
            return "UNKNOWN";
        }
        if (score.compareTo(BigDecimal.valueOf(55)) >= 0) {
            return "investment";
        }
        if (score.compareTo(BigDecimal.valueOf(15)) >= 0) {
            return "speculative";
        }
        return "high risk";
    }

    public boolean isDefault(String ratingCode) {
        return "D".equals(ratingCode) || "C".equals(ratingCode) || "CC".equals(ratingCode);
    }

    public Map<String, BigDecimal> scale() {
        return Map.copyOf(SCALE);
    }
}
