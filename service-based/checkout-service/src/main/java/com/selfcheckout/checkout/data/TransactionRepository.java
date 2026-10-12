package com.selfcheckout.checkout.data;

import org.springframework.data.jpa.repository.JpaRepository;

/** Private to the Checkout Service. */
public interface TransactionRepository extends JpaRepository<Transaction, String> {
}
