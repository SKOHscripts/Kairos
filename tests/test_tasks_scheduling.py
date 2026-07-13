"""Tests de l'algorithme d'ordonnancement de « Kairos »."""

from __future__ import annotations

import random
from datetime import date, datetime, time, timedelta

from app.config import Settings
from app.tasks_models import Task, TimeBlock
from app.tasks_scheduling import (
    build_day_schedule,
    build_timeline,
    count_max_priority_tasks,
    urgency_key,
    wsjf_score,
)

DAY = date(2026, 7, 2)  # jeudi


def _settings(**overrides) -> Settings:
    return Settings(**overrides)


def test_nominal_case_no_busy_blocks() -> None:
    urgent = Task(id=1, title="Urgent", priority=0, fibonacci_points=1, status="todo")
    normal = Task(id=2, title="Normal", priority=4, fibonacci_points=1, status="todo")

    schedule = build_day_schedule([normal, urgent], [], DAY, settings=_settings())

    assert [s.task.id for s in schedule.scheduled] == [1, 2]
    assert schedule.scheduled[0].start_at == datetime.combine(DAY, time(9, 0))
    assert schedule.scheduled[1].start_at == datetime.combine(DAY, time(9, 30))
    assert not schedule.scheduled[0].pushed
    assert schedule.unscheduled == []


def test_meeting_pushes_urgent_task_to_after_it_with_buffer() -> None:
    """Cas emblématique de la spec : réunion 13h-14h → tâche urgente à 14h05."""
    meeting = TimeBlock(
        title="Réunion budget",
        start=datetime.combine(DAY, time(13, 0)),
        end=datetime.combine(DAY, time(14, 0)),
        source="manual",
    )
    urgent_task = Task(id=1, title="Sujet urgent", priority=0, fibonacci_points=1,
                      deadline=DAY, status="todo")

    schedule = build_day_schedule(
        [urgent_task],
        [meeting],
        DAY,
        now=datetime.combine(DAY, time(13, 0)),
        settings=_settings(),
    )

    assert len(schedule.scheduled) == 1
    scheduled = schedule.scheduled[0]
    assert scheduled.start_at == datetime.combine(DAY, time(14, 5))
    assert scheduled.pushed is True
    assert "14h05" in scheduled.pushed_note
    assert "Réunion budget" in scheduled.pushed_note


def test_busy_block_outside_workday_has_no_impact() -> None:
    evening_block = TimeBlock(
        title="Dîner",
        start=datetime.combine(DAY, time(20, 0)),
        end=datetime.combine(DAY, time(21, 0)),
        source="manual",
    )
    task = Task(id=1, title="Tâche", priority=2, fibonacci_points=1, status="todo")

    schedule = build_day_schedule([task], [evening_block], DAY, settings=_settings())

    assert schedule.scheduled[0].start_at == datetime.combine(DAY, time(9, 0))
    assert schedule.scheduled[0].pushed is False


def test_empty_inputs_do_not_crash() -> None:
    schedule = build_day_schedule([], [], DAY, settings=_settings())
    assert schedule.scheduled == []
    assert schedule.unscheduled == []


def test_overflowing_tasks_are_unscheduled_not_pushed_past_workday_end() -> None:
    settings = _settings(workday_start_hour=9, workday_end_hour=10)  # 1h de travail
    tasks = [
        Task(id=1, title="Tâche 1", priority=0, fibonacci_points=1, status="todo"),
        Task(id=2, title="Tâche 2", priority=0, fibonacci_points=1, status="todo"),
        Task(id=3, title="Tâche 3", priority=0, fibonacci_points=1, status="todo"),
    ]

    schedule = build_day_schedule(tasks, [], DAY, settings=settings)

    assert [s.task.id for s in schedule.scheduled] == [1, 2]
    assert [t.id for t in schedule.unscheduled] == [3]


def test_done_tasks_are_ignored() -> None:
    done_task = Task(id=1, title="Faite", status="done")
    todo_task = Task(id=2, title="À faire", priority=2, fibonacci_points=1, status="todo")

    schedule = build_day_schedule([done_task, todo_task], [], DAY, settings=_settings())

    assert [s.task.id for s in schedule.scheduled] == [2]


# --------------------------------------------------------------------------- #
# Phase 2 : durées réelles, épinglage, débordement
# --------------------------------------------------------------------------- #

def test_real_durations_drive_sequencing() -> None:
    long_task = Task(id=1, title="Longue", priority=0, fibonacci_points=1,
                     estimated_minutes=90, status="todo")
    short_task = Task(id=2, title="Courte", priority=2, fibonacci_points=1,
                      estimated_minutes=15, status="todo")

    schedule = build_day_schedule([short_task, long_task], [], DAY, settings=_settings())

    assert schedule.scheduled[0].task.id == 1
    assert schedule.scheduled[0].duration_minutes == 90
    assert schedule.scheduled[1].start_at == datetime.combine(DAY, time(10, 30))  # 9h + 90 min


def test_pinned_task_is_placed_at_its_exact_time() -> None:
    pinned = Task(id=1, title="Point client", status="todo", priority=2, fibonacci_points=1,
                  pinned_start=datetime.combine(DAY, time(11, 0)), estimated_minutes=60)
    auto = Task(id=2, title="Autre", priority=0, fibonacci_points=1, status="todo",
               estimated_minutes=30)

    schedule = build_day_schedule([pinned, auto], [], DAY, settings=_settings())

    by_id = {s.task.id: s for s in schedule.scheduled}
    assert by_id[1].start_at == datetime.combine(DAY, time(11, 0))
    assert by_id[1].pinned is True
    assert by_id[1].conflict is False
    # L'auto démarre à 9h, avant l'épinglée : rien ne l'oblige à attendre.
    assert by_id[2].start_at == datetime.combine(DAY, time(9, 0))


