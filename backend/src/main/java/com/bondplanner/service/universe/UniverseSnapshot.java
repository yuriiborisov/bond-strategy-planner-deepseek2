package com.bondplanner.service.universe;

import com.bondplanner.domain.entity.IssuerFactor;
import com.bondplanner.service.rating.RatingView;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Снимок рыночных данных на момент расчёта: бумаги, котировки, купонные календари,
 * рейтинги эмитентов и фундаментальные факторы.
 */
public record UniverseSnapshot(
        String exchangeCode,
        Instant snapshotTs,
        String dataFreshness,
        List<BondCandidate> bonds,
        Map<Long, RatingView> ratings,
        Map<Long, List<IssuerFactor>> factors) {
}
