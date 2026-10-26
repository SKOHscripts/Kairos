# Durées apprises des tâches récurrentes

_Rôle : une tâche sans estimation explicite récupère une durée à partir de ses
réalisations passées, pour mieux remplir la journée. Fichiers couverts :
`kmp/core/.../engine/DurationEstimator.kt`, `engine/Scheduling.kt`
(`durationMinutes`, `buildDaySchedule`), `engine/Recurrence.kt`
(`nextOccurrence`, `calendarOccurrences`), `day/DayView.kt`, `model/Task.kt`
(`seriesId`), `model/LearningSettings.kt`, `model/Settings.kt` (`learning`),
`settings/SettingsForm.kt`, `team/Effort.kt`, `team/TeamLoad.kt`,
`team/AssignmentSuggestion.kt`, `team/forecast/MonteCarlo.kt`, `data/`
(`7.sqm`, `Kairos.sq`, `KairosStore`, `Mappers`, `ExportCodec`,
`KairosRepository`), `ui/day/TaskRow.kt`, `Why.kt`, `NowCard.kt`,
`EditTaskDialog.kt`, `DayScreen.kt`, `ui/chrono/ChronoWatcher.kt`,
`androidApp/.../ChronoSync.kt`, `ui/settings/SettingsFormCards.kt`,
`ui/team/TeamTaskSheet.kt`. Tests : `DurationEstimatorTest`,
`LearnedTeamLoadTest`, `LearnedDurationsRepositoryTest`,
`LearnedDurationsUiTest`, `ExchangeMigrationTest`, `TeamIsolationTest`._

État : **issue #64 (suggestion 3, prolongement)**, spécifiée le 2026-10-13,
**implémentée en 3.4.0** (niveaux série et titre ; voir « Écarts avec la
spécification initiale »).

## 1. Besoin métier (cahier des charges)

### Objectif / problème

Une tâche qui revient (« relever le courrier », « point hebdomadaire »,
« rapport du lundi ») prend à peu près le même temps à chaque fois, et Kairos
le sait : le chrono et le temps manuel enregistrent le temps passé. Pourtant
l'estimation reste à saisir à la main, ou retombe sur la durée par défaut, ce qui
fausse le remplissage de la journée. L'idée : **apprendre des réalisations
passées** pour affiner les estimations et le calendrier des tâches qui se
ressemblent.

À long terme (une future version **majeure**), le propriétaire veut un **modèle
de machine learning** pour prédire les temps. Cette spec prépare cette évolution
sans la réaliser.

### Comportement attendu (utilisateur)

1. **Pour une tâche sans estimation explicite**, Kairos utilise une durée
   apprise quand il en connaît une **fiable** : celle de la série (« les 5
   dernières fois, médiane 45 min »). Sans durée fiable, rien ne change (durée
   par défaut des réglages).
2. **Une estimation explicite l'emporte toujours** (minutes saisies, ou points
   calibrés quand l'utilisateur les a choisis). La durée apprise n'est jamais
   écrite dans la tâche : elle est calculée, donc toujours cohérente avec
   l'historique.
3. **Repli** : à défaut de série fiable, Kairos descend au **même titre**
   (sans tenir compte de la casse ni des espaces autour), puis à la durée par
   défaut des réglages. (Les calibrages par type et par points qui existent
   déjà dans l'espace Équipe restent à leur place ; ils ne sont pas des
   niveaux de la durée apprise.)
4. **Provenance toujours visible** : un badge `~45 min` avec icône sur la ligne
   de la tâche (description pour le lecteur d'écran : « Estimée d'après tes N
   dernières occurrences chronométrées (médiane) »), la même durée sur la carte
   « Maintenant », une ligne dans « Pourquoi à cette place ? » et, sous le
   seuil, « pas encore apprise (n occurrence(s), m nécessaires) » **sans
   l'appliquer** (dite dans « Pourquoi à cette place ? » et dans la fiche
   d'édition, jamais en badge). Jamais la couleur seule.
