package io.newsworld.collector.repository;

import io.newsworld.collector.model.Article;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Set;

@Repository
public interface ArticleRepository extends JpaRepository<Article, Long> {

    List<Article> findByCountryCodeOrderByPublishedAtDesc(String countryCode);

    List<Article> findByContinentOrderByPublishedAtDesc(String continent);

    List<Article> findByCountryCodeAndPublishedAtAfterOrderByPublishedAtDesc(
            String countryCode, LocalDateTime after);

    @Query("SELECT a FROM Article a WHERE a.expiresAt < :now")
    List<Article> findExpired(@Param("now") LocalDateTime now);

    @Modifying
    @Query("DELETE FROM Article a WHERE a.expiresAt < :now")
    int deleteExpired(@Param("now") LocalDateTime now);

    @Query("SELECT DISTINCT a.continent FROM Article a")
    List<String> findDistinctContinents();

    boolean existsBySourceUrl(String sourceUrl);

    boolean existsBySourceUrlAndCollectedAtAfter(String sourceUrl, LocalDateTime after);

    @Query("SELECT a.sourceUrl FROM Article a WHERE a.sourceUrl IN :urls AND a.collectedAt > :after")
    Set<String> findExistingUrls(@Param("urls") Collection<String> urls, @Param("after") LocalDateTime after);

    @Query("SELECT a FROM Article a WHERE a.contentFetchedAt IS NULL AND a.expiresAt > :now ORDER BY a.collectedAt DESC")
    List<Article> findUnenriched(@Param("now") LocalDateTime now, Pageable pageable);

    @Query("SELECT a FROM Article a WHERE a.translatedAt IS NULL AND a.contentFetchedAt IS NOT NULL AND a.expiresAt > :now ORDER BY a.collectedAt DESC")
    List<Article> findUntranslated(@Param("now") LocalDateTime now, Pageable pageable);

    @Query("SELECT a FROM Article a WHERE a.collectedAt BETWEEN :start AND :end ORDER BY a.continent, a.countryCode")
    List<Article> findByCollectedAtBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    long countByExpiresAtAfter(LocalDateTime now);

    long countByCollectedAtBetween(LocalDateTime start, LocalDateTime end);

    @Query("SELECT COUNT(a) FROM Article a WHERE a.contentFetchedAt IS NULL AND a.expiresAt > :now")
    long countPendingEnrich(@Param("now") LocalDateTime now);

    @Query("SELECT COUNT(a) FROM Article a WHERE a.translatedAt IS NULL AND a.contentFetchedAt IS NOT NULL AND a.expiresAt > :now")
    long countPendingTranslate(@Param("now") LocalDateTime now);

    @Query("SELECT a.countryCode, COUNT(a) FROM Article a WHERE a.collectedAt > :after GROUP BY a.countryCode")
    List<Object[]> countByCountryAfter(@Param("after") LocalDateTime after);

    List<Article> findByCountryCodeAndCollectedAtBetweenOrderByCollectedAtDesc(
            String countryCode, LocalDateTime start, LocalDateTime end, Pageable pageable);
}
