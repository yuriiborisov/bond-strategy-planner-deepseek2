package com.bondplanner.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private String moexBaseUrl = "https://iss.moex.com/iss";
    private boolean syncEnabled = false;
    private String taxProfile = "RU_FLAT_13";
    private boolean ratingsSyncEnabled = true;
    private long ratingsMinIntervalMs = 1000;
    private boolean assetChecksEnabled = true;
    private boolean seedDemo = true;
    private int calcMaxBonds = 5000;
    private List<String> corsAllowedOrigins = new ArrayList<>(List.of("http://localhost:5173", "http://127.0.0.1:5173", "http://localhost:3000", "http://127.0.0.1:3000"));

    private Jwt jwt = new Jwt();
    private Llm llm = new Llm();
    private Scoring scoring = new Scoring();

    @Getter
    @Setter
    public static class Jwt {
        private String secret = "dev-only-secret-key-change-me-0123456789abcdef";
        private Duration accessTtl = Duration.ofMinutes(15);
        private Duration refreshTtl = Duration.ofDays(30);
    }

    @Getter
    @Setter
    public static class Llm {
        private boolean enabled = false;
        private String provider = "LMSTUDIO";
        private String baseUrl = "";
        private String apiKey = "";
        private String model = "qwen3.5-14b";
        private Duration timeout = Duration.ofSeconds(60);
        private int dailyLimit = 0;
        private String privacyMode = "PERCENT_ONLY";
        private int maxContextTokens = 12000;
    }

    @Getter
    @Setter
    public static class Scoring {
        private double weightYtm = 0.40;
        private double weightLiquidity = 0.20;
        private double weightRating = 0.15;
        private double weightMaturity = 0.15;
        private double weightIssueSize = 0.10;
    }
}
