package io.newsworld.collector.service;

import io.newsworld.collector.util.HttpFetcher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.io.IOException;

/**
 * Fetches and caches HTML pages.
 * Must be a separate Spring bean so @Cacheable proxy works correctly
 * (self-calls within the same bean bypass the proxy).
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PageFetcherService {

    private final HttpFetcher httpFetcher;

    /**
     * Fetches the HTML document at the given URL and caches it in memory.
     * Cache key = URL. Eviction is handled by CacheService (hourly).
     * Returns null on failure so the caller can decide whether to skip or fallback.
     */
    @Cacheable(value = "article-pages", key = "#url")
    public Document fetchPage(String url) {
        try {
            log.debug("HTTP GET {}", url);
            String html = httpFetcher.fetch(url, "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
            return Jsoup.parse(html, url);
        } catch (IOException e) {
            log.warn("Failed to fetch page {}: {}", url, e.getMessage());
            return null;
        }
    }
}
