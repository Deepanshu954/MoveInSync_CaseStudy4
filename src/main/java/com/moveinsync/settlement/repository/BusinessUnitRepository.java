package com.moveinsync.settlement.repository;

import com.moveinsync.settlement.entity.BusinessUnit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
/**
 * Repository for corporate client business units and campus zones.
 *
 * @author MoveInSync Core Settlement Team
 */
public interface BusinessUnitRepository extends JpaRepository<BusinessUnit, Long> {
    List<BusinessUnit> findByVendorId(Long vendorId);
    Optional<BusinessUnit> findByVendorIdAndCode(Long vendorId, String code);
}
