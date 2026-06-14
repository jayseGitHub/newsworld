package io.newsworld.collector.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.newsworld.collector.model.Article;
import io.newsworld.collector.model.Country;
import io.newsworld.collector.repository.ArticleRepository;
import io.newsworld.collector.util.HttpFetcher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Collects articles from reverse-engineered JSON endpoints (array or JSON Feed spec).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class JsonApiCollectorService {

    private static final int MAX_ARTICLES = 15;

    private final ArticleRepository articleRepository;
    private final HttpFetcher httpFetcher;
    private final ObjectMapper objectMapper;

    @Value("${newsworld.collector.purge-days:30}")
    private int purgeDays;

    public List<Article> collect(Country country) {
        String apiUrl = country.getApiUrl();
        log.info("[{}] Calling JSON API: {}", country.getCode(), apiUrl);

        try {
            JsonNode root = objectMapper.readTree(httpFetcher.fetch(apiUrl));
            JsonNode items = root.isArray() ? root : root.path("items");
            if (!items.isArray()) {
                log.warn("[{}] JSON API did not return an array or items array", country.getCode());
                return List.of();
            }

            List<Article> articles = new ArrayList<>();
            LocalDateTime now = LocalDateTime.now();

            for (JsonNode item : items) {
                if (articles.size() >= MAX_ARTICLES) break;

                String url = firstText(item, "url", "link", "permalink").trim();
                if (url.isBlank() || articleRepository.existsBySourceUrl(url)) continue;

                String title = firstText(item, "title", "headline", "name").trim();
                if (title.isBlank()) continue;

                Article article = new Article();
                article.setCountryCode(country.getCode());
                article.setContinent(country.getContinent());
                article.setSourceUrl(url);
                article.setOriginalTitle(StringUtils.truncate(title, 512));
                article.setOriginalLanguage(country.getLanguage());
                article.setOriginalSummary(StringUtils.truncate(
                        firstText(item, "summary", "description", "excerpt", "content_text"), 2000));
                article.setPublishedAt(HttpFetcher.parseDate(
                        firstText(item, "date_published", "date", "published_at", "pubDate"), now));
                article.setCollectedAt(now);
                article.setExpiresAt(now.plusDays(purgeDays));
                articles.add(article);
            }

            log.info("[{}] JSON API collected {} new articles", country.getCode(), articles.size());
            return articleRepository.saveAll(articles);

        } catch (Exception e) {
            log.error("[{}] JSON API collection failed: {}", country.getCode(), e.getMessage());
            return List.of();
        }
    }

    private String firstText(JsonNode node, String... fields) {
        for (String field : fields) {
            String v = node.path(field).asText("").trim();
            if (!v.isBlank()) return v;
        }
        return "";
    }
}
