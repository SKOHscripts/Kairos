"""Tests du seam Google Calendar : dégradation propre + conversion des événements
(appels HTTP mockés, respx). Symétrique de `test_timetree_source.py`."""

from __future__ import annotations

from datetime import date, datetime

import httpx
import pytest
import respx

import app.calendar.google_calendar_source as google_calendar_source
from app.calendar.google_calendar_source import fetch_busy_slots
from app.config import Settings


@pytest.fixture(autouse=True)
def _clear_cache():
    google_calendar_source._cache.clear()
    yield
    google_calendar_source._cache.clear()


def _configured_settings(**overrides) -> Settings:
    defaults = dict(
        google_client_id="client-id",
        google_client_secret="client-secret",
        google_refresh_token="refresh-token",
        google_calendar_ids="primary",
    )
    defaults.update(overrides)
    return Settings(**defaults)


def _mock_token_refresh(access_token: str = "access-token"):
    return respx.post("https://oauth2.googleapis.com/token").mock(
        return_value=httpx.Response(200, json={"access_token": access_token})
    )


def _event(**overrides) -> dict:
    base = {
        "summary": "Reunion budget",
        "status": "confirmed",
        "start": {"dateTime": "2026-07-02T13:00:00+00:00"},
        "end": {"dateTime": "2026-07-02T14:00:00+00:00"},
    }
    base.update(overrides)
    return base


def test_fetch_busy_slots_without_credentials_degrades_cleanly() -> None:
    result = fetch_busy_slots(date(2026, 7, 2), date(2026, 7, 8), settings=Settings())

    assert result.ok is False
    assert result.blocks == []
    assert "non configuré" in result.detail


@respx.mock
def test_fetch_busy_slots_converts_matching_events() -> None:
    _mock_token_refresh()
    respx.get("https://www.googleapis.com/calendar/v3/calendars/primary/events").mock(
        return_value=httpx.Response(200, json={"items": [_event()]})
    )
    settings = _configured_settings()

    result = fetch_busy_slots(date(2026, 7, 2), date(2026, 7, 2), settings=settings)

    assert result.ok is True
    assert len(result.blocks) == 1
    assert result.blocks[0].title == "Reunion budget"
    assert result.blocks[0].start == datetime(2026, 7, 2, 13, 0)
    assert result.blocks[0].end == datetime(2026, 7, 2, 14, 0)
    assert result.blocks[0].all_day is False


@respx.mock
def test_fetch_busy_slots_merges_multiple_calendars() -> None:
    _mock_token_refresh()
    respx.get("https://www.googleapis.com/calendar/v3/calendars/primary/events").mock(
        return_value=httpx.Response(200, json={"items": [_event(summary="Perso")]})
    )
    respx.get("https://www.googleapis.com/calendar/v3/calendars/equipe%40group.calendar.google.com/events").mock(
        return_value=httpx.Response(200, json={"items": [_event(summary="Équipe")]})
    )
    settings = _configured_settings(google_calendar_ids="primary,equipe@group.calendar.google.com")

    result = fetch_busy_slots(date(2026, 7, 2), date(2026, 7, 2), settings=settings)

    assert result.ok is True
    assert {b.title for b in result.blocks} == {"Perso", "Équipe"}


@respx.mock
def test_fetch_busy_slots_skips_cancelled_and_transparent_events() -> None:
    _mock_token_refresh()
    respx.get("https://www.googleapis.com/calendar/v3/calendars/primary/events").mock(
        return_value=httpx.Response(200, json={"items": [
            _event(summary="Annulée", status="cancelled"),
            _event(summary="Disponible", transparency="transparent"),
            _event(summary="Vraie réunion"),
        ]})
    )
    settings = _configured_settings()

    result = fetch_busy_slots(date(2026, 7, 2), date(2026, 7, 2), settings=settings)

    assert result.ok is True
    assert [b.title for b in result.blocks] == ["Vraie réunion"]


@respx.mock
def test_all_day_event_is_flagged_not_timed() -> None:
    _mock_token_refresh()
    respx.get("https://www.googleapis.com/calendar/v3/calendars/primary/events").mock(
        return_value=httpx.Response(200, json={"items": [_event(
            summary="Congés", start={"date": "2026-07-02"}, end={"date": "2026-07-03"},
        )]})
    )
    settings = _configured_settings()

    result = fetch_busy_slots(date(2026, 7, 2), date(2026, 7, 2), settings=settings)

    assert result.blocks[0].all_day is True
    assert result.blocks[0].covers(date(2026, 7, 2))
    assert not result.blocks[0].covers(date(2026, 7, 3))  # DTEND exclusif


@respx.mock
def test_fetch_busy_slots_follows_pagination() -> None:
    _mock_token_refresh()
    respx.get("https://www.googleapis.com/calendar/v3/calendars/primary/events").mock(
        side_effect=[
            httpx.Response(200, json={"items": [_event(summary="Page 1")], "nextPageToken": "p2"}),
            httpx.Response(200, json={"items": [_event(summary="Page 2")]}),
        ]
    )
    settings = _configured_settings()

    result = fetch_busy_slots(date(2026, 7, 2), date(2026, 7, 2), settings=settings)

    assert {b.title for b in result.blocks} == {"Page 1", "Page 2"}


@respx.mock
def test_fetch_busy_slots_degrades_when_token_refresh_fails() -> None:
    respx.post("https://oauth2.googleapis.com/token").mock(return_value=httpx.Response(401))
    settings = _configured_settings()

    result = fetch_busy_slots(date(2026, 7, 2), date(2026, 7, 2), settings=settings)

    assert result.ok is False
    assert result.blocks == []


@respx.mock
def test_fetch_busy_slots_degrades_on_api_error() -> None:
    _mock_token_refresh()
    respx.get("https://www.googleapis.com/calendar/v3/calendars/primary/events").mock(
        return_value=httpx.Response(500)
    )
    settings = _configured_settings()

    result = fetch_busy_slots(date(2026, 7, 2), date(2026, 7, 2), settings=settings)

    assert result.ok is False
    assert "Échec de l'appel à Google Calendar" in result.detail


@respx.mock
def test_fetch_busy_slots_uses_cache_within_ttl() -> None:
    _mock_token_refresh()
    route = respx.get("https://www.googleapis.com/calendar/v3/calendars/primary/events").mock(
        return_value=httpx.Response(200, json={"items": [_event()]})
    )
    settings = _configured_settings(google_cache_ttl_minutes=30)

    fetch_busy_slots(date(2026, 7, 2), date(2026, 7, 2), settings=settings)
    fetch_busy_slots(date(2026, 7, 2), date(2026, 7, 2), settings=settings)

    assert route.call_count == 1  # le second appel sert le cache
