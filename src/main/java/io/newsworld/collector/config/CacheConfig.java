package io.newsworld.collector.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Configuration
public class CacheConfig {

    public static final String CACHE_PAGES      = "article-pages";
    public static final String CACHE_TRANSLATE  = "llm-translate";
    public static final String CACHE_ANALYZE    = "llm-analyze";

    @Bean
    public CacheManager cacheManager() {
        SimpleCacheManager mgr = new SimpleCacheManager();
        mgr.setCaches(List.of(
            build(CACHE_PAGES,     500,  1, TimeUnit.HOURS),
            build(CACHE_TRANSLATE, 1000, 24, TimeUnit.HOURS),
            build(CACHE_ANALYZE,   50,   48, TimeUnit.HOURS)
        ));
        return mgr;
    }

    private static CaffeineCache build(String name, int maxSize, long duration, TimeUnit unit) {
        return new CaffeineCache(name, Caffeine.newBuilder()
            .maximumSize(maxSize)
            .expireAfterWrite(duration, unit)
            .recordStats()
            .build());
    }
}
