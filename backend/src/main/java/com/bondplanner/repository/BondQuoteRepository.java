package com.bondplanner.repository;

import com.bondplanner.domain.entity.BondQuote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface BondQuoteRepository extends JpaRepository<BondQuote, Long> {

    @Query("""
            select q from BondQuote q
            where q.bond.id in :bondIds
              and q.snapshotTs = (select max(q2.snapshotTs) from BondQuote q2 where q2.bond.id = q.bond.id)
            """)
    List<BondQuote> findLatestByBondIds(@Param("bondIds") Collection<Long> bondIds);

    @Query("select max(q.snapshotTs) from BondQuote q")
    Optional<Instant> findLatestSnapshotTs();

    List<BondQuote> findByBondIdOrderBySnapshotTsDesc(Long bondId, org.springframework.data.domain.Pageable pageable);

    List<BondQuote> findBySnapshotTs(Instant snapshotTs);

    List<BondQuote> findByBondIdAndSnapshotTs(Long bondId, Instant snapshotTs);
}
