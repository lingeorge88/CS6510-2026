package com.selfcheckout.checkout.data;

import org.springframework.data.jpa.repository.JpaRepository;

/** Data access for the transactions table. */
public interface TransactionRepository extends JpaRepository<Transaction, String> {
}
