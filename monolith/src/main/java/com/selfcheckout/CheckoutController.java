package com.selfcheckout;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicLong;

@RestController
public class CheckoutController {

    @Autowired
    private CatalogItemRepository catalogItemRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private TransactionLineItemRepository lineItemRepository;

    @Autowired
    private ScanEventRepository scanEventRepository;

    @Autowired
    private PopularItemSnapshotRepository popularItemSnapshotRepository;

    /** Thread-safe counter for assigning each scan event a unique, monotonic sequence number. */
    private final AtomicLong globalScanSeq = new AtomicLong(0);
    /**
     * GET /items — Returns the full product catalog.
     * The load-testing client fetches this once at startup to know which SKUs exist.
     * Spring serializes the returned Map to JSON: {@code { "items": [ ... ] }}
     *
     * @return map containing all catalog items under the "items" key
     */
    @GetMapping("/items")
    public Map<String, Object> getAllItems() {
        List<CatalogItem> catalogItems = catalogItemRepository.findAll();
        return Map.of("items", catalogItems);
    }
    /**
     * POST /transactions — Starts a new checkout transaction at a station.
     * Validates that {@code stationId} is present in the request body (400 if missing).
     * The Transaction constructor generates the ID ({@code "tx-" + UUID}), sets status
     * to OPEN, and initializes itemCount/runningTotal to zero.
     *
     * @param body JSON body containing {@code stationId}
     * @return the newly created transaction, HTTP 201
     */
    @PostMapping("/transactions")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> startTransaction(@RequestBody Map<String, String> body) {
        String stationId = body.get("stationId");
        if (stationId == null || stationId.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "stationId is required");
        }

        Transaction transaction = new Transaction();
        transaction.setStationId(stationId);
        transactionRepository.save(transaction);

