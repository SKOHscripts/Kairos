"""Tests de la synchronisation GitLab assignée → base tâches native (mutualisée).

Aucun mock HTTP : `sync_assigned_gitlab_tasks` est pure côté source (upsert
seulement) — ces tests lui passent directement les issues du cache
`CachedGitLabIssue` (alimenté ailleurs par le rafraîchissement de l'onglet
Pilotage GitLab), via le petit helper `_sync` ci-dessous.
"""

from __future__ import annotations

from sqlalchemy import select

from app.pilotage_link import CachedGitLabIssue
from app.tasks_gitlab_sync import SyncResult, sync_assigned_gitlab_tasks
from app.tasks_models import Task, TaskSyncMeta

PROJECT = "mon-groupe/mon-projet"
OTHER_PROJECT = "mon-groupe/autre-projet"


def _cached_issue(iid: int, **overrides) -> CachedGitLabIssue:
    defaults = dict(
        project=PROJECT, iid=iid, title=f"Issue {iid}", state="opened",
        assignees="corentin,alice", due_date="",
    )
    defaults.update(overrides)
    return CachedGitLabIssue(**defaults)


def _sync(pilotage_session, tasks_session, assignee_username: str) -> SyncResult:
    """Relit le cache pilotage (comme le ferait `app/main.py`) puis upsert."""
    issues = list(pilotage_session.scalars(select(CachedGitLabIssue)))
    return sync_assigned_gitlab_tasks(issues, tasks_session, assignee_username)


def test_disabled_when_assignee_username_empty(pilotage_session, tasks_session) -> None:
    pilotage_session.add(_cached_issue(1))
    pilotage_session.commit()

    result = _sync(pilotage_session, tasks_session, "")

    assert result.ok is True
    assert result.count == 0
    assert tasks_session.query(Task).count() == 0


def test_creates_task_from_assigned_open_issue(pilotage_session, tasks_session) -> None:
    pilotage_session.add(_cached_issue(42, title="Corriger le crash", due_date="2026-07-10"))
    pilotage_session.commit()

    result = _sync(pilotage_session, tasks_session, "corentin")

    assert result.ok is True
    assert result.count == 1
    task = tasks_session.query(Task).one()
    assert task.title == "#42 Corriger le crash"
    assert task.source == "gitlab"
    assert task.external_id == f"{PROJECT}#42"
    assert task.deadline.isoformat() == "2026-07-10"
    assert task.project_tag == PROJECT
    assert task.priority is None


def test_ignores_issues_not_assigned_to_username(pilotage_session, tasks_session) -> None:
    pilotage_session.add(_cached_issue(1, assignees="alice,bob"))
    pilotage_session.commit()

    result = _sync(pilotage_session, tasks_session, "corentin")

    assert result.count == 0
    assert tasks_session.query(Task).count() == 0


def test_ignores_closed_issues(pilotage_session, tasks_session) -> None:
    pilotage_session.add(_cached_issue(1, state="closed"))
    pilotage_session.commit()

    _sync(pilotage_session, tasks_session, "corentin")

    assert tasks_session.query(Task).count() == 0


def test_sync_is_idempotent(pilotage_session, tasks_session) -> None:
    pilotage_session.add(_cached_issue(1))
    pilotage_session.commit()

    _sync(pilotage_session, tasks_session, "corentin")
    _sync(pilotage_session, tasks_session, "corentin")

    assert tasks_session.query(Task).count() == 1


def test_priority_never_overwritten(pilotage_session, tasks_session) -> None:
    pilotage_session.add(_cached_issue(1, title="Titre v1"))
    pilotage_session.commit()
    _sync(pilotage_session, tasks_session, "corentin")

    task = tasks_session.query(Task).one()
    task.priority = 1
    tasks_session.commit()

    pilotage_session.query(CachedGitLabIssue).filter_by(iid=1).update({"title": "Titre v2"})
    pilotage_session.commit()
    _sync(pilotage_session, tasks_session, "corentin")

    tasks_session.refresh(task)
    assert task.title == "#1 Titre v2"
    assert task.priority == 1  # jamais écrasée


def test_closed_issue_archives_task(pilotage_session, tasks_session) -> None:
    pilotage_session.add(_cached_issue(1))
    pilotage_session.commit()
    _sync(pilotage_session, tasks_session, "corentin")

    pilotage_session.query(CachedGitLabIssue).filter_by(iid=1).update({"state": "closed"})
    pilotage_session.commit()
    _sync(pilotage_session, tasks_session, "corentin")

    task = tasks_session.query(Task).one()
    assert task.status == "archived"
    assert tasks_session.query(Task).count() == 1  # jamais supprimée


