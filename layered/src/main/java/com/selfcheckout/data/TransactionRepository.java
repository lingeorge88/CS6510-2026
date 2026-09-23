package com.selfcheckout.data;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data JPA repository for {@link Transaction} entities.
 * No custom queries needed — {@code JpaRepository} provides {@code findById},
 * {@code save}, {@code findAll}, and {@code deleteById} out of the box.
 */
public interface TransactionRepository extends JpaRepository<Transaction, String> {

}
