package com.bondplanner.service.checks;

import com.bondplanner.domain.model.CheckStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Результат детерминированной предпроверки бумаги (§19.5).
 */
public record AssetCheckView(
        String verdict,
        double score,
        List<Item> checks,
        String explanation,
        Instant asOf) {

    public record Item(
            String code,
            CheckStatus status,
            Object value,
            String threshold,
            String source,
            LocalDate asOf) {
    }

    public boolean rejected() {
        return "REJECTED".equals(verdict);
    }

    public boolean hasGateFailure() {
        return checks.stream().anyMatch(i -> i.status() == CheckStatus.FAIL);
    }
}
