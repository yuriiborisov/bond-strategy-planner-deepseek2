package com.bondplanner.provider;

import java.math.BigDecimal;
import java.time.LocalDate;

public record RawCashflow(String secid, String flowType, LocalDate date, BigDecimal amountPerBond) {
}
