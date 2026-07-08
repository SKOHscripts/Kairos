"""Tests des calculs en jours ouvrés (capacité de travail)."""

from __future__ import annotations

from datetime import date

from app.workdays import (
    WORKDAYS_PER_WEEK,
    add_business_days,
    build_holidays,
    business_days_between,
    easter_sunday,
    french_public_holidays,
    is_workday,
    on_or_before_business_day,
    previous_business_day,
)


def test_is_workday() -> None:
    assert is_workday(date(2026, 6, 1))   # lundi
    assert is_workday(date(2026, 6, 5))   # vendredi
    assert not is_workday(date(2026, 6, 6))   # samedi
    assert not is_workday(date(2026, 6, 7))   # dimanche


def test_add_business_days_skips_weekends() -> None:
    # Lundi + 5 jours ouvrés = lundi suivant (saute le week-end).
    assert add_business_days(date(2026, 6, 1), 5) == date(2026, 6, 8)
    # Vendredi + 1 jour ouvré = lundi.
    assert add_business_days(date(2026, 6, 5), 1) == date(2026, 6, 8)
    # 0 ou négatif : inchangé.
    assert add_business_days(date(2026, 6, 1), 0) == date(2026, 6, 1)


def test_business_days_between() -> None:
    # Du lundi au lundi suivant : 5 jours ouvrés (week-end exclu).
    assert business_days_between(date(2026, 6, 1), date(2026, 6, 8)) == 5
    # Du vendredi au lundi : 1 seul jour ouvré.
    assert business_days_between(date(2026, 6, 5), date(2026, 6, 8)) == 1
    # Intervalle nul/négatif : 0.
    assert business_days_between(date(2026, 6, 8), date(2026, 6, 1)) == 0
    assert WORKDAYS_PER_WEEK == 5


# --------------------------------------------------------------------------- #
# Jours fériés (optionnels)
# --------------------------------------------------------------------------- #

def test_easter_sunday_known_years() -> None:
    # Dimanches de Pâques de référence.
    assert easter_sunday(2026) == date(2026, 4, 5)
    assert easter_sunday(2024) == date(2024, 3, 31)
    assert easter_sunday(2025) == date(2025, 4, 20)


def test_french_public_holidays_2026() -> None:
    days = french_public_holidays(2026)
    assert date(2026, 1, 1) in days      # Jour de l'an
    assert date(2026, 7, 14) in days     # Fête nationale
    assert date(2026, 4, 6) in days      # Lundi de Pâques (Pâques 05/04)
    assert date(2026, 5, 14) in days     # Ascension (Pâques + 39 j)
    assert date(2026, 5, 25) in days     # Lundi de Pentecôte (Pâques + 50 j)
    assert date(2026, 6, 2) not in days  # jour ordinaire


def test_holidays_shift_business_day_arithmetic() -> None:
    holidays = build_holidays([2026], france=True)
    # Sans fériés : lundi 13/07 + 1 jour ouvré = mardi 14/07.
    assert add_business_days(date(2026, 7, 13), 1) == date(2026, 7, 14)
    # Avec le 14/07 férié : on saute au 15/07.
    assert add_business_days(date(2026, 7, 13), 1, holidays) == date(2026, 7, 15)
    # business_days_between exclut aussi le férié de l'intervalle.
    assert business_days_between(date(2026, 7, 13), date(2026, 7, 17)) == 4
    assert business_days_between(date(2026, 7, 13), date(2026, 7, 17), holidays) == 3
    assert not is_workday(date(2026, 7, 14), holidays)


# --------------------------------------------------------------------------- #
# Décalage « arrière » (récurrence calendaire, phase 4)
# --------------------------------------------------------------------------- #

def test_previous_business_day_skips_weekend() -> None:
    # Dimanche 19/07/2026 → vendredi 17/07 précédent.
    assert previous_business_day(date(2026, 7, 19)) == date(2026, 7, 17)


def test_previous_business_day_chains_through_holiday_and_weekend() -> None:
    # Vendredi 17/07 déclaré férié (simulé) + week-end : recule jusqu'au jeudi 16/07.
    holidays = frozenset({date(2026, 7, 17)})
    assert previous_business_day(date(2026, 7, 19), holidays) == date(2026, 7, 16)


def test_on_or_before_business_day_returns_same_day_when_workday() -> None:
    # Lundi 13/07/2026 est déjà ouvré : inchangé.
    assert on_or_before_business_day(date(2026, 7, 13)) == date(2026, 7, 13)


def test_on_or_before_business_day_shifts_backward_on_weekend() -> None:
    assert on_or_before_business_day(date(2026, 7, 19)) == date(2026, 7, 17)


def test_on_or_before_business_day_never_shifts_forward() -> None:
    # Contraste avec add_business_days (avance) : celui-ci recule toujours.
    sunday = date(2026, 7, 19)
    assert on_or_before_business_day(sunday) < sunday
    assert add_business_days(sunday, 0) == sunday  # add_business_days(jours<=0) inchangé


def test_build_holidays_extra_and_empty_default() -> None:
    # Désactivé par défaut : ensemble vide (seuls les week-ends comptent ailleurs).
    assert build_holidays([2026]) == frozenset()
    # Dates supplémentaires (ponts) acceptées en chaînes ISO, dates invalides ignorées.
    extra = build_holidays([2026], extra=["2026-12-24", "pas-une-date"])
    assert date(2026, 12, 24) in extra and len(extra) == 1
