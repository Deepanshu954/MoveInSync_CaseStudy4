package com.moveinsync.settlement.settlement.repository;

import com.moveinsync.settlement.settlement.entity.SettlementAdjustment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface SettlementAdjustmentRepository extends JpaRepository<SettlementAdjustment, Long> {
    List<SettlementAdjustment> findByDisputeId(Long disputeId);
    List<SettlementAdjustment> findByOriginalRunId(Long originalRunId);
    List<SettlementAdjustment> findByAdjustmentRunId(Long adjustmentRunId);
    List<SettlementAdjustment> findByDriverId(Long driverId);
}
