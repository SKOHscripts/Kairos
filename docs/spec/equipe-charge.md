# Espace Équipe : capacité, charge et répartition

_Rôle : ce que chaque membre peut faire (capacité), ce qu'on lui a confié
(charge), la vision globale et par catégorie, le plan de charge
déterministe qui dit quand chaque tâche devrait finir, et la suggestion de
répartition du backlog. Fichiers prévus : `kmp/core/.../core/team/`
(`Capacity.kt`, `Effort.kt`, `LoadPlan.kt`, `AssignmentSuggestion.kt`),
`kmp/ui/.../team/` (`TeamMembersScreen.kt`, `MemberSheet.kt`,
`LoadBar.kt`, `SuggestionSheet.kt`). Les prévisions probabilistes sont dans
`equipe-simulation.md`, qui réutilise ces calculs._

État : **jalon E4 en cours** : socle (`core`) implémenté le 2026-10-01 ; interface à venir dans la même PR.

## 1. Besoin métier (cahier des charges)

### Objectif / problème

« Qui est surchargé ? Qui a de la marge ? L'équipe peut-elle absorber ce
backlog ? Sur quelles catégories passe notre capacité ? » : le manager a
besoin d'une vue de la **charge** rapportée à la **capacité**, individuelle
et globale, sur un horizon proche, et d'une aide pour **distribuer** le
backlog.

### Comportement attendu (utilisateur)

#### Horizon

- Les chiffres de charge se lisent sur un **horizon glissant** de
  `teamHorizonWeeks` semaines (4 par défaut), d'aujourd'hui inclus au
  dimanche de la dernière semaine. Un sélecteur en tête de la destination
  Équipe propose 1, 2, 4, 8 semaines (le réglage est la valeur par défaut).

#### Capacité

- Capacité d'un membre sur l'horizon, en heures : pour chaque **jour ouvré**
  (jours ouvrés et fériés des Réglages, `ordonnancement.md`) hors de ses
  absences : `heures par jour × quotité × taux de focus`.
- **Taux de focus** de l'équipe (`teamFocusFactor`, 80 % par défaut) : la
  part du temps réellement disponible pour les tâches (réunions, support et
  imprévus en moins). Il s'affiche en clair : « Capacité : 96 h (focus
  80 %) ».
