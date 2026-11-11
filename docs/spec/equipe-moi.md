# Espace Équipe : mon travail perso dans la charge et les prévisions

_Rôle : faire compter le travail personnel de « moi » (le manager membre de
sa propre équipe) dans la charge, le plan et les prévisions de l'espace
Équipe, et rendre ces calculs plus fiables : tâches récurrentes projetées,
créneaux occupés, historique perso, temps chronométré. Fichiers couverts :
`kmp/core/.../core/team/TeamWork.kt` (`TeamWork`), `team/Capacity.kt`,
`team/Effort.kt`, `team/LoadPlan.kt`, `team/TeamLoad.kt`,
`team/AssignmentSuggestion.kt`, `team/forecast/ForecastData.kt`,
`team/forecast/MonteCarlo.kt`, `team/forecast/EffortModel.kt`,
`core/model/Task.kt` (`Task.teamExcluded`), `data` (`6.sqm`, `Kairos.sq`,
`Mappers.kt`, `KairosRepository.kt`, `ExportCodec.kt`), `ui/day/EditTaskDialog.kt`
et ses appels, `ui/team/MemberLoadSection.kt`, `ui/team/TeamTaskSheet.kt`,
`ui/team/forecast/` (périmètre, marques), captures `desktop-team-my-load*.png`
de l'auto-test (`SelfTest.kt`). Tests : `TeamWorkTest`, `TeamWorkForecastTest`,
`MyWorkRepositoryTest`, `TeamMyWorkUiTest`, `TeamIsolationTest`._

État : demandée le 2026-10-10, implémentée.

## 1. Besoin métier (cahier des charges)

### Objectif / problème

