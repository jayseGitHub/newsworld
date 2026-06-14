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
import org.jsoup.Jsoup;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class WordPressApiCollectorService {

    private static final int MAX_ARTICLES = 15;

    private final ArticleRepository articleRepository;
    private final HttpFetcher httpFetcher;
    private final ObjectMapper objectMapper;

    @Value("${newsworld.collector.purge-days:30}")
    private int purgeDays;

    public List<Article> collect(Country country) {
        String apiUrl = country.getApiUrl().replaceFirst("per_page=\\d+", "per_page=" + MAX_ARTICLES);
        log.info("[{}] Calling WordPress REST: {}", country.getCode(), apiUrl);

        try {
            String json = httpFetcher.fetch(apiUrl);
            // Strip illegal JSON control chars (0x00-0x1F except \t \n \r) then U+FFFD replacement char
            json = json.replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]", "")
                       .replace(String.valueOf((char) 0xFFFD), "");
            JsonNode posts = objectMapper.readTree(json);
            if (!posts.isArray()) {
                log.warn("[{}] WP REST did not return a JSON array", country.getCode());
                return List.of();
            }

            List<Article> articles = new ArrayList<>();
            LocalDateTime now = LocalDateTime.now();

            for (JsonNode post : posts) {
                if (articles.size() >= MAX_ARTICLES) break;

                String url = post.path("link").asText("").trim();
                if (url.isBlank() || articleRepository.existsBySourceUrl(url)) continue;

                String title = rendered(post, "title");
                if (title.isBlank()) continue;

                Article article = new Article();
                article.setCountryCode(country.getCode());
                article.setContinent(country.getContinent());
                article.setSourceUrl(url);
                article.setOriginalTitle(StringUtils.truncate(title, 512));
                article.setOriginalLanguage(country.getLanguage());
                article.setOriginalSummary(StringUtils.truncate(
                        Jsoup.parse(rendered(post, "excerpt")).text(), 2000));
                article.setPublishedAt(HttpFetcher.parseDate(post.path("date").asText(""), now));
                article.setCollectedAt(now);
                article.setExpiresAt(now.plusDays(purgeDays));
                articles.add(article);
            }

            log.info("[{}] WP REST collected {} new articles", country.getCode(), articles.size());
            return articleRepository.saveAll(articles);

        } catch (Exception e) {
            log.error("[{}] WP REST collection failed: {}", country.getCode(), e.getMessage());
            return List.of();
        }
    }

    private String rendered(JsonNode node, String field) {
        JsonNode f = node.path(field);
        return f.isObject() ? f.path("rendered").asText("").trim() : f.asText("").trim();
    }
}