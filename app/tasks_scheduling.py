"""Algorithme d'ordonnancement de « Kairos » (v2 : time blocking).

Fonction pure (:func:`build_day_schedule`) : aucun accès DB ni réseau, testable en
isolation totale. Elle répond au critère de succès emblématique de la spec : une
réunion 13h-14h doit repousser une tâche urgente à 14h05 (marge incluse), avec une
note explicite.

Mode **mixte auto + épinglage** (décision actée phase 2) :
- une tâche **épinglée** (``pinned_start`` sur le jour affiché) est posée exactement
  à son heure — un chevauchement avec un créneau occupé est **signalé**, jamais
  résolu d'office (c'est un choix de l'utilisateur) ;
- les autres tâches sont placées automatiquement dans les trous, par urgence, avec
  leur **durée réelle** (``estimated_minutes``, repli sur le réglage).

Tri par **buckets d'urgence** (décision actée avec l'utilisateur, étendue en phase 4
pour ``scheduled_date``) :
  0. en retard (``deadline`` ou ``scheduled_date`` `<= day`),
  1. priorité maximale (0-1) quelle que soit la date,
  2. ``scheduled_date == day`` (programmée pour aujourd'hui),
  3. deadline cette semaine,
  4. le reste,
avec la priorité en départage à l'intérieur de chaque bucket (idiome « None en
dernier » repris du tri de :func:`app.queries.filter_tickets`).

**Éligibilité du jour** (phase 4) : ``scheduled_date`` (« quand je compte m'y
mettre ») est distinct de ``deadline`` (l'échéance réelle). Une tâche est masquée de
l'agenda du jour **seulement si** sa ``scheduled_date`` est future **et** qu'aucune
échéance n'approche (``deadline is None`` ou ``deadline > day``) — l'échéance reste
un garde-fou qui prime toujours. Les tâches masquées vont dans ``ScheduledDay.later``,
jamais nulle part ailleurs (conservation garantie, voir les tests d'invariant).
"""

from __future__ import annotations

from dataclasses import dataclass, field
from datetime import date, datetime, time, timedelta, timezone

from .config import Settings
from .tasks_models import FIBONACCI_SCALE, Task, TimeBlock, WorkSession

# Complexité maximale de l'échelle (21) : borne de normalisation du creux (voir
# _dip_adjusted_effort) — une tâche à 21 points est « aussi complexe que possible ».
_MAX_FIBONACCI = float(FIBONACCI_SCALE[-1])


@dataclass
class ScheduledTask:
    task: Task
    start_at: datetime
    duration_minutes: int
    pinned: bool = False
    pushed: bool = False
    pushed_note: str = ""
    # Épinglée sur un créneau occupé : signalé, jamais déplacé d'office.
    conflict: bool = False
    conflict_note: str = ""
    # Placée dans un bloc deep-work réservé (fenêtre dédiée, non fragmentée).
    deepwork: bool = False
    # Posée ici parce que le créneau tombe dans le creux de l'après-midi et que cette
    # tâche légère y a été préférée à une tâche plus complexe (transparence — voir
    # _selection_key). Vide si le creux n'a pas influé sur ce placement.
    dip_note: str = ""

    @property
    def end_at(self) -> datetime:
        return self.start_at + timedelta(minutes=self.duration_minutes)


@dataclass
class DayStats:
    """Charge du jour : ce qui est requis vs ce qui reste réellement disponible."""

    required_minutes: int = 0
    available_minutes: int = 0

    @property
    def overflow_minutes(self) -> int:
        return max(0, self.required_minutes - self.available_minutes)


@dataclass
class ScheduledDay:
    scheduled: list[ScheduledTask] = field(default_factory=list)
    unscheduled: list[Task] = field(default_factory=list)
    # Masquées aujourd'hui : `scheduled_date` future sans échéance imminente (phase 4).
    later: list[Task] = field(default_factory=list)
    # « À traiter » (GTD, phase 12) : ni priorité ni points de Fibonacci renseignés —
    # pas encore « clarifiée ». Exclue de tout tri tant qu'elle ne l'est pas, avant même
    # le blocage/la programmation (voir build_day_schedule).
    to_process: list[Task] = field(default_factory=list)
    stats: DayStats = field(default_factory=DayStats)


