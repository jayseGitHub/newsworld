package io.newsworld.collector.scheduler;

import io.newsworld.collector.config.CollectorProperties;
import io.newsworld.collector.model.CollectionResult;
import io.newsworld.collector.model.CollectionType;
import io.newsworld.collector.model.Country;
import io.newsworld.collector.service.ArticleService;
import io.newsworld.collector.service.CorrelationService;
import io.newsworld.collector.service.CountryService;
import io.newsworld.collector.service.JsonApiCollectorService;
import io.newsworld.collector.service.NewsSitemapCollectorService;
import io.newsworld.collector.service.RssCollectorService;
import io.newsworld.collector.service.ScraperService;
import io.newsworld.collector.service.WordPressApiCollectorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * Triggers collection for each active country when it is 07:00 local time in that country.
 * The server timezone is irrelevant — scheduling is always country-centric.
 *
 * Key properties (newsworld.collector.schedule.*):
 *   cron                    — how often to check (default: every 15 min)
 *   collection-hour         — target local hour in the country (default: 7)
 *   collection-window-minutes — width of the collection window (default: 15)
 *   bypass-time-check       — skip isDue() entirely, useful for dev/test (default: false)
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CollectionScheduler {

    private final CountryService countryService;
    private final RssCollectorService rssCollectorService;
    private final NewsSitemapCollectorService newsSitemapCollectorService;
    private final WordPressApiCollectorService wordPressApiCollectorService;
    private final JsonApiCollectorService jsonApiCollectorService;
    private final ScraperService scraperService;
    private final ArticleService articleService;
    private final CorrelationService correlationService;
    private final CollectorProperties props;

    @Scheduled(cron = "${newsworld.collector.schedule.cron:0 */15 * * * *}")
    public void collectDueCountries() {
        List<Country> todayCountries = countryService.getTodayCountries();
        int triggered = 0;

        for (Country country : todayCountries) {
            if (isDue(country)) {
                log.info("Collecting {} ({}) — local time window reached", country.getCode(), country.getName());
                collectAsync(country);
                triggered++;
            }
        }

        if (triggered > 0) {
            log.info("Triggered collection for {} countries", triggered);
        }
    }

    @Scheduled(cron = "${newsworld.collector.schedule.purge-cron:0 0 3 * * *}")
    public void purgeExpired() {
        log.info("Starting daily purge of expired articles");
        articleService.purgeExpired();
    }

    @Async
    public void collectAsync(Country country) {
        try {
            CollectionType type = country.getCollectionType() != null
                    ? country.getCollectionType() : CollectionType.RSS;

            switch (type) {
                case NEWS_SITEMAP -> {
                    var articles = newsSitemapCollectorService.collect(country);
                    articles.forEach(correlationService::computeCorrelations);
                }
                case WORDPRESS_REST -> {
                    var articles = wordPressApiCollectorService.collect(country);
                    articles.forEach(correlationService::computeCorrelations);
                }
                case JSON_API -> {
                    var articles = jsonApiCollectorService.collect(country);
                    articles.forEach(correlationService::computeCorrelations);
                }
                case SCRAPER -> {
                    var articles = scraperService.scrape(country);
                    articles.forEach(correlationService::computeCorrelations);
                }
                default -> {
                    CollectionResult result = rssCollectorService.collect(country);
                    if (result.source() != CollectionResult.Source.RSS_NOT_MODIFIED) {
                        result.articles().forEach(correlationService::computeCorrelations);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Collection error for {}: {}", country.getCode(), e.getMessage());
        }
    }

    /**
     * Returns true if the country's local time falls within the configured collection window.
     * Bypassed entirely when newsworld.collector.bypass-time-check=true.
     */
    public boolean isDue(Country country) {
        if (props.isBypassTimeCheck()) {
            return true;
        }
        try {
            ZonedDateTime local = ZonedDateTime.now(ZoneId.of(country.getIanaTimezone()));
            int hour = local.getHour();
            int minute = local.getMinute();
            return hour == props.getSchedule().getCollectionHour()
                    && minute < props.getSchedule().getCollectionWindowMinutes();
        } catch (Exception e) {
            log.warn("Invalid timezone for {}: {}", country.getCode(), country.getIanaTimezone());
            return false;
        }
    }
}
