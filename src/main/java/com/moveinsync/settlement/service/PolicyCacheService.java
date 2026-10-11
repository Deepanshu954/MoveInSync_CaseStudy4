package com.moveinsync.settlement.service;

import com.moveinsync.settlement.entity.ContractRate;
import com.moveinsync.settlement.entity.IncentivePolicy;
import com.moveinsync.settlement.entity.PenaltyPolicy;
import com.moveinsync.settlement.entity.TaxConfig;
import com.moveinsync.settlement.repository.ContractRateRepository;
import com.moveinsync.settlement.repository.IncentivePolicyRepository;
import com.moveinsync.settlement.repository.PenaltyPolicyRepository;
import com.moveinsync.settlement.repository.TaxConfigRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * High-performance caching service for financial rate tables and settlement policies.
 *
 * <p>Avoids repeated database roundtrips during high-volume batch runs by caching
 * immutable policy slices in-memory with explicit cache eviction capabilities.
 *
 * @author MoveInSync Core Settlement Team
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PolicyCacheService {

    
    private final ContractRateRepository contractRateRepository;
    private final IncentivePolicyRepository incentivePolicyRepository;
    private final PenaltyPolicyRepository penaltyPolicyRepository;
    private final TaxConfigRepository taxConfigRepository;


    @Cacheable(value = "contractRates", key = "#contractIds")
    public List<ContractRate> getContractRates(List<Long> contractIds) {
        if (contractIds == null || contractIds.isEmpty()) {
            return Collections.emptyList();
        }
        log.debug("Cache miss for contract rates: {}", contractIds);
        return contractRateRepository.findByContractIds(contractIds);
    }

    @Cacheable(value = "incentivePolicies", key = "{#vendorId, #businessUnitId, #atTime}")
    public List<IncentivePolicy> getIncentivePolicies(Long vendorId, Long businessUnitId, Instant atTime) {
        log.debug("Cache miss for incentive policies: vendorId={}, buId={}, atTime={}", vendorId, businessUnitId, atTime);
        return incentivePolicyRepository.findEffectivePolicies(vendorId, businessUnitId, atTime);
    }

    @Cacheable(value = "penaltyPolicies", key = "{#vendorId, #businessUnitId, #atTime}")
    public List<PenaltyPolicy> getPenaltyPolicies(Long vendorId, Long businessUnitId, Instant atTime) {
        log.debug("Cache miss for penalty policies: vendorId={}, buId={}, atTime={}", vendorId, businessUnitId, atTime);
        return penaltyPolicyRepository.findEffectivePolicies(vendorId, businessUnitId, atTime);
    }

    @Cacheable(value = "taxConfig", key = "#atTime")
    public Optional<TaxConfig> getTaxConfig(Instant atTime) {
        log.debug("Cache miss for statutory tax config: atTime={}", atTime);
        return taxConfigRepository.findEffectiveConfig(atTime);
    }

    @CacheEvict(value = {"contractRates", "incentivePolicies", "penaltyPolicies", "taxConfig"}, allEntries = true)
    public void evictAll() {
        log.info("Evicted all financial policies and contract rate caches");
    }
}
