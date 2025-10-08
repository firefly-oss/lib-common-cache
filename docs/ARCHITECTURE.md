# Firefly Common Cache - Architecture Guide

## Overview

This document explains the architecture of the Firefly Common Cache library and how to properly use it in your Spring Boot applications.

## Hexagonal Architecture (Ports and Adapters)

The library follows hexagonal architecture principles with a clear separation between:

### Core Domain (Ports)
- **`CacheAdapter`**: Internal reactive interface for cache operations
- **`FireflyCacheManager`**: Public API for managing multiple cache instances
- **`CacheSelectionStrategy`**: Strategy for selecting the appropriate cache

### Adapters (Implementations)
- **`CaffeineCacheAdapter`**: In-memory cache implementation (always available)
- **`RedisCacheAdapter`**: Distributed cache implementation (optional)

## Public API

The main entry point for using the cache library is the `FireflyCacheManager` class:

```java
package com.firefly.common.cache.manager;

public class FireflyCacheManager {
    // Register a cache adapter
    public void registerCache(String name, CacheAdapter adapter);
    
    // Unregister a cache adapter
    public void unregisterCache(String name);
    
    // Select the best cache based on strategy
    public CacheAdapter selectCache(String cacheName);
    
    // Get all registered cache names
    public Collection<String> getCacheNames();
    
    // Get health information
    public Mono<CacheHealth> getHealth();
    
    // Get aggregated statistics
    public Mono<CacheStats> getStats();
    
    // Clear all caches
    public void clearAll();
    
    // Close all caches
    public void close();
}
```

## How to Use in Your Application

### 1. Add Dependency

**For Caffeine only (in-memory cache):**
```xml
<dependency>
    <groupId>com.firefly</groupId>
    <artifactId>lib-common-cache</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

**For Caffeine + Redis (distributed cache):**
```xml
<dependency>
    <groupId>com.firefly</groupId>
    <artifactId>lib-common-cache</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis-reactive</artifactId>
</dependency>
<dependency>
    <groupId>io.lettuce</groupId>
    <artifactId>lettuce-core</artifactId>
</dependency>
```

### 2. Inject FireflyCacheManager

```java
import com.firefly.common.cache.manager.FireflyCacheManager;
import org.springframework.stereotype.Service;

@Service
public class MyService {
    
    private final FireflyCacheManager cacheManager;
    
    public MyService(FireflyCacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }
    
    public void doSomething() {
        // Select the best cache
        CacheAdapter cache = cacheManager.selectCache("default");
        
        // Use the cache
        cache.put("key", "value").block();
        Optional<String> value = cache.get("key", String.class).block();
    }
}
```

### 3. Use @ConditionalOnBean for Optional Features

If you want to enable features only when the cache is available:

```java
import com.firefly.common.cache.manager.FireflyCacheManager;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MyConfiguration {
    
    @Bean
    @ConditionalOnBean(FireflyCacheManager.class)
    public MyCachedService myCachedService(FireflyCacheManager cacheManager) {
        return new MyCachedService(cacheManager);
    }
}
```

**Important:** Use `FireflyCacheManager.class` (the concrete class) in `@ConditionalOnBean`.

## Auto-Configuration

The library provides two auto-configuration classes:

### 1. CacheAutoConfiguration (Always Loaded)
- **Package:** `com.firefly.common.cache.config`
- **Provides:**
  - `FireflyCacheManager` bean
  - `CacheSelectionStrategy` bean
  - `CacheSerializer` bean
  - `CaffeineCacheAdapter` bean (when Caffeine is on classpath)

### 2. RedisCacheAutoConfiguration (Conditionally Loaded)
- **Package:** `com.firefly.common.cache.config`
- **Condition:** Only loads when Redis classes are on classpath
- **Provides:**
  - `ReactiveRedisConnectionFactory` bean
  - `ReactiveRedisTemplate` bean
  - `RedisCacheAdapter` bean

## Configuration Properties

```yaml
firefly:
  cache:
    enabled: true  # Enable/disable cache library
    default-cache-name: default
    default-cache-type: AUTO  # AUTO, CAFFEINE, REDIS
    
    caffeine:
      default:
        enabled: true
        maximum-size: 10000
        expire-after-write: 1h
        expire-after-access: 30m
        record-stats: true
    
    redis:
      default:
        enabled: true
        host: localhost
        port: 6379
        database: 0
        key-prefix: "cache:"
        default-ttl: 1h
