package com.selfcheckout.analytics.pipeline;

import com.selfcheckout.data.PopularItemSnapshot;
import com.selfcheckout.data.PopularItemSnapshotRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/** Appends every ranked row in one transaction. Older windows are retained. */
@Service
public class SnapshotWriter {
    private final PopularItemSnapshotRepository snapshots;

    public SnapshotWriter(PopularItemSnapshotRepository snapshots) {
        this.snapshots = snapshots;
    }

    @Transactional
    public void append(AnalyticsSnapshot snapshot) {
        LocalDateTime computedAt = LocalDateTime.ofInstant(snapshot.computedAt(), ZoneOffset.UTC);
        List<PopularItemSnapshot> rows = new ArrayList<>(snapshot.items().size());
        for (AnalyticsSnapshot.RankedItem item : snapshot.items()) {
            rows.add(new PopularItemSnapshot(snapshot.windowStart(), snapshot.windowEnd(), computedAt,
                    item.sku(), item.name(), item.scanCount(), item.rank()));
        }
        snapshots.saveAll(rows);
    }
}
