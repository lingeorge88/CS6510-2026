package com.selfcheckout.shared.inventory;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/** Data access for the inventory_stock table. */
public interface InventoryStockRepository extends JpaRepository<InventoryStock, String> {

    /**
     * Atomically decrements stock by 1, guarded so stock never goes negative.
     *
     * @return 1 if stock was decremented, 0 if the SKU was already out of stock
     */
    @Modifying
    @Query("UPDATE InventoryStock s SET s.stock = s.stock - 1 WHERE s.sku = :sku AND s.stock > 0")
    int decrementStock(@Param("sku") String sku);

    List<InventoryStock> findByStockLessThan(int threshold);
}
