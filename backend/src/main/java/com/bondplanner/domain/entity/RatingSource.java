package com.bondplanner.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Table(name = "rating_sources")
@Getter
@Setter
@NoArgsConstructor
public class RatingSource {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 30)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "base_url", nullable = false)
    private String baseUrl;

    @Column(name = "source_type", nullable = false, length = 20)
    private String sourceType;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "parser_config", nullable = false)
    private String parserConfig = "{}";

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(nullable = false)
    private int priority = 100;

    @Column(length = 50)
    private String cron;

    @Column(name = "last_run_at")
    private Instant lastRunAt;

    @Column(name = "last_status", length = 20)
    private String lastStatus;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}
