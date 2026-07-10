"""Agrégats statistiques de « Kairos » (logique pure, sans I/O).

Construit des indicateurs **constructifs** (actionnables) à partir des tâches et des
sessions de travail déjà collectées : débit hebdomadaire, calibration de l'estimation
(points de Fibonacci et minutes face au temps réel), répartition du temps réel par type,
flux/backlog, complétude des métadonnées. Aucune écriture ni accès réseau — la route
passe les objets déjà chargés en mémoire.

Deux conventions assumées et documentées :

- **Date de complétion.** Une tâche terminée n'a pas d'horodatage de complétion dédié ;
  on utilise ``updated_at`` (dernière modification), approximation déjà retenue ailleurs
  (section « Fait » du jour). Suffisant pour un outil personnel, pas un journal d'audit.
- **Honnêteté statistique.** Toute cellule reposant sur peu de tâches expose son effectif
  ``n`` ; en dessous de :data:`MIN_SAMPLE`, l'agrégat reste calculé mais est marqué
  « peu fiable » côté rendu — on n'invente pas de tendance à partir de trois points.
"""

from __future__ import annotations

from collections import defaultdict
from dataclasses import dataclass, field
from datetime import date, datetime, timedelta, timezone

from .config import Settings
from .tasks_models import Task, WorkSession
from .tasks_scheduling import urgency_bucket
from .tasks_staleness import days_stale
from .tasks_time import (
    session_minutes,
    sessions_in_range,
    spent_minutes_by_task,
    spent_minutes_by_type,
)

# En dessous de cet effectif, un agrégat (médiane par palier, biais…) est marqué peu
# fiable côté rendu — signal, pas un couperet : la valeur reste affichée avec son ``n``.
MIN_SAMPLE = 3


def _aware(dt: datetime) -> datetime:
    """Normalise en UTC-aware (les datetimes SQLite reviennent naïfs)."""
    return dt if dt.tzinfo else dt.replace(tzinfo=timezone.utc)


def _median(values: list[float]) -> float | None:
    """Médiane d'une liste (None si vide). Pure, sans dépendance (projet sobre)."""
    if not values:
        return None
    ordered = sorted(values)
    n = len(ordered)
    mid = n // 2
    if n % 2:
        return ordered[mid]
    return (ordered[mid - 1] + ordered[mid]) / 2


def _monday(day: date) -> date:
    """Lundi de la semaine de ``day`` (semaine ISO, lundi=0)."""
    return day - timedelta(days=day.weekday())


# --------------------------------------------------------------------------- #
# Structures de sortie
# --------------------------------------------------------------------------- #

@dataclass
class WeekThroughput:
    week_start: date
    label: str          # "%d/%m" du lundi
    completed: int      # tâches terminées cette semaine-là
    points: int         # somme des points de Fibonacci terminés (vélocité agile)


@dataclass
class FiboCalibration:
    points: int                 # palier de l'échelle de Fibonacci
    count: int                  # nb de tâches terminées chronométrées à ce palier
    median_minutes: int | None  # temps réel médian passé sur ces tâches

    @property
    def reliable(self) -> bool:
        return self.count >= MIN_SAMPLE


@dataclass
class EstimationBias:
    count: int              # tâches terminées ayant estimation ET temps réel
    estimated_minutes: int  # total estimé
    real_minutes: int       # total réel
    ratio: float            # réel / estimé (1.0 = pile, >1 = sous-estimation)

    @property
    def reliable(self) -> bool:
        return self.count >= MIN_SAMPLE


@dataclass
class TypeShare:
    key: str
    label: str
    minutes: int
    pct: int    # part du temps réel tracké de la fenêtre (0-100)


@dataclass
class TypeCalibration:
    key: str                    # valeur de Task.task_type (le libellé lui-même)
    count: int                  # nb de tâches terminées chronométrées de ce type
    median_minutes: int | None  # temps réel médian passé sur ces tâches

    @property
    def reliable(self) -> bool:
        return self.count >= MIN_SAMPLE


