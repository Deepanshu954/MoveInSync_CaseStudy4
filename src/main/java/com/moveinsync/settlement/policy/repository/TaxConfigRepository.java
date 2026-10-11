package com.moveinsync.settlement.policy.repository;

import com.moveinsync.settlement.policy.entity.TaxConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

@Repository
public interface TaxConfigRepository extends JpaRepository<TaxConfig, Long> {

    @Query("""
        SELECT tc FROM TaxConfig tc
        WHERE tc.effectiveFrom <= :atTime
          AND (tc.effectiveTo IS NULL OR tc.effectiveTo > :atTime)
        ORDER BY tc.version DESC
        LIMIT 1
    """)
    Optional<TaxConfig> findEffectiveConfig(@Param("atTime") Instant atTime);
}
