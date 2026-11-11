package com.bondplanner.api.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.util.List;

public record StrategyDto(
        String code,
        String name,
        String shortDescription,
        int riskLevel,
        BigDecimal horizonMinYears,
        BigDecimal horizonMaxYears,
        JsonNode paramsSchema,
        JsonNode defaultParams,
        boolean enabled,
        int sortOrder,
        List<String> bestFor,
        List<String> notFor,
        List<String> pros,
        List<String> cons) {
}
