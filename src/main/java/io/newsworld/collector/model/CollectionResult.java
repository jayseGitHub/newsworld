package io.newsworld.collector.model;

import java.util.List;

public record CollectionResult(
        List<Article> articles,
        Source source,
        int httpStatus,
        String etag,
        long durationMs
) {
    public enum Source { RSS_FRESH, RSS_NOT_MODIFIED, SCRAPER }

    public static CollectionResult notModified(long durationMs) {
        return new CollectionResult(List.of(), Source.RSS_NOT_MODIFIED, 304, null, durationMs);
    }

    public static CollectionResult rss(List<Article> articles, int httpStatus, String etag, long durationMs) {
        return new CollectionResult(articles, Source.RSS_FRESH, httpStatus, etag, durationMs);
    }

    public static CollectionResult scraper(List<Article> articles, long durationMs) {
        return new CollectionResult(articles, Source.SCRAPER, 200, null, durationMs);
    }
}
