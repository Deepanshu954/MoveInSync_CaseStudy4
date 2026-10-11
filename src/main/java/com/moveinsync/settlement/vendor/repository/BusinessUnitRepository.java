package com.moveinsync.settlement.vendor.repository;

import com.moveinsync.settlement.vendor.entity.BusinessUnit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface BusinessUnitRepository extends JpaRepository<BusinessUnit, Long> {
    List<BusinessUnit> findByVendorId(Long vendorId);
    Optional<BusinessUnit> findByVendorIdAndCode(Long vendorId, String code);
}
