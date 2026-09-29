"""Génère les fixtures des tests différentiels de Kairos 3 (docs/plan-v3-kotlin.md § 7).

Exécute le moteur **Python de Kairos 2** (`app/`) sur des centaines de scénarios
tirés au hasard avec une graine fixe, et écrit entrées et sorties attendues en JSON
dans `kmp/core/src/jvmTest/resources/fixtures/`. Les tests Kotlin
(`DifferentialTest`) rejouent chaque scénario sur le moteur porté et exigent
exactement les mêmes sorties : ordre, heures, listes, scores (flottants exacts).

À relancer seulement si le moteur Python ou ce générateur change (les fixtures sont
commitées ; le Python disparaît à la bascule 3.0.0, les fixtures restent) :

    pip install -e ".[dev]"          # à la racine du dépôt
    python kmp/tools/gen_fixtures.py
"""
from __future__ import annotations

import json
import random
import re
import sys
from datetime import date, datetime, time, timedelta, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT))

from app.config import Settings  # noqa: E402
from app.tasks_dependencies import (  # noqa: E402
    blocked_task_ids,
    derived_urgency,
    detect_cycle_nodes,
    would_create_cycle,
)
from app.tasks_models import Task, TimeBlock  # noqa: E402
from app.tasks_recurrence import (  # noqa: E402
    expand_recurring_blocks,
    next_deadline,
    next_snooze_date,
)
from app.tasks_scheduling import (  # noqa: E402
    build_day_schedule,
    build_timeline,
    urgency_bucket,
    urgency_key,
    wsjf_score,
)
from app.tasks_staleness import days_stale  # noqa: E402
from app.tasks_stats import calibration_by_type, compute_dashboard_stats, fibonacci_references  # noqa: E402
from app.tasks_time import spent_minutes_by_task  # noqa: E402
from app.tasks_models import WorkSession  # noqa: E402
from app.workdays import (  # noqa: E402
    add_business_days,
    build_holidays,
    business_days_between,
    easter_sunday,
    on_or_before_business_day,
)

OUT = ROOT / "kmp" / "core" / "src" / "jvmTest" / "resources" / "fixtures"
WORDS = ["Rapport", "Revue", "Bug", "Réunion", "Doc", "Budget", "Client", "Tests", "Mail", "Plan", "Démo", "Audit"]
NOTE = re.compile(r"après « (.*) »( \(épinglée\))?$")
CONFLICT = re.compile(r"^chevauche « (.*) »$")

# Réglages tirés au hasard : nom Python -> (nom Kotlin, valeurs possibles).
SETTINGS_SPACE = {
    "default_task_duration_minutes": ("defaultTaskDurationMinutes", [15, 30, 45]),
    "meeting_buffer_minutes": ("meetingBufferMinutes", [0, 5, 10]),
    "workday_start_hour": ("workdayStartHour", [8, 9]),
    "workday_end_hour": ("workdayEndHour", [17, 18, 19]),
    "priority_value_base": ("priorityValueBase", [2.0, 3.0, 4.0]),
    "urgency_horizon_days": ("urgencyHorizonDays", [7, 14, 21]),
    "urgency_peak": ("urgencyPeak", [4.0, 8.0]),
    "default_fibonacci_points": ("defaultFibonacciPoints", [1, 3, 5]),
    "cognitive_dip_enabled": ("cognitiveDipEnabled", [True, True, False]),
    "cognitive_dip_penalty": ("cognitiveDipPenalty", [0.0, 0.5, 1.0, 1.0]),
    "stale_overdue_days": ("staleOverdueDays", [0, 7]),
    "stale_untouched_days": ("staleUntouchedDays", [0, 14]),
}
DIP_WINDOWS = [(13, 15, 16), (12, 14, 17), (14, 14, 16)]


def iso(value):
    if value is None:
        return None
    if isinstance(value, datetime):
        return value.strftime("%Y-%m-%dT%H:%M:%S") if value.second else value.strftime("%Y-%m-%dT%H:%M")
    return value.isoformat()


def random_settings(rng: random.Random) -> tuple[dict, dict]:
    py, kt = {}, {}
    for name, (kotlin, values) in SETTINGS_SPACE.items():
        value = rng.choice(values)
        py[name] = value
        kt[kotlin] = value
    start, trough, end = rng.choice(DIP_WINDOWS)
    for name, kotlin, value in (
        ("cognitive_dip_start_hour", "cognitiveDipStartHour", start),
        ("cognitive_dip_trough_hour", "cognitiveDipTroughHour", trough),
        ("cognitive_dip_end_hour", "cognitiveDipEndHour", end),
    ):
        py[name] = value
        kt[kotlin] = value
    return py, kt


