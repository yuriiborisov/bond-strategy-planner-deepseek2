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

import java.time.Instant;

@Entity
@Table(name = "calculations")
@Getter
@Setter
@NoArgsConstructor
public class Calculation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "anonymous_session_id", length = 64)
    private String anonymousSessionId;

    @Column(name = "strategy_code", nullable = false, length = 20)
    private String strategyCode;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "exchange_id", nullable = false)
    private Exchange exchange;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "params_json", nullable = false)
    private String paramsJson;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "result_json", nullable = false)
    private String resultJson;

    @Column(name = "snapshot_ts", nullable = false)
    private Instant snapshotTs;

    @Column(name = "algorithm_version", nullable = false, length = 20)
    private String algorithmVersion;

    @Column(nullable = false, length = 20)
    private String status = "DONE";

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}
