# NewsWorld — Analyse architecturale

## Contexte

Application de veille mondiale automatisée : chaque jour de la semaine couvre une tranche de pays par continent, triée par population décroissante. L'objectif est de consulter chaque matin les dernières actualités de ~40 pays simultanément, de détecter les recoupements cross-nationaux, et de conserver un historique de 30 jours glissants traduit dans la langue du téléphone.

---

## Nouvelle app ou AndroidAgent ?

**Recommandation : nouvelle application dédiée**, mais qui réutilise l'infrastructure LLM d'AndroidAgent (providers, mémoire, sessions). Les besoins UX sont fondamentalement différents — un lecteur de news mondial n'est pas un agent CLI.

---

## Architecture globale

### 1. Le découpage des 7 jours

Chaque continent est divisé en 7 tranches de taille égale, triées par population décroissante. Le lundi correspond à la tranche 1 de chaque continent en même temps.

```
Lundi    → Asie [1-7]   + Afrique [1-7]   + Europe [1-6]   + ...
Mardi    → Asie [8-14]  + Afrique [8-14]  + Europe [7-12]  + ...
...
Dimanche → Asie [43-49] + Afrique [46-52] + Europe [39-44] + ...
```

Chaque jour couvre donc ~30 à 40 pays simultanément, répartis sur les 6 continents.

---

### 2. Collecte des articles — RSS en priorité, scraping en fallback

La grande majorité des médias de la liste ont un flux RSS. C'est le canal propre, respectueux, sans parsing fragile.

```
Source  →  RSS (priorité 1)
        →  Scraping HTML ciblé (fallback pour sites sans RSS)
        →  API officielle si disponible (Al Jazeera, NPR...)
```

Le scheduling est piloté par le **fuseau horaire de chaque média**, pas par l'heure du téléphone. Un journal japonais est consulté à 7h JST, un brésilien à 7h BRT. L'app tourne donc en arrière-plan toute la journée, ce qui est naturel sur Android via **WorkManager**.

---

### 3. Stockage — SQLite + FTS5 sur 30 jours glissants

```sql
Table articles
  id, pays, continent, jour_semaine, source_url,
  titre_original, langue_originale,
  titre_traduit, resume_traduit,
  corps_original, corps_traduit,
  date_publication, date_collecte,
  topics[]  -- entités extraites : noms, lieux, événements

Table correlations
  article_id_a, article_id_b, score, topic_commun

Table pays
  -- la liste complète avec jour_semaine calculé
```

Purge automatique des articles de plus de 30 jours. FTS5 pour la recherche plein texte sur titres et résumés traduits.

---

### 4. Traduction

| Option | Avantage | Inconvénient |
|--------|----------|--------------|
| **ML Kit (Google, on-device)** | Gratuit, hors-ligne, rapide | ~50 langues, qualité inégale |
| **LLM via AndroidAgent providers** | Excellent, contextuel | Coût API, lenteur sur 30 pays/jour |
| **DeepL API** | Meilleure qualité | Payant au volume |

**Approche recommandée** : ML Kit pour le titre + résumé (le plus consulté), LLM uniquement si l'utilisateur demande l'article complet. On ne traduit que ce qui est affiché.

---

### 5. Recoupement cross-pays

C'est la fonctionnalité la plus puissante et la plus complexe. Deux niveaux :

**Niveau 1 — Entités communes (rapide, local)**
Extraire les entités nommées de chaque article (noms de personnes, pays, organisations, événements) via un modèle léger embarqué. Deux articles partageant 3+ entités → corrélation candidate.

**Niveau 2 — Sémantique (LLM, à la demande)**
Quand l'utilisateur ouvre un article, le LLM cherche dans la base les articles de la semaine traitant du même sujet, même sans entités communes exactes.

Le résultat s'affiche comme : *"Ce sujet a été couvert par 4 autres pays cette semaine : Nigeria, Égypte, Brésil, Inde"*

---

### 6. Présentation — les 4 vues essentielles

**Vue Aujourd'hui** *(écran d'accueil)*
Carte du monde ou liste de continents. Pour chaque continent : les pays du jour avec le nombre d'articles et les 2-3 titres les plus importants. Un badge rouge si le pays apparaît dans une corrélation cross-nationale.

**Vue Pays**
Tous les articles du pays sur les 30 jours. Timeline verticale. Filtre par topic.

