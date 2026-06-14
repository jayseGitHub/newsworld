package io.newsworld.collector.service;

import io.newsworld.collector.model.Article;
import io.newsworld.collector.model.Country;
import io.newsworld.collector.repository.ArticleRepository;
import io.newsworld.collector.util.HttpFetcher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class NewsSitemapCollectorService {

    private static final int MAX_ARTICLES = 15;

    private final ArticleRepository articleRepository;
    private final HttpFetcher httpFetcher;

    @Value("${newsworld.collector.purge-days:30}")
    private int purgeDays;

    public List<Article> collect(Country country) {
        String sitemapUrl = country.getApiUrl();
        log.info("[{}] Fetching news sitemap: {}", country.getCode(), sitemapUrl);

        try {
            String xml = httpFetcher.fetch(sitemapUrl, "application/xml, text/xml, */*;q=0.8");
            org.jsoup.nodes.Document doc = Jsoup.parse(xml, "", Parser.xmlParser());
            LocalDateTime now = LocalDateTime.now();

            // Case 1: standard <urlset> with <url> elements
            List<Element> urlElements = doc.select("url");
            if (!urlElements.isEmpty()) {
                List<Article> articles = collectFromUrlSet(urlElements, country, now);
                log.info("[{}] Sitemap collected {} new articles", country.getCode(), articles.size());
                return articleRepository.saveAll(articles);
            }

            // Case 2: <sitemapindex> — fetch first child sitemap and parse it
            Element childLocEl = doc.selectFirst("sitemap > loc");
            if (childLocEl != null) {
                String childUrl = childLocEl.text().trim();
                if (!childUrl.isBlank()) {
                    log.info("[{}] Sitemapindex detected, fetching child: {}", country.getCode(), childUrl);
                    String childXml = httpFetcher.fetch(childUrl, "application/xml, text/xml, */*;q=0.8");
                    org.jsoup.nodes.Document childDoc = Jsoup.parse(childXml, "", Parser.xmlParser());
                    List<Element> childUrlElements = childDoc.select("url");
                    if (!childUrlElements.isEmpty()) {
                        List<Article> articles = collectFromUrlSet(childUrlElements, country, now);
                        log.info("[{}] Sitemap (child) collected {} new articles", country.getCode(), articles.size());
                        return articleRepository.saveAll(articles);
                    }
                }
            }

            // Case 3: RSS feed with <item> elements
            List<Element> itemElements = doc.select("item");
            if (!itemElements.isEmpty()) {
                log.info("[{}] RSS feed detected, parsing <item> elements", country.getCode());
                List<Article> articles = collectFromRssItems(itemElements, country, now);
                log.info("[{}] RSS feed collected {} new articles", country.getCode(), articles.size());
                return articleRepository.saveAll(articles);
            }

            log.warn("[{}] No parseable content found in sitemap", country.getCode());
            return List.of();

        } catch (Exception e) {
            log.error("[{}] News sitemap collection failed: {}", country.getCode(), e.getMessage());
            return List.of();
        }
    }

    private List<Article> collectFromUrlSet(List<Element> urlElements, Country country, LocalDateTime now) {
        List<Article> articles = new ArrayList<>();
        for (Element urlEl : urlElements) {
            if (articles.size() >= MAX_ARTICLES) break;

            Element locEl = urlEl.selectFirst("loc");
            if (locEl == null) continue;
            String url = locEl.text().trim();
            if (url.isBlank() || articleRepository.existsBySourceUrl(url)) continue;

            String title = urlEl.getElementsByTag("news:title").text();
            if (title.isBlank()) {
                Element titleEl = urlEl.selectFirst("title");
                title = titleEl != null ? titleEl.text() : "";
            }
            // Allow empty title only when truly nothing is available; skip only if blank
            if (title.isBlank()) continue;

            LocalDateTime publishedAt = HttpFetcher.parseDate(
                    urlEl.getElementsByTag("news:publication_date").text(), now);

            articles.add(buildArticle(country, now, url, title, publishedAt));
        }
        return articles;
    }

    private List<Article> collectFromRssItems(List<Element> itemElements, Country country, LocalDateTime now) {
        List<Article> articles = new ArrayList<>();
        for (Element item : itemElements) {
            if (articles.size() >= MAX_ARTICLES) break;

            Element titleEl = item.selectFirst("title");
            String title = titleEl != null ? titleEl.text().trim() : "";
            if (title.isBlank()) continue;

            // <link> can be auto-closing in Jsoup XML parser; fall back to <guid>
            String url = "";
            Element linkEl = item.selectFirst("link");
            if (linkEl != null) url = linkEl.text().trim();
            if (url.isBlank()) {
                Element guidEl = item.getElementsByTag("guid").first();
                if (guidEl != null) url = guidEl.text().trim();
            }
            if (url.isBlank() || articleRepository.existsBySourceUrl(url)) continue;

            LocalDateTime publishedAt = HttpFetcher.parseDate(
                    item.selectFirst("pubDate") != null
                            ? item.selectFirst("pubDate").text()
                            : "",
                    now);

            articles.add(buildArticle(country, now, url, title, publishedAt));
        }
        return articles;
    }

    private Article buildArticle(Country country, LocalDateTime now,
                                  String url, String title, LocalDateTime publishedAt) {
        Article article = new Article();
        article.setCountryCode(country.getCode());
        article.setContinent(country.getContinent());
        article.setSourceUrl(url);
        article.setOriginalTitle(StringUtils.truncate(title, 512));
        article.setOriginalLanguage(country.getLanguage());
        article.setPublishedAt(publishedAt);
        article.setCollectedAt(now);
        article.setExpiresAt(now.plusDays(purgeDays));
        return article;
    }
}
