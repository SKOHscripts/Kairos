"""Sens des priorités et des paliers de points (audit UI) — app/task_guide.py."""

from __future__ import annotations

from app.task_guide import (
    FIBONACCI_GUIDE,
    PRIORITY_LEVELS,
    fibo_level,
    priority_level,
)
from app.tasks_models import FIBONACCI_SCALE
from app.tasks_scheduling import _PRIORITY_MAX


def test_fibonacci_guide_covers_exactly_the_model_scale() -> None:
    """Le guide est la source de vérité des libellés : un palier ajouté au
    modèle sans son sens (ou l'inverse) doit casser ici, pas en production."""
    assert tuple(level.points for level in FIBONACCI_GUIDE) == FIBONACCI_SCALE


def test_priority_levels_cover_exactly_the_scheduler_scale() -> None:
    assert tuple(level.value for level in PRIORITY_LEVELS) == tuple(range(_PRIORITY_MAX + 1))


def test_priority_labels_are_about_importance_not_deadlines() -> None:
    assert [level.label for level in PRIORITY_LEVELS] == ["Critique", "Important", "Utile"]
    assert priority_level(0).code == "P0"


def test_lookups_tolerate_missing_or_unknown_values() -> None:
    assert priority_level(None) is None
    assert priority_level(7) is None
    assert fibo_level(None) is None
    assert fibo_level(4) is None
    assert fibo_level(3).label == "modéré"
