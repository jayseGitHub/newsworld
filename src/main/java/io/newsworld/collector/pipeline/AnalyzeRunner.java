package io.newsworld.collector.pipeline;

import io.newsworld.collector.model.ArticleCluster;
import io.newsworld.collector.repository.ArticleClusterRepository;
import io.newsworld.collector.service.DailyAnalysisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * Pipeline 3 — CLI runner for the analyze profile.
 * Runs the daily analysis for a given date (default: today) then exits.
 *
 * Usage:
 *   MISTRAL_API_KEY=xxx mvn spring-boot:run -Dspring-boot.run.profiles=analyze
 *   MISTRAL_API_KEY=xxx mvn spring-boot:run -Dspring-boot.run.profiles=analyze \
 *       -Dspring-boot.run.arguments=2026-06-13
 */
@Profile("analyze")
@Component
@RequiredArgsConstructor
@Slf4j
public class AnalyzeRunner implements ApplicationRunner {

    private final DailyAnalysisService dailyAnalysisService;
    private final ArticleClusterRepository clusterRepository;

    @Override
    public void run(ApplicationArguments args) {
        LocalDate date = resolveDate(args);

        log.info("╔══════════════════════════════════════════════╗");
        log.info("║           ANALYZE PIPELINE START             ║");
        log.info("║  Date : {}", String.format("%-37s║", date));
        log.info("╚══════════════════════════════════════════════╝");

        int clusters = dailyAnalysisService.analyzeDay(date);

        log.info("");
        log.info("╔══════════════════════════════════════════════╗");
        log.info("║           ANALYZE PIPELINE END               ║");
        log.info("║  Clusters créés : {}", pad(clusters));

        if (clusters > 0) {
            List<ArticleCluster> top = clusterRepository.findByClusterDateOrderByRelevanceScoreDesc(date);
            log.info("╠══════════════════════════════════════════════╣");
            log.info("║  TOP CLUSTERS (par pertinence)               ║");
            for (int i = 0; i < Math.min(top.size(), 10); i++) {
                ArticleCluster c = top.get(i);
                log.info("║  {}", String.format("%2d. [score=%4.0f] %d pays — %s",
                        i + 1, c.getRelevanceScore(), c.getCountryCount(), c.getTopic()));
            }
        }

        log.info("╚══════════════════════════════════════════════╝");

        System.exit(0);
    }

    private LocalDate resolveDate(ApplicationArguments args) {
        List<String> nonOption = args.getNonOptionArgs();
        if (!nonOption.isEmpty()) {
            try {
                return LocalDate.parse(nonOption.get(0));
            } catch (DateTimeParseException e) {
                log.warn("Invalid date argument '{}', defaulting to today", nonOption.get(0));
            }
        }
        return LocalDate.now();
    }

    private String pad(int n) {
        return String.format("%-38s║", n);
    }
}