**Vue Corrélations** *(la plus originale)*
Un fil de "stories mondiales" — des sujets qui traversent plusieurs pays simultanément. Ex : *"Crise du blé — 12 pays, 34 articles cette semaine"*. Trié par nombre de pays touchés.

**Vue Semaine**
Un calendrier horizontal lundi→dimanche. Chaque jour = les pays couverts. Permet de naviguer en arrière sur les 4 semaines glissantes.

---

### 7. Ce qui est difficile à bien faire

| Défi | Pourquoi c'est dur |
|------|--------------------|
| **Sites sans RSS** | Le HTML change, le scraping casse silencieusement |
| **Paywalls** | ~30% des médias de qualité en ont un (NZZ, Le Monde, NYT…) |
| **Volume** | ~40 pays/jour × 10 articles = 400 articles/jour à traiter |
| **Qualité de traduction** | Les médias en arabe, mandarin, hindi ont des nuances culturelles que ML Kit rate |
| **Fuseaux horaires** | 193 pays = ~38 fuseaux distincts, WorkManager doit être précis |
| **Corrélation pertinente** | Éviter le bruit (2 articles qui mentionnent "ONU" ne sont pas forcément liés) |

---

## Résumé de la recommandation

```
Nouvelle app Android (Java 21, comme AndroidAgent)
├── WorkManager        → collecte planifiée par timezone
├── SQLite + FTS5      → stockage 30 jours + recherche
├── ML Kit             → traduction on-device des titres/résumés
├── RSS parser         → collecte principale
├── LLM (providers AndroidAgent) → corrélation sémantique + résumé complet
└── 4 vues             → Aujourd'hui / Pays / Corrélations / Semaine
```

---

## Prochaines étapes suggérées

1. **Modèle de données** — affiner le schéma SQLite, définir les index FTS5
2. **Structure des écrans** — wireframes des 4 vues principales
3. **Catalogue RSS** — recenser les flux RSS des 193 médias de `pays_du_monde.md`
4. **Mapping timezone** — associer chaque pays à son fuseau principal
5. **Prototype collecte** — un Worker Android qui consomme 5 flux RSS et stocke en SQLite

---

## Logique de collecte par fuseau horaire

### Principe fondamental : la collecte est country-centric, pas phone-centric

Le fuseau horaire du téléphone **n'influence pas** le moment où un pays est collecté. Que l'utilisateur soit à Montréal, Tokyo ou Paris, le journal japonais Asahi Shimbun est toujours collecté à 7h JST (UTC+9), ce qui correspond à une heure UTC absolue fixe. WorkManager calcule un délai en millisecondes depuis maintenant jusqu'à ce moment UTC — ce calcul est indépendant du fuseau du téléphone.

**Le fuseau du téléphone n'influence que deux choses :**
1. **L'affichage des heures** à l'utilisateur (on lui montre "collecté à 23h" s'il est à Paris, "collecté à 7h" s'il est à Tokyo)
2. **La frontière de la journée** pour la rotation hebdomadaire (quand commence "lundi" pour cet utilisateur)

---

### Deux niveaux de scheduling

**Niveau 1 — Rotation hebdomadaire (dépend du fuseau téléphone)**
Chaque jour local du téléphone détermine quels pays sont surveillés. Lundi local = top 7 de chaque continent par population, mardi local = tranche 2, etc. Soit ~30 pays actifs par jour local. Ce niveau utilise `ZoneId.systemDefault()` pour savoir quel jour il est pour l'utilisateur.

**Niveau 2 — Scheduling intra-journalier (indépendant du fuseau téléphone)**
Pour chaque pays actif du jour, on calcule en UTC absolu quand il sera 7h00 dans ce pays. WorkManager est déclenché à cet instant précis, quelle que soit la position du téléphone.

---

### Les 6 créneaux de collecte (exprimés en UTC absolu)

Le tableau ci-dessous montre la distribution naturelle des 193 pays en 6 groupes selon leur heure locale de 7h. Ces créneaux sont des **références fixes UTC**, pas des heures locales du téléphone.

