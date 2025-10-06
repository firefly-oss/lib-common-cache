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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.firefly.common.cache.manager.CacheSelectionStrategy;
import com.firefly.common.cache.manager.FireflyCacheManager;
import com.firefly.common.cache.properties.CacheProperties;
import com.firefly.common.cache.serialization.CacheSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for CacheAutoConfiguration.
 */
class CacheAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(CacheAutoConfiguration.class));

    @Test
    void shouldCreateAllRequiredBeansWhenCacheEnabled() {
        contextRunner
                .withPropertyValues("firefly.cache.enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(CacheProperties.class);
                    assertThat(context).hasSingleBean(ObjectMapper.class);
                    assertThat(context).hasSingleBean(CacheSerializer.class);
                    assertThat(context).hasSingleBean(CacheSelectionStrategy.class);
                    assertThat(context).hasSingleBean(FireflyCacheManager.class);
                });
    }

    @Test
    void shouldNotCreateBeansWhenCacheDisabled() {
        contextRunner
                .withPropertyValues("firefly.cache.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(CacheProperties.class);
                    assertThat(context).doesNotHaveBean(FireflyCacheManager.class);
                });
    }

    @Test
    void shouldCreateCaffeineAdapterWhenCaffeineAvailable() {
        contextRunner
                .withPropertyValues(
                        "firefly.cache.enabled=true",
                        "firefly.cache.caffeine.default.enabled=true"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(FireflyCacheManager.class);
                    // Note: The actual cache adapter creation happens during manager initialization
                });
    }

    @Test
    void shouldUseCustomPropertiesWhenProvided() {
        contextRunner
                .withPropertyValues(
                        "firefly.cache.enabled=true",
                        "firefly.cache.default-cache-name=custom-default",
                        "firefly.cache.default-cache-type=CAFFEINE",
                        "firefly.cache.metrics-enabled=false"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(CacheProperties.class);
                    
                    CacheProperties properties = context.getBean(CacheProperties.class);
                    assertThat(properties.getDefaultCacheName()).isEqualTo("custom-default");
                    assertThat(properties.getDefaultCacheType().getIdentifier()).isEqualTo("caffeine");
                    assertThat(properties.isMetricsEnabled()).isFalse();
                });
    }

    @Test
    void shouldCreateBeansWithDefaultConfiguration() {
        contextRunner
                .run(context -> {
                    // Cache should be enabled by default
                    assertThat(context).hasSingleBean(CacheProperties.class);
                    assertThat(context).hasSingleBean(FireflyCacheManager.class);
                    
                    CacheProperties properties = context.getBean(CacheProperties.class);
                    assertThat(properties.isEnabled()).isTrue();
                    assertThat(properties.getDefaultCacheName()).isEqualTo("default");
                    assertThat(properties.getDefaultCacheType()).isEqualTo(com.firefly.common.cache.core.CacheType.CAFFEINE);
                    assertThat(properties.isMetricsEnabled()).isTrue();
                    assertThat(properties.isHealthEnabled()).isTrue();
                    assertThat(properties.isStatsEnabled()).isTrue();
                });
    }

    @Test
    void shouldRespectExistingObjectMapperBean() {
        contextRunner
                .withBean("customObjectMapper", ObjectMapper.class, ObjectMapper::new)
                .run(context -> {
                    assertThat(context).hasBean("customObjectMapper");
                    assertThat(context).hasBean("cacheObjectMapper");
                    // Should have both the custom bean and cache object mapper
                    assertThat(context.getBeansOfType(ObjectMapper.class)).hasSize(2);
                });
    }

    @Test
    void shouldConfigureCaffeinePropertiesCorrectly() {
        contextRunner
                .withPropertyValues(
                        "firefly.cache.enabled=true",
                        "firefly.cache.caffeine.default.maximum-size=500",
                        "firefly.cache.caffeine.default.expire-after-write=PT30M",
                        "firefly.cache.caffeine.default.record-stats=false"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(CacheProperties.class);

                    CacheProperties properties = context.getBean(CacheProperties.class);
                    CacheProperties.CaffeineConfig caffeineConfig = properties.getCaffeineConfig("default");

                    assertThat(caffeineConfig.getMaximumSize()).isEqualTo(500L);
                    assertThat(caffeineConfig.getExpireAfterWrite().toMinutes()).isEqualTo(30);
                    assertThat(caffeineConfig.isRecordStats()).isFalse();
                });
    }

    @Test
    void shouldRegisterCaffeineAdapterWithManager() {
        contextRunner
                .withPropertyValues(
                        "firefly.cache.enabled=true",
                        "firefly.cache.default-cache-type=CAFFEINE",
                        "firefly.cache.caffeine.default.enabled=true"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(FireflyCacheManager.class);

                    FireflyCacheManager cacheManager = context.getBean(FireflyCacheManager.class);
                    assertThat(cacheManager.hasCache("default")).isTrue();
                    assertThat(cacheManager.getCache("default").getCacheType())
                            .isEqualTo(com.firefly.common.cache.core.CacheType.CAFFEINE);
                });
    }

    @Test
    void shouldNotConfigureRedisWhenHostNotProvided() {
        contextRunner
                .withPropertyValues(
                        "firefly.cache.enabled=true",
                        "firefly.cache.redis.default.enabled=true"
                        // No host provided
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(FireflyCacheManager.class);
                    // Redis should not be configured without host
                    assertThat(context).doesNotHaveBean(org.springframework.data.redis.core.ReactiveRedisTemplate.class);
                });
    }

    @Test
    void shouldUseFireflyPropertiesNotSpringCacheProperties() {
        // Verify we use firefly.cache.* properties, not spring.cache.*
        contextRunner
                .withPropertyValues(
                        "spring.cache.type=none", // Spring property - should be ignored
                        "firefly.cache.enabled=true", // Firefly property - should be used
                        "firefly.cache.default-cache-type=CAFFEINE"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(FireflyCacheManager.class);

                    FireflyCacheManager cacheManager = context.getBean(FireflyCacheManager.class);
                    // Should have Caffeine cache despite spring.cache.type=none
                    assertThat(cacheManager.hasCache("default")).isTrue();
                });
    }

    @Test
    void shouldDisableCaffeineWhenExplicitlyDisabled() {
        contextRunner
                .withPropertyValues(
                        "firefly.cache.enabled=true",
                        "firefly.cache.caffeine.default.enabled=false"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(FireflyCacheManager.class);

                    FireflyCacheManager cacheManager = context.getBean(FireflyCacheManager.class);
                    // No cache adapters should be registered
                    assertThat(cacheManager.getCacheNames()).isEmpty();
                });
    }

    @Test
    void shouldAutoConfigureWhenLibraryAddedAsDependency() {
        // This simulates adding the library as a dependency with no configuration
        contextRunner
                .run(context -> {
                    // Should auto-configure with defaults
                    assertThat(context).hasSingleBean(FireflyCacheManager.class);
                    assertThat(context).hasSingleBean(CacheProperties.class);

                    CacheProperties properties = context.getBean(CacheProperties.class);
                    assertThat(properties.isEnabled()).isTrue();
                    assertThat(properties.getDefaultCacheType())
                            .isEqualTo(com.firefly.common.cache.core.CacheType.CAFFEINE);

                    FireflyCacheManager cacheManager = context.getBean(FireflyCacheManager.class);
                    assertThat(cacheManager).isNotNull();
                });
    }
}