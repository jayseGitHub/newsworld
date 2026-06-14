package io.newsworld.collector.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "article_clusters", indexes = {
    @Index(name = "idx_clusters_date", columnList = "cluster_date"),
    @Index(name = "idx_clusters_score", columnList = "relevance_score")
})
@Data
@NoArgsConstructor
public class ArticleCluster {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "cluster_seq")
    @SequenceGenerator(name = "cluster_seq", sequenceName = "cluster_seq", allocationSize = 10)
    private Long id;

    @Column(name = "cluster_date", nullable = false)
    private LocalDate clusterDate;

    @Column(name = "topic", nullable = false, length = 512)
    private String topic;

    @Column(name = "synthesis", columnDefinition = "TEXT")
    private String synthesis;

    @Column(name = "relevance_score")
    private Double relevanceScore;

    @Column(name = "article_count")
    private Integer articleCount;

    @Column(name = "country_count")
    private Integer countryCount;

    @Column(name = "continent_count")
    private Integer continentCount;

    @Column(name = "countries_list", length = 1000)
    private String countriesList;

    @Column(name = "continents_list", length = 200)
    private String continentsList;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "cluster_articles",
        joinColumns = @JoinColumn(name = "cluster_id"),
        inverseJoinColumns = @JoinColumn(name = "article_id")
    )
    private List<Article> articles = new ArrayList<>();
}
