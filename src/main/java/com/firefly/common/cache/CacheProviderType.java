package com.firefly.common.cache;

/**
 * Enumeration of supported cache provider types.
 *
 * @author Firefly Team
 * @since 1.0.0
 */
public enum CacheProviderType {
    
    /**
     * Caffeine - High-performance, in-memory cache.
     */
    CAFFEINE("caffeine", "In-memory cache with advanced eviction policies"),
    
    /**
     * Redis - Distributed cache with persistence capabilities.
     */
    REDIS("redis", "Distributed cache with persistence and pub/sub"),
    
    /**
     * Auto - Automatically select the best available provider.
     */
    AUTO("auto", "Automatically select best available cache provider");

    private final String name;
    private final String description;

    CacheProviderType(String name, String description) {
        this.name = name;
        this.description = description;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    @Override
    public String toString() {
        return name;
    }
}
