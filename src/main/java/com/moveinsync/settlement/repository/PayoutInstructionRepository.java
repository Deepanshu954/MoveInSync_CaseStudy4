package com.moveinsync.settlement.repository;

import com.moveinsync.settlement.entity.PayoutInstruction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

/**
 * Repository for banking disbursement instructions sent to payment gateways.
 *
 * @author MoveInSync Core Settlement Team
 */
@Repository
public interface PayoutInstructionRepository extends JpaRepository<PayoutInstruction, Long> {
    Optional<PayoutInstruction> findByIdempotencyKey(String idempotencyKey);
    List<PayoutInstruction> findBySettlementRunId(Long settlementRunId);
    List<PayoutInstruction> findByDriverId(Long driverId);
    List<PayoutInstruction> findByStatus(PayoutInstruction.PayoutStatus status);
}
