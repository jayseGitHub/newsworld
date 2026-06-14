package io.newsworld.collector.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "correlations", indexes = {
    @Index(name = "idx_corr_article_a", columnList = "article_id_a"),
    @Index(name = "idx_corr_article_b", columnList = "article_id_b"),
    @Index(name = "idx_corr_score", columnList = "score")
})
@Data
@NoArgsConstructor
public class Correlation {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "correlation_seq")
    @SequenceGenerator(name = "correlation_seq", sequenceName = "correlation_seq", allocationSize = 20)
    private Long id;

    @Column(name = "article_id_a", nullable = false)
    private Long articleIdA;

    @Column(name = "article_id_b", nullable = false)
    private Long articleIdB;

    @Column(nullable = false)
    private double score;          // 0.0 à 1.0 (plus élevé = plus corrélé)

    @Column(name = "common_topic")
    private String commonTopic;    // entité ou sujet commun principal

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
