# Firefly Common Cache Library

[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2-green.svg)](https://spring.io/projects/spring-boot)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)

A unified caching library providing standardized cache abstractions with Caffeine and Redis implementations, following hexagonal architecture principles.

---

## 📋 Table of Contents

- [Features](#-features)
- [Quick Start](#-quick-start)
  - [1. Add Dependency](#1-add-dependency)
  - [2. Enable Caching](#2-enable-caching)
  - [3. Configure Properties](#3-configure-properties)
- [Usage](#-usage)
  - [Programmatic API](#programmatic-api)
  - [Declarative Annotations](#declarative-annotations)
  - [Cache-Specific Operations](#cache-specific-operations)
- [Architecture](#-architecture)
  - [Hexagonal Architecture](#hexagonal-architecture)
  - [Key Components](#key-components)
- [Configuration](#-configuration)
  - [Cache Types](#cache-types)
  - [Caffeine Configuration](#caffeine-configuration)
  - [Redis Configuration](#redis-configuration)
- [Monitoring](#-monitoring)
  - [Health Checks](#health-checks)
  - [Metrics](#metrics)
  - [Statistics API](#statistics-api)
- [Testing](#-testing)
- [Migration Guide](#-migration-guide)
- [Best Practices](#-best-practices)
- [Troubleshooting](#-troubleshooting)
- [Documentation](#-documentation)
- [Contributing](#-contributing)
- [License](#-license)

---

## ✨ Features

- **Zero Configuration**: Works out of the box with Spring Boot auto-configuration
- **Hexagonal Architecture**: Clean separation between business logic and infrastructure
- **Multiple Cache Providers**: Support for Caffeine (in-memory) and Redis (distributed)
- **Reactive API**: Non-blocking operations using Project Reactor
- **Auto-Configuration**: Automatic Spring Boot configuration with sensible defaults
- **Health Monitoring**: Built-in health checks and metrics
- **Flexible Serialization**: JSON serialization with Jackson support
- **Declarative Caching**: Annotation-based caching support (programmatic API recommended)
- **TTL Support**: Time-to-live configuration for cache entries
- **Statistics**: Comprehensive cache statistics and monitoring

## 🚀 Quick Start

### 1. Add Dependency

```xml
<dependency>
    <groupId>com.firefly</groupId>
    <artifactId>lib-common-cache</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

**That's it!** The library auto-configures with sensible defaults. No additional configuration required.

### 2. (Optional) Configure Properties

Customize the cache behavior via `application.yml`:

```yaml
firefly:
  cache:
    enabled: true
    default-cache-type: CAFFEINE  # Options: CAFFEINE, REDIS, AUTO, NOOP
    default-cache-name: default
    metrics-enabled: true
    health-enabled: true

    # Caffeine configuration
    caffeine:
      default:
        enabled: true
        maximum-size: 1000
        expire-after-write: PT1H
        record-stats: true

    # Redis configuration (optional)
    redis:
      default:
        enabled: true
        host: localhost
        port: 6379
        database: 0
        key-prefix: "firefly:cache"
```

## 💻 Usage

### Programmatic API

```java
@Service
public class UserService {
    
    @Autowired
    private FireflyCacheManager cacheManager;
    
    public Mono<User> getUser(String userId) {
        return cacheManager.get(userId, User.class)
            .flatMap(cached -> {
                if (cached.isPresent()) {
                    return Mono.just(cached.get());
                }
                return loadUserFromDatabase(userId)
                    .flatMap(user -> cacheManager.put(userId, user, Duration.ofMinutes(30))
                        .thenReturn(user));
            });
    }
    
    private Mono<User> loadUserFromDatabase(String userId) {
        // Implementation details
        return Mono.empty();
    }
}
```

### Declarative Annotations

> **Note**: Annotation support (@Cacheable, @CacheEvict, @CachePut) is defined but aspect implementation is not yet complete. Use the programmatic API for production use.

```java
@Service
public class ProductService {

    @Cacheable(value = "products", key = "#productId", ttl = "PT2H")
    public Mono<Product> getProduct(String productId) {
        return productRepository.findById(productId);
    }

    @CacheEvict(value = "products", key = "#product.id")
    public Mono<Product> updateProduct(Product product) {
        return productRepository.save(product);
    }

    @CachePut(value = "products", key = "#result.id")
    public Mono<Product> createProduct(Product product) {
        return productRepository.save(product);
    }
}
```

### Cache-Specific Operations

```java
@Component
public class CacheOperations {

    @Autowired
    private FireflyCacheManager cacheManager;

    public void performOperations() {
        // Use specific cache
        cacheManager.put("user-cache", "key1", "value1").subscribe();

        // Conditional put
        cacheManager.putIfAbsent("key2", "value2", Duration.ofMinutes(10))
            .doOnNext(wasInserted -> {
                if (wasInserted) {
                    log.info("Value was inserted");
                } else {
                    log.info("Key already existed");
                }
            })
            .subscribe();

        // Check existence
        cacheManager.exists("key1")
            .doOnNext(exists -> log.info("Key exists: {}", exists))
            .subscribe();

        // Evict a key
        cacheManager.evict("key1")
            .doOnNext(removed -> log.info("Key removed: {}", removed))
            .subscribe();

        // Clear entire cache
        cacheManager.clear()
            .doOnSuccess(v -> log.info("Cache cleared"))
            .subscribe();
    }
}
```

## 🏗️ Architecture

### Hexagonal Architecture

The library follows hexagonal architecture principles:

```
┌───────────────────────────────────────────────────────────┐
│                      Application Core                     │
│  ┌─────────────────┐    ┌──────────────────────────────┐  │
│  │ FireflyCache    │    │     Cache Annotations        │  │
│  │ Manager         │    │     (@Cacheable, etc.)       │  │
│  └─────────────────┘    └──────────────────────────────┘  │
│              │                          │                 │
│              ▼                          ▼                 │
│  ┌────────────────────────────────────────────────────┐   │
│  │                CacheAdapter (Port)                 │   │
│  └────────────────────────────────────────────────────┘   │
└───────────────────────────┬───────────────────────────────┘
                            │
        ┌───────────────────┼───────────────────┐
        ▼                   ▼                   ▼
┌──────────────┐    ┌──────────────┐    ┌──────────────┐
│   Caffeine   │    │    Redis     │    │    NoOp      │
│   Adapter    │    │   Adapter    │    │   Adapter    │
│ (In-Memory)  │    │(Distributed) │    │  (Fallback)  │
└──────────────┘    └──────────────┘    └──────────────┘
```

### Key Components

- **CacheAdapter**: Port interface defining cache operations
- **FireflyCacheManager**: Orchestrates multiple cache adapters
- **CacheSelectionStrategy**: Determines which cache to use
- **CacheSerializer**: Handles object serialization
- **Cache Annotations**: Declarative caching support
- **Health & Metrics**: Monitoring and observability

## ⚙️ Configuration

### Cache Types

- `CAFFEINE`: High-performance in-memory cache
- `REDIS`: Distributed cache with persistence
- `AUTO`: Automatically selects the best available cache
- `NOOP`: Disables caching (useful for testing)

### Caffeine Configuration

```yaml
firefly:
  cache:
    caffeine:
      default:
        maximum-size: 10000          # Maximum number of entries
        expire-after-write: PT1H     # Expire after write duration
        expire-after-access: PT30M   # Expire after access duration
        refresh-after-write: PT45M   # Refresh after write duration
        record-stats: true           # Enable statistics
        weak-keys: false             # Use weak references for keys
        weak-values: false           # Use weak references for values
        soft-values: false           # Use soft references for values
```

### Redis Configuration

```yaml
firefly:
  cache:
    redis:
      default:
        host: localhost
        port: 6379
        database: 0
        password: secret             # Optional
        username: user               # Optional (Redis 6+)
        connection-timeout: PT10S
        command-timeout: PT5S
        key-prefix: "firefly:cache"
        default-ttl: PT30M           # Optional default TTL
        ssl: false
        max-pool-size: 8
        min-pool-size: 0
```

## 📊 Monitoring

### Health Checks

The library provides health indicators for Spring Boot Actuator:

```bash
# Check cache health
GET /actuator/health/cache

# Response
{
  "status": "UP",
  "details": {
    "totalCaches": 2,
    "healthyCaches": 2,
    "unhealthyCaches": 0,
    "caches": {
      "default": {
        "type": "caffeine",
        "status": "UP",
        "available": true,
        "configured": true,
        "responseTimeMs": 2
      }
    }
  }
}
```

### Metrics

Cache metrics are exposed for monitoring:

- Request count
- Hit/miss ratios
- Cache size
- Response times
- Error rates

### Statistics API

```java
// Get statistics for all caches
cacheManager.getStats()
    .doOnNext(stats -> {
        log.info("Cache: {} - Hits: {}, Misses: {}, Hit Rate: {}%", 
            stats.getCacheName(), 
            stats.getHitCount(), 
            stats.getMissCount(), 
            stats.getHitRate());
    })
    .subscribe();
```

## 🧪 Testing

### Test Configuration

```yaml
# application-test.yml
firefly:
  cache:
    enabled: true
    default-cache-type: caffeine  # Use Caffeine for tests
    caffeine:
      default:
        maximum-size: 100
        expire-after-write: PT1M
```

### Integration Tests

```java
@SpringBootTest
@TestPropertySource(locations = "classpath:application-test.yml")
class CacheIntegrationTest {
    
    @Autowired
    private FireflyCacheManager cacheManager;
    
    @Test
    void shouldCacheAndRetrieveValues() {
        StepVerifier.create(cacheManager.put("key1", "value1"))
            .verifyComplete();
        
        StepVerifier.create(cacheManager.get("key1"))
            .assertNext(result -> {
                assertThat(result).isPresent();
                assertThat(result.get()).isEqualTo("value1");
            })
            .verifyComplete();
    }
}
```

## 🔄 Migration Guide

### From Spring Cache

If you're migrating from Spring's `@Cacheable`:

1. Replace Spring cache annotations with Firefly annotations
2. Update configuration from `spring.cache.*` to `firefly.cache.*`
3. Use reactive return types (`Mono`/`Flux`) where appropriate

### Performance Considerations

- **Caffeine**: Best for high-frequency, low-latency access patterns
- **Redis**: Better for distributed applications and large datasets
- **Serialization**: JSON is human-readable but may be slower than binary formats
- **TTL**: Use appropriate TTL values to balance freshness and performance

## 💡 Best Practices

1. **Choose the right cache type**:
   - Use Caffeine for local, high-speed caching
   - Use Redis for distributed caching and persistence
   - Use AUTO for automatic selection

2. **Set appropriate TTL values**:
   - Short TTL for frequently changing data
   - Long TTL for stable reference data

3. **Monitor cache performance**:
   - Watch hit/miss ratios
   - Monitor memory usage
   - Track response times

4. **Handle cache failures gracefully**:
   - Always provide fallback mechanisms
   - Log cache errors appropriately
   - Consider circuit breaker patterns

5. **Use meaningful cache keys**:
   - Include version information when needed
   - Use consistent naming conventions
   - Avoid key collisions

## 🔧 Troubleshooting

### Common Issues

1. **Cache not working**:
   - Verify `@EnableCaching` is present
   - Check configuration properties
   - Ensure cache adapters are available on classpath

2. **Redis connection issues**:
   - Verify Redis server is running
   - Check network connectivity
   - Validate credentials

3. **Serialization errors**:
   - Ensure objects are serializable
   - Check Jackson configuration
   - Consider custom serializers for complex types

4. **Memory issues**:
   - Adjust maximum cache size
   - Set appropriate TTL values
   - Monitor cache statistics

### Enable Debug Logging

```yaml
logging:
  level:
    com.firefly.common.cache: DEBUG
```

## 📚 Documentation

For more detailed information, please refer to the documentation in the `docs/` folder:

- **[Quick Start Guide](docs/QUICKSTART.md)** - Get started quickly with step-by-step instructions
- **[Auto-Configuration Guide](docs/AUTO_CONFIGURATION.md)** - Spring Boot auto-configuration details
- **[Architecture Guide](docs/ARCHITECTURE.md)** - Understand the hexagonal architecture and design patterns
- **[Configuration Reference](docs/CONFIGURATION.md)** - Complete configuration options and examples
- **[API Reference](docs/API_REFERENCE.md)** - Detailed API documentation
- **[Examples](docs/EXAMPLES.md)** - Practical examples and use cases
- **[Monitoring Guide](docs/MONITORING.md)** - Metrics, health checks, and observability
- **[Testing Guide](docs/TESTING.md)** - How to test code using the cache library

## 🤝 Contributing

Contributions are welcome! Please see our contributing guidelines and code of conduct.

## 📄 License

Licensed under the Apache License, Version 2.0. See [LICENSE](LICENSE) for details.

---

**Made with ❤️ by the Firefly Team**
