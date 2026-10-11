package com.moveinsync.settlement.vendor.repository;

import com.moveinsync.settlement.vendor.entity.Vendor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface VendorRepository extends JpaRepository<Vendor, Long> {
    Optional<Vendor> findByCode(String code);
}
