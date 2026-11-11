package com.bondplanner.api.controller;

import com.bondplanner.security.AuthUser;
import com.bondplanner.service.ai.AiOrchestrator;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
public class AiController {

    private final AiOrchestrator orchestrator;

    @PostMapping("/portfolio-reviews")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String, Object> portfolioReview(@AuthenticationPrincipal AuthUser user,
                                               @RequestBody PortfolioReviewRequest request) {
        long id = orchestrator.requestPortfolioReview(user.id(), request.portfolioId());
        return Map.of("analysisId", id, "status", "PENDING");
    }

    @PostMapping("/discovery")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String, Object> discovery(@AuthenticationPrincipal AuthUser user,
                                         @RequestBody DiscoveryRequest request) {
        long id = orchestrator.requestDiscovery(user.id(), request.calculationId());
        return Map.of("analysisId", id, "status", "PENDING");
    }

    @GetMapping("/analyses/{id}")
    public Map<String, Object> analysis(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) {
        return orchestrator.get(user.id(), id);
    }

    @GetMapping("/analyses")
    public Map<String, Object> analyses(@AuthenticationPrincipal AuthUser user,
                                        @RequestParam(required = false) String type,
                                        @RequestParam(required = false) String status,
                                        @RequestParam(defaultValue = "0") int page,
                                        @RequestParam(defaultValue = "20") int size) {
        return orchestrator.list(user.id(), type, status, page, size);
    }

    @GetMapping("/usage")
    public Map<String, Object> usage(@AuthenticationPrincipal AuthUser user) {
        return orchestrator.usage(user.id());
    }

    @GetMapping("/consent")
    public Map<String, Object> consentStatus(@AuthenticationPrincipal AuthUser user) {
        return orchestrator.consentStatus(user.id());
    }

    @PostMapping("/consent")
    public Map<String, Object> consent(@AuthenticationPrincipal AuthUser user,
                                       @RequestBody(required = false) ConsentRequest request) {
        return orchestrator.grantConsent(user.id(), request == null ? null : request.version());
    }

    @DeleteMapping("/consent")
    public Map<String, Object> revokeConsent(@AuthenticationPrincipal AuthUser user) {
        return orchestrator.revokeConsent(user.id());
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return orchestrator.health();
    }

    public record PortfolioReviewRequest(Long portfolioId) {
    }

    public record DiscoveryRequest(Long calculationId) {
    }

    public record ConsentRequest(String version) {
    }
}