@dataclass
class _Obstacle:
    """Intervalle indisponible pour le placement automatique."""

    start: datetime
    end: datetime
    title: str
    # Marge à laisser après l'intervalle : celle des réunions (`meeting_buffer_minutes`)
    # pour un créneau occupé, aucune après une tâche épinglée (enchaîner deux blocs de
    # travail ne demande pas de « temps de sortie de réunion »).
    buffer: timedelta
    kind: str  # 'busy' | 'pinned'


def _end_of_week(day: date) -> date:
    """Dimanche de la semaine de ``day`` (``weekday()`` : lundi=0 … dimanche=6)."""
    return day + timedelta(days=6 - day.weekday())


# Priorité maximale de l'échelle (0 = la plus forte … PRIORITY_MAX = la plus faible).
# Sert d'exposant de référence à la valeur exponentielle : `base ** (PRIORITY_MAX - p)`.
# Échelle volontairement resserrée à P0/P1/P2 (3 crans) : plus de crans dilue le
# signal plutôt que de l'affiner.
_PRIORITY_MAX = 2


def _is_overdue(task: Task, day: date) -> bool:
    """Vrai si l'échéance ou la date programmée est atteinte/dépassée (palier dur du tri)."""
    return (task.deadline is not None and task.deadline <= day) or (
        task.scheduled_date is not None and task.scheduled_date <= day
    )


def _urgency_bucket(task: Task, day: date) -> int:
    if _is_overdue(task, day):
        return 0
    # Mis en avant (bordure) : seule P0 (la plus forte de l'échelle P0/P1/P2) —
    # `<= 1` incluait encore P1, artefact de l'ancienne échelle à 5 crans (P0-P4).
    if task.priority == 0:
        return 1
    if task.scheduled_date is not None and task.scheduled_date == day:
        return 2
    if task.deadline is not None and task.deadline <= _end_of_week(day):
        return 3
    return 4


def urgency_bucket(task: Task, day: date) -> int:
    """Palier d'urgence 0-4 (public) : signal **visuel** de pression temporelle (bordure
    colorée du template), volontairement découplé de l'ordre de tri WSJF — l'ordre suit
    le score, la couleur reste une lecture rapide « à quel point c'est pressé »."""
    return _urgency_bucket(task, day)


# --------------------------------------------------------------------------- #
# Ordonnancement WSJF (phase 9) : score = coût du retard / effort
# --------------------------------------------------------------------------- #
# « Weighted Shortest Job First » (Reinertsen), variante de la règle de Smith (1956) :
# pour maximiser la valeur livrée par unité de temps, ordonner par valeur/effort. On y
# ajoute une criticité temporelle (échéance qui approche) au numérateur — les échéances
# DÉPASSÉES, elles, restent un palier dur (toujours devant), pas une simple pondération.


def _priority_value(priority: int | None, settings: Settings) -> float:
    """Valeur (numérateur) d'une priorité, **exponentielle** : `base ** (PRIORITY_MAX - priorité)`.

    Une P0 vaut ``base`` fois plus qu'une P1, etc. — « critique » n'est pas juste « un cran
    au-dessus d'important ». Une tâche sans priorité vaut ``base ** -1`` (sous P2, la plus
    faible du barème) : elle reste derrière une priorité affirmée à effort et échéance
    égaux, sans valoir zéro.
    """
    base = settings.priority_value_base
    if priority is None:
        return base ** -1
    clamped = max(0, min(_PRIORITY_MAX, priority))
    return base ** (_PRIORITY_MAX - clamped)


