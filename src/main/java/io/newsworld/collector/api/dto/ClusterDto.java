package io.newsworld.collector.api.dto;

import io.newsworld.collector.model.ArticleCluster;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record ClusterDto(
        Long id,
        LocalDate clusterDate,
        String topic,
        String synthesis,
        double relevanceScore,
        int articleCount,
        int countryCount,
        int continentCount,
        String countriesList,
        String continentsList,
        LocalDateTime createdAt,
        List<ClusterSourceDto> sources
) {
    /** Pour la liste (pas d'articles chargés). */
    public static ClusterDto from(ArticleCluster c) {
        return new ClusterDto(
                c.getId(), c.getClusterDate(), c.getTopic(), c.getSynthesis(),
                c.getRelevanceScore() != null ? c.getRelevanceScore() : 0.0,
                c.getArticleCount() != null ? c.getArticleCount() : 0,
                c.getCountryCount() != null ? c.getCountryCount() : 0,
                c.getContinentCount() != null ? c.getContinentCount() : 0,
                c.getCountriesList(), c.getContinentsList(), c.getCreatedAt(),
                null
        );
    }

    /** Pour le détail (articles chargés via JOIN FETCH). */
    public static ClusterDto withSources(ArticleCluster c) {
        List<ClusterSourceDto> sources = c.getArticles().stream()
                .map(ClusterSourceDto::from)
                .sorted((a, b) -> a.countryCode().compareTo(b.countryCode()))
                .toList();
        return new ClusterDto(
                c.getId(), c.getClusterDate(), c.getTopic(), c.getSynthesis(),
                c.getRelevanceScore() != null ? c.getRelevanceScore() : 0.0,
                c.getArticleCount() != null ? c.getArticleCount() : 0,
                c.getCountryCount() != null ? c.getCountryCount() : 0,
                c.getContinentCount() != null ? c.getContinentCount() : 0,
                c.getCountriesList(), c.getContinentsList(), c.getCreatedAt(),
                sources
        );
    }
}
