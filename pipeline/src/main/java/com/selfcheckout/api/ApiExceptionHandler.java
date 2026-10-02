package com.selfcheckout.api;

import com.selfcheckout.transactions.CheckoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * Translates failures into the {@code ApiError} response shape. The only place in the
 * application that maps domain outcomes to HTTP status codes.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(CheckoutException.class)
    public ResponseEntity<Map<String, String>> handleCheckout(CheckoutException ex) {
        HttpStatus status = switch (ex.getKind()) {
            case TRANSACTION_NOT_FOUND, ITEM_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case TRANSACTION_NOT_OPEN, BASKET_EMPTY -> HttpStatus.CONFLICT;
        };
        return body(status, ex.getKind().name(), ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidation(MethodArgumentNotValidException ex) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getField() + " " + error.getDefaultMessage())
                .orElse("Request failed validation.");
        return body(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", detail);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> handleMalformedBody(HttpMessageNotReadableException ex) {
        return body(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", "Request body is not valid JSON.");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return body(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "An unexpected error occurred.");
    }

    private static ResponseEntity<Map<String, String>> body(HttpStatus status, String error, String message) {
        return ResponseEntity.status(status).body(Map.of("error", error, "message", message));
    }
}
