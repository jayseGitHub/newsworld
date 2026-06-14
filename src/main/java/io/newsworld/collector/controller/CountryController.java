package io.newsworld.collector.controller;

import io.newsworld.collector.api.dto.ArticleListDto;
import io.newsworld.collector.api.dto.CountryDto;
import io.newsworld.collector.model.Country;
import io.newsworld.collector.repository.ArticleRepository;
import io.newsworld.collector.repository.CountryRepository;
import io.newsworld.collector.service.CountryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/countries")
@RequiredArgsConstructor
public class CountryController {

    private final CountryService countryService;
    private final CountryRepository countryRepository;
    private final ArticleRepository articleRepository;

    @GetMapping
    public List<CountryDto> all() {
        LocalDateTime since = LocalDate.now().minusDays(30).atStartOfDay();
        Map<String, Long> countByCode = articleRepository.countByCountryAfter(since)
                .stream().collect(Collectors.toMap(r -> (String) r[0], r -> (Long) r[1]));
        return countryRepository.findAll().stream()
                .map(c -> CountryDto.from(c, countByCode.getOrDefault(c.getCode(), 0L)))
                .toList();
    }

    @GetMapping("/{code}")
    public ResponseEntity<CountryDto> get(@PathVariable String code) {
        return countryRepository.findById(code.toUpperCase())
                .map(c -> {
                    LocalDateTime since = LocalDate.now().minusDays(30).atStartOfDay();
                    long count = articleRepository.countByCountryAfter(since).stream()
                            .filter(r -> code.equalsIgnoreCase((String) r[0]))
                            .mapToLong(r -> (Long) r[1]).sum();
                    return ResponseEntity.ok(CountryDto.from(c, count));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{code}/articles")
    public ResponseEntity<List<ArticleListDto>> articles(
            @PathVariable String code,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        LocalDateTime start = LocalDate.now().minusDays(30).atStartOfDay();
        LocalDateTime end = LocalDateTime.now();
        List<ArticleListDto> result = articleRepository
                .findByCountryCodeAndCollectedAtBetweenOrderByCollectedAtDesc(
                        code.toUpperCase(), start, end, PageRequest.of(page, size))
                .stream().map(ArticleListDto::from).toList();
        return ResponseEntity.ok(result);
    }

    @GetMapping("/today")
    public ResponseEntity<List<Country>> today() {
        return ResponseEntity.ok(countryService.getTodayCountries());
    }

    @GetMapping("/today/{continent}")
    public ResponseEntity<List<Country>> todayByContinent(@PathVariable String continent) {
        return ResponseEntity.ok(countryService.getTodayCountriesForContinent(continent));
    }

    @GetMapping("/day/{weekDay}")
    public ResponseEntity<List<Country>> byDay(@PathVariable int weekDay) {
        return ResponseEntity.ok(countryService.getCountriesForDay(weekDay));
    }
}