        return Map.of(
                "transactionId", transaction.getTransactionId(),
                "stationId", transaction.getStationId(),
                "status", transaction.getStatus(),
                "itemCount", transaction.getItemCount(),
                "runningTotal", transaction.getRunningTotal(),
                "startedAt", transaction.getStartedAt().toString()
                );

    }

    /**
     * POST /transactions/{transactionId}/items — Scans one unit of an item into the basket.
     * Each call represents one physical scan. Validates the transaction is OPEN (409 if not)
     * and the SKU exists (404 if not). Saves a line item, updates the transaction totals,
     * and records a {@link ScanEvent} for the popular-items analytics window.
     *
     * @param transactionId the transaction to scan into
     * @param body JSON body containing {@code sku}
     * @return scan result with updated item count and running total
     */
    @PostMapping("/transactions/{transactionId}/items")
    public Map<String, Object> addItem(@PathVariable String transactionId, @RequestBody Map<String, String> body) {

        Transaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Transaction not found"));

        if (transaction.getStatus() != Transaction.TransactionStatus.OPEN) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Transaction is not open");
        }

        String sku = body.get("sku");
        CatalogItem item = catalogItemRepository.findById(sku)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Item not found"));

        TransactionLineItem lineItem = new TransactionLineItem(transactionId, item.getSku(), item.getPrice());
        lineItemRepository.save(lineItem);

        transaction.setItemCount(transaction.getItemCount() + 1);
        transaction.setRunningTotal(transaction.getRunningTotal().add(item.getPrice()));
        transactionRepository.save(transaction);

        long seq = globalScanSeq.incrementAndGet();
        ScanEvent scanEvent = new ScanEvent(seq, item.getSku(), LocalDateTime.now());
        scanEventRepository.save(scanEvent);

        if (seq % 500 == 0 && seq >= 1000) {
            recomputePopularItems(seq);
        }

        return Map.of(
                "transactionId", transaction.getTransactionId(),
                "sku", item.getSku(),
                "name", item.getName(),
                "unitPrice", item.getPrice(),
                "itemCount", transaction.getItemCount(),
                "runningTotal", transaction.getRunningTotal()
        );

    }


    /**
     * POST /transactions/{transactionId}/complete — Finalizes the transaction.
     * Validates the transaction is OPEN (409 if not) and the basket is non-empty (409 if empty).
     * Decrements stock atomically for each scanned unit via {@link CatalogItemRepository#decrementStock},
     * then groups line items by SKU into receipt lines with aggregated quantities.
     *
     * @param transactionId the transaction to complete
     * @return receipt with transaction details and grouped line items
     */
    @PostMapping("/transactions/{transactionId}/complete")
    public Map<String, Object> completeTransaction(@PathVariable String transactionId) {
        Transaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Transaction not found"));

        if (transaction.getStatus() != Transaction.TransactionStatus.OPEN) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Transaction is not open");
        }

        List<TransactionLineItem> lineItems = lineItemRepository.findByTransactionId(transactionId);
        if (lineItems.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Basket is empty");
        }

        transaction.setStatus(Transaction.TransactionStatus.COMPLETED);
        transaction.setCompletedAt(LocalDateTime.now());
        transactionRepository.save(transaction);

        for (TransactionLineItem lineItem : lineItems) {
            catalogItemRepository.decrementStock(lineItem.getSku());
        }

        // Group line items by SKU into receipt lines with quantity.
        // computeIfAbsent: first time seeing a SKU → create the entry; every time → bump quantity.
        Map<String, Map<String, Object>> grouped = new LinkedHashMap<>();
        for (TransactionLineItem lineItem : lineItems) {
            Map<String, Object> entry = grouped.computeIfAbsent(lineItem.getSku(), sku -> {
                CatalogItem catalogItem = catalogItemRepository.findById(sku).orElseThrow();
                Map<String, Object> newEntry = new LinkedHashMap<>();
                newEntry.put("sku", sku);
                newEntry.put("name", catalogItem.getName());
                newEntry.put("unitPrice", lineItem.getUnitPrice());
                newEntry.put("quantity", 0);
                return newEntry;
            });
            entry.put("quantity", (int) entry.get("quantity") + 1);
        }

        return Map.of(
                "transactionId", transaction.getTransactionId(),
                "stationId", transaction.getStationId(),
                "itemCount", transaction.getItemCount(),
                "totalAmount", transaction.getRunningTotal(),
                "startedAt", transaction.getStartedAt().toString(),
                "completedAt", transaction.getCompletedAt().toString(),
                "lines", new ArrayList<>(grouped.values())
        );
    }


    /**
     * GET /transactions/{transactionId} — Returns the current state of a transaction.
     * Looks up by ID; throws 404 if not found. Used for debugging and instructor review.
     *
     * @param transactionId the transaction ID from the URL path
     * @return the transaction's current state
     */
    @GetMapping("/transactions/{transactionId}")
    public Map<String, Object> getTransaction(@PathVariable("transactionId") String transactionId) {
        Transaction transaction = transactionRepository.findById(transactionId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "transactionId not found"));

        return Map.of(
                "transactionId", transaction.getTransactionId(),
                "stationId", transaction.getStationId(),
                "status", transaction.getStatus(),
                "itemCount", transaction.getItemCount(),
                "runningTotal", transaction.getRunningTotal(),
                "startedAt", transaction.getStartedAt().toString()
        );
    }

    /**
     * GET /inventory/low-stock — Returns all catalog items with stock below a threshold.
     * The threshold defaults to 50 (matching the mock server) and can be overridden
     * via the {@code ?threshold=} query parameter.
     *
     * @param threshold stock level below which items are considered low-stock (default 50)
     * @return low-stock response with threshold, timestamp, and list of alerts
     */
    @GetMapping("/inventory/low-stock")
    public Map<String, Object> getLowStock(@RequestParam(defaultValue = "50") int threshold) {
        List<CatalogItem> lowStockItems = catalogItemRepository.findByStockLessThan(threshold);

        List<Map<String, Object>> alerts = new ArrayList<>();
        for (CatalogItem item : lowStockItems) {
            alerts.add(Map.of(
                    "sku", item.getSku(),
                    "name", item.getName(),
                    "currentStock", item.getStock(),
                    "threshold", threshold,
                    "triggeredAt", LocalDateTime.now().toString()
            ));
        }

        return Map.of(
                "threshold", threshold,
                "generatedAt", LocalDateTime.now().toString(),
                "alerts", alerts
        );
    }


    /**
     * GET /analytics/popular-items — Returns the most popular items in the current sliding window.
     * Reads the latest {@link PopularItemSnapshot} rows from the DB (written by
     * {@link #recomputePopularItems}). If no snapshots exist yet (fewer than 1000 scans),
     * returns an empty items list with zeroed window bounds.
     *
     * @param limit max number of ranked items to return (default 10)
     * @return popular items response matching the OpenAPI PopularItemsResponse schema
     */
    @GetMapping("/analytics/popular-items")
    public Map<String, Object> getPopularItems(@RequestParam(defaultValue = "10") int limit) {
        List<PopularItemSnapshot> snapshots = popularItemSnapshotRepository.findLatestSnapshot();

        List<Map<String, Object>> items = new ArrayList<>();
        for (PopularItemSnapshot snapshot : snapshots) {
            if (snapshot.getRank() <= limit) {
                items.add(Map.of(
                        "sku", snapshot.getSku(),
                        "name", snapshot.getName(),
                        "scanCount", snapshot.getScanCount(),
                        "rank", snapshot.getRank()
                ));
            }
        }
        return Map.of(
                "windowSize", 1000,
                "slideInterval", 500,
                "windowStart", snapshots.isEmpty() ? 0L : snapshots.get(0).getWindowStart(),
                "windowEnd", snapshots.isEmpty() ? 0L : snapshots.get(0).getWindowEnd(),
                "computedAt", snapshots.isEmpty() ? "" : snapshots.get(0).getComputedAt().toString(),
                "items", items
        );
    }

    /**
     * Recomputes the popular-items hopping window snapshot.
     * Called from the scan endpoint every 500 scans (once at least 1000 scans exist).
     * Queries the top 10 most-scanned SKUs in the last 1000 scan events via
     * {@link ScanEventRepository#findTopScannedSkus}, clears the old snapshot, and writes
     * fresh {@link PopularItemSnapshot} rows so {@link #getPopularItems} can serve them.
     *
     * @param currentSeq the global scan sequence number that triggered the recomputation
     */
    private void recomputePopularItems(long currentSeq) {
        List<Object[]> topSkus = scanEventRepository.findTopScannedSkus(1000, 10);
        long windowStart = Math.max(1, currentSeq - 1000 + 1);
        long windowEnd = currentSeq;
        LocalDateTime now = LocalDateTime.now();


        popularItemSnapshotRepository.deleteAll();
        int rank = 1;
        for (Object[] sku : topSkus) {
            String skuId = (String) sku[0];
            int count = ((Number) sku[1]).intValue();
            CatalogItem catalogItem = catalogItemRepository.findById(skuId).orElseThrow();
            popularItemSnapshotRepository.save(new PopularItemSnapshot(windowStart, windowEnd, now, skuId, catalogItem.getName(), count, rank++)
            );
        }
    }
}
