package com.bondplanner.domain.entity;

import com.bondplanner.domain.model.FlowType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "bond_cashflows")
@Getter
@Setter
@NoArgsConstructor
public class BondCashflow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bond_id", nullable = false)
    private Bond bond;

    @Enumerated(EnumType.STRING)
    @Column(name = "flow_type", nullable = false, length = 20)
    private FlowType flowType;

    @Column(name = "flow_date", nullable = false)
    private LocalDate flowDate;

    @Column(name = "amount_per_bond")
    private BigDecimal amountPerBond;

    private BigDecimal percent;

    public BondCashflow(Bond bond, FlowType flowType, LocalDate flowDate, BigDecimal amountPerBond) {
        this.bond = bond;
        this.flowType = flowType;
        this.flowDate = flowDate;
        this.amountPerBond = amountPerBond;
    }
}
