"""Environnement assaini pour les processus externes lancés par Kairos.

PyInstaller (mode onefile, voir `packaging/kairos.spec`) réachemine
`LD_LIBRARY_PATH` (Linux) / `DYLD_LIBRARY_PATH` (macOS) vers son dossier
d'extraction temporaire, pour que l'exécutable gelé y retrouve ses propres
bibliothèques embarquées — et sauvegarde la valeur d'origine dans une variable
`..._ORIG` (absente si la variable n'existait pas avant). Tout processus
externe qui hérite de cette variable détournée (un navigateur, `xdg-open` qui
est lui-même un script shell, `git`...) peut alors charger par erreur une
bibliothèque embarquée par PyInstaller (ex. `libreadline.so`, tirée par le
module `readline` d'un interpréteur figé) incompatible avec la sienne, au lieu
de celle du système — observé en conditions réelles : un `/bin/sh` (symlink
vers `bash` sur certaines distributions) plante au lancement avec
`undefined symbol: rl_print_keybinding`, la bibliothèque système attendant une
version de `libreadline` plus récente que celle embarquée par PyInstaller.

Sans effet hors d'un exécutable PyInstaller (mode `pip install -e .`, service
systemd) : ces variables `_ORIG` n'existent alors pas, donc rien n'est modifié.
"""

from __future__ import annotations

import contextlib
import os

_LIBRARY_PATH_VARS = ("LD_LIBRARY_PATH", "DYLD_LIBRARY_PATH")


def external_process_env() -> dict[str, str]:
    """Copie de `os.environ`, chemins de bibliothèques dynamiques restaurés à
    leur valeur d'avant PyInstaller — à passer en `env=` à `subprocess.run`/
    `Popen` pour tout exécutable externe (pas le propre exécutable gelé)."""
    env = dict(os.environ)
    for key in _LIBRARY_PATH_VARS:
        orig = env.pop(f"{key}_ORIG", None)
        if orig is not None:
            env[key] = orig
        else:
            env.pop(key, None)
    return env


@contextlib.contextmanager
def external_process_environ():
    """Bascule temporairement `os.environ` du process courant sur cet
    environnement assaini, pour les API qui ne permettent pas de passer un
    `env=` explicite (ex. `webbrowser.open`)."""
    sanitized = external_process_env()
    saved = {key: os.environ.get(key) for key in _LIBRARY_PATH_VARS}
    for key in _LIBRARY_PATH_VARS:
        if key in sanitized:
            os.environ[key] = sanitized[key]
        else:
            os.environ.pop(key, None)
    try:
        yield
    finally:
        for key, value in saved.items():
            if value is None:
                os.environ.pop(key, None)
            else:
                os.environ[key] = value
