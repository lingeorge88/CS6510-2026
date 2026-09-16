package com.selfcheckout;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

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

    // TODO: Add AtomicLong for global scan sequence counter

    // ---------------------------------------------------------------
    // GET /items — Return full catalog
    // ---------------------------------------------------------------

    // TODO: Implement GET /items
    // Return: { "items": [ { "sku": "...", "name": "...", "price": ... }, ... ] }

    // ---------------------------------------------------------------
    // POST /transactions — Start a new transaction
    // ---------------------------------------------------------------

    // TODO: Implement POST /transactions
    // Read stationId from request body (return 400 if missing)
    // Generate ID: "tx-" + UUID.randomUUID()
    // Save Transaction with status=OPEN, itemCount=0, runningTotal=0
    // Return 201 with transaction JSON

    // ---------------------------------------------------------------
    // POST /transactions/{transactionId}/items — Scan one item
    // ---------------------------------------------------------------

    // TODO: Implement POST /transactions/{transactionId}/items
    // Look up transaction (404 if not found)
    // Check status is OPEN (409 if not)
    // Look up catalog item by SKU (404 if unknown)
    // Save TransactionLineItem, update transaction counts
    // Save ScanEvent with incrementing globalSeq
    // If globalSeq % 500 == 0 && globalSeq >= 1000: recompute popular items
    // Return scan result JSON

    // ---------------------------------------------------------------
    // POST /transactions/{transactionId}/complete — Complete transaction
    // ---------------------------------------------------------------

    // TODO: Implement POST /transactions/{transactionId}/complete
    // Look up transaction (404 if not found)
    // Check status OPEN (409 if not)
    // Check basket not empty (409 if empty)
    // Set status=COMPLETED, completedAt=now
    // For each line item: catalogItemRepository.decrementStock(sku)
    // Build receipt lines grouped by SKU with quantity
    // Return receipt JSON

    // ---------------------------------------------------------------
    // GET /transactions/{transactionId} — Get transaction status
    // ---------------------------------------------------------------

    // TODO: Implement GET /transactions/{transactionId}
    // Look up transaction (404 if not found)
    // Return transaction JSON

    // ---------------------------------------------------------------
    // GET /inventory/low-stock — Low stock alerts
    // ---------------------------------------------------------------

    // TODO: Implement GET /inventory/low-stock
    // Read optional ?threshold param (default 50)
    // Query items with stock < threshold
    // Return { "threshold": ..., "generatedAt": ..., "alerts": [...] }

    // ---------------------------------------------------------------
    // GET /analytics/popular-items — Popular items in sliding window
    // ---------------------------------------------------------------

    // TODO: Implement GET /analytics/popular-items
    // Read optional ?limit param (default 10)
    // Query latest PopularItemSnapshot rows
    // Return { "windowSize": 1000, "slideInterval": 500, ... "items": [...] }

    // ---------------------------------------------------------------
    // Helper: Recompute popular items hopping window
    // ---------------------------------------------------------------

    // TODO: Implement recomputePopularItems()
    // Query last 1000 scan events
    // Group by SKU, count occurrences
    // Rank by count descending, take top 10
    // Save as new PopularItemSnapshot rows

}
