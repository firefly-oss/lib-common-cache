/*
 * Copyright 2025 Firefly Software Solutions Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.firefly.common.cache.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.firefly.common.cache.adapter.caffeine.CaffeineCacheAdapter;
import com.firefly.common.cache.adapter.caffeine.CaffeineCacheConfig;
import com.firefly.common.cache.core.CacheAdapter;
import com.firefly.common.cache.manager.FireflyCacheManager;
import com.firefly.common.cache.properties.CacheProperties;
import com.firefly.common.cache.serialization.CacheSerializer;
import com.firefly.common.cache.serialization.JsonCacheSerializer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Core auto-configuration for the Firefly Cache library.
 * <p>
 * This configuration class automatically sets up the cache library when included
 * in a Spring Boot application. It enables configuration properties, sets up
 * component scanning, and provides default beans where needed.
 * <p>
 * Components automatically discovered and configured:
 * <ul>
 *   <li>Caffeine cache adapter (always available)</li>
 *   <li>Cache manager with selection strategy</li>
 *   <li>Serialization support</li>
 *   <li>Health indicators for Spring Boot Actuator</li>
 *   <li>Metrics collection via Micrometer</li>
 * </ul>
 * <p>
 * <b>Note:</b> Redis support is optional and configured separately in
 * {@link RedisCacheAutoConfiguration} when Redis dependencies are present.
 *
 * @see RedisCacheAutoConfiguration
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "firefly.cache", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(CacheProperties.class)
@ComponentScan(basePackages = "com.firefly.common.cache")
@EnableAsync
@Slf4j
public class CacheAutoConfiguration {

    public CacheAutoConfiguration() {
        log.info("Firefly Cache Auto-Configuration - Starting initialization");
        log.info("Caffeine cache adapter will be auto-configured (Redis is optional)");
    }

    /**
     * Provides a default ObjectMapper for JSON serialization if none exists.
     */
    @Bean("cacheObjectMapper")
    @ConditionalOnMissingBean(name = "cacheObjectMapper")
    public ObjectMapper cacheObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        return mapper;
    }

    /**
     * Provides a default JSON cache serializer.
     */
    @Bean
    @ConditionalOnMissingBean
    public CacheSerializer cacheSerializer(@Qualifier("cacheObjectMapper") ObjectMapper objectMapper) {
        log.debug("Creating JSON cache serializer");
        return new JsonCacheSerializer(objectMapper);
    }

    /**
     * Creates the main Firefly cache manager.
     * <p>
     * The manager will use the configured cache adapter as primary,
     * and optionally a second one as fallback (e.g., Redis primary, Caffeine fallback).
     */
    @Bean
    @Primary
    public FireflyCacheManager fireflyCacheManager(CacheProperties properties,
                                                   java.util.List<CacheAdapter> cacheAdapters) {
        log.info("Creating Firefly Cache Manager");
        log.info("   • Preferred cache type: {}", properties.getDefaultCacheType());
        log.info("   • Available cache adapters: {}", cacheAdapters.size());

        if (cacheAdapters.isEmpty()) {
            throw new IllegalStateException("No cache adapters available. At least Caffeine should be configured.");
        }

        // Select primary and fallback caches based on type preference
        CacheAdapter primaryCache = null;
        CacheAdapter fallbackCache = null;

        // Prefer Redis as primary if available and configured
        if (properties.getDefaultCacheType() == com.firefly.common.cache.core.CacheType.REDIS) {
            primaryCache = cacheAdapters.stream()
                    .filter(adapter -> adapter.getCacheType() == com.firefly.common.cache.core.CacheType.REDIS)
                    .findFirst()
                    .orElse(null);

            // Use Caffeine as fallback
            fallbackCache = cacheAdapters.stream()
                    .filter(adapter -> adapter.getCacheType() == com.firefly.common.cache.core.CacheType.CAFFEINE)
                    .findFirst()
                    .orElse(null);
        }

        // If no Redis or preference is Caffeine, use Caffeine as primary
        if (primaryCache == null) {
            primaryCache = cacheAdapters.stream()
                    .filter(adapter -> adapter.getCacheType() == com.firefly.common.cache.core.CacheType.CAFFEINE)
                    .findFirst()
                    .orElse(cacheAdapters.get(0)); // Fallback to first available
        }

        log.info("   • Primary cache: {} ({})", primaryCache.getCacheName(), primaryCache.getCacheType());
        if (fallbackCache != null) {
            log.info("   • Fallback cache: {} ({})", fallbackCache.getCacheName(), fallbackCache.getCacheType());
        }

        return new FireflyCacheManager(primaryCache, fallbackCache);
    }

    // ================================
    // Caffeine Cache Configuration
    // ================================

    /**
     * Creates a Caffeine cache adapter when Caffeine is available and enabled.
     */
    @Bean
    @ConditionalOnClass(name = "com.github.benmanes.caffeine.cache.Caffeine")
    @ConditionalOnProperty(prefix = "firefly.cache.caffeine", name = "enabled", havingValue = "true", matchIfMissing = true)
    public CacheAdapter caffeineCacheAdapter(CacheProperties properties) {
        log.debug("Creating Caffeine cache adapter");
        CacheProperties.CaffeineConfig caffeineProps = properties.getCaffeine();

        CaffeineCacheConfig config = CaffeineCacheConfig.builder()
                .keyPrefix(caffeineProps.getKeyPrefix())
                .maximumSize(caffeineProps.getMaximumSize())
                .expireAfterWrite(caffeineProps.getExpireAfterWrite())
                .expireAfterAccess(caffeineProps.getExpireAfterAccess())
                .refreshAfterWrite(caffeineProps.getRefreshAfterWrite())
                .recordStats(caffeineProps.isRecordStats())
                .weakKeys(caffeineProps.isWeakKeys())
                .weakValues(caffeineProps.isWeakValues())
                .softValues(caffeineProps.isSoftValues())
                .build();

        return new CaffeineCacheAdapter(caffeineProps.getCacheName(), config);
    }

}