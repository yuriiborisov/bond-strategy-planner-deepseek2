package com.bondplanner.api.controller;

import com.bondplanner.api.dto.CalculationRequest;
import com.bondplanner.api.error.ApiException;
import com.bondplanner.security.AuthUser;
import com.bondplanner.service.calc.CalculationService;
import com.bondplanner.service.portfolio.PortfolioService;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/calculations")
@RequiredArgsConstructor
public class CalculationController {

    private final CalculationService calculationService;
    private final PortfolioService portfolioService;

    @PostMapping
    @ResponseStatus(HttpStatus.OK)
    public JsonNode calculate(@AuthenticationPrincipal AuthUser user,
                              @RequestHeader(value = "X-Session-Id", required = false) String sessionId,
                              @Valid @RequestBody CalculationRequest request) {
        return calculationService.calculate(user == null ? null : user.id(), sessionId, request);
    }

    @GetMapping("/{id}")
    public JsonNode get(@AuthenticationPrincipal AuthUser user,
                        @RequestHeader(value = "X-Session-Id", required = false) String sessionId,
                        @PathVariable Long id) {
        return calculationService.get(id, user == null ? null : user.id(), sessionId);
    }

    @GetMapping
    public JsonNode history(@AuthenticationPrincipal AuthUser user,
                            @RequestHeader(value = "X-Session-Id", required = false) String sessionId,
                            @RequestParam(defaultValue = "0") int page,
                            @RequestParam(defaultValue = "20") int size) {
        if (user == null && (sessionId == null || sessionId.isBlank())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED",
                    "Для истории нужна авторизация или заголовок X-Session-Id");
        }
        return calculationService.history(user == null ? null : user.id(), sessionId, page, size);
    }

    @PostMapping("/{id}/save")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> save(@AuthenticationPrincipal AuthUser user,
                                    @PathVariable Long id,
                                    @RequestBody(required = false) SavePortfolioRequest request) {
        if (user == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED",
                    "Сохранение портфеля доступно после входа");
        }
        String name = request == null ? null : request.name();
        return portfolioService.save(user.id(), id, name);
    }

    public record SavePortfolioRequest(String name) {
    }
}
