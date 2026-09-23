package com.selfcheckout.transactions;

import com.selfcheckout.analytics.AnalyticsService;
import com.selfcheckout.data.CatalogItem;
import com.selfcheckout.data.CatalogItemRepository;
import com.selfcheckout.data.Transaction;
import com.selfcheckout.data.TransactionLineItem;
import com.selfcheckout.data.TransactionLineItemRepository;
import com.selfcheckout.data.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * Catalog reads and the basket lifecycle: start, scan, inspect, complete.
 */
@Service
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final TransactionLineItemRepository lineItemRepository;
    private final CatalogItemRepository catalogItemRepository;
    private final AnalyticsService analyticsService;

    /**
     * Units that could not be decremented because stock had reached zero. Written on
     * every occurrence and read rarely, so a {@link LongAdder} rather than an
     * {@code AtomicLong}. Must be zero for any run whose numbers are reported.
     */
    private final LongAdder unfulfilledUnits = new LongAdder();

    public TransactionService(TransactionRepository transactionRepository,
                              TransactionLineItemRepository lineItemRepository,
                              CatalogItemRepository catalogItemRepository,
                              AnalyticsService analyticsService) {
        this.transactionRepository = transactionRepository;
        this.lineItemRepository = lineItemRepository;
        this.catalogItemRepository = catalogItemRepository;
        this.analyticsService = analyticsService;
    }

    /** @return every item in the catalog */
    public List<CatalogItem> listCatalog() {
        return catalogItemRepository.findAll();
    }

    /** Opens a new transaction at a station. */
    public Transaction start(String stationId) {
        Transaction transaction = new Transaction();
        transaction.setStationId(stationId);
        transactionRepository.save(transaction);
        return transaction;
    }

    /**
     * @throws CheckoutException if no such transaction exists
     */
    public Transaction find(String transactionId) {
        return transactionRepository.findById(transactionId)
                .orElseThrow(() -> CheckoutException.transactionNotFound(transactionId));
    }

    /**
     * Scans one physical unit into the basket.
     *
     * @return the scanned item joined with the basket's updated totals
     * @throws CheckoutException if the transaction is unknown or not open, or the SKU is unknown
     */
    public Map<String, Object> scan(String transactionId, String sku) {
        Transaction transaction = find(transactionId);

        if (transaction.getStatus() != Transaction.TransactionStatus.OPEN) {
            throw CheckoutException.transactionNotOpen(transactionId, transaction.getStatus());
        }

        CatalogItem item = catalogItemRepository.findById(sku)
                .orElseThrow(() -> CheckoutException.itemNotFound(sku));

        lineItemRepository.save(
                new TransactionLineItem(transactionId, item.getSku(), item.getPrice()));

        transaction.setItemCount(transaction.getItemCount() + 1);
        transaction.setRunningTotal(transaction.getRunningTotal().add(item.getPrice()));
        transactionRepository.save(transaction);

        analyticsService.recordScan(item.getSku());

        return Map.of(
                "transactionId", transaction.getTransactionId(),
                "sku", item.getSku(),
                "name", item.getName(),
                "unitPrice", item.getPrice(),
                "itemCount", transaction.getItemCount(),
                "runningTotal", transaction.getRunningTotal());
    }

    /**
     * Completes the transaction, decrements stock, and returns a receipt.
     *
     * <p>Runs as one transaction. The receipt is built before any stock row is locked, and
     * decrements are issued last, to keep stock locks held for as short a time as possible.
     *
     * @throws CheckoutException if the transaction is unknown, not open, or has an empty basket
     */
    @Transactional
    public Map<String, Object> complete(String transactionId) {
        Transaction transaction = find(transactionId);

        if (transaction.getStatus() != Transaction.TransactionStatus.OPEN) {
            throw CheckoutException.transactionNotOpen(transactionId, transaction.getStatus());
        }

        List<TransactionLineItem> lineItems = lineItemRepository.findByTransactionId(transactionId);
        if (lineItems.isEmpty()) {
            throw CheckoutException.basketEmpty(transactionId);
        }

        Map<String, List<TransactionLineItem>> bySku = new TreeMap<>();
        for (TransactionLineItem lineItem : lineItems) {
            bySku.computeIfAbsent(lineItem.getSku(), sku -> new ArrayList<>()).add(lineItem);
        }

        // Catalog reads happen before any stock row is locked.
        Map<String, CatalogItem> catalog = new LinkedHashMap<>();
        for (CatalogItem item : catalogItemRepository.findAllById(bySku.keySet())) {
            catalog.put(item.getSku(), item);
        }

        List<TransactionLineItem> fulfilled = new ArrayList<>(lineItems.size());
        List<TransactionLineItem> unfulfilled = new ArrayList<>();
        releaseStock(bySku, fulfilled, unfulfilled);

        // A unit that could not be decremented was never sold, so it must not remain a
        // line item on a completed transaction — that is what keeps
        // initial_stock - final_stock equal to the completed line-item count.
        if (!unfulfilled.isEmpty()) {
            lineItemRepository.deleteAllInBatch(unfulfilled);
            unfulfilledUnits.add(unfulfilled.size());
        }

        BigDecimal total = BigDecimal.ZERO;
        for (TransactionLineItem lineItem : fulfilled) {
            total = total.add(lineItem.getUnitPrice());
        }

        transaction.setItemCount(fulfilled.size());
        transaction.setRunningTotal(total);
        transaction.setStatus(Transaction.TransactionStatus.COMPLETED);
        transaction.setCompletedAt(LocalDateTime.now());
        transactionRepository.save(transaction);

        return Map.of(
                "transactionId", transaction.getTransactionId(),
                "stationId", transaction.getStationId(),
                "itemCount", transaction.getItemCount(),
                "totalAmount", transaction.getRunningTotal(),
                "startedAt", transaction.getStartedAt().toString(),
                "completedAt", transaction.getCompletedAt().toString(),
                "lines", buildReceiptLines(fulfilled, catalog));
    }

    /** @return units that could not be decremented because stock had run out */
    public long unfulfilledUnits() {
        return unfulfilledUnits.sum();
    }

    /**
     * Decrements stock by one per scanned unit, partitioning the units into those that
     * succeeded and those that found the SKU already at zero.
     *
     * <p>{@code bySku} is sorted, so concurrent transactions always take stock locks in
     * the same order and cannot deadlock each other.
     */
    private void releaseStock(Map<String, List<TransactionLineItem>> bySku,
                              List<TransactionLineItem> fulfilled,
                              List<TransactionLineItem> unfulfilled) {
        for (Map.Entry<String, List<TransactionLineItem>> entry : bySku.entrySet()) {
            for (TransactionLineItem unit : entry.getValue()) {
                if (catalogItemRepository.decrementStock(entry.getKey()) == 1) {
                    fulfilled.add(unit);
                } else {
                    unfulfilled.add(unit);
                }
            }
        }
    }

    /** Groups sold units by SKU into receipt lines with aggregated quantities. */
    private List<Map<String, Object>> buildReceiptLines(List<TransactionLineItem> soldUnits,
                                                        Map<String, CatalogItem> catalog) {
        Map<String, Map<String, Object>> grouped = new LinkedHashMap<>();
        for (TransactionLineItem lineItem : soldUnits) {
            Map<String, Object> entry = grouped.computeIfAbsent(lineItem.getSku(), sku -> {
                Map<String, Object> newEntry = new LinkedHashMap<>();
                newEntry.put("sku", sku);
                newEntry.put("name", catalog.get(sku).getName());
                newEntry.put("unitPrice", lineItem.getUnitPrice());
                newEntry.put("quantity", 0);
                return newEntry;
            });
            entry.put("quantity", (int) entry.get("quantity") + 1);
        }
        return new ArrayList<>(grouped.values());
    }
}
