# Espace Équipe : backlog, distribution et suivi

_Rôle : le cycle de vie d'une tâche d'équipe, de sa capture dans le backlog
à sa fin : qualification, catégorie, assignation, réaffectation, états,
avancement, tableau de suivi et journal. Fichiers prévus :
`kmp/core/.../core/team/` (`TeamBoard.kt`, `TeamStates.kt`,
`TeamSignals.kt`, `TeamEvent.kt`), `kmp/data/` (table `team_event`,
opérations du dépôt), `kmp/ui/.../team/` (`TeamBacklogScreen.kt`,
`TeamBoardScreen.kt`, `AssignMenu.kt`, `TaskHistory.kt`). Vision,
activation et isolation : `equipe.md`. Charge et suggestion de
répartition : `equipe-charge.md`._

État : **jalon E3 implémenté (2026-10-01)**.

## 1. Besoin métier (cahier des charges)

### Objectif / problème

Le manager doit pouvoir tenir **un backlog commun**, le qualifier comme ses
propres tâches (priorité, points, échéance, catégorie), le **distribuer**,
**déplacer** une tâche d'un membre à un autre, et savoir à tout moment où en
est chaque sujet, avec l'historique de ce qui s'est passé.

### Comportement attendu (utilisateur)

#### Backlog

- Destination **Backlog** : les tâches d'équipe **non assignées**, à faire.
- Capture en tête, comme dans la vue Jour : un titre, Entrée, la tâche naît
  dans le backlog « À qualifier ».
- Deux sections, comme la vue Jour :
  - **À qualifier** : il manque la priorité ou les points (règle
    `needsProcessing` de `modele-donnees.md`) ; mêmes chips de
    qualification que « À traiter » ;
  - **Prêtes** : triées par score WSJF (même calcul que l'espace Perso,
    mêmes réglages de score), en retard d'abord, avec le score en chiffre
    primaire, la priorité, les points, l'échéance, la catégorie.
- Filtres : recherche dans le titre, catégorie, priorité, « avec
  échéance ».
