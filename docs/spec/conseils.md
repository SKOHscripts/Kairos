# Conseils au bon moment

_Rôle : proposer, de temps en temps et seulement quand c'est utile, **une** chose à regarder (surtout les statistiques,
parfois un réglage, parfois une fonctionnalité), avec les chiffres de l'utilisateur, et l'y mener d'un toucher. Fichiers
couverts (prévus) : `kmp/core/.../core/guide/Tips.kt` (sélecteur pur), `core/model/Settings.kt` (`GuideSettings`),
`kmp/ui/.../ui/guide/TipDialog.kt`, `ui/guide/TipWatcher.kt`, `ui/guide/GuideContent.kt` (sections et cibles nouvelles),
`ui/navigation/AppShell.kt`, `ui/settings/SettingsScreen.kt` (carte « Conseils »), ressources des six langues. Tests
(prévus) : `TipsTest`, `TipsPacingTest`, `TipsIsolationTest` (`core`), `KairosRepositoryTest` (mémoire),
`TipDialogUiTest` (`ui`), `StringsParityTest`._

État : issue #221 (jalon 4.2.0), **spécifiée le 2026-11-15, non implémentée**. Périmètre décidé par le propriétaire le
2026-11-15 : espaces Perso **et** Équipe. Points ouverts tranchés par le propriétaire le 2026-11-15 (§ Décisions validées).

## 1. Besoin métier (cahier des charges)

### Objectif / problème

Kairos guide l'utilisateur **quand il le demande** : accueil, visites, « Découvrir Kairos », « Nouveau dans cette
version », liste des fonctionnalités, « Voir l'écran » qui défile jusqu'à l'endroit et le met en valeur
(`visite-guidee.md`, `accueil.md`, `liste-fonctionnalites.md`). Rien ne vient **vers** lui au moment où une fonctionnalité
devient utile. Or beaucoup ne servent qu'une fois des données accumulées : la calibration demande au moins 3 tâches
chronométrées, les meilleures heures 5 sessions et 120 min par type (`statistiques.md`). D'autres dorment dans les
Réglages (horaires, rappels, récapitulatif du jour, fichier lié).

### Comportement attendu (utilisateur)

