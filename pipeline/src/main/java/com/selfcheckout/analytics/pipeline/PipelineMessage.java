package com.selfcheckout.analytics.pipeline;

import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;

/** Scalar payloads and control markers travel through FIFO pipes. */
sealed interface PipelineMessage {
    record Scan(String sku, LocalDateTime scannedAt) implements PipelineMessage {}
    record Window(long start, long end) implements PipelineMessage {}
    record Frame(AnalyticsSnapshot snapshot) implements PipelineMessage {}
    record Barrier(CompletableFuture<AnalyticsSnapshot> result) implements PipelineMessage {}
    enum End implements PipelineMessage { INSTANCE }
}