def random_task(rng: random.Random, task_id: int, day: date, ids: list[int]) -> Task:
    def maybe_date():
        return None if rng.random() < 0.5 else day + timedelta(days=rng.randint(-12, 25))

    pinned = None
    if rng.random() < 0.12:
        pin_day = day if rng.random() < 0.8 else day + timedelta(days=rng.choice([-1, 1]))
        pinned = datetime.combine(pin_day, time(rng.randint(7, 18), rng.choice([0, 15, 30, 45])))
    return Task(
        id=task_id,
        title=f"{rng.choice(WORDS)} {task_id}",
        priority=rng.choice([None, 0, 1, 1, 2, 2, 4]) if rng.random() < 0.9 else None,
        fibonacci_points=rng.choice([None, 1, 2, 3, 5, 8, 13, 21, 3, 5]),
        estimated_minutes=rng.choice([None, None, 0, 15, 20, 30, 45, 60, 90, 120, 240]),
        deadline=maybe_date(),
        scheduled_date=None if rng.random() < 0.7 else day + timedelta(days=rng.randint(-5, 10)),
        pinned_start=pinned,
        parent_id=rng.choice(ids) if ids and rng.random() < 0.12 else None,
        status=rng.choice(["todo"] * 8 + ["done", "archived"]),
        updated_at=datetime.combine(day - timedelta(days=rng.randint(0, 40)), time(rng.randint(0, 23), rng.randint(0, 59))),
    )


def random_block(rng: random.Random, day: date, index: int) -> TimeBlock:
    start = datetime.combine(day, time(rng.randint(7, 18), rng.choice([0, 15, 30, 45])))
    if rng.random() < 0.08:
        start -= timedelta(days=1)  # hors du jour : ignoré par le moteur
    end = start + timedelta(minutes=rng.choice([15, 30, 45, 60, 90, 120]))
    kind = "deepwork" if rng.random() < 0.25 else "busy"
    return TimeBlock(title=f"Créneau {index}", start=start, end=end, source="manual", kind=kind)


def task_json(t: Task) -> dict:
    return {
        "id": t.id, "title": t.title, "priority": t.priority, "fibonacciPoints": t.fibonacci_points,
        "estimatedMinutes": t.estimated_minutes, "deadline": iso(t.deadline), "scheduledDate": iso(t.scheduled_date),
        "pinnedStart": iso(t.pinned_start), "parentId": t.parent_id, "status": t.status,
        "updatedAt": t.updated_at.strftime("%Y-%m-%dT%H:%M:%SZ"),
    }


def dip_task(rng: random.Random, task_id: int, day: date) -> Task:
    """Tâche qualifiée, sans date : la journée se remplit jusqu'au creux de l'après-midi."""
    return Task(
        id=task_id, title=f"{rng.choice(WORDS)} {task_id}", priority=rng.choice([1, 2, 2]),
        fibonacci_points=rng.choice([1, 2, 3, 5, 8, 13, 21]), estimated_minutes=rng.choice([30, 45, 60, 90]),
        deadline=None if rng.random() < 0.8 else day + timedelta(days=rng.randint(3, 25)),
        status="todo", updated_at=datetime.combine(day - timedelta(days=rng.randint(0, 10)), time(9)),
    )


