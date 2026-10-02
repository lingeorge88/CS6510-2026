package com.selfcheckout.analytics.pipeline;

import com.selfcheckout.analytics.AnalyticsService;
import com.selfcheckout.data.CatalogItem;
import com.selfcheckout.data.CatalogItemRepository;
import com.selfcheckout.data.PopularItemSnapshotRepository;
import com.selfcheckout.data.ScanEventRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {
        "selfcheckout.analytics.window-size=12",
        "selfcheckout.analytics.slide-interval=6"
})
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class AnalyticsPersistenceIntegrationTest {
    @Autowired private AnalyticsPipeline pipeline;
    @Autowired private AnalyticsService analytics;
    @Autowired private SnapshotWriter writer;
    @Autowired private ScanEventRepository scans;
    @Autowired private PopularItemSnapshotRepository snapshots;
    @Autowired private CatalogItemRepository catalog;

    @Test
    void logsAndFullSnapshotsAreAppendedAndLatestWindowIsRankOrdered() {
        for (int i = 0; i < 18; i++) {
            String sku = sku(i);
            catalog.save(new CatalogItem(sku, "Item " + i, BigDecimal.ONE, 100000));
        }
        assertEquals(0, pipeline.snapshot().windowEnd());
        for (int i = 0; i < 11; i++) {
            submit(sku(i));
        }
        assertEquals(0, pipeline.snapshot().windowEnd());
        assertEquals(0, snapshots.count());
        submit(sku(11));
        AnalyticsSnapshot first = pipeline.snapshot();
        assertEquals(1, first.windowStart());
        assertEquals(12, first.windowEnd());
        assertEquals(12, first.items().size());
        assertEquals(sku(0), first.items().get(0).sku());
        assertEquals(sku(11), first.items().get(11).sku());
        assertEquals(12, snapshots.count());

        for (int i = 12; i < 18; i++) {
            submit(sku(i));
        }
        AnalyticsSnapshot second = pipeline.snapshot();
        assertEquals(7, second.windowStart());
        assertEquals(18, second.windowEnd());
        assertEquals(24, snapshots.count(), "publishing must preserve the preceding window");
        assertEquals(18, scans.count(), "publishing must preserve every raw scan");
        var latest = snapshots.findLatestSnapshot();
        assertEquals(12, latest.size());
        for (int i = 0; i < latest.size(); i++) {
            assertEquals(18, latest.get(i).getWindowEnd());
            assertEquals(i + 1, latest.get(i).getRank());
            assertEquals(sku(i + 6), latest.get(i).getSku());
            assertEquals(1, latest.get(i).getScanCount());
        }
        var sequence = scans.findAll().stream().map(scan -> scan.getGlobalSeq()).sorted().toList();
        for (int i = 0; i < sequence.size(); i++) {
            assertEquals(i + 1L, sequence.get(i));
        }

        Map<String, Object> response = analytics.popularItems(20);
        assertEquals(12, ((List<?>) response.get("items")).size());
        assertEquals(10, ((List<?>) analytics.popularItems(null).get("items")).size());
        assertEquals(5, ((List<?>) analytics.popularItems(5).get("items")).size());
        assertEquals(18L, response.get("windowEnd"));
        assertNotNull(Instant.parse(response.get("computedAt").toString()));
        assertEquals(12, first.items().size(), "a previously returned snapshot stays unchanged");
        assertEquals(sku(0), first.items().get(0).sku());
    }

    @Test
    void databaseAggregationRanksCountsAndTiesWithinTheExactWindow() {
        for (String sku : List.of("A", "B", "C", "Z")) {
            catalog.save(new CatalogItem(sku, "Item " + sku, BigDecimal.ONE, 100000));
        }
        for (int i = 0; i < 6; i++) submit("Z");
        for (int i = 0; i < 6; i++) submit("A");
        for (int i = 0; i < 3; i++) submit("C");
        for (int i = 0; i < 3; i++) submit("B");
        AnalyticsSnapshot latest = pipeline.snapshot();
        assertEquals(7, latest.windowStart());
        assertEquals(18, latest.windowEnd());
        assertEquals(List.of("A", "B", "C"),
                latest.items().stream().map(AnalyticsSnapshot.RankedItem::sku).toList());
        assertEquals(List.of(6, 3, 3),
                latest.items().stream().map(AnalyticsSnapshot.RankedItem::scanCount).toList());
        assertEquals(18, scans.count());
        assertEquals(5, snapshots.count(), "both the two-SKU and three-SKU windows are retained");
    }

    @Test
    void failedSnapshotAppendRollsBackAllNewRowsAndRetainsPreviousWindow() {
        writer.append(new AnalyticsSnapshot(1, 12, Instant.now(), List.of(
                new AnalyticsSnapshot.RankedItem("A", "Item A", 12, 1))));
        assertEquals(1, snapshots.count());
        AnalyticsSnapshot invalid = new AnalyticsSnapshot(7, 18, Instant.now(), List.of(
                new AnalyticsSnapshot.RankedItem("A", "Item A", 6, 1),
                new AnalyticsSnapshot.RankedItem("SKU_TOO_LONG_FOR_COLUMN_20", "Invalid item", 6, 2)));
        assertThrows(RuntimeException.class, () -> writer.append(invalid));
        assertEquals(1, snapshots.count(), "the valid first insert must roll back with the second");
        var latest = snapshots.findLatestSnapshot();
        assertEquals(1, latest.size());
        assertEquals(12, latest.get(0).getWindowEnd());
        assertEquals("A", latest.get(0).getSku());
    }

    private void submit(String sku) {
        try (var reservation = pipeline.reserveScan()) {
            reservation.submit(sku, LocalDateTime.now());
        }
    }

    private String sku(int number) {
        return "SKU-%02d".formatted(number);
    }
}
