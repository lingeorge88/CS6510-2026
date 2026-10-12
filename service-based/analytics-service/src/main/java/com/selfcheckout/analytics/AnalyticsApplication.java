package com.selfcheckout.analytics;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Analytics Service — owns {@code popular_items_snapshot} and reads the shared
 * {@code scan_events} log to maintain the hopping-window popular-items ranking.
 *
 * <p>Coupling to Checkout is purely through the shared database: Checkout writes
 * scan rows, this service reads them on a schedule. There is no service-to-service
 * call in either direction.
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
