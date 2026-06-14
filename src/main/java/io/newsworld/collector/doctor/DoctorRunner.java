package io.newsworld.collector.doctor;

import io.newsworld.collector.model.Country;
import io.newsworld.collector.repository.CountryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Profile("doctor")
@Component
@RequiredArgsConstructor
@Slf4j
public class DoctorRunner implements ApplicationRunner {

    private final CountryRepository countryRepository;
    private final RssDoctorService doctorService;

    private static final String CSV_HEADER =
            "code,name,continent,population,week_day,media_name,media_url,rss_url,rss_available,iana_timezone,language";

    @Override
    public void run(ApplicationArguments args) throws Exception {
        List<Country> countries = countryRepository.findAll();
        log.info("=== DOCTOR START — {} countries ===", countries.size());

        List<DoctorResult> results = new ArrayList<>();
        Map<DoctorResult.Verdict, List<DoctorResult>> byVerdict = new EnumMap<>(DoctorResult.Verdict.class);
        for (DoctorResult.Verdict v : DoctorResult.Verdict.values()) byVerdict.put(v, new ArrayList<>());

        for (Country country : countries) {
            log.info("Checking {} ({})...", country.getCode(), country.getName());
            DoctorResult result = doctorService.diagnose(country);
            results.add(result);
            byVerdict.get(result.verdict()).add(result);

            String icon = switch (result.verdict()) {
                case RSS_OK       -> "✓";
                case RSS_FIXED    -> "✎";
                case API_FOUND    -> "⚙";
                case SCRAPER_ONLY -> "~";
                case BROKEN       -> "✗";
            };
            log.info("{} [{}] {} — {} | {}",
                    icon, result.verdict(), country.getCode(),
                    result.discoveryMethod() != null ? result.discoveryMethod() : "—",
                    result.detail());
        }

        writeCsvFixes(results);
        writeReport(results, byVerdict);
        printSummary(byVerdict);

        System.exit(0);
    }

    /** Génère data/countries-fixed.csv avec les URLs RSS corrigées. */
    private void writeCsvFixes(List<DoctorResult> results) throws IOException {
        long fixCount = results.stream().filter(DoctorResult::needsCsvUpdate).count();
        if (fixCount == 0) {
            log.info("Aucune correction RSS à appliquer au CSV.");
            return;
        }

        Path out = Path.of("data/countries-fixed.csv");
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(out))) {
            pw.println(CSV_HEADER);
            for (DoctorResult r : results) {
                Country c = r.country();
                String rssUrl = r.needsCsvUpdate() ? r.fixedRssUrl() : (c.getRssUrl() != null ? c.getRssUrl() : "");
                boolean rssAvailable = r.verdict() == DoctorResult.Verdict.RSS_OK
                        || r.verdict() == DoctorResult.Verdict.RSS_FIXED;
                pw.printf("%s,%s,%s,%d,%d,%s,%s,%s,%b,%s,%s%n",
                        c.getCode(), c.getName(), c.getContinent(),
                        c.getPopulation(), c.getWeekDay(),
                        c.getMediaName(), c.getMediaUrl(),
                        rssUrl, rssAvailable,
                        c.getIanaTimezone(), c.getLanguage());
            }
        }
        log.info("CSV corrigé écrit : {} ({} corrections)", out.toAbsolutePath(), fixCount);
    }

    /** Génère data/doctor-report.txt avec le détail complet. */
    private void writeReport(List<DoctorResult> results, Map<DoctorResult.Verdict, List<DoctorResult>> byVerdict) throws IOException {
        Path out = Path.of("data/doctor-report.txt");
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(out))) {
            pw.println("=== NEWSWORLD DOCTOR REPORT ===");
            pw.println();

            pw.println("--- RSS_FIXED : nouvelles URLs trouvées ---");
            byVerdict.get(DoctorResult.Verdict.RSS_FIXED).forEach(r ->
                pw.printf("  %-3s %-30s\n      Ancien : %s\n      Nouveau : %s  [%s]\n\n",
                        r.country().getCode(), r.country().getName(),
                        r.originalRssUrl(), r.fixedRssUrl(), r.discoveryMethod()));

            pw.println("--- API_FOUND : endpoint alternatif découvert ---");
            byVerdict.get(DoctorResult.Verdict.API_FOUND).forEach(r ->
                pw.printf("  %-3s %-30s  [%s] %s  (%s)\n",
                        r.country().getCode(), r.country().getName(),
                        r.discoveryMethod(), r.fixedRssUrl(), r.detail()));
            if (byVerdict.get(DoctorResult.Verdict.API_FOUND).isEmpty()) pw.println("  (aucun)");
            pw.println();

            pw.println("--- BROKEN : intervention manuelle requise ---");
            byVerdict.get(DoctorResult.Verdict.BROKEN).forEach(r ->
                pw.printf("  %-3s %-30s  media: %s\n",
                        r.country().getCode(), r.country().getName(), r.country().getMediaUrl()));

            pw.println();
            pw.println("--- SCRAPER_ONLY : pas de RSS, homepage parseable ---");
            byVerdict.get(DoctorResult.Verdict.SCRAPER_ONLY).forEach(r ->
                pw.printf("  %-3s %-30s\n", r.country().getCode(), r.country().getName()));
        }
        log.info("Rapport complet : {}", out.toAbsolutePath());
    }

    private void printSummary(Map<DoctorResult.Verdict, List<DoctorResult>> byVerdict) {
        int fixed  = byVerdict.get(DoctorResult.Verdict.RSS_FIXED).size();
        int ok     = byVerdict.get(DoctorResult.Verdict.RSS_OK).size();
        int api    = byVerdict.get(DoctorResult.Verdict.API_FOUND).size();
        int scraper= byVerdict.get(DoctorResult.Verdict.SCRAPER_ONLY).size();
        int broken = byVerdict.get(DoctorResult.Verdict.BROKEN).size();

        log.info("");
        log.info("╔═══════════════════════════════════════════╗");
        log.info("║           DOCTOR SUMMARY                  ║");
        log.info("╠═══════════════════════════════════════════╣");
        log.info("║  ✓ RSS_OK        : {}",        pad(ok));
        log.info("║  ✎ RSS_FIXED     : {}  ← CSV à remplacer", pad(fixed));
        log.info("║  ⚙ API_FOUND     : {}  ← adapter à coder", pad(api));
        log.info("║  ~ SCRAPER_ONLY  : {}",        pad(scraper));
        log.info("║  ✗ BROKEN        : {}  ← browser headless", pad(broken));
        log.info("╠═══════════════════════════════════════════╣");
        log.info("║  Fichiers générés :                       ║");
        log.info("║    data/countries-fixed.csv               ║");
        log.info("║    data/doctor-report.txt                 ║");
        log.info("╚═══════════════════════════════════════════╝");
    }

    private String pad(int n) {
        return String.format("%-38s║", n);
    }
}
