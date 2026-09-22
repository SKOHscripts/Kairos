"""Tests des mises à jour (`app/updates.py`, `app/build_info.py`, routes
`/kairos/updates/*`, `packaging/write_build_info.py`,
`packaging/publish_gitlab_release.py`). Appels HTTP mockés (respx) ; l'état
est isolé par test (fixture `_isolated_updates` de conftest)."""

from __future__ import annotations

import hashlib
import http.server
import json
import os
import sys
import threading
import time
from pathlib import Path

import httpx
import pytest
import respx
from starlette.testclient import TestClient

from app import build_info, main, updates
from app.config import Settings, get_settings

sys.path.insert(0, str(Path(__file__).resolve().parent.parent / "packaging"))
import publish_gitlab_release  # noqa: E402
import write_build_info  # noqa: E402

# Capturée à l'import, avant que la fixture de conftest ne la remplace.
_DEFAULT_SOURCE = build_info.default_source
GITLAB = "https://gitlab.entreprise.test"
GL_RELEASES = f"{GITLAB}/api/v4/projects/outils%2Fkairos/releases?per_page=20"
GH_LATEST = "https://api.github.com/repos/SKOHscripts/Kairos/releases/latest"


def _gitlab_settings(**overrides) -> Settings:
    values = {"update_server_url": GITLAB, "update_project": "outils/kairos", "update_token": "glpat-x"}
    values.update(overrides)
    return Settings(**values)


def _gitlab_release(tag: str = "v9.1.0", files: dict[str, bytes] | None = None, **extra) -> dict:
    links = [
        {"name": name, "url": f"{GITLAB}/api/v4/projects/7/packages/generic/kairos/{tag[1:]}/{name}"}
        for name in (files or {})
    ]
    return {"tag_name": tag, "_links": {"self": f"{GITLAB}/outils/kairos/-/releases/{tag}"},
            "assets": {"links": links}, **extra}


def _sums(files: dict[str, bytes]) -> bytes:
    return "".join(f"{hashlib.sha256(data).hexdigest()}  {name}\n" for name, data in files.items()).encode()


def _wait_phase(*phases: str, timeout: float = 5.0) -> dict:
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        state = updates.snapshot(_gitlab_settings(), trigger_check=False)
        if state["install"]["phase"] in phases:
            return state
        time.sleep(0.02)
    raise AssertionError(f"phase jamais atteinte : {phases}")


# --- Versions et sources ------------------------------------------------------


@pytest.mark.parametrize("text, expected", [
    ("v2.6.0", (2, 6, 0)), ("2.10.3", (2, 10, 3)), (" v1.0.0 ", (1, 0, 0)),
    ("v2.6.0-rc1", None), ("latest", None), ("", None), (None, None),
])
def test_parse_version(text, expected) -> None:
    assert updates.parse_version(text) == expected


@pytest.mark.parametrize("remote, expected", [
    ("https://github.com/SKOHscripts/Kairos.git", ("https://github.com", "SKOHscripts/Kairos")),
    ("git@github.com:SKOHscripts/Kairos.git", ("https://github.com", "SKOHscripts/Kairos")),
    ("ssh://git@gitlab.corp.fr:2222/outils/dev/kairos.git", ("https://gitlab.corp.fr", "outils/dev/kairos")),
    ("https://jean:glpat-secret@gitlab.corp.fr/outils/kairos", ("https://gitlab.corp.fr", "outils/kairos")),
    ("https://gitlab.corp.fr:8443/outils/kairos.git", ("https://gitlab.corp.fr:8443", "outils/kairos")),
    ("/chemin/local/kairos", None),
    ("https://gitlab.corp.fr/seul", None),
])
def test_parse_remote(remote, expected) -> None:
    assert build_info.parse_remote(remote) == expected


def test_settings_override_default_source(monkeypatch) -> None:
    monkeypatch.setattr(build_info, "default_source", lambda: ("https://github.com", "SKOHscripts/Kairos"))
    assert Settings().update_source == ("https://github.com", "SKOHscripts/Kairos")
    assert _gitlab_settings().update_source == (GITLAB, "outils/kairos")


