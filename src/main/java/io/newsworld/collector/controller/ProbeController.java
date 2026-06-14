package io.newsworld.collector.controller;

import io.newsworld.collector.model.Article;
import io.newsworld.collector.model.CollectionResult;
import io.newsworld.collector.model.Country;
import io.newsworld.collector.repository.CountryRepository;
import io.newsworld.collector.repository.FeedCacheRepository;
import io.newsworld.collector.service.RssCollectorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
@Slf4j
public class ProbeController {

    private final CountryRepository countryRepository;
    private final FeedCacheRepository feedCacheRepository;
    private final RssCollectorService rssCollectorService;

    /**
     * Collects from a single country on demand and returns diagnostics.
     * Useful for testing individual feed URLs and validating ETag caching.
     * Second call should return source=RSS_NOT_MODIFIED (304) if the feed supports ETags.
     */
    @PostMapping("/probe/{code}")
    public ProbeResponse probe(@PathVariable String code) {
        Country country = countryRepository.findById(code.toUpperCase())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown country: " + code));

        log.info("Manual probe triggered for {} ({})", country.getCode(), country.getName());
        CollectionResult result = rssCollectorService.collect(country);

        Article first = result.articles().isEmpty() ? null : result.articles().get(0);
        return new ProbeResponse(
                country.getCode(),
                country.getName(),
                country.getMediaName(),
                result.source().name(),
                result.httpStatus(),
                result.articles().size(),
                result.etag(),
                result.durationMs(),
                first != null ? first.getOriginalTitle() : null,
                first != null ? first.getSourceUrl() : null
        );
    }

    /**
     * Probes all countries scheduled for today (same set the scheduler uses).
     * Runs synchronously — use only for diagnostics, not in production load.
     */
    @PostMapping("/probe")
    public List<ProbeResponse> probeAll(@RequestParam(defaultValue = "false") boolean allDays) {
        List<Country> countries = allDays
                ? countryRepository.findAll()
                : countryRepository.findAll().stream()
                        .filter(c -> feedCacheRepository.findById(c.getCode()).isEmpty())
                        .toList();

        log.info("Probing {} countries (allDays={})", countries.size(), allDays);
        return countries.stream().map(country -> {
            try {
                CollectionResult result = rssCollectorService.collect(country);
                Article first = result.articles().isEmpty() ? null : result.articles().get(0);
                return new ProbeResponse(
                        country.getCode(), country.getName(), country.getMediaName(),
                        result.source().name(), result.httpStatus(), result.articles().size(),
                        result.etag(), result.durationMs(),
                        first != null ? first.getOriginalTitle() : null,
                        first != null ? first.getSourceUrl() : null
                );
            } catch (Exception e) {
                log.error("Probe failed for {}: {}", country.getCode(), e.getMessage());
                return new ProbeResponse(country.getCode(), country.getName(), country.getMediaName(),
                        "ERROR", 0, 0, null, 0L, e.getMessage(), null);
            }
        }).toList();
    }

    public record ProbeResponse(
            String countryCode,
            String countryName,
            String mediaName,
            String source,
            int httpStatus,
            int articlesCollected,
            String etag,
            long durationMs,
            String firstArticleTitle,
            String firstArticleUrl
    ) {}
}
