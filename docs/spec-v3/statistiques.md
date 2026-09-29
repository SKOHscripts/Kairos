# Statistiques

_Rôle : un retour empirique sur la façon dont on estime, où va le temps et
où s'accumulent les retards, pour calibrer ses estimations et le score.
Fichiers couverts : `kmp/core/src/commonMain/.../core/stats/TaskStats.kt`
(calcul pur), `kmp/ui/.../stats/StatsScreen.kt`. Les repères du guide des
points et les durées suggérées qui en découlent sont affichés par la vue
Jour (`vue-jour.md`). Tests : `core/.../stats/StatsDifferentialTest.kt`
(200 cas contre Kairos 2), `M4ScreensUiTest.statsShowWhatWasDoneAndTimed`
et `calibratedPointsSuggestTheDurationAndFeedTheGuide`._

État : **jalon M4**, portage de `app/tasks_stats.py` (spec Kairos 2
`docs/spec/statistiques.md`).

## 1. Besoin métier (cahier des charges)

### Objectif / problème

Kairos collecte des tâches qualifiées (priorité, points, durée, type) et du
temps réel chronométré. Sans retour, on ne sait pas si l'on estime juste,
où passe le temps, ni si le backlog grossit.

### Comportement attendu (utilisateur)

Destination « Stats », en lecture seule. Sans aucune donnée : un état vide
qui explique comment les chiffres se construisent. Sinon, de haut en bas :

- **Quatre chiffres clés** sur la fenêtre récente (8 semaines par défaut,
  réglable dans Réglages → Statistiques, `reglages.md`) :
  tâches terminées, temps réel chronométré, délai médian de complétion (en
  jours ; alerte au-delà de 7), part des échéances tenues (alerte sous
  70 %).
- **Débit hebdomadaire** : par semaine de la fenêtre (semaines vides
  comprises), tâches terminées et points cumulés (vélocité).
- **Calibration de l'estimation** : temps réel médian par palier de points,
  sur tout l'historique (tâches terminées et chronométrées) ; si deux
  paliers se rejoignent, l'échelle ne discrimine pas. Puis le biais
  « ×1.32 : 7 h réel pour 5 h 20 estimé (n=12) » (point décimal, comme le
  score) et le verdict (« Tu
  sous-estimes » au-delà de ×1.1, « Tu surestimes » sous ×0.9, sinon
  « Estimations justes »), avec une alerte hors de ×0.8–×1.25.
- **Où va le temps réel** : part du temps chronométré de la fenêtre par
  type (« Sans type » compris), du plus au moins chronophage ; puis le
  focus : nombre de sessions et durée moyenne (beaucoup de sessions
  courtes = attention morcelée).
- **Flux et backlog** (état courant) : tâches à faire, âge médian, en
  retard, qui traînent (ces deux-là en alerte s'il y en a).
- **Complétude des métadonnées** : part des tâches à faire ayant des
  points, une durée estimée, un type (« 90 % · 9/10 »).

Honnêteté statistique : chaque agrégat montre son effectif ; sous 3, il est
marqué « peu fiable » (icône d'alerte), mais reste affiché.

### Critères de succès

- Sorties identiques à Kairos 2 (tableau de bord, repères du guide,
  calibration par type) sur 200 historiques aléatoires
  (`StatsDifferentialTest`).
- Une tâche terminée et chronométrée apparaît dans le débit et dans la
  calibration, « n=1, peu fiable » (`M4ScreensUiTest`).
- Aucune barre sans sa valeur écrite ; aucune information portée par la
  couleur seule.

### Hors périmètre / différé

- Graphiques temporels fins, export, estimation par apprentissage, filtres
  interactifs (comme Kairos 2).

## 2. Solution technique

### Calcul (`TaskStats`, `core`)

Pur : tâches, sessions, jour, instant, fuseau et réglages en paramètres.
`dashboard(tâches, sessions, aujourd'hui, réglages, maintenant, fuseau)`
reçoit **toutes** les tâches, archivées comprises (comme Kairos 2).

- Fenêtre : `weeks = max(1, statsWindowWeeks)`, début = lundi de la semaine
  courante moins `weeks − 1` semaines. Sessions de la fenêtre :
  `TimeTracking.sessionsInRange` (date locale du début).
- Date de complétion d'une tâche faite = date locale de `updatedAt` (pas
  d'horodatage dédié, comme Kairos 2).
- `throughputByWeek` : `WeekThroughput(lundi, terminées, points)` pour
  chaque semaine de la fenêtre, zéro compris.
- `fibonacciCalibration` : tâches faites avec points (≠ 0) et temps passé
  > 0 (sessions + saisie manuelle, `spentMinutesByTask`), médiane par
  palier. `calibrationByType` : idem par type non vide. `Calibration(clé,
  n, médiane)`, `reliable` = n ≥ `MIN_SAMPLE` (3).
- `estimationBias` : tâches faites avec estimé (≠ 0) et temps passé > 0 ;
  ratio réel total ÷ estimé total.
- `timeByType` (sessions de la fenêtre, clé `""` = sans type,
  pourcentages arrondis), `focus` (sessions de durée > 0, moyenne arrondie).
- `backlogFlow` : à faire, âge médian (jours depuis la création), en retard
  (`Scheduling.urgencyBucket == 0`), qui traînent
  (`Staleness.daysStale`), délai médian de complétion et échéances tenues
  (faites dans la fenêtre, `done ≤ deadline`).
- `completeness` : sur les tâches à faire.
- `fibonacciReferences` : par palier ayant des tâches faites, la
  calibration du palier et les titres des deux plus récentes (par
  `updatedAt`), chronométrées ou non.
- Arrondis : `pyRound` (`kotlin.math.round`, au pair le plus proche comme
  le `round` de Python), médiane d'un effectif pair = moyenne des deux
  valeurs centrales.

### Écran (`StatsScreen`, `ui`)

- Recalculé quand la base ou le jour changent ; colonne défilante de
  960 dp au plus.
- `StatTile` : `surfaceContainerLow`, valeur en `headlineSmall` ; seuil
  franchi = contour `outline` et icône `Warning`.
- `Panel` : `OutlinedCard`, icône, titre, phrase de rôle.
- `BarRow` : libellé (120 dp), piste neutre `surfaceContainerHighest`,
  remplissage **primaire** à bouts arrondis de 4 dp, valeur écrite à
  droite (icône `Warning` si peu fiable). Débit et calibration : longueur
  relative au maximum ; types et complétude : pourcentage.
- Ratio du biais à deux décimales ; durées par `duration()`.

### Décisions et pièges tracés

- **Tests différentiels en UTC, écran en fuseau local** : le générateur
  (`kmp/tools/gen_fixtures.py`, `stats_case`) produit des horodatages UTC
  (Kairos 2 comparait des dates naïves) ; l'application passe le fuseau du
  système, comme partout (écart volontaire déjà tracé dans `vue-jour.md`).
- **Barres d'une seule teinte, pas de légende** : une série par graphique ;
  la couleur n'encode rien que le texte ne dise déjà (charte : pas d'ambre,
  alertes par la forme).
- **Alertes par contour et icône**, y compris pour « en retard » et les
  échéances tenues que Kairos 2 teintait en rouge ou vert : un chiffre
  statistique n'est pas une erreur ; le rouge reste aux lignes de tâches
  en retard.
- **Pas de bibliothèque de graphiques** : des `Box` suffisent (dépendances
  libres et minimales, `architecture.md`).
- **Guide et durées suggérées sur tout l'historique**, archivées comprises
  (calcul de Kairos 2) : une tâche archivée a été réellement faite ou
  chronométrée.
