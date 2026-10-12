package com.selfcheckout.checkout;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Checkout Service — owns the transaction lifecycle ({@code transactions} and
 * {@code transaction_line_items}, private to this service) and drives the two
 * cross-boundary writes that make service-based coupling interesting:
 * <ul>
 *   <li>at scan time it inserts a {@code scan_events} row (read later by Analytics);</li>
 *   <li>at completion it decrements {@code inventory_stock} with the atomic shared DAO,
 *       inside the same local transaction that finalizes the receipt.</li>
 * </ul>
 * It reads {@code catalog_items} for name/price. All of this is DB-layer coupling —
 * the Checkout Service makes no HTTP call to any other domain service.
 */
@SpringBootApplication
@EntityScan(basePackages = {
        "com.selfcheckout.checkout.data",
        "com.selfcheckout.shared.catalog",
        "com.selfcheckout.shared.inventory",
        "com.selfcheckout.shared.scan"
})
@EnableJpaRepositories(basePackages = {
        "com.selfcheckout.checkout.data",
        "com.selfcheckout.shared.catalog",
        "com.selfcheckout.shared.inventory",
        "com.selfcheckout.shared.scan"
})
public class CheckoutApplication {
    public static void main(String[] args) {
        SpringApplication.run(CheckoutApplication.class, args);
    }
}