def test_github_token_never_resolved_from_git(monkeypatch) -> None:
    """Un jeton git GitHub a souvent des droits d'écriture : jamais emprunté."""
    monkeypatch.setattr("app.git_credentials.resolve_gitlab_token", lambda url: pytest.fail("appelé"))
    settings = Settings(update_server_url="https://github.com", update_project="a/b")
    assert settings.update_token_effective == ""


def test_source_provider_and_trusted_hosts() -> None:
    github = updates.Source("https://github.com", "SKOHscripts/Kairos", "t")
    assert github.provider == "github"
    assert github.releases_url == "https://github.com/SKOHscripts/Kairos/releases"
    assert github.trusts("https://api.github.com/repos/x")
    assert not github.trusts("https://objects.githubusercontent.com/x")
    gitlab = updates.Source(GITLAB, "outils/kairos", "t")
    assert gitlab.provider == "gitlab"
    assert gitlab.releases_url == f"{GITLAB}/outils/kairos/-/releases"
    assert gitlab.trusts(f"{GITLAB}/api/v4/x")
    assert not gitlab.trusts("https://stockage.test/x")
    assert gitlab.auth_headers() == {"PRIVATE-TOKEN": "t"}


def test_build_info_reads_generated_file(monkeypatch, tmp_path) -> None:
    generated = tmp_path / "gen.py"
    generated.write_text(write_build_info.render("v3.2.1", "https://gitlab.corp.fr/", "/outils/kairos/"))
    namespace: dict = {}
    exec(generated.read_text(), namespace)
    fake = type("Gen", (), {k: v for k, v in namespace.items() if k.isupper()})
    monkeypatch.setattr(build_info, "_generated", lambda: fake)
    build_info.version.cache_clear()
    try:
        assert build_info.version() == "3.2.1"
        assert _DEFAULT_SOURCE.__wrapped__() == ("https://gitlab.corp.fr", "outils/kairos")
    finally:
        build_info.version.cache_clear()


# --- Lecture des releases -----------------------------------------------------


@respx.mock
def test_github_latest_public_uses_direct_links() -> None:
    route = respx.get(GH_LATEST).mock(return_value=httpx.Response(200, json={
        "tag_name": "v9.0.0", "html_url": "https://github.com/SKOHscripts/Kairos/releases/tag/v9.0.0",
        "assets": [{"name": "SHA256SUMS", "url": "https://api.github.com/assets/1",
                    "browser_download_url": "https://github.com/dl/SHA256SUMS"}],
    }))
    release = updates.fetch_latest_release(updates.Source("https://github.com", "SKOHscripts/Kairos"))
    assert release.version == (9, 0, 0)
    assert release.assets == {"SHA256SUMS": "https://github.com/dl/SHA256SUMS"}
    assert "authorization" not in route.calls.last.request.headers


@respx.mock
def test_github_latest_private_uses_api_links_with_token() -> None:
    route = respx.get(GH_LATEST).mock(return_value=httpx.Response(200, json={
        "tag_name": "v9.0.0", "html_url": "x",
        "assets": [{"name": "SHA256SUMS", "url": "https://api.github.com/assets/1",
                    "browser_download_url": "https://github.com/dl/SHA256SUMS"}],
    }))
    release = updates.fetch_latest_release(updates.Source("https://github.com", "SKOHscripts/Kairos", "ghp"))
    assert release.assets == {"SHA256SUMS": "https://api.github.com/assets/1"}
    assert route.calls.last.request.headers["authorization"] == "Bearer ghp"


@respx.mock
def test_gitlab_latest_skips_upcoming_and_prereleases() -> None:
    route = respx.get(GL_RELEASES).mock(return_value=httpx.Response(200, json=[
        _gitlab_release("v9.2.0", upcoming_release=True),
        _gitlab_release("v9.1.1-rc1"),
        _gitlab_release("v9.1.0", {"SHA256SUMS": b""}),
    ]))
    release = updates.fetch_latest_release(updates.Source(GITLAB, "outils/kairos", "glpat-x"))
    assert release.tag == "v9.1.0"
    assert list(release.assets) == ["SHA256SUMS"]
    assert route.calls.last.request.headers["private-token"] == "glpat-x"


