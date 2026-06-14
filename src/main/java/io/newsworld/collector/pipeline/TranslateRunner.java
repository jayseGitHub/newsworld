package io.newsworld.collector.pipeline;

import io.newsworld.collector.service.TranslationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Pipeline 2 — CLI runner for the translate profile.
 * Drains all untranslated articles in batches until none remain, then exits.
 *
 * Usage:
 *   MISTRAL_API_KEY=xxx mvn spring-boot:run -Dspring-boot.run.profiles=translate
 */
@Profile("translate")
@Component
@RequiredArgsConstructor
@Slf4j
public class TranslateRunner implements ApplicationRunner {

    private final TranslationService translationService;

    @Override
    public void run(ApplicationArguments args) {
        log.info("╔══════════════════════════════════════════════╗");
        log.info("║           TRANSLATE PIPELINE START           ║");
        log.info("╚══════════════════════════════════════════════╝");

        int total = 0;
        int batch = 0;

        while (true) {
            int count = translationService.translateBatch();
            total += count;
            batch++;

            if (count == 0) {
                log.info("No more articles to translate (batch #{} returned 0)", batch);
                break;
            }

            log.info("Batch #{} — {} articles translated (running total: {})", batch, count, total);
        }

        log.info("╔══════════════════════════════════════════════╗");
        log.info("║           TRANSLATE PIPELINE END             ║");
        log.info("║  Total translated : {}", pad(total));
        log.info("║  Batches run      : {}", pad(batch));
        log.info("╚══════════════════════════════════════════════╝");

        System.exit(0);
    }

    private String pad(int n) {
        return String.format("%-38s║", n);
    }
}
