"""Agrégats de suivi du temps réel (logique pure, sans I/O).

Calcule le temps passé par tâche à partir des ``WorkSession``, identifie la session
en cours (minuteur qui tourne) et compare le réel à l'estimation. Aucune écriture :
les routes gèrent l'ouverture/fermeture des sessions.
"""

from __future__ import annotations

from collections import defaultdict
from collections.abc import Iterable
from datetime import date, datetime, timezone

from .tasks_models import Task, WorkSession


def _aware(dt: datetime) -> datetime:
    """Normalise en UTC-aware (les datetimes SQLite reviennent naïfs)."""
    return dt if dt.tzinfo else dt.replace(tzinfo=timezone.utc)


def session_minutes(session: WorkSession, *, now: datetime | None = None) -> int:
    """Durée d'une session en minutes ; une session ouverte court jusqu'à ``now``."""
    now = now or datetime.now(timezone.utc)
    end = _aware(session.ended_at) if session.ended_at is not None else now
    return max(0, int((end - _aware(session.started_at)).total_seconds() // 60))


def spent_minutes_by_task(
    sessions: list[WorkSession], *, now: datetime | None = None,
    tasks: Iterable[Task] | None = None,
) -> dict[int, int]:
    """Temps total (minutes) passé par tâche : sessions chronométrées (ouvertes
    incluses) + temps saisi à la main (``Task.manual_time_spent_minutes``, issue #6)
    quand ``tasks`` est fourni. Les deux s'additionnent, jamais l'un ne remplace
    l'autre — le manuel comble ce que le chrono n'a pas mesuré."""
    totals: dict[int, int] = defaultdict(int)
    for session in sessions:
        totals[session.task_id] += session_minutes(session, now=now)
    for task in tasks or ():
        if task.manual_time_spent_minutes:
            totals[task.id] += task.manual_time_spent_minutes
    return dict(totals)


def running_session(sessions: list[WorkSession]) -> WorkSession | None:
    """La session en cours (``ended_at`` NULL), ou None. La plus récemment démarrée
    si plusieurs subsistaient (ne devrait pas arriver : invariant d'unicité)."""
    open_sessions = [s for s in sessions if s.ended_at is None]
    if not open_sessions:
        return None
    return max(open_sessions, key=lambda s: _aware(s.started_at))


def total_minutes(sessions: list[WorkSession], *, now: datetime | None = None) -> int:
    """Temps réel total (toutes tâches) sur l'ensemble des sessions fournies."""
    return sum(session_minutes(s, now=now) for s in sessions)


def sessions_in_range(
    sessions: list[WorkSession], start_day: date, end_day: date
) -> list[WorkSession]:
    """Sessions dont le début tombe dans ``[start_day, end_day]`` (bornes incluses).

    Phase 7 : sert à corriger le calcul du « temps travaillé aujourd'hui », qui
    additionnait jusqu'ici toutes les sessions jamais enregistrées faute de
    filtrage par date en amont — ce filtrage se fait ici, pas dans les fonctions
    d'agrégation existantes (``total_minutes``/``spent_minutes_by_task``), qui
    restent volontairement inchangées et reçoivent la liste déjà filtrée.
    """
    return [s for s in sessions if start_day <= _aware(s.started_at).date() <= end_day]


def sessions_on_day(sessions: list[WorkSession], day: date) -> list[WorkSession]:
    """Sessions dont le début tombe le jour ``day`` — cas particulier de
    :func:`sessions_in_range` avec une borne unique."""
    return sessions_in_range(sessions, day, day)


def spent_minutes_by_type(
    sessions: list[WorkSession],
    task_type_by_id: dict[int, str],
    *,
    now: datetime | None = None,
) -> dict[str, int]:
    """Temps réel (minutes) regroupé par ``Task.task_type`` plutôt que par tâche
    — même patron que :func:`spent_minutes_by_task`. Une tâche sans type
    (``""``) ou absente de ``task_type_by_id`` est regroupée sous la clé ``""``.
    """
    totals: dict[str, int] = defaultdict(int)
    for session in sessions:
        totals[task_type_by_id.get(session.task_id, "")] += session_minutes(session, now=now)
    return dict(totals)
