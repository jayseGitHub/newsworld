package io.newsworld.collector.repository;

import io.newsworld.collector.model.LlmUsage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface LlmUsageRepository extends JpaRepository<LlmUsage, Long> {

    List<LlmUsage> findByCalledAtBetweenOrderByCalledAtDesc(LocalDateTime start, LocalDateTime end);

    @Query("SELECT u.model, SUM(u.totalTokens), SUM(u.durationMs), COUNT(u) " +
           "FROM LlmUsage u WHERE u.calledAt BETWEEN :start AND :end GROUP BY u.model")
    List<Object[]> sumByModelBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("SELECT u.pipeline, SUM(u.totalTokens) FROM LlmUsage u " +
           "WHERE u.calledAt BETWEEN :start AND :end GROUP BY u.pipeline")
    List<Object[]> sumByPipelineBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("SELECT u.model, SUM(u.promptTokens), SUM(u.completionTokens), SUM(u.totalTokens), COUNT(u) " +
           "FROM LlmUsage u GROUP BY u.model")
    List<Object[]> sumByModelAll();

    @Query("SELECT COUNT(u), SUM(u.totalTokens) FROM LlmUsage u")
    List<Object[]> totals();
}
