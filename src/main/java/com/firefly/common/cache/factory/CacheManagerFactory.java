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
import com.firefly.common.cache.adapter.redis.RedisCacheAdapter;
import com.firefly.common.cache.adapter.redis.RedisCacheConfig;
import com.firefly.common.cache.core.CacheAdapter;
import com.firefly.common.cache.core.CacheType;
import com.firefly.common.cache.manager.FireflyCacheManager;
import com.firefly.common.cache.properties.CacheProperties;
import com.firefly.common.cache.serialization.CacheSerializer;
import com.firefly.common.cache.serialization.JsonCacheSerializer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

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
    private final ReactiveRedisConnectionFactory redisConnectionFactory;

    /**
     * Creates a new CacheManagerFactory.
     *
     * @param properties the global cache properties
     * @param objectMapper the object mapper for serialization
     * @param redisConnectionFactory optional Redis connection factory (can be null)
     */
    public CacheManagerFactory(CacheProperties properties,
                                ObjectMapper objectMapper,
                                ReactiveRedisConnectionFactory redisConnectionFactory) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.redisConnectionFactory = redisConnectionFactory;
        log.info("CacheManagerFactory initialized");
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
        // Auto-detect caller if not provided
        String caller = requestedBy != null ? requestedBy : detectCaller();

        log.info("");
        log.info("╔═══════════════════════════════════════════════════════════════════════════");
        log.info("║ CREATING NEW CACHE MANAGER");
        log.info("╠═══════════════════════════════════════════════════════════════════════════");
        log.info("║ Cache Name       : {}", cacheName);
        log.info("║ Description      : {}", description);
        log.info("║ Requested By     : {}", caller);
        log.info("║ Preferred Type   : {}", cacheType);
        log.info("║ Key Prefix       : {}", keyPrefix);
        log.info("║ Default TTL      : {}", defaultTtl);
        log.info("╚═══════════════════════════════════════════════════════════════════════════");

        CacheAdapter primaryCache;
        CacheAdapter fallbackCache = null;

        if (cacheType == CacheType.REDIS && redisConnectionFactory != null) {
            log.info("▶ Creating Redis cache as PRIMARY provider...");
            primaryCache = createRedisCacheAdapter(cacheName, keyPrefix, defaultTtl);
            log.info("  ✓ Redis cache created successfully");
            
            log.info("▶ Creating Caffeine cache as FALLBACK provider...");
            fallbackCache = createCaffeineCacheAdapter(cacheName, keyPrefix, defaultTtl);
            log.info("  ✓ Caffeine fallback created successfully");
        } else {
            if (cacheType == CacheType.REDIS && redisConnectionFactory == null) {
                log.warn("⚠ Redis requested but connection factory not available, falling back to Caffeine");
            }
            log.info("▶ Creating Caffeine cache as PRIMARY provider...");
            primaryCache = createCaffeineCacheAdapter(cacheName, keyPrefix, defaultTtl);
            log.info("  ✓ Caffeine cache created successfully");
        }

        log.info("");
        log.info("╔═══════════════════════════════════════════════════════════════════════════");
        log.info("║ CACHE MANAGER CREATED SUCCESSFULLY");
        log.info("╠═══════════════════════════════════════════════════════════════════════════");
        log.info("║ Primary Provider : {} ({})", primaryCache.getCacheType(), primaryCache.getCacheName());
        if (fallbackCache != null) {
            log.info("║ Fallback Provider: {} ({})", fallbackCache.getCacheType(), fallbackCache.getCacheName());
        } else {
            log.info("║ Fallback Provider: NONE");
        }
        log.info("║ Ready for use by : {}", caller);
        log.info("╚═══════════════════════════════════════════════════════════════════════════");
        log.info("");

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
     * Creates a Redis cache adapter.
     */
    private CacheAdapter createRedisCacheAdapter(String cacheName, String keyPrefix, Duration defaultTtl) {
        if (redisConnectionFactory == null) {
            throw new IllegalStateException("Redis connection factory is required for Redis cache");
        }

        log.debug("  → Configuring Redis adapter with prefix '{}'", keyPrefix);

        CacheProperties.RedisConfig redisProps = properties.getRedis();
        CacheSerializer serializer = new JsonCacheSerializer(objectMapper);

        // Create Redis template with custom key prefix
        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        GenericJackson2JsonRedisSerializer jsonSerializer = new GenericJackson2JsonRedisSerializer(objectMapper);

        RedisSerializationContext<String, Object> serializationContext = RedisSerializationContext
                .<String, Object>newSerializationContext()
                .key(stringSerializer)
                .value(jsonSerializer)
                .hashKey(stringSerializer)
                .hashValue(jsonSerializer)
                .build();

        ReactiveRedisTemplate<String, Object> redisTemplate =
                new ReactiveRedisTemplate<>(redisConnectionFactory, serializationContext);

        RedisCacheConfig config = RedisCacheConfig.builder()
                .host(redisProps.getHost())
                .port(redisProps.getPort())
                .database(redisProps.getDatabase())
                .password(redisProps.getPassword())
                .username(redisProps.getUsername())
                .connectionTimeout(redisProps.getConnectionTimeout())
                .commandTimeout(redisProps.getCommandTimeout())
                .keyPrefix(keyPrefix)
                .defaultTtl(defaultTtl != null ? defaultTtl : redisProps.getDefaultTtl())
                .enableKeyspaceNotifications(redisProps.isEnableKeyspaceNotifications())
                .maxPoolSize(redisProps.getMaxPoolSize())
                .minPoolSize(redisProps.getMinPoolSize())
                .ssl(redisProps.isSsl())
                .build();

        return new RedisCacheAdapter(cacheName, redisTemplate, redisConnectionFactory, config, serializer);
    }

    /**
     * Creates a cache manager using default configuration from properties.
     *
     * @param cacheName the name of the cache
     * @return a configured FireflyCacheManager
     */
    public FireflyCacheManager createDefaultCacheManager(String cacheName) {
        CacheType cacheType = properties.getDefaultCacheType();
        String keyPrefix = "firefly:cache:" + cacheName;
        Duration defaultTtl = cacheType == CacheType.REDIS
                ? properties.getRedis().getDefaultTtl()
                : properties.getCaffeine().getExpireAfterWrite();

        return createCacheManager(cacheName, cacheType, keyPrefix, defaultTtl);
    }
}
