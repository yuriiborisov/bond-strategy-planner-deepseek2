package com.bondplanner.repository;

import com.bondplanner.domain.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    @Modifying
    @Query("update RefreshToken t set t.revokedAt = :at where t.user.id = :userId and t.revokedAt is null")
    int revokeAllByUser(@Param("userId") Long userId, @Param("at") Instant at);

    @Modifying
    @Query("delete from RefreshToken t where t.expiresAt < :before")
    int deleteExpired(@Param("before") Instant before);
}
