package io.newsworld.dashboard.model;

import java.time.LocalDateTime;

public class LlmUsageDto {
    public long id;
    public LocalDateTime calledAt;
    public String pipeline;
    public String model;
    public int promptTokens;
    public int completionTokens;
    public int totalTokens;
    public int durationMs;
}
