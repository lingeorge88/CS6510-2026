package com.selfcheckout.analytics;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Analytics Service — owns {@code popular_items_snapshot}, reads the shared
 * {@code scan_events} log, and serves {@code GET /analytics/popular-items}.
 */
@SpringBootApplication
@EnableScheduling
@EntityScan(basePackages = {
        "com.selfcheckout.analytics.data",
        "com.selfcheckout.shared.scan"
})
@EnableJpaRepositories(basePackages = {
        "com.selfcheckout.analytics.data",
        "com.selfcheckout.shared.scan"
})
public class AnalyticsApplication {
    public static void main(String[] args) {
        SpringApplication.run(AnalyticsApplication.class, args);
    }
}
