# Sous-tâches et enchaînement

_Rôle : dire ce que sont les sous-tâches (découper un travail) face aux dépendances « Bloquée par » (enchaîner des
travaux), et fixer les règles qui les relient : fil d'Ariane, priorité, durées et échéances agrégées, conseil de
découpage, section de la fiche, file de sous-tâches enchaînées. Issues #201 et #209 (retours du propriétaire, 2026-11-05). Fichiers couverts :
`core/model/TaskTree.kt` (arbre des tâches, pur), son usage dans `core/day/DayView.kt` et `ui/day/BlockerChoices.kt`.
Les dépendances elles-mêmes sont dans `dependances.md`, la fiche dans `vue-jour.md`, le stockage (`Task.parentId`) dans
`modele-donnees.md`._

État : spécifiée le 2026-11-05 (issue #201, milestone 4.1.0), en PR empilées par lot :

1. **fil d'Ariane complet** (bug) : **livré** ;
2. **priorité imposée** : **livrée** ;
3. **durées et temps passé additionnés, échéance héritée** : **livrés** ;
4. **conseil de découpage** : **livré** ;
5. **section « Découpage et enchaînement » de la fiche, recherche dans « Bloquée par »** : **livrées** (avec la carte
   `split_advice` et le README) ;
6. **file de sous-tâches enchaînées** (issue #209, ajoutée le 2026-11-05) : **livrée**, en 4.1.0-beta.2 par
   **exception au gel** des fonctionnalités (`distribution.md` § Quel numéro).

Décisions du propriétaire du 2026-11-05 : priorité **imposée** ; **somme et échéance** ; conseil **selon l'historique**,
jamais à 1 ou 2 points ; **Perso et Équipe**. Pour la file (#209) : **dans l'ordre des lignes** ; l'interrupteur
**retient le dernier choix** (coupé la première fois, propre à l'appareil) ; elle **enrichit la carte « Sous-tâches »**,
pas de nouvelle carte ; **exception au gel** de la 4.1.0.

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
- **File de sous-tâches** (issue #209) : juste avant « Nouvelles sous-tâches », un interrupteur « Enchaîner : chacune
  attend la précédente ». Actif, les nouvelles sous-tâches forment une file **dans l'ordre des lignes** : avec B, C, D,
  C est bloquée par B et D par C (des dépendances « Bloquée par » ordinaires, qu'on retire dans la fiche de chacune).
  Coupé, seules les sous-tâches naissent. L'interrupteur **retient le dernier choix** sur l'appareil (coupé la première
  fois) ; seules les nouvelles lignes s'enchaînent, jamais les sous-tâches existantes.
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
- Interrupteur actif, trois lignes B, C, D : C est « Bloquée par » B, D par C, et B n'attend rien ; la fiche suivante
  propose l'interrupteur actif. Coupé : trois sous-tâches sans dépendance (lot 6).

### Hors périmètre / différé

- Glisser une tâche sous une autre (changer de mère depuis la vue Jour) : la mère se choisit à la création, comme avant.
- Dépendances entre une fille et sa propre mère : inutiles (la mère attend déjà ses filles).
- Un découpage automatique (Kairos ne propose pas de titres de sous-tâches).
- Enchaîner des sous-tâches existantes, ou la première nouvelle derrière la dernière existante : l'interrupteur ne
  règle que les lignes saisies ; une file existante se prolonge par « Bloquée par ».

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

### Conseil de découpage (lot 4, `core/stats/SplitAdvice.kt`)

- `SplitAdvice.history(tâches, temps passé, espace)` : par niveau de points, les tâches **terminées** de l'espace avec du
  temps réel ; ni les mères (leur temps est celui de leurs filles), ni, côté Perso, les tâches reçues d'un manager.
- `SplitAdvice.advise(points, déjà découpée, historique)` → `Advice(reason, points, samples, bias?, shortest?, longest?)`,
  `null` sous `MIN_POINTS` = 3, pour une tâche qui a déjà des sous-tâches, ou pour un niveau bien estimé. Motifs, du plus
  parlant au moins parlant :
  1. `BIAS` : au moins `MIN_SAMPLES` = 5 tâches estimées, médiane réel ÷ estimé hors de ×0,67–×1,5 ;
  2. `SPREAD` : 80ᵉ centile ÷ 20ᵉ centile du temps réel (rang le plus proche, valeurs observées) au-delà de ×2,5 ;
  3. `NOT_ENOUGH_HISTORY` : moins de 5 tâches terminées à ce niveau.
- Fiche (`EditTaskDialog`, paramètre `splitHistory`) : sous les pastilles de points, `SplitAdviceLine` (icône `Layers`,
  texte neutre) suit les points choisis en direct ; jamais pour une tâche faite ou déjà découpée. Textes :
  `split_advice_bias` (« Découpe-la en sous-tâches : à 8 points, les tâches terminées ont pris ×1.80 le temps prévu
  (médiane de 12). »), `split_advice_spread` (« … ont pris de 2 h à 7 h »), `split_advice_unknown` (« … seulement 2
  tâche(s) terminée(s) à 13 points, trop peu pour savoir si ce niveau est bien estimé » ; sans aucune :
  `split_advice_none`, « aucune tâche terminée à 13 points pour l'instant, rien ne dit si ce niveau est bien estimé »),
  six langues ; le rapport a
  deux décimales et un point décimal, comme le biais des statistiques.
- Historique : celui de l'espace Perso dans la vue Jour (`DayScreen`), celui de l'équipe dans la fiche d'équipe
  (`TeamTaskDialog`), comme les durées posées en qualifiant.
- Tests : `SplitAdviceTest` (seuil personnel du critère de succès, biais, historique court, jamais à 1 ou 2 points,
  espace), `SubtasksUiTest.theSheetAdvisesToSplitALevelWithoutEnoughHistory`.

### Section « Découpage et enchaînement » (lot 5, `EditTaskDialog`)

- Après l'échéance et la durée, avant « Options avancées » : un bouton repliable (`split_section_title`), ouvert d'emblée
  si la tâche a des sous-tâches ou des bloqueurs. Dedans : la phrase de rôle (`split_section_hint` : « Les sous-tâches
  découpent ce travail : il est fini quand elles le sont. « Bloquée par » l'enchaîne à d'autres tâches : il attend
  qu'elles soient faites. »), les sous-tâches existantes (`SubtaskEditor`), « Nouvelles sous-tâches », puis « Bloquée
  par ». Ces deux champs ont quitté les options avancées (`EditField.SUBTASKS` et `BLOCKERS` sans `advanced`, rangés
  après `DEADLINE`) ; une étape de coach qui les explique ouvre la section.
- Recherche : au-delà de `BlockerChoices.SEARCH_FROM` = 6 cases, un champ « Chercher une tâche » (`blockers_search`, icône
  `Search`) ; `BlockerChoices.filter` garde les cases dont le libellé contient chaque mot, sans casse ni accents
  (`SearchFold`, comme la liste des fonctionnalités), et **toujours les cochées** ; aucune : « Aucune tâche ne correspond
  à « … ». » (`blockers_search_none`). L'ordre des cases ne change pas pendant la saisie.
- Le conseil de découpage porte « Découper en sous-tâches » (`split_advice_action`) : il ouvre la section et place le
  curseur dans « Nouvelles sous-tâches » (`FocusRequester`, après une image).
- Fonctionnalité : carte `split_advice` (chapitre « Planifier », `since = "4.1.0"`), puce et ligne du tableau des deux
  README ; la carte `subtasks` et les puces « Subtasks » / « Blockers » disent les nouvelles règles.
- Tests : `BlockerChoicesTest.theSearchIgnoresCaseAndAccentsAndKeepsTheCheckedOnes`,
  `SubtasksUiTest.theSectionGathersSubtasksAndBlockersAndBlockersCanBeSearched`,
  `SubtasksUiTest.theAdviceOpensTheSectionOnTheNewSubtasksField` ; `DayScreenUiTest` et `TeamMyWorkUiTest` ouvrent
  la section pour atteindre « Bloquée par ».

### File de sous-tâches enchaînées (lot 6, issue #209)

- Fiche (`EditTaskDialog`) : `SwitchRow` « Enchaîner » (`subtasks_chain`) juste avant « Nouvelles sous-tâches », dans la
  section ; sa valeur initiale est le paramètre `chainSubtasks`, que les quatre appelants (`DayScreen` création et
  édition, `TeamTaskDialog`, `TeamTaskCoach`) lisent dans `Settings.subtaskChain == true`. Il part dans
  `TaskEdit.chainNewSubtasks`.
- Écriture (`KairosRepository.updateTask`, la création passe aussi par lui) : les nouvelles lignes sont insérées dans
  l'ordre ; interrupteur actif, chaque sous-tâche insérée reçoit `insertDependency(elle, la précédente insérée)`. Pas de
  contrôle de cycle : les deux tâches viennent de naître, n'ont aucune autre dépendance et partagent l'espace de la mère.
- Mémoire : `Settings.subtaskChain: Boolean?` (`null` = coupé, la base qui n'y a jamais touché ne change pas), écrite
  **dans la même transaction**, seulement quand au moins une sous-tâche est née (le choix a servi) et qu'il diffère.
  Propre à l'appareil : dans `SyncScope.LOCAL_SETTINGS`, jamais synchronisé.
- Fonctionnalité : enrichit la carte `subtasks` (`card_subtasks_body`, six langues) et les puces « Subtasks » /
  « Sous-tâches » des deux README ; pas de nouvelle carte ni de ligne de tableau.
- Tests : `SubtaskChainTest` (données : file dans l'ordre, rien sans l'interrupteur, espace Équipe, mémoire),
  `SubtasksUiTest.theChainSwitchQueuesNewSubtasksAndIsRemembered`.

### Décisions et pièges tracés

- **Exception au gel de la 4.1.0** (propriétaire, 2026-11-05) : la 4.1.0-beta.1 était sortie, la règle voulait la
  file en 4.2.0 ; elle part en 4.1.0-beta.2, la 4.1.0 n'étant pas encore finale et le changement restant local à la
  fiche. Une exception, pas une nouvelle règle (`distribution.md` § Quel numéro).
- **Des dépendances ordinaires, pas un mode « file »** : la file est faite de « Bloquée par » que l'on voit et retire
  comme les autres ; rien de nouveau à stocker, à exporter ni à synchroniser. Écarté : un ordre propre aux filles d'une
  mère, que l'ordonnancement aurait dû apprendre.
- **Mémoire à l'enregistrement, pas au basculement** : basculer puis annuler la fiche ne change pas le choix suivant ;
  seul un choix qui a créé des sous-tâches compte.
- **Propre à l'appareil** : une habitude de saisie, comme la langue ou le guide, pas un réglage du planning.

- **Une section, deux relations** (issue #201 : « elles se ressemblent, elles servent deux buts ») : réunies parce
  qu'elles font toutes deux le planning, séparées par leurs sous-titres et une phrase qui dit le rôle de chacune.
  Repliée par défaut pour garder la fiche courte, ouverte dès qu'elle porte quelque chose.
- **Les cochées restent visibles pendant la recherche** : sinon chercher une autre tâche ferait croire qu'on a perdu
  un bloqueur, et l'on ne pourrait plus le décocher.

- **Un seuil personnel, pas un nombre de points** (propriétaire, 2026-11-05 : « un utilisateur peut très bien estimer
  une tâche à 8 mais pas à 5 ») : chaque niveau est jugé sur sa propre dispersion et son propre biais. Écarté : conseiller
  à partir de 8 points pour tout le monde. Le 80ᵉ/20ᵉ centile plutôt que le maximum ÷ minimum : une seule tâche
  aberrante ne déclenche pas le conseil.
- **Faute d'historique, on conseille** (demande de l'issue) **sauf à 1 et 2 points** : une base neuve verrait sinon le
  conseil sur toutes ses petites tâches.

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
