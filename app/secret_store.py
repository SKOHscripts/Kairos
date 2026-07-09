"""Stockage des réglages sensibles (jeton GitLab, mot de passe TimeTree) dans le
trousseau système (`keyring` : Windows Credential Manager, GNOME Keyring/
SecretService, Keychain macOS).

Repli automatique et silencieux sur le fichier de réglages en clair si le
trousseau système est indisponible (ex. Linux headless sans service
SecretService) — même philosophie de dégradation propre que
`app/git_credentials.py` et `app/calendar/timetree_source.py` : jamais
d'erreur dure sur cette dépendance externe, dans un sens ou dans l'autre.
"""

from __future__ import annotations

import keyring

_SERVICE_NAME = "kairos"

_FALLBACK_DETAIL = (
    "Trousseau système indisponible sur ce poste : ce réglage est stocké dans "
    "le fichier de configuration local (non chiffré)."
)


def keyring_available() -> bool:
    """Vrai si un vrai back-end de trousseau est utilisable sur ce poste."""
    try:
        from keyring.backends.fail import Keyring as _FailKeyring

        return not isinstance(keyring.get_keyring(), _FailKeyring)
    except Exception:
        return False


def get_secret(field_name: str, *, plain_fallback: str) -> str:
    """Valeur de ``field_name`` depuis le trousseau système, sinon ``plain_fallback``
    (valeur lue depuis le fichier de réglages, utilisée si le trousseau a échoué
    à la sauvegarde ou est indisponible sur ce poste)."""
    try:
        value = keyring.get_password(_SERVICE_NAME, field_name)
    except Exception:
        return plain_fallback
    return value if value else plain_fallback


def set_secret(field_name: str, value: str) -> tuple[bool, str]:
    """Stocke (ou efface, si ``value`` est vide) ``field_name`` dans le trousseau.

    Retourne ``(stocké_via_trousseau, détail)`` : si ``stocké_via_trousseau`` est
    faux, l'appelant doit conserver ``value`` en clair dans le fichier de
    réglages (voir `app/settings_store.py::save`), et ``détail`` est un message
    à afficher dans l'interface."""
    if not value:
        try:
            keyring.delete_password(_SERVICE_NAME, field_name)
        except Exception:
            pass  # rien à effacer, ou trousseau indisponible : sans conséquence
        return True, ""
    try:
        keyring.set_password(_SERVICE_NAME, field_name, value)
    except Exception:
        return False, _FALLBACK_DETAIL
    return True, ""
