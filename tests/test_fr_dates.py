"""Dates en toutes lettres en français (audit UI) — voir app/fr_dates.py."""

from __future__ import annotations

from datetime import date

from app.fr_dates import date_longue, jour_court


def test_date_longue_is_french_whatever_the_system_locale() -> None:
    assert date_longue(date(2026, 9, 22)) == "mardi 22 septembre 2026"


def test_date_longue_writes_the_first_of_the_month_as_1er() -> None:
    assert date_longue(date(2026, 10, 1)) == "jeudi 1er octobre 2026"


def test_date_longue_covers_accented_months() -> None:
    assert date_longue(date(2026, 2, 3)) == "mardi 3 février 2026"
    assert date_longue(date(2026, 8, 15)) == "samedi 15 août 2026"


def test_jour_court_for_week_columns() -> None:
    assert jour_court(date(2026, 9, 21)) == "lun. 21/09"
    assert jour_court(date(2026, 9, 27)) == "dim. 27/09"
