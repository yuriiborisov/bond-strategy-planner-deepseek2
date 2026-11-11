package com.bondplanner;

import com.bondplanner.config.AppProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(exclude = org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration.class)
@EnableConfigurationProperties(AppProperties.class)
@EnableScheduling
@EnableAsync
@EnableCaching
public class BondPlannerApplication {

    public static void main(String[] args) {
        SpringApplication.run(BondPlannerApplication.class, args);
    }
}
