package com.selfcheckout.catalog;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Catalog Service — product reference data. Owns the {@code catalog_items} and
 * {@code inventory_stock} data domains and serves {@code GET /items} and
 * {@code GET /inventory/low-stock}.
 */
@SpringBootApplication
@EntityScan(basePackages = {
        "com.selfcheckout.shared.catalog",
        "com.selfcheckout.shared.inventory"
})
@EnableJpaRepositories(basePackages = {
        "com.selfcheckout.shared.catalog",
        "com.selfcheckout.shared.inventory"
})
public class CatalogApplication {
    public static void main(String[] args) {
        SpringApplication.run(CatalogApplication.class, args);
    }
}
