"""Récurrence des tâches — modèle « recréation à la complétion ».

Terminer une tâche récurrente marque l'occurrence courante faite ET crée la
suivante, avec la deadline avancée selon la règle. C'est le modèle le plus robuste
pour un outil mono-utilisateur : pas de moteur de planification en tâche de fond,
pas d'occurrences fantômes à générer en avance — la prochaine occurrence naît de la
complétion de la précédente.
"""

from __future__ import annotations

import calendar
from datetime import date, timedelta

from sqlalchemy import select
from sqlalchemy.orm import Session

from datetime import datetime

from .tasks_models import Task, TimeBlock
from .workdays import add_business_days, on_or_before_business_day

RECURRENCE_RULES = ("daily", "weekdays", "weekly", "monthly")
CALENDAR_RECURRENCE = "monthly_on_day"
# Sous-ensemble utilisable pour un TimeBlock (phase 13) : pas de 'monthly', moins
# pertinent pour un créneau récurrent (déjeuner, deep-work hebdomadaire) et non demandé.
BLOCK_RECURRENCE_RULES = ("daily", "weekdays", "weekly")


def next_deadline(rule: str, base: date) -> date:
    """Prochaine échéance après ``base`` selon la règle de récurrence."""
    if rule == "daily":
        return base + timedelta(days=1)
    if rule == "weekdays":
        # Jour ouvré suivant (week-ends sautés ; les fériés relèvent des projections
        # GitLab, pas de la todo personnelle — volontairement simple ici).
        step = base + timedelta(days=1)
        while step.weekday() >= 5:
            step += timedelta(days=1)
        return step
    if rule == "weekly":
        return base + timedelta(days=7)
    if rule == "monthly":
        # +1 mois, jour borné à la fin du mois cible (31 janv. → 28/29 févr.).
        year = base.year + (base.month // 12)
        month = base.month % 12 + 1
        day = min(base.day, calendar.monthrange(year, month)[1])
        return date(year, month, day)
    raise ValueError(f"Règle de récurrence inconnue : {rule!r}")


def spawn_next_occurrence(session: Session, task: Task) -> Task | None:
    """Crée l'occurrence suivante d'une tâche récurrente qui vient d'être terminée.

    - Tâche non récurrente : ne fait rien (``None``).
    - L'échéance repart de ``max(deadline, aujourd'hui)`` : une récurrente terminée
      en retard ne génère pas une occurrence déjà en retard.
    - La nouvelle occurrence est **native** (jamais de ``external_id`` copié : la
      contrainte unique ``(source, external_id)`` interdirait le doublon, et une
      occurrence créée ici n'existe dans aucune source externe).
    - Garde anti-doublon : si une occurrence identique (titre + règle + échéance) est
      déjà à faire — cas du double aller-retour fait/rouvert/fait — rien n'est créé.
    """
    if task.recurrence not in RECURRENCE_RULES:
        return None

    base = max(task.deadline or date.today(), date.today())
    deadline = next_deadline(task.recurrence, base)

    duplicate = session.scalars(
        select(Task).where(
            Task.title == task.title,
            Task.recurrence == task.recurrence,
            Task.deadline == deadline,
            Task.status == "todo",
        )
    ).first()
    if duplicate is not None:
        return None

    occurrence = Task(
        title=task.title,
        description=task.description,
        priority=task.priority,
        deadline=deadline,
        project_tag=task.project_tag,
        estimated_minutes=task.estimated_minutes,
        recurrence=task.recurrence,
        parent_id=task.parent_id,
        source="native",
    )
    session.add(occurrence)
    return occurrence


def next_snooze_date(
    deadline: date | None, today: date, holidays: frozenset[date] | None = None
) -> date:
    """Prochain jour ouvré après ``deadline`` (ou ``today`` si absente/dépassée).

    Le snooze avance toujours (contrairement au décalage arrière de la récurrence
    calendaire) : « demain » un vendredi tombe sur le lundi suivant (ou le jour
    ouvré d'après si ce lundi est férié), jamais sur un week-end ou un jour férié.
    """
    base = deadline if (deadline and deadline > today) else today
    return add_business_days(base, 1, holidays)


def _period_str(day: date) -> str:
    return f"{day.year:04d}-{day.month:02d}"


def ensure_calendar_occurrences(
    session: Session, today: date, holidays: frozenset[date] | None = None
) -> list[Task]:
    """Génère l'occurrence du mois courant des séries calées sur un jour du mois.

    Une série (ex. « le 23 du mois ») est identifiée par ``(title,
    recurrence_day_of_month)`` — même granularité que la garde anti-doublon de
    :func:`spawn_next_occurrence`. Contrairement aux règles de ``RECURRENCE_RULES``
    (recréées à la complétion), une occurrence calendaire est générée **par date**,
    indépendamment du sort de l'occurrence précédente : une échéance manquée en
    retard reste visible, en retard, pendant que celle du mois courant apparaît déjà.

    N'agit jamais rétroactivement (une seule occurrence générée : celle de
    ``today``, pas de rattrapage des mois où l'app n'aurait pas tourné) et ne
    modifie jamais une occurrence existante — la génération est purement additive.
    """
    period = _period_str(today)
    candidates = list(
        session.scalars(
            select(Task).where(
                Task.recurrence == CALENDAR_RECURRENCE,
                Task.recurrence_day_of_month.is_not(None),
            )
        )
    )
    series: dict[tuple[str, int], list[Task]] = {}
    for task in candidates:
        series.setdefault((task.title, task.recurrence_day_of_month), []).append(task)

    created: list[Task] = []
    for (title, day_of_month), members in series.items():
        # Déjà couverte ce mois-ci : soit une occurrence explicitement taguée
        # (recurrence_period), soit la tâche elle-même (créée/éditée à la main,
        # jamais taguée) a déjà une échéance tombant ce mois — évite un doublon dès
        # la première mise en place d'une série sur une tâche existante.
        already_covered = any(
            member.recurrence_period == period
            or (member.deadline is not None and _period_str(member.deadline) == period)
            for member in members
        )
        if already_covered:
            continue

        representative = max(members, key=lambda m: m.id or 0)
        bounded_day = min(day_of_month, calendar.monthrange(today.year, today.month)[1])
        target = date(today.year, today.month, bounded_day)
        deadline = on_or_before_business_day(target, holidays)

        occurrence = Task(
            title=title,
            description=representative.description,
            priority=representative.priority,
            deadline=deadline,
            project_tag=representative.project_tag,
            estimated_minutes=representative.estimated_minutes,
            recurrence=CALENDAR_RECURRENCE,
            recurrence_day_of_month=day_of_month,
            recurrence_period=period,
            source="native",
        )
        session.add(occurrence)
        created.append(occurrence)

    if created:
        session.commit()
    return created


# --------------------------------------------------------------------------- #
# Blocs récurrents (phase 13) : « le … du jour/de la semaine », jamais persistés
# --------------------------------------------------------------------------- #

def _block_recurs_on(rule: str, day, origin_date) -> bool:
    if rule == "daily":
        return True
    if rule == "weekdays":
        return day.weekday() < 5  # même simplicité que Task ('weekdays' ci-dessus)
    if rule == "weekly":
        return day.weekday() == origin_date.weekday()
    raise ValueError(f"Règle de récurrence de bloc inconnue : {rule!r}")


def expand_recurring_blocks(
    templates: list[TimeBlock], range_start: date, range_end: date
) -> list[TimeBlock]:
    """Projette les blocs récurrents sur ``[range_start, range_end]`` (bornes incluses).

    Chaque ``TimeBlock`` de ``templates`` (``recurrence`` non vide) est le **modèle** :
    sa date propre fixe l'origine et l'heure de début/fin canonique ; une occurrence est
    générée pour chaque jour concerné de la plage, à la même heure, avec la même durée.
    Un bloc ne recule **jamais** avant sa date d'origine (pas de rattrapage rétroactif,
    même logique que la récurrence des tâches). Les occurrences retournées sont des
    ``TimeBlock`` **transitoires** (jamais ajoutées à une session, jamais persistées) —
    à fusionner par l'appelant avec les blocs ponctuels réels.
    """
    occurrences: list[TimeBlock] = []
    for tpl in templates:
        if tpl.recurrence not in BLOCK_RECURRENCE_RULES:
            continue
        origin_date = tpl.start.date()
        duration = tpl.end - tpl.start
        day = max(range_start, origin_date)
        while day <= range_end:
            if _block_recurs_on(tpl.recurrence, day, origin_date):
                occ_start = datetime.combine(day, tpl.start.time())
                occurrences.append(TimeBlock(
                    title=tpl.title, start=occ_start, end=occ_start + duration,
                    source=tpl.source, kind=tpl.kind, recurrence=tpl.recurrence,
                ))
            day += timedelta(days=1)
    return occurrences
