package com.bondplanner.service.checks;

import com.bondplanner.domain.entity.Bond;
import com.bondplanner.domain.entity.BondCashflow;
import com.bondplanner.domain.entity.BondQuote;
import com.bondplanner.domain.entity.Issuer;
import com.bondplanner.domain.model.CheckStatus;
import com.bondplanner.domain.model.CouponType;
import com.bondplanner.domain.model.FlowType;
import com.bondplanner.domain.model.Outlook;
import com.bondplanner.service.calc.BondMetricsService;
import com.bondplanner.service.calc.FilterParams;
import com.bondplanner.service.cashflow.DatedCashFlow;
import com.bondplanner.service.rating.RatingNormalizer;
import com.bondplanner.service.rating.RatingView;
import com.bondplanner.service.universe.BondCandidate;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AssetCheckServiceTest {

    private final AssetCheckService service = new AssetCheckService(new RatingNormalizer());
    private final LocalDate today = LocalDate.now();

    @Test
    void rejectsRatingBelowMinimum() {
        AssetCheckView view = check(true, null, rating("BBB+", Outlook.STABLE, 30));
        assertThat(view.verdict()).isEqualTo("REJECTED");
        assertThat(view.checks()).anyMatch(i -> i.code().equals("CREDIT_RATING") && i.status() == CheckStatus.FAIL);
    }

    @Test
    void rejectsMissingRatingWhenRequired() {
        AssetCheckView view = check(true, null, null);
        assertThat(view.verdict()).isEqualTo("REJECTED");
        assertThat(view.checks()).anyMatch(i -> i.code().equals("CREDIT_RATING") && i.status() == CheckStatus.FAIL);
    }

    @Test
    void allowsMissingRatingWhenNotRequired() {
        AssetCheckView view = check(false, null, null);
        assertThat(view.verdict()).isEqualTo("WATCH");
        assertThat(view.checks()).anyMatch(i -> i.code().equals("CREDIT_RATING")
                && i.status() == CheckStatus.UNKNOWN);
    }

    @Test
    void negativeOutlookIsWatchNotReject() {
        AssetCheckView view = check(true, null, rating("AA", Outlook.NEGATIVE, 30));
        assertThat(view.verdict()).isEqualTo("WATCH");
        assertThat(view.checks()).anyMatch(i -> i.code().equals("RATING_OUTLOOK") && i.status() == CheckStatus.WARN);
    }

    @Test
    void staleRatingRejectedWhenRequired() {
        AssetCheckView view = check(true, null, rating("AA", Outlook.STABLE, 400));
        assertThat(view.verdict()).isEqualTo("REJECTED");
        assertThat(view.checks()).anyMatch(i -> i.code().equals("RATING_AGE") && i.status() == CheckStatus.FAIL);
    }

    @Test
    void maturityOutsideWindowRejects() {
        AssetCheckView view = check(true, today.plusYears(1), rating("AA", Outlook.STABLE, 30));
        assertThat(view.verdict()).isEqualTo("REJECTED");
        assertThat(view.checks()).anyMatch(i -> i.code().equals("DURATION_FIT") && i.status() == CheckStatus.FAIL);
    }

    @Test
    void goodBondIsApproved() {
        AssetCheckView view = check(true, today.plusYears(3), rating("AA", Outlook.STABLE, 30));
        assertThat(view.verdict()).isEqualTo("APPROVED");
        assertThat(view.score()).isGreaterThanOrEqualTo(70);
        assertThat(view.asOf()).isNotNull();
    }

    @Test
    void unknownFactorsAreNotPass() {
        AssetCheckView view = check(true, today.plusYears(3), rating("AA", Outlook.STABLE, 30));
        assertThat(view.checks()).anyMatch(i -> i.code().equals("DEBT_EBITDA") && i.status() == CheckStatus.UNKNOWN);
        assertThat(view.checks()).noneMatch(i -> i.code().equals("DEBT_EBITDA") && i.status() == CheckStatus.PASS);
    }

    private AssetCheckView check(boolean requireRating, LocalDate maxMaturity, RatingView rating) {
        AssetCheckService.CheckContext ctx = new AssetCheckService.CheckContext(
                filters(requireRating), "LADDER", today, today.plusYears(1), 45, null, maxMaturity,
                BigDecimal.valueOf(14), BigDecimal.valueOf(70), Instant.now(), List.of());
        return service.check(candidate(), metrics(), rating, ctx);
    }

    private FilterParams filters(boolean requireRating) {
        return new FilterParams(List.of("OFZ", "CORPORATE"), 2, BigDecimal.valueOf(1_000_000_000L),
                BigDecimal.valueOf(1_000_000L), false, true, new BigDecimal("0.25"), new BigDecimal("0.5"),
                "A-", List.of("ACRA"), 180, requireRating, false, BigDecimal.valueOf(60),
                new BigDecimal("0.05"), true, "RU_FLAT_13", BigDecimal.ZERO, true);
    }

    private BondCandidate candidate() {
        Issuer issuer = new Issuer();
        issuer.setId(1L);
        issuer.setName("Эмитент");
        issuer.setNormalizedName("эмитент");
        Bond bond = new Bond();
        bond.setId(1L);
        bond.setIssuer(issuer);
        bond.setSecid("TEST01");
        bond.setBoardId("TQCB");
        bond.setShortName("Тест БО-01");
        bond.setIssuerName("Эмитент");
        bond.setBondType("CORPORATE");
        bond.setCouponType(CouponType.FIXED);
        bond.setCurrency("RUB");
        bond.setFaceValue(BigDecimal.valueOf(1000));
        bond.setLotSize(1);
        bond.setListLevel(1);
        bond.setIssueSize(BigDecimal.valueOf(10_000_000_000L));
        bond.setMaturityDate(today.plusYears(2));
        bond.setStatus("ACTIVE");
        BondQuote quote = new BondQuote();
        quote.setPricePct(BigDecimal.valueOf(98));
        quote.setNkd(BigDecimal.TEN);
        quote.setYtm(BigDecimal.valueOf(14));
        quote.setVolumeRub(BigDecimal.valueOf(5_000_000));
        List<BondCashflow> flows = List.of(
                new BondCashflow(bond, FlowType.COUPON, today.plusMonths(6), BigDecimal.valueOf(70)),
                new BondCashflow(bond, FlowType.COUPON, today.plusYears(1), BigDecimal.valueOf(70)),
                new BondCashflow(bond, FlowType.COUPON, today.plusYears(2), BigDecimal.valueOf(70)));
        return new BondCandidate(bond, quote, flows);
    }

    private BondMetricsService.BondMetrics metrics() {
        return new BondMetricsService.BondMetrics(BigDecimal.valueOf(98), BigDecimal.TEN,
                BigDecimal.valueOf(990), BigDecimal.valueOf(990.5), BigDecimal.valueOf(14),
                BigDecimal.valueOf(2.5), BigDecimal.valueOf(2.2), today.plusYears(2),
                List.of(DatedCashFlow.coupon(today.plusMonths(6), BigDecimal.valueOf(70))));
    }

    private RatingView rating(String code, Outlook outlook, int ageDays) {
        BigDecimal score = switch (code) {
            case "AAA" -> BigDecimal.valueOf(100);
            case "AA" -> BigDecimal.valueOf(90);
            case "BBB+" -> BigDecimal.valueOf(65);
            default -> BigDecimal.valueOf(70);
        };
        return new RatingView(1L, "ACRA", code + "(RU)", code, score, score, outlook,
                today.minusDays(ageDays), "https://rating", ageDays > 180, false);
    }
}
