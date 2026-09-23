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

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Catalog reads and the basket lifecycle: start, scan, inspect, complete.
 */
@Service
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final TransactionLineItemRepository lineItemRepository;
    private final CatalogItemRepository catalogItemRepository;
    private final AnalyticsService analyticsService;

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

        transaction.setStatus(Transaction.TransactionStatus.COMPLETED);
        transaction.setCompletedAt(LocalDateTime.now());
        transactionRepository.save(transaction);

        List<Map<String, Object>> lines = buildReceiptLines(lineItems);
        releaseStock(lineItems);

        return Map.of(
                "transactionId", transaction.getTransactionId(),
                "stationId", transaction.getStationId(),
                "itemCount", transaction.getItemCount(),
                "totalAmount", transaction.getRunningTotal(),
                "startedAt", transaction.getStartedAt().toString(),
                "completedAt", transaction.getCompletedAt().toString(),
                "lines", lines);
    }

    /**
     * Decrements stock by one per scanned unit, in SKU order so that concurrent
     * transactions always take stock locks in the same sequence.
     */
    private void releaseStock(List<TransactionLineItem> lineItems) {
        List<TransactionLineItem> ordered = new ArrayList<>(lineItems);
        ordered.sort(Comparator.comparing(TransactionLineItem::getSku));
        for (TransactionLineItem lineItem : ordered) {
            catalogItemRepository.decrementStock(lineItem.getSku());
        }
    }

    /** Groups scanned units by SKU into receipt lines with aggregated quantities. */
    private List<Map<String, Object>> buildReceiptLines(List<TransactionLineItem> lineItems) {
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
        return new ArrayList<>(grouped.values());
    }
}
