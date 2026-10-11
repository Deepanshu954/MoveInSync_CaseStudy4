package com.moveinsync.settlement.repository;

import com.moveinsync.settlement.entity.IncentivePolicy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

/**
 * Repository for driver and vendor performance incentive rules.
 *
 * @author MoveInSync Core Settlement Team
 */
@Repository
public interface IncentivePolicyRepository extends JpaRepository<IncentivePolicy, Long> {

    @Query("""
        SELECT p FROM IncentivePolicy p
        WHERE p.vendor.id = :vendorId
          AND (p.businessUnit IS NULL OR p.businessUnit.id = :buId)
          AND p.effectiveFrom <= :atTime
          AND (p.effectiveTo IS NULL OR p.effectiveTo > :atTime)
    """)
    List<IncentivePolicy> findEffectivePolicies(
            @Param("vendorId") Long vendorId,
            @Param("buId") Long buId,
            @Param("atTime") Instant atTime
    );
}