def test_auto_tasks_flow_around_pinned_task() -> None:
    """Une auto qui percuterait l'épinglée est repoussée après elle, sans marge réunion."""
    pinned = Task(id=1, title="Point client", status="todo", priority=2, fibonacci_points=1,
                  pinned_start=datetime.combine(DAY, time(9, 30)), estimated_minutes=60)
    auto = Task(id=2, title="Grosse tâche", priority=0, fibonacci_points=1, status="todo",
               estimated_minutes=60)

    schedule = build_day_schedule([pinned, auto], [], DAY, settings=_settings())

    by_id = {s.task.id: s for s in schedule.scheduled}
    # 9h + 60 min percuterait l'épinglée de 9h30 → repoussée à la fin de l'épinglée (10h30),
    # sans la marge de sortie de réunion (réservée aux créneaux occupés).
    assert by_id[2].start_at == datetime.combine(DAY, time(10, 30))
    assert by_id[2].pushed is True
    assert "épinglée" in by_id[2].pushed_note


def test_pinned_task_overlapping_meeting_is_flagged_not_moved() -> None:
    meeting = TimeBlock(title="Réunion budget",
                        start=datetime.combine(DAY, time(13, 0)),
                        end=datetime.combine(DAY, time(14, 0)), source="manual")
    pinned = Task(id=1, title="Épinglée en plein milieu", status="todo",
                  priority=2, fibonacci_points=1,
                  pinned_start=datetime.combine(DAY, time(13, 30)), estimated_minutes=30)

    schedule = build_day_schedule([pinned], [meeting], DAY, settings=_settings())

    item = schedule.scheduled[0]
    assert item.start_at == datetime.combine(DAY, time(13, 30))  # jamais déplacée
    assert item.conflict is True
    assert "Réunion budget" in item.conflict_note


def test_day_stats_required_available_and_overflow() -> None:
    settings = _settings(workday_start_hour=9, workday_end_hour=12)  # 180 min
    meeting = TimeBlock(title="Réunion",
                        start=datetime.combine(DAY, time(10, 0)),
                        end=datetime.combine(DAY, time(11, 0)), source="manual")
    tasks = [
        Task(id=1, title="A", status="todo", priority=2, fibonacci_points=1, estimated_minutes=90),
        Task(id=2, title="B", status="todo", priority=2, fibonacci_points=1, estimated_minutes=60),
    ]

    schedule = build_day_schedule(tasks, [meeting], DAY, settings=settings)

    assert schedule.stats.required_minutes == 150
    assert schedule.stats.available_minutes == 120  # 180 - 60 de réunion
    assert schedule.stats.overflow_minutes == 30


def test_overlapping_busy_blocks_not_double_counted_in_stats() -> None:
    settings = _settings(workday_start_hour=9, workday_end_hour=12)
    blocks = [
        TimeBlock(title="A", start=datetime.combine(DAY, time(10, 0)),
                  end=datetime.combine(DAY, time(11, 0)), source="manual"),
        TimeBlock(title="B", start=datetime.combine(DAY, time(10, 30)),
                  end=datetime.combine(DAY, time(11, 30)), source="timetree"),
    ]

    schedule = build_day_schedule([], blocks, DAY, settings=settings)

    # 10h-11h30 fusionné = 90 min occupées, pas 120.
    assert schedule.stats.available_minutes == 90


def test_blocked_task_is_excluded_from_schedule_and_load() -> None:
    a = Task(id=1, title="Bloquée", priority=0, fibonacci_points=1, status="todo",
            estimated_minutes=60)
    b = Task(id=2, title="Autre", priority=2, fibonacci_points=1, status="todo",
            estimated_minutes=30)

    schedule = build_day_schedule(
        [a, b], [], DAY, settings=_settings(), blocked_ids={1},
    )

    assert [s.task.id for s in schedule.scheduled] == [2]
    assert schedule.unscheduled == []  # la bloquée n'est pas « sans créneau », elle est hors jeu
    assert schedule.stats.required_minutes == 30  # la bloquée ne compte pas dans la charge


def test_derived_urgency_reorders_auto_tasks() -> None:
    # C(3) peu urgente mais bloque A(1) très urgente : son urgence dérivée la fait passer
    # devant B(2) moyennement urgente.
    a = Task(id=1, title="A urgente", priority=0, fibonacci_points=1, status="todo",
            estimated_minutes=30)
    b = Task(id=2, title="B moyenne", priority=2, fibonacci_points=1, status="todo",
            estimated_minutes=30)
    c = Task(id=3, title="C bloqueuse", priority=4, fibonacci_points=1, status="todo",
            estimated_minutes=30)

    # A est bloquée par C ; urgence effective de C = celle de A.
    from app.tasks_dependencies import derived_urgency
    from app.tasks_scheduling import urgency_key
    own = {t.id: urgency_key(t, DAY, settings=_settings()) for t in (a, b, c)}
    eff = derived_urgency([(1, 3)], own)

    schedule = build_day_schedule(
        [a, b, c], [], DAY, settings=_settings(),
        blocked_ids={1},  # A bloquée par C
        urgency_keys=eff,
    )

    # A exclue ; parmi B et C, C (chemin critique) passe avant B.
    assert [s.task.id for s in schedule.scheduled] == [3, 2]


