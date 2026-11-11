package com.bondplanner.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class CacheConfig {

    public static final String STRATEGIES = "strategies";
    public static final String EXCHANGES = "exchanges";
    public static final String SNAPSHOT = "snapshot";
    public static final String RATINGS = "ratings";
    public static final String CHECKS = "assetChecks";

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager(
                STRATEGIES, EXCHANGES, SNAPSHOT, RATINGS, CHECKS);
        manager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(2_000)
                .expireAfterWrite(Duration.ofMinutes(15))
                .recordStats());
        return manager;
    }
}
