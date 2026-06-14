package io.newsworld.collector.api.dto;

public record StatsDto(
        long totalArticles,
        long todayArticles,
        long pendingEnrich,
        long pendingTranslate,
        long totalClusters,
        long todayClusters,
        long totalCountries
) {}
