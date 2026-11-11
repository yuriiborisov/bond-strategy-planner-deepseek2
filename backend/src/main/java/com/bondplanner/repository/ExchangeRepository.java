package com.bondplanner.repository;

import com.bondplanner.domain.entity.Exchange;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ExchangeRepository extends JpaRepository<Exchange, Long> {

    Optional<Exchange> findByCodeIgnoreCase(String code);

    List<Exchange> findByEnabledTrueOrderByCode();

    Optional<Exchange> findFirstByDefaultExchangeTrueAndEnabledTrue();

    List<Exchange> findAllByOrderById();
}
