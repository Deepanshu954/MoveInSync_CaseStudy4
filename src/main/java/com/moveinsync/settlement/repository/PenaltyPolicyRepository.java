package com.moveinsync.settlement.repository;

import com.moveinsync.settlement.entity.PenaltyPolicy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
/**
 * Repository for SLA breach and safety penalty deduction rules.
 *
 * @author MoveInSync Core Settlement Team
 */
public interface PenaltyPolicyRepository extends JpaRepository<PenaltyPolicy, Long> {

    @Query("""
        SELECT p FROM PenaltyPolicy p
        WHERE p.vendor.id = :vendorId
          AND (p.businessUnit IS NULL OR p.businessUnit.id = :buId)
          AND p.effectiveFrom <= :atTime
          AND (p.effectiveTo IS NULL OR p.effectiveTo > :atTime)
    """)
    List<PenaltyPolicy> findEffectivePolicies(
            @Param("vendorId") Long vendorId,
            @Param("buId") Long buId,
            @Param("atTime") Instant atTime
    );
}
