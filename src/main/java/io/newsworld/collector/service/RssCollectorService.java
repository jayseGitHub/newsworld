package io.newsworld.collector.service;

import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;
import io.newsworld.collector.model.Article;
import io.newsworld.collector.model.CollectionResult;
import io.newsworld.collector.model.Country;
import io.newsworld.collector.model.FeedCache;
import io.newsworld.collector.repository.ArticleRepository;
import io.newsworld.collector.repository.FeedCacheRepository;
import io.newsworld.collector.util.HttpFetcher;
import io.newsworld.collector.util.SslUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class RssCollectorService {

    private static final int MAX_REDIRECTS = 5;

    private final ArticleRepository articleRepository;
    private final FeedCacheRepository feedCacheRepository;
    private final ScraperService scraperService;

    @Value("${newsworld.collector.purge-days:30}")
    private int purgeDays;

    @Value("${newsworld.collector.http.connect-timeout:10000}")
    private int connectTimeout;

    @Value("${newsworld.collector.http.read-timeout:15000}")
    private int readTimeout;

    public CollectionResult collect(Country country) {
        long start = System.currentTimeMillis();
        if (country.isRssAvailable() && StringUtils.isNotBlank(country.getRssUrl())) {
            try {
                return collectFromRss(country, start);
            } catch (Exception e) {
                log.warn("RSS failed for {} ({}), falling back to scraper: {}",
                        country.getCode(), country.getRssUrl(), e.getMessage());
            }
        }
        List<Article> articles = scraperService.scrape(country);
        return CollectionResult.scraper(articles, System.currentTimeMillis() - start);
    }

    private CollectionResult collectFromRss(Country country, long start) throws Exception {
        FeedCache cache = feedCacheRepository.findById(country.getCode())
                .orElseGet(() -> new FeedCache(country.getCode()));

        HttpURLConnection conn = openConnection(country.getRssUrl(), cache);
        int status = conn.getResponseCode();

        if (status == HttpURLConnection.HTTP_NOT_MODIFIED) {
            log.info("Feed unchanged for {} (304 Not Modified)", country.getCode());
            cache.setLastFetchedAt(LocalDateTime.now());
            feedCacheRepository.save(cache);
            return CollectionResult.notModified(System.currentTimeMillis() - start);
        }

        // Persist ETag / Last-Modified for next request
        String newEtag = conn.getHeaderField("ETag");
        String newLastModified = conn.getHeaderField("Last-Modified");
        if (newEtag != null) cache.setEtag(newEtag);
        if (newLastModified != null) cache.setLastModified(newLastModified);
        cache.setLastFetchedAt(LocalDateTime.now());

        SyndFeed feed;
        try (InputStream is = conn.getInputStream()) {
            feed = new SyndFeedInput().build(new XmlReader(is));
        }

        // Collect all candidate URLs first, then deduplicate in one DB query
        List<String> candidateUrls = feed.getEntries().stream()
                .map(SyndEntry::getLink)
                .filter(StringUtils::isNotBlank)
                .toList();

        Set<String> existingUrls = candidateUrls.isEmpty() ? Set.of()
                : articleRepository.findExistingUrls(candidateUrls, LocalDateTime.now().minusHours(1));

        List<Article> articles = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();

        for (SyndEntry entry : feed.getEntries()) {
            String url = entry.getLink();
            String title = entry.getTitle();
            if (StringUtils.isBlank(url) || StringUtils.isBlank(title)) continue;
            if (existingUrls.contains(url)) continue;

            String rawSummary = entry.getDescription() != null
                    ? Jsoup.parse(entry.getDescription().getValue()).text()
                    : "";

            Article article = new Article();
            article.setCountryCode(country.getCode());
            article.setContinent(country.getContinent());
            article.setSourceUrl(url);
            article.setOriginalTitle(StringUtils.truncate(title, 512));
            article.setOriginalLanguage(country.getLanguage());
            article.setOriginalSummary(StringUtils.truncate(rawSummary, 2000));
            article.setPublishedAt(toLocalDateTime(entry.getPublishedDate()));
            article.setCollectedAt(now);
            article.setExpiresAt(now.plusDays(purgeDays));
            articles.add(article);
        }

        cache.setLastArticleCount(articles.size());
        feedCacheRepository.save(cache);

        List<Article> saved = articleRepository.saveAll(articles);
        log.info("Collected {}/{} new articles from RSS for {} (ETag: {})",
                saved.size(), feed.getEntries().size(), country.getCode(),
                newEtag != null ? "yes" : "no");

        return CollectionResult.rss(saved, status, cache.getEtag(), System.currentTimeMillis() - start);
    }

    /**
     * Opens an HTTP(S) connection with browser-like headers, conditional cache headers,
     * and manual cross-protocol redirect handling (URLConnection does not follow HTTP→HTTPS).
     */
    private HttpURLConnection openConnection(String url, FeedCache cache) throws IOException {
        String currentUrl = url;
        FeedCache activeCache = cache;

        for (int i = 0; i < MAX_REDIRECTS; i++) {
            HttpURLConnection conn = (HttpURLConnection) URI.create(currentUrl).toURL().openConnection();
            SslUtils.disableSsl(conn);
            conn.setInstanceFollowRedirects(true);
            conn.setConnectTimeout(connectTimeout);
            conn.setReadTimeout(readTimeout);
            conn.setRequestProperty("User-Agent", HttpFetcher.BROWSER_UA);
            conn.setRequestProperty("Accept", "application/rss+xml, application/atom+xml, application/xml;q=0.9, */*;q=0.8");
            conn.setRequestProperty("Accept-Language", "en-US,en;q=0.9");
            conn.setRequestProperty("Cache-Control", "no-cache");

            // Conditional request: skip download if feed unchanged
            if (activeCache != null) {
                if (activeCache.getEtag() != null) conn.setRequestProperty("If-None-Match", activeCache.getEtag());
                if (activeCache.getLastModified() != null) conn.setRequestProperty("If-Modified-Since", activeCache.getLastModified());
            }

            int code = conn.getResponseCode();

            if (code == HttpURLConnection.HTTP_NOT_MODIFIED) return conn;

            if (code == HttpURLConnection.HTTP_MOVED_PERM || code == HttpURLConnection.HTTP_MOVED_TEMP
                    || code == 307 || code == 308) {
                String location = conn.getHeaderField("Location");
                if (location == null) throw new IOException("Redirect with no Location header from " + currentUrl);
                conn.disconnect();
                currentUrl = location.startsWith("http") ? location : URI.create(currentUrl).resolve(location).toString();
                activeCache = null; // ETag is URL-specific, don't reuse after redirect
                log.debug("Redirected to {}", currentUrl);
                continue;
            }

            if (code != HttpURLConnection.HTTP_OK) {
                conn.disconnect();
                throw new IOException("HTTP " + code + " from " + currentUrl);
            }
            return conn;
        }
        throw new IOException("Too many redirects for " + url);
    }

    private LocalDateTime toLocalDateTime(Date date) {
        if (date == null) return LocalDateTime.now();
        return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
    }

    // Keep for compatibility with any direct callers that only need the article list
    public List<Article> collectArticles(Country country) {
        return collect(country).articles();
    }
}