| Heure UTC du déclenchement | Fuseaux ciblés | Heure locale dans ces pays | Exemple à Montréal (UTC-5) | Exemple à Paris (UTC+1) | Exemple à Tokyo (UTC+9) |
|----------------------------|----------------|---------------------------|---------------------------|------------------------|------------------------|
| **07h UTC** | UTC-1, UTC+0, UTC+1, UTC+2 | 6h à 9h | 2h du matin | 8h matin | 16h après-midi |
| **11h UTC** | UTC-5, UTC-4, UTC-3, UTC-2 | 6h à 9h | 6h matin | 12h midi | 20h soir |
| **15h UTC** | UTC-9, UTC-8, UTC-7, UTC-6 | 6h à 9h | 10h matin | 16h après-midi | minuit |
| **19h UTC** | UTC-12, UTC-11, UTC-10, UTC+12, UTC+13 | 7h à 9h | 14h après-midi | 20h soir | 4h du matin |
| **23h UTC** | UTC+7, UTC+8, UTC+9, UTC+10, UTC+11 | 6h à 9h | 18h soir | minuit | 8h matin |
| **03h UTC** | UTC+3, UTC+4, UTC+5, UTC+6 | 6h à 9h | 22h soir | 4h du matin | 12h midi |

> Ces heures UTC sont **constantes** : elles ne bougent pas quand l'utilisateur change de pays. Seule la colonne d'affichage change selon où il se trouve.

---

### Mapping pays → créneau de collecte

#### Créneau 2h Montréal — Europe de l'Ouest, Afrique de l'Ouest et centrale, Afrique australe

**UTC-1**
Cabo Verde

**UTC+0**
Royaume-Uni, Irlande, Portugal, Islande
Ghana, Sénégal, Gambie, Sierra Leone, Guinée, Guinée-Bissau, Côte d'Ivoire, Burkina Faso, Mali, Togo, Bénin, Mauritanie, São Tomé-et-Príncipe

**UTC+1**
France, Allemagne, Italie, Espagne, Pays-Bas, Belgique, Suisse, Autriche, Suède, Norvège, Danemark, Pologne, République tchèque, Slovaquie, Hongrie, Serbie, Croatie, Bosnie-Herzégovine, Monténégro, Macédoine du Nord, Albanie, Luxembourg, Malte, Andorre, Monaco, Liechtenstein, Saint-Marin, Vatican
Nigeria, Niger, Tchad, Cameroun, Algérie, Tunisie, Congo (Rép.), Gabon, Guinée équatoriale, Angola, Namibie, Sahara occidental

**UTC+2**
Finlande, Estonie, Lettonie, Lituanie, Roumanie, Bulgarie, Grèce, Ukraine, Moldavie
Israël, Liban, Chypre, Palestine
Égypte, Libye, Afrique du Sud, Mozambique, Zambie, Malawi, Zimbabwe, Botswana, Rwanda, Burundi, Lesotho, Eswatini, Soudan

---

#### Créneau 6h Montréal — Amériques (Sud et Est)

**UTC-5**
États-Unis *(côte Est)*, Canada *(Est)*, Colombie, Équateur, Pérou, Panama, Haïti, Jamaïque, Bahamas

**UTC-4**
Venezuela, Bolivie, Chili *(été)*, Guyana
République dominicaine, Trinité-et-Tobago, Barbade, Sainte-Lucie, Grenade, Saint-Vincent-et-les-Grenadines, Antigua-et-Barbuda, Dominique, Saint-Kitts-et-Nevis

**UTC-3**
Brésil *(Brasilia/São Paulo)*, Argentine, Uruguay, Suriname

**UTC-2**
*(Aucun pays souverain de notre liste)*

---

#### Créneau 10h Montréal — Amériques (Centre et Pacifique Nord)

**UTC-6**
Mexique *(centre)*, Guatemala, Belize, Honduras, El Salvador, Nicaragua, Costa Rica

**UTC-7**
Mexique *(nord-ouest)*, États-Unis *(Montagnes)*

**UTC-8**
États-Unis *(Pacifique)*, Canada *(Colombie-Britannique)*

**UTC-9**
*(Aucun pays souverain de notre liste — Alaska US uniquement)*

---

#### Créneau 14h Montréal — Pacifique Sud (wrap ligne de date)

**UTC-10**
*(Polynésie française — non listée dans nos 193 pays)*

**UTC-11**
*(Aucun pays souverain de notre liste)*

**UTC+12**
Nouvelle-Zélande, Fidji, Îles Marshall, Tuvalu, Nauru, Kiribati *(partie)*

