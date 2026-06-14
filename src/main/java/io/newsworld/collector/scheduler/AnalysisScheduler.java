package io.newsworld.collector.scheduler;

import io.newsworld.collector.service.DailyAnalysisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
@Profile("!probe & !doctor & !translate & !analyze & !enrich")
@RequiredArgsConstructor
@Slf4j
public class AnalysisScheduler {

    private final DailyAnalysisService dailyAnalysisService;

    @Scheduled(cron = "${newsworld.pipelines.analysis-cron:0 30 23 * * *}")
    public void analyze() {
        LocalDate today = LocalDate.now();
        log.info("=== DAILY ANALYSIS START — {} ===", today);
        int clusters = dailyAnalysisService.analyzeDay(today);
        log.info("=== DAILY ANALYSIS END — {} clusters ===", clusters);
    }
}
