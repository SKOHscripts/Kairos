# Estimateur de durées par modèle local

_Rôle : donner une durée estimée, avec son intervalle bas-haut et sa provenance en clair, à une tâche qui n'a ni estimation
ni série ni titre déjà vus, en la comparant aux tâches déjà faites ; le calcul est local, explicable et reproductible.
Fichiers couverts (prévus, 4.0.0) : `kmp/core/.../engine/DurationEstimator.kt` (interface extraite et façade),
`engine/HistoryEstimators.kt` (séries et titres, code actuel déplacé), `engine/EstimatorChain.kt`, `engine/model/`
(`ModelEstimator.kt`, `TextFeatures.kt`, `Similarity.kt`, `RidgePrior.kt`, `ModelGate.kt`, `ModelIndex.kt`, `ModelCache.kt`,
`ModelParams.kt`), `model/LearningSettings.kt` (`model`), `settings/SettingsForm.kt`, `team/Effort.kt` (`EffortSource.MODEL`),
`team/forecast/EffortModel.kt` et `EffortSpread.kt`, `team/forecast/MonteCarlo.kt` (`prepare`), `day/DayView.kt` (cache),
`ui/day/TaskRow.kt`, `Why.kt`, `NowCard.kt`, `EditTaskDialog.kt`, `ui/settings/SettingsFormCards.kt`, `ui/team/TeamTaskSheet.kt`,
`ui/team/forecast/ForecastPanels.kt`, `ui/app/AppServices.kt` (cache du modèle), `kmp/tools/make_icons.py`. Tests (prévus) :
`ModelEstimatorTest`, `TextFeaturesTest`, `ModelGateTest`, `ModelBacktestTest`, `NoFutureInfoTest`, `ModelReproducibilityTest`,
`IntervalCoverageTest`, `ModelPerformanceTest`, `EffortSpreadTest`, `LearnedModelTeamLoadTest`, `DurationEstimatorTest`,
`TeamIsolationTest`, `LearnedDurationsUiTest`, `StringsParityTest`._

État : **issue #99**, à spécifier le 2026-10-26, à implémenter (4.0.0). Aucun code n'existe encore : cette spec ne modifie
pas les specs existantes ; ses effets sont listés en fin de document (« Impacts sur les specs existantes »).

## 1. Besoin métier (cahier des charges)

### Objectif / problème

Les durées apprises (`durees-apprises.md`, 3.4.0) ne reconnaissent que deux choses : les occurrences d'une même **série**
récurrente et les tâches de **même titre**. Hors de ces deux cas, la tâche retombe sur la durée par défaut des réglages
(30 min), et le plan de la journée comme les prévisions d'équipe se trompent d'autant.

Deux cas fréquents restent sans réponse :

- **Un titre nouveau**, qui ressemble à des tâches passées sans leur être identique (« Rédiger le rapport trimestriel »
  après « Rapport T3 » et « Rapport mensuel ») ;
- **Un type riche en historique mais sans titre répété** (trente tâches « Revue de code » toutes titrées différemment).

Kairos a pourtant, sur l'appareil, tout ce qu'il faut pour deviner : le titre, le type, les points, et le temps réellement passé
sur des centaines de tâches faites. L'objectif est d'**estimer par ressemblance** ces tâches-là, sans jamais rien cacher de la
façon dont la durée est obtenue.

### Comportement attendu (utilisateur)

1. **Une tâche sans estimation explicite et sans durée apprise par série ou titre reçoit une durée estimée par le modèle**
   quand il est fiable : une valeur (« ~45 min ») et son **intervalle** (« entre 30 et 60 min »).
2. **Provenance toujours en clair, jamais une boîte noire.** Chaque estimation dit d'où elle vient, en phrase :
   « Estimée par le modèle : 45 min (entre 30 et 60), d'après 5 tâches proches : « Rapport T3 » (50 min), « Bilan mensuel »
   (40 min)… », ou, quand il n'y a pas de tâche proche : « d'après tes 34 tâches faites de type « Rédaction » à 5 points ».
   Les tâches proches sont listées une à une, avec leur durée réelle et leur degré de ressemblance. Pas de phrase, pas
   d'estimation : le modèle ne donne jamais un chiffre sans sa preuve.
3. **Ordre de repli fixe** : estimation explicite, puis série, puis titre, puis **modèle**, puis durée par défaut. L'estimation
   explicite l'emporte toujours ; la durée du modèle n'est jamais écrite dans la tâche (comme les durées apprises).
4. **Le modèle ne s'applique que s'il a fait ses preuves sur l'historique de l'utilisateur.** Kairos le rejoue sur les
   dernières tâches faites, sans jamais lui montrer la durée qu'il doit retrouver, et compare son erreur à celle de la durée par
   défaut. Si le modèle ne fait pas nettement mieux, ou si l'historique est trop court, son estimation est **affichée comme
   « pas encore fiable » sans être appliquée**, exactement comme une durée apprise sous son seuil. Le résultat de cette
   vérification est lisible dans les Réglages.
5. **Interrupteur « Estimer par modèle »** dans la carte « Durées apprises » des Réglages. **Activé par défaut** (voir
   « Décisions »). Désactivé, ou si les durées apprises sont coupées, Kairos se comporte exactement comme en 3.x.
6. **Local et hors ligne.** Rien ne quitte l'appareil, aucun réseau, aucune permission, aucun modèle téléchargé ni
   pré-entraîné : le « modèle » est calculé à partir de l'historique de l'utilisateur, et seulement de lui. Il fonctionne
   à l'identique sur Android, bureau et web.
7. **Même durée partout.** Le plan de la journée, la carte « Maintenant », l'alerte de dépassement du chrono et les
   projections de l'espace Équipe lisent la même durée du modèle (comme pour les durées apprises).
