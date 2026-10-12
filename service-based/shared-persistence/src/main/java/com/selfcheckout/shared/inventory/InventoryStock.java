package com.selfcheckout.shared.inventory;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Per-SKU stock level. */
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
