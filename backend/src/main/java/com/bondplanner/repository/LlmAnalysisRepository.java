package com.bondplanner.repository;

import com.bondplanner.domain.entity.LlmAnalysis;
import com.bondplanner.domain.model.AnalysisStatus;
import com.bondplanner.domain.model.AnalysisType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface LlmAnalysisRepository extends JpaRepository<LlmAnalysis, Long> {

    Page<LlmAnalysis> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    Page<LlmAnalysis> findByUserIdAndAnalysisTypeOrderByCreatedAtDesc(Long userId, AnalysisType type, Pageable pageable);

    Page<LlmAnalysis> findByAnalysisTypeOrderByCreatedAtDesc(AnalysisType type, Pageable pageable);

    Page<LlmAnalysis> findByOrderByCreatedAtDesc(Pageable pageable);

    Optional<LlmAnalysis> findFirstByPromptHashAndStatusOrderByCreatedAtDesc(String promptHash, AnalysisStatus status);

    List<LlmAnalysis> findByUserIdAndCreatedAtAfter(Long userId, Instant since);

    long countByUserIdAndCreatedAtAfter(Long userId, Instant since);
}
