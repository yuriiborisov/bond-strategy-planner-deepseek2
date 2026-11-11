package com.bondplanner.repository;

import com.bondplanner.domain.entity.Issuer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface IssuerRepository extends JpaRepository<Issuer, Long> {

    Optional<Issuer> findByNormalizedName(String normalizedName);

    Optional<Issuer> findByInn(String inn);

    List<Issuer> findByNormalizedNameIn(Collection<String> normalizedNames);

    Page<Issuer> findByNameContainingIgnoreCaseOrNormalizedNameContainingIgnoreCase(
            String name, String normalizedName, Pageable pageable);

    List<Issuer> findByNameContainingIgnoreCase(String query);
}
