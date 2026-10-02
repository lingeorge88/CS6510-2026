package com.selfcheckout.analytics.pipeline;

import com.selfcheckout.data.ScanEvent;
import com.selfcheckout.data.ScanEventRepository;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Semaphore;

/** Orders and persists every scan before announcing eligible full-window boundaries. */
final class IngestFilter {
    private final BlockingQueue<PipelineMessage> input;
    private final BlockingQueue<PipelineMessage> output;
    private final Semaphore capacity;
    private final ScanEventRepository scans;
    private final AnalyticsProperties policy;

    IngestFilter(BlockingQueue<PipelineMessage> input, BlockingQueue<PipelineMessage> output,
                 Semaphore capacity, ScanEventRepository scans, AnalyticsProperties policy) {
        this.input = input;
        this.output = output;
        this.capacity = capacity;
        this.scans = scans;
        this.policy = policy;
    }

    void run() throws InterruptedException {
        // Startup recreates the database. Sequence allocation is owned by this worker.
        long sequence = 0;
        while (true) {
            PipelineMessage message = input.take();
            capacity.release();
            if (message instanceof PipelineMessage.Scan scan) {
                long end = ++sequence;
                // Repository save returns after its transaction has committed.
                scans.save(new ScanEvent(end, scan.sku(), scan.scannedAt()));
                if (end >= policy.getWindowSize() && end % policy.getSlideInterval() == 0) {
                    output.put(new PipelineMessage.Window(end - policy.getWindowSize() + 1, end));
                }
            } else {
                output.put(message);
                if (message == PipelineMessage.End.INSTANCE) return;
            }
        }
    }
}
