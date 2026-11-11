package com.bondplanner.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "portfolio_positions")
@Getter
@Setter
@NoArgsConstructor
public class PortfolioPosition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "portfolio_id", nullable = false)
    private Portfolio portfolio;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bond_id", nullable = false)
    private Bond bond;

    @Column(name = "rung_index")
    private Integer rungIndex;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "amount_rub", nullable = false, precision = 18, scale = 2)
    private BigDecimal amountRub;

    @Column(name = "target_date")
    private LocalDate targetDate;

    @Column(precision = 8, scale = 6)
    private BigDecimal weight;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "meta")
    private String meta;
}
