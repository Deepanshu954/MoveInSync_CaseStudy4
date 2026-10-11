package com.moveinsync.settlement.reconciliation.repository;

import com.moveinsync.settlement.reconciliation.entity.ReconciliationRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ReconciliationRecordRepository extends JpaRepository<ReconciliationRecord, Long> {
    List<ReconciliationRecord> findByInvoiceId(Long invoiceId);
    List<ReconciliationRecord> findBySettlementRunId(Long settlementRunId);
}