@respx.mock
@pytest.mark.parametrize("status, fragment", [(401, "Accès refusé"), (404, "introuvable"), (500, "HTTP 500")])
def test_forge_errors_become_readable_messages(status, fragment) -> None:
    respx.get(GL_RELEASES).mock(return_value=httpx.Response(status))
    with pytest.raises(updates.UpdateError, match=fragment):
        updates.fetch_latest_release(updates.Source(GITLAB, "outils/kairos"))


@respx.mock
def test_token_is_dropped_on_redirect_to_another_host() -> None:
    respx.get(f"{GITLAB}/fichier").mock(return_value=httpx.Response(
        302, headers={"location": "https://stockage.test/fichier?sig=1"}))
    storage = respx.get("https://stockage.test/fichier?sig=1").mock(return_value=httpx.Response(200, text="ok"))
    source = updates.Source(GITLAB, "outils/kairos", "glpat-x")
    with httpx.Client() as client:
        response = updates._send(client, source, f"{GITLAB}/fichier", accept="*/*")
    assert response.text == "ok"
    assert "private-token" not in storage.calls.last.request.headers


def test_plain_http_refused_outside_loopback() -> None:
    with pytest.raises(updates.UpdateError, match="HTTPS"):
        updates._check_scheme("http://gitlab.corp.fr/x")
    updates._check_scheme("http://127.0.0.1:9000/x")


def test_parse_checksums() -> None:
    digest = "a" * 64
    text = f"{digest}  kairos-linux-x86_64\n{digest.upper()} *kairos.apk\nligne invalide\n"
    assert updates.parse_checksums(text) == {"kairos-linux-x86_64": digest, "kairos.apk": digest}


# --- État, bandeau, notification ---------------------------------------------


@respx.mock
def test_check_now_reports_available_update(monkeypatch) -> None:
    monkeypatch.setattr(build_info, "version", lambda: "9.0.0")
    respx.get(GL_RELEASES).mock(return_value=httpx.Response(200, json=[_gitlab_release("v9.1.0")]))
    settings = _gitlab_settings()
    updates.check_now(settings)
    state = updates.snapshot(settings, trigger_check=False)
    assert state["available"] and state["latest"] == "9.1.0" and state["current"] == "9.0.0"
    assert state["can_install"] is False  # installation depuis les sources
    assert state["command"] == updates.SOURCE_COMMAND
    # Persisté : un redémarrage retrouve l'état sans nouvel appel réseau.
    updates._state.loaded = False
    updates._state.latest = None
    assert updates.snapshot(settings, trigger_check=False)["latest"] == "9.1.0"


@respx.mock
def test_offline_check_keeps_last_known_release(monkeypatch) -> None:
    monkeypatch.setattr(build_info, "version", lambda: "9.0.0")
    route = respx.get(GL_RELEASES)
    route.mock(return_value=httpx.Response(200, json=[_gitlab_release("v9.1.0")]))
    settings = _gitlab_settings()
    updates.check_now(settings)
    route.mock(side_effect=httpx.ConnectError("hors ligne"))
    updates.check_now(settings)
    state = updates.snapshot(settings, trigger_check=False)
    assert state["available"] and "injoignable" in state["error"]


def test_no_update_for_unknown_or_newer_version(monkeypatch) -> None:
    settings = _gitlab_settings()
    updates._state.loaded = True
    updates._state.source_key = f"{GITLAB}|outils/kairos"
    updates._state.latest = updates.Release("v9.1.0", "", {})
    monkeypatch.setattr(build_info, "version", lambda: build_info.UNKNOWN_VERSION)
    assert not updates.snapshot(settings, trigger_check=False)["available"]
    monkeypatch.setattr(build_info, "version", lambda: "9.2.0")
    assert not updates.snapshot(settings, trigger_check=False)["available"]


