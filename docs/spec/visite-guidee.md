# Visite guidée enrichie et catalogue des fonctionnalités

_Rôle : faire connaître à l'utilisateur toutes les fonctionnalités de Kairos,
au bon moment, depuis une source unique. Fichiers couverts :
`kmp/ui/.../ui/guide/FeatureCatalog.kt`, `GuideContent.kt`, `GuideSpot.kt`, `GuideDialog.kt`,
`DiscoverDialog.kt`, `HelpDialog.kt`, `ui/screens/HomeScreen.kt`,
`ui/settings/SettingsScreen.kt`, `ui/navigation/AppShell.kt`, et la mise en valeur des sections dans `DayScreen.kt`,
`StatsScreen.kt` et les cartes des Réglages, la mémoire du guide (`core/model/Settings.kt` `GuideSettings`,
`KairosRepository.updateGuide`), ressources `values/`
et `values-fr/`. Tests : `FeatureCatalogTest`, `GuideStepsTest`, `M6GuideUiTest`, `GuideSpotUiTest`, `GuideMemoryUiTest`,
`KairosRepositoryTest.theGuideMemoryIsWrittenOnlyWhenUsed`, `KairosRepositoryTest.theGuideMemorySurvivesAnExportAndIsAbsentByDefault`,
`StringsParityTest`._

