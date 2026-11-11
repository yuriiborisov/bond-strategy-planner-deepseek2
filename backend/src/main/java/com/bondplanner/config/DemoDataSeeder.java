package com.bondplanner.config;

import com.bondplanner.domain.entity.Bond;
import com.bondplanner.domain.entity.BondCashflow;
import com.bondplanner.domain.entity.BondQuote;
import com.bondplanner.domain.entity.Exchange;
import com.bondplanner.domain.entity.Issuer;
import com.bondplanner.domain.entity.IssuerFactor;
import com.bondplanner.domain.entity.IssuerRating;
import com.bondplanner.domain.entity.RatingSource;
import com.bondplanner.domain.entity.User;
import com.bondplanner.domain.model.CouponType;
import com.bondplanner.domain.model.FlowType;
import com.bondplanner.domain.model.Outlook;
import com.bondplanner.domain.model.Role;
import com.bondplanner.repository.BondCashflowRepository;
import com.bondplanner.repository.BondQuoteRepository;
import com.bondplanner.repository.BondRepository;
import com.bondplanner.repository.ExchangeRepository;
import com.bondplanner.repository.IssuerFactorRepository;
import com.bondplanner.repository.IssuerRatingRepository;
import com.bondplanner.repository.IssuerRepository;
import com.bondplanner.repository.RatingSourceRepository;
import com.bondplanner.repository.UserRepository;
import com.bondplanner.service.rating.IssuerNames;
import com.bondplanner.service.rating.RatingNormalizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Демо-данные для локального запуска без синхронизации с MOEX: 20 бумаг,
 * купонные календари, котировки, рейтинги и факторы. Включается APP_SEED_DEMO.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DemoDataSeeder implements ApplicationRunner {

    private final ExchangeRepository exchangeRepository;
    private final BondRepository bondRepository;
    private final BondQuoteRepository quoteRepository;
    private final BondCashflowRepository cashflowRepository;
    private final IssuerRepository issuerRepository;
    private final IssuerRatingRepository ratingRepository;
    private final IssuerFactorRepository factorRepository;
    private final RatingSourceRepository ratingSourceRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RatingNormalizer ratingNormalizer;
    private final IssuerNames issuerNames;
    private final AppProperties properties;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        ensureDemoUser();
        if (!properties.isSeedDemo()) {
            return;
        }
        if (bondRepository.count() > 0) {
            return;
        }
        Exchange moex = exchangeRepository.findByCodeIgnoreCase("MOEX").orElseThrow();
        log.info("Заполняю демо-данные Bond Strategy Planner");

        LocalDate today = LocalDate.now();
        List<DemoBond> bonds = demoBonds(today);
        Map<String, BigDecimal> ratingScores = Map.of(
                "AAA", BigDecimal.valueOf(100), "AA+", BigDecimal.valueOf(95),
                "AA", BigDecimal.valueOf(90), "AA-", BigDecimal.valueOf(85),
                "A+", BigDecimal.valueOf(80), "A", BigDecimal.valueOf(75),
                "A-", BigDecimal.valueOf(70), "BBB+", BigDecimal.valueOf(65),
                "BBB", BigDecimal.valueOf(60));

        java.util.Set<Long> ratedIssuers = new java.util.HashSet<>();
        java.util.Set<Long> factoredIssuers = new java.util.HashSet<>();
        for (DemoBond demo : bonds) {
            Issuer issuer = issuerRepository.findByNormalizedName(issuerNames.normalize(demo.issuerName()))
                    .orElseGet(() -> issuerRepository.save(createIssuer(demo)));
            Bond bond = new Bond();
            bond.setExchange(moex);
            bond.setIssuer(issuer);
            bond.setSecid(demo.secid());
            bond.setBoardId(demo.type().equals("OFZ") ? "TQOB" : "TQCB");
            bond.setIsin(demo.isin());
            bond.setShortName(demo.name());
            bond.setIssuerName(demo.issuerName());
            bond.setBondType(demo.type());
            bond.setCouponType(demo.couponType());
            bond.setCurrency("RUB");
            bond.setFaceValue(BigDecimal.valueOf(1000));
            bond.setCouponValue(demo.couponValue());
            bond.setCouponPeriodDays(demo.periodMonths() * 30);
            bond.setMaturityDate(demo.maturity());
            bond.setOfferDate(demo.offerDate());
            bond.setListLevel(demo.listLevel());
            bond.setLotSize(demo.lotSize());
            bond.setIssueSize(demo.issueSize());
            bond.setAmortizing(false);
            bond.setPerpetual(false);
            bond.setStatus("ACTIVE");
            bond.setFirstSeenAt(Instant.now().minus(400, ChronoUnit.DAYS));
            bond.setUpdatedAt(Instant.now());
            bondRepository.save(bond);

            seedCoupons(bond, demo, today);
            seedQuote(bond, demo, today);
            if (ratedIssuers.add(issuer.getId())) {
                seedRating(issuer, demo, ratingScores);
            }
            if (factoredIssuers.add(issuer.getId())) {
                seedFactors(issuer, demo);
            }
        }
        log.info("Демо-данные готовы: {} бумаг", bonds.size());
    }

    private void ensureDemoUser() {
        if (!userRepository.existsByEmailIgnoreCase("admin@local.dev")) {
            User admin = new User();
            admin.setEmail("admin@local.dev");
            admin.setPasswordHash(passwordEncoder.encode("admin12345"));
            admin.setRole(Role.ADMIN);
            admin.setEnabled(true);
            userRepository.save(admin);
            log.info("Создан демо-администратор admin@local.dev / admin12345");
        }
    }

    private Issuer createIssuer(DemoBond demo) {
        Issuer issuer = new Issuer();
        issuer.setName(demo.issuerName());
        issuer.setNormalizedName(issuerNames.normalize(demo.issuerName()));
        issuer.setInn(demo.inn());
        issuer.setSector(demo.sector());
        issuer.setCountry("RU");
        return issuer;
    }

    private void seedCoupons(Bond bond, DemoBond demo, LocalDate today) {
        BigDecimal coupon = demo.couponValue();
        LocalDate date = demo.maturity();
        List<LocalDate> dates = new ArrayList<>();
        while (!date.isBefore(today.minusYears(3))) {
            dates.add(date);
            date = date.minusMonths(demo.periodMonths());
        }
        dates.sort(LocalDate::compareTo);
        for (LocalDate couponDate : dates) {
            if (couponDate.isBefore(today.minusYears(1))) {
                continue;
            }
            cashflowRepository.save(new BondCashflow(bond, FlowType.COUPON, couponDate, coupon));
        }
    }

    private void seedQuote(Bond bond, DemoBond demo, LocalDate today) {
        BigDecimal price = demo.price();
        BigDecimal coupon = demo.couponValue();
        int periodDays = demo.periodMonths() * 30;
        LocalDate previousCoupon = today.minusDays(periodDays / 3L);
        long days = Math.max(0, ChronoUnit.DAYS.between(previousCoupon, today));
        BigDecimal nkd = coupon.multiply(BigDecimal.valueOf(days))
                .divide(BigDecimal.valueOf(periodDays), 2, RoundingMode.HALF_UP);
        double years = Math.max(0.3, ChronoUnit.DAYS.between(today, demo.maturity()) / 365.0);
        double approximateYtm = demo.couponRate().doubleValue() + (100 - price.doubleValue()) / years;
        BondQuote quote = new BondQuote();
        quote.setBond(bond);
        quote.setSnapshotTs(Instant.now().minus(1, ChronoUnit.HOURS));
        quote.setPricePct(price);
        quote.setNkd(nkd);
        quote.setYtm(BigDecimal.valueOf(approximateYtm).setScale(4, RoundingMode.HALF_UP));
        quote.setDurationYears(BigDecimal.valueOf(years * 0.85).setScale(4, RoundingMode.HALF_UP));
        quote.setBid(price.subtract(new BigDecimal("0.2")));
        quote.setAsk(price.add(new BigDecimal("0.2")));
        quote.setVolumeRub(demo.volumeRub());
        quote.setNumTrades((int) (demo.volumeRub().doubleValue() / 500_000));
        quote.setSource("MOEX_ISS");
        quoteRepository.save(quote);
    }

    private void seedRating(Issuer issuer, DemoBond demo, Map<String, BigDecimal> scores) {
        RatingSource source = ratingSourceRepository.findByCode(demo.agency()).orElse(null);
        IssuerRating rating = new IssuerRating();
        rating.setIssuer(issuer);
        rating.setAgency(demo.agency());
        rating.setRating(demo.rating() + "(RU)");
        rating.setRatingNormalized(demo.rating());
        rating.setScore(scores.getOrDefault(demo.rating(), BigDecimal.valueOf(60)));
        rating.setOutlook(demo.outlook());
        rating.setScale("NATIONAL");
        rating.setRatingDate(demo.ratingDate());
        rating.setSource(source);
        rating.setSourceUrl("https://example-rating.local/" + demo.secid());
        rating.setFetchedAt(Instant.now().minus(2, ChronoUnit.DAYS));
        rating.setConfidence(BigDecimal.valueOf(95));
        ratingRepository.save(rating);
    }

    private void seedFactors(Issuer issuer, DemoBond demo) {
        if (!"CORPORATE".equals(demo.type())) {
            return;
        }
        LocalDate asOf = LocalDate.now().minusMonths(2);
        saveFactor(issuer, "REVENUE_GROWTH", demo.factorRevenueGrowth(), "%", asOf);
        saveFactor(issuer, "DEBT_EBITDA", demo.factorDebtEbitda(), "x", asOf);
        saveFactor(issuer, "INTEREST_COVERAGE", demo.factorInterestCoverage(), "x", asOf);
    }

    private void saveFactor(Issuer issuer, String code, BigDecimal value, String unit, LocalDate asOf) {
        if (value == null) {
            return;
        }
        IssuerFactor factor = new IssuerFactor();
        factor.setIssuer(issuer);
        factor.setFactorCode(code);
        factor.setValueNum(value);
        factor.setUnit(unit);
        factor.setPeriod("2026-Q2");
        factor.setAsOf(asOf);
        factor.setConfidence(BigDecimal.valueOf(90));
        factorRepository.save(factor);
    }

    private List<DemoBond> demoBonds(LocalDate today) {
        List<DemoBond> list = new ArrayList<>();
        list.add(new DemoBond("SU26238RMFS4", "ОФЗ 26238", "Минфин России", null, "OFZ", CouponType.FIXED,
                new BigDecimal("9.5"), 6, today.plusYears(1).plusMonths(1), null, 1, 1,
                new BigDecimal("400000000000"), new BigDecimal("95.2"), "AAA", "ACRA", Outlook.STABLE,
                today.minusDays(45), "Государственные бумаги", null, null, null, new BigDecimal("25000000")));
        list.add(new DemoBond("SU26243RMFS4", "ОФЗ 26243", "Минфин России", null, "OFZ", CouponType.FIXED,
                new BigDecimal("9.8"), 6, today.plusYears(2).plusMonths(5), null, 1, 1,
                new BigDecimal("450000000000"), new BigDecimal("93.4"), "AAA", "ACRA", Outlook.STABLE,
                today.minusDays(45), "Государственные бумаги", null, null, null, new BigDecimal("18000000")));
        list.add(new DemoBond("SU26244RMFS2", "ОФЗ 26244", "Минфин России", null, "OFZ", CouponType.FIXED,
                new BigDecimal("10.2"), 6, today.plusYears(3).plusMonths(1), null, 1, 1,
                new BigDecimal("500000000000"), new BigDecimal("92.1"), "AAA", "ACRA", Outlook.STABLE,
                today.minusDays(45), "Государственные бумаги", null, null, null, new BigDecimal("12000000")));
        list.add(new DemoBond("SU26245RMFS9", "ОФЗ 26245", "Минфин России", null, "OFZ", CouponType.FIXED,
                new BigDecimal("10.6"), 6, today.plusYears(4).plusMonths(4), null, 1, 1,
                new BigDecimal("500000000000"), new BigDecimal("90.8"), "AAA", "ACRA", Outlook.STABLE,
                today.minusDays(45), "Государственные бумаги", null, null, null, new BigDecimal("9000000")));
        list.add(new DemoBond("SU26246RMFS7", "ОФЗ 26246", "Минфин России", null, "OFZ", CouponType.FIXED,
                new BigDecimal("10.9"), 6, today.plusYears(5).plusMonths(3), null, 1, 1,
                new BigDecimal("500000000000"), new BigDecimal("89.5"), "AAA", "ACRA", Outlook.STABLE,
                today.minusDays(45), "Государственные бумаги", null, null, null, new BigDecimal("8000000")));
        list.add(new DemoBond("SU26247RMFS5", "ОФЗ 26247", "Минфин России", null, "OFZ", CouponType.FIXED,
                new BigDecimal("11.1"), 6, today.plusYears(7).plusMonths(6), null, 1, 1,
                new BigDecimal("500000000000"), new BigDecimal("88.2"), "AAA", "ACRA", Outlook.STABLE,
                today.minusDays(45), "Государственные бумаги", null, null, null, new BigDecimal("6000000")));
        list.add(new DemoBond("SU26248RMFS3", "ОФЗ 26248", "Минфин России", null, "OFZ", CouponType.FIXED,
                new BigDecimal("11.3"), 6, today.plusYears(9).plusMonths(2), null, 1, 1,
                new BigDecimal("500000000000"), new BigDecimal("87.4"), "AAA", "ACRA", Outlook.STABLE,
                today.minusDays(45), "Государственные бумаги", null, null, null, new BigDecimal("5000000")));
        list.add(new DemoBond("SU26249RMFS1", "ОФЗ 26249", "Минфин России", null, "OFZ", CouponType.FIXED,
                new BigDecimal("11.5"), 6, today.plusYears(11).plusMonths(1), null, 1, 1,
                new BigDecimal("450000000000"), new BigDecimal("86.1"), "AAA", "ACRA", Outlook.STABLE,
                today.minusDays(45), "Государственные бумаги", null, null, null, new BigDecimal("4000000")));

        list.add(new DemoBond("SBER27A", "Сбербанк БО-27", "Сбербанк", "7707083893", "CORPORATE", CouponType.FIXED,
                new BigDecimal("12.1"), 3, today.plusMonths(2), null, 1, 10,
                new BigDecimal("30000000000"), new BigDecimal("99.1"), "AA", "ACRA", Outlook.STABLE,
                today.minusDays(40), "Банки", new BigDecimal("11.2"), new BigDecimal("1.1"), new BigDecimal("7.4"),
                new BigDecimal("16000000")));

        list.add(new DemoBond("SU26250RMFS3", "ОФЗ 26250", "Минфин России", null, "OFZ", CouponType.FIXED,
                new BigDecimal("11.6"), 6, today.plusYears(12).plusMonths(2), null, 1, 1,
                new BigDecimal("500000000000"), new BigDecimal("85.3"), "AAA", "ACRA", Outlook.STABLE,
                today.minusDays(45), "Государственные бумаги", null, null, null, new BigDecimal("3500000")));
        list.add(new DemoBond("SU26251RMFS1", "ОФЗ 26251", "Минфин России", null, "OFZ", CouponType.FIXED,
                new BigDecimal("11.7"), 6, today.plusYears(13).plusMonths(3), null, 1, 1,
                new BigDecimal("500000000000"), new BigDecimal("84.2"), "AAA", "ACRA", Outlook.STABLE,
                today.minusDays(45), "Государственные бумаги", null, null, null, new BigDecimal("3200000")));

        list.add(new DemoBond("GAZP35A", "Газпром БО-35", "Газпром", "7736050003", "CORPORATE", CouponType.FIXED,
                new BigDecimal("12.4"), 3, today.plusYears(1).plusMonths(2), null, 1, 10,
                new BigDecimal("30000000000"), new BigDecimal("98.2"), "AA+", "ACRA", Outlook.STABLE,
                today.minusDays(60), "Нефтегаз", new BigDecimal("8.4"), new BigDecimal("1.4"), new BigDecimal("6.1"),
                new BigDecimal("14000000")));
        list.add(new DemoBond("GAZP36A", "Газпром БО-36", "Газпром", "7736050003", "CORPORATE", CouponType.FIXED,
                new BigDecimal("12.8"), 3, today.plusYears(3).plusMonths(1), null, 1, 10,
                new BigDecimal("30000000000"), new BigDecimal("96.5"), "AA+", "ACRA", Outlook.STABLE,
                today.minusDays(60), "Нефтегаз", new BigDecimal("8.4"), new BigDecimal("1.4"), new BigDecimal("6.1"),
                new BigDecimal("9000000")));
        list.add(new DemoBond("RZD28A", "РЖД БО-28", "РЖД", "7708503727", "CORPORATE", CouponType.FIXED,
                new BigDecimal("13.1"), 3, today.plusYears(2).plusMonths(1), null, 1, 10,
                new BigDecimal("25000000000"), new BigDecimal("97.1"), "AA-", "EXPERT_RA", Outlook.STABLE,
                today.minusDays(75), "Транспорт", new BigDecimal("6.1"), new BigDecimal("2.1"), new BigDecimal("3.8"),
                new BigDecimal("7000000")));
        list.add(new DemoBond("SBER28A", "Сбербанк БО-28", "Сбербанк", "7707083893", "CORPORATE", CouponType.FIXED,
                new BigDecimal("13.4"), 3, today.plusYears(2).plusMonths(1), null, 1, 10,
                new BigDecimal("40000000000"), new BigDecimal("98.6"), "AA", "ACRA", Outlook.STABLE,
                today.minusDays(40), "Банки", new BigDecimal("11.2"), new BigDecimal("1.1"), new BigDecimal("7.4"),
                new BigDecimal("21000000")));
        list.add(new DemoBond("LKOH30A", "Лукойл БО-30", "Лукойл", "7728000810", "CORPORATE", CouponType.FIXED,
                new BigDecimal("13.8"), 3, today.plusYears(4).plusMonths(1), null, 1, 10,
                new BigDecimal("20000000000"), new BigDecimal("96.2"), "A+", "ACRA", Outlook.STABLE,
                today.minusDays(50), "Нефтегаз", new BigDecimal("5.4"), new BigDecimal("1.9"), new BigDecimal("4.9"),
                new BigDecimal("5500000")));
        list.add(new DemoBond("ROSN31A", "Роснефть БО-31", "Роснефть", "7706107510", "CORPORATE", CouponType.FIXED,
                new BigDecimal("14.1"), 3, today.plusYears(5).plusMonths(1), null, 1, 10,
                new BigDecimal("25000000000"), new BigDecimal("95.4"), "A", "ACRA", Outlook.STABLE,
                today.minusDays(80), "Нефтегаз", new BigDecimal("7.9"), new BigDecimal("2.4"), new BigDecimal("4.1"),
                new BigDecimal("4800000")));
        list.add(new DemoBond("MTS29A", "МТС БО-29", "МТС", "7740000076", "CORPORATE", CouponType.FIXED,
                new BigDecimal("14.5"), 3, today.plusYears(2).plusMonths(8), null, 1, 10,
                new BigDecimal("15000000000"), new BigDecimal("96.8"), "A-", "ACRA", Outlook.NEGATIVE,
                today.minusDays(120), "Телеком", new BigDecimal("4.2"), new BigDecimal("2.6"), new BigDecimal("3.4"),
                new BigDecimal("3500000")));
        list.add(new DemoBond("ALFA30A", "Альфа-Банк БО-30", "Альфа-Банк", "7728168971", "CORPORATE", CouponType.FIXED,
                new BigDecimal("15.2"), 3, today.plusYears(3).plusMonths(6), null, 2, 10,
                new BigDecimal("12000000000"), new BigDecimal("94.9"), "BBB+", "ACRA", Outlook.STABLE,
                today.minusDays(90), "Банки", new BigDecimal("9.1"), new BigDecimal("3.4"), new BigDecimal("2.3"),
                new BigDecimal("2200000")));
        list.add(new DemoBond("VTB32A", "ВТБ БО-32", "ВТБ", "7702070139", "CORPORATE", CouponType.FIXED,
                new BigDecimal("14.8"), 3, today.plusYears(5).plusMonths(4), null, 1, 10,
                new BigDecimal("22000000000"), new BigDecimal("95.7"), "A+", "ACRA", Outlook.STABLE,
                today.minusDays(400), "Банки", new BigDecimal("10.3"), new BigDecimal("1.8"), new BigDecimal("4.4"),
                new BigDecimal("6100000")));
        list.add(new DemoBond("NLMK30A", "НЛМК БО-30", "НЛМК", "7701020680", "CORPORATE", CouponType.FIXED,
                new BigDecimal("13.9"), 3, today.plusYears(4).plusMonths(9), null, 1, 10,
                new BigDecimal("18000000000"), new BigDecimal("96.9"), "A", "ACRA", Outlook.STABLE,
                today.minusDays(70), "Металлургия", new BigDecimal("3.7"), new BigDecimal("1.2"), new BigDecimal("6.8"),
                new BigDecimal("4100000")));
        list.add(new DemoBond("TRNFP31A", "Транснефть БО-31", "Транснефть", "7728070730", "CORPORATE", CouponType.FIXED,
                new BigDecimal("13.6"), 3, today.plusYears(6).plusMonths(3), null, 1, 10,
                new BigDecimal("16000000000"), new BigDecimal("95.1"), "AA-", "EXPERT_RA", Outlook.STABLE,
                today.minusDays(65), "Транспорт", new BigDecimal("6.8"), new BigDecimal("1.6"), new BigDecimal("5.2"),
                new BigDecimal("3800000")));
        list.add(new DemoBond("MMK31A", "ММК БО-31", "ММК", "7740000084", "CORPORATE", CouponType.FIXED,
                new BigDecimal("14.2"), 3, today.plusYears(7).plusMonths(3), null, 1, 10,
                new BigDecimal("14000000000"), new BigDecimal("94.4"), "A", "ACRA", Outlook.STABLE,
                today.minusDays(85), "Металлургия", new BigDecimal("4.9"), new BigDecimal("2.2"), new BigDecimal("4.0"),
                new BigDecimal("3100000")));
        list.add(new DemoBond("SNGPR32A", "Сургутнефтегаз БО-32", "Сургутнефтегаз", "8602060555", "CORPORATE",
                CouponType.FIXED, new BigDecimal("14.4"), 3, today.plusYears(8).plusMonths(2), null, 2, 10,
                new BigDecimal("10000000000"), new BigDecimal("93.8"), "AA-", "ACRA", Outlook.STABLE,
                today.minusDays(55), "Нефтегаз", new BigDecimal("5.9"), new BigDecimal("1.3"), new BigDecimal("5.6"),
                new BigDecimal("2500000")));
        return list;
    }

    private record DemoBond(
            String secid,
            String name,
            String issuerName,
            String inn,
            String type,
            CouponType couponType,
            BigDecimal couponRate,
            int periodMonths,
            LocalDate maturity,
            LocalDate offerDate,
            int listLevel,
            int lotSize,
            BigDecimal issueSize,
            BigDecimal price,
            String rating,
            String agency,
            Outlook outlook,
            LocalDate ratingDate,
            String sector,
            BigDecimal factorRevenueGrowth,
            BigDecimal factorDebtEbitda,
            BigDecimal factorInterestCoverage,
            BigDecimal volumeRub) {

        String isin() {
            return "RU000" + String.format("%09d", Math.abs(secid.hashCode() % 1_000_000_000));
        }

        BigDecimal couponValue() {
            return BigDecimal.valueOf(1000)
                    .multiply(couponRate)
                    .divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(periodMonths))
                    .divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP);
        }
    }
}
