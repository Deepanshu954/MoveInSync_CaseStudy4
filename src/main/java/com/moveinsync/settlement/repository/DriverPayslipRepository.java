package com.moveinsync.settlement.repository;

import com.moveinsync.settlement.entity.DriverPayslip;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

/**
 * Repository for immutable driver monthly earnings summaries (payslips).
 *
 * <p>Contains gross earnings, volume incentives, safety deductions, and net payouts.
 *
 * @author Deepanshu
 */
@Repository
public interface DriverPayslipRepository extends JpaRepository<DriverPayslip, Long> {
    List<DriverPayslip> findByRunId(Long runId);
    Optional<DriverPayslip> findByRunIdAndDriverId(Long runId, Long driverId);
}
