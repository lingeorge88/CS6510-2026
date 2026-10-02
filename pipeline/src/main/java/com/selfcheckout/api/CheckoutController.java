package com.selfcheckout.api;

import com.selfcheckout.analytics.AnalyticsService;
import com.selfcheckout.data.CatalogItem;
import com.selfcheckout.data.Transaction;
import com.selfcheckout.transactions.TransactionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * All seven endpoints of the API contract. Each method delegates to a service;
 * no business logic and no repository access lives here.
 */
@RestController
public class CheckoutController {

    private final TransactionService transactionService;
    private final AnalyticsService analyticsService;

    public CheckoutController(TransactionService transactionService,
                              AnalyticsService analyticsService) {
        this.transactionService = transactionService;
        this.analyticsService = analyticsService;
    }

    /** Request body of {@code POST /transactions}. */
    public record StartTransactionRequest(@NotBlank String stationId) {}

    /** Request body of {@code POST /transactions/{id}/items}. */
    public record ScanItemRequest(@NotBlank String sku) {}

    @GetMapping("/items")
    public Map<String, List<CatalogItem>> getAllItems() {
        return Map.of("items", transactionService.listCatalog());
    }

    @PostMapping("/transactions")
    @ResponseStatus(HttpStatus.CREATED)
    public Transaction startTransaction(@Valid @RequestBody StartTransactionRequest request) {
        return transactionService.start(request.stationId());
    }

    @PostMapping("/transactions/{transactionId}/items")
    public Map<String, Object> addItem(@PathVariable String transactionId,
                                       @Valid @RequestBody ScanItemRequest request) {
        return transactionService.scan(transactionId, request.sku());
    }

    @PostMapping("/transactions/{transactionId}/complete")
    public Map<String, Object> completeTransaction(@PathVariable String transactionId) {
        return transactionService.complete(transactionId);
    }

    @GetMapping("/transactions/{transactionId}")
    public Transaction getTransaction(@PathVariable String transactionId) {
        return transactionService.find(transactionId);
    }

    /** A null {@code threshold} means "use the server default". */
    @GetMapping("/inventory/low-stock")
    public Map<String, Object> getLowStock(@RequestParam(required = false) Integer threshold) {
        return analyticsService.lowStock(threshold);
    }

    @GetMapping("/analytics/popular-items")
    public Map<String, Object> getPopularItems(@RequestParam(required = false) Integer limit) {
        return analyticsService.popularItems(limit);
    }
}
