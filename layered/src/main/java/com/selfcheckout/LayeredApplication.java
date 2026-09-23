package com.selfcheckout;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Week 2 — layered implementation of the self-checkout API.
 *
 * <p>Layers: {@code api} → {@code transactions} → {@code analytics} → {@code data},
 * enforced by {@code LayeringRulesTest}.
 */
@SpringBootApplication
public class LayeredApplication {

    public static void main(String[] args) {
        SpringApplication.run(LayeredApplication.class, args);
    }
}
