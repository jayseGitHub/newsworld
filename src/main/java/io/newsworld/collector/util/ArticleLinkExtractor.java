package io.newsworld.collector.util;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Extracts article links from a news homepage regardless of DOM structure.
 *
 * Strategy (applied in order, stops at first success):
 *   1. Structural CSS selectors  — works on well-structured sites (article/h2/h3)
 *   2. Heuristic + URL pattern   — removes nav/footer noise, finds content area,
 *                                   scores links by text length and URL shape
 *   3. Heuristic relaxed         — same but without URL pattern requirement
 *
 * Shared between RssDoctorService (detection) and ScraperService (collection).
 */
public final class ArticleLinkExtractor {

    // --- Thresholds --------------------------------------------------------

    /** Minimum headline character count — filters out nav items ("Home", "Sports"). */
    private static final int MIN_TEXT = 20;

    /** Maximum headline character count — filters out paragraphs or long blurbs. */
    private static final int MAX_TEXT = 300;

    /** Minimum matching links to consider the page parseable. */
    private static final int MIN_LINKS = 3;

    // --- Selectors ---------------------------------------------------------

    private static final String NOISE_SELECTOR =
            "nav, header, footer, aside, " +
            "[class*=nav], [class*=menu], [class*=footer], [class*=sidebar], " +
            "[class*=cookie], [class*=banner], [class*=advertisement], [class*=promo], " +
            "[class*=subscribe], [class*=newsletter], [class*=social], [class*=share], " +
            "[id*=nav], [id*=menu], [id*=footer], [id*=sidebar], [id*=header], " +
            "script, style, noscript, [aria-hidden=true]";

    private static final List<String> CONTENT_AREA_SELECTORS = List.of(
            "main", "[role=main]",
            "#content", "#main", "#articles", "#news", "#posts",
            ".content", ".main-content", ".posts", ".articles",
            ".news-list", ".post-list", ".article-list",
            "[class*=feed]", "[class*=stream]"
    );

    private static final List<String> STRUCTURAL_SELECTORS = List.of(
            "article h1 a, article h2 a, article h3 a",
            ".headline a, [class*=headline] a",
            "[class*=article] h2 a, [class*=story] h2 a, [class*=news] h2 a, [class*=post] h2 a",
            "[class*=article] a[href], [class*=story] a[href]",
            "h2 a[href], h3 a[href]"
    );

    // --- URL pattern -------------------------------------------------------

    /**
     * Matches URLs that look like articles:
     * - contain a date year in the path  (/2024/, /2025/, /2026/)
     * - contain common article path segments  (/article/, /news/, /story/, /post/, ...)
     * - end with a long slug (likely a headline-derived URL)
     */
    private static final Pattern ARTICLE_URL_PATTERN = Pattern.compile(
            "/(20[12][0-9])/" +                                       // year in path
            "|/(article|news|story|post|actualite|actu|artikel|"  +
              "noticia|noticias|article-detail|artigo|yazı|haberi|" +
              "report|opinion|feature|analysis)/" +                   // common path segments
            "|/[a-zA-Z0-9][a-zA-Z0-9-]{19,}/?(?:\\?|$)"             // long slug (≥20 chars)
    );

    // -----------------------------------------------------------------------

    private ArticleLinkExtractor() {}

    /**
     * Returns a deduplicated list of article-like URLs found on the page.
     * Returns empty list if nothing is found (never null).
     */
    public static List<String> extractLinks(Document originalDoc, String mediaUrl) {
        // Step 1 — fast path: structural CSS selectors
        for (String selector : STRUCTURAL_SELECTORS) {
            List<String> found = collectUrls(originalDoc.select(selector), mediaUrl);
            if (found.size() >= MIN_LINKS) return found;
        }

        // Step 2 — heuristic: remove noise, narrow to content area
        Document doc = originalDoc.clone();
        doc.select(NOISE_SELECTOR).remove();
        Element area = findContentArea(doc);

        // Step 2a — heuristic with URL pattern requirement
        List<String> strict = filterLinks(area.select("a[href]"), mediaUrl, true);
        if (strict.size() >= MIN_LINKS) return strict;

        // Step 2b — heuristic without URL pattern (any internal link with headline text)
        List<String> relaxed = filterLinks(area.select("a[href]"), mediaUrl, false);
        return relaxed;
    }

    /** True if the page contains at least MIN_LINKS article-like links. */
    public static boolean hasLinks(Document doc, String mediaUrl) {
        return !extractLinks(doc, mediaUrl).isEmpty();
    }

    // -----------------------------------------------------------------------

    private static Element findContentArea(Document doc) {
        for (String sel : CONTENT_AREA_SELECTORS) {
            Element el = doc.selectFirst(sel);
            if (el != null) return el;
        }
        return doc.body() != null ? doc.body() : doc;
    }

    /** Collects href values from a set of anchor elements, deduped, same-domain only. */
    private static List<String> collectUrls(Elements anchors, String mediaUrl) {
        String domain = domain(mediaUrl);
        List<String> result = new ArrayList<>();
        for (Element a : anchors) {
            String url = a.absUrl("href");
            if (!url.isBlank() && url.contains(domain) && !url.contains("{") && !result.contains(url))
                result.add(url);
        }
        return result;
    }

    /**
     * Applies heuristic scoring to anchor elements.
     *
     * @param requireArticleUrl if true, the URL must also match ARTICLE_URL_PATTERN
     */
    private static List<String> filterLinks(Elements anchors, String mediaUrl, boolean requireArticleUrl) {
        String domain = domain(mediaUrl);
        List<String> result = new ArrayList<>();

        for (Element a : anchors) {
            String url = a.absUrl("href");
            String text = a.text().trim();

            if (url.isBlank() || !url.contains(domain) || url.contains("{")) continue; // external / template
            if (text.length() < MIN_TEXT || text.length() > MAX_TEXT) continue; // not a headline
            if (text.equals(text.toUpperCase()) && text.length() > 15) continue; // nav all-caps
            if (requireArticleUrl && !isArticleLike(url)) continue;
            if (result.contains(url)) continue;                              // dedup

            result.add(url);
        }
        return result;
    }

    private static boolean isArticleLike(String url) {
        if (ARTICLE_URL_PATTERN.matcher(url).find()) return true;
        try {
            String path = URI.create(url).getPath();
            String[] segments = path.replaceAll("/$", "").split("/");
            // 3+ path segments: /cat/subcat/slug
            if (segments.length >= 4) return true;
            // Last segment is a slug-like string (≥ 15 chars with at least one hyphen)
            String last = segments[segments.length - 1];
            return last.length() >= 15 && last.contains("-");
        } catch (Exception e) {
            return false;
        }
    }

    private static String domain(String url) {
        try { return URI.create(url).getHost(); }
        catch (Exception e) { return url; }
    }
}