def test_changed_source_hides_release_of_previous_source(monkeypatch) -> None:
    monkeypatch.setattr(build_info, "version", lambda: "9.0.0")
    updates._state.loaded = True
    updates._state.source_key = f"{GITLAB}|outils/kairos"
    updates._state.latest = updates.Release("v9.1.0", "", {})
    other = _gitlab_settings(update_project="autre/projet")
    assert not updates.snapshot(other, trigger_check=False)["available"]


def test_dismiss_and_single_notification_per_version(monkeypatch) -> None:
    monkeypatch.setattr(build_info, "version", lambda: "9.0.0")
    updates._state.loaded = True
    updates._state.source_key = f"{GITLAB}|outils/kairos"
    updates._state.latest = updates.Release("v9.1.0", "", {})
    assert updates.claim_notification("v9.1.0") is True
    assert updates.claim_notification("v9.1.0") is False
    assert updates.claim_notification("v0.0.1") is False
    updates.dismiss("v9.1.0")
    assert updates.snapshot(_gitlab_settings(), trigger_check=False)["dismissed"] is True


def test_background_check_respects_interval_and_switch(monkeypatch) -> None:
    calls = []
    monkeypatch.setattr(updates, "_run_check", lambda source: calls.append(source))
    monkeypatch.setattr("app.updates.threading.Thread",
                        lambda target, args, **kw: type("T", (), {"start": lambda self: target(*args)})())
    settings = _gitlab_settings()
    updates.snapshot(settings)  # KAIROS_UPDATE_CHECK=0 (conftest)
    assert calls == []
    monkeypatch.setenv("KAIROS_UPDATE_CHECK", "1")
    updates.snapshot(_gitlab_settings(update_check_enabled=False))
    assert calls == []
    updates.snapshot(settings)
    assert len(calls) == 1
    from datetime import datetime, timezone
    with updates._lock:
        updates._state.checking = False
        updates._state.checked_at = datetime.now(timezone.utc)
        updates._state.source_key = f"{GITLAB}|outils/kairos"
    updates.snapshot(settings)
    assert len(calls) == 1  # vérifié il y a moins de 6 h


# --- Installation -------------------------------------------------------------


def _prepare_release(monkeypatch, kind: str, files: dict[str, bytes], sums: bytes | None = None):
    monkeypatch.setattr(build_info, "version", lambda: "9.0.0")
    monkeypatch.setattr(build_info, "install_kind", lambda: kind)
    all_files = dict(files)
    all_files["SHA256SUMS"] = _sums(files) if sums is None else sums
    release = _gitlab_release("v9.1.0", all_files)
    respx.get(GL_RELEASES).mock(return_value=httpx.Response(200, json=[release]))
    for link in release["assets"]["links"]:
        respx.get(link["url"]).mock(return_value=httpx.Response(200, content=all_files[link["name"]]))
    updates.check_now(_gitlab_settings())


@respx.mock
def test_android_install_downloads_and_verifies_apk(monkeypatch) -> None:
    apk = b"PK-faux-apk" * 1000
    _prepare_release(monkeypatch, "android", {updates.ANDROID_ASSET: apk})
    state = updates.snapshot(_gitlab_settings(), trigger_check=False)
    assert state["can_install"]
    updates.start_install(_gitlab_settings(), port=8001)
    _wait_phase("ready", "error")
    assert updates._state.install.phase == "ready", updates._state.install.message
    assert (updates.data_dir() / updates.ANDROID_APK_PATH).read_bytes() == apk


@respx.mock
def test_checksum_mismatch_installs_nothing(monkeypatch) -> None:
    apk = b"PK-faux-apk"
    _prepare_release(monkeypatch, "android", {updates.ANDROID_ASSET: apk},
                     sums=f"{'0' * 64}  {updates.ANDROID_ASSET}\n".encode())
    updates.start_install(_gitlab_settings(), port=8001)
    state = _wait_phase("error", "ready")
    assert "Somme de contrôle incorrecte" in state["install"]["message"]
    assert not (updates.data_dir() / updates.ANDROID_APK_PATH).exists()
    assert not list((updates.data_dir() / "updates").glob("*.part"))


