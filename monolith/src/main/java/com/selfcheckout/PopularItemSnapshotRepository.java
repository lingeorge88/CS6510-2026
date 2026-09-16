package com.selfcheckout;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface PopularItemSnapshotRepository extends JpaRepository<PopularItemSnapshot, Long> {

    /**
     * Returns all rows from the most recently computed popular-items snapshot.
     * Each snapshot computation writes multiple rows (one per ranked SKU) sharing the same
     * {@code windowEnd} value. The subquery finds the max {@code windowEnd}, then the outer
     * query returns all rows matching it — this is atomic, so no new snapshot can sneak in
     * between two separate queries.
     *
     * <p>This is JPQL (not native SQL), so it uses Java field names (e.g. {@code windowEnd})
     * rather than column names (e.g. {@code window_end}).
     *
     * @return all snapshot rows from the latest computation, or empty list if none exist yet
     */
    @Query("SELECT p FROM PopularItemSnapshot p WHERE p.windowEnd = " +
            "(SELECT MAX(p2.windowEnd) FROM PopularItemSnapshot p2)")
    List<PopularItemSnapshot> findLatestSnapshot();
}
