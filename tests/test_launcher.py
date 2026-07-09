"""Tests du launcher de bureau (`app/launcher.py`) : choix de port et verrou
d'instance unique — `main()` (ouverture navigateur + `uvicorn.run` bloquant)
n'est pas exercé ici."""

from __future__ import annotations

import http.server
import socket
import threading

from app.launcher import (
    _clear_lock,
    _instance_already_running,
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
