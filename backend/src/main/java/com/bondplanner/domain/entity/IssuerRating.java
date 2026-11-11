package com.bondplanner.domain.entity;

import com.bondplanner.domain.model.Outlook;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "issuer_ratings")
@Getter
@Setter
@NoArgsConstructor
public class IssuerRating {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "issuer_id", nullable = false)
    private Issuer issuer;

    @Column(nullable = false, length = 30)
    private String agency;

    @Column(nullable = false, length = 20)
    private String rating;

    @Column(name = "rating_normalized", nullable = false, length = 10)
    private String ratingNormalized;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal score;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Outlook outlook;

    @Column(name = "rating_scale", nullable = false, length = 20)
    private String scale = "NATIONAL";

    @Column(name = "rating_date", nullable = false)
    private LocalDate ratingDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_id")
    private RatingSource source;

    @Column(name = "source_url")
    private String sourceUrl;

    @Column(name = "fetched_at", nullable = false)
    private Instant fetchedAt = Instant.now();

    private BigDecimal confidence;

    public String agencyOrSource() {
        return agency;
    }
}
