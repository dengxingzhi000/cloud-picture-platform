package com.cn.cloudpictureplatform.config.cache;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

public class FallbackCacheManager implements CacheManager {

    private final CacheManager primary;
    private final CacheManager fallback;

    public FallbackCacheManager(CacheManager primary, CacheManager fallback) {
        this.primary = primary;
        this.fallback = fallback;
    }

    @Override
    @Nullable
    public Cache getCache(@NonNull String name) {
        Cache primaryCache = primary.getCache(name);
        Cache fallbackCache = fallback.getCache(name);
        if (primaryCache == null) {
            return fallbackCache;
        }
        if (fallbackCache == null) {
            return primaryCache;
        }
        return new FallbackCache(primaryCache, fallbackCache);
    }

    @Override
    @NonNull
    public Collection<String> getCacheNames() {
        Set<String> names = new LinkedHashSet<>();
        names.addAll(primary.getCacheNames());
        names.addAll(fallback.getCacheNames());
        return names;
    }
}
