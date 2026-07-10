"""Tests des agrégats statistiques (module pur, phase 10)."""

from __future__ import annotations

from datetime import date, datetime, timedelta, timezone

from app.config import Settings
from app.tasks_models import Task, WorkSession
from app.tasks_stats import (
    MIN_SAMPLE,
    _median,
    backlog_flow,
    calibration_by_type,
    compute_dashboard_stats,
    estimation_bias,
    fibonacci_calibration,
    focus_stats,
    metadata_completeness,
    throughput_by_week,
    time_by_type,
)

TODAY = date(2026, 7, 6)  # lundi


def _settings(**overrides) -> Settings:
    return Settings(**overrides)


def _done(task_id: int, done_day: date, **kw) -> Task:
    """Tâche terminée « ce jour-là » (updated_at = complétion approximée)."""
    dt = datetime(done_day.year, done_day.month, done_day.day, 12, tzinfo=timezone.utc)
    return Task(id=task_id, title=f"T{task_id}", status="done", updated_at=dt, **kw)


def _session(task_id: int, start: datetime, minutes: int) -> WorkSession:
    return WorkSession(task_id=task_id, started_at=start,
                       ended_at=start + timedelta(minutes=minutes))


def _todo(task_id: int, created: datetime, **kw) -> Task:
    """Tâche todo avec ``updated_at`` posé (comme une ligne réelle issue de la base)."""
    kw.setdefault("updated_at", created)
    return Task(id=task_id, title=f"T{task_id}", status="todo", created_at=created, **kw)


# --------------------------------------------------------------------------- #
# Médiane
# --------------------------------------------------------------------------- #

def test_median_empty_is_none() -> None:
    assert _median([]) is None


def test_median_odd_and_even() -> None:
    assert _median([3, 1, 2]) == 2
    assert _median([1, 2, 3, 4]) == 2.5


# --------------------------------------------------------------------------- #
# Débit hebdomadaire
# --------------------------------------------------------------------------- #

def test_throughput_zero_fills_all_weeks() -> None:
    weeks = throughput_by_week([], TODAY, 4)
    assert len(weeks) == 4
    assert [w.completed for w in weeks] == [0, 0, 0, 0]
    # Axe continu, du plus ancien au plus récent, se terminant sur la semaine courante.
    assert weeks[-1].week_start == TODAY  # lundi courant


def test_throughput_counts_tasks_and_points_by_completion_week() -> None:
    tasks = [
        _done(1, TODAY, fibonacci_points=3),
        _done(2, TODAY, fibonacci_points=5),
        _done(3, TODAY - timedelta(days=7), fibonacci_points=2),  # semaine précédente
    ]
    weeks = throughput_by_week(tasks, TODAY, 2)
    assert weeks[0].completed == 1 and weeks[0].points == 2   # semaine -1
    assert weeks[1].completed == 2 and weeks[1].points == 8   # semaine courante


def test_throughput_ignores_tasks_outside_window() -> None:
    tasks = [_done(1, TODAY - timedelta(weeks=10))]  # hors fenêtre de 4 semaines
    weeks = throughput_by_week(tasks, TODAY, 4)
    assert sum(w.completed for w in weeks) == 0


# --------------------------------------------------------------------------- #
# Calibration Fibonacci → temps réel
# --------------------------------------------------------------------------- #

def test_fibonacci_calibration_medians_by_points() -> None:
    tasks = [
        _done(1, TODAY, fibonacci_points=3),
        _done(2, TODAY, fibonacci_points=3),
        _done(3, TODAY, fibonacci_points=5),
    ]
    spent = {1: 60, 2: 80, 3: 150}
    calib = fibonacci_calibration(tasks, spent)
    by_pts = {c.points: c for c in calib}
    assert by_pts[3].count == 2 and by_pts[3].median_minutes == 70
    assert by_pts[5].count == 1 and by_pts[5].median_minutes == 150


