package com.selfcheckout.checkout.data;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/** A single scanned unit within a transaction (one row per unit). */
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

    protected TransactionLineItem() {
    }

    public TransactionLineItem(String transactionId, String sku, BigDecimal unitPrice) {
        this.transactionId = transactionId;
        this.sku = sku;
        this.unitPrice = unitPrice;
    }

    public Long getId() {
        return id;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public String getSku() {
        return sku;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }
}
