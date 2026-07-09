"""Tests du stockage des réglages (`app/settings_store.py`)."""

from __future__ import annotations

import os

from app import settings_store
from app.config import Settings


def test_load_without_file_returns_defaults(tmp_settings_dir) -> None:
    settings = settings_store.load()
    assert settings.workday_start_hour == 9
    assert not settings_store.settings_path().exists()


def test_save_then_load_round_trip(tmp_settings_dir) -> None:
    settings = Settings(workday_start_hour=8, workday_end_hour=17, log_level="DEBUG")
    settings_store.save(settings)
    loaded = settings_store.load()
    assert loaded.workday_start_hour == 8
    assert loaded.workday_end_hour == 17
    assert loaded.log_level == "DEBUG"


def test_save_writes_atomically_no_tmp_file_left(tmp_settings_dir) -> None:
    settings_store.save(Settings())
    assert settings_store.settings_path().exists()
    assert not settings_store.settings_path().with_suffix(".json.tmp").exists()


def test_save_secret_without_keyring_falls_back_to_plain_file(tmp_settings_dir, monkeypatch) -> None:
    monkeypatch.setattr(
        "app.secret_store.keyring.set_password",
        lambda *a, **k: (_ for _ in ()).throw(RuntimeError("no backend")),
    )
    warnings = settings_store.save(Settings(gitlab_token="secret-tok"))
    assert "gitlab_token" in warnings
    raw = settings_store.settings_path().read_text(encoding="utf-8")
    assert "secret-tok" in raw  # replié en clair, comportement attendu sans trousseau
    loaded = settings_store.load()
    assert loaded.gitlab_token == "secret-tok"


def test_save_secret_never_written_to_json_when_keyring_succeeds(tmp_settings_dir, monkeypatch) -> None:
    stored = {}
    monkeypatch.setattr(
        "app.secret_store.keyring.set_password",
        lambda service, field, value: stored.__setitem__(field, value),
    )
    monkeypatch.setattr(
        "app.secret_store.keyring.get_password",
        lambda service, field: stored.get(field),
    )
    settings_store.save(Settings(gitlab_token="secret-tok"))
    raw = settings_store.settings_path().read_text(encoding="utf-8")
    assert "secret-tok" not in raw
    loaded = settings_store.load()
    assert loaded.gitlab_token == "secret-tok"


def test_unknown_keys_in_file_are_ignored(tmp_settings_dir) -> None:
    settings_store.save(Settings())
    path = settings_store.settings_path()
    import json

    envelope = json.loads(path.read_text(encoding="utf-8"))
    envelope["settings"]["a_field_removed_in_a_later_version"] = "whatever"
    path.write_text(json.dumps(envelope), encoding="utf-8")
    loaded = settings_store.load()  # extra="ignore" : ne doit pas lever
    assert loaded.workday_start_hour == 9


def test_corrupted_file_degrades_to_defaults(tmp_settings_dir) -> None:
    settings_store.settings_path().write_text("{not valid json", encoding="utf-8")
    loaded = settings_store.load()
    assert loaded.workday_start_hour == 9


def test_migrate_legacy_env_if_needed(tmp_settings_dir, tmp_path, monkeypatch) -> None:
    env_file = tmp_path / ".env"
    env_file.write_text(
        "\n".join(
            [
                "# commentaire",
                "TASKS_DATABASE_PATH=custom.db",
                "WORKDAY_START_HOUR=8",
                "WORKDAY_END_HOUR=17",
                "COGNITIVE_DIP_ENABLED=false",
                "GITLAB_TOKEN=legacy-tok",
                "",
            ]
        ),
        encoding="utf-8",
    )
    monkeypatch.chdir(tmp_path)

    settings = settings_store.load()

    assert settings.tasks_database_path == "custom.db"
    assert settings.workday_start_hour == 8
    assert settings.cognitive_dip_enabled is False
    assert settings.gitlab_token == "legacy-tok"
    assert settings_store.meta()["migrated_from_env_path"] == str(env_file)


def test_migration_runs_only_once(tmp_settings_dir, tmp_path, monkeypatch) -> None:
    env_file = tmp_path / ".env"
    env_file.write_text("WORKDAY_START_HOUR=8\n", encoding="utf-8")
    monkeypatch.chdir(tmp_path)

    settings_store.load()
    env_file.write_text("WORKDAY_START_HOUR=6\n", encoding="utf-8")  # ignoré : déjà migré
    settings = settings_store.load()

    assert settings.workday_start_hour == 8


def test_migration_never_deletes_legacy_env(tmp_settings_dir, tmp_path, monkeypatch) -> None:
    env_file = tmp_path / ".env"
    env_file.write_text("LOG_LEVEL=DEBUG\n", encoding="utf-8")
    monkeypatch.chdir(tmp_path)

    settings_store.load()

    assert env_file.exists()


def test_no_legacy_env_returns_defaults_without_writing_file(tmp_settings_dir, tmp_path, monkeypatch) -> None:
    monkeypatch.chdir(tmp_path)
    settings = settings_store.load()
    assert settings.workday_start_hour == 9
    assert not settings_store.settings_path().exists()
