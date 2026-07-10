"""Tests de la configuration de « Kairos »."""

from __future__ import annotations

from app import git_credentials
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


def test_gitlab_token_effective_prefers_explicit_env_token(monkeypatch) -> None:
    """`GITLAB_TOKEN` explicite : jamais de résolution via git credential/netrc."""
    def _boom(url):
        raise AssertionError("resolve_gitlab_token ne doit pas être appelée")

    monkeypatch.setattr(git_credentials, "resolve_gitlab_token", _boom)

    settings = Settings(gitlab_url="https://gitlab.test", gitlab_token="explicit-tok")
    assert settings.gitlab_token_effective == "explicit-tok"


def test_gitlab_token_effective_falls_back_to_git_credentials(monkeypatch) -> None:
    monkeypatch.setattr(git_credentials, "resolve_gitlab_token", lambda url: "resolved-tok")

    settings = Settings(gitlab_url="https://gitlab.test", gitlab_token="")
    assert settings.gitlab_token_effective == "resolved-tok"


def test_gitlab_token_effective_empty_without_url() -> None:
    assert Settings(gitlab_token="").gitlab_token_effective == ""


def test_gitlab_direct_configured_true_via_resolved_git_credentials(monkeypatch) -> None:
    monkeypatch.setattr(git_credentials, "resolve_gitlab_token", lambda url: "resolved-tok")

    settings = Settings(
        gitlab_url="https://gitlab.test", gitlab_token="",
        gitlab_projects="equipe/projet", gitlab_assignee_username="corentin",
    )
    assert settings.gitlab_direct_configured is True


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
    assert settings.priority_value_base == 4.0
    assert settings.urgency_horizon_days == 14
    assert settings.urgency_peak == 8.0
    assert settings.default_fibonacci_points == 3


def test_cognitive_dip_defaults() -> None:
    settings = Settings()
    assert settings.cognitive_dip_enabled is True
    assert settings.cognitive_dip_start_hour == 13
    assert settings.cognitive_dip_trough_hour == 15
    assert settings.cognitive_dip_end_hour == 16
    assert settings.cognitive_dip_penalty == 1.0


def test_stats_window_default() -> None:
    assert Settings().stats_window_weeks == 8


def test_timer_alert_defaults() -> None:
    settings = Settings()
    assert settings.timer_idle_alert_minutes == 180
    assert settings.pomodoro_focus_minutes == 50


def test_task_type_list_defaults_to_the_seven_historical_types() -> None:
    assert Settings().task_type_list == [
        "Développement", "Revue de code", "Réunion", "Documentation",
        "Administratif", "Veille/formation", "Pilotage/dette technique",
    ]


def test_task_type_list_splits_and_strips_custom_value() -> None:
    settings = Settings(task_types=" Coaching , , Support client ,")
    assert settings.task_type_list == ["Coaching", "Support client"]
