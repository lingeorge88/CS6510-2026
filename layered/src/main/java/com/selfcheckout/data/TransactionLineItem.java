package com.selfcheckout.data;


import jakarta.persistence.*;

import java.math.BigDecimal;

/**
 * A single scanned unit within a transaction. One row per scan; rows are grouped by SKU
 * at completion to build receipt lines.
 *
 * <p>The index on {@code transaction_id} matters: without it {@code findByTransactionId}
 * sequentially scans a table that grows throughout a load run.
 */
@Entity
@Table(name = "transaction_line_items",
       indexes = @Index(name = "idx_line_item_transaction_id", columnList = "transaction_id"))
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