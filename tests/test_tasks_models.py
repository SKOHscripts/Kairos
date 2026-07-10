"""Tests des modèles de « Kairos » (Task, TimeBlock, TaskSyncMeta)."""

from __future__ import annotations

import tempfile
from pathlib import Path

import pytest
from sqlalchemy import create_engine, inspect
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import sessionmaker

from app.pilotage_link import PilotageBase
from app.tasks_models import Task, TasksBase, TaskSyncMeta, TimeBlock


def test_task_unique_constraint_on_source_and_external_id(tasks_session) -> None:
    tasks_session.add(Task(title="Import 1", source="gitlab", external_id="gl-1"))
    tasks_session.commit()

    tasks_session.add(Task(title="Import 1 bis", source="gitlab", external_id="gl-1"))
    with pytest.raises(IntegrityError):
        tasks_session.commit()


def test_task_defaults(tasks_session) -> None:
    task = Task(title="Tâche native")
    tasks_session.add(task)
    tasks_session.commit()

    assert task.priority is None
    assert task.status == "todo"
    assert task.source == "native"
    assert task.external_id is None


def test_two_native_tasks_do_not_collide_on_unique_constraint(tasks_session) -> None:
    """Régression phase 2 : external_id NULL (et non "") sur les natives, sinon la
    contrainte unique (source, external_id) interdisait la deuxième tâche native."""
    tasks_session.add(Task(title="Première native"))
    tasks_session.add(Task(title="Deuxième native"))
    tasks_session.commit()

    assert tasks_session.query(Task).count() == 2


def test_time_block_round_trip(tasks_session) -> None:
    from datetime import datetime

    block = TimeBlock(
        title="Réunion",
        start=datetime(2026, 7, 2, 13, 0),
        end=datetime(2026, 7, 2, 14, 0),
        source="manual",
    )
    tasks_session.add(block)
    tasks_session.commit()

    fetched = tasks_session.get(TimeBlock, block.id)
    assert fetched.title == "Réunion"
    assert fetched.source == "manual"


def test_task_sync_meta_unique_per_source(tasks_session) -> None:
    tasks_session.add(TaskSyncMeta(source="gitlab", item_count=3))
    tasks_session.commit()

    tasks_session.add(TaskSyncMeta(source="gitlab", item_count=5))
    with pytest.raises(IntegrityError):
        tasks_session.commit()


def test_task_type_and_fibonacci_points_defaults(tasks_session) -> None:
    task = Task(title="Sans métadonnées phase 5")
    tasks_session.add(task)
    tasks_session.commit()

    assert task.task_type == ""
    assert task.fibonacci_points is None


def test_task_type_and_fibonacci_points_round_trip(tasks_session) -> None:
    task = Task(title="Avec métadonnées", task_type="dev", fibonacci_points=5)
    tasks_session.add(task)
    tasks_session.commit()

    fetched = tasks_session.get(Task, task.id)
    assert fetched.task_type == "dev"
    assert fetched.fibonacci_points == 5


def test_tasks_base_is_separate_from_ticket_base() -> None:
    """Les modèles tâches et les projections pilotage ne partagent aucune table."""
    tasks_tables = set(TasksBase.metadata.tables)
    ticket_tables = set(PilotageBase.metadata.tables)
    assert tasks_tables.isdisjoint(ticket_tables)


