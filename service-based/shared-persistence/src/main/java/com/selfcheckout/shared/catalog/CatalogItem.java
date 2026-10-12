package com.selfcheckout.shared.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/**
 * Catalog reference data (sku, name, price). Logically owned by the Catalog
 * Service, but the read model lives in shared-persistence because three services
 * read it (Catalog serves {@code GET /items}; Checkout needs name/price at scan;
 * Analytics joins it to name popular items).
 *
 * <p>Stock is deliberately NOT here — it was split into {@code inventory_stock}
 * so the Inventory Service owns its own data domain.
 */
@Entity
@Table(name = "catalog_items")
public class CatalogItem {

    @Id
    @Column(length = 20, nullable = false)
    private String sku;

    @Column(name = "name", length = 255, nullable = false)
    private String name;

    @Column(name = "price", precision = 10, scale = 2, nullable = false)
    private BigDecimal price;

    protected CatalogItem() {
    }

    public CatalogItem(String sku, String name, BigDecimal price) {
        this.sku = sku;
        this.name = name;
        this.price = price;
    }

    public String getSku() {
        return sku;
    }

    public String getName() {
        return name;
    }

    public BigDecimal getPrice() {
        return price;
    }
}
