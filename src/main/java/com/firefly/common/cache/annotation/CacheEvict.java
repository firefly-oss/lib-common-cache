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
 * Annotation to mark methods that should trigger cache eviction.
 * <p>
 * When applied to a method, the specified cache entries will be removed
 * either before or after the method execution, depending on the configuration.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface CacheEvict {

    /**
     * Names of the cache(s) to evict from.
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
     * Spring Expression Language (SpEL) expression for computing the cache key to evict.
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
     * Spring Expression Language (SpEL) expression for conditional eviction.
     * The cache entry will only be evicted if this expression evaluates to true.
     *
     * @return the condition expression
     */
    String condition() default "";

    /**
     * Whether to evict all entries from the cache.
     * When true, all entries in the specified cache(s) will be removed.
     *
     * @return true to evict all entries
     */
    boolean allEntries() default false;

    /**
     * Whether to perform the eviction before the method execution.
     * When false, eviction occurs after the method execution.
     *
     * @return true to evict before method execution
     */
    boolean beforeInvocation() default false;
}