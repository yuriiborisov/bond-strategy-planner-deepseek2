package com.bondplanner.service.catalog;

import com.bondplanner.api.dto.PageResponse;
import com.bondplanner.api.error.ApiException;
import com.bondplanner.domain.entity.Bond;
import com.bondplanner.domain.entity.BondCashflow;
import com.bondplanner.domain.entity.BondQuote;
import com.bondplanner.domain.entity.Exchange;
import com.bondplanner.domain.entity.IssuerFactor;
import com.bondplanner.domain.model.OfferTreatment;
import com.bondplanner.repository.BondCashflowRepository;
import com.bondplanner.repository.BondQuoteRepository;
import com.bondplanner.repository.BondRepository;
import com.bondplanner.repository.ExchangeRepository;
import com.bondplanner.repository.IssuerFactorRepository;
import com.bondplanner.repository.IssuerRepository;
import com.bondplanner.service.calc.BondMetricsService;
import com.bondplanner.service.calc.FilterParams;
import com.bondplanner.service.calc.Params;
import com.bondplanner.service.rating.RatingService;
import com.bondplanner.service.rating.RatingView;
import com.bondplanner.service.universe.BondCandidate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class BondService {

    private final BondRepository bondRepository;
    private final BondQuoteRepository quoteRepository;
    private final BondCashflowRepository cashflowRepository;
    private final IssuerFactorRepository factorRepository;
    private final IssuerRepository issuerRepository;
    private final ExchangeRepository exchangeRepository;
    private final BondMetricsService metricsService;
    private final RatingService ratingService;

    @Transactional(readOnly = true)
    public PageResponse<Map<String, Object>> search(String exchangeCode, String query, String type, int page, int size) {
        Exchange exchange = exchangeRepository.findByCodeIgnoreCase(exchangeCode)
                .orElseThrow(() -> ApiException.notFound("Биржа не найдена: " + exchangeCode));
        int safeSize = Math.min(Math.max(size, 1), 200);
        int safePage = Math.max(page, 0);
        List<Bond> all = bondRepository.search(exchange.getId(), blankToNull(type), blankToNull(query),
                PageRequest.of(0, 5000));
        long total = all.size();
        int from = Math.min(safePage * safeSize, all.size());
        int to = Math.min(from + safeSize, all.size());
        List<Bond> bonds = all.subList(from, to);
        List<Long> ids = bonds.stream().map(Bond::getId).toList();
        Map<Long, BondQuote> quotes = ids.isEmpty() ? Map.of()
                : quoteRepository.findLatestByBondIds(ids).stream()
                        .collect(java.util.stream.Collectors.toMap(q -> q.getBond().getId(), q -> q));
        List<Map<String, Object>> content = bonds.stream().map(bond -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", bond.getId());
            map.put("secid", bond.getSecid());
            map.put("isin", bond.getIsin());
            map.put("name", bond.getShortName());
            map.put("issuer", bond.getIssuerName());
            map.put("bondType", bond.getBondType());
            map.put("couponType", bond.getCouponType().name());
            map.put("currency", bond.getCurrency());
            map.put("maturityDate", bond.getMaturityDate());
            map.put("offerDate", bond.getOfferDate());
            map.put("listLevel", bond.getListLevel());
            map.put("lotSize", bond.getLotSize());
            map.put("issueSizeRub", bond.getIssueSize());
            map.put("amortizing", bond.isAmortizing());
            BondQuote quote = quotes.get(bond.getId());
            if (quote != null) {
                map.put("pricePct", quote.getPricePct());
                map.put("nkdRub", quote.getNkd());
                map.put("ytmPct", quote.getYtm());
                map.put("durationYears", quote.getDurationYears());
                map.put("volumeRub", quote.getVolumeRub());
                map.put("snapshotTs", quote.getSnapshotTs());
            }
            return map;
        }).toList();
        return new PageResponse<>(content, safePage, safeSize, total);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> card(String exchangeCode, String secid) {
        Bond bond = bondRepository.findByExchangeIdAndSecid(
                        exchangeRepository.findByCodeIgnoreCase(exchangeCode)
                                .orElseThrow(() -> ApiException.notFound("Биржа не найдена: " + exchangeCode)).getId(),
                        secid)
                .orElseThrow(() -> ApiException.notFound("Бумага не найдена: " + secid));
        List<BondQuote> quotes = quoteRepository.findByBondIdOrderBySnapshotTsDesc(bond.getId(), PageRequest.of(0, 1));
        BondQuote quote = quotes.isEmpty() ? null : quotes.get(0);
        List<BondCashflow> cashflows = cashflowRepository.findByBondIdOrderByFlowDateAsc(bond.getId());
        BondCandidate candidate = new BondCandidate(bond, quote, cashflows);
        BondMetricsService.BondMetrics metrics = metricsService.metrics(candidate, OfferTreatment.AUTO,
                OfferTreatment.OPTION, LocalDate.now(), new BigDecimal("0.05"));
        RatingView rating = bond.getIssuer() == null ? null
                : ratingService.resolve(List.of(bond.getIssuer().getId()), 180).get(bond.getIssuer().getId());

        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", bond.getId());
        map.put("secid", bond.getSecid());
        map.put("boardId", bond.getBoardId());
        map.put("isin", bond.getIsin());
        map.put("name", bond.getShortName());
        map.put("issuer", bond.getIssuerName());
        map.put("bondType", bond.getBondType());
        map.put("couponType", bond.getCouponType().name());
        map.put("currency", bond.getCurrency());
        map.put("faceValue", bond.getFaceValue());
        map.put("couponValue", bond.getCouponValue());
        map.put("couponPeriodDays", bond.getCouponPeriodDays());
        map.put("maturityDate", bond.getMaturityDate());
        map.put("offerDate", bond.getOfferDate());
        map.put("listLevel", bond.getListLevel());
        map.put("lotSize", bond.getLotSize());
        map.put("issueSizeRub", bond.getIssueSize());
        map.put("amortizing", bond.isAmortizing());
        map.put("status", bond.getStatus());
        if (quote != null) {
            Map<String, Object> quoteMap = new LinkedHashMap<>();
            quoteMap.put("snapshotTs", quote.getSnapshotTs());
            quoteMap.put("pricePct", quote.getPricePct());
            quoteMap.put("nkdRub", quote.getNkd());
            quoteMap.put("ytmPct", quote.getYtm());
            quoteMap.put("durationYears", quote.getDurationYears());
            quoteMap.put("volumeRub", quote.getVolumeRub());
            quoteMap.put("numTrades", quote.getNumTrades());
            map.put("quote", quoteMap);
        }
        if (metrics != null) {
            map.put("calculated", Map.of(
                    "ytmPct", metrics.ytmPct() == null ? BigDecimal.ZERO : metrics.ytmPct(),
                    "durationYears", metrics.modifiedDurationYears() == null ? BigDecimal.ZERO : metrics.modifiedDurationYears(),
                    "dirtyPriceRub", metrics.dirtyPriceNoFee(),
                    "effectiveMaturity", metrics.effectiveMaturity()));
        }
        map.put("rating", rating);
        map.put("cashflows", cashflows.stream().map(flow -> Map.of(
                "date", flow.getFlowDate(),
                "type", flow.getFlowType().name(),
                "amountPerBond", flow.getAmountPerBond() == null ? BigDecimal.ZERO : flow.getAmountPerBond())).toList());
        map.put("factors", bond.getIssuer() == null ? List.of()
                : factorRepository.findByIssuerIdOrderByAsOfDesc(bond.getIssuer().getId()).stream()
                        .map(this::factorMap).toList());
        return map;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> cashflows(String exchangeCode, String secid) {
        Bond bond = bondRepository.findByExchangeIdAndSecid(
                        exchangeRepository.findByCodeIgnoreCase(exchangeCode)
                                .orElseThrow(() -> ApiException.notFound("Биржа не найдена: " + exchangeCode)).getId(),
                        secid)
                .orElseThrow(() -> ApiException.notFound("Бумага не найдена: " + secid));
        return cashflowRepository.findByBondIdOrderByFlowDateAsc(bond.getId()).stream().map(flow -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("date", flow.getFlowDate());
            map.put("type", flow.getFlowType().name());
            map.put("amountPerBond", flow.getAmountPerBond());
            map.put("percent", flow.getPercent());
            return map;
        }).toList();
    }

    private Map<String, Object> factorMap(IssuerFactor factor) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("code", factor.getFactorCode());
        map.put("value", factor.getValueNum());
        map.put("unit", factor.getUnit());
        map.put("period", factor.getPeriod());
        map.put("asOf", factor.getAsOf());
        return map;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public FilterParams defaultFilters() {
        return FilterParams.from(Params.of(null));
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> searchIssuers(String query) {
        String q = blankToNull(query);
        List<com.bondplanner.domain.entity.Issuer> issuers = q == null
                ? issuerRepository.findAll(PageRequest.of(0, 50)).getContent()
                : issuerRepository.findByNameContainingIgnoreCase(q);
        return issuers.stream().map(issuer -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", issuer.getId());
            map.put("name", issuer.getName());
            map.put("inn", issuer.getInn());
            map.put("sector", issuer.getSector());
            return map;
        }).toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> issuerCard(Long id) {
        var issuer = issuerRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Эмитент не найден: " + id));
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", issuer.getId());
        map.put("name", issuer.getName());
        map.put("inn", issuer.getInn());
        map.put("sector", issuer.getSector());
        map.put("country", issuer.getCountry());
        map.put("ratings", issuerRatings(id));
        map.put("factors", issuerFactors(id));
        map.put("bonds", bondRepository.findAll().stream()
                .filter(b -> b.getIssuer() != null && b.getIssuer().getId().equals(id))
                .map(b -> Map.<String, Object>of(
                        "secid", b.getSecid(),
                        "name", b.getShortName() == null ? b.getSecid() : b.getShortName(),
                        "maturityDate", b.getMaturityDate() == null ? "" : b.getMaturityDate().toString(),
                        "bondType", b.getBondType() == null ? "" : b.getBondType()))
                .toList());
        return map;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> issuerRatings(Long id) {
        return ratingService.history(id).stream().map(rating -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("agency", rating.agency());
            map.put("rating", rating.rating());
            map.put("ratingNormalized", rating.ratingNormalized());
            map.put("score", rating.score());
            map.put("outlook", rating.outlook());
            map.put("ratingDate", rating.ratingDate());
            map.put("sourceUrl", rating.sourceUrl());
            map.put("stale", rating.stale());
            return map;
        }).toList();
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> issuerFactors(Long id) {
        return factorRepository.findByIssuerIdOrderByAsOfDesc(id).stream().map(this::factorMap).toList();
    }
}
