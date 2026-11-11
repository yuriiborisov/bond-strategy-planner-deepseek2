package com.bondplanner.api.controller;

import com.bondplanner.service.admin.AdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;

    @PostMapping("/sync")
    public Map<String, Object> sync(@RequestBody(required = false) SyncRequest request) {
        return adminService.sync(request == null ? "MOEX" : request.exchange(),
                request == null ? "FULL" : request.kind());
    }

    @GetMapping("/sync/runs")
    public List<Map<String, Object>> syncRuns() {
        return adminService.syncRuns();
    }

    @GetMapping("/stats")
    public Map<String, Object> stats() {
        return adminService.stats();
    }

    @PostMapping(value = "/ratings/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> importRatings(@RequestPart("file") MultipartFile file) throws IOException {
        return adminService.importRatings(file.getInputStream());
    }

    @PostMapping("/ratings/sync")
    public Map<String, Object> syncRatings(@RequestBody Map<String, String> request) {
        return adminService.syncRatingSource(request.getOrDefault("source", "ACRA"));
    }

    @GetMapping("/ratings/unmatched")
    public List<Map<String, Object>> unmatched() {
        return adminService.unmatchedIssuers();
    }

    @PostMapping("/issuer-aliases")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> addAlias(@RequestBody AliasRequest request) {
        return adminService.addAlias(request.issuerId(), request.alias());
    }

    @DeleteMapping("/issuer-aliases/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAlias(@PathVariable Long id) {
        adminService.deleteAlias(id);
    }

    @GetMapping("/rating-sources")
    public List<Map<String, Object>> ratingSources() {
        return adminService.ratingSources();
    }

    @PatchMapping("/rating-sources/{id}")
    public Map<String, Object> updateRatingSource(@PathVariable Long id,
                                                  @RequestBody RatingSourcePatch request) {
        return adminService.updateRatingSource(id, request.enabled(), request.priority(), request.cron());
    }

    @PatchMapping("/exchanges/{id}")
    public Map<String, Object> updateExchange(@PathVariable Long id, @RequestBody ExchangePatch request) {
        return adminService.updateExchange(id, request.enabled(), request.isDefault());
    }

    @GetMapping("/ai/analyses")
    public Map<String, Object> aiAnalyses(@RequestParam(required = false) String status,
                                          @RequestParam(defaultValue = "0") int page,
                                          @RequestParam(defaultValue = "20") int size) {
        return adminService.aiAnalyses(status, page, size);
    }

    @PatchMapping("/ai/config")
    public Map<String, Object> aiConfig(@RequestBody AiConfigPatch request) {
        return adminService.updateAiConfig(request.enabled(), request.dailyLimit());
    }

    public record SyncRequest(String exchange, String kind) {
    }

    public record AliasRequest(Long issuerId, String alias) {
    }

    public record RatingSourcePatch(Boolean enabled, Integer priority, String cron) {
    }

    public record ExchangePatch(Boolean enabled, Boolean isDefault) {
    }

    public record AiConfigPatch(Boolean enabled, Integer dailyLimit) {
    }
}
