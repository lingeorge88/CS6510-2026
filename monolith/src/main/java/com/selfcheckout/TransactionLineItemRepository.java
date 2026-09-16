package com.selfcheckout;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TransactionLineItemRepository extends JpaRepository<TransactionLineItem, Long> {

}
