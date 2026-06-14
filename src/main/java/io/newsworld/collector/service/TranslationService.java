package io.newsworld.collector.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.newsworld.collector.model.Article;
import io.newsworld.collector.repository.ArticleRepository;
import io.newsworld.collector.util.MistralClient;
import io.newsworld.collector.config.NewsWorldProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Pipeline 2 — Translation.
 * Batch-translates originalTitle + originalSummary via Mistral.
 * Runs only on articles whose content has been fetched (contentFetchedAt IS NOT NULL).
 * Articles already in the target language get their fields copied directly.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TranslationService {

    private static final String SYSTEM_PROMPT = """
            Tu es un traducteur professionnel spécialisé en actualité internationale.
            Traduis les titres et résumés fournis vers la langue cible indiquée.
            Préserve les noms propres, acronymes et termes techniques sans les traduire.
            RÈGLES STRICTES:
            - Réponds UNIQUEMENT avec un JSON array valide, sans markdown, sans ```json, sans explication
            - Utilise des guillemets doubles pour toutes les clés et valeurs string
            - Échappe les guillemets doubles internes avec \\\"
            - Si un résumé est null, mets null (sans guillemets) dans la réponse
            - Ne troncature pas, ne modifie pas les ids
            Format exact: [{"id":1,"title":"...","summary":"...ou null"}]
            """;

    private final ArticleRepository articleRepository;
    private final MistralClient mistralClient;
    private final ObjectMapper objectMapper;
    private final NewsWorldProperties props;

    public int translateBatch() {
        LocalDateTime now = LocalDateTime.now();
        List<Article> articles = articleRepository.findUntranslated(now, PageRequest.of(0, props.getPipelines().getTranslationBatchSize()));

        if (articles.isEmpty()) {
            log.debug("Translation: nothing to process");
            return 0;
        }

        // Articles already in target language: copy fields directly
        List<Article> toTranslate = articles.stream()
                .filter(a -> !props.getTranslation().getTargetLanguage().equalsIgnoreCase(a.getOriginalLanguage()))
                .toList();

        int count = 0;
        for (Article a : articles) {
            if (props.getTranslation().getTargetLanguage().equalsIgnoreCase(a.getOriginalLanguage())) {
                a.setTranslatedTitle(a.getOriginalTitle());
                a.setTranslatedSummary(a.getOriginalSummary());
                a.setTranslatedAt(now);
                articleRepository.save(a);
                count++;
            }
        }

        if (toTranslate.isEmpty()) return count;

        try {
            count += callMistral(toTranslate, now);
        } catch (Exception e) {
            log.warn("Batch of {} failed ({}), retrying in sub-batches of 5", toTranslate.size(), e.getMessage());
            count += retryInSubBatches(toTranslate, now);
        }

        return count;
    }

    private int callMistral(List<Article> articles, LocalDateTime now) throws Exception {
        ArrayNode input = objectMapper.createArrayNode();
        for (Article a : articles) {
            ObjectNode item = input.addObject();
            item.put("id", a.getId());
            item.put("title", sanitize(a.getOriginalTitle()));
            if (a.getOriginalSummary() != null && !a.getOriginalSummary().isBlank()) {
                // Truncate summaries to 400 chars before sending — reduces problematic chars
                item.put("summary", sanitize(StringUtils.truncate(a.getOriginalSummary(), 400)));
            } else {
                item.putNull("summary");
            }
        }

        String systemPrompt = SYSTEM_PROMPT + "\nLangue cible: " + props.getTranslation().getTargetLanguage();
        String response = mistralClient.translate(systemPrompt, input.toString());
        String json = extractJsonArray(response);
        JsonNode translated = objectMapper.readTree(json);
        Map<Long, Article> byId = articles.stream().collect(Collectors.toMap(Article::getId, a -> a));

        int count = 0;
        for (JsonNode item : translated) {
            long id = item.path("id").asLong(-1);
            Article article = byId.get(id);
            if (article == null) continue;

            String title = item.path("title").asText(null);
            if (title != null && !title.isBlank()) {
                article.setTranslatedTitle(StringUtils.truncate(title, 512));
            }
            JsonNode summaryNode = item.path("summary");
            if (!summaryNode.isNull() && summaryNode.asText(null) != null) {
                article.setTranslatedSummary(summaryNode.asText());
            }
            article.setTranslatedAt(now);
            articleRepository.save(article);
            count++;
        }

        log.info("Translation batch: {}/{} articles translated to {}", count, articles.size(), props.getTranslation().getTargetLanguage());
        return count;
    }

    private int retryInSubBatches(List<Article> articles, LocalDateTime now) {
        int count = 0;
        int subSize = 5;
        for (int i = 0; i < articles.size(); i += subSize) {
            List<Article> sub = articles.subList(i, Math.min(i + subSize, articles.size()));
            try {
                count += callMistral(sub, now);
            } catch (Exception e) {
                log.error("Sub-batch of {} also failed ({}), marking as skipped", sub.size(), e.getMessage());
                for (Article a : sub) {
                    a.setTranslatedAt(now);
                    articleRepository.save(a);
                }
            }
        }
        return count;
    }

    /** Strips newlines, tabs and control chars that could break Mistral's JSON output. */
    private String sanitize(String text) {
        if (text == null) return "";
        return text.replaceAll("[\\r\\n\\t]", " ")
                   .replaceAll("[\\x00-\\x1F]", "")
                   .trim();
    }

    private String extractJsonArray(String text) {
        int start = text.indexOf('[');
        int end = text.lastIndexOf(']') + 1;
        return (start >= 0 && end > start) ? text.substring(start, end) : text;
    }
}
