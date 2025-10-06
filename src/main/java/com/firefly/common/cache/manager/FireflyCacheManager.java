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

package com.firefly.common.cache.manager;

import com.firefly.common.cache.core.CacheAdapter;
import com.firefly.common.cache.core.CacheHealth;
import com.firefly.common.cache.core.CacheStats;
import com.firefly.common.cache.core.CacheType;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Main cache manager that orchestrates cache operations across multiple cache adapters.
 * <p>
 * This manager provides a unified interface for cache operations while managing
 * multiple cache instances. It supports cache selection strategies, health monitoring,
 * and comprehensive statistics aggregation.
 * <p>
 * Features:
 * <ul>
 *   <li>Multi-cache management</li>
 *   <li>Automatic cache selection</li>
 *   <li>Health monitoring and failover</li>
 *   <li>Statistics aggregation</li>
 *   <li>Cache lifecycle management</li>
 * </ul>
 */
@Slf4j
public class FireflyCacheManager {

    private final Map<String, CacheAdapter> caches;
    private final CacheSelectionStrategy selectionStrategy;
    private final String defaultCacheName;
    private volatile boolean closed = false;

    public FireflyCacheManager() {
        this(new AutoCacheSelectionStrategy(), "default");
    }

    public FireflyCacheManager(CacheSelectionStrategy selectionStrategy, String defaultCacheName) {
        this.caches = new ConcurrentHashMap<>();
        this.selectionStrategy = selectionStrategy;
        this.defaultCacheName = defaultCacheName;
        log.info("Created Firefly Cache Manager with strategy: {}, default cache: {}", 
                selectionStrategy.getClass().getSimpleName(), defaultCacheName);
    }

    /**
     * Registers a cache adapter with the manager.
     *
     * @param name the cache name
     * @param adapter the cache adapter
     */
    public void registerCache(String name, CacheAdapter adapter) {
        if (closed) {
            throw new IllegalStateException("Cache manager is closed");
        }
        
        caches.put(name, adapter);
        log.info("Registered cache '{}' of type {}", name, adapter.getCacheType());
    }

    /**
     * Unregisters a cache adapter from the manager.
     *
     * @param name the cache name
     * @return the removed cache adapter, or null if not found
     */
    public CacheAdapter unregisterCache(String name) {
        CacheAdapter removed = caches.remove(name);
        if (removed != null) {
            log.info("Unregistered cache '{}' of type {}", name, removed.getCacheType());
            try {
                removed.close();
            } catch (Exception e) {
                log.warn("Error closing unregistered cache '{}': {}", name, e.getMessage());
            }
        }
        return removed;
    }

    /**
     * Gets a cache adapter by name.
     *
     * @param name the cache name
     * @return the cache adapter, or null if not found
     */
    public CacheAdapter getCache(String name) {
        return caches.get(name);
    }

    /**
     * Gets the default cache adapter based on the selection strategy.
     *
     * @return the selected cache adapter
     * @throws IllegalStateException if no cache is available
     */
    public CacheAdapter getDefaultCache() {
        if (caches.isEmpty()) {
            throw new IllegalStateException("No caches registered");
        }
        
        return selectionStrategy.selectCache(caches.values())
                .orElseThrow(() -> new IllegalStateException("No available cache found"));
    }

    /**
     * Gets all registered cache names.
     *
     * @return set of cache names
     */
    public Set<String> getCacheNames() {
        return new HashSet<>(caches.keySet());
    }

    /**
     * Checks if a cache with the given name is registered.
     *
     * @param name the cache name
     * @return true if the cache exists
     */
    public boolean hasCache(String name) {
        return caches.containsKey(name);
    }

    /**
     * Gets the number of registered caches.
     *
     * @return the cache count
     */
    public int getCacheCount() {
        return caches.size();
    }

    // Convenience methods for cache operations using default cache

    /**
     * Retrieves a value from the default cache.
     *
     * @param key the cache key
     * @param <K> the key type
     * @param <V> the value type
     * @return a Mono containing the value if present
     */
    public <K, V> Mono<Optional<V>> get(K key) {
        return getDefaultCache().get(key);
    }

    /**
     * Retrieves a value from the default cache with type checking.
     *
     * @param key the cache key
     * @param valueType the expected value type
     * @param <K> the key type
     * @param <V> the value type
     * @return a Mono containing the value if present and of correct type
     */
    public <K, V> Mono<Optional<V>> get(K key, Class<V> valueType) {
        return getDefaultCache().get(key, valueType);
    }

    /**
     * Stores a value in the default cache.
     *
     * @param key the cache key
     * @param value the value to store
     * @param <K> the key type
     * @param <V> the value type
     * @return a Mono that completes when the value is stored
     */
    public <K, V> Mono<Void> put(K key, V value) {
        return getDefaultCache().put(key, value);
    }

    /**
     * Stores a value in the default cache with TTL.
     *
     * @param key the cache key
     * @param value the value to store
     * @param ttl the time-to-live
     * @param <K> the key type
     * @param <V> the value type
     * @return a Mono that completes when the value is stored
     */
    public <K, V> Mono<Void> put(K key, V value, Duration ttl) {
        return getDefaultCache().put(key, value, ttl);
    }

    /**
     * Stores a value in the default cache only if absent.
     *
     * @param key the cache key
     * @param value the value to store
     * @param <K> the key type
     * @param <V> the value type
     * @return a Mono containing true if the value was stored
     */
    public <K, V> Mono<Boolean> putIfAbsent(K key, V value) {
        return getDefaultCache().putIfAbsent(key, value);
    }

