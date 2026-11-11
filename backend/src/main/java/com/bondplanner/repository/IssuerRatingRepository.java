package com.bondplanner.repository;

import com.bondplanner.domain.entity.IssuerRating;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface IssuerRatingRepository extends JpaRepository<IssuerRating, Long> {

    @Query("""
            select r from IssuerRating r
            where r.issuer.id in :issuerIds
            order by r.ratingDate desc, r.fetchedAt desc
            """)
    List<IssuerRating> findByIssuerIdsOrderByDateDesc(@Param("issuerIds") Collection<Long> issuerIds);

    List<IssuerRating> findByIssuerIdOrderByRatingDateDesc(Long issuerId);

    List<IssuerRating> findByIssuerIdInAndAgency(Collection<Long> issuerIds, String agency);

    Optional<IssuerRating> findFirstByIssuerIdAndAgencyAndRatingDate(Long issuerId, String agency, LocalDate ratingDate);

    long countByIssuerId(Long issuerId);
}
