package com.bondplanner.service.strategy;

import com.bondplanner.service.checks.AssetCheckService;
import com.bondplanner.service.calc.BondMetricsService;
import com.bondplanner.service.calc.RankingService;
import com.bondplanner.service.calc.UniverseFilter;
import com.bondplanner.service.cashflow.CashFlowEngine;
import com.bondplanner.service.rating.RatingNormalizer;
import com.bondplanner.service.tax.TaxCalculator;

public record CalculationServices(
        BondMetricsService metrics,
        RankingService ranking,
        UniverseFilter filter,
        AssetCheckService checks,
        CashFlowEngine cashFlow,
        TaxCalculator tax,
        RatingNormalizer ratingNormalizer) {
}
