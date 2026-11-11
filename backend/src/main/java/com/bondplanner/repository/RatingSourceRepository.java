package com.bondplanner.repository;

import com.bondplanner.domain.entity.RatingSource;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RatingSourceRepository extends JpaRepository<RatingSource, Long> {

    Optional<RatingSource> findByCode(String code);

    List<RatingSource> findAllByOrderByPriorityAsc();
}
