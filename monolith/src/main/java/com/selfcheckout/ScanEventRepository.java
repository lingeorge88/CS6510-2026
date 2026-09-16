package com.selfcheckout;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
public interface ScanEventRepository extends JpaRepository<ScanEvent, Long> {

    /**
     * Finds the most frequently scanned SKUs within the last {@code windowSize} scan events.
     * Uses native SQL because JPQL does not support LIMIT inside subqueries.
     *
     * <p>How it works:
     * <ol>
     *   <li>Inner query: selects the most recent {@code windowSize} events ordered by global_seq</li>
     *   <li>Outer query: groups those by SKU, counts occurrences, returns the top {@code limit} results</li>
     * </ol>
     *
     * <p>{@code nativeQuery = true} means this is raw PostgreSQL — column names use snake_case
     * (e.g. {@code global_seq}) rather than Java field names (e.g. {@code globalSeq}).
     *
     * <p>Returns {@code List<Object[]>} where each row is:
     * {@code [0]} = sku (String), {@code [1]} = scan_count (Number).
     *
     * @param windowSize how many recent scan events to consider (spec default: 1000)
     * @param limit how many top SKUs to return (spec default: 10)
     * @return ranked list of [sku, scanCount] pairs, most scanned first
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
}