def scheduling_case(seed: int, dip: bool = False) -> dict:
    """Scénario aléatoire ; ``dip`` : journée pleine de tâches qualifiées pour
    exercer le creux cognitif (choix d'une tâche légère sur le créneau creux)."""
    rng = random.Random(seed)
    day = date(2026, 1, 1) + timedelta(days=rng.randint(0, 730))
    py_settings, kt_settings = random_settings(rng)
    if dip:
        for name, value in (("cognitive_dip_enabled", True), ("cognitive_dip_penalty", rng.choice([0.5, 1.0]))):
            py_settings[name] = value
            kt_settings[SETTINGS_SPACE[name][0]] = value
    settings = Settings(**py_settings)
    now = None
    if dip:
        tasks = [dip_task(rng, task_id, day) for task_id in range(1, rng.randint(8, 16) + 1)]
    elif rng.random() < 0.5:
        now_day = day if rng.random() < 0.85 else day - timedelta(days=1)
        now = datetime.combine(now_day, time(rng.randint(6, 20), rng.randint(0, 59)))
    if not dip:
        tasks = []
        for task_id in range(1, rng.randint(0, 16) + 1):
            tasks.append(random_task(rng, task_id, day, [t.id for t in tasks]))
    blocks = [random_block(rng, day, i) for i in range(rng.randint(0, 5))]
    edges = []
    ids = [t.id for t in tasks]
    for _ in range(rng.randint(0, 6) if len(ids) >= 2 else 0):
        a, b = rng.sample(ids, 2)
        edges.append((a, b))

    # Même câblage que app/main.py::_build_kairos_context, avec le statut réel de
    # chaque tâche (une archivée ne bloque pas : écart volontaire, spec v3).
    status_by_id = {t.id: t.status for t in tasks}
    todo = [t for t in tasks if t.status == "todo"]
    blocked = blocked_task_ids(edges, status_by_id)
    own = {t.id: urgency_key(t, day, settings=settings) for t in todo}
    effective = derived_urgency(edges, own)
    raised = sorted(tid for tid, key in effective.items() if key < own.get(tid, key))
    schedule = build_day_schedule(
        tasks, blocks, day, now=now, settings=settings, blocked_ids=blocked, urgency_keys=effective
    )
    timeline = build_timeline(schedule, blocks, day, settings=settings)

    def scheduled_json(s):
        pushed_after = pushed_kind = None
        if s.pushed:
            match = NOTE.search(s.pushed_note)
            pushed_after = match.group(1)
            pushed_kind = "PINNED" if match.group(2) else "BUSY"
        conflict = CONFLICT.match(s.conflict_note).group(1) if s.conflict else None
        return {
            "id": s.task.id, "start": iso(s.start_at), "duration": s.duration_minutes, "pinned": s.pinned,
            "pushedAfter": pushed_after, "pushedAfterKind": pushed_kind, "conflictWith": conflict,
            "deepwork": s.deepwork, "dip": bool(s.dip_note),
        }

    return {
        "seed": seed,
        "day": iso(day),
        "now": iso(now),
        "settings": kt_settings,
        "tasks": [task_json(t) for t in tasks],
        "blocks": [{"title": b.title, "start": iso(b.start), "end": iso(b.end), "kind": b.kind} for b in blocks],
        "edges": [list(e) for e in edges],
        "expected": {
            "blocked": sorted(blocked),
            "raised": raised,
            "scheduled": [scheduled_json(s) for s in schedule.scheduled],
            "unscheduled": [t.id for t in schedule.unscheduled],
            "later": [t.id for t in schedule.later],
            "toProcess": [t.id for t in schedule.to_process],
            "required": schedule.stats.required_minutes,
            "available": schedule.stats.available_minutes,
            "timeline": [
                {"kind": e.kind.upper().replace("-", "_"), "title": e.title, "top": e.top_min, "height": e.height_min}
                for e in timeline
            ],
            "scores": {str(t.id): repr(wsjf_score(t, day, settings=settings)) for t in todo},
            "buckets": {str(t.id): urgency_bucket(t, day) for t in todo},
            "stale": {
                str(t.id): d for t in todo
                if (d := days_stale(t, day, overdue_days=settings.stale_overdue_days,
                                    untouched_days=settings.stale_untouched_days)) is not None
            },
        },
    }


def dependencies_case(seed: int) -> dict:
    rng = random.Random(10_000 + seed)
    n = rng.randint(2, 9)
    edges = [tuple(rng.sample(range(1, n + 1), 2)) for _ in range(rng.randint(0, 12))]
    if rng.random() < 0.2:
        node = rng.randint(1, n)
        edges.append((node, node))
    status = {i: rng.choice(["todo", "todo", "done"]) for i in range(1, n + 1)}
    own = {i: (rng.randint(0, 5),) for i in range(1, n + 1)}
    new = (rng.randint(1, n), rng.randint(1, n))
    return {
        "edges": [list(e) for e in edges],
        "status": {str(k): v for k, v in status.items()},
        "own": {str(k): v[0] for k, v in own.items()},
        "newEdge": list(new),
        "expected": {
            "cycleNodes": sorted(detect_cycle_nodes(edges)),
            "blocked": sorted(blocked_task_ids(edges, status)),
            "derived": {str(k): v[0] for k, v in derived_urgency(edges, own).items()},
            "wouldCreateCycle": would_create_cycle(edges, *new),
        },
    }