def test_parent_with_open_children_is_not_scheduled_itself() -> None:
    parent = Task(id=1, title="Mère", priority=0, status="todo")
    child = Task(id=2, title="Fille", priority=0, fibonacci_points=1, status="todo",
                parent_id=1, estimated_minutes=30)

    schedule = build_day_schedule([parent, child], [], DAY, settings=_settings())

    assert [s.task.id for s in schedule.scheduled] == [2]
    assert schedule.unscheduled == []  # la mère n'atterrit pas non plus en « sans créneau »
    # La charge du jour est celle des unités de travail réelles (la fille seule).
    assert schedule.stats.required_minutes == 30


def test_parent_becomes_schedulable_once_children_are_done() -> None:
    parent = Task(id=1, title="Mère", priority=0, fibonacci_points=1, status="todo",
                 estimated_minutes=20)
    done_child = Task(id=2, title="Fille faite", status="done", parent_id=1)

    schedule = build_day_schedule([parent, done_child], [], DAY, settings=_settings())

    assert [s.task.id for s in schedule.scheduled] == [1]


def test_deepwork_block_fills_with_multiple_tasks_keeping_own_duration() -> None:
    """Régression #12 : une tâche de 30 min dans un bloc deep-work de 2h garde ses
    30 min (pas la durée du bloc), et une autre tâche vient occuper le reste de la
    fenêtre avec le même label deep-work."""
    dw = TimeBlock(title="Focus", kind="deepwork",
                   start=datetime.combine(DAY, time(9, 0)),
                   end=datetime.combine(DAY, time(11, 0)), source="manual")
    a = Task(id=1, title="Rédaction", priority=0, fibonacci_points=1, status="todo",
            estimated_minutes=30)
    b = Task(id=2, title="Autre", priority=2, fibonacci_points=1, status="todo",
            estimated_minutes=30)

    schedule = build_day_schedule([a, b], [dw], DAY, settings=_settings())

    by_id = {s.task.id: s for s in schedule.scheduled}
    # A (la plus urgente) occupe le début du bloc, avec SA PROPRE durée (30 min).
    assert by_id[1].deepwork is True
    assert by_id[1].start_at == datetime.combine(DAY, time(9, 0))
    assert by_id[1].duration_minutes == 30
    # B vient occuper la suite de la fenêtre, avec le même label deep-work.
    assert by_id[2].deepwork is True
    assert by_id[2].start_at == datetime.combine(DAY, time(9, 30))
    assert by_id[2].duration_minutes == 30


def test_deepwork_block_exact_fill_with_two_tasks() -> None:
    dw = TimeBlock(title="Focus", kind="deepwork",
                   start=datetime.combine(DAY, time(9, 0)),
                   end=datetime.combine(DAY, time(10, 0)), source="manual")
    a = Task(id=1, title="A", priority=0, fibonacci_points=1, status="todo",
            estimated_minutes=30)
    b = Task(id=2, title="B", priority=1, fibonacci_points=1, status="todo",
            estimated_minutes=30)

    schedule = build_day_schedule([a, b], [dw], DAY, settings=_settings())

    by_id = {s.task.id: s for s in schedule.scheduled}
    assert by_id[1].deepwork is True and by_id[1].duration_minutes == 30
    assert by_id[2].deepwork is True and by_id[2].duration_minutes == 30
    assert by_id[2].start_at == datetime.combine(DAY, time(9, 30))


def test_deepwork_block_skips_task_too_big_for_remaining_time() -> None:
    dw = TimeBlock(title="Focus", kind="deepwork",
                   start=datetime.combine(DAY, time(9, 0)),
                   end=datetime.combine(DAY, time(10, 0)), source="manual")
    a = Task(id=1, title="A", priority=0, fibonacci_points=1, status="todo",
            estimated_minutes=30)
    b = Task(id=2, title="B trop grande", priority=1, fibonacci_points=1, status="todo",
            estimated_minutes=40)

    schedule = build_day_schedule([a, b], [dw], DAY, settings=_settings())

    by_id = {s.task.id: s for s in schedule.scheduled}
    assert by_id[1].deepwork is True
    # B ne tient pas dans les 30 min restantes du bloc : elle n'est pas deep-work et
    # est placée normalement, en dehors de la fenêtre protégée.
    assert by_id[2].deepwork is False
    assert by_id[2].start_at >= datetime.combine(DAY, time(10, 0))


def test_deepwork_block_is_available_time_not_busy() -> None:
    # Un bloc deep-work ne réduit PAS le temps disponible (c'est du travail dédié).
    dw = TimeBlock(title="Focus", kind="deepwork",
                   start=datetime.combine(DAY, time(9, 0)),
                   end=datetime.combine(DAY, time(11, 0)), source="manual")
    settings = _settings(workday_start_hour=9, workday_end_hour=12)  # 180 min
    schedule = build_day_schedule([], [dw], DAY, settings=settings)
    assert schedule.stats.available_minutes == 180  # le bloc deep-work n'est pas « occupé »


def test_empty_deepwork_block_still_protects_window() -> None:
    dw = TimeBlock(title="Focus", kind="deepwork",
                   start=datetime.combine(DAY, time(9, 0)),
                   end=datetime.combine(DAY, time(10, 0)), source="manual")
    a = Task(id=1, title="Seule tâche", priority=0, fibonacci_points=1, status="todo",
            estimated_minutes=30)

    schedule = build_day_schedule([a], [dw], DAY, settings=_settings())
    # La seule tâche remplit le bloc deep-work.
    assert schedule.scheduled[0].deepwork is True


