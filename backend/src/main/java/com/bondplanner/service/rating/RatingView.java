package com.bondplanner.service.rating;

import com.bondplanner.domain.model.Outlook;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Нормализованный рейтинг эмитента для расчётов и UI.
 *
 * @param stale     рейтинг старше допустимого возраста
 * @param invalid   рейтинг в будущем или старше 3 лет — использовать нельзя
 * @param effective итоговый (консервативный) score с учётом прогноза
 */
public record RatingView(
        Long issuerId,
        String agency,
        String rating,
        String ratingNormalized,
        BigDecimal score,
        BigDecimal effectiveScore,
        Outlook outlook,
        LocalDate ratingDate,
        String sourceUrl,
        boolean stale,
        boolean invalid) {
}
