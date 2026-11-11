package com.bondplanner.service.calc;

import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Типобезопасное чтение параметров расчёта из JSON с default-значениями.
 */
public final class Params {

    private final JsonNode root;

    private Params(JsonNode root) {
        this.root = root;
    }

    public static Params of(JsonNode node) {
        return new Params(node == null ? com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode() : node);
    }

    public JsonNode node() {
        return root;
    }

    public boolean has(String field) {
        JsonNode n = root.get(field);
        return n != null && !n.isNull();
    }

    public String text(String field, String defaultValue) {
        JsonNode n = root.get(field);
        if (n == null || n.isNull() || n.asText().isBlank()) {
            return defaultValue;
        }
        return n.asText();
    }

    public BigDecimal decimal(String field, BigDecimal defaultValue) {
        JsonNode n = root.get(field);
        if (n == null || n.isNull()) {
            return defaultValue;
        }
        if (n.isNumber()) {
            return n.decimalValue();
        }
        String text = n.asText().replace("%", "").replace(",", ".").trim();
        if (text.isEmpty()) {
            return defaultValue;
        }
        try {
            return new BigDecimal(text);
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }

    public int integer(String field, int defaultValue) {
        return decimal(field, BigDecimal.valueOf(defaultValue)).intValue();
    }

    public boolean bool(String field, boolean defaultValue) {
        JsonNode n = root.get(field);
        if (n == null || n.isNull()) {
            return defaultValue;
        }
        return n.isBoolean() ? n.booleanValue() : Boolean.parseBoolean(n.asText());
    }

    public List<String> stringList(String field, List<String> defaultValue) {
        JsonNode n = root.get(field);
        if (n == null || n.isNull() || !n.isArray()) {
            return defaultValue;
        }
        List<String> values = new ArrayList<>();
        n.forEach(item -> {
            if (item != null && !item.isNull()) {
                values.add(item.asText());
            }
        });
        return values.isEmpty() ? defaultValue : values;
    }

    public List<BigDecimal> decimalList(String field, List<BigDecimal> defaultValue) {
        JsonNode n = root.get(field);
        if (n == null || n.isNull() || !n.isArray()) {
            return defaultValue;
        }
        List<BigDecimal> values = new ArrayList<>();
        n.forEach(item -> values.add(item.decimalValue()));
        return values.isEmpty() ? defaultValue : values;
    }

    public Params child(String field) {
        JsonNode n = root.get(field);
        return new Params(n != null && n.isObject() ? n : com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode());
    }

    public Map<String, Object> asMap() {
        @SuppressWarnings("unchecked")
        Map<String, Object> map = com.fasterxml.jackson.databind.json.JsonMapper.builder().build()
                .convertValue(root, Map.class);
        return map;
    }
}
