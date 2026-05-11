package com.example.ordermgmt.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * CACHE CONFIGURATION
 *
 * CONCEPT: Store frequently read data in memory
 *          Avoid hitting DB for every request
 *
 * INTERVIEW POINTS:
 * Cache hierarchy:
 *   L1: Application memory (Caffeine) -- fastest, local
 *   L2: Distributed cache (Redis)     -- shared across instances
 *   L3: Database                      -- slowest, source of truth
 *
 * Caffeine:
 *   In-memory cache
 *   Single instance only
 *   Lost on restart
 *   Best for: read-heavy, rarely changing data
 *
 * Redis:
 *   Distributed cache
 *   Shared across multiple instances
 *   Survives restart
 *   Best for: multi-instance prod deployments
 *
 * Cache eviction strategies:
 *   expireAfterWrite  -- evict N seconds after write
 *   expireAfterAccess -- evict N seconds after last access
 *   maximumSize       -- max entries, evict LRU when full
 */
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager();

        // per-cache config
        manager.registerCustomCache("order",
                Caffeine.newBuilder()
                        .expireAfterWrite(10, TimeUnit.MINUTES)
                        .maximumSize(1000)
                        .recordStats()
                        .build());

        manager.registerCustomCache("orders",
                Caffeine.newBuilder()
                        .expireAfterWrite(5, TimeUnit.MINUTES)  // shorter TTL for lists
                        .maximumSize(100)
                        .recordStats()
                        .build());

        return manager;
    }
}