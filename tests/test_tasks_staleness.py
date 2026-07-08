"""Tests de la détection des tâches qui traînent (phase 7)."""

from __future__ import annotations

from datetime import date, datetime, timedelta

from app.tasks_models import Task
from app.tasks_staleness import days_stale

TODAY = date(2026, 7, 3)
OVERDUE_DAYS = 7
UNTOUCHED_DAYS = 14


def test_deadline_overdue_beyond_threshold_returns_days() -> None:
    task = Task(title="En retard", deadline=TODAY - timedelta(days=10))
    assert days_stale(
        task, TODAY, overdue_days=OVERDUE_DAYS, untouched_days=UNTOUCHED_DAYS
    ) == 10


def test_deadline_overdue_within_threshold_returns_none() -> None:
    task = Task(title="Un peu en retard", deadline=TODAY - timedelta(days=3))
    assert days_stale(
        task, TODAY, overdue_days=OVERDUE_DAYS, untouched_days=UNTOUCHED_DAYS
    ) is None


def test_deadline_exactly_at_threshold_returns_none() -> None:
    """Seuil strict : pile au seuil, pas encore « qui traîne »."""
    task = Task(title="Pile au seuil", deadline=TODAY - timedelta(days=OVERDUE_DAYS))
    assert days_stale(
        task, TODAY, overdue_days=OVERDUE_DAYS, untouched_days=UNTOUCHED_DAYS
    ) is None


def test_scheduled_date_overdue_uses_earliest_of_the_two_dates() -> None:
    """deadline et scheduled_date dépassées : la plus ancienne des deux sert de référence."""
    task = Task(
        title="Les deux dépassées",
        deadline=TODAY - timedelta(days=8),
        scheduled_date=TODAY - timedelta(days=15),
    )
    assert days_stale(
        task, TODAY, overdue_days=OVERDUE_DAYS, untouched_days=UNTOUCHED_DAYS
    ) == 15


def test_future_dates_are_never_stale() -> None:
    task = Task(title="Pas encore due", deadline=TODAY + timedelta(days=1))
    assert days_stale(
        task, TODAY, overdue_days=OVERDUE_DAYS, untouched_days=UNTOUCHED_DAYS
    ) is None


def test_no_dates_but_recently_updated_returns_none() -> None:
    task = Task(title="Récente, sans date")
    task.updated_at = datetime.combine(TODAY - timedelta(days=2), datetime.min.time())
    assert days_stale(
        task, TODAY, overdue_days=OVERDUE_DAYS, untouched_days=UNTOUCHED_DAYS
    ) is None


def test_no_dates_and_untouched_beyond_threshold_returns_days() -> None:
    task = Task(title="Vieille, sans date")
    task.updated_at = datetime.combine(TODAY - timedelta(days=20), datetime.min.time())
    assert days_stale(
        task, TODAY, overdue_days=OVERDUE_DAYS, untouched_days=UNTOUCHED_DAYS
    ) == 20


def test_no_dates_and_untouched_within_threshold_returns_none() -> None:
    task = Task(title="Assez récente, sans date")
    task.updated_at = datetime.combine(TODAY - timedelta(days=10), datetime.min.time())
    assert days_stale(
        task, TODAY, overdue_days=OVERDUE_DAYS, untouched_days=UNTOUCHED_DAYS
    ) is None
