"""Génère de vraies bases Kairos 2 pour tester la migration de Kairos 3
(docs/spec-v3/migration-2x.md § Tests).

Deux générations de schéma, écrites dans `kmp/desktopApp/src/jvmTest/resources/legacy/` :

- `current/` : `tasks.db` créée par les modèles SQLAlchemy de Kairos 2
  (`app/tasks_models.py`, schéma final) et son `settings.json` ; on y met tous
  les cas de conversion (tâche GitLab, créneau TimeTree, ancienne clé de type,
  valeurs hors échelle, dépendance orpheline, chrono en cours, note convertie) ;
- `phase1/` : le schéma de la phase 1 (avant les colonnes ajoutées par
  `_ensure_tasks_columns`), sans tables de notes, sessions ni dépendances, et
  sans `settings.json`.

À relancer seulement si ce script change (les bases sont commitées) :

    python kmp/tools/gen_legacy_db.py
"""
from __future__ import annotations

import json
import sqlite3
import sys
from dataclasses import asdict
from datetime import date, datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT))

from sqlalchemy import create_engine  # noqa: E402
from sqlalchemy.orm import Session  # noqa: E402

from app.config import Settings  # noqa: E402
from app.tasks_models import Note, Task, TaskDependency, TaskSyncMeta, TasksBase, TimeBlock, WorkSession  # noqa: E402

OUT = ROOT / "kmp/desktopApp/src/jvmTest/resources/legacy"


def utc(y, mo, d, h=0, mi=0):
    return datetime(y, mo, d, h, mi, tzinfo=timezone.utc)


def current() -> None:
    folder = OUT / "current"
    folder.mkdir(parents=True, exist_ok=True)
    db = folder / "tasks.db"
    db.unlink(missing_ok=True)
    engine = create_engine(f"sqlite:///{db}", future=True)
    TasksBase.metadata.create_all(engine)
    with Session(engine) as s:
        s.add_all([
            Task(id=1, title="Corriger le bug de connexion", description="Voir le ticket\nsur deux lignes",
                 priority=0, fibonacci_points=5, estimated_minutes=45, task_type="Développement",
                 deadline=date(2026, 10, 2), project_tag="Kairos",
                 created_at=utc(2026, 9, 1, 8), updated_at=utc(2026, 9, 20, 9, 15)),
            Task(id=2, title="Issue GitLab importée", source="gitlab", external_id="123", project_tag="infra",
                 status="done", priority=1, fibonacci_points=3, task_type="dev",
                 created_at=utc(2026, 8, 1), updated_at=utc(2026, 9, 25, 16)),
            Task(id=3, title="Point hebdo", parent_id=1, recurrence="weekly", recurrence_day_of_week=2,
                 pinned_start=datetime(2026, 9, 29, 14, 30), priority=2, fibonacci_points=1,
                 scheduled_date=date(2026, 9, 29), manual_time_spent_minutes=20,
                 created_at=utc(2026, 9, 2), updated_at=utc(2026, 9, 2)),
            Task(id=4, title="Note de frais", status="archived", recurrence="monthly_on_day",
                 recurrence_day_of_month=23, recurrence_period="2026-09", priority=1, fibonacci_points=2,
                 created_at=utc(2026, 9, 3), updated_at=utc(2026, 9, 23)),
            Task(id=5, title="Valeurs hors échelle", priority=5, fibonacci_points=4, estimated_minutes=0,
                 created_at=utc(2026, 9, 4), updated_at=utc(2026, 9, 4)),
            TimeBlock(id=1, title="Deep work", start=datetime(2026, 9, 29, 10), end=datetime(2026, 9, 29, 11, 30),
                      kind="deepwork", recurrence="weekly", created_at=utc(2026, 9, 1)),
            TimeBlock(id=2, title="Réunion TimeTree", source="timetree", external_id="tt-1",
                      start=datetime(2026, 9, 29, 13), end=datetime(2026, 9, 29, 14), created_at=utc(2026, 9, 1)),
            TaskDependency(id=1, task_id=1, blocker_id=3, created_at=utc(2026, 9, 5)),
            TaskDependency(id=2, task_id=1, blocker_id=99, created_at=utc(2026, 9, 5)),
            WorkSession(id=1, task_id=1, started_at=utc(2026, 9, 28, 8), ended_at=utc(2026, 9, 28, 9, 30),
                        created_at=utc(2026, 9, 28, 8)),
            WorkSession(id=2, task_id=3, started_at=utc(2026, 9, 29, 7), ended_at=None, created_at=utc(2026, 9, 29, 7)),
            Note(id=1, body="Idée : automatiser le rapport", created_at=utc(2026, 9, 10), updated_at=utc(2026, 9, 10)),
            Note(id=2, body="Issue GitLab importée", status="archived", converted_task_id=2,
                 created_at=utc(2026, 7, 30), updated_at=utc(2026, 8, 1)),
            TaskSyncMeta(id=1, source="gitlab", item_count=1),
        ])
        s.commit()
    settings = asdict(Settings(tasks_database_path=str(db)))
    settings.update(workday_start_hour=8, priority_value_base=2.5, cognitive_dip_enabled=False,
                    task_types="Dev,Ops", extra_holidays="2026-12-24", gitlab_token="secret-never-imported",
                    timetree_email="moi@example.com")
    (folder / "settings.json").write_text(
        json.dumps({"settings": settings, "meta": {}}, indent=2, ensure_ascii=False, sort_keys=True), encoding="utf-8")


def phase1() -> None:
    folder = OUT / "phase1"
    folder.mkdir(parents=True, exist_ok=True)
    db = folder / "tasks.db"
    db.unlink(missing_ok=True)
    con = sqlite3.connect(db)
    con.executescript("""
        CREATE TABLE task (id INTEGER PRIMARY KEY, title VARCHAR(512), description TEXT, priority INTEGER,
            deadline DATE, project_tag VARCHAR(255), status VARCHAR(16), source VARCHAR(32),
            external_id VARCHAR(64), created_at DATETIME, updated_at DATETIME);
        CREATE TABLE time_block (id INTEGER PRIMARY KEY, title VARCHAR(512), start DATETIME, "end" DATETIME,
            source VARCHAR(32), external_id VARCHAR(64), created_at DATETIME);
        CREATE TABLE task_sync_meta (id INTEGER PRIMARY KEY, source VARCHAR(32), last_synced_at DATETIME,
            last_outcome VARCHAR(16), last_detail TEXT, item_count INTEGER);
        INSERT INTO task VALUES (1, 'Ancienne tâche', '', 1, '2026-03-02', 'Pilotage', 'todo', 'native', NULL,
            '2026-02-01 08:00:00.000000', '2026-02-02 09:00:00.000000');
        INSERT INTO task VALUES (2, 'Issue GitLab', '', NULL, NULL, 'infra', 'done', 'gitlab', '42',
            '2026-02-01 08:00:00.000000', '2026-02-03 10:00:00.000000');
        INSERT INTO time_block VALUES (1, 'Réunion', '2026-02-02 13:00:00.000000', '2026-02-02 14:00:00.000000',
            'manual', '', '2026-02-01 08:00:00.000000');
        INSERT INTO time_block VALUES (2, 'TimeTree', '2026-02-02 15:00:00.000000', '2026-02-02 16:00:00.000000',
            'timetree', 'x', '2026-02-01 08:00:00.000000');
    """)
    con.commit()
    con.close()


if __name__ == "__main__":
    current()
    phase1()
    print(f"Écrit : {OUT}")
