package com.bondplanner.domain.model;

/**
 * Как учитывать put-оферту (решение D-14).
 */
public enum OfferTreatment {
    MATURITY,
    OPTION,
    AUTO;

    public static OfferTreatment parse(String value, OfferTreatment fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
    }
}
