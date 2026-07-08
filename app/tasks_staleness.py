"""Détection des tâches qui traînent (phase 7).

Fonction pure : aucun accès DB, purement un signal d'affichage supplémentaire.
Ne modifie jamais l'ordre de tri ni les buckets d'urgence existants
(``app/tasks_scheduling.py``) — une tâche qui traîne depuis 1 jour et une qui
traîne depuis 3 semaines partagent le même bucket, mais seule cette fonction
distingue les deux à l'affichage.
"""

from __future__ import annotations

from datetime import date

from .tasks_models import Task


def days_stale(
    task: Task, today: date, *, overdue_days: int, untouched_days: int
) -> int | None:
    """Nombre de jours « de trop » si la tâche traîne, sinon ``None``.

    Une tâche traîne si :
    - sa ``deadline`` ou sa ``scheduled_date`` est dépassée de plus de
      ``overdue_days`` jours (la plus ancienne des deux dates dépassées sert de
      référence : c'est depuis ce moment-là que la tâche est actionnable) ; ou
    - elle n'a ni l'une ni l'autre et n'a pas été modifiée
      (``Task.updated_at``) depuis plus de ``untouched_days`` jours.
    """
    overdue_dates = [
        d for d in (task.deadline, task.scheduled_date) if d is not None and d <= today
    ]
    if overdue_dates:
        days_overdue = (today - min(overdue_dates)).days
        return days_overdue if days_overdue > overdue_days else None

    if task.deadline is None and task.scheduled_date is None:
        days_untouched = (today - task.updated_at.date()).days
        return days_untouched if days_untouched > untouched_days else None

    return None
