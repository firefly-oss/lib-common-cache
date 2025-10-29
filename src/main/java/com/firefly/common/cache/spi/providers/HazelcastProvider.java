package com.firefly.common.cache.spi.providers;

import com.firefly.common.cache.core.CacheAdapter;
import com.firefly.common.cache.core.CacheType;
import com.firefly.common.cache.factory.HazelcastCacheHelper;
import com.firefly.common.cache.spi.CacheProviderFactory;

import java.time.Duration;

public class HazelcastProvider implements CacheProviderFactory {
    @Override public CacheType getType() { return CacheType.HAZELCAST; }
    @Override public int priority() { return 20; }

    @Override
    public boolean isAvailable(ProviderContext ctx) {
        try {
            Class.forName("com.hazelcast.core.HazelcastInstance");
            return ctx.hazelcastInstance != null;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    @Override
    public CacheAdapter create(String cacheName, String keyPrefix, Duration defaultTtl, ProviderContext ctx) {
        try {
            Class<?> helperClass = Class.forName("com.firefly.common.cache.factory.HazelcastCacheHelper");
            Class<?> hz = Class.forName("com.hazelcast.core.HazelcastInstance");
            var m = helperClass.getMethod("createHazelcastCacheAdapter",
                    String.class, String.class, Duration.class, hz);
            return (CacheAdapter) m.invoke(null, cacheName, keyPrefix, defaultTtl, ctx.hazelcastInstance);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to create Hazelcast cache via SPI", e);
        }
    }
}
