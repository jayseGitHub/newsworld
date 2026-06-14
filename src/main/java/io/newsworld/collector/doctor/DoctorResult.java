package io.newsworld.collector.doctor;

import io.newsworld.collector.model.Country;

public record DoctorResult(
        Country country,
        Verdict verdict,
        String originalRssUrl,
        String fixedRssUrl,      // non-null si on a trouvé mieux
        String discoveryMethod,  // "AUTODISCOVERY", "PATTERN:/feed/", "ORIGINAL_OK", etc.
        String detail
) {
    public enum Verdict {
        RSS_OK,        // RSS original fonctionne
        RSS_FIXED,     // RSS original cassé mais nouvelle URL trouvée
        API_FOUND,     // WP REST / news sitemap / JSON feed découvert
        SCRAPER_ONLY,  // pas de RSS ni API, scraper fonctionne
        BROKEN         // rien ne fonctionne
    }

    public boolean needsCsvUpdate() {
        return verdict == Verdict.RSS_FIXED && fixedRssUrl != null;
    }

    public boolean hasApiUrl() {
        return verdict == Verdict.API_FOUND && fixedRssUrl != null;
    }
}
