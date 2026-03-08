package com.cn.cloudpictureplatform.config;

import java.time.Duration;
import java.util.List;
import com.cn.cloudpictureplatform.config.cache.FallbackCacheManager;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.RedisSerializer;

@Configuration
public class CacheConfig {
    private static final Logger log = LoggerFactory.getLogger(CacheConfig.class);

    @Bean
    public RedisCacheConfiguration redisCacheConfiguration() {
        return RedisCacheConfiguration.defaultCacheConfig()
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(
                        RedisSerializer.json()
                ))
                .disableCachingNullValues();
    }

    @Bean
    public RedisCacheManager redisCacheManager(
            RedisConnectionFactory connectionFactory,
            RedisCacheConfiguration redisCacheConfiguration
    ) {
        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(redisCacheConfiguration)
                .withCacheConfiguration(
                        "publicGallery",
                        redisCacheConfiguration.entryTtl(Duration.ofMinutes(2))
                )
                .withCacheConfiguration(
                        "tagCatalog",
                        redisCacheConfiguration.entryTtl(Duration.ofMinutes(30))
                )
                .withCacheConfiguration(
                        "pictureSearch",
                        redisCacheConfiguration.entryTtl(Duration.ofSeconds(30))
                )
                .withCacheConfiguration(
                        "adminPending",
                        redisCacheConfiguration.entryTtl(Duration.ofSeconds(30))
                )
                .withCacheConfiguration(
                        "moderationHistory",
                        redisCacheConfiguration.entryTtl(Duration.ofSeconds(30))
                )
                .build();
    }

    @Bean
    public CacheManager caffeineCacheManager() {
        SimpleCacheManager manager = new SimpleCacheManager();
        manager.setCaches(List.of(
                buildCaffeineCache("publicGallery", Duration.ofMinutes(2), 2000),
                buildCaffeineCache("tagCatalog", Duration.ofMinutes(30), 5000),
                buildCaffeineCache("pictureSearch", Duration.ofSeconds(30), 3000),
                buildCaffeineCache("adminPending", Duration.ofSeconds(30), 1000),
                buildCaffeineCache("moderationHistory", Duration.ofSeconds(30), 1000)
        ));
        return manager;
    }

    @Bean
    @Primary
    public CacheManager cacheManager(
            RedisCacheManager redisCacheManager,
            @Qualifier("caffeineCacheManager") CacheManager caffeineCacheManager
    ) {
        return new FallbackCacheManager(redisCacheManager, caffeineCacheManager);
    }

    @Bean
    public CacheErrorHandler cacheErrorHandler() {
        return new CacheErrorHandler() {
            @Override
            public void handleCacheGetError(
                    @NonNull RuntimeException exception,
                    @NonNull Cache cache,
                    @NonNull Object key
            ) {
                log.warn("Cache get error on {} key={}", cache.getName(), key, exception);
            }

            @Override
            public void handleCachePutError(
                    @NonNull RuntimeException exception,
                    @NonNull Cache cache,
                    @NonNull Object key,
                    @Nullable Object value
            ) {
                log.warn("Cache put error on {} key={}", cache.getName(), key, exception);
            }

            @Override
            public void handleCacheEvictError(
                    @NonNull RuntimeException exception,
                    @NonNull Cache cache,
                    @NonNull Object key
            ) {
                log.warn("Cache evict error on {} key={}", cache.getName(), key, exception);
            }

            @Override
            public void handleCacheClearError(@NonNull RuntimeException exception, @NonNull Cache cache) {
                log.warn("Cache clear error on {}", cache.getName(), exception);
            }
        };
    }

    private CaffeineCache buildCaffeineCache(String name, Duration ttl, long maxSize) {
        return new CaffeineCache(
                name,
                Caffeine.newBuilder()
                        .expireAfterWrite(ttl)
                        .maximumSize(maxSize)
                        .build()
        );
    }
}
