package io.newsworld.collector.api.dto;

import io.newsworld.collector.model.LlmUsage;

import java.time.LocalDateTime;

public record LlmUsageDto(
        Long id,
        LocalDateTime calledAt,
        String pipeline,
        String model,
        int promptTokens,
        int completionTokens,
        int totalTokens,
        int durationMs
) {
    public static LlmUsageDto from(LlmUsage u) {
        return new LlmUsageDto(
                u.getId(), u.getCalledAt(), u.getPipeline(), u.getModel(),
                u.getPromptTokens() != null ? u.getPromptTokens() : 0,
                u.getCompletionTokens() != null ? u.getCompletionTokens() : 0,
                u.getTotalTokens() != null ? u.getTotalTokens() : 0,
                u.getDurationMs() != null ? u.getDurationMs() : 0
        );
    }
}
