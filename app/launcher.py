"""Point d'entrée de bureau pour « Kairos » : lancement en un double-clic.

Ciblé par PyInstaller (voir `packaging/kairos.spec`) à la place de la CLI
`uvicorn app.main:app` — un exécutable figé n'a pas de terminal pour lire une
erreur de démarrage, ni de shell pour taper une commande : ce module choisit un
port libre, ouvre le navigateur tout seul, et journalise tout échec de
démarrage dans un fichier avant de remonter l'erreur.

**Instance unique** : un fichier de verrou (`kairos.lock`, dans le dossier de
données) retient le port de la dernière instance lancée. Relancer l'exécutable
pendant qu'une instance tourne déjà rouvre simplement le navigateur dessus au
lieu d'en démarrer une seconde — sans ça, le port avance à chaque lancement
(8001, 8002, ...) puisque l'instance précédente reste en arrière-plan tant que
personne ne clique sur « Quitter » (fermer l'onglet du navigateur n'arrête pas
le serveur). Un verrou obsolète (process tué, jamais nettoyé) est détecté sans
vérification de PID — multiplateforme plus simple — en sondant directement le
port : s'il ne répond plus, on démarre normalement et on écrase le verrou.

Aussi accessible en mode venv via `pip install -e .` (`[project.scripts]` dans
`pyproject.toml` : commande `kairos`), pour une expérience de lancement identique.
"""

from __future__ import annotations

import json
import logging
import multiprocessing
import os
import socket
import sys
import threading
import traceback
import urllib.error
import urllib.request
import webbrowser

import uvicorn

# Imports absolus (pas `from .main import ...`) : PyInstaller exécute ce
# fichier comme script top-level (`Analysis(['app/launcher.py'])`), sans
# contexte de paquet parent — un import relatif y échoue avec « attempted
# relative import with no known parent package ». Les imports absolus
# fonctionnent dans les deux cas (script figé et `pip install -e .`), tant que
# la racine du dépôt est sur `sys.path` (c'est le cas ici : `pathex` du spec,
# ou le `.pth` du mode editable).
from app.desktop_browser import find_app_capable_browser, launch_app_window
from app.main import app
from app.settings_store import data_dir
from app.subprocess_env import external_process_environ

_DEFAULT_PORT = 8001
_HOST = "127.0.0.1"  # jamais 0.0.0.0 : contrairement au service systemd (exposé
# volontairement sur le LAN), l'exécutable de bureau ne doit pas s'exposer par défaut.
_LOCK_FILENAME = "kairos.lock"


def _port_available(port: int) -> bool:
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as sock:
        return sock.connect_ex((_HOST, port)) != 0


def _pick_port(preferred: int = _DEFAULT_PORT, tries: int = 20) -> int:
    for port in range(preferred, preferred + tries):
        if _port_available(port):
            return port
    return preferred  # aucun port libre trouvé : laisse uvicorn échouer avec une erreur claire


def _open_browser(url: str) -> None:
    # KAIROS_NO_BROWSER : échappatoire pour les lancements automatisés (voir
    # `packaging/smoke_test.py`) où ouvrir un vrai navigateur est indésirable
    # (processus fantôme sur un runner CI, effets de bord imprévisibles).
    if os.environ.get("KAIROS_NO_BROWSER"):
        return
    # Fenêtre d'application (Chromium `--app=URL`, voir `app/desktop_browser.py`)
    # imposée par défaut, sans réglage utilisateur : c'est le ressenti recherché
    # pour l'exécutable de bureau (pas un onglet de navigateur généraliste), et
    # ça se dégrade tout seul vers l'ancien comportement (onglet du navigateur
    # par défaut) si aucun navigateur Chromium n'est trouvé ou si son lancement
    # échoue pour n'importe quelle raison — jamais d'erreur remontée à
    # l'utilisateur pour cette fonctionnalité de confort.
    browser_path = find_app_capable_browser()
    if browser_path and launch_app_window(browser_path, url):
        return
    # `external_process_environ()` : voir `app/subprocess_env.py` — évite
    # qu'un navigateur/`xdg-open` lancé par PyInstaller (mode onefile) hérite
    # du `LD_LIBRARY_PATH` détourné vers ses bibliothèques embarquées.
    with external_process_environ():
        webbrowser.open(url)


