"""Détection d'un navigateur Chromium et ouverture en « fenêtre d'application ».

`webbrowser.open` (utilisé par défaut dans `app/launcher.py`) ouvre un onglet
dans le navigateur système par défaut — barre d'adresse, onglets, tout
l'attirail d'un navigateur généraliste, alors que Kairos se veut ressenti
comme une application de bureau à part entière (voir `docs/spec/
packaging-lancement.md`). Les navigateurs de la famille Chromium (Chrome,
Chromium, Edge, Brave, Vivaldi — tous basés sur le même moteur et acceptant
les mêmes indicateurs de ligne de commande) savent s'ouvrir en **fenêtre
d'application** via `--app=URL` : pas de barre d'adresse, pas d'onglets, un
ressenti de « web app installée ». Firefox et Safari n'ont pas d'équivalent
strict à cet indicateur — ce module ne cible donc que la famille Chromium.

Ce module reste volontairement séparé de `app/launcher.py` : sa logique
(détection d'un binaire, construction des arguments) est pure et se teste
sans toucher à uvicorn, aux threads ou au fichier de verrou. `app/launcher.py`
l'appelle depuis `_open_browser`, avec un repli automatique et silencieux vers
`webbrowser.open` si la détection ou le lancement échoue — voir le
commentaire à l'appel pour le détail de cette décision.
"""

from __future__ import annotations

import os
import shutil
import subprocess
import sys

from app.settings_store import data_dir
from app.subprocess_env import external_process_env

# Ordre de préférence indicatif seulement (le premier trouvé gagne) — pas de
# hiérarchie qualitative entre ces navigateurs, juste une liste stable pour
# un comportement déterministe d'un poste à l'autre.
_LINUX_BROWSER_NAMES = (
    "google-chrome-stable",
    "google-chrome",
    "chromium-browser",
    "chromium",
    "brave-browser",
    "microsoft-edge",
    "microsoft-edge-stable",
    "vivaldi-stable",
    "vivaldi",
)

# Chemins relatifs sous chacun des dossiers de base Windows testés (l'ordre des
# bases ci-dessous est : Program Files, Program Files (x86), puis LocalAppData
# — un même navigateur peut atterrir sous l'une ou l'autre selon qu'il a été
# installé pour tous les utilisateurs ou seulement l'utilisateur courant ; on
# ne présume pas laquelle pour ne pas rater une installation valide).
_WINDOWS_BROWSER_RELATIVE_PATHS = (
    r"Google\Chrome\Application\chrome.exe",
    r"Microsoft\Edge\Application\msedge.exe",
    r"BraveSoftware\Brave-Browser\Application\brave.exe",
    r"Vivaldi\Application\vivaldi.exe",
)

_BROWSER_PROFILE_DIRNAME = "browser-profile"


def _windows_base_dirs() -> list[str]:
    bases = []
    for env_var in ("ProgramFiles", "ProgramFiles(x86)", "LocalAppData"):
        base = os.environ.get(env_var)
        if base:
            bases.append(base)
    return bases


def find_app_capable_browser() -> str | None:
    """Cherche un navigateur de la famille Chromium installé sur ce poste.

    Fonction pure (aucun effet de bord, pas d'impression) : ne fait que des
    vérifications sur le système de fichiers / l'environnement, pour rester
    facilement testable en monkeypatchant `shutil.which`, `os.environ` et
    `os.path.exists`.
    """
    # `KAIROS_BROWSER` : échappatoire explicite pour les tests/CI (imposer un
    # binaire précis sans dépendre de ce qui est réellement installé), et pour
    # un utilisateur avancé qui voudrait forcer un navigateur particulier —
    # prioritaire sur toute détection automatique.
    override = os.environ.get("KAIROS_BROWSER")
    if override:
        if os.path.isfile(override) and os.access(override, os.X_OK):
            return override
        resolved = shutil.which(override)
        if resolved:
            return resolved
        return None

    if sys.platform == "linux":
        for name in _LINUX_BROWSER_NAMES:
            found = shutil.which(name)
            if found:
                return found
        return None

    if sys.platform == "win32":
        base_dirs = _windows_base_dirs()
        for relative_path in _WINDOWS_BROWSER_RELATIVE_PATHS:
            for base_dir in base_dirs:
                candidate = os.path.join(base_dir, relative_path)
                if os.path.isfile(candidate):
                    return candidate
        return None

    # macOS (et tout autre OS) : hors périmètre de Kairos, voir
    # `docs/spec/packaging-lancement.md` § Hors périmètre. Pas de détection
    # dédiée, repli automatique vers `webbrowser.open` côté appelant.
    return None


def launch_app_window(browser_path: str, url: str) -> bool:
    """Lance ``browser_path`` en fenêtre d'application sur ``url``.

    Retourne `True` si le processus a bien été lancé (pas de garantie que la
    fenêtre s'affiche effectivement — fonctionnalité de confort, jamais
    bloquante), `False` sur tout échec.
    """
    # Profil dédié, séparé du profil personnel de l'utilisateur : la fenêtre
    # d'application ne doit pas se mêler à ses onglets/extensions/sessions du
    # navigateur habituel, et un profil Chromium ne peut de toute façon pas
    # être ouvert deux fois simultanément par deux processus distincts.
    profile_dir = str(data_dir() / _BROWSER_PROFILE_DIRNAME)
    argv = [browser_path, f"--user-data-dir={profile_dir}", f"--app={url}"]

    kwargs: dict = {}
    if sys.platform == "win32" and hasattr(subprocess, "DETACHED_PROCESS"):
        kwargs["creationflags"] = subprocess.DETACHED_PROCESS

    try:
        # `external_process_env()` (pas la version context manager,
        # `Popen` accepte un `env=` explicite) : évite qu'un navigateur lancé
        # depuis l'exécutable PyInstaller onefile hérite du `LD_LIBRARY_PATH`
        # détourné vers les bibliothèques embarquées — voir
        # `app/subprocess_env.py`.
        subprocess.Popen(
            argv,
            env=external_process_env(),
            stdin=subprocess.DEVNULL,
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
            start_new_session=True,
            **kwargs,
        )
    except Exception:
        # Fonctionnalité de confort en arrière-plan : un binaire manquant
        # malgré la détection, un droit refusé, ou toute autre surprise ne
        # doit jamais faire planter ni bloquer le lancement de Kairos —
        # l'appelant retombe sur `webbrowser.open`.
        return False
    return True
