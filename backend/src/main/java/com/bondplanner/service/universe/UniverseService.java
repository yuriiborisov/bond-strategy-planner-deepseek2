package com.bondplanner.service.universe;

import com.bondplanner.api.error.ApiException;
import com.bondplanner.config.AppProperties;
import com.bondplanner.domain.entity.Bond;
import com.bondplanner.domain.entity.BondCashflow;
import com.bondplanner.domain.entity.BondQuote;
import com.bondplanner.domain.entity.Exchange;
import com.bondplanner.domain.entity.IssuerFactor;
import com.bondplanner.repository.BondCashflowRepository;
import com.bondplanner.repository.BondQuoteRepository;
import com.bondplanner.repository.BondRepository;
import com.bondplanner.repository.ExchangeRepository;
import com.bondplanner.repository.IssuerFactorRepository;
import com.bondplanner.service.rating.RatingService;
import com.bondplanner.service.rating.RatingView;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Загрузка снимка вселенной: бумаги, последние котировки, купонные календари,
 * рейтинги эмитентов и факторы.
 */
@Service
@RequiredArgsConstructor
public class UniverseService {

    private final BondRepository bondRepository;
    private final BondQuoteRepository quoteRepository;
    private final BondCashflowRepository cashflowRepository;
    private final ExchangeRepository exchangeRepository;
    private final IssuerFactorRepository factorRepository;
    private final RatingService ratingService;
    private final AppProperties properties;

    public UniverseSnapshot load(String exchangeCode, int maxRatingAgeDays) {
        Exchange exchange = exchangeRepository.findByCodeIgnoreCase(exchangeCode)
                .orElseThrow(() -> ApiException.notFound("Биржа не найдена: " + exchangeCode));
        if (!exchange.isEnabled()) {
            throw new ApiException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                    "EXCHANGE_UNAVAILABLE", "Биржа отключена: " + exchangeCode);
        }
        List<Bond> bonds = bondRepository.findByExchangeIdAndStatus(exchange.getId(), "ACTIVE");
        if (bonds.size() > properties.getCalcMaxBonds()) {
            bonds = bonds.subList(0, properties.getCalcMaxBonds());
        }
        List<Long> bondIds = bonds.stream().map(Bond::getId).toList();
        Map<Long, BondQuote> quotes = bondIds.isEmpty() ? Map.of()
                : quoteRepository.findLatestByBondIds(bondIds).stream()
                        .collect(Collectors.toMap(q -> q.getBond().getId(), Function.identity(), (a, b) -> a));
        Map<Long, List<BondCashflow>> cashflows = bondIds.isEmpty() ? Map.of()
                : cashflowRepository.findByBondIdInOrderByFlowDateAsc(bondIds).stream()
                        .collect(Collectors.groupingBy(f -> f.getBond().getId(), LinkedHashMap::new, Collectors.toList()));

        List<Long> issuerIds = bonds.stream()
                .map(b -> b.getIssuer() == null ? null : b.getIssuer().getId())
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, RatingView> ratings = ratingService.resolve(issuerIds, maxRatingAgeDays);
        Map<Long, List<IssuerFactor>> factors = new HashMap<>();
        if (!issuerIds.isEmpty()) {
            for (IssuerFactor factor : factorRepository.findByIssuerIdInOrderByAsOfDesc(issuerIds)) {
                factors.computeIfAbsent(factor.getIssuer().getId(), k -> new ArrayList<>()).add(factor);
            }
        }

        List<BondCandidate> candidates = new ArrayList<>();
        for (Bond bond : bonds) {
            candidates.add(new BondCandidate(bond, quotes.get(bond.getId()), cashflows.getOrDefault(bond.getId(), List.of())));
        }

        Instant snapshotTs = quotes.values().stream()
                .map(BondQuote::getSnapshotTs)
                .filter(Objects::nonNull)
                .max(Instant::compareTo)
                .orElse(null);
        String freshness = "UNKNOWN";
        if (snapshotTs != null) {
            Duration age = Duration.between(snapshotTs, Instant.now());
            if (age.compareTo(Duration.ofHours(24)) <= 0) {
                freshness = "FRESH";
            } else if (age.compareTo(Duration.ofDays(7)) <= 0) {
                freshness = "STALE";
            } else {
                freshness = "EXPIRED";
            }
        }
        return new UniverseSnapshot(exchange.getCode(), snapshotTs, freshness, candidates, ratings, factors);
    }
}
