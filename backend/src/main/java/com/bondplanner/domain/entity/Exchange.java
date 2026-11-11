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

@Entity
@Table(name = "exchanges")
@Getter
@Setter
@NoArgsConstructor
public class Exchange {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 20)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 50)
    private String provider;

    @Column(name = "base_url", nullable = false)
    private String baseUrl;

    @Column(nullable = false, length = 50)
    private String timezone = "Europe/Moscow";

    @Column(name = "is_default", nullable = false)
    private boolean defaultExchange = false;

    @Column(nullable = false)
    private boolean enabled = true;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "config", nullable = false)
    private String config = "{}";
}
