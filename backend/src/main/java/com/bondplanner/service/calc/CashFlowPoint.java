package com.bondplanner.service.calc;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CashFlowPoint(LocalDate date, String type, BigDecimal amountRub) {
}
