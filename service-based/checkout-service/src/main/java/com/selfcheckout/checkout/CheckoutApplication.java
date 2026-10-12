package com.selfcheckout.checkout;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Checkout Service — owns the transaction lifecycle ({@code transactions},
 * {@code transaction_line_items}) and serves the start/scan/complete/get
 * endpoints.
 */
@SpringBootApplication
@EntityScan(basePackages = {
        "com.selfcheckout.checkout.data",
        "com.selfcheckout.shared.catalog",
        "com.selfcheckout.shared.inventory",
        "com.selfcheckout.shared.scan"
})
@EnableJpaRepositories(basePackages = {
        "com.selfcheckout.checkout.data",
        "com.selfcheckout.shared.catalog",
        "com.selfcheckout.shared.inventory",
        "com.selfcheckout.shared.scan"
})
public class CheckoutApplication {
    public static void main(String[] args) {
        SpringApplication.run(CheckoutApplication.class, args);
    }
}
