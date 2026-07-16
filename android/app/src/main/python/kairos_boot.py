"""Amorce Chaquopy de Kairos : fait le pont entre `MainActivity` et l'application.

Le code de l'application vit dans le paquet embarqué ``kairos_dist`` (généré par
la tâche Gradle ``stageKairosPython`` : app/, templates/, static/, README.md —
même arborescence que le dépôt), extrait en vrais fichiers par Chaquopy
(``extractPackages``). Ce module :

1. ajoute le dossier extrait à ``sys.path`` pour que ``import app`` fonctionne
   comme sur le poste de bureau (les imports du projet restent inchangés) ;
2. pose ``KAIROS_BASE_DIR`` (templates/static/README lus par de vrais chemins)
   et ``KAIROS_PLATFORM=android`` — **avant** tout import de ``app.main`` ;
3. expose :func:`prepare` / :func:`serve` à `MainActivity` (voir
   ``app/android_launcher.py`` pour le contrat).
"""

from __future__ import annotations

import os
import sys
from pathlib import Path

_base_dir: Path | None = None


def _ensure_app_importable() -> None:
    global _base_dir
    if _base_dir is not None:
        return
    import kairos_dist

    _base_dir = Path(kairos_dist.__file__).resolve().parent
    sys.path.insert(0, str(_base_dir))
    os.environ["KAIROS_BASE_DIR"] = str(_base_dir)
    os.environ["KAIROS_PLATFORM"] = "android"


def prepare(files_dir: str) -> int:
    """Prépare l'environnement, retourne le port à servir (voir `android_launcher`)."""
    _ensure_app_importable()
    from app.android_launcher import prepare as app_prepare

    return app_prepare(files_dir)


def serve(port: int) -> None:
    """Sert l'application (bloquant — appelé dans un thread dédié côté Java)."""
    _ensure_app_importable()
    from app.android_launcher import serve as app_serve

    app_serve(port)