@dataclass
class FocusStats:
    session_count: int
    total_minutes: int
    avg_session_minutes: int | None  # durée moyenne d'une session (proxy de fragmentation)


@dataclass
class BacklogFlow:
    open_count: int                    # tâches todo (WIP)
    median_age_days: int | None        # âge médian des todo (aujourd'hui - création)
    overdue_count: int                 # todo en retard (bucket d'urgence 0)
    stale_count: int                   # todo qui traînent (days_stale non nul)
    completion_delay_days: int | None  # délai médian création → complétion (fenêtre)
    deadline_total: int                # terminées (fenêtre) qui avaient une échéance
    deadline_on_time: int              # …et terminées à temps

    @property
    def deadline_hit_pct(self) -> int | None:
        if not self.deadline_total:
            return None
        return round(100 * self.deadline_on_time / self.deadline_total)


@dataclass
class Completeness:
    total: int          # tâches todo (celles qu'il est encore utile de qualifier)
    with_points: int
    with_estimate: int
    with_type: int

    def _pct(self, n: int) -> int:
        return round(100 * n / self.total) if self.total else 0

    @property
    def points_pct(self) -> int:
        return self._pct(self.with_points)

    @property
    def estimate_pct(self) -> int:
        return self._pct(self.with_estimate)

    @property
    def type_pct(self) -> int:
        return self._pct(self.with_type)


@dataclass
class DashboardStats:
    window_weeks: int
    generated_for: date
    # KPIs (fenêtre récente)
    completed_in_window: int
    tracked_minutes_window: int
    # Panneaux
    throughput: list[WeekThroughput] = field(default_factory=list)
    calibration: list[FiboCalibration] = field(default_factory=list)
    bias: EstimationBias | None = None
    time_by_type: list[TypeShare] = field(default_factory=list)
    focus: FocusStats | None = None
    flow: BacklogFlow | None = None
    completeness: Completeness | None = None

    @property
    def has_any_data(self) -> bool:
        """Vrai s'il y a matière à afficher (au moins une tâche ou une session)."""
        return bool(
            self.completed_in_window
            or self.tracked_minutes_window
            or (self.flow and self.flow.open_count)
            or (self.completeness and self.completeness.total)
        )


# --------------------------------------------------------------------------- #
# Calculs (chacun pur et testable en isolation)
# --------------------------------------------------------------------------- #

def _done_date(task: Task) -> date | None:
    """Date de complétion approximée (``updated_at`` d'une tâche terminée)."""
    if task.status != "done" or task.updated_at is None:
        return None
    return _aware(task.updated_at).date()


def throughput_by_week(
    tasks: list[Task], today: date, weeks: int
) -> list[WeekThroughput]:
    """Tâches et points de Fibonacci terminés par semaine, sur les ``weeks`` dernières
    semaines (semaine courante incluse), zéro-remplies pour un axe temporel continu."""
    first_monday = _monday(today) - timedelta(weeks=weeks - 1)
    counts: dict[date, int] = defaultdict(int)
    points: dict[date, int] = defaultdict(int)
    for task in tasks:
        done = _done_date(task)
        if done is None or done < first_monday:
            continue
        wk = _monday(done)
        counts[wk] += 1
        points[wk] += task.fibonacci_points or 0
    return [
        WeekThroughput(
            week_start=(wk := first_monday + timedelta(weeks=i)),
            label=wk.strftime("%d/%m"),
            completed=counts.get(wk, 0),
            points=points.get(wk, 0),
        )
        for i in range(weeks)
    ]


