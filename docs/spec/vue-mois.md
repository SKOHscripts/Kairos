# Vue Mois

_Rôle : voir le mois d'un coup d'œil (charge, échéances, fériés et jours libres, séries mensuelles) et ouvrir n'importe
quel jour. Fichiers couverts : `kmp/core/src/commonMain/.../core/month/MonthView.kt` (modèle, pur),
`kmp/ui/.../month/MonthScreen.kt`, la bascule `WeekMonthSwitch` de `kmp/ui/.../week/WeekScreen.kt`,
`NavState.monthMode` / `NavState.month`, le titre dans `AppShell.kt` et l'aiguillage de `screens/DestinationScreen.kt`.
Tests : `core/.../month/MonthViewTest.kt`, `desktopApp/.../MonthViewUiTest.kt`._

État : 3.8.0 (issue #107), implémentée.

## 1. Besoin métier (cahier des charges)

### Objectif / problème

Pour les échéances et les routines mensuelles, la semaine est trop courte : on veut voir le mois entier, quels jours sont
libres ou fériés, où les échéances s'accumulent, où tombe une série mensuelle, quels jours sont déjà trop chargés.

### Comportement attendu (utilisateur)

- La destination « Bilan » (« Semaine » jusqu'au 2026-11-11, `bilan.md`) s'ouvre sur une bascule « Semaine » / « Mois » (« Week » / « Month ») ; la vue Mois n'est
  pas une sixième destination. Passer de la semaine au mois montre le mois de la semaine affichée ; revenir à la
  semaine montre la semaine du 1er du mois affiché (la semaine courante si c'est le mois courant). Le mode choisi reste
  le temps de la session ; changer de destination ramène à la période courante, dans ce mode.
- La barre de titre dit « Mois · novembre 2026 » ; en tête, une ligne « ‹ novembre 2026 › » (boutons à icône « Mois
  précédent » et « Mois suivant », « Revenir à aujourd'hui » hors du mois courant ; 4.1.0, `bilan.md`), la carte
  « Bilan du mois » (4.1.0, issue #233, `bilan.md`), puis une légende : « Dans chaque jour : le nombre de tâches prévues, un
  drapeau pour une échéance, un avertissement si la journée est trop chargée, une icône pour un férié ou un jour libre.
  Touche un jour pour l'ouvrir. »
- Une grille du lundi au dimanche (en-têtes « lun. » … « dim. »), une case par jour du mois, des cases vides avant le
  1er et après le dernier. Chaque case : le numéro du jour, puis, s'il y a lieu, le nombre de tâches **prévues** ce jour
  (en chiffre primaire), un drapeau (au moins une échéance), un avertissement rouge (journée trop chargée), une icône
  « jour occupé » (férié ou jour libre, case en surface plus foncée). Aujourd'hui : conteneur secondaire et contour,
  numéro en gras. Jamais la couleur seule : chaque signal a son chiffre ou son icône, et la case se lit en entier au
  lecteur d'écran (« mardi 10 novembre 2026. Tâches prévues : 1. Échéances : 1. Trop chargé. »).
- Toucher une case (48 dp de haut au moins ; « Voir le jour ») ouvre la vue Jour de ce jour (`vue-jour.md` § Un autre
  jour).
- **Prévue** ce jour : la règle de la vue Semaine (`vue-semaine.md`) : programmée pour ce jour, ou due ce jour sans être
  programmée un autre jour. **Échéance** : une tâche à faire dont l'échéance tombe ce jour, programmée ou non (une tâche
  due le 3 et programmée le 5 est prévue le 5 et a son drapeau le 3). Les tâches faites ne comptent pas.
- **Trop chargé** : la durée des tâches prévues (estimation, sinon la durée par défaut des réglages, 30 min) dépasse le
  temps disponible du jour : ses plages de travail (horaires par jour, `planning-personnalise.md`) moins les créneaux
  **occupés** (occurrences des récurrents comprises, mensuels et exceptions compris, `recurrence.md`). Un jour libre a
  0 min : une tâche qui y est prévue le rend trop chargé.
- **Journée entière d'un calendrier importé** (3.10.0, `connexions-externes.md`) : icône `Event` dans la case, les
  titres dans sa description d'accessibilité ; ni capacité ni surcharge. La légende sous le titre le dit.
- Sous la grille, la même information en listes, chaque ligne ouvrant son jour : « Échéances du mois » (« 10 nov. :
  Rapport · Revue »), « Jours trop chargés » (« mar. 10/11 : 8 h 30 prévues pour 7 h disponibles »), « Fériés et jours
  libres » (« mer. 11/11 · férié », « sam. 14/11 · jour libre »). Rien de prévu ni d'échéance : « Rien de prévu ce
  mois-ci. ».
- Espace Perso seulement : la vue lit la vue Perso (aucune tâche d'équipe d'un collègue), comme la vue Semaine.
- À 360 dp : sept colonnes égales (46 dp environ), sans défilement horizontal.

### Critères de succès

- Grille du lundi, mois complet, cases vides, mois suivant et précédent à travers l'année
  (`MonthViewTest.the_grid_starts_on_monday_and_covers_the_whole_month`).
- Prévues, échéances, durée par défaut, capacité moins une réunion, surcharge, série mensuelle reculée avant un férié,
  férié, jour libre sans capacité (`MonthViewTest.planned_tasks_deadlines_holidays_free_days_and_overload`).
- La case se lit en entier, les listes disent la même chose, toucher un jour ouvre « Jour · Tuesday, November 10, 2026 »
  (`MonthViewUiTest.aDayOfTheMonthOpensTheDayView`) ; mois suivant puis retour à la semaine de son 1er
  (`MonthViewUiTest.theNextMonthAndBackToTheWeek`) ; à 360 dp, aucune case ne sort de la fenêtre et une case fait
  48 dp de haut au moins (`MonthViewUiTest.theGridFitsAt360dp`).
- Captures de l'auto-test `desktop-month.png` (1 200 dp) et `desktop-month-narrow.png` (360 dp), regardées.

### Hors périmètre / différé

- Déplacer une tâche depuis la vue Mois (la vue Semaine le fait ; le mois ouvre le jour) ; afficher les titres des
  tâches dans les cases ; vue Mois de l'espace Équipe. (Le temps chronométré du mois, d'abord différé, est dans la
  carte « Bilan du mois » depuis la 4.1.0, `bilan.md`.)

## 2. Solution technique

- `MonthView.build(snapshot, unJour)` : `first` = 1er du mois, `days` = un `MonthDay` par jour : `planned` (à faire,
  `scheduledDate == jour` ou `deadline == jour && scheduledDate == null`, triées priorité puis titre), `deadlines` (à
  faire, `deadline == jour`), `slots` (ponctuels du mois et `Recurrence.expandRecurringBlocks(modèles, 1er, dernier,
  fériés, snapshot.blockExceptions)`), `plannedMinutes` (somme de `estimatedMinutes ?: defaultTaskDurationMinutes`),
  `capacityMinutes` (pour chaque `Schedule.windowsFor(jour)`, sa durée moins `Scheduling.busyMinutesInWindow` des
  créneaux `BUSY` du jour, bornée à 0), `holiday` (`Workdays.holidaysFor(1er, …)`, qui couvre l'année d'avant et les
  deux suivantes), `dayOff` (aucune plage). `MonthDay.overloaded` = `plannedMinutes > capacityMinutes`.
  `MonthView.leading` = jour de semaine du 1er (lundi = 0) ; `weeks` = cases par sept, `null` hors du mois ;
  `MonthView.shift(1er, ±1)`.
- `MonthScreen(services, nav, onOpenDay)` lit `repository.personalSnapshot` ; colonne défilante de 1 000 dp au plus ;
  `WeekMonthSwitch` ; `PeriodHeader` et `RecapCard` (`bilan.md`, du 1er au dernier jour du mois) ; `MonthGrid` (en-tête `Dates.weekdayAbbrev`, sans sémantique ; lignes `Row` à hauteur intrinsèque
  maximale, cases `weight(1f)`) ; `DayCell` : `Surface` `shapes.small`, `heightIn(min = 56.dp)`, `clickable` (rôle
  bouton, « Voir le jour ») et `clearAndSetSemantics` (description `dayDescription`, action), icônes `Flag`, `Warning`
  (`error`), `EventBusy` ; `MonthLists` (sections à titre `heading`, lignes `Line` de 48 dp cliquables).
- Navigation : `NavState.monthMode` et `NavState.month` (un jour du mois affiché, `null` = le courant) ;
  `DestinationScreen` montre `MonthScreen` quand `monthMode` est vrai ; la navigation principale, le changement d'espace
  et la désactivation de l'équipe remettent `month` à `null` (comme `week`) ; `WeekMonthSwitch` recopie `week` dans
  `month` et inversement (`null` pour le mois courant). Titre : `title_month_of` + `Dates.monthYear`.
- Icônes `Flag` et `CalendarMonth` ajoutées à `tools/make_icons.py` (`KairosIcons` régénéré).

### Décisions et pièges tracés

- **Une bascule dans « Semaine », pas une destination** (2026-10-24) : la barre MD3 a cinq destinations au plus, et la
  semaine et le mois répondent à la même question (« quand ? ») à deux échelles. Écarté : une sixième destination.
- **Le mode reste pour la session** : quelqu'un qui préfère le mois le retrouve en revenant ; la période, elle, revient à
  la courante, comme la vue Semaine.
- **Des chiffres et des icônes, pas de titres dans les cases** : à 360 dp une case fait 46 dp ; les titres sont dans les
  listes sous la grille et dans la vue Jour. Écarté : un agenda seul (on perd la forme du mois, qui est la demande).
- **Surcharge sur la durée prévue, sans placement** : refaire le placement de la vue Jour pour 31 jours coûterait cher
  et dépendrait de l'heure ; la somme des durées contre la capacité du jour suffit à repérer un jour impossible. Les
  durées apprises n'y entrent pas (estimation ou durée par défaut seulement) : elles sont faites pour le placement.
- **Rouge pour la surcharge** : c'est un dépassement (le design system le réserve aux dépassements, à P0 et aux
  erreurs), toujours accompagné de l'icône et du texte.
- **Un férié garde sa capacité** : le planning de la vue Jour ne l'exclut pas non plus ; il est signalé, pas vidé. Un
  jour libre (horaires par jour) n'a, lui, aucune plage.
