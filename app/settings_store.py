"""Persistance des réglages de « Kairos » : fichier JSON dans le dossier de
données de l'OS (voir `data_dir`), plus trousseau système pour les secrets
(`app/secret_store.py`) — remplace l'ancien chargement `.env`.

Une ancienne installation `.env` (utilisateur existant) est importée une seule
fois, automatiquement, la première fois qu'aucun fichier de réglages n'existe
encore (voir `_migrate_legacy_env_if_needed`). Le `.env` de l'utilisateur n'est
jamais supprimé par cette migration.
"""

from __future__ import annotations

import json
import os
import re
from datetime import datetime, timezone
from pathlib import Path

from pydantic import ValidationError

from . import secret_store
from .config import Settings
from .settings_sections import SECRET_FIELDS

_APP_NAME = "Kairos"
_SETTINGS_FILENAME = "settings.json"
_SCHEMA_VERSION = 1


def data_dir() -> Path:
    """Dossier de données de l'OS pour Kairos (créé si besoin)."""
    from platformdirs import user_data_dir

    path = Path(user_data_dir(_APP_NAME, appauthor=False))
    path.mkdir(parents=True, exist_ok=True)
    return path


def settings_path() -> Path:
    return data_dir() / _SETTINGS_FILENAME


def _read_envelope() -> dict:
    path = settings_path()
    if not path.exists():
        return {}
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except (OSError, ValueError):
        return {}


def _write_envelope(envelope: dict) -> None:
    path = settings_path()
    tmp_path = path.with_suffix(".json.tmp")
    tmp_path.write_text(
        json.dumps(envelope, indent=2, ensure_ascii=False, sort_keys=True),
        encoding="utf-8",
    )
    os.replace(tmp_path, path)  # écriture atomique : jamais de fichier à moitié écrit


def meta() -> dict:
    """Métadonnées de la dernière sauvegarde (ex. date de migration `.env`)."""
    return _read_envelope().get("meta", {})


def load() -> Settings:
    """Charge les réglages : fichier existant, sinon migration `.env`, sinon défauts."""
    envelope = _read_envelope()
    if not envelope:
        migrated = _migrate_legacy_env_if_needed()
        if migrated is not None:
            return migrated
        return Settings()
    data = dict(envelope.get("settings", {}))
    for field in SECRET_FIELDS:
        data[field] = secret_store.get_secret(field, plain_fallback=data.get(field, ""))
    try:
        return Settings(**data)
    except ValidationError:
        # Fichier corrompu/champ invalide après une modification manuelle : on ne
        # bloque jamais le démarrage de l'application pour un fichier de réglages,
        # dégradation propre vers les valeurs par défaut (cohérent avec le reste
        # du projet : jamais d'erreur dure sur une dépendance externe/fichier local).
        return Settings()


def save(settings: Settings) -> dict[str, str]:
    """Persiste ``settings`` (fichier JSON + trousseau système pour les secrets).

    Retourne un dict ``{champ: message}`` pour chaque secret retombé en repli
    fichier (trousseau système indisponible sur ce poste) — à afficher comme
    avertissement dans la page Réglages.
    """
    warnings: dict[str, str] = {}
    fallback_values: dict[str, str] = {}
    for field in SECRET_FIELDS:
        value = getattr(settings, field)
        stored, detail = secret_store.set_secret(field, value)
        if not stored:
            fallback_values[field] = value
            if detail:
                warnings[field] = detail

    dump = settings.model_dump(mode="json")
    for field in SECRET_FIELDS:
        dump.pop(field, None)
    dump.update(fallback_values)

    envelope = {"schema_version": _SCHEMA_VERSION, "settings": dump, "meta": meta()}
    _write_envelope(envelope)
    return warnings


_ENV_LINE_RE = re.compile(r"^([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*?)\s*$")


def _parse_dotenv(path: Path) -> dict[str, str]:
    """Petit parseur `.env` fait main (clé=valeur, commentaires `#`, pas de
    guillemets/multi-lignes à gérer — le format de l'ancien `.env.example` n'en
    a jamais eu besoin) : évite de garder `pydantic-settings` comme dépendance
    uniquement pour ce pont de migration ponctuel."""
    values: dict[str, str] = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        stripped = line.strip()
        if not stripped or stripped.startswith("#"):
            continue
        match = _ENV_LINE_RE.match(stripped)
        if not match:
            continue
        key, value = match.group(1), match.group(2)
        if len(value) >= 2 and value[0] == value[-1] and value[0] in "\"'":
            value = value[1:-1]
        values[key] = value
    return values


def _coerce(annotation, raw: str):
    if annotation is bool:
        return raw.strip().lower() in ("1", "true", "yes", "on", "vrai")
    if annotation is int:
        return int(raw)
    if annotation is float:
        return float(raw)
    return raw


def _find_legacy_env() -> Path | None:
    """Un `.env` éventuel dans le répertoire de lancement — même résolution que
    l'ancien `pydantic-settings` (`env_file=".env"`, relatif au cwd) : `make run`/
    `make dev` et le service systemd (`WorkingDirectory=`) lancent tous deux
    depuis la racine du dépôt."""
    candidate = Path.cwd() / ".env"
    return candidate if candidate.is_file() else None


def _migrate_legacy_env_if_needed() -> Settings | None:
    env_path = _find_legacy_env()
    if env_path is None:
        return None
    raw = _parse_dotenv(env_path)
    candidate: dict[str, object] = {}
    for key, value in raw.items():
        field = key.lower()
        if field not in Settings.model_fields:
            continue
        try:
            candidate[field] = _coerce(Settings.model_fields[field].annotation, value)
        except ValueError:
            continue  # valeur illisible pour ce type : ignorée, le défaut s'applique
    try:
        settings = Settings(**candidate)
    except ValidationError:
        settings = Settings()
    save(settings)
    envelope = _read_envelope()
    envelope["meta"] = {
        "migrated_from_env_at": datetime.now(timezone.utc).isoformat(),
        "migrated_from_env_path": str(env_path),
    }
    _write_envelope(envelope)
    return settings
