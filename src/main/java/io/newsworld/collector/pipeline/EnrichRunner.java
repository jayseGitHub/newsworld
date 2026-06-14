package io.newsworld.collector.pipeline;

import io.newsworld.collector.service.ContentEnrichmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Pipeline 1 — CLI runner for the enrich profile.
 * Fetches article pages and extracts originalSummary for all unenriched articles,
 * then marks content_fetched_at so the translate pipeline can pick them up.
 *
 * Usage:
 *   mvn spring-boot:run -Dspring-boot.run.profiles=enrich
 *
 * Note: many pages will return 403/timeout (bot protection) — those are marked
 * as fetched with null summary so translation can still proceed on the title alone.
 */
@Profile("enrich")
@Component
@RequiredArgsConstructor
@Slf4j
public class EnrichRunner implements ApplicationRunner {

    private final ContentEnrichmentService contentEnrichmentService;

    @Override
    public void run(ApplicationArguments args) {
        log.info("╔══════════════════════════════════════════════╗");
        log.info("║           ENRICH PIPELINE START              ║");
        log.info("╚══════════════════════════════════════════════╝");

        int total = 0;
        int batch = 0;

        while (true) {
            int count = contentEnrichmentService.enrichBatch();
            batch++;
            total += count;

            if (count == 0) {
                log.info("No more articles to enrich (batch #{} returned 0)", batch);
                break;
            }

            log.info("Batch #{} — {} articles processed (running total: {})", batch, count, total);
        }

        log.info("╔══════════════════════════════════════════════╗");
        log.info("║           ENRICH PIPELINE END                ║");
        log.info("║  Total processed  : {}", pad(total));
        log.info("║  Batches run      : {}", pad(batch));
        log.info("╚══════════════════════════════════════════════╝");

        System.exit(0);
    }

    private String pad(int n) {
        return String.format("%-38s║", n);
    }
}
