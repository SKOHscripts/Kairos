"""Tests de la récurrence (modèle « recréation à la complétion »)."""

from __future__ import annotations

from datetime import date, datetime, time, timedelta

import pytest

from app.tasks_models import Task, TimeBlock
from app.tasks_recurrence import (
    ensure_calendar_occurrences,
    expand_recurring_blocks,
    next_deadline,
    next_snooze_date,
    spawn_next_occurrence,
)

TODAY = date.today()


# --------------------------------------------------------------------------- #
# Règles de calcul de la prochaine échéance
# --------------------------------------------------------------------------- #

def test_next_deadline_daily() -> None:
    assert next_deadline("daily", date(2026, 7, 2)) == date(2026, 7, 3)


def test_next_deadline_weekdays_skips_weekend() -> None:
    # Vendredi 3 juillet 2026 → lundi 6 juillet.
    assert next_deadline("weekdays", date(2026, 7, 3)) == date(2026, 7, 6)
    # Mercredi → jeudi (pas de saut).
    assert next_deadline("weekdays", date(2026, 7, 1)) == date(2026, 7, 2)


def test_next_deadline_weekly() -> None:
    assert next_deadline("weekly", date(2026, 7, 2)) == date(2026, 7, 9)


def test_next_deadline_monthly_clamps_to_month_end() -> None:
    assert next_deadline("monthly", date(2026, 7, 15)) == date(2026, 8, 15)
    # 31 janvier → 28 février (2027 non bissextile).
    assert next_deadline("monthly", date(2027, 1, 31)) == date(2027, 2, 28)
    # Décembre → janvier de l'année suivante.
    assert next_deadline("monthly", date(2026, 12, 10)) == date(2027, 1, 10)


def test_next_deadline_unknown_rule_raises() -> None:
    with pytest.raises(ValueError):
        next_deadline("fortnightly", date(2026, 7, 2))


# --------------------------------------------------------------------------- #
# Snooze : décalage avant, jour ouvré + fériés
# --------------------------------------------------------------------------- #

def test_next_snooze_date_friday_shifts_to_monday() -> None:
    friday = date(2026, 7, 3)  # vendredi, aucun férié le lundi suivant
    assert next_snooze_date(None, friday) == date(2026, 7, 6)


def test_next_snooze_date_friday_before_holiday_monday_shifts_to_tuesday() -> None:
    """Vendredi 3 avril 2026 : le lundi suivant (6 avril) est férié (Lundi de
    Pâques) → le décalage saute au mardi 7."""
    friday = date(2026, 4, 3)
    holidays = frozenset({date(2026, 4, 6)})
    assert next_snooze_date(None, friday, holidays) == date(2026, 4, 7)


def test_next_snooze_date_overdue_restarts_from_today_not_past_deadline() -> None:
    today = date(2026, 7, 3)
    overdue = today - timedelta(days=5)
    assert next_snooze_date(overdue, today) == date(2026, 7, 6)


def test_next_snooze_date_future_deadline_advances_from_it() -> None:
    today = date(2026, 7, 3)
    future = date(2026, 7, 10)  # vendredi suivant
    assert next_snooze_date(future, today) == date(2026, 7, 13)  # lundi d'après


# --------------------------------------------------------------------------- #
# Création de l'occurrence suivante
# --------------------------------------------------------------------------- #

def test_spawn_copies_fields_and_advances_deadline(tasks_session) -> None:
    task = Task(
        title="Point hebdo", description="Ordre du jour", priority=1,
        deadline=TODAY, project_tag="MSI", estimated_minutes=30,
        recurrence="weekly", status="done",
    )
    tasks_session.add(task)
    tasks_session.commit()

    occurrence = spawn_next_occurrence(tasks_session, task)
    tasks_session.commit()

    assert occurrence is not None
    assert occurrence.title == "Point hebdo"
    assert occurrence.priority == 1
    assert occurrence.estimated_minutes == 30
    assert occurrence.recurrence == "weekly"
    assert occurrence.deadline == TODAY + timedelta(days=7)
    assert occurrence.status == "todo"
    assert occurrence.source == "native"
    assert occurrence.external_id is None


