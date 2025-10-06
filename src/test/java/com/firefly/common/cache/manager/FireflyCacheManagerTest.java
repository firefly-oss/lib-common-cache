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

import com.firefly.common.cache.adapter.caffeine.CaffeineCacheAdapter;
import com.firefly.common.cache.adapter.caffeine.CaffeineCacheConfig;
import com.firefly.common.cache.core.CacheAdapter;
import com.firefly.common.cache.core.CacheType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for FireflyCacheManager.
 */
class FireflyCacheManagerTest {

    private FireflyCacheManager cacheManager;
    private CacheAdapter mockCacheAdapter1;
    private CacheAdapter mockCacheAdapter2;

    @BeforeEach
    void setUp() {
        cacheManager = new FireflyCacheManager();
        
        // Create real cache adapters for testing
        CaffeineCacheConfig config1 = CaffeineCacheConfig.builder()
                .maximumSize(100L)
                .recordStats(true)
                .build();
        mockCacheAdapter1 = new CaffeineCacheAdapter("cache1", config1);
        
        CaffeineCacheConfig config2 = CaffeineCacheConfig.builder()
                .maximumSize(50L)
                .recordStats(true)
                .build();
        mockCacheAdapter2 = new CaffeineCacheAdapter("cache2", config2);
    }

    @Test
    void shouldRegisterAndRetrieveCache() {
        // When
        cacheManager.registerCache("test-cache", mockCacheAdapter1);
        
        // Then
        assertThat(cacheManager.hasCache("test-cache")).isTrue();
        assertThat(cacheManager.getCache("test-cache")).isSameAs(mockCacheAdapter1);
        assertThat(cacheManager.getCacheCount()).isEqualTo(1);
        assertThat(cacheManager.getCacheNames()).containsExactly("test-cache");
    }

    @Test
    void shouldUnregisterCache() {
        // Given
        cacheManager.registerCache("test-cache", mockCacheAdapter1);
        
        // When
        CacheAdapter removed = cacheManager.unregisterCache("test-cache");
        
        // Then
        assertThat(removed).isSameAs(mockCacheAdapter1);
        assertThat(cacheManager.hasCache("test-cache")).isFalse();
        assertThat(cacheManager.getCacheCount()).isEqualTo(0);
    }

    @Test
    void shouldGetDefaultCacheWhenAvailable() {
        // Given
        cacheManager.registerCache("cache1", mockCacheAdapter1);
        cacheManager.registerCache("cache2", mockCacheAdapter2);
        
        // When
        CacheAdapter defaultCache = cacheManager.getDefaultCache();
        
        // Then
        assertThat(defaultCache).isNotNull();
        assertThat(defaultCache.getCacheType()).isEqualTo(CacheType.CAFFEINE);
    }

    @Test
    void shouldThrowExceptionWhenNoDefaultCacheAvailable() {
        // When & Then
        assertThatThrownBy(() -> cacheManager.getDefaultCache())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("No caches registered");
    }

    @Test
    void shouldPerformCacheOperationsOnDefaultCache() {
        // Given
        cacheManager.registerCache("default", mockCacheAdapter1);
        String key = "test-key";
        String value = "test-value";
        
        // When & Then - put and get
        StepVerifier.create(cacheManager.put(key, value))
                .verifyComplete();
        
        StepVerifier.create(cacheManager.get(key))
                .assertNext(result -> {
                    assertThat(result).isPresent();
                    assertThat(result.get()).isEqualTo(value);
                })
                .verifyComplete();
    }

    @Test
    void shouldPerformCacheOperationsOnSpecificCache() {
        // Given
        cacheManager.registerCache("specific-cache", mockCacheAdapter1);
        String key = "specific-key";
        String value = "specific-value";
        
        // When & Then
        StepVerifier.create(cacheManager.put("specific-cache", key, value))
                .verifyComplete();
        
        StepVerifier.create(cacheManager.get("specific-cache", key))
                .assertNext(result -> {
                    assertThat(result).isPresent();
                    assertThat(result.get()).isEqualTo(value);
                })
                .verifyComplete();
    }

    @Test
    void shouldReturnEmptyWhenGettingFromNonExistentCache() {
        // When & Then
        StepVerifier.create(cacheManager.get("non-existent", "key"))
                .assertNext(result -> assertThat(result).isEmpty())
                .verifyComplete();
    }

    @Test
    void shouldReturnFalseWhenEvictingFromNonExistentCache() {
        // When & Then
        StepVerifier.create(cacheManager.evict("non-existent", "key"))
                .assertNext(result -> assertThat(result).isFalse())
                .verifyComplete();
    }

