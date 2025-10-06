# Architecture Guide

This document explains the architecture and design principles of the Firefly Common Cache Library.

## Table of Contents

- [Overview](#overview)
- [Hexagonal Architecture](#hexagonal-architecture)
- [Core Components](#core-components)
- [Package Structure](#package-structure)
- [Design Patterns](#design-patterns)
- [Data Flow](#data-flow)
- [Extension Points](#extension-points)

## Overview

The Firefly Common Cache Library is built following **Hexagonal Architecture** (also known as Ports and Adapters) principles. This architectural style ensures:

- **Separation of Concerns**: Business logic is isolated from infrastructure
- **Testability**: Core logic can be tested without external dependencies
- **Flexibility**: Easy to swap cache implementations
- **Maintainability**: Clear boundaries between components

## Hexagonal Architecture

### Architecture Diagram

```
┌──────────────────────────────────────────────────────────────────┐
│                        Application Core                          │
│                                                                  │
│   ┌──────────────────────────────────────────────────────────┐   │
│   │              FireflyCacheManager                         │   │
│   │  - Orchestrates cache operations                         │   │
│   │  - Manages multiple cache instances                      │   │
│   │  - Provides unified API                                  │   │
│   └──────────────────────────────────────────────────────────┘   │
│                                │                                 │
│                                ▼                                 │
│   ┌──────────────────────────────────────────────────────────┐   │
│   │         CacheAdapter (Port Interface)                    │   │
│   │  - Defines cache operations contract                     │   │
│   │  - Reactive API (Mono/Flux)                              │   │
│   │  - Type-safe operations                                  │   │
│   └──────────────────────────────────────────────────────────┘   │
│                                                                  │
└────────────────────────────────┬─────────────────────────────────┘
                                 │
              ┌──────────────────┼──────────────────┐
              ▼                  ▼                  ▼
       ┌──────────────┐   ┌──────────────┐   ┌──────────────┐
       │   Caffeine   │   │    Redis     │   │    NoOp      │
       │   Adapter    │   │   Adapter    │   │   Adapter    │
       │              │   │              │   │              │
       │ (In-Memory)  │   │(Distributed) │   │  (Fallback)  │
       └──────────────┘   └──────────────┘   └──────────────┘
             │                   │                   │
             ▼                   ▼                   ▼
       ┌──────────────┐   ┌──────────────┐   ┌──────────────┐
       │   Caffeine   │   │    Redis     │   │     None     │
       │   Library    │   │   (Lettuce)  │   │              │
       └──────────────┘   └──────────────┘   └──────────────┘
```

### Layers

1. **Application Core** (Domain Layer)
   - Contains business logic and domain models
   - Independent of infrastructure concerns
   - Defines ports (interfaces) for external interactions

2. **Ports** (Interfaces)
   - `CacheAdapter`: Primary port for cache operations
   - `CacheSelectionStrategy`: Strategy for selecting cache instances
   - `CacheSerializer`: Serialization abstraction

3. **Adapters** (Infrastructure Layer)
   - `CaffeineCacheAdapter`: In-memory cache implementation
   - `RedisCacheAdapter`: Distributed cache implementation
   - `NoOpCacheAdapter`: Null object pattern for disabled caching

## Core Components

### 1. FireflyCacheManager

**Location**: `com.firefly.common.cache.manager.FireflyCacheManager`

**Responsibilities**:
- Manages multiple cache instances
- Provides unified API for cache operations
- Delegates to appropriate cache adapter
- Aggregates health and statistics

**Key Methods**:
```java
// Default cache operations
<K, V> Mono<Optional<V>> get(K key, Class<V> valueType)
<K, V> Mono<Void> put(K key, V value, Duration ttl)
<K> Mono<Boolean> evict(K key)

// Named cache operations
<K, V> Mono<Optional<V>> get(String cacheName, K key)
<K, V> Mono<Void> put(String cacheName, K key, V value)

// Management
void registerCache(String name, CacheAdapter adapter)
CacheAdapter getCache(String name)
```

### 2. CacheAdapter (Port)

**Location**: `com.firefly.common.cache.core.CacheAdapter`

**Purpose**: Defines the contract for all cache implementations

**Key Operations**:
- `get(K key)`: Retrieve value
- `put(K key, V value, Duration ttl)`: Store value with TTL
- `putIfAbsent(K key, V value)`: Conditional store
- `evict(K key)`: Remove entry
- `clear()`: Remove all entries
- `exists(K key)`: Check existence
- `keys()`: Get all keys
- `getStats()`: Get statistics
- `getHealth()`: Get health status

### 3. Cache Adapters (Implementations)

#### CaffeineCacheAdapter

**Location**: `com.firefly.common.cache.adapter.caffeine.CaffeineCacheAdapter`

**Characteristics**:
- In-memory cache using Caffeine library
- High performance, low latency
- Size-based and time-based eviction
- Statistics tracking
- Not distributed (local to JVM)

**Configuration**: `CaffeineCacheConfig`
- Maximum size
- Expire after write/access
- Refresh after write
- Weak/soft references

#### RedisCacheAdapter

**Location**: `com.firefly.common.cache.adapter.redis.RedisCacheAdapter`

**Characteristics**:
- Distributed cache using Redis
- Persistence support
- Pub/sub capabilities
- Cluster support
- Higher latency than in-memory

**Configuration**: `RedisCacheConfig`
- Connection settings (host, port, database)
- Authentication (username, password)
- Timeouts
- Pool settings
- SSL support

### 4. CacheSelectionStrategy

**Location**: `com.firefly.common.cache.manager.CacheSelectionStrategy`

**Purpose**: Determines which cache to use when multiple are available

**Implementations**:
- `AutoCacheSelectionStrategy`: Automatically selects based on availability and health

### 5. CacheSerializer

**Location**: `com.firefly.common.cache.serialization.CacheSerializer`

**Purpose**: Handles object serialization/deserialization

**Implementations**:
- `JsonCacheSerializer`: JSON-based serialization using Jackson

### 6. Configuration

**Location**: `com.firefly.common.cache.properties.CacheProperties`

**Purpose**: Centralized configuration properties

**Structure**:
```java
CacheProperties
├── enabled: boolean
├── defaultCacheType: CacheType
├── defaultCacheName: String
├── metricsEnabled: boolean
├── healthEnabled: boolean
├── caches: Map<String, CacheConfig>
├── caffeine: Map<String, CaffeineConfig>
└── redis: Map<String, RedisConfig>
```

### 7. Auto-Configuration

**Location**: `com.firefly.common.cache.config.CacheAutoConfiguration`

**Purpose**: Spring Boot auto-configuration

**Responsibilities**:
- Registers cache adapters based on configuration
- Creates FireflyCacheManager bean
- Sets up serializers
- Configures health indicators
- Enables metrics collection

## Package Structure

```
com.firefly.common.cache
├── adapter/                    # Cache adapter implementations
│   ├── caffeine/              # Caffeine adapter
│   │   ├── CaffeineCacheAdapter.java
│   │   └── CaffeineCacheConfig.java
│   └── redis/                 # Redis adapter
│       ├── RedisCacheAdapter.java
│       └── RedisCacheConfig.java
├── annotation/                # Cache annotations
│   ├── Cacheable.java
│   ├── CacheEvict.java
│   ├── CachePut.java
│   ├── Caching.java
│   └── EnableCaching.java
├── config/                    # Configuration classes
│   └── CacheAutoConfiguration.java
├── core/                      # Core interfaces and types
│   ├── CacheAdapter.java      # Main port interface
│   ├── CacheHealth.java
│   ├── CacheStats.java
│   └── CacheType.java
├── exception/                 # Exception classes
│   └── CacheException.java
├── health/                    # Health indicators
│   └── CacheHealthIndicator.java
├── manager/                   # Cache manager
│   ├── FireflyCacheManager.java
│   ├── CacheSelectionStrategy.java
│   └── AutoCacheSelectionStrategy.java
├── metrics/                   # Metrics support
│   └── CacheMetrics.java
├── properties/                # Configuration properties
│   └── CacheProperties.java
└── serialization/             # Serialization
    ├── CacheSerializer.java
    ├── JsonCacheSerializer.java
    └── SerializationException.java
```

## Design Patterns

### 1. Hexagonal Architecture (Ports and Adapters)

**Purpose**: Isolate business logic from infrastructure

**Implementation**:
- `CacheAdapter` is the port (interface)
- `CaffeineCacheAdapter`, `RedisCacheAdapter` are adapters (implementations)

### 2. Strategy Pattern

**Purpose**: Select cache implementation at runtime

**Implementation**:
- `CacheSelectionStrategy` interface
- `AutoCacheSelectionStrategy` implementation

### 3. Facade Pattern

**Purpose**: Provide simplified interface to complex subsystem

**Implementation**:
- `FireflyCacheManager` acts as facade over multiple cache adapters

### 4. Builder Pattern

**Purpose**: Construct complex configuration objects

**Implementation**:
- `CaffeineCacheConfig.builder()`
- `RedisCacheConfig.builder()`

### 5. Null Object Pattern

**Purpose**: Provide default behavior when caching is disabled

**Implementation**:
- `NoOpCacheAdapter` (future implementation)

## Data Flow

### Cache Read Operation

```
User Code
    │
    ▼
FireflyCacheManager.get(key)
    │
    ├─→ Select cache (via strategy)
    │
    ▼
CacheAdapter.get(key)
    │
    ├─→ CaffeineCacheAdapter
    │   └─→ Caffeine.getIfPresent(key)
    │
    └─→ RedisCacheAdapter
        └─→ RedisTemplate.opsForValue().get(key)
            └─→ Deserialize value
```

### Cache Write Operation

```
User Code
    │
    ▼
FireflyCacheManager.put(key, value, ttl)
    │
    ├─→ Select cache (via strategy)
    │
    ▼
CacheAdapter.put(key, value, ttl)
    │
    ├─→ CaffeineCacheAdapter
    │   ├─→ Caffeine.put(key, value)
    │   └─→ Track expiration time
    │
    └─→ RedisCacheAdapter
        ├─→ Serialize value
        └─→ RedisTemplate.opsForValue().set(key, value, ttl)
```

## Extension Points

### Adding a New Cache Adapter

1. Implement `CacheAdapter` interface
2. Create configuration class
3. Add auto-configuration bean
4. Register with `FireflyCacheManager`

Example:

```java
public class MemcachedCacheAdapter implements CacheAdapter {
    // Implement all methods
}

@Configuration
public class MemcachedAutoConfiguration {
    @Bean
    @ConditionalOnProperty("firefly.cache.memcached.enabled")
    public MemcachedCacheAdapter memcachedCache() {
        return new MemcachedCacheAdapter(config);
    }
}
```

### Custom Serialization

Implement `CacheSerializer` interface:

```java
public class ProtobufSerializer implements CacheSerializer {
    @Override
    public Object serialize(Object value) {
        // Protobuf serialization
    }
    
    @Override
    public <T> T deserialize(Object data, Class<T> type) {
        // Protobuf deserialization
    }
}
```

### Custom Selection Strategy

Implement `CacheSelectionStrategy`:

```java
public class LoadBasedSelectionStrategy implements CacheSelectionStrategy {
    @Override
    public Optional<CacheAdapter> selectCache(Collection<CacheAdapter> caches) {
        // Select based on load metrics
    }
}
```

## Thread Safety

- `FireflyCacheManager`: Thread-safe (uses `ConcurrentHashMap`)
- `CaffeineCacheAdapter`: Thread-safe (Caffeine is thread-safe)
- `RedisCacheAdapter`: Thread-safe (Lettuce is thread-safe)

## Performance Considerations

1. **Caffeine**: Optimized for high-throughput, low-latency scenarios
2. **Redis**: Network overhead, suitable for distributed scenarios
3. **Serialization**: JSON is readable but slower than binary formats
4. **Reactive API**: Non-blocking operations prevent thread pool exhaustion

## Testing Strategy

- **Unit Tests**: Test each component in isolation
- **Integration Tests**: Test with real cache implementations
- **TestContainers**: Use for Redis integration tests
- **Mock Adapters**: Use for testing business logic

## Future Enhancements

- Aspect-based annotation processing
- Multi-level caching (L1/L2)
- Cache warming strategies
- Advanced eviction policies
- Distributed cache synchronization

