package com.bondplanner.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;

@Entity
@Table(name = "strategies")
@Getter
@Setter
@NoArgsConstructor
public class Strategy {

    @Id
    @Column(length = 20)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "short_description", nullable = false)
    private String shortDescription;

    @Column(name = "risk_level", nullable = false)
    private int riskLevel;

    @Column(name = "horizon_min_years")
    private BigDecimal horizonMinYears;

    @Column(name = "horizon_max_years")
    private BigDecimal horizonMaxYears;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "params_schema", nullable = false)
    private String paramsSchema;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "default_params", nullable = false)
    private String defaultParams;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 0;
}
