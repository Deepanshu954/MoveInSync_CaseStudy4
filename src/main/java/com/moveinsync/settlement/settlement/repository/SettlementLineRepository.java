package com.moveinsync.settlement.settlement.repository;

import com.moveinsync.settlement.settlement.entity.SettlementLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface SettlementLineRepository extends JpaRepository<SettlementLine, Long> {
    List<SettlementLine> findByRunId(Long runId);
    List<SettlementLine> findByRunIdAndDriverId(Long runId, Long driverId);
    List<SettlementLine> findByRunIdOrderByDriverIdAsc(Long runId);
}
