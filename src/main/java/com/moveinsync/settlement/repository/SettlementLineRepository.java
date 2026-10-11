package com.moveinsync.settlement.repository;

import com.moveinsync.settlement.entity.SettlementLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
/**
 * Repository for granular accounting line items generated during settlement execution.
 *
 * <p>Tracks base trip earnings, incentive additions, violation deductions, and TDS withholding lines.
 *
 * @author MoveInSync Core Settlement Team
 */
public interface SettlementLineRepository extends JpaRepository<SettlementLine, Long> {
    List<SettlementLine> findByRunId(Long runId);
    List<SettlementLine> findByRunIdAndDriverId(Long runId, Long driverId);
    List<SettlementLine> findByRunIdOrderByDriverIdAsc(Long runId);
    List<SettlementLine> findBySourceTypeAndSourceId(String sourceType, Long sourceId);
    Optional<SettlementLine> findFirstBySourceTypeAndSourceId(String sourceType, Long sourceId);
}