def test_timeline_positions_busy_and_work_blocks() -> None:
    settings = _settings()  # journée 9h-18h
    meeting = TimeBlock(title="Réunion", start=datetime.combine(DAY, time(13, 0)),
                        end=datetime.combine(DAY, time(14, 0)), source="manual")
    task = Task(id=1, title="Tâche", priority=0, fibonacci_points=1, status="todo",
               estimated_minutes=45)

    schedule = build_day_schedule([task], [meeting], DAY, settings=settings)
    timeline = build_timeline(schedule, [meeting], DAY, settings=settings)

    by_kind = {e.kind: e for e in timeline}
    # Réunion 13h-14h → top = 4 h après 9h = 240 min, hauteur 60 min.
    assert by_kind["busy"].top_min == 240
    assert by_kind["busy"].height_min == 60
    # Tâche auto à 9h → top 0, hauteur = durée réelle.
    assert by_kind["work"].top_min == 0
    assert by_kind["work"].height_min == 45


def test_timeline_clamps_blocks_to_workday_window() -> None:
    settings = _settings()
    early_meeting = TimeBlock(title="Très tôt", start=datetime.combine(DAY, time(8, 0)),
                              end=datetime.combine(DAY, time(9, 30)), source="manual")

    schedule = build_day_schedule([], [early_meeting], DAY, settings=settings)
    timeline = build_timeline(schedule, [early_meeting], DAY, settings=settings)

    assert len(timeline) == 1
    assert timeline[0].top_min == 0  # borné au début de journée
    assert timeline[0].height_min == 30  # seule la partie 9h-9h30 est affichée


def test_timeline_marks_pinned_and_conflicts() -> None:
    settings = _settings()
    meeting = TimeBlock(title="Réunion", start=datetime.combine(DAY, time(10, 0)),
                        end=datetime.combine(DAY, time(11, 0)), source="manual")
    pinned_clash = Task(id=1, title="Épinglée en conflit", status="todo",
                        priority=2, fibonacci_points=1,
                        pinned_start=datetime.combine(DAY, time(10, 30)), estimated_minutes=30)

    schedule = build_day_schedule([pinned_clash], [meeting], DAY, settings=settings)
    timeline = build_timeline(schedule, [meeting], DAY, settings=settings)

    kinds = {e.kind for e in timeline}
    assert "conflict" in kinds


def test_smaller_task_still_fits_when_a_bigger_one_does_not() -> None:
    """Le placement ne s'arrête plus à la première tâche qui ne rentre pas : une
    grosse tâche qui ne tient pas dans le trou avant la réunion est écartée, mais la
    petite suivante, elle, tient dans ce trou et reste planifiée."""
    settings = _settings(workday_start_hour=9, workday_end_hour=10)  # 60 min
    meeting = TimeBlock(title="Réunion",
                        start=datetime.combine(DAY, time(9, 20)),
                        end=datetime.combine(DAY, time(10, 0)), source="manual")
    big = Task(id=1, title="Trop grosse", priority=0, fibonacci_points=1, status="todo",
              estimated_minutes=30)
    small = Task(id=2, title="Petite", priority=2, fibonacci_points=1, status="todo",
                estimated_minutes=15)

    schedule = build_day_schedule([big, small], [meeting], DAY, settings=settings)

    # La grosse (30 min) percute la réunion → repoussée à 10h05, hors fenêtre → écartée.
    # La petite (15 min) tient dans le trou 9h00-9h20 → planifiée à 9h.
    assert [t.id for t in schedule.unscheduled] == [1]
    assert [s.task.id for s in schedule.scheduled] == [2]
    assert schedule.scheduled[0].start_at == datetime.combine(DAY, time(9, 0))


def test_task_scheduled_for_later_without_deadline_goes_to_later_bucket() -> None:
    """Programmée pour lundi, sans échéance : masquée aujourd'hui (vendredi), pas
    perdue — elle apparaît dans « plus tard », jamais dans « sans créneau »."""
    later = Task(id=1, title="Pour lundi", status="todo", priority=2, fibonacci_points=1,
                 scheduled_date=DAY + timedelta(days=4))

    schedule = build_day_schedule([later], [], DAY, settings=_settings())

    assert schedule.scheduled == []
    assert schedule.unscheduled == []
    assert [t.id for t in schedule.later] == [1]


def test_scheduled_for_later_but_deadline_today_stays_visible() -> None:
    """L'échéance prime toujours sur la programmation : une deadline du jour rend
    la tâche visible et planifiée même si `scheduled_date` est future."""
    task = Task(id=1, title="Échéance aujourd'hui", status="todo", priority=2, fibonacci_points=1,
                scheduled_date=DAY + timedelta(days=4), deadline=DAY)

    schedule = build_day_schedule([task], [], DAY, settings=_settings())

    assert [s.task.id for s in schedule.scheduled] == [1]
    assert schedule.later == []


def test_overdue_scheduled_date_lands_in_bucket_zero() -> None:
    """Une tâche programmée dans le passé (jamais traitée) est aussi urgente qu'une
    échéance dépassée : bucket 0, priorité de placement maximale."""
    overdue = Task(id=1, title="En retard", status="todo", priority=2, fibonacci_points=1,
                   scheduled_date=DAY - timedelta(days=2))
    normal = Task(id=2, title="Normale", status="todo", priority=3, fibonacci_points=1)

    schedule = build_day_schedule([overdue, normal], [], DAY, settings=_settings())

    assert [s.task.id for s in schedule.scheduled] == [1, 2]


def test_pinned_task_scheduled_for_later_is_still_eligible_today() -> None:
    """Une tâche épinglée explicitement sur le jour reste visible même si sa
    `scheduled_date` est future — l'épinglage est un choix plus fort."""
    pinned = Task(id=1, title="Épinglée quand même", status="todo",
                  priority=2, fibonacci_points=1,
                  scheduled_date=DAY + timedelta(days=4),
                  pinned_start=datetime.combine(DAY, time(9, 0)))

    schedule = build_day_schedule([pinned], [], DAY, settings=_settings())

    assert [s.task.id for s in schedule.scheduled] == [1]
    assert schedule.later == []


