package com.moveinsync.settlement.repository;

import com.moveinsync.settlement.entity.Vendor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
/**
 * Repository for fleet vendor (transporter) organizations.
 *
 * @author MoveInSync Core Settlement Team
 */
public interface VendorRepository extends JpaRepository<Vendor, Long> {
    Optional<Vendor> findByCode(String code);
}