1. **Un dialogue, un conseil** (choix du propriétaire du 2026-11-06). Même allure que les cartes du guide : icône, titre,
   une ou deux phrases qui disent **pourquoi maintenant**, avec les chiffres de l'utilisateur (« Tu as terminé 12 tâches
   chronométrées. Tes estimations tiennent-elles ? »). Trois boutons :
   - **« Montre-moi »** ouvre l'écran et met l'endroit en valeur, comme « Voir l'écran » du guide (contour et « Ici »,
     `visite-guidee.md` point 3) ; section absente : le message d'absence habituel ;
   - **« Plus tard »** : le conseil revient au plus tôt dans **14 jours**, si sa condition tient encore ;
   - **« Ne plus me le proposer »** : ce conseil ne revient jamais (jusqu'à une remise à zéro).
   Fermer le dialogue autrement (retour système, Échap, toucher hors du dialogue) vaut « Plus tard ».
2. **Rythme** :
   - rien pendant les **3 premiers jours** (comptés depuis la plus ancienne donnée de l'utilisateur, exemples exclus) ;
   - au plus **un conseil par jour** et **deux par période de 7 jours glissants** ;
   - au plus un par lancement de Kairos ;
   - le conseil apparaît **5 s** après l'ouverture de Kairos, ou en arrivant sur l'Accueil s'il n'a pas pu apparaître à
     l'ouverture.
3. **Jamais** : pendant qu'un chrono tourne, dans la démonstration, pendant une visite, un chapitre du guide ou un
   accompagnement (première tâche, tâche récurrente, Réglages pas à pas, première tâche d'équipe), par-dessus un autre
   dialogue, ni pendant qu'un bandeau est affiché (mise à jour, raccourci, stockage du navigateur, estimations qui
   changent) : **une seule sollicitation à la fois**. Ni dans un écran secondaire (Réglages, liste des fonctionnalités).
   Une condition qui manque reporte le conseil au moment suivant (arrivée sur l'Accueil) ; elle ne le consomme pas.
4. **Priorité** quand plusieurs conseils sont dus : la sûreté des données, puis les statistiques, les réglages, les
   fonctionnalités ; dans un groupe, l'ordre des tableaux ci-dessous.
5. **Espace** : dans l'espace Perso, seuls les conseils Perso ; dans l'espace Équipe, seuls les conseils d'équipe. Le
   rythme est commun aux deux. Sans espace Équipe activé, aucun conseil ne lit une donnée d'équipe.
6. **Interrupteur « Conseils »** (activé par défaut) : Réglages → « À propos », sous « Revoir le guide
   depuis le début ». Effet immédiat, sans « Enregistrer » (comme la carte du guide). « Revoir le guide depuis le début »
   efface aussi ce que les conseils ont retenu (vus, écartés, reportés), mais pas l'interrupteur ; « Restaurer les
   valeurs par défaut » efface tout, interrupteur compris.
7. **Par appareil** : ce que les conseils retiennent reste sur l'appareil, comme le reste du guide (pas de fusion par le
   fichier lié). Un conseil vu sur le téléphone peut donc être proposé sur l'ordinateur.

#### Conseils de l'espace Perso

« Jours d'usage » : âge de la base (point 2). « Jour actif » : une date où une tâche a été créée ou modifiée, une note
créée ou une session de chrono démarrée. Les comptes ne lisent que l'espace Perso (tâches propres, pas les reçues).

| Id | Conseil | Condition (toutes requises) | Mène à |
|---|---|---|---|
| **Sûreté des données** | | | |
| `data_safety` | Mettre ses données à l'abri | ≥ 30 jours d'usage, ≥ 50 tâches, aucun fichier lié, aucun export depuis 90 jours sur cet appareil | Réglages → Données (carte « Données ») |
| **Statistiques** | | | |
| `stats_first` | Découvrir les statistiques | ≥ 7 jours d'usage, ≥ 5 tâches faites, Statistiques jamais ouvertes | Statistiques |
| `stats_calibration` | Tes estimations tiennent-elles ? | ≥ 10 tâches faites, chronométrées et estimées | Statistiques → calibration |
| `stats_correct` | Corriger les estimations | une calibration **fiable** (≥ 3 tâches) par type ou par niveau de points s'écarte d'au moins 25 % (réel ÷ estimé ≤ 0,8 ou ≥ 1,25), et au moins une tâche à corriger | Statistiques → « Corriger les estimations… » |
| `stats_hours` | Quand tu travailles chaque type | au moins un type est devenu **fiable** pour les meilleures heures (≥ 5 sessions et 120 min) | Statistiques → « Quand tu travailles chaque type » |
| `week_review` | Ta semaine passée | on est du lundi au mercredi, ≥ 5 tâches faites la semaine précédente ; revient au plus tous les 28 jours | Bilan, semaine précédente |
| **Réglages** | | | |
| `work_hours` | Régler tes horaires | horaires jamais personnalisés (9 h - 18 h, aucun horaire par jour) et sessions démarrées hors de ces horaires sur ≥ 3 jours des 14 derniers | Réglages → « Horaires par jour de la semaine » |
| `best_hours` | Planifier autour de tes meilleures heures | `stats_hours` déjà accepté ou condition vraie, réglage « Meilleures heures » coupé | Réglages → « Meilleures heures » |
| `reminders` | Les rappels | rappels coupés, et ≥ 3 échéances dépassées dans les 30 derniers jours ou ≥ 3 tâches à heure fixe dans les 14 prochains | Réglages → Rappels |
| `daily_summary` | Le récapitulatif du jour | récapitulatif coupé, ≥ 5 jours actifs sur 7 pendant chacune des 2 dernières semaines | Réglages → Rappels |
| `timer_limits` | Les seuils du chrono | une session de 14 derniers jours a duré au moins le seuil « chrono oublié » (180 min par défaut) | Réglages → « Alertes du chrono » |
| `calendar_import` | Importer ton calendrier | ≥ 5 créneaux occupés saisis à la main dans les 14 derniers jours, aucun calendrier importé, import possible sur la plateforme | Réglages → Données |
| `dark_theme` | Le thème sombre | thème effectif clair, activité après 20 h sur ≥ 5 des 14 derniers jours | Réglages → Apparence |
| **Fonctionnalités** | | | |
| `timer` | Le chrono | aucune session de chrono, ≥ 7 jours d'usage, ≥ 5 tâches faites | Jour → « Maintenant » |
| `recurrence` | Les tâches récurrentes | 3 tâches non récurrentes du même titre (sans casse ni accents) créées dans les 60 derniers jours, aucune tâche récurrente | « Créer ma première tâche récurrente » |
| `recap` | Le Bilan de la semaine et du mois | ≥ 14 jours d'usage, Bilan jamais ouvert | Bilan |
| `widgets` | Les widgets (Android) | Android, ≥ 10 jours actifs sur les 14 derniers, aucun widget posé | carte du guide « Ta journée sur l'écran d'accueil » |
| `shortcuts` | Les raccourcis clavier (bureau, web) | bureau ou web, ≥ 7 jours d'usage, aucun raccourci clavier jamais utilisé | carte du guide « Raccourcis clavier » |

#### Conseils de l'espace Équipe

Seulement si l'espace Équipe est activé et affiché. « Âge de l'équipe » : depuis la plus ancienne tâche d'équipe.

| Id | Conseil | Condition | Mène à |
|---|---|---|---|
| `team_signals` | Des tâches à surveiller | ≥ 3 tâches portent un signal « à surveiller » (sans avancement, ballottée, trop d'en-cours) ; revient au plus tous les 28 jours | Équipe → « Suivi » |
| `team_load` | Un membre est surchargé | un membre actif dépasse le seuil « à surveiller » de charge (90 % par défaut) sur l'horizon ; revient au plus tous les 28 jours | Équipe → « Équipe » (charge des membres) |
| `team_forecast` | Les prévisions | ≥ 8 tâches d'équipe faites (seuil « échantillons minimum »), au moins une échéance d'équipe, Prévisions jamais ouvertes | Équipe → « Prévisions » |
| `team_suggest` | La suggestion de répartition | ≥ 5 tâches d'équipe qualifiées et non assignées, au moins 2 membres actifs | Équipe → « Backlog » |
| `team_exchanges` | Les échanges par fichier | ≥ 14 jours d'âge de l'équipe, ≥ 5 tâches assignées à d'autres membres que « moi », aucun paquet jamais envoyé | Équipe → « Équipe » → onglet « Échanges » |

Priorité dans l'espace Équipe : les deux signaux de risque (`team_signals`, `team_load`), puis les fonctionnalités.

#### Textes (ton du guide, tutoiement en français)

Exemples ; les textes définitifs, avec leurs nombres, vivent dans les ressources des six langues.

- `stats_calibration` : « Tes estimations tiennent-elles ? » / « Tu as terminé 12 tâches chronométrées. Les statistiques
  comparent le temps prévu au temps réel, par type et par points. »
- `stats_correct` : « Tu sous-estimes le développement » / « Sur 7 tâches, le développement a pris 1,4 fois le temps
  prévu. Tu peux corriger les estimations d’après le temps réel. »
- `data_safety` : « Tes 64 tâches ne sont que sur cet appareil » / « Un fichier lié ou un export les met à l’abri d’une
  panne ou d’une perte. »
- `team_load` : « Alex est à 137 % » / « Sur les 4 prochaines semaines, Alex a plus de travail que de temps. La charge
  montre où rééquilibrer. »

### Critères de succès

- Chaque conseil : vrai sur un instantané qui remplit sa condition, faux dès qu'une condition manque, aux bornes
  (`TipsTest`, un cas par conseil et par borne, horloge simulée).
- Rythme : rien avant 3 jours, un par jour, deux par 7 jours glissants, « Plus tard » 14 jours, « Ne plus me le
  proposer » définitif, retour après 28 jours des conseils récurrents, priorité entre groupes (`TipsPacingTest`).
- Isolation : sans espace Équipe activé, un instantané qui contient des données d'équipe donne exactement le même
  résultat qu'un instantané qui n'en a pas, et aucun conseil d'équipe (`TipsIsolationTest.soloNeverReadsTeamData`).
- Mémoire : nulle par défaut ; une base où le guide n'a rien retenu (aucun conseil montré, aucun écran suivi ouvert,
  aucun export fait) garde un export identique octet pour octet (`KairosRepositoryTest.tipsMemoryIsWrittenOnlyWhenUsed`,
  `TeamIsolationTest.soloExportIsByteIdentical` inchangé) ; « Revoir le guide » et « Restaurer les valeurs par défaut »
  effacent ce qu'il faut (`GuideMemoryUiTest`).
- Interface : dialogue avec les trois boutons, « Montre-moi » met l'endroit en valeur ; aucun conseil pendant un chrono,
  une démonstration, une visite ou un autre dialogue ; interrupteur coupé = aucun conseil (`TipDialogUiTest`).
- Concordance : chaque conseil désigne une carte du catalogue qui existe, dont les plateformes couvrent les siennes et
  qui mène au même écran (`TipsCatalogTest`) ; la carte `tips` est reliée au README comme toute fonctionnalité
  (`FeatureCatalogTest`).
- Textes dans les six langues (`StringsParityTest`), cibles de 48 dp, 360 dp sans débordement, captures `--self-test`
  (`desktop-tip.png`, `desktop-tip-narrow.png`) regardées.

### Hors périmètre / différé

- Notifications du système : un conseil ne s'affiche que dans Kairos ouvert.
- Conseils dans les widgets, dans la démonstration, ou sur la page web `site/`.
- Apprendre de ce que l'utilisateur accepte ou écarte (seuils fixes).
- Plus d'un conseil à la fois, une liste de conseils, un centre de notifications.
- Fusion de la mémoire des conseils entre appareils par le fichier lié.
- Conseils sur les prévisions par tâche (`previsions-taches.md`) et sur le modèle d'estimation (`estimateur-modele.md`,
  qui a déjà son bandeau et sa revue).

## 2. Solution technique

### Calcul pur : `core/guide/Tips.kt`

`core` reste pur : instant, fuseau, faits de plateforme et d'usage arrivent en paramètres.

- `enum class TipId(val group: TipGroup, val space: TipSpace, val recurring: Boolean, val card: String)`, dans l'ordre de
  priorité ; `card` est l'identifiant de la carte du catalogue que le conseil fait découvrir (`core` ne connaît pas le
  catalogue, qui vit dans `ui` : le lien est vérifié par test, § Concordance) ;
  `TipGroup { DATA, STATS, SETTINGS, FEATURES, TEAM_RISK, TEAM_FEATURES }`.
