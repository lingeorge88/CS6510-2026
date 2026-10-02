package com.selfcheckout.analytics.pipeline;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicReference;

/** Commits complete append-only windows, then exposes them to ordered query barriers. */
final class PublishFilter {
    private final BlockingQueue<PipelineMessage> input;
    private final SnapshotWriter writer;
    private final AtomicReference<AnalyticsSnapshot> latest;

    PublishFilter(BlockingQueue<PipelineMessage> input, SnapshotWriter writer,
                  AtomicReference<AnalyticsSnapshot> latest) {
        this.input = input;
        this.writer = writer;
        this.latest = latest;
    }

    void run() throws InterruptedException {
        while (true) {
            PipelineMessage message = input.take();
            if (message instanceof PipelineMessage.Frame frame) {
                // The separately proxied writer commits before returning to this worker.
                writer.append(frame.snapshot());
                latest.set(frame.snapshot());
            } else if (message instanceof PipelineMessage.Barrier barrier) {
                barrier.result().complete(latest.get());
            } else if (message == PipelineMessage.End.INSTANCE) {
                return;
            } else {
                throw new IllegalStateException("Unexpected publication message: " + message);
            }
        }
    }
}