def test_spawn_does_nothing_for_non_recurring(tasks_session) -> None:
    task = Task(title="Ponctuelle", status="done")
    tasks_session.add(task)
    tasks_session.commit()

    assert spawn_next_occurrence(tasks_session, task) is None
    assert tasks_session.query(Task).count() == 1


def test_spawn_overdue_recurring_restarts_from_today(tasks_session) -> None:
    """Une quotidienne terminée avec 5 jours de retard repart de demain, pas d'il y a 4 jours."""
    task = Task(title="Relever le courrier", recurrence="daily",
                deadline=TODAY - timedelta(days=5), status="done")
    tasks_session.add(task)
    tasks_session.commit()

    occurrence = spawn_next_occurrence(tasks_session, task)

    assert occurrence.deadline == TODAY + timedelta(days=1)


def test_spawn_guards_against_duplicates(tasks_session) -> None:
    """Double aller-retour fait/rouvert/fait : une seule occurrence suivante créée."""
    task = Task(title="Quotidienne", recurrence="daily", deadline=TODAY, status="done")
    tasks_session.add(task)
    tasks_session.commit()

    first = spawn_next_occurrence(tasks_session, task)
    tasks_session.commit()
    second = spawn_next_occurrence(tasks_session, task)

    assert first is not None
    assert second is None
    todo_count = tasks_session.query(Task).filter_by(status="todo").count()
    assert todo_count == 1


def test_spawn_recurring_task_from_import_creates_native_occurrence(tasks_session) -> None:
    """L'occurrence d'une récurrente importée est native (pas de collision unique)."""
    task = Task(title="Importée récurrente", recurrence="daily", deadline=TODAY,
                status="done", source="gitlab", external_id="gl-7")
    tasks_session.add(task)
    tasks_session.commit()

    occurrence = spawn_next_occurrence(tasks_session, task)
    tasks_session.commit()

    assert occurrence.source == "native"
    assert occurrence.external_id is None


# --------------------------------------------------------------------------- #
# Récurrence calendaire (« le … du mois »)
# --------------------------------------------------------------------------- #

def test_calendar_recurrence_generates_this_month_once(tasks_session) -> None:
    """Première mise en place d'une série : une occurrence créée, jamais deux."""
    seed = Task(title="Rapport mensuel", recurrence="monthly_on_day",
                recurrence_day_of_month=15, priority=2, project_tag="MSI",
                estimated_minutes=45, status="done", deadline=date(2026, 5, 20))
    tasks_session.add(seed)
    tasks_session.commit()

    created = ensure_calendar_occurrences(tasks_session, date(2026, 7, 3))
    assert len(created) == 1
    occurrence = created[0]
    assert occurrence.title == "Rapport mensuel"
    assert occurrence.priority == 2
    assert occurrence.project_tag == "MSI"
    assert occurrence.estimated_minutes == 45
    assert occurrence.deadline == date(2026, 7, 15)
    assert occurrence.recurrence == "monthly_on_day"
    assert occurrence.recurrence_period == "2026-07"
    assert occurrence.source == "native"

    # Idempotent : un deuxième appel le même mois ne crée rien de plus.
    again = ensure_calendar_occurrences(tasks_session, date(2026, 7, 20))
    assert again == []
    assert tasks_session.query(Task).count() == 2


def test_calendar_recurrence_skips_if_seed_deadline_already_this_month(tasks_session) -> None:
    """La tâche de départ, éditée à la main avec une échéance ce mois-ci, compte déjà
    comme l'occurrence du mois — pas de doublon dès la mise en place de la série."""
    seed = Task(title="Cotisation", recurrence="monthly_on_day",
                recurrence_day_of_month=23, deadline=date(2026, 7, 23), status="todo")
    tasks_session.add(seed)
    tasks_session.commit()

    created = ensure_calendar_occurrences(tasks_session, date(2026, 7, 1))
    assert created == []
    assert tasks_session.query(Task).count() == 1


