package com.moveinsync.settlement.settlement.repository;

import com.moveinsync.settlement.settlement.entity.DriverPayslip;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface DriverPayslipRepository extends JpaRepository<DriverPayslip, Long> {
    List<DriverPayslip> findByRunId(Long runId);
    Optional<DriverPayslip> findByRunIdAndDriverId(Long runId, Long driverId);
}
