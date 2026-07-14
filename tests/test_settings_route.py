"""Tests de la page Réglages (`/kairos/settings`)."""

from __future__ import annotations

import httpx
import pytest
import respx
from starlette.testclient import TestClient

from app import main
from app.calendar import google_oauth
from app.config import get_settings


@pytest.fixture(autouse=True)
def _clear_google_oauth_pending():
    google_oauth._pending.clear()
    yield
    google_oauth._pending.clear()


@pytest.fixture
def settings_client(tmp_settings_dir):
    """Isole le stockage des réglages (voir `tmp_settings_dir`) et vide le cache
    `lru_cache` de `get_settings()` avant/après chaque test, pour ne jamais lire
    un état laissé par un test précédent (ou le vrai poste de dev)."""
    get_settings.cache_clear()
    try:
        yield TestClient(main.app)
    finally:
        get_settings.cache_clear()


def test_get_settings_page_returns_200_with_current_values(settings_client) -> None:
    resp = settings_client.get("/kairos/settings")
    assert resp.status_code == 200
    assert "Réglages" in resp.text
    assert 'value="9"' in resp.text  # workday_start_hour par défaut


def test_post_valid_settings_persists_and_redirects(settings_client) -> None:
    resp = settings_client.post(
        "/kairos/settings",
        data=_full_form(workday_start_hour="8", workday_end_hour="17"),
        follow_redirects=False,
    )
    assert resp.status_code == 303
    assert resp.headers["location"] == "/kairos/settings?saved=1"
    assert get_settings().workday_start_hour == 8
    assert get_settings().workday_end_hour == 17


def test_post_valid_settings_persists_custom_task_types(settings_client) -> None:
    """Issue #7 : la liste des types (CSV, même patron que `gitlab_projects`) est
    librement éditable depuis la page Réglages."""
    resp = settings_client.post(
        "/kairos/settings",
        data=_full_form(task_types="Coaching, Support client"),
        follow_redirects=False,
    )
    assert resp.status_code == 303
    assert get_settings().task_type_list == ["Coaching", "Support client"]


def test_post_invalid_settings_rerenders_with_error_and_does_not_save(settings_client) -> None:
    resp = settings_client.post(
        "/kairos/settings",
        data=_full_form(workday_start_hour="20", workday_end_hour="9"),
        follow_redirects=False,
    )
    assert resp.status_code == 200
    assert "doit être avant" in resp.text
    assert get_settings().workday_start_hour == 9  # valeur par défaut, jamais sauvegardée


def test_post_non_numeric_value_rerenders_with_error(settings_client) -> None:
    resp = settings_client.post(
        "/kairos/settings",
        data=_full_form(workday_start_hour="pas-un-nombre"),
        follow_redirects=False,
    )
    assert resp.status_code == 200
    assert "invalide" in resp.text


def test_secret_left_blank_keeps_previous_value(settings_client, monkeypatch) -> None:
    _use_fake_keyring(monkeypatch)

    settings_client.post(
        "/kairos/settings", data=_full_form(gitlab_token="first-token"), follow_redirects=False
    )
    assert get_settings().gitlab_token == "first-token"

    get_settings.cache_clear()
    settings_client.post(
        "/kairos/settings", data=_full_form(gitlab_token=""), follow_redirects=False
    )
    assert get_settings().gitlab_token == "first-token"  # inchangé : champ laissé vide


def test_secret_clear_checkbox_blanks_value(settings_client, monkeypatch) -> None:
    _use_fake_keyring(monkeypatch)

    settings_client.post(
        "/kairos/settings", data=_full_form(gitlab_token="to-clear"), follow_redirects=False
    )
    get_settings.cache_clear()

    settings_client.post(
        "/kairos/settings",
        data=_full_form(**{"gitlab_token_clear": "on"}),
        follow_redirects=False,
    )
    assert get_settings().gitlab_token == ""


def test_secret_value_never_appears_in_rendered_html(settings_client, monkeypatch) -> None:
    _use_fake_keyring(monkeypatch)

    settings_client.post(
        "/kairos/settings", data=_full_form(gitlab_token="super-secret-value"), follow_redirects=False
    )
    get_settings.cache_clear()

    page = settings_client.get("/kairos/settings")
    assert "super-secret-value" not in page.text
    assert "défini" in page.text  # statut affiché, jamais la valeur


def test_keyring_unavailable_shows_warning_banner(settings_client, monkeypatch) -> None:
    from keyring.backends.fail import Keyring as FailKeyring

    monkeypatch.setattr("app.secret_store.keyring.get_keyring", lambda: FailKeyring())
    page = settings_client.get("/kairos/settings")
    assert "Trousseau système indisponible" in page.text


