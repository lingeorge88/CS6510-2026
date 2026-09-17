package com.selfcheckout;

import jakarta.persistence.*;
import java.math.BigDecimal;

/**
 * A product in the store catalog, mapped to the {@code catalog_items} table.
 * Each item has a unique SKU, a name, a price, and a current stock level.
 * Stock is decremented atomically at transaction completion via
 * {@link CatalogItemRepository#decrementStock}.
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

    @Column(name = "stock", nullable = false)
    private int stock = 0;

    public CatalogItem() {

    }

    public CatalogItem(String sku, String name, BigDecimal price, Integer stock) {
        this.sku = sku;
        this.name = name;
        this.price = price;
        this.stock = stock;
    }

    public String getSku() {
        return sku;
    }

    public void setSku(String sku) {
        this.sku = sku;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public int getStock() {
        return stock;
    }

    public void setStock(int stock) {
        this.stock = stock;
    }
}