package io.newsworld.collector.repository;

import io.newsworld.collector.model.ArticleCluster;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface ArticleClusterRepository extends JpaRepository<ArticleCluster, Long> {

    List<ArticleCluster> findByClusterDateOrderByRelevanceScoreDesc(LocalDate date);

    boolean existsByClusterDate(LocalDate date);

    List<ArticleCluster> findTop10ByOrderByRelevanceScoreDesc();

    @Query("SELECT DISTINCT c.clusterDate FROM ArticleCluster c WHERE c.clusterDate >= :start AND c.clusterDate <= :end ORDER BY c.clusterDate")
    List<LocalDate> findDistinctClusterDatesBetween(@Param("start") LocalDate start, @Param("end") LocalDate end);

    @Query("SELECT c FROM ArticleCluster c LEFT JOIN FETCH c.articles WHERE c.id = :id")
    Optional<ArticleCluster> findByIdWithArticles(@Param("id") Long id);
}
