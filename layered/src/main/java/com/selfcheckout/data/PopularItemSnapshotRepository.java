package com.selfcheckout.data;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface PopularItemSnapshotRepository extends JpaRepository<PopularItemSnapshot, Long> {

    /**
     * All rows of the most recently computed snapshot, identified by max {@code windowEnd}.
     *
     * @return the latest snapshot rows, or empty if none exist yet
     */
    @Query("SELECT p FROM PopularItemSnapshot p WHERE p.windowEnd = " +
            "(SELECT MAX(p2.windowEnd) FROM PopularItemSnapshot p2)")
    List<PopularItemSnapshot> findLatestSnapshot();
}
