package com.bondplanner.service.calc;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PositionResult {

    private Integer rungIndex;
    private String leg;
    private LocalDate targetDate;
    private BondInfo bond;
    private int quantity;
    private BigDecimal amountRub;
    private BigDecimal weight;
    private BigDecimal ytmPct;
    private BigDecimal durationYears;
    private BigDecimal couponIncomeRub;
    private BigDecimal costRub;
    private BigDecimal residualRub;
    private BigDecimal lots;
    private String reason;
    private com.bondplanner.service.checks.AssetCheckView checks;
}
