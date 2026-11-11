package com.bondplanner.repository;

import com.bondplanner.domain.entity.PromptTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PromptTemplateRepository extends JpaRepository<PromptTemplate, Long> {

    Optional<PromptTemplate> findFirstByCodeAndLocaleAndActiveTrueOrderByCreatedAtDesc(String code, String locale);
}
