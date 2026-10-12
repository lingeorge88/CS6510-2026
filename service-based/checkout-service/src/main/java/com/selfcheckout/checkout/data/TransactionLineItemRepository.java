package com.selfcheckout.checkout.data;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** Private to the Checkout Service. */
public interface TransactionLineItemRepository extends JpaRepository<TransactionLineItem, Long> {

    List<TransactionLineItem> findByTransactionId(String transactionId);
}
