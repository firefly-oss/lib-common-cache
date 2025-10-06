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

package com.firefly.common.cache.properties;

import com.firefly.common.cache.core.CacheType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Configuration properties for the Cache library.
 * <p>
 * This class centralizes all configuration for the cache library, including
 * cache type selection, adapter configurations, and global settings.
 */
@ConfigurationProperties(prefix = "firefly.cache")
@Validated
@Data
public class CacheProperties {

    /**
     * Whether the cache library is enabled.
     */
    private boolean enabled = true;

    /**
     * Default cache type to use when none is specified.
     * Defaults to CAFFEINE for optimal performance and reliability.
     */
    @NotNull(message = "Default cache type cannot be null")
    private CacheType defaultCacheType = CacheType.CAFFEINE;

    /**
     * Default cache name for operations when none is specified.
     */
    @NotBlank(message = "Default cache name cannot be blank")
    @Size(max = 100, message = "Default cache name must not exceed 100 characters")
    private String defaultCacheName = "default";

    /**
     * Default serialization format.
     */
    @NotNull(message = "Default serialization format cannot be null")
    private String defaultSerializationFormat = "json";

    /**
     * Default timeout for cache operations.
     */
    @NotNull(message = "Default timeout cannot be null")
    private Duration defaultTimeout = Duration.ofSeconds(5);

    /**
     * Whether to enable metrics collection.
     */
    private boolean metricsEnabled = true;

    /**
     * Whether to enable health checks.
     */
    private boolean healthEnabled = true;

    /**
     * Whether to enable cache statistics.
     */
    private boolean statsEnabled = true;

    /**
     * Cache configurations by name.
     */
    @Valid
    private final Map<String, CacheConfig> caches = new HashMap<>();

    /**
     * Caffeine cache configurations by name.
     */
    @Valid
    private final Map<String, CaffeineConfig> caffeine = new HashMap<>();

    /**
     * Redis cache configurations by name.
     */
    @Valid
    private final Map<String, RedisConfig> redis = new HashMap<>();

    // Initialize default configurations
    public CacheProperties() {
        caches.put("default", new CacheConfig());
        caffeine.put("default", new CaffeineConfig());
        redis.put("default", new RedisConfig());
    }

    @Data
    public static class CacheConfig {
        /**
         * Cache type for this specific cache.
         * Defaults to CAFFEINE for optimal performance.
         */
        private CacheType type = CacheType.CAFFEINE;

        /**
         * Default TTL for cache entries.
         */
        private Duration defaultTtl;

        /**
         * Whether this cache is enabled.
         */
        private boolean enabled = true;

        /**
         * Serialization format for this cache.
         */
        private String serializationFormat = "json";
    }

    @Data
    public static class CaffeineConfig {
        /**
         * Whether Caffeine cache is enabled.
         */
        private boolean enabled = true;

        /**
         * Maximum number of entries the cache may contain.
         */
        private Long maximumSize = 1000L;

        /**
         * Duration after which entries should be automatically removed
         * after the entry's creation or replacement.
         */
        private Duration expireAfterWrite = Duration.ofHours(1);

        /**
         * Duration after which entries should be automatically removed
         * after the last access.
         */
        private Duration expireAfterAccess;

        /**
         * Duration after which entries are eligible for automatic refresh.
         */
        private Duration refreshAfterWrite;

        /**
         * Whether to record cache statistics.
         */
        private boolean recordStats = true;

        /**
         * Whether to use weak references for keys.
         */
        private boolean weakKeys = false;

        /**
         * Whether to use weak references for values.
         */
        private boolean weakValues = false;

        /**
         * Whether to use soft references for values.
         */
        private boolean softValues = false;
    }

    @Data
    public static class RedisConfig {
        /**
         * Whether Redis cache is enabled.
         */
        private boolean enabled = true;

        /**
         * Redis server host.
         */
        private String host = "localhost";

        /**
         * Redis server port.
         */
        private int port = 6379;

        /**
         * Redis database index.
         */
        private int database = 0;

        /**
         * Redis authentication password.
         */
        private String password;

        /**
         * Redis username for ACL authentication.
         */
        private String username;

        /**
         * Connection timeout.
         */
        private Duration connectionTimeout = Duration.ofSeconds(10);

        /**
         * Command timeout.
         */
        private Duration commandTimeout = Duration.ofSeconds(5);

        /**
         * Key prefix for all cache entries.
         */
        private String keyPrefix = "firefly:cache";

        /**
         * Default TTL for cache entries.
         */
        private Duration defaultTtl;

        /**
         * Whether to enable key expiration events.
         */
        private boolean enableKeyspaceNotifications = false;

        /**
         * Maximum number of connections in the pool.
         */
        private int maxPoolSize = 8;

        /**
         * Minimum number of connections in the pool.
         */
        private int minPoolSize = 0;

        /**
         * Whether to use SSL/TLS for connection.
         */
        private boolean ssl = false;

        /**
         * Additional Redis configuration properties.
         */
        private Map<String, String> properties = new HashMap<>();
    }

    /**
     * Gets the cache configuration for a specific cache name.
     *
     * @param cacheName the cache name
     * @return the cache configuration
     */
    public CacheConfig getCacheConfig(String cacheName) {
        return caches.getOrDefault(cacheName, caches.get("default"));
    }

    /**
     * Gets the Caffeine configuration for a specific cache name.
     *
     * @param cacheName the cache name
     * @return the Caffeine configuration
     */
    public CaffeineConfig getCaffeineConfig(String cacheName) {
        return caffeine.getOrDefault(cacheName, caffeine.get("default"));
    }

    /**
     * Gets the Redis configuration for a specific cache name.
     *
     * @param cacheName the cache name
     * @return the Redis configuration
     */
    public RedisConfig getRedisConfig(String cacheName) {
        return redis.getOrDefault(cacheName, redis.get("default"));
    }
}