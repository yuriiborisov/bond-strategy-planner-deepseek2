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
import java.time.LocalDate;

@Entity
@Table(name = "issuer_factors")
@Getter
@Setter
@NoArgsConstructor
public class IssuerFactor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "issuer_id", nullable = false)
    private Issuer issuer;

    @Column(name = "factor_code", nullable = false, length = 50)
    private String factorCode;

    @Column(name = "value_num", precision = 18, scale = 4)
    private BigDecimal valueNum;

    @Column(name = "value_text")
    private String valueText;

    @Column(length = 20)
    private String unit;

    @Column(length = 20)
    private String period;

    @Column(name = "as_of", nullable = false)
    private LocalDate asOf;

    @Column(name = "source_document_id")
    private Long sourceDocumentId;

    private BigDecimal confidence;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}
