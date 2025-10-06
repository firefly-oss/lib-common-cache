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
 * Group annotation that allows combining multiple cache operations.
 * <p>
 * This annotation can be used when multiple cache operations need to be performed
 * on a single method, such as caching a result while also evicting related entries.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface Caching {

    /**
     * Array of {@link Cacheable} annotations.
     *
     * @return cacheable operations
     */
    Cacheable[] cacheable() default {};

    /**
     * Array of {@link CachePut} annotations.
     *
     * @return cache put operations
     */
    CachePut[] put() default {};

    /**
     * Array of {@link CacheEvict} annotations.
     *
     * @return cache evict operations
     */
    CacheEvict[] evict() default {};
}