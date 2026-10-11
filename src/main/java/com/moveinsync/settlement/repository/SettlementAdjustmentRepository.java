package com.moveinsync.settlement.repository;

import com.moveinsync.settlement.entity.SettlementAdjustment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

/**
 * Repository for managing retroactive delta adjustments and dispute settlements.
 *
 * <p>Preserves immutable historical ledgers by booking approved dispute corrections
 * into subsequent settlement cycles.
 *
 * @author Deepanshu
 */
@Repository
public interface SettlementAdjustmentRepository extends JpaRepository<SettlementAdjustment, Long> {
    List<SettlementAdjustment> findByDisputeId(Long disputeId);
    List<SettlementAdjustment> findByOriginalRunId(Long originalRunId);
    List<SettlementAdjustment> findByAdjustmentRunId(Long adjustmentRunId);
    List<SettlementAdjustment> findByDriverId(Long driverId);
}
