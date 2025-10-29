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

    // No-arg constructor

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
     * Creates the CacheManagerFactory that can create multiple independent cache managers.
     * <p>
     * This factory is used by other modules (like lib-common-web, microservices, etc.) to create
     * their own cache managers with independent configurations and key prefixes.
     * <p>
     * This version is used when Redis dependencies are available on the classpath.
     */
    @Bean
    @ConditionalOnClass(name = "org.springframework.data.redis.connection.ReactiveRedisConnectionFactory")
    @ConditionalOnMissingBean
    public com.firefly.common.cache.factory.CacheManagerFactory cacheManagerFactoryWithRedis(
            CacheProperties properties,
            @Qualifier("cacheObjectMapper") ObjectMapper objectMapper,
            org.springframework.beans.factory.ObjectProvider<org.springframework.data.redis.connection.ReactiveRedisConnectionFactory> redisConnectionFactoryProvider) {
        log.info("Creating CacheManagerFactory (with Redis support)");
        org.springframework.data.redis.connection.ReactiveRedisConnectionFactory redisConnectionFactory =
                redisConnectionFactoryProvider.getIfAvailable();
        return new com.firefly.common.cache.factory.CacheManagerFactory(
                properties,
                objectMapper,
                redisConnectionFactory
        );
    }

    /**
     * Creates the CacheManagerFactory that can create multiple independent cache managers.
     * <p>
     * This version is used when Redis dependencies are NOT available, providing Caffeine-only support.
     */
    @Bean
    @ConditionalOnMissingBean
    public com.firefly.common.cache.factory.CacheManagerFactory cacheManagerFactoryCaffeineOnly(
            CacheProperties properties,
            @Qualifier("cacheObjectMapper") ObjectMapper objectMapper) {
        log.info("Creating CacheManagerFactory (Caffeine-only, Redis not available)");
        return new com.firefly.common.cache.factory.CacheManagerFactory(
                properties,
                objectMapper,
                null  // No Redis support
        );
    }

    // Default cache manager removed - applications should create specific caches as needed

}