"""Résolution d'un jeton GitLab via les moyens d'authentification déjà
configurés pour `git` sur ce poste, en alternative à `GITLAB_TOKEN` dans `.env`.

Deux sources, dans l'ordre (la première qui répond gagne) :
1. `git credential fill` — protocole standard de `git`, qui délègue à quelque
   helper que ce soit déjà configuré (`credential.helper`) : trousseau GNOME/
   libsecret, Keychain macOS, Windows Credential Manager, `cache`, `store`...
   Rien de spécifique à coder par backend : c'est `git` qui choisit.
2. `~/.netrc` (ou `$NETRC`), lu directement (`git` ne le consulte pas nativement
   sans helper dédié, mais beaucoup d'outils l'utilisent comme entrepôt simple).

Jamais interactif : `GIT_TERMINAL_PROMPT=0` coupe le repli sur un prompt
terminal, et un timeout court évite de bloquer une requête HTTP si un helper
traîne. Aucune exception ne remonte — absence de jeton = chaîne vide, comme
`Settings.gitlab_token` non renseigné.
"""

from __future__ import annotations

import netrc
import os
import subprocess
from functools import lru_cache
from urllib.parse import urlparse


def _git_credential_fill(url: str, host: str) -> str | None:
    scheme = urlparse(url).scheme or "https"
    request = f"protocol={scheme}\nhost={host}\n\n"
    try:
        result = subprocess.run(
            ["git", "credential", "fill"],
            input=request,
            capture_output=True,
            text=True,
            timeout=5,
            env={**os.environ, "GIT_TERMINAL_PROMPT": "0"},
        )
    except (OSError, subprocess.TimeoutExpired):
        return None
    if result.returncode != 0:
        return None
    for line in result.stdout.splitlines():
        if line.startswith("password="):
            return line[len("password="):] or None
    return None


def _netrc_lookup(host: str) -> str | None:
    try:
        parsed = netrc.netrc()
    except (FileNotFoundError, netrc.NetrcParseError, OSError):
        return None
    auth = parsed.authenticators(host)
    if not auth:
        return None
    _login, _account, password = auth
    return password or None


@lru_cache(maxsize=8)
def resolve_gitlab_token(url: str) -> str:
    """Jeton pour ``url`` via `git credential fill` puis `~/.netrc`, sinon "".

    Mis en cache pour le processus courant (un jeton ne change pas en cours de
    route) — redémarrer le service après une rotation de jeton, comme pour un
    changement de `.env`.
    """
    host = urlparse(url).hostname
    if not host:
        return ""
    return _git_credential_fill(url, host) or _netrc_lookup(host) or ""
