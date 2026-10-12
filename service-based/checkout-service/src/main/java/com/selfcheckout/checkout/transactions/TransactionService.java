package com.selfcheckout.checkout.transactions;

import com.selfcheckout.checkout.data.Transaction;
import com.selfcheckout.checkout.data.TransactionLineItem;
import com.selfcheckout.checkout.data.TransactionLineItemRepository;
import com.selfcheckout.checkout.data.TransactionRepository;
import com.selfcheckout.shared.catalog.CatalogItem;
import com.selfcheckout.shared.catalog.CatalogItemRepository;
import com.selfcheckout.shared.inventory.InventoryStockRepository;
import com.selfcheckout.shared.scan.ScanEvent;
import com.selfcheckout.shared.scan.ScanEventRepository;
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

/** The basket lifecycle: start, scan, inspect, complete. */
@Service
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final TransactionLineItemRepository lineItemRepository;
    private final CatalogItemRepository catalogItemRepository;
    private final InventoryStockRepository inventoryStockRepository;
    private final ScanEventRepository scanEventRepository;

    private final LongAdder unfulfilledUnits = new LongAdder();

    public TransactionService(TransactionRepository transactionRepository,
                              TransactionLineItemRepository lineItemRepository,
                              CatalogItemRepository catalogItemRepository,
                              InventoryStockRepository inventoryStockRepository,
                              ScanEventRepository scanEventRepository) {
        this.transactionRepository = transactionRepository;
        this.lineItemRepository = lineItemRepository;
        this.catalogItemRepository = catalogItemRepository;
        this.inventoryStockRepository = inventoryStockRepository;
        this.scanEventRepository = scanEventRepository;
    }

    /** Opens a new transaction at a station. */
    public Transaction start(String stationId) {
        Transaction transaction = new Transaction();
        transaction.setStationId(stationId);
        transactionRepository.save(transaction);
        return transaction;
    }

    /** @throws CheckoutException if no such transaction exists */
    public Transaction find(String transactionId) {
        return transactionRepository.findById(transactionId)
                .orElseThrow(() -> CheckoutException.transactionNotFound(transactionId));
    }

    /**
     * Scans one physical unit into the basket.
     *
     * @throws CheckoutException if the transaction is unknown or not open, or the SKU is unknown
     */
    @Transactional
    public Map<String, Object> scan(String transactionId, String sku) {
        Transaction transaction = find(transactionId);

        if (transaction.getStatus() != Transaction.TransactionStatus.OPEN) {
            throw CheckoutException.transactionNotOpen(transactionId, transaction.getStatus());
        }

        CatalogItem item = catalogItemRepository.findById(sku)
                .orElseThrow(() -> CheckoutException.itemNotFound(sku));

        lineItemRepository.save(new TransactionLineItem(transactionId, item.getSku(), item.getPrice()));

        transaction.setItemCount(transaction.getItemCount() + 1);
        transaction.setRunningTotal(transaction.getRunningTotal().add(item.getPrice()));
        transactionRepository.save(transaction);

        scanEventRepository.save(new ScanEvent(item.getSku(), LocalDateTime.now()));

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
     * @throws CheckoutException if the transaction is unknown, not open, or empty
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

        Map<String, CatalogItem> catalog = new LinkedHashMap<>();
        for (CatalogItem item : catalogItemRepository.findAllById(bySku.keySet())) {
            catalog.put(item.getSku(), item);
        }

        List<TransactionLineItem> fulfilled = new ArrayList<>(lineItems.size());
        List<TransactionLineItem> unfulfilled = new ArrayList<>();
        releaseStock(bySku, fulfilled, unfulfilled);

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
     * Decrements stock by one per scanned unit, partitioning units into fulfilled
     * and those whose SKU was already at zero.
     */
    private void releaseStock(Map<String, List<TransactionLineItem>> bySku,
                              List<TransactionLineItem> fulfilled,
                              List<TransactionLineItem> unfulfilled) {
        for (Map.Entry<String, List<TransactionLineItem>> entry : bySku.entrySet()) {
            for (TransactionLineItem unit : entry.getValue()) {
                if (inventoryStockRepository.decrementStock(entry.getKey()) == 1) {
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
