package com.bondplanner.repository;

import com.bondplanner.domain.entity.BondCashflow;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface BondCashflowRepository extends JpaRepository<BondCashflow, Long> {

    List<BondCashflow> findByBondIdInOrderByFlowDateAsc(Collection<Long> bondIds);

    List<BondCashflow> findByBondIdOrderByFlowDateAsc(Long bondId);

    void deleteByBondId(Long bondId);
}
