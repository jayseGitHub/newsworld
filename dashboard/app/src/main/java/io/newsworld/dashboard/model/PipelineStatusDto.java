package io.newsworld.dashboard.model;

public class PipelineStatusDto {
    public String status;   // IDLE | RUNNING | SUCCESS | FAILED
    public String startedAt;
    public String finishedAt;
    public String message;
}