def test_later_tasks_do_not_count_toward_required_minutes() -> None:
    later = Task(id=1, title="Plus tard", status="todo", priority=2, fibonacci_points=1,
                 estimated_minutes=60, scheduled_date=DAY + timedelta(days=4))
    today_task = Task(id=2, title="Aujourd'hui", status="todo", priority=2, fibonacci_points=1,
                      estimated_minutes=30)

    schedule = build_day_schedule([later, today_task], [], DAY, settings=_settings())

    assert schedule.stats.required_minutes == 30


# --------------------------------------------------------------------------- #
# « À traiter » (GTD, phase 12) : ni priorité ni points de Fibonacci → jamais triée
# --------------------------------------------------------------------------- #

def test_task_without_priority_or_fibo_goes_to_to_process() -> None:
    task = Task(id=1, title="Toute neuve", status="todo")

    schedule = build_day_schedule([task], [], DAY, settings=_settings())

    assert schedule.scheduled == []
    assert schedule.unscheduled == []
    assert [t.id for t in schedule.to_process] == [1]


def test_task_with_only_priority_still_goes_to_to_process() -> None:
    """Une priorité seule ne suffit pas : il manque encore l'effort (points Fibo)."""
    task = Task(id=1, title="Priorisée sans effort", status="todo", priority=0)

    schedule = build_day_schedule([task], [], DAY, settings=_settings())

    assert [t.id for t in schedule.to_process] == [1]
    assert schedule.scheduled == []


def test_task_with_only_fibo_still_goes_to_to_process() -> None:
    """Des points seuls ne suffisent pas : il manque encore la valeur (priorité)."""
    task = Task(id=1, title="Estimée sans priorité", status="todo", fibonacci_points=3)

    schedule = build_day_schedule([task], [], DAY, settings=_settings())

    assert [t.id for t in schedule.to_process] == [1]
    assert schedule.scheduled == []


def test_qualified_task_is_scheduled_normally() -> None:
    """Priorité ET points renseignés : la tâche entre dans le tri comme avant."""
    task = Task(id=1, title="Clarifiée", status="todo", priority=0, fibonacci_points=3)

    schedule = build_day_schedule([task], [], DAY, settings=_settings())

    assert [s.task.id for s in schedule.scheduled] == [1]
    assert schedule.to_process == []


def test_to_process_excluded_from_required_minutes() -> None:
    unqualified = Task(id=1, title="À traiter", status="todo", estimated_minutes=60)
    qualified = Task(id=2, title="Prête", status="todo", priority=2, fibonacci_points=1,
                     estimated_minutes=30)

    schedule = build_day_schedule([unqualified, qualified], [], DAY, settings=_settings())

    assert schedule.stats.required_minutes == 30  # la non qualifiée ne compte pas


def test_unqualified_and_blocked_task_goes_to_to_process_not_blocked() -> None:
    """La clarification prime sur le blocage : une tâche non qualifiée ET bloquée
    apparaît en « À traiter », pas en « Bloquées » (on ne peut pas organiser ce qui
    n'est pas encore clarifié)."""
    task = Task(id=1, title="Ni clarifiée ni débloquée", status="todo")

    schedule = build_day_schedule(
        [task], [], DAY, settings=_settings(), blocked_ids={1},
    )

    assert [t.id for t in schedule.to_process] == [1]


def test_pinned_unqualified_task_also_goes_to_to_process() -> None:
    """L'épinglage ne contourne pas la clarification : une tâche épinglée sans
    priorité ni points reste « À traiter », pas posée sur l'agenda."""
    pinned = Task(id=1, title="Épinglée mais pas clarifiée", status="todo",
                  pinned_start=datetime.combine(DAY, time(10, 0)))

    schedule = build_day_schedule([pinned], [], DAY, settings=_settings())

    assert [t.id for t in schedule.to_process] == [1]
    assert schedule.scheduled == []


def test_parent_with_open_children_exempt_from_qualification_gate() -> None:
    """Une mère (conteneur, jamais planifiée elle-même) n'a pas besoin d'être
    qualifiée : seules les unités de travail réelles (ses filles) le doivent."""
    parent = Task(id=1, title="Projet", status="todo")  # ni priorité ni fibo
    child = Task(id=2, title="Fille", status="todo", priority=0, fibonacci_points=1,
                parent_id=1, estimated_minutes=30)

    schedule = build_day_schedule([parent, child], [], DAY, settings=_settings())

    assert schedule.to_process == []  # la mère n'y est pas reléguée
    assert [s.task.id for s in schedule.scheduled] == [2]


# --------------------------------------------------------------------------- #
# W7 — Invariant « aucune tâche jamais perdue » (test de propriété, graine fixe)
# --------------------------------------------------------------------------- #

def _random_task(rnd: random.Random, task_id: int, existing_ids: list[int]) -> Task:
    status = rnd.choice(["todo", "todo", "todo", "todo", "done", "archived"])
    priority = rnd.choice([None, 0, 1, 2, 3, 4])
    deadline_offset = rnd.choice([None, -5, -3, -1, 0, 1, 2, 3, 7, 20])
    deadline = DAY + timedelta(days=deadline_offset) if deadline_offset is not None else None
    scheduled_offset = rnd.choice([None, -3, -1, 0, 1, 2, 4, 10])
    scheduled_date = (
        DAY + timedelta(days=scheduled_offset) if scheduled_offset is not None else None
    )
    pinned_start = None
    if rnd.random() < 0.15:
        pinned_start = datetime.combine(
            DAY, time(hour=rnd.randint(9, 17), minute=rnd.choice([0, 15, 30, 45]))
        )
    estimated_minutes = rnd.choice([None, 15, 30, 45, 60, 90])
    fibonacci_points = rnd.choice([None, 1, 2, 3, 5, 8, 13, 21])
    parent_id = rnd.choice(existing_ids) if existing_ids and rnd.random() < 0.25 else None
    return Task(
        id=task_id, title=f"Tâche {task_id}", status=status, priority=priority,
        deadline=deadline, scheduled_date=scheduled_date, pinned_start=pinned_start,
        estimated_minutes=estimated_minutes, fibonacci_points=fibonacci_points,
        parent_id=parent_id,
    )


