# Dépendances entre tâches

_Rôle : bloquer une tâche tant qu'une tâche qu'elle attend n'est pas
terminée, et faire remonter l'urgence d'un bloqueur au niveau de ce qu'il
bloque. Fichiers couverts :
`kmp/core/src/commonMain/.../core/engine/Dependencies.kt` (calcul pur), leur
usage dans `core/day/DayView.kt` et `data/KairosRepository.kt`
(`setBlockers`, `deleteTask`). Le stockage (`TaskDependency`) est dans
`modele-donnees.md`, l'affichage dans `vue-jour.md`._

État : **jalon M2**, prouvé par un test sur fichiers d'or figés
(`DifferentialTest.dependencies_match_golden`, `ordonnancement.md`).

## 1. Besoin métier (cahier des charges)

### Objectif / problème

Une tâche peut attendre qu'une autre soit terminée (« Déployer » attend
« Obtenir la validation du client »). Sans mécanisme dédié, elle serait
proposée dans la journée alors qu'elle n'est pas actionnable. Et un
bloqueur de faible priorité propre peut conditionner une tâche très
urgente : il doit en hériter l'urgence pour passer plus tôt.

### Comportement attendu (utilisateur)

- Une tâche à faire dont un bloqueur (direct ou transitif) est encore à
  faire sort du planning et apparaît dans « Bloquées », avec les titres des
  bloqueurs directs encore ouverts (« en attente de : … », « Mère › Fille », lignée complète « A › C › D »
  pour une sous-tâche).
- Terminer, archiver ou supprimer le dernier bloqueur ouvert la remet
  aussitôt dans le planning, sans action de déblocage.
- Un bloqueur d'une tâche plus urgente remonte dans l'ordre et porte
  « chemin critique » ; sa priorité affichée ne change jamais.
- Le dialogue d'édition propose les autres tâches à faire par une recherche
  (« Bloquée par », 4.1.0, `fiche-tache.md`) ; l'ensemble des puces choisies est l'état voulu. Une tâche qui
  attend déjà celle-ci (directement ou non) est proposée grisée, avec « (attend
  déjà cette tâche) » : la choisir fermerait une boucle. Le dépôt ignore de
  toute façon une telle arête sans erreur, le reste s'enregistre. Chaque choix
  porte la lignée puis le titre pour une sous-tâche (« A › C › D », `sous-taches.md`), et deux libellés identiques
  (une tâche et sa prochaine occurrence) se distinguent par leur date.

### Critères de succès

- Blocage transitif : A bloquée par B bloquée par C → A et B bloquées, C
  libre ; terminer C libère B, puis terminer B libère A.
- Un bloqueur fait ou archivé ne bloque plus ; une tâche faite ou archivée
  n'est jamais « bloquée ».
- Un cycle ne masque aucune tâche : ses arêtes internes sont neutralisées.
- Une tâche **en aval** d'un cycle (C attend A, A et B s'attendent l'un
  l'autre) reste bloquée tant que A est à faire (2026-11-04, voir § Décisions).
- Les sous-tâches d'une tâche bloquée sont bloquées aussi (de proche en proche) :
  jamais planifiées, listées dans « Bloquées » avec « en attente de : Mère »
  (2026-10-12, demande explicite : elles étaient planifiées malgré leur mère
  en attente). Vue Jour seulement : `Dependencies.inheritedBlock`, appelée par
  `DayView.build` ; l'espace Équipe (`TeamStates`) garde les arêtes telles quelles.
- Ajouter une arête qui fermerait un cycle (auto-dépendance comprise) est
  détecté avant écriture.
- L'urgence héritée est recalculée à chaque affichage et ne modifie aucune
  donnée.
- Sorties identiques aux fichiers d'or (`dependencies.json`) sur 300 graphes
  aléatoires (cycles, bloquées, urgence dérivée, cycle potentiel).

### Hors périmètre / différé

- Graphe dessiné, dépendances « au moins N sur M », notification de
  déblocage.

## 2. Solution technique

