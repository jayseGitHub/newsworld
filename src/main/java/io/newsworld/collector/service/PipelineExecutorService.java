package io.newsworld.collector.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class PipelineExecutorService {

    public enum PipelineStatus { IDLE, RUNNING, SUCCESS, FAILED }

    public record PipelineState(PipelineStatus status, LocalDateTime startedAt, LocalDateTime finishedAt, String message) {}

    private final Map<String, PipelineState> states = new ConcurrentHashMap<>();

    private final ContentEnrichmentService enrichmentService;
    private final TranslationService translationService;
    private final DailyAnalysisService dailyAnalysisService;

    public Map<String, PipelineState> getStates() {
        return Map.copyOf(states);
    }

    public boolean isRunning(String pipeline) {
        PipelineState s = states.get(pipeline);
        return s != null && s.status() == PipelineStatus.RUNNING;
    }

    @Async
    public void runEnrich() {
        runBatched("enrich", enrichmentService::enrichBatch, "articles enriched");
    }

    @Async
    public void runTranslate() {
        runBatched("translate", translationService::translateBatch, "articles translated");
    }

    @Async
    public void runAnalyze(LocalDate date) {
        runOnce("analyze", () -> dailyAnalysisService.analyzeDay(date), "clusters created");
    }

    private void runBatched(String name, Callable<Integer> task, String unit) {
        if (isRunning(name)) return;
        setState(name, PipelineStatus.RUNNING, "Started", null);
        try {
            int total = 0, count;
            do { count = task.call(); total += count; } while (count > 0);
            complete(name, total + " " + unit);
        } catch (Exception e) {
            fail(name, e.getMessage());
        }
    }

    private void runOnce(String name, Callable<Integer> task, String unit) {
        if (isRunning(name)) return;
        setState(name, PipelineStatus.RUNNING, "Started", null);
        try {
            int count = task.call();
            complete(name, count + " " + unit);
        } catch (Exception e) {
            fail(name, e.getMessage());
        }
    }

    private void complete(String name, String message) {
        setState(name, PipelineStatus.SUCCESS, message, LocalDateTime.now());
        log.info("{} pipeline complete — {}", name, message);
    }

    private void fail(String name, String message) {
        setState(name, PipelineStatus.FAILED, message, LocalDateTime.now());
        log.error("{} pipeline failed: {}", name, message);
    }

    private void setState(String pipeline, PipelineStatus status, String message, LocalDateTime finishedAt) {
        LocalDateTime startedAt = status == PipelineStatus.RUNNING
                ? LocalDateTime.now()
                : (states.containsKey(pipeline) ? states.get(pipeline).startedAt() : LocalDateTime.now());
        states.put(pipeline, new PipelineState(status, startedAt, finishedAt, message));
    }
}
