# Accompagnement de la première tâche

_Rôle : guider pas à pas la création d'une vraie première tâche, en expliquant
chaque champ, la priorité, les points (Fibonacci), l'estimation et le placement,
puis féliciter. Fichiers couverts : `kmp/ui/.../ui/guide/FirstTaskCoach.kt`,
`ui/day/EditField.kt`, `ui/day/EditTaskDialog.kt`, `ui/day/DayScreen.kt`,
`ui/day/FirstTaskCards.kt`, `ui/day/TaskRow.kt`, `ui/app/Welcome.kt`,
`ui/navigation/AppShell.kt`, `ui/navigation/NavState.kt`, `core/ExampleData.kt`
(`isExample`). Tests : `FirstTaskCoachUiTest`, `FirstTaskCoachTest`,
`ExampleDataTest`, `M6GuideUiTest`, `M5ScreensUiTest`. Récurrente et Réglages (3.9.0) : `RecurrenceCoach`, `RecurrencePreview`
(`ui/guide/FirstTaskCoach.kt`), `ui/guide/SettingsTour.kt`, `core/engine/Recurrence.kt` (`preview`) ; tests
`RecurrenceCoachTest`, `RecurrencePreviewTest`, `RecurrenceCoachUiTest`. Première tâche d'équipe (3.8.0) : `ui/team/TeamTaskCoach.kt`,
`TeamTaskCoach` (`ui/guide/FirstTaskCoach.kt`), `ui/team/TeamTaskSheet.kt`, `ui/guide/DiscoverDialog.kt`,
`ui/navigation/AppShell.kt` ; tests `TeamTaskCoachTest`, `TeamTaskCoachUiTest`._

