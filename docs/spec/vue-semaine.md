# Vue Semaine

_Rôle : voir la semaine d'un coup d'œil (échéances, fait, créneaux, temps
réel) et ouvrir n'importe quel jour. Fichiers couverts :
`kmp/core/src/commonMain/.../core/week/WeekView.kt` (modèle, pur),
`kmp/ui/.../week/WeekScreen.kt`, `kmp/ui/.../navigation/NavState.kt`, et leur
câblage dans `KairosApp.kt`, `AppShell.kt` (titre) et
`screens/DestinationScreen.kt`. Tests : `core/.../week/WeekViewTest.kt`,
`M4ScreensUiTest.weekOpensAnotherDayAndComesBack`._

État : **jalon M4**. Reprend la vue Semaine de Kairos 2
(`_build_week_view`, `docs/spec/vue-jour-gtd.md` au tag `v2.6.0`), sans les puces
« journée entière » (événements TimeTree, retirés). Le temps de la semaine
par type, calculé au jalon M3 (`temps-reel-chrono.md`), s'affiche ici.

## 1. Besoin métier (cahier des charges)

### Objectif / problème

La vue Jour répond à « que faire maintenant » ; il faut aussi voir ce qui
tombe cette semaine et la suivante, ce qui a été fait, et pouvoir préparer
un autre jour.

### Comportement attendu (utilisateur)

- En tête : « Semaine du 28/09 », puis dessous « Semaine précédente » à
  gauche et « Semaine suivante » à droite. La barre de titre dit « Semaine · du lundi 28 septembre
  2026 ».
- « Temps réel cette semaine : 3 h 10 (Dev 2 h · Réunion 1 h 10) » :
  sessions commencées dans la semaine, ventilées par type (types non vides).
- « Rechercher / filtrer » (mêmes facettes que la vue Jour) : réduit les
  listes des jours, jamais la grille.
- « Backlog, sans date (N) », replié : les tâches à faire sans échéance ni
  date programmée, non bloquées, qui sinon n'apparaîtraient nulle part dans
  la grille.
- Sept cartes, du lundi au dimanche (« mar. 29/09 ») ; aujourd'hui en
  conteneur secondaire avec un contour. Dans chaque carte : les créneaux
  (« Déjeuner (12h00-13h00) », occurrences des récurrents comprises), les
  tâches faites ce jour-là (barrées, coche verte), les tâches à faire dont
  l'**échéance** tombe ce jour-là (priorité d'abord, sans priorité en
  dernier, avec la pastille de priorité et le projet), « — » si rien ; puis
  « Voir le jour », qui ouvre la vue Jour de ce jour (`vue-jour.md` § Un
  autre jour).
- Largeur bureau : sept colonnes égales, de même hauteur. Largeur étroite :
  cartes de 184 dp qui passent à la ligne, sans défilement horizontal.
- Quitter la destination et y revenir ramène à la semaine courante.

### Critères de succès

- Les sept jours commencent au lundi ; échéances triées par priorité ;
  faites par titre ; créneaux par heure ; un récurrent « jours ouvrés »
  n'apparaît pas le samedi ; le filtre réduit les listes (`WeekViewTest`).
- Une tâche due jeudi apparaît dans la semaine ; « Voir le jour » du
  mercredi ouvre « Jour · mercredi 30 septembre 2026 » ; « Semaine
  suivante » passe au lundi 5 octobre (`M4ScreensUiTest`).
- Aucune carte ne déborde à 420 px (capture `desktop-week-narrow` de
  l'auto-test).

### Hors périmètre / différé

- Glisser une tâche d'un jour à l'autre, vue mois (comme Kairos 2).
- Tâches programmées (`scheduledDate`) dans la grille : seule l'échéance
  place une tâche dans un jour (comme Kairos 2).

## 2. Solution technique

Depuis le jalon E1 de l'espace Équipe, l'écran de la vue Semaine lit `repository.personalSnapshot` (la vue Perso, `equipe.md` § Espaces et filtre central) et non la base complète ; sans tâche d'équipe, c'est la même instance.

- `WeekView.build(snapshot, unJour, maintenant, fuseau, filtre)` : lundi de
  la semaine (`TaskStats.monday`), `days` (`WeekDay` : `tasks` = à faire
  filtrées dont `deadline` est ce jour, triées `(priorité absente,
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
  intrinsèque si 7 × 140 dp + 6 × 8 dp tiennent, sinon `FlowRow` de cartes
  de 184 dp. `DayCard` : `Surface` `surfaceContainerLow` (aujourd'hui :
  `secondaryContainer` et contour `secondary`), titres sur une ligne avec
  points de suspension, `Badge` des créneaux, `PriorityBadge`, coche
  `CheckCircleFilled` en vert « ok ».

### Décisions et pièges tracés

- **« Fait » du jour en date locale** : Kairos 2 comparait la date UTC de
  modification (même écart volontaire que `vue-jour.md`).
- **Pas de puces « journée entière »** : elles venaient de TimeTree, retiré.
- **Colonnes égales plutôt que cartes fixes en largeur bureau** : à
  1 200 px, sept cartes de 184 dp renvoyaient le dimanche seul sur une
  deuxième ligne (constaté sur la capture de l'auto-test).
- **Le backlog sous le filtre, comme dans la vue Jour** : sans lui, une
  tâche sans date ne serait visible dans aucune carte.
