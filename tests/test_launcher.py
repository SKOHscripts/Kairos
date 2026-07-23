"""Tests du launcher de bureau (`app/launcher.py`) : choix de port et verrou
d'instance unique — `main()` (ouverture navigateur + `uvicorn.run` bloquant)
n'est pas exercé ici."""

from __future__ import annotations

import http.server
import os
import socket
import sys
import threading

from app.desktop_browser import (
    _desktop_entry_content,
    find_app_capable_browser,
    install_linux_desktop_entry,
    launch_app_window,
)
from app.launcher import (
    _clear_lock,
    _ensure_std_streams,
    _instance_already_running,
    _open_browser,
    _pick_port,
    _port_available,
    _read_lock_port,
    _write_lock,
)


def _free_port() -> int:
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as probe:
        probe.bind(("127.0.0.1", 0))
        return probe.getsockname()[1]


def test_port_available_true_for_a_free_port() -> None:
    assert _port_available(_free_port()) is True


def test_pick_port_returns_preferred_when_free() -> None:
    free_port = _free_port()
    assert _pick_port(preferred=free_port) == free_port


def test_pick_port_skips_occupied_port() -> None:
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as occupied:
        occupied.bind(("127.0.0.1", 0))
        occupied.listen(1)
        occupied_port = occupied.getsockname()[1]
        assert _pick_port(preferred=occupied_port) != occupied_port


def test_read_lock_port_returns_none_without_file(tmp_path, monkeypatch) -> None:
    monkeypatch.setattr("app.launcher.data_dir", lambda: tmp_path)
    assert _read_lock_port() is None


def test_write_then_read_lock_round_trip(tmp_path, monkeypatch) -> None:
    monkeypatch.setattr("app.launcher.data_dir", lambda: tmp_path)
    _write_lock(12345)
    assert _read_lock_port() == 12345


def test_read_lock_port_returns_none_on_corrupted_file(tmp_path, monkeypatch) -> None:
    monkeypatch.setattr("app.launcher.data_dir", lambda: tmp_path)
    (tmp_path / "kairos.lock").write_text("not json at all", encoding="utf-8")
    assert _read_lock_port() is None


def test_clear_lock_never_raises_if_file_absent(tmp_path, monkeypatch) -> None:
    monkeypatch.setattr("app.launcher.data_dir", lambda: tmp_path)
    _clear_lock()  # aucun fichier : ne doit jamais lever


def test_clear_lock_removes_existing_file(tmp_path, monkeypatch) -> None:
    monkeypatch.setattr("app.launcher.data_dir", lambda: tmp_path)
    _write_lock(12345)
    _clear_lock()
    assert not (tmp_path / "kairos.lock").exists()


def test_instance_already_running_false_for_a_free_port() -> None:
    """Verrou obsolète (process tué sans nettoyage) : port muet, traité comme
    « pas d'instance » plutôt que de bloquer les lancements suivants."""
    assert _instance_already_running(_free_port()) is False


def test_instance_already_running_true_for_a_responding_server() -> None:
    class _Handler(http.server.BaseHTTPRequestHandler):
        def do_GET(self):  # noqa: N802 (nom imposé par BaseHTTPRequestHandler)
            self.send_response(200)
            self.end_headers()

        def log_message(self, *args):
            pass  # silence les logs d'accès du serveur de test

    server = http.server.HTTPServer(("127.0.0.1", 0), _Handler)
    thread = threading.Thread(target=server.serve_forever, daemon=True)
    thread.start()
    try:
        assert _instance_already_running(server.server_address[1]) is True
    finally:
        server.shutdown()
        thread.join()


def test_ensure_std_streams_replaces_none_stdout_and_stderr(monkeypatch) -> None:
    """Régression Windows : PyInstaller en mode fenêtré (console=False) laisse
    sys.stdout/stderr à None (pas de console attachée), ce qui fait planter
    uvicorn dès la configuration de son logging par défaut."""
    monkeypatch.setattr(sys, "stdout", None)
    monkeypatch.setattr(sys, "stderr", None)

    _ensure_std_streams()

    assert sys.stdout is not None
    assert sys.stderr is not None
    assert sys.stdout.isatty() is False
    sys.stdout.write("ignoré")  # ne doit pas lever
    sys.stdout.flush()


def test_ensure_std_streams_leaves_real_streams_untouched() -> None:
    real_stdout, real_stderr = sys.stdout, sys.stderr

    _ensure_std_streams()

    assert sys.stdout is real_stdout
    assert sys.stderr is real_stderr


