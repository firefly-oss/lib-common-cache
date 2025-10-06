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

import com.firefly.common.cache.config.CacheAutoConfiguration;
import org.springframework.context.annotation.Import;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to enable Firefly caching annotations processing.
 * <p>
 * This annotation should be added to a configuration class to enable
 * support for Firefly cache annotations like {@link Cacheable}, {@link CacheEvict},
 * and {@link CachePut}.
 * <p>
 * Example usage:
 * <pre>
 * &#64;Configuration
 * &#64;EnableCaching
 * public class AppConfig {
 *     // Configuration beans
 * }
 * </pre>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Import(CacheAutoConfiguration.class)
public @interface EnableCaching {

    /**
     * Indicate whether subclass-based (CGLIB) proxies are to be created
     * as opposed to standard Java interface-based proxies.
     *
     * @return true to use CGLIB proxies
     */
    boolean proxyTargetClass() default false;

    /**
     * Indicate how caching advice should be applied.
     *
     * @return the advice mode
     */
    AdviceMode mode() default AdviceMode.PROXY;

    /**
     * Indicate the order in which the caching advice should be applied.
     * The default order is lowest precedence.
     *
     * @return the order value
     */
    int order() default Integer.MAX_VALUE;

    /**
     * Enum defining the possible advice modes.
     */
    enum AdviceMode {
        /**
         * JDK proxy-based advice.
         */
        PROXY,
        
        /**
         * AspectJ weaving-based advice.
         */
        ASPECTJ
    }
}