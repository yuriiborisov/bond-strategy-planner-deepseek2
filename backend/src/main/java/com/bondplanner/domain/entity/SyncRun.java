package com.bondplanner.domain.entity;

import com.bondplanner.domain.model.SyncStatus;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Table(name = "sync_runs")
@Getter
@Setter
@NoArgsConstructor
public class SyncRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "exchange_id")
    private Exchange exchange;

    @Column(name = "provider_code", length = 50)
    private String providerCode;

    @Column(nullable = false, length = 30)
    private String kind;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt = Instant.now();

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SyncStatus status = SyncStatus.RUNNING;

    @Column(name = "rows_upserted")
    private Integer rowsUpserted = 0;

    @Column(name = "error_message")
    private String errorMessage;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "meta")
    private String meta;
}