def fibonacci_calibration(
    tasks: list[Task], spent_by_task: dict[int, int]
) -> list[FiboCalibration]:
    """Temps réel médian par palier de points de Fibonacci, sur les tâches terminées
    effectivement chronométrées (points renseignés **et** temps réel > 0).

    C'est l'indicateur clé de la boucle empirique : si les paliers 3 et 5 donnent le même
    temps réel, l'échelle ne discrimine pas et mérite d'être recalibrée à la main."""
    minutes_by_points: dict[int, list[float]] = defaultdict(list)
    for task in tasks:
        if task.status != "done" or not task.fibonacci_points:
            continue
        spent = spent_by_task.get(task.id, 0)
        if spent <= 0:
            continue
        minutes_by_points[task.fibonacci_points].append(spent)
    result = []
    for points in sorted(minutes_by_points):
        samples = minutes_by_points[points]
        median = _median(samples)
        result.append(
            FiboCalibration(
                points=points,
                count=len(samples),
                median_minutes=round(median) if median is not None else None,
            )
        )
    return result


def estimation_bias(
    tasks: list[Task], spent_by_task: dict[int, int]
) -> EstimationBias | None:
    """Biais global d'estimation : temps réel total / temps estimé total, sur les tâches
    terminées ayant une estimation ``estimated_minutes`` **et** du temps réel.

    Agrégat (pas une moyenne de ratios) : peu sensible aux micro-tâches, se lit
    « globalement vous passez ×N votre estimation ». None si aucune tâche éligible."""
    est_total = 0
    real_total = 0
    count = 0
    for task in tasks:
        if task.status != "done" or not task.estimated_minutes:
            continue
        spent = spent_by_task.get(task.id, 0)
        if spent <= 0:
            continue
        est_total += task.estimated_minutes
        real_total += spent
        count += 1
    if count == 0 or est_total == 0:
        return None
    return EstimationBias(
        count=count,
        estimated_minutes=est_total,
        real_minutes=real_total,
        ratio=real_total / est_total,
    )


def time_by_type(
    sessions: list[WorkSession],
    task_type_by_id: dict[int, str],
    *,
    now: datetime | None = None,
) -> list[TypeShare]:
    """Répartition du temps réel par type de tâche (parts en %), du plus au moins chronophage.
    Les sessions sans type rattaché sont regroupées sous « Sans type ». ``Task.task_type``
    est la valeur configurable (Settings.task_types) : ici directement le libellé affiché,
    pas de table de correspondance à part."""
    minutes = spent_minutes_by_type(sessions, task_type_by_id, now=now)
    total = sum(minutes.values())
    shares = [
        TypeShare(
            key=key,
            label=key or "Sans type",
            minutes=mins,
            pct=round(100 * mins / total) if total else 0,
        )
        for key, mins in minutes.items()
        if mins > 0
    ]
    shares.sort(key=lambda s: s.minutes, reverse=True)
    return shares


def calibration_by_type(
    tasks: list[Task], spent_by_task: dict[int, int]
) -> list[TypeCalibration]:
    """Temps réel médian par type de tâche, sur les tâches terminées et effectivement
    chronométrées (type renseigné, temps réel > 0) — même patron que
    :func:`fibonacci_calibration`, mais par type plutôt que par palier d'effort.

    Sert à suggérer une durée par défaut quand l'utilisateur choisit un type dans le
    formulaire d'édition (voir le JS de ``templates/kairos.html``) : plus on ferme de
    tâches d'un type donné, plus la suggestion se rapproche du temps réel observé."""
    minutes_by_type: dict[str, list[float]] = defaultdict(list)
    for task in tasks:
        if task.status != "done" or not task.task_type:
            continue
        spent = spent_by_task.get(task.id, 0)
        if spent <= 0:
            continue
        minutes_by_type[task.task_type].append(spent)
    result = []
    for key in sorted(minutes_by_type):
        samples = minutes_by_type[key]
        median = _median(samples)
        result.append(
            TypeCalibration(
                key=key,
                count=len(samples),
                median_minutes=round(median) if median is not None else None,
            )
        )
    return result


def focus_stats(
    sessions: list[WorkSession], *, now: datetime | None = None
) -> FocusStats:
    """Nombre de sessions, temps total et durée moyenne d'une session (proxy de
    fragmentation : beaucoup de sessions courtes = attention morcelée)."""
    durations = [session_minutes(s, now=now) for s in sessions]
    durations = [d for d in durations if d > 0]
    total = sum(durations)
    avg = round(total / len(durations)) if durations else None
    return FocusStats(session_count=len(durations), total_minutes=total, avg_session_minutes=avg)


