package io.newsworld.collector.api.dto;

import io.newsworld.collector.model.Article;

public record ClusterSourceDto(
        Long id,
        String title,
        String countryCode,
        String originalLanguage,
        String sourceUrl,
        String originalSummary,
        String translatedSummary
) {
    public static ClusterSourceDto from(Article a) {
        String title = a.getTranslatedTitle() != null ? a.getTranslatedTitle() : a.getOriginalTitle();
        return new ClusterSourceDto(
                a.getId(), title, a.getCountryCode(), a.getOriginalLanguage(), a.getSourceUrl(),
                a.getOriginalSummary(), a.getTranslatedSummary()
        );
    }
}
