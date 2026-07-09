"""Tests du seam TimeTree : dégradation propre + conversion des événements.

Aucun test n'appelle le vrai réseau TimeTree : `login`/`TimeTreeCalendar` sont
systématiquement mockés au niveau de l'API du paquet `timetree_exporter`.
"""

from __future__ import annotations

from datetime import date, datetime, timezone

import pytest

import app.calendar.timetree_source as timetree_source
from app.calendar.timetree_source import fetch_busy_slots
from app.config import Settings


@pytest.fixture(autouse=True)
def _clear_cache():
    timetree_source._cache.clear()
    yield
    timetree_source._cache.clear()


def _configured_settings(**overrides) -> Settings:
    return Settings(
        timetree_email="user@example.com",
        timetree_password="secret",
        timetree_calendar_code="abc123",
        **overrides,
    )


def _epoch_ms(year, month, day, hour=0, minute=0) -> int:
    return int(datetime(year, month, day, hour, minute, tzinfo=timezone.utc).timestamp() * 1000)


def _event(**overrides) -> dict:
    base = {
        "uuid": "evt-1",
        "title": "Reunion budget",
        "created_at": 0,
        "updated_at": 0,
        "note": "",
        "location": "",
        "location_lat": None,
        "location_lon": None,
        "url": "",
        "start_at": _epoch_ms(2026, 7, 2, 13, 0),
        "start_timezone": "UTC",
        "end_at": _epoch_ms(2026, 7, 2, 14, 0),
        "end_timezone": "UTC",
        "all_day": False,
        "alerts": [],
        "recurrences": [],
        "parent_id": None,
        "recurring_uuid": None,
        "type": 0,  # TimeTreeEventType.NORMAL
        "category": 1,  # TimeTreeEventCategory.NORMAL
        "label_id": None,
        "comments": None,
    }
    base.update(overrides)
    return base


def _fake_calendar_api_cls(metadata_list: list[dict], events: list[dict], *, call_counter: list[int] | None = None):
    class _FakeApi:
        def __init__(self, session_id, capture_raw_responses=False):
            self.session_id = session_id

        def get_metadata(self):
            return metadata_list

        def get_events(self, calendar_id, calendar_name=None, calendar_users=None,
                        include_comments=False, num_workers=10):
            if call_counter is not None:
                call_counter[0] += 1
            return events

    return _FakeApi


def _patch_timetree_api(monkeypatch, metadata_list, events, *, call_counter=None):
    monkeypatch.setattr(timetree_source, "login", lambda email, password: "session-123")
    monkeypatch.setattr(
        timetree_source, "TimeTreeCalendar",
        _fake_calendar_api_cls(metadata_list, events, call_counter=call_counter),
    )


_CALENDAR_METADATA = [{"id": 1, "name": "Perso", "alias_code": "abc123", "deactivated_at": None}]


def test_fetch_busy_slots_without_credentials_degrades_cleanly() -> None:
    settings = Settings()
    result = fetch_busy_slots(date(2026, 7, 2), date(2026, 7, 8), settings=settings)

    assert result.ok is False
    assert result.blocks == []
    assert "non configuré" in result.detail


def test_fetch_busy_slots_converts_matching_events(monkeypatch) -> None:
    _patch_timetree_api(
        monkeypatch, _CALENDAR_METADATA,
        [_event(), _event(uuid="evt-2", title="Hors plage",
                          start_at=_epoch_ms(2026, 8, 1, 9), end_at=_epoch_ms(2026, 8, 1, 10))],
    )
    settings = _configured_settings()

    result = fetch_busy_slots(date(2026, 7, 2), date(2026, 7, 2), settings=settings)

    assert result.ok is True
    assert len(result.blocks) == 1  # l'événement hors plage est filtré
    assert result.blocks[0].title == "Reunion budget"
    assert result.blocks[0].start == datetime(2026, 7, 2, 13, 0)
    assert result.blocks[0].end == datetime(2026, 7, 2, 14, 0)


def test_fetch_busy_slots_calendar_code_not_found(monkeypatch) -> None:
    _patch_timetree_api(
        monkeypatch,
        [{"id": 1, "name": "Autre", "alias_code": "un-autre-code", "deactivated_at": None}],
        [],
    )
    settings = _configured_settings()

    result = fetch_busy_slots(date(2026, 7, 2), date(2026, 7, 2), settings=settings)

    assert result.ok is False
    assert result.blocks == []
    assert "introuvable" in result.detail