def test_calendar_recurrence_shifts_sunday_to_friday(tasks_session) -> None:
    """Le 23 août 2026 tombe un dimanche : l'occurrence recule au vendredi 21."""
    seed = Task(title="Note de frais", recurrence="monthly_on_day",
                recurrence_day_of_month=23, status="done")
    tasks_session.add(seed)
    tasks_session.commit()

    created = ensure_calendar_occurrences(tasks_session, date(2026, 8, 5))
    assert len(created) == 1
    assert created[0].deadline == date(2026, 8, 21)


def test_calendar_recurrence_chains_through_holiday_and_weekend(tasks_session) -> None:
    """Le 14 février 2026 tombe un samedi ; le 13 (vendredi) est férié (injecté) :
    la chaîne recule jusqu'au jeudi 12, jour ouvré le plus proche."""
    seed = Task(title="Suivi budget", recurrence="monthly_on_day",
                recurrence_day_of_month=14, status="done")
    tasks_session.add(seed)
    tasks_session.commit()

    holidays = frozenset({date(2026, 2, 13)})
    created = ensure_calendar_occurrences(tasks_session, date(2026, 2, 2), holidays)
    assert len(created) == 1
    assert created[0].deadline == date(2026, 2, 12)


def test_calendar_recurrence_never_overwrites_prior_month_still_open(tasks_session) -> None:
    """Une occurrence du mois précédent encore ouverte reste intacte ; celle du mois
    courant est créée à côté, jamais à la place."""
    previous = Task(title="Facture fournisseur", recurrence="monthly_on_day",
                     recurrence_day_of_month=10, deadline=date(2026, 6, 10),
                     recurrence_period="2026-06", status="todo")
    tasks_session.add(previous)
    tasks_session.commit()
    previous_id = previous.id

    created = ensure_calendar_occurrences(tasks_session, date(2026, 7, 12))
    assert len(created) == 1
    assert created[0].deadline == date(2026, 7, 10)

    still_there = tasks_session.get(Task, previous_id)
    assert still_there.status == "todo"
    assert still_there.deadline == date(2026, 6, 10)  # jamais modifiée


def test_calendar_recurrence_uses_most_recent_representative(tasks_session) -> None:
    """Le titre/priorité/durée copiés viennent de l'occurrence la plus récente de la
    série, pas de la toute première (dont les détails ont pu changer depuis)."""
    old = Task(title="Revue mensuelle", recurrence="monthly_on_day",
               recurrence_day_of_month=5, priority=4, deadline=date(2026, 5, 5),
               recurrence_period="2026-05", status="done")
    tasks_session.add(old)
    tasks_session.commit()
    newer = Task(title="Revue mensuelle", recurrence="monthly_on_day",
                 recurrence_day_of_month=5, priority=0, estimated_minutes=90,
                 deadline=date(2026, 6, 5), recurrence_period="2026-06", status="done")
    tasks_session.add(newer)
    tasks_session.commit()

    created = ensure_calendar_occurrences(tasks_session, date(2026, 7, 1))
    assert len(created) == 1
    assert created[0].priority == 0
    assert created[0].estimated_minutes == 90


def test_calendar_recurrence_bounds_day_to_end_of_month(tasks_session) -> None:
    """Jour 31 sur un mois plus court (février) : borné au dernier jour du mois
    (le 28 février 2026 tombe un samedi, d'où le recul au vendredi 27)."""
    seed = Task(title="Clôture", recurrence="monthly_on_day",
                recurrence_day_of_month=31, status="done")
    tasks_session.add(seed)
    tasks_session.commit()

    created = ensure_calendar_occurrences(tasks_session, date(2026, 2, 1))
    assert len(created) == 1
    assert created[0].deadline == date(2026, 2, 27)