- `Edge(blocked, blocker)`. Les cycles sont les **composantes fortement
  connexes** (algorithme de Tarjan, itératif : pas de récursion, une chaîne de
  20 000 tâches ne déborde pas la pile, wasm compris). `cycleNodes` : nœuds
  d'une composante de plus d'un nœud, ou portant une auto-arête.
  `acyclicEdges` : arêtes sans doublon ni auto-boucle dont les deux bouts sont
  dans des composantes **différentes** ; l'arête qui relie une tâche en aval à
  un cycle, ou deux cycles entre eux, reste. Le graphe qui reste est sans
  cycle. Tests : `DependencyCyclesTest`.
- `blockedTaskIds(edges, isTodo)` : tâches à faire ayant un bloqueur direct à
  faire sur une arête hors cycle (la transitivité vient d'elle-même : un
  bloqueur bloqué reste à faire). `openBlockers` : bloqueurs directs à faire
  de chaque tâche, pour le motif affiché.
- `wouldCreateCycle(existing, blocked, blocker)` : auto-arête, ou `blocked`
  atteignable depuis les bloqueurs de `blocker`.
- `derivedUrgency(edges, own)` : point fixe sur les arêtes hors cycle, un
  bloqueur prend la clé la plus petite (la plus urgente) de ce qu'il bloque.
  Générique (`Comparable`) : la vue Jour lui passe les `SortKey` des tâches
  à faire ; « chemin critique » = clé effective plus petite que la clé
  propre.
- `DayView.build` passe le **statut réel** de chaque tâche (`isTodo`, statut
  inconnu = à faire) et rend `blocked` (tâche et titres des bloqueurs, par
  titre), `raised`, `blockersOf` (bloqueurs directs stockés, pour cocher le
  dialogue).
- Dépôt : `setBlockers(tâche, cible)` (dans `updateTask`) retire les
  arêtes absentes de la cible, ajoute les nouvelles dans l'ordre des
  identifiants en ignorant une tâche inconnue ou un cycle ; `deleteTask`
  supprime les arêtes où la tâche figure des deux côtés.

### Décisions et pièges tracés

- **L'aval d'un cycle reste bloqué** (2026-11-04, décision rouverte après la
  QA d'avant 4.0) : l'algorithme de Kahn rangeait dans les « nœuds du cycle »
  toute tâche jamais libérée, donc aussi celles **en aval** ; leurs arêtes
  étaient neutralisées et la tâche passait au planning avant son bloqueur.
  Comportement hérité de l'ancienne version et figé jusque-là par les fichiers
  d'or ; ceux-ci ont été mis à jour (86 cas de `dependencies.json` sur 300,
  10 de `scheduling.json` sur 480, tous avec une tâche en aval d'un cycle ;
  sorties recalculées par le moteur corrigé, les 470 autres cas vérifiés
  identiques). Aucun cas n'a débloqué de tâche : l'ensemble des bloquées ne
  fait que grandir. Le plan de charge de l'équipe (`LoadPlan`) suit
  (`LoadPlanTest.dependencyCyclesTerminate`).

- **Une tâche archivée ne bloque pas** : `DayView.build` donne le statut réel
  de chaque tâche, archivées comprises ; une archivée n'est donc jamais « à
  faire » et ne bloque pas. Comportement figé par les fichiers d'or.
- **Les bloqueurs proposés sont les tâches à faire** : un bloqueur déjà fait
  n'a pas de case, donc disparaît au prochain enregistrement du dialogue
  (il ne bloquait déjà plus).
- **Cases à cocher, pas de liste à sélection multiple** ; le libellé de chaque case est cliquable (`LabeledCheckbox`).
- **Boucle annoncée avant l'enregistrement** (2026-10-10, test exploratoire) :
  la case était proposée puis silencieusement oubliée, sans retour.
  `BlockerChoices.of(tâche, candidats, dépendances, titres, langue)` (`ui/day`)
  rend libellé et `cyclic` (`wouldCreateCycle`), et écarte les tâches d'un
  autre espace (une tâche d'équipe de « moi » visible dans l'espace Perso
  était proposée à une tâche perso, puis refusée sans un mot par le dépôt) ; une case `cyclic` non cochée
  est désactivée (une case déjà cochée reste décochable). Doublons de libellé :
  « (15 oct.) » d'après l'échéance, sinon la date programmée, sinon
  « (#id) ».
