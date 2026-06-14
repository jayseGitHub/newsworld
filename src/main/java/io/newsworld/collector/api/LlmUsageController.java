package io.newsworld.collector.api;

import io.newsworld.collector.api.dto.LlmUsageDto;
import io.newsworld.collector.repository.LlmUsageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/llm-usage")
@RequiredArgsConstructor
public class LlmUsageController {

    private final LlmUsageRepository llmUsageRepository;

    @GetMapping
    public List<LlmUsageDto> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        LocalDate target = date != null ? date : LocalDate.now();
        LocalDateTime start = target.atStartOfDay();
        LocalDateTime end = target.plusDays(1).atStartOfDay();
        return llmUsageRepository.findByCalledAtBetweenOrderByCalledAtDesc(start, end)
                .stream().map(LlmUsageDto::from).toList();
    }

    @GetMapping("/summary")
    public Map<String, Object> summary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        LocalDate target = date != null ? date : LocalDate.now();
        LocalDateTime start = target.atStartOfDay();
        LocalDateTime end = target.plusDays(1).atStartOfDay();

        List<Object[]> byModel = llmUsageRepository.sumByModelBetween(start, end);
        List<Object[]> byPipeline = llmUsageRepository.sumByPipelineBetween(start, end);

        Map<String, Object> models = byModel.stream().collect(Collectors.toMap(
                r -> (String) r[0],
                r -> Map.of("totalTokens", r[1], "totalDurationMs", r[2], "calls", r[3])
        ));
        Map<String, Long> pipelines = byPipeline.stream().collect(Collectors.toMap(
                r -> (String) r[0],
                r -> (Long) r[1]
        ));

        return Map.of("date", target, "byModel", models, "byPipeline", pipelines);
    }

    @GetMapping("/total")
    public Map<String, Object> total() {
        List<Object[]> rows = llmUsageRepository.totals();
        Object[] t = rows.isEmpty() ? new Object[]{0L, 0L} : rows.get(0);
        long totalCalls  = t[0] != null ? ((Number) t[0]).longValue() : 0L;
        long totalTokens = t[1] != null ? ((Number) t[1]).longValue() : 0L;

        List<Object[]> byModel = llmUsageRepository.sumByModelAll();
        Map<String, Object> models = byModel.stream().collect(Collectors.toMap(
                r -> (String) r[0],
                r -> Map.of(
                        "promptTokens",     r[1] != null ? ((Number) r[1]).longValue() : 0L,
                        "completionTokens", r[2] != null ? ((Number) r[2]).longValue() : 0L,
                        "totalTokens",      r[3] != null ? ((Number) r[3]).longValue() : 0L,
                        "calls",            r[4] != null ? ((Number) r[4]).longValue() : 0L
                )
        ));

        return Map.of("totalCalls", totalCalls, "totalTokens", totalTokens, "byModel", models);
    }
}
