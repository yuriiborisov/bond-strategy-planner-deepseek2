package com.bondplanner.service.rating;

import com.bondplanner.api.error.ApiException;
import com.bondplanner.domain.entity.Issuer;
import com.bondplanner.domain.entity.IssuerAlias;
import com.bondplanner.domain.entity.IssuerRating;
import com.bondplanner.domain.entity.RatingSource;
import com.bondplanner.repository.IssuerAliasRepository;
import com.bondplanner.repository.IssuerRatingRepository;
import com.bondplanner.repository.IssuerRepository;
import com.bondplanner.repository.RatingSourceRepository;
import lombok.RequiredArgsConstructor;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Импорт кредитных рейтингов из CSV (fallback-источник, §18.1, AC-11):
 * формат issuer,inn,agency,rating,outlook,date.
 */
@Service
@RequiredArgsConstructor
public class RatingImportService {

    private final IssuerRepository issuerRepository;
    private final IssuerAliasRepository aliasRepository;
    private final IssuerRatingRepository ratingRepository;
    private final RatingSourceRepository sourceRepository;
    private final RatingNormalizer normalizer;
    private final IssuerNames issuerNames;

    @Transactional
    public Map<String, Object> importCsv(InputStream inputStream) {
        List<Map<String, Object>> errors = new ArrayList<>();
        List<Map<String, Object>> unmatched = new ArrayList<>();
        int imported = 0;
        int createdIssuers = 0;
        try (Reader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8);
             CSVParser parser = CSVFormat.DEFAULT.builder()
                     .setHeader().setSkipHeaderRecord(true).setIgnoreHeaderCase(true).setTrim(true)
                     .setDelimiter(',').build().parse(reader)) {
            for (CSVRecord record : parser) {
                String issuerName = value(record, "issuer");
                if (issuerName == null) {
                    errors.add(Map.of("line", record.getRecordNumber(), "error", "не указан issuer"));
                    continue;
                }
                String ratingRaw = value(record, "rating");
                String code = normalizer.extractCode(ratingRaw);
                if (code == null) {
                    errors.add(Map.of("line", record.getRecordNumber(), "issuer", issuerName,
                            "error", "не распознан рейтинг: " + ratingRaw));
                    continue;
                }
                String agency = value(record, "agency");
                if (agency == null) {
                    errors.add(Map.of("line", record.getRecordNumber(), "issuer", issuerName, "error", "не указано agency"));
                    continue;
                }
                agency = agency.toUpperCase().replace('-', '_').replace(' ', '_');
                LocalDate ratingDate = parseDate(value(record, "date"));
                if (ratingDate == null) {
                    ratingDate = LocalDate.now();
                }
                String outlookRaw = value(record, "outlook");
                var outlook = normalizer.parseOutlook(outlookRaw);
                String inn = value(record, "inn");

                Resolved resolved = resolveIssuer(issuerName, inn);
                if (resolved == null) {
                    unmatched.add(Map.of("issuer", issuerName, "inn", inn == null ? "" : inn));
                    continue;
                }
                if (resolved.created()) {
                    createdIssuers++;
                }
                Issuer issuer = resolved.issuer();
                RatingSource source = sourceRepository.findByCode(agency).orElse(null);
                IssuerRating rating = ratingRepository
                        .findFirstByIssuerIdAndAgencyAndRatingDate(issuer.getId(), agency, ratingDate)
                        .orElseGet(IssuerRating::new);
                rating.setIssuer(issuer);
                rating.setAgency(agency);
                rating.setRating(ratingRaw);
                rating.setRatingNormalized(code);
                rating.setScore(normalizer.scoreOf(code));
                rating.setOutlook(outlook);
                rating.setScale("NATIONAL");
                rating.setRatingDate(ratingDate);
                rating.setSource(source);
                rating.setFetchedAt(Instant.now());
                rating.setConfidence(BigDecimal.valueOf(95));
                ratingRepository.save(rating);
                imported++;
            }
        } catch (Exception ex) {
            throw ApiException.badRequest("VALIDATION_ERROR", "Не удалось разобрать CSV: " + ex.getMessage());
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("imported", imported);
        result.put("createdIssuers", createdIssuers);
        result.put("errors", errors);
        result.put("unmatched", unmatched);
        return result;
    }

    private Resolved resolveIssuer(String name, String inn) {
        if (inn != null && !inn.isBlank()) {
            Optional<Issuer> byInn = issuerRepository.findByInn(inn.trim());
            if (byInn.isPresent()) {
                return new Resolved(byInn.get(), false);
            }
        }
        String normalized = issuerNames.normalize(name);
        Optional<Issuer> byName = issuerRepository.findByNormalizedName(normalized);
        if (byName.isPresent()) {
            return new Resolved(byName.get(), false);
        }
        Optional<IssuerAlias> alias = aliasRepository.findByNormalizedAlias(normalized);
        if (alias.isPresent()) {
            return new Resolved(alias.get().getIssuer(), false);
        }
        double best = 0;
        Issuer bestIssuer = null;
        for (Issuer issuer : issuerRepository.findAll()) {
            double score = issuerNames.similarity(normalized, issuer.getNormalizedName());
            if (score > best) {
                best = score;
                bestIssuer = issuer;
            }
        }
        if (bestIssuer != null && best >= 0.92) {
            IssuerAlias created = new IssuerAlias();
            created.setIssuer(bestIssuer);
            created.setAlias(name);
            created.setNormalizedAlias(normalized);
            created.setSource("AUTO");
            created.setConfidence(BigDecimal.valueOf(best * 100));
            aliasRepository.save(created);
            return new Resolved(bestIssuer, false);
        }
        Issuer issuer = new Issuer();
        issuer.setName(name);
        issuer.setNormalizedName(normalized);
        issuer.setInn(inn == null || inn.isBlank() ? null : inn.trim());
        issuer.setCountry("RU");
        issuerRepository.save(issuer);
        return new Resolved(issuer, true);
    }

    private String value(CSVRecord record, String name) {
        try {
            if (!record.isMapped(name)) {
                return null;
            }
            String value = record.get(name);
            return value == null || value.isBlank() ? null : value.trim();
        } catch (Exception ex) {
            return null;
        }
    }

    private LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        List<DateTimeFormatter> formats = List.of(
                DateTimeFormatter.ISO_LOCAL_DATE,
                DateTimeFormatter.ofPattern("dd.MM.yyyy"),
                DateTimeFormatter.ofPattern("dd/MM/yyyy"),
                DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        for (DateTimeFormatter format : formats) {
            try {
                return LocalDate.parse(value, format);
            } catch (Exception ignored) {
                // пробуем следующий формат
            }
        }
        return null;
    }

    private record Resolved(Issuer issuer, boolean created) {
    }
}
