# NewsWorld — Workflow de collecte et d'analyse

## Mode manuel one-shot

```
┌─────────┐    ┌─────────┐    ┌───────────┐    ┌─────────┐
│  probe  │───►│  enrich │───►│ translate │───►│ analyze │
└─────────┘    └─────────┘    └───────────┘    └─────────┘
  ~8 min         ~2-3h           ~45 min        ~5-10 min
```

### Étapes

**1 — probe** : collecte les 193 pays en un seul run. Remplit `articles.original_title` (+ `original_summary` si le flux le fournit — RSS `<description>`, WP excerpt). S'arrête et quitte.

**2 — enrich** : pour chaque article sans `content_fetched_at`, fetch la page HTML et extrait le corps de l'article → `original_summary`. Les pages qui retournent 403/timeout sont quand même marquées (`content_fetched_at = now`, `original_summary = null`) pour ne pas réessayer. Débloque le pipeline translate.

**3 — translate** : prend tous les articles avec `content_fetched_at IS NOT NULL` et `translated_at IS NULL`, les envoie à `mistral-small-latest` par batches de 20 → `translated_title` + `translated_summary`. Boucle jusqu'à épuisement.

**4 — analyze** : prend les articles du jour, demande à `mistral-medium-latest` de les regrouper par événement, génère une synthèse géopolitique par cluster, scoré par pays/continent. Résultat dans `article_clusters`.

### Séquence complète

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=probe
mvn spring-boot:run -Dspring-boot.run.profiles=enrich
mvn spring-boot:run -Dspring-boot.run.profiles=translate
mvn spring-boot:run -Dspring-boot.run.profiles=analyze

# Optionnel : date spécifique
mvn spring-boot:run -Dspring-boot.run.profiles=analyze -Dspring-boot.run.arguments=2026-06-13
```

---

## Mode schedulé (production automatique)

En dev ou prod (Spring Boot lancé en daemon), 4 schedulers tournent sans aucune action manuelle :

```
Temps →  0h    2h    4h    6h    8h   10h   12h   ...  23h30
         │     │     │     │     │     │     │          │
Collect  ●──●──●──●──●──●──●──●──●──●──●──●──●─── (toutes les 15min)
Enrich         ●           ●           ●           (toutes les 2h)
Translate         ●                 ●              (toutes les 6h à H+30)
Analyze                                       ●    (23h30)
```

### Découpage des pays par jour et fuseau horaire

Les 193 pays sont répartis statiquement sur 7 jours de la semaine via la colonne `week_day` (1 = lundi … 7 = dimanche) en base. Chaque jour, le `CollectionScheduler` ne charge que les pays dont `week_day` correspond au jour courant (~27 pays/jour).

Pour chaque pays sélectionné, la méthode `isDue()` vérifie que l'heure locale du pays (via son `iana_timezone`) est dans la fenêtre **7h00 – 7h15**. Le scheduler tourne toutes les 15 min côté serveur, mais chaque pays n'est collecté qu'une fois par jour, à 7h du matin dans son propre fuseau.

```
Lundi     → ~27 pays  (week_day = 1)   ex: FR 07:00 Europe/Paris
Mardi     → ~27 pays  (week_day = 2)   ex: JP 07:00 Asia/Tokyo
...
Dimanche  → ~28 pays  (week_day = 7)
```

> En probe (mode one-shot), ce découpage est ignoré : `bypass-time-check=true` et tous les pays sont traités dans le même run.

| Scheduler            | Cron             | Ce qu'il fait                                                          |
|----------------------|------------------|------------------------------------------------------------------------|
| CollectionScheduler  | `0 */15 * * * *` | Collecte les pays du jour (1/7 des 193 à chaque run, ~27 pays)         |
| EnrichmentScheduler  | `0 0 */2 * * *`  | Batch de 50 pages — fetch et extrait les résumés des nouveaux articles |
| TranslationScheduler | `0 30 */6 * * *` | Batch de 20 articles — traduit ce qui est enrichi depuis le dernier run |
| AnalysisScheduler    | `0 30 23 * * *`  | Analyse et synthèse de toute la journée, génère les clusters           |

### Flux de données automatique

```
CollectionScheduler
  └─► Article { original_title, original_summary? }
         │ (content_fetched_at IS NULL)
         ▼ (2h plus tard)
EnrichmentScheduler
  └─► Article { original_summary rempli, content_fetched_at = now }
         │ (translated_at IS NULL)
         ▼ (6h plus tard)
TranslationScheduler
  └─► Article { translated_title, translated_summary, translated_at = now }
         │
         ▼ (23h30)
AnalysisScheduler
  └─► ArticleCluster { topic, synthesis, relevance_score, countries }
```

### Garanties

| Propriété | Mécanisme |
|-----------|-----------|
| **Idempotent** | Chaque pipeline ne traite que ce qui n'a pas encore été traité (flags `IS NULL`) |
| **Résilient** | Un article qui plante en enrichissement est quand même marqué → ne bloque pas la traduction |
| **Sans doublon** | `existsBySourceUrl()` à la collecte, `translatedAt`/`contentFetchedAt` aux pipelines |
| **Analyse unique** | `existsByClusterDate()` → une seule analyse par jour, relancer ne recrée pas |

### Latence de bout en bout en production

> Article collecté à **8h** → enrichi à **10h** → traduit à **12h30** → analysé à **23h30**
>
> Délai max : **~15h30** de la collecte à la synthèse finale
