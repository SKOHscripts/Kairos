# Vue Jour et flux GTD

_Rôle : l'écran d'ouverture de Kairos, où l'on capture, qualifie, ordonne
et fait ses tâches. Fichiers couverts :
`kmp/core/src/commonMain/.../core/day/DayView.kt` (modèle de l'écran, pur),
`kmp/ui/src/commonMain/.../ui/day/` (`DayScreen.kt`, `TaskRow.kt`,
`Why.kt`, `NowCard.kt`, `Timeline.kt`, `Capture.kt`, `Blocks.kt`,
`Filters.kt`, `Qualify.kt`, `EditTaskDialog.kt`, `Levels.kt`, `Dates.kt`).
Tests : `core/.../day/DayViewTest.kt`, `ui/.../day/DatesTest.kt`,
`desktopApp/.../DayScreenUiTest.kt` (clics et clavier réels)._

État : **jalon M2**. Reprend le besoin de `docs/spec/vue-jour-gtd.md`
(Kairos 2). Le moteur est décrit par `ordonnancement.md`, `dependances.md`
et `recurrence.md`. Restent pour plus tard : le chrono (« Démarrer le
chrono », carte « En ce moment », temps passé, rail du réel sur la frise :
M3), le guide des points fondé sur l'historique et la vue Semaine (M4).

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
   liste des créneaux du jour, chacun modifiable).
3. **À traiter** : toute tâche sans priorité **ou** sans points, avec ce qui
   manque, qualifiable en un clic par pastilles dont le sens est écrit
   (P0 Critique · P1 Important · P2 Utile ; 1 trivial … 21 énorme).
   Recliquer une valeur la retire. Vide : « Rien à traiter : tout est déjà
   clarifié ».
4. **Maintenant** (seul bloc teinté) : « À faire maintenant : » la première
   tâche placée (avec son heure), sinon la première sans créneau ; boutons
   nommés « Fait » et « Décaler » ; bilan : faites aujourd'hui, à faire,
   « requis … · disponible … », « la journée déborde de … » s'il y a lieu.
5. **Bandeau de surcharge** si plus de P0 non bloquées que le seuil.
6. **Aujourd'hui, dans l'ordre** : les tâches placées par heure, ordre du
   score ; étiquettes « épinglée », « deep work », « chemin critique » ;
   notes « à partir de 14h05, après « Réunion » » (« (épinglée) » si
   l'obstacle est une tâche épinglée), « créneau creux (~15 h) : tâche
   légère privilégiée », « chevauche « Réunion » ». Vide : « Aucune tâche à
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
   échéance ni date programmée, non bloquées.
10. **Agenda** (frise) : la journée de travail heure par heure, créneaux et
    tâches placées ; à droite de la liste en largeur bureau, en bas sinon.

**Ligne de tâche** (toutes sections) : coche ronde ; corps : heure (agenda),
« Mère › » pour une sous-tâche, titre, puis étiquettes (projet, type,
durée, « échéance 30 sept. », « programmée 2 oct. », icône de récurrence,
« traîne depuis N j ») qui passent à la ligne, puis la description sur une
ligne, dépliable d'un clic ; colonne « clés » alignée à droite : score (qui
s'ouvre sur « Pourquoi à cette place ? »), priorité (P0 en rouge), points ;
actions : décaler (tâches à faire), modifier. En largeur étroite, la
colonne « clés » passe sous le corps. Liseré rouge de 3 dp à gauche pour
une tâche en retard. Tâche bloquée : fond de surface, contour, titre
atténué.

**Pourquoi à cette place ?** : valeur de la priorité, ce qu'ajoute la date
la plus proche (« Échéance dépassée de 12 j », « Date programmée demain »,
« Aucune échéance »), l'effort et sa provenance, le score ; une tâche en
retard le dit en tête. Le score n'est affiché que pour une tâche qualifiée.

**Édition** (dialogue) : titre, description, priorité, points, échéance,
durée ; « Options avancées » : programmée pour, projet, temps passé manuel,
récurrence (aucune, quotidienne, jours ouvrés, hebdomadaire, mensuelle,
« le … du mois » avec le jour), type, heure fixe (HH:MM, vide = aucune),
nouvelles sous-tâches (une par ligne), « Bloquée par » (cases à cocher).
Un seul « Enregistrer » ; « Supprimer » avec confirmation. Dialogue de
créneau : mêmes champs que la capture de créneau, « Supprimer » qui dit
« toutes ses occurrences » pour un récurrent.

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

## 2. Solution technique

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
`DayFilter` : recherche insensible à la casse sur titre, description et
projet ; `active` si la recherche n'est pas vide ou qu'une facette est
posée.

### Écran (`DayScreen`, `ui`)

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
  de test `day-screen`) ; elle reprend le focus après la première image et à
  la fermeture d'un dialogue. Un champ de saisie consomme la frappe avant
  elle : les raccourcis sont inactifs pendant une saisie.
