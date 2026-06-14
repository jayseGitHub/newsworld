package io.newsworld.collector.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.newsworld.collector.config.CacheConfig;
import io.newsworld.collector.config.NewsWorldProperties;
import io.newsworld.collector.model.LlmUsage;
import io.newsworld.collector.repository.LlmUsageRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

@Component
@Slf4j
public class MistralClient {

    private static final String CHAT_URL = "https://api.mistral.ai/v1/chat/completions";
    private static final int CONNECT_TIMEOUT = 15_000;
    private static final int READ_TIMEOUT    = 90_000;

    private final ObjectMapper objectMapper;
    private final LlmUsageRepository llmUsageRepository;
    private final NewsWorldProperties props;
    private final String modelTranslation;
    private final String modelAnalysis;

    public MistralClient(ObjectMapper objectMapper, LlmUsageRepository llmUsageRepository,
                         NewsWorldProperties props) {
        this.objectMapper = objectMapper;
        this.llmUsageRepository = llmUsageRepository;
        this.props = props;
        this.modelTranslation = props.getMistral().getModelTranslation();
        this.modelAnalysis = props.getMistral().getModelAnalysis();
    }

    @Cacheable(cacheNames = CacheConfig.CACHE_TRANSLATE)
    public String translate(String systemPrompt, String userMessage) throws IOException {
        return complete(systemPrompt, userMessage, modelTranslation, 0.1);
    }

    @Cacheable(cacheNames = CacheConfig.CACHE_ANALYZE)
    public String analyze(String systemPrompt, String userMessage) throws IOException {
        return complete(systemPrompt, userMessage, modelAnalysis, 0.3);
    }

    private String complete(String systemPrompt, String userMessage, String model, double temperature) throws IOException {
        String apiKey = props.getMistral().getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new IOException("Mistral API key not configured (newsworld.mistral.api-key)");
        }

        String requestBody = buildRequest(model, temperature, systemPrompt, userMessage);
        log.debug("Mistral {} call — {} chars user message", model, userMessage.length());

        HttpURLConnection conn = (HttpURLConnection) URI.create(CHAT_URL).toURL().openConnection();
        long start = System.currentTimeMillis();
        try {
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setConnectTimeout(CONNECT_TIMEOUT);
            conn.setReadTimeout(READ_TIMEOUT);
            conn.setRequestProperty("Authorization", "Bearer " + apiKey);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Accept", "application/json");

            try (OutputStream os = conn.getOutputStream()) {
                os.write(requestBody.getBytes(StandardCharsets.UTF_8));
            }

            int status = conn.getResponseCode();
            if (status != 200) {
                String error;
                try (InputStream err = conn.getErrorStream()) {
                    error = err != null ? new String(err.readAllBytes(), StandardCharsets.UTF_8) : "(no body)";
                }
                throw new IOException("Mistral HTTP " + status + " [" + model + "]: " + error);
            }

            String responseBody;
            try (InputStream body = conn.getInputStream()) {
                responseBody = new String(body.readAllBytes(), StandardCharsets.UTF_8);
            }

            long durationMs = System.currentTimeMillis() - start;

            JsonNode response = objectMapper.readTree(responseBody);
            String content = response.path("choices").path(0).path("message").path("content").asText();

            int promptTokens     = response.path("usage").path("prompt_tokens").asInt(0);
            int completionTokens = response.path("usage").path("completion_tokens").asInt(0);
            int totalTokens      = response.path("usage").path("total_tokens").asInt(promptTokens + completionTokens);

            log.debug("Mistral {} — prompt={} completion={} total={} tokens ({}ms)",
                    model, promptTokens, completionTokens, totalTokens, durationMs);

            saveUsage(model, promptTokens, completionTokens, totalTokens, (int) durationMs);

            return content;
        } finally {
            conn.disconnect();
        }
    }

    private void saveUsage(String model, int promptTokens, int completionTokens, int totalTokens, int durationMs) {
        try {
            LlmUsage usage = new LlmUsage();
            usage.setCalledAt(LocalDateTime.now());
            usage.setPipeline(inferPipeline(model));
            usage.setModel(model);
            usage.setPromptTokens(promptTokens);
            usage.setCompletionTokens(completionTokens);
            usage.setTotalTokens(totalTokens);
            usage.setDurationMs(durationMs);
            llmUsageRepository.save(usage);
        } catch (Exception e) {
            log.warn("Failed to save LLM usage stats: {}", e.getMessage());
        }
    }

    private String inferPipeline(String model) {
        if (model.equals(modelTranslation)) return "translate";
        if (model.equals(modelAnalysis))    return "analyze";
        return "unknown";
    }

    private String buildRequest(String model, double temperature, String systemPrompt, String userMessage) throws IOException {
        ObjectNode request = objectMapper.createObjectNode();
        request.put("model", model);
        request.put("temperature", temperature);

        ArrayNode messages = request.putArray("messages");

        ObjectNode system = messages.addObject();
        system.put("role", "system");
        system.put("content", systemPrompt);

        ObjectNode user = messages.addObject();
        user.put("role", "user");
        user.put("content", userMessage);

        return objectMapper.writeValueAsString(request);
    }
}
