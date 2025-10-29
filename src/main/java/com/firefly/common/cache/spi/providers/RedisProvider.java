package com.firefly.common.cache.spi.providers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.firefly.common.cache.core.CacheAdapter;
import com.firefly.common.cache.core.CacheType;
import com.firefly.common.cache.factory.RedisCacheHelper;
import com.firefly.common.cache.spi.CacheProviderFactory;

import java.time.Duration;

public class RedisProvider implements CacheProviderFactory {
    @Override public CacheType getType() { return CacheType.REDIS; }
    @Override public int priority() { return 10; }

    @Override
    public boolean isAvailable(ProviderContext ctx) {
        try {
            Class.forName("org.springframework.data.redis.connection.ReactiveRedisConnectionFactory");
            return ctx.redisConnectionFactory != null && ctx.properties.getRedis().isEnabled();
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    @Override
    public CacheAdapter create(String cacheName, String keyPrefix, Duration defaultTtl, ProviderContext ctx) {
        try {
            Class<?> helperClass = Class.forName("com.firefly.common.cache.factory.RedisCacheHelper");
            Class<?> rrCf = Class.forName("org.springframework.data.redis.connection.ReactiveRedisConnectionFactory");
            Class<?> props = Class.forName("com.firefly.common.cache.properties.CacheProperties");
            Class<?> om = Class.forName("com.fasterxml.jackson.databind.ObjectMapper");
            var m = helperClass.getMethod("createRedisCacheAdapter",
                    String.class, String.class, Duration.class, rrCf, props, om);
            return (CacheAdapter) m.invoke(null, cacheName, keyPrefix, defaultTtl,
                    ctx.redisConnectionFactory, ctx.properties, ctx.objectMapper);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to create Redis cache via SPI", e);
        }
    }
}
