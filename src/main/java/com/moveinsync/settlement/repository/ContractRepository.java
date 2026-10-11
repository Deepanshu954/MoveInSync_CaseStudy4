package com.moveinsync.settlement.repository;

import com.moveinsync.settlement.entity.Contract;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
/**
 * Data access operations for vendor commercial contracts and master SLA agreements.
 *
 * <p>In MoveInSync's commute operations, a {@link com.moveinsync.settlement.entity.Contract}
 * represents the master commercial agreement between MoveInSync/Client Enterprises and a transporter (Vendor).
 *
 * @author MoveInSync Core Settlement Team
 */
public interface ContractRepository extends JpaRepository<Contract, Long> {
    List<Contract> findByVendorId(Long vendorId);
}
