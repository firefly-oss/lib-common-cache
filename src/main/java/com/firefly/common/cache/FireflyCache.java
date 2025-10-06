package com.firefly.common.cache;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.Callable;

/**
 * Core cache interface for the Firefly caching abstraction.
 * This interface defines the contract for all cache implementations following hexagonal architecture principles.
 * 
 * <p>Implementations of this interface act as adapters to specific caching technologies
 * (Caffeine, Redis, etc.) while maintaining a unified API.</p>
 *
 * @author Firefly Team
 * @since 1.0.0
 */
public interface FireflyCache {

    /**
     * Returns the name of this cache.
     *
     * @return the cache name
     */
    String getName();

    /**
     * Returns the native cache provider.
     *
     * @return the underlying cache implementation
     */
    Object getNativeCache();

    /**
     * Retrieves a value from the cache.
     *
     * @param key the cache key
     * @return an Optional containing the value if present, or empty if not found
     */
    Optional<Object> get(Object key);

    /**
     * Retrieves a value from the cache, casting it to the specified type.
     *
     * @param <T>  the type of the cached value
     * @param key  the cache key
     * @param type the expected type of the value
     * @return an Optional containing the typed value if present and of correct type, or empty otherwise
     */
    <T> Optional<T> get(Object key, Class<T> type);

    /**
     * Retrieves a value from the cache, or computes and caches it if not present.
     *
     * @param <T>         the type of the value
     * @param key         the cache key
     * @param valueLoader a callable to compute the value if not cached
     * @return the cached or computed value
     */
    <T> T get(Object key, Callable<T> valueLoader);

    /**
     * Associates the specified value with the specified key in this cache.
     *
     * @param key   the cache key
     * @param value the value to cache
     */
    void put(Object key, Object value);

    /**
     * Associates the specified value with the specified key in this cache with a specific TTL.
     *
     * @param key      the cache key
     * @param value    the value to cache
     * @param duration the time-to-live for this entry
     */
    void put(Object key, Object value, Duration duration);

    /**
     * Atomically associates the specified value with the specified key if not already present.
     *
     * @param key   the cache key
     * @param value the value to cache
     * @return the existing value if present, or null if the value was cached
     */
    Object putIfAbsent(Object key, Object value);

    /**
     * Removes the mapping for a key from this cache if it is present.
     *
     * @param key the cache key
     */
    void evict(Object key);

    /**
     * Removes all mappings from this cache.
     */
    void clear();

    /**
     * Invalidates the cache (similar to clear but may have different semantics in distributed caches).
     */
    void invalidate();

    /**
     * Returns the cache provider type.
     *
     * @return the cache provider type
     */
    CacheProviderType getProviderType();

    /**
     * Returns statistics about this cache.
     *
     * @return cache statistics
     */
    CacheStats getStats();

    /**
     * Checks if a key exists in the cache.
     *
     * @param key the cache key
     * @return true if the key exists, false otherwise
     */
    boolean containsKey(Object key);

    /**
     * Returns the approximate number of entries in this cache.
     *
     * @return the estimated cache size
     */
    long size();
}
