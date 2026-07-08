"""Moteur de dépendances « bloqué par » entre tâches natives (logique pure, sans I/O).

Adapté du moteur GitLab existant (``app/dependency_rules.py``) : détection de cycles
par l'algorithme de **Kahn** et propagation à point fixe monotone. Ici, deux calculs :

1. **Blocage transitif** : une tâche est bloquée tant qu'au moins un de ses bloqueurs
   (directs ou transitifs) est encore à faire. Un bloqueur ``done``/``archived`` est
   levé. Sert à retirer les tâches bloquées de la planification du jour.
2. **Urgence dérivée** : un bloqueur hérite de l'urgence **la plus forte** de tout ce
   qu'il bloque (chemin critique). Calculée à l'affichage, elle ne modifie **jamais**
   ``Task.priority`` : non destructif, réversible, recalculé à chaque rendu.

Les **cycles** sont neutralisés (arêtes ignorées) : jamais de boucle infinie ni de
blocage mutuel qui masquerait tout un ensemble de tâches.

Convention d'arête, comme dans ``dependency_rules`` : ``(bloquée, bloquante)``.
"""

from __future__ import annotations

from collections import defaultdict


def detect_cycle_nodes(edges: list[tuple[int, int]]) -> set[int]:
    """Nœuds impliqués dans un cycle (arête ``(bloquée, bloquante)``), via Kahn.

    Repris de ``dependency_rules._cycle_nodes`` : un nœud dont le degré entrant ne
    retombe jamais à zéro appartient à un cycle.
    """
    nodes = {n for edge in edges for n in edge}
    indeg = {n: 0 for n in nodes}
    adj: dict[int, list[int]] = {n: [] for n in nodes}
    for blocked, blocker in edges:
        adj[blocker].append(blocked)
        indeg[blocked] += 1
    queue = [n for n in nodes if indeg[n] == 0]
    while queue:
        node = queue.pop()
        for nxt in adj[node]:
            indeg[nxt] -= 1
            if indeg[nxt] == 0:
                queue.append(nxt)
    return {n for n in nodes if indeg[n] > 0}


def _acyclic_edges(edges: list[tuple[int, int]]) -> list[tuple[int, int]]:
    """Arêtes hors cycle (une arête dont les deux extrémités sont dans un cycle est
    ignorée), dédupliquées et sans auto-boucle."""
    cycles = detect_cycle_nodes(edges)
    seen: set[tuple[int, int]] = set()
    kept: list[tuple[int, int]] = []
    for blocked, blocker in edges:
        if blocked == blocker:
            continue
        if blocked in cycles and blocker in cycles:
            continue
        if (blocked, blocker) in seen:
            continue
        seen.add((blocked, blocker))
        kept.append((blocked, blocker))
    return kept


def blocked_task_ids(
    edges: list[tuple[int, int]], status_by_id: dict[int, str]
) -> set[int]:
    """Ids des tâches bloquées : au moins un bloqueur direct est encore à faire (``todo``).

    La transitivité est naturelle et ne demande pas de point fixe : si A est bloquée
    par B et B par C, alors tant que C n'est pas fait, B reste ``todo`` — donc A est
    bloquée par B (todo), et B par C (todo). Terminer C débloque B (qui reste todo),
    A reste bloquée par B ; terminer B débloque A. Un bloqueur ``done``/``archived``
    (statut ≠ ``todo``) ne bloque plus.
    """
    blocked: set[int] = set()
    for task, blocker in _acyclic_edges(edges):
        if status_by_id.get(task, "todo") != "todo":
            continue  # une tâche déjà faite/archivée n'est jamais « bloquée »
        if status_by_id.get(blocker, "todo") == "todo":
            blocked.add(task)
    return blocked


def blocking_reason(
    edges: list[tuple[int, int]],
    status_by_id: dict[int, str],
    title_by_id: dict[int, str],
) -> dict[int, list[str]]:
    """Pour chaque tâche bloquée, les titres des bloqueurs **directs** encore à faire."""
    active_edges = _acyclic_edges(edges)
    reasons: dict[int, list[str]] = defaultdict(list)
    for blocked, blocker in active_edges:
        if status_by_id.get(blocker, "todo") == "todo":
            reasons[blocked].append(title_by_id.get(blocker, f"#{blocker}"))
    return {tid: titles for tid, titles in reasons.items() if titles}


def would_create_cycle(
    existing_edges: list[tuple[int, int]], new_blocked: int, new_blocker: int
) -> bool:
    """Vrai si ajouter l'arête ``(new_blocked, new_blocker)`` créerait un cycle.

    Un cycle apparaît si ``new_blocker`` dépend déjà (transitivement) de
    ``new_blocked`` — ajouter l'arête inverse bouclerait. Couvre aussi l'auto-arête.
    """
    if new_blocked == new_blocker:
        return True
    return new_blocked in _reachable_blockers(existing_edges, new_blocker)


def _reachable_blockers(edges: list[tuple[int, int]], start: int) -> set[int]:
    """Ensemble des tâches dont ``start`` dépend transitivement (ses bloqueurs, récursif)."""
    blockers_of: dict[int, list[int]] = defaultdict(list)
    for blocked, blocker in edges:
        blockers_of[blocked].append(blocker)
    seen: set[int] = set()
    stack = list(blockers_of.get(start, []))
    while stack:
        node = stack.pop()
        if node in seen:
            continue
        seen.add(node)
        stack.extend(blockers_of.get(node, []))
    return seen


def derived_urgency(
    edges: list[tuple[int, int]], own_urgency: dict[int, tuple]
) -> dict[int, tuple]:
    """Urgence effective : un bloqueur hérite de l'urgence min (plus forte) de ce qu'il
    bloque, transitivement (point fixe monotone, cycles exclus).

    ``own_urgency`` : clé de tri propre de chaque tâche (plus petite = plus urgente,
    convention du scheduler). Retourne la clé effective par id ; identique à la clé
    propre pour une tâche que rien n'élève. Ne mute aucun modèle.
    """
    active_edges = _acyclic_edges(edges)
    effective = dict(own_urgency)
    # blocked → [blockers] : pour propager l'urgence de la bloquée vers ses bloquantes.
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