def test_open_browser_uses_sanitized_environment(monkeypatch) -> None:
    """Régression Linux : PyInstaller (onefile) détourne LD_LIBRARY_PATH vers
    ses bibliothèques embarquées, ce qu'hériterait le navigateur/`xdg-open`
    lancé par `webbrowser.open` sans cette précaution (voir
    `app/subprocess_env.py` pour le détail du bug, `rl_print_keybinding`)."""
    monkeypatch.delenv("KAIROS_NO_BROWSER", raising=False)
    monkeypatch.setenv("LD_LIBRARY_PATH", "/tmp/_MEIxxxxxx")
    monkeypatch.delenv("LD_LIBRARY_PATH_ORIG", raising=False)
    # Aucun navigateur Chromium trouvé : force le repli `webbrowser.open`,
    # déterministe qu'un Chromium soit installé ou non sur la machine de test.
    monkeypatch.setattr("app.launcher.find_app_capable_browser", lambda: None)
    seen = {}

    def fake_open(url):
        seen["LD_LIBRARY_PATH"] = os.environ.get("LD_LIBRARY_PATH")
        return True

    monkeypatch.setattr("app.launcher.webbrowser.open", fake_open)

    _open_browser("http://127.0.0.1:8001")

    assert seen["LD_LIBRARY_PATH"] is None
    # L'environnement du process est restauré après l'appel.
    assert os.environ["LD_LIBRARY_PATH"] == "/tmp/_MEIxxxxxx"


def test_open_browser_skips_when_disabled_via_env(monkeypatch) -> None:
    """KAIROS_NO_BROWSER (voir packaging/smoke_test.py) : les lancements
    automatisés ne doivent pas faire apparaître un vrai navigateur."""
    monkeypatch.setenv("KAIROS_NO_BROWSER", "1")
    called = False

    def fake_open(url):
        nonlocal called
        called = True
        return True

    monkeypatch.setattr("app.launcher.webbrowser.open", fake_open)
    # Ni la recherche de navigateur d'application ni `webbrowser.open` ne
    # doivent être tentés : KAIROS_NO_BROWSER coupe tout avant.
    monkeypatch.setattr(
        "app.launcher.find_app_capable_browser",
        lambda: (_ for _ in ()).throw(AssertionError("ne doit pas être appelé")),
    )

    _open_browser("http://127.0.0.1:8001")

    assert called is False


def test_open_browser_skips_disabled_via_env_without_app_window_attempt(monkeypatch) -> None:
    """Variante explicite : KAIROS_NO_BROWSER doit rester le tout premier
    contrôle, avant même la tentative de fenêtre d'application (comportement
    déjà garanti avant ce module, à ne jamais régresser)."""
    monkeypatch.setenv("KAIROS_NO_BROWSER", "1")
    launch_called = False

    def fake_launch(browser_path, url):
        nonlocal launch_called
        launch_called = True
        return True

    monkeypatch.setattr("app.launcher.find_app_capable_browser", lambda: "/usr/bin/chromium")
    monkeypatch.setattr("app.launcher.launch_app_window", fake_launch)
    monkeypatch.setattr(
        "app.launcher.webbrowser.open",
        lambda url: (_ for _ in ()).throw(AssertionError("ne doit pas être appelé")),
    )

    _open_browser("http://127.0.0.1:8001")

    assert launch_called is False


def test_open_browser_prefers_app_window_and_skips_webbrowser(monkeypatch) -> None:
    """Quand un navigateur Chromium est trouvé et se lance avec succès,
    `webbrowser.open` (repli) ne doit jamais être appelé."""
    monkeypatch.delenv("KAIROS_NO_BROWSER", raising=False)
    monkeypatch.setattr("app.launcher.find_app_capable_browser", lambda: "/usr/bin/chromium")
    monkeypatch.setattr("app.launcher.launch_app_window", lambda browser_path, url: True)
    monkeypatch.setattr(
        "app.launcher.webbrowser.open",
        lambda url: (_ for _ in ()).throw(AssertionError("ne doit pas être appelé")),
    )

    _open_browser("http://127.0.0.1:8001")  # ne doit pas lever


