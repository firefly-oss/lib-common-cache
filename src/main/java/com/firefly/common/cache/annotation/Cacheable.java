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

package com.firefly.common.cache.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.time.Duration;

/**
 * Annotation to mark methods whose results should be cached.
 * <p>
 * When applied to a method, the result of the method will be stored in the cache
 * using the computed key. Subsequent calls with the same key will return the
 * cached value instead of executing the method.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface Cacheable {

    /**
     * Names of the cache(s) to use.
     * If multiple caches are specified, they will be checked in order.
     *
     * @return cache names
     */
    String[] value() default {"default"};

    /**
     * Cache names (alias for value).
     *
     * @return cache names
     */
    String[] cacheNames() default {};

    /**
     * Spring Expression Language (SpEL) expression for computing the cache key.
     * If not specified, a default key will be generated based on method parameters.
     *
     * @return the key expression
     */
    String key() default "";

    /**
     * Spring Expression Language (SpEL) expression for computing the cache key.
     * Alternative to the 'key' attribute that allows for more complex expressions.
     *
     * @return the key generator expression
     */
    String keyGenerator() default "";

    /**
     * Spring Expression Language (SpEL) expression for conditional caching.
     * The method result will only be cached if this expression evaluates to true.
     *
     * @return the condition expression
     */
    String condition() default "";

    /**
     * Spring Expression Language (SpEL) expression to prevent caching.
     * If this expression evaluates to true, the result will not be cached.
     * This is evaluated after the method execution.
     *
     * @return the unless expression
     */
    String unless() default "";

    /**
     * Time-to-live for cached values in ISO-8601 duration format (e.g., "PT1H" for 1 hour).
     * If not specified, the cache's default TTL will be used.
     *
     * @return TTL in ISO-8601 format
     */
    String ttl() default "";

    /**
     * Whether to sync the cache operation.
     * When true, concurrent calls with the same key will be synchronized.
     *
     * @return true to sync cache operations
     */
    boolean sync() default false;
}