def _time_criticality(task: Task, day: date, settings: Settings) -> float:
    """Criticité temporelle (numérateur) : rampe **linéaire** 0 → ``urgency_peak``.

    Nulle au-delà de ``urgency_horizon_days`` avant l'échéance (ou la date programmée, la
    plus proche des deux) ; croît linéairement à l'approche ; plafonne à ``urgency_peak``
    la veille/le jour même. Le dépassement est traité en amont par le palier dur du tri
    (voir :func:`_sort_key`), donc pas de valeur spéciale « en retard » ici.
    """
    dates = [d for d in (task.deadline, task.scheduled_date) if d is not None]
    if not dates:
        return 0.0
    horizon = settings.urgency_horizon_days
    peak = settings.urgency_peak
    days_until = (min(dates) - day).days
    if days_until >= horizon:
        return 0.0
    if days_until <= 0:
        return peak
    return peak * (horizon - days_until) / horizon


def _effort_points(task: Task, settings: Settings) -> float:
    """Effort (dénominateur) d'une tâche, en « points » homogènes à Fibonacci.

    Priorité aux ``fibonacci_points`` saisis ; à défaut, l'estimation en minutes ramenée à
    l'échelle (≈ 1 point / 30 min, bornée à 1-21) ; à défaut encore, ``default_fibonacci_
    points`` (neutre). Ainsi, sans effort renseigné, le tri dégrade proprement vers l'ordre
    de priorité — plus on renseigne le Fibo, plus le tri s'affine.
    """
    if task.fibonacci_points is not None and task.fibonacci_points > 0:
        return float(task.fibonacci_points)
    if task.estimated_minutes is not None and task.estimated_minutes > 0:
        return min(21.0, max(1.0, task.estimated_minutes / 30))
    return float(settings.default_fibonacci_points)


def wsjf_score(task: Task, day: date, *, settings: Settings) -> float:
    """Score WSJF : ``(valeur(priorité) + criticité(échéance)) / effort``. Plus grand =
    plus prioritaire. Fonction pure, testable en isolation (aucun accès DB/réseau)."""
    cost_of_delay = _priority_value(task.priority, settings) + _time_criticality(
        task, day, settings
    )
    return cost_of_delay / _effort_points(task, settings)


# --------------------------------------------------------------------------- #
# Creux de l'après-midi (post-lunch dip) : modulation du PLACEMENT par l'heure
# --------------------------------------------------------------------------- #
# Le score WSJF affiché ne bouge pas (c'est une propriété de la tâche). Ce qui bouge,
# c'est *où* une tâche est posée : pendant le creux, l'effort effectif d'une tâche est
# gonflé PROPORTIONNELLEMENT À SA COMPLEXITÉ (points de Fibonacci), donc son score de
# placement baisse et une tâche légère lui prend le créneau. Hors du creux, l'effort
# effectif == effort réel ⇒ placement identique à l'ordonnancement d'urgence pur.


def _dip_intensity(when: datetime, settings: Settings) -> float:
    """Intensité du creux ∈ [0, 1] à l'instant ``when`` : triangle asymétrique, 0 aux
    bords de la fenêtre, 1 au tronc (le point le plus creux). 0 si désactivé, pénalité
    nulle, ou hors de la fenêtre ``[start_hour, end_hour]``."""
    if not settings.cognitive_dip_enabled or settings.cognitive_dip_penalty <= 0:
        return 0.0
    start = settings.cognitive_dip_start_hour
    trough = settings.cognitive_dip_trough_hour
    end = settings.cognitive_dip_end_hour
    hour = when.hour + when.minute / 60.0
    if hour <= start or hour >= end:
        return 0.0
    if hour <= trough:
        return (hour - start) / (trough - start) if trough > start else 1.0
    return (end - hour) / (end - trough) if end > trough else 1.0


