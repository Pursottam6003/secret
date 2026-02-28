package com.expensetracker.config;

import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Lightweight in-memory cache for the dev profile.
 * No Redis required — data lives in a ConcurrentHashMap.
 */
@Configuration
@Profile("dev")
public class DevCacheConfig {

    @Bean
    public CacheManager cacheManager() {
        return new ConcurrentMapCacheManager("groupBalances", "analytics", "userGroups");
    }
}
