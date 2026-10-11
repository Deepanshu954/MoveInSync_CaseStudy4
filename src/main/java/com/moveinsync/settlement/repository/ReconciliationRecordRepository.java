package com.moveinsync.settlement.repository;

import com.moveinsync.settlement.entity.ReconciliationRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
/**
 * Repository for three-way financial reconciliation audit logs.
 *
 * @author MoveInSync Core Settlement Team
 */
public interface ReconciliationRecordRepository extends JpaRepository<ReconciliationRecord, Long> {
    List<ReconciliationRecord> findByInvoiceId(Long invoiceId);
    List<ReconciliationRecord> findBySettlementRunId(Long settlementRunId);
}
