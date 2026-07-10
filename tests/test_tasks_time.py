"""Tests des agrégats de suivi du temps réel (logique pure)."""

from __future__ import annotations

from datetime import date, datetime, timedelta, timezone

from app.tasks_models import Task, WorkSession
from app.tasks_time import (
    running_session,
    sessions_in_range,
    sessions_on_day,
    session_minutes,
    spent_minutes_by_task,
    spent_minutes_by_type,
    total_minutes,
)

NOW = datetime(2026, 7, 2, 12, 0, tzinfo=timezone.utc)


def test_closed_session_duration() -> None:
    s = WorkSession(task_id=1, started_at=NOW - timedelta(minutes=45), ended_at=NOW)
    assert session_minutes(s, now=NOW) == 45


def test_open_session_runs_until_now() -> None:
    s = WorkSession(task_id=1, started_at=NOW - timedelta(minutes=10), ended_at=None)
    assert session_minutes(s, now=NOW) == 10


def test_spent_minutes_aggregates_by_task() -> None:
    sessions = [
        WorkSession(task_id=1, started_at=NOW - timedelta(minutes=30), ended_at=NOW - timedelta(minutes=10)),
        WorkSession(task_id=1, started_at=NOW - timedelta(minutes=10), ended_at=NOW),
        WorkSession(task_id=2, started_at=NOW - timedelta(minutes=5), ended_at=NOW),
    ]
    totals = spent_minutes_by_task(sessions, now=NOW)
    assert totals == {1: 30, 2: 5}


def test_spent_minutes_by_task_adds_manual_minutes_on_top_of_sessions() -> None:
    """Issue #6 : le temps saisi à la main s'additionne au temps chronométré, il ne
    le remplace jamais — comble ce que le chrono n'a pas mesuré (oubli partiel)."""
    sessions = [
        WorkSession(task_id=1, started_at=NOW - timedelta(minutes=30), ended_at=NOW),
    ]
    tasks = [
        Task(id=1, manual_time_spent_minutes=15),
        Task(id=2, manual_time_spent_minutes=20),  # jamais chronométrée : tout est manuel
        Task(id=3, manual_time_spent_minutes=None),  # rien de saisi : ignorée
    ]
    totals = spent_minutes_by_task(sessions, now=NOW, tasks=tasks)
    assert totals == {1: 45, 2: 20}


def test_spent_minutes_by_task_ignores_tasks_without_sessions_or_manual_entry() -> None:
    assert spent_minutes_by_task([], now=NOW, tasks=[Task(id=1)]) == {}


def test_running_session_is_the_open_one() -> None:
    closed = WorkSession(task_id=1, started_at=NOW - timedelta(minutes=30), ended_at=NOW - timedelta(minutes=10))
    open_ = WorkSession(task_id=2, started_at=NOW - timedelta(minutes=5), ended_at=None)
    assert running_session([closed, open_]) is open_


def test_running_session_none_when_all_closed() -> None:
    closed = WorkSession(task_id=1, started_at=NOW - timedelta(minutes=30), ended_at=NOW)
    assert running_session([closed]) is None


def test_total_minutes_across_all_sessions() -> None:
    sessions = [
        WorkSession(task_id=1, started_at=NOW - timedelta(minutes=20), ended_at=NOW),
        WorkSession(task_id=2, started_at=NOW - timedelta(minutes=15), ended_at=NOW),
    ]
    assert total_minutes(sessions, now=NOW) == 35


def test_naive_datetimes_are_handled() -> None:
    """Les datetimes SQLite reviennent naïfs : ne doit pas planter (comparaison aware)."""
    naive_now = NOW.replace(tzinfo=None)
    s = WorkSession(task_id=1, started_at=naive_now - timedelta(minutes=12), ended_at=naive_now)
    assert session_minutes(s, now=NOW) == 12


# --------------------------------------------------------------------------- #
# Y4 — Suivi du temps réel exploité (phase 7) : filtrage par jour/semaine +
# ventilation par type, correction du bug « temps travaillé aujourd'hui »
# --------------------------------------------------------------------------- #

TODAY = NOW.date()  # 2026-07-02


def test_sessions_on_day_excludes_sessions_from_other_days() -> None:
    """Régression du bug corrigé en phase 7 : une session d'hier ne doit plus
    compter dans le total du jour affiché."""
    today_session = WorkSession(
        task_id=1, started_at=NOW - timedelta(hours=1), ended_at=NOW
    )
    yesterday_session = WorkSession(
        task_id=2,
        started_at=NOW - timedelta(days=1, hours=1),
        ended_at=NOW - timedelta(days=1),
    )
    filtered = sessions_on_day([today_session, yesterday_session], TODAY)
    assert filtered == [today_session]
    assert total_minutes(filtered, now=NOW) == 60
    # Avant correction, total_minutes([today_session, yesterday_session]) aurait
    # inclus la session d'hier — la régression porte sur le filtrage en amont.


def test_sessions_in_range_includes_both_bounds() -> None:
    monday = date(2026, 6, 29)
    sunday = date(2026, 7, 5)
    in_range = WorkSession(
        task_id=1, started_at=datetime(2026, 7, 1, 9, 0, tzinfo=timezone.utc),
        ended_at=datetime(2026, 7, 1, 9, 30, tzinfo=timezone.utc),
    )
    on_monday = WorkSession(
        task_id=2, started_at=datetime(2026, 6, 29, 8, 0, tzinfo=timezone.utc),
        ended_at=datetime(2026, 6, 29, 8, 30, tzinfo=timezone.utc),
    )
    before_range = WorkSession(
        task_id=3, started_at=datetime(2026, 6, 28, 8, 0, tzinfo=timezone.utc),
        ended_at=datetime(2026, 6, 28, 8, 30, tzinfo=timezone.utc),
    )
    after_range = WorkSession(
        task_id=4, started_at=datetime(2026, 7, 6, 8, 0, tzinfo=timezone.utc),
        ended_at=datetime(2026, 7, 6, 8, 30, tzinfo=timezone.utc),
    )
    result = sessions_in_range(
        [in_range, on_monday, before_range, after_range], monday, sunday
    )
    assert result == [in_range, on_monday]


def test_spent_minutes_by_type_groups_across_tasks() -> None:
    sessions = [
        WorkSession(task_id=1, started_at=NOW - timedelta(minutes=30), ended_at=NOW),
        WorkSession(task_id=2, started_at=NOW - timedelta(minutes=15), ended_at=NOW),
        WorkSession(task_id=3, started_at=NOW - timedelta(minutes=10), ended_at=NOW),
    ]
    task_type_by_id = {1: "dev", 2: "dev", 3: "reunion"}
    assert spent_minutes_by_type(sessions, task_type_by_id, now=NOW) == {
        "dev": 45, "reunion": 10,
    }


def test_spent_minutes_by_type_unknown_task_falls_back_to_empty_key() -> None:
    sessions = [WorkSession(task_id=99, started_at=NOW - timedelta(minutes=5), ended_at=NOW)]
    assert spent_minutes_by_type(sessions, {}, now=NOW) == {"": 5}