5. **Suggestion à l'édition** : dans la fiche, une puce « Utiliser 45 min »
   remplit le champ de minutes **sur action** de l'utilisateur seulement
   (règle déjà en vigueur : on ne remplit qu'un champ vide).
6. **Portée** (choix du propriétaire) : la durée apprise sert au **placement**
   dans la journée, à l'**alerte de dépassement** du chrono et aux
   **projections d'équipe** (charge « moi », occurrences projetées).
7. **Par défaut, actif** pour toute tâche sans estimation explicite
   (choix du propriétaire). Un réglage permet de le couper, de fixer la taille
   de l'historique et le seuil minimal.
8. **Isolation** : les tâches reçues d'une équipe n'entrent jamais dans une
   série personnelle ; une tâche d'un espace n'apprend que de son espace.

### Critères de succès

- Sans historique chronométré, rien ne change : `DifferentialTest` (480
  scénarios, aucun historique) passe inchangé, ainsi que
  `DurationEstimatorTest.theSchedulerAppliesALearnedDurationOnlyWithoutAnExplicitOne`
  pour le placement.
- Une durée explicite n'est jamais écrasée (`DurationEstimatorTest`).
- Limites : n = 2 non appliquée / n = 3 appliquée ; écrêtage des aberrants
  (> 3 × la médiane) ; arrondi à 5 min au demi-supérieur ; bornes 5 min à la
  fenêtre de travail ; séances à 0 minute ignorées (`DurationEstimatorTest`).
- Invariance à l'ordre : mélanger l'historique donne le même résultat
  (`DurationEstimatorTest`).
- Isolation d'espace : une tâche reçue n'alimente pas une série personnelle
  (`DurationEstimatorTest`).
- Série renommée : repli sur le titre, et inversement une série survit à un
  renommage (`DurationEstimatorTest`).
- Export : le fichier d'or est inchangé et `seriesId` est absent du JSON solo
  tant qu'il est nul (`TeamIsolationTest.soloExportIsByteIdentical`) ;
  migration et marqueur (`ExchangeMigrationTest`).
- Alerte de dépassement, carte « Maintenant » et projection d'équipe lisent
  les mêmes durées apprises : `DurationEstimator.effectiveMinutes` et
  `withLearnedEstimates` (`DurationEstimatorTest`, `LearnedTeamLoadTest`).
- À l'écran : pastille, puce de suggestion de la fiche, réglage
  (`LearnedDurationsUiTest`).

### Hors périmètre / différé

- **Le modèle d'apprentissage automatique** (version majeure à venir) : voir
  « Évolution ».
- Apprentissage entre appareils ou entre membres d'une équipe.
- Modifier rétroactivement l'estimation des tâches terminées **de façon automatique** : l'action explicite « Corriger
  les estimations… » des statistiques (3.8.0, issue #101, `statistiques.md`) le fait à la demande, après aperçu ; elle
  ne change rien à la durée apprise, calculée sur le temps réel et non sur l'estimation.

## 2. Solution technique

### Port pur dans `core`

`object DurationEstimator` (`core/engine/DurationEstimator.kt`) : pur, ni
horloge ni accès aux données (l'instant et l'historique sont des paramètres,
`architecture.md`).

```kotlin
enum class Source { SERIES, TITLE }
data class Estimate(val minutes: Int, val source: Source, val samples: Int, val reliable: Boolean)
data class Sample(val taskId: Long, val seriesId: Long?, val title: String, val space: TaskSpace, val minutes: Int)
fun samples(tasks, spentByTask): List<Sample>
fun estimate(task, samples, settings): Estimate?
fun estimates(snapshot, now): Map<Long, Estimate>          // tâches à faire sans estimation
fun appliedMinutes(estimates): Map<Long, Int>              // les fiables seulement
fun effectiveMinutes(task, snapshot, now): Int?            // explicite, sinon apprise fiable, sinon null
fun withLearnedEstimates(snapshot, now): KairosSnapshot    // lecture des calculs d'équipe
```

Les échantillons sont les tâches **terminées** (`origin == null`) dont le temps
passé (`TimeTracking.spentMinutesByTask` : chrono et saisie manuelle) est d'au
moins `MIN_SAMPLE_MINUTES` = 2 minutes.

### Choix de calcul

- **Hiérarchie de repli** (la première **fiable** gagne ; à défaut la première
  qui a des occurrences, « peu fiable ») : série (`sample.seriesId == task.seriesId`
  ou `sample.taskId == task.seriesId`, la première tâche de la série n'ayant
  pas de `seriesId` propre) → même titre (insensible à la casse et aux espaces
  autour). Seuls les échantillons du **même espace** (Perso / Équipe) comptent,
  hors la tâche elle-même.
- **Statistique** : médiane des `history` (5 par défaut) derniers échantillons
  par `taskId` décroissant (**jamais `updatedAt`**, modifiable après coup),
  `TaskStats.median` réutilisée. Fiable si au moins `minSamples` (3) échantillons
  **retenus** ; en dessous, l'estimation est exposée avec `reliable = false` et
  n'est jamais appliquée.
- **Aberrants** : parmi les derniers, ceux au-delà de 3 × la médiane (chrono
  oublié) sont écartés avant la médiane finale ; le compte `samples` est celui
  des retenus.
- **Arrondi** : au multiple de 5 min le plus proche, la moitié vers le haut
  (`floor(x / 5 + 0,5)`, arithmétique propre, **pas** `pyRound` demi-pair) ;
  borné de 5 min à la fenêtre de travail (`workdayEndHour - workdayStartHour`).

### Point d'injection unique

`Scheduling.durationMinutes(task, settings, learned)` est l'unique point de
calcul de la durée d'une tâche : estimation explicite (≠ 0), sinon
`learned[task.id]`, sinon la durée par défaut. `buildDaySchedule` reçoit la
table `learned: Map<Long, Int>` (vide par défaut = code d'avant, octet pour
octet) et la passe à chaque calcul de durée. `DayView` la construit
(`DurationEstimator.estimates` puis `appliedMinutes`) et expose aussi les
estimations complètes (`DayView.learned`) pour l'interface.

Les autres consommateurs :

- **Alerte de dépassement** : `ChronoWatcher` (et `ChronoSync` sur Android)
  appellent `DurationEstimator.effectiveMinutes` à la place de
  `task.estimatedMinutes` ; `ChronoAlerts` n'est pas modifié.
- **Équipe** : `TeamLoad.build`, `AssignmentSuggestion.suggest` et
  `MonteCarlo.prepare` lisent d'abord `withLearnedEstimates(snapshot, now)`, qui
  copie la durée apprise fiable dans `estimatedMinutes` des tâches **à faire**
  sans estimation. Ainsi charge, répartition, prévisions et projection des
  occurrences la lisent sans autre changement ; les tâches faites ne sont
  jamais touchées (ni calibrage, ni erreurs d'estimation passées). La fiche
  d'une tâche d'équipe (`TeamTaskSheet`) dit « …, apprise d'après les
  occurrences passées » via `EffortSource.LEARNED` (`core/team/Effort.kt`).

### Série stable

- `Task.seriesId: Long?`, nul par défaut. `Recurrence.nextOccurrence` et
  `calendarOccurrences` (séries « le N du mois ») transmettent le `seriesId` de
  la mère ou, à défaut, son propre `id` ; une tâche reçue d'un manager
  (`origin != null`) n'ouvre jamais de série personnelle.
- **Pas de rétro-remplissage** : la migration `7.sqm` ajoute la colonne
  `series_id` (en fin de table `task`, `insertTaskWithId` est positionnel),
  nulle, sans réécrire les lignes ; les tâches existantes retombent sur le
  titre jusqu'à leur prochaine occurrence. L'export n'écrit rien tant que
  `seriesId` est nul (`explicitNulls = false`) : le fichier d'or ne change pas.
- Le marqueur de la migration (`KairosStore.MARKERS`, version 8 : colonne
  `series_id`) fait lire la version du schéma réel, pas du `PRAGMA user_version`
  (`demarrage.md`, `ExchangeMigrationTest.everyMigrationHasAMarker`).
- Le dépôt écrit `series_id` à la création, à la mise à jour et à l'import
  (trois sites de `KairosRepository`).

### Réglages

`Settings.learning: LearningSettings? = null` ; `LearningSettings(enabled = true,
history = 5, minSamples = 3)`. Nul = comportement par défaut (**actif**).
`SettingsForm` expose `learning.enabled`, `learning.history` (≥ 3) et
`learning.minSamples` (≥ 2, au plus `history` : `TOO_LARGE` sur
`learning.minSamples`) et ne crée l'objet qu'en cas d'écart ; `team` n'est
jamais touché. Carte « Durées apprises » dans Réglages, catégorie « Planification »
(`SettingsFormCards`, hors section avancée, `reglages.md`).

### Interface

- `ui/day/TaskRow.kt` : `LearnedBadge` (`~45 min` + icône + description
  d'accessibilité) à la place du badge d'estimation, seulement pour une tâche à
  faire, sans estimation, à durée apprise **fiable**.
- `ui/day/NowCard.kt` : la carte « Maintenant » affiche la durée apprise fiable
  quand il n'y a pas d'estimation.
- `ui/day/Why.kt` : ligne de provenance (fiable : durée et nombre
  d'occurrences ; peu fiable : « pas encore apprise (n, m nécessaires) »).
- `ui/day/EditTaskDialog.kt` (`learned` transmis par `DayScreen`) : sous le
  champ des minutes, tant qu'il est vide : puce « Utiliser 45 min (tes 5
  dernières) » si fiable, qui remplit le champ **sur action** (règle « on ne
  remplit que ce qui est vide ») ; sinon une phrase « peu fiable » sans action.
- Textes FR/EN (`learned_*`, `why_learned*`, `setting_learning_*`,
  `settings_section_learning*`, `effort_learned`).

### Évolution : modèle d'apprentissage automatique (version majeure)

Cette évolution est spécifiée (non implémentée) dans [`estimateur-modele.md`](estimateur-modele.md) (issue #99, 4.0.0) : modèle local et explicable calculé de l'historique, sans poids pré-entraînés, qui précise ou remplace les contraintes ci-dessous à l'implémentation.

Un `ModelEstimator(params)` se branche derrière le **même point d'entrée**
(`DurationEstimator.estimate`, extrait en interface à ce moment-là) et renvoie
`Source.MODEL` avec un intervalle `low`/`high` qui alimente le modèle d'effort
de la simulation (`core/team/forecast/EffortModel.kt`). Contraintes déjà
connues :

- Kotlin pur exécutable sur wasm, JVM et Android ; aucune dépendance native ni
  non libre (F-Droid, `architecture.md`).
- Les poids du modèle sont des **données** passées en paramètre (JSON) ;
  l'entraînement est hors ligne ou une fonction pure à graine fixe
  (reproductibilité, skill `kairos-monte-carlo`).
- Caractéristiques : ce que porte déjà `Sample` (à enrichir : type, points) (jetons du titre,
  type, points, priorité, récurrence, jour de semaine).
- Le planificateur, le badge et les specs ne changent pas : seuls `Source` et
  `samples` traversent la frontière.

### Décisions et pièges tracés

- **Dérivée, jamais écrite** (écarté : remplir `estimatedMinutes` à la
  complétion) : l'historique reste corrigeable, rien à défaire.
- **Actif par défaut** (choix du propriétaire ; écartés : suggestion seule,
  désactivé). **Tension assumée** avec « rien ne change sans réglage » : un
  planning existant change pour qui a des récurrentes sans estimation et du
  temps chronométré. Atténuations : jamais sur une durée explicite ; au moins 3
  occurrences chronométrées ; provenance visible partout ; annoncé par la visite
  (« Nouveau dans cette version ») ; interrupteur dans Réglages.
- **Identifiant de série stable** (choix du propriétaire ; écarté : clé
  (titre, espace), qui casse au renommage). Coût : colonne + migration, sans
  rétro-remplissage.
- **Médiane et non moyenne** : robuste aux chronos oubliés ; la moyenne
  tronquée reste possible pour n ≥ 6.
- **Ordre par `id`** : l'`updatedAt` change à chaque édition.
- **Séances à zéro ignorées** : jamais comptées comme 0 minute.

### Écarts avec la spécification initiale (décidés à l'implémentation)

- **Deux niveaux seulement (série, titre)**, et non quatre : le calibrage par
  type et par points de l'espace Équipe (`Effort.teamCalibration`) existe déjà et
  s'applique de lui-même quand aucune durée apprise n'est fiable ; en faire des
  niveaux du port aurait dupliqué ce calcul. `Source.TYPE`, `POINTS`, `MODEL`
  ne sont donc pas déclarés ; `Source.MODEL` s'ajoutera avec le modèle
  d'apprentissage automatique.
- **Un objet, pas une interface** : le port pur est `DurationEstimator`
  (fonctions) ; l'interface se dégagera quand un second estimateur existera (le
  modèle d'apprentissage automatique), sans que les appelants changent.
- **L'estimation apprise de l'équipe passe par l'instantané**
  (`withLearnedEstimates`) plutôt que par une table : un seul point, aucune
  signature d'`Effort`/`TeamWork` modifiée.
- **Pas d'intervalle** `low`/`high` ni d'`EffortSource.LEARNED` dans le
  planificateur de la vue Jour : la provenance y est portée par
  `DayView.learned`.
- Le calibrage de `statistiques.md` n'est pas partagé : rien à reporter.

