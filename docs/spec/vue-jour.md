# Vue Jour et flux GTD

_Rôle : l'écran d'ouverture de Kairos, où l'on capture, qualifie, ordonne
et fait ses tâches. Fichiers couverts :
`kmp/core/src/commonMain/.../core/day/DayView.kt` (modèle de l'écran, pur),
`kmp/ui/src/commonMain/.../ui/day/` (`DayScreen.kt`, `TaskRow.kt`,
`Why.kt`, `NowCard.kt`, `Timeline.kt`, `Capture.kt`, `Blocks.kt`,
`Filters.kt`, `Qualify.kt`, `EditTaskDialog.kt`, `SubtaskEditor.kt`, `PointsGuide.kt`,
`Levels.kt`, `Dates.kt`). Tests : `core/.../day/DayViewTest.kt`,
`ui/.../day/DatesTest.kt`, `desktopApp/.../DayScreenUiTest.kt` (clics et
clavier réels), `desktopApp/.../M4ScreensUiTest.kt` (autre jour, guide des
points, durée suggérée), `data/.../SubtaskEditTest.kt` (sous-tâches
existantes), `desktopApp/.../SubtaskEditUiTest.kt` (section « Sous-tâches »)._

État : **jalons M2 et M4**. Le moteur est décrit par `ordonnancement.md`, `dependances.md`
et `recurrence.md`. Le chrono (bouton de chaque ligne, « Démarrer le
chrono » de « Maintenant », carte « En ce moment », temps passé, temps du
jour par type, rail du réel sur la frise, alertes) est décrit par
`temps-reel-chrono.md` (jalon M3). Le jalon M4 ajoute la vue d'un autre
jour (ouverte depuis la vue Semaine, `vue-semaine.md`), l'aide « Comment
qualifier ? », le guide des points fondé sur l'historique et les durées
suggérées ; les repères viennent de `statistiques.md`.

## 1. Besoin métier (cahier des charges)

### Objectif / problème

À l'ouverture, répondre à trois questions sans changer d'écran : qu'est-ce
qui n'est pas encore clarifié, qu'est-ce que je fais maintenant, qu'est-ce
qui vient ensuite. Cycle GTD : **capturer** sans friction, **traiter** la
boîte de réception (priorité et taille), **faire** dans l'ordre du score.

### Comportement attendu (utilisateur), de haut en bas

1. **Bandeau de stockage** (version web seulement, `export-import.md`).
2. **Capture**, toujours visible, deux volets : « Tâche » (titre seul ;
   Entrée ajoute, le champ se vide et garde le curseur) et « Créneau / deep
   work » (titre, jour, début, fin, case deep work, récurrence ; puis la
   liste des créneaux du jour, chacun modifiable ; un créneau importé d'un calendrier porte « Importé de « Nom » » et
   se supprime seulement ; celui d'un calendrier du téléphone porte « Calendrier du téléphone « Nom » » et n'a aucune
   action, `connexions-externes.md`). Les journées entières d'un calendrier importé sont des
   pastilles sous le titre « Aujourd'hui, dans l'ordre », sans effet sur le planning.
   **Avis des mois sautés** (3.13.0, issue #111, `recurrence.md` § Mois sautés), sous la capture et seulement sur le
   vrai aujourd'hui : une ligne à contour par série « le N du mois » qui n'a pas d'occurrence un mois passé (« 2
   occurrences mensuelles ont été sautées : Note de frais »), avec « Les recréer » (occurrences déjà en retard) et
   « Ignorer » ; chacun affiche un message avec « Annuler ».
3. **À traiter** : toute tâche sans priorité **ou** sans points, avec ce qui
   manque, qualifiable en un clic par pastilles dont le sens est écrit
   (P0 Critique · P1 Important · P2 Utile ; 1 trivial … 21 énorme).
   Recliquer une valeur la retire. Vide : « Rien à traiter : tout est déjà
   clarifié » ; vidée par un filtre actif seulement : « Aucune tâche à
   traiter ne correspond au filtre » (ne pas annoncer que tout est clarifié). Une tâche **bloquée** non qualifiée n'y figure pas : elle reste dans « Bloquées » (une tâche ouverte, une seule section). Sous l'en-tête, « Comment qualifier ? » (replié) : pourquoi
   qualifier, le sens des priorités (importance, le délai compte à part par
   l'échéance), puis le guide des points (voir **Guide des points**).
4. **Maintenant** (seul bloc teinté) : « À faire maintenant : » la première
   tâche placée (avec son heure), sinon la première sans créneau ; boutons
   nommés « Fait », « Démarrer le chrono » (ou « Arrêter le chrono ») et
   « Décaler » ; bilan : faites aujourd'hui, à faire, « requis … · disponible
   … », « la journée déborde de … » s'il y a lieu, temps travaillé
   aujourd'hui et par type ; état des alertes du chrono
   (`temps-reel-chrono.md`).
5. **Bandeau de surcharge** si plus de P0 non bloquées que le seuil.
6. **Aujourd'hui, dans l'ordre** : les tâches placées par heure, ordre du
   score ; étiquettes « épinglée », « deep work », « chemin critique » ;
   notes « à partir de 14h05, après « Réunion » » (« (épinglée) » si
   l'obstacle est une tâche épinglée, jamais pour une fenêtre de deep work :
  `ObstacleKind.DEEPWORK`, que le test différentiel range avec `PINNED`), « créneau creux (~15 h) : tâche
   légère privilégiée », « heure préférée pour « Dev » » ou « ton heure habituelle pour « Dev » » (3.6, meilleures heures :
   seulement si elles ont changé le choix du créneau), « chevauche « Réunion » ». Vide : « Aucune tâche à
   faire n'est actuellement planifiée ».
7. **Sections secondaires**, chacune avec son compte et une phrase de rôle,
   absentes si vides : « Sans créneau aujourd'hui » (**dépliée**),
   « Bloquées » (« en attente de : … »), « Programmées plus tard »,
   « Tâches mères en cours » (« 1/2 sous-tâche(s) »), « Fait » (terminées
   aujourd'hui). Les quatre dernières sont repliées.
8. **Rechercher / filtrer** (repliée) : recherche dans le titre, la
   description et le projet, facettes priorité, projet, type, points ;
   « filtre actif » et « Réinitialiser ». Un filtre actif remonte au-dessus
   de l'agenda. Il réduit les listes, jamais le planning.
9. **Backlog, sans date** (replié, toujours présent) : tâches à faire sans
   échéance ni date programmée, non bloquées. Elles comblent quand même les
   trous de l'agenda selon leur score (`isEligibleToday`) ; la phrase de rôle
   le dit, et dit qu'aucun jour de la semaine ne les affiche.
10. **En ce moment** (chrono en cours) puis **Agenda** (frise ; 3.4 : de l'heure de début à l'heure de fin **du jour** quand il a un
    horaire propre, `DayView.workStartHour`/`workEndHour` ; un **jour libre** garde la journée des réglages et une ligne « Jour libre :
    rien n'est planifié automatiquement… », `DayView.freeDay` ; 3.5, plusieurs plages : `DayView.windows`, les trous entre plages grisés et le nom du contexte de chaque plage
    réservée en pastille ; une tâche « Sans créneau » dont le contexte n'a aucune plage ce jour-là porte l'étiquette « Pas de plage Maison aujourd'hui », `DayView.noWindow`) : la journée
    de travail heure par heure, créneaux, tâches placées et rail du temps
    chronométré ; à droite de la liste en largeur bureau, en bas sinon.

**Ligne de tâche** (toutes sections) : coche ronde ; corps : heure (agenda),
« Mère › » pour une sous-tâche, titre, puis étiquettes (projet, type,
durée, « échéance 30 sept. », « programmée 2 oct. », icône de récurrence,
temps passé ou minuteur vivant, « traîne depuis N j ») qui passent à la ligne, puis la description sur une
ligne, dépliable d'un clic ; colonne « clés » alignée à droite : score (qui
s'ouvre sur « Pourquoi à cette place ? »), priorité (P0 en rouge), points ;
actions : chrono et décaler (tâches à faire), modifier (une ligne de « Fait »,
sans action, garde une marge de 12 dp au bord droit). En largeur étroite
(< 600 dp), la colonne « clés » passe sous le corps **et les actions la
rejoignent** sur une même ligne (clés à gauche, actions à droite, `RowActions`) :
à 400 dp, trois boutons à droite ne laissaient au titre que 175 px, soit trois
ou quatre lignes par titre (test exploratoire du 2026-10-11) ; la coche ne bouge
pas. Liseré rouge de 3 dp à gauche pour
une tâche en retard. Tâche bloquée : fond de surface, contour, titre
atténué.

**Pourquoi à cette place ?** : valeur de la priorité, ce qu'ajoute la date
la plus proche (« Échéance dépassée de 12 j », « Date programmée demain »,
« Aucune échéance »), l'effort et sa provenance, le score ; une tâche en
retard le dit en tête. Le score n'est affiché que pour une tâche qualifiée. Quand les meilleures heures ont fait préférer la tâche sur son créneau (3.6),
une phrase de plus le dit (« Placée ici car tu travailles d'habitude les tâches « Dev » vers cette heure » ou « … car tu as réglé une heure préférée pour « Dev » ») et rappelle
que ce n'est qu'une préférence : l'urgence et les tâches en retard passent d'abord (`ScoreBadge(affinity)`, lu sur `DayView.schedule`).

**Guide des points** (« Comment estimer les points ? », replié) : estimer
en taille relative (volume × complexité × incertitude), en comparant à ses
propres tâches terminées ; pour chaque palier, son sens (« 3 · modéré : bien
cadré, zéro inconnue »), puis le temps réel médian observé chez
l'utilisateur (« chez toi ≈ 45 min (médiane sur 6 tâche(s)) », « peu
fiable » sous 3 tâches) et les titres de ses deux dernières tâches
terminées à ce palier ; sans historique à un palier, l'exemple générique et
« aucune de tes tâches terminées à ce palier pour l'instant ». Enfin, la
différence entre points (l'ordre) et durée (le placement).

**Édition** (dialogue) : titre, description, priorité, points (puis le
guide des points), échéance, durée ; « Options avancées » : programmée pour, projet, temps passé manuel,
récurrence (aucune, quotidienne, jours ouvrés, hebdomadaire, mensuelle,
« le … du mois » avec le jour), type, heure fixe (HH:MM, vide = aucune),
nouvelles sous-tâches (une par ligne), « Bloquée par » (cases à cocher).
Quand la tâche a déjà des sous-tâches (issue #69, 3.3.0), une section
**« Sous-tâches »** les liste dans les essentiels, avant « Options
avancées » : case « faite », titre modifiable, corbeille (la ligne reste, barrée,
avec « Annuler la suppression »). Rien n'est écrit avant « Enregistrer » ;
« Annuler » abandonne tout. Une tâche d'équipe n'a pas cette section.
Un seul « Enregistrer » ; « Supprimer » avec confirmation. Seul le contenu
défile : « Supprimer », « Annuler » et « Enregistrer » restent en bas de la
fiche (à 900x700, « Enregistrer » était hors de vue sous les options
avancées). **Durée
suggérée** : choisir un palier de points dont le temps réel médian est
fiable (3 tâches au moins) **remplace** la durée par cette médiane ; choisir
un type fiable ne la remplit que si elle est vide. On reste libre de la
retoucher. **Durée apprise** (3.4, `durees-apprises.md`) : sans estimation, une tâche à faire dont les occurrences passées chronométrées
sont assez nombreuses affiche un badge `~45 min` (icône et description d'accessibilité, à la place du badge d'estimation), la
carte « Maintenant » la même durée, « Pourquoi à cette place ? » la provenance ; peu fiable, elle est dite sans être appliquée. La
fiche propose une puce « Utiliser 45 min (tes 5 dernières) » sous le champ des minutes quand il est vide : elle le remplit sur
action seulement. Dialogue de
créneau : mêmes champs que la capture de créneau, « Supprimer » qui dit
« toutes ses occurrences » pour un récurrent. Pour un récurrent (3.8.0, issue #110), le dialogue s'ouvre sur
« Seulement celle-ci » (titre et heures du jour affiché, « Sauter ce jour », « Rétablir ») ou « Toutes les
occurrences » (le formulaire complet) ; la liste des créneaux du jour montre l'occurrence du jour, une occurrence
sautée barrée avec « sauté ce jour », une modifiée avec « modifié ce jour » (`recurrence.md` § Une seule occurrence).

**Première tâche** (3.4, `premiere-tache.md`) : tant que l'utilisateur n'a aucune tâche à lui (les exemples ne comptent pas), une carte
d'invitation ouvre la fiche en **création** (identifiant 0, sans « Supprimer », titre requis) accompagnée d'un panneau qui explique
chaque champ (`EditField`) ; la création réelle déclenche les félicitations et l'explication de la place de la tâche (ligne cernée).

**Dates, déplacer, blocage** (3.4, issue #64, `planning-personnalise.md` lot A).
Les champs date de la fiche (échéance, « Programmée pour ») et le jour d'un
créneau ont un bouton « Choisir une date » (sélecteur Material 3) ; l'échéance et
« Programmée pour » ont en plus les raccourcis « Aujourd'hui », « Demain », « Lundi
prochain », « Dans une semaine » (omis quand c'est le lundi prochain) et « Effacer ».
La saisie accepte `2026-09-30`, `2026-9-30`, `2026/9/30` et, en français, `30/09/2026`
(jour, mois, année ; l'ordre mois/jour de l'anglais est refusé : ambigu). Les libellés
n'affichent plus « (AAAA-MM-JJ) ». Les raccourcis partent du **vrai** aujourd'hui, pas du
jour affiché. « Décaler » met l'échéance au jour ouvré suivant **et** programme la
tâche personnelle pour ce jour : elle quitte « Aujourd'hui » jusque-là. « Déplacer… »
(icône calendrier, `Déplacer vers un autre jour…`) ouvre un menu (« Demain », « Lundi
prochain », « Dans une semaine », « Choisir une date… ») qui programme la tâche
personnelle pour ce jour sans toucher à l'échéance ; un message « « X » déplacée au … »
propose « Annuler » (la date d'avant revient). Une tâche d'équipe n'a pas « Déplacer… ».
Quand une édition **ajoute** un bloqueur à une tâche à faire, la section « Bloquées »
s'ouvre pour cette fois et un message dit « « X » attend « Y » ». La fiche ouvre d'emblée
ses « Options avancées » si la tâche a un bloqueur ou une date programmée ; l'aide de
« Bloquée par » dit que la tâche attend dans « Bloquées ». Sur la ligne du **bloqueur**
(à faire), un badge « Bloque « X » » (ou « Bloque N tâches »).

**Un autre jour** : « Voir le jour » d'une carte de la vue Semaine ouvre la
vue Jour de ce jour-là, titrée « Jour · mercredi 30 septembre 2026 » : même
écran, planning calculé depuis le début de la journée de travail (l'heure
courante ne compte que pour aujourd'hui), « Fait » de ce jour-là, créneaux de
ce jour proposés à la capture, et un bouton « Revenir à aujourd'hui » en
tête. Changer de destination ramène à aujourd'hui.

**Raccourcis clavier** (bureau et web ; indiqués à côté de leur contrôle,
masqués sur Android) : `N` met le curseur dans la capture de tâche (en
revenant au volet « Tâche »), `/` ouvre la recherche et y met le curseur.
Inactifs pendant une saisie et avec Ctrl, Cmd ou Alt.

### Critères de succès

- Une tâche capturée apparaît aussitôt dans « À traiter », jamais dans le
  planning tant qu'il lui manque la priorité ou les points ; qualifiée d'un
  clic chacune, elle entre dans l'agenda (`DayScreenUiTest`).
- Toute tâche ouverte est dans une seule section (`DayViewTest`).
- « Pourquoi à cette place ? » s'ouvre d'un clic et se referme d'un clic à
  côté ; « Fait » et « Décaler » de « Maintenant » agissent sur la bonne
  tâche ; un créneau saisi apparaît dans la liste du jour ; l'édition pose
  un bloqueur et une heure fixe ; `N` et `/` placent le curseur
  (`DayScreenUiTest`).
- Aucune ligne ne déborde horizontalement à 420 px (captures de l'auto-test).
- Depuis la fiche de la mère, on renomme, coche et supprime ses sous-tâches
  existantes en un seul « Enregistrer » ; « Annuler » ne change rien ; une tâche
  sans sous-tâche n'a pas la section (`SubtaskEditUiTest`, `SubtaskEditTest`).
- Une bloquée non qualifiée n'est que dans « Bloquées » (`DayViewTest.a_blocked_unqualified_task_is_listed_only_in_blocked`) ;
  un bloqueur liste les tâches qu'il bloque tant qu'il est à faire
  (`DayViewTest.a_blocker_lists_the_tasks_it_blocks_while_it_is_open`) ; la section « Bloquées » et le badge
  « Bloque … » apparaissent après l'édition (`DayScreenUiTest.editDialogSetsBlockerAndFixedTime`).
- « Décaler » programme la tâche personnelle à sa nouvelle échéance
  (`KairosRepositoryTest.snoozeAlsoSchedulesAPersonalTaskOnItsNewDeadline`) ; « Déplacer… » programme sans toucher à
  l'échéance et « Annuler » rend la date d'avant (`KairosRepositoryTest.scheduleMovesAPersonalTaskWithoutTouchingItsDeadline`,
  `DayScreenUiTest.moveSchedulesATaskOnAnotherDayAndCanBeUndone`).
- Saisie souple des dates et raccourcis (`DateInputTest`).
- Depuis la vue Semaine, « Voir le jour » ouvre le mercredi, titré ; « Revenir
  à aujourd'hui » y ramène (`M4ScreensUiTest`).
- Avec trois tâches terminées à 3 points et 45 min chacune, le guide dit
  « chez toi ≈ 45 min (médiane sur 3 tâche(s)) » avec deux de leurs titres,
  et choisir 3 points pose 45 min (`M4ScreensUiTest`).

## 2. Solution technique

Depuis le jalon E1 de l'espace Équipe, l'écran de la vue Jour lit `repository.personalSnapshot` (la vue Perso, `equipe.md` § Espaces et filtre central) et non la base complète ; sans tâche d'équipe, c'est la même instance. Depuis le jalon E3, une tâche d'équipe assignée à « moi » y porte la marque « Équipe » (`TeamMark`), une ligne de plus dans « Pourquoi à cette place ? », un bloqueur d'un collègue nommé « Titre (assigné) », et sa fiche montre « Assigné à » en lecture seule (`equipe-backlog-suivi.md` § Interface). Depuis le jalon E6, une tâche **reçue** d'un manager par paquet porte le badge neutre « de Corentin » (`ReceivedMark`), ou « retirée par Corentin » en contour avec icône d'alerte si le manager l'a retirée ; sa fiche d'édition montre la marque, une aide, le curseur d'avancement (`setReceivedProgress`) et, pour une tâche retirée, « Garder comme tâche personnelle » (`equipe-echanges.md` § Interface). Sans tâche reçue, rien ne change.

### Modèle (`DayView.build`, `core`)

`DayView.build(snapshot, jour, maintenant, fuseau, filtre)` rend en une fois
tout ce que l'écran affiche : `schedule` (planning complet, jamais filtré),
listes filtrées (`inbox`, `agenda`, `unscheduled`, `later`, `blocked` avec
motifs, `parents` avec avancement, `doneToday`, `backlog`), `nextUp` et son
heure, `why` (tâches qualifiées), `buckets`, `staleDays`, `raised`,
`parentTitle`, `blockersOf`, `openTasks` (candidats bloqueurs, par titre),
`priorityOverload`, `projects`, `editableBlocks` (ponctuels du jour et
modèles récurrents qui y tombent, par heure), `dayBlocks` (créneaux
effectifs), `timeline`, `holidays`. Le backlog est trié par priorité (sans
priorité en dernier) puis titre ; « Fait » par modification décroissante.
Une tâche mère bloquée n'est que dans `blocked`, pas dans `parents`
(une tâche ouverte, une seule section).
Pour un autre jour que celui de l'heure courante, le placement part du
début de la journée de travail (`Scheduling.buildDaySchedule` n'utilise
l'heure que si elle tombe ce jour-là).
`DayFilter` : recherche insensible à la casse sur titre, description et
projet ; `active` si la recherche n'est pas vide ou qu'une facette est
posée.

### Écran (`DayScreen`, `ui`)

- `DayScreen(services, selectedDay, onBackToToday)` : jour affiché =
  `selectedDay` (posé par la vue Semaine dans `NavState.day`) ou
  aujourd'hui ; « Revenir à aujourd'hui » (`OutlinedButton`, en tête de liste)
  seulement pour un autre jour. `ensureCalendarOccurrences` reste appelé
  pour **aujourd'hui** (l'occurrence du mois courant), quel que soit le jour
  affiché. Titre de la barre : `AppShell` (`navigation-theme.md`).
- Avis des mois sautés (3.13.0, `recurrence.md` § Avis des mois sautés) : `Recurrence.skippedSeries` sur
  `personalSnapshot`, mémorisé sur les tâches, le jour et `settings.recurrence` ; un `SkippedSeriesNotice` par série
  (clé `skipped-<jour>|<titre>`) entre `capture` et `inbox-title`, seulement si le jour affiché est aujourd'hui.
- L'heure est lue sur `services.clock` et réévaluée à chaque minute
  (`produceState`) ; `DayView` est recalculé quand la base, la minute ou le
  filtre changent. `LaunchedEffect(jour, tâches)` appelle
  `ensureCalendarOccurrences`.
- `BoxWithConstraints` : deux colonnes (liste de 840 dp au plus, frise de
  320 dp) à partir de 900 dp de large ; en dessous, frise en fin de liste.
  Lignes « compactes » sous 600 dp de liste.
- `LazyColumn` construite par `ListBuilder`, qui retient la position de
  chaque entrée nommée (capture, filtres, sections) pour y faire défiler les
  raccourcis. États d'ouverture des sections : `mutableStateMapOf`, gardés
  tant que l'écran vit.
- Racine focalisable (`focusRequester` + `focusable` + `onKeyEvent`, marque
  de test `day-screen`) ; elle reprend le focus après la première image, à
  la fermeture d'un dialogue et à tout clic dans le vide (`detectTapGestures`,
  après les éléments cliquables qui consomment le leur) : sans cela, le focus
  n'était plus nulle part et `N` / `/` restaient sourds. Les raccourcis sont
  inactifs pendant une saisie : le champ ne consomme pas toujours la touche (le
  caractère arrive en « KeyTyped »), donc chaque champ de la vue Jour (capture,
  titre / date / heures d'un créneau, recherche) porte `quietShortcuts()` qui
  arrête `N` et `/` avant l'écran ; sans cela, « n » dans le titre d'un créneau
  renvoyait vers « Nouvelle tâche » et le titre était perdu
  (`DayScreenUiTest.theShortcutsStayQuietWhileTypingTheTitleOfASlot`).
- `TaskRow(tâche, RowContext, heure?, bloquée, modifiable, description,
  before, after, extra)` : `RowContext` porte la vue, la langue, le mode
  compact, les actions (chrono compris) et l'horloge du minuteur vivant. Formes et couleurs de la charte : `Badge` neutre,
  `WarnBadge` (contour et icône, jamais d'ambre), `ErrorBadge` (conflit),
  `PriorityBadge`, score en chiffre primaire (`ScoreBadge`, menu déroulant
  MD3 de 300 dp).
- `NowCard` : `primaryContainer`, `Button` « Fait », `OutlinedButton`
  du chrono, `TextButton` « Décaler », badges du bilan (vert « ok » pour les
  faites), temps du jour, état des alertes. `RunningCard` (« En ce
  moment ») : `inverseSurface`, bouton `inversePrimary`.
- `TimelineCard` : carte à contour, 1 min = 1 dp, graduations par heure,
  8 dp de marge en haut et en bas ; occupé en conteneur de surface le plus
  haut, travail et épinglée en conteneur secondaire (contour pour
  l'épinglée), conflit en conteneur d'erreur, deep work en tertiaire
  (hachures dessinées pour le bloc réservé) ; libellé « heure · titre » si
  l'entrée fait au moins 14 min, sauf pour un fond (deep work, occupé) dont
  le haut est recouvert par une tâche commençant moins de 16 min après lui
  (les deux libellés se chevauchaient).
- `Capture` : `SingleChoiceSegmentedButtonRow` ; `CaptureState` (volet,
  `FocusRequester` du champ). `BlockFormState` / `BlockFields` : jour
  (AAAA-MM-JJ), début et fin (`Dates.parseTime` accepte « 9:30 »,
  « 09h30 », « 14h »), fin après début sinon erreur et bouton inactif ;
  `LabeledCheckbox` (libellé cliquable, 48 dp).
- `FilterCard` : carte à contour repliable, `ExposedDropdownMenuBox` par
  facette.
- `Estimates.of(tâches, sessions, maintenant)` (recalculé quand la base
  change) : `references` (`TaskStats.fibonacciReferences`), et les médianes
  **fiables** et non nulles par palier (`minutesByPoints`, depuis
  `fibonacciCalibration`) et par type (`minutesByType`, depuis
  `calibrationByType`), sur toutes les tâches, archivées comprises, temps
  passé = sessions + saisie manuelle. `PointsGuide` (dialogue) et
  `InboxHelp` (« À traiter ») : `TextButton` à chevron qui déplie le texte ;
  sens et exemples génériques par `Levels.pointsMeaning` /
  `pointsExample`. `EditTaskDialog` reçoit `estimates` : `PointsPills`
  remplace la durée par `minutesByPoints[palier]`, `TypeField` ne remplit
  qu'une durée vide avec `minutesByType[type]`.
- `Dates` : « 30 sept. » / « Sep 30 », « 09h15 » / « 09:15 », graduations
  « 9h » / « 9:00 », nombres à une décimale sans « .0 » (deux pour une valeur positive
  qui s'arrondirait à 0 : un score de 1/21 s'affiche « 0,05 », jamais « 0 ») ;
  la virgule en français (`Dates.number(valeur, langue)` : « 7,6 », « 7.6 » en anglais) ;
  `Dates.short(date, langue, référence)` ajoute l'année si elle diffère de celle
  du jour affiché (« 29 févr. 2028 », « Feb 29, 2028 ») : échéances et jours
  prévus des lignes de tâche, du Backlog et du Suivi d'équipe ; `duration()` :
  « 1 h 30 », « 2 h », « 45 min » ; `long` : « mardi 29 septembre 2026 »
(« 1er » le premier du mois) / « Tuesday, September 29, 2026 » ;
`dayShort` : « mar. 29/09 » / « Tue 9/29 » ; `dayMonth` : « 28/09 » /
« 9/28 ».

### Sous-tâches existantes (`SubtaskEditor.kt`, issue #69)

- `DayScreen` passe à `EditTaskDialog` `subtasksOf(id, tâches)` : les sous-tâches non
  archivées de la tâche, dans l'ordre de création, **seulement pour une tâche perso**
  (`TaskSpace.PERSONAL`) ; une tâche d'équipe (`TeamTaskDialog`) ne reçoit rien.
- La fiche garde un `SubtaskDraft` par sous-tâche (titre, fait, supprimée), figé à
  l'ouverture (`remember(task.id)`) : un changement de la base pendant que la fiche
  est ouverte ne réécrit pas les saisies. `SubtaskEditor` : une ligne par brouillon
  (case « faite » `toggleable` 48 dp, `OutlinedTextField` d'une ligne, corbeille
  `KairosRowIconButton` en couleur d'erreur) ; une ligne supprimée reste, barrée, avec
  « Annuler la suppression ».
- « Enregistrer » : `List<SubtaskDraft>.edits(originales)` ne garde que les brouillons
  qui diffèrent de la tâche d'origine (titre nettoyé ≠ titre, état, suppression) et
  les passe dans `TaskEdit.subtaskEdits` ; `updateTask` les applique dans la même
  transaction (`modele-donnees.md`).
- Le champ « Nouvelles sous-tâches » reste dans les options avancées, inchangé.

### Décisions et pièges tracés

- **Sous-tâches modifiables depuis la mère** (issue #69, « après la création, aucun
  moyen d'éditer ses enfants ») : une sous-tâche s'ouvrait seule depuis sa ligne
  mais la fiche de la mère ne les montrait pas. Écarté : une liste de liens vers
  la fiche de chaque sous-tâche (un clic de plus, et aucun « tout en un seul
  Enregistrer »). Modifications en attente dans le dialogue, comme tout le reste de la fiche.
- **`write` n'est pas réentrant** : cocher une sous-tâche depuis `updateTask`
  réutilise `toggleInTransaction`, extrait de `toggleDone`, plutôt que de l'appeler
  (verrou déjà pris : blocage).
- **Défiler seulement après composition** : un raccourci qui change l'état
  (volet « Tâche », section ouverte) puis fait défiler la liste dans la même
  image fait échouer la liste paresseuse (erreur interne de Compose,
  constatée par `DayScreenUiTest`). On attend deux images
  (`awaitComposed`), on défile, on attend encore, puis on donne le focus.
  Un `delay` fixe ne convient pas non plus : l'horloge de test ne l'avance
  pas.
- **Focus racine après la première image** : demandé plus tôt, l'écran
  n'est pas encore attaché et les raccourcis restaient sourds.
- **« Fait » du jour en heure locale** (fuseau injecté) : comparer la
  date UTC de modification ferait tomber la veille une tâche faite à 0 h 30 à
  Paris.
- **Pas de « Décaler » sur une tâche faite** (sans utilité dans « Fait »).
- **« Décaler » programme la tâche** (décision du propriétaire, lot A ; écartés : ne changer que l'échéance, qui laissait la
  tâche dans « Aujourd'hui », et fusionner avec « Déplacer… »). `KairosRepository.snooze` pose `scheduledDate` = la nouvelle
  échéance pour une tâche **personnelle** seulement : `scheduledDate` n'est pas un champ d'équipe (la requalification d'une
  tâche d'équipe reste celle de l'échéance).
- **Le filtre « une tâche, une section » est dans `DayView`**, pas dans le moteur : `Scheduling.buildDaySchedule` garde sa
  liste `toProcess` (figée par les fichiers d'or, qui y rangent aussi une bloquée non qualifiée) ; `DayView.inbox` en retire
  les bloquées.
- **Annuler passe par `LocalUndoMessages`** (snackbar avec action, fournie par `AppShell`) ; `LocalMessages` reste pour les
  messages sans action.
- **Saisie `JJ/MM/AAAA` seulement en français** : l'anglais M/J/AAAA serait ambigu (3/4) ; ISO partout.
- **Score affiché « 16 », pas « 16.0 »** : même format que « Pourquoi à
  cette place ? » (le badge n'affiche pas l'arrondi brut).
- **Sections repliables sans carte englobante** : dans une liste paresseuse,
  une carte ne peut pas contenir plusieurs éléments ; titre cliquable avec
  chevron, puis lignes.
- **Un autre jour, pas de navigation jour par jour** : on y arrive par la vue
  Semaine sans flèches « veille /
  lendemain » dans la vue Jour. La décision M2 « aujourd'hui seulement » est
  levée par la vue Semaine.
- **Durée suggérée : le palier remplace, le type complète** (issues #15.6 et #7) : choisir un palier est un geste
  d'estimation, qui doit changer la durée ; un type n'est qu'un indice, qui ne
  doit jamais écraser une estimation saisie. Les pastilles de « À traiter »
  ne touchent jamais à la durée.
- **Guide replié par défaut**, dans le dialogue comme dans « À traiter » :
  il est long, et la phrase d'aide courte reste visible.
- **Jour et heures saisis à part** pour un créneau : un champ
  `datetime-local` n'a pas d'équivalent commun à Compose.
- **Projet dans les options avancées** ; le type garde une
  valeur retirée de la liste des réglages (décision M1).
- **Opacité interdite sur une ligne bloquée** : on ne baisse pas l'opacité
  (fond et contour à la place), même si le dialogue de Compose, fenêtre à
  part, n'hérite pas de l'opacité.
- **Priorité et points passent sous le corps en largeur étroite** :
  décision M1 rouverte et tranchée au jalon M2.
