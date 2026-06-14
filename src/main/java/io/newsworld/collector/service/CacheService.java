package io.newsworld.collector.service;

import io.newsworld.collector.config.CacheConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class CacheService {

    private final CacheManager cacheManager;

    public void evictSingleCacheValue(String cacheName, String cacheKey) {
        cacheManager.getCache(cacheName).evict(cacheKey);
    }

    public void evictAllCacheValues(String cacheName) {
        cacheManager.getCache(cacheName).clear();
    }

    public void evictAllCaches() {
        cacheManager.getCacheNames()
                .forEach(name -> cacheManager.getCache(name).clear());
    }

    /** Vide le cache de pages toutes les heures (les caches LLM ont leur propre TTL Caffeine). */
    @Scheduled(fixedRateString = "${newsworld.collector.cache.evict-interval-ms:3600000}")
    public void evictAllCachesAtIntervals() {
        log.info("Evicting article-pages cache");
        evictAllCacheValues(CacheConfig.CACHE_PAGES);
    }
}
