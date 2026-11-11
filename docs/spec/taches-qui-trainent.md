# Ce qui traîne : échéance implicite et revue du backlog

_Rôle : garantir qu'une tâche sans date ne s'enfonce pas indéfiniment sous les tâches datées, et donner un endroit où
faire le point sur ce qui attend. Fichiers couverts : `kmp/core/.../core/engine/Scheduling.kt` (`implicitDeadline`,
`timeCriticality`, `wsjfBreakdown`, `isLingering`), `kmp/core/.../core/model/Settings.kt` (`implicitDeadlineDays`),
`kmp/core/.../core/settings/SettingsForm.kt`, `kmp/ui/.../settings/SettingsFormCards.kt`, `kmp/ui/.../settings/SettingsCategory.kt`,
`kmp/ui/.../day/Why.kt` (`dateLabel`), `kmp/ui/.../week/WeekScreen.kt` (`BacklogList`, `MovableTask`, `moveTargets`),
carte `lingering` de `kmp/ui/.../guide/FeatureCatalog.kt`. Tests :
`core/.../engine/ImplicitDeadlineTest.kt`, `core/.../engine/DifferentialTest.kt`, `desktopApp/.../BacklogReviewUiTest.kt`._

État : 4.1.0, implémentée le 2026-11-11 (demande du propriétaire, sans issue).

## 1. Besoin métier (cahier des charges)

### Objectif / problème

Kairos promet qu'on n'a pas à se soucier du placement : chaque tâche apparaît au moment opportun. Le score WSJF trie par
`(valeur + criticité) ÷ effort`, et la criticité ne vient **que** d'une date (échéance ou date programmée). Une tâche
sans date garde donc le même score pour toujours, pendant que chaque nouvelle tâche datée grimpe la rampe d'urgence et
passe devant. Quand il arrive plus de travail que la journée n'en absorbe, la queue de la liste n'obtient jamais de
créneau : elle disparaît.

Constat sur une sauvegarde réelle (2026-11-11, 60 tâches à faire, journée de 9 h) : des tâches de juillet sans date
(P1, 2 à 3 points) restent à un score de 4 ÷ 3 ≈ 1,3, sous toutes les tâches datées de la semaine. Leur pastille « traîne »
reste muette : elle lit la **dernière modification**, et ces tâches avaient été retouchées en novembre (estimation,
correction groupée) sans avancer d'un pas.

### Comportement attendu (utilisateur)

