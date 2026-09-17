package com.selfcheckout;


import jakarta.persistence.*;

import java.math.BigDecimal;

/**
 * A single scanned item within a transaction, mapped to the {@code transaction_line_items} table.
 * Each scan creates one row; at completion, rows are grouped by SKU to build receipt lines
 * with aggregated quantities.
 */
@Entity
@Table(name = "transaction_line_items")
public class TransactionLineItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "transaction_id", length = 50, nullable = false)
    private String transactionId;

    @Column(length = 20, nullable = false)
    private String sku;

    @Column(name = "unit_price", precision = 10, scale = 2, nullable = false)
    private BigDecimal unitPrice;

    public TransactionLineItem() {

    }
    public TransactionLineItem(String transactionId, String sku, BigDecimal unitPrice) {
        this.transactionId = transactionId;
        this.sku = sku;
        this.unitPrice = unitPrice;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(String transactionId) {
        this.transactionId = transactionId;
    }

    public String getSku() {
        return sku;
    }

    public void setSku(String sku) {
        this.sku = sku;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public void setUnitPrice(BigDecimal unitPrice) {
        this.unitPrice = unitPrice;
    }
}