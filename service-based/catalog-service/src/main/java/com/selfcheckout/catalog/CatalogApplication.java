package com.selfcheckout.catalog;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Catalog Service — an independently deployable domain service for product
 * reference data. It owns two data domains, {@code catalog_items} and
 * {@code inventory_stock}, and serves {@code GET /items} and
 * {@code GET /inventory/low-stock}. (Catalog and inventory are merged here: both
 * are product reference data, and keeping stock beside the catalog it belongs to
 * is a cleaner coarse-grained boundary than splitting them.)
 *
 * <p>It does NOT perform the stock decrement: that write is issued by the
 * Checkout Service via the shared DAO at completion — the deliberate DB-layer
 * coupling of the service-based style.
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
