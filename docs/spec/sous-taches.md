# Sous-tâches et enchaînement

_Rôle : dire ce que sont les sous-tâches (découper un travail) face aux dépendances « Bloquée par » (enchaîner des
travaux), et fixer les règles qui les relient : fil d'Ariane, priorité, durées et échéances agrégées, conseil de
découpage, section de la fiche. Issue #201 (retour du propriétaire, 2026-11-05). Fichiers couverts :
`core/model/TaskTree.kt` (arbre des tâches, pur), son usage dans `core/day/DayView.kt` et `ui/day/BlockerChoices.kt`.
Les dépendances elles-mêmes sont dans `dependances.md`, la fiche dans `vue-jour.md`, le stockage (`Task.parentId`) dans
`modele-donnees.md`._

État : spécifiée le 2026-11-05 (issue #201, milestone 4.1.0), en PR empilées par lot :

1. **fil d'Ariane complet** (bug) : **livré** ;
2. **priorité imposée** : **livrée** ;
3. **durées et temps passé additionnés, échéance héritée** : **livrés** ;
4. conseil de découpage : à implémenter ;
5. section « Découpage et enchaînement » de la fiche, recherche dans « Bloquée par » : à implémenter.

Décisions du propriétaire du 2026-11-05 : priorité **imposée** ; **somme et échéance** ; conseil **selon l'historique**,
jamais à 1 ou 2 points ; **Perso et Équipe**.

## 1. Besoin métier (cahier des charges)

### Objectif / problème

Kairos a deux façons de relier des tâches, que rien ne distinguait clairement :

- les **sous-tâches** (relation mère-fille) **découpent** un travail : la mère est finie quand ses filles le sont, et
  découper sert à mieux estimer un sujet ;
- les **dépendances** « Bloquée par » (relation d'ordre) **enchaînent** des travaux distincts : une tâche attend qu'une autre
  soit terminée (`dependances.md`, qui fonctionne bien et ne change pas).

Les deux font le planning, mais pour deux raisons différentes ; l'interface et la spec doivent le dire. Le découpage
doit aussi tenir ses promesses : une fille hérite de l'importance de sa mère, la mère montre ce que coûtent ses filles, et
Kairos conseille de découper une tâche que l'historique montre mal estimée.

### Comportement attendu (utilisateur)

- **Fil d'Ariane** : une sous-tâche montre **toute** sa lignée, « A › C › D » pour D, fille de C, elle-même fille de A.
  Une lignée de plus de trois ancêtres garde la racine et les deux derniers (« A › … › X › Y › titre »).
- **Priorité imposée** : une nouvelle sous-tâche prend la priorité de sa mère. Une fille n'est jamais moins prioritaire que
  ses ancêtres : monter la priorité d'une mère monte celle de ses filles moins prioritaires, de proche en proche ; la fiche
  d'une fille ne propose pas de priorité plus basse, et dit pourquoi (« Au moins P1, comme « A » »). Une mère sans
  priorité n'impose rien.
- **Durées et temps passé additionnés** : une mère montre la somme des durées de ses sous-tâches et le temps passé sur
  elles, dans « Tâches mères en cours » et dans sa fiche.
- **Échéance héritée** : une fille sans échéance est planifiée avec celle de sa mère ; une fille ne finit jamais après sa
  mère (une échéance plus tardive est ramenée à celle de la mère, ce que la fiche signale).
- **Conseil de découpage** : dans la fiche, près du guide des points, « Découpe-la en sous-tâches » quand l'historique de
  ce niveau de points montre une estimation peu sûre : temps réels trop dispersés, écart estimé/réel trop grand, ou trop
  peu de tâches terminées pour le savoir. Le seuil est **personnel** : 8 points peut être fiable chez l'un et 5 non chez
  l'autre. Jamais à 1 ou 2 points ; jamais pour une tâche déjà découpée.
- **Une section dédiée** de la fiche, « Découpage et enchaînement », réunit les sous-tâches (existantes et nouvelles) et
  « Bloquée par », avec une phrase qui dit le rôle de chacun. « Bloquée par » a une **recherche** : la liste devient
  impraticable au-delà de quelques tâches.
- Ces règles valent dans l'espace Perso et dans l'espace Équipe (côté Équipe, le conseil lit l'historique de l'équipe).

### Critères de succès

- A ⊃ {B, C}, C ⊃ D : D se lit « A › C › D » dans l'agenda, et C « A › C » dans « Tâches mères en cours » (lot 1).
- Une sous-tâche créée sous une mère P0 est P0 ; passer une mère de P2 à P0 passe ses filles P1 et P2 en P0 (lot 2).
- Une mère dont les filles durent 1 h et 30 min montre 1 h 30 (lot 3).
- Une fille sans échéance d'une mère due le 15 est planifiée comme due le 15 (lot 3).
- Le conseil apparaît à 8 points quand les tâches de 8 points terminées ont pris de 2 h à 9 h, et pas à 5 points quand
  elles ont toutes pris de 1 h à 1 h 30 (lot 4).
- Avec trente tâches à faire, trois lettres dans la recherche de « Bloquée par » ne laissent que les tâches qui les
  contiennent, les cases cochées restant visibles (lot 5).

### Hors périmètre / différé

- Glisser une tâche sous une autre (changer de mère depuis la vue Jour) : la mère se choisit à la création, comme avant.
- Dépendances entre une fille et sa propre mère : inutiles (la mère attend déjà ses filles).
- Un découpage automatique (Kairos ne propose pas de titres de sous-tâches).

## 2. Solution technique

### Arbre des tâches (`core/model/TaskTree.kt`, lot 1)

- `TaskTree.ancestors(id, byId)` : les ancêtres de la tâche, de la racine à la mère directe, en suivant `parentId` tant
  que la mère est dans `byId` (une mère absente ou archivée arrête la lignée). Borné : une boucle de mères (fichier
  corrompu, avant `SyncRepair`) s'arrête au premier ancêtre déjà vu, au plus 64 niveaux.
- `TaskTree.path(id, byId)` : le préfixe affiché avant le titre, ancêtres joints par « › » ; `null` sans mère.
  Au-delà de trois ancêtres : la racine, « … », puis les deux derniers (`MAX_SHOWN = 3`).
- `DayView.parentTitle` porte désormais ce préfixe complet (« A › C » pour D), utilisé tel quel par `TaskRow` (« A › C ›
  D »), par le motif des tâches bloquées (« en attente de : A › C › D ») et par `BlockerChoices` (libellé des cases).

### Priorité imposée (lot 2)

- `TaskTree.floorTask(id, byId)` : l'ancêtre à la priorité la plus forte (le plus proche à égalité) ;
  `priorityFloor` : sa priorité. `inheritedPriority(mère, byId)` : la plus forte de la mère et de ses ancêtres, pour une
  sous-tâche qui naît. `bounded(priorité, plancher)` : jamais moins forte que le plancher ; `null` prend le plancher
  (« hériter ») ; plus forte que la mère reste permis. `raisedDescendants(id, byId)` : les sous-tâches **à faire** à
  monter, de proche en proche (largeur d'abord, `descendants`) ; les faites et archivées gardent la leur.
- Dépôt (`KairosRepository`) : `createTask` et `createTeamTask` (avec mère) prennent `inheritedPriority` ;
  `setPriority`, `updateTask` (et ses « nouvelles sous-tâches », qui prennent la priorité enregistrée dans la même
  fiche) et l'application d'un scénario (`SetPriority`) bornent la valeur puis montent les descendants
  (`raiseSubtasks`, même transaction). Une sous-tâche d'équipe montée est journalisée (`qualified`, `priority`).
- Interface : `PriorityPills(…, floor)` désactive les pastilles moins prioritaires que le plancher, ne retire pas la
  valeur au second clic et écrit « Au moins P1, comme « A » : une sous-tâche n'est jamais moins prioritaire que la tâche
  dont elle fait partie » (`priority_floor`, six langues). Branché dans la fiche (`EditTaskDialog`, Perso et Équipe),
  « À traiter » (`InboxQualify`) et « À qualifier » du backlog d'équipe.
- Hors des gestes de l'utilisateur, rien n'est réécrit : un import, une fusion de synchronisation ou un paquet reçu
  d'un manager peuvent porter une fille moins prioritaire que sa mère ; la prochaine modification de la mère ou de la
  fille la remet en règle. Le moteur d'ordonnancement lit la priorité stockée (fichiers d'or inchangés).
- Tests : `TaskTreeTest`, `SubtaskPriorityTest`, `SubtaskPriorityUiTest`.

### Échéance héritée et bornée (lot 3)

- `TaskTree.deadlineFloorTask(id, byId)` : l'ancêtre à l'échéance la plus proche (le plus proche dans la lignée à date
  égale). `boundedDeadline(échéance, plancher)` : jamais après le plancher ; sans échéance, rien n'est posé.
  `inheritedDeadlines(tâches, byId)` : pour chaque sous-tâche **à faire** sans échéance ou plus tardive, l'ancêtre qui
  donne la sienne. `clampedDescendants(id, byId)` : les sous-tâches à faire plus tardives que leurs ancêtres, ramenées.
- Planning : `DayView.build` remplace l'échéance de ces sous-tâches par celle de l'ancêtre **dans la vue seulement**
  (agenda, sans créneau, plus tard, backlog : datée par sa mère, elle quitte le backlog sans date) ; `inheritedDeadline`
  (identifiant → titre de l'ancêtre) fait écrire « échéance 20 nov. (de « A ») » sur la ligne
  (`deadline_inherited_badge`). La fiche relit la tâche stockée : rien n'est écrit. `LoadPlan.build` (charge de l'équipe)
  fait de même pour les tâches d'équipe. Le moteur reçoit des tâches déjà datées : fichiers d'or inchangés.
- Écriture : `updateTask` et le scénario `SetDeadline` bornent l'échéance d'une sous-tâche à celle de ses ancêtres, et
  `raiseSubtasks` ramène ensuite les sous-tâches à faire plus tardives (après la montée des priorités), journalisé côté
  Équipe (`qualified`, `deadline`). Une sous-tâche sans échéance n'en reçoit jamais : elle l'hérite au planning.
- Fiche : sous le champ Échéance, « Échéance de « A » : 20 nov. Une sous-tâche ne finit jamais plus tard ; sans
  échéance propre, elle prend celle-ci. » (`deadline_floor`).

### Durées et temps passé additionnés (lot 3)

- `TaskTree.rollup(id, tâches, durée, temps passé)` → `Rollup(subtasks, done, plannedMinutes, unestimated,
  spentMinutes)` : les durées des **feuilles** (descendants sans fille : une mère intermédiaire compte par ses filles,
  pas par sa propre estimation), les feuilles sans durée, et le temps passé sur la mère et tous ses descendants.
- `DayView.rollups` : celui de chaque tâche qui a des sous-tâches ; durée d'une feuille : estimée, sinon apprise, sinon
  (faite) son temps réel. Fiche d'équipe : `teamRollup` (estimée ou temps réel, sans durée apprise).
- Affichage (`rollupText`) : « 1 h 30 prévues · 1 sans durée · 45 min passées », chaque partie omise quand elle est nulle ;
  dans « Tâches mères en cours » après l'avancement (« 0/2 sous-tâche(s) · 1 h 30 prévues »), et dans la fiche sous la
  durée (« Ses sous-tâches : … », `rollup_sheet`). La durée propre de la mère reste modifiable (elle ne sert pas au
  planning, la mère n'étant pas une unité de travail).
- Tests : `TaskTreeTest`, `DayViewTest.a_child_without_a_deadline_is_planned_with_its_mothers_and_the_mother_adds_up`,
  `SubtaskPriorityTest.aSubtaskNeverEndsAfterItsMother`, `SubtaskPriorityUiTest.aChildSaysWhereItsDeadlineComesFromAndTheMotherAddsUp`.

### Décisions et pièges tracés

- **Échéance héritée dans la vue, pas en base** (2026-11-05) : écrire l'échéance de la mère dans chaque fille la
  figerait (déplacer la mère ne la déplacerait plus) et créerait des modifications que l'utilisateur n'a pas faites.
  Seule une échéance **plus tardive**, saisie, est ramenée à l'écriture, parce que la promesse « une fille ne finit pas
  après sa mère » doit se lire aussi dans la fiche.
- **Feuilles seulement dans la somme** : compter aussi la propre estimation d'une mère intermédiaire compterait deux fois
  le même travail.

- **Priorité imposée plutôt que signalée** (propriétaire, 2026-11-05) : un avertissement laissait la fille moins
  prioritaire, donc planifiée après des tâches moins importantes que sa mère. Le plancher est le plus fort des
  ancêtres, pas seulement la mère directe : une petite-fille d'une tâche P0 est P0 même si sa mère est restée P1.
- **Pas de réécriture à l'import ou à la fusion** : réécrire des priorités hors d'un geste de l'utilisateur créerait des
  modifications qu'il n'a pas faites (et, synchronisé, des marques sur l'autre appareil).

- **Fil d'Ariane complet** (2026-11-05, bug de l'issue #201) : D, fille de C elle-même fille de A, se lisait « C › D », et
  C, devenue mère, quittait l'agenda pour « Tâches mères en cours » : le lien vers A semblait perdu jusqu'à ce que D soit
  faite. Le préfixe ne portait que la mère directe. Écarté : indenter les sous-tâches dans l'agenda (l'agenda est ordonné
  par l'heure, pas par l'arbre).
- **Troncature à trois ancêtres** : à 360 dp, une lignée de cinq titres repoussait le titre de la tâche sur plusieurs
  lignes ; la racine dit le sujet, les deux derniers où l'on en est.
