package com.selfcheckout;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

// Entity: Transaction
// Table: transactions
// Schema reference: specs/001-self-checkout-monolith/data-model.md → "2. Transaction"
// Fields: id (PK, assigned not generated), stationId, status, itemCount, runningTotal, startedAt, completedAt
// State transitions: OPEN → COMPLETED or OPEN → CANCELLED

@Entity
@Table(name = "transactions")
public class Transaction {
    @Id
    @Column(length = 50, nullable = false)
    private String transactionId;

    @Column(length = 50, nullable = false)
    private String stationId;

    public enum TransactionStatus {
        OPEN,
        COMPLETED,
        CANCELLED
    }

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private TransactionStatus status;

    @Column(name = "item_count", nullable = false)
    private int itemCount;

    @Column(name = "running_total", precision = 10, scale = 2,nullable = false)
    private BigDecimal runningTotal;

    @Column(name = "started_at", nullable = false, updatable = false)
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    public Transaction() {
        this.transactionId = "tx-" + UUID.randomUUID().toString();
        this.status = TransactionStatus.OPEN;
        this.itemCount = 0;
        this.runningTotal = BigDecimal.ZERO;
        this.startedAt = LocalDateTime.now();
    }

    public String getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(String transactionId) {
        this.transactionId = transactionId;
    }

    public String getStationId() {
        return stationId;
    }

    public void setStationId(String stationId) {
        this.stationId = stationId;
    }

    public TransactionStatus getStatus() {
        return status;
    }

    public void setStatus(TransactionStatus status) {
        this.status = status;
    }

    public int getItemCount() {
        return itemCount;
    }

    public void setItemCount(int itemCount) {
        this.itemCount = itemCount;
    }

    public BigDecimal getRunningTotal() {
        return runningTotal;
    }

    public void setRunningTotal(BigDecimal runningTotal) {
        this.runningTotal = runningTotal;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(LocalDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
    }
}