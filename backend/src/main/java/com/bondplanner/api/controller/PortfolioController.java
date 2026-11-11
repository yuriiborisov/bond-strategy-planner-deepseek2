package com.bondplanner.api.controller;

import com.bondplanner.security.AuthUser;
import com.bondplanner.service.portfolio.PortfolioService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/portfolios")
@RequiredArgsConstructor
public class PortfolioController {

    private final PortfolioService portfolioService;

    @GetMapping
    public Map<String, Object> list(@AuthenticationPrincipal AuthUser user,
                                    @RequestParam(defaultValue = "0") int page,
                                    @RequestParam(defaultValue = "20") int size) {
        return portfolioService.list(user.id(), page, size);
    }

    @GetMapping("/{id}")
    public Map<String, Object> get(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) {
        return portfolioService.get(user.id(), id);
    }

    @PatchMapping("/{id}")
    public Map<String, Object> rename(@AuthenticationPrincipal AuthUser user, @PathVariable Long id,
                                      @RequestBody RenameRequest request) {
        return portfolioService.rename(user.id(), id, request.name());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) {
        portfolioService.delete(user.id(), id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/export")
    public ResponseEntity<byte[]> export(@AuthenticationPrincipal AuthUser user, @PathVariable Long id,
                                         @RequestParam(defaultValue = "csv") String format) {
        byte[] body = portfolioService.export(user.id(), id, format);
        String filename = "portfolio-" + id + ("xlsx".equalsIgnoreCase(format) ? ".xlsx" : ".csv");
        MediaType type = "xlsx".equalsIgnoreCase(format)
                ? MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                : new MediaType("text", "csv", java.nio.charset.StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(type)
                .body(body);
    }

    public record RenameRequest(String name) {
    }
}
