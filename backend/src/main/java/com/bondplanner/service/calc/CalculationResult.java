package com.bondplanner.service.calc;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Результат расчёта. Стратег-специфичные блоки (rungs, scenarios, comparison)
 * кладутся в {@code details} и сериализуются в корень JSON.
 */
@Getter
@Builder
public class CalculationResult {

    private final String strategy;
    private final String exchange;
    private final Instant snapshotTs;
    private final String dataFreshness;
    private final Map<String, Object> summary;
    private final List<PositionResult> positions;
    private final List<CashFlowPoint> cashFlowSchedule;
    private final List<WarningItem> warnings;
    private final Map<String, Object> assumptions;

    @JsonIgnore
    private final Map<String, Object> details;

    @JsonIgnore
    public Map<String, Object> getDetails() {
        return details;
    }

    @JsonAnyGetter
    public Map<String, Object> details() {
        return details == null ? Map.of() : details;
    }
}