def calendar_cases() -> dict:
    rng = random.Random(20_000)
    holidays = build_holidays(range(2025, 2029), france=True, extra=["2026-08-14"])
    rules = ["daily", "weekdays", "weekly", "monthly"]
    deadlines = []
    for _ in range(400):
        rule = rng.choice(rules)
        base = date(2025, 1, 1) + timedelta(days=rng.randint(0, 1400))
        dow = rng.choice([None, 0, 1, 2, 3, 4, 5, 6]) if rule == "weekly" else None
        deadlines.append({"rule": rule, "base": iso(base), "dow": dow, "expected": iso(next_deadline(rule, base, dow))})
    business = []
    for _ in range(300):
        start = date(2025, 1, 1) + timedelta(days=rng.randint(0, 1400))
        days = rng.randint(-1, 9)
        end = start + timedelta(days=rng.randint(-3, 30))
        deadline = rng.choice([None, start + timedelta(days=rng.randint(-5, 5))])
        business.append({
            "start": iso(start), "days": days, "end": iso(end), "deadline": iso(deadline),
            "add": iso(add_business_days(start, days, holidays)),
            "onOrBefore": iso(on_or_before_business_day(start, holidays)),
            "between": business_days_between(start, end, holidays),
            "snooze": iso(next_snooze_date(deadline, start, holidays)),
        })
    blocks = []
    for i in range(80):
        origin = datetime.combine(date(2026, 1, 1) + timedelta(days=rng.randint(0, 300)), time(rng.randint(7, 18), rng.choice([0, 30])))
        tpl = TimeBlock(title=f"Bloc {i}", start=origin, end=origin + timedelta(minutes=rng.choice([30, 60, 90])),
                        source="manual", kind="busy", recurrence=rng.choice(["daily", "weekdays", "weekly"]))
        range_start = origin.date() + timedelta(days=rng.randint(-10, 20))
        range_end = range_start + timedelta(days=rng.randint(0, 14))
        occ = expand_recurring_blocks([tpl], range_start, range_end)
        blocks.append({
            "start": iso(tpl.start), "end": iso(tpl.end), "recurrence": tpl.recurrence,
            "rangeStart": iso(range_start), "rangeEnd": iso(range_end),
            "expected": [[iso(o.start), iso(o.end)] for o in occ],
        })
    return {
        "holidays": sorted(iso(d) for d in holidays),
        "easter": {str(y): iso(easter_sunday(y)) for y in range(1950, 2101)},
        "nextDeadline": deadlines,
        "business": business,
        "blocks": blocks,
    }


TYPES = ["", "", "Dev", "Réunion", "Admin"]


