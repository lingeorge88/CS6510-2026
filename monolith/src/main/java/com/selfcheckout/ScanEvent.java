package com.selfcheckout;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * A record of a single item scan, mapped to the {@code scan_events} table.
 * Each event carries a monotonically increasing {@code globalSeq} used to define the
 * hopping window for popular-items analytics (last 1000 scans, recomputed every 500).
 */
@Entity
@Table(name = "scan_events")
public class ScanEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "global_seq", nullable = false, unique = true)
    private Long globalSeq;

    @Column(length = 20, nullable = false)
    private String sku;

    @Column(name = "scanned_at", nullable = false, updatable = false)
    private LocalDateTime scannedAt;

    public ScanEvent() {

    }
    public ScanEvent(Long globalSeq, String sku, LocalDateTime scannedAt) {
        this.globalSeq = globalSeq;
        this.sku = sku;
        this.scannedAt = scannedAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getGlobalSeq() {
        return globalSeq;
    }

    public void setGlobalSeq(Long globalSeq) {
        this.globalSeq = globalSeq;
    }

    public String getSku() {
        return sku;
    }

    public void setSku(String sku) {
        this.sku = sku;
    }

    public LocalDateTime getScannedAt() {
        return scannedAt;
    }

    public void setScannedAt(LocalDateTime scannedAt) {
        this.scannedAt = scannedAt;
    }
}