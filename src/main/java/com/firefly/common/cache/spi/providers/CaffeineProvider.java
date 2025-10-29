package com.firefly.common.cache.spi.providers;

import com.firefly.common.cache.adapter.caffeine.CaffeineCacheAdapter;
import com.firefly.common.cache.adapter.caffeine.CaffeineCacheConfig;
import com.firefly.common.cache.core.CacheAdapter;
import com.firefly.common.cache.core.CacheType;
import com.firefly.common.cache.spi.CacheProviderFactory;

import java.time.Duration;

public class CaffeineProvider implements CacheProviderFactory {
    @Override
    public CacheType getType() { return CacheType.CAFFEINE; }
    @Override
    public int priority() { return 40; }
    @Override
    public boolean isAvailable(ProviderContext ctx) { return ctx.properties.getCaffeine().isEnabled(); }
    @Override
    public CacheAdapter create(String cacheName, String keyPrefix, Duration defaultTtl, ProviderContext ctx) {
        var caffeineProps = ctx.properties.getCaffeine();
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
}