def _open_browser_later(url: str, delay: float = 1.2) -> None:
    threading.Timer(delay, lambda: _open_browser(url)).start()


def _lock_path():
    return data_dir() / _LOCK_FILENAME


def _read_lock_port() -> int | None:
    try:
        return json.loads(_lock_path().read_text(encoding="utf-8"))["port"]
    except (OSError, ValueError, KeyError):
        return None  # absent, illisible ou corrompu : traité comme « pas d'instance »


def _instance_already_running(port: int) -> bool:
    """Vrai si une vraie instance Kairos répond déjà sur ``port``.

    Sonde `/favicon.ico` (toujours 200 sur une instance réelle) plutôt que le
    PID enregistré : évite toute logique de vivacité de process spécifique à
    l'OS, et un port répondant avec autre chose (204/404/refus de connexion)
    est traité comme « pas une instance Kairos réutilisable », pas une erreur.
    """
    try:
        with urllib.request.urlopen(f"http://{_HOST}:{port}/favicon.ico", timeout=0.5):
            return True
    except (OSError, urllib.error.URLError):
        return False


def _write_lock(port: int) -> None:
    _lock_path().write_text(json.dumps({"port": port, "pid": os.getpid()}), encoding="utf-8")


def _clear_lock() -> None:
    _lock_path().unlink(missing_ok=True)


class _NullStream:
    """Flux minimal pour remplacer un `sys.stdout`/`sys.stderr` absent (voir
    `_ensure_std_streams`) : `write`/`flush`/`isatty` sont les seules méthodes
    dont uvicorn et le module `logging` ont besoin au démarrage."""

    def write(self, *_args, **_kwargs) -> int:
        return 0

    def flush(self) -> None:
        return None

    def isatty(self) -> bool:
        return False


def _ensure_std_streams() -> None:
    """Sous Windows, un exécutable PyInstaller en mode fenêtré (``console=False``,
    voir `packaging/kairos.spec`) n'a pas de console attachée : `sys.stdout`/
    `sys.stderr` valent `None` plutôt qu'un flux réel. uvicorn plante dès la
    configuration de son logging par défaut (`ColourizedFormatter.__init__`
    appelle `stream.isatty()`) — remplacer `None` par un flux nul avant tout
    évite ce crash, ici et pour tout autre code qui suppose un flux réel."""
    if sys.stdout is None:
        sys.stdout = _NullStream()
    if sys.stderr is None:
        sys.stderr = _NullStream()


def main() -> None:
    _ensure_std_streams()
    multiprocessing.freeze_support()  # requis sous Windows/PyInstaller (ré-exécution figée)

    existing_port = _read_lock_port()
    if existing_port is not None and _instance_already_running(existing_port):
        # Une instance tourne déjà (le cas courant si l'utilisateur a relancé
        # l'exécutable sans avoir cliqué « Quitter ») : on la rouvre, sans
        # démarrer un second serveur ni consommer un nouveau port.
        _open_browser(f"http://{_HOST}:{existing_port}")
        return

    try:
        port = _pick_port()
        _write_lock(port)
        _open_browser_later(f"http://{_HOST}:{port}")
        # `reload`/`workers>1` reposent tous deux sur un ré-exec du process
        # (rechargeur, workers multiples) : incompatible avec un exécutable figé.
        uvicorn.run(app, host=_HOST, port=port, reload=False)
    except Exception:
        log_path = data_dir() / "kairos-crash.log"
        log_path.write_text(traceback.format_exc(), encoding="utf-8")
        logging.getLogger("kairos").error("Échec du démarrage, trace dans %s", log_path)
        raise
    finally:
        # Atteint après un arrêt propre (bouton Quitter → SIGINT → uvicorn.run
        # revient normalement ; SIGTERM, lui, laisse l'OS tuer le process avant ce
        # `finally`, voir app/main.py::shutdown) : le prochain lancement ne doit
        # pas croire qu'une instance tourne encore. Un arrêt brutal (process tué,
        # ex. Gestionnaire des tâches) laisse le verrou en place, mais
        # _instance_already_running le détecte comme obsolète dès que le port ne
        # répond plus, plutôt que de bloquer les lancements suivants.
        _clear_lock()


if __name__ == "__main__":
    main()
