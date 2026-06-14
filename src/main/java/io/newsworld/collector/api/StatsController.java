package io.newsworld.collector.api;

import io.newsworld.collector.api.dto.StatsDto;
import io.newsworld.collector.repository.ArticleClusterRepository;
import io.newsworld.collector.repository.ArticleRepository;
import io.newsworld.collector.repository.CountryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;

@RestController
@RequestMapping("/stats")
@RequiredArgsConstructor
public class StatsController {

    private final ArticleRepository articleRepository;
    private final ArticleClusterRepository clusterRepository;
    private final CountryRepository countryRepository;

    @GetMapping
    public StatsDto stats() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        LocalDateTime todayEnd = todayStart.plusDays(1);

        return new StatsDto(
                articleRepository.countByExpiresAtAfter(now),
                articleRepository.countByCollectedAtBetween(todayStart, todayEnd),
                articleRepository.countPendingEnrich(now),
                articleRepository.countPendingTranslate(now),
                clusterRepository.count(),
                clusterRepository.findByClusterDateOrderByRelevanceScoreDesc(LocalDate.now()).size(),
                countryRepository.count()
        );
    }
}
