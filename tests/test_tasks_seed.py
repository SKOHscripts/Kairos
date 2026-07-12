"""Tests des données d'exemple de la première utilisation (voir `app/tasks_seed.py`)."""

from __future__ import annotations

from datetime import date

from sqlalchemy import create_engine
from sqlalchemy.orm import Session, sessionmaker

from app import tasks_db
from app.tasks_models import Task, TaskDependency, TimeBlock
from app.tasks_seed import EXAMPLE_PROJECT_TAG, seed_example_data

TODAY = date(2026, 7, 12)


def test_seed_creates_illustrative_tasks_and_blocks(tasks_session: Session) -> None:
    seed_example_data(tasks_session, today=TODAY)

    tasks = tasks_session.query(Task).all()
    blocks = tasks_session.query(TimeBlock).all()
    assert len(tasks) > 0
    assert len(blocks) > 0
    # Toutes les tâches d'exemple sont natives et taguées « Exemple » (repérables,
    # supprimables une à une comme n'importe quelle tâche native).
    assert all(t.source == "native" for t in tasks)
    assert all(t.project_tag == EXAMPLE_PROJECT_TAG for t in tasks)


def test_seed_includes_inbox_task(tasks_session: Session) -> None:
    """Au moins une tâche « À traiter » : ni priorité ni points de Fibonacci."""
    seed_example_data(tasks_session, today=TODAY)
    inbox = [
        t for t in tasks_session.query(Task).all()
        if t.priority is None and t.fibonacci_points is None
    ]
    assert inbox, "aucune tâche d'exemple ne démontre la section « À traiter »"


def test_seed_includes_parent_with_subtasks(tasks_session: Session) -> None:
    seed_example_data(tasks_session, today=TODAY)
    tasks = tasks_session.query(Task).all()
    parent_ids = {t.parent_id for t in tasks if t.parent_id is not None}
    assert parent_ids, "aucune sous-tâche d'exemple (parent_id)"
    # Chaque parent référencé existe bien.
    ids = {t.id for t in tasks}
    assert parent_ids <= ids


def test_seed_includes_dependency(tasks_session: Session) -> None:
    seed_example_data(tasks_session, today=TODAY)
    deps = tasks_session.query(TaskDependency).all()
    assert deps, "aucune dépendance « bloqué par » d'exemple"


def test_seed_includes_meeting_deepwork_and_recurring_blocks(tasks_session: Session) -> None:
    seed_example_data(tasks_session, today=TODAY)
    blocks = tasks_session.query(TimeBlock).all()
    kinds = {b.kind for b in blocks}
    assert "busy" in kinds and "deepwork" in kinds
    assert any(b.recurrence == "daily" for b in blocks), "aucun créneau récurrent d'exemple"
    # Réunion 13h-14h aujourd'hui : le cas emblématique (repoussé à 14h05).
    assert any(
        b.start.date() == TODAY and b.start.hour == 13 and b.end.hour == 14
        for b in blocks
    ), "aucune réunion 13h-14h d'exemple"


def test_init_tasks_db_seeds_fresh_then_never_reseeds(tmp_path, monkeypatch) -> None:
    """`init_tasks_db` sème sur une base vierge, et jamais ensuite (table présente)."""
    db_path = tmp_path / "tasks.db"
    engine = create_engine(
        f"sqlite:///{db_path}", connect_args={"check_same_thread": False}, future=True
    )
    local = sessionmaker(bind=engine, autoflush=False, expire_on_commit=False)
    monkeypatch.setattr(tasks_db, "tasks_engine", engine)
    monkeypatch.setattr(tasks_db, "TasksSessionLocal", local)

    tasks_db.init_tasks_db()  # base vierge → pose les exemples
    with local() as session:
        first = session.query(Task).count()
    assert first > 0

    # Supprimer tous les exemples ne doit pas les faire réapparaître : la table existe.
    with local() as session:
        session.query(Task).delete()
        session.commit()
    tasks_db.init_tasks_db()  # table présente → aucun re-seed
    with local() as session:
        assert session.query(Task).count() == 0
