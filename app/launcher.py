"""Point d'entrée de bureau pour « Kairos » : lancement en un double-clic.

Ciblé par PyInstaller (voir `packaging/kairos.spec`) à la place de la CLI
`uvicorn app.main:app` — un exécutable figé n'a pas de terminal pour lire une
erreur de démarrage, ni de shell pour taper une commande : ce module choisit un
port libre, ouvre le navigateur tout seul, et journalise tout échec de
démarrage dans un fichier avant de remonter l'erreur.

Aussi accessible en mode venv via `pip install -e .` (`[project.scripts]` dans
`pyproject.toml` : commande `kairos`), pour une expérience de lancement identique.
"""

from __future__ import annotations

import logging
import multiprocessing
import socket
import sys
import threading
import traceback
import webbrowser

import uvicorn

# Imports absolus (pas `from .main import ...`) : PyInstaller exécute ce
# fichier comme script top-level (`Analysis(['app/launcher.py'])`), sans
# contexte de paquet parent — un import relatif y échoue avec « attempted
# relative import with no known parent package ». Les imports absolus
# fonctionnent dans les deux cas (script figé et `pip install -e .`), tant que
# la racine du dépôt est sur `sys.path` (c'est le cas ici : `pathex` du spec,
# ou le `.pth` du mode editable).
from app.main import app
from app.settings_store import data_dir

_DEFAULT_PORT = 8001
_HOST = "127.0.0.1"  # jamais 0.0.0.0 : contrairement au service systemd (exposé
# volontairement sur le LAN), l'exécutable de bureau ne doit pas s'exposer par défaut.


def _port_available(port: int) -> bool:
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as sock:
        return sock.connect_ex((_HOST, port)) != 0


def _pick_port(preferred: int = _DEFAULT_PORT, tries: int = 20) -> int:
    for port in range(preferred, preferred + tries):
        if _port_available(port):
            return port
    return preferred  # aucun port libre trouvé : laisse uvicorn échouer avec une erreur claire


def _open_browser_later(url: str, delay: float = 1.2) -> None:
    threading.Timer(delay, lambda: webbrowser.open(url)).start()


def main() -> None:
    multiprocessing.freeze_support()  # requis sous Windows/PyInstaller (ré-exécution figée)
    try:
        port = _pick_port()
        _open_browser_later(f"http://{_HOST}:{port}")
        # `reload`/`workers>1` reposent tous deux sur un ré-exec du process
        # (rechargeur, workers multiples) : incompatible avec un exécutable figé.
        uvicorn.run(app, host=_HOST, port=port, reload=False)
    except Exception:
        log_path = data_dir() / "kairos-crash.log"
        log_path.write_text(traceback.format_exc(), encoding="utf-8")
        logging.getLogger("kairos").error("Échec du démarrage, trace dans %s", log_path)
        raise


if __name__ == "__main__":
    main()
