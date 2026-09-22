"""Sens des valeurs que l'utilisateur pose lui-même : priorité et points.

Source **unique** (audit UI, voir `docs/spec/vue-jour-gtd.md` § Comprendre sans
quitter la liste) : les pastilles de qualification de la boîte de réception, le
panneau d'édition, le guide d'estimation et les infobulles des badges lisent
tous ces tables — un libellé ne se corrige qu'ici, jamais dans un gabarit.

Les priorités sont des degrés d'**importance**, pas de délai : l'urgence est
déjà portée par l'échéance dans le score WSJF (`_time_criticality`), des
libellés « urgent / cette semaine » auraient fait doublon avec elle.
"""

from __future__ import annotations

from dataclasses import dataclass


@dataclass(frozen=True)
class PriorityLevel:
    value: int
    label: str
    meaning: str

    @property
    def code(self) -> str:
        return f"P{self.value}"


@dataclass(frozen=True)
class FiboLevel:
    points: int
    label: str
    meaning: str
    example: str


PRIORITY_LEVELS: tuple[PriorityLevel, ...] = (
    PriorityLevel(
        0, "Critique",
        "bloquant ou engagement ferme — rare par nature",
    ),
    PriorityLevel(1, "Important", "compte vraiment, à caser cette semaine"),
    PriorityLevel(2, "Utile", "à faire quand il y a de la place"),
)

# Reprend mot pour mot l'échelle historique de l'aide « Comment estimer les
# points ? » (volume × complexité × incertitude), désormais source de vérité.
FIBONACCI_GUIDE: tuple[FiboLevel, ...] = (
    FiboLevel(1, "trivial", "expédié, sans réflexion", "valider une MR triviale"),
    FiboLevel(2, "petit", "chemin connu", "une petite revue de code"),
    FiboLevel(3, "modéré", "bien cadré, zéro inconnue", "un développement bien cadré"),
    FiboLevel(5, "conséquent", "plusieurs étapes ou un peu d'inconnu",
              "une fonctionnalité en plusieurs étapes"),
    FiboLevel(8, "gros", "vraie complexité ou inconnues",
              "un sujet qu'il faudra d'abord explorer"),
    FiboLevel(13, "très gros", "trop gros pour une seule tâche : à découper",
              "à découper en sous-tâches"),
    FiboLevel(21, "énorme", "un projet plutôt qu'une tâche : à découper",
              "à découper en sous-tâches"),
)

_PRIORITY_BY_VALUE = {level.value: level for level in PRIORITY_LEVELS}
_FIBO_BY_POINTS = {level.points: level for level in FIBONACCI_GUIDE}


def priority_level(value: int | None) -> PriorityLevel | None:
    return _PRIORITY_BY_VALUE.get(value) if value is not None else None


def fibo_level(points: int | None) -> FiboLevel | None:
    return _FIBO_BY_POINTS.get(points) if points is not None else None
