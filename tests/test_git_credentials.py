"""Tests de la résolution du jeton GitLab via `git credential fill` / `~/.netrc`
(alternative à `GITLAB_TOKEN` dans `.env`) — jamais d'appel process/disque réel :
`subprocess.run` et l'emplacement du `.netrc` sont toujours mockés."""

from __future__ import annotations

import subprocess

import pytest

from app import git_credentials


@pytest.fixture(autouse=True)
def _clear_cache():
    git_credentials.resolve_gitlab_token.cache_clear()
    yield
    git_credentials.resolve_gitlab_token.cache_clear()


def _fake_run(stdout: str, returncode: int = 0):
    def run(*args, **kwargs):
        return subprocess.CompletedProcess(args, returncode, stdout=stdout, stderr="")
    return run


def test_resolves_via_git_credential_fill(monkeypatch) -> None:
    monkeypatch.setattr(
        git_credentials.subprocess, "run",
        _fake_run("protocol=https\nhost=gitlab.example.com\npassword=abc123\n"),
    )
    assert git_credentials.resolve_gitlab_token("https://gitlab.example.com") == "abc123"


def test_git_credential_fill_passes_protocol_and_host(monkeypatch) -> None:
    captured = {}

    def run(cmd, input, **kwargs):
        captured["cmd"] = cmd
        captured["input"] = input
        return subprocess.CompletedProcess(cmd, 0, stdout="password=xyz\n", stderr="")

    monkeypatch.setattr(git_credentials.subprocess, "run", run)

    git_credentials.resolve_gitlab_token("https://gitlab.example.com/some/path")

    assert captured["cmd"] == ["git", "credential", "fill"]
    assert "protocol=https" in captured["input"]
    assert "host=gitlab.example.com" in captured["input"]


def test_falls_back_to_netrc_when_git_credential_fill_empty(monkeypatch) -> None:
    monkeypatch.setattr(git_credentials.subprocess, "run", _fake_run(""))

    class FakeNetrc:
        def authenticators(self, host):
            assert host == "gitlab.example.com"
            return ("me", "", "netrc-token")

    monkeypatch.setattr(git_credentials.netrc, "netrc", lambda: FakeNetrc())

    assert git_credentials.resolve_gitlab_token("https://gitlab.example.com") == "netrc-token"


def test_returns_empty_string_when_nothing_found(monkeypatch) -> None:
    monkeypatch.setattr(git_credentials.subprocess, "run", _fake_run(""))

    class FakeNetrc:
        def authenticators(self, host):
            return None

    monkeypatch.setattr(git_credentials.netrc, "netrc", lambda: FakeNetrc())

    assert git_credentials.resolve_gitlab_token("https://gitlab.example.com") == ""


def test_never_raises_when_git_binary_is_missing(monkeypatch) -> None:
    def run(*args, **kwargs):
        raise FileNotFoundError("git not found")

    monkeypatch.setattr(git_credentials.subprocess, "run", run)
    monkeypatch.setattr(
        git_credentials.netrc, "netrc",
        lambda: (_ for _ in ()).throw(FileNotFoundError()),
    )

    assert git_credentials.resolve_gitlab_token("https://gitlab.example.com") == ""


def test_never_raises_on_credential_fill_timeout(monkeypatch) -> None:
    def run(*args, **kwargs):
        raise subprocess.TimeoutExpired(cmd="git", timeout=5)

    monkeypatch.setattr(git_credentials.subprocess, "run", run)
    monkeypatch.setattr(
        git_credentials.netrc, "netrc",
        lambda: (_ for _ in ()).throw(FileNotFoundError()),
    )

    assert git_credentials.resolve_gitlab_token("https://gitlab.example.com") == ""


def test_returns_empty_string_for_url_without_host(monkeypatch) -> None:
    called = {"run": False}
    monkeypatch.setattr(
        git_credentials.subprocess, "run",
        lambda *a, **k: called.__setitem__("run", True),
    )

    assert git_credentials.resolve_gitlab_token("") == ""
    assert called["run"] is False


def test_git_credential_fill_sanitizes_ld_library_path(monkeypatch) -> None:
    """Régression Linux : `git credential fill` délègue au helper configuré via
    un `sh -c` interne, qui hériterait sinon du `LD_LIBRARY_PATH` détourné par
    PyInstaller (mode onefile) vers ses propres bibliothèques embarquées."""
    monkeypatch.setenv("LD_LIBRARY_PATH", "/tmp/_MEIxxxxxx")
    monkeypatch.delenv("LD_LIBRARY_PATH_ORIG", raising=False)
    captured = {}

    def run(cmd, input, env, **kwargs):
        captured["env"] = env
        return subprocess.CompletedProcess(cmd, 0, stdout="password=xyz\n", stderr="")

    monkeypatch.setattr(git_credentials.subprocess, "run", run)

    git_credentials.resolve_gitlab_token("https://gitlab.example.com")

    assert "LD_LIBRARY_PATH" not in captured["env"]


def test_result_is_cached_per_url(monkeypatch) -> None:
    calls = {"count": 0}

    def run(*args, **kwargs):
        calls["count"] += 1
        return subprocess.CompletedProcess(args, 0, stdout="password=once\n", stderr="")

    monkeypatch.setattr(git_credentials.subprocess, "run", run)

    first = git_credentials.resolve_gitlab_token("https://gitlab.example.com")
    second = git_credentials.resolve_gitlab_token("https://gitlab.example.com")

    assert first == second == "once"
    assert calls["count"] == 1