def test_open_browser_falls_back_when_no_app_capable_browser_found(monkeypatch) -> None:
    """Aucun navigateur Chromium détecté : repli automatique sur
    `webbrowser.open`, comportement d'origine."""
    monkeypatch.delenv("KAIROS_NO_BROWSER", raising=False)
    monkeypatch.setattr("app.launcher.find_app_capable_browser", lambda: None)
    called = False

    def fake_open(url):
        nonlocal called
        called = True
        return True

    monkeypatch.setattr("app.launcher.webbrowser.open", fake_open)

    _open_browser("http://127.0.0.1:8001")

    assert called is True


def test_open_browser_falls_back_when_app_window_launch_fails(monkeypatch) -> None:
    """Un navigateur Chromium est trouvé mais son lancement échoue
    (`launch_app_window` renvoie `False`) : repli automatique sur
    `webbrowser.open`, jamais d'erreur remontée."""
    monkeypatch.delenv("KAIROS_NO_BROWSER", raising=False)
    monkeypatch.setattr("app.launcher.find_app_capable_browser", lambda: "/usr/bin/chromium")
    monkeypatch.setattr("app.launcher.launch_app_window", lambda browser_path, url: False)
    called = False

    def fake_open(url):
        nonlocal called
        called = True
        return True

    monkeypatch.setattr("app.launcher.webbrowser.open", fake_open)

    _open_browser("http://127.0.0.1:8001")

    assert called is True


# --- app/desktop_browser.py -------------------------------------------------


def test_find_app_capable_browser_returns_none_when_nothing_found(monkeypatch) -> None:
    monkeypatch.delenv("KAIROS_BROWSER", raising=False)
    monkeypatch.setattr("app.desktop_browser.sys.platform", "linux")
    monkeypatch.setattr("app.desktop_browser.shutil.which", lambda name: None)

    assert find_app_capable_browser() is None


def test_find_app_capable_browser_returns_first_match_on_linux(monkeypatch) -> None:
    monkeypatch.delenv("KAIROS_BROWSER", raising=False)
    monkeypatch.setattr("app.desktop_browser.sys.platform", "linux")

    def fake_which(name):
        return "/usr/bin/chromium" if name == "chromium" else None

    monkeypatch.setattr("app.desktop_browser.shutil.which", fake_which)

    assert find_app_capable_browser() == "/usr/bin/chromium"


def test_find_app_capable_browser_env_override_takes_priority(monkeypatch, tmp_path) -> None:
    fake_browser = tmp_path / "my-browser"
    fake_browser.write_text("#!/bin/sh\n", encoding="utf-8")
    fake_browser.chmod(0o755)
    monkeypatch.setenv("KAIROS_BROWSER", str(fake_browser))

    def fail_which(name):  # pragma: no cover - ne doit jamais être appelé
        raise AssertionError("shutil.which ne doit pas être consulté avec KAIROS_BROWSER posé")

    monkeypatch.setattr("app.desktop_browser.shutil.which", fail_which)

    assert find_app_capable_browser() == str(fake_browser)


def test_launch_app_window_builds_argv_with_app_and_profile_flags(monkeypatch, tmp_path) -> None:
    monkeypatch.setattr("app.desktop_browser.data_dir", lambda: tmp_path)
    # Hors périmètre de ce test (couvert séparément) : neutralisé pour ne pas
    # dépendre de sys.platform/sys.frozen dans l'environnement de test.
    monkeypatch.setattr("app.desktop_browser.install_linux_desktop_entry", lambda: None)
    seen_argv = {}

    class _FakeProcess:
        pass

    def fake_popen(argv, **kwargs):
        seen_argv["argv"] = argv
        return _FakeProcess()

    monkeypatch.setattr("app.desktop_browser.subprocess.Popen", fake_popen)

    result = launch_app_window("/usr/bin/chromium", "http://127.0.0.1:8001")

    assert result is True
    argv = seen_argv["argv"]
    assert argv[0] == "/usr/bin/chromium"
    assert "--app=http://127.0.0.1:8001" in argv
    assert "--class=Kairos" in argv
    assert any(arg.startswith("--user-data-dir=") for arg in argv)
    assert any(str(tmp_path) in arg for arg in argv if arg.startswith("--user-data-dir="))


