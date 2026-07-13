"""Tests des adaptations Android : ancrage des données et point d'entrée serveur.

Le projet Gradle (`android/`) n'est constructible qu'avec le SDK Android (CI) ;
ici on teste la partie Python : les overrides d'environnement et `prepare`.
"""

from __future__ import annotations

from pathlib import Path

from app import settings_store
from app.android_launcher import _pick_port, prepare
from app.config import _default_tasks_database_path
from app.main import _resolve_base_dir


def test_data_dir_honors_kairos_data_dir(tmp_path, monkeypatch) -> None:
    target = tmp_path / "portable"
    monkeypatch.setenv("KAIROS_DATA_DIR", str(target))
    assert settings_store.data_dir() == target
    assert target.is_dir()  # créé si besoin, comme le chemin platformdirs


def test_default_tasks_database_path_honors_kairos_data_dir(tmp_path, monkeypatch) -> None:
    monkeypatch.setenv("KAIROS_DATA_DIR", str(tmp_path))
    assert _default_tasks_database_path() == str(tmp_path / "tasks.db")


def test_resolve_base_dir_honors_kairos_base_dir(tmp_path, monkeypatch) -> None:
    monkeypatch.setenv("KAIROS_BASE_DIR", str(tmp_path))
    assert _resolve_base_dir() == tmp_path


def test_resolve_base_dir_defaults_to_repo_root(monkeypatch) -> None:
    monkeypatch.delenv("KAIROS_BASE_DIR", raising=False)
    root = _resolve_base_dir()
    assert (root / "templates").is_dir() and (root / "static").is_dir()


def test_prepare_sets_data_dir_and_returns_port(tmp_path, monkeypatch) -> None:
    monkeypatch.delenv("KAIROS_DATA_DIR", raising=False)
    port = prepare(str(tmp_path))
    assert isinstance(port, int) and 1024 < port < 65536
    data_dir = Path(tmp_path) / "kairos-data"
    import os

    assert os.environ["KAIROS_DATA_DIR"] == str(data_dir)


def test_pick_port_skips_busy_port() -> None:
    import socket

    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as sock:
        sock.bind(("127.0.0.1", 0))
        sock.listen(1)
        busy = sock.getsockname()[1]
        assert _pick_port(preferred=busy, tries=5) != busy
