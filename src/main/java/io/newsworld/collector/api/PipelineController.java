package io.newsworld.collector.api;

import io.newsworld.collector.service.PipelineExecutorService;
import io.newsworld.collector.service.PipelineExecutorService.PipelineState;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/pipelines")
@RequiredArgsConstructor
public class PipelineController {

    private final PipelineExecutorService pipelineExecutor;

    @GetMapping("/status")
    public Map<String, PipelineState> status() {
        return pipelineExecutor.getStates();
    }

    @PostMapping("/enrich/run")
    public ResponseEntity<Map<String, String>> runEnrich() {
        if (pipelineExecutor.isRunning("enrich")) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("status", "already_running"));
        }
        pipelineExecutor.runEnrich();
        return ResponseEntity.accepted().body(Map.of("status", "started", "pipeline", "enrich"));
    }

    @PostMapping("/translate/run")
    public ResponseEntity<Map<String, String>> runTranslate() {
        if (pipelineExecutor.isRunning("translate")) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("status", "already_running"));
        }
        pipelineExecutor.runTranslate();
        return ResponseEntity.accepted().body(Map.of("status", "started", "pipeline", "translate"));
    }

    @PostMapping("/analyze/run")
    public ResponseEntity<Map<String, String>> runAnalyze(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        if (pipelineExecutor.isRunning("analyze")) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("status", "already_running"));
        }
        LocalDate target = date != null ? date : LocalDate.now();
        pipelineExecutor.runAnalyze(target);
        return ResponseEntity.accepted().body(Map.of("status", "started", "pipeline", "analyze", "date", target.toString()));
    }
}