# --------------------------------------------------------------------------- #
# Blocs récurrents (phase 13) : « bloc déjeuner quotidien », « deep-work chaque mardi »
# --------------------------------------------------------------------------- #

def test_daily_block_expands_one_occurrence_per_day() -> None:
    """Bloc déjeuner quotidien : une occurrence chaque jour de la plage, même heure."""
    template = TimeBlock(
        title="Déjeuner", start=datetime(2026, 7, 6, 12, 0),  # lundi
        end=datetime(2026, 7, 6, 13, 0), recurrence="daily",
    )

    occurrences = expand_recurring_blocks([template], date(2026, 7, 6), date(2026, 7, 10))

    assert len(occurrences) == 5
    assert [o.start.date() for o in occurrences] == [
        date(2026, 7, 6), date(2026, 7, 7), date(2026, 7, 8),
        date(2026, 7, 9), date(2026, 7, 10),
    ]
    assert all(o.start.time() == time(12, 0) and o.end.time() == time(13, 0)
              for o in occurrences)
    assert all(o.title == "Déjeuner" for o in occurrences)


def test_weekly_block_only_matches_same_weekday() -> None:
    """Deep-work chaque mardi : seuls les mardis de la plage reçoivent une occurrence."""
    template = TimeBlock(
        title="Focus", start=datetime(2026, 7, 7, 9, 0),  # mardi
        end=datetime(2026, 7, 7, 11, 0), recurrence="weekly", kind="deepwork",
    )

    occurrences = expand_recurring_blocks([template], date(2026, 7, 6), date(2026, 7, 21))

    assert [o.start.date() for o in occurrences] == [date(2026, 7, 7), date(2026, 7, 14), date(2026, 7, 21)]
    assert all(o.kind == "deepwork" for o in occurrences)


def test_weekdays_block_skips_weekend() -> None:
    template = TimeBlock(
        title="Point d'équipe", start=datetime(2026, 7, 6, 9, 0),
        end=datetime(2026, 7, 6, 9, 15), recurrence="weekdays",
    )

    occurrences = expand_recurring_blocks([template], date(2026, 7, 10), date(2026, 7, 13))

    # Vendredi 10, samedi 11 et dimanche 12 exclus/sautés, lundi 13 inclus.
    assert [o.start.date() for o in occurrences] == [date(2026, 7, 10), date(2026, 7, 13)]


def test_block_recurrence_never_goes_before_origin_date() -> None:
    """Un bloc créé le mercredi ne recule jamais avant cette date, même si la plage
    demandée commence plus tôt (pas de rattrapage rétroactif)."""
    template = TimeBlock(
        title="Focus", start=datetime(2026, 7, 8, 9, 0),  # mercredi
        end=datetime(2026, 7, 8, 10, 0), recurrence="daily",
    )

    occurrences = expand_recurring_blocks([template], date(2026, 7, 1), date(2026, 7, 9))

    assert min(o.start.date() for o in occurrences) == date(2026, 7, 8)


def test_non_recurring_block_produces_no_occurrence() -> None:
    template = TimeBlock(title="Ponctuel", start=datetime(2026, 7, 6, 9, 0),
                         end=datetime(2026, 7, 6, 10, 0), recurrence="")
    assert expand_recurring_blocks([template], date(2026, 7, 6), date(2026, 7, 10)) == []


def test_expand_recurring_blocks_returns_transient_objects() -> None:
    """Les occurrences ne sont jamais persistées (pas d'id) : c'est à l'appelant de
    les fusionner avec les blocs ponctuels, jamais de les ajouter en base."""
    template = TimeBlock(title="Déjeuner", start=datetime(2026, 7, 6, 12, 0),
                         end=datetime(2026, 7, 6, 13, 0), recurrence="daily")
    occurrences = expand_recurring_blocks([template], date(2026, 7, 6), date(2026, 7, 6))
    assert occurrences[0].id is None
