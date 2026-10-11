package com.moveinsync.settlement.common.config;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * High-performance in-memory cache configuration for financial policies and contract rates.
 *
 * <p>Caches are partitioned by domain entity type to avoid cross-domain contention
 * and allow targeted invalidation upon policy or contract rate revisions.
 *
 * @author Deepanshu
 */
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        return new ConcurrentMapCacheManager(
                "contractRates",
                "incentivePolicies",
                "penaltyPolicies",
                "taxConfig"
        );
    }
}
