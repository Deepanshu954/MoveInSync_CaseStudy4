package com.moveinsync.settlement.driver.repository;

import com.moveinsync.settlement.driver.entity.Driver;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface DriverRepository extends JpaRepository<Driver, Long> {
    List<Driver> findByVendorId(Long vendorId);
    Optional<Driver> findByVendorIdAndExternalRef(Long vendorId, String externalRef);
    Optional<Driver> findByAppUserId(Long appUserId);
}
