package com.selfcheckout.analytics.pipeline;

import java.time.Instant;
import java.util.List;

/** A complete, immutable ranking. The HTTP facade applies the requested limit. */
public record AnalyticsSnapshot(long windowStart, long windowEnd, Instant computedAt,
                                List<RankedItem> items) {
    public AnalyticsSnapshot {
        items = List.copyOf(items);
    }

    public record RankedItem(String sku, String name, int scanCount, int rank) {}
}
