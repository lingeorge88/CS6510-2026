package com.selfcheckout;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TransactionLineItemRepository extends JpaRepository<TransactionLineItem, Long> {

    /**
     * Finds all line items belonging to a given transaction.
     * Used at completion time to build the receipt (group by SKU, aggregate quantity).
     * Spring Data JPA derives the query from the method name:
     * "findBy" + "TransactionId" → {@code SELECT * FROM transaction_line_items WHERE transaction_id = ?}
     *
     * @param transactionId the transaction to look up line items for
     * @return all line items for that transaction
     */
    List<TransactionLineItem> findByTransactionId(String transactionId);
}
