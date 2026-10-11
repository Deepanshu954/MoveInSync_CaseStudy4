package com.moveinsync.settlement.repository;

import com.moveinsync.settlement.entity.TripViolation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

/**
 * Repository for telematics safety violations and SLA breach infractions.
 *
 * <p>Captures over-speeding, route deviations, and late-night female employee escort breaches.
 *
 * @author MoveInSync Core Settlement Team
 */
@Repository
public interface TripViolationRepository extends JpaRepository<TripViolation, Long> {

    @Query("""
        SELECT tv FROM TripViolation tv
        JOIN tv.trip t
        WHERE t.driver.id = :driverId
          AND tv.eventAt >= :periodStart
          AND tv.eventAt < :periodEnd
          AND tv.status = com.moveinsync.settlement.entity.TripViolation.ViolationStatus.ACTIVE
        ORDER BY tv.eventAt ASC
    """)
    List<TripViolation> findDriverViolationsInPeriod(
            @Param("driverId") Long driverId,
            @Param("periodStart") Instant periodStart,
            @Param("periodEnd") Instant periodEnd
    );
}
