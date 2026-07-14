"""Flux OAuth 2.0 (PKCE) pour connecter un compte Google Calendar.

Isolé du reste de l'app derrière les fonctions ci-dessous : point d'entrée
unique consommé par les routes ``/kairos/settings/google/connect`` et
``/kairos/settings/google/callback`` (voir ``app/main.py``). Client OAuth de
type « Application de bureau » (créé par l'utilisateur dans Google Cloud
Console, `client_id`/`client_secret` saisis dans Réglages) : le jeton
d'autorisation est échangé via un ``redirect_uri`` loopback
(``http://127.0.0.1:<port>/...``, RFC 8252 §7.3 — Google accepte n'importe
quel port sur 127.0.0.1 pour ce type de client), fonctionne donc aussi bien
pour le launcher de bureau que dans la WebView Android (même serveur local).

**Contrat** : aucune fonction publique ne lève. Toute erreur (jeton expiré,
code invalide, requête réseau en échec) se traduit par un
``GoogleTokenResult(ok=False, ...)`` avec un ``detail`` lisible.

**PKCE** : ``code_verifier`` gardé en mémoire (par processus), à la clé
``state``, le temps du round-trip navigateur — jamais persisté, purgé après
usage ou expiration (``_PENDING_TTL_MINUTES``).
"""

from __future__ import annotations

import base64
import hashlib
import secrets
from dataclasses import dataclass
from datetime import datetime, timezone
from urllib.parse import urlencode

import httpx

from ..config import Settings

__all__ = [
    "GoogleTokenResult",
    "build_authorize_url",
    "exchange_code_for_tokens",
    "refresh_access_token",
]

_AUTHORIZE_URL = "https://accounts.google.com/o/oauth2/v2/auth"
_TOKEN_URL = "https://oauth2.googleapis.com/token"
_SCOPE = "https://www.googleapis.com/auth/calendar.readonly"
_PENDING_TTL_MINUTES = 10


@dataclass
class GoogleTokenResult:
    ok: bool
    access_token: str = ""
    refresh_token: str = ""
    detail: str = ""


# state -> (code_verifier, généré à)
_pending: dict[str, tuple[str, datetime]] = {}


def _purge_pending() -> None:
    now = datetime.now(timezone.utc)
    expired = [
        state for state, (_, at) in _pending.items()
        if (now - at).total_seconds() > _PENDING_TTL_MINUTES * 60
    ]
    for state in expired:
        _pending.pop(state, None)


def build_authorize_url(redirect_uri: str, *, settings: Settings) -> str:
    """URL de consentement Google, avec PKCE (S256). Enregistre le
    `code_verifier` associé au `state` généré, à usage unique (consommé par
    :func:`exchange_code_for_tokens`)."""
    _purge_pending()
    state = secrets.token_urlsafe(24)
    code_verifier = secrets.token_urlsafe(64)
    challenge = base64.urlsafe_b64encode(
        hashlib.sha256(code_verifier.encode("ascii")).digest()
    ).rstrip(b"=").decode("ascii")
    _pending[state] = (code_verifier, datetime.now(timezone.utc))
    params = {
        "client_id": settings.google_client_id,
        "redirect_uri": redirect_uri,
        "response_type": "code",
        "scope": _SCOPE,
        # offline + consent : force l'émission d'un refresh_token à chaque
        # connexion (Google n'en renvoie pas systématiquement sinon).
        "access_type": "offline",
        "prompt": "consent",
        "code_challenge": challenge,
        "code_challenge_method": "S256",
        "state": state,
    }
    return f"{_AUTHORIZE_URL}?{urlencode(params)}"


def exchange_code_for_tokens(
    code: str, state: str, redirect_uri: str, *, settings: Settings,
    client: httpx.Client | None = None,
) -> GoogleTokenResult:
    """Échange un code d'autorisation contre un jeton de rafraîchissement. Ne lève jamais."""
    pending = _pending.pop(state, None)
    if pending is None:
        return GoogleTokenResult(ok=False, detail="Requête de connexion expirée ou invalide, réessayez.")
    code_verifier, _ = pending
    http_client = client or httpx.Client(timeout=10.0)
    try:
        response = http_client.post(_TOKEN_URL, data={
            "client_id": settings.google_client_id,
            "client_secret": settings.google_client_secret,
            "code": code,
            "code_verifier": code_verifier,
            "grant_type": "authorization_code",
            "redirect_uri": redirect_uri,
        })
        response.raise_for_status()
        payload = response.json()
    except httpx.HTTPError as exc:
        return GoogleTokenResult(ok=False, detail=f"Échec de l'échange du code Google : {exc}")
    except ValueError as exc:
        return GoogleTokenResult(ok=False, detail=f"Réponse Google invalide : {exc}")
    refresh_token = payload.get("refresh_token", "")
    if not refresh_token:
        return GoogleTokenResult(
            ok=False,
            detail="Google n'a pas renvoyé de jeton de rafraîchissement (reconnectez-vous).",
        )
    return GoogleTokenResult(
        ok=True, access_token=payload.get("access_token", ""), refresh_token=refresh_token,
    )


def refresh_access_token(settings: Settings, *, client: httpx.Client | None = None) -> GoogleTokenResult:
    """Échange `settings.google_refresh_token` contre un jeton d'accès de courte durée. Ne lève jamais."""
    if not settings.google_refresh_token:
        return GoogleTokenResult(ok=False, detail="Google Calendar non connecté.")
    http_client = client or httpx.Client(timeout=10.0)
    try:
        response = http_client.post(_TOKEN_URL, data={
            "client_id": settings.google_client_id,
            "client_secret": settings.google_client_secret,
            "refresh_token": settings.google_refresh_token,
            "grant_type": "refresh_token",
        })
        response.raise_for_status()
        payload = response.json()
    except httpx.HTTPError as exc:
        return GoogleTokenResult(ok=False, detail=f"Échec du rafraîchissement du jeton Google : {exc}")
    except ValueError as exc:
        return GoogleTokenResult(ok=False, detail=f"Réponse Google invalide : {exc}")
    return GoogleTokenResult(ok=True, access_token=payload.get("access_token", ""))