def _dip_adjusted_effort(task: Task, when: datetime, settings: Settings) -> float:
    """Effort effectif au créneau ``when`` : l'effort réel, renchéri pendant le creux en
    proportion de la complexité de la tâche. Une tâche à 1 point n'est jamais pénalisée
    (elle « passe » partout) ; une tâche à 21 points l'est au maximum (au tronc, pénalité
    1.0 ⇒ effort ×2, score de placement ÷2). Hors creux, retourne l'effort réel."""
    effort = _effort_points(task, settings)
    intensity = _dip_intensity(when, settings)
    if intensity <= 0:
        return effort
    norm = max(0.0, min(1.0, (effort - 1.0) / (_MAX_FIBONACCI - 1.0)))
    return effort * (1.0 + settings.cognitive_dip_penalty * intensity * norm)


def _placement_score(task: Task, day: date, when: datetime, *, settings: Settings) -> float:
    """Score de PLACEMENT au créneau ``when`` : même numérateur que WSJF (coût du retard),
    divisé par l'effort renchéri par le creux. Égal à ``wsjf_score`` hors du creux."""
    cost_of_delay = _priority_value(task.priority, settings) + _time_criticality(
        task, day, settings
    )
    return cost_of_delay / _dip_adjusted_effort(task, when, settings)


def count_max_priority_tasks(tasks: list[Task]) -> int:
    """Nombre de tâches à priorité **strictement maximale** (P0 uniquement) parmi
    ``tasks`` — l'appelant est censé n'y passer que des tâches ``todo``. Sert de
    garde-fou de surcharge (phase 7) : si trop de tâches partagent la priorité
    maximale, le signal de priorité se dilue. Échelle resserrée à P0/P1/P2 : élargir
    à P1 diluerait déjà le garde-fou lui-même (2 des 3 crans du barème).
    """
    return sum(1 for t in tasks if t.priority == 0)


def is_eligible_today(task: Task, day: date, *, pinned_for_today: bool = False) -> bool:
    """Vrai si la tâche doit apparaître dans l'agenda de ``day`` (pas dans « plus tard »).

    Une tâche épinglée explicitement sur ``day`` est toujours éligible (l'épinglage
    est un choix plus fort que la programmation). Sinon, masquée seulement si
    programmée pour une date future **et** sans échéance imminente.
    """
    if pinned_for_today:
        return True
    hidden = (
        task.scheduled_date is not None and task.scheduled_date > day
        and (task.deadline is None or task.deadline > day)
    )
    return not hidden


def _sort_key(task: Task, day: date, settings: Settings) -> tuple:
    """Clé de tri hybride : **plus petite = plus urgente** (convention du scheduler et de
    l'urgence dérivée des dépendances).

    1. Palier dur ``0`` pour les tâches en retard (échéance/date programmée atteinte),
       ``1`` pour les autres — une échéance dépassée passe toujours devant, quel que soit
       le score (choix « hybride » acté avec l'utilisateur).
    2. À l'intérieur d'un palier, ``-score`` WSJF : le score le plus élevé sort en premier.
    3. Départages déterministes (priorité, échéance, id) pour un ordre stable à score égal.
    """
    priority_value = task.priority if task.priority is not None else 999
    deadline_value = task.deadline or date.max
    return (
        0 if _is_overdue(task, day) else 1,
        -wsjf_score(task, day, settings=settings),
        priority_value,
        deadline_value,
        task.id or 0,
    )


def urgency_key(task: Task, day: date, *, settings: Settings) -> tuple:
    """Clé de tri d'urgence propre d'une tâche (publique : base de l'urgence dérivée
    des dépendances, calculée côté route avant d'appeler ``build_day_schedule``)."""
    return _sort_key(task, day, settings)


