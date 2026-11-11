package com.bondplanner.service.rating;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Нормализация имён эмитентов и fuzzy-матчинг (Jaro-Winkler) для сопоставления
 * рейтингов с бумагами (D-3, §18.5).
 */
@Component
public class IssuerNames {

    private static final Set<String> LEGAL_FORMS = Set.of(
            "ооо", "оао", "зао", "пао", "ао", "ано", "нко", "мфк", "мкк", "ип",
            "llc", "ltd", "inc", "jsc", "pjsc", "plc", "corp", "co");

    public String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        String value = raw.toLowerCase(Locale.ROOT)
                .replace('ё', 'е')
                .replaceAll("[«»\"'`]", "")
                .replaceAll("[^a-zа-я0-9\\s\\-+]", " ")
                .replaceAll("\\s+", " ")
                .trim();
        StringBuilder sb = new StringBuilder();
        for (String token : value.split(" ")) {
            if (token.isBlank() || LEGAL_FORMS.contains(token)) {
                continue;
            }
            if (!sb.isEmpty()) {
                sb.append(' ');
            }
            sb.append(token);
        }
        return sb.toString();
    }

    /** Jaro-Winkler similarity, 0..1. */
    public double similarity(String a, String b) {
        if (a == null || b == null || a.isEmpty() || b.isEmpty()) {
            return 0;
        }
        if (a.equals(b)) {
            return 1;
        }
        int prefix = 0;
        int maxPrefix = Math.min(4, Math.min(a.length(), b.length()));
        while (prefix < maxPrefix && a.charAt(prefix) == b.charAt(prefix)) {
            prefix++;
        }
        return jaro(a, b) + prefix * 0.1 * (1 - jaro(a, b));
    }

    private double jaro(String a, String b) {
        int aLen = a.length();
        int bLen = b.length();
        int matchDistance = Math.max(aLen, bLen) / 2 - 1;
        boolean[] aMatches = new boolean[aLen];
        boolean[] bMatches = new boolean[bLen];
        int matches = 0;
        for (int i = 0; i < aLen; i++) {
            int start = Math.max(0, i - matchDistance);
            int end = Math.min(i + matchDistance + 1, bLen);
            for (int j = start; j < end; j++) {
                if (bMatches[j] || a.charAt(i) != b.charAt(j)) {
                    continue;
                }
                aMatches[i] = true;
                bMatches[j] = true;
                matches++;
                break;
            }
        }
        if (matches == 0) {
            return 0;
        }
        int transpositions = 0;
        int k = 0;
        for (int i = 0; i < aLen; i++) {
            if (!aMatches[i]) {
                continue;
            }
            while (!bMatches[k]) {
                k++;
            }
            if (a.charAt(i) != b.charAt(k)) {
                transpositions++;
            }
            k++;
        }
        double m = matches;
        return (m / aLen + m / bLen + (m - transpositions / 2.0) / m) / 3.0;
    }

    public List<String> legalForms() {
        return List.copyOf(LEGAL_FORMS);
    }
}
