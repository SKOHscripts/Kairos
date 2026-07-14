"""Tests du flux OAuth PKCE Google Calendar (appels HTTP mockés, respx).

Symétrique de `test_gitlab_direct.py` : dégradation propre (jamais d'exception),
aucun appel réseau réel.
"""

from __future__ import annotations

import httpx
import pytest
import respx

import app.calendar.google_oauth as google_oauth
from app.calendar.google_oauth import (
    build_authorize_url,
    exchange_code_for_tokens,
    refresh_access_token,
)
from app.config import Settings


@pytest.fixture(autouse=True)
def _clear_pending():
    google_oauth._pending.clear()
    yield
    google_oauth._pending.clear()


def _settings(**overrides) -> Settings:
    defaults = dict(google_client_id="client-id", google_client_secret="client-secret")
    defaults.update(overrides)
    return Settings(**defaults)


def test_build_authorize_url_contains_pkce_challenge_and_registers_state() -> None:
    url = build_authorize_url("http://127.0.0.1:8000/kairos/settings/google/callback", settings=_settings())

    assert url.startswith("https://accounts.google.com/o/oauth2/v2/auth?")
    assert "code_challenge=" in url
    assert "code_challenge_method=S256" in url
    assert "client_id=client-id" in url
    assert len(google_oauth._pending) == 1


@respx.mock
def test_exchange_code_for_tokens_success() -> None:
    respx.post("https://oauth2.googleapis.com/token").mock(
        return_value=httpx.Response(200, json={"access_token": "at", "refresh_token": "rt"})
    )
    settings = _settings()
    url = build_authorize_url("http://127.0.0.1:8000/callback", settings=settings)
    state = url.split("state=")[1].split("&")[0]

    result = exchange_code_for_tokens("auth-code", state, "http://127.0.0.1:8000/callback", settings=settings)

    assert result.ok is True
    assert result.refresh_token == "rt"
    assert result.access_token == "at"
    assert state not in google_oauth._pending  # usage unique


def test_exchange_code_for_tokens_rejects_unknown_state() -> None:
    result = exchange_code_for_tokens("auth-code", "unknown-state", "http://127.0.0.1:8000/callback", settings=_settings())

    assert result.ok is False
    assert "expirée" in result.detail


@respx.mock
def test_exchange_code_for_tokens_without_refresh_token_degrades_cleanly() -> None:
    """Google n'émet un refresh_token que sur un premier consentement — sans
    lui, l'intégration ne peut pas fonctionner en tâche de fond : traité comme
    un échec explicite plutôt qu'un jeton d'accès à durée de vie éphémère."""
    respx.post("https://oauth2.googleapis.com/token").mock(
        return_value=httpx.Response(200, json={"access_token": "at"})
    )
    settings = _settings()
    url = build_authorize_url("http://127.0.0.1:8000/callback", settings=settings)
    state = url.split("state=")[1].split("&")[0]

    result = exchange_code_for_tokens("auth-code", state, "http://127.0.0.1:8000/callback", settings=settings)

    assert result.ok is False
    assert "rafraîchissement" in result.detail


@respx.mock
def test_exchange_code_for_tokens_degrades_on_http_error() -> None:
    respx.post("https://oauth2.googleapis.com/token").mock(return_value=httpx.Response(400))
    settings = _settings()
    url = build_authorize_url("http://127.0.0.1:8000/callback", settings=settings)
    state = url.split("state=")[1].split("&")[0]

    result = exchange_code_for_tokens("auth-code", state, "http://127.0.0.1:8000/callback", settings=settings)

    assert result.ok is False


def test_refresh_access_token_not_connected_degrades_cleanly() -> None:
    result = refresh_access_token(_settings())

    assert result.ok is False
    assert "non connecté" in result.detail


@respx.mock
def test_refresh_access_token_success() -> None:
    respx.post("https://oauth2.googleapis.com/token").mock(
        return_value=httpx.Response(200, json={"access_token": "new-at"})
    )
    settings = _settings(google_refresh_token="rt")

    result = refresh_access_token(settings)

    assert result.ok is True
    assert result.access_token == "new-at"


@respx.mock
def test_refresh_access_token_degrades_on_http_error() -> None:
    respx.post("https://oauth2.googleapis.com/token").mock(return_value=httpx.Response(401))
    settings = _settings(google_refresh_token="revoked")

    result = refresh_access_token(settings)

    assert result.ok is False
    assert "Échec du rafraîchissement" in result.detail