- **Échéance implicite.** Une tâche **sans échéance ni date programmée** compte, pour son score seulement, comme si elle
  était due **30 jours après sa création** (réglage « Échéance implicite des tâches sans date », 30 j par défaut, 0 =
  coupée, ≥ 0). Elle gagne donc de l'urgence comme une tâche datée : rien pendant les premiers jours, puis la même rampe
  (horizon d'urgence des réglages, 14 j par défaut) jusqu'au maximum à 30 j. Une tâche oubliée finit par revenir d'elle-même,
  à sa taille : à valeur égale, une grosse tâche reste derrière une petite.
- **Jamais « en retard ».** L'échéance implicite ne fait pas passer la tâche dans le palier dur des retards, ne colore pas
  sa ligne, n'est affichée nulle part comme une date, et ne compte ni dans les vues Semaine et Mois, ni comme échéance
  dans les prévisions, ni dans « traîne » de la vue Jour : seul le score la voit. **L'ordre**, lui, change partout où le
  score trie : vue Jour, file des prévisions (« fin probable », semaine réaliste), plan de charge, suggestion de
  répartition et « Prêtes » de l'espace Équipe.
- **« Pourquoi à cette place ? »** la nomme : « Sans date : remonte comme une échéance dans 9 j », puis, une fois
  atteinte, « Sans date depuis trop longtemps : urgence au maximum ».
- **Revue de ce qui traîne** (onglet Bilan, vue Semaine, étape « Faire le point » de GTD). La liste repliée « Backlog,
  sans date (N) » devient la revue :
  - son titre dit combien traînent : « Backlog, sans date (12) · 4 traînent » (rien après le nombre si aucune) ;
  - une tâche **traîne** quand son échéance implicite est atteinte : sa ligne porte la pastille à contour « traîne
    depuis 47 j » (l'âge depuis la création), la même que celle de la vue Jour ;
  - les tâches sont rangées de la plus ancienne à la plus récente (celles qui traînent d'abord, par construction) ;
  - toucher une tâche ouvre « Déplacer » comme dans les cartes des jours : les jours de la semaine affichée à partir
    d'aujourd'hui, aujourd'hui compris, et « Choisir une date… » ; le message « Annuler » rend la tâche à son état
    sans date. Programmée aujourd'hui, la tâche passe en tête de la journée ; programmée lundi, elle revient lundi en
    tête : c'est le moment choisi ;
  - sous l'aide existante, une phrase dit la règle : « Sans date depuis 30 j, une tâche remonte comme si elle était
    due : celles qui traînent l'ont dépassé. Touche une tâche pour la programmer. » (absente si le réglage vaut 0).
- Ce qui ne doit jamais remonter n'a pas sa place dans les tâches : « Un jour peut-être », c'est Notes
  (`methode-gtd.md`), et la suppression reste dans la fiche de la vue Jour.

### Critères de succès

- Une tâche sans date n'a aucune criticité avant `N − horizon` jours, la criticité maximale à `N` jours et au-delà ; une
  tâche datée ne change pas ; réglage à 0 : aucune criticité (`ImplicitDeadlineTest`).
- L'échéance implicite ne rend jamais une tâche « en retard » : palier du tri, pastille d'urgence (`ImplicitDeadlineTest`).
- Une tâche sans date de 60 j finit devant une tâche datée de même priorité et même taille dont l'échéance est dans
  7 j (`ImplicitDeadlineTest`).
- « Traîne » : vraie à partir de `N` jours sans date, fausse pour une tâche datée ou avec le réglage à 0
  (`ImplicitDeadlineTest`).
- Les 480 scénarios d'or restent identiques, rejoués réglage coupé (`DifferentialTest`).
- La revue montre le décompte, la pastille et l'ordre par âge ; toucher une tâche puis « aujourd'hui » la programme
  (`BacklogReviewUiTest`) ; textes dans les six langues (`StringsParityTest`) ; carte, puce et ligne du README
  (`FeatureCatalogTest`).

### Hors périmètre / différé

- Boutons « Vers les Notes » et « Abandonner » dans la revue : convertir ou supprimer une tâche efface ses sessions de
  chrono ; la fiche de la vue Jour garde ce geste, avec sa confirmation.
- La pastille « traîne » de la vue Jour garde sa règle (dernière modification) : elle est figée par le fichier d'or des
  statistiques (`stats.json`) et partagée avec les Stats et l'espace Équipe. La revue dit l'âge réel.
- Une revue guidée pas à pas, tâche par tâche : la liste suffit, et elle est déjà dans l'onglet où l'on fait le point.
- La vue Mois : elle sert à planifier ; la revue est dans la vue Semaine, avec l'ancienne liste.
- L'espace Équipe : le manager voit son backlog entier, trié par score, et l'échéance implicite y joue comme ailleurs.

## 2. Solution technique

### Score (`Scheduling`)

- `Settings.implicitDeadlineDays: Int = 30` (0 = coupé).
- `implicitDeadline(task, settings): LocalDate?` : `null` si la tâche a une échéance ou une date programmée, ou si le
  réglage vaut 0 ou moins ; sinon la date **UTC** de `createdAt` plus `N` jours (même convention que `Staleness`).
- `timeCriticality` : la date la plus proche est l'échéance ou la date programmée, sinon l'échéance implicite ; la rampe
  est inchangée.
- `wsjfBreakdown` : `DateKind.IMPLICIT` et `daysUntil` vers l'échéance implicite quand c'est elle qui sert ;
  `overdue` reste `isOverdue`, qui ne lit que les vraies dates.
- `isOverdue`, `urgencyBucket`, `sortKey.tier`, `sortKey.deadline`, `isEligibleToday` : inchangés (vraies dates
  seulement).
- `isLingering(task, day, settings)` : échéance implicite atteinte (`≤ day`).
- Tout ce qui lit `wsjfScore` ou `sortKey` en profite sans changement : vue Jour (`DayView`), placement, urgence
  héritée des dépendances, file des prévisions solo (`SoloForecast.queue`), plan de charge (`LoadPlan`), suggestion de
  répartition (`AssignmentSuggestion`), « Prêtes » d'équipe (`TeamBoard`). Une sous-tâche sans date dont la mère est
  datée hérite de l'échéance avant le score (`TaskTree.inheritedDeadlines` dans `DayView`) : pas d'échéance implicite.

### Revue (`WeekScreen`)

- `BacklogList` reçoit les tâches (plus leurs seuls titres), triées par `createdAt` croissant, et les réglages.
  Titre : `backlog_title` puis `backlog_lingering` (« · %1$d traînent ») si au moins une traîne.
- Chaque ligne est un `MovableTask(task, from = null, move)` ; ligne qui traîne : `WarnBadge(tag_stale, âge)`, l'âge
  étant les jours depuis la date UTC de `createdAt`.
- `moveTargets(days, from: LocalDate?, today)` : `from` nul pour une tâche sans date, aujourd'hui compris.
- `backlog_implicit_hint` sous `backlog_hint` si `implicitDeadlineDays > 0`.

### Réglage

`int("implicitDeadlineDays", 0)` dans `SettingsForm`, groupe avancé du score (après `urgencyPeak`), textes
`setting_implicit_deadline` et `setting_implicit_deadline_help`.

### Décisions et pièges tracés

- **Une échéance implicite plutôt qu'un terme d'âge** (choix du propriétaire, 2026-11-11). Elle réutilise la rampe
  d'urgence telle quelle : une tâche sans date se comporte, au bout de `N` jours, comme une tâche due ce jour-là, et la
  comparaison avec les tâches datées reste celle de WSJF. Écartés : un bonus d'âge sans borne (fait passer n'importe
  quelle vieillerie devant une P0) ; un bonus plafonné sous l'écart de priorité (ne rattrape jamais une tâche datée de
  même priorité, le problème constaté) ; un créneau de rattrapage quotidien (une seconde règle de placement, contraire
  au « score d'abord »).
- **Rouvre « l'ancienneté n'est jamais une clé de tri »** (`Staleness.kt`), explicitement : `Staleness` reste un signal
  d'affichage ; c'est l'âge depuis la création, par l'échéance implicite, qui entre dans le score.
- **Rouvre « pas de revue hebdomadaire guidée »** (`methode-gtd.md`), à moitié : une liste à faire défiler, pas un
  parcours pas à pas.
- **Jamais en retard** : l'échéance implicite dans le palier dur ferait passer toute vieille tâche devant tout le reste,
  et l'en ferait ressortir dès qu'on la date. Elle ne nourrit que la criticité.
- **`createdAt`, pas `updatedAt`** : retoucher une tâche (estimation, type) ne la fait pas avancer ; seule une date
  posée ou la fin de la tâche arrête l'horloge.
- **Active par défaut, 30 j** (choix du propriétaire) : tout le monde en profite sans réglage. 30 j : une tâche sans
  date ne gagne rien pendant ses 16 premiers jours avec l'horizon par défaut, ce qui laisse le temps de la faire sans
  la presser.
- **Fichiers d'or rejoués réglage coupé** : ils décrivent le moteur sans échéance implicite (leurs tâches ont
  `createdAt = updatedAt`) ; `DifferentialTest` pose `implicitDeadlineDays = 0` plutôt que d'éditer 480 scénarios, et
  `ImplicitDeadlineTest` couvre la montée.
- **Catalogue** : carte « Ce qui traîne » (chapitre « Suivre », après « Ce que Kairos apprend », `since = "4.1.0"`, mène à
  l'onglet Bilan), puce et ligne « What lingers » / « Ce qui traîne » des deux README.
- **La revue reprend « Déplacer »** de la vue Semaine (menu, message « Annuler ») au lieu de boutons propres : un seul
  geste pour dater une tâche dans l'onglet.
