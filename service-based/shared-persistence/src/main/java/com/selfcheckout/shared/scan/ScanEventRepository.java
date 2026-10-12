package com.selfcheckout.shared.scan;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Scan access. Checkout only ever calls {@code save}; Analytics calls the read
 * methods to drive the hopping-window recomputation.
 */
public interface ScanEventRepository extends JpaRepository<ScanEvent, Long> {

    /** @return the highest scan id so far (the current global sequence head), or null if none */
    @Query("SELECT MAX(s.id) FROM ScanEvent s")
    Long maxSequence();

    /**
     * Ranks SKUs within the inclusive id window {@code [start, end]}, joining the
     * catalog for the display name. Count desc, then SKU asc for stable tie-breaks.
     *
     * @return rows of {@code [sku, name, scanCount]}
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