def _random_busy_blocks(rnd: random.Random, count: int) -> list[TimeBlock]:
    blocks = []
    for i in range(count):
        start = datetime.combine(
            DAY, time(hour=rnd.randint(8, 17), minute=rnd.choice([0, 15, 30, 45]))
        )
        duration = rnd.choice([15, 30, 45, 60, 90, 120])
        kind = rnd.choice(["busy", "busy", "busy", "deepwork"])
        blocks.append(TimeBlock(
            title=f"Bloc {i}", start=start, end=start + timedelta(minutes=duration),
            kind=kind, source="manual",
        ))
    return blocks


def test_every_eligible_todo_task_lands_in_exactly_one_bucket() -> None:
    """Aucune fiche perdue : sur ~50 tirages aléatoires (graine fixe) combinant
    statuts, priorités, points de Fibonacci, deadlines, scheduled_date, épinglage,
    blocs deep-work et hiérarchie mère/fille, chaque tâche à faire (hors mère à
    filles ouvertes) apparaît dans exactement une des quatre listes
    scheduled/unscheduled/later/to_process (phase 12 : « À traiter » GTD)."""
    rnd = random.Random(42)
    settings = _settings()

    for trial in range(50):
        n_tasks = rnd.randint(3, 15)
        tasks: list[Task] = []
        ids: list[int] = []
        for task_id in range(1, n_tasks + 1):
            tasks.append(_random_task(rnd, task_id, ids))
            ids.append(task_id)
        blocks = _random_busy_blocks(rnd, rnd.randint(0, 3))

        schedule = build_day_schedule(tasks, blocks, DAY, settings=settings)

        todo_tasks = [t for t in tasks if t.status == "todo"]
        parents_with_open_children = {
            t.parent_id for t in todo_tasks if t.parent_id is not None
        }
        expected_ids = {
            t.id for t in todo_tasks if t.id not in parents_with_open_children
        }

        scheduled_ids = {s.task.id for s in schedule.scheduled}
        unscheduled_ids = {t.id for t in schedule.unscheduled}
        later_ids = {t.id for t in schedule.later}
        to_process_ids = {t.id for t in schedule.to_process}

        assert scheduled_ids.isdisjoint(unscheduled_ids), f"tirage {trial}"
        assert scheduled_ids.isdisjoint(later_ids), f"tirage {trial}"
        assert scheduled_ids.isdisjoint(to_process_ids), f"tirage {trial}"
        assert unscheduled_ids.isdisjoint(later_ids), f"tirage {trial}"
        assert unscheduled_ids.isdisjoint(to_process_ids), f"tirage {trial}"
        assert later_ids.isdisjoint(to_process_ids), f"tirage {trial}"

        union = scheduled_ids | unscheduled_ids | later_ids | to_process_ids
        assert union == expected_ids, f"tirage {trial} : {union} != {expected_ids}"


# --------------------------------------------------------------------------- #
# Y2 — Garde-fou de surcharge de priorité maximale (phase 7)
# --------------------------------------------------------------------------- #

def test_count_max_priority_tasks_counts_p0_only() -> None:
    """Échelle resserrée à P0/P1/P2 : le garde-fou ne compte que P0 (P1 n'est déjà
    plus la priorité maximale du barème, il ne doit pas diluer son propre signal)."""
    tasks = [
        Task(id=1, priority=0), Task(id=2, priority=1), Task(id=3, priority=2),
        Task(id=4, priority=None), Task(id=5, priority=0),
    ]
    assert count_max_priority_tasks(tasks) == 2


def test_count_max_priority_tasks_zero_when_none_at_max() -> None:
    tasks = [Task(id=1, priority=2), Task(id=2, priority=None)]
    assert count_max_priority_tasks(tasks) == 0


def test_count_max_priority_tasks_empty_list() -> None:
    assert count_max_priority_tasks([]) == 0


# --------------------------------------------------------------------------- #
# Ordonnancement WSJF (phase 9) : score = coût du retard / effort
# --------------------------------------------------------------------------- #

def test_wsjf_priority_value_is_exponential() -> None:
    """Une P0 vaut `base` fois plus qu'une P1 (valeur exponentielle), à effort/échéance
    égaux : le rapport des scores est exactement la base configurée."""
    s = _settings(priority_value_base=2.0)
    p0 = Task(id=1, priority=0, fibonacci_points=1)
    p1 = Task(id=2, priority=1, fibonacci_points=1)
    assert wsjf_score(p0, DAY, settings=s) == 4.0  # 2**(2-0)
    assert wsjf_score(p1, DAY, settings=s) == 2.0  # 2**(2-1)


def test_wsjf_missing_priority_sits_below_p2() -> None:
    """Échelle resserrée à P0/P1/P2 (P2 = la plus faible du barème)."""
    s = _settings(priority_value_base=2.0)
    p2 = Task(id=1, priority=2, fibonacci_points=1)
    none = Task(id=2, priority=None, fibonacci_points=1)
    assert wsjf_score(none, DAY, settings=s) < wsjf_score(p2, DAY, settings=s)


