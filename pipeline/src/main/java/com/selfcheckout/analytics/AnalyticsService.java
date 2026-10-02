package com.selfcheckout.analytics;

import com.selfcheckout.analytics.pipeline.AnalyticsPipeline;
import com.selfcheckout.analytics.pipeline.AnalyticsProperties;
import com.selfcheckout.analytics.pipeline.AnalyticsSnapshot;
import com.selfcheckout.data.CatalogItem;
import com.selfcheckout.data.CatalogItemRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** HTTP-facing analytics facade; only scan popularity uses the threaded pipeline. */
@Service
public class AnalyticsService {
    private final AnalyticsPipeline pipeline;
    private final CatalogItemRepository catalog;
    private final AnalyticsProperties properties;

    public AnalyticsService(AnalyticsPipeline pipeline, CatalogItemRepository catalog,
                            AnalyticsProperties properties) {
        this.pipeline = pipeline;
        this.catalog = catalog;
        this.properties = properties;
    }

    /**
     * Records a scan into the analytics pipeline. Non-blocking and best-effort: analytics
     * backpressure never blocks or fails the caller's checkout. Call after basket writes.
     */
    public void recordScan(String sku) {
        pipeline.offerScan(sku, LocalDateTime.now());
    }

    public Map<String, Object> popularItems(Integer limit) {
        int effectiveLimit = limit == null ? properties.getPopularItemsLimit() : limit;
        AnalyticsSnapshot snapshot = pipeline.latestSnapshot();
        List<Map<String, Object>> items = new ArrayList<>();
        for (AnalyticsSnapshot.RankedItem item : snapshot.items()) {
            if (item.rank() > effectiveLimit) break;
            items.add(Map.of("sku", item.sku(), "name", item.name(),
                    "scanCount", item.scanCount(), "rank", item.rank()));
        }
        return Map.of("windowSize", properties.getWindowSize(),
                "slideInterval", properties.getSlideInterval(),
                "windowStart", snapshot.windowStart(), "windowEnd", snapshot.windowEnd(),
                "computedAt", snapshot.computedAt().toString(), "items", items);
    }

    /** Low-stock retains its live repository query and per-request threshold override. */
    public Map<String, Object> lowStock(Integer thresholdOverride) {
        int threshold = thresholdOverride == null ? properties.getLowStockThreshold() : thresholdOverride;
        List<CatalogItem> lowStockItems = catalog.findByStockLessThan(threshold);
        List<Map<String, Object>> alerts = new ArrayList<>(lowStockItems.size());
        for (CatalogItem item : lowStockItems) {
            alerts.add(Map.of("sku", item.getSku(), "name", item.getName(),
                    "currentStock", item.getStock(), "threshold", threshold,
                    "triggeredAt", LocalDateTime.now().toString()));
        }
        return Map.of("threshold", threshold, "generatedAt", LocalDateTime.now().toString(), "alerts", alerts);
    }
}
