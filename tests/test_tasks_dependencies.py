"""Tests du moteur de dépendances (logique pure : blocage, cycles, urgence dérivée)."""

from __future__ import annotations

from app.tasks_dependencies import (
    blocked_task_ids,
    blocking_reason,
    derived_urgency,
    detect_cycle_nodes,
    would_create_cycle,
)


# --------------------------------------------------------------------------- #
# Blocage
# --------------------------------------------------------------------------- #

def test_open_blocker_blocks_task() -> None:
    # A (1) bloquée par B (2) ; B à faire → A bloquée.
    edges = [(1, 2)]
    status = {1: "todo", 2: "todo"}
    assert blocked_task_ids(edges, status) == {1}


def test_done_blocker_releases_task() -> None:
    edges = [(1, 2)]
    status = {1: "todo", 2: "done"}
    assert blocked_task_ids(edges, status) == set()


def test_archived_blocker_releases_task() -> None:
    edges = [(1, 2)]
    status = {1: "todo", 2: "archived"}
    assert blocked_task_ids(edges, status) == set()


def test_transitive_blocking() -> None:
    # A(1) ← B(2) ← C(3), tous à faire → A et B bloquées, C libre.
    edges = [(1, 2), (2, 3)]
    status = {1: "todo", 2: "todo", 3: "todo"}
    assert blocked_task_ids(edges, status) == {1, 2}


def test_transitive_release_cascades() -> None:
    edges = [(1, 2), (2, 3)]
    # C fait → B libre (todo, plus bloquée) ; A toujours bloquée par B (todo).
    assert blocked_task_ids(edges, {1: "todo", 2: "todo", 3: "done"}) == {1}
    # B fait aussi → A libre.
    assert blocked_task_ids(edges, {1: "todo", 2: "done", 3: "done"}) == set()


def test_done_task_never_reported_blocked() -> None:
    edges = [(1, 2)]
    assert blocked_task_ids(edges, {1: "done", 2: "todo"}) == set()


# --------------------------------------------------------------------------- #
# Cycles
# --------------------------------------------------------------------------- #

def test_detect_two_node_cycle() -> None:
    assert detect_cycle_nodes([(1, 2), (2, 1)]) == {1, 2}


def test_no_cycle_on_dag() -> None:
    assert detect_cycle_nodes([(1, 2), (2, 3)]) == set()


def test_cycle_is_neutralised_in_blocking() -> None:
    # Cycle 1↔2 : aucune ne bloque l'autre (arêtes ignorées), pas de blocage fantôme.
    assert blocked_task_ids([(1, 2), (2, 1)], {1: "todo", 2: "todo"}) == set()


def test_would_create_cycle() -> None:
    edges = [(1, 2)]  # 1 bloquée par 2
    # Ajouter « 2 bloquée par 1 » fermerait la boucle.
    assert would_create_cycle(edges, new_blocked=2, new_blocker=1) is True
    # Ajouter « 3 bloquée par 1 » est sûr.
    assert would_create_cycle(edges, new_blocked=3, new_blocker=1) is False
    # Auto-dépendance interdite.
    assert would_create_cycle(edges, new_blocked=5, new_blocker=5) is True


def test_would_create_cycle_transitive() -> None:
    edges = [(1, 2), (2, 3)]  # 1 ← 2 ← 3
    # « 3 bloquée par 1 » fermerait 1←2←3←1.
    assert would_create_cycle(edges, new_blocked=3, new_blocker=1) is True


# --------------------------------------------------------------------------- #
# Raison de blocage
# --------------------------------------------------------------------------- #

def test_blocking_reason_lists_open_blockers() -> None:
    edges = [(1, 2), (1, 3)]
    status = {1: "todo", 2: "todo", 3: "done"}
    titles = {2: "Écrire la spec", 3: "Réunion faite"}
    reasons = blocking_reason(edges, status, titles)
    assert reasons == {1: ["Écrire la spec"]}  # seul le bloqueur encore à faire


# --------------------------------------------------------------------------- #
# Urgence dérivée (chemin critique)
# --------------------------------------------------------------------------- #

def test_blocker_inherits_urgency_of_what_it_blocks() -> None:
    # A(1) très urgente (clé (0,)), B(2) peu urgente (clé (3,)). A bloquée par B.
    # B doit hériter de l'urgence de A pour remonter dans l'ordre.
    edges = [(1, 2)]
    own = {1: (0,), 2: (3,)}
    eff = derived_urgency(edges, own)
    assert eff[2] == (0,)  # B relevée au niveau de A
    assert eff[1] == (0,)  # A inchangée


def test_urgency_propagates_along_chain() -> None:
    # A(1) urgente ← B(2) ← C(3) : C hérite aussi de l'urgence de A.
    edges = [(1, 2), (2, 3)]
    own = {1: (0,), 2: (5,), 3: (9,)}
    eff = derived_urgency(edges, own)
    assert eff[2] == (0,)
    assert eff[3] == (0,)


def test_urgency_unchanged_without_dependencies() -> None:
    own = {1: (2,), 2: (4,)}
    assert derived_urgency([], own) == own


def test_urgency_cycle_does_not_loop() -> None:
    # Cycle : la propagation ne doit pas boucler à l'infini ni planter.
    own = {1: (1,), 2: (2,)}
    result = derived_urgency([(1, 2), (2, 1)], own)
    assert result == own  # arêtes du cycle ignorées, urgences propres conservées