- Chaque ligne : toucher ouvre la **fiche** (la fiche d'édition existante,
  plus le champ « Assigné à » et l'onglet « Historique ») ; menu d'actions
  « Assigner à… », « Supprimer ».
- **Sélection multiple** (appui long sur téléphone, case à cocher en
  largeur bureau) : « Assigner à… », « Changer la catégorie », « Changer la
  priorité ».
- Bouton **« Suggérer une répartition »** : ouvre la proposition calculée
  par `equipe-charge.md` § Suggestion, à accepter en tout ou ligne à ligne.

#### Catégories

- La catégorie d'une tâche est son **type** (`Task.taskType`), choisi dans
  la liste de Réglages → Types de tâches, commune aux deux espaces
  (décision 5 de `equipe.md`). « Sans catégorie » quand le type est vide.
- Partout dans l'espace Équipe, le type est libellé « Catégorie ».
- Filtres, charge par catégorie (`equipe-charge.md`) et prévisions par
  catégorie (`equipe-simulation.md`) lisent ce champ. Renommer un type dans
  les Réglages ne renomme pas les tâches existantes (comportement actuel
  des types, inchangé).

#### Assignation et réaffectation

- « Assigner à… » ouvre un menu des **membres actifs**, triés par nom,
  chacun avec sa charge sur l'horizon (« 82 % ») et un contour d'alerte
  s'il est surchargé ou absent aujourd'hui (E3 : nombre d'en-cours et limite
  d'en-cours ; la charge en % arrive au jalon E4, `equipe-charge.md`) ; « moi »
  en tête s'il existe ; dernière entrée « Remettre au backlog ».
- Assigner une tâche la fait passer du Backlog au Suivi, état **À faire**.
- **Réaffecter** : la même action (« Réaffecter à… »), depuis le menu d'une
  carte du Suivi ou depuis la fiche.
- Une réaffectation garde l'avancement et le temps passé ; elle remet
  l'état à « À faire » si la tâche était « En cours » (le nouveau titulaire
  n'a pas commencé), après avoir demandé : « Garder l'état En cours ? ».
- Les sous-tâches d'une tâche d'équipe sont des tâches d'équipe ; à la
  création, elles prennent l'assigné de la mère ; ensuite elles se
  réaffectent chacune.

#### États et avancement

États d'une tâche d'équipe, dérivés, jamais saisis en double :

| État | Règle |
|---|---|
| **Backlog** | à faire, sans assigné. |
| **À faire** | à faire, assignée, pas commencée (`startedOn` vide). |
| **En cours** | à faire, assignée, `startedOn` posé. |
| **Bloquée** | à faire et bloquée par une tâche à faire (`dependances.md`) ; s'affiche **en plus** de À faire / En cours. |
| **Faite** | `status = done`. |

- « Commencer » pose `startedOn` = aujourd'hui ; lancer le chrono sur une
  tâche d'équipe assignée à « moi » la commence aussi.
- **Avancement** : curseur MD3 discret de 0 à 100 % par pas de 10, dans la
  fiche et dans le menu de la carte. Le poser au-dessus de 0 commence la
  tâche si elle ne l'était pas. Terminer la tâche vaut 100 % ; la rouvrir
  garde l'avancement d'avant la fin.
- Terminer, rouvrir, supprimer : mêmes gestes et mêmes règles que dans
  l'espace Perso (récurrence comprise : l'occurrence suivante garde
  l'assigné).

#### Suivi (tableau)

- Destination **Suivi**, écran d'ouverture de l'espace Équipe.
- En tête, quatre chiffres clés : **en cours**, **faites cette semaine**
  (lundi → aujourd'hui), **en retard**, **à surveiller** (tâches portant au
  moins un des signaux ci-dessous). Même tuile que les Statistiques.
- Puis **une ligne par membre actif** (« moi » d'abord), repliable, avec
  son nom, sa quotité, un indicateur de charge et ses cartes groupées par
  état : En cours, À faire, Faites (7 derniers jours). En largeur ≥ 840 dp,
  les trois états sont des colonnes côte à côte ; en dessous, des sections
  empilées.
- Carte de tâche : titre, catégorie, priorité (liseré P0 rouge comme une
  ligne de tâche), échéance, avancement (barre fine primaire + « 60 % »),
  icônes de signal.
- Filtres : catégorie, membre, « seulement à surveiller ».
- **Signaux « à surveiller »** (forme : contour + icône, jamais d'ambre) :
  - **en retard** (règle `isOverdue` du moteur) ;
  - **traîne** : règle `Staleness` existante ;
  - **sans avancement** : en cours et avancement inchangé depuis
    `teamStaleProgressDays` jours ouvrés (5 par défaut) ;
  - **ballottée** : réaffectée au moins `teamChurnThreshold` fois (3 par
    défaut) ;
  - **trop d'en-cours** : un membre a plus de `teamWipLimit` tâches en
    cours (3 par défaut, 0 = sans limite) ; signal porté par la ligne du
    membre.
- Toucher une carte ouvre la fiche.

#### Journal (suivi complet)

- Chaque changement d'une tâche d'équipe est **journalisé** : création,
  qualification (priorité, points, catégorie, échéance), assignation
  (de → vers), commencement, avancement (de → vers), fin, réouverture,
  suppression, rapport reçu (`equipe-echanges.md`), application d'un
  scénario (`equipe-simulation.md`).
- Fiche → onglet **Historique** : les événements de la tâche, du plus
  récent au plus ancien (« 30 sept. · Assignée à Léa (était : Marc) »).
- Fiche membre → **Activité** : les événements de ses tâches sur 30 jours.
- Une tâche supprimée garde ses événements (titre recopié dans
  l'événement) : l'activité d'un membre reste lisible.

#### Tâches assignées à moi

- Espace activé et un membre « C'est moi » : ses tâches d'équipe à faire
  apparaissent dans la **vue Jour** et la **vue Semaine** comme des tâches
  personnelles (score, placement, chrono), marquées d'une icône `Groups`
  et du mot « Équipe » dans la ligne, et dans « Pourquoi à cette place ? »
  (« Tâche d'équipe, assignée à moi »).
- Les modifier depuis l'espace Perso les modifie dans l'équipe (même
  ligne) et journalise.
- Capturer une tâche dans l'espace Perso crée toujours une tâche
  **personnelle**.

### Critères de succès

- Une tâche capturée dans le Backlog n'apparaît jamais dans la vue Jour
  tant qu'elle n'est pas assignée à « moi » (`TeamBoardTest`).
- Toute tâche d'équipe à faire est dans **exactement un** des états Backlog,
  À faire, En cours (Bloquée s'y ajoute) ; une faite est dans Faite
  (`TeamStatesTest`, propriété sur tirages aléatoires).
- Assigner puis réaffecter puis remettre au backlog produit trois
  événements, dans l'ordre, avec les bons « de → vers »
  (`TeamRepositoryTest.reassignmentIsJournaled`).
- Le tri des Prêtes est celui de `Scheduling.sortKey` sur les mêmes
  réglages (`TeamBoardTest.backlogUsesTheWsjfOrder`).
- Signaux : chaque seuil franchi et non franchi est testé ; un signal n'est
  jamais porté par la couleur seule.
- Suivi lisible à 360 dp (sections empilées) et à 1280 dp (colonnes).

### Hors périmètre / différé

- Colonnes d'état personnalisables, états supplémentaires (« En revue »…) :
  les cinq états suffisent ; un besoin de plus rouvrira cette spec.
- Sprints ou itérations nommées : l'horizon glissant de `equipe-charge.md`
  en tient lieu.
- Commentaires en fil sur une tâche (la description sert de notes).
- Glisser-déposer (toutes plateformes, § Décisions).

## 2. Solution technique

### États et tableau (`core/team/TeamStates.kt`, `TeamBoard.kt`, purs)

- `TeamState` : `BACKLOG`, `TODO`, `IN_PROGRESS`, `DONE` ;
  `TeamStates.of(task): TeamState?` selon le tableau ci-dessus (`null` pour
  une tâche archivée) ; `TeamStates.blockedIds(snapshot)` par
  `Dependencies.blockedTaskIds` sur les arêtes d'équipe.
- `TeamBoard.build(snapshot, day, timeZone, filter)` lit la base complète
  (il n'y a pas de type `TeamSnapshot` séparé : les tâches d'équipe sont
  filtrées sur `space`). Il rend `keyFigures` (`TeamKeyFigures` : en
  cours, faites cette semaine du lundi à aujourd'hui, en retard, à
  surveiller ; les deux derniers comptent aussi le backlog), `lanes`
  (`MemberLane` pour **chaque** membre actif, même sans tâche, « moi »
  d'abord puis l'ordre de `TeamMembers` ; `inProgress`, `todo`, `done` ;
  `inProgressCount` et `wipExceeded` calculés hors filtre), `toQualify`
  (triées par identifiant) et `ready` (triées par `Scheduling.sortKey`,
  urgence héritée des dépendances comme la vue Jour). Une carte
  (`TeamCard`) porte la tâche, son état, `blocked`, ses signaux, son score
  et `doneOn`. Mêmes archivées ignorées que la vue Jour ; une mère à
  sous-tâches ouvertes reste dans le backlog (la vue Jour, elle, ne pose que
  des unités de travail).
- « Faites » = `doneOn` de `day − 6` à `day` inclus ; `doneOn` = date locale
  de l'événement `done` le plus récent, à défaut `updatedAt` (règle des
  statistiques).
- `TeamBoardFilter(text, category, priority, memberId, onlyWatched,
  withDeadline)` s'applique aux lignes, au backlog et aux chiffres clés ;
  un filtre par membre ne garde que sa ligne et vide le backlog.

### Signaux (`core/team/TeamSignals.kt`, pur)

- `TeamSignal` : `OVERDUE` (`Scheduling.isOverdue`), `STALE`
  (`Staleness.daysStale`, seuils existants), `NO_PROGRESS` (en cours et
  **strictement plus** de `staleProgressDays` jours ouvrés depuis le
  dernier événement `started` ou `progress`, à défaut depuis `startedOn` ;
  `Workdays.businessDaysBetween`, comme `Staleness`), `CHURN` (événements
  `assigned` d'un membre à un autre, hors premier assignement et hors
  retours au backlog, ≥ `churnThreshold`). `TeamSignals.of(task, events,
  day, timeZone, settings, holidays)`, `noProgress`, `churn`,
  `inProgressCount(tasks, membre)`, `wipExceeded(tasks, membre, settings)`
  (en cours > `wipLimit` si > 0).
- Réglages ajoutés (`TeamSettings`, `equipe.md` § Modèle) :
  `staleProgressDays` (5, ≥ 1), `churnThreshold` (3, ≥ 2), `wipLimit` (3,
  ≥ 0, 0 = sans limite) ; champs `team.staleProgressDays`,
  `team.churnThreshold`, `team.wipLimit` de `SettingsForm` (nuls en mode
  solo, comme les autres champs `team.*`).

### Journal (`TeamEvent`, table `team_event`)

- `TeamEvent` : `id`, `taskId`, `taskTitle` (recopié), `memberId` (assigné
  **après** l'opération, `null` au backlog), `kind`, `fromValue`,
  `toValue`, `source`, `at` (instant UTC). `TeamEventSource` : `manual`,
  `self`, `report` (rapport d'un membre, E6, `equipe-echanges.md`),
  `scenario` (E5) ; code inconnu → `manual`.
- `TeamEventKind` : `created` (`toValue` = titre), `qualified` (un
  événement par champ changé, valeurs `priority:1`, `points:5`,
  `type:Dev`, `deadline:2026-10-03`, vide après le deux-points si le champ
  est vidé ; `QualifiedField`, `TeamEvents.qualifiedValue`,
  `parseQualified`), `assigned` (identifiants de membre de → vers),
  `started` (`toValue` = date ISO), `progress` (pourcentages ; « 0 » si
  l'avancement était vide), `done` (`fromValue` = avancement d'avant la
  fin, `toValue` = « 100 »), `reopened` (avancement d'avant → avancement
  restauré), `deleted`, `sent` (envoyée dans un paquet, `toValue` =
  identifiant du paquet, E6), `time` (temps passé rapporté, minutes de →
  vers, E6), et `UNKNOWN` : un code inconnu à la lecture est
  gardé et affiché « Modification », jamais une erreur (réécrit
  « unknown » à l'export ou au remplacement). `TeamEvents.historyOf(events,
  tâche)` : plus récent d'abord.
- **Écrit par le dépôt**, dans la transaction de la modification, jamais
  par l'interface : aucun chemin ne modifie une tâche d'équipe sans son
  événement. Une modification sans changement réel n'écrit rien.
- Source par défaut des opérations de l'espace Perso (`toggleDone`,
  `startTimer`, `setPriority`, `setPoints`, `updateTask`, `deleteTask`,
  paramètre `source` facultatif) : `self` si la tâche d'équipe est assignée
  au membre « moi » actif, `manual` sinon.

### Dépôt (`KairosRepository`)

- `createTeamTask(titre, parentId?): Long?` : `space = TEAM`, `teamUid`
  tiré par le dépôt, sans assigné ; une sous-tâche prend l'assigné de sa
  mère et n'écrit que `created` (avec ce `memberId`).
- `assign(taskIds, memberId?, keepInProgress = false, source): Int` (nombre
  de tâches changées) : seulement des tâches d'équipe à faire (les autres
  sont ignorées) ; membre archivé ou inconnu → rien ; réaffecter une tâche
  en cours la remet à « À faire » sauf `keepInProgress` ; le retour au
  backlog vide toujours `startedOn` (l'avancement est gardé) ; un
  événement `assigned` par tâche réellement changée.
- `startTeamTask(id)`, `setProgress(id, pct)` (borné 0-100, arrondi à la
  dizaine ; > 0 commence, `started` puis `progress`) : tâche d'équipe à
  faire et **assignée** seulement.
- `TaskEdit.reassign: Reassignment?` (`memberId`, `keepInProgress`) porte
  un changement d'assigné dans l'édition complète ; `null` = inchangé.
- `updateTask`, `setPriority`, `setPoints` (`qualified`), `toggleDone`
  (`done` met l'avancement à 100 ; `reopened` restaure celui du dernier
  `done`, ou garde l'actuel s'il n'y en a pas), `deleteTask` (`deleted` ;
  les événements restent), `startTimer` (pose `startedOn` sur une tâche
  d'équipe assignée) : inchangés pour une tâche personnelle.
- Récurrence d'une tâche d'équipe : l'occurrence suivante garde espace et
  assigné, reçoit un **nouveau** `teamUid`, un avancement et un
  commencement vides, et son événement `created`. Sous-tâches créées en lot
  par `updateTask` : espace et assigné de la mère.
- `archiveMember` (E2) journalise chaque retour au backlog (et vide
  `startedOn`) ; `deleteMember` est refusé aussi si le membre est le
  titulaire (`memberId`) d'un événement (pas s'il n'apparaît qu'en
  `fromValue` : l'archivage l'y écrit toujours) ; `clearTeamData` supprime
  le journal.
- Stockage : `3.sqm` (schéma 3 → 4, `equipe.md` § Stockage).

### Interface (`ui/team/`)

- `TeamBacklogScreen(services, initialSelection)` : `Capture` réutilisé en
  volet « Tâche » seul (`taskOnly`, crée par `createTeamTask`) ; filtres
  (recherche, catégorie, priorité, « Avec échéance ») ; sections « À
  qualifier » (mêmes chips que « À traiter », `InboxQualify`) et « Prêtes »
  (`ScoreBadge`, priorité, points, échéance, catégorie, liseré P0) dans une
  ligne dédiée : `TaskRow` est lié au modèle de la vue Jour, une « variante
  équipe » l'aurait alourdi. Menu ⋮ : « Assigner à… », « Supprimer »
  (confirmé). Sélection multiple : case à cocher si la fenêtre fait au moins
  600 dp, appui long en dessous ; barre du bas « Assigner à… », « Changer la
  catégorie » (`KairosRepository.setTaskType`, journalisé `qualified`),
  « Changer la priorité ».
- `AssignMenu` (`AssignMenuItems`, `AssignContext`) : membres actifs,
  « moi » en tête, nombre d'en-cours, contour + `Warning` si la limite
  d'en-cours est dépassée ou si le membre est absent aujourd'hui (la charge
  en % arrivera en E4) ; « Remettre au backlog » en dernier ; entrées
  ≥ 48 dp. `Reassigner` + `KeepInProgressDialog` (« Garder l'état En
  cours ? », Oui / Non ; fermer annule).
- `TeamBoardScreen(services)` : quatre `StatTile` (rendu `internal` dans
  `StatsScreen.kt`), filtres catégorie, membre, « Seulement à surveiller » ;
  une `OutlinedCard` repliable par membre (nom, badge « moi », quotité,
  en-cours, signal « Trop d'en-cours (4/3) ») ; états en colonnes à partir
  de 840 dp de **contenu**, en sections empilées en dessous. Carte de tâche
  **pleine** (`surfaceContainerLow`) dans la ligne en contour, pour éviter
  deux contours imbriqués ; barre d'avancement primaire + « 60 % » ;
  signaux en badge contour + icône + **texte court** (En retard, Traîne,
  Sans avancement, Ballottée, Bloquée), lisibles sans l'icône et par le
  lecteur d'écran. Menu de carte : Commencer, curseur d'avancement,
  Réaffecter à…
- Fiche : `EditTaskDialog` reçoit `team: TeamTaskSheet?` (`TeamTaskSheet.kt`,
  `TeamTaskDialog.kt`) : onglets Détails / Historique (`TaskHistory`,
  phrases du journal), « Assigné à » (menu), « Catégorie », curseur 0-100 %
  par pas de 10 enregistré avec la fiche, « Commencer » immédiat. Le
  dialogue se ferme après l'écriture. En espace Perso, une tâche d'équipe
  assignée à « moi » n'a ni Historique ni avancement : « Assigné à » en
  lecture seule.
- Fiche membre : section « Activité » (`TeamActivity.memberActivity`, 30
  jours) : événements dont il est titulaire, plus les réaffectations qui lui
  ont retiré une tâche.
- Vue Jour : `TeamMark` (icône `Groups` + « Équipe », lu « tâche
  d'équipe ») dans `TaskRow` ; « Pourquoi à cette place ? » ajoute « Tâche
  d'équipe, assignée à moi » ; « Bloquée par » remplace l'identifiant d'un
  bloqueur d'équipe hors vue Perso par « Titre (assigné) », lu dans
  `repository.snapshot` (`DayView` reste inchangé).
- Réglages : `team.staleProgressDays`, `team.churnThreshold`,
  `team.wipLimit` dans la carte Équipe (mode activé et enregistré).
- Icônes : `SwapHoriz`, `MoreVert`, `HourglassEmpty` (sans avancement).
- Tests : `TeamStatesTest`, `TeamSignalsTest`, `TeamBoardTest`,
  `TeamEventTest` (`core`), `TeamRepositoryTest`, `TeamTaskTypeTest`
  (`data`), `TeamActivityTest` (`ui`), `TeamBacklogBoardUiTest`
  (`desktopApp`). Auto-test : `desktop-team-board[-narrow]`,
  `desktop-team-backlog[-narrow]`, `desktop-team-backlog-selection`,
  `desktop-team-task-sheet[-details][-narrow]`,
  `desktop-team-member-activity[-narrow]` (données `TeamSeed`).

### Décisions et alternatives écartées

- **États dérivés** de `status`, `assigneeId`, `startedOn` plutôt qu'une
  colonne d'état : pas de combinaison incohérente possible (« En cours »
  sans assigné), et `status` garde ses trois codes de Kairos 2.
- **Avancement par pas de 10 %** : une précision plus fine est illusoire
  pour un avancement déclaratif, et le curseur discret se manie au doigt.
- **Réaffecter remet à « À faire » (sur confirmation)** : le temps passé
  appartient à l'ancien titulaire, le nouvel assigné n'a pas commencé ;
  mais le manager peut garder « En cours » (passation d'un travail entamé).
- **Journal écrit par le dépôt** : c'est la seule façon de garantir le
  « suivi complet » demandé, y compris pour les modifications faites depuis
  la vue Jour.
- **Pas de glisser-déposer** (prévu en complément, écarté en codant) : il
  n'est ni accessible au lecteur d'écran ni praticable à 360 dp, et pas
  assez fiable à la fois sur Android, le bureau et le web ; le menu
  « Réaffecter à… » est le seul chemin.
- **Colonnes de membres plutôt que colonnes d'état (kanban classique)** :
  la question du manager est « qui fait quoi » ; l'état se lit dans chaque
  ligne. Un kanban par état reste possible via le filtre par membre.

### Impacts sur les specs existantes

Reportés au jalon E3 : `vue-jour.md` (marque « Équipe », « Pourquoi »,
bloqueur d'un collègue, fiche), `modele-donnees.md` (`team_event`, `3.sqm`,
opérations), `reglages.md` (trois seuils), `export-import.md` (journal),
`recurrence.md` (clé de série), `navigation-theme.md` (icônes),
`accessibilite.md` (sélection multiple).