- Aujourd'hui compte pour sa part restante seulement si le membre est
  « moi » et que la journée de travail est entamée ; sinon il compte en
  entier (le manager ne connaît pas l'heure des autres).

#### Effort restant d'une tâche

- Effort de base, en heures, par ordre de préférence :
  1. la **durée estimée** de la tâche, si renseignée ;
  2. sinon, la **médiane réelle** du palier de points de la tâche mesurée
     sur les tâches d'équipe faites (`equipe-simulation.md` § Données), si
     elle est fiable (au moins 3 tâches) ;
  3. sinon, `points × teamHoursPerPoint` (2 h par point par défaut).
- Effort **restant** = effort de base × (1 − avancement).
- Une tâche sans points ni durée est **non estimée** : elle n'est jamais
  comptée pour zéro en silence ; elle est comptée à part (« + 3 tâches non
  estimées ») et signalée (contour + icône).
- La source de chaque effort est lisible dans la fiche (« 6 h, médiane des
  tâches à 3 points, n=8 »).

#### Charge individuelle

- Destination **Équipe** : une carte par membre actif (« moi » d'abord),
  avec son nom, son rôle, sa quotité, et :
  - **charge / capacité** sur l'horizon : barre neutre, remplissage
    primaire jusqu'à 100 %, la part au-delà en **rouge** (dépassement,
    charte), valeur écrite (« 112 h / 96 h · 117 % ») ;
  - le nombre de tâches en cours et à faire, non estimées comprises ;
  - la prochaine absence (« absent du 12 au 16 oct. ») ;
  - les tâches **en danger** du plan de charge (ci-dessous) : « 2 échéances
    en danger ».
- Seuils : **surchargé** au-delà de 100 % ; **à surveiller** au-delà de
  `teamLoadWarnPercent` (90 % par défaut) : contour + icône, jamais d'ambre.
- Toucher la carte ouvre la **fiche membre** : coordonnées de capacité,
  absences (ajout, modification, suppression), charge **par semaine** de
  l'horizon (une barre par semaine), charge **par catégorie**, liste de ses
  tâches ouvertes dans l'ordre du plan avec leur date de fin prévue, et son
  activité (`equipe-backlog-suivi.md` § Journal).

#### Charge globale et par catégorie

- En tête de la destination Équipe, quatre chiffres clés : **capacité de
  l'équipe**, **charge assignée**, **taux de charge**, **backlog non
  assigné** (en heures, et en semaines d'équipe : « ≈ 1,5 semaine »).
- Panneau **Par catégorie** : pour chaque catégorie (« Sans catégorie »
  comprise), charge assignée + backlog, et part de la capacité de
  l'horizon, de la plus à la moins lourde. Répond à « où passe notre
  capacité ».
- Panneau **Répartition** : charge de chaque membre sur la même échelle,
  pour voir d'un coup d'œil le déséquilibre ; indicateur d'écart
  (« écart max : Marc 117 %, Léa 54 % »).

#### Plan de charge (déterministe)

- Pour chaque membre, ses tâches ouvertes sont posées l'une après l'autre
  dans l'ordre du score (en retard d'abord, comme la vue Jour), dans sa
  capacité jour après jour, en respectant les dépendances (une tâche ne
  commence pas avant la fin prévue de ses bloqueurs, même chez un autre
  membre). On obtient une **date de fin prévue** par tâche.
- Une tâche dont la fin prévue dépasse son échéance est **en danger** ;
  la fiche dit de combien (« fin prévue le 14 oct., échéance le 10 »).
- Ce plan est une estimation ponctuelle, sans incertitude : l'écran le dit
  (« Plan sans aléa : voir Prévisions pour les probabilités »).

#### Suggestion de répartition

- Depuis le Backlog, « Suggérer une répartition » propose un assigné pour
  chaque tâche **prête** (qualifiée), ou pour la sélection.
- Règle, lisible par le manager : les tâches sont prises dans l'ordre du
  score ; chacune va au membre actif qui la **finirait le plus tôt** selon
  le plan de charge, en tenant compte de ses absences, avec deux
  préférences :
  - **affinité** : à fin égale à `teamAffinityDays` jours ouvrés près (2 par
    défaut), on préfère le membre qui a fait le plus de tâches de cette
    catégorie sur les 12 dernières semaines ;
  - **limite d'en-cours** : un membre déjà au-delà de `teamWipLimit` passe
    après les autres.
- Chaque ligne proposée dit pourquoi (« Léa : fin prévue le 8 oct., 5
  tâches Développement faites en 12 semaines »). Le manager accepte tout,
  refuse tout, ou change une ligne ; rien n'est assigné avant « Appliquer ».
  L'application passe par l'assignation normale (journalisée).
- Tâches à qualifier et membres archivés : exclus, et l'écran le dit.

### Critères de succès

- Capacité : un membre à 80 % de quotité, 7 h/j, focus 80 %, sur une semaine
  de 5 jours ouvrés avec un jour férié et un jour d'absence : 3 jours ×
  7 × 0,8 × 0,8 = 13,44 h (`CapacityTest`).
- Effort : les trois sources dans l'ordre de préférence ; avancement à 50 %
  divise par deux ; une tâche non estimée est comptée à part, jamais à zéro
  (`EffortTest`).
- Plan de charge : deux tâches de 8 h pour un membre à 8 h/j sans focus
  réduit finissent à J et J+1 ; une tâche bloquée par la tâche d'un autre
  membre qui finit à J+2 commence après ; une absence décale d'autant
  (`LoadPlanTest`).
- Suggestion : déterministe (mêmes entrées → même proposition), ne propose
  jamais un membre archivé ou absent sur tout l'horizon, et, à capacité
  égale, répartit en alternance (`AssignmentSuggestionTest`).
- Aucune barre sans sa valeur écrite ; la couleur n'encode rien que le texte
  ne dise (`statistiques.md` § Décisions).

### Hors périmètre / différé

- Capacité heure par heure (créneaux des membres) : le manager ne tient pas
  leur agenda ; la capacité est journalière.
- Optimisation globale de la répartition (programmation linéaire) : un
  glouton explicable suffit et reste prévisible.
- Plusieurs taux de focus par membre : un seul, d'équipe (une évolution par
  membre est possible sans migration, le réglage vivant dans le JSON).

## 2. Solution technique

### Capacité (`core/team/Capacity.kt`, pur)

- `Capacity.dailyHours(membre, jour, fériés, absences, réglages,
  todayFraction = 1.0)` : 0 hors jour ouvré (`Workdays.isWorkday`), 0 un
  jour d'absence (plage incluse, `isAbsent`), sinon `hoursPerDay ×
  availabilityPercent / 100 × focusFactor`, multiplié par `todayFraction`.
- `todayFraction(maintenant, réglages)` : 1 avant le début de la journée de
  travail, 0 après sa fin, la part restante entre les deux ; appliquée au
  seul membre « moi », aujourd'hui.
- `overRange(…)`, `team(…)` (membres actifs seulement),
  `isAvailableDuring(…)`, `nominalDayHours`, `weeklyHours` (5 jours ×
  heures × quotité × focus), `horizonEnd(aujourd'hui, semaines)` (dimanche
  de la dernière semaine).

