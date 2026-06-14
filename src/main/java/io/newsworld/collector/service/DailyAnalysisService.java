package io.newsworld.collector.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.newsworld.collector.model.Article;
import io.newsworld.collector.model.ArticleCluster;
import io.newsworld.collector.repository.ArticleClusterRepository;
import io.newsworld.collector.repository.ArticleRepository;
import io.newsworld.collector.util.MistralClient;
import io.newsworld.collector.config.NewsWorldProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Pipeline 3 — Daily analysis.
 * Groups translated articles by topic (via Mistral), generates a synthesis
 * per cluster, and scores by geopolitical reach (country count, continent spread).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DailyAnalysisService {

    private static final int MAX_ARTICLES_FOR_CLUSTERING = 200;
    private static final int MIN_ARTICLES_PER_CLUSTER    = 2;
    private static final int MAX_CLUSTERS                = 25;

    private final ArticleRepository articleRepository;
    private final ArticleClusterRepository clusterRepository;
    private final MistralClient mistralClient;
    private final ObjectMapper objectMapper;
    private final NewsWorldProperties props;

    public int analyzeDay(LocalDate date) {
        if (clusterRepository.existsByClusterDate(date)) {
            log.info("Analysis for {} already exists — skipping", date);
            return 0;
        }

        LocalDateTime start = date.atStartOfDay();
        LocalDateTime end   = date.plusDays(1).atStartOfDay();
        List<Article> articles = articleRepository.findByCollectedAtBetween(start, end);

        if (articles.isEmpty()) {
            log.info("No articles found for {}", date);
            return 0;
        }

        // Keep only translated articles; fall back to original title if no translation yet
        List<Article> pool = articles.stream()
                .filter(a -> a.getOriginalTitle() != null)
                .limit(MAX_ARTICLES_FOR_CLUSTERING)
                .toList();

        log.info("Daily analysis for {} — {} articles", date, pool.size());

        try {
            List<ClusterSpec> specs = clusterArticles(pool, date);
            log.info("Mistral produced {} clusters for {}", specs.size(), date);

            Map<Long, Article> byId = pool.stream().collect(Collectors.toMap(Article::getId, a -> a));
            List<ArticleCluster> saved = new ArrayList<>();

            for (ClusterSpec spec : specs) {
                List<Article> clusterArticles = spec.articleIds().stream()
                        .map(byId::get)
                        .filter(Objects::nonNull)
                        .toList();

                if (clusterArticles.size() < MIN_ARTICLES_PER_CLUSTER) continue;

                Set<String> countries  = clusterArticles.stream().map(Article::getCountryCode).collect(Collectors.toSet());
                Set<String> continents = clusterArticles.stream().map(Article::getContinent).collect(Collectors.toSet());

                String synthesis = generateSynthesis(spec.topic(), clusterArticles, date);
                try { Thread.sleep(1200); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }

                ArticleCluster cluster = new ArticleCluster();
                cluster.setClusterDate(date);
                cluster.setTopic(spec.topic());
                cluster.setSynthesis(synthesis);
                cluster.setArticleCount(clusterArticles.size());
                cluster.setCountryCount(countries.size());
                cluster.setContinentCount(continents.size());
                cluster.setCountriesList(String.join(", ", new TreeSet<>(countries)));
                cluster.setContinentsList(String.join(", ", new TreeSet<>(continents)));
                cluster.setRelevanceScore(computeScore(countries.size(), continents.size(), clusterArticles.size()));
                cluster.setCreatedAt(LocalDateTime.now());
                cluster.setArticles(new ArrayList<>(clusterArticles));

                saved.add(clusterRepository.save(cluster));
                log.info("Cluster saved: [{}] {} countries — {}", spec.topic(), countries.size(), cluster.getCountriesList());
            }

            log.info("Daily analysis for {} complete — {} clusters saved", date, saved.size());
            return saved.size();

        } catch (Exception e) {
            log.error("Daily analysis failed for {}: {}", date, e.getMessage());
            return 0;
        }
    }

    private List<ClusterSpec> clusterArticles(List<Article> articles, LocalDate date) throws Exception {
        ArrayNode input = objectMapper.createArrayNode();
        for (Article a : articles) {
            ObjectNode item = input.addObject();
            item.put("id", a.getId());
            // Prefer translated title if available
            String title = a.getTranslatedTitle() != null ? a.getTranslatedTitle() : a.getOriginalTitle();
            item.put("title", title);
            item.put("country", a.getCountryCode());
        }

        String systemPrompt = """
                Tu analyses des titres d'articles d'actualité internationale.
                Regroupe-les par événement ou sujet (max %d clusters, min %d articles par cluster).
                Priorité aux événements couverts dans plusieurs pays différents.
                Ignore les articles très similaires du même pays (doublons).
                Réponds UNIQUEMENT en JSON, sans markdown ni explication:
                {"clusters":[{"topic":"Titre court (<70 chars)","article_ids":[1,2,3,...]},...]}"
                """.formatted(MAX_CLUSTERS, MIN_ARTICLES_PER_CLUSTER);

        String userMessage = "Articles du " + date + ":\n" + input;
        String response = mistralClient.analyze(systemPrompt, userMessage);
        String json = extractJsonObject(response);

        JsonNode root = objectMapper.readTree(json);
        List<ClusterSpec> specs = new ArrayList<>();
        for (JsonNode cluster : root.path("clusters")) {
            String topic = cluster.path("topic").asText("").trim();
            if (topic.isBlank()) continue;
            List<Long> ids = new ArrayList<>();
            for (JsonNode id : cluster.path("article_ids")) {
                ids.add(id.asLong());
            }
            if (!ids.isEmpty()) specs.add(new ClusterSpec(topic, ids));
        }
        return specs;
    }

    private String generateSynthesis(String topic, List<Article> articles, LocalDate date) {
        try {
            String countriesStr = articles.stream()
                    .map(Article::getCountryCode)
                    .distinct()
                    .sorted()
                    .collect(Collectors.joining(", "));

            ArrayNode input = objectMapper.createArrayNode();
            for (Article a : articles) {
                ObjectNode item = input.addObject();
                item.put("country", a.getCountryCode());
                String title = a.getTranslatedTitle() != null ? a.getTranslatedTitle() : a.getOriginalTitle();
                item.put("title", title);
                String summary = a.getTranslatedSummary() != null ? a.getTranslatedSummary()
                        : (a.getOriginalSummary() != null ? a.getOriginalSummary() : "");
                if (!summary.isBlank()) item.put("summary", summary);
            }

            String systemPrompt = """
                    Tu es un analyste géopolitique expert.
                    À partir des articles fournis, écris une synthèse de 4 à 6 phrases en %s qui:
                    - Décrit l'événement ou le sujet principal
                    - Met en lumière les différents angles selon les pays couvrants
                    - Apporte un contexte géopolitique si pertinent
                    - Identifie les tensions ou convergences notables
                    Réponds UNIQUEMENT avec la synthèse, sans titre ni préambule.
                    """.formatted(props.getTranslation().getTargetLanguage());

            String userMessage = "Sujet: " + topic + "\nDate: " + date
                    + "\nPays couvrants: " + countriesStr + "\n\nArticles:\n" + input;

            return mistralClient.analyze(systemPrompt, userMessage);

        } catch (Exception e) {
            log.warn("Synthesis failed for [{}]: {}", topic, e.getMessage());
            return null;
        }
    }

    private double computeScore(int countryCount, int continentCount, int articleCount) {
        // Weighted: multi-continent events rank highest
        return (countryCount * 3.0) + (continentCount * 5.0) + Math.min(articleCount, 10);
    }

    private String extractJsonObject(String text) {
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}') + 1;
        return (start >= 0 && end > start) ? text.substring(start, end) : text;
    }

    private record ClusterSpec(String topic, List<Long> articleIds) {}
}