- `data class TipFacts(platform: TipPlatform, darkTheme: Boolean, linkedFile: Boolean, calendarImport: Boolean,
  widgetPlaced: Boolean?)` : ce que seule la plateforme sait (`widgetPlaced` nul hors Android).
- `data class Tip(id: TipId, numbers: List<Number>, target: TipTarget)` : les nombres du texte (« 12 », « 1,4 »,
  « Alex », « 137 ») et la cible, une valeur de `core` traduite en `GuideTarget` par l'interface.
- `object Tips` :
  - `fun due(snapshot, memory: GuideSettings?, facts, space, now, tz): Tip?` : `null` si l'interrupteur est coupé, si le
    rythme l'interdit, sinon le premier conseil de l'espace dont la condition tient et que la mémoire laisse passer
    (non écarté, pas reporté au-delà de `now`, pas déjà accepté sauf récurrent passé son délai) ;
  - `fun pacingAllows(memory, now, tz, baseAge): Boolean` (3 jours, 1 par jour, 2 par 7 jours) ;
  - une fonction par condition (`dataSafety`, `statsFirst`…), testable seule.
- Sources réutilisées, sans recalcul parallèle : `TaskStats.calibrationByType`, `fibonacciCalibration` et `MIN_SAMPLE`
  (calibration fiable), `EstimateCorrection.candidates` (tâches à corriger), `BestHours` et `MIN_RELIABLE_SESSIONS` /
  `MIN_RELIABLE_MINUTES`, `TimeTracking` (sessions, chrono ouvert), `ExampleData.isExample`, `SearchFold` (titres
  comparés sans casse ni accents), `TeamSignals`, `TeamLoad` et `Settings.team.loadWarnPercent`, `ExchangeStatus`
  (dernier paquet par membre).
