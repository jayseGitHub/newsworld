package io.newsworld.collector.service;

import io.newsworld.collector.model.Article;
import io.newsworld.collector.repository.ArticleRepository;
import io.newsworld.collector.config.NewsWorldProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Pipeline 1 — Content enrichment.
 * Fetches the article page and extracts a text summary into originalSummary.
 * WP articles already have originalSummary from the excerpt; they are marked
 * as enriched without an extra fetch.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ContentEnrichmentService {

    private static final String[] ARTICLE_SELECTORS = {
        "article", "[itemprop=articleBody]", "main article",
        ".article-body", ".article-content", ".post-content",
        ".entry-content", "#article-body", "#content article"
    };
    private static final int MIN_PARAGRAPH_LENGTH = 80;
    private static final int MAX_SUMMARY_LENGTH   = 2000;

    private final ArticleRepository articleRepository;
    private final PageFetcherService pageFetcherService;
    private final NewsWorldProperties props;

    public int enrichBatch() {
        LocalDateTime now = LocalDateTime.now();
        List<Article> articles = articleRepository.findUnenriched(now, PageRequest.of(0, props.getPipelines().getEnrichmentBatchSize()));

        if (articles.isEmpty()) {
            log.debug("Enrichment: nothing to process");
            return 0;
        }

        int enriched = 0;
        for (Article article : articles) {
            try {
                enriched += enrichOne(article, now);
            } catch (Exception e) {
                log.warn("[{}] Enrichment error on {}: {}", article.getCountryCode(), article.getSourceUrl(), e.getMessage());
                article.setContentFetchedAt(now);
                articleRepository.save(article);
            }
        }

        log.info("Enrichment batch complete: {}/{} articles processed", enriched, articles.size());
        return enriched;
    }

    private int enrichOne(Article article, LocalDateTime now) {
        // WP articles already have a summary — just mark as fetched
        if (article.getOriginalSummary() != null && !article.getOriginalSummary().isBlank()) {
            article.setContentFetchedAt(now);
            articleRepository.save(article);
            return 1;
        }

        String url = article.getSourceUrl();
        if (url == null || url.isBlank()) {
            article.setContentFetchedAt(now);
            articleRepository.save(article);
            return 0;
        }

        Document page = pageFetcherService.fetchPage(url);
        article.setContentFetchedAt(now);

        if (page != null) {
            String summary = extractSummary(page);
            if (summary != null) {
                article.setOriginalSummary(summary);
                log.debug("[{}] Enriched: {}", article.getCountryCode(), article.getOriginalTitle());
            }
        }

        articleRepository.save(article);
        return 1;
    }

    private String extractSummary(Document page) {
        for (String selector : ARTICLE_SELECTORS) {
            Elements found = page.select(selector);
            if (!found.isEmpty()) {
                String text = found.first().text();
                if (text.length() > MIN_PARAGRAPH_LENGTH) {
                    return StringUtils.truncate(text, MAX_SUMMARY_LENGTH);
                }
            }
        }

        StringBuilder sb = new StringBuilder();
        for (Element p : page.select("p")) {
            String text = p.text();
            if (text.length() >= MIN_PARAGRAPH_LENGTH) {
                sb.append(text).append(' ');
                if (sb.length() >= MAX_SUMMARY_LENGTH) break;
            }
        }
        String result = sb.toString().trim();
        return result.isBlank() ? null : StringUtils.truncate(result, MAX_SUMMARY_LENGTH);
    }
}
