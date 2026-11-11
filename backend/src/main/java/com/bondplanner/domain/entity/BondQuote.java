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

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "bond_quotes")
@Getter
@Setter
@NoArgsConstructor
public class BondQuote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bond_id", nullable = false)
    private Bond bond;

    @Column(name = "snapshot_ts", nullable = false)
    private Instant snapshotTs;

    @Column(name = "price_pct")
    private BigDecimal pricePct;

    private BigDecimal nkd;

    private BigDecimal ytm;

    @Column(name = "ytm_to_offer")
    private BigDecimal ytmToOffer;

    @Column(name = "duration_years")
    private BigDecimal durationYears;

    private BigDecimal bid;

    private BigDecimal ask;

    @Column(name = "volume_rub")
    private BigDecimal volumeRub;

    @Column(name = "num_trades")
    private Integer numTrades;

    @Column(nullable = false, length = 50)
    private String source = "MOEX_ISS";
}
