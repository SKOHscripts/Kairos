"""Tests du stockage des secrets (`app/secret_store.py`)."""

from __future__ import annotations

import pytest

from app import secret_store


def test_get_secret_returns_plain_fallback_when_keyring_empty(monkeypatch) -> None:
    monkeypatch.setattr(secret_store.keyring, "get_password", lambda service, field: None)
    assert secret_store.get_secret("gitlab_token", plain_fallback="from-file") == "from-file"


def test_get_secret_prefers_keyring_value(monkeypatch) -> None:
    monkeypatch.setattr(secret_store.keyring, "get_password", lambda service, field: "from-keyring")
    assert secret_store.get_secret("gitlab_token", plain_fallback="from-file") == "from-keyring"


def test_get_secret_falls_back_when_keyring_raises(monkeypatch) -> None:
    def _boom(service, field):
        raise RuntimeError("no backend")

    monkeypatch.setattr(secret_store.keyring, "get_password", _boom)
    assert secret_store.get_secret("gitlab_token", plain_fallback="from-file") == "from-file"


def test_set_secret_success_reports_stored_via_keyring(monkeypatch) -> None:
    calls = {}
    monkeypatch.setattr(
        secret_store.keyring, "set_password",
        lambda service, field, value: calls.setdefault(field, value),
    )
    stored, detail = secret_store.set_secret("gitlab_token", "tok")
    assert stored is True
    assert detail == ""
    assert calls == {"gitlab_token": "tok"}


def test_set_secret_failure_falls_back_with_detail(monkeypatch) -> None:
    def _boom(service, field, value):
        raise RuntimeError("no backend")

    monkeypatch.setattr(secret_store.keyring, "set_password", _boom)
    stored, detail = secret_store.set_secret("gitlab_token", "tok")
    assert stored is False
    assert detail  # message non vide à afficher dans l'UI


def test_set_secret_empty_value_clears_without_error(monkeypatch) -> None:
    monkeypatch.setattr(
        secret_store.keyring, "delete_password",
        lambda service, field: (_ for _ in ()).throw(RuntimeError("not found")),
    )
    stored, detail = secret_store.set_secret("gitlab_token", "")
    assert stored is True
    assert detail == ""


def test_keyring_available_false_when_fail_backend(monkeypatch) -> None:
    from keyring.backends.fail import Keyring as FailKeyring

    monkeypatch.setattr(secret_store.keyring, "get_keyring", lambda: FailKeyring())
    assert secret_store.keyring_available() is False


def test_keyring_available_true_for_a_real_backend(monkeypatch) -> None:
    class _FakeBackend:
        pass

    monkeypatch.setattr(secret_store.keyring, "get_keyring", lambda: _FakeBackend())
    assert secret_store.keyring_available() is True
