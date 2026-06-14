package io.newsworld.collector.service;

import io.newsworld.collector.config.NewsWorldProperties;
import io.newsworld.collector.util.HttpFetcher;
import io.newsworld.collector.util.SslUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

/**
 * Discovers non-RSS data sources for news sites:
 * WordPress REST API, Google News sitemaps, JSON Feed spec.
 * Used by the doctor pipeline; will also feed the article collector.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ApiDiscoveryService {

    private static final String WP_REST_PATH =
            "/wp-json/wp/v2/posts?per_page=5&_fields=id,title,link,date,excerpt";

    private static final List<String> NEWS_SITEMAP_PATHS = List.of(
            "/news-sitemap.xml", "/sitemap-news.xml", "/sitemap/news.xml",
            "/sitemap_news.xml", "/google-news-sitemap.xml"
    );

    private static final List<String> JSON_FEED_PATHS = List.of(
            "/feed.json", "/api/feed", "/api/news.json",
            "/api/articles", "/api/latest", "/api/posts"
    );

    private static final int MAX_BODY_BYTES = 512 * 1024;

    private final NewsWorldProperties props;

    public record ApiResult(String type, String url, int itemCount) {}

    public Optional<ApiResult> discover(String mediaUrl) {
        String root = extractRoot(mediaUrl);
        if (root == null) return Optional.empty();

        Optional<ApiResult> wp = tryWordPressRest(root);
        if (wp.isPresent()) return wp;

        Optional<ApiResult> sitemap = tryNewsSitemaps(root);
        if (sitemap.isPresent()) return sitemap;

        return tryJsonFeeds(root);
    }

    // -------------------------------------------------------------------------

    private Optional<ApiResult> tryWordPressRest(String root) {
        String url = root + WP_REST_PATH;
        HttpURLConnection conn = null;
        try {
            conn = openConnection(url);
            int code = conn.getResponseCode();
            String ct = conn.getContentType();
            if (code != 200 || ct == null || !ct.contains("json")) return Optional.empty();
            String body = readBounded(conn.getInputStream());
            if (body.trim().startsWith("[") && body.contains("\"link\"") && body.contains("\"title\"")) {
                return Optional.of(new ApiResult("WORDPRESS_REST", url, countOccurrences(body, "\"id\"")));
            }
        } catch (Exception e) {
            log.debug("WP REST failed for {}: {}", root, e.getMessage());
        } finally {
            if (conn != null) conn.disconnect();
        }
        return Optional.empty();
    }

    private Optional<ApiResult> tryNewsSitemaps(String root) {
        for (String path : NEWS_SITEMAP_PATHS) {
            String url = root + path;
            HttpURLConnection conn = null;
            try {
                conn = openConnection(url);
                if (conn.getResponseCode() != 200) continue;
                String body = readBounded(conn.getInputStream());
                if (body.contains("<news:") || (body.contains("<urlset") && body.contains("<loc>"))) {
                    int count = countOccurrences(body, "<url>");
                    if (count > 0) return Optional.of(new ApiResult("NEWS_SITEMAP", url, count));
                }
            } catch (Exception e) {
                log.debug("Sitemap {} failed for {}: {}", path, root, e.getMessage());
            } finally {
                if (conn != null) conn.disconnect();
            }
        }
        return Optional.empty();
    }

    private Optional<ApiResult> tryJsonFeeds(String root) {
        for (String path : JSON_FEED_PATHS) {
            String url = root + path;
            HttpURLConnection conn = null;
            try {
                conn = openConnection(url);
                String ct = conn.getContentType();
                if (conn.getResponseCode() != 200 || ct == null || !ct.contains("json")) continue;
                String body = readBounded(conn.getInputStream());
                if (body.contains("\"items\"") && body.contains("\"title\"")) {
                    return Optional.of(new ApiResult("JSON_FEED", url, countOccurrences(body, "\"id\"")));
                }
                if (body.trim().startsWith("[") && body.contains("\"title\"") && body.contains("\"url\"")) {
                    return Optional.of(new ApiResult("JSON_API", url, countOccurrences(body, "\"title\"")));
                }
            } catch (Exception e) {
                log.debug("JSON feed {} failed for {}: {}", path, root, e.getMessage());
            } finally {
                if (conn != null) conn.disconnect();
            }
        }
        return Optional.empty();
    }

    // -------------------------------------------------------------------------

    private HttpURLConnection openConnection(String url) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
        SslUtils.disableSsl(conn);
        conn.setConnectTimeout(props.getCollector().getDoctor().getConnectTimeout());
        conn.setReadTimeout(props.getCollector().getDoctor().getReadTimeout());
        conn.setInstanceFollowRedirects(true);
        conn.setRequestProperty("User-Agent", HttpFetcher.BROWSER_UA);
        conn.setRequestProperty("Accept", "application/json, application/xml, text/xml, */*;q=0.8");
        conn.setRequestProperty("Accept-Language", "en-US,en;q=0.9");
        conn.setRequestProperty("Referer", "https://www.google.com/");
        return conn;
    }

    private String readBounded(InputStream is) throws Exception {
        try (is) {
            byte[] buf = is.readNBytes(MAX_BODY_BYTES);
            return new String(buf, StandardCharsets.UTF_8);
        }
    }

    private String extractRoot(String mediaUrl) {
        try {
            URI uri = URI.create(mediaUrl);
            int port = uri.getPort();
            return uri.getScheme() + "://" + uri.getHost() + (port > 0 ? ":" + port : "");
        } catch (Exception e) {
            return null;
        }
    }

    private int countOccurrences(String text, String token) {
        int count = 0, idx = 0;
        while ((idx = text.indexOf(token, idx)) != -1) { count++; idx += token.length(); }
        return count;
    }
}
