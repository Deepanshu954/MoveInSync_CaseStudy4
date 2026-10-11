package com.moveinsync.settlement.trip.repository;

import com.moveinsync.settlement.trip.entity.Trip;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

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
          AND t.status IN (com.moveinsync.settlement.trip.entity.Trip.TripStatus.COMPLETED, com.moveinsync.settlement.trip.entity.Trip.TripStatus.DISPUTED)
        ORDER BY t.completedAt ASC
    """)
    List<Trip> findEligibleTrips(
            @Param("vendorId") Long vendorId,
            @Param("businessUnitId") Long businessUnitId,
            @Param("periodStart") Instant periodStart,
            @Param("periodEnd") Instant periodEnd
    );
}
