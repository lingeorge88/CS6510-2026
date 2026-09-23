package com.selfcheckout.analytics;

import com.selfcheckout.data.CatalogItem;
import com.selfcheckout.data.CatalogItemRepository;
import com.selfcheckout.data.PopularItemSnapshot;
import com.selfcheckout.data.PopularItemSnapshotRepository;
import com.selfcheckout.data.ScanEvent;
import com.selfcheckout.data.ScanEventRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Scan ingestion, the popular-items hopping window, and low-stock alerting.
 */
@Service
public class AnalyticsService {

    /** Most-recent scans considered by the popular-items window. */
    private static final int WINDOW_SIZE = 1000;
    /** How often, in scans, the window is recomputed. */
    private static final int SLIDE_INTERVAL = 500;
    /** Default number of ranked items returned. */
    private static final int DEFAULT_LIMIT = 10;
    /** Stock level below which an item is considered low-stock. */
    private static final int DEFAULT_THRESHOLD = 50;

    /**
     * Assigns each scan a unique, monotonic sequence number. Resets on boot, which is
     * safe only because {@code ddl-auto: create} truncates {@code scan_events} at startup.
     */
    private final AtomicLong globalScanSeq = new AtomicLong(0);

    private final ScanEventRepository scanEventRepository;
    private final PopularItemSnapshotRepository snapshotRepository;
    private final CatalogItemRepository catalogItemRepository;

    public AnalyticsService(ScanEventRepository scanEventRepository,
                            PopularItemSnapshotRepository snapshotRepository,
                            CatalogItemRepository catalogItemRepository) {
        this.scanEventRepository = scanEventRepository;
        this.snapshotRepository = snapshotRepository;
        this.catalogItemRepository = catalogItemRepository;
    }

    /** Records one scan and recomputes the window every {@link #SLIDE_INTERVAL} scans. */
    public void recordScan(String sku) {
        long seq = globalScanSeq.incrementAndGet();
        scanEventRepository.save(new ScanEvent(seq, sku, LocalDateTime.now()));

        if (seq % SLIDE_INTERVAL == 0 && seq >= WINDOW_SIZE) {
            recompute(seq);
        }
    }

    /** @param limit max ranked items; {@code null} uses the server default. */
    public Map<String, Object> popularItems(Integer limit) {
        int effectiveLimit = limit != null ? limit : DEFAULT_LIMIT;
        List<PopularItemSnapshot> snapshots = snapshotRepository.findLatestSnapshot();

        List<Map<String, Object>> items = new ArrayList<>();
        for (PopularItemSnapshot snapshot : snapshots) {
            if (snapshot.getRank() <= effectiveLimit) {
                items.add(Map.of(
                        "sku", snapshot.getSku(),
                        "name", snapshot.getName(),
                        "scanCount", snapshot.getScanCount(),
                        "rank", snapshot.getRank()));
            }
        }

        boolean empty = snapshots.isEmpty();
        return Map.of(
                "windowSize", WINDOW_SIZE,
                "slideInterval", SLIDE_INTERVAL,
                "windowStart", empty ? 0L : snapshots.get(0).getWindowStart(),
                "windowEnd", empty ? 0L : snapshots.get(0).getWindowEnd(),
                "computedAt", empty ? "" : snapshots.get(0).getComputedAt().toString(),
                "items", items);
    }

    /** @param thresholdOverride per-query threshold; {@code null} uses the server default. */
    public Map<String, Object> lowStock(Integer thresholdOverride) {
        int threshold = thresholdOverride != null ? thresholdOverride : DEFAULT_THRESHOLD;
        List<CatalogItem> lowStockItems = catalogItemRepository.findByStockLessThan(threshold);

        List<Map<String, Object>> alerts = new ArrayList<>(lowStockItems.size());
        for (CatalogItem item : lowStockItems) {
            alerts.add(Map.of(
                    "sku", item.getSku(),
                    "name", item.getName(),
                    "currentStock", item.getStock(),
                    "threshold", threshold,
                    "triggeredAt", LocalDateTime.now().toString()));
        }

        return Map.of(
                "threshold", threshold,
                "generatedAt", LocalDateTime.now().toString(),
                "alerts", alerts);
    }

    /** Rebuilds the snapshot table from the last {@link #WINDOW_SIZE} scan events. */
    private void recompute(long currentSeq) {
        List<Object[]> topSkus = scanEventRepository.findTopScannedSkus(WINDOW_SIZE, DEFAULT_LIMIT);
        long windowStart = Math.max(1, currentSeq - WINDOW_SIZE + 1);
        LocalDateTime now = LocalDateTime.now();

        snapshotRepository.deleteAllInBatch();
        int rank = 1;
        for (Object[] row : topSkus) {
            String sku = (String) row[0];
            int count = ((Number) row[1]).intValue();
            CatalogItem catalogItem = catalogItemRepository.findById(sku).orElseThrow();
            snapshotRepository.save(new PopularItemSnapshot(
                    windowStart, currentSeq, now, sku, catalogItem.getName(), count, rank++));
        }
    }
}