État : **issue #64 (retour du propriétaire)**, spécifiée le 2026-10-13,
**implémentée en 3.4.0** (voir « Écarts avec la spécification initiale ») ; cartes qui ouvrent la section précise d'un
écran en 3.7.0 (issue #95) ; le guide se souvient de ce qui a été vu en 3.7.0 (issue #94).
**Rouvre explicitement** les décisions de `accueil.md` § La visite guidée (deux
listes de 6 étapes écrites en dur, aucune persistance) ; le reste de `accueil.md`
est inchangé.

**Visite interactive sur une démonstration** (issue #96) : décidée par le propriétaire, spécifiée le 2026-10-26,
**implémentée le 2026-10-28** (3.13.0). Elle est décrite au point 8 du comportement attendu, dans la section « Visite
interactive » de la solution technique et dans les décisions ; elle **rouvre en partie** deux décisions (« pas de
pastilles sur l'interface » d'`accueil.md`, « visite interactive » ci-dessous en hors périmètre), les précise sans les
contredire ; ses impacts sur les autres specs, listés en fin de document, sont reportés.

## 1. Besoin métier (cahier des charges)

### Objectif / problème

La visite actuelle compte deux listes de six étapes fixes. Elle ne parle ni de
l'équipe, ni des sous-tâches, ni de la frise et du temps réel, ni des
statistiques, ni des réglages, et rien ne dit ce qui est nouveau d'une version à
l'autre. Les chantiers de la 3.4 (horaires par jour, durées apprises, déplacer
une tâche…) ajoutent des fonctionnalités qu'il faut faire
découvrir sans que l'utilisateur ait à lire une documentation.

### Comportement attendu (utilisateur)

1. **Une visite initiale courte** (six étapes environ) reste proposée à
   l'accueil du premier lancement.
2. **Un hub « Découvrir Kairos »**, accessible depuis Réglages et l'aide, liste
   des **chapitres** et permet d'en rouvrir un à tout moment : Premiers pas ;
   Planifier (priorité, points, score, horaires par jour, heures préférées) ;
   Suivre (chrono, durées apprises, statistiques) ; Organiser (dépendances,
   sous-tâches, récurrences, déplacer) ; Équipe (si l'espace est activé) ;
   Réglages. Un chapitre **parcouru jusqu'au bout** (sa dernière carte atteinte, puis « Terminer » ou « Voir l'écran »)
   porte une coche et « · lu » (« 5 cartes · lu ») ; il reste ouvrable (3.7.0, issue #94).
3. **Chaque carte pointe vers l'écran** qu'elle décrit (« Voir l'écran »), vers la
   section précise quand elle existe (3.7.0, issue #95) : l'écran s'ouvre, **défile** jusqu'à la section et la
   **met en valeur** environ 3 s (contour primaire de 2 dp et étiquette « Ici » avec icône, qui apparaissent et
   disparaissent sans animation ; le lecteur d'écran annonce « Ici »). Correspondances :
   - Réglages : « Plusieurs plages et contextes » → carte « Contextes » ; « Horaires par jour » → « Horaires par jour de la
     semaine » (catégorie « Journée et horaires ») ; « Planifier autour de tes meilleures heures » → carte « Meilleures heures » ;
     « Durées apprises » → carte « Durées apprises » (catégorie « Planification ») ; « Tes données et ton manager » → carte « Données »
     (catégorie « Données »). La catégorie s'ouvre d'abord (`reglages.md`). « Des réglages clairs… » ouvre la liste des
     catégories, sans section.
   - Vue Jour : « Bloqueurs » → section « Bloquées », ouverte ; « Sous-tâches » → « Tâches mères en cours », ouverte ;
     « Qualifier en un clic » et « Priorité, points et estimation » → « À traiter » ; « Faire dans l'ordre » et « Score et placement » →
     « Aujourd'hui, dans l'ordre » ; « Suivre le temps » → carte « Maintenant ».
   - Statistiques : « Quand tu travailles chaque type » → son panneau.
   - Les autres cartes ouvrent l'écran en haut, comme avant.
   Section absente (vide, par exemple aucune tâche bloquée, ou des statistiques sans données) : l'écran s'ouvre en
   haut et un message bref le dit (« Aucune tâche bloquée pour l'instant. »).
4. **« Nouveau dans cette version »** : sur l'accueil, une section liste les
   cartes ajoutées par la version courante. « Masquer » la retire **pour cette version** (mêmes numéros majeur et
   mineur : une `3.7.1` ne la ramène pas) ; la version suivante (`3.8.0`) a de nouveau la sienne (3.7.0, issue #94 ;
   avant : masquée pour la session seulement).
5. **L'aide d'un écran** (bouton « ? ») et la visite lisent les **mêmes
   textes** ; aucun texte n'est écrit à deux endroits.
6. L'accompagnement de la **première tâche** (`premiere-tache.md`) fait partie
   des chapitres et réutilise ces cartes. Une fois mené à son terme, son invitation ne revient plus (`premiere-tache.md`).
7. **Repartir de zéro** (3.7.0, issue #94) : Réglages → « À propos » → **« Revoir le guide depuis le début »** efface
   aussitôt ce que le guide a retenu (chapitres lus, « Nouveau » masqué, accompagnement fait), avec « Annuler » ;
   « Restaurer les valeurs par défaut » l'efface aussi, à l'enregistrement (`reglages.md`).
8. **Visite interactive sur une démonstration** (spécifiée le 2026-10-26, issue #96, décision du propriétaire ;
   implémentée en 3.13.0). Le guide décrit mais ne fait pas faire : on lit « Qualifier » sans jamais voir un score bouger. Le
   risque d'une visite qui manipule l'écran est d'agir sur les **vraies** données (tâche parasite, mélange du réel et de
   l'exemple). D'où ce principe, qui fait toute la conception :
   - **Une démonstration jetable en mémoire, jamais la vraie base.** La visite tourne sur un dépôt **neuf à chaque fois**,
     rempli du jeu d'exemples (`ExampleData`), séparé de la base de l'utilisateur : ce qui s'y passe ne touche ni ses
     tâches, ni ses réglages, ni son export, ni ses sauvegardes, ni son fichier lié, et disparaît quand on quitte.
   - **Entrée** : « Essayer en démonstration » (« Quatre gestes sur des exemples jetables : tes données ne sont pas
     touchées. ») dans « Découvrir Kairos », sous « Créer ma première tâche » : une entrée du hub (comme « Créer ma première
     tâche récurrente », `premiere-tache.md`), présentée aussi par une carte du catalogue (« Démonstration interactive »,
     chapitre « Premiers pas »). Grisée tant qu'un chrono tourne sur les vraies données (« Arrête d’abord le chrono en
     cours. ») ; toucher la carte pendant ce temps affiche la même phrase en message.
   - **Un bandeau permanent et un bouton « Quitter » toujours visibles** sous la barre du haut, sur chaque écran :
     « Démonstration : tes données ne sont pas touchées » et « Quitter ». « Quitter » (ou le retour système / Échap quand
     rien d'autre n'est ouvert) rend l'application **exactement où elle était** (destination, jour affiché), sans
     confirmation : il n'y a rien à perdre. Rien n'est retenu d'une démonstration à l'autre.
   - **Les vrais écrans**, pas une maquette : Notes, Jour, Semaine et Stats, avec leurs composants ; elle s'ouvre sur la vue Jour. L'Accueil, la roue
     crantée (Réglages), le « ? », le cœur « Soutenir Kairos » et le sélecteur d'espace n'y sont pas : il n'y a rien à
     enregistrer ni d'équipe dans une démonstration.
   - **Quatre scénarios courts et scriptés**, enchaînés sur la **même tâche** : (1) **Capturer** : écrire un titre dans
     la capture de la vue Jour, la tâche arrive dans « À traiter » ; (2) **Qualifier** : choisir une priorité puis des
     points sur sa ligne, elle reçoit un score et prend sa place ; (3) **Lancer le chrono** : le démarrer puis l’arrêter,
     « En ce moment » apparaît ; (4) **Déplacer** : « Déplacer… » puis « Demain », elle quitte « Aujourd'hui » (message
     « Annuler »). Six étapes en tout, puis un dialogue de fin.
   - **Chaque étape** a un texte court (quoi faire, pourquoi), un **repère** sur l'élément concerné (contour et
     étiquette « Ici ») et deux actions : **« Faire pour moi »** (la visite fait le geste, avec le même effet que si
     l'utilisateur l'avait fait) et **« À moi de jouer »** (le focus passe sur l'élément à toucher, la visite attend).
     Faire le geste soi-même sans toucher au panneau marche aussi : l'étape se valide dès que son effet est là et passe à
     la suivante. « Précédent » revient d'une étape, « Passer l’étape » la saute : **aucune étape ne bloque**.
   - **Fin** : « Tu as fait le tour » (« Tes données n’ont pas été touchées. »), « Quitter la démonstration » et
     « Recommencer » (une démonstration neuve).
   - **Mouvement et accessibilité** : tout est faisable **sans geste** (« Faire pour moi » à chaque étape, aucun glisser,
     `accessibilite.md` § Décisions) ; le panneau annonce « Étape n sur N » et le texte de l'étape (région vivante
     polie) sans prendre le focus ; le repère se lit « Ici » (jamais la couleur seule) ; bandeau et « Quitter » sont des
     textes avec icône, atteignables en premier ; cibles de 48 dp, 360 dp sans débordement. **Aucun mouvement** :
     repères sans animation, défilement instantané, rien ne se tape ni ne bouge tout seul, aucun délai automatique
     (la visite n'avance que sur un effet constaté ou sur un bouton), ce qui respecte « réduire les animations ».
   - **Pointeurs discrets, sans pastille flottante** : le repère est le même langage que la mise en valeur du guide
     (3.7.0) et de la première tâche (contour primaire de 2 dp et « Ici »), appliqué à l'élément lui-même ; ce rapport avec
     la décision de 2026-10-01 d'`accueil.md` (« pas de pastilles sur l'interface ») est tranché dans les décisions.

### Critères de succès

- Chaque carte du catalogue a ses textes FR et EN et une cible qui existe
  (`FeatureCatalogTest`).
- Chaque fonctionnalité listée dans `README.md` correspond à au moins une carte
  (`FeatureCatalogTest.readmeFeaturesAreCovered`, sur une liste d'identifiants
  maintenue à côté du catalogue).
- La visite initiale, l'aide et la section « Nouveau » lisent le catalogue
  (`GuideStepsTest`, `M6GuideUiTest` mis à jour).
- Les cartes « Équipe » n'apparaissent que si l'espace est activé.
- Textes FR/EN (`StringsParityTest`), 360 dp sans débordement, cibles de 48 dp,
  captures `--self-test` regardées.
- Ce que le guide retient n'est écrit qu'au premier usage : une base ou un export qui n'y a jamais touché ne change pas
  (`KairosRepositoryTest.theGuideMemoryIsWrittenOnlyWhenUsed`, `DifferentialTest` et
  `TeamIsolationTest.soloExportIsByteIdentical` inchangés) ; il passe l'export et l'import
  (`KairosRepositoryTest.theGuideMemorySurvivesAnExportAndIsAbsentByDefault`).
- « Masquer » la section « Nouveau » tient après un redémarrage de la même version ; un chapitre parcouru jusqu'au bout
  est coché dans le hub ; « Revoir le guide depuis le début » ramène tout, et « Annuler » rend l'état d'avant
  (`GuideMemoryUiTest`).
- Une section visée appartient à l'écran de sa carte, et une section des Réglages à une catégorie
  (`FeatureCatalogTest.sectionsBelongToTheScreenTheyOpen`).
- « Durées apprises » ouvre la catégorie « Planification » et met sa carte en valeur ; « Bloqueurs » ouvre la section
  « Bloquées » et la met en valeur ; sans tâche bloquée, le message le dit ; la mise en valeur s'efface d'elle-même ;
  « Thème sombre » (3.7.0) met en valeur la carte Apparence, « Les créneaux des jours ouvrés sautent les fériés » la
  carte « Jours fériés » (`GuideSpotUiTest`).
- La 3.7.0 liste ses six cartes dans « Nouveau dans cette version » (`FeatureCatalogTest`) ; « Premiers pas » en a six,
  comme le chapitre de l'équipe (`M6GuideUiTest`).
- **Visite interactive** (issue #96, 3.13.0 ; tests du module `desktopApp`, qui a SQLite en mémoire) :
  - Le script (`DemoStepsTest`) : six étapes dans l'ordre (capturer, priorité, points, démarrer, arrêter, déplacer) ; pour
    chacune, `done` faux avant, `act` (« Faire pour moi ») sur une démonstration neuve le rend vrai, sans interface
    (`sixStepsInOrderEachDoneByItsActOnAFreshDemo`) ; la tâche qui disparaît ramène à la première étape
    (`theTrackedTaskThatDisappearsBringsTheTourBackToTheFirstStep`) ; des étapes sautées n'empêchent pas « Faire pour moi »
    d'obtenir l'effet de la suivante (`skippedStepsStillLeadToTheirEffect`).
  - **Isolation** (`DemoStepsTest`) : construits depuis les vrais services du bureau (fichier lié, mises à jour,
    diagnostic, démonstration), ceux de la démonstration ne reprennent que le dépôt de la session, l'horloge et la langue ;
    chaque champ d'`AppServices` est décidé dans `forDemo`, un champ nouveau fait échouer le test
    (`demoServicesCarryNothingFromTheRealOnes`) ; après une démonstration jouée jusqu'au bout, la base réelle, son export
    (octet pour octet) et son `onChanged` sont intacts (`aWholeDemoLeavesTheRealBaseAndItsExportUntouched`).
  - Interface (`DemoUiTest`, avec une source de démonstration en mémoire) : bandeau, « Quitter » et Notes, Jour, Semaine,
    Stats, sans Accueil, roue crantée ni « ? » ; panneau en région vivante polie, repère « Ici », cibles de 48 dp ;
    « Faire pour moi » mène de la première étape au dialogue de fin, et « Quitter la démonstration » laisse la vraie base
    vide (`doItForMeLeadsFromTheFirstStepToTheEndAndExitLeavesTheRealDataAlone`) ; « À moi de jouer » donne le focus au
    champ de capture, puis frapper un titre et toucher les puces de la ligne font avancer la visite, « Précédent » revient
    d'une étape (`myTurnFocusesTheMarkedElementAndRealGesturesMoveTheTourOn`) ; « Quitter » rend l'écran d'où la
    démonstration a été ouverte (`exitBringsBackTheScreenTheDemoWasOpenedFrom`) ; l'entrée du hub est grisée et dit
    pourquoi pendant un chrono réel (`theHubEntryIsGreyedWhileARealTimerRuns`).
  - `StringsParityTest` sur les six langues ; captures `desktop-demo.png` (rail) et `desktop-demo-narrow.png` (360 dp,
    barre basse) de l'auto-test regardées ; les autres captures ne changent pas (rien de la démonstration n'est composé
    hors démonstration).

### Hors périmètre / différé

- Mémoriser chaque carte vue (seuls les chapitres terminés le sont) et « Pas maintenant » de l'invitation à la première
  tâche (valable pour la session, `premiere-tache.md`).
- Visite interactive qui manipule l'écran à la place de l'utilisateur **sur ses vraies données** : jamais. Sur une
  démonstration jetable, elle est **rouverte le 2026-10-26** (issue #96, point 8, implémentée en 3.13.0).
- Vidéos ou animations (la démonstration n'en a pas non plus : repères fixes, défilement instantané).
- **Réseau** : aucun, la démonstration est locale et le reste de l'application garde « sans permission réseau ».
- Démonstration de l'espace Équipe (manager, membres, backlog) : la première version est solo ; à demander si besoin.
- Retenir la progression ou « démonstration faite », jeu d'exemples personnalisable, scénarios en plus des quatre,
  démonstration hors de l'application (page web `site/`), enregistrer et rejouer les gestes de l'utilisateur.

## 2. Solution technique

### Catalogue piloté par les données

`FeatureCatalog.cards` (`ui/guide/FeatureCatalog.kt`) : une liste de `FeatureCard(id, chapter, icon, title, body, target,
since, tour, platforms)`, **dans l'ordre de la visite** ; **une carte par fonctionnalité du README** depuis l'inventaire du
2026-10-28 (61 cartes à l'inventaire, 63 avec `skipped_months` et `demo`, `liste-fonctionnalites.md` § Inventaire), `platforms` (toutes par défaut) disant celles qui n'existent pas
partout. La liste complète des fonctionnalités lit le même catalogue.

- **Chapitres** (`GuideChapter`) : `FIRST_STEPS` (capture, raccourcis clavier, qualification, « faire dans l'ordre », « Découvrir
  Kairos », première tâche, le guide qui retient et montre l'endroit, récurrente et Réglages pas à pas), `PLAN` (priorité, points et
  estimation ; noms des niveaux ; score ; placement ; creux de l'après-midi ; plages et contextes ; meilleures heures ; horaires par
  jour ; jours ouvrés et fériés), `FOLLOW` (frise ; chrono ; trois alertes ; durées apprises ; heures de travail par type et heures les
  plus efficaces ; widgets ; semaine ; mois ; déplacer depuis la semaine ; statistiques, évolutions, correction et filtres),
  `ORGANIZE` (sous-tâches et sous-tâches modifiables ; récurrence ; décaler et déplacer ; bloqueurs ; créneaux occupés ; deep work ;
  créneaux « jours ouvrés » sans les fériés ; créneaux mensuels ; import de calendrier ; calendriers du téléphone), `TEAM`
  (première tâche d'équipe guidée, membres, travail perso compté, backlog, assignation, suivi, charge, prévisions, échanges),
  `SETTINGS` (réglages clairs ; par catégories ; rappels ; récapitulatif du jour ; couleur du thème ; thème sombre ; langues ;
  données ; fichier lié ; version web). `FeatureCatalog.chapters(teamEnabled)` n'offre `TEAM` que si l'espace Équipe est activé.
- **Visites** : `GuideSteps.of(GuideKind.PERSONAL)` = les cartes `tour = true` hors Équipe (six) ; `GuideSteps.of(GuideKind.TEAM)` =
  les cartes `tour = true` du chapitre `TEAM` (les six d'origine ; le chapitre en compte neuf depuis l'inventaire). Les textes des
  douze cartes d'avant gardent leurs chaînes `guide_you_*` et `guide_team_*` ; les autres sont `card_<id>_title` / `card_<id>_body`.
- **Cibles** (`GuideTarget`) : `Personal(destination, section?)`, `Team(destination, exchanges)`, `Settings(section?)` et
  `FirstTask` (lance l'accompagnement de `premiere-tache.md`, sans changer d'écran ; le bouton devient « Créer ma première
  tâche »).
- **Sections** (`GuideSection`, issue #95) : `DAY_INBOX`, `DAY_AGENDA`, `DAY_NOW`, `DAY_BLOCKED`, `DAY_PARENTS`,
  `SETTINGS_WORKDAY`, `SETTINGS_CONTEXTS`, `SETTINGS_HOURS`, `SETTINGS_HOLIDAYS`, `SETTINGS_BEST_HOURS`, `SETTINGS_LEARNED`, `SETTINGS_LEVELS`,
  `SETTINGS_APPEARANCE`, `SETTINGS_DATA`, `STATS_HOURS` ; chacune
  connaît son écran (`screen`), sa catégorie des Réglages (`settingsCategory`) et son message d'absence (`missing`, pour
  les sections qui peuvent manquer). `AppShell.openTarget` pose la demande dans `NavState.guideSpot`
  (`GuideSpotState.pending`) après avoir ouvert l'écran ; pour les Réglages, `onOpenSettings()` puis
  `nav.settings.category = section.settingsCategory`. Naviguer à la main (destination, espace, roue crantée) efface une
  demande en attente (`KairosApp`).
- **Mise en valeur** (`GuideSpot.kt`) : `LocalGuideSpot` fournit l'état aux écrans (`AppShell`). `GuideMark(section)`
  dessine, tant que `shown == section`, un contour `BorderStroke(2.dp, primary)` aux coins de la carte
  (`shapes.medium`) et l'étiquette « Ici » (`primary` / `onPrimary`, icône `Spot`, `liveRegion` poli) en haut à droite ;
  `GuideSpotState.show(section)` la pose 3 s (`SPOT_MILLIS`) puis l'efface si rien ne l'a remplacée. `GuideSpotState.held` (3.9.0) cerne une
  section tant qu'une étape de la visite des Réglages dure (`premiere-tache.md`) ; `GuideMark` lit `shown` ou `held`.
  `GuideSpot(section)` = `GuideMark` + `BringIntoViewRequester` : quand la demande en attente est la sienne, il la
  consomme, attend deux images (l'écran doit être mesuré) et se fait défiler à l'écran. Écrans à colonne défilante :
  les cartes des Réglages (`ContextsCard`, `ScheduleCard`, carte « Jours fériés », `BestHoursCard`, carte « Durées
  apprises » de `SettingsFormCards`, `AppearanceCard`, `DataCard`) et le panneau « Quand tu travailles chaque type » de `StatsScreen`.
- **Vue Jour** (liste paresseuse : une ligne hors de l'écran n'est pas composée, `bringIntoView` n'y peut rien) :
  `DayScreen` consomme la demande lui-même : ouvre la section repliable (`sectionOpen`), attend la composition, puis
  `listState.scrollToItem(positions[clé])` et `show(section)` ; `GuideMark` entoure l'élément de tête (« inbox-title »,
  « agenda-title », « now », « section-blocked », « section-parents »). Clé absente de `positions` (section vide) : le
  message `missing` par `LocalMessages`, et l'écran reste en haut. Statistiques sans aucune donnée (panneau absent) :
  même message.
- **Captures de l'auto-test** : `desktop-guide-spot.png` (900 dp) et `desktop-guide-spot-narrow.png` (360 dp), la carte
  « Durées apprises » mise en valeur et figée (`GuideSpotState.shown` posé d'avance).
- **`since`** : version de livraison ; `FeatureCatalog.newIn(version)` rend les cartes de **mêmes numéros majeur et mineur**
  (`3.4.1` ou `3.4.0-beta.2` gardent la section de la `3.4.0` ; une version illisible ou sans carte rend une liste vide).
- **Cartes de la 3.7.0** (une par nouveauté, choix du propriétaire) : `guide_memory` (le guide retient et montre
  l'endroit, issues #94 et #95 ; Accueil), `week_move` (déplacer depuis la vue Semaine, #106 ; Semaine),
  `stats_explore` (filtres et export des statistiques, #113 et #114 ; Stats), `holidays_slots` (fériés des créneaux
  « jours ouvrés », #109 ; section `SETTINGS_HOLIDAYS`), `settings_categories` (Réglages par catégories, PR #124 ;
  Réglages), `darktheme` (thème sombre, #72 ; section `SETTINGS_APPEARANCE`, icône `DarkMode`).
- **Cartes de la 3.8.0** (une par nouveauté) : `slots_month` (créneaux mensuels et une seule occurrence, #108 et #110 ;
  vue Jour, chapitre « Organiser »), `month` (vue Mois, #107 ; destination Semaine, chapitre « Suivre », icône
  `CalendarMonth`), `stats_correct` (corriger les estimations, #101 ; Stats, chapitre « Suivre », icône `EditNote`), `stats_trends`
  (évolutions des statistiques, #112 ; Stats, chapitre « Suivre », icône `ShowChart`), `reminders` (rappels, #103 ;
  section `SETTINGS_REMINDERS`, chapitre « Réglages », icône `Alarm`).
- **Cartes de la 3.9.0** : `level_names` (noms des niveaux, #93 ; section `SETTINGS_LEVELS` de la catégorie
  Planification, chapitre « Planifier », icône `Flag`), `besthours_efficiency` (meilleures heures v2, #105 ; section
  `STATS_HOURS`, chapitre « Suivre », icône `Timer`), `guided_walks` (récurrente et Réglages pas à pas, #98 ; Accueil,
  chapitre « Premiers pas », icône `Repeat`).
- **Cartes de la 3.10.0** : `calendar_import` (import d'un calendrier `.ics` et calendriers du téléphone, #136 et
  #138 ; section `SETTINGS_DATA`, chapitre « Organiser », icône `Event`), `linked_file` (fichier lié sur le bureau et
  Android, #137 ; section `SETTINGS_DATA`, chapitre « Réglages », icône `Description`).
- **Inventaire du 2026-10-28** (issue #153, `liste-fonctionnalites.md` § Inventaire) : quinze cartes créées avec leur version
  d'origine (`since` de 3.0.0 à 3.10.0 : elles n'apparaissent dans « Nouveau » d'aucune version à venir) ; les cartes d'équipe passent
  à 3.1.0 et `recurrence` à 3.0.0.
- **Carte de la 3.11.0** : `languages` (les six langues et leur choix, #75 ; cible `GuideTarget.Settings()` sans section,
  chapitre « Réglages », icône `Translate`).
- **Cartes de la 3.12.0** : `widgets` (widgets Android, #150 ; vue Jour, section `DAY_NOW`, chapitre « Suivre », icône `Today` ;
  la carte se lit sur toutes les plateformes, son texte dit « Sur Android »), `daily_summary` (récapitulatif du jour, #151 ;
  section `SETTINGS_REMINDERS`, chapitre « Réglages », icône `Alarm`).
- **Comptes de cartes dans les tests** : `M6GuideUiTest` tire les nombres de cartes par chapitre de `FeatureCatalog`
  (une carte ajoutée à une version ne casse plus le test du hub).
- **Lien avec le README** : `FeatureCatalog.README_FEATURES` associe chaque fonctionnalité en gras de la section
  « Features » de `README.md` à une carte ; `FeatureCatalogTest.readmeFeaturesAreCovered` lit le README et compare.
- **`GuideDialog`** accepte n'importe quelle sélection (`GuideDialog(steps, key, …)`, `GuideDialog(kind, …)` en est la
  forme courte) et garde la mesure du plus long titre et du plus long texte.
- **Hub « Découvrir Kairos »** (`DiscoverDialog`) : « Créer ma première tâche » puis un chapitre par ligne avec son nombre
  de cartes (3.8.0 : sous le chapitre de l'équipe, quand il est proposé, « Créer ma première tâche d'équipe »,
  `premiere-tache.md` § Première tâche d'équipe ; 3.9.0 : sous « Organiser », « Créer ma première tâche récurrente », et
  sous « Réglages », « Régler Kairos pas à pas », `premiere-tache.md` § Première tâche récurrente et visite des Réglages) ; choisir un chapitre ferme le hub et ouvre ses cartes dans `GuideDialog` ; accessible depuis l'Accueil (bouton),
  l'aide (entrée « Découvrir Kairos ») et les Réglages (carte « Découvrir Kairos » de la catégorie « À propos », `reglages.md`) ; états `discover` et `chapter` de
  `AppShell` (`rememberSaveable`, rien n'est mémorisé au-delà).
- **« Nouveau dans cette version »** : `HomeScreen(version = KairosBuild.VERSION_NAME)` affiche, sous le bloc teinté, les
  cartes de `newIn(version)` (toute la carte ouvre sa cible) avec un bouton « Masquer » valable pour la session
  (`rememberSaveable`).

### Persistance (3.7.0, issue #94)

- `Settings.guide` : `GuideSettings?`, **nul par défaut** (patron de `team`, `schedule`, `learning`) : rien n'est écrit
  dans la base ni dans l'export tant que le guide n'a rien retenu. `GuideSettings` : `newsHidden` (`"3.7"`, la version
  majeure.mineure dont « Nouveau » est masqué, nul sinon), `readChapters` (noms de `GuideChapter` terminés),
  `firstTaskDone` (accompagnement mené à son terme) ; aucun n'est écrit à sa valeur par défaut.
- `KairosRepository.updateGuide(change)` : applique `change` à `guide ?: GuideSettings()` et enregistre `null` si le
  résultat vaut `GuideSettings()` ; n'écrit rien s'il ne change rien. `resetGuide()` remet `guide` à nul.
- `FeatureCatalog.newsKey(version)` : `"majeur.mineur"` d'une version lisible (`3.7.1` → `"3.7"`), nul sinon ; la même
  règle que `newIn`.
- Écritures : `HomeScreen` (« Masquer » → `onHideNews`, la coquille écrit `newsHidden`), `AppShell` (un chapitre du hub
  terminé : `GuideDialog(onFinish)`, appelé par « Terminer » sur la dernière carte ou « Voir l'écran » sur elle, ajoute
  le chapitre à `readChapters`), `DayScreen` (tâche enregistrée depuis la fiche avec l'accompagnement actif :
  `firstTaskDone`), carte « Revoir le guide depuis le début » des Réglages (`resetGuide`, puis « Annuler » réécrit
  l'état d'avant).
- Lectures : `HomeScreen(newsHidden)` ; `DiscoverDialog(read)` (coche `CheckCircleFilled` en vert « ok » et « · lu »
  dans l'aide de la ligne, jamais la couleur seule) ; `FirstTaskInvite` absente si `firstTaskDone`.
- Le formulaire des Réglages ignore `guide` : son état est indexé sur les réglages **sans** `guide` (une écriture du
  guide ne remet pas à zéro des modifications non enregistrées), et `validate(values, current)` garde le `guide`
  courant.

### Visite interactive (spécifiée le 2026-10-26, issue #96 ; implémentée en 3.13.0)

Fichiers : `ui/app/Demo.kt` (`DemoSource`, `DemoSession`, `openDemo`, `forDemo`), `ui/app/AppServices.kt` (`demo`),
`ui/KairosApp.kt` (`RealOrDemoShell`), `ui/navigation/AppShell.kt`, `ui/guide/demo/` (`DemoSteps.kt`, `DemoTour.kt` avec
`DemoMark`, `DemoPanel.kt` avec `DemoBanner` et `DemoDoneDialog`), `ui/guide/DiscoverDialog.kt`, les repères dans
`ui/day/` (`Capture.kt`, `DayScreen.kt`, `TaskRow.kt`, `NowCard.kt`) et un fournisseur par plateforme
(`DesktopServices.kt`, `AndroidServices.kt`, `WebServices.kt`).

- **Dépôt de démonstration** :
  - `AppServices.demo: DemoSource?` (**nul par défaut** : les tests et les captures n'ont pas d'entrée).
    `fun interface DemoSource { suspend fun open(language: String): DemoSession }` (la langue de l'interface, pour le jeu
    d'exemples) et `class DemoSession(val repository: KairosRepository, val close: suspend () -> Unit)`.
  - Chaque plateforme fournit seulement le **pilote en mémoire** ; le reste est commun (`openDemo`) : bureau
    `JdbcSqliteDriver(IN_MEMORY)` (y compris pour l'auto-test), Android `AndroidSqliteDriver(DeferredSchema, context, null)`
    (un nom nul : base en mémoire), web un `WebWorkerDriver` **neuf** (sa base sql.js est en mémoire, distincte de celle
    que l'OPFS conserve). `openDemo(opened, langue, close, clock)` appelle `KairosRepository.open(opened, examples =
    { ExampleData.snapshot(aujourd'hui, maintenant, langue) }, clock)` **sans `onChanged`** : ni OPFS, ni fichier lié, ni
    sauvegarde. `close` ferme le pilote (web : termine le worker).
  - `AppServices.forDemo(session)` (commun) construit les services **champ par champ** : `repository` de la session,
    `files` qui refuse tout (`saveText` rend `false`, `openText` `null`), `backups` sans effet, `clock` reprise,
    `notifier = NoNotifier` (ni notification, ni titre de fenêtre clignotant, ni son), `firstLaunch = false`, `teamUi`
    neuf, `systemLanguage` et `applyLanguage` repris (même langue) ; tous les autres champs vides : ni `dataLocation`,
    `linkedStorage`, `updates`, `downloadPage`, `dataFolder`, `diagnostics`, `calendars`, `shortcuts`, ni `demo`. Un champ
    de plus dans `AppServices` doit être décidé là : `DemoStepsTest.demoServicesCarryNothingFromTheRealOnes` compare la
    liste des champs.
  - Réglages de la démonstration : ceux d'`ExampleData`, donc des scénarios identiques pour tous ; rappels et équipe
    absents. L'**apparence** (couleur, thème, langue, noms des niveaux) reste celle de l'utilisateur : `KairosApp` la lit
    sur les **vrais** réglages, au-dessus des deux coquilles.
- **`KairosApp`** : `RealOrDemoShell` tient `demo: DemoRun?` (`remember`, **pas** `rememberSaveable` : une rotation ou la mort
  du processus met fin à la démonstration, qui est jetable). `DemoRun(services, session, tour, nav)`. Lancer :
  `ready.demo.open(langue)` puis `DemoRun(ready.forDemo(session), session, DemoTour(session.repository))`. Quitter :
  `demo = null`, `removeState("demo")`, puis `session.close()`. « Recommencer » : quitter puis lancer. Les deux coquilles
  vivent sous un `SaveableStateHolder` (clés `"app"` et `"demo"`) : la coquille réelle qui quitte la composition garde ses
  `rememberSaveable` (destination, espace, écran secondaire) et les retrouve au retour ; sa `NavState` (jour, semaine et
  mois affichés, qui n'est pas sauvegardable) est créée par `RealOrDemoShell` et passée à `KairosShell`. La coquille de la
  démonstration s'ouvre sur `Destination.WORK` (la vue Jour). Un widget touché pendant une démonstration la quitte : la
  coquille réelle revient et ouvre la destination demandée.
- **Coquille en démonstration** : `AppShell(…, demo: DemoTour? = null, onStartDemo, onExitDemo, onRestartDemo)`. `null` :
  rendu et comportement **inchangés**. Sinon : `DemoBanner` en tête du contenu, sous la barre du haut, hors de la zone
  défilante, toujours visible (surface neutre `surfaceContainerHighest`, filet `outlineVariant`, icône `Science`, texte et
  `TextButton` « Quitter » : jamais un second bloc teinté) ; destinations `Destination.entries − HOME` ; barre du haut sans
  actions (ni cœur, ni « ? », ni roue crantée) ; pas de sélecteur d'espace (la base de démonstration n'a pas d'équipe) ;
  ni `WelcomeDialog`, ni hub ; la vue Jour masque `FirstTaskInvite` (`LocalDemoTour` non nul) ; `BackHandler` qui quitte
  une fois l'écran secondaire refermé (les dialogues gèrent leur propre retour) ; `DemoPanel` dans la `bottomBar` du
  `Scaffold`, au-dessus de la barre basse. `LocalDemoTour` fournit le déroulé aux écrans.
- **Le script** (`DemoSteps.kt`, données pures) : `DemoStep(id, spot: DemoSpot, title, body, act: suspend (DemoContext) ->
  Unit, done: (KairosSnapshot, DemoContext) -> Boolean)` et `DemoContext(repository, today, taskId, taskTitle)` (le titre de
  « Faire pour moi », `demo_task_title`, résolu par la coquille). `DemoSteps.ALL` :

  | # | Scénario | Repère (`DemoSpot`) | `done` (effet constaté dans l'instantané de la démo) | « Faire pour moi » (`act`) |
  |---|---|---|---|---|
  | 1 | Capturer | `CAPTURE` : champ « Nouvelle tâche » de la capture | une tâche qui n'est pas un exemple (`ExampleData.isExample`) existe | `createTask(demo_task_title)` |
  | 2 | Qualifier | `PRIORITY` : puces de priorité de sa ligne « À traiter » | sa `priority` n'est plus nulle | `setPriority(taskId, 1)` |
  | 3 | Qualifier | `POINTS` : puces de points | ses `fibonacciPoints` ne sont plus nuls | `setPoints(taskId, 3)` |
  | 4 | Chrono | `TIMER_START` : bouton chrono de sa ligne | une séance est ouverte sur elle | `startTimer(taskId)` |
  | 5 | Chrono | `TIMER_STOP` : « Arrêter le chrono » de « En ce moment » | plus de séance ouverte, une séance close sur elle | `startTimer` si rien ne tourne sur elle, puis `stopTimer()` |
  | 6 | Déplacer | `MOVE` : bouton « Déplacer… » de sa ligne | sa `scheduledDate` est après aujourd'hui | `schedule(taskId, demain)` |

  La tâche suivie est la plus récente qui n'est pas un exemple (`DemoSteps.trackedTask`) ; une étape dont la tâche manque
  (étapes précédentes sautées) la crée d'abord (`ensureTask`). `done` lit l'**effet dans les données**, jamais le geste ni
  la place dans la frise (qui dépend de l'heure) : l'étape se valide quelle que soit la façon de faire.
- **Déroulé** (`DemoTour`) : `index`, `taskId`, `finished`, demandes de focus. `observe(instantané)` est appelé à **chaque
  changement de données** de la démonstration (la coquille collecte `repository.snapshot`) : la tâche suivie apparaît, ou
  disparaît (retour à l'étape 1), puis l'étape avance tant que son effet est là. Changer d'étape n'appelle pas `observe` :
  « Précédent » reste donc sur l'étape d'avant jusqu'au prochain changement de données. « Passer l’étape » avance d'une
  étape ; passer la dernière termine.
- **Panneau** (`DemoPanel`) : `OutlinedCard` sans ombre, non bloquante, qui ne prend jamais le focus ; « Étape n sur N »
  (`guide_step_counter`), titre et texte de l'étape forment une région vivante polie ; « Faire pour moi » (`Button`),
  « À moi de jouer » (`OutlinedButton`), « Précédent » (dès la deuxième étape, `guide_previous`) et « Passer l’étape »
  (`TextButton`) dans un `FlowRow` (360 dp sans débordement).
  - « Faire pour moi » exécute `act` dans la portée de la coquille, par les **mêmes méthodes du dépôt** que l'interface :
    même effet, aucun curseur simulé, aucun texte tapé en animation.
  - « À moi de jouer » ouvre la vue Jour si besoin, puis demande le focus (`DemoTour.askFocus`) : la vue Jour défile
    jusqu'à l'entrée du repère (sans animation : `capture`, la ligne de la tâche, ou `running` en largeur étroite) et donne
    le focus à l'élément cerné (`FocusRequester` posé par son `DemoMark`, qui le passe au premier élément focalisable).
    Une demande n'est servie qu'une fois (`handledFocus`) : revenir sur la vue Jour ne la rejoue pas.
- **Repères** (`DemoMark(spot, taskId)`) : contour `BorderStroke(2.dp, primary)` aux coins de l'élément et étiquette « Ici »
  (`guide_spot_here`, icône `Spot`) qui chevauche le bord haut (posée dedans, elle cacherait un bouton de 48 dp), **tenus**
  tant que l'étape dure, sans animation, sans région vivante (le panneau annonce déjà l'étape). Posés en enveloppant
  l'élément lui-même (champ de capture, `PriorityPills`, `PointsPills`, boutons chrono et « Déplacer… » de la ligne de la
  tâche suivie, bouton d'arrêt de `RunningCard`) et non par coordonnées : ils suivent le rail, la barre basse et 360 dp.
  Hors démonstration, ou pour une autre étape, l'élément est rendu tel quel, sans conteneur. Balise de test
  `DEMO_MARK_TAG`.
- **Fin** : `DemoDoneDialog` (`AlertDialog`, « Quitter la démonstration » et « Recommencer »).
- **Entrées** : `DiscoverDialog(onDemo, demoBusy)` ajoute « Essayer en démonstration » (`demo_hub_title`, `demo_hub_hint`,
  icône `Science`) sous « Créer ma première tâche », seulement si `services.demo != null` ; grisée (couleurs « désactivé »
  de Material 3), avec `demo_hub_busy` à la place de l'explication, si `TimeTracking.runningSession` trouve une séance
  ouverte dans les vraies données. La carte `demo` du catalogue (chapitre « Premiers pas », `since` 3.13.0, icône `Science`)
  a pour cible `GuideTarget.Demo`, qui lance la démonstration sans changer d'écran ; pendant un chrono réel, elle affiche
  `demo_hub_busy` en message. `FeatureCatalog.README_FEATURES` la relie au README (« Interactive demo »).
- **Textes** : préfixe `demo_` et `card_demo_*`, six langues, parité (`StringsParityTest`). `demo_task_title` est le titre
  donné par « Faire pour moi » (sans préfixe d'exemple : `done` de l'étape 1 le compte comme tâche de l'utilisateur).
- **Pas de persistance** : rien dans `Settings.guide`, rien dans l'export, pas de schéma. L'entrée reste à rejouer.
- **Auto-test** : captures `demo` (rail, 1 200 px) et `demo-narrow` (barre basse, 360 px) d'une démonstration à l'étape de
  la priorité.

### Règle de livraison

Toute fonctionnalité visible par l'utilisateur (donc dans le README) ajoute ou
met à jour une carte dans le même changement ; `FeatureCatalogTest` casse sinon (règle consignée dans la skill `kairos-spec`, § 4).

### Décisions et pièges tracés

- **Sections : une énumération plutôt qu'une clé texte** (issue #95) : le compilateur garantit qu'une section citée
  existe ; le test vérifie qu'elle appartient à l'écran de sa carte. Écarté : des chaînes libres (« Day.Blocked »), qu'une
  faute de frappe rendait silencieuses.
- **Mise en valeur : contour et étiquette « Ici », pas la couleur seule** (choix du propriétaire, 2026-10-22) : même
  langage que la ligne mise en évidence par l'accompagnement de la première tâche (contour primaire de 2 dp). Sans
  animation de mouvement (respecte « réduire les animations ») ; 3 s, puis elle s'efface d'elle-même.
- **Section vide : le dire** (choix du propriétaire) : l'écran reste en haut et un message bref évite de chercher une
  section qui n'existe pas. Écarté : afficher la section vide le temps de la visite (une section fantôme dans la liste).
- **La vue Jour défile elle-même** : sa liste est paresseuse ; `bringIntoView` ne vaut que pour un élément composé.

- **Catalogue unique** (écarté : garder des listes par écran, qui divergent) : la
  dérive entre visite, aide et README est le défaut à éviter.
- **Pas de « vu » persistant** (écarté pour la 3.4) : évite un réglage de plus ;
  la section « Nouveau » est peu intrusive. **Repris en 3.7.0** (issue #94) sous la forme d'un sous-objet nul par
  défaut, sans effet sur les bases et les exports de ceux qui n'y touchent pas.
- **Chapitre « lu » : parcouru jusqu'au bout** (choix du propriétaire) : l'ouvrir ne suffit pas, et le compte carte par
  carte (« 3/5 ») était plus lourd pour peu de gain.
- **« Masquer » vaut pour la version majeure.mineure** : une version corrective n'ajoute pas de carte (`newIn` suit la
  même règle) ; la ramener aurait été du bruit.
- **Remise à zéro : deux chemins** (choix du propriétaire) : « Restaurer les valeurs par défaut », comme le demandait
  l'issue, et un bouton dédié, immédiat et annulable, pour qui veut revoir le guide sans toucher à ses réglages.
- **Visite initiale courte** (écarté : une visite exhaustive d'emblée) : un
  nouveau venu abandonne une visite de vingt étapes ; le hub porte la profondeur.
- **Titres de l'étape** : le dialogue mesure déjà le plus long titre et le plus
  long texte pour que les boutons ne sautent pas ; le catalogue conserve ce
  comportement (`GuideDialog`).
- **Visite interactive : une démonstration jetable en mémoire, jamais la vraie base** (2026-10-26, issue #96, décision du
  propriétaire ; implémenté en 3.13.0). Écartés : agir sur les vraies données en les « marquant » puis les nettoyant
  (un abandon, un plantage ou une erreur de nettoyage laissent une tâche parasite ; le réel et l'exemple se mélangent
  dans l'export et le fichier lié) ; une base de démonstration conservée (un fichier de plus à tenir, à vider, à
  exporter) ; une copie de la vraie base (les données de l'utilisateur dans une démonstration, durée et mémoire). La
  démonstration ne partage avec le réel que l'**apparence** (couleur, thème, langue, noms des niveaux), pour la
  continuité visuelle ; ses réglages sont ceux par défaut.
- **Rapport à « pas de pastilles sur l'interface » (`accueil.md`, 2026-10-01) : respectée sur le fond, rouverte sur deux
  points, explicitement.** Cette décision écartait les *coach marks* parce qu'une pastille ancrée à un élément de Compose
  Multiplatform par coordonnées casse dès que la taille de la fenêtre, le rail ou la barre basse changent. *Respectée* :
  le repère n'est pas une pastille posée par coordonnées, c'est un modificateur appliqué **à l'élément lui-même**, avec le
  rendu que `GuideMark` (3.7.0, issue #95) et la mise en valeur de la première tâche emploient déjà et qui suivent le
  rail, la barre basse et 360 dp ; les consignes vont dans un panneau en bas, jamais dans une bulle accrochée à
  l'élément ; les visites existantes (`GuideDialog`, « Voir l’écran ») ne changent pas, la démonstration s'ajoute.
  *Rouverte* : (a) la visite cerne désormais des **éléments** de l'écran réel (champ de capture, puces, boutons de la
  ligne), pas seulement des sections, le temps de l'étape ; (b) elle peut **agir** (« Faire pour moi »), ce que le
  hors-périmètre ci-dessus excluait (« visite interactive … à la place de l'utilisateur »), sur la démonstration
  seulement. Prix accepté : les repères sont de l'interface à maintenir quand une ligne de tâche change
  (`DemoUiTest` casse alors).
- **Rapport à « coach sur la vraie fiche » (`premiere-tache.md`)** : cette décision écartait une fiche factice qui ne crée
  rien et diverge de la vraie. La démonstration n'est pas un écran factice : ce sont les **vrais** écrans sur un dépôt
  réel et jetable, donc sans divergence possible. Elle ne remplace pas l'accompagnement de la première tâche, qui reste
  le chemin pour repartir avec une tâche à soi ; ni l'un ni l'autre ne passe par la base de l'autre.
- **« Faire pour moi » et « À moi de jouer » toujours ensemble** : un mode automatique seul serait un spectacle passif ;
  un mode « à toi » seul exclurait qui ne peut pas faire le geste (clavier seul, lecteur d'écran, contacteur). Les deux
  mènent au même effet dans les données.
- **L'étape se valide sur l'effet, pas sur le geste** : `done` lit l'instantané de la démonstration. Écarté : écouter les
  clics (fragile, exclut le clavier et le lecteur d'écran) et lire la place dans la frise (elle dépend de l'heure : le
  soir, tout est « Sans créneau »).
- **Six étapes, quatre scénarios, une minute** : le hub porte la profondeur (`Visite initiale courte`). Écartés : tout
  couvrir ; un scénario d'équipe (autre public, autre isolation) ; plusieurs tâches (une seule suffit à voir le fil
  capturer → qualifier → faire → déplacer).
- **Le lancement est une entrée du hub** (comme la première tâche d'équipe et la récurrente), pas une carte « faire » dans
  les visites : elle ajouterait à une visite une étape d'une autre nature. La fonctionnalité étant visible par
  l'utilisateur, elle a aussi sa carte de présentation dans le catalogue (règle de livraison, README) dont la cible
  `GuideTarget.Demo` lance la démonstration sans changer d'écran, comme `GuideTarget.FirstTask` lance l'accompagnement.
- **Pas de persistance** : ni « démonstration faite » ni chapitre « lu » (rien dans `Settings.guide`, donc rien dans
  l'export ni les bases de ceux qui n'y touchent pas) ; une rotation ou la mort du processus jette la démonstration.
  Écarté : recréer la base et restaurer l'étape (une complexité réelle pour un contenu jetable).
- **Entrée refusée pendant un chrono réel** : la coquille réelle quitte la composition pendant la démonstration, donc
  `ChronoWatcher` cesse de veiller (un seuil franchi serait manqué) et deux chronos coexisteraient à l'écran. Écarté :
  garder la coquille réelle composée dessous (double veille, double mémoire). Limite assumée : les rappels du bureau et du
  web (`ReminderWatcher`) sont aussi suspendus pour la durée, brève, de la démonstration ; ceux d'Android sont des alarmes
  du système et ne le sont pas.
- **Pas d'Accueil, de réglages ni de « ? » dans la démonstration** : rien n'y est enregistrable (les Réglages écriraient dans
  le dépôt jetable) et le « Importer » de la carte « Données » n'a pas de sens ; l'aide reste dans l'application réelle.
- **Aucun mouvement** : le choix de ne pas animer les repères (comme `GuideMark`), de défiler sans animation et de ne
  simuler ni curseur ni frappe est la façon de respecter « réduire les animations » sans dépendre d'un réglage système que
  Kairos ne lit pas.

### Écarts avec la spécification initiale (décidés à l'implémentation)

- **Pas de clé de section dans `GuideTarget`** (ex. `Day.Blocked`) en 3.4 : une carte ouvrait l'écran, pas un endroit
  précis. **Repris en 3.7.0** (issue #95) avec `GuideSection`, une fois le défilement ciblé disponible (enregistrement
  des positions de la vue Jour, `ErrorReveal` et catégories des Réglages).
- **L'aide d'un écran garde ses textes `help_*`** (mode d'emploi de l'écran) ; elle renvoie au hub par une entrée. Le catalogue
  décrit les fonctionnalités, pas le mode d'emploi de chaque écran : les deux ne se recouvrent pas, donc rien n'est écrit
  deux fois.
- **Chapitres de six cartes ou moins** : « Premiers pas » en compte cinq (dont « Découvrir Kairos » et la première tâche).
  L'inventaire du 2026-10-28 (une carte par fonctionnalité du README) a levé cette limite : « Premiers pas » en compte neuf
  avec la démonstration.
- **Démonstration, décidé à l'implémentation (2026-10-28, issue #96)** :
  - `DemoSource.open(langue)` reçoit la langue de l'interface : le jeu d'exemples est écrit dans la langue affichée, que la
    plateforme ne connaît pas au moment où elle construit ses services (la langue choisie dans Kairos peut différer de
    celle du système).
  - Le panneau est dans la `bottomBar` du `Scaffold`, pas superposé avec une marge basse mesurée : le contenu se réduit
    de sa hauteur, donc il ne cache jamais le repère, sans mesure à tenir. Prix : la liste change de hauteur d'une étape
    à l'autre.
  - `DemoPanel` est un composant à part, au même rendu que `CoachPanel` (carte à contour, région vivante, compteur
    d'étape) : leurs boutons diffèrent (« Faire pour moi », « À moi de jouer », « Passer l’étape ») et `CoachPanel` est
    lié à `CoachStep`.
  - La carte du catalogue n'est pas grisée pendant un chrono réel (le catalogue ne connaît pas l'état du chrono) : la
    toucher affiche la phrase de l'entrée grisée du hub.
  - Le cœur « Soutenir Kairos » est aussi retiré de la barre en démonstration : une barre sans aucune action, le bandeau
    et « Quitter » restent les seuls repères.
  - L'entrée grisée lit toute séance ouverte (`TimeTracking.runningSession` sur l'instantané complet), pas seulement celles
    de l'espace Perso : un chrono d'équipe tourne aussi sous la veille de la coquille réelle.
  - Tous les tests de la démonstration sont dans `DemoStepsTest` et `DemoUiTest` (module `desktopApp`, le seul à avoir
    SQLite en mémoire) plutôt que dans trois classes.

### Impacts sur les specs existantes (reportés en 3.13.0)

- `accueil.md` : renvoi (sa décision « pas de pastilles » est précisée par `visite-guidee.md` § Visite interactive) ;
  l'entrée du hub n'ajoute rien à l'Accueil lui-même.
- `premiere-tache.md` : renvoi sur la décision « coach sur la vraie fiche » (la démonstration est un dépôt jetable, pas une
  fiche factice).
- `architecture.md` : `AppServices.demo`, `DemoSource`, `DemoSession`, `forDemo` dans le graphe ; seule la plateforme
  fournit le pilote en mémoire.
- `navigation-theme.md` : `AppShell(demo)`, `DemoBanner`, `SaveableStateHolder` de `KairosApp`.
- `accessibilite.md` : panneau en région vivante polie, repère « Ici », aucun geste imposé, aucun mouvement.
- `i18n.md` : les clés `demo_*` dans les six langues.
- `distribution.md` : captures `desktop-demo.png` et `desktop-demo-narrow.png` de l'auto-test.
- README (`README.md` et `README.fr.md`, section Features), carte du catalogue (`FeatureCatalog` : `since` = version de
  livraison, `README_FEATURES`, `FeatureCatalogTest.readmeFeaturesAreCovered`) et notes de version Fastlane dans les six
  langues : une fonctionnalité visible par l'utilisateur.
- Cette spec : l'« État » passe à « implémentée en 3.13.0 », le hors-périmètre « visite interactive » est mis à jour, le
  marquage « non implémentée » retiré, et le tableau des étapes aligné sur `DemoSteps.ALL`. Les notes de version Fastlane
  viennent avec le passage en 3.13.0, en dernière PR du cycle.
