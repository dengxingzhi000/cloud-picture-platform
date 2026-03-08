package com.cn.cloudpictureplatform.config.cache;

import java.util.concurrent.Callable;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;

public class FallbackCache implements Cache {
    private static final Logger log = LoggerFactory.getLogger(FallbackCache.class);

    private final Cache primary;
    private final Cache fallback;

    public FallbackCache(Cache primary, Cache fallback) {
        this.primary = primary;
        this.fallback = fallback;
    }

    @Override
    @NonNull
    public String getName() {
        return primary.getName();
    }

    @Override
    @NonNull
    public Object getNativeCache() {
        return primary.getNativeCache();
    }

    @Override
    @Nullable
    public ValueWrapper get(@NonNull Object key) {
        try {
            ValueWrapper value = primary.get(key);
            if (value != null) {
                return value;
            }
        } catch (RuntimeException ex) {
            log.warn("Cache get failed on {} key={}", primary.getName(), key, ex);
        }
        return fallback.get(key);
    }

    @Override
    @Nullable
    public <T> T get(@NonNull Object key, @Nullable Class<T> type) {
        try {
            T value = primary.get(key, type);
            if (value != null || (type == null && primary.get(key) != null)) {
                return value;
            }
        } catch (RuntimeException ex) {
            log.warn("Cache get(type) failed on {} key={}", primary.getName(), key, ex);
        }
        return fallback.get(key, type);
    }

    @Override
    @Nullable
    public <T> T get(@NonNull Object key, @NonNull Callable<T> valueLoader) {
        try {
            T value = primary.get(key, valueLoader);
            if (value != null) {
                try {
                    fallback.put(key, value);
                } catch (RuntimeException ex) {
                    log.warn("Cache put to fallback failed on {} key={}", fallback.getName(), key, ex);
                }
            }
            return value;
        } catch (RuntimeException ex) {
            log.warn("Cache get(loader) failed on {} key={}", primary.getName(), key, ex);
            return fallback.get(key, valueLoader);
        }
    }

    @Override
    public void put(@NonNull Object key, @Nullable Object value) {
        try {
            primary.put(key, value);
        } catch (RuntimeException ex) {
            log.warn("Cache put failed on {} key={}", primary.getName(), key, ex);
        }
        try {
            fallback.put(key, value);
        } catch (RuntimeException ex) {
            log.warn("Cache put fallback failed on {} key={}", fallback.getName(), key, ex);
        }
    }

    @Override
    @Nullable
    public ValueWrapper putIfAbsent(@NonNull Object key, @Nullable Object value) {
        ValueWrapper primaryResult = null;
        try {
            primaryResult = primary.putIfAbsent(key, value);
        } catch (RuntimeException ex) {
            log.warn("Cache putIfAbsent failed on {} key={}", primary.getName(), key, ex);
        }
        ValueWrapper fallbackResult = null;
        try {
            fallbackResult = fallback.putIfAbsent(key, value);
        } catch (RuntimeException ex) {
            log.warn("Cache putIfAbsent fallback failed on {} key={}", fallback.getName(), key, ex);
        }
        return primaryResult != null ? primaryResult : fallbackResult;
    }

    @Override
    public void evict(@NonNull Object key) {
        try {
            primary.evict(key);
        } catch (RuntimeException ex) {
            log.warn("Cache evict failed on {} key={}", primary.getName(), key, ex);
        }
        try {
            fallback.evict(key);
        } catch (RuntimeException ex) {
            log.warn("Cache evict fallback failed on {} key={}", fallback.getName(), key, ex);
        }
    }

    @Override
    public void clear() {
        try {
            primary.clear();
        } catch (RuntimeException ex) {
            log.warn("Cache clear failed on {}", primary.getName(), ex);
        }
        try {
            fallback.clear();
        } catch (RuntimeException ex) {
            log.warn("Cache clear fallback failed on {}", fallback.getName(), ex);
        }
    }
}
