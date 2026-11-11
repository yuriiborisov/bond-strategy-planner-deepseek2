package com.bondplanner.api.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;

public record CalculationRequest(
        @NotBlank String strategy,
        String exchange,
        JsonNode params,
        Boolean allowStale) {
}
