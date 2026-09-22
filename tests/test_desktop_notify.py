"""Tests de la notification système émise par le serveur (issue #34) — voir
docs/spec/temps-reel-chrono.md. Aucun test ne lance de vraie notification :
`subprocess` est monkeypatché, le point d'intérêt étant la commande construite
et les garde-fous qui décident d'appeler, ou non."""

from __future__ import annotations

import subprocess

import pytest

from app import desktop_notify


# ---------------------------------------------------------------- garde-fou
# « le client est-il la machine hôte ? »


@pytest.mark.parametrize(
    "host", ["127.0.0.1", "::1", "localhost", "::ffff:127.0.0.1"]
)
def test_loopback_client_accepts_the_host_machine(host: str) -> None:
    assert desktop_notify.loopback_client(host) is True


@pytest.mark.parametrize("host", ["192.168.1.42", "10.0.0.7", "", None, "0.0.0.0"])
def test_loopback_client_refuses_anything_else(host) -> None:
    """Une notification système s'affiche sur l'écran du serveur : dans le
    doute, on ne notifie pas (le coût d'un faux positif est une notification sur
    l'écran de quelqu'un d'autre)."""
    assert desktop_notify.loopback_client(host) is False


# ---------------------------------------------------------------- disponibilité


def test_is_available_looks_for_notify_send_on_linux(monkeypatch) -> None:
    monkeypatch.setattr(desktop_notify.sys, "platform", "linux")
    monkeypatch.setattr(desktop_notify.shutil, "which", lambda name: "/usr/bin/notify-send")
    assert desktop_notify.is_available() is True

    monkeypatch.setattr(desktop_notify.shutil, "which", lambda name: None)
    assert desktop_notify.is_available() is False


def test_is_available_is_false_on_unsupported_platforms(monkeypatch) -> None:
    """macOS est hors périmètre du dépôt : pas de détection dédiée, repli propre."""
    monkeypatch.setattr(desktop_notify.sys, "platform", "darwin")
    assert desktop_notify.is_available() is False


# ---------------------------------------------------------------- envoi Linux


def test_send_builds_a_safe_notify_send_argv(monkeypatch) -> None:
    calls = []
    monkeypatch.setattr(desktop_notify.sys, "platform", "linux")
    monkeypatch.setattr(desktop_notify.subprocess, "run", lambda argv, **kw: calls.append((argv, kw)))

    assert desktop_notify.send("Ma tâche", "temps réel au-delà de l'estimé.") is True

    argv, kwargs = calls[0]
    assert argv[0] == "notify-send"
    # `--` ferme l'analyse des options : un texte commençant par un tiret reste
    # un argument, jamais une option.
    assert "--" in argv
    assert argv[-2] == "Kairos · Ma tâche"
    assert argv[-1] == "temps réel au-delà de l'estimé."
    # Jamais de shell : la liste d'arguments est passée telle quelle.
    assert kwargs.get("shell") is not True


def test_send_always_prefixes_the_title_with_kairos(monkeypatch) -> None:
    """La route est joignable par tout ce qui tourne sur la machine : une
    notification émise par Kairos s'annonce toujours comme telle et ne peut pas
    être fabriquée pour ressembler à un autre logiciel."""
    calls = []
    monkeypatch.setattr(desktop_notify.sys, "platform", "linux")
    monkeypatch.setattr(desktop_notify.subprocess, "run", lambda argv, **kw: calls.append(argv))

    desktop_notify.send("Windows Update", "Redémarrez maintenant")
    assert calls[0][-2].startswith("Kairos · ")


def test_send_flattens_newlines_and_bounds_length(monkeypatch) -> None:
    calls = []
    monkeypatch.setattr(desktop_notify.sys, "platform", "linux")
    monkeypatch.setattr(desktop_notify.subprocess, "run", lambda argv, **kw: calls.append(argv))

    desktop_notify.send("A\nB", "x" * 999)
    title, body = calls[0][-2], calls[0][-1]
    assert title == "Kairos · A B"  # retours à la ligne remplacés, pas supprimés
    assert len(body) == desktop_notify._MAX_BODY_CHARS


def test_send_returns_false_when_the_tool_fails(monkeypatch) -> None:
    """Best-effort de bout en bout : une alerte qui ne sort pas ne doit jamais
    faire remonter une exception dans la requête HTTP."""
    monkeypatch.setattr(desktop_notify.sys, "platform", "linux")

    def boom(*args, **kwargs):
        raise subprocess.CalledProcessError(1, "notify-send")

    monkeypatch.setattr(desktop_notify.subprocess, "run", boom)
    assert desktop_notify.send("Titre", "Corps") is False


def test_send_is_false_on_unsupported_platform(monkeypatch) -> None:
    monkeypatch.setattr(desktop_notify.sys, "platform", "darwin")
    assert desktop_notify.send("Titre", "Corps") is False


# ---------------------------------------------------------------- envoi Windows


def test_send_passes_text_through_the_environment_on_windows(monkeypatch) -> None:
    """Le script PowerShell est une constante : le texte transite par des
    variables d'environnement, donc aucun échappement — donc aucune injection."""
    calls = []
    monkeypatch.setattr(desktop_notify.sys, "platform", "win32")
    monkeypatch.setattr(desktop_notify.shutil, "which", lambda name: "C:\\powershell.exe")
    monkeypatch.setattr(
        desktop_notify.subprocess, "Popen", lambda argv, **kw: calls.append((argv, kw))
    )

    assert desktop_notify.send("Ma tâche", "une pause ?") is True
    argv, kwargs = calls[0]
    assert argv[0] == "C:\\powershell.exe"
    assert kwargs["env"]["KAIROS_NOTIFY_TITLE"] == "Kairos · Ma tâche"
    assert kwargs["env"]["KAIROS_NOTIFY_BODY"] == "une pause ?"
    # Le texte n'apparaît jamais dans le script lui-même.
    assert "Ma tâche" not in " ".join(argv)
