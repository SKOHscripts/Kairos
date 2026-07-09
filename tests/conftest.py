"""Fixtures partagées : bases SQLite en mémoire isolées par test."""

from __future__ import annotations

from pathlib import Path

import pytest
from sqlalchemy import create_engine
from sqlalchemy.orm import Session, sessionmaker

from app import settings_store
from app.pilotage_link import PilotageBase
from app.tasks_models import TasksBase


@pytest.fixture
def tasks_session() -> Session:
    """Session sur la base tâches en mémoire."""
    engine = create_engine(
        "sqlite:///:memory:", connect_args={"check_same_thread": False}
    )
    TasksBase.metadata.create_all(engine)
    factory = sessionmaker(bind=engine, expire_on_commit=False)
    db = factory()
    try:
        yield db
    finally:
        db.close()
        engine.dispose()


@pytest.fixture
def tmp_settings_dir(tmp_path: Path, monkeypatch) -> Path:
    """Isole `settings_store` (et donc `secret_store`/`config`) dans un dossier
    temporaire : jamais de test qui touche le vrai `~/.local/share/Kairos/`."""
    monkeypatch.setattr(settings_store, "data_dir", lambda: tmp_path)
    monkeypatch.setattr(settings_store, "settings_path", lambda: tmp_path / "settings.json")
    return tmp_path


@pytest.fixture
def pilotage_session() -> Session:
    """Session sur une base pilotage simulée (projections lecture seule), isolée."""
    engine = create_engine(
        "sqlite:///:memory:", connect_args={"check_same_thread": False}
    )
    PilotageBase.metadata.create_all(engine)  # dans les tests uniquement
    factory = sessionmaker(bind=engine, expire_on_commit=False)
    db = factory()
    try:
        yield db
    finally:
        db.close()
        engine.dispose()