def test_reassigned_while_still_open_archives_task(pilotage_session, tasks_session) -> None:
    """Phase 7 : durcissement — une issue réassignée à quelqu'un d'autre en
    restant ouverte (pas fermée) doit aussi archiver la tâche correspondante.
    Ce scénario n'était pas testé jusqu'ici (seule la fermeture l'était), même
    si la logique de sync_assigned_gitlab_tasks le gère déjà correctement."""
    pilotage_session.add(_cached_issue(1, assignees="corentin,alice"))
    pilotage_session.commit()
    _sync(pilotage_session, tasks_session, "corentin")
    assert tasks_session.query(Task).filter_by(status="todo").count() == 1

    # L'issue reste ouverte mais n'est plus assignée à corentin.
    pilotage_session.query(CachedGitLabIssue).filter_by(iid=1).update({"assignees": "alice"})
    pilotage_session.commit()
    _sync(pilotage_session, tasks_session, "corentin")

    task = tasks_session.query(Task).one()
    assert task.status == "archived"
    assert tasks_session.query(Task).count() == 1  # jamais supprimée, jamais dupliquée


def test_writes_sync_meta(pilotage_session, tasks_session) -> None:
    pilotage_session.add(_cached_issue(1))
    pilotage_session.add(_cached_issue(2))
    pilotage_session.commit()

    _sync(pilotage_session, tasks_session, "corentin")

    meta = tasks_session.query(TaskSyncMeta).filter_by(source="gitlab").one()
    assert meta.last_outcome == "ok"
    assert meta.item_count == 2


def test_assigned_issue_from_any_cached_project_is_imported(pilotage_session, tasks_session) -> None:
    """Phase 6 : l'auto-import n'est plus borné à un seul projet — toute issue
    assignée, quel que soit le projet en cache, apparaît."""
    pilotage_session.add(_cached_issue(1, project=OTHER_PROJECT, title="Depuis un autre projet"))
    pilotage_session.commit()

    result = _sync(pilotage_session, tasks_session, "corentin")

    assert result.count == 1
    task = tasks_session.query(Task).one()
    assert task.title == "#1 Depuis un autre projet"
    assert task.project_tag == OTHER_PROJECT


def test_same_iid_in_different_projects_creates_two_distinct_tasks(pilotage_session, tasks_session) -> None:
    """external_id qualifié par projet : deux issues !5 dans deux projets
    différents ne doivent jamais entrer en collision."""
    pilotage_session.add(_cached_issue(5, project=PROJECT, title="Issue A"))
    pilotage_session.add(_cached_issue(5, project=OTHER_PROJECT, title="Issue B"))
    pilotage_session.commit()

    result = _sync(pilotage_session, tasks_session, "corentin")

    assert result.count == 2
    tasks = tasks_session.query(Task).order_by(Task.title).all()
    assert [t.title for t in tasks] == ["#5 Issue A", "#5 Issue B"]
    assert {t.external_id for t in tasks} == {f"{PROJECT}#5", f"{OTHER_PROJECT}#5"}


def test_legacy_external_id_format_is_rekeyed_not_duplicated(pilotage_session, tasks_session) -> None:
    """Une tâche synchronisée sous l'ancien format (phase 4, external_id = str(iid)
    brut) est rebaptisée en place au nouveau format qualifié, jamais dupliquée —
    priorité et statut déjà posés dessus restent intacts."""
    legacy_task = Task(
        source="gitlab", external_id="42", project_tag=PROJECT,
        title="Ancien format", priority=1, status="todo",
    )
    tasks_session.add(legacy_task)
    tasks_session.commit()
    legacy_id = legacy_task.id

    pilotage_session.add(_cached_issue(42, title="Ancien format (màj)"))
    pilotage_session.commit()

    result = _sync(pilotage_session, tasks_session, "corentin")

    assert result.count == 1
    assert tasks_session.query(Task).count() == 1  # pas de doublon
    task = tasks_session.get(Task, legacy_id)
    assert task.external_id == f"{PROJECT}#42"
    assert task.priority == 1  # priorité déjà posée, préservée
    assert task.title == "#42 Ancien format (màj)"  # titre resynchronisé normalement
