package com.firefly.common.cache;

/**
 * Immutable statistics about a cache.
 *
 * @param hitCount        the number of cache hits
 * @param missCount       the number of cache misses
 * @param loadSuccessCount the number of successful cache loads
 * @param loadFailureCount the number of failed cache loads
 * @param evictionCount   the number of cache evictions
 * @param size            the current cache size
 *
 * @author Firefly Team
 * @since 1.0.0
 */
public record CacheStats(
    long hitCount,
    long missCount,
    long loadSuccessCount,
    long loadFailureCount,
    long evictionCount,
    long size
) {
    
    /**
     * Calculates the hit rate.
     *
     * @return the hit rate (0.0 to 1.0), or 0.0 if no requests
     */
    public double hitRate() {
        long totalRequests = hitCount + missCount;
        return totalRequests == 0 ? 0.0 : (double) hitCount / totalRequests;
    }

    /**
     * Calculates the miss rate.
     *
     * @return the miss rate (0.0 to 1.0), or 0.0 if no requests
     */
    public double missRate() {
        long totalRequests = hitCount + missCount;
        return totalRequests == 0 ? 0.0 : (double) missCount / totalRequests;
    }

    /**
     * Returns the total number of requests (hits + misses).
     *
     * @return the total request count
     */
    public long requestCount() {
        return hitCount + missCount;
    }

    /**
     * Creates an empty statistics object.
     *
     * @return empty cache statistics
     */
    public static CacheStats empty() {
        return new CacheStats(0, 0, 0, 0, 0, 0);
    }
}
