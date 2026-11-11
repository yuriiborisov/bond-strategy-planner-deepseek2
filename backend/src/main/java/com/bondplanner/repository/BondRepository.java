package com.bondplanner.repository;

import com.bondplanner.domain.entity.Bond;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BondRepository extends JpaRepository<Bond, Long> {

    Optional<Bond> findByExchangeIdAndSecid(Long exchangeId, String secid);

    Optional<Bond> findBySecid(String secid);

    List<Bond> findByExchangeIdAndStatus(Long exchangeId, String status);

    @Query("""
            select b from Bond b
            where (:exchangeId is null or b.exchange.id = :exchangeId)
              and (:type is null or b.bondType = :type)
              and (:query is null or lower(b.secid) like lower(concat('%', :query, '%'))
                   or lower(b.shortName) like lower(concat('%', :query, '%'))
                   or lower(b.issuerName) like lower(concat('%', :query, '%')))
            order by b.secid
            """)
    List<Bond> search(@Param("exchangeId") Long exchangeId,
                      @Param("type") String type,
                      @Param("query") String query,
                      org.springframework.data.domain.Pageable pageable);

    long countByExchangeId(Long exchangeId);
}