État : **issue #64 (retour du propriétaire)**, spécifiée le 2026-10-13,
**implémentée en 3.4.0** ; première tâche d'équipe (issue #97) implémentée en 3.8.0 ; première tâche
récurrente et visite des Réglages (issue #98) implémentées en 3.9.0. Complète `accueil.md` (accueil du premier lancement) et
s'appuie sur le catalogue de `visite-guidee.md`.

## 1. Besoin métier (cahier des charges)

### Objectif / problème

Au premier lancement, l'accueil présente Kairos et des **exemples**
(« [Exemple] »), et la visite décrit les écrans. Mais personne n'accompagne la
**création d'une vraie tâche**, alors que la fiche cumule titre, description,
priorité, points, minutes, échéance, type puis des options avancées (projet,
temps manuel, jour du mois, heure fixe, sous-tâches, bloqueurs, récurrence). Le
vocabulaire (priorité P0 à P3, points de Fibonacci, score, placement) est
déroutant sans explication.

### Comportement attendu (utilisateur)

1. **Entrée** : l'accueil du premier lancement propose « Créer ma première
   tâche » à côté de « Visite guidée ». Une carte d'invitation reste sur la vue
   Jour tant que l'utilisateur n'a créé aucune tâche autre que les exemples **et n'a jamais mené l'accompagnement à son
   terme** (3.7.0, issue #94 : une tâche enregistrée depuis la fiche guidée ; l'invitation ne revient plus, même si l'on
   supprime ensuite toutes ses tâches) ; « Pas maintenant » la masque pour la session. Le hub « Découvrir Kairos »
   permet toujours de rejouer l'accompagnement.
2. **Création guidée dans la vraie fiche** : l'utilisateur remplit la vraie
   fiche d'une nouvelle tâche. Un panneau d'explication, en bas de la fiche, met
   en évidence le champ expliqué et dit à quoi il sert, avec « Précédent »,
   « Suivant » et « Passer ». Rien n'est imposé, sauf le titre à
   l'enregistrement ; les champs restent modifiables à tout moment.
3. **Étapes** : (1) titre : un verbe et un résultat ; (2) description ; (3)
   priorité : P0 à P3, le rouge est réservé à P0 ; (4) points : taille relative et
   pourquoi la suite de Fibonacci, avec les repères du guide des points ; (5)
   estimation en minutes : durée suggérée et durée apprise ; (6) échéance et date
   programmée ; (7) type, et ce qu'il change pour les horaires ; (8) aperçu des
   options avancées (projet, récurrence, bloqueurs, heure fixe, sous-tâches) ;
   (9) enregistrer.
4. **Placement** : après l'enregistrement, la vue Jour s'ouvre sur la tâche,
   mise en évidence, et le coach explique sa **position** : le score, son rang, son
   créneau dans la frise, « Pourquoi à cette place ? », puis les éléments de la
   ligne (priorité, points, durée, badges, échéance, boutons Faire, Chrono,
   Décaler).
5. **Félicitations** : un dialogue chaleureux, une fois la tâche **réellement
   créée**, propose la suite (« Lancer le chrono », « Découvrir Kairos »). Pas
   d'animation décorative.
6. **Interruption** : fermer la fiche ou le coach n'écrit rien ; rouvrir
   recommence au titre. La rotation de l'écran conserve l'étape.

7. **Première tâche d'équipe** (3.8.0, issue #97). Le manager qui active l'espace Équipe découvre une fiche aux
   champs propres (titulaire, effort). Dans « Découvrir Kairos », sous le chapitre de l'équipe et **seulement si
   l'espace Équipe est activé**, l'entrée « Créer ma première tâche d'équipe » (« Pas à pas : titulaire, taille,
   effort, catégorie. ») ouvre l'espace Équipe, le Backlog, et la vraie fiche d'une **nouvelle tâche d'équipe**
   (« Nouvelle tâche d'équipe », sans onglets ni avancement ni « Commencer » tant qu'elle n'existe pas), avec le même
   panneau (non bloquant, « Précédent / Suivant / Passer le guide », région vivante). Sept étapes : ce que l'équipe
   doit livrer (titre, description) ; qui la porte (« Assigné à » : Backlog, ou un membre dont le menu montre la
   charge) ; priorité et taille (points, effort calibré) ; effort et échéance (l'estimation l'emporte, la ligne
   « Effort », les signaux) ; catégorie (type de tâche) ; pour aller plus loin (bloqueurs entre tâches d'équipe,
   récurrence qui garde le titulaire, le reste comme en Perso) ; enregistrer (Backlog ou « À faire » chez le
   titulaire, puis « Commencer », l'avancement, l'« Historique »). Les félicitations « Première tâche d'équipe créée »
   ne viennent qu'une fois la tâche **réellement créée** : « « X » est au Backlog : assigne-la quand quelqu'un peut la
   prendre. » ou « « X » est « À faire » chez Léa : le Suivi montre son état et sa charge. », avec « Voir le Backlog »
   ou « Voir le Suivi », et « Fermer ». « Annuler » n'écrit rien. Rien de tout cela n'existe en mode solo.

8. **Première tâche récurrente** (3.9.0, issue #98). La récurrence est là où l'on se perd : la différence entre
   « hebdomadaire » et « le N du mois », pourquoi une tâche « jours ouvrés » saute un férié. Dans « Découvrir
   Kairos », sous le chapitre « Organiser », l'entrée « Créer ma première tâche récurrente » (« Pas à pas : la règle,
   la première date et les suivantes. ») ouvre la vue Jour et la vraie fiche d'une nouvelle tâche avec le même
   panneau. Cinq étapes : ce qui revient (titre) ; la première date (échéance et date programmée : la règle part de
   là) ; la règle (le champ « Récurrence » : quotidienne, jours ouvrés, hebdomadaire, mensuelle, le N du mois), avec,
   **pour chaque règle, la prochaine échéance qu'elle produira** à partir de la date saisie (ou d'aujourd'hui), la
   règle choisie marquée, fériés des réglages compris ; ce qui se répète (priorité, points, minutes valent pour
   chaque occurrence, la durée apprise suit la série) ; enregistrer (terminer une occurrence crée la suivante, « le
   N du mois » est posée chaque mois). Mêmes règles que la première tâche : « Annuler » n'écrit rien, les
   félicitations suivent la création réelle.
9. **Visite des Réglages** (3.9.0, issue #98). Dans « Découvrir Kairos », sous le chapitre « Réglages », l'entrée
   « Régler Kairos pas à pas » (« Journée, horaires, contextes, meilleures heures : une carte à la fois. ») ouvre les
   Réglages sur la catégorie de la première carte. Un panneau en bas de l'écran (le même, non bloquant, « Passer »)
   mène de carte en carte : la journée de travail ; les horaires par jour de la semaine ; les contextes ; les
   meilleures heures ; enregistrer. La carte de l'étape est **cernée tant que l'étape dure** et amenée à l'écran ; la
   catégorie s'ouvre d'elle-même. **Rien n'est écrit** tant que l'utilisateur n'appuie pas sur « Enregistrer » :
   passer ou finir la visite laisse le formulaire tel quel.

### Critères de succès

- Parcours complet : la tâche existe avec les valeurs saisies, les félicitations
  n'apparaissent qu'après création, rien n'est écrit si l'on abandonne
  (`FirstTaskCoachUiTest`, `givingUpWritesNothing…`).
- **Couverture** : chaque champ de la fiche a une étape (`FirstTaskCoachTest.everyFieldOfTheSheetHasExactlyOneStep` compare la liste du coach à `EditField`, et
  `FirstTaskCoachUiTest.theWholeWalkCreatesTheTaskThenCongratulatesAndExplainsItsPlace` vérifie, étape par étape, que
  la vraie fiche porte la balise de chaque champ expliqué).
- Le nouveau bouton de l'accueil ne casse pas l'accueil existant
  (`M5ScreensUiTest`, `M6GuideUiTest`).
- Accessibilité : cibles ≥ 48 dp, 360 dp sans débordement, clavier ouvert sans
  masquer le champ, annonce de l'étape pour TalkBack, aucun raccourci clavier
  annoncé sur Android ; textes FR/EN (`StringsParityTest`) ; captures
  `--self-test` et `--store-screenshots` regardées.

- Première tâche d'équipe : chaque champ de la fiche d'équipe a exactement une étape, « Assigné à » compris, la
  marche part du titre et finit sur l'enregistrement, ses textes sont les siens (`TeamTaskCoachTest`) ; parcours
  complet avec Léa assignée, journal `created` et `assigned`, félicitations après la création seulement, « Voir le
  Suivi » ; abandon sans écriture ; aucune entrée en mode solo (`TeamTaskCoachUiTest`) ; captures
  `desktop-team-first-task-coach(-narrow).png` regardées.

- Récurrente (3.9.0) : la marche part du titre, couvre « Récurrence », finit sur l'enregistrement, ses champs sont de
  vrais champs de la fiche (`RecurrenceCoachTest`) ; l'aperçu donne une date par règle, « jours ouvrés » saute
  week-end et fériés, « le N du mois » recule au jour ouvré précédent (`RecurrencePreviewTest`) ; parcours complet
  qui crée une tâche hebdomadaire, abandon sans écriture (`RecurrenceCoachUiTest`).
- Visite des Réglages (3.9.0) : une carte des Réglages par étape puis l'enregistrement (`RecurrenceCoachTest`) ; chaque
  étape ouvre la catégorie de sa carte ; finir n'écrit rien ; le formulaire modifié pendant la visite ne s'enregistre
  qu'avec « Enregistrer » (`RecurrenceCoachUiTest.theSettingsTourGoesCardByCardAndWritesNothingUntilSave`).

### Hors périmètre / différé

- Expliquer la place d'une tâche d'équipe après sa création (le Suivi la montre ; pas d'étapes de placement).
- Visite des autres cartes des Réglages (équipe, données) : les cartes du catalogue et « Aide » suffisent.

## 2. Solution technique

### Un coach par-dessus la vraie fiche

`EditTaskDialog` sert aussi à la création : une tâche brouillon d'identifiant 0 (`creating`), sans « Supprimer », dont
« Enregistrer » exige un titre non vide (titre de la fiche « Première tâche »). Le paramètre optionnel
`coach: CoachSession?` ajoute le panneau d'explication en bas de la fiche ; sans coach, la fiche est inchangée.

- `EditField` (`ui/day/EditField.kt`) : les quatorze champs de la fiche, dont `advanced` pour ceux des options avancées ;
  chacun porte la balise de test `edit-field-<NOM>` (`Modifier.coachField`).
- `FirstTaskCoach.STEPS` (`ui/guide/FirstTaskCoach.kt`) : neuf `CoachStep`, chacune avec les champs qu'elle explique :
  titre ; description ; priorité ; points ; minutes ; échéance et programmée ; type ; options avancées (projet, temps
  manuel, récurrence, heure fixe, sous-tâches, bloqueurs) ; enregistrer (met « Enregistrer » en évidence).
  Une étape qui explique un champ avancé ouvre les options avancées.
- Mise en évidence : contour primaire de 2 dp et défilement jusqu'au champ (`BringIntoViewRequester`) ; une marge constante
  est posée dès qu'un coach est là pour que la fiche ne saute pas d'étape en étape ; jamais la couleur seule : le panneau
  nomme l'étape.
- Panneau (`CoachPanel`) : `OutlinedCard` sans ombre, non bloquant (le focus et le clavier restent sur la fiche), région
  vivante polie (TalkBack annonce l'étape), « Étape n sur N », « Passer le guide » (la fiche reste utilisable, « Terminer »
  à la dernière étape), « Précédent » dès la deuxième, « Suivant ».
- État : `DayScreen` garde en `rememberSaveable` `creating`, `coachStep` (-1 : coach passé), `placedId`, `congrats`,
  `placedStep` ; l'étape survit donc à une rotation. Fermer la fiche n'écrit rien ; rouvrir recommence au titre, fiche vide.

### Création, placement et félicitations

« Enregistrer » appelle `KairosRepository.createTask(titre)` puis `updateTask(id, édition)` : c'est la création **réelle**
(l'`id` rendu) qui pose `placedId` et affiche `FirstTaskCongratsDialog`, jamais le dernier clic du coach (« Passer le
guide » puis enregistrer félicite aussi). Le dialogue propose « Lancer le chrono » (`startTimer` sur la tâche), « Me montrer
où elle est placée » et « Découvrir Kairos » (hub), ou « Fermer ».

« Me montrer où elle est placée » lance trois étapes sur la vue Jour (`FirstTaskCoach.placement(placed)`), dans un panneau
en bas d'écran : son rang (le score, « Pourquoi à cette place ? ») ; son créneau (la frise) ; la ligne (coche, chrono,
« Décaler », crayon, badges). Une tâche absente de l'agenda du jour (« À traiter » faute de priorité ou de points, ou « Plus
tard ») remplace la première étape par « Pas encore placée » qui dit pourquoi. Pendant ces étapes, `RowContext.highlight`
cerne la ligne de la tâche (2 dp primaire) et la liste défile jusqu'à elle (`ListBuilder` retient la place de chaque ligne,
`rowPositions`).

