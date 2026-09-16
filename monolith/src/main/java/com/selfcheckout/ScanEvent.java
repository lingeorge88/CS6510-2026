package com.selfcheckout;

import jakarta.persistence.*;

import java.time.LocalDateTime;

// Entity: ScanEvent
// Table: scan_events
// Schema reference: specs/001-self-checkout-monolith/data-model.md → "4. ScanEvent"
// Fields: id (PK, auto-generated), globalSeq (unique, monotonic), sku, scannedAt
// Used for the popular items hopping window analytics

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