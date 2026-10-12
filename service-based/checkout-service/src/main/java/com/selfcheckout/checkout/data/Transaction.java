package com.selfcheckout.checkout.data;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/** A checkout transaction at a station. */
@Entity
@Table(name = "transactions")
public class Transaction {

    @Id
    @Column(name = "transaction_id", length = 50, nullable = false)
    private String transactionId;

    @Column(name = "station_id", length = 50, nullable = false)
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

    @Column(name = "running_total", precision = 10, scale = 2, nullable = false)
    private BigDecimal runningTotal;

    @Column(name = "started_at", nullable = false, updatable = false)
    private LocalDateTime startedAt;

    @JsonIgnore
    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    public Transaction() {
        this.transactionId = "tx-" + UUID.randomUUID();
        this.status = TransactionStatus.OPEN;
        this.itemCount = 0;
        this.runningTotal = BigDecimal.ZERO;
        this.startedAt = LocalDateTime.now();
    }

    public String getTransactionId() {
        return transactionId;
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

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
    }
}
