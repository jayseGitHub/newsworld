package io.newsworld.collector.scheduler;

import io.newsworld.collector.service.TranslationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Profile("!probe & !doctor & !translate & !analyze & !enrich")
@RequiredArgsConstructor
@Slf4j
public class TranslationScheduler {

    private final TranslationService translationService;

    @Scheduled(cron = "${newsworld.pipelines.translation-cron:0 30 */6 * * *}")
    public void translate() {
        log.info("=== TRANSLATION START ===");
        int count = translationService.translateBatch();
        log.info("=== TRANSLATION END — {} articles ===", count);
    }
}
