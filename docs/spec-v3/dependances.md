# Dépendances entre tâches

_Rôle : bloquer une tâche tant qu'une tâche qu'elle attend n'est pas
terminée, et faire remonter l'urgence d'un bloqueur au niveau de ce qu'il
bloque. Fichiers couverts :
`kmp/core/src/commonMain/.../core/engine/Dependencies.kt` (calcul pur), leur
usage dans `core/day/DayView.kt` et `data/KairosRepository.kt`
(`setBlockers`, `deleteTask`). Le stockage (`TaskDependency`) est dans
`modele-donnees.md`, l'affichage dans `vue-jour.md`._

État : **jalon M2**, portage de `app/tasks_dependencies.py` (spec Kairos 2
`docs/spec/dependances.md`), prouvé par tests différentiels
(`ordonnancement.md` § Tests différentiels).

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
  bloqueurs directs encore ouverts (« en attente de : … », « Mère › Fille »
  pour une sous-tâche).
- Terminer, archiver ou supprimer le dernier bloqueur ouvert la remet
  aussitôt dans le planning, sans action de déblocage.
- Un bloqueur d'une tâche plus urgente remonte dans l'ordre et porte
  « chemin critique » ; sa priorité affichée ne change jamais.
- Le dialogue d'édition propose les autres tâches à faire en cases à cocher
  (« Bloquée par ») ; l'ensemble coché est l'état voulu. Une case qui
  fermerait une boucle est ignorée sans erreur, le reste s'enregistre.

### Critères de succès

- Blocage transitif : A bloquée par B bloquée par C → A et B bloquées, C
  libre ; terminer C libère B, puis terminer B libère A.
- Un bloqueur fait ou archivé ne bloque plus ; une tâche faite ou archivée
  n'est jamais « bloquée ».
- Un cycle ne masque aucune tâche : ses arêtes sont neutralisées.
- Ajouter une arête qui fermerait un cycle (auto-dépendance comprise) est
  détecté avant écriture.
- L'urgence héritée est recalculée à chaque affichage et ne modifie aucune
  donnée.
- Sorties identiques à Kairos 2 sur 300 graphes aléatoires (cycles,
  bloquées, urgence dérivée, cycle potentiel).

### Hors périmètre / différé

- Graphe dessiné, dépendances « au moins N sur M », notification de
  déblocage (comme Kairos 2).

## 2. Solution technique

- `Edge(blocked, blocker)`. `cycleNodes` : algorithme de Kahn (les nœuds
  jamais libérés sont dans un cycle). `acyclicEdges` : arêtes hors cycle,
  sans doublon.
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

- **Une tâche archivée ne bloque pas** (écart volontaire avec le code de
  Kairos 2, conforme à sa spec) : Kairos 2 construisait la table des statuts
  sans les archivées, qui passaient donc pour « à faire » et bloquaient
  encore. Kairos 3 donne le statut réel ; le générateur de fixtures fait de
  même pour que les tests différentiels portent sur le comportement voulu.
- **Les bloqueurs proposés sont les tâches à faire** : un bloqueur déjà fait
  n'a pas de case, donc disparaît au prochain enregistrement du dialogue
  (comme Kairos 2 ; il ne bloquait déjà plus).
- **Cases à cocher, pas de liste à sélection multiple** (décision de Kairos 2
  reprise) ; le libellé de chaque case est cliquable (`LabeledCheckbox`).
