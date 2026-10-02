package com.selfcheckout.analytics.pipeline;

import com.selfcheckout.data.ScanEvent;
import com.selfcheckout.data.ScanEventRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AnalyticsPipelineTest {
    private final ScanEventRepository scans = mock(ScanEventRepository.class);
    private final SnapshotWriter writer = mock(SnapshotWriter.class);
    private final List<ScanEvent> persisted = new CopyOnWriteArrayList<>();
    private AnalyticsPipeline pipeline;

    @AfterEach
    void stopWorkers() {
        if (pipeline != null) {
            pipeline.stop();
        }
    }

    @Test
    void warmupAndHopsUseExactRangesAndEvictOldScans() {
        start(1000, 500);
        submit(500, "A");
        submit(499, "B");
        assertEquals(0, pipeline.snapshot().windowEnd());
        submit(1, "B");
        AnalyticsSnapshot first = pipeline.snapshot();
        assertEquals(1, first.windowStart());
        assertEquals(1000, first.windowEnd());
        assertEquals(1000, first.items().stream().mapToInt(AnalyticsSnapshot.RankedItem::scanCount).sum());
        submit(499, "B");
        assertEquals(1000, pipeline.snapshot().windowEnd());
        submit(1, "B");
        AnalyticsSnapshot second = pipeline.snapshot();
        assertEquals(501, second.windowStart());
        assertEquals(1500, second.windowEnd());
        assertEquals(List.of("B"), second.items().stream().map(AnalyticsSnapshot.RankedItem::sku).toList());
        assertEquals(1000, second.items().get(0).scanCount());
        // Captured snapshots remain stable after later scans and publication.
        assertEquals(2, first.items().size());
        assertEquals(500, first.items().get(0).scanCount());
        assertThrows(UnsupportedOperationException.class, () -> first.items().clear());
        verify(writer, times(2)).append(any(AnalyticsSnapshot.class));
        assertEquals(1500, persisted.size());
    }

    @Test
    void concurrentProducersPersistOneContiguousSequence() throws Exception {
        start(20, 10);
        try (var producers = Executors.newFixedThreadPool(4)) {
            List<java.util.concurrent.Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                String sku = "SKU-" + i;
                futures.add(producers.submit(() -> submit(50, sku)));
            }
            for (var future : futures) {
                future.get(5, TimeUnit.SECONDS);
            }
        }
        assertEquals(200, pipeline.snapshot().windowEnd());
        assertEquals(200, persisted.size());
        for (int i = 0; i < persisted.size(); i++) {
            assertEquals(i + 1L, persisted.get(i).getGlobalSeq());
        }
    }

    @Test
    void canceledReservationsRestoreAdmissionWithoutCreatingScans() {
        AnalyticsProperties properties = properties(4, 2);
        properties.setInputCapacity(1);
        properties.setAdmissionTimeoutMs(50);
        start(properties);
        var first = pipeline.reserveScan();
        assertThrows(RuntimeException.class, pipeline::reserveScan);
        first.close();
        first.close();
        try (var replacement = pipeline.reserveScan()) {
            replacement.submit("A", LocalDateTime.now());
        }
        pipeline.snapshot();
        assertEquals(1, persisted.size());
        assertEquals(1L, persisted.get(0).getGlobalSeq());
    }

    @Test
    void barrierWaitsForDatabasePublicationAndKeepsHopBoundary() throws Exception {
        CountDownLatch appendEntered = new CountDownLatch(1);
        CountDownLatch releaseAppend = new CountDownLatch(1);
        doAnswer(invocation -> {
            appendEntered.countDown();
            assertTrue(releaseAppend.await(3, TimeUnit.SECONDS));
            return null;
        }).when(writer).append(any());
        start(4, 2);
        submit(4, "A");
        assertTrue(appendEntered.await(3, TimeUnit.SECONDS));
        try (var reader = Executors.newSingleThreadExecutor()) {
            var result = reader.submit(pipeline::snapshot);
            try {
                assertThrows(java.util.concurrent.TimeoutException.class,
                        () -> result.get(100, TimeUnit.MILLISECONDS));
            } finally {
                releaseAppend.countDown();
            }
            assertEquals(4, result.get(3, TimeUnit.SECONDS).windowEnd());
        }
        submit(1, "B");
        assertEquals(4, pipeline.snapshot().windowEnd());
        verify(writer, times(1)).append(any());
    }

    @Test
    void blockedPublicationRespectsQueryDeadlineWithoutDisablingHealthyWorkers() throws Exception {
        CountDownLatch appendEntered = new CountDownLatch(1);
        CountDownLatch releaseAppend = new CountDownLatch(1);
        doAnswer(invocation -> {
            appendEntered.countDown();
            assertTrue(releaseAppend.await(3, TimeUnit.SECONDS));
            return null;
        }).when(writer).append(any());
        AnalyticsProperties properties = properties(4, 2);
        properties.setQueryTimeoutMs(100);
        start(properties);
        submit(4, "A");
        assertTrue(appendEntered.await(3, TimeUnit.SECONDS));
        try {
            assertTimeoutPreemptively(java.time.Duration.ofSeconds(1),
                    () -> assertThrows(RuntimeException.class, pipeline::snapshot));
        } finally {
            releaseAppend.countDown();
        }
        assertEquals(4, pipeline.snapshot().windowEnd());
        assertTrue(pipeline.isRunning());
    }

    @Test
    void workerFailureFailsReadsAndRejectsNewAdmission() {
        doThrow(new IllegalStateException("injected snapshot failure")).when(writer).append(any());
        start(4, 2);
        submit(4, "A");
        assertTimeoutPreemptively(java.time.Duration.ofSeconds(3),
                () -> assertThrows(RuntimeException.class, pipeline::snapshot));
        assertThrows(RuntimeException.class, pipeline::reserveScan);
    }

    @Test
    void shutdownWaitsForAnOutstandingScanReservationBeforeDraining() throws Exception {
        start(4, 2);
        try (var reserved = pipeline.reserveScan(); var closer = Executors.newSingleThreadExecutor()) {
            var stopped = closer.submit(() -> { pipeline.stop(); });
            assertThrows(java.util.concurrent.TimeoutException.class,
                    () -> stopped.get(100, TimeUnit.MILLISECONDS));
            reserved.submit("A", LocalDateTime.now());
            stopped.get(3, TimeUnit.SECONDS);
        }
        assertEquals(1, persisted.size());
        assertFalse(pipeline.isRunning());
    }

    @Test
    void shutdownDrainsAcceptedScansAndRejectsLaterAdmission() {
        start(4, 2);
        submit(40, "A");
        pipeline.stop();
        assertFalse(pipeline.isRunning());
        assertEquals(40, persisted.size());
        verify(writer, times(19)).append(any());
        assertThrows(RuntimeException.class, pipeline::reserveScan);
    }

    private void start(int windowSize, int slideInterval) {
        start(properties(windowSize, slideInterval));
    }

    private AnalyticsProperties properties(int windowSize, int slideInterval) {
        AnalyticsProperties properties = new AnalyticsProperties();
        properties.setWindowSize(windowSize);
        properties.setSlideInterval(slideInterval);
        properties.setQueryTimeoutMs(2000);
        properties.setShutdownTimeoutMs(3000);
        return properties;
    }

    private void start(AnalyticsProperties properties) {
        when(scans.save(any(ScanEvent.class))).thenAnswer(invocation -> {
            ScanEvent scan = invocation.getArgument(0);
            persisted.add(scan);
            return scan;
        });
        when(scans.findRankedScans(anyLong(), anyLong())).thenAnswer(invocation -> {
            long start = invocation.getArgument(0);
            long end = invocation.getArgument(1);
            Map<String, Long> counts = new TreeMap<>();
            for (ScanEvent scan : persisted) {
                if (scan.getGlobalSeq() >= start && scan.getGlobalSeq() <= end) {
                    counts.merge(scan.getSku(), 1L, Long::sum);
                }
            }
            return counts.entrySet().stream()
                    .sorted(Comparator.<Map.Entry<String, Long>>comparingLong(Map.Entry::getValue)
                            .reversed().thenComparing(Map.Entry::getKey))
                    .map(entry -> new Object[]{entry.getKey(), "Name " + entry.getKey(), entry.getValue()})
                    .toList();
        });
        pipeline = new AnalyticsPipeline(properties, scans, writer);
        pipeline.start();
    }

    private void submit(int count, String sku) {
        for (int i = 0; i < count; i++) {
            try (var reservation = pipeline.reserveScan()) {
                reservation.submit(sku, LocalDateTime.now());
            }
        }
    }
}
