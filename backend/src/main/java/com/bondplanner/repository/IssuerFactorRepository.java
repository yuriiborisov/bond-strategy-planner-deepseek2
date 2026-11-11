package com.bondplanner.repository;

import com.bondplanner.domain.entity.IssuerFactor;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface IssuerFactorRepository extends JpaRepository<IssuerFactor, Long> {

    List<IssuerFactor> findByIssuerIdInOrderByAsOfDesc(Collection<Long> issuerIds);

    List<IssuerFactor> findByIssuerIdOrderByAsOfDesc(Long issuerId);
}
