# Firefly Common Cache - Architecture Guide

## Overview

This document explains the simplified architecture of the Firefly Common Cache library and how to properly use it in your Spring Boot applications.

## Design Philosophy

The library follows these key principles:

1. **Simplicity First**: `FireflyCacheManager` **IS** the cache, not a manager of multiple caches
2. **Single Cache Instance**: One cache configuration per application
3. **Automatic Fallback**: Built-in support for primary/fallback pattern (e.g., Redis → Caffeine)
4. **Hexagonal Architecture**: Clean separation between public API and internal adapters
5. **Consistent Key Format**: Both Caffeine and Redis use `keyPrefix:cacheName:key`

## Architecture Diagram

```
┌─────────────────────────────────────────────────────────────┐
│                    Application Layer                        │
│                                                              │
│  ┌────────────────────────────────────────────────────────┐ │
│  │      FireflyCacheManager (implements CacheAdapter)     │ │
│  │                                                         │ │
│  │  • Direct cache operations (get, put, evict, etc.)    │ │
│  │  • Automatic fallback support                         │ │
│  │  • Health monitoring & statistics                     │ │
│  └────────────────────────────────────────────────────────┘ │
│                          │                                   │
│                          ▼                                   │
│  ┌────────────────────────────────────────────────────────┐ │
│  │              CacheAdapter (Port Interface)             │ │
│  │                                                         │ │
│  │  • Reactive operations (Mono/Flux)                    │ │
│  │  • TTL support                                        │ │
│  │  • Statistics & health                                │ │
│  └────────────────────────────────────────────────────────┘ │
└─────────────────────────┬───────────────────────────────────┘
                          │
        ┌─────────────────┼─────────────────┐
        ▼                 ▼                 ▼
┌──────────────┐  ┌──────────────┐  ┌──────────────┐
│   Caffeine   │  │    Redis     │  │    NoOp      │
│   Adapter    │  │   Adapter    │  │   Adapter    │
│ (In-Memory)  │  │(Distributed) │  │  (Disabled)  │
└──────────────┘  └──────────────┘  └──────────────┘
```

## Hexagonal Architecture (Ports and Adapters)

### Public API (Application Layer)
- **`FireflyCacheManager`**: Main cache interface
  - Implements `CacheAdapter` directly
  - Single instance per application
  - Delegates to primary cache with optional fallback
  - Provides all cache operations

### Port (Core Domain)
- **`CacheAdapter`**: Internal reactive interface
  - Defines cache operations contract
  - Used by adapters (Caffeine, Redis)
  - Not exposed to application code

### Adapters (Infrastructure Layer)
- **`CaffeineCacheAdapter`**: High-performance in-memory cache (always available)
- **`RedisCacheAdapter`**: Distributed cache with persistence (optional)
- **`NoOpCacheAdapter`**: Disabled cache for testing

## Public API

The main entry point is the `FireflyCacheManager` class:

```java
package com.firefly.common.cache.manager;

public class FireflyCacheManager implements CacheAdapter {

    // Constructor with primary cache only
    public FireflyCacheManager(CacheAdapter primaryCache);

    // Constructor with primary and fallback caches
    public FireflyCacheManager(CacheAdapter primaryCache, CacheAdapter fallbackCache);

    // Cache operations (from CacheAdapter interface)
    <K, V> Mono<Optional<V>> get(K key);
    <K, V> Mono<Optional<V>> get(K key, Class<V> valueType);
    <K, V> Mono<Void> put(K key, V value);
    <K, V> Mono<Void> put(K key, V value, Duration ttl);
    <K, V> Mono<Boolean> putIfAbsent(K key, V value);
    <K, V> Mono<Boolean> putIfAbsent(K key, V value, Duration ttl);
    <K> Mono<Boolean> evict(K key);
    <K> Mono<Boolean> exists(K key);
    Mono<Void> clear();
    <K> Mono<Set<K>> keys();
    Mono<Long> size();

    // Metadata
    String getCacheName();
    CacheType getCacheType();

    // Monitoring
    Mono<CacheHealth> getHealth();
    Mono<CacheStats> getStats();

    // Lifecycle
    void close();
}
```

