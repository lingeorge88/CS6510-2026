package com.selfcheckout.analytics.data;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

/** Private to the Analytics Service. */
public interface PopularItemSnapshotRepository extends JpaRepository<PopularItemSnapshot, Long> {

    /** @return the most recent computed window boundary, or null if none computed yet */
    @Query("SELECT MAX(p.windowEnd) FROM PopularItemSnapshot p")
    Long latestWindowEnd();

    /** The ranked rows of one window, in rank order. */
    List<PopularItemSnapshot> findByWindowEndOrderByRankAsc(Long windowEnd);
}
