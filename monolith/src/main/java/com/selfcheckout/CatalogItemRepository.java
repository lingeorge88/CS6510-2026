package com.selfcheckout;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

public interface CatalogItemRepository extends JpaRepository<CatalogItem, String> {

    /**
     * Atomically decrements stock by 1 for the given SKU.
     * The WHERE guard ({@code stock > 0}) prevents negative inventory and makes this
     * concurrent-safe — if two threads race on the last unit, the DB row lock ensures
     * only one UPDATE matches; the loser gets 0 rows affected.
     *
     * <p>{@code @Modifying} tells Spring this is a write query (not a SELECT).
     * {@code @Transactional} wraps it in a transaction so the UPDATE can execute.
     * {@code @Query} provides the JPQL directly instead of using a derived method name.
     *
     * @param sku the catalog item SKU to decrement
     * @return 1 if stock was decremented, 0 if the item was already out of stock
     */
    @Modifying
    @Transactional
    @Query("UPDATE CatalogItem c SET c.stock = c.stock - 1 WHERE c.sku = :sku AND c.stock > 0")
    int decrementStock(@Param("sku") String sku);

    /**
     * Finds all catalog items with stock below the given threshold.
     * Spring Data JPA generates the query automatically from the method name:
     * "findBy" + "Stock" + "LessThan" → {@code SELECT * FROM catalog_items WHERE stock < ?}
     *
     * @param threshold the stock level below which items are considered low-stock
     * @return list of catalog items with stock less than the threshold
     */
    List<CatalogItem> findByStockLessThan(int threshold);
}
