# Vue Semaine

_Rôle : voir la semaine d'un coup d'œil (échéances, fait, créneaux, temps
réel) et ouvrir n'importe quel jour. Fichiers couverts :
`kmp/core/src/commonMain/.../core/week/WeekView.kt` (modèle, pur),
`kmp/ui/.../week/WeekScreen.kt`, `kmp/ui/.../navigation/NavState.kt`, et leur
câblage dans `KairosApp.kt`, `AppShell.kt` (titre) et
`screens/DestinationScreen.kt`. Tests : `core/.../week/WeekViewTest.kt`,
`M4ScreensUiTest.weekOpensAnotherDayAndComesBack`, `WeekMoveUiTest`._

État : **jalon M4** ; « Déplacer » depuis la semaine en 3.7.0 (issue #106). Le temps de la semaine
par type, calculé au jalon M3 (`temps-reel-chrono.md`), s'affiche ici.

## 1. Besoin métier (cahier des charges)

### Objectif / problème

La vue Jour répond à « que faire maintenant » ; il faut aussi voir ce qui
tombe cette semaine et la suivante, ce qui a été fait, et pouvoir préparer
un autre jour.

### Comportement attendu (utilisateur)

- En tête : « Semaine du 28/09 », puis dessous « Semaine précédente » à
  gauche et « Semaine suivante » à droite (même hauteur : à 360 dp l'un passe
  sur deux lignes, l'autre l'imite). La barre de titre dit « Semaine · du lundi 28 septembre
  2026 ».
- « Temps réel cette semaine : 3 h 10 (Dev 2 h · Réunion 1 h 10) » :
  sessions commencées dans la semaine, ventilées par type (types non vides).
- « Réaliste : 12 h à 22 h (tes 8 dernières semaines) · 27 h à échéance ou programmées cette semaine » (#232,
  `previsions-taches.md` § Étape C) : pour la semaine courante ou à venir, avec au moins 4 semaines chronométrées.
- « Rechercher / filtrer » (mêmes facettes que la vue Jour) : réduit les
  listes des jours, jamais la grille.
- « Backlog, sans date (N) », replié : les tâches à faire sans échéance ni
  date programmée, non bloquées, qui sinon n'apparaîtraient nulle part dans
  la grille.
- Sept cartes, du lundi au dimanche (« mar. 29/09 ») ; aujourd'hui en
  conteneur secondaire avec un contour. Dans chaque carte : les créneaux
  (« Déjeuner (12h00-13h00) », occurrences des récurrents comprises ; un créneau importé d'un calendrier ou lu dans un
  calendrier du téléphone porte l'icône `Event`, et les journées entières d'un calendrier importé sont des pastilles sous la date, 3.10.0,
  `connexions-externes.md`), les
  tâches faites ce jour-là (barrées, coche verte), les tâches à faire dont
  l'**échéance** tombe ce jour-là et qui ne sont pas programmées un autre jour (priorité d'abord, sans priorité en
  dernier, avec la pastille de priorité et le projet), puis les tâches
  **programmées pour** ce jour dont l'échéance n'est pas ce jour-là, avec
  la pastille « Programmée » et, si elle a une échéance, son rappel (« échéance 1 oct. ») ; ce rappel prend le contour
  d'avertissement quand l'échéance tombe **avant** le jour programmé. Une tâche programmée n'apparaît **qu'à son jour
  programmé** (3.7.0, issue #106 : avant, elle apparaissait aussi à son échéance), comme elle quitte « Aujourd'hui » dans
  la vue Jour ; une seule ligne par tâche dans la semaine. « — » si rien ; puis
  « Voir le jour », qui ouvre la vue Jour de ce jour (`vue-jour.md` § Un
  autre jour).
- **Déplacer depuis la semaine** (3.7.0, issue #106) : toucher une tâche **personnelle** à faire d'une carte (toute la
  ligne, 48 dp au moins ; le lecteur d'écran dit « Déplacer vers un autre jour… ») ouvre un menu : les jours de la
  semaine affichée, à partir d'aujourd'hui, sauf celui où la tâche est déjà (« jeu. 01/10 ») ; un jour férié ou un
  jour libre (horaires par jour, `planning-personnalise.md`) le dit (« jeu. 01/10 · férié », « sam. 03/10 · jour
  libre ») mais reste choisissable ; puis « Choisir une date… » (sélecteur de date). Choisir un jour **programme** la
  tâche pour ce jour, comme « Déplacer… » de la vue Jour (`vue-jour.md`) : l'échéance ne change pas ; la grille se met à
  jour aussitôt et un message « « X » déplacée au … » propose « Annuler » (la date programmée d'avant revient). Une tâche
  d'équipe assignée à « moi », une tâche faite et un créneau n'ouvrent pas de menu.
- Largeur bureau : sept colonnes égales, de même hauteur. Largeur étroite :
  cartes de largeur égale (160 dp au moins) qui remplissent chaque ligne et
  passent à la ligne, sans défilement horizontal ni moitié de ligne vide.
- Quitter la destination et y revenir ramène à la semaine courante.
- En tête, la bascule « Semaine » / « Mois » (3.8.0) : la vue Mois est l'autre mode de cette destination
  (`vue-mois.md`).

### Critères de succès

- Les sept jours commencent au lundi ; échéances triées par priorité ;
  faites par titre ; créneaux par heure ; un récurrent « jours ouvrés »
  n'apparaît pas le samedi ; le filtre réduit les listes (`WeekViewTest`).
- Une tâche due jeudi apparaît dans la semaine ; « Voir le jour » du
  mercredi ouvre « Jour · mercredi 30 septembre 2026 » ; « Semaine
  suivante » passe au lundi 5 octobre (`M4ScreensUiTest`).
- Une tâche **programmée pour** un jour de la semaine n'apparaît qu'à ce jour, une seule fois (jamais à son échéance si
  elle est un autre jour), avec la pastille « Programmée » ; une tâche faite ou programmée une autre semaine n'y est pas
  (`WeekViewTest.scheduled_tasks_appear_on_their_day_once`).
- Les jours fériés (réglages) et les jours libres (horaires par jour) sont repérés
  (`WeekViewTest.holidays_and_days_off_are_flagged`).
- Toucher une tâche ouvre le menu ; « jeu. 01/10 » la programme pour jeudi, elle quitte le mercredi de son échéance,
  l'échéance ne bouge pas, et « Annuler » rend la date d'avant ; une tâche d'équipe n'a pas de menu ; le menu tient à
  360 dp sans débordement (`WeekMoveUiTest`).
- Aucune carte ne déborde à 420 px (capture `desktop-week-narrow` de
  l'auto-test).

### Hors périmètre / différé

- Glisser une tâche d'un jour à l'autre (le menu suffit et reste le seul chemin accessible), indicateur de charge par
  jour (la vue Mois l'a, `vue-mois.md`).

## 2. Solution technique

Depuis le jalon E1 de l'espace Équipe, l'écran de la vue Semaine lit `repository.personalSnapshot` (la vue Perso, `equipe.md` § Espaces et filtre central) et non la base complète ; sans tâche d'équipe, c'est la même instance.

- `WeekView.build(snapshot, unJour, maintenant, fuseau, filtre)` : lundi de
  la semaine (`TaskStats.monday`), `days` (`WeekDay` : `tasks` = à faire
  filtrées dont `deadline` est ce jour ; `scheduled` = à faire filtrées dont `scheduledDate` est ce jour et `deadline` un autre (même tri, puis titre) ; `tasks` triées `(priorité absente,
  priorité)` ; `done` = faites filtrées dont la date **locale** de
  modification est ce jour, par titre ; `blocks` = ponctuels du jour et
  occurrences des récurrents (`Recurrence.expandRecurringBlocks` sur la
  semaine), par début), `spentMinutes` et `spentByType` (sessions de la
  semaine, `TimeTracking.sessionsInRange` en date locale, types non vides,
  minutes > 0).
- `NavState` (dans `KairosApp`, `remember`) : `day` (jour de la vue Jour,
  `null` = aujourd'hui) et `week` (un jour de la semaine affichée, `null` =
  la courante). Changer de destination remet les deux à `null` ;
  `onOpenDay(jour)` pose `day` et va sur « Jour ».
- `WeekScreen(services, nav, onOpenDay)` : colonne défilante de 1 400 dp au
  plus ; titre au-dessus d'une `Row` des deux boutons `OutlinedButton` à
  chevron séparés par un `Spacer(weight(1f))` (placé entre eux, le titre
  n'avait plus de place sur un téléphone et s'écrivait une lettre par
  ligne) ; `FilterCard` et backlog de la
  vue Jour (`DayView.build(...).backlog` d'aujourd'hui) ; grille :
  `BoxWithConstraints`, `Row` à cartes `weight(1f)` et hauteur
  intrinsèque si 7 × 140 dp + 6 × 8 dp tiennent, sinon `FlowRow`
  (`maxItemsInEachRow` = colonnes) de cartes de largeur égale, autant de
  colonnes de 160 dp au moins que la largeur en permet (une seule carte de
  184 dp laissait la moitié droite vide à 380 dp). `DayCard` : `Surface` `surfaceContainerLow` (aujourd'hui :
  `secondaryContainer` et contour `secondary`), titres sur une ligne avec
  points de suspension, `Badge` des créneaux, `PriorityBadge`, coche
  `CheckCircleFilled` en vert « ok ». Rappel d'échéance d'une tâche programmée : `Badge(deadline_badge)`, ou `WarnBadge`
  si l'échéance précède le jour programmé.
- Placement (`WeekView.build`) : `tasks` = à faire dont `deadline` est ce jour et dont `scheduledDate` est nulle ou ce
  même jour ; `scheduled` = à faire dont `scheduledDate` est ce jour et `deadline` un autre (ou nulle). Une tâche a donc
  au plus une ligne dans la semaine. `WeekDay.holiday` : le jour est dans `Workdays.holidaysFor` des réglages ;
  `WeekDay.dayOff` : `Schedule.windowsFor(jour, réglages)` est vide (jour libre).
- Déplacer (`WeekScreen`) : `MovableTask` enveloppe la ligne d'une tâche `PERSONAL` à faire (`Modifier.clickable`,
  rôle bouton, `onClickLabel` = « Déplacer vers un autre jour… », `heightIn(min = 48.dp)`), avec un `DropdownMenu`
  (`moveTargets` : jours de la semaine `>= aujourd'hui`, sauf le jour de la ligne, libellé `Dates.dayShort` suivi de
  « · férié » / « · jour libre ») et « Choisir une date… » (`DateChoice`, comme la vue Jour). Le choix appelle
  `KairosRepository.schedule(id, jour)` puis le message à annulation (`LocalUndoMessages`, `task_moved`,
  `action_revert`), qui rappelle `schedule(id, dateAvant)`.

### Décisions et pièges tracés

- **Une tâche programmée n'apparaît qu'à son jour programmé** (2026-10-22, issue #106, décision du propriétaire) : rouvre
  « aux deux jours si elle est programmée avant son échéance ». Sinon « Déplacer » ne soulageait pas le jour chargé, et
  la semaine contredisait la vue Jour (la tâche programmée quitte « Aujourd'hui »). L'échéance reste lisible sur la
  ligne (rappel « échéance … »). Écartés : garder les deux lignes (le jour de départ ne se vide pas) et déplacer
  l'échéance (contredit la vue Jour, et une tâche d'équipe n'a pas d'échéance à soi).
- **Toucher la ligne plutôt qu'une icône** : une colonne fait 140 dp en bureau ; une icône de 48 dp par tâche y prenait
  un tiers de la largeur du titre. Le menu n'existe que pour une tâche personnelle à faire : une ligne d'équipe reste
  inerte (pas de faux bouton).
- **Pas de jour passé dans le menu** : programmer hier ramènerait la tâche dans « Aujourd'hui » de la vue Jour ;
  « Choisir une date… » reste libre, comme dans la vue Jour.
- **Férié ou jour libre : signalés, pas interdits** (l'issue le demande) : on peut vouloir avancer un dossier un jour
  chômé.

- **« Fait » du jour en date locale** : la date de
  modification est lue dans le fuseau local, pas en UTC (même règle que
  `vue-jour.md`).
- **Colonnes égales plutôt que cartes fixes en largeur bureau** : à
  1 200 px, sept cartes de 184 dp renvoyaient le dimanche seul sur une
  deuxième ligne (constaté sur la capture de l'auto-test).
- **Le backlog sous le filtre, comme dans la vue Jour** : sans lui, une
  tâche sans date ne serait visible dans aucune carte.
