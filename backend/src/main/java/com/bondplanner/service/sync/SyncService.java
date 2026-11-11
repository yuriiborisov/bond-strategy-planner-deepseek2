package com.bondplanner.service.sync;

import com.bondplanner.api.error.ApiException;
import com.bondplanner.config.AppProperties;
import com.bondplanner.domain.entity.Bond;
import com.bondplanner.domain.entity.BondCashflow;
import com.bondplanner.domain.entity.BondQuote;
import com.bondplanner.domain.entity.Exchange;
import com.bondplanner.domain.entity.Issuer;
import com.bondplanner.domain.entity.SyncRun;
import com.bondplanner.domain.model.CouponType;
import com.bondplanner.domain.model.FlowType;
import com.bondplanner.domain.model.SyncStatus;
import com.bondplanner.provider.ExchangeProvider;
import com.bondplanner.provider.RawBond;
import com.bondplanner.provider.RawCashflow;
import com.bondplanner.repository.BondCashflowRepository;
import com.bondplanner.repository.BondQuoteRepository;
import com.bondplanner.repository.BondRepository;
import com.bondplanner.repository.ExchangeRepository;
import com.bondplanner.repository.IssuerRepository;
import com.bondplanner.repository.SyncRunRepository;
import com.bondplanner.service.rating.IssuerNames;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Синхронизация с биржей и запись sync_runs (§5.4, §8.3).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SyncService {

    private static final int CASHFLOW_BOND_LIMIT = 600;

    private final ExchangeRepository exchangeRepository;
    private final BondRepository bondRepository;
    private final BondQuoteRepository quoteRepository;
    private final BondCashflowRepository cashflowRepository;
    private final IssuerRepository issuerRepository;
    private final SyncRunRepository syncRunRepository;
    private final IssuerNames issuerNames;
    private final List<ExchangeProvider> providers;
    private final AppProperties properties;
    private final AtomicBoolean running = new AtomicBoolean(false);

    @Transactional
    public SyncRun run(String exchangeCode, String kind) {
        if (!running.compareAndSet(false, true)) {
            throw new ApiException(HttpStatus.CONFLICT, "CONFLICT", "Синхронизация уже выполняется");
        }
        try {
            return doRun(exchangeCode, kind.toUpperCase());
        } finally {
            running.set(false);
        }
    }

    private SyncRun doRun(String exchangeCode, String kind) {
        Exchange exchange = exchangeRepository.findByCodeIgnoreCase(exchangeCode)
                .orElseThrow(() -> ApiException.notFound("Биржа не найдена: " + exchangeCode));
        ExchangeProvider provider = providers.stream()
                .filter(p -> p.code().equalsIgnoreCase(exchange.getProvider()))
                .findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "EXCHANGE_UNAVAILABLE",
                        "Провайдер биржи не реализован: " + exchange.getProvider()));
        SyncRun run = new SyncRun();
        run.setExchange(exchange);
        run.setProviderCode(provider.code());
        run.setKind(kind);
        run.setStartedAt(Instant.now());
        run.setStatus(SyncStatus.RUNNING);
        run.setRowsUpserted(0);
        syncRunRepository.save(run);

        int rows = 0;
        int errors = 0;
        try {
            if ("FULL".equals(kind) || "REFERENCE".equals(kind) || "QUOTES".equals(kind)) {
                List<RawBond> bonds = provider.fetchBonds();
                Instant snapshot = Instant.now();
                Map<String, Bond> existing = new HashMap<>();
                for (Bond bond : bondRepository.findByExchangeIdAndStatus(exchange.getId(), "ACTIVE")) {
                    existing.put(bond.getBoardId() + ":" + bond.getSecid(), bond);
                }
                for (RawBond raw : bonds) {
                    try {
                        Bond bond = upsertBond(exchange, raw, existing);
                        if ("FULL".equals(kind) || "QUOTES".equals(kind)) {
                            if (upsertQuote(bond, raw, snapshot)) {
                                rows++;
                            }
                        } else {
                            rows++;
                        }
                    } catch (Exception ex) {
                        errors++;
                        log.debug("Не удалось сохранить бумагу {}: {}", raw.secid(), ex.getMessage());
                    }
                }
            }
            if ("FULL".equals(kind) || "CASHFLOWS".equals(kind)) {
                List<Bond> bonds = bondRepository.findByExchangeIdAndStatus(exchange.getId(), "ACTIVE");
                int limit = Math.min(bonds.size(), CASHFLOW_BOND_LIMIT);
                for (int i = 0; i < limit; i++) {
                    Bond bond = bonds.get(i);
                    try {
                        List<RawCashflow> flows = provider.fetchCashflows(bond.getSecid());
                        cashflowRepository.deleteByBondId(bond.getId());
                        for (RawCashflow raw : flows) {
                            if (raw.date() == null || "OFFER".equals(raw.flowType())) {
                                continue;
                            }
                            BondCashflow entity = new BondCashflow(bond, FlowType.valueOf(raw.flowType()),
                                    raw.date(), raw.amountPerBond());
                            cashflowRepository.save(entity);
                        }
                        rows++;
                    } catch (Exception ex) {
                        errors++;
                        log.debug("Купонный календарь {} не загружен: {}", bond.getSecid(), ex.getMessage());
                    }
                }
            }
            run.setRowsUpserted(rows);
            run.setStatus(errors == 0 ? SyncStatus.SUCCESS : (rows > 0 ? SyncStatus.PARTIAL : SyncStatus.FAILED));
            if (errors > 0) {
                run.setErrorMessage("Ошибок: " + errors);
            }
        } catch (Exception ex) {
            log.error("Синхронизация {} {} завершилась ошибкой", exchangeCode, kind, ex);
            run.setStatus(SyncStatus.FAILED);
            run.setErrorMessage(ex.getMessage());
        } finally {
            run.setFinishedAt(Instant.now());
            run.setMeta("{\"rows\":" + rows + ",\"errors\":" + errors + "}");
            syncRunRepository.save(run);
        }
        return run;
    }

    private Bond upsertBond(Exchange exchange, RawBond raw, Map<String, Bond> existing) {
        String key = raw.boardId() + ":" + raw.secid();
        Bond bond = existing.get(key);
        if (bond == null) {
            bond = new Bond();
            bond.setExchange(exchange);
            bond.setSecid(raw.secid());
            bond.setBoardId(raw.boardId());
            bond.setFirstSeenAt(Instant.now());
        }
        bond.setIsin(raw.isin());
        bond.setShortName(raw.shortName());
        bond.setIssuerName(raw.issuerName());
        bond.setBondType(raw.bondType());
        bond.setCouponType(parseCouponType(raw.couponType()));
        bond.setCurrency(raw.currency() == null ? "RUB" : raw.currency());
        bond.setFaceValue(raw.faceValue());
        bond.setCouponValue(raw.couponValue());
        bond.setCouponPeriodDays(raw.couponPeriodDays());
        bond.setMaturityDate(raw.maturityDate());
        bond.setOfferDate(raw.offerDate());
        bond.setListLevel(raw.listLevel());
        bond.setLotSize(raw.lotSize() == null ? 1 : raw.lotSize());
        bond.setIssueSize(raw.issueSize());
        bond.setAmortizing(raw.amortizing());
        bond.setPerpetual(raw.perpetual());
        bond.setStatus("ACTIVE");
        bond.setUpdatedAt(Instant.now());
        if (raw.issuerName() != null) {
            String normalized = issuerNames.normalize(raw.issuerName());
            if (!normalized.isBlank()) {
                Issuer issuer = issuerRepository.findByNormalizedName(normalized).orElseGet(() -> {
                    Issuer created = new Issuer();
                    created.setName(raw.issuerName());
                    created.setNormalizedName(normalized);
                    created.setCountry("RU");
                    return issuerRepository.save(created);
                });
                bond.setIssuer(issuer);
            }
        }
        return bondRepository.save(bond);
    }

    private boolean upsertQuote(Bond bond, RawBond raw, Instant snapshot) {
        if (raw.pricePct() == null) {
            return false;
        }
        if (!quoteRepository.findByBondIdAndSnapshotTs(bond.getId(), snapshot).isEmpty()) {
            return false;
        }
        BondQuote quote = new BondQuote();
        quote.setBond(bond);
        quote.setSnapshotTs(snapshot);
        quote.setPricePct(raw.pricePct());
        quote.setNkd(raw.nkd());
        quote.setYtm(raw.ytm());
        quote.setYtmToOffer(raw.ytmToOffer());
        quote.setDurationYears(raw.durationYears());
        quote.setVolumeRub(raw.volumeRub());
        quote.setNumTrades(raw.numTrades());
        quote.setSource("MOEX_ISS");
        quoteRepository.save(quote);
        return true;
    }

    private CouponType parseCouponType(String value) {
        if (value == null) {
            return CouponType.FIXED;
        }
        try {
            return CouponType.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException ex) {
            return CouponType.FIXED;
        }
    }

    @Scheduled(cron = "${app.sync.reference-cron:0 0 3 * * *}")
    public void scheduledReference() {
        if (properties.isSyncEnabled()) {
            safeRun("MOEX", "REFERENCE");
        }
    }

    @Scheduled(cron = "${app.sync.quotes-cron:0 */15 10-19 * * MON-FRI}")
    public void scheduledQuotes() {
        if (properties.isSyncEnabled()) {
            safeRun("MOEX", "QUOTES");
        }
    }

    @Scheduled(cron = "${app.sync.cashflows-cron:0 30 3 * * *}")
    public void scheduledCashflows() {
        if (properties.isSyncEnabled()) {
            safeRun("MOEX", "CASHFLOWS");
        }
    }

    private void safeRun(String exchange, String kind) {
        try {
            run(exchange, kind);
        } catch (Exception ex) {
            log.warn("Плановая синхронизация {} {} не выполнена: {}", exchange, kind, ex.getMessage());
        }
    }
}
