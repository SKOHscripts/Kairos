"""Tests du launcher de bureau (`app/launcher.py`) : choix de port uniquement —
`main()` (ouverture navigateur + `uvicorn.run` bloquant) n'est pas exercé ici."""

from __future__ import annotations

import socket

from app.launcher import _pick_port, _port_available


def test_port_available_true_for_a_free_port() -> None:
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as probe:
        probe.bind(("127.0.0.1", 0))
        free_port = probe.getsockname()[1]
    assert _port_available(free_port) is True


def test_pick_port_returns_preferred_when_free() -> None:
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as probe:
        probe.bind(("127.0.0.1", 0))
        free_port = probe.getsockname()[1]
    assert _pick_port(preferred=free_port) == free_port


def test_pick_port_skips_occupied_port() -> None:
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as occupied:
        occupied.bind(("127.0.0.1", 0))
        occupied.listen(1)
        occupied_port = occupied.getsockname()[1]
        assert _pick_port(preferred=occupied_port) != occupied_port
