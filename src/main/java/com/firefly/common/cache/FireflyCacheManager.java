package com.firefly.common.cache;

import java.util.Collection;
import java.util.Optional;

/**
 * Central interface for managing multiple caches.
 * This is the main entry point for interacting with the Firefly cache abstraction.
 *
 * <p>Following hexagonal architecture principles, this interface defines the port
 * through which the application interacts with caching functionality.</p>
 *
 * @author Firefly Team
 * @since 1.0.0
 */
public interface FireflyCacheManager {

    /**
     * Retrieves a cache by name, or creates it if it doesn't exist.
     *
     * @param name the cache name
     * @return the cache instance
     */
    FireflyCache getCache(String name);

    /**
     * Retrieves a cache by name if it exists.
     *
     * @param name the cache name
     * @return an Optional containing the cache if it exists
     */
    Optional<FireflyCache> getCacheIfPresent(String name);

    /**
     * Returns all cache names known to this manager.
     *
     * @return a collection of cache names
     */
    Collection<String> getCacheNames();

    /**
     * Returns all caches managed by this manager.
     *
     * @return a collection of caches
     */
    Collection<FireflyCache> getAllCaches();

    /**
     * Clears all caches managed by this manager.
     */
    void clearAll();

    /**
     * Returns the default cache provider type used by this manager.
     *
     * @return the provider type
     */
    CacheProviderType getProviderType();

    /**
     * Checks if the cache manager is enabled.
     *
     * @return true if enabled, false otherwise
     */
    boolean isEnabled();

    /**
     * Invalidates all caches and resets the cache manager.
     */
    void invalidateAll();
}