def test_fibonacci_calibration_skips_untimed_tasks() -> None:
    tasks = [_done(1, TODAY, fibonacci_points=3)]  # jamais chronométrée
    assert fibonacci_calibration(tasks, {}) == []


def test_fibonacci_calibration_reliability_flag() -> None:
    tasks = [_done(i, TODAY, fibonacci_points=3) for i in range(MIN_SAMPLE)]
    spent = {i: 30 for i in range(MIN_SAMPLE)}
    calib = fibonacci_calibration(tasks, spent)
    assert calib[0].reliable is True
    # Un seul échantillon → peu fiable.
    assert fibonacci_calibration([_done(9, TODAY, fibonacci_points=8)], {9: 30})[0].reliable is False


# --------------------------------------------------------------------------- #
# Calibration par type de tâche (issue #7)
# --------------------------------------------------------------------------- #

def test_calibration_by_type_medians_by_type() -> None:
    tasks = [
        _done(1, TODAY, task_type="Développement"),
        _done(2, TODAY, task_type="Développement"),
        _done(3, TODAY, task_type="Réunion"),
    ]
    spent = {1: 60, 2: 80, 3: 15}
    calib = calibration_by_type(tasks, spent)
    by_key = {c.key: c for c in calib}
    assert by_key["Développement"].count == 2 and by_key["Développement"].median_minutes == 70
    assert by_key["Réunion"].count == 1 and by_key["Réunion"].median_minutes == 15


def test_calibration_by_type_skips_untyped_and_untimed_tasks() -> None:
    tasks = [_done(1, TODAY), _done(2, TODAY, task_type="Développement")]
    assert calibration_by_type(tasks, {}) == []


def test_calibration_by_type_reliability_flag() -> None:
    tasks = [_done(i, TODAY, task_type="Développement") for i in range(MIN_SAMPLE)]
    spent = {i: 30 for i in range(MIN_SAMPLE)}
    calib = calibration_by_type(tasks, spent)
    assert calib[0].reliable is True
    single = calibration_by_type([_done(9, TODAY, task_type="Réunion")], {9: 15})
    assert single[0].reliable is False


# --------------------------------------------------------------------------- #
# Biais d'estimation
# --------------------------------------------------------------------------- #

def test_estimation_bias_ratio_over_matched_tasks() -> None:
    tasks = [
        _done(1, TODAY, estimated_minutes=60),
        _done(2, TODAY, estimated_minutes=40),
    ]
    spent = {1: 90, 2: 40}  # total réel 130 / estimé 100 = 1.3
    bias = estimation_bias(tasks, spent)
    assert bias is not None
    assert bias.count == 2
    assert round(bias.ratio, 2) == 1.3


def test_estimation_bias_none_without_matched_tasks() -> None:
    assert estimation_bias([_done(1, TODAY, estimated_minutes=60)], {}) is None
    assert estimation_bias([_done(1, TODAY)], {1: 30}) is None  # pas d'estimation


# --------------------------------------------------------------------------- #
# Répartition du temps par type
# --------------------------------------------------------------------------- #

def test_time_by_type_shares_sorted_desc_with_percentages() -> None:
    sessions = [
        _session(1, datetime(2026, 7, 6, 9, tzinfo=timezone.utc), 30),
        _session(2, datetime(2026, 7, 6, 10, tzinfo=timezone.utc), 90),
    ]
    shares = time_by_type(sessions, {1: "Réunion", 2: "Développement"})
    assert [s.key for s in shares] == ["Développement", "Réunion"]  # trié par temps décroissant
    assert shares[0].pct == 75 and shares[1].pct == 25
    assert shares[0].label == "Développement"


def test_time_by_type_untyped_grouped() -> None:
    sessions = [_session(1, datetime(2026, 7, 6, 9, tzinfo=timezone.utc), 30)]
    shares = time_by_type(sessions, {1: ""})
    assert shares[0].label == "Sans type"


# --------------------------------------------------------------------------- #
# Focus / fragmentation
# --------------------------------------------------------------------------- #

