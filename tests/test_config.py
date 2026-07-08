"""Tests de la configuration de « Kairos »."""

from __future__ import annotations

from app.config import Settings


def test_tasks_database_url() -> None:
    assert Settings(tasks_database_path="tasks.db").tasks_database_url == "sqlite:///tasks.db"


def test_pilotage_integration_disabled_by_default() -> None:
    assert Settings().pilotage_configured is False


def test_pilotage_integration_enabled_with_path() -> None:
    assert Settings(pilotage_database_path="/tmp/p.db").pilotage_configured is True


def test_gitlab_direct_configured_false_by_default() -> None:
    assert Settings().gitlab_direct_configured is False


def test_gitlab_direct_configured_true_when_fully_set() -> None:
    settings = Settings(
        gitlab_url="https://gitlab.test", gitlab_token="tok",
        gitlab_projects="equipe/projet", gitlab_assignee_username="corentin",
    )
    assert settings.gitlab_direct_configured is True


def test_gitlab_direct_configured_false_when_pilotage_path_set() -> None:
    """Le cache pilotage prime : l'import direct reste ignoré si les deux
    intégrations sont renseignées en même temps."""
    settings = Settings(
        gitlab_url="https://gitlab.test", gitlab_token="tok",
        gitlab_projects="equipe/projet", gitlab_assignee_username="corentin",
        pilotage_database_path="/tmp/p.db",
    )
    assert settings.gitlab_direct_configured is False


def test_gitlab_project_list_splits_and_strips() -> None:
    settings = Settings(gitlab_projects=" equipe/a , equipe/b ,,")
    assert settings.gitlab_project_list == ["equipe/a", "equipe/b"]


def test_timetree_configured_false_by_default() -> None:
    assert Settings().timetree_configured is False


def test_timetree_configured_true_when_credentials_set() -> None:
    assert Settings(timetree_email="a@b.com", timetree_password="secret").timetree_configured is True


def test_timetree_configured_false_when_only_email_set() -> None:
    assert Settings(timetree_email="a@b.com").timetree_configured is False


def test_wsjf_defaults() -> None:
    settings = Settings()
    assert settings.priority_value_base == 2.0
    assert settings.urgency_horizon_days == 14
    assert settings.urgency_peak == 8.0
    assert settings.default_fibonacci_points == 3


def test_stats_window_default() -> None:
    assert Settings().stats_window_weeks == 8


def test_timer_alert_defaults() -> None:
    settings = Settings()
    assert settings.timer_idle_alert_minutes == 180
    assert settings.pomodoro_focus_minutes == 50