    @Test
    void shouldPutIfAbsentOnDefaultCache() {
        // Given
        cacheManager.registerCache("default", mockCacheAdapter1);
        String key = "absent-key";
        String value1 = "first-value";
        String value2 = "second-value";
        
        // When & Then
        StepVerifier.create(cacheManager.putIfAbsent(key, value1))
                .assertNext(result -> assertThat(result).isTrue())
                .verifyComplete();
        
        StepVerifier.create(cacheManager.putIfAbsent(key, value2))
                .assertNext(result -> assertThat(result).isFalse())
                .verifyComplete();
        
        StepVerifier.create(cacheManager.get(key))
                .assertNext(result -> {
                    assertThat(result).isPresent();
                    assertThat(result.get()).isEqualTo(value1);
                })
                .verifyComplete();
    }

    @Test
    void shouldPutWithTtlOnDefaultCache() {
        // Given
        cacheManager.registerCache("default", mockCacheAdapter1);
        String key = "ttl-key";
        String value = "ttl-value";
        Duration ttl = Duration.ofMillis(100);
        
        // When & Then
        StepVerifier.create(cacheManager.<String, String>put(key, value, ttl))
                .verifyComplete();
        
        StepVerifier.create(cacheManager.get(key))
                .assertNext(result -> {
                    assertThat(result).isPresent();
                    assertThat(result.get()).isEqualTo(value);
                })
                .verifyComplete();
    }

    @Test
    void shouldEvictFromDefaultCache() {
        // Given
        cacheManager.registerCache("default", mockCacheAdapter1);
        String key = "evict-key";
        String value = "evict-value";
        
        StepVerifier.create(cacheManager.put(key, value))
                .verifyComplete();
        
        // When & Then
        StepVerifier.create(cacheManager.evict(key))
                .assertNext(result -> assertThat(result).isTrue())
                .verifyComplete();
        
        StepVerifier.create(cacheManager.exists(key))
                .assertNext(result -> assertThat(result).isFalse())
                .verifyComplete();
    }

    @Test
    void shouldClearDefaultCache() {
        // Given
        cacheManager.registerCache("default", mockCacheAdapter1);
        
        StepVerifier.create(cacheManager.put("key1", "value1"))
                .verifyComplete();
        StepVerifier.create(cacheManager.put("key2", "value2"))
                .verifyComplete();
        
        // When & Then
        StepVerifier.create(cacheManager.clear())
                .verifyComplete();
        
        StepVerifier.create(cacheManager.exists("key1"))
                .assertNext(result -> assertThat(result).isFalse())
                .verifyComplete();
    }

    @Test
    void shouldGetHealthForAllCaches() {
        // Given
        cacheManager.registerCache("cache1", mockCacheAdapter1);
        cacheManager.registerCache("cache2", mockCacheAdapter2);
        
        // When & Then
        StepVerifier.create(cacheManager.getHealth())
                .expectNextCount(2)
                .verifyComplete();
    }

    @Test
    void shouldGetHealthForSpecificCache() {
        // Given
        cacheManager.registerCache("test-cache", mockCacheAdapter1);
        
        // When & Then
        StepVerifier.create(cacheManager.getHealth("test-cache"))
                .assertNext(health -> {
                    assertThat(health.getCacheName()).isEqualTo("cache1");
                    assertThat(health.getCacheType()).isEqualTo(CacheType.CAFFEINE);
                    assertThat(health.isHealthy()).isTrue();
                })
                .verifyComplete();
    }

    @Test
    void shouldGetStatsForAllCaches() {
        // Given
        cacheManager.registerCache("cache1", mockCacheAdapter1);
        cacheManager.registerCache("cache2", mockCacheAdapter2);
        
        // When & Then
        StepVerifier.create(cacheManager.getStats())
                .expectNextCount(2)
                .verifyComplete();
    }

    @Test
    void shouldGetStatsForSpecificCache() {
        // Given
        cacheManager.registerCache("test-cache", mockCacheAdapter1);
        
        // When & Then
        StepVerifier.create(cacheManager.getStats("test-cache"))
                .assertNext(stats -> {
                    assertThat(stats.getCacheName()).isEqualTo("cache1");
                    assertThat(stats.getCacheType()).isEqualTo(CacheType.CAFFEINE);
                })
                .verifyComplete();
    }

    @Test
    void shouldCloseAllCaches() {
        // Given
        cacheManager.registerCache("cache1", mockCacheAdapter1);
        cacheManager.registerCache("cache2", mockCacheAdapter2);
        
        assertThat(cacheManager.isClosed()).isFalse();
        
        // When
        cacheManager.close();
        
        // Then
        assertThat(cacheManager.isClosed()).isTrue();
        assertThat(cacheManager.getCacheCount()).isEqualTo(0);
    }

    @Test
    void shouldThrowExceptionWhenRegisteringCacheAfterClose() {
        // Given
        cacheManager.close();
        
        // When & Then
        assertThatThrownBy(() -> cacheManager.registerCache("test", mockCacheAdapter1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Cache manager is closed");
    }
}