def _selection_key(
    task: Task, day: date, cursor: datetime, settings: Settings,
    urgency_keys: dict[int, tuple],
) -> tuple:
    """Clé de choix d'une tâche POUR le créneau ``cursor`` (plus petite = choisie).

    Réduit **exactement** à la clé d'urgence là où il ne faut rien changer :
    - hors du creux (``cursor`` en dehors de la fenêtre) → ordre d'urgence pur ;
    - tâche remontée par une dépendance (chemin critique) → intouchable, ordre d'urgence.
    Sinon (tâche ordinaire, pendant le creux) : on garde le **palier dur** (0/1, en
    retard/critique vs reste, jamais réordonné entre paliers) et on remplace le score
    intra-palier par le score de placement horaire — une tâche légère passe devant une
    tâche complexe sur ce créneau creux.
    """
    own = _sort_key(task, day, settings)
    effective = urgency_keys.get(task.id, own)
    if effective != own or _dip_intensity(cursor, settings) <= 0:
        return effective
    return (
        own[0],
        -_placement_score(task, day, cursor, settings=settings),
        own[2], own[3], own[4],
    )


def _advance_past_obstacles(cursor: datetime, obstacles: list["_Obstacle"]) -> datetime:
    """Premier instant ≥ ``cursor`` hors de tout obstacle (marge incluse) — enchaîne les
    obstacles adjacents. Sert à évaluer l'énergie du créneau à l'heure de placement
    RÉELLE (un curseur au milieu d'une réunion 13h-14h ⇒ créneau évalué à 14h05)."""
    moved = True
    while moved:
        moved = False
        for obstacle in obstacles:
            if obstacle.start <= cursor < obstacle.end:
                cursor = obstacle.end + obstacle.buffer
                moved = True
    return cursor


def _duration_minutes(task: Task, settings: Settings) -> int:
    return task.estimated_minutes or settings.default_task_duration_minutes


