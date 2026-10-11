package com.moveinsync.settlement.repository;

import com.moveinsync.settlement.entity.Dispute;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

/**
 * Repository for managing vendor and driver billing dispute tickets.
 *
 * @author Deepanshu
 */
@Repository
public interface DisputeRepository extends JpaRepository<Dispute, Long> {
    List<Dispute> findByTripId(Long tripId);
    List<Dispute> findBySettlementRunId(Long settlementRunId);
    List<Dispute> findByStatus(Dispute.DisputeStatus status);
}
