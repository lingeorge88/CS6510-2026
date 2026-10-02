package com.selfcheckout.data;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TransactionLineItemRepository extends JpaRepository<TransactionLineItem, Long> {

    /** All line items for a transaction, used at completion to build the receipt. */
    List<TransactionLineItem> findByTransactionId(String transactionId);
}
