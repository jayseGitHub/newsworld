package io.newsworld.collector.doctor;

import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;
import io.newsworld.collector.model.Country;
import io.newsworld.collector.service.ApiDiscoveryService;
import io.newsworld.collector.util.ArticleLinkExtractor;
import io.newsworld.collector.util.HttpFetcher;
import io.newsworld.collector.util.SslUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class RssDoctorService {

    private final ApiDiscoveryService apiDiscoveryService;

    private static final List<String> COMMON_PATTERNS = List.of(
            "/feed/", "/rss/", "/rss.xml", "/feed.xml", "/atom.xml",
            "/feeds/posts/default", "/rss/all.rss", "/news/rss",
            "/en/rss.xml", "/english/rss", "/spip.php?page=backend"
    );

    @Value("${newsworld.collector.doctor.connect-timeout:8000}")
    private int connectTimeout;

    @Value("${newsworld.collector.doctor.read-timeout:10000}")
    private int readTimeout;

    public DoctorResult diagnose(Country country) {
        String originalUrl = country.getRssUrl();

        // Step 1 — test current RSS
        if (country.isRssAvailable() && StringUtils.isNotBlank(originalUrl)) {
            RssCheckResult check = checkRss(originalUrl);
            if (check.isOk()) {
                return new DoctorResult(country, DoctorResult.Verdict.RSS_OK,
                        originalUrl, null, "ORIGINAL_OK", check.articleCount() + " articles");
            }
            log.debug("[{}] Original RSS failed ({}) : {}", country.getCode(), check.status(), check.detail());
        }

        // Step 2 — autodiscovery via <link rel="alternate"> in homepage
        Optional<String> discovered = discoverRssFromHomepage(country.getMediaUrl());
        if (discovered.isPresent() && !discovered.get().equals(originalUrl)) {
            RssCheckResult check = checkRss(discovered.get());
            if (check.isOk()) {
                log.info("[{}] RSS autodiscovered: {}", country.getCode(), discovered.get());
                return new DoctorResult(country, DoctorResult.Verdict.RSS_FIXED,
                        originalUrl, discovered.get(), "AUTODISCOVERY", check.articleCount() + " articles");
            }
        }

        // Step 3 — common URL patterns
        Optional<String> pattern = tryCommonPatterns(country.getMediaUrl());
        if (pattern.isPresent()) {
            log.info("[{}] RSS found via pattern: {}", country.getCode(), pattern.get());
            return new DoctorResult(country, DoctorResult.Verdict.RSS_FIXED,
                    originalUrl, pattern.get(),
                    "PATTERN:" + extractPath(pattern.get(), country.getMediaUrl()),
                    "found");
        }

        // Step 4 — API discovery (WP REST, news sitemap, JSON feed)
        Optional<ApiDiscoveryService.ApiResult> api = apiDiscoveryService.discover(country.getMediaUrl());
        if (api.isPresent()) {
            ApiDiscoveryService.ApiResult a = api.get();
            log.info("[{}] API discovered ({}): {} — {} items", country.getCode(), a.type(), a.url(), a.itemCount());
            return new DoctorResult(country, DoctorResult.Verdict.API_FOUND,
                    originalUrl, a.url(), a.type(), a.itemCount() + " items");
        }

        // Step 5 — scraper fallback check (broad selector set)
        if (scraperFindsLinks(country.getMediaUrl())) {
            return new DoctorResult(country, DoctorResult.Verdict.SCRAPER_ONLY,
                    originalUrl, null, "SCRAPER", "homepage parseable");
        }

        return new DoctorResult(country, DoctorResult.Verdict.BROKEN,
                originalUrl, null, null, "RSS dead + scraper empty");
    }

    /** Tests an RSS URL with SSL bypass and 403-retry with extra headers. */
    public RssCheckResult checkRss(String url) {
        try {
            HttpURLConnection conn = openConnection(url, false);
            int code = conn.getResponseCode();

            // 403 → retry with Referer + cookies hint
            if (code == 403) {
                conn.disconnect();
                conn = openConnection(url, true);
                code = conn.getResponseCode();
            }

            if (code != 200) {
                conn.disconnect();
                return RssCheckResult.error(RssCheckResult.Status.HTTP_ERROR, url, code, "HTTP " + code);
            }
            try (InputStream is = conn.getInputStream()) {
                int count = new SyndFeedInput().build(new XmlReader(is)).getEntries().size();
                return RssCheckResult.ok(url, count);
            }
        } catch (java.net.SocketTimeoutException e) {
            return RssCheckResult.error(RssCheckResult.Status.TIMEOUT, url, 0, "timeout");
        } catch (com.rometools.rome.io.FeedException | org.jdom2.JDOMException e) {
            return RssCheckResult.error(RssCheckResult.Status.PARSE_ERROR, url, 200, e.getMessage());
        } catch (Exception e) {
            return RssCheckResult.error(RssCheckResult.Status.UNREACHABLE, url, 0, e.getMessage());
        }
    }

    private Optional<String> discoverRssFromHomepage(String mediaUrl) {
        try {
            Document doc = Jsoup.connect(mediaUrl)
                    .userAgent(HttpFetcher.BROWSER_UA)
                    .header("Accept", "text/html,*/*;q=0.8")
                    .header("Accept-Encoding", "gzip, deflate, br")
                    .header("Referer", "https://www.google.com/")
                    .sslSocketFactory(SslUtils.trustAllFactory())
                    .followRedirects(true)
                    .timeout(connectTimeout)
                    .get();

            Elements links = doc.select("link[rel=alternate]");
            for (Element link : links) {
                String type = link.attr("type");
                if (type.contains("rss") || type.contains("atom")) {
                    String href = link.absUrl("href");
                    if (StringUtils.isNotBlank(href)) return Optional.of(href);
                }
            }
        } catch (Exception e) {
            log.debug("Homepage fetch failed for {}: {}", mediaUrl, e.getMessage());
        }
        return Optional.empty();
    }

    private Optional<String> tryCommonPatterns(String mediaUrl) {
        String root;
        try {
            root = URI.create(mediaUrl).resolve("/").toString();
            if (root.endsWith("/")) root = root.substring(0, root.length() - 1);
        } catch (Exception e) {
            return Optional.empty();
        }

        List<String> candidates = new ArrayList<>();
        for (String p : COMMON_PATTERNS) candidates.add(root + p);
        try {
            String path = URI.create(mediaUrl).getPath();
            if (!path.equals("/") && !path.isBlank()) {
                for (String p : List.of("/feed/", "/rss/", "/rss.xml"))
                    candidates.add(root + path + p);
            }
        } catch (Exception ignored) {}

        for (String candidate : candidates) {
            try {
                HttpURLConnection conn = openConnection(candidate, false);
                int code = conn.getResponseCode();
                if (code == 403) { conn.disconnect(); conn = openConnection(candidate, true); code = conn.getResponseCode(); }
                if (code == 200) {
                    try (InputStream is = conn.getInputStream()) {
                        new SyndFeedInput().build(new XmlReader(is));
                        conn.disconnect();
                        return Optional.of(candidate);
                    }
                }
                conn.disconnect();
            } catch (Exception ignored) {}
        }
        return Optional.empty();
    }

    private boolean scraperFindsLinks(String mediaUrl) {
        try {
            Document doc = Jsoup.connect(mediaUrl)
                    .userAgent(HttpFetcher.BROWSER_UA)
                    .header("Accept-Encoding", "gzip, deflate, br")
                    .header("Referer", "https://www.google.com/")
                    .sslSocketFactory(SslUtils.trustAllFactory())
                    .followRedirects(true)
                    .timeout(connectTimeout)
                    .get();
            return ArticleLinkExtractor.hasLinks(doc, mediaUrl);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Opens an HTTP connection with SSL bypass and optional aggressive headers for 403 bypass.
     * aggressive=true adds Referer + Accept-Encoding + cookie hint.
     */
    private HttpURLConnection openConnection(String url, boolean aggressive) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
        SslUtils.disableSsl(conn);
        conn.setConnectTimeout(connectTimeout);
        conn.setReadTimeout(readTimeout);
        conn.setInstanceFollowRedirects(true);
        conn.setRequestProperty("User-Agent", HttpFetcher.BROWSER_UA);
        conn.setRequestProperty("Accept", "application/rss+xml, application/atom+xml, text/xml, */*;q=0.8");
        conn.setRequestProperty("Accept-Language", "en-US,en;q=0.9");
        if (aggressive) {
            conn.setRequestProperty("Accept-Encoding", "gzip, deflate, br");
            conn.setRequestProperty("Referer", "https://www.google.com/");
            conn.setRequestProperty("Cache-Control", "no-cache");
            conn.setRequestProperty("Upgrade-Insecure-Requests", "1");
        }
        return conn;
    }

    private String extractPath(String fullUrl, String baseUrl) {
        try {
            return fullUrl.replace(URI.create(baseUrl).resolve("/").toString().replaceAll("/$", ""), "");
        } catch (Exception e) {
            return fullUrl;
        }
    }
}
