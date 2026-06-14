package io.newsworld.dashboard.api;

import android.content.Context;

import com.google.gson.reflect.TypeToken;

import org.json.JSONObject;

import java.io.IOException;
import java.lang.reflect.Type;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import io.newsworld.dashboard.model.ArticleDto;
import io.newsworld.dashboard.model.ClusterDto;
import io.newsworld.dashboard.model.CountryDto;
import io.newsworld.dashboard.model.LlmUsageDto;
import io.newsworld.dashboard.model.PipelineStatusDto;
import io.newsworld.dashboard.model.StatsDto;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class NewsWorldApi {

    private static final MediaType JSON = MediaType.get("application/json");

    private final ApiClient client;

    public NewsWorldApi(Context ctx) {
        this.client = ApiClient.get(ctx);
    }

    // --- Stats ---

    public StatsDto getStats() throws IOException {
        return get("/stats", StatsDto.class);
    }

    // --- Countries ---

    public List<CountryDto> getCountries() throws IOException {
        Type type = new TypeToken<List<CountryDto>>(){}.getType();
        return getList("/countries", type);
    }

    public List<ArticleDto> getCountryArticles(String code, int page) throws IOException {
        Type type = new TypeToken<List<ArticleDto>>(){}.getType();
        return getList("/countries/" + code + "/articles?page=" + page + "&size=20", type);
    }

    // --- Clusters ---

    public List<ClusterDto> getClusters(LocalDate date) throws IOException {
        String dateParam = date != null ? "?date=" + date : "";
        Type type = new TypeToken<List<ClusterDto>>(){}.getType();
        return getList("/clusters" + dateParam, type);
    }

    public List<ClusterDto> getTopClusters() throws IOException {
        Type type = new TypeToken<List<ClusterDto>>(){}.getType();
        return getList("/clusters/top", type);
    }

    public ClusterDto getCluster(long id) throws IOException {
        return get("/clusters/" + id, ClusterDto.class);
    }

    // --- LLM Usage ---

    public List<LlmUsageDto> getLlmUsage(LocalDate date) throws IOException {
        String dateParam = date != null ? "?date=" + date : "";
        Type type = new TypeToken<List<LlmUsageDto>>(){}.getType();
        return getList("/llm-usage" + dateParam, type);
    }

    public Map<String, Object> getLlmUsageSummary(LocalDate date) throws IOException {
        String dateParam = date != null ? "?date=" + date : "";
        Type type = new TypeToken<Map<String, Object>>(){}.getType();
        return get("/llm-usage/summary" + dateParam, type);
    }

    public Map<String, Object> getLlmUsageTotal() throws IOException {
        Type type = new TypeToken<Map<String, Object>>(){}.getType();
        return get("/llm-usage/total", type);
    }

    // --- Pipelines ---

    public Map<String, PipelineStatusDto> getPipelineStatus() throws IOException {
        Type type = new TypeToken<Map<String, PipelineStatusDto>>(){}.getType();
        return get("/pipelines/status", type);
    }

    public Map<String, String> runPipeline(String name) throws IOException {
        return runPipeline(name, null);
    }

    public Map<String, String> runPipeline(String name, LocalDate date) throws IOException {
        String url = "/pipelines/" + name + "/run";
        if (date != null) url += "?date=" + date;
        Type type = new TypeToken<Map<String, String>>(){}.getType();
        return post(url, type);
    }

    // --- Internal helpers ---

    private <T> T get(String path, Class<T> cls) throws IOException {
        String body = rawGet(path);
        return client.gson.fromJson(body, cls);
    }

    private <T> T get(String path, Type type) throws IOException {
        String body = rawGet(path);
        return client.gson.fromJson(body, type);
    }

    private <T> List<T> getList(String path, Type type) throws IOException {
        String body = rawGet(path);
        return client.gson.fromJson(body, type);
    }

    private <T> T post(String path, Type type) throws IOException {
        Request req = new Request.Builder()
                .url(client.baseUrl() + path)
                .post(RequestBody.create("{}", JSON))
                .build();
        try (Response resp = client.http().newCall(req).execute()) {
            String body = resp.body() != null ? resp.body().string() : "{}";
            return client.gson.fromJson(body, type);
        }
    }

    private String rawGet(String path) throws IOException {
        Request req = new Request.Builder()
                .url(client.baseUrl() + path)
                .get()
                .build();
        try (Response resp = client.http().newCall(req).execute()) {
            if (!resp.isSuccessful()) throw new IOException("HTTP " + resp.code() + " for " + path);
            return resp.body() != null ? resp.body().string() : "{}";
        }
    }
}