**UTC+13**
Samoa, Tonga

> Ces pays ont 7h–9h local quand Montréal est à 14h. Exemple : Fidji (UTC+12) = 19h UTC + 12h = 31h = **7h00** ✓

---

#### Créneau 18h Montréal — Asie du Sud-Est, Asie de l'Est, Pacifique Nord

**UTC+7**
Thaïlande, Laos, Cambodge, Viêt Nam, Indonésie *(Ouest/Jakarta)*

**UTC+8**
Chine, Singapour, Malaisie, Philippines, Brunei, Taïwan, Mongolie

**UTC+9**
Japon, Corée du Sud, Corée du Nord, Timor-Leste, Palaos

**UTC+10**
Papouasie-Nouvelle-Guinée, Australie *(Est/Sydney)*, Micronésie *(partie)*

**UTC+11**
Îles Salomon, Vanuatu

---

#### Créneau 22h Montréal — Moyen-Orient, Afrique de l'Est, Asie centrale et du Sud

**UTC+3**
Russie *(Moscou)*, Turquie, Arabie Saoudite, Irak, Koweït, Bahreïn, Qatar, Yémen, Syrie, Jordanie
Éthiopie, Kenya, Ouganda, Somalie, Tanzanie, Madagascar, Djibouti, Comores, Érythrée, Soudan du Sud
Biélorussie *(officielement UTC+3)*

**UTC+3:30**
Iran

**UTC+4**
Émirats arabes unis, Oman, Azerbaïdjan, Arménie, Géorgie
Maurice, Seychelles

**UTC+4:30**
Afghanistan

**UTC+5**
Pakistan, Ouzbékistan, Tadjikistan, Turkménistan, Maldives, Kazakhstan *(Ouest)*

**UTC+5:30**
Inde, Sri Lanka

**UTC+5:45**
Népal

**UTC+6**
Bangladesh, Bhoutan, Kirghizistan, Kazakhstan *(Est)*

**UTC+6:30**
Myanmar

---

### Cas particuliers

#### Fuseaux non entiers
Iran (UTC+3:30), Afghanistan (UTC+4:30), Inde/Sri Lanka (UTC+5:30), Népal (UTC+5:45), Myanmar (UTC+6:30) ne sont pas des fuseaux entiers. Ce n'est pas un problème : le calcul utilise l'identifiant IANA complet (`"Asia/Kolkata"`, `"Asia/Kathmandu"`, etc.) qui gère nativement les demi-heures et quarts d'heure. La cible reste 7h00 précises dans ce fuseau, sans arrondi.

#### Pays couvrant plusieurs fuseaux
On utilise l'identifiant IANA de la ville où est basé le média sélectionné :

| Pays | Fuseaux réels | Identifiant IANA retenu | Raison |
|------|--------------|------------------------|--------|
| Russie | UTC+2 à UTC+12 | `Europe/Moscow` | Meduza basé à Moscou |
| États-Unis | UTC-5 à UTC-10 | `America/New_York` | NPR basé à Washington D.C. |
| Brésil | UTC-3 à UTC-5 | `America/Sao_Paulo` | Agência Pública basée à São Paulo |
| Australie | UTC+8 à UTC+11 | `Australia/Sydney` | Guardian Australia basé à Sydney |
| Indonésie | UTC+7 à UTC+9 | `Asia/Jakarta` | Kompas basé à Jakarta |
| Kazakhstan | UTC+5 à UTC+6 | `Asia/Almaty` | Vlast.kz basé à Almaty |

#### Heure d'été (DST) dans les pays cibles
Certains pays cibles pratiquent le changement d'heure (USA, Europe, Australie…). Utiliser les identifiants IANA — et non des offsets numériques fixes comme `UTC+1` — garantit que `ZoneId.of("Europe/Paris")` applique automatiquement le passage heure d'hiver/été. **Ne jamais stocker un offset numérique en base : toujours stocker l'identifiant IANA.**

---

### Dynamisme : le téléphone change de pays ou de fuseau

#### Ce qui change — et ce qui ne change pas

