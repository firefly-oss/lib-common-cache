package com.firefly.common.cache.exception;

/**
 * Base exception for all cache-related errors.
 *
 * @author Firefly Team
 * @since 1.0.0
 */
public class CacheException extends RuntimeException {

    public CacheException(String message) {
        super(message);
    }

    public CacheException(String message, Throwable cause) {
        super(message, cause);
    }

    public CacheException(Throwable cause) {
        super(cause);
    }
}
