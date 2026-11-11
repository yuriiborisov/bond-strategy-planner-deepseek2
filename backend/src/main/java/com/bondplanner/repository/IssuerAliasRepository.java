package com.bondplanner.repository;

import com.bondplanner.domain.entity.IssuerAlias;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface IssuerAliasRepository extends JpaRepository<IssuerAlias, Long> {

    Optional<IssuerAlias> findByNormalizedAlias(String normalizedAlias);

    List<IssuerAlias> findByIssuerId(Long issuerId);

    boolean existsByIssuerIdAndNormalizedAlias(Long issuerId, String normalizedAlias);
}
