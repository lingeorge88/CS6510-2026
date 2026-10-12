package com.selfcheckout.analytics.data;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/** One ranked row of a computed popular-items window. */
@Entity
@Table(name = "popular_items_snapshot",
        indexes = @Index(name = "idx_snapshot_window_end_rank", columnList = "window_end,rank"))
public class PopularItemSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "window_start", nullable = false)
    private Long windowStart;

    @Column(name = "window_end", nullable = false)
    private Long windowEnd;

    @Column(name = "computed_at", nullable = false)
    private LocalDateTime computedAt;

    @Column(length = 20, nullable = false)
    private String sku;

    @Column(nullable = false)
    private String name;

    @Column(name = "scan_count", nullable = false)
    private int scanCount;

    @Column(nullable = false)
    private int rank;

    protected PopularItemSnapshot() {
    }

    public PopularItemSnapshot(Long windowStart, Long windowEnd, LocalDateTime computedAt,
                               String sku, String name, int scanCount, int rank) {
        this.windowStart = windowStart;
        this.windowEnd = windowEnd;
        this.computedAt = computedAt;
        this.sku = sku;
        this.name = name;
        this.scanCount = scanCount;
        this.rank = rank;
    }

    public Long getWindowStart() {
        return windowStart;
    }

    public Long getWindowEnd() {
        return windowEnd;
    }

    public LocalDateTime getComputedAt() {
        return computedAt;
    }

    public String getSku() {
        return sku;
    }

    public String getName() {
        return name;
    }

    public int getScanCount() {
        return scanCount;
    }

    public int getRank() {
        return rank;
    }
}
