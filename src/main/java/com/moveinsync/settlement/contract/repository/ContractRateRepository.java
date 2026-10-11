package com.moveinsync.settlement.contract.repository;

import com.moveinsync.settlement.contract.entity.ContractRate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ContractRateRepository extends JpaRepository<ContractRate, Long> {
    List<ContractRate> findByContractId(Long contractId);

    @Query("SELECT cr FROM ContractRate cr WHERE cr.contract.id IN :contractIds")
    List<ContractRate> findByContractIds(@Param("contractIds") List<Long> contractIds);
}
