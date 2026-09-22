"""Pilotage de la fenêtre de démarrage de l'exécutable de bureau.

La fenêtre elle-même est affichée par le bootloader PyInstaller (cible `Splash`
de `packaging/kairos.spec`) dès le double-clic, avant même que Python ne
démarre : c'est ce qui couvre l'extraction de l'exécutable onefile, la phase la
plus longue sous Windows (antivirus) et pendant laquelle aucun code Kairos ne
tourne encore. Ce module se contente de mettre à jour son texte d'état et de
la fermer, via le module `pyi_splash` que PyInstaller n'expose que dans
l'exécutable figé.

Hors exécutable (`pip install -e .`, tests) ou si le bootloader n'a pas pu
afficher la fenêtre (Linux sans serveur X, Tcl/Tk en échec), chaque appel est un
no-op silencieux : la fenêtre de démarrage est un confort, jamais une raison
d'échouer au lancement.
"""

from __future__ import annotations

import os
import sys


def _splash():
    # `_PYI_SPLASH_IPC` est posé par le bootloader seulement quand la fenêtre
    # s'est réellement affichée : sans lui, importer `pyi_splash` journalise un
    # avertissement inutile. Import paresseux et mis en cache par `sys.modules`.
    if not getattr(sys, "frozen", False):
        return None
    if "pyi_splash" not in sys.modules and "_PYI_SPLASH_IPC" not in os.environ:
        return None
    try:
        import pyi_splash  # type: ignore[import-not-found]
    except Exception:
        return None
    return pyi_splash


def update(text: str) -> None:
    splash = _splash()
    if splash is None:
        return
    try:
        if splash.is_alive():
            splash.update_text(text)
    except Exception:
        pass  # fenêtre fermée entre-temps ou IPC rompu : sans conséquence


def close() -> None:
    splash = _splash()
    if splash is None:
        return
    try:
        if splash.is_alive():
            splash.close()
    except Exception:
        pass