## Key Format

Both Caffeine and Redis use **consistent key formatting**:

```
Format: keyPrefix:cacheName:key
Example: firefly:cache:default:user:123
```

This ensures:
- **Namespace isolation**: Different applications can share Redis
- **Easy debugging**: Keys are self-documenting
- **Consistent behavior**: Same format across all adapters

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
    <artifactId>spring-boot-starter-data-redis</artifactId>
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
import reactor.core.publisher.Mono;

@Service
public class MyService {

    private final FireflyCacheManager cacheManager;

    public MyService(FireflyCacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    public Mono<User> getUser(String userId) {
        // Direct API - manager IS the cache
        return cacheManager.get(userId, User.class)
            .flatMap(cached -> {
                if (cached.isPresent()) {
                    return Mono.just(cached.get());
                }
                return loadUser(userId)
                    .flatMap(user -> cacheManager.put(userId, user)
                        .thenReturn(user));
            });
    }

    private Mono<User> loadUser(String userId) {
        // Load from database
        return Mono.empty();
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

**Important:** Always use `com.firefly.common.cache.manager.FireflyCacheManager` (the concrete class) in `@ConditionalOnBean`.

## Auto-Configuration

The library provides two auto-configuration classes:

### 1. CacheAutoConfiguration (Always Loaded)
- **Package:** `com.firefly.common.cache.config`
- **Provides:**
  - `FireflyCacheManager` bean (primary cache manager)
  - `CacheSerializer` bean (JSON serialization)
  - `CaffeineCacheAdapter` bean (when Caffeine is on classpath - always)

### 2. RedisCacheAutoConfiguration (Conditionally Loaded)
- **Package:** `com.firefly.common.cache.config`
- **Condition:** Only loads when Redis classes are on classpath
- **Provides:**
  - `ReactiveRedisConnectionFactory` bean
  - `ReactiveRedisTemplate` bean
  - `RedisCacheAdapter` bean

### Bean Creation Logic

```java
@Bean
@Primary
public FireflyCacheManager fireflyCacheManager(
        CacheProperties properties,
        List<CacheAdapter> cacheAdapters) {

    // Select primary cache based on preference
    CacheAdapter primary = selectPrimaryCache(properties, cacheAdapters);

    // Optional fallback cache
    CacheAdapter fallback = selectFallbackCache(properties, cacheAdapters, primary);

    return new FireflyCacheManager(primary, fallback);
}
```

## Configuration Properties

```yaml
firefly:
  cache:
    enabled: true                    # Enable/disable cache library
    default-cache-type: AUTO         # AUTO, CAFFEINE, or REDIS
    metrics-enabled: true            # Enable metrics collection
    health-enabled: true             # Enable health checks

    caffeine:
      cache-name: default            # Cache name
      enabled: true                  # Enable/disable Caffeine
      key-prefix: "firefly:cache"    # Key prefix (format: prefix:cacheName:key)
      maximum-size: 10000            # Maximum entries
      expire-after-write: PT1H       # Expire after write
      expire-after-access: PT30M     # Expire after access
      record-stats: true             # Enable statistics

    redis:
      cache-name: default            # Cache name
      enabled: true                  # Enable/disable Redis
      host: localhost                # Redis host
      port: 6379                     # Redis port
      database: 0                    # Redis database
      key-prefix: "firefly:cache"    # Key prefix (format: prefix:cacheName:key)
      default-ttl: PT1H              # Default TTL
```

## Bean Matching Rules

### ✅ Correct Usage

```java
// Always use the full package name for clarity
import com.firefly.common.cache.manager.FireflyCacheManager;

// Inject the concrete class
@Autowired
private FireflyCacheManager cacheManager;

// Use in @ConditionalOnBean
@Bean
@ConditionalOnBean(FireflyCacheManager.class)
public MyService myService(FireflyCacheManager cacheManager) {
    return new MyService(cacheManager);
}
```

## Why This Architecture?

### Design Decisions

1. **Single Cache Instance**
   - Simplified from managing multiple caches to being the cache itself
   - Reduces complexity and API surface
   - Easier to understand and use

2. **Direct Implementation of CacheAdapter**
   - `FireflyCacheManager` implements `CacheAdapter` directly
   - No need for `selectCache()` or `getCache()` methods
   - Direct method calls: `cacheManager.get()`, `cacheManager.put()`

3. **Automatic Fallback Support**
   - Built-in primary/fallback pattern
   - Example: Redis (primary) → Caffeine (fallback)
   - Transparent to the application

4. **Consistent Key Format**
   - Both Caffeine and Redis use `keyPrefix:cacheName:key`
   - Makes switching between adapters seamless
   - Easier debugging and monitoring

### Evolution of the Architecture

**Version 1.0 (Initial - Over-complicated)**
- Multiple cache registration
- Strategy pattern for cache selection
- Complex API: `selectCache().get()`

**Version 2.0 (Current - Simplified)**
- Single cache instance
- Direct delegation pattern
- Simple API: `cacheManager.get()`
## Testing

### Unit Tests
```java
@Test
void testCacheManager() {
    CaffeineCacheConfig config = CaffeineCacheConfig.builder()
        .keyPrefix("test")
        .maximumSize(100L)
        .build();

    CacheAdapter primaryCache = new CaffeineCacheAdapter("test", config);
    FireflyCacheManager manager = new FireflyCacheManager(primaryCache);

    // Direct usage - manager IS the cache
    manager.put("key", "value").block();
    Optional<String> value = manager.get("key", String.class).block();

    assertNotNull(value);
    assertTrue(value.isPresent());
    assertEquals("value", value.get());
}
```

### Integration Tests
```java
@SpringBootTest
@TestPropertySource(properties = {
    "firefly.cache.enabled=true",
    "firefly.cache.default-cache-type=CAFFEINE"
})
class MyIntegrationTest {

    @Autowired
    private FireflyCacheManager cacheManager;

    @Test
    void testCacheIsAvailable() {
        assertNotNull(cacheManager);

        // Test cache operations
        StepVerifier.create(cacheManager.put("test-key", "test-value"))
            .verifyComplete();

        StepVerifier.create(cacheManager.get("test-key", String.class))
            .assertNext(value -> {
                assertTrue(value.isPresent());
                assertEquals("test-value", value.get());
            })
            .verifyComplete();
    }
}
```

## Best Practices

1. **Always use the full package name**
   ```java
   import com.firefly.common.cache.manager.FireflyCacheManager;
   ```

2. **Use `@ConditionalOnBean` for optional features**
   ```java
   @Bean
   @ConditionalOnBean(FireflyCacheManager.class)
   public MyService myService(FireflyCacheManager cacheManager) {
       return new MyService(cacheManager);
   }
   ```

3. **Use the reactive API**
   - All operations return `Mono` or `Flux`
   - Handle `Optional` properly: `Mono<Optional<T>>`
   - Use `StepVerifier` for testing

4. **Configure via properties**
   - Don't create beans manually
   - Use `application.yml` for configuration
   - Let auto-configuration do its job

5. **Set appropriate key prefixes**
   - Use meaningful prefixes for namespace isolation
   - Format: `keyPrefix:cacheName:key`
   - Example: `myapp:cache:default:user:123`

## Summary

- ✅ **Public API:** `com.firefly.common.cache.manager.FireflyCacheManager`
- ✅ **Internal API:** `com.firefly.common.cache.core.CacheAdapter`
- ✅ **Single cache instance:** One cache per application
- ✅ **Automatic fallback:** Primary/fallback pattern built-in
- ✅ **Consistent key format:** `keyPrefix:cacheName:key`
- ✅ **Redis is optional:** Works without Redis dependencies
- ✅ **Auto-configuration:** Automatically sets up based on classpath
- ✅ **Reactive API:** Non-blocking operations with Project Reactor
- ✅ **Bean matching:** Use `FireflyCacheManager.class` in conditions
- ✅ **Clean architecture:** Clear separation of concerns

For more information, see:
- [Optional Dependencies Guide](OPTIONAL_DEPENDENCIES.md)
- [README](../README.md)

