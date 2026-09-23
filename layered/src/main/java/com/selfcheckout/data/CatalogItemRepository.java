package com.selfcheckout.data;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

public interface CatalogItemRepository extends JpaRepository<CatalogItem, String> {

    /**
     * Atomically decrements stock by 1. The {@code stock > 0} guard prevents negative
     * inventory: if two threads race on the last unit, only one UPDATE matches.
     *
     * @return 1 if stock was decremented, 0 if the item was already out of stock
     */
    @Modifying
    @Transactional
    @Query("UPDATE CatalogItem c SET c.stock = c.stock - 1 WHERE c.sku = :sku AND c.stock > 0")
    int decrementStock(@Param("sku") String sku);

    /** @param threshold stock level below which items are considered low-stock */
    List<CatalogItem> findByStockLessThan(int threshold);
}
