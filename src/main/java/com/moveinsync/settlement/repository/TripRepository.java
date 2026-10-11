package com.moveinsync.settlement.repository;

import com.moveinsync.settlement.entity.Trip;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

/**
 * High-performance repository for commute trip records eligible for settlement.
 *
 * <p>Uses explicit {@code JOIN FETCH} directives on driver and contract entities
 * to eliminate Hibernate N+1 query cascades during 10,000+ trip batch calculations.
 *
 * @author Deepanshu
 */
@Repository
public interface TripRepository extends JpaRepository<Trip, Long> {

    @Query("""
        SELECT t FROM Trip t
        JOIN FETCH t.driver d
        JOIN FETCH t.contract c
        WHERE t.vendor.id = :vendorId
          AND (:businessUnitId IS NULL OR t.businessUnit.id = :businessUnitId)
          AND t.completedAt >= :periodStart
          AND t.completedAt < :periodEnd
          AND t.status IN (com.moveinsync.settlement.entity.Trip.TripStatus.COMPLETED, com.moveinsync.settlement.entity.Trip.TripStatus.DISPUTED)
        ORDER BY t.completedAt ASC
    """)
    List<Trip> findEligibleTrips(
            @Param("vendorId") Long vendorId,
            @Param("businessUnitId") Long businessUnitId,
            @Param("periodStart") Instant periodStart,
            @Param("periodEnd") Instant periodEnd
    );
}
