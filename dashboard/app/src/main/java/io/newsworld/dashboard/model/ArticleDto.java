package io.newsworld.dashboard.model;

import java.io.Serializable;
import java.time.LocalDateTime;

public class ArticleDto implements Serializable {
    public long id;
    public String countryCode;
    public String originalTitle;
    public String translatedTitle;
    public String originalLanguage;
    public String sourceUrl;
    public LocalDateTime collectedAt;
    public LocalDateTime publishedAt;
    public String originalSummary;
    public String translatedSummary;
    public boolean enriched;
    public boolean translated;

    public String displayTitle() {
        return translatedTitle != null && !translatedTitle.isBlank() ? translatedTitle : originalTitle;
    }
}
