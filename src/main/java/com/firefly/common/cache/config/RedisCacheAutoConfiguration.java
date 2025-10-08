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
import com.firefly.common.cache.adapter.redis.RedisCacheAdapter;
import com.firefly.common.cache.adapter.redis.RedisCacheConfig;
import com.firefly.common.cache.core.CacheAdapter;
import com.firefly.common.cache.properties.CacheProperties;
import com.firefly.common.cache.serialization.CacheSerializer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Auto-configuration for Redis cache support.
 * <p>
 * This configuration is only loaded when Redis classes are available on the classpath.
 * It provides Redis connection factory, template, and cache adapter beans.
 */
@AutoConfiguration(after = CacheAutoConfiguration.class)
@ConditionalOnClass(name = "org.springframework.data.redis.core.ReactiveRedisTemplate")
@Slf4j
public class RedisCacheAutoConfiguration {

    /**
     * Creates a Redis connection factory from Firefly cache properties when:
     * - No existing ReactiveRedisConnectionFactory bean exists
     * - Redis is enabled (or not explicitly disabled) AND host is configured
     */
    @Bean
    @ConditionalOnMissingBean(ReactiveRedisConnectionFactory.class)
    @ConditionalOnExpression("${firefly.cache.redis.default.enabled:true} && '${firefly.cache.redis.default.host:}'.length() > 0")
    public ReactiveRedisConnectionFactory redisConnectionFactory(CacheProperties properties) {
        log.debug("Creating Redis connection factory from Firefly cache properties");
        CacheProperties.RedisConfig redisProps = properties.getRedisConfig("default");
        
        RedisStandaloneConfiguration serverConfig = new RedisStandaloneConfiguration();
        serverConfig.setHostName(redisProps.getHost());
        serverConfig.setPort(redisProps.getPort());
        serverConfig.setDatabase(redisProps.getDatabase());
        
        if (redisProps.getPassword() != null) {
            serverConfig.setPassword(redisProps.getPassword());
        }
        if (redisProps.getUsername() != null) {
            serverConfig.setUsername(redisProps.getUsername());
        }
        
        LettuceClientConfiguration clientConfig = LettuceClientConfiguration.builder()
                .commandTimeout(redisProps.getCommandTimeout())
                .build();
        
        log.info("   • Redis host: {}:{}", redisProps.getHost(), redisProps.getPort());
        log.info("   • Redis database: {}", redisProps.getDatabase());
        
        return new LettuceConnectionFactory(serverConfig, clientConfig);
    }

    /**
     * Creates a Redis template from connection factory when:
     * - No existing ReactiveRedisTemplate bean exists
     * - ReactiveRedisConnectionFactory is available
     */
    @Bean
    @ConditionalOnMissingBean(ReactiveRedisTemplate.class)
    @ConditionalOnBean(ReactiveRedisConnectionFactory.class)
    public ReactiveRedisTemplate<String, Object> reactiveRedisTemplate(
            ReactiveRedisConnectionFactory connectionFactory,
            @Qualifier("cacheObjectMapper") ObjectMapper objectMapper) {
        log.debug("Creating ReactiveRedisTemplate from connection factory");
        
        // Configure serializers
        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        GenericJackson2JsonRedisSerializer jsonSerializer = new GenericJackson2JsonRedisSerializer(objectMapper);
        
        RedisSerializationContext<String, Object> serializationContext = RedisSerializationContext
                .<String, Object>newSerializationContext()
                .key(stringSerializer)
                .value(jsonSerializer)
                .hashKey(stringSerializer)
                .hashValue(jsonSerializer)
                .build();
        
        return new ReactiveRedisTemplate<>(connectionFactory, serializationContext);
    }

    /**
     * Creates a Redis cache adapter when Redis is available and enabled.
     */
    @Bean
    @ConditionalOnProperty(prefix = "firefly.cache.redis.default", name = "enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnBean({ReactiveRedisTemplate.class, ReactiveRedisConnectionFactory.class})
    public CacheAdapter redisCacheAdapter(CacheProperties properties,
                                         ReactiveRedisTemplate<String, Object> redisTemplate,
                                         ReactiveRedisConnectionFactory connectionFactory,
                                         CacheSerializer serializer) {
        log.debug("Creating Redis cache adapter");
        CacheProperties.RedisConfig redisProps = properties.getRedisConfig("default");
        
        RedisCacheConfig config = RedisCacheConfig.builder()
                .host(redisProps.getHost())
                .port(redisProps.getPort())
                .database(redisProps.getDatabase())
                .password(redisProps.getPassword())
                .username(redisProps.getUsername())
                .connectionTimeout(redisProps.getConnectionTimeout())
                .commandTimeout(redisProps.getCommandTimeout())
                .keyPrefix(redisProps.getKeyPrefix())
                .defaultTtl(redisProps.getDefaultTtl())
                .enableKeyspaceNotifications(redisProps.isEnableKeyspaceNotifications())
                .maxPoolSize(redisProps.getMaxPoolSize())
                .minPoolSize(redisProps.getMinPoolSize())
                .ssl(redisProps.isSsl())
                .build();
        
        return new RedisCacheAdapter("default", redisTemplate, connectionFactory, config, serializer);
    }
}

