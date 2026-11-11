package com.bondplanner.service.calc;

import com.bondplanner.config.AppProperties;
import com.bondplanner.domain.model.CouponType;
import com.bondplanner.domain.model.OfferTreatment;
import com.bondplanner.service.rating.RatingView;
import com.bondplanner.service.universe.BondCandidate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Ранжирование бумаг по score (§3.7).
 */
@Service
@RequiredArgsConstructor
public class RankingService {

    private final BondMetricsService metricsService;
    private final AppProperties properties;

    public record ScoredBond(
            BondCandidate candidate,
            BondMetricsService.BondMetrics metrics,
            double score,
            double maturityFit) {
    }

    public List<ScoredBond> rank(List<BondCandidate> candidates,
                                 Map<Long, RatingView> ratings,
                                 FilterParams filters,
                                 OfferTreatment treatment,
                                 OfferTreatment autoDefault,
                                 LocalDate settlement,
                                 LocalDate targetDate,
                                 int toleranceDays) {
        List<BondCandidate> priced = new ArrayList<>();
        List<BondMetricsService.BondMetrics> metricsList = new ArrayList<>();
        for (BondCandidate candidate : candidates) {
            BondMetricsService.BondMetrics metrics = metricsService.metrics(
                    candidate, treatment, autoDefault, settlement, filters.commissionPct());
            if (metrics == null || metrics.ytmPct() == null) {
                continue;
            }
            priced.add(candidate);
            metricsList.add(metrics);
        }
        if (priced.isEmpty()) {
            return List.of();
        }
        double[] ytm = metricsList.stream().mapToDouble(m -> m.ytmPct().doubleValue()).toArray();
        double[] liquidity = priced.stream().mapToDouble(c -> Math.log1p(c.volumeRub().doubleValue())).toArray();
        double[] issueSize = priced.stream()
                .mapToDouble(c -> Math.log1p(c.bond().getIssueSize() == null ? 0 : c.bond().getIssueSize().doubleValue()))
                .toArray();
        double[] ytmNorm = normalize(ytm);
        double[] liquidityNorm = normalize(liquidity);
        double[] issueNorm = normalize(issueSize);

        AppProperties.Scoring weights = properties.getScoring();
        List<ScoredBond> result = new ArrayList<>();
        for (int i = 0; i < priced.size(); i++) {
            BondCandidate candidate = priced.get(i);
            BondMetricsService.BondMetrics metrics = metricsList.get(i);
            RatingView rating = candidate.bond().getIssuer() == null ? null
                    : ratings.get(candidate.bond().getIssuer().getId());
            double ratingNorm = rating == null ? 0 : rating.effectiveScore().doubleValue() / 100;
            double fit = maturityFit(metrics.effectiveMaturity(), targetDate, toleranceDays);
            double score = weights.getWeightYtm() * ytmNorm[i]
                    + weights.getWeightLiquidity() * liquidityNorm[i]
                    + weights.getWeightRating() * ratingNorm
                    + weights.getWeightMaturity() * fit
                    + weights.getWeightIssueSize() * issueNorm[i]
                    - penalties(candidate, targetDate, filters);
            result.add(new ScoredBond(candidate, metrics, score, fit));
        }
        result.sort(Comparator.comparingDouble(ScoredBond::score).reversed()
                .thenComparing(b -> b.metrics().ytmPct(), Comparator.reverseOrder()));
        return result;
    }

    private double penalties(BondCandidate candidate, LocalDate targetDate, FilterParams filters) {
        double penalty = 0;
        var bond = candidate.bond();
        if (targetDate != null && bond.getOfferDate() != null && bond.getOfferDate().isBefore(targetDate)) {
            penalty += 0.15;
        }
        if (bond.getCouponType() == CouponType.FLOATING) {
            penalty += 0.2;
        }
        if (bond.isAmortizing()) {
            penalty += 0.05;
        }
        if (bond.getIssueSize() != null && bond.getIssueSize().compareTo(filters.minIssueSizeRub()) < 0) {
            penalty += 0.1;
        }
        return penalty;
    }

    public double maturityFit(LocalDate maturity, LocalDate targetDate, int toleranceDays) {
        if (maturity == null || targetDate == null) {
            return 0.5;
        }
        long days = Math.abs(ChronoUnit.DAYS.between(maturity, targetDate));
        double tolerance = Math.max(1, toleranceDays);
        return 1 - Math.min(1, days / tolerance);
    }

    private double[] normalize(double[] values) {
        double min = Double.MAX_VALUE;
        double max = -Double.MAX_VALUE;
        for (double v : values) {
            min = Math.min(min, v);
            max = Math.max(max, v);
        }
        double[] result = new double[values.length];
        if (max - min < 1e-12) {
            for (int i = 0; i < values.length; i++) {
                result[i] = 0.5;
            }
            return result;
        }
        for (int i = 0; i < values.length; i++) {
            result[i] = (values[i] - min) / (max - min);
        }
        return result;
    }

    public BigDecimal normalizedScore(double score) {
        return BigDecimal.valueOf(Math.max(0, Math.min(1, score))).setScale(4, java.math.RoundingMode.HALF_UP);
    }
}
