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
import com.firefly.common.cache.manager.AutoCacheSelectionStrategy;
import com.firefly.common.cache.manager.CacheSelectionStrategy;
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
     * Provides a default cache selection strategy.
     */
    @Bean
    @ConditionalOnMissingBean
    public CacheSelectionStrategy cacheSelectionStrategy() {
        log.debug("Creating auto cache selection strategy");
        return new AutoCacheSelectionStrategy();
    }

    /**
     * Creates the main Firefly cache manager.
     */
    @Bean
    @Primary
    public FireflyCacheManager fireflyCacheManager(CacheSelectionStrategy selectionStrategy,
                                                   CacheProperties properties,
                                                   java.util.List<CacheAdapter> cacheAdapters) {
        log.info("Creating Firefly Cache Manager");
        log.info("   • Default cache name: {}", properties.getDefaultCacheName());
        log.info("   • Default cache type: {}", properties.getDefaultCacheType());

        FireflyCacheManager manager = new FireflyCacheManager(selectionStrategy, properties.getDefaultCacheName());

        // Register all discovered cache adapters
        for (CacheAdapter adapter : cacheAdapters) {
            log.info("   • Registering cache adapter: {} (type: {})",
                adapter.getCacheName(), adapter.getCacheType());
            manager.registerCache(adapter.getCacheName(), adapter);
        }

        log.info("Firefly Cache Manager initialized with {} cache adapter(s)", cacheAdapters.size());
        return manager;
    }

    // ================================
    // Caffeine Cache Configuration
    // ================================

    /**
     * Creates a Caffeine cache adapter when Caffeine is available and enabled.
     */
    @Bean
    @ConditionalOnClass(name = "com.github.benmanes.caffeine.cache.Caffeine")
    @ConditionalOnProperty(prefix = "firefly.cache.caffeine.default", name = "enabled", havingValue = "true", matchIfMissing = true)
    public CacheAdapter caffeineCacheAdapter(CacheProperties properties) {
        log.debug("Creating Caffeine cache adapter");
        CacheProperties.CaffeineConfig caffeineProps = properties.getCaffeineConfig("default");

        CaffeineCacheConfig config = CaffeineCacheConfig.builder()
                .maximumSize(caffeineProps.getMaximumSize())
                .expireAfterWrite(caffeineProps.getExpireAfterWrite())
                .expireAfterAccess(caffeineProps.getExpireAfterAccess())
                .refreshAfterWrite(caffeineProps.getRefreshAfterWrite())
                .recordStats(caffeineProps.isRecordStats())
                .weakKeys(caffeineProps.isWeakKeys())
                .weakValues(caffeineProps.isWeakValues())
                .softValues(caffeineProps.isSoftValues())
                .build();

        return new CaffeineCacheAdapter("default", config);
    }

}