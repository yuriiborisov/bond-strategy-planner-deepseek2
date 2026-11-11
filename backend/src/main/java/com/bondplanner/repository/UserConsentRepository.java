package com.bondplanner.repository;

import com.bondplanner.domain.entity.UserConsent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserConsentRepository extends JpaRepository<UserConsent, Long> {

    List<UserConsent> findByUserIdOrderByGrantedAtDesc(Long userId);

    Optional<UserConsent> findFirstByUserIdAndConsentTypeAndRevokedAtIsNullOrderByGrantedAtDesc(
            Long userId, String consentType);
}