def test_wsjf_effort_denominator_uses_fibonacci() -> None:
    """À valeur égale, une tâche 8× plus grosse a un score 8× plus faible (le Fibo entre
    enfin dans l'ordre — c'est le cœur de WSJF)."""
    s = _settings()
    small = Task(id=1, priority=1, fibonacci_points=1)
    big = Task(id=2, priority=1, fibonacci_points=8)
    assert wsjf_score(small, DAY, settings=s) == 8 * wsjf_score(big, DAY, settings=s)


def test_wsjf_degrades_to_priority_order_without_effort() -> None:
    """Sans points ni estimation, l'effort est neutre (identique pour tous) : l'ordre
    retombe sur la seule valeur de priorité — dégradation propre."""
    s = _settings()
    a = Task(id=1, priority=0)  # ni fibo ni estimation
    b = Task(id=2, priority=2)
    # Effort commun ⇒ le ratio des scores = ratio des valeurs, P0 devant P2.
    assert wsjf_score(a, DAY, settings=s) > wsjf_score(b, DAY, settings=s)


def test_wsjf_time_criticality_ramps_within_horizon() -> None:
    """Une échéance proche relève le score ; au-delà de l'horizon, elle ne pèse pas."""
    s = _settings(urgency_horizon_days=14, urgency_peak=8.0)
    far = Task(id=1, priority=2, fibonacci_points=1, deadline=DAY + timedelta(days=30))
    near = Task(id=2, priority=2, fibonacci_points=1, deadline=DAY + timedelta(days=2))
    assert wsjf_score(far, DAY, settings=s) == 1.0  # criticité nulle : valeur P2 seule
    assert wsjf_score(near, DAY, settings=s) > wsjf_score(far, DAY, settings=s)


def test_wsjf_worked_example_orders_value_over_effort() -> None:
    """Exemple emblématique : B (P1 minuscule) et C (P2 mais due dans 2 j, petite) passent
    devant A (P0 mais grosse et lointaine) et D (P0 énorme sans échéance)."""
    s = _settings()
    a = Task(id=1, title="A", priority=0, fibonacci_points=8, deadline=DAY + timedelta(days=10))
    b = Task(id=2, title="B", priority=1, fibonacci_points=1, deadline=DAY + timedelta(days=20))
    c = Task(id=3, title="C", priority=2, fibonacci_points=2, deadline=DAY + timedelta(days=2))
    d = Task(id=4, title="D", priority=0, fibonacci_points=21)
    order = sorted([a, b, c, d], key=lambda t: urgency_key(t, DAY, settings=s))
    assert [t.id for t in order] == [2, 3, 1, 4]


def test_overdue_is_hard_tier_before_any_score() -> None:
    """« Hybride » : une tâche en retard, même à faible score, passe devant une tâche non
    en retard à très fort score. Le retard est un palier dur, pas une simple pondération."""
    s = _settings()
    overdue_weak = Task(id=1, title="En retard", priority=4, fibonacci_points=21,
                        deadline=DAY - timedelta(days=1))
    fresh_strong = Task(id=2, title="Fort score", priority=0, fibonacci_points=1,
                        deadline=DAY + timedelta(days=20))
    order = sorted([fresh_strong, overdue_weak], key=lambda t: urgency_key(t, DAY, settings=s))
    assert [t.id for t in order] == [1, 2]  # l'en-retard d'abord, malgré un score bien plus bas


def test_wsjf_orders_scheduled_list_end_to_end() -> None:
    """Bout en bout via `build_day_schedule` : la liste planifiée suit l'ordre WSJF."""
    s = _settings()
    small_p1 = Task(id=1, title="B", priority=1, fibonacci_points=1, status="todo",
                    estimated_minutes=30)
    big_p0 = Task(id=2, title="D", priority=0, fibonacci_points=21, status="todo",
                  estimated_minutes=30)
    schedule = build_day_schedule([big_p0, small_p1], [], DAY, settings=s)
    assert [x.task.id for x in schedule.scheduled] == [1, 2]  # le petit P1 avant le gros P0


# --------------------------------------------------------------------------- #
# Timeline des sessions réelles (phase 11)
# --------------------------------------------------------------------------- #

def _utc_of_local(y, mo, d, hh, mm) -> datetime:
    """Heure locale de paroi → UTC aware (round-trip déterministe quel que soit le fuseau)."""
    from datetime import timezone as _tz
    return datetime(y, mo, d, hh, mm).astimezone(_tz.utc)


def test_session_timeline_places_closed_session() -> None:
    from app.tasks_models import WorkSession
    from app.tasks_scheduling import session_timeline_entries
    s = WorkSession(task_id=7, started_at=_utc_of_local(2026, 7, 2, 10, 0),
                    ended_at=_utc_of_local(2026, 7, 2, 11, 30))
    entries = session_timeline_entries([s], DAY, {7: "Rédaction"}, settings=_settings())
    assert len(entries) == 1
    assert entries[0].kind == "session"
    assert entries[0].title == "Rédaction"
    assert entries[0].top_min == 60      # 10h, journée démarrée à 9h
    assert entries[0].height_min == 90   # 1h30


def test_session_timeline_running_extends_to_now() -> None:
    from app.tasks_models import WorkSession
    from app.tasks_scheduling import session_timeline_entries
    s = WorkSession(task_id=1, started_at=_utc_of_local(2026, 7, 2, 9, 0), ended_at=None)
    now = datetime(2026, 7, 2, 9, 30)  # heure locale naïve
    entries = session_timeline_entries([s], DAY, {1: "En cours"}, settings=_settings(), now=now)
    assert entries[0].height_min == 30


