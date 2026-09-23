"""Publie une release GitLab de Kairos (dernier job de `.gitlab-ci.yml`).

Pendant GitLab du job `release` de `.github/workflows/release.yml`, pour un
hébergement sur un GitLab (d'entreprise, privé) :

1. calcule ``SHA256SUMS`` sur les fichiers ``kairos-*`` construits ;
2. les dépose, avec ``SHA256SUMS``, dans le registre de paquets génériques du
   projet (``packages/generic/kairos/<version>/<fichier>``) ;
3. crée la release du tag avec un lien par fichier.

Mêmes noms de fichiers que sur GitHub : la mise à jour intégrée
(`app/updates.py`) lit l'une ou l'autre forge avec la même logique, et refuse
d'installer un fichier qui ne correspond pas à ``SHA256SUMS``.

Uniquement la bibliothèque standard (l'image du job n'installe rien).
Authentification par ``CI_JOB_TOKEN`` (en-tête ``JOB-TOKEN``), fourni par
GitLab au job : aucun jeton personnel à configurer.

    python packaging/publish_gitlab_release.py out/
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import sys
import urllib.request
from pathlib import Path

PACKAGE_NAME = "kairos"
CHECKSUMS = "SHA256SUMS"


def write_checksums(directory: Path) -> list[Path]:
    """Écrit ``SHA256SUMS`` (format `sha256sum`) ; retourne les fichiers à publier."""
    files = sorted(p for p in directory.glob("kairos-*") if p.is_file())
    if not files:
        raise SystemExit(f"Aucun fichier kairos-* dans {directory}")
    lines = [f"{hashlib.sha256(p.read_bytes()).hexdigest()}  {p.name}" for p in files]
    (directory / CHECKSUMS).write_text("\n".join(lines) + "\n", encoding="utf-8")
    return [*files, directory / CHECKSUMS]


def _request(method: str, url: str, token: str, *, data: bytes, content_type: str) -> bytes:
    request = urllib.request.Request(url, data=data, method=method, headers={
        "JOB-TOKEN": token, "Content-Type": content_type,
    })
    with urllib.request.urlopen(request, timeout=300) as response:
        return response.read()


def publish(directory: Path, *, api_url: str, project_id: str, tag: str, token: str,
            description: str = "") -> dict:
    files = write_checksums(directory)
    version = tag[1:] if tag.startswith("v") else tag
    package_url = f"{api_url}/projects/{project_id}/packages/generic/{PACKAGE_NAME}/{version}"
    links = []
    for path in files:
        url = f"{package_url}/{path.name}"
        _request("PUT", url, token, data=path.read_bytes(), content_type="application/octet-stream")
        links.append({"name": path.name, "url": url, "link_type": "package"})
        print(f"Déposé : {path.name}")
    payload = {
        "name": f"Kairos {tag}",
        "tag_name": tag,
        "description": description or f"Kairos {tag}",
        "assets": {"links": links},
    }
    body = _request("POST", f"{api_url}/projects/{project_id}/releases", token,
                    data=json.dumps(payload).encode("utf-8"), content_type="application/json")
    print(f"Release créée : {tag}")
    return json.loads(body or b"{}")


def main(argv: list[str] | None = None) -> None:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("directory", type=Path)
    args = parser.parse_args(argv)
    env = os.environ
    missing = [k for k in ("CI_API_V4_URL", "CI_PROJECT_ID", "CI_COMMIT_TAG", "CI_JOB_TOKEN") if not env.get(k)]
    if missing:
        sys.exit(f"Variables GitLab CI manquantes : {', '.join(missing)}")
    publish(
        args.directory,
        api_url=env["CI_API_V4_URL"],
        project_id=env["CI_PROJECT_ID"],
        tag=env["CI_COMMIT_TAG"],
        token=env["CI_JOB_TOKEN"],
        description=env.get("CI_COMMIT_TAG_MESSAGE", ""),
    )


if __name__ == "__main__":
    main()
