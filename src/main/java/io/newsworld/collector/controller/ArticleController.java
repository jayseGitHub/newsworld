package io.newsworld.collector.controller;

import io.newsworld.collector.model.Article;
import io.newsworld.collector.service.ArticleService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/articles")
@RequiredArgsConstructor
public class ArticleController {

    private final ArticleService articleService;

    @GetMapping("/country/{code}")
    public ResponseEntity<List<Article>> byCountry(@PathVariable String code) {
        return ResponseEntity.ok(articleService.getByCountry(code));
    }

    @GetMapping("/continent/{continent}")
    public ResponseEntity<List<Article>> byContinent(@PathVariable String continent) {
        return ResponseEntity.ok(articleService.getByContinent(continent));
    }

    @GetMapping("/country/{code}/since")
    public ResponseEntity<List<Article>> byCountrySince(
            @PathVariable String code,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime since) {
        return ResponseEntity.ok(articleService.getByCountrySince(code, since));
    }
}
