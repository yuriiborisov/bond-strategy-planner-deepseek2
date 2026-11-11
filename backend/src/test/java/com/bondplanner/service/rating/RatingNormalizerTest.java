package com.bondplanner.service.rating;

import com.bondplanner.domain.model.Outlook;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class RatingNormalizerTest {

    private final RatingNormalizer normalizer = new RatingNormalizer();

    @Test
    void extractsCodeFromAgencyFormats() {
        assertThat(normalizer.extractCode("AA(RU)")).isEqualTo("AA");
        assertThat(normalizer.extractCode("ruAA-")).isEqualTo("AA-");
        assertThat(normalizer.extractCode("A+.ru")).isEqualTo("A+");
        assertThat(normalizer.extractCode("BBB(RU)")).isEqualTo("BBB");
        assertThat(normalizer.extractCode("ruAAA")).isEqualTo("AAA");
    }

    @Test
    void unknownRatingReturnsNull() {
        assertThat(normalizer.extractCode("нет рейтинга")).isNull();
        assertThat(normalizer.extractCode(null)).isNull();
    }

    @Test
    void scoreMappingMatchesScale() {
        assertThat(normalizer.scoreOf("AAA")).isEqualByComparingTo("100");
        assertThat(normalizer.scoreOf("A-")).isEqualByComparingTo("70");
        assertThat(normalizer.scoreOf("BBB+")).isEqualByComparingTo("65");
        assertThat(normalizer.scoreOf("D")).isEqualByComparingTo("0");
    }

    @Test
    void minimumScoreUsesLetterCode() {
        assertThat(normalizer.minimumScore("A-")).isEqualByComparingTo("70");
        assertThat(normalizer.minimumScore("AAA")).isEqualByComparingTo("100");
        assertThat(normalizer.minimumScore(null)).isEqualByComparingTo("70");
    }

    @Test
    void outlookAdjustsScoreForRanking() {
        assertThat(normalizer.applyOutlook(BigDecimal.valueOf(70), Outlook.POSITIVE))
                .isEqualByComparingTo("73");
        assertThat(normalizer.applyOutlook(BigDecimal.valueOf(70), Outlook.NEGATIVE))
                .isEqualByComparingTo("63");
        assertThat(normalizer.applyOutlook(BigDecimal.valueOf(70), Outlook.DEVELOPING))
                .isEqualByComparingTo("67");
        assertThat(normalizer.applyOutlook(BigDecimal.valueOf(1), Outlook.NEGATIVE))
                .isEqualByComparingTo("0");
    }

    @Test
    void parsesRussianOutlookWords() {
        assertThat(normalizer.parseOutlook("Негативный")).isEqualTo(Outlook.NEGATIVE);
        assertThat(normalizer.parseOutlook("стабильный")).isEqualTo(Outlook.STABLE);
        assertThat(normalizer.parseOutlook("Позитивный")).isEqualTo(Outlook.POSITIVE);
        assertThat(normalizer.parseOutlook("Развивающийся")).isEqualTo(Outlook.DEVELOPING);
        assertThat(normalizer.parseOutlook("unknown")).isNull();
    }

    @Test
    void defaultRatingsAreDetected() {
        assertThat(normalizer.isDefault("D")).isTrue();
        assertThat(normalizer.isDefault("C")).isTrue();
        assertThat(normalizer.isDefault("AA")).isFalse();
    }
}
