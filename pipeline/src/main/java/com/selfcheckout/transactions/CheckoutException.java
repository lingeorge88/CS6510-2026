package com.selfcheckout.transactions;

/**
 * A business-rule failure, distinguished by {@link Kind}. Carries no HTTP concept;
 * {@code ApiExceptionHandler} maps each kind to a status code.
 */
public class CheckoutException extends RuntimeException {

    public enum Kind {
        TRANSACTION_NOT_FOUND,
        ITEM_NOT_FOUND,
        TRANSACTION_NOT_OPEN,
        BASKET_EMPTY
    }

    private final Kind kind;

    public CheckoutException(Kind kind, String message) {
        super(message);
        this.kind = kind;
    }

    public Kind getKind() {
        return kind;
    }

    public static CheckoutException transactionNotFound(String transactionId) {
        return new CheckoutException(Kind.TRANSACTION_NOT_FOUND,
                "Transaction " + transactionId + " was not found.");
    }

    public static CheckoutException itemNotFound(String sku) {
        return new CheckoutException(Kind.ITEM_NOT_FOUND, "SKU " + sku + " was not found.");
    }

    public static CheckoutException transactionNotOpen(String transactionId, Object status) {
        return new CheckoutException(Kind.TRANSACTION_NOT_OPEN,
                "Transaction " + transactionId + " is already " + status + ".");
    }

    public static CheckoutException basketEmpty(String transactionId) {
        return new CheckoutException(Kind.BASKET_EMPTY,
                "Transaction " + transactionId + " has no scanned items.");
    }
}
