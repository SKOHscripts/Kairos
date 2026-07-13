"""Point d'entrée Android de « Kairos » : serveur local dans l'application.

Pendant Android de `app/launcher.py` (exécutable de bureau), en plus simple :
pas de navigateur à ouvrir (la WebView de `MainActivity` s'en charge), pas de
verrou d'instance unique (le bac à sable Android garantit une seule instance),
pas de bouton « Quitter » (`is_frozen` est faux, l'utilisateur quitte par le
système — voir `templates/base.html`).

Deux étapes, appelées par `MainActivity` via l'amorce Chaquopy
(`android/app/src/main/python/kairos_boot.py`) :

1. :func:`prepare` — fixe l'environnement (données dans le stockage privé de
   l'application) et choisit un port libre. **Avant tout import de**
   ``app.main`` : le moteur de base de données et `BASE_DIR` sont résolus à
   l'import.
2. :func:`serve` — importe l'application et lance uvicorn (bloquant, appelé
   depuis un thread dédié côté Java).
"""

from __future__ import annotations

import os
import socket

_HOST = "127.0.0.1"  # jamais 0.0.0.0 : l'appli ne doit pas s'exposer sur le réseau
_DEFAULT_PORT = 8001


def _port_available(port: int) -> bool:
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as sock:
        return sock.connect_ex((_HOST, port)) != 0


def _pick_port(preferred: int = _DEFAULT_PORT, tries: int = 20) -> int:
    for port in range(preferred, preferred + tries):
        if _port_available(port):
            return port
    return preferred  # aucun port libre : laisse uvicorn échouer avec une erreur claire


def prepare(files_dir: str) -> int:
    """Prépare l'environnement et retourne le port à servir (rapide, thread UI).

    ``files_dir`` : stockage privé de l'application (`Context.getFilesDir()`).
    ``KAIROS_DATA_DIR`` y ancre réglages et base de tâches (voir
    `settings_store.data_dir`) — déterministe, sans dépendre de la détection
    Android de `platformdirs`.
    """
    data_dir = os.path.join(files_dir, "kairos-data")
    os.environ["KAIROS_DATA_DIR"] = data_dir
    os.environ.setdefault("HOME", files_dir)
    return _pick_port()


def serve(port: int) -> None:
    """Sert l'application sur ``port`` (bloquant — à appeler dans un thread dédié).

    Import de ``app.main`` fait ici, jamais en tête de module : `prepare` doit
    avoir posé l'environnement avant que `BASE_DIR` et les réglages ne soient lus.
    """
    import uvicorn

    from .main import app

    # `reload`/`workers>1` re-exécutent le process : sans objet dans une appli
    # Android (même contrainte que l'exécutable figé, voir `launcher.main`).
    uvicorn.run(app, host=_HOST, port=int(port), reload=False)
