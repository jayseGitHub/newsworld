package io.newsworld.collector.probe;

import io.newsworld.collector.model.CollectionResult;
import io.newsworld.collector.model.CollectionType;
import io.newsworld.collector.model.Country;
import io.newsworld.collector.repository.CountryRepository;
import io.newsworld.collector.service.JsonApiCollectorService;
import io.newsworld.collector.service.NewsSitemapCollectorService;
import io.newsworld.collector.service.RssCollectorService;
import io.newsworld.collector.service.ScraperService;
import io.newsworld.collector.service.WordPressApiCollectorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Profile("probe")
@Component
@RequiredArgsConstructor
@Slf4j
public class ProbeRunner implements ApplicationRunner {

    private final CountryRepository countryRepository;
    private final RssCollectorService rssCollectorService;
    private final NewsSitemapCollectorService newsSitemapCollectorService;
    private final WordPressApiCollectorService wordPressApiCollectorService;
    private final JsonApiCollectorService jsonApiCollectorService;
    private final ScraperService scraperService;

    @Override
    public void run(ApplicationArguments args) {
        List<Country> countries = countryRepository.findAll();
        log.info("=== PROBE START — {} countries ===", countries.size());

        List<String> failures = new ArrayList<>();
        Map<String, List<String>> bySource = new TreeMap<>();
        int totalArticles = 0;

        for (Country country : countries) {
            long start = System.currentTimeMillis();
            try {
                CollectionType type = country.getCollectionType() != null
                        ? country.getCollectionType() : CollectionType.RSS;

                int count;
                String label;
                String extra = "";

                switch (type) {
                    case NEWS_SITEMAP -> {
                        var articles = newsSitemapCollectorService.collect(country);
                        count = articles.size();
                        label = count > 0 ? "NEWS_SITEMAP" : "SITEMAP_EMPTY";
                    }
                    case WORDPRESS_REST -> {
                        var articles = wordPressApiCollectorService.collect(country);
                        count = articles.size();
                        label = count > 0 ? "WORDPRESS_REST" : "WP_EMPTY";
                    }
                    case JSON_API -> {
                        var articles = jsonApiCollectorService.collect(country);
                        count = articles.size();
                        label = count > 0 ? "JSON_API" : "JSON_EMPTY";
                    }
                    default -> {
                        CollectionResult result = rssCollectorService.collect(country);
                        count = result.articles().size();
                        label = switch (result.source()) {
                            case RSS_FRESH        -> "RSS_FRESH";
                            case RSS_NOT_MODIFIED -> "RSS_CACHED";
                            case SCRAPER          -> count > 0 ? "SCRAPER" : "SCRAPER_EMPTY";
                        };
                        if (result.etag() != null) extra = " — ETag: oui";
                    }
                }

                totalArticles += count;
                long duration = System.currentTimeMillis() - start;
                bySource.computeIfAbsent(label, k -> new ArrayList<>()).add(country.getCode());
                log.info("[{}] {} ({}) — {} articles — {}ms{}",
                        label, country.getCode(), country.getName(), count, duration, extra);

            } catch (Exception e) {
                failures.add(String.format("%-3s %-30s %s", country.getCode(), country.getName(), e.getMessage()));
                log.error("[ERROR] {} ({}) — {}", country.getCode(), country.getName(), e.getMessage());
            }
        }

        printSummary(countries.size(), totalArticles, bySource, failures);
        System.exit(0);
    }

    private void printSummary(int total, int totalArticles, Map<String, List<String>> bySource, List<String> failures) {
        log.info("");
        log.info("╔══════════════════════════════════════════════╗");
        log.info("║             PROBE SUMMARY                    ║");
        log.info("╠══════════════════════════════════════════════╣");
        log.info("║  Total pays     : {}", pad(total));
        log.info("║  Total articles : {}", pad(totalArticles));
        log.info("╠══════════════════════════════════════════════╣");
        bySource.forEach((source, codes) ->
            log.info("║  {} : {} pays — {}", String.format("%-15s", source), codes.size(), String.join(", ", codes))
        );
        log.info("╠══════════════════════════════════════════════╣");
        log.info("║  Échecs         : {}", pad(failures.size()));
        failures.forEach(f -> log.warn("║    {}", f));
        log.info("╚══════════════════════════════════════════════╝");
    }

    private String pad(int n) {
        return String.format("%-38s║", n);
    }
}