    /**
     * Stores a value in the default cache only if absent, with TTL.
     *
     * @param key the cache key
     * @param value the value to store
     * @param ttl the time-to-live
     * @param <K> the key type
     * @param <V> the value type
     * @return a Mono containing true if the value was stored
     */
    public <K, V> Mono<Boolean> putIfAbsent(K key, V value, Duration ttl) {
        return getDefaultCache().putIfAbsent(key, value, ttl);
    }

    /**
     * Removes a value from the default cache.
     *
     * @param key the cache key
     * @param <K> the key type
     * @return a Mono containing true if the key was removed
     */
    public <K> Mono<Boolean> evict(K key) {
        return getDefaultCache().evict(key);
    }

    /**
     * Clears the default cache.
     *
     * @return a Mono that completes when the cache is cleared
     */
    public Mono<Void> clear() {
        return getDefaultCache().clear();
    }

    /**
     * Checks if a key exists in the default cache.
     *
     * @param key the cache key
     * @param <K> the key type
     * @return a Mono containing true if the key exists
     */
    public <K> Mono<Boolean> exists(K key) {
        return getDefaultCache().exists(key);
    }

    // Cache-specific operations

    /**
     * Retrieves a value from a specific cache.
     *
     * @param cacheName the cache name
     * @param key the cache key
     * @param <K> the key type
     * @param <V> the value type
     * @return a Mono containing the value if present
     */
    public <K, V> Mono<Optional<V>> get(String cacheName, K key) {
        CacheAdapter cache = getCache(cacheName);
        if (cache == null) {
            return Mono.just(Optional.empty());
        }
        return cache.get(key);
    }

    /**
     * Stores a value in a specific cache.
     *
     * @param cacheName the cache name
     * @param key the cache key
     * @param value the value to store
     * @param <K> the key type
     * @param <V> the value type
     * @return a Mono that completes when the value is stored
     */
    public <K, V> Mono<Void> put(String cacheName, K key, V value) {
        CacheAdapter cache = getCache(cacheName);
        if (cache == null) {
            return Mono.error(new IllegalArgumentException("Cache not found: " + cacheName));
        }
        return cache.put(key, value);
    }

    /**
     * Removes a value from a specific cache.
     *
     * @param cacheName the cache name
     * @param key the cache key
     * @param <K> the key type
     * @return a Mono containing true if the key was removed
     */
    public <K> Mono<Boolean> evict(String cacheName, K key) {
        CacheAdapter cache = getCache(cacheName);
        if (cache == null) {
            return Mono.just(false);
        }
        return cache.evict(key);
    }

    /**
     * Clears a specific cache.
     *
     * @param cacheName the cache name
     * @return a Mono that completes when the cache is cleared
     */
    public Mono<Void> clear(String cacheName) {
        CacheAdapter cache = getCache(cacheName);
        if (cache == null) {
            return Mono.empty();
        }
        return cache.clear();
    }

    // Health and monitoring

    /**
     * Gets health information for all registered caches.
     *
     * @return a Flux of cache health information
     */
    public Flux<CacheHealth> getHealth() {
        return Flux.fromIterable(caches.values())
                .flatMap(CacheAdapter::getHealth)
                .onErrorContinue((error, item) -> 
                    log.error("Error getting health for cache: {}", error.getMessage()));
    }

    /**
     * Gets health information for a specific cache.
     *
     * @param cacheName the cache name
     * @return a Mono containing cache health information
     */
    public Mono<CacheHealth> getHealth(String cacheName) {
        CacheAdapter cache = getCache(cacheName);
        if (cache == null) {
            return Mono.just(CacheHealth.notConfigured(CacheType.NOOP, cacheName));
        }
        return cache.getHealth();
    }

    /**
     * Gets statistics for all registered caches.
     *
     * @return a Flux of cache statistics
     */
    public Flux<CacheStats> getStats() {
        return Flux.fromIterable(caches.values())
                .flatMap(CacheAdapter::getStats)
                .onErrorContinue((error, item) -> 
                    log.error("Error getting stats for cache: {}", error.getMessage()));
    }

    /**
     * Gets statistics for a specific cache.
     *
     * @param cacheName the cache name
     * @return a Mono containing cache statistics
     */
    public Mono<CacheStats> getStats(String cacheName) {
        CacheAdapter cache = getCache(cacheName);
        if (cache == null) {
            return Mono.just(CacheStats.empty(CacheType.NOOP, cacheName));
        }
        return cache.getStats();
    }

    /**
     * Gets the cache selection strategy.
     *
     * @return the selection strategy
     */
    public CacheSelectionStrategy getSelectionStrategy() {
        return selectionStrategy;
    }

    /**
     * Gets the default cache name.
     *
     * @return the default cache name
     */
    public String getDefaultCacheName() {
        return defaultCacheName;
    }

    /**
     * Checks if the cache manager is closed.
     *
     * @return true if closed
     */
    public boolean isClosed() {
        return closed;
    }

    /**
     * Closes the cache manager and all registered caches.
     */
    public void close() {
        if (closed) {
            return;
        }
        
        closed = true;
        log.info("Closing Firefly Cache Manager with {} caches", caches.size());
        
        for (Map.Entry<String, CacheAdapter> entry : caches.entrySet()) {
            try {
                entry.getValue().close();
                log.debug("Closed cache '{}'", entry.getKey());
            } catch (Exception e) {
                log.error("Error closing cache '{}': {}", entry.getKey(), e.getMessage(), e);
            }
        }
        
        caches.clear();
        log.info("Firefly Cache Manager closed");
    }
}