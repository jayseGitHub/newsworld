package io.newsworld.collector.service;

import io.newsworld.collector.model.Article;
import io.newsworld.collector.repository.ArticleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ArticleService {

    private final ArticleRepository articleRepository;

    public List<Article> getByCountry(String countryCode) {
        return articleRepository.findByCountryCodeOrderByPublishedAtDesc(countryCode);
    }

    public List<Article> getByContinent(String continent) {
        return articleRepository.findByContinentOrderByPublishedAtDesc(continent);
    }

    public List<Article> getByCountrySince(String countryCode, LocalDateTime since) {
        return articleRepository.findByCountryCodeAndPublishedAtAfterOrderByPublishedAtDesc(
                countryCode, since);
    }

    @Transactional
    public int purgeExpired() {
        int deleted = articleRepository.deleteExpired(LocalDateTime.now());
        log.info("Purged {} expired articles", deleted);
        return deleted;
    }
}
