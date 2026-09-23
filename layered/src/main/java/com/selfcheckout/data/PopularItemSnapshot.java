package com.selfcheckout.data;

import jakarta.persistence.*;


import java.time.LocalDateTime;

/**
 * One ranked row of a computed popular-items window. Each recomputation writes several
 * rows sharing the same {@code windowStart}/{@code windowEnd}.
 */
@Entity
@Table(name = "popular_items_snapshot")
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

    public PopularItemSnapshot() {

    }

    public PopularItemSnapshot(Long windowStart, Long windowEnd, LocalDateTime computedAt, String sku, String name, int scanCount, int rank) {
        this.windowStart = windowStart;
        this.windowEnd = windowEnd;
        this.computedAt = computedAt;
        this.sku = sku;
        this.name = name;
        this.scanCount = scanCount;
        this.rank = rank;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getWindowStart() {
        return windowStart;
    }

    public void setWindowStart(Long windowStart) {
        this.windowStart = windowStart;
    }

    public Long getWindowEnd() {
        return windowEnd;
    }

    public void setWindowEnd(Long windowEnd) {
        this.windowEnd = windowEnd;
    }

    public LocalDateTime getComputedAt() {
        return computedAt;
    }

    public void setComputedAt(LocalDateTime computedAt) {
        this.computedAt = computedAt;
    }

    public String getSku() {
        return sku;
    }

    public void setSku(String sku) {
        this.sku = sku;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getScanCount() {
        return scanCount;
    }

    public void setScanCount(int scanCount) {
        this.scanCount = scanCount;
    }

    public int getRank() {
        return rank;
    }

    public void setRank(int rank) {
        this.rank = rank;
    }
}