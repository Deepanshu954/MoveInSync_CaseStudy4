package com.moveinsync.settlement.dispute.repository;

import com.moveinsync.settlement.dispute.entity.Dispute;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface DisputeRepository extends JpaRepository<Dispute, Long> {
    List<Dispute> findByTripId(Long tripId);
    List<Dispute> findBySettlementRunId(Long settlementRunId);
    List<Dispute> findByStatus(Dispute.DisputeStatus status);
}
