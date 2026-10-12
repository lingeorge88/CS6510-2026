package com.selfcheckout.checkout.data;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** Data access for the transaction_line_items table. */
public interface TransactionLineItemRepository extends JpaRepository<TransactionLineItem, Long> {

    List<TransactionLineItem> findByTransactionId(String transactionId);
}
