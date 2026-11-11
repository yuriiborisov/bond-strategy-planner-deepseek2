package com.bondplanner.service.rating;

import com.bondplanner.domain.entity.IssuerRating;
import com.bondplanner.repository.IssuerRatingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class RatingService {

    private static final List<String> AGENCY_PRIORITY =
            List.of("ACRA", "EXPERT_RA", "NKR", "CBR", "MOEX_PROXY", "CBONDS", "REFINITIV", "MANUAL");

    private final IssuerRatingRepository repository;
    private final RatingNormalizer normalizer;

    /**
     * Актуальные рейтинги эмитентов. Для каждого эмитента берётся последний рейтинг
     * каждого агентства; в расчёт идёт консервативный (минимальный effective score).
     */
    public Map<Long, RatingView> resolve(Collection<Long> issuerIds, int maxAgeDays) {
        if (issuerIds == null || issuerIds.isEmpty()) {
            return Map.of();
        }
        LocalDate today = LocalDate.now();
        List<IssuerRating> all = repository.findByIssuerIdsOrderByDateDesc(issuerIds);
        Map<Long, Map<String, IssuerRating>> latestByAgency = new LinkedHashMap<>();
        for (IssuerRating rating : all) {
            if (rating.getIssuer() == null) {
                continue;
            }
            LocalDate date = rating.getRatingDate();
            boolean invalid = date == null || date.isAfter(today) || date.isBefore(today.minusYears(3));
            if (invalid) {
                continue;
            }
            latestByAgency
                    .computeIfAbsent(rating.getIssuer().getId(), k -> new LinkedHashMap<>())
                    .putIfAbsent(rating.getAgency(), rating);
        }

        Map<Long, RatingView> result = new HashMap<>();
        for (Map.Entry<Long, Map<String, IssuerRating>> entry : latestByAgency.entrySet()) {
            RatingView best = null;
            for (IssuerRating rating : entry.getValue().values()) {
                RatingView view = toView(rating, maxAgeDays, today);
                if (best == null || isBetter(view, best)) {
                    best = view;
                }
            }
            if (best != null) {
                result.put(entry.getKey(), best);
            }
        }
        return result;
    }

    private boolean isBetter(RatingView candidate, RatingView current) {
        int byScore = candidate.effectiveScore().compareTo(current.effectiveScore());
        if (byScore != 0) {
            return byScore < 0;
        }
        return agencyRank(candidate.agency()) < agencyRank(current.agency());
    }

    private int agencyRank(String agency) {
        int index = AGENCY_PRIORITY.indexOf(agency);
        return index < 0 ? 100 : index;
    }

    public RatingView toView(IssuerRating rating, int maxAgeDays, LocalDate today) {
        BigDecimal base = rating.getScore();
        BigDecimal effective = normalizer.applyOutlook(base, rating.getOutlook());
        boolean stale = rating.getRatingDate().isBefore(today.minusDays(maxAgeDays));
        boolean invalid = rating.getRatingDate().isAfter(today) || rating.getRatingDate().isBefore(today.minusYears(3));
        return new RatingView(
                rating.getIssuer().getId(),
                rating.getAgency(),
                rating.getRating(),
                rating.getRatingNormalized(),
                base,
                effective,
                rating.getOutlook(),
                rating.getRatingDate(),
                rating.getSourceUrl(),
                stale,
                invalid);
    }

    public List<RatingView> history(Long issuerId) {
        List<RatingView> views = new ArrayList<>();
        LocalDate today = LocalDate.now();
        for (IssuerRating rating : repository.findByIssuerIdOrderByRatingDateDesc(issuerId)) {
            views.add(toView(rating, 180, today));
        }
        views.sort(Comparator.comparing(RatingView::ratingDate).reversed());
        return views;
    }
}