def test_fetch_busy_slots_skips_birthday_and_memo_events(monkeypatch) -> None:
    """L'ancien export iCal filtrait silencieusement anniversaires et mémos
    (`ICalEventFormatter.to_ical`) : la conversion en-process doit répliquer ce
    filtre, sinon des événements jusqu'ici invisibles deviendraient des
    créneaux occupés (régression de comportement)."""
    _patch_timetree_api(
        monkeypatch, _CALENDAR_METADATA,
        [
            _event(uuid="b", title="Anniversaire", type=1),  # BIRTHDAY
            _event(uuid="m", title="Mémo", category=2),  # MEMO
            _event(uuid="ok", title="Vraie réunion"),
        ],
    )
    settings = _configured_settings()

    result = fetch_busy_slots(date(2026, 7, 2), date(2026, 7, 2), settings=settings)

    assert result.ok is True
    assert [b.title for b in result.blocks] == ["Vraie réunion"]


def test_all_day_events_are_flagged_not_timed(monkeypatch) -> None:
    """Un événement « journée entière » est marqué all_day : il ne doit ni
    bloquer l'ordonnancement ni remplir la timeline (juste une puce sur le jour)."""
    _patch_timetree_api(
        monkeypatch, _CALENDAR_METADATA,
        [
            _event(uuid="allday", title="Anniversaire", all_day=True,
                   start_at=_epoch_ms(2026, 7, 2), end_at=_epoch_ms(2026, 7, 2)),
            _event(uuid="timed", title="Reunion horaire", all_day=False),
        ],
    )
    settings = _configured_settings()

    result = fetch_busy_slots(date(2026, 7, 2), date(2026, 7, 2), settings=settings)

    by_title = {b.title: b for b in result.blocks}
    assert by_title["Anniversaire"].all_day is True
    assert by_title["Anniversaire"].covers(date(2026, 7, 2))
    assert not by_title["Anniversaire"].covers(date(2026, 7, 3))  # DTEND exclusif
    assert by_title["Reunion horaire"].all_day is False


def test_fetch_busy_slots_degrades_on_login_failure(monkeypatch) -> None:
    def _boom(email, password):
        raise RuntimeError("identifiants invalides")

    monkeypatch.setattr(timetree_source, "login", _boom)
    settings = _configured_settings()

    result = fetch_busy_slots(date(2026, 7, 2), date(2026, 7, 2), settings=settings)

    assert result.ok is False
    assert result.blocks == []
    assert "Échec de l'export TimeTree" in result.detail


def test_fetch_busy_slots_degrades_on_malformed_api_response(monkeypatch) -> None:
    """`TimeTreeCalendar.get_events` (le vrai) ne lève pas toujours sur une
    réponse HTTP en échec — elle peut lever `KeyError`/`ValueError` plus loin.
    Cette frontière avec un paquet non-officiel ne doit jamais remonter en 500."""

    class _BrokenApi:
        def __init__(self, session_id, capture_raw_responses=False):
            pass

        def get_metadata(self):
            return _CALENDAR_METADATA

        def get_events(self, *a, **k):
            raise KeyError("events")

    monkeypatch.setattr(timetree_source, "login", lambda email, password: "session-123")
    monkeypatch.setattr(timetree_source, "TimeTreeCalendar", _BrokenApi)
    settings = _configured_settings()

    result = fetch_busy_slots(date(2026, 7, 2), date(2026, 7, 2), settings=settings)

    assert result.ok is False


def test_fetch_busy_slots_uses_cache_within_ttl(monkeypatch) -> None:
    call_counter = [0]
    _patch_timetree_api(monkeypatch, _CALENDAR_METADATA, [_event()], call_counter=call_counter)
    settings = _configured_settings(timetree_cache_ttl_minutes=30)

    fetch_busy_slots(date(2026, 7, 2), date(2026, 7, 2), settings=settings)
    fetch_busy_slots(date(2026, 7, 2), date(2026, 7, 2), settings=settings)

    assert call_counter[0] == 1  # le second appel sert le cache, pas un second appel API