- **Isolation** : en espace Perso, `due` reçoit l'instantané Perso (`personalSnapshot`) ; les conditions d'équipe ne
  sont évaluées que si `settings.teamModeEnabled` et `space == TEAM`. Aucune condition Perso ne lit `members`,
  `teamEvents`, `teamScenarios` ni une tâche dont `space != PERSONAL`.

### Mémoire : `GuideSettings`

Champs ajoutés, tous `@EncodeDefault(NEVER)` (rien d'écrit à la valeur par défaut, `Settings.guide` reste nul tant que
rien n'est retenu) ; `guide` est déjà dans `SyncScope.LOCAL_SETTINGS` (propre à l'appareil) :

- `tipsOff: Boolean = false` : l'interrupteur « Conseils » ;
- `tipsShown: List<String> = []` : les deux dernières dates (ISO) où un conseil a été montré (rythme) ;
- `tipsSnoozed: Map<String, String> = {}` : id → date jusqu'à laquelle le conseil attend (« Plus tard ») ;
- `tipsDone: Map<String, String> = {}` : id → date d'acceptation (« Montre-moi ») ;
- `tipsDismissed: List<String> = []` : ids écartés pour de bon ;
- `seenScreens: List<String> = []` : écrans suivis déjà ouverts au moins une fois (`stats`, `recap`, `team_forecast`) et
  `shortcut` au premier raccourci clavier utilisé ;
