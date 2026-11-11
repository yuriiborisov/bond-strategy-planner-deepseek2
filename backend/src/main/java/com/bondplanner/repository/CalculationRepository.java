package com.bondplanner.repository;

import com.bondplanner.domain.entity.Calculation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CalculationRepository extends JpaRepository<Calculation, Long> {

    Page<Calculation> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    Page<Calculation> findByAnonymousSessionIdOrderByCreatedAtDesc(String sessionId, Pageable pageable);

    Optional<Calculation> findByIdAndUserId(Long id, Long userId);

    Optional<Calculation> findByIdAndAnonymousSessionId(Long id, String sessionId);

    long countByUserId(Long userId);
}
