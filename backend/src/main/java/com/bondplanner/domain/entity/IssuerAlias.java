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
@Table(name = "issuer_aliases")
@Getter
@Setter
@NoArgsConstructor
public class IssuerAlias {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "issuer_id", nullable = false)
    private Issuer issuer;

    @Column(nullable = false)
    private String alias;

    @Column(name = "normalized_alias", nullable = false)
    private String normalizedAlias;

    @Column(nullable = false, length = 30)
    private String source = "AUTO";

    private BigDecimal confidence;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}
