# Dépendances entre tâches

_Rôle : bloquer une tâche tant qu'au moins une autre tâche qu'elle attend n'est pas
terminée, et faire remonter l'urgence d'un bloqueur jusqu'au niveau de ce qu'il
bloque. Fichier couvert intégralement : `app/tasks_dependencies.py` (module pur,
aucun accès DB/réseau — détection de cycles façon Kahn, blocage transitif,
urgence dérivée/chemin critique). Le stockage (`TaskDependency`) est décrit par
`modele-donnees.md` ; la consommation du résultat par le tri/placement du jour
(clé de tri, badge « chemin critique », bordure de bucket) est décrite par
`ordonnancement.md` § 2.3 ; l'UI d'édition (panneau « Bloquée par », capture,
boîte de réception GTD) est décrite par `vue-jour-gtd.md` — ce document ne
couvre que le **moteur de calcul** et son point d'entrée dans `app/main.py`._

## 1. Besoin métier (cahier des charges)

### Objectif / problème

Une tâche peut réellement attendre qu'une autre soit terminée avant de pouvoir
être commencée (ex. « Déployer en production » attend « Obtenir la validation
du client »). Sans mécanisme dédié, cette tâche apparaîtrait dans l'agenda du
jour comme n'importe quelle autre — proposée à une heure, comptée dans la
charge du jour — alors qu'elle n'est concrètement pas actionnable tant que son
bloqueur traîne. Symétriquement, un bloqueur anodin en apparence (faible
priorité propre) peut en réalité conditionner une tâche très urgente : il
mérite d'hériter de cette urgence pour ne pas être oublié plus bas dans le
tri.

### Comportement attendu (utilisateur)

- Une tâche `todo` avec au moins un bloqueur encore `todo` (direct ou
  transitif) sort de l'agenda planifié/sans créneau/plus tard et apparaît dans
  une section dédiée « Bloquées », avec le ou les titres des bloqueurs
  encore ouverts affichés (« en attente de : … »).
- Terminer (ou archiver) le dernier bloqueur ouvert d'une tâche la fait
  réapparaître automatiquement dans le tri normal au chargement suivant —
  aucune action manuelle de « déblocage ».
