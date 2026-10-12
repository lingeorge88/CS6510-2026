package com.selfcheckout.shared.inventory;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Shared stock access. The decrement is the correctness-critical write of the
 * whole system and is the one piece of DB access genuinely shared across a
 * service boundary (Checkout writes, Inventory reads).
 */
public interface InventoryStockRepository extends JpaRepository<InventoryStock, String> {

    /**
     * Atomically decrements stock by 1. The {@code stock > 0} guard collapses the
     * check-then-decrement into a single statement, so even though the decision is
     * made in the Checkout Service there is no cross-boundary race window: the row
     * write-lock serializes concurrent decrements and only one UPDATE matches the
     * last unit. Backed by a {@code CHECK (stock >= 0)} constraint as a backstop.
     *
     * @return 1 if stock was decremented, 0 if the SKU was already out of stock
     */
    @Modifying
    @Query("UPDATE InventoryStock s SET s.stock = s.stock - 1 WHERE s.sku = :sku AND s.stock > 0")
    int decrementStock(@Param("sku") String sku);

    /** @param threshold stock level below which items are considered low-stock */
    List<InventoryStock> findByStockLessThan(int threshold);
}