Le manager qui est aussi membre de son équipe (« C'est moi », `equipe.md`)
voit ses tâches d'équipe dans sa vue Jour, mais l'inverse n'existe pas : ses
tâches **personnelles** sont invisibles de l'espace Équipe. Le plan de charge
et les prévisions supposent donc que « moi » consacre toute sa capacité aux
tâches d'équipe : sa charge est sous-estimée, ses dates de fin trop
optimistes, et la suggestion de répartition lui confie trop de travail. Les
prévisions ignorent aussi les occurrences à venir des tâches récurrentes, les
réunions de l'agenda et le temps déjà chronométré : elles sont moins fiables
qu'elles ne pourraient l'être.

### Comportement attendu (utilisateur)

#### Compter mes tâches perso dans l'équipe

- Quand la gestion d'équipe est activée **et** qu'un membre actif porte
  « C'est moi », chaque tâche personnelle **compte dans l'équipe** par défaut :
  elle reste une tâche perso (rien n'est copié, rien ne change dans la vue
  Jour), mais elle entre dans la charge, le plan et les prévisions de « moi ».
- La fiche d'édition d'une tâche perso porte alors, dans « Options
  avancées », un interrupteur **« Compter dans l'équipe »** (actif par défaut)
  avec la phrase « Ta charge et tes prévisions dans l'espace Équipe en
  tiennent compte. » (tutoiement de l'espace Perso). L'éteindre exclut la tâche (une tâche privée, un
  rendez-vous personnel…). Sans gestion d'équipe ou sans « moi »,
  l'interrupteur n'apparaît pas et rien ne compte.
- Ne comptent pas : les tâches reçues d'un manager par paquet (elles sont le
  travail d'une autre équipe), les tâches archivées.
- Dans l'espace Équipe, ces tâches apparaissent **avec leur titre**, marquées
  « Perso » : dans la liste des tâches ouvertes de « moi » (fiche du membre,
  ordre du plan, pastille « Perso ») et dans les listes des prévisions
  (échéances, criticité, issue la plus probable : « Titre · Perso »).
  Elles ne sont jamais dans le Backlog ni dans le Suivi (qui suivent le
  travail confié à l'équipe), ni dans un paquet envoyé à un membre.
- Les prévisions s'en servent : le périmètre « Travail assigné » les
  comprend, et un nouveau périmètre **« Mon travail »** (premier de la liste
  des membres, si « moi » existe) répond à « quand aurai-je fini, mes
  échéances perso comprises ? ».

#### Tâches récurrentes

- Une tâche récurrente à faire (perso comptée ou d'équipe) ne pèse plus pour
  une seule occurrence : ses **occurrences à venir** jusqu'à la fin de
  l'horizon de charge sont projetées et prennent leur part de capacité dans
  le plan et dans chaque tirage des prévisions (« Rapport hebdo » chaque
  vendredi : 1 h chaque semaine).
- Une occurrence projetée n'est disponible qu'à partir de son jour, comme
  dans la vue Jour. Elle apparaît dans la liste des tâches de son membre
  marquée « Récurrente » avec sa date ; elle n'est pas une tâche du
  périmètre (une série ne « finit » jamais) : elle retarde le reste, sans
  compter dans « date de fin », « combien d'ici… » ni dans les échéances.
- Plus largement, le plan ne commence plus avant sa date une tâche
  programmée plus tard (date « Programmée pour »), comme la vue Jour qui la
  cache jusque-là.

#### Créneaux de « moi »

- Les créneaux **occupés** de la vue Jour (réunions, ponctuelles ou
  récurrentes, et depuis la 3.10.0 ceux importés d'un calendrier ou lus dans les calendriers du téléphone,
  `connexions-externes.md`) réduisent la capacité de
  « moi » les jours concernés, pour la
  part qui tombe dans sa journée de travail. Les fenêtres de deep work ne
  réduisent rien (c'est du temps de travail).

#### Historique et temps passé

- Les tâches perso **faites** qui comptent dans l'équipe enrichissent
  l'historique : calibration des points (médiane réelle par palier),
  facteurs d'erreur et débit de « moi ». Utile au démarrage, quand
  l'historique d'équipe est maigre.
- Effort restant : sans avancement déclaré, le temps déjà passé sur la tâche
  (chrono, temps saisi, temps rapporté par un membre) est déduit de l'effort
  de base, sans descendre sous 10 % de celui-ci (une tâche ouverte n'est
  jamais finie dans le plan). Un avancement déclaré reste prioritaire.

### Critères de succès

- Base solo (espace jamais activé) : rien ne change, export identique à
  l'octet (`TeamIsolationTest.soloExportIsByteIdentical`), tous les tests
  existants passent.
- Gestion d'équipe activée avec « moi » : une tâche perso estimée à 4 h
  ajoute 4 h à la charge de « moi » et à son plan ; exclue, elle n'y est plus
  (`TeamWorkTest.aPersonalTaskCountsForMeWhenTeamManagementIsOnAndMeExists`,
  `excludedReceivedAndArchivedTasksDoNotCount`,
  `TeamMyWorkUiTest.aPersonalTaskCountsForMeUntilExcluded`).
- Sans « moi », ou espace désactivé : aucune tâche perso ne compte, et
  l'interrupteur n'apparaît pas (`TeamWorkTest.nothingCountsWithoutMeOrWithTeamManagementOff`,
  `TeamMyWorkUiTest.theSwitchOnlyShowsWhenAMemberIsMe`).
- Une tâche hebdomadaire de 1 h projette une occurrence par semaine de
  l'horizon, chacune disponible à partir de son jour ; elle n'entre pas dans
  le périmètre des prévisions mais y prend sa capacité
  (`TeamWorkTest.aWeeklyTaskProjectsOneOccurrencePerWeekOfTheHorizon`,
  `TeamWorkForecastTest.projectedOccurrencesTakeCapacityButAreNotInTheScope`).
- Une tâche programmée plus tard n'est pas commencée avant sa date, sans
  compter d'attente (`TeamWorkTest.aTaskScheduledLaterIsNotStartedBeforeItsDateAndTheMemberTakesTheNextOne`).
- Une réunion de 2 h un jour ouvré retire 2 h (avant le focus) à la capacité
  de « moi » ce jour-là, pas aux autres
  (`TeamWorkTest.aMeetingReducesOnlyMyCapacityThatDay`,
  `busyBlocksInTheWorkdayAreCountedRecurringOnesIncludedButNotDeepWork`).
- Une tâche de 5 h dont 3 h ont été chronométrées reste à 2 h ; à 4 h 50
  chronométrées, elle reste à 0,5 h (10 %) ; un avancement déclaré prime
  (`TeamWorkTest.timeSpentIsDeductedDownToTenPercentAndADeclaredProgressWins`,
  `thePlanDeductsTheChronometeredTime`).
- Les tâches perso faites comptent dans la calibration et le débit de
  « moi » ; ses tâches perso sont dans le périmètre « Mon travail »
  (`TeamWorkTest.myDonePersonalTasksFeedTheTeamCalibration`,
  `TeamWorkForecastTest.myDonePersonalTasksCountInMyThroughput`,
  `myPersonalTasksAreInMyWorkScopeAndDelayMyTeamTask`).
- Une tâche perso n'arrive jamais au Backlog, dans le Suivi ni dans un
  paquet envoyé à un membre, même si un calcul lisait par erreur la vue de
  calcul (elle y est assignée à « moi » et n'a pas de `teamUid`)
  (`TeamWorkTest.myPersonalTasksNeverReachTheBacklogTheTrackingOrAPack`).
- Stockage : migration 6 → 7 sans perte, exclusion enregistrée pour une
  tâche perso seulement, export 2 avec `teamExcluded` (et export solo
  inchangé), nettoyage par « Supprimer les données d'équipe »
  (`MyWorkRepositoryTest`).

### Hors périmètre / différé

- Les tâches perso dans le Suivi et le Backlog : non, ce sont les écrans du
  travail confié à l'équipe ; la charge de « moi » et les prévisions
  suffisent à en tenir compte.
- Compter le travail perso des autres membres : Kairos ne le connaît pas
  (un membre n'envoie que l'avancement de ses tâches reçues).
- Les créneaux des autres membres : le manager ne tient pas leur agenda ;
  le taux de focus les couvre.
- Projeter les récurrences au-delà de l'horizon de charge.

## 2. Solution technique

### Le pendant de `personalView` : `TeamWork` (`core/team/TeamWork.kt`, pur)

- `TeamWork.self(snapshot)` : le membre « moi » actif si la gestion d'équipe
  est activée (`teamModeEnabled`), sinon `null`.
- `TeamWork.counts(task, snapshot)` / `countable(task)` : tâche
  `PERSONAL`, non reçue (`origin == null`), non archivée, non exclue
  (`!teamExcluded`), et « moi » existe.
- `TeamWork.view(snapshot)` : **la** vue que lisent les calculs d'équipe.
  Les tâches perso qui comptent y deviennent des tâches `TEAM` assignées à
  « moi » (copies, même `id`) ; tout le reste est rendu tel quel. Sans « moi »
  (ou espace désactivé), l'entrée est rendue **telle quelle** (même
  instance). Idempotente : la vue d'une vue est la même vue.
- `TeamWork.planInput(snapshot, day, now, horizonEnd)` : ce que reçoit le
  posage (`PlanInput`) : `view` plus les **occurrences projetées**
  (`occurrences`), ajoutées comme tâches `TEAM` virtuelles à identifiant
  **négatif** (`isProjected(task)` : `id < 0`), le temps passé
  (`spentMinutes`) et les heures occupées de « moi » (`selfBusy`).
- `TeamWork.occurrences(view, day, horizonEnd)` : pour chaque série
  récurrente à faire de la vue (série = titre + règle + assigné, tâches
  `TEAM` de la vue, donc perso de « moi » comprises ; on part de sa dernière
  occurrence à faire), les occurrences suivantes dont l'échéance tombe
  jusqu'à `horizonEnd` :
  - règles « à la complétion » (`Recurrence.COMPLETION_RULES`) : échéances
    successives `Recurrence.nextDeadline` à partir de celle de la tâche
    (ou d'aujourd'hui si elle est dépassée ou absente), ancre hebdomadaire
    comprise ;
  - « le N du mois » : le jour N des mois qui suivent celui de l'occurrence
    à faire, reculé au jour ouvré précédent
    (`Workdays.onOrBeforeBusinessDay`), comme `Recurrence.calendarOccurrences`,
    jamais avant aujourd'hui ;
  - chaque occurrence copie la tâche (effort, points, type, priorité,
    assigné) avec `deadline` = son jour et `scheduledDate` = ce jour, ou le jour ouvré qui le précède
    s'il tombe un week-end ou un férié (une hebdomadaire ancrée un dimanche resterait sinon « en retard » en
    permanence : elle ne pouvait commencer qu'après son échéance), avancement nul,
    `teamUid` dérivé et stable (`"<teamUid ou task-id>@<date>"`, pour les
    tirages), identifiants `-1, -2…` dans l'ordre (série, date) ;
  - garde-fou : 400 occurrences au plus par série.
- `TeamWork.busyHours(snapshot, day)` : heures des créneaux `BUSY` (ponctuels
  et récurrents, `Recurrence.expandRecurringBlocks`) dans la journée de
  travail de [day] (`Scheduling.busyMinutesInWindow`) ; `selfBusy(snapshot)`
  la mémorise par jour, et rend toujours 0 sans « moi » ou sans créneau
  occupé.
- `TeamWork.spentMinutes(snapshot, now)` : temps passé par tâche
  (`TimeTracking.spentMinutesByTask` sur les tâches à faire de la vue,
  `Task.reportedMinutes` compris).

### Branchement

- Points d'entrée qui appliquent la vue (`view`, ou `planInput` pour le
  posage) : `TeamLoad.build`, `AssignmentSuggestion.suggest`,
  `MonteCarlo.prepare`, `ForecastData.build`, `Effort.teamCalibration`,
  `ForecastFingerprint.of`. L'interface ne change pas d'appel. `TeamBoard`
  (Suivi, Backlog) lit toujours la base telle quelle : pas de tâche perso.
- `TeamLoad.build` : le plan lit `planInput` sur l'horizon choisi à l'écran ;
  les compteurs « en cours » / « à faire » d'un membre et le backlog restent
  ceux des tâches d'équipe ; `MemberLoad.personalHours` et `recurringHours`
  disent la part de la charge venue des tâches perso et des occurrences, et
  `personalCount` le nombre de tâches perso ouvertes qui comptent : la carte
  de « moi » dit « 0 en cours · 0 à faire · 5 perso » (avant, « 0 à faire »
  pour 20 h de charge se lisait comme une erreur ; test exploratoire du
  2026-10-11).
- `LoadPlan.prepare` reçoit en plus `spent` (minutes par tâche, vide par
  défaut) et `busy` (heures occupées de « moi » par jour, aucune par défaut) ;
  `TeamLoad`, `AssignmentSuggestion` et `MonteCarlo` les fournissent depuis
  `TeamWork`. Le plan pose les occurrences projetées comme les autres tâches.
- **Date de disponibilité** (`LoadPlan.availableOffset`) : une tâche avec
  `scheduledDate` n'est posable qu'à partir de min(`scheduledDate`,
  `deadline`), la règle « cachée » de la vue Jour (`Scheduling`) ; une
  occurrence projetée a `scheduledDate` = son échéance (ou le jour ouvré qui précède). Le posage saute une
  tâche pas encore disponible et prend la suivante ; si rien n'est
  disponible, ce n'est pas de l'« attente » (qui reste la capacité perdue
  faute de bloqueur fini).
- `LoadPlan.Result.tasksById` : les tâches posées (vue + occurrences), pour
  que l'interface affiche une occurrence virtuelle (son titre, sa date).
- **Prévisions** : `MonteCarlo.prepare` calcule le périmètre sur la vue
  (sans les occurrences projetées) et ajoute les occurrences de
  `planInput` (horizon des réglages) au plan rejoué (`EffortModel`), leurs
  efforts tirés comme les autres (clé : leur `teamUid` dérivé). La capacité de base de « moi » tient compte des créneaux.
  Le périmètre `ForecastScope.Member(moi)` est présenté « Mon travail » en
  tête des membres.
- **Empreinte de péremption** des prévisions (`ForecastFingerprint`) :
  tâches `TEAM` de la vue (`view`), leurs dépendances et sessions, plus les
  créneaux quand « moi » existe ; une tâche perso qui compte, ou une réunion,
  change donc le résultat en « périmé ».

### Capacité (`Capacity`)

- `dailyHours(…, busyHours = 0.0)` : `max(0, heures par jour × quotité −
  busyHours) × focus × todayFraction`. Seul « moi » reçoit des heures
  occupées. Le focus s'applique après : les réunions connues sont retirées,
  le focus couvre le reste (support, imprévus).
- `overRange(…, busy = { 0.0 })` et `team(…)` transmettent la fonction.

### Effort (`Effort`)

- `remaining(task, calibration, settings, spentMinutes = 0)` : avancement
  déclaré > 0 → effort × (1 − avancement) ; sinon, temps passé > 0 →
  max(effort − passé, 10 % de l'effort) (`MIN_REMAINING_SHARE`) ; sinon
  l'effort. `planned(…, spentMinutes)` suit.
- `teamCalibration` lit la vue : les tâches perso faites qui comptent
  enrichissent la médiane par palier.

### Données (`ForecastData.build`)

- Lit la vue : facteurs d'erreur, débits et facteurs de capacité de « moi »
  comprennent ses tâches perso qui comptent ; capacité prévue des semaines
  passées avec ses créneaux occupés.

### Stockage (`data`)

- `Task.teamExcluded: Boolean = false` ; colonne `team_excluded INTEGER NOT
  NULL DEFAULT 0` en fin de table (`6.sqm`, additive ; schéma 6 → 7).
- `TaskEdit.teamExcluded: Boolean?` (`null` = inchangé), écrit par
  `updateTask` pour une tâche perso seulement.
- `clearTeamData` remet toutes les exclusions à 0 : une base vidée de ses
  données d'équipe redevient une base solo, export compris.
- Export : `teamExcluded` écrit seulement s'il est vrai (champ nullable,
  `encodeDefaults = true`) et compté par `Workspaces.hasTeamData` ; un export
  solo reste identique à l'octet.

### Interface

- `EditTaskDialog(…, countInTeam: Boolean? = null)` : `null` masque
  l'interrupteur ; sinon `SwitchRow` (ligne `toggleable` de 48 dp, `Switch`
  MD3) et sa phrase d'aide, en fin de « Qualification » (4.1.0, `fiche-tache.md` ; avant, en fin d'« Options
  avancées »). L'édition porte `TaskEdit.teamExcluded` seulement si
  l'interrupteur a changé. Appelant : la vue Jour (`DayScreen`), qui passe
  `!teamExcluded` si `TeamWork.countable(task)` et que « moi » existe.
- `MemberLoadSection` : une tâche du plan dont la tâche réelle est perso
  porte la pastille « Perso » (icône `Person`) ; une occurrence projetée
  porte « Récurrente » (icône `Repeat`), sa date dans la ligne d'échéance.
  Pastille et ligne vont dans un `FlowRow` (rien ne déborde à 360 dp). La
  phrase « Dont X de tâches perso et Y de récurrences. »
  (`MemberLoad.personalHours`, `recurringHours`) suit la barre de charge
  quand l'une des deux est non nulle. `TeamMembersScreen` cherche une tâche
  du plan dans la base, puis dans `LoadPlan.Result.tasksById` (occurrences).
- `TeamTaskSheet` (« Effort … · reste X h ») déduit le temps passé comme le
  plan (`TeamWork.spentMinutes`).
- Prévisions : `ForecastNames.task` suffixe le titre d'une tâche perso
  (« Titre · Perso », `forecast_task_personal`) ; le menu des périmètres met
  « moi » en tête des membres sous « Mon travail (perso + équipe) », libellé
  repris par `scopeLabel`.
- Textes FR / EN : `team_count_in_team`, `team_count_in_team_hint`,
  `team_badge_personal`, `team_badge_recurring`, `member_load_from_elsewhere`,
  `forecast_scope_my_work`, `forecast_task_personal`.

### Auto-test

- `SelfTest.kt` ajoute une récurrence hebdomadaire de 1 h à la base des
  membres (« moi » = Claire, tâches perso d'exemple) et rend la fiche de
  « moi » : `desktop-team-my-load.png` (900 px) et
  `desktop-team-my-load-narrow.png` (360 px). Les captures
  `desktop-team-members*.png` montrent désormais la charge perso de « moi ».

### Décisions et pièges tracés

- **Compter sans copier** (2026-10-10, choix de l'utilisateur) : une tâche
  perso reste une seule ligne `PERSONAL` ; c'est une vue de calcul
  (`TeamWork.view`) qui la présente comme une tâche d'équipe de « moi ».
  Écarté : la convertir en tâche d'équipe (elle quitterait l'espace Perso,
  entrerait dans le journal, les paquets, le Backlog), ou une copie à
  réconcilier (même raison que `equipe.md` § Décisions, colonne `space`).
- **Exclusion plutôt qu'inclusion** (choix de l'utilisateur) : tout compte
  par défaut, rien n'est écrit en base tant qu'on ne touche pas
  l'interrupteur ; un booléen « exclue » à 0 par défaut garde les bases et
  l'export solo identiques.
- **La vue appliquée aux points d'entrée, pas dans le dépôt** : les écrans
  d'équipe lisent toujours `snapshot` (édition, journal, Suivi) ; seuls les
  calculs voient les tâches perso. La vue est idempotente, donc un calcul qui
  en appelle un autre ne double rien.
- **Occurrences hors périmètre** : une série n'a pas de fin ; les mettre
  dans le périmètre repousserait « la date de fin » à la dernière occurrence
  de l'horizon. Elles prennent leur capacité, c'est tout.
- **Disponibilité = règle de la vue Jour** : honorer `scheduledDate` pour
  toutes les tâches (pas seulement les occurrences) aligne le plan sur ce
  que voit le membre ; une tâche programmée vendredi n'est pas commencée
  lundi.
- **Plancher de 10 %** pour le temps passé : une tâche ouverte dont le temps
  dépasse l'estimation n'est pas finie ; sans plancher, le plan la poserait à
  zéro.
- **Créneaux avant le focus** : retirer les réunions puis appliquer le focus
  compte un peu deux fois les réunions déjà couvertes par le focus ; c'est le
  côté prudent (une date un peu plus tardive), et le focus reste réglable.
- **Tâches reçues exclues** : chez un membre qui gère aussi sa propre équipe,
  les tâches de son manager ne sont pas son travail perso à planifier deux
  fois.
