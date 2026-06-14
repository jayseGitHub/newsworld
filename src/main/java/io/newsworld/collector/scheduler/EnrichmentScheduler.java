package io.newsworld.collector.scheduler;

import io.newsworld.collector.service.ContentEnrichmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Profile("!probe & !doctor & !translate & !analyze & !enrich")
@RequiredArgsConstructor
@Slf4j
public class EnrichmentScheduler {

    private final ContentEnrichmentService contentEnrichmentService;

    @Scheduled(cron = "${newsworld.pipelines.enrichment-cron:0 0 */2 * * *}")
    public void enrich() {
        log.info("=== ENRICHMENT START ===");
        int count = contentEnrichmentService.enrichBatch();
        log.info("=== ENRICHMENT END — {} articles ===", count);
    }
}