def test_init_tasks_db_migrates_phase1_schema_without_data_loss(tmp_path: Path) -> None:
    """Base phase 1 peuplée (schéma sans les colonnes phase 2) → migrée intacte.

    Reproduit la situation réelle de l'utilisateur : tasks.db créée par la phase 1,
    contenant des tâches importées (à l'époque depuis Superproductivity, retiré
    depuis) et des priorités posées à la main. Sert aussi de régression pour le
    retrait de Superproductivity : une tâche `source='superproductivity'`
    existante doit devenir `'native'` au démarrage, sans être dupliquée ni perdue.
    """
    from sqlalchemy import text
    from sqlalchemy.orm import sessionmaker

    from app.tasks_db import init_tasks_db
    from app.tasks_models import Task
    import app.tasks_db as tasks_db_module

    db_file = tmp_path / "tasks_phase1.db"
    engine = create_engine(f"sqlite:///{db_file}", connect_args={"check_same_thread": False})
    # Schéma phase 1 : sans estimated_minutes/pinned_start/parent_id/recurrence.
    with engine.begin() as conn:
        conn.execute(
            text(
                "CREATE TABLE task ("
                " id INTEGER PRIMARY KEY, title VARCHAR(512), description TEXT,"
                " priority INTEGER, deadline DATE, project_tag VARCHAR(255),"
                " status VARCHAR(16), source VARCHAR(32), external_id VARCHAR(64),"
                " created_at DATETIME, updated_at DATETIME,"
                " CONSTRAINT uq_task_source_external_id UNIQUE (source, external_id))"
            )
        )
        conn.execute(
            text(
                "INSERT INTO task (title, priority, status, source, external_id)"
                " VALUES ('Tâche existante', 1, 'todo', 'superproductivity', 'sp-42')"
            )
        )

    original_engine = tasks_db_module.tasks_engine
    original_session_local = tasks_db_module.TasksSessionLocal
    tasks_db_module.tasks_engine = engine
    tasks_db_module.TasksSessionLocal = sessionmaker(bind=engine, expire_on_commit=False)
    try:
        init_tasks_db()
        columns = {col["name"] for col in inspect(engine).get_columns("task")}
        assert {"estimated_minutes", "pinned_start", "parent_id", "recurrence"} <= columns

        with sessionmaker(bind=engine)() as db:
            task = db.query(Task).one()
            assert task.title == "Tâche existante"
            assert task.priority == 1  # données phase 1 intactes
            assert task.estimated_minutes is None
            assert task.recurrence in ("", None)  # défaut SQL appliqué
            assert task.source == "native"  # retrait de Superproductivity : rebaptisée
            assert task.external_id == "sp-42"  # trace d'origine conservée
            assert db.query(Task).count() == 1  # jamais dupliquée
    finally:
        tasks_db_module.tasks_engine = original_engine
        tasks_db_module.TasksSessionLocal = original_session_local
        engine.dispose()


def test_init_tasks_db_migrates_time_block_kind(tmp_path: Path) -> None:
    """Base phase 2 : une table time_block sans colonne `kind` reçoit le défaut 'busy'."""
    from sqlalchemy import text
    from sqlalchemy.orm import sessionmaker

    from app.tasks_db import init_tasks_db
    from app.tasks_models import TimeBlock
    import app.tasks_db as tasks_db_module

    db_file = tmp_path / "tasks_phase2.db"
    engine = create_engine(f"sqlite:///{db_file}", connect_args={"check_same_thread": False})
    with engine.begin() as conn:
        conn.execute(
            text(
                'CREATE TABLE time_block (id INTEGER PRIMARY KEY, title VARCHAR(512),'
                ' start DATETIME, "end" DATETIME, source VARCHAR(32),'
                ' external_id VARCHAR(64), created_at DATETIME)'
            )
        )
        conn.execute(
            text(
                'INSERT INTO time_block (title, start, "end", source)'
                " VALUES ('Réunion héritée', '2026-07-02 13:00:00', '2026-07-02 14:00:00', 'manual')"
            )
        )

    original_engine = tasks_db_module.tasks_engine
    original_session_local = tasks_db_module.TasksSessionLocal
    tasks_db_module.tasks_engine = engine
    tasks_db_module.TasksSessionLocal = sessionmaker(bind=engine, expire_on_commit=False)
    try:
        init_tasks_db()
        columns = {col["name"] for col in inspect(engine).get_columns("time_block")}
        assert "kind" in columns
        with sessionmaker(bind=engine)() as db:
            block = db.query(TimeBlock).one()
            assert block.title == "Réunion héritée"  # donnée intacte
            assert block.kind == "busy"  # défaut appliqué
    finally:
        tasks_db_module.tasks_engine = original_engine
        tasks_db_module.TasksSessionLocal = original_session_local
        engine.dispose()


