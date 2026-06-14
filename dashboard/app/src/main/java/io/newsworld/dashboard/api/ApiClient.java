package io.newsworld.dashboard.api;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializer;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;

public class ApiClient {

    public static final String PREFS = "newsworld_prefs";
    public static final String KEY_SERVER_URL = "server_url";
    public static final String DEFAULT_URL = "http://localhost:8090/api";

    private static ApiClient instance;
    private final SharedPreferences prefs;
    private final OkHttpClient http;
    public final Gson gson;
    private final ExecutorService executor = Executors.newFixedThreadPool(3);

    private ApiClient(Context ctx) {
        prefs = ctx.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        http = buildHttp();
        gson = new GsonBuilder()
                .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
                .registerTypeAdapter(LocalDateTime.class, (JsonDeserializer<LocalDateTime>) (json, type, c) ->
                        LocalDateTime.parse(json.getAsString(), DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                .registerTypeAdapter(LocalDate.class, (JsonDeserializer<LocalDate>) (json, type, c) ->
                        LocalDate.parse(json.getAsString()))
                .create();
    }

    public static synchronized ApiClient get(Context ctx) {
        if (instance == null) instance = new ApiClient(ctx);
        return instance;
    }

    public String baseUrl() {
        return prefs.getString(KEY_SERVER_URL, DEFAULT_URL);
    }

    public void setBaseUrl(String url) {
        prefs.edit().putString(KEY_SERVER_URL, url).apply();
    }

    public OkHttpClient http() { return http; }

    public ExecutorService executor() { return executor; }

    private OkHttpClient buildHttp() {
        return new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();
    }
}
