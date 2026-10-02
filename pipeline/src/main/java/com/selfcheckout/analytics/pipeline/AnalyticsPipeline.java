package com.selfcheckout.analytics.pipeline;

import com.selfcheckout.data.ScanEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

/** Coordinates three worker filters, bounded admission, FIFO query barriers, and drain. */
@Service
public class AnalyticsPipeline implements SmartLifecycle {
    private static final Logger log = LoggerFactory.getLogger(AnalyticsPipeline.class);
    private final AnalyticsProperties properties;
    private final ScanEventRepository scans;
    private final SnapshotWriter writer;
    private final BlockingQueue<PipelineMessage> inputs;
    private final BlockingQueue<PipelineMessage> windows;
    private final BlockingQueue<PipelineMessage> frames;
    private final Semaphore inputCapacity;
    private final Object admissionLock = new Object();
    private final AtomicReference<Throwable> failure = new AtomicReference<>();
    private final AtomicReference<AnalyticsSnapshot> latest = new AtomicReference<>(
            new AnalyticsSnapshot(0, 0, Instant.now(), List.of()));
    private final Set<CompletableFuture<AnalyticsSnapshot>> pendingQueries = ConcurrentHashMap.newKeySet();
    private volatile List<Thread> workers = List.of();
    private volatile boolean running;
    private boolean accepting;
    private boolean started;
    private int reservations;

    public AnalyticsPipeline(AnalyticsProperties properties, ScanEventRepository scans,
                             SnapshotWriter writer) {
        this.properties = properties;
        this.scans = scans;
        this.writer = writer;
        inputs = new ArrayBlockingQueue<>(properties.getInputCapacity());
        windows = new ArrayBlockingQueue<>(properties.getWindowCapacity());
        frames = new ArrayBlockingQueue<>(properties.getSnapshotCapacity());
        inputCapacity = new Semaphore(properties.getInputCapacity(), true);
    }

    @Override
    public void start() {
        synchronized (admissionLock) {
            if (running) return;
            if (started) throw new IllegalStateException("An analytics pipeline cannot restart in place");
            started = true;
            workers = List.of(
                    worker("analytics-ingest", new IngestFilter(inputs, windows, inputCapacity, scans, properties)::run),
                    worker("analytics-aggregate", new AggregateFilter(windows, frames, scans)::run),
                    worker("analytics-publish", new PublishFilter(frames, writer, latest)::run));
            running = true;
            accepting = true;
            workers.forEach(Thread::start);
        }
    }

    /** Reserve before basket writes. A reservation guarantees physical input-queue space. */
    public Reservation reserveScan() {
        return reserve(deadline(properties.getAdmissionTimeoutMs()));
    }

    /**
     * Best-effort, non-blocking submission. Drops the sample when admission capacity is
     * momentarily unavailable (or the pipeline is not accepting) so analytics backpressure
     * can never block or fail a checkout. Windowed popularity is best-effort; a dropped
     * sample simply never enters the ingest sequence, so persisted windows stay complete.
     */
    public void offerScan(String sku, LocalDateTime scannedAt) {
        Reservation reservation = tryReserveNow();
        if (reservation == null) return; // pipe saturated or not accepting: drop the sample
        try {
            reservation.submit(sku, scannedAt);
        } catch (RuntimeException ex) {
            reservation.close(); // release capacity if submission raced a stop or failure
        }
    }