### Première tâche d'équipe (`TeamTaskCreateDialog`, 3.8.0)

- `DiscoverDialog(onFirstTeamTask)` : l'entrée suit la ligne du chapitre `TEAM` (proposé seulement si
  `teamModeEnabled`). `AppShell` passe dans l'espace Équipe, ouvre le Backlog et pose `teamCoachStep = 0`
  (`rememberSaveable` : l'étape survit à une rotation, `-1` = coach passé, `null` = fiche fermée).
- `TeamTaskCreateDialog(services, étape, …)` : brouillon `Task(0, "", space = TEAM)`, `EditTaskDialog` avec
  `TeamTaskSheet(editable = true)` (menu d'assignation et sa charge, `rememberTeamLoad`) et
  `CoachSession(steps = TeamTaskCoach.STEPS)`. En création, `EditTaskDialog` titre « Nouvelle tâche d'équipe »
  (`edit_title_first_team`) et n'affiche pas les onglets ; `TeamTaskFields` n'affiche ni avancement ni « Commencer »
  (`task.id == 0`). « Enregistrer » : `createTeamTask` puis `updateTask` ; `onCreated(id)` pose `teamCreated`.
- `TeamTaskCreatedDialog` : lit la tâche créée et son titulaire dans la base ; « Voir le Backlog » (sans titulaire) ou
  « Voir le Suivi », « Fermer » (`discover_close`).

### Déclenchement

- `WelcomeDialog(onFirstTask)` : bouton « Créer ma première tâche » sous le texte des exemples (visite guidée et
  « Commencer » inchangés) ; il ferme l'accueil et lance l'accompagnement.
- `AppShell.startFirstTask` : revient à l'espace Perso, ouvre la vue Jour et pose `NavState.firstTaskRequest` ; `DayScreen`
  l'acquitte (`onFirstTaskHandled`) et ouvre la fiche. Mêmes chemins : `GuideTarget.FirstTask` (cartes du catalogue, hub
  « Découvrir Kairos » : on rejoue l'accompagnement ; l'enregistrement reste un geste volontaire).
- Carte d'invitation `FirstTaskInvite` en tête de la vue Jour tant que `snapshot.tasks.none { !ExampleData.isExample(it) }`
  et que `settings.guide?.firstTaskDone` n'est pas vrai, masquable pour la session (« Pas maintenant »). `DayScreen`
  pose `firstTaskDone` (`KairosRepository.updateGuide`) quand une tâche est enregistrée depuis la fiche alors que
  l'accompagnement est actif (`coachStep >= 0`) ; `visite-guidee.md` § Persistance. `ExampleData.isExample`
  reconnaît le préfixe de titre « [Exemple] » / « [Example] » / « [Ejemplo] » / « [Beispiel] » / « [Пример] » / « [Esempio] » dans les six langues.

### Première tâche récurrente et visite des Réglages (3.9.0, issue #98)

- **Récurrente** : `RecurrenceCoach.STEPS` (cinq `CoachStep`) : titre ; échéance et programmée ; récurrence
  (`preview = true`) ; priorité, points et minutes ; enregistrer. `DiscoverDialog(onFirstRecurring)` : l'entrée suit la
  ligne du chapitre `ORGANIZE`. `AppShell.startTask(recurring = true)` pose `NavState.firstTaskRecurring` puis `firstTaskRequest` ;
  `DayScreen(firstTaskRecurring)` garde `coachRecurring` (`rememberSaveable`) et passe
  `CoachSession(steps = RecurrenceCoach.STEPS, holidays)`. La même création, les mêmes félicitations ; l'invitation
  de la première tâche n'est pas concernée (`firstTaskDone` n'est posé que par l'accompagnement de la première tâche).
- **Aperçu** : `Recurrence.preview(base, dayOfMonth, holidays)` (`core`, pur) rend, pour chaque règle autre que
  « aucune », la prochaine échéance après `base` : `nextDeadline` (ancre hebdomadaire = jour de `base`) ; « le N du
  mois » = `calendarDeadline` du mois suivant (N = jour saisi, sinon celui de `base`). `base` = échéance saisie, sinon
  date programmée, sinon aujourd'hui ; fériés = `Workdays.holidaysFor` des réglages. `CoachPanel(extra)` affiche
  `RecurrencePreview` (une ligne par règle, la règle choisie en gras et suivie de « (ta règle) ») sous le texte de
  l'étape.
- **Visite des Réglages** (`ui/guide/SettingsTour.kt`) : `SettingsTour.STEPS` : `SETTINGS_WORKDAY` (nouvelle section,
  carte « Journée de travail »), `SETTINGS_HOURS`, `SETTINGS_CONTEXTS`, `SETTINGS_BEST_HOURS`, puis l'étape
  « Enregistrer » (sans section). Textes repris du catalogue (`card_hours_*`, `card_contexts_*`,
  `card_besthours_apply_*`) ; la journée et l'enregistrement ont les leurs. `SettingsNav.tourStep` (`null` : pas de
  visite) ; à chaque étape, `SettingsTourEffect` ouvre `section.settingsCategory`, pose `GuideSpotState.pending` (défilement)
  et `held` (contour **tenu** tant que l'étape dure, au lieu des 3 s d'une carte du catalogue ; `GuideMark` lit
  `shown` ou `held`). `SettingsScreen` affiche `CoachPanel` au-dessus de la barre d'enregistrement ; la dernière étape
  cerne la barre. Passer ou finir remet `tourStep` et `held` à `null` : le formulaire n'est pas touché.
  `DiscoverDialog(onSettingsTour)` : l'entrée suit la ligne du chapitre `SETTINGS` ; `AppShell` ouvre les Réglages et
  pose `tourStep = 0`.
