package io.newsworld.collector.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "llm_usage")
@Getter
@Setter
public class LlmUsage {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "llm_usage_seq")
    @SequenceGenerator(name = "llm_usage_seq", sequenceName = "llm_usage_seq", allocationSize = 10)
    private Long id;

    @Column(name = "called_at", nullable = false)
    private LocalDateTime calledAt;

    @Column(name = "pipeline", length = 20)
    private String pipeline;

    @Column(name = "model", nullable = false, length = 64)
    private String model;

    @Column(name = "prompt_tokens")
    private Integer promptTokens;

    @Column(name = "completion_tokens")
    private Integer completionTokens;

    @Column(name = "total_tokens")
    private Integer totalTokens;

    @Column(name = "duration_ms")
    private Integer durationMs;
}
