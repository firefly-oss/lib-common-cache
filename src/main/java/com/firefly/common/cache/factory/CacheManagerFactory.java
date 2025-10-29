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

package com.firefly.common.cache.factory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.firefly.common.cache.adapter.caffeine.CaffeineCacheAdapter;
import com.firefly.common.cache.adapter.caffeine.CaffeineCacheConfig;
import com.firefly.common.cache.core.CacheAdapter;
import com.firefly.common.cache.core.CacheType;
import com.firefly.common.cache.manager.FireflyCacheManager;
import com.firefly.common.cache.properties.CacheProperties;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Method;
import java.time.Duration;

/**
 * Factory for creating multiple independent CacheManager instances.
 * <p>
 * This factory allows creating cache managers with different configurations,
 * enabling multiple cache contexts within the same application without conflicts.
 * Each cache manager has its own key prefix and configuration.
 * <p>
 * <b>Example Usage:</b>
 * <pre>
 * // Create HTTP idempotency cache
 * FireflyCacheManager httpIdempotencyCache = factory.createCacheManager(
 *     "http-idempotency",
 *     CacheType.REDIS,
 *     "firefly:http:idempotency",
 *     Duration.ofHours(24)
 * );
 *
 * // Create webhook event cache
 * FireflyCacheManager webhookCache = factory.createCacheManager(
 *     "webhook-events",
 *     CacheType.REDIS,
 *     "firefly:webhooks:events",
 *     Duration.ofDays(7)
 * );
 * </pre>
 */
@Slf4j
public class CacheManagerFactory {

    private final CacheProperties properties;
    private final ObjectMapper objectMapper;
    private final Object redisConnectionFactory; // Use Object to avoid loading Redis classes
    private final boolean redisAvailable;

