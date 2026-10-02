package com.selfcheckout.data;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
public interface ScanEventRepository extends JpaRepository<ScanEvent, Long> {

    /**
     * The most frequently scanned SKUs within the last {@code windowSize} scan events.
     * Native SQL because JPQL cannot express LIMIT inside a subquery.
     *
     * @return ranked rows of {@code [sku, scanCount]}, most scanned first
     */
    @Query(value =
        "SELECT recent_scans.sku, COUNT(*) AS scan_count " +
        "FROM (SELECT * FROM scan_events ORDER BY global_seq DESC LIMIT :windowSize) recent_scans " +
        "GROUP BY recent_scans.sku " +
        "ORDER BY scan_count DESC " +
        "LIMIT :limit",
        nativeQuery = true)
    List<Object[]> findTopScannedSkus(@Param("windowSize") int windowSize,
                                      @Param("limit") int limit);

    /**
     * Ranks every SKU in one immutable, inclusive scan sequence interval.
     * Joining catalog metadata avoids a separate catalog lookup per ranked SKU.
     *
     * @return rows of {@code [sku, name, scanCount]}, count descending then SKU ascending
     */
    @Query(value = """
            SELECT e.sku, c.name, COUNT(*) AS scan_count
            FROM scan_events e
            JOIN catalog_items c ON c.sku = e.sku
            WHERE e.global_seq >= :windowStart AND e.global_seq <= :windowEnd
            GROUP BY e.sku, c.name
            ORDER BY scan_count DESC, e.sku ASC
            """, nativeQuery = true)
    List<Object[]> findRankedScans(@Param("windowStart") long windowStart,
                                  @Param("windowEnd") long windowEnd);
}