def test_focus_stats_counts_and_average() -> None:
    sessions = [
        _session(1, datetime(2026, 7, 6, 9, tzinfo=timezone.utc), 30),
        _session(1, datetime(2026, 7, 6, 11, tzinfo=timezone.utc), 90),
    ]
    focus = focus_stats(sessions)
    assert focus.session_count == 2
    assert focus.total_minutes == 120
    assert focus.avg_session_minutes == 60


def test_focus_stats_empty() -> None:
    focus = focus_stats([])
    assert focus.session_count == 0 and focus.avg_session_minutes is None


# --------------------------------------------------------------------------- #
# Flux / backlog
# --------------------------------------------------------------------------- #

def test_backlog_flow_wip_age_overdue_and_hit_rate() -> None:
    created = datetime(2026, 7, 1, 9, tzinfo=timezone.utc)  # 5 jours avant TODAY
    tasks = [
        _todo(1, created),
        _todo(2, created, deadline=TODAY - timedelta(days=1)),
        _done(3, TODAY, created_at=created, deadline=TODAY + timedelta(days=1)),  # à temps
        _done(4, TODAY, created_at=created, deadline=TODAY - timedelta(days=2)),  # en retard
    ]
    flow = backlog_flow(tasks, TODAY, TODAY - timedelta(weeks=8), settings=_settings())
    assert flow.open_count == 2
    assert flow.overdue_count == 1
    assert flow.median_age_days == 5
    assert flow.completion_delay_days == 5   # 3 et 4 : créées J-5, finies J
    assert flow.deadline_total == 2 and flow.deadline_on_time == 1
    assert flow.deadline_hit_pct == 50


def test_backlog_flow_hit_pct_none_without_deadlines() -> None:
    flow = backlog_flow([_done(1, TODAY)], TODAY, TODAY - timedelta(weeks=8),
                        settings=_settings())
    assert flow.deadline_hit_pct is None


# --------------------------------------------------------------------------- #
# Complétude des métadonnées
# --------------------------------------------------------------------------- #

def test_metadata_completeness_percentages() -> None:
    tasks = [
        Task(id=1, title="A", status="todo", fibonacci_points=3, estimated_minutes=30,
             task_type="dev"),
        Task(id=2, title="B", status="todo"),
        Task(id=3, title="C", status="done", fibonacci_points=5),  # ignorée (pas todo)
    ]
    comp = metadata_completeness(tasks)
    assert comp.total == 2  # seules les todo comptent
    assert comp.with_points == 1 and comp.points_pct == 50
    assert comp.type_pct == 50


def test_metadata_completeness_empty_no_division_error() -> None:
    comp = metadata_completeness([])
    assert comp.total == 0 and comp.points_pct == 0


# --------------------------------------------------------------------------- #
# Assemblage complet
# --------------------------------------------------------------------------- #

def test_compute_dashboard_stats_empty_has_no_data() -> None:
    stats = compute_dashboard_stats([], [], TODAY, settings=_settings())
    assert stats.has_any_data is False
    assert stats.window_weeks == _settings().stats_window_weeks


def test_compute_dashboard_stats_end_to_end() -> None:
    created = datetime(2026, 7, 1, 9, tzinfo=timezone.utc)
    tasks = [
        _done(1, TODAY, created_at=created, fibonacci_points=3, estimated_minutes=60,
              task_type="dev"),
        _todo(2, created, task_type="dev"),
    ]
    sessions = [_session(1, datetime(2026, 7, 6, 9, tzinfo=timezone.utc), 90)]
    stats = compute_dashboard_stats(tasks, sessions, TODAY, settings=_settings())
    assert stats.has_any_data is True
    assert stats.completed_in_window == 1
    assert stats.tracked_minutes_window == 90
    assert stats.bias is not None and round(stats.bias.ratio, 2) == 1.5
    assert stats.calibration[0].points == 3 and stats.calibration[0].median_minutes == 90
    assert stats.flow.open_count == 1
    assert stats.completeness.total == 1
