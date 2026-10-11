package com.moveinsync.settlement.repository;

import com.moveinsync.settlement.entity.Driver;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

/**
 * Repository for commercial driver profiles.
 *
 * @author Deepanshu
 */
@Repository
public interface DriverRepository extends JpaRepository<Driver, Long> {
    List<Driver> findByVendorId(Long vendorId);
    Optional<Driver> findByVendorIdAndExternalRef(Long vendorId, String externalRef);
    Optional<Driver> findByAppUserId(Long appUserId);
}
