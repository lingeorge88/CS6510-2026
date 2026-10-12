package com.selfcheckout.shared.scan;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * One item scan. Written by the Checkout Service at scan time and read by the
 * Analytics Service to compute popular items — a shared table, so it lives in
 * shared-persistence (Checkout → shared DB → Analytics, with no service call).
 *
 * <p>{@code id} is a DB identity column and doubles as the monotonic global scan
 * sequence that defines the hopping window; {@code windowStart}/{@code windowEnd}
 * in the analytics response are {@code id} values. Because Checkout inserts
 * synchronously at scan time, insertion order is scan order.
 */
@Entity
@Table(name = "scan_events")
public class ScanEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 20, nullable = false)
    private String sku;

    @Column(name = "scanned_at", nullable = false, updatable = false)
    private LocalDateTime scannedAt;

    protected ScanEvent() {
    }

    public ScanEvent(String sku, LocalDateTime scannedAt) {
        this.sku = sku;
        this.scannedAt = scannedAt;
    }

    /** @return the identity id, which doubles as the global scan sequence number */
    public Long getId() {
        return id;
    }

    public String getSku() {
        return sku;
    }

    public LocalDateTime getScannedAt() {
        return scannedAt;
    }
}
