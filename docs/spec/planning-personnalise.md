# Planning personnalisé : ergonomie, horaires par jour et par contexte, meilleures heures

_Rôle : rendre Kairos utilisable pour tous les usages (travail, maison, rythmes
variés) sans rien changer pour qui ne touche pas aux réglages. Fichiers
couverts (à venir) : `kmp/core/.../model/Settings.kt` (`schedule`),
`core/engine/Scheduling.kt` (fenêtres), nouveau `core/engine/Schedule.kt`,
`core/day/DayView.kt`, `core/stats/` (meilleures heures), `core/settings/SettingsForm.kt`,
`ui/day/DateField.kt`, `ui/day/EditTaskDialog.kt`, `ui/week/WeekScreen.kt`, `ui/settings/`. Tests :
`DifferentialTest`, `TeamIsolationTest`, tests nommés dans les critères._

État : **issue #64**. **Lot A (ergonomie et réglages) : implémenté en 3.4.0**,
décrit en détail dans `vue-jour.md` (§ Dates, déplacer, blocage), `vue-semaine.md`,
`recurrence.md` et `reglages.md`, où ses décisions sont tracées. **Lot B1 (fenêtre par jour
de la semaine) : implémenté en 3.4.0** (voir « Lot B1 (implémenté) »). **B2 (plusieurs plages et
contextes) : implémenté en 3.5.0** (voir « Lot B2 (implémenté) »). **Lot C : étape C1 (observer) implémentée en 3.5.0, étape C2 (appliquer) implémentée en 3.6.0** (voir « Lot C », ci-dessous). Tous
les lots sont implémentés. Les **échelles libres** de priorité et de points ne sont **ni spécifiées ni implémentées** : elles sont écartées (voir « Hors périmètre / différé » et « Décisions et pièges tracés »), seuls des **noms personnalisés des niveaux** (lot D, issue #93, implémenté en 3.9.0) étant retenus : ils changent l'affichage, jamais le calcul.

## 1. Besoin métier (cahier des charges)

### Objectif / problème

Une utilisatrice ou un utilisateur (issue #64) veut se servir de Kairos pour
toute sa vie, pas seulement pour un bureau de 9 h à 17 h :

1. des **horaires différents selon le jour** et selon la **catégorie** de
   tâche (travail le matin, maison le soir, week-end libre) ;
2. pouvoir **adapter le planning à son rythme** (« documents vers 12 h, mails
   vers 18 h ») ;
3. moins de **friction** au quotidien : dates à saisir en texte, déplacer une
   tâche, comprendre pourquoi une tâche est bloquée, réglages touffus.

Contrainte posée par le propriétaire : **tout doit fonctionner comme avant
pour qui ne vérifie jamais les réglages.** Les échelles de priorité et de taille
personnalisables (4e demande de l'issue) sont écartées pour l'instant.

### Comportement attendu (utilisateur)

_Lot D, noms des niveaux (issue #93) : voir la sous-section dédiée en fin de
besoin métier._

**Lot A : ergonomie et réglages** (aucun effet sur les valeurs par défaut)

1. **Dates** : un sélecteur de date et des raccourcis (« Aujourd'hui »,
   « Demain », « Lundi prochain », « Dans une semaine », « Effacer ») pour
   l'échéance, la date programmée et le jour d'un créneau. La saisie au clavier
   accepte aussi `2026-9-30` et, en français, `30/09/2026`. Le libellé ne
   répète plus « (AAAA-MM-JJ) ».
2. **Déplacer** : une action de ligne « Déplacer… » (demain, lundi, dans une
   semaine, choisir une date) qui reporte la **date programmée** de la tâche
   (elle quitte « Aujourd'hui » jusqu'à ce jour), avec confirmation et
   possibilité d'annuler. « Décaler » met l'échéance au jour ouvré suivant et
   programme aussi la tâche pour ce jour. La vue Semaine montre aussi les tâches
   « programmées pour » un jour (elle reste en lecture seule : « Voir le jour » y
   mène ; écarté : ouvrir la fiche depuis la carte). Depuis la 3.7.0 (issue #106), toucher une tâche d'une carte
   ouvre seulement « Déplacer vers un autre jour… » (`vue-semaine.md`) ; la fiche reste dans la vue Jour.
3. **Blocage** : après avoir bloqué une tâche, un retour dit où elle est allée
   (« X attend Y », section « Bloquées » ouverte pour cette fois) ; l'aide de la
   fiche dit ce que fait un bloqueur ; l'en-tête replié de la fiche résume
   bloqueurs et date programmée (4.1.0, `fiche-tache.md` ; avant, les sections s'ouvraient d'office) ; le
   bloqueur porte un badge « bloque X ».
4. **Réglages** : libellés en langage clair (« WSJF » retiré des titres et des
   aides de l'agenda) ; langue et apparence en tête ; un groupe replié
   « Avancé : comment les tâches sont ordonnées » (ouvert d'office sur une
   erreur de saisie) ; un bouton **« Restaurer les valeurs par défaut »** qui
   remplit le formulaire, exige « Enregistrer » et ne touche jamais l'espace
   Équipe.
5. **Deux bugs probables, à vérifier avant tout** (annoncés comme *à
   vérifier*, pas comme avérés) : (a) une tâche « Décalée » reste dans
   « Aujourd'hui » (seule une date programmée future la masque) ; (b) une tâche
   bloquée non qualifiée serait listée à la fois dans « À traiter » et dans
   « Bloquées ». Un test qui échoue est écrit d'abord, puis la correction.
   **Constat** : les deux étaient avérés. (b) est corrigé dans `DayView`. Pour (a), le
   propriétaire a tranché : « Décaler » masque la tâche jusqu'à la nouvelle échéance.

**Lot B : horaires par jour et par contexte**

6. Chaque jour de la semaine peut être : « comme d'habitude » (la fenêtre
   actuelle), « personnalisé » (une ou plusieurs plages avec début, fin et
   contexte) ou « libre » (aucune plage).
7. Un **contexte** (« Travail », « Maison ») est un nom et un ensemble de
   **types de tâche** (le type existant, texte libre). Une tâche dont le type
   n'est dans aucun contexte est **sans contexte** : elle va dans n'importe
   quelle plage. Aucune migration : le type est déjà sur chaque tâche.
8. Une tâche d'un contexte n'est placée que dans une plage de ce contexte.
   Si ce contexte n'a aucune plage ce jour-là, elle va dans « Sans créneau »
   avec sa raison (« pas de plage Maison aujourd'hui »).
9. La frise et le temps réel du jour affichent les plages ; les trous entre
   plages sont grisés ; le temps disponible est la somme des plages.
10. Un type renommé ou supprimé est signalé « type inconnu » dans le contexte,
    jamais perdu en silence.

**Lot C : meilleures heures détectées automatiquement**

11. Kairos observe, par type de tâche, **quand tu travailles vraiment** (le
    chrono peut être lancé sur n'importe quelle tâche depuis n'importe quel
    classement : ces sessions sont la donnée). Il le montre d'abord
    (étape C1, aucun effet sur le planning) : carte Statistiques « Quand tu
    travailles chaque type », par tranche de 2 h, avec les effectifs et la
    mention « peu fiable » sous le seuil.
12. Ensuite, et **seulement si l'utilisateur l'active** (étape C2, implémentée en 3.6.0, désactivé
    par défaut), le planning préfère, à score égal, les créneaux où ce type est
    habituellement travaillé. Une **préférence manuelle** par type (« vers
    12 h ») prime sur la détection. « Pourquoi à cette place ? » le mentionne.

### Meilleures heures v2 : l'efficacité (besoin, issue #105)

La v1 mesure **quand** on travaille un type, pas **comment**. Qui fait ses
documents à 10 h seulement parce que Kairos les y place paraît « meilleur à
10 h » : la mesure confirme la suggestion (boucle d'auto-renforcement). Les
vraies meilleures heures sont celles où le travail se fait le plus vite au
regard de son estimation.

- La carte Statistiques « Quand tu travailles chaque type » dit aussi, par
  type, **à quelle heure il se termine le plus vite** par rapport à
  l'estimation (« 30 % plus vite que d'habitude »), ou qu'il n'y a pas encore
  assez de tâches terminées et estimées pour le dire.
- Le réglage « Meilleures heures » choisit sur quoi s'appuyer : **la
  fréquence** (v1, par défaut), **l'efficacité**, ou **les deux**. Sans y
  toucher, rien ne change. La mention de la vue Jour et « Pourquoi ? » disent
  laquelle a joué.
- Une heure suggérée artificiellement ne se renforce plus d'elle-même en mode
  efficacité : y travailler plus souvent ne la rend pas plus « efficace ».
- Aucune donnée nouvelle : estimations, tâches terminées et sessions du chrono
  suffisent.

### Lot D : noms des niveaux (besoin)

Certains pensent dans leur propre vocabulaire (« Urgent / Bientôt / Un jour »,
tailles S/M/L) et trouvent P0–P2 et les points de Fibonacci rébarbatifs. Ils
veulent lire leurs mots dans la vue Jour, la fiche d'une tâche et le guide,
pendant que Kairos continue de calculer sur l'échelle fixe.

- Réglages → Planification → carte **« Noms des niveaux »** : un champ par
  priorité (P0, P1, P2) et par palier de points (1, 2, 3, 5, 8, 13, 21). Le
  libellé de chaque champ dit le nom par défaut ; laissé vide, le niveau garde
  son nom par défaut (FR/EN). 16 caractères au plus ; deux niveaux d'une même
  échelle ne peuvent pas porter le même nom.
- Un nom choisi **remplace le code** là où le code est seul (badge d'une
  tâche, filtres, journal et scénarios de l'espace Équipe) : « Urgent » au lieu
  de « P0 », « M » au lieu de « 3 pts ». Là où le code est suivi de son nom
  (pastilles de qualification, « Pourquoi à cette place ? », aide de la boîte
  de réception, guide des points), il s'écrit **nom puis valeur** : « Urgent
  (P0) », « M (3) », pour qu'on retrouve toujours la valeur qui compte.
- Le **rouge reste au niveau 0**, quel que soit son nom (charte : rouge
  seulement pour P0).
- Le score, le tri, les statistiques, les exports de données et les échanges
  d'équipe utilisent toujours les valeurs ; seul le texte affiché change. Les
  noms ne sont jamais dans un paquet d'échange : chacun voit ses propres mots.


- Par défaut rien ne change : `DifferentialTest` (480 cas) et
  `TeamIsolationTest.soloExportIsByteIdentical` passent sans modification ;
  le fichier d'or d'export n'a aucun champ nouveau ; sans `schedule` les
  sorties du moteur sont identiques (test `ScheduleDefaultsTest`).
- Résolveur de fenêtres : un test par jour de semaine, jour libre, plages
  multiples, tâche sans contexte, contexte sans plage, type renommé, conservation
  « une seule liste » (`ScheduleWindowsTest`).
- Mesure des meilleures heures déterministe sur une base de sessions fixée,
  seuil de fiabilité, session à cheval sur deux tranches, fuseau explicite,
  boost nul quand l'option est désactivée (`BestHoursTest`).
- Ergonomie : les deux bugs ont chacun leur test (`DayViewTest`), l'analyse de
  dates souples a ses cas (`DatesTest`), « Restaurer » ne modifie pas `team`
  (`SettingsFormTest`).
- Meilleures heures v2 : efficacité déterministe sur une base fixée, seuils
  de fiabilité, biais d'estimation du type neutralisé, heure suggérée
  artificiellement sans gain d'efficacité (`BestHoursEfficiencyTest`) ; sans
  `measure`, le réglage et l'export sont identiques (`SettingsFormTest`).
- Noms des niveaux (lot D) : sans nom choisi, `levelNames` reste nul et rien
  ne change (`LevelNamesTest`, `SettingsFormTest`) ; un nom choisi remplace le
  code dans les badges et les filtres, s'écrit « nom (valeur) » dans les
  pastilles, « Pourquoi ? » et le guide des points (`LevelNamesUiTest`) ; le
  badge du niveau 0 reste rouge ; aucun nom n'entre dans un paquet d'échange.
- Interface : `--self-test` et captures des magasins regardés (360 dp sans
  débordement, cibles de 48 dp, textes FR/EN, conforme MD3).

### Hors périmètre / différé

- **Échelles libres** de priorité et de points (suggestion 2 de l'issue) :
  trop risquées (`FIBONACCI_SCALE` sur 11 sites, `PRIORITY_VALUES` sur 15,
  27 libellés par langue, calibrage, échanges d'équipe, exports). Seuls les
  **noms** des niveaux existants sont personnalisables (lot D) : ni ajouter ni
  retirer un niveau, ni changer une valeur.
- Noms des niveaux partagés avec l'équipe : ils restent sur l'appareil (voir lot D).
- Glisser-déposer (décision d'accessibilité déjà consignée).
- Apprentissage entre appareils ou d'équipe, prédiction de durée (voir
  `durees-apprises.md`), notifications (les rappels d'une tâche à heure fixe et de la tâche suivante sont arrivés en
  3.8.0, `rappels.md`).
- Capacité d'équipe par plage (voir « Décisions »).

## 2. Solution technique

### Principe directeur : « nul par défaut »

Toute nouveauté de réglage passe par un objet **nullable**
`Settings.schedule: ScheduleSettings? = null`, sur le patron de
`Settings.team` (`explicitNulls = false`, l'objet reste nul tant que la valeur
écrite est celle par défaut). Nul ⇒ le résolveur renvoie l'ancienne fenêtre
unique ⇒ mêmes sorties, export et fichier d'or inchangés
(`solo-export-v1.json`). `ExportCodec.check` fait aller-retour les réglages via
`SettingsForm` : le formulaire doit donc porter `schedule` sans le créer.

### Lot A (implémenté, détail dans les specs de domaine)

- **Dates** : `ui/day/DateField.kt` (`DateField`, `DateChoice` remonté de l'espace Équipe, `dateShortcuts`) et
  `Dates.parseDate(texte, french)` (`ui/day/Dates.kt`) ; partagés par la fiche (échéance, « Programmée pour ») et le jour d'un
  créneau. Voir `vue-jour.md` § Dates, déplacer, blocage.
- **Déplacer** : `KairosRepository.schedule`, bouton `MoveButton` de `TaskRow`, `LocalUndoMessages` pour « Annuler ».
  « Décaler » (`KairosRepository.snooze`) programme aussi la tâche personnelle. `WeekView` expose `WeekDay.scheduled`. Voir
  `vue-jour.md`, `vue-semaine.md`, `recurrence.md`.
- **Blocage** : `DayView.blocking` (badge « Bloque … »), `DayScreen` (message, section ouverte), `EditTaskDialog` (options
  avancées ouvertes d'emblée). Voir `vue-jour.md`.
- **Réglages** : groupe « Avancé » (`SettingsFormCards`), `SettingsForm.restoreDefaults`, carte « Réglages par défaut ». Voir
  `reglages.md`.
- **Bugs** : (a) `KairosRepository.snooze` ; (b) `DayView.inbox` (le moteur garde son `toProcess`, figé par les fichiers d'or).

### Lot B1 (implémenté en 3.4.0)

- **Modèle** (`core/model/ScheduleSettings.kt`) : `Settings.schedule: ScheduleSettings?` (nul par défaut, comme `team`) =
  `weekdays: Map<Int, DaySchedule>` (0 lundi … 6 dimanche, `Workdays.weekday`) ; `DaySchedule(free, startHour, endHour)` : **heures
  pleines**, comme les réglages de la journée (décision d'implémentation ; écartés : des minutes, la frise est graduée à l'heure).
  Un jour absent est « comme d'habitude ». B2 ajoutera plusieurs plages et les contextes à ce même sous-objet.
- **Résolveur pur** `Schedule.windowFor(jour, réglages): Window?` et `Schedule.displayWindow` (`core/engine/Schedule.kt`) : sans
  `schedule`, la fenêtre des réglages pour les sept jours (week-ends compris, figés par les fichiers d'or) ; `null` pour un jour
  libre ; un horaire incomplet ou incohérent retombe sur la journée des réglages. Détail : `ordonnancement.md`.
- **Consommateurs** : `Scheduling.buildDaySchedule` (placement, charge disponible), `Scheduling.buildTimeline` et
  `TimeTracking.sessionTimeline` (via `displayWindow`), `DayView.workStartHour`/`workEndHour`/`freeDay` pour la frise
  (`TimelineCard`). **Gardés tels quels** : capacité d'équipe (`Capacity`, `TeamWork`, `MemberForm`) lit toujours
  `workdayStartHour`/`workdayEndHour` ; le creux de l'après-midi reste en heures absolues.
- **Réglages** : carte « Horaires par jour de la semaine » (`ScheduleCard`), champs `schedule.dN.mode|start|end` de
  `SettingsForm` ; voir `reglages.md`.
- **Jour libre** : rien n'est placé automatiquement (les tâches vont dans « Sans créneau aujourd'hui ») ; les tâches épinglées
  gardent leur heure ; la frise montre la journée des réglages et une ligne qui le dit.
- **Tests** : `ScheduleTest` (résolveur, placement, jour libre, `DayView`), `SettingsFormTest`, `M5ScreensUiTest`,
  `DayScreenUiTest.aDayOffPlacesNothingAndSaysSo`, `KairosRepositoryTest.theScheduleSurvivesAnExportAndIsAbsentWhenNotCustomised` ;
  `DifferentialTest` et `TeamIsolationTest.soloExportIsByteIdentical` inchangés.

### Lot B2 (implémenté en 3.5.0)

- **Modèle** (`core/model/ScheduleSettings.kt`) : `DaySchedule` gagne `windows: List<WorkWindow(startHour, endHour, context?)>` (heures
  pleines) qui, quand elle n'est pas vide, **remplace** `startHour`/`endHour` ; ceux-ci restent la forme d'une plage unique sans
  contexte (celle de la 3.4, donc l'export d'un horaire de la 3.4 est identique). `ScheduleSettings` gagne
  `contexts: List<WorkContext(name, types)>`. Les deux listes ne sont **jamais écrites quand elles sont vides**
  (`@EncodeDefault(NEVER)`, l'export porte `encodeDefaults = true`). Une plage relie son contexte **par son nom**.
- **Résolveur** (`core/engine/Schedule.kt`) : `Schedule.windowsFor(jour, réglages): List<Window>` (généralise `windowFor`) : vide pour un jour
  libre ; la journée des réglages sans horaire propre ou si rien de valable ne reste ; plusieurs plages : triées, celles qui sont
  incohérentes ou qui chevauchent la précédente sont ignorées. `windowFor` rend l'**étendue** (début de la première, fin de la dernière)
  et `displayWindow` celle d'affichage. `Schedule.contextOf(type, réglages)` : le premier contexte qui liste le type (sans casse,
  espaces retirés) ; `null` pour un type vide ou dans aucun contexte. `Schedule.accepts(plage, contexteDeLaTâche)` : vrai si la tâche est
  sans contexte, si la plage est **ouverte** (sans contexte) ou si les contextes sont égaux.
- **Moteur** (`Scheduling.buildDaySchedule`) : la boucle de placement parcourt les plages dans l'ordre ; réunions, épinglées et deep work
  restent des obstacles communs et le curseur passe d'une plage à la suivante. Le filtre de contexte ne joue que sur **le choix au créneau**
  (jamais `sortKey` ni le score affiché ; le test `theContextFilterNeverChangesTheOrderOrTheScore` le fige). Une tâche qui ne tient pas dans une
  plage (son début dépasserait la fin) passe à la suivante qui l'accueille, sinon « Sans créneau » comme avant (même ordre de la liste). Une tâche
  dont le contexte n'a aucune plage ce jour-là va dans « Sans créneau » et `ScheduledDay.noWindow` (id → nom du contexte) dit pourquoi ;
  `DayStats.availableMinutes` est la somme des plages (de maintenant à leur fin), moins l'occupé. Un jour libre garde la plage vide de la 3.4.
  Une seule plage sans contexte : octet pour octet le placement de la 3.4 (`DifferentialTest` inchangé ; `aSingleWindowListIsTheSameDayAsTheOldRange`).
- **Frise** : `DayView.windows` (les plages du jour) et `DayView.noWindow` ; `TimelineCard` grise les trous entre plages et nomme le contexte de chaque
  plage réservée, en pastille.
- **Réglages** : champs `schedule.dN.count` (1 à 4), `schedule.dN.start|end|ctx` (première plage, clés de la 3.4) et `schedule.dN.wK.start|end|ctx`
  (K = 2 à 4) ; contextes `schedule.cK.name` et `schedule.cK.types` (K = 1 à 4). Cartes « Horaires par jour de la semaine » (`ScheduleCard` :
  « Ajouter une plage », pastilles « Réservée à ») et « Contextes » (`ContextsCard` : nom, pastilles des types). Voir `reglages.md`.
- **Tests** : `ScheduleWindowsTest`, `SettingsFormTest` (plages, contextes, nul par défaut), `KairosRepositoryTest`, `SchedulePlanningUiTest`.

### Décisions du lot B2

- **Une plage sans contexte est ouverte à toute tâche** (précision de la spec initiale, qui disait « une tâche d'un contexte n'est placée que dans
  une plage de ce contexte ») : sinon définir un contexte priverait de ses tâches tout jour « Comme d'habitude » (« tout doit fonctionner comme avant »).
  Seules les plages **réservées** restreignent.
- **Au plus 4 plages par jour et 4 contextes** : les champs du formulaire sont en nombre fixe (`SettingsForm.MAX_WINDOWS`, `MAX_CONTEXTS`) ; un nombre
  libre demanderait un formulaire à champs dynamiques. Assez pour « matin, midi, soir » et trois contextes.
- **Plage reliée à son contexte par le nom** : renommer le contexte laisse la plage citer un nom inconnu, refusé à l'enregistrement
  (`FieldErrorKind.UNKNOWN_CONTEXT`, « Aucun contexte nommé … ») plutôt que perdu ; un type sorti de « Types de tâche » reste dans son contexte et s'affiche
  « type inconnu » (jamais perdu en silence). Un type n'est que dans un contexte, un nom de contexte est unique (`DUPLICATE`).
- **Contextes vides gardés pendant l'écriture des champs** : le formulaire est positionnel ; `SettingsForm.validate` retire les emplacements vides à la fin,
  de sorte qu'aucun objet `schedule` n'apparaît tant que rien n'est rempli.

### Lot C (C1 implémenté en 3.5.0, C2 implémenté en 3.6.0)

- **Mesure** (fonction pure dans `core/stats`, fuseau en paramètre comme
  `DayView`) : par type et par tranche de 2 h, part du temps chronométré sur
  `statsWindowWeeks` (8 par défaut) ; les minutes d'une session sont réparties
  entre les tranches traversées ; sessions < 5 min ignorées ; pondération
  récente. Comparaison à la distribution **globale** de l'utilisateur
  (« lift » = part du type ÷ part de toutes les tâches) pour ne pas confondre
  « je travaille à 10 h » et « les documents, c'est à 10 h ». Seuil de
  fiabilité comme le « peu fiable » des stats (minimum de sessions et
  d'heures par type), sinon rien n'est proposé.
- **C1 observer** (implémenté en 3.5.0) : carte Statistiques, aucune écriture, aucun effet. `core/stats/BestHours.kt` (`BestHours.measure`, détail et
  constantes dans `statistiques.md` § Meilleures heures) et `StatsScreen` (`HoursProfile`). Écarts avec la rédaction initiale : la fiabilité est un
  minimum de **5 sessions et 120 minutes** par type (constantes du code, non réglables) ; le « lift » désigne une **tranche de pointe** parmi celles qui
  portent au moins 20 % du temps du type ; la pondération récente va de 1 à 1/2 sur la fenêtre. Pas de réglage : la carte s'affiche toujours (observation).
- **C2 appliquer** (implémenté en 3.6.0) : réglage `Settings.schedule.bestHours` (`BestHoursSettings(enabled = false, strength = 1,
  preferred = {})`, nul tant que rien n'y est touché, donc absent de l'export ; `ScheduleSettings.bestHours` en `EncodeDefault.NEVER`). Au créneau,
  `effort' = effort ÷ (1 + force × gain(type, heure))`, **même patron que `dipAdjustedEffort`** : l'affinité n'intervient que dans le choix au
  créneau (`Scheduling.placementScore` / `selectionKey`), jamais dans `sortKey` ni dans le score affiché, ni dans le remplissage des fenêtres de
  deep work (qui reste « les plus urgentes »). Jamais sur une tâche **en retard** (palier 0) ni **remontée par dépendance** (`effective != own`) :
  l'affinité ne réordonne que les autres. Désactivé (ou `bestHours` nul), `buildDaySchedule(…, affinity = null)` est exactement le code d'avant
  (`DifferentialTest` inchangé). Détail du moteur dans `ordonnancement.md` § Meilleures heures au créneau.
  - **Force** : un interrupteur et **trois niveaux** nommés Faible / Moyenne / Forte (`strength` 0 / 1 / 2 → `BestHours.strengthOf` = 0,15 / 0,30 / 0,50 ;
    Moyenne par défaut). Le gain valant au plus 1, l'effort est au plus divisé par 1,5 : une tâche d'une priorité plus haute ne se fait pas doubler
    (P0 contre P1 : rapport de valeurs 4).
  - **Gain** (`Affinity.boost`, ∈ [0, 1]) : **détecté** = `lift − 1` borné à [0, 1] de la tranche de 2 h de l'heure du créneau, pour les types dont le
    profil est **fiable** (`BestHours.affinity`) et les tranches qui portent au moins 10 % du temps du type ; rien pour un type peu fiable, sans
    type ou inconnu. Sans contraste (un seul type chronométré, lift 1) le gain est nul : la détection ne pousse que ce qui se distingue du rythme global.
  - **Préférence manuelle** : **une heure préférée par type** (0 à 23, champ vide = détection ; `BestHoursSettings.preferred`, type → heure, comparé sans
    la casse). Elle **prime** sur la détection pour ce type (la détection est alors ignorée) et marche sans aucun historique de chrono. Gain =
    `max(0, 1 − |heure − préférée| / 3)` : plein à l'heure dite, nul à ± 3 h (`Affinity.PREFERRED_RADIUS_HOURS`).
  - **Mention** : `ScheduledTask.affinity` (`AffinitySource.DETECTED` ou `MANUAL`, `affinityApplied` en dérive) n'est posé que si l'affinité a **changé le
    choix** du créneau (comparé au choix que le creux seul aurait fait) ; dans ce cas `dipApplied` reste faux (une seule mention). La vue Jour affiche la
    note « heure préférée pour « Dev » » / « ton heure habituelle pour « Dev » » à côté du créneau, et « Pourquoi à cette place ? » une phrase de plus
    (`why_affinity_*`) qui dit que ce n'est qu'une préférence : l'urgence et les tâches en retard passent d'abord.
  - **Interface** : carte **Meilleures heures** (`BestHoursCard`) dans les Réglages, catégorie « Planification », après « Types de tâches » (après « Contextes » jusqu'au rangement par catégories du 2026-10-22, `reglages.md`) : interrupteur « Préférer mes heures habituelles »,
    force en trois boutons segmentés et, quand c'est activé, un champ « Heure préférée pour <type> (0–23) » par type de « Types de tâche » (plus ceux
    qui ont une heure mais ont quitté la liste, signalés « type inconnu » : jamais perdus en silence). Champs du formulaire :
    `schedule.bestHours.enabled`, `.strength` (0 à 2), `.preferred` (texte `type=heure,…`, `SettingsForm.parsePreferred` / `encodePreferred`) ; une heure
    hors de 0 à 23 est refusée sur ce champ, à l'enregistrement comme à l'import. « Restaurer les valeurs par défaut » le remet à zéro. Carte du catalogue
    « Planifier autour de tes meilleures heures » (`besthours_apply`, `since = "3.6.0"`).
  - **Mesure à la demande** : `DayView.build` ne mesure (`BestHours.measure`, sur les sessions du chrono) que si le réglage est actif ; la mesure n'est faite qu'une
    fois par construction de la vue (le fuseau et l'instant sont ceux de la vue).
  - **Écarts avec la rédaction initiale** : la force bornée « 0 = neutre » devient trois niveaux nommés (choix du propriétaire, qui rend le
    réglage lisible) ; la préférence manuelle est une **heure** par type, pas une plage.
  - Tests : `AffinityTest` (égalité départagée, retard, dépendance, bornage, creux + affinité, `DayView`), `BestHoursTest` (gain détecté, préférence prioritaire),
    `SettingsFormTest`, `KairosRepositoryTest.theBestHoursSurviveAnExport…`, `SchedulePlanningUiTest`.
- **Anti-boucle** : le planning suggère un créneau, l'utilisateur l'honore, la
  mesure le confirme (auto-renforcement). Parades : poids borné, ajustement
  doux, exclusion des tâches en retard ; en v2, mesure d'**efficacité**
  (estimé ÷ réel par tranche) plutôt que la seule fréquence. La v1 reste la
  fréquence relative au global, et la mesure par défaut.

### Lot C v2 : efficacité (implémenté en 3.9.0, issue #105)

- **Mesure** (`BestHours.measure`, même fenêtre et mêmes sessions que la v1) :
  pour chaque tâche **terminée** dans la fenêtre, avec une **estimation** et du
  temps chronométré, rapport `r = estimé ÷ réel` (réel = tout son temps
  chronométré, comme le calibrage), borné à [1/4, 4] (une session oubliée ne
  doit pas peser des centaines de fois). Ses minutes retenues (sessions d'au
  moins 5 min de la fenêtre) sont réparties entre les tranches de 2 h : la
  tâche pèse `w_b` = part de ses minutes dans la tranche `b` (somme 1).
- **Efficacité relative** d'une tranche = moyenne géométrique pondérée de `r`
  dans la tranche ÷ moyenne géométrique de `r` sur toutes les tâches du type.
  Diviser par celle du type **neutralise son biais d'estimation** (un type
  toujours sous-estimé n'est pas « lent partout ») : on compare le type à
  lui-même. Moyenne géométrique : 2× plus vite et 2× plus lent se compensent.
- **Fiabilité** : un type a un profil d'efficacité s'il compte au moins
  **5 tâches** (`MIN_EFFICIENCY_TASKS`) ; une tranche n'a de valeur que si elle
  porte au moins **3 tâches-équivalents** (`Σ w_b ≥ MIN_BUCKET_TASKS`), sinon
  `null`. **Tranche la plus efficace** (`fastest`) : la plus haute efficacité
  relative, si elle atteint 1,1 (10 % plus vite, `EFFICIENCY_NOTABLE`).
- **Anti-boucle** : placer plus de tâches à une heure en augmente l'effectif,
  pas la vitesse ; un type travaillé à une seule heure a une efficacité
  relative de 1 partout où il a des données, donc aucun gain. Seule une heure
  où l'on finit **plus vite que d'habitude** gagne.
- **Gain** (`BestHours.affinity`) : efficacité = `(relative − 1) × 2` borné à
  [0, 1] (plein à 50 % plus vite, `EFFICIENCY_FULL_GAIN`), 0 sans valeur
  fiable ; fréquence = v1 inchangée ; **les deux** = moyenne des deux gains
  (une donnée d'efficacité encore absente ne rend pas la fréquence muette).
  L'heure préférée prime toujours.
- **Réglage** : `BestHoursSettings.measure` (`frequency` par défaut,
  `efficiency`, `both`), jamais écrit à sa valeur par défaut
  (`@EncodeDefault(NEVER)` : l'export d'une installation v1 ne change pas) ;
  champ `schedule.bestHours.measure`, normalisé à l'écriture (valeur inconnue
  = fréquence), jamais refusé. `BestHoursCard` : « Mesurer par » en trois
  boutons segmentés (Fréquence, Efficacité, Les deux) sous la force.
- **Mention** : `AffinitySource.EFFICIENT` (efficacité) et `COMBINED` (les
  deux) à côté de `DETECTED` (fréquence) et `MANUAL` ; notes
  `note_affinity_efficient` / `_combined`, phrases `why_affinity_efficient` /
  `_combined`.
- **Statistiques** : chaque profil porte `efficiency` (relative par tranche,
  `null` si peu fiable), `efficiencyTasks` et `fastest` ; la carte ajoute une
  ligne sous la pointe (`stats_hours_fastest`, `stats_hours_even`,
  `stats_hours_few_tasks`) ; le CSV `hours_by_type` ajoute `efficiency_tasks`,
  `fastest_start`, `fastest_gain_pct` (`statistiques.md`).
- **Décisions** : *estimé ÷ réel* plutôt que la part des tâches finies en une
  session (écartée : dépend du découpage des sessions et du chrono oublié,
  pas de la vitesse) ; *comparé au type* plutôt qu'au global (écarté :
  confondrait « ce type est sous-estimé » et « cette heure est lente ») ;
  *fréquence par défaut* : l'efficacité exige des estimations, que beaucoup
  n'ont pas.

### Lot D : noms des niveaux (implémenté en 3.9.0, issue #93)

- **Donnée** : `Settings.levelNames: LevelNames?` (`core/model/Settings.kt`),
  nul tant qu'aucun nom n'est choisi (principe « nul par défaut »).
  `LevelNames.priority` et `LevelNames.points` : valeur → nom, une valeur sans
  nom est absente, jamais écrites vides (`@EncodeDefault(NEVER)`).
  `LevelNames.MAX_LENGTH` = 16.
- **Formulaire** (`SettingsForm`) : champs texte `levelNames.p0`…`p2` et
  `levelNames.pts1`…`pts21` ; nom nettoyé (espaces retirés), vide = absent ;
  le sous-objet redevient nul quand il ne porte plus aucun nom. Erreurs :
  `TOO_LARGE` (borne 16, message « 16 caractères au plus » propre à la
  carte, comme le nom d'un membre) au-delà de 16 caractères, `DUPLICATE` sur le second de
  deux niveaux d'une même échelle au même nom (sans tenir compte de la casse).
  Catégorie Planification (`SettingsCategory.of`) ; « Restaurer les valeurs
  par défaut » les vide.
- **Affichage** (`ui/day/Levels.kt`) : `LocalLevelNames` (posé par
  `KairosApp` depuis les réglages) ; `priorityShort`/`pointsShort` (nom choisi,
  sinon « P0 » ou « 3 pts ») pour les badges et les filtres,
  `priorityLabel`/`pointsLabel` (« nom (P0) », « nom (3) », sinon « P0
  Critique », « 3 trivial ») pour les pastilles, « Pourquoi ? », l'aide de la
  boîte de réception ; ligne du guide des points `guide_points_named`
  (« M (3) : … »). Carte `LevelNamesCard` (`ui/settings/LevelNamesCard.kt`).
- **Décisions** :
  - *Partout dans l'interface de l'utilisateur*, espace Équipe compris
    (backlog, tableau, journal, scénarios) : ce sont ses mots, sur son appareil
    ; écarté : l'espace Perso seulement, qui aurait montré deux vocabulaires
    pour la même tâche. Les paquets d'échange, l'export JSON et le CSV des
    statistiques restent numériques.
  - *Les statistiques gardent les valeurs* (calibrage par palier, débit en
    points) : elles mesurent l'échelle elle-même, un nom n'y ajoute rien.
  - *Le terme « Effort » de « Pourquoi ? » reste numérique* (« Effort 3 pts »,
    « ÷ 3 ») : il explique le calcul, qui se fait sur la valeur.
  - *Les percentiles des prévisions (P50, P85)* ne sont pas des priorités :
    inchangés.
  - *16 caractères* : un badge doit tenir dans une ligne de tâche à 360 dp.
  - *Pas de nom pour « sans priorité » ni « sans points »* : ce n'est pas un
    niveau.

### Feuille de route

- **3.4.0** : lot A + B1 (avec les chantiers `retrait-kairos2.md`,
  `durees-apprises.md`, `visite-guidee.md` et `premiere-tache.md`).
- **3.5.0** : B2 (livré) + C1 (livré).
- **3.6.0** : C2 (livré). Échelles libres réévaluées après retours.
- **3.9.0** : C v2, efficacité (issue #105, livré) ; D, noms des niveaux (issue #93, livré).

### Décisions et pièges tracés

- **Contexte = type de tâche existant** (écarté : un champ « contexte »
  dédié, qui exigerait migration, export et UI de qualification). Risque : un
  type est un libellé traduit, un renommage casse la liaison ; elle est
  signalée, jamais silencieuse. Un champ dédié reste possible plus tard.
- **Capacité d'équipe inchangée** (écarté : la faire suivre les plages) : elle
  reste sur les champs historiques ; à reconsidérer si les horaires par jour
  deviennent courants chez les managers.
- **Détection avant préférence** (écarté : seulement une préférence manuelle) :
  la donnée est déjà là grâce au chrono sur n'importe quelle tâche ; la
  préférence manuelle reste le recours.
- **Observer avant d'appliquer** : C1 prouve que la mesure est lisible avant
  qu'elle n'influence quoi que ce soit.
- **Échelles libres écartées** (voir Hors périmètre) ; seuls les noms des
  niveaux sont personnalisables (lot D).

### Impacts sur les specs existantes

Aucun en attente : les lots A, B1, B2, D et les étapes C1 et C2 sont reportés dans `vue-jour`, `vue-semaine`, `recurrence`, `reglages`, `ordonnancement`,
`statistiques` et `modele-donnees`.
