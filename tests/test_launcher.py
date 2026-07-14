"""Tests du launcher de bureau (`app/launcher.py`) : choix de port et verrou
d'instance unique — `main()` (ouverture navigateur + `uvicorn.run` bloquant)
n'est pas exercé ici."""

from __future__ import annotations

import http.server
import os
import socket
import sys
import threading

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

    _open_browser("http://127.0.0.1:8001")

    assert called is False
