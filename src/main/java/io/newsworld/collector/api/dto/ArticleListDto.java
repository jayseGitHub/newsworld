package io.newsworld.collector.api.dto;

import io.newsworld.collector.model.Article;

import java.time.LocalDateTime;

public record ArticleListDto(
        Long id,
        String countryCode,
        String originalTitle,
        String translatedTitle,
        String originalLanguage,
        String originalSummary,
        String translatedSummary,
        String sourceUrl,
        LocalDateTime collectedAt,
        LocalDateTime publishedAt,
        boolean enriched,
        boolean translated
) {
    public static ArticleListDto from(Article a) {
        return new ArticleListDto(
                a.getId(),
                a.getCountryCode(),
                a.getOriginalTitle(),
                a.getTranslatedTitle(),
                a.getOriginalLanguage(),
                a.getOriginalSummary(),
                a.getTranslatedSummary(),
                a.getSourceUrl(),
                a.getCollectedAt(),
                a.getPublishedAt(),
                a.getContentFetchedAt() != null,
                a.getTranslatedAt() != null
        );
    }
}
