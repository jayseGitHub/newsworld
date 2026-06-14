package io.newsworld.collector.api;

import io.newsworld.collector.api.dto.ClusterDto;
import io.newsworld.collector.repository.ArticleClusterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/clusters")
@RequiredArgsConstructor
public class ClusterController {

    private final ArticleClusterRepository clusterRepository;

    @GetMapping
    public List<ClusterDto> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        LocalDate target = date != null ? date : LocalDate.now();
        return clusterRepository.findByClusterDateOrderByRelevanceScoreDesc(target)
                .stream().map(ClusterDto::from).toList();
    }

    @GetMapping("/top")
    public List<ClusterDto> top() {
        return clusterRepository.findTop10ByOrderByRelevanceScoreDesc()
                .stream().map(ClusterDto::from).toList();
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public ResponseEntity<ClusterDto> get(@PathVariable Long id) {
        return clusterRepository.findByIdWithArticles(id)
                .map(c -> ResponseEntity.ok(ClusterDto.withSources(c)))
                .orElse(ResponseEntity.notFound().build());
    }
}
