package io.newsworld.collector.repository;

import io.newsworld.collector.model.Correlation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CorrelationRepository extends JpaRepository<Correlation, Long> {

    List<Correlation> findByArticleIdAOrArticleIdBOrderByScoreDesc(Long articleIdA, Long articleIdB);

    @Query("""
        SELECT c FROM Correlation c
        WHERE (c.articleIdA = :id OR c.articleIdB = :id)
        AND c.score >= :minScore
        ORDER BY c.score DESC
    """)
    List<Correlation> findByArticleAndMinScore(@Param("id") Long articleId,
                                               @Param("minScore") double minScore);

    @Query("""
        SELECT c.commonTopic, COUNT(c) as cnt FROM Correlation c
        WHERE c.score >= :minScore
        GROUP BY c.commonTopic
        ORDER BY cnt DESC
    """)
    List<Object[]> findTopCorrelatedTopics(@Param("minScore") double minScore);
}