### Effort (`core/team/Effort.kt`, pur)

- `EffortSource` (paquet `team`) : `ESTIMATE`, `CALIBRATED`, `POINTS_RATE`,
  `NONE`.
- `Effort.base(tâche, calibration, réglages): Pair<Double?, EffortSource>`
  en heures ; `Effort.remaining` applique `progressPercent` ;
  `Effort.planned` rend `Planned(heures, source, unestimated)`, avec la
  valeur par défaut `defaultFibonacciPoints × hoursPerPoint` pour une tâche
  non estimée.
- `Effort.teamCalibration(snapshot, maintenant)` :
  `TaskStats.fibonacciCalibration` sur les tâches d'**équipe** faites,
  temps passé = sessions (une session ouverte court jusqu'à maintenant) +
  temps manuel ; le temps des rapports s'y ajoutera au jalon E6. Fiable si
  n ≥ 3.
- Réglages ajoutés (`TeamSettings`) : `horizonWeeks` (4, 1-26),
  `focusFactor` (0.8, > 0 exclu et ≤ 1), `hoursPerPoint` (2.0, > 0 exclu),
  `loadWarnPercent` (90, 1-100), `affinityDays` (2, ≥ 0) ; champs `team.*`
  de `SettingsForm`, nuls en mode solo.

### Plan de charge (`core/team/LoadPlan.kt`, pur)

- `LoadPlan.build(snapshot, jour, calibration, horizonWeeks, todayFraction,
  efforts?, capacity?)` : `efforts` (heures restantes par tâche) et
  `capacity` ((membre, jour) → heures) **surchargent** les valeurs
  calculées : c'est le point d'entrée de la simulation (E5).
- Ordre d'un membre : `Scheduling.sortKey` (en retard d'abord, puis score)
  avec l'urgence héritée des dépendances (`Dependencies.derivedUrgency`),
  comme la vue Jour.
- Posage : simulation à événements, tous les membres ensemble, jour par
  jour. À chaque instant libre, un membre prend la **première** tâche de
  son ordre dont tous les bloqueurs d'équipe sont finis ; sinon il attend
  (capacité perdue, comptée en heures d'attente). Une tâche en cours n'est
  jamais interrompue. Une tâche bloquée par la tâche d'**un autre membre**
  commence le jour ouvré **suivant** la fin de ce bloqueur (le résultat ne
  dépend pas de l'ordre de traitement des membres) ; chez le même membre,
  elle peut commencer le jour même. Un bloqueur hors équipe ou fait ne
  bloque pas. Une tâche déjà à 100 % finit au premier jour de capacité.
- Une tâche d'équipe ouverte **sans porteur** (non assignée, ou assignée à
  un archivé) ne finira pas : elle et ses dépendantes sortent de la
  simulation avec `Placement.BLOCKED_BY_BACKLOG`, sans dates, sans compter
  en attente ; leur charge compte quand même. Au-delà de 730 jours
  (`GUARD_DAYS`) : `OUT_OF_HORIZON`. Pour ces deux placements, `late` = la
  tâche a une échéance, `lateDays = 0` ; pour une tâche posée, `lateDays`
  en jours ouvrés, au moins 1.
- Cycles : `Dependencies.acyclicEdges` (même traitement que la vue Jour :
  une tâche en aval d'un cycle perd aussi son arête).
- Sortie : `Result(jour, horizonEnd, members, tasks)` ; `PlannedTask(taskId,
  memberId, start?, end?, late, lateDays, effortSource, unestimated, hours,
  placement)` ; `MemberPlan(memberId, tasks, capacityHours, loadHours,
  assumedHours, unestimatedCount, weeks, byCategory, waitHours)` ;
  `WeekLoad(start, end, capacityHours, hours, assumedHours)` (la première
  semaine commence aujourd'hui, les suivantes le lundi, toutes finissent le
  dimanche).

### Charge (`core/team/TeamLoad.kt`, pur)

- `TeamLoad.build(snapshot, maintenant, fuseau, horizonWeeks?,
  calibration?)` : `MemberLoad` par membre actif (capacité, charge, taux,
  `LoadLevel` `OK` / `WATCH` / `OVERLOADED`, en-cours, à faire, non
  estimées, prochaine absence, échéances en danger, plan) ; capacité,
  charge et taux de l'équipe ; backlog (toutes les tâches d'équipe à faire
  sans membre actif, à qualifier comprises) en heures, en tâches et en
  **semaines d'équipe** (heures ÷ capacité hebdomadaire nominale, sans
  férié ni absence) ; non estimées assignées et du backlog ; `CategoryLoad`
  (heures estimées assignées + backlog, toutes tâches et pas seulement
  l'horizon, part de la capacité en fraction) ; `LoadSpread` (taux le plus
  bas et le plus haut).