@respx.mock
def test_release_without_checksums_is_never_installed(monkeypatch) -> None:
    monkeypatch.setattr(build_info, "version", lambda: "9.0.0")
    monkeypatch.setattr(build_info, "install_kind", lambda: "android")
    respx.get(GL_RELEASES).mock(return_value=httpx.Response(200, json=[
        _gitlab_release("v9.1.0", {updates.ANDROID_ASSET: b""})]))
    updates.check_now(_gitlab_settings())
    state = updates.snapshot(_gitlab_settings(), trigger_check=False)
    assert state["available"] and not state["can_install"]
    assert "SHA256SUMS" in state["install_blocker"]
    with pytest.raises(updates.UpdateError, match="SHA256SUMS"):
        updates.start_install(_gitlab_settings(), port=8001)


@respx.mock
def test_desktop_install_replaces_executable_then_restarts(monkeypatch, tmp_path) -> None:
    asset = updates._asset_for_kind("desktop") or "kairos-linux-x86_64"
    monkeypatch.setattr(updates, "DESKTOP_ASSETS", {sys.platform: asset})
    new_exe = b"#!/bin/sh\necho nouvelle version\n"
    _prepare_release(monkeypatch, "desktop", {asset: new_exe})
    exe = tmp_path / "kairos"
    exe.write_bytes(b"ancienne version")
    monkeypatch.setattr(updates.sys, "executable", str(exe))
    restarted = []
    monkeypatch.setattr(updates, "restart_desktop", lambda path, port: restarted.append((path, port)))
    updates.start_install(_gitlab_settings(), port=8123)
    _wait_phase("restarting", "error")
    assert updates._state.install.phase == "restarting", updates._state.install.message
    assert exe.read_bytes() == new_exe
    assert restarted == [(exe.resolve(), 8123)]
    if os.name != "nt":
        assert os.access(exe, os.X_OK)


def test_replace_executable_reports_protected_folder(monkeypatch, tmp_path) -> None:
    new = tmp_path / "new"
    new.write_bytes(b"x")
    missing_dir_exe = tmp_path / "absent" / "kairos"
    with pytest.raises(updates.UpdateError, match="Impossible de remplacer"):
        updates.replace_executable(new, missing_dir_exe)


def test_restart_desktop_passes_port_and_resets_pyinstaller_env(monkeypatch, tmp_path) -> None:
    launched, timers = [], []
    monkeypatch.setattr(updates.subprocess, "Popen", lambda args, **kw: launched.append((args, kw)))
    monkeypatch.setattr(updates.threading, "Timer",
                        lambda delay, fn: type("T", (), {"start": lambda self: timers.append(delay)})())
    updates.restart_desktop(tmp_path / "kairos", 8123)
    (args, kwargs), = launched
    assert args == [str(tmp_path / "kairos")]
    assert kwargs["env"]["KAIROS_RESTART_PORT"] == "8123"
    assert kwargs["env"]["PYINSTALLER_RESET_ENVIRONMENT"] == "1"
    assert timers == [updates._RESTART_DELAY]


# --- Routes -------------------------------------------------------------------


@pytest.fixture
def client(tmp_settings_dir):
    get_settings.cache_clear()
    try:
        yield TestClient(main.app)
    finally:
        get_settings.cache_clear()


def test_status_route_returns_json(client) -> None:
    body = client.get("/kairos/updates/status").json()
    assert body["current"] and body["kind"] == "source"
    assert body["install"]["phase"] == "idle"


def test_install_route_requires_fetch_header_and_local_client(client, monkeypatch) -> None:
    assert client.post("/kairos/updates/install").status_code == 403
    headers = {"X-Requested-With": "fetch"}
    assert client.post("/kairos/updates/install", headers=headers).status_code == 403  # client distant
    monkeypatch.setattr(main.desktop_notify, "loopback_client", lambda host: True)
    resp = client.post("/kairos/updates/install", headers=headers)
    assert resp.status_code == 409 and "vérification" in resp.json()["error"]


def test_notified_route_claims_once(client, monkeypatch) -> None:
    updates._state.loaded = True
    updates._state.latest = updates.Release("v9.1.0", "", {})
    headers = {"X-Requested-With": "fetch"}
    assert client.post("/kairos/updates/notified", data={"tag": "v9.1.0"}).status_code == 403
    assert client.post("/kairos/updates/notified", data={"tag": "v9.1.0"}, headers=headers).json() == {"claimed": True}
    assert client.post("/kairos/updates/notified", data={"tag": "v9.1.0"}, headers=headers).json() == {"claimed": False}