def test_session_timeline_clamps_outside_workday() -> None:
    from app.tasks_models import WorkSession
    from app.tasks_scheduling import session_timeline_entries
    # Session entièrement avant l'heure de début de journée → ignorée.
    s = WorkSession(task_id=1, started_at=_utc_of_local(2026, 7, 2, 6, 0),
                    ended_at=_utc_of_local(2026, 7, 2, 7, 0))
    assert session_timeline_entries([s], DAY, {1: "Tôt"}, settings=_settings()) == []


# --------------------------------------------------------------------------- #
# Creux de l'après-midi (post-lunch dip) : placement conscient de l'heure
# --------------------------------------------------------------------------- #

def _at(hh, mm=0) -> datetime:
    return datetime.combine(DAY, time(hh, mm))


def test_dip_intensity_triangular_shape() -> None:
    from app.tasks_scheduling import _dip_intensity
    s = _settings()  # 13 / 15 / 16 par défaut, activé
    assert _dip_intensity(_at(15, 0), s) == 1.0            # tronc
    assert _dip_intensity(_at(14, 0), s) == 0.5            # mi-rampe montante
    assert _dip_intensity(_at(15, 30), s) == 0.5           # mi-rampe descendante
    assert _dip_intensity(_at(13, 0), s) == 0.0            # bord gauche (exclu)
    assert _dip_intensity(_at(16, 0), s) == 0.0            # bord droit (exclu)
    assert _dip_intensity(_at(9, 0), s) == 0.0             # matin, hors fenêtre


def test_dip_intensity_zero_when_disabled_or_penalty_null() -> None:
    from app.tasks_scheduling import _dip_intensity
    assert _dip_intensity(_at(15, 0), _settings(cognitive_dip_enabled=False)) == 0.0
    assert _dip_intensity(_at(15, 0), _settings(cognitive_dip_penalty=0.0)) == 0.0


def test_dip_defers_complex_task_and_promotes_simple_one() -> None:
    """Au tronc du creux, une tâche légère passe devant une tâche complexe pourtant plus
    urgente au sens WSJF pur — le créneau creux lui est préféré."""
    settings = _settings(workday_start_hour=15, workday_end_hour=18)  # 1er créneau = tronc
    complex_urgent = Task(id=1, title="Complexe", priority=0, fibonacci_points=13,
                          estimated_minutes=30, status="todo")
    simple = Task(id=2, title="Simple", priority=4, fibonacci_points=1,
                  estimated_minutes=30, status="todo")

    # Contrôle : sans le creux, la complexe (WSJF plus élevé) prend le 1er créneau.
    off = build_day_schedule([complex_urgent, simple], [], DAY,
                             settings=_settings(workday_start_hour=15, workday_end_hour=18,
                                                cognitive_dip_enabled=False))
    assert [s.task.id for s in off.scheduled] == [1, 2]

    # Avec le creux : la simple remonte au tronc, la complexe est repoussée après.
    on = build_day_schedule([complex_urgent, simple], [], DAY, settings=settings)
    assert [s.task.id for s in on.scheduled] == [2, 1]
    assert on.scheduled[0].start_at == _at(15, 0)
    assert on.scheduled[0].dip_note  # la tâche promue est annotée (transparence)
    assert not on.scheduled[1].dip_note


def test_dip_never_defers_an_overdue_task() -> None:
    """Palier dur : une tâche EN RETARD (et complexe) garde la priorité de placement,
    le creux ne la relègue jamais derrière une tâche simple non urgente."""
    settings = _settings(workday_start_hour=15, workday_end_hour=18)
    overdue_complex = Task(id=1, title="En retard", priority=0, fibonacci_points=13,
                           deadline=DAY - timedelta(days=1), estimated_minutes=30, status="todo")
    simple = Task(id=2, title="Simple", priority=4, fibonacci_points=1,
                  estimated_minutes=30, status="todo")

    schedule = build_day_schedule([overdue_complex, simple], [], DAY, settings=settings)
    assert schedule.scheduled[0].task.id == 1        # en retard placée d'abord (tier 0)
    assert schedule.scheduled[0].start_at == _at(15, 0)


def test_dip_does_not_disturb_dependency_raised_task() -> None:
    """Chemin critique : une tâche remontée par une dépendance (urgency_keys) n'est pas
    réordonnée par le creux."""
    settings = _settings(workday_start_hour=15, workday_end_hour=18)
    raised_complex = Task(id=1, title="Bloqueur", priority=4, fibonacci_points=13,
                          estimated_minutes=30, status="todo")
    simple = Task(id=2, title="Simple", priority=4, fibonacci_points=1,
                  estimated_minutes=30, status="todo")
    # La complexe est remontée au palier dur (0) par une dépendance urgente qu'elle bloque.
    urgency_keys = {1: (0, -100.0, 4, date.max, 1)}

    schedule = build_day_schedule([raised_complex, simple], [], DAY, settings=settings,
                                  urgency_keys=urgency_keys)
    assert schedule.scheduled[0].task.id == 1        # chemin critique intact malgré le creux
    assert not schedule.scheduled[0].dip_note


def test_dip_leaves_morning_untouched() -> None:
    """Le matin (hors fenêtre) reste piloté par l'urgence pure, même creux activé."""
    settings = _settings(workday_start_hour=9, workday_end_hour=12)
    complex_urgent = Task(id=1, title="Complexe", priority=0, fibonacci_points=13,
                          estimated_minutes=30, status="todo")
    simple = Task(id=2, title="Simple", priority=4, fibonacci_points=1,
                  estimated_minutes=30, status="todo")

    schedule = build_day_schedule([complex_urgent, simple], [], DAY, settings=settings)
    assert schedule.scheduled[0].start_at == _at(9, 0)
    assert schedule.scheduled[0].task.id == 1        # WSJF pur : complexe urgente d'abord
    assert not schedule.scheduled[0].dip_note