def test_launch_app_window_attempts_desktop_entry_installation(monkeypatch, tmp_path) -> None:
    """`launch_app_window` doit tenter l'installation .desktop/icônes avant de
    lancer le navigateur (best-effort — voir `install_linux_desktop_entry`)."""
    monkeypatch.setattr("app.desktop_browser.data_dir", lambda: tmp_path)
    monkeypatch.setattr("app.desktop_browser.subprocess.Popen", lambda argv, **kwargs: object())
    called = False

    def fake_install():
        nonlocal called
        called = True

    monkeypatch.setattr("app.desktop_browser.install_linux_desktop_entry", fake_install)

    launch_app_window("/usr/bin/chromium", "http://127.0.0.1:8001")

    assert called is True


def test_launch_app_window_returns_false_and_does_not_raise_on_popen_error(
    monkeypatch, tmp_path
) -> None:
    monkeypatch.setattr("app.desktop_browser.data_dir", lambda: tmp_path)

    def failing_popen(argv, **kwargs):
        raise OSError("binaire introuvable")

    monkeypatch.setattr("app.desktop_browser.subprocess.Popen", failing_popen)

    result = launch_app_window("/usr/bin/chromium", "http://127.0.0.1:8001")

    assert result is False


# --- Identité desktop Linux (.desktop + icônes) --------------------------------


def test_desktop_entry_content_has_required_fields() -> None:
    content = _desktop_entry_content("/opt/kairos/kairos")

    assert 'Exec="/opt/kairos/kairos"' in content
    assert "Icon=kairos" in content
    assert "StartupWMClass=Kairos" in content
    assert "Type=Application" in content
    assert "Name=Kairos" in content


def test_install_linux_desktop_entry_is_noop_outside_linux(monkeypatch, tmp_path) -> None:
    monkeypatch.setattr("app.desktop_browser.sys.platform", "win32")
    monkeypatch.setattr("app.desktop_browser.sys.frozen", True, raising=False)
    monkeypatch.setenv("XDG_DATA_HOME", str(tmp_path))

    install_linux_desktop_entry()

    assert list(tmp_path.iterdir()) == []


def test_install_linux_desktop_entry_is_noop_when_not_frozen(monkeypatch, tmp_path) -> None:
    monkeypatch.setattr("app.desktop_browser.sys.platform", "linux")
    monkeypatch.delattr("app.desktop_browser.sys.frozen", raising=False)
    monkeypatch.setenv("XDG_DATA_HOME", str(tmp_path))

    install_linux_desktop_entry()

    assert list(tmp_path.iterdir()) == []


def test_install_linux_desktop_entry_writes_desktop_file_and_icons(monkeypatch, tmp_path) -> None:
    monkeypatch.setattr("app.desktop_browser.sys.platform", "linux")
    monkeypatch.setattr("app.desktop_browser.sys.frozen", True, raising=False)
    monkeypatch.setattr("app.desktop_browser.sys.executable", "/opt/kairos/kairos")
    monkeypatch.setenv("XDG_DATA_HOME", str(tmp_path))

    fake_base_dir = tmp_path / "base"
    (fake_base_dir / "static").mkdir(parents=True)
    (fake_base_dir / "static" / "icon-192.png").write_bytes(b"fake-png-192")
    (fake_base_dir / "static" / "icon-512.png").write_bytes(b"fake-png-512")
    monkeypatch.setattr("app.main.BASE_DIR", fake_base_dir)

    install_linux_desktop_entry()

    desktop_file = tmp_path / "applications" / "kairos.desktop"
    assert desktop_file.is_file()
    assert "StartupWMClass=Kairos" in desktop_file.read_text(encoding="utf-8")

    icon_192 = tmp_path / "icons" / "hicolor" / "192x192" / "apps" / "kairos.png"
    icon_512 = tmp_path / "icons" / "hicolor" / "512x512" / "apps" / "kairos.png"
    assert icon_192.read_bytes() == b"fake-png-192"
    assert icon_512.read_bytes() == b"fake-png-512"


def test_install_linux_desktop_entry_never_raises_on_write_failure(monkeypatch, tmp_path) -> None:
    """Best-effort : une erreur d'écriture (dossier en lecture seule, disque
    plein...) ne doit jamais remonter à l'appelant."""
    monkeypatch.setattr("app.desktop_browser.sys.platform", "linux")
    monkeypatch.setattr("app.desktop_browser.sys.frozen", True, raising=False)
    monkeypatch.setenv("XDG_DATA_HOME", str(tmp_path))

    def failing_mkdir(*args, **kwargs):
        raise OSError("dossier en lecture seule")

    monkeypatch.setattr("app.desktop_browser.Path.mkdir", failing_mkdir)

    install_linux_desktop_entry()  # ne doit pas lever
