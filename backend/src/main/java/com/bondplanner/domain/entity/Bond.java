package com.bondplanner.domain.entity;

import com.bondplanner.domain.model.CouponType;
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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "bonds")
@Getter
@Setter
@NoArgsConstructor
public class Bond {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "exchange_id", nullable = false)
    private Exchange exchange;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "issuer_id")
    private Issuer issuer;

    @Column(nullable = false, length = 50)
    private String secid;

    @Column(name = "board_id", nullable = false, length = 20)
    private String boardId;

    @Column(length = 20)
    private String isin;

    @Column(name = "short_name")
    private String shortName;

    @Column(name = "issuer")
    private String issuerName;

    @Column(name = "bond_type", length = 30)
    private String bondType;

    @Enumerated(EnumType.STRING)
    @Column(name = "coupon_type", nullable = false, length = 20)
    private CouponType couponType = CouponType.FIXED;

    @Column(nullable = false, length = 3)
    private String currency = "RUB";

    @Column(name = "face_value")
    private BigDecimal faceValue;

    @Column(name = "coupon_value")
    private BigDecimal couponValue;

    @Column(name = "coupon_period_days")
    private Integer couponPeriodDays;

    @Column(name = "maturity_date")
    private LocalDate maturityDate;

    @Column(name = "offer_date")
    private LocalDate offerDate;

    @Column(name = "list_level")
    private Integer listLevel;

    @Column(name = "lot_size", nullable = false)
    private int lotSize = 1;

    @Column(name = "issue_size")
    private BigDecimal issueSize;

    @Column(name = "is_amortizing", nullable = false)
    private boolean amortizing = false;

    @Column(name = "is_perpetual", nullable = false)
    private boolean perpetual = false;

    @Column(nullable = false, length = 20)
    private String status = "ACTIVE";

    @Column(name = "first_seen_at", nullable = false)
    private Instant firstSeenAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
