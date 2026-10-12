package com.selfcheckout.checkout.api;

import com.selfcheckout.checkout.data.Transaction;
import com.selfcheckout.checkout.transactions.TransactionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * The transaction endpoints of the API contract. {@code GET /items} and
 * {@code GET /inventory/low-stock} are served by the Catalog Service; this
 * service owns the checkout lifecycle.
 */
@RestController
public class CheckoutController {

    private final TransactionService transactionService;

    public CheckoutController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    /** Request body of {@code POST /transactions}. */
    public record StartTransactionRequest(@NotBlank String stationId) {
    }

    /** Request body of {@code POST /transactions/{id}/items}. */
    public record ScanItemRequest(@NotBlank String sku) {
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
}
