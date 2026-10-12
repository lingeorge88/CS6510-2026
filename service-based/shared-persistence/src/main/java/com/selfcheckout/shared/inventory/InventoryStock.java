package com.selfcheckout.shared.inventory;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Per-SKU stock level. Owned by the Inventory Service (low-stock reads), but the
 * entity and its atomic decrement live in shared-persistence because the Checkout
 * Service performs the decrement at transaction completion via the shared DAO —
 * this is the deliberate database-layer coupling of the service-based style.
 */
@Entity
@Table(name = "inventory_stock")
public class InventoryStock {

    @Id
    @Column(length = 20, nullable = false)
    private String sku;

    @Column(name = "stock", nullable = false)
    private int stock;

    protected InventoryStock() {
    }

    public InventoryStock(String sku, int stock) {
        this.sku = sku;
        this.stock = stock;
    }

    public String getSku() {
        return sku;
    }

    public int getStock() {
        return stock;
    }
}