8. **L'intervalle nourrit les prévisions d'équipe.** Dans les prévisions « Par effort », l'effort d'une tâche estimée par le
   modèle est tiré **dans son intervalle** (et non dans le seul écart historique de l'équipe) : une tâche mal connue
   élargit la fourchette de la date de fin, une tâche bien connue la resserre. Le résultat dit combien de tâches du périmètre
   sont estimées ainsi.
9. **Isolation.** Une tâche d'un espace n'apprend que de son espace ; une tâche reçue d'un manager n'apprend ni n'enseigne
   rien ; le mode solo ne dépend d'aucune donnée d'équipe et son fichier d'export reste identique octet pour octet.

### Critères de succès

Les critères chiffrés sont mesurés par un **rétro-test** (§ 2, « Évaluation ») sur des historiques simulés à graines fixes.
Ils sont posés a priori ; un seuil qui échoue au premier passage se discute (modèle ou seuil) et la décision est consignée ici :
on ne relâche jamais un seuil en silence.

- **Mieux que rien là où l'estimateur actuel ne sait rien** (profil `NOVEL` : titres tous nouveaux, types et points riches) :
  l'erreur relative symétrique médiane de la chaîne complète est **≤ 0,8 ×** celle de la chaîne sans modèle (donc de la
  durée par défaut), sur au moins 18 graines sur 20 (`ModelBacktestTest.novelTitlesBeatTheDefault`).
- **Pas pire que la médiane par titre** (profil `RECURRING` : titres répétés) : l'erreur médiane du modèle **seul** est ≤ à
  celle de la médiane par titre, en agrégé sur les 20 graines, et jamais supérieure de plus de 10 % sur une graine
  (`ModelBacktestTest.modelIsNotWorseThanTitleMedian`).
- **Jamais pire que la durée par défaut au-delà de 10 %** : sur tous les profils, y compris `NOISE` (durées indépendantes des
  caractéristiques), l'erreur médiane de la chaîne complète est ≤ 1,1 × celle de la durée par défaut
  (`ModelBacktestTest.neverWorseThanDefault`). Sur `NOISE`, la garde coupe le modèle (estimation « pas fiable ») sur au moins
  18 graines sur 20 (`ModelGateTest.noiseIsGatedOff`) ; sur `SPARSE` (12 tâches faites), le modèle n'est jamais appliqué
  (`ModelGateTest.tooLittleHistoryIsNeverApplied`).
- **Intervalle honnête** : l'intervalle nominal à 80 % contient la durée réelle dans 70 % à 90 % des cas (profil `NOVEL`,
  au moins 100 tâches testées) (`IntervalCoverageTest`).
- **Reproductible** : mêmes tâches, mêmes réglages → mêmes estimations, au bit près, sur JVM, Android et wasm ; l'ordre dans
  lequel on présente l'historique ne change rien ; des valeurs attendues écrites en dur dans `commonTest` détectent toute
  divergence entre cibles (`ModelReproducibilityTest`). Un historique simulé de même graine redonne les mêmes erreurs.
- **Aucune information future** : changer les durées ou les titres des tâches faites **après** une tâche donnée ne change pas
  l'estimation de celle-ci (`NoFutureInfoTest`).
- **Explicable** : toute estimation du modèle porte au moins une tâche proche ou une base de régression nommant ses
  caractéristiques (`ModelEstimatorTest.everyEstimateCarriesItsEvidence`).
- **Rapide** : 500 tâches faites et 200 tâches à estimer en moins de 1 s sur JVM en test (mesuré ~0,2 s visé), sans geler
  l'interface plus de 100 ms sur le web une fois l'index construit (`ModelPerformanceTest` ; mesure manuelle dans Chromium
  consignée ici à l'implémentation).
- **Prévisions** : sans écart (`low` = `high` = durée), le plan rejoué est **exactement** le plan déterministe ; avec un
  écart, la fourchette P50-P85 de la date de fin s'élargit et reste reproductible à graine égale (`EffortSpreadTest`,
  `ReproducibilityTest` étendu).
- **Rien ne change sans le modèle** : interrupteur coupé → `DurationEstimatorTest`, `DifferentialTest` et
  `LearnedTeamLoadTest` passent inchangés ; fichier d'or d'export inchangé
  (`TeamIsolationTest.soloExportIsByteIdentical`).
- **Isolation** : ajouter des tâches d'équipe chronométrées ne change aucune estimation personnelle, et réciproquement ;
  une tâche reçue n'est jamais un voisin (`TeamIsolationTest`, `ModelEstimatorTest`).
- **À l'écran** : badge distinct, ligne de provenance, puce de suggestion et liste des tâches proches dans la fiche, interrupteur
  et résumé de la vérification dans les Réglages, aucun débordement à 360 dp (`LearnedDurationsUiTest`) ; textes dans toutes
  les langues de l'application (`StringsParityTest`).
- **Libre et sans réseau** : `kmp/gradle/libs.versions.toml` inchangé, manifeste Android sans nouvelle permission.

### Hors périmètre / différé

