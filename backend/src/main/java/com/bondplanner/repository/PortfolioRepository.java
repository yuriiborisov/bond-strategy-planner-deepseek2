package com.bondplanner.repository;

import com.bondplanner.domain.entity.Portfolio;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PortfolioRepository extends JpaRepository<Portfolio, Long> {

    Page<Portfolio> findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(Long userId, Pageable pageable);

    Optional<Portfolio> findByIdAndUserIdAndDeletedAtIsNull(Long id, Long userId);

    long countByUserIdAndDeletedAtIsNull(Long userId);
}