- `TaskRow(tâche, RowContext, heure?, bloquée, modifiable, description,
  before, after, extra)` : `RowContext` porte la vue, la langue, le mode
  compact et les actions. Formes et couleurs de la charte : `Badge` neutre,
  `WarnBadge` (contour et icône, jamais d'ambre), `ErrorBadge` (conflit),
  `PriorityBadge`, score en chiffre primaire (`ScoreBadge`, menu déroulant
  MD3 de 300 dp).
- `NowCard` : `primaryContainer`, `Button` « Fait », `TextButton`
  « Décaler », badges du bilan (vert « ok » pour les faites).
- `TimelineCard` : carte à contour, 1 min = 1 dp, graduations par heure,
  8 dp de marge en haut et en bas ; occupé en conteneur de surface le plus
  haut, travail et épinglée en conteneur secondaire (contour pour
  l'épinglée), conflit en conteneur d'erreur, deep work en tertiaire
  (hachures dessinées pour le bloc réservé) ; libellé « heure · titre » si
  l'entrée fait au moins 14 min.
- `Capture` : `SingleChoiceSegmentedButtonRow` ; `CaptureState` (volet,
  `FocusRequester` du champ). `BlockFormState` / `BlockFields` : jour
  (AAAA-MM-JJ), début et fin (`Dates.parseTime` accepte « 9:30 »,
  « 09h30 », « 14h »), fin après début sinon erreur et bouton inactif ;
  `LabeledCheckbox` (libellé cliquable, 48 dp).
- `FilterCard` : carte à contour repliable, `ExposedDropdownMenuBox` par
  facette.
- `Dates` : « 30 sept. » / « Sep 30 », « 09h15 » / « 09:15 », graduations
  « 9h » / « 9:00 », nombres à une décimale sans « .0 » ; `duration()` :
  « 1 h 30 », « 2 h », « 45 min ».

### Décisions et pièges tracés

- **Défiler seulement après composition** : un raccourci qui change l'état
  (volet « Tâche », section ouverte) puis fait défiler la liste dans la même
  image fait échouer la liste paresseuse (erreur interne de Compose,
  constatée par `DayScreenUiTest`). On attend deux images
  (`awaitComposed`), on défile, on attend encore, puis on donne le focus.
  Un `delay` fixe ne convient pas non plus : l'horloge de test ne l'avance
  pas.
- **Focus racine après la première image** : demandé plus tôt, l'écran
  n'est pas encore attaché et les raccourcis restaient sourds.
- **« Fait » du jour en heure locale** (fuseau injecté) : Kairos 2 comparait
  la date UTC de modification, si bien qu'une tâche faite à 0 h 30 à Paris
  tombait la veille. Écart volontaire.
- **Pas de « Décaler » sur une tâche faite** (Kairos 2 l'affichait aussi
  dans « Fait », sans utilité).
- **Score affiché « 16 », pas « 16.0 »** : même format que « Pourquoi à
  cette place ? » (Kairos 2 affichait l'arrondi Python brut dans le badge).
- **Sections repliables sans carte englobante** : dans une liste paresseuse,
  une carte ne peut pas contenir plusieurs éléments ; titre cliquable avec
  chevron, puis lignes.
- **Aujourd'hui seulement** : pas de navigation vers un autre jour avant la
  vue Semaine (M4), qui y mène.
- **Jour et heures saisis à part** pour un créneau : le `datetime-local` de
  Kairos 2 n'a pas d'équivalent commun à Compose.
- **Projet dans les options avancées** (comme Kairos 2) ; le type garde une
  valeur retirée de la liste des réglages (décision M1).
- **Opacité interdite sur une ligne bloquée** : décision de Kairos 2 reprise
  (fond et contour à la place), même si le dialogue de Compose, fenêtre à
  part, n'hérite pas de l'opacité.
- **Priorité et points passent sous le corps en largeur étroite** (comme
  Kairos 2) : décision M1 rouverte et tranchée au jalon M2.
