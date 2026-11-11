package com.bondplanner.repository;

import com.bondplanner.domain.entity.SyncRun;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface SyncRunRepository extends JpaRepository<SyncRun, Long> {

    List<SyncRun> findTop50ByOrderByStartedAtDesc();

    List<SyncRun> findTop20ByProviderCodeAndKindOrderByStartedAtDesc(String providerCode, String kind);

    long countByStartedAtAfter(Instant since);
}
