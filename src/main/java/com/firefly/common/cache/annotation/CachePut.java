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

/**
 * Annotation to mark methods that should always update the cache.
 * <p>
 * Unlike {@link Cacheable}, this annotation always executes the method and stores
 * the result in the cache. This is useful for methods that modify data and need
 * to update the cache with the latest values.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface CachePut {

    /**
     * Names of the cache(s) to update.
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
}