| Élément | Impact d'un changement de fuseau téléphone |
|---------|---------------------------------------------|
| Heure de collecte UTC des pays | **Aucun** — calculée uniquement sur le fuseau du pays cible |
| Affichage des heures dans l'UI | **Oui** — recalculer l'affichage avec `ZoneId.systemDefault()` |
| Frontière du jour (rotation lundi/mardi) | **Oui** — "lundi" commence à minuit heure locale téléphone |
| Workers WorkManager actifs | **Oui** — recalculer quels pays sont actifs si le jour a changé |

#### Écoute du changement de fuseau

Android émet `Intent.ACTION_TIMEZONE_CHANGED` dès que le fuseau système change (manuellement ou automatiquement via le réseau opérateur). C'est le seul point d'entrée nécessaire.

```java
// AndroidManifest.xml
<receiver android:name=".receivers.SystemEventReceiver"
          android:exported="false">
    <intent-filter>
        <action android:name="android.intent.action.TIMEZONE_CHANGED" />
        <action android:name="android.intent.action.DATE_CHANGED" />
        <action android:name="android.intent.action.BOOT_COMPLETED" />
    </intent-filter>
</receiver>
```

```java
// SystemEventReceiver.java
public class SystemEventReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        switch (intent.getAction()) {
            case Intent.ACTION_TIMEZONE_CHANGED:
            case Intent.ACTION_DATE_CHANGED:
            case Intent.ACTION_BOOT_COMPLETED:
                // Dans tous les cas : recalcul complet
                NewsScheduler.rescheduleAll(context);
                break;
        }
    }
}
```

#### Reschedule complet à chaque événement

```java
public class NewsScheduler {

    public static void rescheduleAll(Context context) {
        WorkManager wm = WorkManager.getInstance(context);

        // Annuler tous les Workers existants
        wm.cancelAllWork();

        // Quel jour est-il pour l'utilisateur ? (fuseau dynamique du téléphone)
        ZoneId phoneZone = ZoneId.systemDefault();
        DayOfWeek today = LocalDate.now(phoneZone).getDayOfWeek();

        // Charger les pays actifs pour ce jour depuis SQLite
        List<Country> activeCountries = CountryRepository.getCountriesForDay(today);

        for (Country country : activeCountries) {
            scheduleWorker(context, country);
        }
    }

    private static void scheduleWorker(Context context, Country country) {
        // Calcul du délai jusqu'à 7h00 dans le pays cible
        // Ce calcul est INDÉPENDANT du fuseau du téléphone
        ZoneId countryZone = ZoneId.of(country.getIanaTimezone());
        ZonedDateTime nowInCountry = ZonedDateTime.now(countryZone);
        ZonedDateTime next7am = nowInCountry
            .withHour(7).withMinute(0).withSecond(0).withNano(0);
        if (!nowInCountry.isBefore(next7am)) {
            next7am = next7am.plusDays(1);
        }
        long delayMs = Duration.between(Instant.now(), next7am.toInstant()).toMillis();

        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(
                NewsCollectorWorker.class, 24, TimeUnit.HOURS)
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .setConstraints(new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build())
            .addTag("country_" + country.getCode())
            .build();

        // REPLACE : on recalcule depuis zéro après chaque changement de fuseau
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "collect_" + country.getCode(),
            ExistingPeriodicWorkPolicy.REPLACE,
            request
        );
    }
}
```

#### Tableau des événements déclencheurs

| Événement Android | Broadcast | Effet sur l'app |
|-------------------|-----------|----------------|
| Changement de fuseau | `ACTION_TIMEZONE_CHANGED` | Reschedule complet + recalcul du jour courant |
| Passage à minuit | `ACTION_DATE_CHANGED` | Chargement du nouveau batch de pays (jour suivant) |
| Redémarrage | `ACTION_BOOT_COMPLETED` | Réenregistrement de tous les Workers (WorkManager ne persiste pas au reboot sans ça) |
| Réseau rétabli | géré par `NetworkType.CONNECTED` dans les Constraints | WorkManager relance automatiquement les Workers en attente |

> **Lien avec AndroidAgent** : le projet `AndroidAgent` implémente déjà un `BootReceiver` et un système de tâches planifiées. La classe `SystemEventReceiver` ci-dessus peut en être une extension directe, en réutilisant l'infrastructure existante.

---

*Analyse rédigée avec l'assistance de Claude (Anthropic), mai 2026.*
*Voir aussi : `pays_du_monde.md` pour la liste complète des pays et médias de référence.*