    /**
     * Creates a new CacheManagerFactory.
     *
     * @param properties the global cache properties
     * @param objectMapper the object mapper for serialization
     * @param redisConnectionFactory optional Redis connection factory (can be null)
     */
    public CacheManagerFactory(CacheProperties properties,
                                ObjectMapper objectMapper,
                                Object redisConnectionFactory) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.redisConnectionFactory = redisConnectionFactory;
        this.redisAvailable = checkRedisAvailable();
        log.info("CacheManagerFactory initialized (Redis available: {})", redisAvailable);
    }

    /**
     * Checks if Redis classes are available on the classpath.
     */
    private boolean checkRedisAvailable() {
        try {
            Class.forName("org.springframework.data.redis.connection.ReactiveRedisConnectionFactory");
            Class.forName("com.firefly.common.cache.factory.RedisCacheHelper");
            return redisConnectionFactory != null;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /**
     * Creates a new cache manager with custom configuration.
     *
     * @param cacheName the name of the cache
     * @param cacheType the type of cache (CAFFEINE or REDIS)
     * @param keyPrefix the key prefix for this cache
     * @param defaultTtl the default TTL for cache entries
     * @return a configured FireflyCacheManager
     */
    public FireflyCacheManager createCacheManager(String cacheName,
                                                   CacheType cacheType,
                                                   String keyPrefix,
                                                   Duration defaultTtl) {
        return createCacheManager(cacheName, cacheType, keyPrefix, defaultTtl, "General purpose cache", null);
    }

    /**
     * Creates a new cache manager with custom configuration and tracking information.
     *
     * @param cacheName the name of the cache
     * @param cacheType the type of cache (CAFFEINE or REDIS)
     * @param keyPrefix the key prefix for this cache
     * @param defaultTtl the default TTL for cache entries
     * @param description a description of what this cache is used for
     * @param requestedBy the component/module requesting this cache (optional, will be auto-detected)
     * @return a configured FireflyCacheManager
     */
    public FireflyCacheManager createCacheManager(String cacheName,
                                                   CacheType cacheType,
                                                   String keyPrefix,
                                                   Duration defaultTtl,
                                                   String description,
                                                   String requestedBy) {
        // Check what's available and enabled
        boolean caffeineEnabled = properties.getCaffeine().isEnabled();
        boolean redisEnabled = properties.getRedis().isEnabled() && redisAvailable;
        
        // Resolve AUTO to actual type based on availability and enabled flags
        CacheType resolvedType = cacheType;
        if (cacheType == CacheType.AUTO) {
            if (redisEnabled) {
                resolvedType = CacheType.REDIS;
            } else if (caffeineEnabled) {
                resolvedType = CacheType.CAFFEINE;
            } else {
                throw new IllegalStateException("No cache adapters available. At least one cache type must be enabled.");
            }
        }

        CacheAdapter primaryCache;
        CacheAdapter fallbackCache = null;

        // Create cache based on resolved type
        if (resolvedType == CacheType.REDIS && redisEnabled) {
            primaryCache = createRedisCacheAdapterViaReflection(cacheName, keyPrefix, defaultTtl);
            // Only create fallback for distributed caches to handle Redis failures (if Caffeine is enabled)
            if (caffeineEnabled) {
                fallbackCache = createCaffeineCacheAdapter(cacheName, keyPrefix, defaultTtl);
            }
            log.info("Cache '{}' created: {} {} (TTL: {})", cacheName, resolvedType, 
                    fallbackCache != null ? "+ Caffeine fallback" : "(no fallback)", defaultTtl);
        } else if (resolvedType == CacheType.CAFFEINE && caffeineEnabled) {
            primaryCache = createCaffeineCacheAdapter(cacheName, keyPrefix, defaultTtl);
            log.info("Cache '{}' created: {} (TTL: {})", cacheName, resolvedType, defaultTtl);
        } else {
            throw new IllegalStateException(
                String.format("Cannot create cache of type %s: %s",
                    resolvedType,
                    resolvedType == CacheType.REDIS ? "Redis is not available or not enabled" : "Caffeine is not enabled")
            );
        }

        return new FireflyCacheManager(primaryCache, fallbackCache);
    }

    /**
     * Detects the caller class from the stack trace.
     */
    private String detectCaller() {
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        // Skip: getStackTrace, detectCaller, createCacheManager (x2), and factory method
        for (int i = 5; i < Math.min(stackTrace.length, 10); i++) {
            String className = stackTrace[i].getClassName();
            // Skip internal factory classes
            if (!className.contains("CacheManagerFactory") && 
                !className.contains("CacheAutoConfiguration") &&
                !className.contains("java.lang.reflect") &&
                !className.contains("org.springframework")) {
                return className + "." + stackTrace[i].getMethodName() + "()";
            }
        }
        return "Unknown caller";
    }

    /**
     * Creates a Caffeine cache adapter.
     */
    private CacheAdapter createCaffeineCacheAdapter(String cacheName, String keyPrefix, Duration defaultTtl) {
        log.debug("  → Configuring Caffeine adapter with prefix '{}'", keyPrefix);

        CacheProperties.CaffeineConfig caffeineProps = properties.getCaffeine();

        CaffeineCacheConfig config = CaffeineCacheConfig.builder()
                .keyPrefix(keyPrefix)
                .maximumSize(caffeineProps.getMaximumSize())
                .expireAfterWrite(defaultTtl != null ? defaultTtl : caffeineProps.getExpireAfterWrite())
                .expireAfterAccess(caffeineProps.getExpireAfterAccess())
                .refreshAfterWrite(caffeineProps.getRefreshAfterWrite())
                .recordStats(caffeineProps.isRecordStats())
                .weakKeys(caffeineProps.isWeakKeys())
                .weakValues(caffeineProps.isWeakValues())
                .softValues(caffeineProps.isSoftValues())
                .build();

        return new CaffeineCacheAdapter(cacheName, config);
    }

    /**
     * Creates a Redis cache adapter using reflection to avoid loading Redis classes.
     * <p>
     * This approach ensures that Redis classes are only loaded when Redis is actually available.
     */
    private CacheAdapter createRedisCacheAdapterViaReflection(String cacheName, String keyPrefix, Duration defaultTtl) {
        try {
            log.debug("  → Configuring Redis adapter with prefix '{}' (via reflection)", keyPrefix);
            
            // Load RedisCacheHelper class dynamically
            Class<?> helperClass = Class.forName("com.firefly.common.cache.factory.RedisCacheHelper");
            Method createMethod = helperClass.getMethod(
                "createRedisCacheAdapter",
                String.class,
                String.class,
                Duration.class,
                Class.forName("org.springframework.data.redis.connection.ReactiveRedisConnectionFactory"),
                CacheProperties.class,
                ObjectMapper.class
            );
            
            // Invoke the static method
            return (CacheAdapter) createMethod.invoke(
                null,
                cacheName,
                keyPrefix,
                defaultTtl,
                redisConnectionFactory,
                properties,
                objectMapper
            );
        } catch (Exception e) {
            log.error("Failed to create Redis cache adapter via reflection", e);
            throw new IllegalStateException("Failed to create Redis cache: " + e.getMessage(), e);
        }
    }

    /**
     * Creates a cache manager using default configuration from properties.
     *
     * @param cacheName the name of the cache
     * @return a configured FireflyCacheManager
     */
    public FireflyCacheManager createDefaultCacheManager(String cacheName) {
        // Validate that at least one cache type is available and enabled
        boolean caffeineEnabled = properties.getCaffeine().isEnabled();
        boolean redisEnabled = properties.getRedis().isEnabled() && redisAvailable;
        
        if (!caffeineEnabled && !redisEnabled) {
            throw new IllegalStateException(
                "No cache adapters available. At least one cache type (Caffeine or Redis) must be enabled."
            );
        }
        
        CacheType cacheType = properties.getDefaultCacheType();
        
        // Determine the actual cache name from properties
        String actualCacheName = cacheName;
        if (cacheType == CacheType.CAFFEINE || (cacheType == CacheType.AUTO && !redisEnabled)) {
            // Use Caffeine cache name if available and not default
            String caffeineCacheName = properties.getCaffeine().getCacheName();
            if (caffeineCacheName != null && !"default".equals(caffeineCacheName)) {
                actualCacheName = caffeineCacheName;
            }
        } else if (cacheType == CacheType.REDIS || (cacheType == CacheType.AUTO && redisEnabled)) {
            // Use Redis cache name if available and not default
            String redisCacheName = properties.getRedis().getCacheName();
            if (redisCacheName != null && !"default".equals(redisCacheName)) {
                actualCacheName = redisCacheName;
            }
        }
        
        String keyPrefix = "firefly:cache:" + actualCacheName;
        Duration defaultTtl = cacheType == CacheType.REDIS
                ? properties.getRedis().getDefaultTtl()
                : properties.getCaffeine().getExpireAfterWrite();

        return createCacheManager(actualCacheName, cacheType, keyPrefix, defaultTtl);
    }
}
