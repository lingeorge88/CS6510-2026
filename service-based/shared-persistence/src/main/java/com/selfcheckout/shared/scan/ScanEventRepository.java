package com.selfcheckout.shared.scan;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/** Data access for the scan_events table. */
public interface ScanEventRepository extends JpaRepository<ScanEvent, Long> {

    /** @return the highest scan id so far, or null if there are none */
    @Query("SELECT MAX(s.id) FROM ScanEvent s")
    Long maxSequence();

    /**
     * Ranks SKUs within the inclusive id window, joined to the catalog name,
     * ordered by scan count descending then SKU ascending.
     */
    @Query(value = """
            SELECT s.sku AS "sku", c.name AS "name", COUNT(*) AS "scanCount"
            FROM scan_events s
            JOIN catalog_items c ON c.sku = s.sku
            WHERE s.id BETWEEN :start AND :end
            GROUP BY s.sku, c.name
            ORDER BY "scanCount" DESC, s.sku ASC
            """, nativeQuery = true)
    List<RankedScanRow> rankWindow(@Param("start") long start, @Param("end") long end);

    /** Projection for {@link #rankWindow}. */
    interface RankedScanRow {
        String getSku();

        String getName();

        long getScanCount();
    }
}
