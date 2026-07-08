"""Initialisation de la base SQLite des tâches et gestion des sessions SQLAlchemy.

Mirror strict de ``app/db.py``, mais pointant sur ``Settings.tasks_database_url`` :
un fichier SQLite séparé de celui du suivi dette technique (``pilotage.db``).
"""

from __future__ import annotations

from collections.abc import Iterator
from contextlib import contextmanager

from sqlalchemy import create_engine, inspect, text
from sqlalchemy.orm import Session, sessionmaker

from .config import get_settings
from .tasks_models import TasksBase

_settings = get_settings()
tasks_engine = create_engine(
    _settings.tasks_database_url,
    connect_args={"check_same_thread": False},
    future=True,
)
TasksSessionLocal = sessionmaker(
    bind=tasks_engine, autoflush=False, expire_on_commit=False
)

# Colonnes ajoutées après coup, par table : {table: {colonne: type SQL}}. Même
# mécanisme que `db.py` : `create_all` ne modifie pas une table existante, or la
# base tâches d'un utilisateur de la phase 1 contient des données réelles (tâches
# importées, priorités posées) — on complète donc le schéma sans jamais recréer.
_TASKS_MIGRATION_COLUMNS: dict[str, dict[str, str]] = {
    "task": {
        # Phase 2 (time blocking, hiérarchie, récurrence).
        "estimated_minutes": "INTEGER",
        "pinned_start": "DATETIME",
        "parent_id": "INTEGER",
        "recurrence": "VARCHAR(16) DEFAULT ''",
        # Phase 4 : date programmée, récurrence calendaire.
        "scheduled_date": "DATE",
        "recurrence_day_of_month": "INTEGER",
        "recurrence_period": "VARCHAR(16) DEFAULT ''",
        # Phase 5 : métadonnées pures de préparation d'analyses futures.
        "task_type": "VARCHAR(32) DEFAULT ''",
        "fibonacci_points": "INTEGER",
        # Phase 6 : liaison manuelle en lecture vers une fiche `Ticket`.
        "linked_ticket_id": "INTEGER",
    },
    "time_block": {
        # Phase 3 : distingue les créneaux occupés des blocs deep-work protégés.
        "kind": "VARCHAR(16) DEFAULT 'busy'",
        # Phase 13 : blocs récurrents (bloc déjeuner quotidien, deep-work hebdomadaire).
        "recurrence": "VARCHAR(16) DEFAULT ''",
    },
}


def _ensure_tasks_columns() -> None:
    """Ajoute les colonnes manquantes sur une base tâches existante (migration légère)."""
    inspector = inspect(tasks_engine)
    tables = set(inspector.get_table_names())
    for table, columns in _TASKS_MIGRATION_COLUMNS.items():
        if table not in tables:
            continue
        existing = {col["name"] for col in inspector.get_columns(table)}
        missing = {n: ddl for n, ddl in columns.items() if n not in existing}
        if missing:
            with tasks_engine.begin() as conn:
                for name, ddl in missing.items():
                    conn.execute(
                        text(f"ALTER TABLE {table} ADD COLUMN {name} {ddl}")
                    )

    if "task" in tables:
        # Correctif de données phase 1 : external_id="" sur les tâches natives entrait
        # en collision avec la contrainte unique (source, external_id) dès la deuxième
        # tâche native. Le modèle utilise désormais NULL (distinct au sens d'UNIQUE).
        with tasks_engine.begin() as conn:
            conn.execute(
                text(
                    "UPDATE task SET external_id = NULL"
                    " WHERE source = 'native' AND external_id = ''"
                )
            )
        # Retrait de l'intégration Superproductivity (réseau pro incompatible) :
        # toute tâche déjà synchronisée devient native une fois pour toutes, sans
        # jamais être recréée ni dupliquée. `external_id` est conservé comme trace
        # d'origine — même geste que l'ancien bouton « Adopter les tâches SP ».
        with tasks_engine.begin() as conn:
            conn.execute(
                text("UPDATE task SET source = 'native' WHERE source = 'superproductivity'")
            )
    if "task_sync_meta" in tables:
        with tasks_engine.begin() as conn:
            conn.execute(
                text("DELETE FROM task_sync_meta WHERE source = 'superproductivity'")
            )


def init_tasks_db() -> None:
    """Crée les tables si elles n'existent pas, puis applique la migration légère."""
    TasksBase.metadata.create_all(bind=tasks_engine)
    _ensure_tasks_columns()


@contextmanager
def tasks_session_scope() -> Iterator[Session]:
    """Fournit une session transactionnelle (commit/rollback automatiques)."""
    session = TasksSessionLocal()
    try:
        yield session
        session.commit()
    except Exception:
        session.rollback()
        raise
    finally:
        session.close()


def get_tasks_session() -> Iterator[Session]:
    """Dépendance FastAPI : une session (base tâches) par requête."""
    session = TasksSessionLocal()
    try:
        yield session
    finally:
        session.close()
