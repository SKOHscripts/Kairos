# Vue Jour et flux GTD

_Rôle : l'écran d'ouverture de Kairos, où l'on capture, qualifie et fait
ses tâches. Fichiers couverts : `kmp/ui/src/commonMain/.../ui/day/`
(`DayScreen.kt`, `DayLists.kt`, `TaskRow.kt`, `Qualify.kt`,
`EditTaskDialog.kt`, `Levels.kt`, `Dates.kt`)._

État : **jalon M1**. Reprend le besoin de `docs/spec/vue-jour-gtd.md`
(Kairos 2) pour la capture, la boîte de réception, la liste, « Fait » et
l'édition. « Maintenant », l'agenda trié par score WSJF, les créneaux, la
timeline, les sections secondaires (sans créneau, bloquées, plus tard),
filtres, recherche, sous-tâches, bloqueurs, récurrence, « Pourquoi à cette
place ? », guide des points et raccourcis arrivent avec le moteur (M2).

## 1. Besoin métier (cahier des charges)

### Objectif / problème

La vue Jour répond, à l'ouverture, à trois questions : qu'est-ce qui n'est pas
encore clarifié, qu'est-ce que je fais, qu'est-ce qui est fait. Elle suit le
cycle GTD : **capturer** sans friction, **traiter** la boîte de réception
(donner une priorité et une taille), **faire**.

### Comportement attendu (utilisateur)

1. **Capture**, toujours visible en tête : un seul champ (le titre) et un
   bouton « Ajouter ». Entrée ajoute ; le champ se vide et garde le curseur
   pour enchaîner les captures au clavier.
2. **À traiter** (boîte de réception) : toute tâche à laquelle il manque la
   priorité **ou** les points. Chacune dit ce qui manque (« priorité et points
   manquants », « priorité manquante », « points manquants ») et se qualifie
   en un clic : pastilles **P0 Critique · P1 Important · P2 Utile** et
   **1 trivial · 2 petit · 3 modéré · 5 conséquent · 8 gros · 13 très gros ·
   21 énorme**. Le sens est écrit sur la pastille, jamais seulement au survol.
   Recliquer une valeur la retire. Section vide : « Rien à traiter : tout est
   déjà clarifié » (la section ne disparaît pas).
3. **À faire** : les tâches qualifiées, dans un **ordre provisoire**
   (priorité, puis échéance, les tâches sans échéance en dernier, puis
   ancienneté). Une phrase dit que le score et le placement arrivent dans la
   préversion suivante.
4. **Fait** : repliée par défaut, avec son compte ; la plus récente en tête.
   Décocher une tâche la remet à faire.
5. **Ligne de tâche** : coche ronde en tête ; titre, puis étiquettes (projet,
   type, « échéance 30 sept. », « 45 min ») qui passent à la ligne, puis la
   première ligne de la description, atténuée ; à droite, la priorité (P0 en
   rouge, P1 et P2 neutres) et les points ; tout à droite, le crayon
   d'édition. Un titre long fait grandir la ligne vers le bas.
6. **Édition** (dialogue) : titre, description (juste sous le titre),
   priorité et points (pastilles avec leur sens complet), échéance
   (AAAA-MM-JJ, refusée si invalide), durée estimée en minutes, projet, type
   (liste des Réglages ; une valeur retirée de la liste reste affichée).
   « Enregistrer » pose tout ; « Supprimer » demande confirmation et
   supprime définitivement.
7. **Version web** : un bandeau en tête rappelle que les données ne sont que
   dans le navigateur tant qu'elles ne sont pas liées à un fichier
   (`export-import.md` § Version web).

### Critères de succès

- Une tâche capturée apparaît aussitôt dans « À traiter », jamais dans « À
  faire » tant qu'il lui manque la priorité ou les points.
- Poser la priorité ou les points prend **un** clic, sans ouvrir l'édition.
- Une tâche dans une seule liste à la fois ; une tâche archivée (base 2.x)
  n'apparaît nulle part (`DayListsTest`).
- Aucun débordement horizontal à 420 px de large (vérifié sur le rendu de
  l'auto-test).

## 2. Solution technique

- `DayScreen(services)` : lit `repository.snapshot` (`collectAsState`),
  dérive `DayLists.of(tasks)` ; `LazyColumn` centrée (840 dp au plus) :
  bandeau de stockage (web), capture, titre et aide ou état vide de « À
  traiter », lignes avec `InboxQualify`, titre de « À faire » et phrase
  d'ordre provisoire ou « Rien à faire pour l'instant », lignes, bouton
  repliable « Fait (n) » et ses lignes. Chaque action appelle le dépôt dans
  une coroutine de l'écran ; l'état republié redessine l'écran.
- `DayLists.of` : `inbox` = `todo` et `needsProcessing`, par identifiant ;
  `todo` = `todo` qualifiées, triées par (priorité, sans échéance, échéance,
  identifiant) ; `done` = `done`, par `updatedAt` décroissant.
- `Capture` : carte « filled » (`surfaceContainerLow`, élévation 0),
  `OutlinedTextField` sur une ligne ; Entrée (touche physique, intercepté par
  `onPreviewKeyEvent`, ou action « OK » du clavier virtuel) et le bouton
  appellent `submit` : ajoute si non vide, vide le champ, redonne le focus.
- `PriorityPills` / `PointsPills` (`Qualify.kt`) : `FilterChip` en
  `FlowRow`, hauteur minimale 48 dp ; libellés `Levels` (définitions de
  `app/task_guide.py`) ; `showMeaning` ajoute le sens complet des priorités
  (dialogue d'édition).
- `TaskRow` : `Surface` `surfaceContainerLow` en forme `medium` (12 dp) ;
  `IconButton` coche (`RadioUnchecked`, ou `CheckCircleFilled` teinte « ok »
  si faite) ; colonne corps en `weight(1f)` ; rangée priorité
  (`PriorityBadge` : P0 en `errorContainer`) et points (`Badge` neutre) ;
  `IconButton` crayon. Titre barré et atténué si fait. Emplacement `extra`
  sous la ligne (qualification de la boîte de réception).
- `EditTaskDialog` : `Dialog` + `Surface` `extraLarge` (28 dp),
  `surfaceContainerHigh`, ombre (élément flottant), 640 dp au plus,
  défilant. Échéance analysée par `LocalDate.parse` ; invalide → erreur
  sous le champ et « Enregistrer » désactivé. Durée : chiffres seulement,
  4 au plus. Type : `ExposedDropdownMenuBox` en lecture seule (« Non classé »
  = vide). Suppression : `AlertDialog` de confirmation.
- `Dates.short(date, langue)` : « 30 sept. » en français (langue par défaut),
  « Sep 30 » en anglais. Langue lue par `Locale.current`.

### Décisions et pièges tracés

- **Ordre provisoire affiché comme tel** : plutôt que d'imiter le score
  WSJF sans le moteur, M1 trie simplement et le dit à l'écran. La phrase
  disparaît en M2.
- **Priorité et points restent à droite même en largeur étroite** (Kairos 2
  les passait sous le corps sous 720 px) : à reprendre avec la ligne complète
  en M2.
- **Pas de raccourci `N` ni `/`** en M1 : ils arrivent avec la recherche (M2).
