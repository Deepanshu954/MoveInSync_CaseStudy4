package com.moveinsync.settlement.payout.repository;

import com.moveinsync.settlement.payout.entity.PayoutInstruction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface PayoutInstructionRepository extends JpaRepository<PayoutInstruction, Long> {
    Optional<PayoutInstruction> findByIdempotencyKey(String idempotencyKey);
    List<PayoutInstruction> findBySettlementRunId(Long settlementRunId);
    List<PayoutInstruction> findByDriverId(Long driverId);
    List<PayoutInstruction> findByStatus(PayoutInstruction.PayoutStatus status);
}
