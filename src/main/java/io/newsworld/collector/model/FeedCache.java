package io.newsworld.collector.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "feed_cache")
@Data
@NoArgsConstructor
public class FeedCache {

    @Id
    @Column(name = "country_code", length = 3)
    private String countryCode;

    @Column(length = 512)
    private String etag;

    @Column(name = "last_modified", length = 256)
    private String lastModified;

    @Column(name = "last_fetched_at")
    private LocalDateTime lastFetchedAt;

    @Column(name = "last_article_count")
    private int lastArticleCount;

    public FeedCache(String countryCode) {
        this.countryCode = countryCode;
    }
}
