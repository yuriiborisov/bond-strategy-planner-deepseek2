package com.bondplanner.api.controller;

import com.bondplanner.api.dto.PageResponse;
import com.bondplanner.api.dto.StrategyDto;
import com.bondplanner.domain.entity.Exchange;
import com.bondplanner.repository.ExchangeRepository;
import com.bondplanner.service.catalog.BondService;
import com.bondplanner.service.catalog.CatalogService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class CatalogController {

    private final CatalogService catalogService;
    private final BondService bondService;
    private final ExchangeRepository exchangeRepository;

    @GetMapping("/strategies")
    public List<StrategyDto> strategies() {
        return catalogService.strategies();
    }

    @GetMapping("/strategies/{code}")
    public StrategyDto strategy(@PathVariable String code) {
        return catalogService.strategy(code);
    }

    @GetMapping("/exchanges")
    public List<Map<String, Object>> exchanges() {
        return exchangeRepository.findByEnabledTrueOrderByCode().stream().map(this::exchangeMap).toList();
    }

    @GetMapping("/bonds")
    public PageResponse<Map<String, Object>> bonds(
            @RequestParam(defaultValue = "MOEX") String exchange,
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return bondService.search(exchange, query, type, page, size);
    }

    @GetMapping("/bonds/{exchange}/{secid}")
    public Map<String, Object> bond(@PathVariable String exchange, @PathVariable String secid) {
        return bondService.card(exchange, secid);
    }

    @GetMapping("/bonds/{exchange}/{secid}/cashflows")
    public List<Map<String, Object>> cashflows(@PathVariable String exchange, @PathVariable String secid) {
        return bondService.cashflows(exchange, secid);
    }

    @GetMapping("/issuers")
    public List<Map<String, Object>> issuers(@RequestParam(defaultValue = "") String query) {
        return bondService.searchIssuers(query);
    }

    @GetMapping("/issuers/{id}")
    public Map<String, Object> issuer(@PathVariable Long id) {
        return bondService.issuerCard(id);
    }

    @GetMapping("/issuers/{id}/ratings")
    public List<Map<String, Object>> issuerRatings(@PathVariable Long id) {
        return bondService.issuerRatings(id);
    }

    @GetMapping("/issuers/{id}/factors")
    public List<Map<String, Object>> issuerFactors(@PathVariable Long id) {
        return bondService.issuerFactors(id);
    }

    private Map<String, Object> exchangeMap(Exchange exchange) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", exchange.getId());
        map.put("code", exchange.getCode());
        map.put("name", exchange.getName());
        map.put("provider", exchange.getProvider());
        map.put("timezone", exchange.getTimezone());
        map.put("isDefault", exchange.isDefaultExchange());
        return map;
    }
}
