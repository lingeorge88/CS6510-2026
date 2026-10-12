package com.selfcheckout.analytics.data;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

/** Data access for the popular_items_snapshot table. */
public interface PopularItemSnapshotRepository extends JpaRepository<PopularItemSnapshot, Long> {

    /** @return the most recent computed window boundary, or null if none computed yet */
    @Query("SELECT MAX(p.windowEnd) FROM PopularItemSnapshot p")
    Long latestWindowEnd();

    List<PopularItemSnapshot> findByWindowEndOrderByRankAsc(Long windowEnd);
}