```

## Bean Matching Rules

### ✅ Correct Usage

```java
// Inject the concrete class
@Autowired
private FireflyCacheManager cacheManager;

// Use in @ConditionalOnBean
@ConditionalOnBean(FireflyCacheManager.class)
public MyService myService(FireflyCacheManager cacheManager) {
    return new MyService(cacheManager);
}
```

### ❌ Incorrect Usage (Old API - Removed)

```java
// DON'T: These interfaces were removed
import com.firefly.common.cache.FireflyCacheManager;  // Removed
import com.firefly.common.cache.FireflyCache;         // Removed
```

## Why This Architecture?

### Previous Problem (Before Fix)

The library had two classes with the same name:
1. **Interface:** `com.firefly.common.cache.FireflyCacheManager` (synchronous API)
2. **Class:** `com.firefly.common.cache.manager.FireflyCacheManager` (reactive implementation)

The class did NOT implement the interface, causing:
- `@ConditionalOnBean(FireflyCacheManager.class)` to fail in other libraries
- Confusion about which type to use
- Bean matching issues in Spring

### Current Solution (After Fix)

We removed the unnecessary interfaces and simplified the architecture:
- **Single public class:** `com.firefly.common.cache.manager.FireflyCacheManager`
- **Internal reactive API:** `CacheAdapter` interface
- **Clear separation:** Public API (FireflyCacheManager) vs Internal API (CacheAdapter)

This follows the **Single Responsibility Principle** and makes the library easier to use.

## Testing

### Unit Tests
```java
@Test
void testCacheManager() {
    FireflyCacheManager manager = new FireflyCacheManager();
    
    CaffeineCacheConfig config = CaffeineCacheConfig.builder()
        .maximumSize(100L)
        .build();
    
    CacheAdapter adapter = new CaffeineCacheAdapter("test", config);
    manager.registerCache("test", adapter);
    
    CacheAdapter selected = manager.selectCache("test");
    assertNotNull(selected);
}
```

### Integration Tests
```java
@SpringBootTest
@TestPropertySource(properties = {
    "firefly.cache.enabled=true"
})
class MyIntegrationTest {
    
    @Autowired
    private FireflyCacheManager cacheManager;
    
    @Test
    void testCacheIsAvailable() {
        assertNotNull(cacheManager);
        assertTrue(cacheManager.isEnabled());
    }
}
```

## Best Practices

1. **Always inject `FireflyCacheManager`** - This is the public API
2. **Use `@ConditionalOnBean(FireflyCacheManager.class)`** - For optional cache features
3. **Configure via properties** - Don't create beans manually unless necessary
4. **Use reactive API internally** - `CacheAdapter` returns `Mono`/`Flux`
5. **Handle Optional properly** - Cache operations return `Mono<Optional<T>>`

## Summary

- ✅ **Public API:** `com.firefly.common.cache.manager.FireflyCacheManager`
- ✅ **Internal API:** `com.firefly.common.cache.core.CacheAdapter`
- ✅ **Redis is optional:** Works without Redis dependencies
- ✅ **Auto-configuration:** Automatically sets up based on classpath
- ✅ **Bean matching:** Use `FireflyCacheManager.class` in conditions
- ✅ **Clean architecture:** Clear separation of concerns

For more information, see:
- [Optional Dependencies Guide](OPTIONAL_DEPENDENCIES.md)
- [README](../README.md)