def test_google_calendar_connect_redirects_to_google(settings_client, monkeypatch) -> None:
    _use_fake_keyring(monkeypatch)
    settings_client.post(
        "/kairos/settings",
        data=_full_form(google_client_id="cid", google_client_secret="csecret"),
        follow_redirects=False,
    )
    get_settings.cache_clear()

    resp = settings_client.get("/kairos/settings/google/connect", follow_redirects=False)

    assert resp.status_code == 303
    location = resp.headers["location"]
    assert location.startswith("https://accounts.google.com/o/oauth2/v2/auth?")
    assert "client_id=cid" in location


@respx.mock
def test_google_calendar_callback_success_persists_refresh_token(settings_client, monkeypatch) -> None:
    _use_fake_keyring(monkeypatch)
    settings_client.post(
        "/kairos/settings",
        data=_full_form(google_client_id="cid", google_client_secret="csecret"),
        follow_redirects=False,
    )
    get_settings.cache_clear()
    connect_resp = settings_client.get("/kairos/settings/google/connect", follow_redirects=False)
    state = connect_resp.headers["location"].split("state=")[1].split("&")[0]
    respx.post("https://oauth2.googleapis.com/token").mock(
        return_value=httpx.Response(200, json={"access_token": "at", "refresh_token": "rt"})
    )

    resp = settings_client.get(
        "/kairos/settings/google/callback", params={"code": "auth-code", "state": state},
    )

    assert resp.status_code == 200
    assert "connecté avec succès" in resp.text
    get_settings.cache_clear()
    assert get_settings().google_refresh_token == "rt"


def test_google_calendar_callback_shows_error_from_google(settings_client) -> None:
    resp = settings_client.get("/kairos/settings/google/callback", params={"error": "access_denied"})

    assert resp.status_code == 200
    assert "Échec de la connexion" in resp.text
    assert "access_denied" in resp.text


def test_google_calendar_callback_rejects_unknown_state(settings_client) -> None:
    resp = settings_client.get(
        "/kairos/settings/google/callback", params={"code": "auth-code", "state": "unknown"},
    )

    assert resp.status_code == 200
    assert "Échec de la connexion" in resp.text


def _use_fake_keyring(monkeypatch) -> dict[str, str]:
    """Trousseau système simulé (dict en mémoire) — un vrai `get_password` doit
    retrouver ce qu'un `set_password` précédent a stocké, sinon les tests de
    « laisser vide = ne pas modifier » ne vérifient rien d'utile."""
    store: dict[str, str] = {}
    monkeypatch.setattr(
        "app.secret_store.keyring.set_password",
        lambda service, field, value: store.__setitem__(field, value),
    )
    monkeypatch.setattr(
        "app.secret_store.keyring.get_password",
        lambda service, field: store.get(field),
    )
    monkeypatch.setattr(
        "app.secret_store.keyring.delete_password",
        lambda service, field: store.pop(field, None),
    )
    return store


def _full_form(**overrides: str) -> dict[str, str]:
    """Un POST /kairos/settings doit fournir toutes les valeurs (le formulaire
    HTML soumet tous les champs, y compris les cases décochées absentes) — cette
    aide construit un formulaire complet à partir des défauts, avec overrides."""
    base = {
        "tasks_database_path": "tasks.db",
        "pilotage_database_path": "",
        "gitlab_assignee_username": "",
        "gitlab_url": "",
        "gitlab_token": "",
        "gitlab_projects": "",
        "gitlab_cache_ttl_minutes": "5",
        "timetree_email": "",
        "timetree_password": "",
        "timetree_calendar_code": "",
        "timetree_cache_ttl_minutes": "30",
        "google_client_id": "",
        "google_client_secret": "",
        "google_calendar_ids": "",
        "google_cache_ttl_minutes": "30",
        "default_task_duration_minutes": "30",
        "meeting_buffer_minutes": "5",
        "workday_start_hour": "9",
        "workday_end_hour": "18",
        "stale_overdue_days": "7",
        "stale_untouched_days": "14",
        "priority_overload_threshold": "5",
        "priority_value_base": "4.0",
        "urgency_horizon_days": "14",
        "urgency_peak": "8.0",
        "default_fibonacci_points": "3",
        "cognitive_dip_start_hour": "13",
        "cognitive_dip_trough_hour": "15",
        "cognitive_dip_end_hour": "16",
        "cognitive_dip_penalty": "1.0",
        "task_types": (
            "Développement,Revue de code,Réunion,Documentation,Administratif,"
            "Veille/formation,Pilotage/dette technique"
        ),
        "stats_window_weeks": "8",
        "timer_idle_alert_minutes": "180",
        "pomodoro_focus_minutes": "50",
        "extra_holidays": "",
        "log_level": "INFO",
        "http_proxy": "",
        "https_proxy": "",
        "no_proxy": "127.0.0.1,localhost",
    }
    base.update(overrides)
    return base
