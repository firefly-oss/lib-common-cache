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

package com.firefly.common.cache.config;

import com.firefly.common.cache.core.CacheType;
import com.firefly.common.cache.manager.FireflyCacheManager;
import com.firefly.common.cache.properties.CacheProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import reactor.test.StepVerifier;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for cache auto-configuration in a real Spring Boot application.
 * Verifies that the library auto-configures correctly when added as a dependency.
 */
@SpringBootTest(classes = CacheAutoConfiguration.class)
@TestPropertySource(properties = {
        "firefly.cache.enabled=true",
        "firefly.cache.default-cache-type=CAFFEINE",
        "firefly.cache.caffeine.default.maximum-size=100",
        "firefly.cache.caffeine.default.expire-after-write=PT1M"
})
class CacheAutoConfigurationIntegrationTest {

    @Autowired
    private FireflyCacheManager cacheManager;

    @Autowired
    private CacheProperties cacheProperties;

    @Test
    void shouldAutoConfigureCacheManager() {
        assertThat(cacheManager).isNotNull();
    }

    @Test
    void shouldLoadCacheProperties() {
        assertThat(cacheProperties).isNotNull();
        assertThat(cacheProperties.isEnabled()).isTrue();
        assertThat(cacheProperties.getDefaultCacheType()).isEqualTo(CacheType.CAFFEINE);
        assertThat(cacheProperties.getDefaultCacheName()).isEqualTo("default");
    }

    @Test
    void shouldRegisterDefaultCaffeineCache() {
        assertThat(cacheManager.hasCache("default")).isTrue();
        assertThat(cacheManager.getCache("default")).isNotNull();
        assertThat(cacheManager.getCache("default").getCacheType()).isEqualTo(CacheType.CAFFEINE);
    }

    @Test
    void shouldPerformCacheOperations() {
        String key = "test-key";
        String value = "test-value";

        // Put value
        StepVerifier.create(cacheManager.put(key, value))
                .verifyComplete();

        // Get value
        StepVerifier.create(cacheManager.<String, String>get(key, String.class))
                .assertNext(result -> {
                    assertThat(result).isPresent();
                    assertThat(result.get()).isEqualTo(value);
                })
                .verifyComplete();

        // Evict value
        StepVerifier.create(cacheManager.evict(key))
                .expectNext(true)
                .verifyComplete();

        // Verify evicted
        StepVerifier.create(cacheManager.<String, String>get(key, String.class))
                .assertNext(result -> assertThat(result).isEmpty())
                .verifyComplete();
    }

    @Test
    void shouldRespectTTL() throws InterruptedException {
        String key = "ttl-test-key";
        String value = "ttl-test-value";
        Duration ttl = Duration.ofMillis(100);

        // Put with TTL
        StepVerifier.create(cacheManager.<String, String>put(key, value, ttl))
                .verifyComplete();

        // Should exist immediately
        StepVerifier.create(cacheManager.exists(key))
                .expectNext(true)
                .verifyComplete();

        // Wait for expiration
        Thread.sleep(150);

        // Should be expired
        StepVerifier.create(cacheManager.<String, String>get(key, String.class))
                .assertNext(result -> assertThat(result).isEmpty())
                .verifyComplete();
    }

    @Test
    void shouldGetCacheStatistics() {
        // Perform some operations
        cacheManager.<String, String>put("stat-key-1", "value1").block();
        cacheManager.<String, String>get("stat-key-1", String.class).block(); // hit
        cacheManager.<String, String>get("stat-key-2", String.class).block(); // miss

        // Get statistics
        StepVerifier.create(cacheManager.getStats("default"))
                .assertNext(stats -> {
                    assertThat(stats).isNotNull();
                    assertThat(stats.getCacheName()).isEqualTo("default");
                    assertThat(stats.getCacheType()).isEqualTo(CacheType.CAFFEINE);
                    assertThat(stats.getHitCount()).isGreaterThan(0);
                    assertThat(stats.getMissCount()).isGreaterThan(0);
                })
                .verifyComplete();
    }

    @Test
    void shouldGetCacheHealth() {
        StepVerifier.create(cacheManager.getHealth("default"))
                .assertNext(health -> {
                    assertThat(health).isNotNull();
                    assertThat(health.getCacheName()).isEqualTo("default");
                    assertThat(health.getCacheType()).isEqualTo(CacheType.CAFFEINE);
                    assertThat(health.isAvailable()).isTrue();
                    assertThat(health.getStatus()).isEqualTo("UP");
                })
                .verifyComplete();
    }

    @Test
    void shouldClearCache() {
        // Add some entries
        cacheManager.<String, String>put("clear-key-1", "value1").block();
        cacheManager.<String, String>put("clear-key-2", "value2").block();

        // Clear cache
        StepVerifier.create(cacheManager.clear())
                .verifyComplete();

        // Verify cleared
        StepVerifier.create(cacheManager.<String, String>get("clear-key-1", String.class))
                .assertNext(result -> assertThat(result).isEmpty())
                .verifyComplete();

        StepVerifier.create(cacheManager.<String, String>get("clear-key-2", String.class))
                .assertNext(result -> assertThat(result).isEmpty())
                .verifyComplete();
    }

    @Test
    void shouldHandleComplexObjects() {
        TestObject obj = new TestObject("123", "Test Name", 42);
        String key = "complex-object-key";

        // Put complex object
        StepVerifier.create(cacheManager.<String, TestObject>put(key, obj))
                .verifyComplete();

        // Get complex object
        StepVerifier.create(cacheManager.<String, TestObject>get(key, TestObject.class))
                .assertNext(result -> {
                    assertThat(result).isPresent();
                    TestObject retrieved = result.get();
                    assertThat(retrieved.getId()).isEqualTo("123");
                    assertThat(retrieved.getName()).isEqualTo("Test Name");
                    assertThat(retrieved.getValue()).isEqualTo(42);
                })
                .verifyComplete();
    }

    @Test
    void shouldUsePutIfAbsent() {
        String key = "put-if-absent-key";
        String value1 = "first-value";
        String value2 = "second-value";

        // First put should succeed
        StepVerifier.create(cacheManager.<String, String>putIfAbsent(key, value1))
                .expectNext(true)
                .verifyComplete();

        // Second put should fail (key exists)
        StepVerifier.create(cacheManager.<String, String>putIfAbsent(key, value2))
                .expectNext(false)
                .verifyComplete();

        // Value should still be the first one
        StepVerifier.create(cacheManager.<String, String>get(key, String.class))
                .assertNext(result -> {
                    assertThat(result).isPresent();
                    assertThat(result.get()).isEqualTo(value1);
                })
                .verifyComplete();
    }

    /**
     * Test object for complex object caching.
     */
    public static class TestObject {
        private String id;
        private String name;
        private int value;

        public TestObject() {
        }

        public TestObject(String id, String name, int value) {
            this.id = id;
            this.name = name;
            this.value = value;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getValue() {
            return value;
        }

        public void setValue(int value) {
            this.value = value;
        }
    }
}

