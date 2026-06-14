package io.newsworld.collector.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "articles", indexes = {
    @Index(name = "idx_articles_country", columnList = "country_code"),
    @Index(name = "idx_articles_continent", columnList = "continent"),
    @Index(name = "idx_articles_published", columnList = "published_at"),
    @Index(name = "idx_articles_expires", columnList = "expires_at")
})
@Data
@NoArgsConstructor
public class Article {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "article_seq")
    @SequenceGenerator(name = "article_seq", sequenceName = "article_seq", allocationSize = 50)
    private Long id;

    @Column(name = "country_code", nullable = false, length = 3)
    private String countryCode;

    @Column(nullable = false)
    private String continent;

    @Column(name = "source_url", length = 2048)
    private String sourceUrl;

    @Column(name = "original_title", nullable = false)
    private String originalTitle;

    @Column(name = "original_language", length = 10)
    private String originalLanguage;

    @Column(name = "translated_title")
    private String translatedTitle;

    @Column(name = "original_summary", columnDefinition = "TEXT")
    private String originalSummary;

    @Column(name = "translated_summary", columnDefinition = "TEXT")
    private String translatedSummary;

    @Column(name = "original_content", columnDefinition = "TEXT")
    private String originalContent;

    @Column(name = "translated_content", columnDefinition = "TEXT")
    private String translatedContent;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @Column(name = "collected_at", nullable = false)
    private LocalDateTime collectedAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;   // collectedAt + 30 jours

    @Column(name = "content_fetched_at")
    private LocalDateTime contentFetchedAt;

    @Column(name = "translated_at")
    private LocalDateTime translatedAt;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "article_topics", joinColumns = @JoinColumn(name = "article_id"))
    @Column(name = "topic")
    @com.fasterxml.jackson.annotation.JsonIgnore
    private List<String> topics = new ArrayList<>();   // entités nommées extraites
}
