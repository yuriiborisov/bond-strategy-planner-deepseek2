package com.bondplanner.service.calc;

import com.bondplanner.domain.model.CouponType;
import com.bondplanner.domain.model.OfferTreatment;
import com.bondplanner.service.rating.RatingNormalizer;
import com.bondplanner.service.rating.RatingView;
import com.bondplanner.service.universe.BondCandidate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Структурные фильтры вселенной (§3.7, §3.2).
 */
@Service
@RequiredArgsConstructor
public class UniverseFilter {

    private final RatingNormalizer ratingNormalizer;

    public boolean matchesStructural(BondCandidate candidate, FilterParams filters, RatingView rating) {
        var bond = candidate.bond();
        if (!"RUB".equalsIgnoreCase(bond.getCurrency())) {
            return false;
        }
        if (bond.isPerpetual() || bond.isAmortizing() && !filters.allowAmortizing()) {
            return false;
        }
        if (bond.getMaturityDate() == null && bond.getOfferDate() == null) {
            return false;
        }
        if (bond.getMaturityDate() != null && bond.getMaturityDate().isBefore(LocalDate.now())) {
            return false;
        }
        List<String> types = filters.bondTypes();
        if (types != null && !types.isEmpty() && bond.getBondType() != null
                && types.stream().noneMatch(t -> t.equalsIgnoreCase(bond.getBondType()))) {
            return false;
        }
        if (bond.getListLevel() != null && bond.getListLevel() > filters.minListLevel()) {
            return false;
        }
        if (bond.getIssueSize() != null && bond.getIssueSize().compareTo(filters.minIssueSizeRub()) < 0) {
            return false;
        }
        if (candidate.volumeRub().compareTo(filters.minAvgDailyVolumeRub()) < 0 && !filters.minAvgDailyVolumeRub().equals(BigDecimal.ZERO)) {
            return false;
        }
        if (bond.getCouponType() == CouponType.FLOATING && !filters.allowFloatingCoupon()) {
            return false;
        }
        if (bond.getCouponType() == CouponType.UNKNOWN) {
            return false;
        }
        return ratingMatches(rating, filters);
    }

    public boolean ratingMatches(RatingView rating, FilterParams filters) {
        if (rating == null) {
            return !filters.requireRating();
        }
        if (filters.requireRating() && rating.stale()) {
            return false;
        }
        BigDecimal min = ratingNormalizer.minimumScore(filters.minRating());
        return rating.score().compareTo(min) >= 0;
    }

    /** Эффективная дата погашения в прошлом — бумага не подходит. */
    public boolean notMatured(BondCandidate candidate, OfferTreatment treatment, OfferTreatment autoDefault,
                              LocalDate settlement) {
        LocalDate effective = candidate.effectiveMaturity(treatment, autoDefault);
        return effective != null && effective.isAfter(settlement);
    }
}
