package io.newsworld.collector.service;

import io.newsworld.collector.model.Article;
import io.newsworld.collector.model.Correlation;
import io.newsworld.collector.repository.ArticleRepository;
import io.newsworld.collector.repository.CorrelationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CorrelationService {

    private static final double MIN_SCORE = 0.4;
    private static final int MIN_COMMON_TOPICS = 2;

    private final ArticleRepository articleRepository;
    private final CorrelationRepository correlationRepository;

    public List<Correlation> findCorrelationsForArticle(Long articleId) {
        return correlationRepository.findByArticleAndMinScore(articleId, MIN_SCORE);
    }

    public List<Object[]> getTopTopics() {
        return correlationRepository.findTopCorrelatedTopics(MIN_SCORE);
    }

    /**
     * Computes correlations between a newly collected article and recent articles
     * from other countries. Uses topic overlap (Level 1 — fast, local).
     */
    public void computeCorrelations(Article newArticle) {
        if (newArticle.getTopics() == null || newArticle.getTopics().isEmpty()) return;

        Set<String> newTopics = Set.copyOf(newArticle.getTopics());
        LocalDateTime since = LocalDateTime.now().minusDays(7);

        List<Article> candidates = articleRepository
                .findByContinentOrderByPublishedAtDesc(newArticle.getContinent())
                .stream()
                .filter(a -> !a.getId().equals(newArticle.getId()))
                .filter(a -> !a.getCountryCode().equals(newArticle.getCountryCode()))
                .filter(a -> a.getPublishedAt() != null && a.getPublishedAt().isAfter(since))
                .toList();

        List<Correlation> correlations = new ArrayList<>();

        for (Article candidate : candidates) {
            if (candidate.getTopics() == null || candidate.getTopics().isEmpty()) continue;

            List<String> common = candidate.getTopics().stream()
                    .filter(newTopics::contains)
                    .toList();

            if (common.size() < MIN_COMMON_TOPICS) continue;

            double score = (double) common.size() /
                    Math.max(newTopics.size(), candidate.getTopics().size());

            Correlation correlation = new Correlation();
            correlation.setArticleIdA(newArticle.getId());
            correlation.setArticleIdB(candidate.getId());
            correlation.setScore(score);
            correlation.setCommonTopic(common.get(0));
            correlation.setCreatedAt(LocalDateTime.now());
            correlations.add(correlation);
        }

        if (!correlations.isEmpty()) {
            correlationRepository.saveAll(correlations);
            log.debug("Saved {} correlations for article {}", correlations.size(), newArticle.getId());
        }
    }
}
