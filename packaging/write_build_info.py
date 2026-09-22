"""Génère `app/_build_info.py` : version et source des mises à jour embarquées.

Appelé par la CI juste avant chaque build (PyInstaller, Gradle) :

    python packaging/write_build_info.py --version v2.6.0 \
        --server-url https://github.com --project SKOHscripts/Kairos

La forge passée ici devient la source par défaut des mises à jour de
l'exécutable ou de l'APK produit (voir `app/build_info.py` et
`docs/spec/mises-a-jour.md`) : le build GitHub pointe vers les releases GitHub,
le build GitLab vers les releases du projet GitLab qui le construit. Jamais de
jeton ici : le fichier généré est lisible dans l'exécutable.
"""

from __future__ import annotations

import argparse
from pathlib import Path

TARGET = Path(__file__).resolve().parent.parent / "app" / "_build_info.py"


def render(version: str, server_url: str, project: str) -> str:
    return (
        '"""Généré par packaging/write_build_info.py au build : ne pas éditer, ne pas commiter."""\n\n'
        f"VERSION = {version!r}\n"
        f"UPDATE_SERVER_URL = {server_url.rstrip('/')!r}\n"
        f"UPDATE_PROJECT = {project.strip('/')!r}\n"
    )


def main(argv: list[str] | None = None) -> None:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--version", required=True, help="tag de la release (vX.Y.Z)")
    parser.add_argument("--server-url", required=True, help="URL de la forge (https://...)")
    parser.add_argument("--project", required=True, help="chemin du projet (groupe/projet)")
    parser.add_argument("--output", type=Path, default=TARGET)
    args = parser.parse_args(argv)
    args.output.write_text(render(args.version, args.server_url, args.project), encoding="utf-8")
    print(f"Informations de build écrites : {args.output}")


if __name__ == "__main__":
    main()