- **Tout réseau** : pas de service distant, pas de téléchargement de poids, pas de télémétrie.
- **Modèles pré-entraînés** (plongements de mots, modèle de langue, poids embarqués) et toute dépendance non libre ou native.
- **Apprentissage entre utilisateurs ou entre appareils** (déjà hors périmètre de `durees-apprises.md`) : chaque appareil
  apprend de son historique ; la synchronisation éventuelle (#88) transporte des tâches, pas un modèle.
- **Effet du membre** dans l'espace Équipe (un même type de tâche prend plus de temps à tel membre) : différé ; la garde
  baisse la confiance d'elle-même quand l'historique mélange des profils.
- **Description de la tâche, jour de la semaine, heure de la journée, durée des sous-tâches** comme caractéristiques : écartés
  pour la première version (voir « Décisions »).
- **Réentraînement automatique en arrière-plan, notifications** de changement d'estimation.
- **Modifier rétroactivement** des estimations : inchangé (`statistiques.md`, « Corriger les estimations… »).
- **Remplacer l'écart historique de l'équipe** pour les tâches estimées par série ou titre (voir « Décisions », prévisions).

## 2. Solution technique

### Interface extraite et chaîne de repli

`DurationEstimator` devient une **interface** (`core/engine/DurationEstimator.kt`) ; l'objet actuel y est déplacé en deux
implémentations et une chaîne. Les appelants ne changent pas : la façade garde les mêmes noms.

```kotlin
interface DurationEstimator {
    enum class Source { SERIES, TITLE, MODEL }
    data class Estimate(
        val minutes: Int, val source: Source, val samples: Int, val reliable: Boolean,
        val low: Int? = null, val high: Int? = null,      // MODEL seulement ; nuls pour SERIES et TITLE
        val evidence: Evidence? = null,                   // MODEL seulement
    )
    data class Sample(
        val taskId: Long, val seriesId: Long?, val title: String, val space: TaskSpace, val minutes: Int,
        val taskType: String = "", val points: Int? = null, val priority: Int? = null,
        val projectTag: String = "", val recurring: Boolean = false,
    )
    enum class Basis { NEIGHBOURS, REGRESSION }
    data class Neighbour(val taskId: Long, val title: String, val minutes: Int, val similarityPercent: Int)
    data class Evidence(
        val basis: Basis, val neighbours: List<Neighbour>,          // les plus proches d'abord ; vide pour REGRESSION
        val taskType: String?, val points: Int?, val corpus: Int,   // caractéristiques de la base de régression
        val gate: ModelGate.Result,                                  // la vérification, pour « Pourquoi » et les Réglages
    )

    /** Au plus une estimation, ou `null` : ce niveau ne sait rien de [task]. */
    fun estimate(task: Task, history: History, settings: Settings): Estimate?

    class History(val samples: List<Sample>, val cache: ModelCache = ModelCache.NONE)

    companion object { /* samples, estimate, estimates, appliedMinutes, effectiveMinutes, withLearnedEstimates, spreads */ }
}
```

- `SeriesEstimator` et `TitleEstimator` (`HistoryEstimators.kt`) : le code de `fromSamples` et des filtres série / titre,
  **déplacé sans changement de comportement** (médiane des `history` derniers, aberrants à 3 ×, arrondi à 5 min, bornes). Leurs
  `Estimate` gardent `low = high = null` : l'intervalle est propre au modèle.
- `ModelEstimator` (`engine/model/`) : voir ci-dessous. `Source.MODEL`.
- `EstimatorChain(listOf(SeriesEstimator, TitleEstimator, ModelEstimator))` : **la règle actuelle étendue** : on essaie les
  niveaux dans l'ordre, **le premier fiable gagne** ; à défaut, le premier qui a quelque chose à dire est rendu avec
  `reliable = false` (affiché, jamais appliqué). Le niveau modèle est sauté si `learning.enabled` est faux ou
  `learning.model == false`. Les garde-fous communs restent en tête : désactivé, tâche reçue, estimation explicite → `null`.
- La façade `DurationEstimator.estimate(task, samples, settings, cache = ModelCache.NONE)` délègue à la chaîne ; les
  signatures de `samples`, `estimates`, `appliedMinutes`, `effectiveMinutes` et `withLearnedEstimates` sont **inchangées**
  (paramètre `cache` facultatif ajouté à celles qui calculent), donc les tests actuels compilent tels quels.
  `appliedMinutes` garde les estimations fiables, modèle compris.
- Nouveau : `DurationEstimator.spreads(estimates): Map<Long, Spread>` (les estimations **fiables** de source `MODEL` :
  `Spread(low, high)`, en minutes), et `DurationEstimator.withLearned(snapshot, now, cache): Learned(snapshot, spreads)` ;
  `withLearnedEstimates` en est la projection `snapshot`. Voir « Prévisions ».

### Données : `Sample` enrichi

`samples(tasks, spentByTask)` garde sa règle (tâches **terminées**, `origin == null`, temps passé ≥ `MIN_SAMPLE_MINUTES` = 2) et
remplit en plus `taskType`, `points` (`fibonacciPoints`), `priority`, `projectTag`, `recurring` (`recurrence != NONE`). Les champs
ajoutés ont une valeur par défaut : aucun appelant ni test existant ne change. La **description** n'est pas utilisée.

Pour une tâche à estimer, les mêmes caractéristiques sont lues sur la `Task` (jamais le temps, qu'elle n'a pas).

### Le modèle : voisins pondérés, régression en repli

Le modèle est un **index calculé de l'historique** (`ModelIndex.build(samples)`), jamais stocké : pas de colonne, pas
d'export, pas de migration, pas de poids à versionner. Il retient au plus `MAX_CORPUS` = 500 échantillons **du même espace**
que la tâche, les plus récents par `taskId` (jamais `updatedAt`), hors la tâche elle-même.

**1. Caractéristiques textuelles** (`TextFeatures.kt`, du titre seul) :

- Normalisation écrite à la main, sans dépendance : minuscules, repli des diacritiques latins par une table explicite
  (é è ê ë → e, ü → u, ñ → n, ç → c, ß → ss, œ → oe, æ → ae…) et du « ё » russe en « е », toute suite de chiffres remplacée par
  un seul « 0 » (« T3 » et « T4 » se confondent ; « semaine 42 » et « semaine 7 » aussi), ponctuation et symboles en
  séparateurs. Pas de `java.text.Normalizer` (absent de `common`).
- Deux familles de caractéristiques binaires (présence) : les **mots** (d'au moins 2 caractères) et les **trigrammes de
  caractères** de chaque mot d'au moins 3 caractères, entouré de bornes (« ^rapport$ » → `^ra`, `rap`, `app`…). Les trigrammes
  rapprochent « rapport / rapports » ou « rédiger / rédaction » sans lemmatiseur ni liste de mots vides, qu'il faudrait
  écrire et tenir pour six langues ; le texte russe fonctionne comme les autres (pas de supposition d'alphabet).
- **Pondération** (idf) : `idf(f) = sqrt((N + 1) / (df(f) + 1))`, où `N` est le nombre d'échantillons du corpus et `df` le nombre
  qui portent `f`. Les mots courants (« le », « de », « the ») pèsent peu sans liste de mots vides. Un mot **absent du corpus**
  garde le poids maximal : un mot jamais vu baisse la ressemblance, comme il se doit. `sqrt` et non un logarithme : voir
  « Déterminisme ».
- Chaque famille donne un vecteur normalisé en norme L2 ; `texte(a, b) = 0,5 × cosinus(mots) + 0,5 × cosinus(trigrammes)`.
  Les vecteurs sont des tableaux triés par identifiant de caractéristique (identifiants attribués dans l'ordre d'apparition en
  parcourant les échantillons par `taskId` croissant), le produit scalaire se fait par fusion de deux tableaux triés :
  ordre de sommation fixe.

**2. Ressemblance** (`Similarity.kt`), entre la tâche à estimer `t` et un échantillon `s` :

| Terme | Poids | Valeur | Disponible si |
|---|---|---|---|
| Texte | 0,5 | `texte(t, s)` | le titre a au moins une caractéristique |
| Type | 0,2 | 1 si même `taskType` (non vide), sinon 0 | `t` a un type |
| Points | 0,2 | `1 − \|rang(t) − rang(s)\| / 6`, le rang étant la position dans l'échelle `FIBONACCI_SCALE` (0 à 6) ; 0 si `s` n'a pas de points | `t` a des points |
| Étiquette de projet | 0,1 | 1 si même `projectTag` (non vide), sinon 0 | `t` a une étiquette |

`ressemblance(t, s) = Σ (poids × valeur) / Σ poids` **sur les seuls termes disponibles pour `t`** : une tâche sans type ne
perd pas la moitié de son score maximal. Résultat dans [0 ; 1]. La **priorité** n'entre pas dans la ressemblance : c'est une
urgence, pas une taille ; on la laisse à la régression, qui dira si les données lui donnent un effet.

**3. Voisins et médiane pondérée** (`ModelEstimator.kt`) :

- Voisins = les `NEIGHBOURS_MAX` = 9 échantillons de ressemblance la plus haute, **au moins `SIMILARITY_MIN` = 0,25**
  (égalités départagées par `taskId` décroissant, donc par récence).
- Poids d'un voisin : `ressemblance² × récence`, avec `récence = 2 − rang/n` (`rang` 0 pour le plus récent des `n` voisins) :
  le carré favorise les quasi-doublons, le plus récent compte au plus deux fois le plus ancien (les durées dérivent à mesure
  qu'on s'améliore).
- **Aberrants** : comme pour les séries, un voisin au-delà de 3 × la médiane pondérée (chrono oublié) est écarté, puis la
  médiane est recalculée ; `samples` compte les voisins retenus.
- **Médiane pondérée** (basse) : les durées triées croissantes (égalités par `taskId`), on retient la première dont le poids
  cumulé atteint la moitié du poids total. **Quantiles de l'intervalle** : même procédé à 10 % et 90 % (`low`, `high`).
  Arrondi et bornes de l'estimateur actuel (multiple de 5 min, demi vers le haut, de 5 min à la fenêtre de travail) ; on
  garantit `low ≤ minutes ≤ high`.
- **Élargissement** : un intervalle fait de `k` voisins sous-estime la dispersion d'une tâche nouvelle (le min-max de `k`
  tirages ne couvre qu'une part `(k−1)/(k+1)` des suivants). L'écart de chaque côté de la médiane est multiplié par
  `1 + 2/k`. Valeur de départ, arbitrée par `IntervalCoverageTest` (critère 70-90 % de couverture) ; tout changement se consigne.
- `Evidence(NEIGHBOURS, …)` : les voisins retenus avec leur durée et leur ressemblance en pour cent entier.

**4. Régression légère en repli** (`RidgePrior.kt`) quand il y a moins de `MIN_NEIGHBOURS` voisins retenus
(`MIN_NEIGHBOURS = max(3, learning.minSamples)`) : un type riche en historique mais dont aucun titre ne ressemble.

- Cible : `y = sqrt(minutes)` (stabilise la variance, la racine est exacte sur toutes les cibles ; pas de logarithme).
- Variables : constante ; rang des points centré et réduit ((rang − 3) / 3, 0 si absents) et indicateur « points absents » ;
  une variable 0/1 par **type** présent dans au moins 3 échantillons (huit au plus, dans l'ordre alphabétique des types
  repliés ; les autres types et l'absence de type forment la référence) ; priorité P0 et P1 (P2 et absence = référence) ;
  récurrence 0/1. Au plus 14 variables.
- **Crête** (ridge) de coefficient `RIDGE_LAMBDA` = 1,0 sur toutes les variables sauf la constante ; équations normales
  résolues par élimination de Gauss à pivot partiel (pivot de plus grande valeur absolue, égalités au plus petit indice) :
  ordre fixe, `+ − × ÷` seulement. Coût `O(n × d²)` avec `d ≤ 14` : négligeable pour 500 échantillons.
- Estimation : `ŷ = x·β`, `minutes = ŷ²`. Intervalle : quantiles à 10 % et 90 % des résidus du corpus (rang le plus proche),
  élargis de 1,25, soit `low = (ŷ + 1,25 q10)²`, `high = (ŷ + 1,25 q90)²` (bornés par 5 min en bas).
- **Pas de régression** si le corpus compte moins de `MIN_CORPUS` = 20 échantillons ou si la tâche n'a ni type connu du modèle
  ni points : sans caractéristique pour la décrire, la régression ne rendrait que la moyenne des tâches, ce qui n'est pas une
  estimation de *cette* tâche. Le niveau modèle rend alors `null`.
- `Evidence(REGRESSION, …)` : le type et les points utilisés (ceux qui ont servi) et la taille du corpus.

**5. Fiabilité et garde** (`ModelGate.kt`). L'estimation du modèle est `reliable` si et seulement si :

1. le corpus de l'espace compte au moins `MIN_CORPUS` = 20 échantillons ;
2. la base est suffisante : voisins retenus ≥ `MIN_NEIGHBOURS`, ou base de régression admise (ci-dessus) ;
3. la **garde** a réussi.

La garde est le moyen de tenir « jamais pire que la durée par défaut » *sur l'historique de l'utilisateur lui-même* :

- Candidats : les `GATE_SIZE` = 30 derniers échantillons du corpus (par `taskId`) qui ont au moins `GATE_PREFIX_MIN` = 8
  échantillons plus anciens. Chacun est estimé par le modèle **construit sur les seuls échantillons plus anciens** (index,
  idf et régression compris : aucune information future), sa durée réelle n'est jamais lue avant le calcul.
- Erreur : **erreur relative symétrique** `e(p, r) = |p − r| / max(p, r)` ∈ [0 ; 1[, pour une prédiction `p` et un réel `r`
  (bornée et indifférente au sens de l'erreur, contrairement à `|p − r| / r`, qui pardonne les sous-estimations).
- La garde réussit si au moins `GATE_MIN` = 10 candidats ont pu être estimés **et** si la médiane de `e` du modèle est
  `≤ GATE_RATIO` = 0,9 × la médiane de `e` de la durée par défaut (`defaultTaskDurationMinutes`) sur les mêmes candidats.
  Un modèle qui ne fait pas nettement mieux que 30 min ne sert à rien et ne mérite pas de bouger le planning.
- `ModelGate.Result(evaluated, modelError, defaultError, passed, reason)` avec `reason` ∈ {`OK`, `TOO_FEW_SAMPLES`,
  `NOT_BETTER`} ; il est rendu dans `Evidence` et dans l'état de la carte des Réglages.

Une estimation de source `MODEL` non fiable (garde échouée, corpus trop court, trop peu de voisins) est **exposée** avec
`reliable = false` quand elle existe : affichée « pas encore fiable », jamais appliquée.

**Paramètres** : toutes les constantes vivent dans `ModelParams` (valeurs de départ ci-dessus, arbitrées par le rétro-test) :
`MAX_CORPUS` 500, `MIN_CORPUS` 20, `NEIGHBOURS_MAX` 9, `SIMILARITY_MIN` 0,25, `MIN_NEIGHBOURS` max(3, `minSamples`),
poids 0,5 / 0,2 / 0,2 / 0,1, quantiles 0,10 et 0,90, `RIDGE_LAMBDA` 1,0, `GATE_SIZE` 30, `GATE_PREFIX_MIN` 8, `GATE_MIN` 10,
`GATE_RATIO` 0,9, facteur d'aberrant 3,0 (partagé avec `HistoryEstimators`). Ce ne sont **pas** des réglages utilisateur
(un seul interrupteur : § Réglage). `learning.history` ne concerne que séries et titres ; `learning.minSamples` fixe aussi
le nombre minimal de voisins, avec un plancher à 3.

### Déterminisme et pureté

- `core` reste pur : pas d'horloge, pas d'E/S, pas de graine tirée (le modèle n'est pas aléatoire du tout) ; l'instant et
  l'historique sont des paramètres.
- **Identique au bit près sur JVM, Android et wasm** : uniquement `+ − × ÷` et `sqrt` (exacte sur toutes les cibles), jamais
  `ln`, `exp`, `pow` ni trigonométrie (leurs derniers chiffres divergent selon les moteurs) ; c'est pourquoi l'idf est une
  racine et la cible de la régression une racine, pas des logarithmes.
- Aucune itération sur `HashMap` / `HashSet` pour produire un résultat : tableaux triés, `LinkedHashMap` au besoin ; les
  échantillons sont **triés par `taskId` avant tout calcul** (l'ordre d'entrée est sans effet) ; toutes les égalités ont un
  départage explicite (`taskId`).
- La normalisation du texte n'appelle que `lowercaseChar()` et `isLetterOrDigit()` du standard, après la table de repli
  explicite ; `ModelReproducibilityTest` fige des valeurs sur des titres accentués (« É », « ß », « Ё », « ñ ») pour détecter
  toute divergence de tables Unicode entre cibles.

### Cache de l'index et performance

- `DayScreen` recalcule `DayView.build` à chaque minute et à chaque changement de données : l'index ne doit pas être reconstruit
  chaque fois. `ModelCache` (`engine/model/ModelCache.kt`) est une **mémoïsation explicite à une case**, passée en paramètre :
  clé = `ModelIndex.fingerprint(samples)`, un FNV-1a 64 écrit à la main (jamais `String.hashCode()`, comme
  `ForecastRandom.fnv1a64`) sur (`taskId`, minutes, type, points, priorité, étiquette, récurrence, titre) des échantillons
  retenus. La garde dépend en plus de `defaultTaskDurationMinutes` : elle est recalculée, seule, quand ce réglage change.
  `ModelCache.NONE` ne mémorise rien (tests, prévisions). L'instance de l'interface vit dans `AppServices` et est passée à
  `DayView.build` (Jour et Semaine).
- Coût de référence (500 échantillons) : construction de l'index et garde, l'ordre de 15 puis 20 ms sur JVM (30 constructions
  sur des préfixes) ; une estimation = `n` produits scalaires creux (quelques dizaines de caractéristiques chacun) soit moins de
  0,5 ms ; 200 estimations en dessous de 100 ms. Budget de test : **1 s** pour 500 échantillons et 200 tâches
  (`ModelPerformanceTest`, `jvmTest`, exécuté par défaut ; bornes larges pour la variance de la CI). Sur le web, un seul fil :
  la construction est payée **une fois par changement d'historique** (cache) ; à mesurer à la main dans Chromium
  (`architecture.md`), cible < 300 ms ; si la mesure dépasse, on **baisse `MAX_CORPUS`** (pas la qualité des calculs) et on le
  consigne ici.
- Seules les tâches à faire sans estimation explicite **et** sans niveau fiable avant le modèle y passent : en pratique peu de
  tâches par recalcul.

### Évaluation par rétro-test (`core/src/commonTest/.../engine/model/`)

L'évaluation vit dans les tests (aucun code de production) :

- **`SimulatedHistory.generate(seed, profile, n = 200)`** : historique synthétique (`kotlin.random.Random(seed)`, bruit en
  `sqrt` seulement) dont la vérité est connue ; identifiants croissants = ordre de réalisation. Profils : `RECURRING` (6 titres
  répétés), `NOVEL` (chaque titre nouveau, mais un lexique propre à chaque type : durée = base du type × facteur du palier de
  points × bruit triangulaire 0,7-1,5), `NOISE` (durées indépendantes de toute caractéristique), `SPARSE` (12 tâches faites).
- **Découpage temporel (walk-forward)** : les 30 % premiers échantillons servent de départ ; pour chaque suivant `i`, on estime
  avec `samples[0 until i]` seuls, **sans** sa durée, puis on mesure l'erreur. Jamais de découpage aléatoire (il laisserait
  fuir l'avenir) ni de validation croisée.
- **Comparaisons** sur les mêmes tâches testées : durée par défaut ; médiane par titre (`TitleEstimator`, sinon défaut) ;
  chaîne sans modèle ; chaîne complète ; modèle seul. Métrique : médiane de `e(p, r)` (voir la garde), couverture de
  l'intervalle `[low ; high]`.
- **`NoFutureInfoTest`** (propriété) : pour un historique et un indice `i`, remplacer arbitrairement les durées et les titres
  des échantillons d'indice > `i` laisse l'estimation de `i` strictement égale.
- Les seuils sont ceux des critères de succès. Graines 1 à 20 en dur. Les valeurs attendues en dur de
  `ModelReproducibilityTest` couvrent l'index (idf), une ressemblance, une médiane pondérée, la régression et un intervalle.

### Prévisions : de l'intervalle à une loi d'effort

Aujourd'hui (`equipe-simulation.md`), `EffortModel` tire, par tirage et par tâche, un **facteur d'erreur** réel / base dans
l'historique de l'équipe (borné [0,2 ; 5]) et le multiplie par l'effort du plan. Une tâche estimée par le modèle a mieux : sa
propre dispersion, apprise de tâches qui lui ressemblent. On la substitue au facteur global pour cette tâche seule.

- **Transport** : `withLearned` rend `Learned(snapshot, spreads)` ; le snapshot porte la durée du modèle comme
  `estimatedMinutes` (comme les durées apprises), et `spreads` (`taskId → Spread(low, high)`) l'intervalle. `MonteCarlo.prepare`
  lit `withLearned` à la place de `withLearnedEstimates` et passe `spreads` à `EffortModel`. Aucune signature d'`Effort`,
  `TeamWork` ou `LoadPlan` ne change.
- **Loi** (`team/forecast/EffortSpread.kt`) : avec `m` la durée (médiane), `l = low / m` et `h = high / m`, le **facteur
  d'effort** est la fonction quantile affine par morceaux `f(u)` passant par les points (0 ; `a`), (0,10 ; `l`), (0,50 ; 1),
  (0,90 ; `h`), (1 ; `b`), avec `a = max(0,2 ; l − (1 − l) / 2)` et `b = min(5 ; h + (h − 1) / 2)` : les queues prolongent chaque
  côté de la moitié de l'écart médiane-bord, et les bornes [0,2 ; 5] sont celles des facteurs d'erreur de l'équipe.
  `f` n'utilise que `+ − × ÷` (reproductible au bit près) ; `l = h = 1` donne `f ≡ 1`.
- **Tirage** : dans `runBatch`, pour une tâche à écart, `u = stream(base, DOMAIN_ERROR, clé de la tâche).nextDouble()` et le
  facteur est `f(u)` **au lieu de** `data.errorFactor(...)`. Même sous-générateur (clé = `teamUid`) : les **tirages communs**
  entre réel et scénarios sont conservés (décision de `equipe-simulation.md`). L'effort reste `Effort.planned × facteur` : une
  tâche entamée garde sa forme de dispersion sur le restant.
- **Pas de double compte** : pour ces tâches, le facteur d'erreur historique de l'équipe n'est **pas** appliqué en plus, car
  l'intervalle est déjà bâti sur des durées réelles. Les tâches estimées par série ou titre gardent le comportement actuel
  (approximation existante, non rouverte ici).
- **Honnêteté** : `ForecastResult` porte `modelEstimated` (nombre de tâches du périmètre dont l'effort vient du modèle) ; la
  ligne de source dit « dont n tâches estimées par le modèle (fourchette apprise) ». Le modèle par débit ne change pas.
- **Cohérence avec `equipe-simulation.md`** : le rééchantillonnage de l'historique réel reste le moteur ; la loi du modèle est
  **elle aussi tirée de durées réelles** (voisins), pas ajustée à une forme. Seule la ligne « Hors périmètre : apprentissage
  automatique, régression » est précisée (elle vise l'ajustement de lois sur l'échantillon, petit, d'**une équipe**, pas
  l'estimation par ressemblance sur l'historique de **l'utilisateur**) ; le report se fait à l'implémentation.

### Réglage

- `LearningSettings` gagne `model: Boolean? = null` ; **nul = actif**. Nullable à défaut nul, et non un booléen à `true`, car
  l'export écrit les valeurs par défaut (`encodeDefaults = true`) : un booléen écrirait `"model": true` dans le fichier de
  tout utilisateur qui a déjà un objet `learning`. Pas de migration ni de nouvelle colonne (`Settings` est sérialisé tel quel).
- `SettingsForm` expose `learning.model` et ne crée l'objet `learning` qu'en cas d'écart (règle actuelle) ; `team` n'est jamais
  touché. `learning.enabled == false` coupe tout, modèle compris.
- **Actif par défaut** (voir « Décisions »).

### Interface

La provenance est visible partout où la durée l'est, avec les mêmes règles qu'aujourd'hui (jamais la couleur seule) :

- `ui/day/TaskRow.kt` : `LearnedBadge` pour une estimation de source `MODEL` fiable : `~45 min` avec une **icône propre au
  modèle** (distincte de celle des séries) et une description d'accessibilité : « Estimée par le modèle d'après 5 tâches
  proches, entre 30 et 60 minutes ». L'intervalle n'est pas dans le badge (largeur) mais dans les surfaces suivantes.
- `ui/day/NowCard.kt` : la même durée fiable, mêmes règles.
- `ui/day/Why.kt` : ligne de provenance en phrase complète (fiable : durée, intervalle, nombre et titres de quelques voisins ;
  non fiable : la raison de la garde, « le modèle ne fait pas encore mieux que la durée par défaut sur tes dernières
  tâches », « pas assez de tâches faites (12 sur 20 nécessaires) » ou « pas assez de tâches proches (2, 3 nécessaires) »).
- `ui/day/EditTaskDialog.kt` : sous le champ des minutes, tant qu'il est vide : puce « Utiliser 45 min (entre 30 et 60) » qui
  remplit le champ **sur action** (règle « on ne remplit que ce qui est vide »), et une section repliable « Tâches proches »
  qui liste chaque voisin (titre, durée réelle, ressemblance en %), bascule de 48 dp. Pour une base de régression : la phrase
  « d'après tes 34 tâches de type « Rédaction » à 5 points ». Si le modèle n'est pas fiable : une phrase, sans action.
- `ui/team/TeamTaskSheet.kt` : « …, estimée par le modèle (30 à 60 min) » via `EffortSource.MODEL`
  (`core/team/Effort.kt`, à côté de `LEARNED`).
- `ui/team/forecast/ForecastPanels.kt` : « dont n tâches estimées par le modèle » sous la source des données.
- `ui/settings/SettingsFormCards.kt` : dans la carte « Durées apprises » (catégorie « Planification »), interrupteur
  « Estimer par modèle » avec son aide (« Compare le titre, le type et les points d'une tâche à tes tâches déjà faites. Rien ne
  quitte cet appareil. ») et, dessous, **l'état de la vérification** : « Sur tes 30 dernières tâches : erreur médiane 25 %,
  contre 48 % pour la durée par défaut » / « Pas encore assez de tâches chronométrées (12 sur 20) » / « Ne fait pas mieux que la
  durée par défaut : non appliqué ». Interrupteur grisé, avec l'explication, quand les durées apprises sont coupées.
- Une icône ajoutée dans `kmp/tools/make_icons.py` (jamais à la main), textes `learned_model_*`, `why_learned_model*`,
  `setting_learning_model*`, `effort_model` dans **toutes** les langues de l'application (`StringsParityTest`, apostrophe
  typographique), rien en dur ; 360 dp sans débordement ; pas de raccourci clavier annoncé sur Android.
- Visite guidée « Nouveau dans cette version » et carte du catalogue (`FeatureCatalog`, `since` = 4.0.0) : c'est une
  fonctionnalité visible (voir « Impacts »).

### Isolation du mode solo

- Le modèle est dans `core/engine` et ne lit que `Sample`, jamais `core/team` : le mode solo n'importe aucune donnée d'équipe.
- Un échantillon n'est retenu que s'il est du **même espace** que la tâche estimée (règle actuelle, `Sample.space`) ; une
  tâche reçue d'un manager (`origin != null`) n'est ni estimée ni échantillon ; le corpus d'une tâche d'équipe est celui des
  tâches d'équipe faites (temps rapporté compris, `TimeTracking.spentMinutesByTask`).
- Sans donnée d'équipe, aucune table `team_*` n'est lue ; l'interrupteur vit sous `learning`, jamais sous `team` ; l'export
  ne change pas tant que `learning.model` est nul.
- Les prévisions n'existent que dans l'espace Équipe : le chemin `spreads` n'est atteint que par `MonteCarlo.prepare`.

### Décisions et pièges tracés

Décidées le 2026-10-26 à la rédaction de la spec ; à confirmer par le propriétaire à la revue (l'activation par défaut en
particulier).

- **Modèle local, calculé de l'historique, sans poids stockés** (écarté : poids pré-entraînés passés en paramètre en JSON,
  piste de `durees-apprises.md`) : un historique personnel est petit, et aucun jeu de données public ne ressemble à celui d'une
  personne ; un modèle générique serait plus faux qu'un modèle de dix tâches proches, et sa provenance introuvable. Rien à
  versionner, à migrer, à exporter, à synchroniser.
- **Voisins avec médiane pondérée comme cœur** : explicable en une phrase (« tes tâches les plus proches ont pris… »), la
  preuve *est* le calcul, donne un intervalle sans hypothèse de forme (même philosophie que le rééchantillonnage de
  `equipe-simulation.md`), et la médiane est robuste aux chronos oubliés. Sur des titres identiques, il se confond avec la
  médiane par titre, ce qui rend le critère « pas pire que le titre » naturel.
- **Régression en repli seulement, pas en mélange** : mêler une régression aux voisins ferait perdre la phrase de provenance
  (« d'après ces tâches »). Utilisée seulement faute de voisins, elle garde une explication courte (type, points, effectif).
  Écarté : régression en cœur (moins explicable, plus sensible aux types rares).
- **Racine plutôt que logarithme** (idf et cible de la régression) : `sqrt` est exacte sur toutes les cibles, `ln` / `exp`
  ne le sont pas, et la reproductibilité au bit près est une règle (`kairos-monte-carlo`).
- **Trigrammes de caractères et idf plutôt que lemmatiseur et mots vides** : six langues à servir, sans dépendance ni liste à
  tenir ; l'idf fait le travail des mots vides à la volée. Écartés : lemmatiseur par langue, liste de mots vides, plongements
  de mots (poids lourds, opaques).
- **La priorité n'entre pas dans la ressemblance** : urgence ≠ taille ; la régression (qui dira si elle pèse) la reçoit.
  Écartés pour la v1 : **description** (longue, bruitée, plus de calcul, plus intrusive à afficher comme preuve), **jour de la
  semaine / heure** (non connus d'une tâche non planifiée, effet faible, fragiliserait le rétro-test).
- **Activé par défaut, sous garde** (écartés : désactivé par défaut ; suggestion seule). Pour : c'est la raison d'être de
  #99 (une option cachée n'aide personne), les durées apprises sont déjà actives par défaut (`durees-apprises.md`), et la garde
  fait ce que « désactivé » voulait faire : si le modèle ne bat pas la durée par défaut sur l'historique de l'utilisateur, il
  ne s'applique pas. Contre, **tension assumée** avec « rien ne change sans réglage » : à la mise à jour, le plan peut changer
  pour qui a un historique riche. Atténuations : garde sur l'historique personnel, jamais sur une durée explicite, série ou
  titre fiables passent avant, provenance et fourchette visibles partout, annoncé par la visite, interrupteur dans les Réglages,
  version majeure.
- **Garde ≤ 0,9 × l'erreur de la durée par défaut** : à égalité le modèle ne justifie pas de déplacer le planning ; la marge
  de 10 % évite une bascule entre deux valeurs au gré d'une tâche de plus. Erreur relative symétrique plutôt que `|p − r| / r`,
  qui récompense les sous-estimations.
- **Médiane pondérée basse** (pas de moyenne des deux valeurs centrales) : sans égalité de poids garantie, la moyenne
  exigerait une comparaison de flottants à 0,5 exact ; la médiane basse est reproductible et l'arrondi à 5 min absorbe l'écart
  avec `TaskStats.median`.
- **Index recalculé, jamais stocké** et mémoïsé par empreinte : pas de migration, pas de cohérence à garder entre un modèle
  stocké et un historique corrigé après coup (une tâche peut être rééditée à tout moment). `MAX_CORPUS` = 500 : borne le coût
  de l'index, du cache et du web, et les 500 dernières tâches suffisent à un outil personnel.
- **L'intervalle remplace le facteur d'erreur d'équipe pour ses tâches** (écartés : multiplier les deux, double compte ; ne
  pas utiliser l'intervalle, ce qui jette précisément l'information que le modèle apporte). Le nombre de tâches concernées
  est affiché : le manager sait ce que couvre la fourchette.
- **Alerte du chrono sur `minutes`, comme pour les séries** (écarté : `high`) : une seule règle pour toutes les durées apprises
  (`DurationEstimator.effectiveMinutes`).
- **Le plan de la journée n'utilise que `minutes`** : le placement est déterministe ; l'intervalle ne sert qu'à l'affichage et
  aux prévisions.
- **Pas de réglage fin** (voisins, seuils, poids) : un seul interrupteur, comme pour les autres cartes de Réglages ; un
  utilisateur ne sait pas régler une ressemblance, et la garde remplace le réglage.
- **Piège : le modèle ne doit jamais voir la durée à prédire.** L'index ne contient que des tâches terminées avec leur
  temps réel ; la tâche à estimer est exclue (`it.taskId != task.id`) ; la garde et le rétro-test reconstruisent l'index sur
  les seuls échantillons plus anciens (`NoFutureInfoTest`).
- **Piège : Unicode.** Les tables de minuscules peuvent différer entre JVM et wasm sur des caractères rares ; la table de repli
  est explicite pour les accents des langues de l'application et un test fige les valeurs.

**Alternatives écartées (avec leur raison)**

- **Réseau de neurones** (perceptron, petit transformeur) : l'historique d'une personne (quelques centaines de tâches) est trop
  petit, le résultat est opaque (contraire à « jamais une boîte noire »), et `exp` / `tanh` ne sont pas reproductibles au bit
  près entre cibles.
- **Arbres, forêts, gradient boosting** : la dépendance (ou le code à écrire) pour un gain nul à cette taille, une explication
  par chemin d'arbre moins parlante que des tâches concrètes, entraînement sensible à l'ordre.
- **Bibliothèque d'apprentissage automatique** (Smile, ONNX Runtime, TensorFlow Lite…) : natives ou lourdes, hors wasm, hors
  F-Droid (binaires, poids), contraires à « dépendances libres, versions figées, APK reproductible » (`architecture.md`,
  `publication.md`).
- **Service distant ou modèle de langue** : exige la permission réseau (rouvre « sans permission réseau »), envoie les titres
  des tâches hors de l'appareil (vie privée), indisponible hors ligne, non reproductible.
- **Apprentissage entre utilisateurs** (modèle commun, fédéré) : nécessiterait un serveur ; les durées d'une personne ne sont
  pas celles d'une autre.
- **Régression seule, sur toutes les tâches** : moins explicable que les voisins et trompée par les types rares.
- **Moyenne au lieu de médiane** : sensible aux chronos oubliés (`durees-apprises.md`).

### Impacts sur les specs existantes (à reporter à l'implémentation)

- `durees-apprises.md` : « Hors périmètre » (le modèle n'y est plus différé), § « Évolution » à remplacer par un renvoi ici
  (en particulier : **aucun poids pré-entraîné ni JSON**, le modèle se calcule de l'historique local), § « Écarts avec la
  spécification initiale » (l'interface est extraite, `Source.MODEL` et l'intervalle existent), fichiers couverts et critères.
- `ordonnancement.md` § Durée de placement : `learned` inclut les durées fiables de source `MODEL`.
- `equipe-simulation.md` : « Hors périmètre » (apprentissage automatique) précisé ; § Moteur (`EffortModel` : écart par tâche,
  `modelEstimated`) ; § Honnêteté (ligne de source) ; décisions (tirages communs conservés) ; tests ajoutés.
- `equipe-charge.md` (§ Effort restant d'une tâche, `EffortSource.MODEL`) et `equipe.md` (isolation, `TeamIsolationTest`).
- `reglages.md` : carte « Durées apprises », `learning.model`, résumé de la vérification, interrupteur grisé.
- `vue-jour.md` : badge du modèle, « Pourquoi », puce et « Tâches proches » de la fiche ; `temps-reel-chrono.md` :
  `effectiveMinutes` lit aussi le modèle ; `accessibilite.md` : descriptions du badge et de la section repliable.
- `i18n.md` (nouvelles clés dans toutes les langues), `architecture.md` (paquet `core/engine/model/`, pureté, `ModelCache`
  dans `AppServices`), `modele-donnees.md` (`Settings.learning.model`, pas de migration), `export-import.md` (fichier d'or
  inchangé).
- `visite-guidee.md` et `FeatureCatalog.kt` (carte, `README_FEATURES`, `since` = 4.0.0 ; `FeatureCatalogTest`) ; `README.md` et
  `README.fr.md` (fonctionnalité visible, **dans les deux langues**) ; notes Fastlane FR et EN de la version ;
  `docs/ROADMAP.md` (#99 sort de `needs-spec`) ; `docs/spec/README.md` (index : cette spec).
