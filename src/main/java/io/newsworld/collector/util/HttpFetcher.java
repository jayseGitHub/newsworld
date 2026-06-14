package io.newsworld.collector.util;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.zip.GZIPInputStream;

@Component
public class HttpFetcher {

    public static final String BROWSER_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36";

    private static final int CONNECT_TIMEOUT = 12_000;
    private static final int READ_TIMEOUT = 18_000;

    public String fetch(String url) throws IOException {
        return fetch(url, "application/json, application/xml, text/xml, */*;q=0.8");
    }

    public String fetch(String url, String accept) throws IOException {
        try {
            HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
            SslUtils.disableSsl(conn);
            conn.setConnectTimeout(CONNECT_TIMEOUT);
            conn.setReadTimeout(READ_TIMEOUT);
            conn.setInstanceFollowRedirects(true);
            conn.setRequestProperty("User-Agent", BROWSER_UA);
            conn.setRequestProperty("Accept", accept);
            conn.setRequestProperty("Accept-Encoding", "gzip");
            conn.setRequestProperty("Accept-Language", "en-US,en;q=0.9");
            conn.setRequestProperty("Referer", "https://www.google.com/");

            int code = conn.getResponseCode();
            if (code != 200) throw new IOException("HTTP " + code + " for " + url);

            try (InputStream raw = conn.getInputStream()) {
                InputStream stream = "gzip".equalsIgnoreCase(conn.getContentEncoding())
                        ? new GZIPInputStream(raw) : raw;
                return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Fetch failed for " + url + ": " + e.getMessage(), e);
        }
    }

    public static LocalDateTime parseDate(String dateStr, LocalDateTime fallback) {
        if (dateStr == null || dateStr.isBlank()) return fallback;
        try {
            return OffsetDateTime.parse(dateStr).toLocalDateTime();
        } catch (Exception ignored) {}
        try {
            String s = dateStr.length() > 19 ? dateStr.substring(0, 19) : dateStr;
            return LocalDateTime.parse(s, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } catch (Exception ignored) {}
        return fallback;
    }
}
