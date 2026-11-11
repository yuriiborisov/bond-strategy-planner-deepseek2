package com.bondplanner.provider;

import java.time.LocalDate;
import java.util.List;

/**
 * SPI провайдера биржи: провайдер не знает о стратегиях и отдаёт нормализованные данные.
 */
public interface ExchangeProvider {

    String code();

    List<RawBond> fetchBonds();

    List<RawCashflow> fetchCashflows(String secid);

    default List<RawBond> fetchHistory(LocalDate from, LocalDate to) {
        return List.of();
    }
}