def stats_case(seed: int) -> dict:
    """Statistiques (`compute_dashboard_stats`, repères du guide des points,
    calibration par type) sur un historique tiré au hasard, en UTC."""
    rng = random.Random(30_000 + seed)
    today = date(2026, 1, 1) + timedelta(days=rng.randint(0, 600))
    settings = Settings(
        stats_window_weeks=rng.choice([1, 4, 8, 12]),
        stale_overdue_days=rng.choice([0, 7]),
        stale_untouched_days=rng.choice([0, 14]),
    )
    now = datetime.combine(today, time(rng.randint(8, 20), rng.randint(0, 59)), tzinfo=timezone.utc)
    tasks = []
    for task_id in range(1, rng.randint(0, 25) + 1):
        created = datetime.combine(today - timedelta(days=rng.randint(0, 120)), time(rng.randint(0, 23), rng.randint(0, 59)))
        updated = created + timedelta(days=rng.randint(0, 60), minutes=rng.randint(0, 600))
        if updated > now.replace(tzinfo=None):
            updated = now.replace(tzinfo=None) - timedelta(minutes=rng.randint(0, 300))
        tasks.append(Task(
            id=task_id, title=f"{rng.choice(WORDS)} {task_id}",
            priority=rng.choice([None, 0, 1, 2]), fibonacci_points=rng.choice([None, 1, 2, 3, 5, 8, 13, 21, 0]),
            estimated_minutes=rng.choice([None, 0, 15, 30, 45, 60, 120]),
            task_type=rng.choice(TYPES), status=rng.choice(["todo"] * 3 + ["done"] * 3 + ["archived"]),
            deadline=None if rng.random() < 0.5 else today + timedelta(days=rng.randint(-30, 20)),
            scheduled_date=None if rng.random() < 0.8 else today + timedelta(days=rng.randint(-10, 10)),
            manual_time_spent_minutes=rng.choice([None, None, None, 0, 10, 40]),
            created_at=created, updated_at=updated,
        ))
    sessions = []
    ids = [t.id for t in tasks] + [999]
    for session_id in range(1, rng.randint(0, 30) + 1):
        start = now - timedelta(days=rng.randint(0, 100), minutes=rng.randint(0, 900))
        end = None if session_id == 1 and rng.random() < 0.3 else start + timedelta(minutes=rng.randint(0, 180), seconds=rng.randint(0, 59))
        if end is not None and end > now:
            end = now
        sessions.append(WorkSession(id=session_id, task_id=rng.choice(ids), started_at=start.replace(tzinfo=None),
                                    ended_at=end.replace(tzinfo=None) if end else None))
    stats = compute_dashboard_stats(tasks, sessions, today, settings=settings, now=now)
    spent = spent_minutes_by_task(sessions, now=now, tasks=tasks)
    live = [t for t in tasks if t.status != "archived"]

    def calib(items):
        return [[str(c.points if hasattr(c, "points") else c.key), c.count, c.median_minutes] for c in items]

    return {
        "today": iso(today), "now": now.strftime("%Y-%m-%dT%H:%M:%SZ"),
        "settings": {"statsWindowWeeks": settings.stats_window_weeks, "staleOverdueDays": settings.stale_overdue_days,
                     "staleUntouchedDays": settings.stale_untouched_days},
        "tasks": [dict(task_json(t), taskType=t.task_type, manualTimeSpentMinutes=t.manual_time_spent_minutes,
                       createdAt=t.created_at.strftime("%Y-%m-%dT%H:%M:%SZ")) for t in tasks],
        "sessions": [{"id": s.id, "taskId": s.task_id, "startedAt": s.started_at.strftime("%Y-%m-%dT%H:%M:%SZ"),
                      "endedAt": s.ended_at.strftime("%Y-%m-%dT%H:%M:%SZ") if s.ended_at else None} for s in sessions],
        "expected": {
            "windowWeeks": stats.window_weeks,
            "completedInWindow": stats.completed_in_window,
            "trackedMinutesWindow": stats.tracked_minutes_window,
            "throughput": [[iso(w.week_start), w.completed, w.points] for w in stats.throughput],
            "calibration": calib(stats.calibration),
            "bias": None if stats.bias is None else [stats.bias.count, stats.bias.estimated_minutes, stats.bias.real_minutes, repr(stats.bias.ratio)],
            "timeByType": [[s.key, s.minutes, s.pct] for s in stats.time_by_type],
            "focus": [stats.focus.session_count, stats.focus.total_minutes, stats.focus.avg_session_minutes],
            "flow": [stats.flow.open_count, stats.flow.median_age_days, stats.flow.overdue_count, stats.flow.stale_count,
                     stats.flow.completion_delay_days, stats.flow.deadline_total, stats.flow.deadline_on_time, stats.flow.deadline_hit_pct],
            "completeness": [stats.completeness.total, stats.completeness.with_points, stats.completeness.with_estimate,
                             stats.completeness.with_type, stats.completeness.points_pct, stats.completeness.estimate_pct,
                             stats.completeness.type_pct],
            "references": {str(k): [None if r.calibration is None else [r.calibration.count, r.calibration.median_minutes], list(r.examples)]
                           for k, r in fibonacci_references(live, spent_minutes_by_task(sessions, now=now, tasks=live)).items()},
            "byType": calib(calibration_by_type(live, spent_minutes_by_task(sessions, now=now, tasks=live))),
        },
    }


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    fixtures = {
        "scheduling.json": [scheduling_case(seed) for seed in range(1, 401)]
        + [scheduling_case(seed, dip=True) for seed in range(401, 481)],
        "dependencies.json": [dependencies_case(seed) for seed in range(1, 301)],
        "calendar.json": calendar_cases(),
        "stats.json": [stats_case(seed) for seed in range(1, 201)],
    }
    for name, data in fixtures.items():
        (OUT / name).write_text(json.dumps(data, ensure_ascii=False, indent=1) + "\n", encoding="utf-8")
        print(f"Écrit : {OUT / name}")


if __name__ == "__main__":
    main()
