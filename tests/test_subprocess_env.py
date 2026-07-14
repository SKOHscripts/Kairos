"""Tests de l'assainissement d'environnement pour les processus externes
(`app/subprocess_env.py`) — voir ce module pour le contexte du bug qu'il
corrige (`rl_print_keybinding` observé sur un exécutable PyInstaller Linux)."""

from __future__ import annotations

import os

from app.subprocess_env import external_process_env, external_process_environ


def test_leaves_environment_untouched_outside_pyinstaller(monkeypatch) -> None:
    monkeypatch.delenv("LD_LIBRARY_PATH", raising=False)
    monkeypatch.delenv("LD_LIBRARY_PATH_ORIG", raising=False)
    monkeypatch.delenv("DYLD_LIBRARY_PATH", raising=False)
    monkeypatch.delenv("DYLD_LIBRARY_PATH_ORIG", raising=False)

    env = external_process_env()

    assert "LD_LIBRARY_PATH" not in env
    assert "DYLD_LIBRARY_PATH" not in env


def test_restores_saved_original_value(monkeypatch) -> None:
    monkeypatch.setenv("LD_LIBRARY_PATH", "/tmp/_MEIxxxxxx")
    monkeypatch.setenv("LD_LIBRARY_PATH_ORIG", "/usr/local/mylibs")

    env = external_process_env()

    assert env["LD_LIBRARY_PATH"] == "/usr/local/mylibs"
    assert "LD_LIBRARY_PATH_ORIG" not in env


def test_drops_variable_when_no_original_existed(monkeypatch) -> None:
    """Cas réel du bug rapporté : PyInstaller redirige LD_LIBRARY_PATH vers son
    dossier d'extraction mais n'exporte pas de `_ORIG` quand la variable
    n'existait pas avant (constaté empiriquement, pas juste documenté)."""
    monkeypatch.setenv("LD_LIBRARY_PATH", "/tmp/_MEIxxxxxx")
    monkeypatch.delenv("LD_LIBRARY_PATH_ORIG", raising=False)

    env = external_process_env()

    assert "LD_LIBRARY_PATH" not in env


def test_does_not_mutate_current_process_environment(monkeypatch) -> None:
    monkeypatch.setenv("LD_LIBRARY_PATH", "/tmp/_MEIxxxxxx")
    monkeypatch.delenv("LD_LIBRARY_PATH_ORIG", raising=False)

    external_process_env()

    assert os.environ["LD_LIBRARY_PATH"] == "/tmp/_MEIxxxxxx"


def test_external_process_environ_context_manager_restores_afterwards(monkeypatch) -> None:
    monkeypatch.setenv("LD_LIBRARY_PATH", "/tmp/_MEIxxxxxx")
    monkeypatch.setenv("DYLD_LIBRARY_PATH", "/tmp/_MEIxxxxxx")
    monkeypatch.delenv("LD_LIBRARY_PATH_ORIG", raising=False)
    monkeypatch.delenv("DYLD_LIBRARY_PATH_ORIG", raising=False)

    with external_process_environ():
        assert "LD_LIBRARY_PATH" not in os.environ
        assert "DYLD_LIBRARY_PATH" not in os.environ

    assert os.environ["LD_LIBRARY_PATH"] == "/tmp/_MEIxxxxxx"
    assert os.environ["DYLD_LIBRARY_PATH"] == "/tmp/_MEIxxxxxx"


def test_external_process_environ_restores_even_on_exception(monkeypatch) -> None:
    monkeypatch.setenv("LD_LIBRARY_PATH", "/tmp/_MEIxxxxxx")
    monkeypatch.delenv("LD_LIBRARY_PATH_ORIG", raising=False)

    try:
        with external_process_environ():
            raise RuntimeError("boom")
    except RuntimeError:
        pass

    assert os.environ["LD_LIBRARY_PATH"] == "/tmp/_MEIxxxxxx"


def test_other_environment_variables_are_preserved(monkeypatch) -> None:
    monkeypatch.setenv("LD_LIBRARY_PATH", "/tmp/_MEIxxxxxx")
    monkeypatch.setenv("SOME_OTHER_VAR", "kept")
    monkeypatch.delenv("LD_LIBRARY_PATH_ORIG", raising=False)

    env = external_process_env()

    assert env["SOME_OTHER_VAR"] == "kept"
