package com.bondplanner.service.strategy;

import com.bondplanner.service.calc.CalculationResult;
import com.bondplanner.service.calc.Params;

public interface StrategyCalculator {

    String code();

    CalculationResult calculate(CalculationContext context, Params params);
}
