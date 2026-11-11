package com.bondplanner.repository;

import com.bondplanner.domain.entity.PortfolioPosition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PortfolioPositionRepository extends JpaRepository<PortfolioPosition, Long> {

    List<PortfolioPosition> findByPortfolioIdOrderByRungIndexAscIdAsc(Long portfolioId);

    void deleteByPortfolioId(Long portfolioId);
}
