package com.selfcheckout.analytics.pipeline;

import com.selfcheckout.data.ScanEventRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;

/** Counts the exact persisted sequence range, even when ingestion has advanced ahead. */
final class AggregateFilter {
    private final BlockingQueue<PipelineMessage> input;
    private final BlockingQueue<PipelineMessage> output;
    private final ScanEventRepository scans;

    AggregateFilter(BlockingQueue<PipelineMessage> input, BlockingQueue<PipelineMessage> output,
                    ScanEventRepository scans) {
        this.input = input;
        this.output = output;
        this.scans = scans;
    }

    void run() throws InterruptedException {
        while (true) {
            PipelineMessage message = input.take();
            if (message instanceof PipelineMessage.Window window) {
                List<Object[]> rows = scans.findRankedScans(window.start(), window.end());
                List<AnalyticsSnapshot.RankedItem> items = new ArrayList<>(rows.size());
                int rank = 1;
                long counted = 0;
                for (Object[] row : rows) {
                    int count = ((Number) row[2]).intValue();
                    counted += count;
                    items.add(new AnalyticsSnapshot.RankedItem(
                            (String) row[0], (String) row[1], count, rank++));
                }
                if (counted != window.end() - window.start() + 1) {
                    throw new IllegalStateException("Persisted analytics window is incomplete: " + window);
                }
                output.put(new PipelineMessage.Frame(new AnalyticsSnapshot(
                        window.start(), window.end(), Instant.now(), items)));
            } else {
                output.put(message);
                if (message == PipelineMessage.End.INSTANCE) return;
            }
        }
    }
}