- Un bloqueur d'une tâche urgente est signalé visuellement (badge « chemin
  critique ») et remonte dans l'ordre de traitement, sans que sa propre
  priorité affichée ne change jamais : l'urgence affichée reste honnête
  (on ne réécrit pas la priorité posée par l'utilisateur), seul l'ordre de
  passage en tient compte.
- Poser un bloqueur qui fermerait une boucle (A attend B qui attend A) est
  refusé silencieusement — l'enregistrement du reste du formulaire réussit
  quand même, aucune erreur bloquante affichée pour ce seul champ.
- Le panneau d'édition d'une tâche propose l'ensemble de ses bloqueurs sous
  forme de cases à cocher (l'état complet coché = l'état cible) : cocher/
  décocher plusieurs bloqueurs et sauvegarder en un seul clic suffit.

### Critères de succès

Repris et fusionnés des phases historiques (SPEC_KAIROS.md phases 3, 6, 13) :

- Blocage transitif : A bloquée par B bloquée par C, tous `todo` → A et B
  bloquées, C libre ; terminer C libère B (reste bloquée-par-personne, donc
  planifiable), terminer B libère ensuite A — jamais de déblocage manuel
  requis.
- Un bloqueur `done` ou `archived` ne bloque plus (levée automatique).
- Une tâche déjà `done`/`archived` n'est jamais elle-même rapportée comme
  « bloquée », quel que soit l'état de ses bloqueurs.
- Un cycle de dépendances (2 nœuds ou plus) ne provoque ni boucle infinie ni
  blocage mutuel masquant les deux tâches : les arêtes du cycle sont
  neutralisées, aucune des deux n'apparaît en « Bloquées » à cause de l'autre.
- Poser une nouvelle dépendance qui fermerait un cycle (y compris transitif,
  y compris l'auto-dépendance) est détecté et refusé avant écriture.
- Un bloqueur hérite de l'urgence la plus forte (la plus « pressée ») de tout
  ce qu'il bloque, transitivement, y compris à travers une chaîne de
  plusieurs bloqueurs.
- Cette urgence dérivée est recalculée à chaque rendu de page, ne modifie
  jamais `Task.priority` ni aucune autre colonne : non destructive, réversible
  d'elle-même (aucune donnée à « annuler » si les dépendances changent).
- Éditer une tâche : cocher/décocher plusieurs bloqueurs et sauvegarder en un
  seul « Enregistrer » (avec le reste du formulaire — infos, sous-tâches en
  lot) ; cocher un bloqueur qui créerait un cycle est ignoré silencieusement,
  le reste de l'enregistrement réussit quand même.

### Hors périmètre / différé

- **Représentation graphique du graphe de dépendances** (diagramme, vue
  dédiée) : jamais demandée, l'app reste liste-first.
- **Dépendances non binaires** (ex. « au moins 2 des 3 bloqueurs ») : le
  modèle est strictement « tous les bloqueurs directs encore ouverts
  bloquent » — pas de logique OU/seuil.
- **Notification au déblocage** (« B vient d'être débloquée ») : pas de
  mécanisme proactif, la tâche réapparaît simplement au tri au chargement
  suivant (cohérent avec l'outil 100 % pull, voir `temps-reel-chrono.md`
  § alertes pour le seul mécanisme proactif de l'app, sans rapport avec les
  dépendances).
- **UI de sélection des bloqueurs** (cases à cocher vs `<select multiple>`,
  panneau d'édition, boîte de réception GTD) : `vue-jour-gtd.md`. Ce document
  ne couvre que le calcul, pas le rendu du formulaire (voir néanmoins
  § Décisions et pièges tracés ci-dessous pour l'historique de ce choix, qui
  touche directement la route consommatrice de ce moteur).
- **Consommation du résultat dans le tri/placement** (clé de tri, bordure de
  bucket, badge affiché) : `ordonnancement.md`.

## 2. Solution technique

### Vue d'ensemble

`app/tasks_dependencies.py` est un module **pur** : aucune session SQLAlchemy,
aucune I/O — il opère sur des listes d'arêtes `(bloquée, bloquante)` et des
dictionnaires `{id: valeur}` déjà chargés en mémoire par l'appelant
(`app/main.py`, route de la vue jour/semaine). Adapté d'un moteur GitLab
préexistant (`app/dependency_rules.py`, mentionné en tête de fichier comme
source d'inspiration pour l'algorithme de Kahn), il expose quatre fonctions
publiques et deux fonctions privées de support :

| Fonction | Rôle |
|---|---|
| `detect_cycle_nodes(edges)` | nœuds appartenant à un cycle (Kahn) |
| `_acyclic_edges(edges)` | arêtes hors cycle, dédupliquées, sans auto-boucle (support interne) |
| `blocked_task_ids(edges, status_by_id)` | ids des tâches `todo` bloquées |
| `blocking_reason(edges, status_by_id, title_by_id)` | titres des bloqueurs directs encore ouverts, par tâche bloquée |
| `would_create_cycle(existing_edges, new_blocked, new_blocker)` | vrai si ajouter l'arête fermerait une boucle |
| `_reachable_blockers(edges, start)` | bloqueurs transitifs d'un nœud (support interne de `would_create_cycle`) |
| `derived_urgency(edges, own_urgency)` | urgence effective par id (chemin critique) |

**Convention d'arête** (partagée avec `dependency_rules`, rappelée en tête de
fichier) : `(bloquée, bloquante)` — c'est-à-dire `(task_id, blocker_id)` côté
`TaskDependency`. Toutes les fonctions publiques respectent cette convention
dans l'ordre de leurs paramètres/tuples.

### Détail par composant

#### Détection de cycles (`detect_cycle_nodes`, lignes 24-43)

Algorithme de **Kahn** (tri topologique par élimination des nœuds à degré
entrant nul) : on construit le graphe orienté bloquant → bloquée (`adj`), on
retire itérativement les nœuds sans dépendance restante, et tout nœud dont le
degré entrant ne retombe jamais à zéro appartient à un cycle. Utilisé comme
brique de base par `_acyclic_edges`.

#### Filtrage acyclique (`_acyclic_edges`, lignes 46-61)

Prépare les arêtes utilisées par `blocked_task_ids`, `blocking_reason` et
`derived_urgency` : retire les auto-boucles (`blocked == blocker`), retire
toute arête dont **les deux extrémités** sont dans un même cycle détecté (pas
seulement l'une des deux — une arête reliant un nœud cyclique à un nœud hors
cycle reste valide), déduplique. Chaque fonction publique de lecture appelle
`_acyclic_edges` en tête pour ne jamais raisonner sur un graphe cyclique.

#### Blocage transitif (`blocked_task_ids`, lignes 64-81)

```python
def blocked_task_ids(edges, status_by_id) -> set[int]:
    blocked = set()
    for task, blocker in _acyclic_edges(edges):
        if status_by_id.get(task, "todo") != "todo":
            continue  # déjà faite/archivée : jamais rapportée bloquée
        if status_by_id.get(blocker, "todo") == "todo":
            blocked.add(task)
    return blocked
```

Ne calcule **aucun** point fixe explicite : la transitivité est une
conséquence naturelle du fait qu'un bloqueur intermédiaire encore `todo` reste
lui-même dans l'ensemble `blocked` calculé sur la même passe — le commentaire
de la fonction retrace le raisonnement (A←B←C, C non fait → B bloquée, A
bloquée par B qui reste `todo` qu'elle soit elle-même bloquée ou non).

#### Motif de blocage (`blocking_reason`, lignes 84-95)

Pour chaque tâche bloquée, liste les **titres des bloqueurs directs** encore
`todo` (pas les transitifs — l'utilisateur voit la cause immédiate, pas toute
la chaîne). Retourne un `dict[int, list[str]]`, filtré pour ne garder que les
entrées non vides.

#### Détection préventive de cycle (`would_create_cycle`, lignes 98-124)

```python
def would_create_cycle(existing_edges, new_blocked, new_blocker) -> bool:
    if new_blocked == new_blocker:
        return True
    return new_blocked in _reachable_blockers(existing_edges, new_blocker)
```

Un cycle apparaîtrait si `new_blocker` dépend déjà, directement ou
transitivement, de `new_blocked` — ajouter l'arête inverse boucherait la
boucle. `_reachable_blockers` fait une recherche en profondeur (pile
explicite, pas de récursion) sur l'ensemble des bloqueurs (directs et
transitifs) d'un nœud de départ. Couvre aussi l'auto-arête (`new_blocked ==
new_blocker`) comme cas trivial de cycle.

#### Urgence dérivée / chemin critique (`derived_urgency`, lignes 127-150)

```python
def derived_urgency(edges, own_urgency) -> dict[int, tuple]:
    active_edges = _acyclic_edges(edges)
    effective = dict(own_urgency)
    for _ in range(len(own_urgency) + 1):
        changed = False
        for blocked, blocker in active_edges:
            if blocked not in effective or blocker not in effective:
                continue
            if effective[blocked] < effective[blocker]:
                effective[blocker] = effective[blocked]
                changed = True
        if not changed:
            break
    return effective
```

**Point fixe monotone** : à chaque passe, un bloqueur adopte la clé
d'urgence de ce qu'il bloque si elle est strictement plus forte (plus petite,
convention partagée avec `tasks_scheduling.urgency_key` — « plus petite =
plus urgente »). Le nombre de passes est borné à `len(own_urgency) + 1` : sur
un graphe acyclique fini, une propagation ne peut jamais nécessiter plus de
passes que de nœuds (chaque passe fait progresser d'au moins un maillon la
chaîne la plus longue) ; la borne empêche toute boucle infinie même en cas
d'erreur de raisonnement future sur les arêtes fournies. `own_urgency` :
dictionnaire `{id: clé de tri}` fourni par l'appelant — ce module ne connaît
ni `Task`, ni `Settings`, ni la formule WSJF, il ne fait que propager des
tuples comparables. Ne mute **aucun** objet — retourne un nouveau dict, clé
identique à `own_urgency` pour toute tâche que rien n'élève.

### Point d'entrée dans `app/main.py`

La route de rendu (`_render_kairos`, autour de la ligne 390-490) orchestre le
module dans cet ordre :

1. `dep_edges = [(d.task_id, d.blocker_id) for d in ... TaskDependency]` —
   toutes les arêtes de la base, chargées une fois par requête.
2. `status_by_id` — statut de chaque tâche (y compris `done`/`archived`, pas
   seulement `todo`), nécessaire pour lever le blocage sur bloqueur terminé.
3. `blocked_ids = blocked_task_ids(dep_edges, status_by_id)` — calculé **tôt**
   dans la fonction (commentaire explicite ligne 395-396) : sert dès le
   garde-fou de surcharge de priorité (`count_max_priority_tasks`, exclut les
   tâches bloquées du décompte — voir `ordonnancement.md`), avant même le
   calcul du planning du jour.
4. `block_reasons = blocking_reason(dep_edges, status_by_id,
   breadcrumb_title_by_id)` — les titres passés en motif sont **préfixés par
   le titre de la tâche mère** quand elle existe (`breadcrumb_title_of`,
   construit juste avant), pour lever l'ambiguïté entre deux sous-tâches de
   même nom sous des mères différentes dans l'affichage « en attente de : … ».
5. `own_urgency = {t.id: urgency_key(t, target_day, settings=settings) for t
   in tasks}` puis `effective_urgency = derived_urgency(dep_edges,
   own_urgency)` — l'urgence propre de chaque tâche `todo` du jour est
   calculée par `tasks_scheduling.urgency_key` (hors périmètre de ce
   document), puis relevée par ce module.
6. `raised_ids = {tid for tid, key in effective_urgency.items() if key <
   own_urgency.get(tid, key)}` — l'ensemble des tâches dont la clé effective
   est strictement plus forte que la clé propre : c'est cet ensemble, pas
   `effective_urgency` lui-même, qui pilote le badge « chemin critique »
   affiché dans `_kairos_day.html` (`{% if item.task.id in raised_ids %}`) et
   le court-circuit du creux de l'après-midi dans `_selection_key`
   (`ordonnancement.md`).

`blocked_ids` est ensuite utilisé pour construire `blocked_tasks` (jointure
avec `by_id` pour récupérer l'objet `Task`, tri alphabétique par titre) et
pour exclure ces tâches du calcul de `backlog_tasks` — une tâche bloquée
n'apparaît **jamais** dans le panneau Backlog (sans échéance ni date
programmée), cohérent avec le principe qu'une tâche bloquée n'est pas
« organisable » avant d'être débloquée.

### Édition des bloqueurs (`POST /kairos/tasks/{id}/edit`)

Depuis la phase 6, l'édition des bloqueurs d'une tâche n'est plus un couple
de routes dédiées (`.../deps`, `.../deps/{blocker_id}/delete`, disparues) mais
un champ du formulaire d'édition consolidé, traité dans `edit_task`
(`app/main.py`, lignes ~1133-1156) :

```python
target_blocker_ids = {
    bid for raw in form.getlist("blocker_ids")
    if (bid := _optional_int(raw)) is not None and bid != task_id
}
existing_deps = list(tasks_session.scalars(
    select(TaskDependency).where(TaskDependency.task_id == task_id)
))
existing_blocker_ids = {d.blocker_id for d in existing_deps}
for dep in existing_deps:
    if dep.blocker_id not in target_blocker_ids:
        tasks_session.delete(dep)
edges = [(d.task_id, d.blocker_id) for d in tasks_session.scalars(select(TaskDependency))]
for blocker_id in target_blocker_ids - existing_blocker_ids:
    if tasks_session.get(Task, blocker_id) is None:
        continue
    if would_create_cycle(edges, task_id, blocker_id):
        continue  # ignoré silencieusement
    tasks_session.add(TaskDependency(task_id=task_id, blocker_id=blocker_id))
    edges.append((task_id, blocker_id))
```

`target_blocker_ids` est **l'état cible complet** soumis par le formulaire
(cases cochées), pas un delta explicite — la route calcule elle-même le diff
avec `existing_blocker_ids` : ce qui est en base mais plus coché est retiré,
ce qui est coché mais absent est ajouté (après vérification d'existence de la
tâche cible et de non-création de cycle via `would_create_cycle`, rechargé
sur `edges` mis à jour à chaque ajout pour détecter un cycle introduit par un
ajout précédent dans le même enregistrement). Une arête refusée (cycle) est
**ignorée silencieusement** : le reste du formulaire (infos, sous-tâches en
lot, autres bloqueurs valides) est enregistré normalement — pas d'échec en
cascade sur une seule arête invalide.

`deps_of` (contexte de rendu, ligne ~594-600) reconstruit, pour chaque tâche
ayant au moins un bloqueur, la liste `{id, title}` de ses bloqueurs directs à
partir de `dep_edges` et `title_by_id` — consommé par `edit_panel` pour
pré-cocher les cases déjà posées (`current_blocker_ids`).

### Décisions et pièges tracés

1. **Convention d'arête `(bloquée, bloquante)` reprise telle quelle de
   `dependency_rules.py`** (GitLab, module d'inspiration cité en tête de
   fichier) : l'algorithme de Kahn et le point fixe monotone de
   `derived_urgency` sont des adaptations directes, pas une réinvention.
2. **Transitivité de `blocked_task_ids` sans point fixe explicite.** Le
   commentaire de la fonction (lignes 72-74) trace explicitement pourquoi
   aucune boucle de propagation supplémentaire n'est nécessaire : le statut
   `todo`/non-`todo` d'un bloqueur intermédiaire suffit à faire remonter le
   blocage sur une seule passe des arêtes.
3. **Cycles neutralisés, jamais signalés en erreur.** `_acyclic_edges` retire
   silencieusement toute arête interne à un cycle plutôt que de lever une
   exception ou d'afficher un avertissement — cohérent avec la philosophie de
   dégradation propre du reste de l'app (TimeTree, GitLab) : un cycle ne doit
   jamais provoquer de page cassée ni de blocage mutuel masquant deux tâches
   à la fois.
4. **`would_create_cycle` couvre l'auto-arête comme cas trivial**
   (`new_blocked == new_blocker` → `True` avant même d'appeler
   `_reachable_blockers`) — testé explicitement
   (`test_would_create_cycle`, `tests/test_tasks_dependencies.py`).
5. **Urgence dérivée jamais persistée.** Tracé dès l'en-tête du module (ligne
   9-11) : « calculée à l'affichage, elle ne modifie **jamais**
   `Task.priority` ». Aucune colonne `Task` ne stocke de valeur d'urgence
   dérivée — recalculée à chaque requête à partir de `own_urgency`
   (elle-même recalculée à chaque requête par `urgency_key`). Conséquence :
   changer une dépendance ou l'urgence propre d'une tâche a un effet immédiat
   au prochain rendu, sans migration ni nettoyage de données obsolètes.
6. **Borne de passes `len(own_urgency) + 1` dans `derived_urgency`**, plutôt
   qu'une boucle `while changed` non bornée : garde-fou défensif contre une
   boucle infinie si une future modification introduisait par erreur des
   arêtes non filtrées par `_acyclic_edges` en amont — sur le graphe
   acyclique réellement utilisé (post-filtrage), le point fixe est de toute
   façon atteint bien avant cette borne.
7. **`blocking_reason` ne remonte que les bloqueurs directs**, pas la chaîne
   transitive complète : choix d'affichage (l'utilisateur voit la cause
   immédiate à traiter, pas un historique complet de dépendances) — testé par
   `test_blocking_reason_lists_open_blockers`.
8. **Revirement de l'UI de sélection des bloqueurs, sans changement
   backend.** La phase 13 (SPEC_KAIROS.md) avait remplacé la liste de cases à
   cocher par un `<select name="blocker_ids" multiple>`, pour un rendu
   identique à « Fiche liée ». La refonte GTD ultérieure (commit `589e2cb`,
   « Refonte Jour (étapes B-E) ») est **revenue** à des cases à cocher
   (`.mj-blocker-checks`, template `_kairos_macros.html::edit_panel`) : un
   `<select multiple>` impose un Ctrl-clic pour sélectionner plusieurs
   valeurs, moins accessible qu'une liste de cases à cocher pour un nombre de
   bloqueurs potentiellement grand. Le champ soumis reste `name="blocker_ids"`
   dans les deux cas — `edit_task` traite l'ensemble reçu comme l'état cible
   complet sans distinguer la nature du widget HTML d'origine, donc **aucun
   changement côté route** n'a été nécessaire pour ce second revirement
   (tracé explicitement par le test `test_blocker_picker_uses_checkboxes`,
   `tests/test_kairos_route.py`). État courant du code (juillet 2026) : cases
   à cocher — ne pas réintroduire le `<select multiple>` sans en rediscuter,
   la régression d'accessibilité a motivé le retour en arrière.

### Invariants et garde-fous

- **Pureté fonctionnelle totale** : aucune fonction du module n'effectue de
  requête SQL ni d'appel réseau — entièrement testable en isolation sur des
  listes/dictionnaires en mémoire (`tests/test_tasks_dependencies.py`).
- **Jamais de blocage fantôme causé par un cycle** : un cycle isolé (2 nœuds
  ou plus, arêtes uniquement internes au cycle) ne bloque jamais aucune des
  tâches impliquées (`test_cycle_is_neutralised_in_blocking`).
- **Une tâche `done`/`archived` n'est jamais rapportée bloquée**, quel que
  soit l'état de ses propres bloqueurs (`test_done_task_never_reported_blocked`).
- **Aucune écriture de cycle possible via `edit_task`** : toute arête qui
  fermerait une boucle (directe ou transitive) est filtrée par
  `would_create_cycle` avant `tasks_session.add(TaskDependency(...))` — aucun
  garde-fou de base (`UniqueConstraint` sur `(task_id, blocker_id)`
  seulement) ne protège contre les cycles, la protection est entièrement
  côté application.
- **`derived_urgency` ne mute aucun objet ni aucune colonne persistée** : la
  seule sortie est un nouveau dictionnaire `{id: clé}`, jamais un effet de
  bord sur `Task`.
- **Convention d'arête stable** `(bloquée, bloquante)` dans toute l'API
  publique du module — toute nouvelle fonction ajoutée à ce fichier doit la
  respecter pour rester cohérente avec `TaskDependency.task_id`/`blocker_id`.