- **La charge ne compte que les tâches estimées** ; les non estimées sont
  dénombrées à part et leur valeur supposée exposée à part
  (`assumedHours`) : la charge affichée ne ment jamais par défaut.
- Taux sans capacité : `null`, niveau `OVERLOADED` s'il y a de la charge,
  `OK` sinon. Seuils stricts : > 100 % surchargé, > `loadWarnPercent` à
  surveiller.

### Suggestion (`core/team/AssignmentSuggestion.kt`, pur)

- `AssignmentSuggestion.suggest(snapshot, maintenant, fuseau, taskIds?,
  horizonWeeks?, calibration?)` → `Result(suggestions, toQualify,
  unplaceable, archivedMembers)` ; `Suggestion(taskId, memberId,
  plannedEnd?, reason)` ; `Reason(plannedEnd, category, affinityTasks,
  wipLimit, overWipLimit, decidedBy)`, `Decider` : `EARLIEST`, `AFFINITY`,
  `WIP_LIMIT`, `LOAD`, `ID`.
- Glouton : tâches prêtes non assignées (ou la sélection) dans l'ordre de
  `TeamBoard.ready` (urgence héritée : un bloqueur passe avant ce qu'il
  bloque) ; candidats = membres actifs ayant au moins un jour ouvré hors
  absence sur l'horizon ; pour chacun, le plan est rejoué avec la tâche
  ajoutée et l'on garde la fin la plus tôt ; dans `affinityDays` jours
  ouvrés de la meilleure fin, départage par affinité (tâches faites de la
  même catégorie sur `AFFINITY_WEEKS` = 12 semaines ; 0 pour une tâche
  sans catégorie), puis membre sous la limite d'en-cours
  (`TeamSignals.wipExceeded`), puis charge la plus faible, puis plus petit
  identifiant. Le plan est mis à jour après chaque choix. Si aucun
  candidat n'a de date (tâche qui attend le backlog), tous sont ex æquo et
  `plannedEnd` est nul.
- Coût : un plan rejoué par candidat et par tâche, environ 1,2 s en JVM
  pour 150 tâches de backlog, 60 assignées et 8 membres ; l'interface
  calcule hors du fil principal. Un recalcul incrémental reste possible si
  le web le demande.

### Interface (`ui/team/`)

- `TeamMembersScreen` : `FlowRow` de tuiles clés (même `StatTile` que les
  statistiques, 150 dp minimum), puis `LazyColumn` de `MemberCard`
  (`OutlinedCard`), puis panneaux Par catégorie et Répartition
  (`BarRow` des statistiques, étendu d'une part de dépassement en
  `error`).
- `LoadBar` : piste `surfaceContainerHighest`, remplissage `primary`
  jusqu'à 100 %, puis `error` ; valeur écrite à droite ; contour et icône
  `Warning` au-delà du seuil d'alerte.
- `MemberSheet` : dialogue plein écran sur téléphone, feuille latérale en
  largeur étendue ; absences en `ListItem` + `DatePicker` MD3 pour les
  plages.
- `SuggestionSheet` : liste des propositions (case à cocher, assigné
  modifiable par menu, raison en texte secondaire), « Appliquer » en
  bouton primaire plein, « Annuler ».

### Décisions et alternatives écartées

- **Heures plutôt que points pour la charge** : la capacité se compte en
  temps (quotité, absences, jours fériés) ; les points sont convertis par
  la calibration réelle de l'équipe quand elle existe, par un taux réglable
  sinon. Les points restent l'unité du score.
- **Taux de focus explicite** plutôt qu'une capacité « brute » : sans lui,
  toute équipe paraît sous-chargée ; l'afficher évite qu'il soit un
  paramètre caché.
- **Non estimées comptées à part** : une charge qui les compterait pour
  zéro mentirait ; la charge affiche donc toujours ce qu'elle ne sait pas.
- **Plan déterministe distinct de la simulation** : il répond vite et
  explique chaque date ; la simulation (`equipe-simulation.md`) y ajoute
  l'incertitude en rejouant le **même** plan avec des efforts tirés.
- **Glouton « fin la plus tôt » plutôt que « charge la plus faible »** :
  il tient compte des absences et des dépendances, qui comptent plus que
  l'équilibre brut.

### Impacts sur les specs existantes (à reporter à l'implémentation)

- `reglages.md` : réglages de charge dans la carte Équipe.
- `statistiques.md` : `StatTile` et `BarRow` réutilisés (et la part de
  dépassement de `BarRow`).
- `ordonnancement.md` : réutilisation de `sortKey` et de l'urgence héritée
  hors de la vue Jour.