def _busy_minutes_in_window(
    blocks: list[TimeBlock], window_start: datetime, window_end: datetime
) -> int:
    """Minutes occupées dans la fenêtre, chevauchements fusionnés (pas de double compte)."""
    merged: list[tuple[datetime, datetime]] = []
    for block in sorted(blocks, key=lambda b: b.start):
        start = max(block.start, window_start)
        end = min(block.end, window_end)
        if end <= start:
            continue
        if merged and start <= merged[-1][1]:
            merged[-1] = (merged[-1][0], max(merged[-1][1], end))
        else:
            merged.append((start, end))
    return sum(int((end - start).total_seconds() // 60) for start, end in merged)


@dataclass
class TimelineEntry:
    """Bloc positionné sur la timeline verticale du jour (1 minute = 1 unité).

    ``top_min``/``height_min`` sont des offsets en minutes depuis le début de la
    journée de travail, bornés à la fenêtre affichée : le template les convertit
    directement en pixels (rendu serveur, aucun JavaScript).
    """

    kind: str  # 'busy' | 'work' | 'pinned' | 'conflict'
    title: str
    start: datetime
    end: datetime
    top_min: int
    height_min: int


def build_timeline(
    schedule: ScheduledDay,
    busy_blocks: list[TimeBlock],
    day: date,
    *,
    settings: Settings,
) -> list[TimelineEntry]:
    """Projette créneaux occupés et blocs de travail sur la timeline du jour."""
    workday_start = datetime.combine(day, time(hour=settings.workday_start_hour))
    workday_end = datetime.combine(day, time(hour=settings.workday_end_hour))

    def _entry(kind: str, title: str, start: datetime, end: datetime) -> TimelineEntry | None:
        clamped_start = max(start, workday_start)
        clamped_end = min(end, workday_end)
        if clamped_end <= clamped_start:
            return None
        top = int((clamped_start - workday_start).total_seconds() // 60)
        height = int((clamped_end - clamped_start).total_seconds() // 60)
        return TimelineEntry(kind=kind, title=title, start=start, end=end,
                             top_min=top, height_min=height)

    entries: list[TimelineEntry] = []
    for block in busy_blocks:
        block_kind = "deepwork" if getattr(block, "kind", "busy") == "deepwork" else "busy"
        title = block.title or ("Deep work" if block_kind == "deepwork" else "")
        if entry := _entry(block_kind, title, block.start, block.end):
            entries.append(entry)
    for item in schedule.scheduled:
        if item.conflict:
            kind = "conflict"
        elif item.deepwork:
            kind = "deepwork-task"
        elif item.pinned:
            kind = "pinned"
        else:
            kind = "work"
        if entry := _entry(kind, item.task.title, item.start_at, item.end_at):
            entries.append(entry)
    # Les fonds de blocs (busy/deepwork) d'abord, puis les tâches posées au-dessus.
    entries.sort(key=lambda e: (e.top_min, e.kind not in ("busy", "deepwork")))
    return entries


def _to_local_naive(dt: datetime) -> datetime:
    """Ramène un horodatage (``WorkSession`` stocké en UTC) au fuseau local naïf, frame de
    la timeline (les blocs/épinglages y sont en heure locale naïve)."""
    aware = dt if dt.tzinfo else dt.replace(tzinfo=timezone.utc)
    return aware.astimezone().replace(tzinfo=None)


def session_timeline_entries(
    sessions: list[WorkSession],
    day: date,
    title_by_id: dict[int, str],
    *,
    settings: Settings,
    now: datetime | None = None,
) -> list[TimelineEntry]:
    """Projette les **sessions de travail réelles** de ``day`` sur la timeline (kind
    ``'session'``) : le « réel » à côté du « planifié », pour comparer d'un coup d'œil.

    Une session ouverte (chrono en cours) court jusqu'à ``now`` (heure locale). Pure :
    ``now`` est injectable pour les tests ; par défaut l'heure locale courante."""
    workday_start = datetime.combine(day, time(hour=settings.workday_start_hour))
    workday_end = datetime.combine(day, time(hour=settings.workday_end_hour))
    now = now or datetime.now()
    entries: list[TimelineEntry] = []
    for s in sessions:
        start = _to_local_naive(s.started_at)
        end = _to_local_naive(s.ended_at) if s.ended_at is not None else now
        clamped_start = max(start, workday_start)
        clamped_end = min(end, workday_end)
        if clamped_end <= clamped_start:
            continue  # hors de la fenêtre de travail affichée
        top = int((clamped_start - workday_start).total_seconds() // 60)
        height = int((clamped_end - clamped_start).total_seconds() // 60)
        entries.append(
            TimelineEntry(
                kind="session", title=title_by_id.get(s.task_id, ""),
                start=start, end=end, top_min=top, height_min=max(1, height),
            )
        )
    entries.sort(key=lambda e: e.top_min)
    return entries


def build_day_schedule(
    tasks: list[Task],
    busy_blocks: list[TimeBlock],
    day: date,
    *,
    now: datetime | None = None,
    settings: Settings,
    blocked_ids: set[int] | None = None,
    urgency_keys: dict[int, tuple] | None = None,
) -> ScheduledDay:
    """Ordonnance les tâches ``todo`` du jour compte tenu des créneaux occupés.

    Une tâche **mère** (dont au moins une sous-tâche est à faire) n'est pas planifiée
    elle-même : ce sont ses sous-tâches — les unités de travail réelles — qui le
    sont. Une mère dont toutes les filles sont faites redevient planifiable (le
    travail restant est le sien).

    **« À traiter » (GTD, phase 12)** : une unité de travail sans priorité **ni** points
    de Fibonacci n'est pas encore « clarifiée » — elle est retirée de tout tri **avant**
    même de regarder si elle est bloquée ou programmée plus tard (``result.to_process``),
    pour forcer sa qualification avant qu'elle n'entre dans le flux normal. S'applique à
    toute source (natives et importées) : ni la synchro GitLab ni aucune autre n'écrit
    jamais ces deux champs.

    ``blocked_ids`` : tâches bloquées par une dépendance non levée — retirées de la
    planification (ni placées, ni comptées dans la charge). ``urgency_keys`` : clé de
    tri effective par id (urgence dérivée du chemin critique), avec repli sur la clé
    d'urgence propre. Les deux sont pré-calculés par la route pour garder cette
    fonction pure (voir ``app/tasks_dependencies.py``).
    """
    blocked_ids = blocked_ids or set()
    urgency_keys = urgency_keys or {}

    def _key(task: Task) -> tuple:
        return urgency_keys.get(task.id, _sort_key(task, day, settings))

    todo = [t for t in tasks if t.status == "todo"]
    parents_with_open_children = {
        t.parent_id for t in todo if t.parent_id is not None
    }
    work_units = [t for t in todo if t.id not in parents_with_open_children]
    to_process = [
        t for t in work_units if t.priority is None or t.fibonacci_points is None
    ]
    to_process_ids = {t.id for t in to_process}
    non_blocked = [
        t for t in work_units
        if t.id not in to_process_ids and t.id not in blocked_ids
    ]
    pinned_today = [
        t for t in non_blocked
        if t.pinned_start is not None and t.pinned_start.date() == day
    ]
    pinned_ids = {t.id for t in pinned_today}

    result = ScheduledDay(to_process=to_process)
    schedulable: list[Task] = []
    for t in non_blocked:
        if is_eligible_today(t, day, pinned_for_today=t.id in pinned_ids):
            schedulable.append(t)
        else:
            result.later.append(t)

    auto = sorted(
        (t for t in schedulable if t not in pinned_today), key=_key
    )

    workday_start = datetime.combine(day, time(hour=settings.workday_start_hour))
    workday_end = datetime.combine(day, time(hour=settings.workday_end_hour))
    effective_start = workday_start
    if now is not None and now.date() == day and now > effective_start:
        effective_start = now

    buffer = timedelta(minutes=settings.meeting_buffer_minutes)
    in_window = [b for b in busy_blocks if b.start < workday_end and b.end > workday_start]
    # Occupés (réunions, TimeTree) vs blocs deep-work réservés : les premiers sont
    # indisponibles, les seconds sont du temps de travail dédié à une seule tâche.
    day_blocks = [b for b in in_window if getattr(b, "kind", "busy") != "deepwork"]
    deepwork_blocks = sorted(
        (b for b in in_window if getattr(b, "kind", "busy") == "deepwork"),
        key=lambda b: b.start,
    )

    assigned_ids: set[int] = set()

    # 1. Les épinglées d'abord : posées à leur heure, chevauchements signalés.
    obstacles = [
        _Obstacle(start=b.start, end=b.end, title=b.title, buffer=buffer, kind="busy")
        for b in day_blocks
    ]
    for task in sorted(pinned_today, key=lambda t: t.pinned_start):
        duration = _duration_minutes(task, settings)
        start_at = task.pinned_start
        end_at = start_at + timedelta(minutes=duration)
        clash = next(
            (b for b in day_blocks if b.start < end_at and b.end > start_at), None
        )
        result.scheduled.append(
            ScheduledTask(
                task=task, start_at=start_at, duration_minutes=duration, pinned=True,
                conflict=clash is not None,
                conflict_note=f"chevauche « {clash.title} »" if clash else "",
            )
        )
        assigned_ids.add(task.id)
        obstacles.append(
            _Obstacle(start=start_at, end=end_at, title=task.title,
                      buffer=timedelta(0), kind="pinned")
        )

    # 1bis. Blocs deep-work : chaque fenêtre réservée est remplie par autant de tâches
    # que nécessaire (les plus urgentes non encore placées), chacune gardant SA PROPRE
    # durée (_duration_minutes) — jamais la durée du bloc. Le bloc entier reste ensuite
    # un obstacle pour le placement automatique général, rempli en totalité ou non :
    # c'est une fenêtre dédiée, les tâches "normales" ne s'y intercalent jamais entre
    # deux tâches deep-work.
    for block in deepwork_blocks:
        slot_cursor = block.start
        while slot_cursor < block.end:
            candidate = next(
                (t for t in auto if t.id not in assigned_ids), None
            )
            if candidate is None:
                break
            duration = _duration_minutes(candidate, settings)
            if slot_cursor + timedelta(minutes=duration) > block.end:
                break
            result.scheduled.append(
                ScheduledTask(
                    task=candidate, start_at=slot_cursor,
                    duration_minutes=duration, deepwork=True,
                )
            )
            assigned_ids.add(candidate.id)
            slot_cursor += timedelta(minutes=duration)
        obstacles.append(
            _Obstacle(start=block.start, end=block.end,
                      title=block.title or "Deep work", buffer=timedelta(0),
                      kind="pinned")
        )

    remaining = [t for t in auto if t.id not in assigned_ids]

    # 2. Placement automatique par CURSEUR : à chaque créneau, on choisit la meilleure
    #    tâche POUR CETTE HEURE (le creux de l'après-midi renchérit l'effort des tâches
    #    complexes → les légères y remontent ; hors du creux, l'ordre reste celui de
    #    l'urgence pure). Le palier dur et le chemin critique ne sont jamais réordonnés
    #    (voir _selection_key). Durées réelles, contournement des obstacles.
    obstacles.sort(key=lambda o: o.start)
    cursor = effective_start
    while remaining:
        # Heure de placement RÉELLE du prochain créneau (curseur sauté hors des obstacles) :
        # c'est à cette heure qu'on évalue l'énergie cognitive, pas au curseur brut.
        slot_start = _advance_past_obstacles(cursor, obstacles)
        task = min(
            remaining,
            key=lambda t: _selection_key(t, day, slot_start, settings, urgency_keys),
        )
        dip_active = _dip_intensity(slot_start, settings) > 0
        # Repère du choix « urgence pure » pour n'annoter (dip_note) que si le creux a
        # réellement changé la tâche retenue sur ce créneau.
        urgency_pick = (
            min(remaining, key=lambda t: urgency_keys.get(t.id, _sort_key(t, day, settings)))
            if dip_active else task
        )
        remaining.remove(task)

        duration = timedelta(minutes=_duration_minutes(task, settings))
        start = cursor
        pushed = False
        pushed_note = ""
        while True:
            blocking = next(
                (o for o in obstacles if o.start < start + duration and o.end > start),
                None,
            )
            if blocking is None:
                break
            pushed = True
            start = blocking.end + blocking.buffer
            if blocking.kind == "busy":
                pushed_note = f"à partir de {start:%Hh%M}, après « {blocking.title} »"
            else:
                pushed_note = f"à partir de {start:%Hh%M}, après « {blocking.title} » (épinglée)"

        if start >= workday_end:
            result.unscheduled.append(task)
            continue  # une tâche plus courte peut encore tenir : on n'abandonne pas la suite

        dip_note = ""
        if dip_active and task is not urgency_pick:
            dip_note = f"créneau creux (~{settings.cognitive_dip_trough_hour}h) — tâche légère privilégiée"
        result.scheduled.append(
            ScheduledTask(
                task=task, start_at=start,
                duration_minutes=_duration_minutes(task, settings),
                pushed=pushed, pushed_note=pushed_note, dip_note=dip_note,
            )
        )
        cursor = start + duration

    result.scheduled.sort(key=lambda s: s.start_at)

    # 3. Charge du jour : requis (les unités de travail réelles — les mères sont
    # exclues, leur charge est celle de leurs filles) vs disponible (fenêtre restante
    # moins les créneaux occupés — les épinglées sont du travail, pas de l'occupé).
    required = sum(_duration_minutes(t, settings) for t in schedulable)
    window_minutes = max(
        0, int((workday_end - effective_start).total_seconds() // 60)
    )
    busy_minutes = _busy_minutes_in_window(day_blocks, effective_start, workday_end)
    result.stats = DayStats(
        required_minutes=required,
        available_minutes=max(0, window_minutes - busy_minutes),
    )
    return result