def test_dismiss_route_without_js_redirects_to_local_path_only(client) -> None:
    resp = client.post("/kairos/updates/dismiss", data={"tag": "v9.1.0", "next": "//evil.test/x"},
                       follow_redirects=False)
    assert resp.status_code == 303 and resp.headers["location"] == "/"
    resp = client.post("/kairos/updates/dismiss", data={"tag": "v9.1.0", "next": "/kairos/notes"},
                       follow_redirects=False)
    assert resp.headers["location"] == "/kairos/notes"


def test_banner_rendered_when_update_available(client, monkeypatch) -> None:
    monkeypatch.setattr(build_info, "version", lambda: "9.0.0")
    monkeypatch.setattr(build_info, "default_source", lambda: (GITLAB, "outils/kairos"))
    updates._state.loaded = True
    updates._state.source_key = f"{GITLAB}|outils/kairos"
    updates._state.latest = updates.Release("v9.1.0", f"{GITLAB}/outils/kairos/-/releases/v9.1.0", {})
    monkeypatch.setattr("app.git_credentials.resolve_gitlab_token", lambda url: "")
    html = client.get("/kairos/notes").text
    assert 'id="mj-update"' in html and "<strong>9.1.0</strong> est disponible" in html
    assert 'id="mj-update" class="banner mj-update" role="status"' in html
    assert "hidden>" not in html.split('id="mj-update"')[1].split(">")[0] + ">"


def test_settings_page_shows_update_section(client) -> None:
    html = client.get("/kairos/settings?updates=1").text
    assert 'id="mises-a-jour"' in html and "Version installée" in html
    assert 'formaction="/kairos/updates/check"' in html
    assert 'name="update_token"' in html and 'type="password" id="f_update_token"' in html


# --- Publication GitLab -------------------------------------------------------


def test_publish_gitlab_release_uploads_files_and_creates_release(tmp_path) -> None:
    out = tmp_path / "out"
    out.mkdir()
    (out / "kairos-linux-x86_64").write_bytes(b"linux")
    (out / "kairos-android-arm64.apk").write_bytes(b"apk")
    received = []

    class Handler(http.server.BaseHTTPRequestHandler):
        def _record(self):
            length = int(self.headers.get("Content-Length") or 0)
            received.append((self.command, self.path, self.headers.get("JOB-TOKEN"), self.rfile.read(length)))
            self.send_response(201)
            self.end_headers()
            self.wfile.write(b"{}")

        do_PUT = do_POST = _record

        def log_message(self, *args):
            pass

    server = http.server.HTTPServer(("127.0.0.1", 0), Handler)
    thread = threading.Thread(target=server.serve_forever, daemon=True)
    thread.start()
    try:
        publish_gitlab_release.publish(
            out, api_url=f"http://127.0.0.1:{server.server_address[1]}/api/v4",
            project_id="7", tag="v9.1.0", token="jobtok")
    finally:
        server.shutdown()
    puts = [(path, body) for method, path, _, body in received if method == "PUT"]
    assert [p.rsplit("/", 1)[1] for p, _ in puts] == ["kairos-android-arm64.apk", "kairos-linux-x86_64", "SHA256SUMS"]
    assert all(p.startswith("/api/v4/projects/7/packages/generic/kairos/9.1.0/") for p, _ in puts)
    sums = updates.parse_checksums(puts[-1][1].decode())
    assert sums["kairos-linux-x86_64"] == hashlib.sha256(b"linux").hexdigest()
    method, path, token, body = received[-1]
    assert (method, path, token) == ("POST", "/api/v4/projects/7/releases", "jobtok")
    payload = json.loads(body)
    assert payload["tag_name"] == "v9.1.0"
    assert [link["name"] for link in payload["assets"]["links"]] == [
        "kairos-android-arm64.apk", "kairos-linux-x86_64", "SHA256SUMS"]
