package io.newsworld.collector.doctor;

public record RssCheckResult(
        Status status,
        String url,
        int httpCode,
        int articleCount,
        String detail
) {
    public enum Status {
        OK,           // flux valide, articles parsés
        HTTP_ERROR,   // 4xx/5xx
        PARSE_ERROR,  // réponse HTTP 200 mais pas du RSS/Atom valide
        TIMEOUT,      // connexion ou lecture trop lente
        UNREACHABLE   // DNS/SSL/connexion impossible
    }

    public boolean isOk() { return status == Status.OK; }

    public static RssCheckResult ok(String url, int articleCount) {
        return new RssCheckResult(Status.OK, url, 200, articleCount, null);
    }

    public static RssCheckResult error(Status status, String url, int httpCode, String detail) {
        return new RssCheckResult(status, url, httpCode, 0, detail);
    }
}