- **Décisions** : *entrées du hub, pas des cartes* (comme la première tâche d'équipe : une carte « faire » dans un
  chapitre ajouterait à sa visite une étape d'une autre nature) ; *la vraie fiche et les vrais Réglages* (écarté : un
  écran de démonstration) ; *contour tenu* pendant la visite (3 s ne suffisent pas à lire l'étape) ; *aperçu de toutes
  les règles*, pas seulement de la règle choisie : c'est la comparaison qui explique la différence ; *pas de
  persistance* : les deux entrées restent à rejouer.

### Décisions et pièges tracés

- **Coach sur la vraie fiche** (écarté : une fiche factice de démonstration, qui
  ne crée rien et diverge de la vraie) : l'utilisateur ressort avec une tâche à lui.
- **Félicitations sur la création réelle** (écarté : au dernier pas du coach) :
  on ne félicite pas d'une tâche qui n'existe pas.
- **Ordre : félicitations d'abord, puis la place** (choix à l'implémentation) : le dialogue vient dès la création et propose
  d'expliquer la place ; il ne dépend d'aucune étape du coach.
- **Pas de persistance** (3.4) : l'invitation disparaissait d'elle-même dès la première tâche. **Rouvert en 3.7.0**
  (issue #94) : l'accompagnement mené à son terme est retenu (`Settings.guide`, nul par défaut), pour qu'une base vidée
  ne réinvite pas quelqu'un qui l'a déjà fait. « Pas maintenant » reste valable pour la session (choix du
  propriétaire).
- **Textes partagés** avec le catalogue : la carte « Créer ta première tâche » du chapitre « Premiers pas » lance ce même
  accompagnement.
- **Aucune animation décorative** : sobriété du design system.
- **Titres des étapes distincts des libellés de la fiche** (« Enregistrer la tâche », « Pour aller plus loin ») : un titre
  de panneau identique à un libellé de la fiche rendrait les tests et les lecteurs d'écran ambigus.
- **Première tâche d'équipe : la même fiche, d'autres étapes** (2026-10-24, issue #97) : `CoachSession` prend sa liste
  d'étapes (`FirstTaskCoach.STEPS` par défaut, `TeamTaskCoach.STEPS`) ; `EditField.ASSIGNEE` (`team = true`) balise
  « Assigné à » (`TeamTaskFields(coach)`), `EditField.personal` est la liste de la fiche personnelle
  (`FirstTaskCoachTest` la compare). Une étape couvre parfois deux champs voisins (titre et description, priorité et
  points) : sept étapes plutôt que dix, pour un manager qui connaît déjà la fiche personnelle.
- **Entrée dans le hub, pas une carte du chapitre Équipe** : les cartes du chapitre Équipe **sont** la visite de
  l'espace Équipe (six étapes, cibles d'équipe seulement, `GuideStepsTest`) ; une carte « créer » y aurait ajouté une
  étape d'une autre nature. L'entrée suit la ligne du chapitre, comme « Créer ma première tâche » ouvre le hub.
- **Création réelle d'abord** : `createTeamTask(titre)` puis `updateTask(id, édition)`, dont la réaffectation écrit
  l'événement `assigned` ; les félicitations lisent la tâche créée (titulaire réel). Écarté : un dépôt dédié à la
  création complète (un chemin d'écriture de plus à journaliser).
- **Démonstration interactive (3.13.0, issue #96) et « coach sur la vraie fiche »** : la démonstration n'est pas une fiche
  factice mais les vrais écrans sur un dépôt jetable en mémoire (`visite-guidee.md` § Visite interactive) ; elle ne
  remplace pas l'accompagnement de la première tâche, qui reste le chemin pour repartir avec une tâche à soi.
- **Pas de persistance** : l'entrée reste dans le hub, à rejouer ; rien n'est retenu dans `Settings.guide` (aucune
  invitation à faire disparaître côté équipe).
- **Empreinte sur les bases vides** : l'invitation s'affiche aussi sur une base sans aucune tâche ; les tests de la vue Jour
  qui y cherchent la carte « Recherche » défilent jusqu'à elle (liste paresseuse).
