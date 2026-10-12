package com.selfcheckout.shared.scan;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/** A single item scan; its identity id doubles as the global scan sequence. */
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