def test_init_tasks_db_migrates_time_block_recurrence(tmp_path: Path) -> None:
    """Base pré-phase-13 : une table time_block sans colonne `recurrence` reçoit le
    défaut '' (bloc ponctuel), sans perte du bloc déjà enregistré."""
    from sqlalchemy import text
    from sqlalchemy.orm import sessionmaker

    from app.tasks_db import init_tasks_db
    from app.tasks_models import TimeBlock
    import app.tasks_db as tasks_db_module

    db_file = tmp_path / "tasks_pre_phase13.db"
    engine = create_engine(f"sqlite:///{db_file}", connect_args={"check_same_thread": False})
    with engine.begin() as conn:
        conn.execute(
            text(
                'CREATE TABLE time_block (id INTEGER PRIMARY KEY, title VARCHAR(512),'
                ' start DATETIME, "end" DATETIME, source VARCHAR(32),'
                " kind VARCHAR(16) DEFAULT 'busy',"
                ' external_id VARCHAR(64), created_at DATETIME)'
            )
        )
        conn.execute(
            text(
                'INSERT INTO time_block (title, start, "end", source, kind)'
                " VALUES ('Focus hérité', '2026-07-02 09:00:00', '2026-07-02 11:00:00',"
                " 'manual', 'deepwork')"
            )
        )

    original_engine = tasks_db_module.tasks_engine
    original_session_local = tasks_db_module.TasksSessionLocal
    tasks_db_module.tasks_engine = engine
    tasks_db_module.TasksSessionLocal = sessionmaker(bind=engine, expire_on_commit=False)
    try:
        init_tasks_db()
        columns = {col["name"] for col in inspect(engine).get_columns("time_block")}
        assert "recurrence" in columns
        with sessionmaker(bind=engine)() as db:
            block = db.query(TimeBlock).one()
            assert block.title == "Focus hérité"  # donnée intacte
            assert block.kind == "deepwork"       # colonne pré-existante intacte
            assert block.recurrence == ""         # défaut appliqué
    finally:
        tasks_db_module.tasks_engine = original_engine
        tasks_db_module.TasksSessionLocal = original_session_local
        engine.dispose()


def test_init_tasks_db_migrates_phase3_schema_without_data_loss(tmp_path: Path) -> None:
    """Base phase 3 peuplée (colonnes phase 1/2, sans les colonnes phase 4) → migrée
    intacte. Phase 3 n'a ajouté aucune colonne à `task` (seulement de nouvelles
    tables `task_dependency`/`work_session` et `time_block.kind`) : le schéma phase 2
    de `task` est donc identique au schéma « phase 3 » pour cette table."""
    from sqlalchemy import text
    from sqlalchemy.orm import sessionmaker

    from app.tasks_db import init_tasks_db
    from app.tasks_models import Task
    import app.tasks_db as tasks_db_module

    db_file = tmp_path / "tasks_phase3.db"
    engine = create_engine(f"sqlite:///{db_file}", connect_args={"check_same_thread": False})
    with engine.begin() as conn:
        conn.execute(
            text(
                "CREATE TABLE task ("
                " id INTEGER PRIMARY KEY, title VARCHAR(512), description TEXT,"
                " priority INTEGER, deadline DATE, project_tag VARCHAR(255),"
                " status VARCHAR(16), source VARCHAR(32), external_id VARCHAR(64),"
                " estimated_minutes INTEGER, pinned_start DATETIME, parent_id INTEGER,"
                " recurrence VARCHAR(16) DEFAULT '',"
                " created_at DATETIME, updated_at DATETIME,"
                " CONSTRAINT uq_task_source_external_id UNIQUE (source, external_id))"
            )
        )
        conn.execute(
            text(
                "INSERT INTO task (title, priority, status, source, recurrence)"
                " VALUES ('Tâche phase 3', 0, 'todo', 'native', 'weekly')"
            )
        )

    original_engine = tasks_db_module.tasks_engine
    original_session_local = tasks_db_module.TasksSessionLocal
    tasks_db_module.tasks_engine = engine
    tasks_db_module.TasksSessionLocal = sessionmaker(bind=engine, expire_on_commit=False)
    try:
        init_tasks_db()
        columns = {col["name"] for col in inspect(engine).get_columns("task")}
        assert {"scheduled_date", "recurrence_day_of_month", "recurrence_period"} <= columns

        with sessionmaker(bind=engine)() as db:
            task = db.query(Task).one()
            assert task.title == "Tâche phase 3"
            assert task.priority == 0  # données antérieures intactes
            assert task.recurrence == "weekly"
            assert task.scheduled_date is None
            assert task.recurrence_period in ("", None)  # défaut SQL appliqué
    finally:
        tasks_db_module.tasks_engine = original_engine
        tasks_db_module.TasksSessionLocal = original_session_local
        engine.dispose()


