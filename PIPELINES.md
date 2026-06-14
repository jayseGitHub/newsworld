# NewsWorld — Pipelines de traitement

Les trois pipelines s'enchaînent dans l'ordre : **Enrich → Translate → Analyze**.
Chaque article passe successivement par les trois étapes avant d'apparaître dans les clusters du dashboard.

---

## Pipeline 1 — Enrich

**Classe** : `ContentEnrichmentService`
**Scheduler** : `EnrichmentScheduler` — toutes les 2h (`enrichment-cron`)
**Batch** : 50 articles (`enrichmentBatchSize`)
**Critère** : `content_fetched_at IS NULL`

Remplit `original_summary` sur les articles qui n'en ont pas.

- **Articles WordPress** (collectés via `WordPressApiCollectorService`) : ont déjà un `originalSummary` issu de l'excerpt de l'API WP. Marqués `contentFetchedAt = now` sans fetch HTTP.
- **Articles RSS** : fetch HTTP de la page source avec Jsoup. Cascade de 9 sélecteurs CSS (`article`, `[itemprop=articleBody]`, `.article-body`, `.entry-content`…). Premier résultat ≥ 80 chars retenu. Fallback : concat de tous les `<p>` ≥ 80 chars. Tronqué à 2000 chars.
- En cas d'erreur : `contentFetchedAt` est quand même positionné pour ne pas boucler.

---

## Pipeline 2 — Translate

**Classe** : `TranslationService`
**Scheduler** : `TranslationScheduler` — toutes les 6h (`translation-cron`)
**Batch** : 20 articles (`translationBatchSize`)
**Critère** : `content_fetched_at IS NOT NULL AND translated_at IS NULL`
**Modèle** : `mistral-small-latest` — température 0.1

Remplit `translated_title` + `translated_summary`.

- Articles déjà dans la langue cible (`fr`) : copie directe sans appel Mistral.
- Les autres : **un seul appel batch** Mistral avec un JSON array `[{"id":1,"title":"...","summary":"..."}]`. Le résumé est tronqué à 400 chars avant envoi pour réduire les erreurs de parsing JSON.
- Le system prompt impose un format JSON strict (pas de markdown, guillemets échappés).
- La réponse est extraite par `indexOf('[')` / `lastIndexOf(']')` pour tolérer les bavardages du modèle.
- **Retry automatique** : si le batch échoue, retry par sous-batches de 5. Si un sous-batch échoue aussi, les articles sont marqués `translatedAt = now` avec champs vides (skipped) pour débloquer la queue.

---

## Pipeline 3 — Analyze

**Classe** : `DailyAnalysisService`
**Scheduler** : `AnalysisScheduler` — 23h30 chaque soir (`analysis-cron`)
**Périmètre** : tous les articles collectés dans la journée (max 200)
**Modèle** : `mistral-medium-latest` — température 0.3
**Idempotent** : vérifie `existsByClusterDate(date)` avant de lancer.

Regroupe les articles du jour par sujet et génère une synthèse géopolitique par cluster.

### Phase 1 — Clustering

Un seul appel Mistral avec la liste des articles (id + titre traduit + code pays).
Le modèle retourne :
```json
{"clusters": [{"topic": "Titre court < 70 chars", "article_ids": [1, 2, 3]}]}
```
Contraintes : max 25 clusters, min 2 articles par cluster. Clusters à 1 article ignorés.

### Phase 2 — Synthèse (1 appel Mistral par cluster)

Pour chaque cluster : appel Mistral avec titres + résumés traduits de tous les articles membres.
Le modèle rédige **4-6 phrases** couvrant :
- Description de l'événement principal
- Angles selon les pays couvrants
- Contexte géopolitique si pertinent
- Tensions ou convergences notables

Pause de 1,2s entre chaque synthèse pour éviter le rate-limiting.

### Score de pertinence

```
score = (nbPays × 3) + (nbContinents × 5) + min(nbArticles, 10)
```

Les événements multi-continents arrivent en tête du feed dashboard.

---

## Déclenchement manuel

Via le dashboard Android (onglet Opérations) ou directement :

```
POST /api/pipelines/enrich/run
POST /api/pipelines/translate/run
POST /api/pipelines/analyze/run?date=2026-06-14
```
