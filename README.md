# NewsWorld Collector

Agrégateur d'actualité mondiale — collecte, enrichit, traduit et analyse les articles de 193 pays via RSS, sitemaps, WordPress REST et API JSON.

## Stack

| Composant | Technologie |
|-----------|-------------|
| Runtime | Java 21 / Spring Boot 3.5.13 |
| Base de données (prod) | PostgreSQL 42.7 |
| Base de données (dev/CLI) | H2 (fichier `data/newsworld-probe.mv.db`) |
| Migrations | Liquibase XML |
| Parsing RSS | ROME 2.1 |
| Scraping HTML | Jsoup 1.21 |
| LLM | Mistral AI (`mistral-small-latest`, `mistral-medium-latest`) |
| API REST | Spring MVC — port 8090, context `/api` |

## Prérequis

- Java 21+, Maven 3.9+
- PostgreSQL (prod uniquement)
- Clé API Mistral (`newsworld.mistral.api-key` dans `application.yml`)

## Lancement

### Prod (daemon + schedulers automatiques)

```bash
# Copier et remplir les variables
cp src/main/resources/application.yml.example src/main/resources/application.yml

mvn spring-boot:run
```

### CLI — pipelines one-shot

```bash
# 1. Collecte tous les 193 pays
mvn spring-boot:run -Dspring-boot.run.profiles=probe

# 2. Fetch les pages et extrait les résumés
mvn spring-boot:run -Dspring-boot.run.profiles=enrich

# 3. Traduit via Mistral (cible : fr par défaut)
mvn spring-boot:run -Dspring-boot.run.profiles=translate

# 4. Analyse du jour — clusters géopolitiques
mvn spring-boot:run -Dspring-boot.run.profiles=analyze

# 4b. Analyse d'une date spécifique
mvn spring-boot:run -Dspring-boot.run.profiles=analyze -Dspring-boot.run.arguments=2026-06-12
```

Les profils CLI utilisent H2 (`data/newsworld-probe.mv.db`) — aucune connexion PostgreSQL requise.

## Types de collecte

| Type | Description |
|------|-------------|
| `RSS` | Flux Atom/RSS via ROME |
| `NEWS_SITEMAP` | `<news:news>` Google News Sitemap |
| `WORDPRESS_REST` | Endpoint `/wp-json/wp/v2/posts` |
| `JSON_API` | API JSON propriétaire |
| `SCRAPER` | Extraction HTML par sélecteurs CSS |

## Pipelines post-collecte

```
probe → enrich → translate → analyze
```

Voir [WORKFLOW.md](WORKFLOW.md) pour le détail complet (mode one-shot et schedulers automatiques).

## Configuration clé

```yaml
newsworld:
  mistral:
    api-key: <votre-clé>
    model-translation: mistral-small-latest
    model-analysis: mistral-medium-latest
  translation:
    target-language: fr          # langue cible des traductions
  collector:
    bypass-time-check: false     # true = collecte immédiate sans vérifier l'heure locale
    purge-days: 30               # articles supprimés après N jours
  pipelines:
    enrichment-batch-size: 50
    translation-batch-size: 20
```

## Endpoints actuator

```
GET /api/actuator/health
GET /api/actuator/metrics
GET /api/actuator/scheduledtasks
```
