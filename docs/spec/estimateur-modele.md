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

État : **issue #99**, spécifiée le 2026-10-26, **en cours d'implémentation (4.0.0)**, en PR empilées par étape (choix du
propriétaire du 2026-11-02) : 1) interface extraite et chaîne de repli, sans changement de comportement ; 2) le modèle et ses
rétro-tests dans `core` ; 3) l'interface, les Réglages, l'estimation au tri et la mesure web ; 4) les prévisions d'équipe ;
5) le facteur jour et heure ; 6) la notification groupée et les propositions sur les estimations saisies (5 et 6 ajoutées
le 2026-11-03).
**Étape 1 livrée** : `DurationEstimator.kt` (interface et façade), `HistoryEstimators.kt` (`SeriesEstimator`,
`TitleEstimator`), `EstimatorChain.kt`, `Sample` enrichi ; test `EstimatorChainTest`. **Étape 2 livrée** : `engine/model/`
(`ModelParams`, `TextFeatures`, `ModelIndex`, `Similarity`, `RidgePrior`, `ModelGate`, `ModelCache`, `ModelEstimator`),
`Source.MODEL`, `Estimate.low`/`high`/`evidence`, `History.cache`, `LearningSettings.model` ; le rétro-test et ses tests.
**Étape 3 livrée** : le modèle est branché dans la chaîne de la façade (`DurationEstimator.chain` : série, titre, modèle),
avec la pastille, « Pourquoi », la fiche, la carte des Réglages et son état (`ModelStatusLine`, `DurationEstimator.
modelStatus`), l'estimation au tri (besoin 9), la carte du catalogue et le README ; test `ModelEstimateUiTest`. L'effort
d'une tâche d'équipe estimée par le modèle se lit encore « apprise des occurrences passées » dans la fiche d'équipe jusqu'à
l'étape 4 (`EffortSource.MODEL`). **Étape 4 livrée** : `DurationEstimator.Spread`, `spreads`, `withLearned` ;
`team/forecast/EffortSpread.kt` ; `EffortModel` et `MonteCarlo.prepare` ; `ForecastResult.modelEstimated` et sa ligne dans
`ForecastPanels` ; `EffortSource.MODEL` et l'intervalle dans `TeamTaskSheet` ; test `EffortSpreadTest`. **Étape 5 livrée**
(rythme, § Rythme ; `engine/model/Rhythm.kt`). **Étape 6 livrée** (§ Estimations qui changent ; `engine/model/EstimateChanges.kt`,
`ui/app/EstimateReview.kt`, champ `learning.notifyChanges`). Les effets sur les
autres specs sont listés en fin de document (« Impacts sur les specs existantes ») et reportés à mesure.

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
5. **Interrupteur « Estimer par machine learning »** dans la carte « Durées apprises » des Réglages. **Activé par défaut** (voir
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
9. **Estimation au tri** (ajoutée le 2026-11-02, choix du propriétaire, prolonge `vue-jour.md` § Durée dès le traitement,
   3.14.0). Quand « À traiter » (vue Jour) ou « À qualifier » (backlog d'équipe) pose une durée à une tâche qui vient d'être
   entièrement qualifiée sans durée, cette durée est d'abord la durée apprise **fiable** de la chaîne (série, titre, puis
   **modèle**), et seulement à défaut la médiane du type, puis des points (règle de 3.14.0). Le message dit la provenance
   (« Estimée à 45 min par le modèle, d'après 5 tâches proches ») et garde « Annuler » ; une durée déjà là n'est jamais
   touchée. Dans le backlog d'équipe, la chaîne ne lit que les tâches d'équipe faites, comme les médianes.
10. **Isolation.** Une tâche d'un espace n'apprend que de son espace ; une tâche reçue d'un manager n'apprend ni n'enseigne
   rien ; le mode solo ne dépend d'aucune donnée d'équipe et son fichier d'export reste identique octet pour octet.

Ajouts du 2026-11-03 (demande du propriétaire, qui rouvre trois points du hors-périmètre ; détail technique écrit à
l'implémentation de chaque étape, dans cette spec) :

11. **Rythme selon le jour et l'heure** (étape 5). Kairos apprend des séances chronométrées des tâches faites si tu vas plus
    vite ou plus lentement selon le **jour de la semaine** et la **tranche de la journée** où tu as commencé (matin,
    après-midi, soir) : un **facteur de correction** (« le lundi matin, tes tâches prennent 20 % de plus »). Il ne s'applique
    qu'à une tâche **planifiée** sur un jour (ou un créneau, pour la tranche), seulement s'il est fondé sur assez de séances
    et s'il améliore l'erreur sur ton historique (même principe que la garde), et il apparaît dans la preuve (« dont +20 %
    le lundi matin »). Une tâche sans jour ni créneau garde l'estimation sans facteur.
12. **Prévenir quand les estimations changent** (étape 6). Quand le modèle a appris (une tâche terminée, une durée
    corrigée) et que la durée appliquée de tâches **à faire** change nettement (au moins 15 min **ou** 25 %), Kairos envoie
    **une seule notification groupée** (« 3 estimations ont changé »), qui ouvre la liste avant/après ; au plus une par jour,
    désactivable dans la carte « Durées apprises ». Pas de calcul en arrière-plan, application fermée (choix du
    propriétaire) : le modèle est recalculé à chaque changement de l'historique, application ouverte, et la notification
    part à ce moment-là.
13. **Proposer de revoir les estimations saisies** (étape 6). Quand l'estimation **fiable** du modèle s'écarte nettement
    (même seuil) d'une durée **saisie à la main** sur une tâche à faire, Kairos le propose : « Passer à 45 min ? » dans la
    fiche, et « Revoir n estimations » depuis la liste de la notification (une par une ou toutes), avec « Annuler ». Rien
    n'est jamais changé sans accord : l'estimation explicite garde la priorité.

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
- **Description de la tâche, durée des sous-tâches** comme caractéristiques : écartés pour la première version (voir
  « Décisions »). Le jour de la semaine et l'heure de la journée, d'abord écartés, sont **rouverts le 2026-11-03** sous forme
  de facteur de correction (besoin 11), pas de caractéristiques de ressemblance.
- **Calcul en arrière-plan, application fermée** (Android `WorkManager`, ou autre) : écarté le 2026-11-03 par le
  propriétaire ; le modèle n'étant jamais stocké, il est déjà à jour à chaque ouverture. Les notifications de changement
  sont rouvertes (besoin 12).
- **Réécrire des estimations sans accord** : jamais. Les estimations saisies des tâches à faire peuvent être revues sur
  proposition (besoin 13) ; celles des tâches faites restent du ressort de « Corriger les estimations » (`statistiques.md`).
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
- La façade `DurationEstimator.estimate(task, samples, settings, cache = ModelCache.shared)` délègue à la chaîne ; les
  signatures de `samples`, `estimates`, `appliedMinutes`, `effectiveMinutes` et `withLearnedEstimates` sont **inchangées**
  (paramètre `cache` facultatif ajouté à celles qui calculent), donc les tests actuels compilent tels quels.
  `appliedMinutes` garde les estimations fiables, modèle compris.
- Nouveau : `DurationEstimator.spreads(estimates): Map<Long, Spread>` (les estimations **fiables** de source `MODEL` :
  `Spread(minutes, low, high)`, en minutes), et `DurationEstimator.withLearned(snapshot, now, timeZone): Learned(snapshot,
  spreads)` ;
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

- Voisins = les `NEIGHBOURS_MAX` = 5 échantillons de ressemblance la plus haute, **au moins `SIMILARITY_MIN` = 0,25**
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
(`MIN_NEIGHBOURS = min(NEIGHBOURS_MAX, max(3, learning.minSamples))`) : un type riche en historique mais dont aucun titre ne ressemble.

- Cible : `y = sqrt(minutes)` (stabilise la variance, la racine est exacte sur toutes les cibles ; pas de logarithme).
- Variables : constante ; rang des points centré et réduit ((rang − 3) / 3, 0 si absents) et indicateur « points absents » ;
  une variable 0/1 par **type** présent dans au moins 3 échantillons (les huit **plus fréquents**, égalités par nom, puis
  rangés par nom ; les autres types et l'absence de type forment la référence) ; priorité P0 et P1 (P2 et absence = référence) ;
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

- Candidats (`ModelGate.candidates`) : les `GATE_SIZE` = 30 derniers échantillons du corpus (par `taskId`) qui ont au moins
  `GATE_PREFIX_MIN` = 8 échantillons plus anciens. Chacun est estimé par le modèle **construit sur les seuls échantillons
  plus anciens** (index, idf et régression compris : aucune information future), sa durée réelle n'est jamais lue avant le
  calcul. Ne comptent que les estimations **à base suffisante** (assez de voisins, ou régression admise) : celles qui seraient
  appliquées.
- Erreur : **erreur relative symétrique** `e(p, r) = |p − r| / max(p, r)` ∈ [0 ; 1[, pour une prédiction `p` et un réel `r`
  (bornée et indifférente au sens de l'erreur, contrairement à `|p − r| / r`, qui pardonne les sous-estimations).
- La garde réussit si au moins `GATE_MIN` = 10 candidats ont pu être estimés **et** si la médiane de `e` du modèle est
  `≤ GATE_RATIO` = 0,8 × la médiane de `e` de la durée par défaut (`defaultTaskDurationMinutes`) sur les mêmes candidats.
  Un modèle qui ne fait pas nettement mieux que 30 min ne sert à rien et ne mérite pas de bouger le planning.
- `ModelGate.Result(evaluated, modelError, defaultError, passed, reason)` avec `reason` ∈ {`OK`, `TOO_FEW_SAMPLES`,
  `NOT_BETTER`} ; il est rendu dans `Evidence` et dans l'état de la carte des Réglages. Les médianes sont **basses** (la valeur
  de rang ⌈n/2⌉, `ModelGate.lowMedian`) ; `ModelGate.fromErrors` décide à partir des erreurs déjà mesurées (le rétro-test
  s'en sert).

Une estimation de source `MODEL` non fiable (garde échouée, corpus trop court, trop peu de voisins) est **exposée** avec
`reliable = false` quand elle existe : affichée « pas encore fiable », jamais appliquée.

**Paramètres** : toutes les constantes vivent dans `ModelParams` (valeurs de départ ci-dessus, arbitrées par le rétro-test) :
`MAX_CORPUS` 500, `MIN_CORPUS` 20, `NEIGHBOURS_MAX` 5, `SIMILARITY_MIN` 0,25, `MIN_NEIGHBOURS` min(5, max(3, `minSamples`)),
poids 0,5 / 0,2 / 0,2 / 0,1, quantiles 0,10 et 0,90, `NEIGHBOUR_WIDEN` 2 (élargissement `1 + 2/k`), `RIDGE_LAMBDA` 1,0,
`RIDGE_WIDEN` 1,25, `RIDGE_TYPE_MIN` 3, `RIDGE_MAX_TYPES` 8, `GATE_SIZE` 30, `GATE_PREFIX_MIN` 8, `GATE_MIN` 10,
`GATE_RATIO` 0,8, facteur d'aberrant 3,0 (partagé avec `HistoryEstimators`). Ce ne sont **pas** des réglages utilisateur
(un seul interrupteur : § Réglage). `learning.history` ne concerne que séries et titres ; `learning.minSamples` fixe aussi
le nombre minimal de voisins, avec un plancher à 3 et un **plafond à `NEIGHBOURS_MAX`** (relecture du 2026-11-03 : sans
plafond, `minSamples` ≥ 6, permis jusqu'à `learning.history`, rendait toute base de voisins insuffisante et éteignait le
modèle sans le dire ; `RobustnessTest.manyRequiredSamplesNeverSilenceTheModel`).

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
  chaque fois. `ModelCache` (`engine/model/ModelCache.kt`) est une **mémoïsation explicite à une case par espace** (la vue
  Jour estime des tâches perso et des tâches d'équipe qui me sont assignées : une seule case se viderait à chaque alternance),
  passée en paramètre (`DurationEstimator.History.cache`) ; elle garde `ModelEstimator.State` (index, régression, garde).
  Clé = `ModelIndex.fingerprint(corpus)`, un FNV-1a 64 écrit dans `ModelIndex` (jamais `String.hashCode()` ; pas celui de
  `ForecastRandom`, qui vit dans `core/team`) sur (`taskId`, minutes, type, points, priorité, étiquette, récurrence, titre)
  des échantillons retenus, plus la taille du corpus et les réglages que lit la garde (durée par défaut, fenêtre de travail,
  `minSamples`) : modèle et garde sont recalculés ensemble quand l'un change (rare ; plus simple que deux clés).
  `ModelCache.NONE` ne mémorise rien (tests, prévisions), `ModelCache.memo()` crée un cache. La façade utilise par défaut **`ModelCache.shared`**, un cache partagé du processus : vue Jour et Semaine
  (`DayView.build`), rappels, widgets, chrono et calculs d'équipe en profitent sans changer de signature (écart à la spec
  d'origine, qui le plaçait dans `AppServices` et le passait à `DayView.build` : le cache ne change aucun résultat, testé,
  et un seul point évite de l'oublier chez un appelant).
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
  points × bruit triangulaire 0,7-1,5), `NOISE` (durées indépendantes de toute caractéristique, **uniformes entre 15 et
  45 min, centrées sur la durée par défaut**), `SPARSE` (12 tâches faites). Fichiers `SimulatedHistory.kt` et `Backtest.kt`
  (`core/src/commonTest/.../engine/model/`).
- **Découpage temporel (walk-forward)** : les 30 % premiers échantillons servent de départ ; pour chaque suivant `i`, on estime
  avec `samples[0 until i]` seuls, **sans** sa durée, puis on mesure l'erreur. Jamais de découpage aléatoire (il laisserait
  fuir l'avenir) ni de validation croisée.
- **Comparaisons** sur les mêmes tâches testées : durée par défaut ; médiane par titre (`TitleEstimator`, sinon défaut) ;
  chaîne sans modèle ; chaîne complète ; modèle seul. Métrique : médiane de `e(p, r)` (voir la garde), couverture de
  l'intervalle `[low ; high]`.
- **Raccourci du rétro-test** : l'estimation du modèle pour `i` est celle du modèle ajusté à `samples[0 until i]`, exactement
  ce que la garde calcule pour chaque candidat ; `Backtest` la calcule donc une fois par échantillon et tire la garde de chaque
  pas de la même fenêtre (`ModelGate.fromErrors`). `BacktestConsistencyTest` vérifie que ce raccourci rend la même durée
  appliquée et la même garde que la vraie chaîne (`EstimatorChain(série, titre, modèle)`, `ModelGate.evaluate`).
- **`NoFutureInfoTest`** (propriété) : pour un candidat `j` de la garde, remplacer arbitrairement les durées et les titres
  des échantillons d'indice > `j`, ou la durée de `j` lui-même, laisse son estimation strictement égale.
- Les seuils sont ceux des critères de succès. Graines 1 à 20 en dur. Les valeurs attendues en dur de
  `ModelReproducibilityTest` couvrent l'index (idf), une ressemblance, une médiane pondérée, la régression, un intervalle et
  l'empreinte. Elles sont vérifiées sur la JVM, et **sur wasm par la CI** (étape « Tests du modèle (wasm, Chrome) » de
  `kmp.yml` : `:core:wasmJsBrowserTest` limité à `ModelReproducibilityTest`, `TextFeaturesTest`, `ModelEstimatorTest`,
  `NoFutureInfoTest` et `ModelPerformanceTest`, Chrome sans tête par Karma, `core/build.gradle.kts`), qui mesure du même coup
  le budget de 1 s sur la cible web. L'environnement de développement ne peut ni lancer ces tests ni compiler la version web
  (le proxy y refuse le téléchargement de Karma, `codeload.github.com`) : la mesure manuelle dans Chromium prévue plus haut
  est remplacée par ce passage en CI, plus sûr car refait à chaque PR.
- **Résultats mesurés** (2026-11-03, 20 graines par profil, réglages ci-dessus) : `NOVEL`, erreur médiane de la chaîne de
  0,30 à 0,46 × celle de la chaîne sans modèle sur les 20 graines (seuil 0,8), modèle fiable sur au moins 137 tâches testées
  sur 140 ; `RECURRING`, le modèle égale la médiane par titre sur chaque graine (rapport 1,00) ; `NOISE`, la garde coupe le
  modèle sur 19 graines sur 20 (seuil 18), chaîne au plus 1,07 × la durée par défaut (seuil 1,1) ; `SPARSE`, jamais
  appliqué ; couverture de l'intervalle 87 % sur `NOVEL` (2 800 tâches, cible 70-90 %).

### Prévisions : de l'intervalle à une loi d'effort

Aujourd'hui (`equipe-simulation.md`), `EffortModel` tire, par tirage et par tâche, un **facteur d'erreur** réel / base dans
l'historique de l'équipe (borné [0,2 ; 5]) et le multiplie par l'effort du plan. Une tâche estimée par le modèle a mieux : sa
propre dispersion, apprise de tâches qui lui ressemblent. On la substitue au facteur global pour cette tâche seule.

- **Transport** : `withLearned` rend `Learned(snapshot, spreads)` ; le snapshot porte la durée du modèle comme
  `estimatedMinutes` (comme les durées apprises), et `spreads` (`taskId → Spread(minutes, low, high)`) l'intervalle ;
  `EffortSpread.ratios` en tire `l` et `h`, bornés à [0,2 ; 1] et [1 ; 5]. Une durée apprise ne dépasse jamais la fenêtre de
  travail (plafond commun à toutes les durées apprises) : l'intervalle non plus. `MonteCarlo.prepare`
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

### Rythme selon le jour et l'heure (étape 5, besoin 11)

- **Ce qui est mesuré.** Pour chaque échantillon du corpus qui a au moins une séance chronométrée, le **jour de la
  semaine** et la **tranche** (matin avant 12 h, après-midi de 12 h à 17 h, soir à partir de 17 h) de sa **première
  séance**, à l'heure locale (`Sample.weekday`, jour ISO 1 = lundi, et `Sample.band`, remplis par `samples(tâches, temps,
  firstStarts)` où `DurationEstimator.firstStarts(séances, fuseau)` donne l'heure locale de la première séance de chaque
  tâche ; une tâche au temps seulement saisi à la main n'en a pas et ne compte pas pour le rythme). Son **rapport**
  `r = réel / attendu`, où `attendu` est l'estimation du modèle par les voisins **sans cet échantillon** (laisser-un-dehors
  sur l'index complet : l'idf le compte encore, écart négligeable et accepté ; la garde, elle, reste stricte sur
  l'avenir) ; un échantillon sans estimation à base suffisante ne compte pas.
- **Le facteur.** Par jour (7) et par tranche (3), séparément : la médiane basse des rapports du groupe, bornée à
  [0,5 ; 2], retenue seulement si le groupe compte au moins `MIN_RATIOS` = 8 rapports, si elle s'écarte d'au moins 10 %
  de 1 (`|f − 1| ≥ 0,1`) et si, **sur ce groupe**, l'erreur relative symétrique médiane de `attendu × f` est au plus
  0,95 × celle de `attendu` (même principe que la garde : un facteur qui n'aide pas ne s'applique pas). Calculé une fois
  par corpus avec le modèle (`Rhythm.learn`, `ModelEstimator.State.rhythm`, même cache : l'empreinte couvre le jour et la
  tranche), et seulement si un échantillon a un jour. Le laisser-un-dehors coûte un passage par échantillon :
  `ModelPerformanceTest` le compte dans son budget (500 tâches, toutes avec jour et tranche). Constantes dans `Rhythm`
  (`MIN_RATIOS` 8, `MIN_EFFECT` 0,1, bornes 0,5 et 2, `MIN_GAIN` 0,95).
- **Application.** À une estimation **fiable du modèle** seulement (les séries et titres gardent leur médiane) : le facteur
  du jour si la tâche est planifiée (`scheduledDate`, ou la date de `pinnedStart`), celui de la tranche si elle a une heure
  fixe (`pinnedStart`) ; les deux se multiplient (deux marges estimées séparément, approximation assumée : un tableau
  jour × tranche serait trop clairsemé). La durée, `low` et `high` sont multipliés, puis arrondis et bornés comme avant.
  Une tâche sans jour ni heure garde l'estimation sans facteur.
- **Preuve.** `Evidence.rhythm` (`Rhythm.Applied(weekday, weekdayFactor, band, bandFactor)`, facteurs nuls quand non
  appliqués, `null` sans facteur) ; « Pourquoi » et la fiche ajoutent « Ton rythme : +20 % le lundi, −15 % le matin. »
  (`rhythmText`, `ModelTexts.kt` ; textes `why_model_rhythm`, `model_rhythm_*`, nom du jour par `Dates.weekdayName`).
- **Fuseau.** `core` reste pur : le fuseau est un paramètre facultatif de la façade (`estimates`, `effectiveMinutes`,
  `withLearned`, `withLearnedEstimates`, `modelStatus`). Le passent : `DayView.build` (Jour, Semaine, rappels, widgets),
  `MonteCarlo.prepare`, `TeamLoad`, `AssignmentSuggestion` (leur fuseau), et, côté interface, `ChronoWatcher`,
  `ChronoSync` (Android), la fiche d'équipe et l'estimation au tri (fuseau du système). Sans fuseau (`null`), pas de
  rythme : les échantillons n'ont ni jour ni tranche.
- **Tests** : `RhythmTest` (un historique simulé où le lundi prend 30 % de plus : facteur trouvé sur au moins 8 graines sur
  10, entre 1,1 et 1,5, appliqué à une tâche planifiée un lundi, pas un mardi ni sans date ; sans effet de jour : au plus
  un faux facteur sur dix graines ; groupes trop petits ou sans séance : aucun facteur ; jour et tranche de la première
  séance à l'heure locale). Mesuré : le facteur est **sous-estimé, jamais surestimé** (1,11 à 1,3 pour un vrai 1,3), car
  les voisins d'une tâche du lundi sont souvent des lundis ; écart prudent, accepté.

### Estimations qui changent et estimations saisies à revoir (étape 6, besoins 12 et 13)

- **Écart net** (`EstimateChanges.significant(avant, après)`, `core/engine/model/EstimateChanges.kt`, pur) : au moins
  `MIN_MINUTES` = 15 min **ou** `MIN_SHARE` = 25 % de l'ancienne valeur. Une seule règle pour les deux besoins.
- **Changements** (`EstimateChanges.diff(avant, après)`) : les tâches présentes dans les deux relevés dont la durée
  appliquée a changé nettement, triées par `taskId`. Le relevé est `DurationEstimator.appliedMinutes(estimates(…))` de
  l'instantané **perso** (les tâches à faire sans estimation saisie).
- **Propositions** (`EstimateChanges.proposals(instantané, maintenant, fuseau)`) : les tâches **perso** à faire **avec** une
  durée saisie (jamais une tâche d'équipe : sa durée est celle du manager, et une donnée d'équipe n'a pas à paraître dans
  l'espace Perso ; la carte des Réglages et la revue lisent l'instantané perso) dont la durée apprise **fiable du modèle** (chaîne interrogée sur la tâche sans sa durée saisie) s'en écarte
  nettement ; la série et le titre ne proposent rien (leur médiane ne s'applique déjà qu'aux tâches sans durée, on ne les
  oppose pas à une durée saisie). Triées par écart décroissant, puis `taskId`.
- **Veille** (`EstimateWatcher`, `ui/app/`, dans la coquille comme `ReminderWatcher`) : au premier relevé de la session, il
  prend la **référence** sans rien dire (ce qui a changé application fermée n'est pas annoncé : pas de calcul en
  arrière-plan, choix du propriétaire) ; à chaque changement de l'instantané perso, il compare le relevé à la référence,
  **seulement si l'historique a bougé** (`EstimateChanges.historyKey`, l'empreinte des tâches faites et de leur temps) :
  retoucher une tâche à faire ou un réglage change aussi des durées, mais ce n'est pas « le modèle a appris », et la
  référence suit sans rien dire ; des changements nets → le bandeau et, si le système ne s'en charge pas (`ChronoNotifier.systemHandlesAlerts` faux), une
  notification « n estimations ont changé » (`tag` `estimates`) ; **au plus une par jour et par session** (garde en
  mémoire : la référence repart du relevé du lancement, rien n'est stocké) ; puis la référence devient le relevé. Un
  second changement le même jour n'est donc **pas** annoncé (la référence avance quand même) : voulu, une annonce par jour
  suffit. Le bandeau ne compte que les tâches encore à faire (`EstimateChanges.stillOpen`) et disparaît s'il n'en reste
  aucune.
  Réglage `learning.notifyChanges` (`Boolean?`, nul = actif, seul `false` s'écrit), dans la carte « Durées apprises »,
  affiché avec les durées apprises (`SHOWN_WITH`, suivi de proche en proche) et **non** sous le modèle : la veille annonce
  aussi les durées des séries et des titres, il doit rester coupable modèle coupé ; coupé, ni bandeau ni notification
  (`EstimateChanges.applied` rend un relevé vide).
- **Bandeau** (`EstimateChangesBanner`, neutre comme celui des mises à jour, sous lui) : « 3 estimations ont changé »,
  « Voir » ouvre la revue, « Plus tard » le ferme.
- **Revue** (`EstimateReviewDialog`) : « Ce qui a changé » (titre, avant → après) et « Estimations saisies à revoir »
  (titre, saisie → modèle avec son intervalle, « Passer à 45 min » par ligne, « Tout appliquer ») ; chaque application
  écrit par `KairosRepository.setEstimate` et propose « Annuler » (`LocalUndoMessages`), qui remet la durée saisie, **sauf**
  sur une tâche retouchée depuis (sa durée n'est plus celle du modèle : l'annulation ne l'écrase pas). Les écritures et
  l'annulation passent par la **portée de la coquille** (`LocalShellScope`, `ui/app/Messages.kt`), jamais celle du dialogue
  ni de l'écran : « Annuler » vit dans la snackbar de la coquille et peut servir après la fermeture du dialogue (un clic
  hors d'un dialogue modal le ferme) ou de l'écran des Réglages ; ces portées l'auraient perdue (bug trouvé par
  `EstimateReviewUiTest`, puis par la seconde relecture pour les Réglages).
  Ouverte aussi depuis la carte « Durées apprises » (« Revoir les estimations saisies (n) », affiché s'il y en a).
- **Fiche** : une durée saisie dont le modèle s'écarte nettement montre « Le modèle estime 45 min (entre 30 et 60) » et
  la puce « Passer à 45 min », qui remplit le champ **sur action** (rien n'est changé sans accord).
- **Tests** : `EstimateChangesTest` (seuil, diff, propositions, la série ne propose rien), `EstimateReviewUiTest`
  (bandeau après une tâche terminée, revue, application et annulation, puce de la fiche, réglage).

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
- `ui/day/EditTaskDialog.kt` : sous le champ des minutes, tant qu'il est vide : l'intitulé « Estimation par machine learning »
  (`sheet_model_title`, ajouté le 2026-11-03 : la fiche disait la durée sans dire d'où elle venait), puis la puce « Utiliser
  45 min (entre 30 et 60) » qui
  remplit le champ **sur action** (règle « on ne remplit que ce qui est vide »), et une section repliable « Tâches proches »
  qui liste chaque voisin (titre, durée réelle, ressemblance en %), bascule de 48 dp. Pour une base de régression : la phrase
  « d'après tes 34 tâches de type « Rédaction » à 5 points ». Si le modèle n'est pas fiable : une phrase, sans action (elle
  nomme déjà le machine learning, sans intitulé). La fiche ainsi ouverte est la capture 2 des magasins (`publication.md`
  § Captures), dont « Tâches proches » est dépliée d'emblée par `LocalShowcaseTask` ; ailleurs elle attend le toucher.
- `ui/team/TeamTaskSheet.kt` : « …, estimée par le modèle (30 à 60 min) » via `EffortSource.MODEL`
  (`core/team/Effort.kt`, à côté de `LEARNED`).
- `ui/team/forecast/ForecastPanels.kt` : « dont n tâches estimées par le modèle » sous la source des données.
- `ui/settings/SettingsFormCards.kt` : dans la carte « Durées apprises » (catégorie « Planification »), interrupteur
  « Estimer par machine learning » avec son aide (« Machine learning sur l’appareil : compare le titre, le type et les points
  d'une tâche à tes tâches déjà faites. Rien ne quitte cet appareil. ») et, dessous, **l'état de la vérification** : « Sur tes 30 dernières tâches : erreur médiane 25 %,
  contre 48 % pour la durée par défaut » / « Pas encore assez de tâches chronométrées (12 sur 20) » / « Ne fait pas mieux que la
  durée par défaut : non appliqué ». Quand les durées apprises sont coupées, l'interrupteur et son état **disparaissent**
  (règle `SHOWN_WITH` des Réglages, comme l'heure du récapitulatif), plutôt que d'être grisés : écart décidé à
  l'implémentation, une seule règle pour tous les champs qui dépendent d'un interrupteur. L'état (`ModelStatusLine`, lu par
  `DurationEstimator.modelStatus` sur les données enregistrées de l'espace Perso) n'apparaît qu'avec l'interrupteur actif.
- Les phrases de provenance sont dans `ui/day/ModelTexts.kt` (`modelProvenance`, `regressionBasis`, `ModelSuggestion`),
  partagées par « Pourquoi » et la fiche ; la pastille choisit son icône (`KairosIcons.Model`, symbole `model_training`)
  et sa description selon la source et la base.
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

**Vocabulaire « machine learning » (demande du propriétaire du 2026-11-03)** : la fonctionnalité se présente comme du
**machine learning sur l'appareil**, plus parlant et plus accrocheur que « modèle ». Partout où l'utilisateur lit « le
modèle » au sens de cet estimateur, il lit « machine learning » : interrupteur (« Estimer par machine learning »), aide,
pastille, « Pourquoi ? », remplissage au tri, fiche et prévisions d'équipe, revue des estimations, carte du catalogue
(« Estimation par machine learning » / « Machine-learning estimates », clé `model` et `README_FEATURES` inchangées). Mot
retenu par langue : « machine learning » en français, en anglais et en italien (l'anglicisme y est courant), « maschinelles
Lernen » en allemand, « aprendizaje automático » en espagnol, « машинное обучение » en russe. Il reste « modèle de machine
learning » là où la phrase parle de l'objet vérifié (« vérifier le modèle de machine learning »). **Hors de ce renommage** :
le « Modèle » des prévisions d'équipe (« Par effort » / « Par débit », `forecast_model*`), qui est un autre concept, les noms
de code (`ModelEstimator`, `learning.model`, clés `*_model*`) et les commentaires. Le terme est juste : voisins pondérés et
régression ridge, entraînés sur l'historique et validés hors échantillon (§ Le modèle, § Fiabilité). « IA » est écarté : il
évoque un modèle génératif ou en ligne, à rebours de « pas de boîte noire, rien ne quitte l'appareil ». Mise en avant
(choix du même jour) : **dans l'accroche** du README (FR, EN), du héros et de la description de la page (six langues), de la
première phrase de la fiche Fastlane et de sa description courte, en plus de la section dédiée, de la première puce « En
bref » et du premier point de la page (`publication.md`). La description courte des fiches cède « sans compte ni réseau »
au machine learning (80 caractères au plus) ; l'absence de compte reste dans la description longue.

**Mention « Une IA ? Non, du machine learning » (demande du 2026-11-03, ton volontairement complice)** : la vitrine dit,
pour la blague et pour mettre le machine learning en avant, qu'il n'y a **pas d'IA** dans Kairos (ni chatbot, ni modèle
géant, ni cloud), seulement du machine learning robuste, local, qui **s'entraîne** à chaque tâche terminée, léger et
rapide. Formulation revue le même jour : « Garanti sans IA », d'abord retenu, était trop affirmatif pour un clin d'œil ;
« s'entraîne » remplace « s'affine », plus juste pour du machine learning (le modèle est recalculé sur l'historique à
chaque tâche faite). Le paragraphe du site reste juste sous la carte « Nouveau en 4.0 », en tête de « Ce que fait
Kairos ». Où : un encadré dans la section dédiée du README (FR, EN), un paragraphe sous la
carte « Nouveau en 4.0 » de la page (six langues), une phrase au bout du paragraphe « Nouveau en 4.0 » des fiches Fastlane
et au bout de la carte du catalogue (six langues). Les chiffres du README sont ceux que les tests vérifient à chaque build :
corpus borné aux 500 tâches faites les plus récentes (`ModelParams.MAX_CORPUS`), 200 estimations sur 500 tâches en moins
d'une seconde (`ModelPerformanceTest`, JVM et wasm en CI). Ailleurs, « léger et très rapide » sans chiffre. « Ton téléphone
ne chauffera pas » est une image, pas une mesure : Android n'est pas chronométré.

**Passe de robustesse du 2026-11-03** (trois relectures indépendantes, en lecture seule : le moteur, l'interface, puis
l'étape 6 et les corrections ; tests `RobustnessTest`, `EstimateChangesTest`, `EstimateReviewUiTest`) :

- **Plafond de `MIN_NEIGHBOURS`** à `NEIGHBOURS_MAX` (§ Paramètres) : sinon un `minSamples` élevé éteignait le modèle.
- **Types de la régression par fréquence** : par ordre alphabétique, un type lourd en fin d'alphabet perdait sa variable
  au profit de huit types rares (`theMostFrequentTypesGetARegressionVariable`).
- **Corpus et empreinte une fois par calcul** : `ModelCache.prepare` mémorise par **identité** de la liste d'échantillons,
  et la façade passe la même liste pour toutes les tâches d'un espace (`estimates` regroupe par espace ; la chaîne garde la
  liste telle quelle si elle est déjà réduite). Avant, chaque tâche triait le corpus et recalculait son empreinte : 200
  tâches × 500 échantillons à chaque minute de la vue Jour (`theFacadeShareOneCorpusPerSpaceForAllTasks`). Les listes du
  cache sont `@Volatile` (publication sûre entre la vue Jour en arrière-plan, les Réglages et les prévisions ; une course
  ne coûte qu'une reconstruction).
- **Accents décomposés** (NFD, U+0300 à U+036F) ignorés dans le mot au lieu de le couper (« re\u0301diger » = « rédiger »).
- **La garde trie elle-même** son corpus : un préfixe n'est le passé que dans l'ordre des `taskId`, même si un appelant
  futur oublie `corpus()`.
- **Rythme** : le produit jour × tranche est borné comme chaque facteur ([0,5 ; 2]) ; la validation d'un facteur arrondit
  et borne comme la production (multiple de 5, fenêtre de travail). Le laisser-un-dehors ne retire la tâche que des voisins
  (l'idf et la régression la comptent encore) : écart accepté, la garde reste, elle, stricte sur l'avenir.
- **« Tâches proches »** : une tâche du même type passe le seuil de ressemblance à elle seule (0,2 / 0,7 ≈ 0,29 ≥ 0,25 sans
  points ni étiquette) ; c'est voulu (le type est une ressemblance réelle), et la fiche donne le pour cent de chacune.
- **Interface** : le rythme est dit aussi dans la fiche pour une base de voisins ; « pas encore assez de tâches
  comparables pour vérifier le modèle » quand la garde manque de candidats (et non « pas assez de tâches faites ») ; « ne
  fait pas **nettement** mieux » (la garde exige 0,8 ×) ; « … » après trois tâches proches nommées ; « points : 1 » plutôt
  que « 1 points » ; l'état de la vérification dans les Réglages suit les valeurs **enregistrées** (il apparaît après
  « Enregistrer » quand on vient d'activer le modèle) et passe le fuseau, comme la vue Jour, pour partager sa case de cache.

Décidées le 2026-10-26 à la rédaction de la spec ; **l'activation par défaut sous garde est confirmée par le propriétaire le
2026-11-02**, avec le périmètre de la première version : prévisions d'équipe, estimation au tri et mesure web comprises.

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
- **Garde ≤ 0,8 × l'erreur de la durée par défaut** : à égalité le modèle ne justifie pas de déplacer le planning ; la marge
  évite une bascule entre deux valeurs au gré d'une tâche de plus. **Rouvert au premier rétro-test (2026-11-03)** : à 0,9, la
  garde ne coupait le modèle que sur 14 graines sur 20 de `NOISE` (critère : 18) ; 0,85 en donnait 18, 0,8 en donne 19, et
  `NOVEL` reste appliqué à 98 % : 0,8 retenu, pour une marge d'une graine. Fenêtre de 50 candidats essayée, écartée (13 à 17
  graines sur 20).
- **5 voisins au plus, et non 9** (rétro-test du 2026-11-03) : à 9, le modèle seul dépassait la médiane par titre de 15 % sur
  deux graines de `RECURRING` (critère : 10 %), car il mélangeait des titres proches aux occurrences du même titre ; à 5, il
  l'égale sur chaque graine, et `NOVEL` passe d'au plus 0,44 à au plus 0,46 (seuil 0,8).
- **Profil `NOISE` centré sur la durée par défaut** (2026-11-03) : avec des durées de 10 à 120 min, le modèle battait les
  30 min par défaut en apprenant le seul niveau moyen de l'utilisateur, une amélioration réelle que la garde n'a pas à couper ;
  le critère vise le cas où il n'y a rien à apprendre au-delà de la durée par défaut, d'où des durées de 15 à 45 min. Erreur relative symétrique plutôt que `|p − r| / r`,
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