    private Reservation reserve(long deadline) {
        try {
            while (true) {
                synchronized (admissionLock) { requireAvailable(); }
                long remaining = remaining(deadline);
                if (remaining <= 0) throw unavailable("Timed out waiting for analytics input capacity", null);
                if (!inputCapacity.tryAcquire(Math.min(remaining, TimeUnit.MILLISECONDS.toNanos(100)),
                        TimeUnit.NANOSECONDS)) continue;
                synchronized (admissionLock) {
                    try {
                        requireAvailable();
                        reservations++;
                        return new Reservation();
                    } catch (RuntimeException ex) {
                        inputCapacity.release();
                        throw ex;
                    }
                }
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw unavailable("Interrupted while reserving analytics input capacity", ex);
        }
    }

    /** Non-blocking admission for best-effort callers; returns null when no capacity is free. */
    private Reservation tryReserveNow() {
        synchronized (admissionLock) {
            if (!canAdmit()) return null;
        }
        if (!inputCapacity.tryAcquire()) return null;
        synchronized (admissionLock) {
            if (!canAdmit()) {
                inputCapacity.release();
                return null;
            }
            reservations++;
            return new Reservation();
        }
    }

    private boolean canAdmit() {
        return failure.get() == null && running && accepting;
    }

    /**
     * Returns the most recently published snapshot without blocking. Unlike {@link #snapshot()}
     * this does not enqueue an ordered barrier, so a query can never be blocked or failed by a
     * saturated input queue. It may omit scans still queued ahead of the latest committed window.
     */
    public AnalyticsSnapshot latestSnapshot() {
        Throwable cause = failure.get();
        if (cause != null) throw unavailable("Analytics worker failed", cause);
        return latest.get();
    }

    /** Returns the complete snapshot observed at a barrier behind already admitted scans. */
    public AnalyticsSnapshot snapshot() {
        long deadline = deadline(properties.getQueryTimeoutMs());
        CompletableFuture<AnalyticsSnapshot> result = new CompletableFuture<>();
        pendingQueries.add(result);
        try (Reservation reservation = reserve(deadline)) {
            reservation.enqueue(new PipelineMessage.Barrier(result));
            long remaining = remaining(deadline);
            if (remaining <= 0) throw new TimeoutException("Query deadline expired");
            return result.get(remaining, TimeUnit.NANOSECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw unavailable("Interrupted while waiting for the analytics query barrier", ex);
        } catch (ExecutionException ex) {
            throw unavailable("Analytics worker failed", ex.getCause());
        } catch (TimeoutException ex) {
            throw unavailable("Timed out waiting for the analytics query barrier", ex);
        } finally {
            pendingQueries.remove(result);
        }
    }

    /** A transferred reservation belongs to the queue; close cancels only unused capacity. */
    public final class Reservation implements AutoCloseable {
        private boolean settled;
        private Reservation() {}

        public void submit(String sku, LocalDateTime scannedAt) {
            enqueue(new PipelineMessage.Scan(Objects.requireNonNull(sku), Objects.requireNonNull(scannedAt)));
        }

        private void enqueue(PipelineMessage message) {
            synchronized (admissionLock) {
                if (settled) throw new IllegalStateException("Analytics reservation already settled");
                // Existing reservations may submit during graceful shutdown.
                Throwable cause = failure.get();
                if (cause != null) throw unavailable("Analytics worker failed", cause);
                if (!running) throw unavailable("Analytics pipeline is stopped", null);
                if (!inputs.offer(message)) {
                    throw new IllegalStateException("Reserved analytics queue capacity was unavailable");
                }
                settled = true;
                reservations--;
                admissionLock.notifyAll();
            }
        }

        @Override
        public void close() {
            synchronized (admissionLock) {
                if (!settled) {
                    settled = true;
                    reservations--;
                    inputCapacity.release();
                    admissionLock.notifyAll();
                }
            }
        }
    }

    private Thread worker(String name, InterruptibleTask task) {
        return new Thread(() -> {
            try { task.run(); } catch (Throwable ex) { fail(ex); }
        }, name);
    }

    private void fail(Throwable cause) {
        if (!failure.compareAndSet(null, cause)) return;
        synchronized (admissionLock) {
            accepting = false;
            admissionLock.notifyAll();
        }
        log.error("Analytics pipeline stopped after a worker failure", cause);
        pendingQueries.forEach(query -> query.completeExceptionally(cause));
        workers.stream().filter(thread -> thread != Thread.currentThread()).forEach(Thread::interrupt);
    }

    private void requireAvailable() {
        Throwable cause = failure.get();
        if (cause != null) throw unavailable("Analytics worker failed", cause);
        if (!running || !accepting) throw unavailable("Analytics pipeline is not accepting inputs", null);
    }

    @Override
    public void stop() {
        long deadline = deadline(properties.getShutdownTimeoutMs());
        synchronized (admissionLock) {
            if (!running) return;
            accepting = false;
        }
        try {
            synchronized (admissionLock) {
                while (reservations != 0 && failure.get() == null) {
                    long remaining = remaining(deadline);
                    if (remaining <= 0) throw new TimeoutException("Outstanding analytics reservations did not settle");
                    TimeUnit.NANOSECONDS.timedWait(admissionLock,
                            Math.min(remaining, TimeUnit.MILLISECONDS.toNanos(100)));
                }
            }
            if (failure.get() == null) {
                // All reserved scans are now ahead of End, which drains each FIFO stage.
                long remaining = remaining(deadline);
                if (remaining <= 0 || !inputCapacity.tryAcquire(remaining, TimeUnit.NANOSECONDS)) {
                    throw new TimeoutException("Timed out sending analytics end-of-stream");
                }
                if (!inputs.offer(PipelineMessage.End.INSTANCE)) {
                    inputCapacity.release();
                    throw new IllegalStateException("End-of-stream queue reservation failed");
                }
            }
            for (Thread worker : workers) {
                long remaining = remaining(deadline);
                if (remaining > 0) TimeUnit.NANOSECONDS.timedJoin(worker, remaining);
                if (worker.isAlive()) throw new TimeoutException("Analytics worker did not drain: " + worker.getName());
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            fail(ex);
        } catch (TimeoutException | RuntimeException ex) {
            fail(ex);
        } finally {
            running = false;
            workers.stream().filter(Thread::isAlive).forEach(Thread::interrupt);
        }
    }

    @Override
    public void stop(Runnable callback) {
        try { stop(); } finally { callback.run(); }
    }

    @Override
    public boolean isRunning() { return running; }

    /** Start after persistence initialization; drain before persistence resources close. */
    @Override
    public int getPhase() { return Integer.MAX_VALUE; }

    private static long deadline(long milliseconds) {
        return System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(milliseconds);
    }

    private static long remaining(long deadline) { return deadline - System.nanoTime(); }

    private static IllegalStateException unavailable(String message, Throwable cause) {
        return new IllegalStateException(message, cause);
    }

    @FunctionalInterface
    private interface InterruptibleTask { void run() throws InterruptedException; }
}
