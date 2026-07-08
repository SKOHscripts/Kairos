"""Tests du client GitLab direct (lecture seule) avec appels HTTP mockés (respx).

Symétrique de `test_timetree_source.py` : dégradation propre (jamais d'exception
hors de `fetch_assigned_issues`), cache en mémoire à TTL, aucun appel réseau réel.
"""

from __future__ import annotations

import httpx
import pytest
import respx

import app.gitlab_direct as gitlab_direct
from app.config import Settings
from app.gitlab_direct import GitLabClient, GitLabClientError, fetch_assigned_issues


@pytest.fixture(autouse=True)
def _clear_cache():
    gitlab_direct._cache.clear()
    yield
    gitlab_direct._cache.clear()


def _configured_settings(**overrides) -> Settings:
    defaults = dict(
        gitlab_url="https://gitlab.test",
        gitlab_token="tok",
        gitlab_projects="equipe/projet",
        gitlab_assignee_username="corentin",
    )
    defaults.update(overrides)
    return Settings(**defaults)


def _issue_payload(iid: int, **overrides) -> dict:
    payload = {
        "iid": iid, "title": f"Issue {iid}", "state": "opened",
        "assignees": [{"username": "corentin"}], "due_date": None,
    }
    payload.update(overrides)
    return payload


@respx.mock
def test_client_lists_open_issues_assigned_to_username() -> None:
    respx.get("https://gitlab.test/api/v4/projects/equipe%2Fprojet/issues").mock(
        return_value=httpx.Response(200, json=[_issue_payload(5, due_date="2026-07-10")])
    )
    client = GitLabClient("https://gitlab.test", "tok")

    issues = client.list_open_issues_assigned_to("equipe/projet", "corentin")

    assert len(issues) == 1
    assert issues[0].project == "equipe/projet"
    assert issues[0].iid == 5
    assert issues[0].state == "opened"
    assert issues[0].assignee_list == ["corentin"]
    assert issues[0].due_date == "2026-07-10"


@respx.mock
def test_client_follows_pagination() -> None:
    route = respx.get("https://gitlab.test/api/v4/projects/equipe%2Fprojet/issues").mock(
        side_effect=[
            httpx.Response(200, json=[_issue_payload(i) for i in range(1, 101)]),
            httpx.Response(200, json=[_issue_payload(101)]),
        ]
    )
    client = GitLabClient("https://gitlab.test", "tok")

    issues = client.list_open_issues_assigned_to("equipe/projet", "corentin")

    assert route.call_count == 2
    assert len(issues) == 101


@respx.mock
def test_client_raises_gitlab_client_error_on_http_error() -> None:
    respx.get("https://gitlab.test/api/v4/projects/equipe%2Fprojet/issues").mock(
        return_value=httpx.Response(500)
    )
    client = GitLabClient("https://gitlab.test", "tok")

    with pytest.raises(GitLabClientError):
        client.list_open_issues_assigned_to("equipe/projet", "corentin")


def test_fetch_assigned_issues_not_configured_degrades_cleanly() -> None:
    result = fetch_assigned_issues(Settings())

    assert result.ok is False
    assert result.issues == []


@respx.mock
def test_fetch_assigned_issues_merges_all_configured_projects() -> None:
    respx.get("https://gitlab.test/api/v4/projects/equipe%2Fa/issues").mock(
        return_value=httpx.Response(200, json=[_issue_payload(1)])
    )
    respx.get("https://gitlab.test/api/v4/projects/equipe%2Fb/issues").mock(
        return_value=httpx.Response(200, json=[_issue_payload(2)])
    )
    settings = _configured_settings(gitlab_projects="equipe/a,equipe/b")

    result = fetch_assigned_issues(settings)

    assert result.ok is True
    assert {i.project for i in result.issues} == {"equipe/a", "equipe/b"}
    assert len(result.issues) == 2


@respx.mock
def test_fetch_assigned_issues_degrades_on_api_error() -> None:
    respx.get("https://gitlab.test/api/v4/projects/equipe%2Fprojet/issues").mock(
        return_value=httpx.Response(403)
    )
    settings = _configured_settings()

    result = fetch_assigned_issues(settings)

    assert result.ok is False
    assert result.issues == []
    assert "Échec de l'appel à l'API GitLab" in result.detail


@respx.mock
def test_fetch_assigned_issues_uses_cache_within_ttl() -> None:
    route = respx.get("https://gitlab.test/api/v4/projects/equipe%2Fprojet/issues").mock(
        return_value=httpx.Response(200, json=[_issue_payload(1)])
    )
    settings = _configured_settings(gitlab_cache_ttl_minutes=30)

    fetch_assigned_issues(settings)
    fetch_assigned_issues(settings)

    assert route.call_count == 1  # le second appel sert le cache


@respx.mock
def test_fetch_assigned_issues_ignored_when_pilotage_configured() -> None:
    """`gitlab_direct_configured` est faux si `pilotage_database_path` est
    renseigné : le cache pilotage (zéro appel réseau) prime toujours."""
    settings = _configured_settings(pilotage_database_path="/tmp/pilotage.db")

    result = fetch_assigned_issues(settings)

    assert result.ok is False
