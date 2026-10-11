package com.moveinsync.settlement.settlement.repository;

import com.moveinsync.settlement.settlement.entity.SettlementRun;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface SettlementRunRepository extends JpaRepository<SettlementRun, Long> {

    @Query("""
        SELECT r FROM SettlementRun r
        WHERE r.vendor.id = :vendorId
          AND (:businessUnitId IS NULL OR r.businessUnit.id = :businessUnitId)
          AND r.periodStart = :periodStart
          AND r.periodEnd = :periodEnd
          AND r.runType = com.moveinsync.settlement.settlement.entity.SettlementRun.RunType.ORIGINAL
          AND r.status != com.moveinsync.settlement.settlement.entity.SettlementRun.RunStatus.FAILED
    """)
    Optional<SettlementRun> findExistingOriginalRun(
            @Param("vendorId") Long vendorId,
            @Param("businessUnitId") Long businessUnitId,
            @Param("periodStart") LocalDate periodStart,
            @Param("periodEnd") LocalDate periodEnd
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM SettlementRun r WHERE r.id = :id")
    Optional<SettlementRun> findByIdForUpdate(@Param("id") Long id);

    @Query("""
        SELECT r FROM SettlementRun r
        WHERE r.status = com.moveinsync.settlement.settlement.entity.SettlementRun.RunStatus.PROCESSING
          AND r.createdAt < :cutoff
    """)
    List<SettlementRun> findStaleProcessingRuns(@Param("cutoff") Instant cutoff);

    List<SettlementRun> findByVendorIdOrderByPeriodStartDesc(Long vendorId);
}
