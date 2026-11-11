package com.bondplanner.service.strategy;

import com.bondplanner.service.universe.UniverseSnapshot;

/**
 * Всё, что нужно калькулятору стратегии: снимок данных и сервисы.
 */
public record CalculationContext(
        UniverseSnapshot snapshot,
        CalculationServices services) {
}
