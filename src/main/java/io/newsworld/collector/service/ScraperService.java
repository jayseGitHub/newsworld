package io.newsworld.collector.service;

import io.newsworld.collector.model.Article;
import io.newsworld.collector.model.Country;
import io.newsworld.collector.repository.ArticleRepository;
import io.newsworld.collector.util.ArticleLinkExtractor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ScraperService {

    private static final int MAX_ARTICLES = 15;
    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_DATE_TIME;

    private final ArticleRepository articleRepository;
    private final PageFetcherService pageFetcherService;

    @Value("${newsworld.collector.purge-days:30}")
    private int purgeDays;

    /**
     * Scrapes a country's media homepage for article links, then fetches each
     * individual article page (cached) to extract proper metadata.
     * DB check before every individual article fetch to avoid redundant HTTP calls.
     */
    public List<Article> scrape(Country country) {
        Document homepage = pageFetcherService.fetchPage(country.getMediaUrl());
        if (homepage == null) {
            log.error("Homepage unreachable for {} ({})", country.getCode(), country.getMediaUrl());
            return List.of();
        }

        List<String> candidateUrls = extractArticleUrls(homepage, country.getMediaUrl());
        if (candidateUrls.isEmpty()) {
            log.warn("No article links found on homepage for {} ({})", country.getCode(), country.getMediaUrl());
            return List.of();
        }

        List<Article> articles = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();

        for (String url : candidateUrls) {
            if (articles.size() >= MAX_ARTICLES) break;

            // DB check — if we already have this article, skip the HTTP call entirely
            if (articleRepository.existsBySourceUrl(url)) {
                log.debug("Already in DB, skipping: {}", url);
                continue;
            }

            // Fetch individual article page (cached by PageFetcherService)
            Document articleDoc = pageFetcherService.fetchPage(url);
            if (articleDoc == null) continue;

            String title = extractTitle(articleDoc);
            if (StringUtils.isBlank(title)) continue;

            Article article = new Article();
            article.setCountryCode(country.getCode());
            article.setContinent(country.getContinent());
            article.setSourceUrl(url);
            article.setOriginalTitle(StringUtils.truncate(title, 512));
            article.setOriginalLanguage(country.getLanguage());
            article.setOriginalSummary(StringUtils.truncate(extractDescription(articleDoc), 2000));
            article.setPublishedAt(extractPublishedAt(articleDoc, now));
            article.setCollectedAt(now);
            article.setExpiresAt(now.plusDays(purgeDays));
            articles.add(article);
        }

        log.info("Scraped {}/{} new articles for {} via HTML", articles.size(), candidateUrls.size(), country.getCode());
        return articleRepository.saveAll(articles);
    }

    private List<String> extractArticleUrls(Document doc, String baseUrl) {
        List<String> all = ArticleLinkExtractor.extractLinks(doc, baseUrl);
        // Keep at most 2× the article cap as candidates for the DB-check loop
        return all.size() > MAX_ARTICLES * 2 ? all.subList(0, MAX_ARTICLES * 2) : all;
    }

    /** og:title → twitter:title → <title> → <h1> */
    private String extractTitle(Document doc) {
        String og = doc.select("meta[property=og:title]").attr("content");
        if (!og.isBlank()) return og;
        String twitter = doc.select("meta[name=twitter:title]").attr("content");
        if (!twitter.isBlank()) return twitter;
        String h1 = doc.select("h1").first() != null ? doc.select("h1").first().text() : "";
        if (!h1.isBlank()) return h1;
        return doc.title();
    }

    /** og:description → twitter:description → meta[name=description] */
    private String extractDescription(Document doc) {
        String og = doc.select("meta[property=og:description]").attr("content");
        if (!og.isBlank()) return og;
        String twitter = doc.select("meta[name=twitter:description]").attr("content");
        if (!twitter.isBlank()) return twitter;
        return doc.select("meta[name=description]").attr("content");
    }

    /** article:published_time → datePublished (JSON-LD) → fallback to now */
    private LocalDateTime extractPublishedAt(Document doc, LocalDateTime fallback) {
        String published = doc.select("meta[property=article:published_time]").attr("content");
        if (published.isBlank()) {
            published = doc.select("meta[itemprop=datePublished]").attr("content");
        }
        if (!published.isBlank()) {
            try {
                return LocalDateTime.parse(published.length() > 19 ? published.substring(0, 19) : published,
                        DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            } catch (DateTimeParseException ignored) {}
        }
        return fallback;
    }
}
