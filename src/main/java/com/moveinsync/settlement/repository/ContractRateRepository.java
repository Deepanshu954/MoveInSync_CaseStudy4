package com.moveinsync.settlement.repository;

import com.moveinsync.settlement.entity.ContractRate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

/**
 * Repository interface for managing time-sliced contract rate schedules.
 *
 * <p>Supports time-sliced pricing amendments where rate changes occur mid-month (e.g. Oct 15th).
 *
 * @author Deepanshu
 */
@Repository
public interface ContractRateRepository extends JpaRepository<ContractRate, Long> {
    List<ContractRate> findByContractId(Long contractId);

    @Query("SELECT cr FROM ContractRate cr WHERE cr.contract.id IN :contractIds")
    List<ContractRate> findByContractIds(@Param("contractIds") List<Long> contractIds);
}
