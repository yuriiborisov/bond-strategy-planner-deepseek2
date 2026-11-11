package com.bondplanner.service.rating;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IssuerNamesTest {

    private final IssuerNames names = new IssuerNames();

    @Test
    void removesLegalFormAndPunctuation() {
        assertThat(names.normalize("ПАО «Газпром»")).isEqualTo("газпром");
        assertThat(names.normalize("ООО \"Ромашка\"")).isEqualTo("ромашка");
        assertThat(names.normalize("АО  РЖД")).isEqualTo("ржд");
    }

    @Test
    void normalizesYoAndSpaces() {
        assertThat(names.normalize("ПАО   Сбербанк")).isEqualTo("сбербанк");
        assertThat(names.normalize("Транснефть")).isEqualTo("транснефть");
    }

    @Test
    void similarityIsHighForCloseNames() {
        assertThat(names.similarity("газпром", "газпром")).isEqualTo(1.0);
        assertThat(names.similarity("сбербанк", "сбербанка")).isGreaterThan(0.9);
        assertThat(names.similarity("лукойл", "норильский никель")).isLessThan(0.7);
    }

    @Test
    void emptyInputHasZeroSimilarity() {
        assertThat(names.similarity("", "газпром")).isZero();
        assertThat(names.similarity(null, null)).isZero();
    }
}
