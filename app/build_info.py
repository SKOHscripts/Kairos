"""Version installée de Kairos et source par défaut de ses mises à jour.

Trois cas, selon la manière dont Kairos a été installé (voir
`docs/spec/mises-a-jour.md`) :

- **exécutable de bureau ou APK** construits par la CI : `app/_build_info.py`
  est généré juste avant le build par `packaging/write_build_info.py` (version
  = tag de la release, source = forge qui construit : GitHub ou GitLab). Ce
  fichier n'est jamais commité (`.gitignore`) ;
- **installation depuis les sources** (clone + venv, service systemd) : pas de
  fichier généré ; la version est le dernier tag atteignable
  (`git describe --tags --abbrev=0`) et la source suit le remote `origin`,
  pour que la source des mises à jour s'adapte au remote sans réglage ;
- ni l'un ni l'autre (archive sans `.git`) : version inconnue, aucune
  vérification possible sans réglage explicite de la source.
"""

from __future__ import annotations

import os
import re
import subprocess
import sys
from functools import lru_cache
from pathlib import Path

from .subprocess_env import external_process_env

UNKNOWN_VERSION = "0.0.0-dev"
_REPO_ROOT = Path(__file__).resolve().parent.parent


def _generated():
    try:
        from . import _build_info  # type: ignore[attr-defined]
    except ImportError:
        return None
    return _build_info


def install_kind() -> str:
    """``android`` (APK), ``desktop`` (exécutable PyInstaller) ou ``source``."""
    if os.environ.get("KAIROS_PLATFORM") == "android":
        return "android"
    if getattr(sys, "frozen", False):
        return "desktop"
    return "source"


def _git(*args: str) -> str | None:
    """Sortie d'une commande git dans le dépôt, ou None (git absent, pas un
    clone, délai dépassé). Jamais d'exception : purement informatif."""
    if not (_REPO_ROOT / ".git").exists():
        return None
    try:
        result = subprocess.run(
            ["git", *args], cwd=_REPO_ROOT, capture_output=True, text=True,
            timeout=3, env=external_process_env(), check=False,
        )
    except (OSError, subprocess.SubprocessError):
        return None
    out = result.stdout.strip()
    return out if result.returncode == 0 and out else None


@lru_cache
def version() -> str:
    """Version installée, sans le « v » du tag (``2.5.0``)."""
    generated = _generated()
    raw = getattr(generated, "VERSION", "") if generated else ""
    if not raw and install_kind() == "source":
        raw = _git("describe", "--tags", "--abbrev=0") or ""
    raw = raw.strip()
    return (raw[1:] if raw.startswith("v") else raw) or UNKNOWN_VERSION


# git@hôte:groupe/projet.git, ssh://git@hôte[:port]/groupe/projet.git,
# https://[identifiants@]hôte[:port]/groupe/projet(.git)
_SCP_REMOTE = re.compile(r"^[\w.-]+@(?P<host>[\w.-]+):(?P<path>[^/].*)$")
_URL_REMOTE = re.compile(
    r"^(?P<scheme>https?|ssh|git)://(?:[^@/]+@)?(?P<host>[\w.-]+)(?::(?P<port>\d+))?/(?P<path>.+)$"
)


def parse_remote(remote: str) -> tuple[str, str] | None:
    """``(url_de_la_forge, chemin_du_projet)`` d'un remote git, ou None.

    La forge est toujours rendue en ``https://hôte`` (un remote SSH n'a pas
    d'URL web ; son port SSH n'a pas de sens en HTTPS), un port explicite n'est
    conservé que pour un remote HTTP(S). Les identifiants éventuels d'un remote
    HTTPS (``https://user:jeton@...``) ne sont jamais recopiés.
    """
    remote = remote.strip()
    match = _SCP_REMOTE.match(remote)
    if match:
        host, path, port = match["host"], match["path"], None
    else:
        match = _URL_REMOTE.match(remote)
        if not match:
            return None
        host, path = match["host"], match["path"]
        port = match["port"] if match["scheme"] in ("http", "https") else None
    path = path.strip("/")
    if path.endswith(".git"):
        path = path[:-4]
    if "/" not in path:
        return None
    return (f"https://{host}" + (f":{port}" if port else ""), path)


@lru_cache
def default_source() -> tuple[str, str]:
    """``(url_de_la_forge, projet)`` par défaut, ``("", "")`` si inconnue."""
    generated = _generated()
    if generated and getattr(generated, "UPDATE_SERVER_URL", ""):
        return (generated.UPDATE_SERVER_URL, getattr(generated, "UPDATE_PROJECT", ""))
    if install_kind() == "source":
        remote = _git("remote", "get-url", "origin")
        parsed = parse_remote(remote) if remote else None
        if parsed:
            return parsed
    return ("", "")
