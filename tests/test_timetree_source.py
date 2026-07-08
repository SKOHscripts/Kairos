"""Tests du seam TimeTree : dégradation propre + intégration réelle (T9).

Aucun test n'appelle le vrai binaire `timetree-exporter` ni le réseau TimeTree :
`subprocess.run` est systématiquement mocké.
"""

from __future__ import annotations

import subprocess
from datetime import date
from pathlib import Path

import pytest

import app.calendar.timetree_source as timetree_source
from app.calendar.timetree_source import _resolve_timetree_binary, fetch_busy_slots
from app.config import Settings

FIXTURE_ICS = b"""BEGIN:VCALENDAR
VERSION:2.0
PRODID:-//Test//EN
BEGIN:VEVENT
UID:1@test
DTSTART:20260702T130000Z
DTEND:20260702T140000Z
SUMMARY:Reunion budget
END:VEVENT
BEGIN:VEVENT
UID:2@test
DTSTART:20260801T090000Z
DTEND:20260801T100000Z
SUMMARY:Hors plage
END:VEVENT
END:VCALENDAR
"""

FIXTURE_ICS_ALL_DAY = b"""BEGIN:VCALENDAR
VERSION:2.0
PRODID:-//Test//EN
BEGIN:VEVENT
UID:3@test
DTSTART;VALUE=DATE:20260702
DTEND;VALUE=DATE:20260703
SUMMARY:Anniversaire
END:VEVENT
BEGIN:VEVENT
UID:4@test
DTSTART:20260702T130000Z
DTEND:20260702T140000Z
SUMMARY:Reunion horaire
END:VEVENT
END:VCALENDAR
"""


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


def test_fetch_busy_slots_without_credentials_degrades_cleanly() -> None:
    settings = Settings()
    result = fetch_busy_slots(date(2026, 7, 2), date(2026, 7, 8), settings=settings)

    assert result.ok is False
    assert result.blocks == []
    assert "non configuré" in result.detail


def test_fetch_busy_slots_invokes_cli_and_parses_ics(monkeypatch) -> None:
    calls = []

    def fake_run(args, *, env, capture_output, timeout, check):
        calls.append(args)
        output_path = Path(args[args.index("-o") + 1])
        output_path.write_bytes(FIXTURE_ICS)
        return subprocess.CompletedProcess(args, 0)

    monkeypatch.setattr(timetree_source.subprocess, "run", fake_run)
    settings = _configured_settings()

    result = fetch_busy_slots(date(2026, 7, 2), date(2026, 7, 2), settings=settings)

    assert result.ok is True
    assert len(result.blocks) == 1  # l'événement hors plage est filtré
    assert result.blocks[0].title == "Reunion budget"
    assert len(calls) == 1
    assert "-e" in calls[0] and "user@example.com" in calls[0]
    assert "-c" in calls[0] and "abc123" in calls[0]


def test_all_day_events_are_flagged_not_timed(monkeypatch) -> None:
    """Un événement « journée entière » (DTSTART date) est marqué all_day : il ne doit
    ni bloquer l'ordonnancement ni remplir la timeline (juste une puce sur le jour)."""

    def fake_run(args, *, env, capture_output, timeout, check):
        output_path = Path(args[args.index("-o") + 1])
        output_path.write_bytes(FIXTURE_ICS_ALL_DAY)
        return subprocess.CompletedProcess(args, 0)

    monkeypatch.setattr(timetree_source.subprocess, "run", fake_run)
    settings = _configured_settings()

    result = fetch_busy_slots(date(2026, 7, 2), date(2026, 7, 2), settings=settings)

    by_title = {b.title: b for b in result.blocks}
    assert by_title["Anniversaire"].all_day is True
    assert by_title["Anniversaire"].covers(date(2026, 7, 2))
    assert not by_title["Anniversaire"].covers(date(2026, 7, 3))  # DTEND exclusif
    assert by_title["Reunion horaire"].all_day is False


def test_fetch_busy_slots_degrades_on_subprocess_failure(monkeypatch) -> None:
    def fake_run(*args, **kwargs):
        raise subprocess.CalledProcessError(1, "timetree-exporter")

    monkeypatch.setattr(timetree_source.subprocess, "run", fake_run)
    settings = _configured_settings()

    result = fetch_busy_slots(date(2026, 7, 2), date(2026, 7, 2), settings=settings)

    assert result.ok is False
    assert result.blocks == []
    assert "Échec de l'export TimeTree" in result.detail


def test_fetch_busy_slots_degrades_on_timeout(monkeypatch) -> None:
    def fake_run(*args, **kwargs):
        raise subprocess.TimeoutExpired("timetree-exporter", 30)

    monkeypatch.setattr(timetree_source.subprocess, "run", fake_run)
    settings = _configured_settings()

    result = fetch_busy_slots(date(2026, 7, 2), date(2026, 7, 2), settings=settings)

    assert result.ok is False


def test_resolve_timetree_binary_prefers_venv_sibling(monkeypatch, tmp_path) -> None:
    """Reproduit le bug réel : `timetree-exporter` doit être trouvé sans dépendre de
    PATH, car le service systemd invoque `.venv/bin/uvicorn` sans activer le venv."""
    fake_python = tmp_path / "python3"
    fake_python.write_text("")
    fake_binary = tmp_path / "timetree-exporter"
    fake_binary.write_text("")
    monkeypatch.setattr(timetree_source.sys, "executable", str(fake_python))

    assert _resolve_timetree_binary() == str(fake_binary)


def test_resolve_timetree_binary_falls_back_to_path(monkeypatch, tmp_path) -> None:
    fake_python = tmp_path / "python3"
    fake_python.write_text("")  # pas de "timetree-exporter" à côté
    monkeypatch.setattr(timetree_source.sys, "executable", str(fake_python))
    monkeypatch.setattr(timetree_source.shutil, "which", lambda name: "/usr/bin/timetree-exporter")

    assert _resolve_timetree_binary() == "/usr/bin/timetree-exporter"


def test_fetch_busy_slots_uses_cache_within_ttl(monkeypatch) -> None:
    call_count = 0

    def fake_run(args, *, env, capture_output, timeout, check):
        nonlocal call_count
        call_count += 1
        output_path = Path(args[args.index("-o") + 1])
        output_path.write_bytes(FIXTURE_ICS)
        return subprocess.CompletedProcess(args, 0)

    monkeypatch.setattr(timetree_source.subprocess, "run", fake_run)
    settings = _configured_settings(timetree_cache_ttl_minutes=30)

    fetch_busy_slots(date(2026, 7, 2), date(2026, 7, 2), settings=settings)
    fetch_busy_slots(date(2026, 7, 2), date(2026, 7, 2), settings=settings)

    assert call_count == 1  # le second appel sert le cache, pas de second subprocess