- `lastExport: String? = null` : date du dernier export réussi depuis cet appareil.

`KairosRepository.updateGuide` est réutilisé tel quel. `resetGuide()` devient « tout effacer sauf `tipsOff` » (comportement attendu, point 6) ;
« Restaurer les valeurs par défaut » remet toujours `guide` à nul.

### Interface

- `TipWatcher` (dans `AppShell`, coquille réelle seulement : jamais quand `demo != null`) : une fois par lancement,
  5 s après la composition, puis à chaque arrivée sur l'Accueil tant qu'aucun conseil n'a été montré dans ce lancement,
  vérifie les conditions d'affichage du point 3 (aucun dialogue ni état de visite ouvert dans `AppShell`, aucune séance
  ouverte, aucun bandeau, destination principale), appelle `Tips.due` et pose le conseil dans un état
  `tip` de la coquille. Il écrit `tipsShown` **au moment où le dialogue s'affiche**.
- `TipDialog` : `AlertDialog` Material 3 (forme 28 dp, ombre de dialogue), icône du conseil, titre, texte avec les
  nombres ; boutons « Montre-moi » (`Button`), « Plus tard » (`TextButton`), « Ne plus me le proposer » (`TextButton`)
  empilés sous 360 dp. « Montre-moi » écrit `tipsDone` puis appelle `openTarget` (le chemin de « Voir l'écran »).
- Cibles : `GuideTarget` existants, plus `GuideSection` nouvelles `STATS_CALIBRATION`, `STATS_CORRECT` (la ligne
  « Corriger les estimations… »), `GuideTarget.RecurringTask` (lance « Créer ma première tâche récurrente »),
  `GuideTarget.Card(id)` (ouvre une seule carte du catalogue dans `GuideDialog`, pour `widgets` et `shortcuts`) et
  `GuideTarget.Personal(WEEK, recapWeekOffset = -1)` pour la semaine précédente du Bilan.
- Faits de plateforme : `AppServices.widgets: WidgetProbe?` (Android : `AppWidgetManager.getAppWidgetIds` des deux
  widgets ; nul ailleurs) ; `linkedStorage`, `calendars` et le thème effectif sont déjà connus. `forDemo` met
  `widgets` à nul (`DemoStepsTest.demoServicesCarryNothingFromTheRealOnes`).
- Écrans suivis : `StatsScreen`, le Bilan et `ForecastScreen` ajoutent leur nom à `seenScreens` à la première ouverture ;
  le premier raccourci clavier ajoute `shortcut`. Un ajout déjà présent n'écrit rien.
- Export : `DataCard` écrit `lastExport` après un export réussi.
- Réglages : carte « Conseils » (interrupteur) dans la catégorie « À propos », sous « Revoir le guide depuis le début ».

### Décisions et pièges tracés

- **Âge de la base lu dans les données, pas une date d'installation retenue** : retenir la date du premier lancement
  écrirait `guide` dès le premier jour et changerait l'export de tout le monde. La plus ancienne date de création
  (tâches, notes, créneaux, exemples exclus) suffit ; une base vide n'a pas de conseil.
- **Jour actif approché** : `Task.updatedAt` ne garde que la dernière modification ; une date d'activité est donc une
  date de création de tâche ou de note, de dernière modification de tâche ou de début de session. Sous-estime l'usage,
  donc retarde un conseil plutôt que de le faire venir trop tôt.
- **`work_hours` sans le seul critère « horaires par défaut après 7 jours »** de l'issue : 9 h - 18 h convient à beaucoup ;
  seul un travail régulier hors de ces horaires justifie le conseil.
- **`week_review` mène au Bilan de la semaine passée**, pas aux évolutions des Statistiques comme l'issue le proposait :
  le Bilan (`bilan.md`, 4.1.0, postérieur à l'issue) est l'écran fait pour cela.
- **`recap` remplace « Week or Month view »** : la destination Semaine est devenue Bilan (avec la bascule Mois).
- **Conseils récurrents** (`week_review`, `team_signals`, `team_load`) : un signal qui revient mérite d'être redit, mais
  au plus tous les 28 jours, pour ne pas occuper tout le rythme de deux par semaine.
- **Un bandeau ou un dialogue présent reporte le conseil** sans le consommer : « une sollicitation à la fois ».
- **La section « Nouveau dans cette version » n'empêche pas un conseil** : c'est une section de l'Accueil, pas une
  sollicitation qui interrompt.
- **Fermer sans choisir = « Plus tard »** : le cas le plus prudent ; un retour système ne vaut pas un refus définitif.
- **Mémoire propre à l'appareil** (déjà le cas de `guide`) : la synchroniser obligerait à fusionner des dates et des
  listes pour un gain faible.

### Concordance avec le catalogue et le README

Demande du propriétaire (2026-11-15) : les cartes du catalogue, le README et les conseils restent synchronisés.

- Chaque `TipId` porte la carte qu'il fait découvrir (`card`), par exemple `stats_calibration` → `stats`,
  `stats_correct` → `stats_correct`, `timer` → `time`, `recurrence` → `recurrence`, `widgets` → `widgets`,
  `team_forecast` → `team_forecast`.
- `TipsCatalogTest` (module `ui`, qui voit `core` et le catalogue) : la carte existe dans `FeatureCatalog.cards` ; ses
  plateformes couvrent celles du conseil (un conseil Android n'a pas de carte réservée au bureau) ; la cible du conseil
  ouvre le même écran que celle de la carte (même destination, ou la même catégorie des Réglages, ou une carte unique) ;
  chaque conseil d'équipe désigne une carte du chapitre `TEAM`. Supprimer ou déplacer une carte casse donc le test du
  conseil qui la cite.
- Les conseils eux-mêmes sont une fonctionnalité : carte `tips` (chapitre « Premiers pas », `since` 4.2.0, icône
  `Lightbulb`), puce de la section Features et ligne du tableau des deux README, reliées par `README_FEATURES`
  (`FeatureCatalogTest`, règle de livraison de `visite-guidee.md`).

### Décisions validées (propriétaire, 2026-11-15)

1. **Export identique tant que le guide n'a rien retenu** : retenir un écran suivi ouvert ou un export écrit `guide`
   avant tout conseil, et c'est voulu. Écarté : ne retenir les écrans qu'après un premier conseil (proposer les
   Statistiques à qui les a déjà ouvertes).
2. **`lastExport` gardé** : `data_safety` ne vient pas à qui exporte à la main au moins tous les 90 jours.
3. **Seuils des tableaux retenus**, y compris les cinq conseils d'équipe.
4. **Retour des conseils récurrents : 28 jours.**
5. **« Revoir le guide depuis le début » garde l'interrupteur** ; seule la restauration des valeurs par défaut le remet.
6. **Fonctionnalité du README**, avec la concordance ci-dessus.

### Impacts sur les specs existantes (à reporter à l'implémentation)

- `visite-guidee.md` : `GuideSettings` (nouveaux champs), `resetGuide` qui garde `tipsOff`, `GuideTarget.RecurringTask` et
  `GuideTarget.Card`, sections `STATS_CALIBRATION` et `STATS_CORRECT`, renvoi vers cette spec.
- `reglages.md` : carte « Conseils » de « À propos », effet immédiat ; « Restaurer les valeurs par défaut » l'efface.
- `statistiques.md`, `bilan.md`, `equipe-simulation.md` : l'écran retient sa première ouverture (`seenScreens`) ; Bilan
  ouvrable sur la semaine précédente.
- `export-import.md` : un export réussi retient sa date (`lastExport`).
- `widgets-android.md` et `architecture.md` : `AppServices.widgets` (`WidgetProbe`), décidé dans `forDemo`.
- `vue-jour.md` (raccourcis clavier) : le premier raccourci utilisé est retenu.
- `equipe.md` : isolation (les conseils d'équipe ne sont lus que dans l'espace Équipe activé).
- `i18n.md` : clés `tip_*` dans les six langues.
- `distribution.md` : captures `desktop-tip.png` et `desktop-tip-narrow.png`.
- `liste-fonctionnalites.md`, README : carte `tips`, puce, ligne du tableau ; notes de version avec la 4.2.0 finale.
- `docs/spec/README.md` : ligne de cette spec à mettre à jour (« implémentée en 4.2.0 »).
