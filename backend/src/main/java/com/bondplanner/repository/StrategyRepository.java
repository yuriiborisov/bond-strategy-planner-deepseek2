package com.bondplanner.repository;

import com.bondplanner.domain.entity.Strategy;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StrategyRepository extends JpaRepository<Strategy, String> {

    List<Strategy> findAllByOrderBySortOrderAsc();

    Optional<Strategy> findByCodeAndEnabledTrue(String code);
}