def backlog_flow(
    tasks: list[Task], today: date, window_start: date, *, settings: Settings
) -> BacklogFlow:
    """Indicateurs de flux : WIP, âge du backlog, retards, délai de complétion, respect
    des échéances. Le backlog courant est mesuré sur tout l'ouvert ; les délais et le
    respect des échéances sur les tâches terminées dans la fenêtre récente."""
    todo = [t for t in tasks if t.status == "todo"]
    ages = [
        (today - _aware(t.created_at).date()).days
        for t in todo
        if t.created_at is not None
    ]
    median_age = _median([float(a) for a in ages])
    overdue = sum(1 for t in todo if urgency_bucket(t, today) == 0)
    stale = sum(
        1 for t in todo
        if days_stale(
            t, today,
            overdue_days=settings.stale_overdue_days,
            untouched_days=settings.stale_untouched_days,
        ) is not None
    )
    # Délais de complétion et respect des échéances : sur les terminées de la fenêtre.
    delays: list[float] = []
    deadline_total = 0
    deadline_on_time = 0
    for task in tasks:
        done = _done_date(task)
        if done is None or done < window_start:
            continue
        if task.created_at is not None:
            delays.append(float((done - _aware(task.created_at).date()).days))
        if task.deadline is not None:
            deadline_total += 1
            if done <= task.deadline:
                deadline_on_time += 1
    median_delay = _median(delays)
    return BacklogFlow(
        open_count=len(todo),
        median_age_days=round(median_age) if median_age is not None else None,
        overdue_count=overdue,
        stale_count=stale,
        completion_delay_days=round(median_delay) if median_delay is not None else None,
        deadline_total=deadline_total,
        deadline_on_time=deadline_on_time,
    )


def metadata_completeness(tasks: list[Task]) -> Completeness:
    """Part des tâches todo dont les métadonnées utiles à l'analyse sont renseignées
    (points, estimation, type) : les stats de calibration n'ont de sens que si ces champs
    sont posés — cet indicateur incite à les remplir."""
    todo = [t for t in tasks if t.status == "todo"]
    return Completeness(
        total=len(todo),
        with_points=sum(1 for t in todo if t.fibonacci_points),
        with_estimate=sum(1 for t in todo if t.estimated_minutes),
        with_type=sum(1 for t in todo if t.task_type),
    )


def compute_dashboard_stats(
    tasks: list[Task],
    sessions: list[WorkSession],
    today: date,
    *,
    settings: Settings,
    now: datetime | None = None,
) -> DashboardStats:
    """Point d'entrée : assemble tous les indicateurs à partir des tâches et sessions
    déjà chargées. Pur — aucune requête ni appel réseau."""
    weeks = max(1, settings.stats_window_weeks)
    window_start = _monday(today) - timedelta(weeks=weeks - 1)
    window_sessions = sessions_in_range(sessions, window_start, today)

    spent_all = spent_minutes_by_task(sessions, now=now, tasks=tasks)
    task_type_by_id = {t.id: t.task_type for t in tasks}

    return DashboardStats(
        window_weeks=weeks,
        generated_for=today,
        completed_in_window=sum(
            1 for t in tasks
            if (d := _done_date(t)) is not None and d >= window_start
        ),
        tracked_minutes_window=sum(session_minutes(s, now=now) for s in window_sessions),
        throughput=throughput_by_week(tasks, today, weeks),
        calibration=fibonacci_calibration(tasks, spent_all),
        bias=estimation_bias(tasks, spent_all),
        time_by_type=time_by_type(window_sessions, task_type_by_id, now=now),
        focus=focus_stats(window_sessions, now=now),
        flow=backlog_flow(tasks, today, window_start, settings=settings),
        completeness=metadata_completeness(tasks),
    )