def test_init_tasks_db_migrates_phase4_schema_without_data_loss(tmp_path: Path) -> None:
    """Base phase 4 peuplée (colonnes jusqu'à `scheduled_date`/récurrence
    calendaire, sans `task_type`/`fibonacci_points`) → migrée intacte."""
    from sqlalchemy import text
    from sqlalchemy.orm import sessionmaker

    from app.tasks_db import init_tasks_db
    from app.tasks_models import Task
    import app.tasks_db as tasks_db_module

    db_file = tmp_path / "tasks_phase4.db"
    engine = create_engine(f"sqlite:///{db_file}", connect_args={"check_same_thread": False})
    with engine.begin() as conn:
        conn.execute(
            text(
                "CREATE TABLE task ("
                " id INTEGER PRIMARY KEY, title VARCHAR(512), description TEXT,"
                " priority INTEGER, deadline DATE, project_tag VARCHAR(255),"
                " status VARCHAR(16), source VARCHAR(32), external_id VARCHAR(64),"
                " estimated_minutes INTEGER, pinned_start DATETIME, parent_id INTEGER,"
                " recurrence VARCHAR(16) DEFAULT '',"
                " scheduled_date DATE, recurrence_day_of_month INTEGER,"
                " recurrence_period VARCHAR(16) DEFAULT '',"
                " created_at DATETIME, updated_at DATETIME,"
                " CONSTRAINT uq_task_source_external_id UNIQUE (source, external_id))"
            )
        )
        conn.execute(
            text(
                "INSERT INTO task (title, priority, status, source, scheduled_date)"
                " VALUES ('Tâche phase 4', 1, 'todo', 'native', '2026-07-06')"
            )
        )

    original_engine = tasks_db_module.tasks_engine
    original_session_local = tasks_db_module.TasksSessionLocal
    tasks_db_module.tasks_engine = engine
    tasks_db_module.TasksSessionLocal = sessionmaker(bind=engine, expire_on_commit=False)
    try:
        init_tasks_db()
        columns = {col["name"] for col in inspect(engine).get_columns("task")}
        assert {"task_type", "fibonacci_points"} <= columns

        with sessionmaker(bind=engine)() as db:
            task = db.query(Task).one()
            assert task.title == "Tâche phase 4"
            assert task.priority == 1  # données antérieures intactes
            assert task.scheduled_date.isoformat() == "2026-07-06"
            assert task.task_type == ""  # défaut SQL appliqué
            assert task.fibonacci_points is None
    finally:
        tasks_db_module.tasks_engine = original_engine
        tasks_db_module.TasksSessionLocal = original_session_local
        engine.dispose()


