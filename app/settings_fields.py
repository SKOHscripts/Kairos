"""Métadonnées des champs de réglages, sans Pydantic.

Reproduit la petite surface que le projet utilisait de ``pydantic.BaseModel`` :
défauts, description (affichée page Réglages), bornes numériques (``ge``/``gt``/
``le``), validation des types à la construction, et le registre
``Settings.model_fields`` — mêmes noms d'attributs (``.annotation``,
``.description``) qu'avant la migration, pour ne toucher ni les gabarits ni les
appelants.

Motivation : le portage Android (voir ``docs/ANDROID_PACKAGING.md``). FastAPI
dépend structurellement de Pydantic v2, donc de ``pydantic-core`` — un module
Rust compilé sans wheel Android nulle part (vérifié le 2026-07-12 : PyPI toutes
versions, dépôt Chaquopy, canal BeeWare). En retirant Pydantic (et FastAPI, au
profit de Starlette), plus aucune extension native ne bloque le chemin.
"""

from __future__ import annotations

import dataclasses
from typing import get_type_hints


class SettingsValidationError(ValueError):
    """Réglages invalides. ``errors`` mappe chaque champ fautif vers un message
    affichable ; la clé ``_general`` porte les règles inter-champs (plages)."""

    def __init__(self, errors: dict[str, str]):
        super().__init__("; ".join(f"{name}: {msg}" for name, msg in errors.items()))
        self.errors = errors


def Field(
    default=dataclasses.MISSING,
    *,
    default_factory=dataclasses.MISSING,
    description: str = "",
    ge: float | None = None,
    gt: float | None = None,
    le: float | None = None,
):
    """Champ de réglage : un ``dataclasses.field`` portant les métadonnées que la
    page Réglages et la validation consomment. Même signature d'appel que le
    ``pydantic.Field`` qu'il remplace (pour la surface utilisée ici)."""
    metadata = {"description": description, "ge": ge, "gt": gt, "le": le}
    if default_factory is not dataclasses.MISSING:
        return dataclasses.field(default_factory=default_factory, metadata=metadata)
    return dataclasses.field(default=default, metadata=metadata)


@dataclasses.dataclass(frozen=True)
class FieldInfo:
    """Métadonnées d'un champ, sous les noms d'attributs attendus par les
    appelants historiques (``.annotation``/``.description``)."""

    annotation: type
    description: str = ""
    ge: float | None = None
    gt: float | None = None
    le: float | None = None


def build_field_registry(cls) -> dict[str, FieldInfo]:
    """Registre ``nom → FieldInfo`` d'une dataclass de réglages (équivalent de
    l'ancien ``Settings.model_fields`` de Pydantic)."""
    hints = get_type_hints(cls)
    return {
        f.name: FieldInfo(
            annotation=hints[f.name],
            description=f.metadata.get("description", ""),
            ge=f.metadata.get("ge"),
            gt=f.metadata.get("gt"),
            le=f.metadata.get("le"),
        )
        for f in dataclasses.fields(cls)
    }


def validate_fields(obj) -> dict[str, str]:
    """Types et bornes de chaque champ ; retourne les erreurs par champ (vide = OK).

    Un ``int`` est accepté pour un champ ``float`` (converti sur place — un JSON
    édité à la main écrit volontiers ``4`` pour ``4.0``) ; ``bool`` n'est jamais
    accepté comme nombre (piège classique d'``isinstance(True, int)``).
    """
    errors: dict[str, str] = {}
    for name, info in type(obj).model_fields.items():
        value = getattr(obj, name)
        annotation = info.annotation
        if annotation is bool:
            if not isinstance(value, bool):
                errors[name] = "Valeur booléenne attendue."
                continue
        elif annotation is int:
            if isinstance(value, bool) or not isinstance(value, int):
                errors[name] = "Valeur entière attendue."
                continue
        elif annotation is float:
            if isinstance(value, bool) or not isinstance(value, (int, float)):
                errors[name] = "Valeur numérique attendue."
                continue
            setattr(obj, name, float(value))
            value = getattr(obj, name)
        elif annotation is str and not isinstance(value, str):
            errors[name] = "Texte attendu."
            continue
        if info.gt is not None and not value > info.gt:
            errors[name] = f"Doit être strictement supérieur à {info.gt:g}."
        elif info.ge is not None and not value >= info.ge:
            errors[name] = f"Doit être supérieur ou égal à {info.ge:g}."
        elif info.le is not None and not value <= info.le:
            errors[name] = f"Doit être inférieur ou égal à {info.le:g}."
    return errors
