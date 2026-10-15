# Statistiques

_Rôle : un retour empirique sur la façon dont on estime, où va le temps et
où s'accumulent les retards, pour calibrer ses estimations et le score.
Fichiers couverts : `kmp/core/src/commonMain/.../core/stats/TaskStats.kt`
(calcul pur), `kmp/ui/.../stats/StatsScreen.kt`. Les repères du guide des
points et les durées suggérées qui en découlent sont affichés par la vue
Jour (`vue-jour.md`). Tests : `core/.../stats/StatsDifferentialTest.kt`
(200 cas contre les fichiers d'or `stats.json`), `M4ScreensUiTest.statsShowWhatWasDoneAndTimed`
et `calibratedPointsSuggestTheDurationAndFeedTheGuide`._

État : **jalon M4**. Les sorties sont figées par les fichiers d'or de
`StatsDifferentialTest` (`stats.json`, source de vérité depuis
`retrait-kairos2.md`).

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
- **Quand tu travailles chaque type** (3.5, étape C1 de `planning-personnalise.md`) : pour chaque type (« Sans type » compris), du plus au
  moins chronométré, ses effectifs (« n=5 · 5 h », « peu fiable » avec l'icône d'alerte sous le seuil), la **tranche de 2 h qui le
  distingue** (« Se distingue à 14:00–16:00 : 80 % de son temps (×2.2 ton rythme habituel) », ou « Réparti dans la journée, sans plage
  marquée »), les trois plus grandes parts écrites et douze barres (une par tranche, de minuit à minuit) qui les illustrent. Observation
  seulement : rien ne change dans le planning.

Honnêteté statistique : chaque agrégat montre son effectif ; sous 3, il est
marqué « peu fiable » (icône d'alerte), mais reste affiché.

### Critères de succès

- Sorties identiques aux fichiers d'or (tableau de bord, repères du guide,
  calibration par type) sur 200 historiques aléatoires
  (`StatsDifferentialTest`).
- Une tâche terminée et chronométrée apparaît dans le débit et dans la
  calibration, « n=1, peu fiable » (`M4ScreensUiTest`).
- Aucune barre sans sa valeur écrite ; aucune information portée par la
  couleur seule.

### Hors périmètre / différé

- Graphiques temporels fins, export, estimation par apprentissage, filtres
  interactifs.

## 2. Solution technique

Depuis le jalon E1 de l'espace Équipe, l'écran Stats lit `repository.personalSnapshot` (la vue Perso, `equipe.md` § Espaces et filtre central) et non la base complète ; sans tâche d'équipe, c'est la même instance.

### Calcul (`TaskStats`, `core`)

Pur : tâches, sessions, jour, instant, fuseau et réglages en paramètres.
`dashboard(tâches, sessions, aujourd'hui, réglages, maintenant, fuseau)`
reçoit **toutes** les tâches, archivées comprises.

- Fenêtre : `weeks = max(1, statsWindowWeeks)`, début = lundi de la semaine
  courante moins `weeks − 1` semaines. Sessions de la fenêtre :
  `TimeTracking.sessionsInRange` (date locale du début).
- Date de complétion d'une tâche faite = date locale de `updatedAt` (pas
  d'horodatage dédié).
- `throughputByWeek` : `WeekThroughput(lundi, terminées, points)` pour
  chaque semaine de la fenêtre, zéro compris.
- `fibonacciCalibration` : tâches faites avec points (≠ 0) et temps passé
  > 0 (sessions + saisie manuelle + temps rapporté,
  `spentMinutesByTask`), médiane par
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
- Arrondis : `pyRound` (`kotlin.math.round`, au pair le plus proche : comportement propre de
  Kairos, figé par les fichiers d'or), médiane d'un effectif pair = moyenne des deux
  valeurs centrales.

### Meilleures heures (`BestHours`, `core`, 3.5)

Pur : tâches, sessions, instant, fuseau et réglages en paramètres (`BestHours.measure`). Sessions de la même fenêtre que le tableau de bord
(`statsWindowWeeks`) ; **sessions de moins de 5 minutes ignorées** (démarrage-arrêt accidentel) ; une session ouverte court jusqu'à
maintenant. Les minutes d'une session sont **réparties entre les tranches de 2 h** qu'elle traverse, sur l'**heure locale** du fuseau passé
(minuit compris). **Pondération récente** : de 1 (aujourd'hui) à 1/2 (premier jour de la fenêtre). Par type (clé `""` = sans type ; une tâche
disparue y tombe) : `Profile(type, sessions, minutes, share, lift, peak, reliable)`.

- `share` : part du temps pondéré du type par tranche (somme 1). `lift` : cette part ÷ la part de **toutes** les tâches dans la même tranche
  (« je travaille à 10 h » n'est pas « les documents, c'est à 10 h »).
- `peak` : la tranche au plus fort lift parmi celles qui portent au moins 20 % du temps du type ; `null` quand le type se répartit sans pointe.
- `reliable` : au moins 5 sessions retenues **et** 120 minutes réelles ; sinon le profil reste affiché, marqué « peu fiable ».
- Tri par temps réel décroissant, puis par nom. Tests : `BestHoursTest` (déterministe sur une base de sessions fixée), `BestHoursUiTest`.

### Écran (`StatsScreen`, `ui`)

- Recalculé quand la base ou le jour changent ; colonne défilante de
  960 dp au plus.
- `StatTile` : `surfaceContainerLow`, valeur en `headlineSmall` ; seuil
  franchi = contour `outline` et icône `Warning`. Dans chaque `FlowRow`, une
  tuile prend `weight(1f)` et au moins 150 dp : quatre par ligne en largeur
  bureau, deux sur un téléphone (à largeur fixe de 200 dp, un téléphone n'en
  plaçait qu'une par ligne, écran trois fois trop long ; défaut vu sur les
  captures des magasins, `publication.md`). `fillMaxRowHeight()` : les
  tuiles d'une même ligne ont la même hauteur, même quand un libellé passe
  à la ligne.
- `Panel` : `OutlinedCard`, icône, titre, phrase de rôle.
- `BarRow` : libellé (96 dp), piste neutre `surfaceContainerHighest`,
  remplissage **primaire** à bouts arrondis de 4 dp, valeur écrite à
  droite, entre 110 et 200 dp (icône `Warning` si peu fiable) : la piste
  garde de la place sur un téléphone. Débit et calibration : longueur
  relative au maximum ; types et complétude : pourcentage.
- Ratio du biais à deux décimales ; durées par `duration()`.

- Carte « Quand tu travailles chaque type » (`HoursProfile`, 3.5) : par type, nom et effectifs, phrase de pointe, douze barres d'une seule teinte
  (`primary`, hauteur relative à la plus grande part du type, jamais sans valeur écrite : les trois plus grandes parts sont écrites dessous), la
  frise est masquée aux lecteurs d'écran (le texte porte l'information).

- Briques partagées avec l'espace Équipe (jalons E3 et E4,
  `equipe-charge.md`) : `StatTile` et `Panel` sont `internal` ; `StatTile`
  accepte une `note` (pastille en contour + icône) ; `BarRow` accepte
  `overflow` (part au-delà de 100 % en `error`), `mark`, `detail` et
  `stackedWhenNarrow`, et partage son tracé (`BarTrack`). Sans ces options,
  le rendu des statistiques est inchangé.

### Décisions et pièges tracés

- **Tests différentiels en UTC, écran en fuseau local** : les fichiers d'or
  (`stats.json`) portent des horodatages UTC, lus comme tels par
  `StatsDifferentialTest` ; l'application passe le fuseau du
  système, comme partout (règle tracée dans `vue-jour.md`).
- **Barres d'une seule teinte, pas de légende** : une série par graphique ;
  la couleur n'encode rien que le texte ne dise déjà (charte : pas d'ambre,
  alertes par la forme).
- **Alertes par contour et icône**, y compris pour « en retard » et les
  échéances tenues : un chiffre
  statistique n'est pas une erreur ; le rouge reste aux lignes de tâches
  en retard.
- **Pas de bibliothèque de graphiques** : des `Box` suffisent (dépendances
  libres et minimales, `architecture.md`).
- **Guide et durées suggérées sur tout l'historique**, archivées comprises :
  une tâche archivée a été réellement faite ou
  chronométrée.