def test_init_tasks_db_migrates_phase5_schema_without_data_loss(tmp_path: Path) -> None:
    """Base phase 5 peuplée (colonnes jusqu'à `task_type`/`fibonacci_points`, sans
    `linked_ticket_id`) → migrée intacte, y compris une tâche GitLab synchronisée
    sous l'ancien format d'`external_id` (préparation X1 : le rekey côté
    `tasks_gitlab_sync` s'appuie sur cette même base migrée)."""
    from sqlalchemy import text
    from sqlalchemy.orm import sessionmaker

    from app.tasks_db import init_tasks_db
    from app.tasks_models import Task
    import app.tasks_db as tasks_db_module

    db_file = tmp_path / "tasks_phase5.db"
    engine = create_engine(f"sqlite:///{db_file}", connect_args={"check_same_thread": False})
    with engine.begin() as conn:
        conn.execute(
            text(
                "CREATE TABLE task ("
                " id INTEGER PRIMARY KEY, title VARCHAR(512), description TEXT,"
                " priority INTEGER, deadline DATE, project_tag VARCHAR(255),"
                " status VARCHAR(16), source VARCHAR(32), external_id VARCHAR(64),"
                " estimated_minutes INTEGER, pinned_start DATETIME, parent_id INTEGER,"
                " recurrence VARCHAR(16) DEFAULT '',"
                " scheduled_date DATE, recurrence_day_of_month INTEGER,"
                " recurrence_period VARCHAR(16) DEFAULT '',"
                " task_type VARCHAR(32) DEFAULT '', fibonacci_points INTEGER,"
                " created_at DATETIME, updated_at DATETIME,"
                " CONSTRAINT uq_task_source_external_id UNIQUE (source, external_id))"
            )
        )
        conn.execute(
            text(
                "INSERT INTO task (title, priority, status, source, external_id,"
                " project_tag, task_type, fibonacci_points)"
                " VALUES ('Fiche GitLab (ancien format)', 2, 'todo', 'gitlab', '42',"
                " 'mon-groupe/mon-projet', 'dev', 5)"
            )
        )

    original_engine = tasks_db_module.tasks_engine
    original_session_local = tasks_db_module.TasksSessionLocal
    tasks_db_module.tasks_engine = engine
    tasks_db_module.TasksSessionLocal = sessionmaker(bind=engine, expire_on_commit=False)
    try:
        init_tasks_db()
        columns = {col["name"] for col in inspect(engine).get_columns("task")}
        assert "linked_ticket_id" in columns

        with sessionmaker(bind=engine)() as db:
            task = db.query(Task).one()
            assert task.title == "Fiche GitLab (ancien format)"
            assert task.priority == 2  # données antérieures intactes
            assert task.external_id == "42"  # rekey géré par tasks_gitlab_sync, pas la migration
            # Issue #7 : remappage one-shot de l'ancienne clé interne vers son libellé
            # d'origine (task_type stocke désormais directement le libellé configurable).
            assert task.task_type == "Développement"
            assert task.fibonacci_points == 5
            assert task.linked_ticket_id is None  # défaut SQL appliqué
    finally:
        tasks_db_module.tasks_engine = original_engine
        tasks_db_module.TasksSessionLocal = original_session_local
        engine.dispose()


def test_init_tasks_db_creates_tables_without_touching_ticket_db(tmp_path: Path) -> None:
    from app.tasks_db import init_tasks_db

    db_file = tmp_path / "tasks.db"
    engine = create_engine(f"sqlite:///{db_file}", connect_args={"check_same_thread": False})

    import app.tasks_db as tasks_db_module

    original_engine = tasks_db_module.tasks_engine
    original_session_local = tasks_db_module.TasksSessionLocal
    tasks_db_module.tasks_engine = engine
    tasks_db_module.TasksSessionLocal = sessionmaker(bind=engine, expire_on_commit=False)
    try:
        init_tasks_db()
        tables = set(inspect(engine).get_table_names())
        assert {"task", "time_block", "task_sync_meta"} <= tables
    finally:
        tasks_db_module.tasks_engine = original_engine
        tasks_db_module.TasksSessionLocal = original_session_local
        engine.dispose()

    assert not (tmp_path / "pilotage.db").exists()
