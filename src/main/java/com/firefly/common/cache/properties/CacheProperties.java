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
 * Simplified configuration for a single cache with optional fallback support.
 * The cache type is selected based on availability and preference.
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
     * Preferred cache type to use.
     * <ul>
     *   <li>CAFFEINE - Use in-memory cache (always available)</li>
     *   <li>REDIS - Use distributed cache (requires Redis dependencies)</li>
     *   <li>AUTO - Automatically select based on availability (Redis preferred if available)</li>
     * </ul>
     * Defaults to CAFFEINE for optimal performance and reliability.
     */
    @NotNull(message = "Default cache type cannot be null")
    private CacheType defaultCacheType = CacheType.CAFFEINE;

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
     * Caffeine cache configuration.
     */
    @Valid
    @NotNull
    private CaffeineConfig caffeine = new CaffeineConfig();

    /**
     * Redis cache configuration.
     */
    @Valid
    @NotNull
    private RedisConfig redis = new RedisConfig();

    /**
     * Caffeine cache configuration.
     */
    @Data
    public static class CaffeineConfig {
        /**
         * Cache name for the Caffeine adapter.
         */
        private String cacheName = "default";


        /**
         * Whether Caffeine cache is enabled.
         */
        private boolean enabled = true;

        /**
         * Key prefix for all cache entries.
         */
        private String keyPrefix = "firefly:cache";

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

    /**
     * Redis cache configuration.
     */
    @Data
    public static class RedisConfig {
        /**
         * Cache name for the Redis adapter.
         */
        private String cacheName = "default";

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
}