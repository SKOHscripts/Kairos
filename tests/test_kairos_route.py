"""Tests de la route « Kairos » (T7/T8) : rendu, priorité, bloc manuel, dégradation, vue semaine."""

from __future__ import annotations

from datetime import date, timedelta

import pytest
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker
from sqlalchemy.pool import StaticPool
from starlette.testclient import TestClient

from app import main
from app.config import Settings
from app.pilotage_link import PilotageBase
from app.tasks_models import Task, TasksBase, TimeBlock

TODAY = date.today()  # la route calcule sa propre journée avec `date.today()`


@pytest.fixture
def route_client(monkeypatch):
    """Monte l'app réelle sur une base tâches + une base dette technique en mémoire
    dédiées (StaticPool) : la route « Kairos » lit désormais les deux (synchro
    GitLab mutualisée sur `CachedGitLabIssue`)."""
    engine = create_engine(
        "sqlite://", connect_args={"check_same_thread": False}, poolclass=StaticPool
    )
    TasksBase.metadata.create_all(engine)
    TestSession = sessionmaker(bind=engine, expire_on_commit=False)

    ticket_engine = create_engine(
        "sqlite://", connect_args={"check_same_thread": False}, poolclass=StaticPool
    )
    PilotageBase.metadata.create_all(ticket_engine)
    TicketTestSession = sessionmaker(bind=ticket_engine, expire_on_commit=False)

    def override_tasks_session():
        db = TestSession()
        try:
            yield db
        finally:
            db.close()

    def override_session():
        db = TicketTestSession()
        try:
            yield db
        finally:
            db.close()

    test_settings = Settings()
    monkeypatch.setattr(main, "get_settings", lambda: test_settings)
    main.app.dependency_overrides[main.get_tasks_session] = override_tasks_session
    main.app.dependency_overrides[main.get_pilotage_session] = override_session
    client = TestClient(main.app)
    client.ticket_session_factory = TicketTestSession  # base dette technique (GitLab)
    try:
        yield client, TestSession
    finally:
        main.app.dependency_overrides.clear()


def test_get_home_renders_readme_content() -> None:
    """La page d'accueil (`/`) rend le README (mutualisé) : pas de DB requise."""
    client = TestClient(main.app)
    resp = client.get("/")
    assert resp.status_code == 200
    assert "Bienvenue" in resp.text
    assert "Ordonnancement automatique" in resp.text  # section du README
    assert "&amp;amp;" not in resp.text  # pas de double-échappement du sommaire


def test_get_home_toc_links_to_readme_sections() -> None:
    client = TestClient(main.app)
    resp = client.get("/")
    assert resp.status_code == 200
    assert 'href="#fonctionnalites"' in resp.text


def test_get_spec_kairos_file_served() -> None:
    """Le lien relatif `SPEC_KAIROS.md` du README reste résolvable depuis la page."""
    client = TestClient(main.app)
    resp = client.get("/SPEC_KAIROS.md")
    assert resp.status_code == 200


def test_get_kairos_returns_200_even_without_data(route_client) -> None:
    client, _ = route_client
    resp = client.get("/kairos")
    assert resp.status_code == 200
    assert "Kairos" in resp.text


def test_get_kairos_shows_timetree_not_configured_banner(route_client) -> None:
    client, _ = route_client
    resp = client.get("/kairos")
    assert resp.status_code == 200
    assert "non configuré" in resp.text


def test_update_priority_changes_only_priority(route_client) -> None:
    client, TestSession = route_client
    with TestSession() as db:
        task = Task(title="Tâche", status="todo")
        db.add(task)
        db.commit()
        task_id = task.id

    resp = client.post(
        f"/kairos/tasks/{task_id}/priority", data={"priority": "1"}, follow_redirects=False
    )
    assert resp.status_code == 303

    with TestSession() as db:
        task = db.get(Task, task_id)
        assert task.priority == 1
        assert task.title == "Tâche"  # rien d'autre n'a bougé


def test_create_manual_block_appears_and_impacts_scheduling(route_client) -> None:
    client, TestSession = route_client
    with TestSession() as db:
        urgent = Task(title="Sujet urgent", priority=0, deadline=TODAY, status="todo")
        db.add(urgent)
        db.commit()

    resp = client.post(
        "/kairos/blocks",
        data={
            "title": "Réunion budget",
            "start": f"{TODAY.isoformat()}T13:00",
            "end": f"{TODAY.isoformat()}T14:00",
        },
        follow_redirects=False,
    )
    assert resp.status_code == 303

    with TestSession() as db:
        blocks = db.query(TimeBlock).all()
        assert len(blocks) == 1
        assert blocks[0].source == "manual"

    page = client.get("/kairos")
    assert "Réunion budget" in page.text


def test_quick_create_native_task(route_client) -> None:
    client, TestSession = route_client
    resp = client.post(
        "/kairos/tasks",
        data={
            "title": "  Relire le rapport  ",
            "priority": "1",
            "deadline": TODAY.isoformat(),
            "project_tag": "MSI",
            "estimated_minutes": "45",
        },
        follow_redirects=False,
    )
    assert resp.status_code == 303

    with TestSession() as db:
        task = db.query(Task).one()
        assert task.title == "Relire le rapport"  # titre nettoyé
        assert task.priority == 1
        assert task.deadline == TODAY
        assert task.project_tag == "MSI"
        assert task.estimated_minutes == 45
        assert task.source == "native"


def test_quick_create_rejects_blank_title(route_client) -> None:
    client, TestSession = route_client
    client.post("/kairos/tasks", data={"title": "   "}, follow_redirects=False)
    with TestSession() as db:
        assert db.query(Task).count() == 0


def test_edit_task_updates_all_fields(route_client) -> None:
    client, TestSession = route_client
    with TestSession() as db:
        task = Task(title="Avant", status="todo")
        db.add(task)
        db.commit()
        task_id = task.id

    resp = client.post(
        f"/kairos/tasks/{task_id}/edit",
        data={
            "title": "Après",
            "description": "Détails",
            "priority": "2",
            "deadline": TODAY.isoformat(),
            "project_tag": "Perso",
            "estimated_minutes": "30",
            "recurrence": "weekly",
        },
        follow_redirects=False,
    )
    assert resp.status_code == 303
    with TestSession() as db:
        task = db.get(Task, task_id)
        assert task.title == "Après"
        assert task.description == "Détails"
        assert task.priority == 2
        assert task.deadline == TODAY
        assert task.project_tag == "Perso"
        assert task.estimated_minutes == 30
        assert task.recurrence == "weekly"


def test_edit_task_sets_task_type_and_fibonacci_points(route_client) -> None:
    client, TestSession = route_client
    with TestSession() as db:
        task = Task(title="À catégoriser", status="todo")
        db.add(task)
        db.commit()
        task_id = task.id

    resp = client.post(
        f"/kairos/tasks/{task_id}/edit",
        data={"title": "À catégoriser", "task_type": "dev", "fibonacci_points": "5"},
        follow_redirects=False,
    )
    assert resp.status_code == 303
    with TestSession() as db:
        task = db.get(Task, task_id)
        assert task.task_type == "dev"
        assert task.fibonacci_points == 5


def test_edit_task_rejects_unknown_task_type(route_client) -> None:
    """Whitelist côté route (même patron que recurrence) : une valeur hors liste
    est silencieusement ignorée, pas stockée telle quelle."""
    client, TestSession = route_client
    with TestSession() as db:
        task = Task(title="À catégoriser", status="todo", task_type="dev")
        db.add(task)
        db.commit()
        task_id = task.id

    client.post(
        f"/kairos/tasks/{task_id}/edit",
        data={"title": "À catégoriser", "task_type": "n-importe-quoi"},
        follow_redirects=False,
    )
    with TestSession() as db:
        assert db.get(Task, task_id).task_type == ""


def test_edit_task_pose_type_points_and_pin_in_one_submit(route_client) -> None:
    """Un seul POST /edit pose les infos, la typologie/points ET l'heure fixe —
    plus besoin de deux soumissions séparées (ergonomie phase 5)."""
    client, TestSession = route_client
    with TestSession() as db:
        task = Task(title="Tout en un", status="todo")
        db.add(task)
        db.commit()
        task_id = task.id

    resp = client.post(
        f"/kairos/tasks/{task_id}/edit",
        data={
            "title": "Tout en un", "priority": "1", "task_type": "reunion",
            "fibonacci_points": "3", "pin_time": "14:00", "pin_day": TODAY.isoformat(),
        },
        follow_redirects=False,
    )
    assert resp.status_code == 303
    with TestSession() as db:
        task = db.get(Task, task_id)
        assert task.priority == 1
        assert task.task_type == "reunion"
        assert task.fibonacci_points == 3
        assert task.pinned_start is not None
        assert task.pinned_start.hour == 14


def test_edit_task_links_and_unlinks_ticket(route_client) -> None:
    """Phase 6 : liaison manuelle en lecture seule vers une fiche Ticket existante."""
    from app.pilotage_link import LinkedTicket

    client, TestSession = route_client
    with client.ticket_session_factory() as db:
        ticket = LinkedTicket(pleiade_id=101, pleiade_subject="Fuite mémoire cache")
        db.add(ticket)
        db.commit()
        ticket_id = ticket.id

    with TestSession() as db:
        task = Task(title="À lier", status="todo")
        db.add(task)
        db.commit()
        task_id = task.id

    resp = client.post(
        f"/kairos/tasks/{task_id}/edit",
        data={"title": "À lier", "linked_ticket_id": str(ticket_id)},
        follow_redirects=False,
    )
    assert resp.status_code == 303
    with TestSession() as db:
        assert db.get(Task, task_id).linked_ticket_id == ticket_id

    client.post(
        f"/kairos/tasks/{task_id}/edit",
        data={"title": "À lier", "linked_ticket_id": ""},
        follow_redirects=False,
    )
    with TestSession() as db:
        assert db.get(Task, task_id).linked_ticket_id is None


def test_edit_task_ignores_nonexistent_ticket_id(route_client) -> None:
    client, TestSession = route_client
    with TestSession() as db:
        task = Task(title="À lier", status="todo")
        db.add(task)
        db.commit()
        task_id = task.id

    client.post(
        f"/kairos/tasks/{task_id}/edit",
        data={"title": "À lier", "linked_ticket_id": "999999"},
        follow_redirects=False,
    )
    with TestSession() as db:
        assert db.get(Task, task_id).linked_ticket_id is None


def test_linked_ticket_badge_appears_in_rendered_page(route_client) -> None:
    from app.pilotage_link import LinkedTicket

    client, TestSession = route_client
    with client.ticket_session_factory() as db:
        ticket = LinkedTicket(
            pleiade_id=202, pleiade_subject="Correction du parseur CSV",
            gitlab_web_url="https://gitlab.example.com/grp/proj/-/issues/12",
        )
        db.add(ticket)
        db.commit()
        ticket_id = ticket.id

    with TestSession() as db:
        db.add(Task(title="Tâche liée", status="todo", linked_ticket_id=ticket_id))
        db.commit()

    resp = client.get("/kairos")
    assert resp.status_code == 200
    assert "#202" in resp.text
    assert "https://gitlab.example.com/grp/proj/-/issues/12" in resp.text


def test_task_type_and_fibonacci_badges_appear_in_rendered_page(route_client) -> None:
    client, TestSession = route_client
    with TestSession() as db:
        db.add(Task(title="Badge test", status="todo", task_type="pilotage",
                     fibonacci_points=8))
        db.commit()

    resp = client.get("/kairos")
    assert resp.status_code == 200
    assert "Pilotage/dette technique" in resp.text
    assert "8 pts" in resp.text


def test_overdue_task_gets_urgent_bucket_border_class(route_client) -> None:
    """Ergonomie phase 5 : une tâche en retard (bucket 0) est rendue avec une classe
    de bordure distincte, pour la distinguer à l'œil sans lire le texte."""
    from datetime import timedelta as _td

    client, TestSession = route_client
    with TestSession() as db:
        db.add(Task(title="En retard", status="todo", deadline=TODAY - _td(days=2)))
        db.commit()

    resp = client.get("/kairos")
    assert "mj-bucket-0" in resp.text


def test_normal_task_gets_low_urgency_bucket_class(route_client) -> None:
    client, TestSession = route_client
    with TestSession() as db:
        db.add(Task(title="Normale", status="todo"))
        db.commit()

    resp = client.get("/kairos")
    assert "mj-bucket-4" in resp.text
    assert "mj-bucket-0" not in resp.text


def test_priority_badge_shown_when_priority_set(route_client) -> None:
    client, TestSession = route_client
    with TestSession() as db:
        db.add(Task(title="Prioritaire", status="todo", priority=2))
        db.commit()

    resp = client.get("/kairos")
    assert '<span class="badge prio" title="Priorité">P2</span>' in resp.text


def test_wsjf_score_badge_shown(route_client) -> None:
    """Transparence phase 9 : le score WSJF qui ordonne la liste est affiché sur la tâche.
    Une P1 à 1 point Fibonacci → score 8.0 (valeur 8 / effort 1)."""
    client, TestSession = route_client
    with TestSession() as db:
        db.add(Task(title="Rapide et prioritaire", status="todo",
                    priority=1, fibonacci_points=1))
        db.commit()

    resp = client.get("/kairos")
    assert 'class="badge mj-score"' in resp.text
    assert "8.0" in resp.text


def test_fibonacci_estimation_help_present(route_client) -> None:
    """L'aide d'estimation des points (barème Fibonacci) est disponible dans l'édition."""
    client, TestSession = route_client
    with TestSession() as db:
        db.add(Task(title="À éditer", status="todo"))
        db.commit()

    resp = client.get("/kairos")
    assert "Comment estimer les points" in resp.text
    assert "mj-help-scale" in resp.text


def test_stale_task_shows_badge(route_client) -> None:
    """Phase 7 : une tâche en retard bien au-delà du seuil (défaut 7 j) affiche
    un badge « traîne », en plus (pas à la place) de la bordure d'urgence."""
    from datetime import timedelta as _td

    client, TestSession = route_client
    with TestSession() as db:
        db.add(Task(title="Depuis longtemps", status="todo", deadline=TODAY - _td(days=15)))
        db.commit()

    resp = client.get("/kairos")
    assert "traîne depuis 15 j" in resp.text


def test_recently_overdue_task_has_no_stale_badge(route_client) -> None:
    from datetime import timedelta as _td

    client, TestSession = route_client
    with TestSession() as db:
        db.add(Task(title="Récemment en retard", status="todo", deadline=TODAY - _td(days=1)))
        db.commit()

    resp = client.get("/kairos")
    assert "traîne depuis" not in resp.text


def test_priority_overload_banner_shown_beyond_threshold(route_client) -> None:
    """Phase 7 : au-delà du seuil (défaut 5), un bandeau avertit que trop de
    tâches sont à priorité maximale."""
    client, TestSession = route_client
    with TestSession() as db:
        for i in range(6):
            db.add(Task(title=f"Urgente {i}", status="todo", priority=0))
        db.commit()

    resp = client.get("/kairos")
    assert "priorité maximale" in resp.text
    assert "6 tâches" in resp.text


def test_priority_overload_banner_absent_under_threshold(route_client) -> None:
    client, TestSession = route_client
    with TestSession() as db:
        for i in range(3):
            db.add(Task(title=f"Urgente {i}", status="todo", priority=0))
        db.commit()

    resp = client.get("/kairos")
    assert "priorité maximale" not in resp.text


def test_done_section_is_collapsed_by_default(route_client) -> None:
    """« Fait » est repliée par défaut (même idiome que « Programmées plus tard »),
    mais les tâches y restent présentes dans le HTML (dépliable en un clic)."""
    client, TestSession = route_client
    with TestSession() as db:
        db.add(Task(title="Terminée", status="done"))
        db.commit()

    resp = client.get("/kairos")
    assert "Terminée" in resp.text
    assert "Fait (1)" in resp.text
    # Repliée par défaut : un <h3>Fait</h3> nu (sans <details>) ne doit plus exister.
    assert "<h3>Fait</h3>" not in resp.text


def test_done_toggle_marks_and_reopens(route_client) -> None:
    client, TestSession = route_client
    with TestSession() as db:
        task = Task(title="À basculer", status="todo")
        db.add(task)
        db.commit()
        task_id = task.id

    client.post(f"/kairos/tasks/{task_id}/done", follow_redirects=False)
    with TestSession() as db:
        assert db.get(Task, task_id).status == "done"

    client.post(f"/kairos/tasks/{task_id}/done", follow_redirects=False)
    with TestSession() as db:
        assert db.get(Task, task_id).status == "todo"


def test_done_on_recurring_task_creates_next_occurrence(route_client) -> None:
    from datetime import timedelta as _td

    client, TestSession = route_client
    with TestSession() as db:
        task = Task(title="Stand-up", recurrence="daily", deadline=TODAY, status="todo")
        db.add(task)
        db.commit()
        task_id = task.id

    client.post(f"/kairos/tasks/{task_id}/done", follow_redirects=False)

    with TestSession() as db:
        assert db.get(Task, task_id).status == "done"
        occurrence = db.query(Task).filter_by(status="todo").one()
        assert occurrence.title == "Stand-up"
        assert occurrence.deadline == TODAY + _td(days=1)


def test_snooze_moves_deadline_to_next_business_day(route_client) -> None:
    """« Demain » avance au prochain jour ouvré (week-ends et fériés sautés) — la
    route utilise le même réglage `holiday_set` par défaut que la fixture."""
    from datetime import timedelta as _td

    from app.workdays import add_business_days

    client, TestSession = route_client
    expected = add_business_days(TODAY, 1, Settings().holiday_set)
    with TestSession() as db:
        overdue = Task(title="En retard", status="todo", deadline=TODAY - _td(days=3))
        no_deadline = Task(title="Sans échéance", status="todo")
        db.add_all([overdue, no_deadline])
        db.commit()
        overdue_id, no_deadline_id = overdue.id, no_deadline.id

    client.post(f"/kairos/tasks/{overdue_id}/snooze", follow_redirects=False)
    client.post(f"/kairos/tasks/{no_deadline_id}/snooze", follow_redirects=False)

    with TestSession() as db:
        # Une tâche en retard repart d'aujourd'hui, pas de son échéance passée.
        assert db.get(Task, overdue_id).deadline == expected
        assert db.get(Task, no_deadline_id).deadline == expected


def test_pin_and_unpin_via_edit_form(route_client) -> None:
    """Phase 5 : l'épinglage est fusionné dans le formulaire d'édition — un seul
    POST /edit pose l'heure fixe, et un champ vide désépingle."""
    from datetime import datetime as _dt

    client, TestSession = route_client
    with TestSession() as db:
        task = Task(title="À épingler", status="todo")
        db.add(task)
        db.commit()
        task_id = task.id

    resp = client.post(
        f"/kairos/tasks/{task_id}/edit",
        data={"title": "À épingler", "pin_time": "09:30", "pin_day": TODAY.isoformat()},
        follow_redirects=False,
    )
    assert resp.status_code == 303
    with TestSession() as db:
        pinned = db.get(Task, task_id).pinned_start
        assert pinned == _dt.combine(TODAY, _dt.min.time()).replace(hour=9, minute=30)

    client.post(
        f"/kairos/tasks/{task_id}/edit",
        data={"title": "À épingler", "pin_time": ""},
        follow_redirects=False,
    )
    with TestSession() as db:
        assert db.get(Task, task_id).pinned_start is None


def test_edit_with_invalid_pin_time_is_ignored(route_client) -> None:
    client, TestSession = route_client
    with TestSession() as db:
        task = Task(title="À épingler", status="todo")
        db.add(task)
        db.commit()
        task_id = task.id

    resp = client.post(
        f"/kairos/tasks/{task_id}/edit",
        data={"title": "À épingler", "pin_time": "pas-une-heure"},
        follow_redirects=False,
    )
    assert resp.status_code == 303
    with TestSession() as db:
        assert db.get(Task, task_id).pinned_start is None


def test_delete_removes_native_but_archives_imported_task(route_client) -> None:
    client, TestSession = route_client
    with TestSession() as db:
        native = Task(title="Native", status="todo", source="native")
        imported = Task(title="Importée", status="todo", source="gitlab", external_id="gl-9")
        db.add_all([native, imported])
        db.commit()
        native_id, imported_id = native.id, imported.id

    client.post(f"/kairos/tasks/{native_id}/delete", follow_redirects=False)
    client.post(f"/kairos/tasks/{imported_id}/delete", follow_redirects=False)

    with TestSession() as db:
        assert db.get(Task, native_id) is None  # supprimée
        assert db.get(Task, imported_id).status == "archived"  # jamais supprimée


def test_done_tasks_appear_in_done_section(route_client) -> None:
    client, TestSession = route_client
    with TestSession() as db:
        task = Task(title="Tâche accomplie du jour", status="todo")
        db.add(task)
        db.commit()
        task_id = task.id

    client.post(f"/kairos/tasks/{task_id}/done", follow_redirects=False)
    page = client.get("/kairos")
    assert "Fait" in page.text
    assert "Tâche accomplie du jour" in page.text


def test_timer_start_creates_open_session(route_client) -> None:
    from app.tasks_models import WorkSession

    client, TestSession = route_client
    with TestSession() as db:
        t = Task(title="Bosser", status="todo")
        db.add(t)
        db.commit()
        tid = t.id

    resp = client.post(f"/kairos/tasks/{tid}/timer/start", follow_redirects=False)
    assert resp.status_code == 303
    with TestSession() as db:
        sessions = db.query(WorkSession).filter_by(task_id=tid).all()
        assert len(sessions) == 1
        assert sessions[0].ended_at is None


def test_starting_a_timer_stops_the_previous_one(route_client) -> None:
    from app.tasks_models import WorkSession

    client, TestSession = route_client
    with TestSession() as db:
        a = Task(title="A", status="todo")
        b = Task(title="B", status="todo")
        db.add_all([a, b])
        db.commit()
        a_id, b_id = a.id, b.id

    client.post(f"/kairos/tasks/{a_id}/timer/start", follow_redirects=False)
    client.post(f"/kairos/tasks/{b_id}/timer/start", follow_redirects=False)

    with TestSession() as db:
        # Une seule session ouverte : celle de B ; celle de A a été fermée.
        open_sessions = db.query(WorkSession).filter(WorkSession.ended_at.is_(None)).all()
        assert len(open_sessions) == 1
        assert open_sessions[0].task_id == b_id


def test_timer_stop_closes_session(route_client) -> None:
    from app.tasks_models import WorkSession

    client, TestSession = route_client
    with TestSession() as db:
        t = Task(title="Bosser", status="todo")
        db.add(t)
        db.commit()
        tid = t.id

    client.post(f"/kairos/tasks/{tid}/timer/start", follow_redirects=False)
    client.post(f"/kairos/tasks/{tid}/timer/stop", follow_redirects=False)

    with TestSession() as db:
        session = db.query(WorkSession).filter_by(task_id=tid).one()
        assert session.ended_at is not None


def test_marking_done_stops_running_timer(route_client) -> None:
    from app.tasks_models import WorkSession

    client, TestSession = route_client
    with TestSession() as db:
        t = Task(title="Bosser", status="todo")
        db.add(t)
        db.commit()
        tid = t.id

    client.post(f"/kairos/tasks/{tid}/timer/start", follow_redirects=False)
    client.post(f"/kairos/tasks/{tid}/done", follow_redirects=False)

    with TestSession() as db:
        session = db.query(WorkSession).filter_by(task_id=tid).one()
        assert session.ended_at is not None


def test_today_time_total_excludes_sessions_from_other_days(route_client) -> None:
    """Phase 7 : régression du bug où « temps travaillé aujourd'hui » additionnait
    en réalité toutes les sessions jamais enregistrées."""
    from datetime import datetime as _dt, time as _time, timedelta as _td
    from app.tasks_models import WorkSession

    client, TestSession = route_client
    with TestSession() as db:
        task = Task(title="Ancienne tâche", status="todo")
        db.add(task)
        db.commit()
        task_id = task.id
        yesterday_start = _dt.combine(TODAY - _td(days=1), _time(9, 0))
        db.add(WorkSession(
            task_id=task_id, started_at=yesterday_start,
            ended_at=yesterday_start + _td(hours=3),
        ))
        db.commit()

    page = client.get("/kairos")
    # Les 3 h d'hier ne doivent pas apparaître dans le total du jour (format « 3 h »).
    after_label = page.text.split("temps travaillé aujourd'hui")[1][:30]
    assert "3 h" not in after_label
    assert "0 min" in after_label


def test_today_time_breakdown_by_type_shown(route_client) -> None:
    from datetime import datetime as _dt, time as _time, timedelta as _td
    from app.tasks_models import WorkSession

    client, TestSession = route_client
    with TestSession() as db:
        dev_task = Task(title="Coder", status="todo", task_type="dev")
        meeting_task = Task(title="Point", status="todo", task_type="reunion")
        db.add_all([dev_task, meeting_task])
        db.commit()
        today_9am = _dt.combine(TODAY, _time(9, 0))
        db.add(WorkSession(
            task_id=dev_task.id, started_at=today_9am, ended_at=today_9am + _td(minutes=45),
        ))
        db.add(WorkSession(
            task_id=meeting_task.id, started_at=today_9am, ended_at=today_9am + _td(minutes=30),
        ))
        db.commit()

    page = client.get("/kairos")
    assert "Développement 45 min" in page.text
    assert "Réunion 30 min" in page.text


def test_running_timer_shown_on_dashboard(route_client) -> None:
    client, TestSession = route_client
    with TestSession() as db:
        t = Task(title="Tâche chronométrée", status="todo")
        db.add(t)
        db.commit()
        tid = t.id

    client.post(f"/kairos/tasks/{tid}/timer/start", follow_redirects=False)
    page = client.get("/kairos")
    assert "mj-timer" in page.text  # le minuteur vivant est rendu
    assert "temps travaillé aujourd'hui" in page.text


def test_add_dependency_removes_blocked_task_from_agenda(route_client) -> None:
    """Phase 6 : les bloqueurs se posent désormais via /edit (blocker_ids), plus
    de route dédiée /deps."""
    client, TestSession = route_client
    with TestSession() as db:
        a = Task(title="A bloquée", priority=0, status="todo")
        b = Task(title="B bloqueuse", status="todo")
        db.add_all([a, b])
        db.commit()
        a_id, b_id = a.id, b.id

    resp = client.post(
        f"/kairos/tasks/{a_id}/edit",
        data={"title": "A bloquée", "blocker_ids": [str(b_id)]},
        follow_redirects=False,
    )
    assert resp.status_code == 303

    page = client.get("/kairos")
    assert "Bloquées" in page.text
    assert "en attente de : B bloqueuse" in page.text

    # Terminer B débloque A.
    client.post(f"/kairos/tasks/{b_id}/done", follow_redirects=False)
    page2 = client.get("/kairos")
    assert "en attente de : B bloqueuse" not in page2.text


def test_dependency_cycle_is_refused(route_client) -> None:
    from app.tasks_models import TaskDependency

    client, TestSession = route_client
    with TestSession() as db:
        a = Task(title="A", status="todo")
        b = Task(title="B", status="todo")
        db.add_all([a, b])
        db.commit()
        a_id, b_id = a.id, b.id

    # A bloquée par B, puis tentative B bloquée par A (cycle) : ignorée
    # silencieusement, le reste de l'enregistrement réussit quand même.
    client.post(
        f"/kairos/tasks/{a_id}/edit",
        data={"title": "A", "blocker_ids": [str(b_id)]}, follow_redirects=False,
    )
    resp = client.post(
        f"/kairos/tasks/{b_id}/edit",
        data={"title": "B", "priority": "1", "blocker_ids": [str(a_id)]},
        follow_redirects=False,
    )
    assert resp.status_code == 303

    with TestSession() as db:
        edges = [(d.task_id, d.blocker_id) for d in db.query(TaskDependency).all()]
        assert edges == [(a_id, b_id)]  # la seconde arête (cycle) n'a pas été créée
        # Le reste de l'enregistrement (priorité) a bien été appliqué malgré le cycle ignoré.
        assert db.get(Task, b_id).priority == 1


def test_remove_dependency(route_client) -> None:
    from app.tasks_models import TaskDependency

    client, TestSession = route_client
    with TestSession() as db:
        a = Task(title="A", status="todo")
        b = Task(title="B", status="todo")
        db.add_all([a, b])
        db.commit()
        a_id, b_id = a.id, b.id

    client.post(
        f"/kairos/tasks/{a_id}/edit",
        data={"title": "A", "blocker_ids": [str(b_id)]}, follow_redirects=False,
    )
    with TestSession() as db:
        assert db.query(TaskDependency).count() == 1

    # Ne pas soumettre b_id dans blocker_ids retire la dépendance (état cible complet).
    client.post(
        f"/kairos/tasks/{a_id}/edit",
        data={"title": "A"}, follow_redirects=False,
    )

    with TestSession() as db:
        assert db.query(TaskDependency).count() == 0


def test_deleting_task_cleans_up_its_dependencies(route_client) -> None:
    from app.tasks_models import TaskDependency

    client, TestSession = route_client
    with TestSession() as db:
        a = Task(title="A", status="todo", source="native")
        b = Task(title="B", status="todo", source="native")
        db.add_all([a, b])
        db.commit()
        a_id, b_id = a.id, b.id

    client.post(
        f"/kairos/tasks/{a_id}/edit",
        data={"title": "A", "blocker_ids": [str(b_id)]}, follow_redirects=False,
    )
    client.post(f"/kairos/tasks/{b_id}/delete", follow_redirects=False)

    with TestSession() as db:
        assert db.query(TaskDependency).count() == 0  # arête orpheline nettoyée


def test_old_deps_routes_are_gone(route_client) -> None:
    """Les anciennes routes /deps et /deps/{id}/delete ont disparu (fusionnées
    dans /edit, phase 6) : plus aucun lien mort ne doit y pointer."""
    client, TestSession = route_client
    with TestSession() as db:
        task = Task(title="Sans route dédiée", status="todo")
        db.add(task)
        db.commit()
        task_id = task.id

    resp = client.post(
        f"/kairos/tasks/{task_id}/deps", data={"blocker_id": "1"},
        follow_redirects=False,
    )
    assert resp.status_code == 404


def test_edit_creates_multiple_subtasks_and_sets_blockers_in_one_submit(route_client) -> None:
    """Un seul POST /edit crée plusieurs sous-tâches ET pose des bloqueurs, en
    plus des autres infos (ergonomie phase 6)."""
    client, TestSession = route_client
    with TestSession() as db:
        task = Task(title="Mère", status="todo")
        blocker = Task(title="Bloqueur", status="todo")
        db.add_all([task, blocker])
        db.commit()
        task_id, blocker_id = task.id, blocker.id

    resp = client.post(
        f"/kairos/tasks/{task_id}/edit",
        data={
            "title": "Mère", "priority": "2",
            "new_subtasks": "Première étape\n  \nDeuxième étape\n",
            "blocker_ids": [str(blocker_id)],
        },
        follow_redirects=False,
    )
    assert resp.status_code == 303

    with TestSession() as db:
        parent = db.get(Task, task_id)
        assert parent.priority == 2
        subtasks = db.query(Task).filter_by(parent_id=task_id).order_by(Task.id).all()
        assert [t.title for t in subtasks] == ["Première étape", "Deuxième étape"]
        from app.tasks_models import TaskDependency
        dep = db.query(TaskDependency).filter_by(task_id=task_id).one()
        assert dep.blocker_id == blocker_id


def test_blocker_picker_uses_a_select_menu_like_linked_ticket(route_client) -> None:
    """Le sélecteur de bloqueurs est un menu <select multiple>, même widget que
    « Fiche liée » (phase 13) — plus une liste de cases à cocher."""
    client, TestSession = route_client
    with TestSession() as db:
        task = Task(title="Mère", status="todo")
        blocker = Task(title="Bloqueur existant", status="todo")
        db.add_all([task, blocker])
        db.commit()
        blocker_id = blocker.id
        from app.tasks_models import TaskDependency
        db.add(TaskDependency(task_id=task.id, blocker_id=blocker_id))
        db.commit()

    page = client.get("/kairos")
    assert 'name="blocker_ids" multiple' in page.text
    assert f'<option value="{blocker_id}" selected>Bloqueur existant</option>' in page.text
    assert 'type="checkbox" name="blocker_ids"' not in page.text


def test_edit_with_blank_subtask_lines_ignored(route_client) -> None:
    client, TestSession = route_client
    with TestSession() as db:
        task = Task(title="Mère", status="todo")
        db.add(task)
        db.commit()
        task_id = task.id

    client.post(
        f"/kairos/tasks/{task_id}/edit",
        data={"title": "Mère", "new_subtasks": "\n   \n\n"},
        follow_redirects=False,
    )
    with TestSession() as db:
        assert db.query(Task).filter_by(parent_id=task_id).count() == 0


def test_create_subtask_via_parent_id(route_client) -> None:
    client, TestSession = route_client
    with TestSession() as db:
        parent = Task(title="Mère", status="todo")
        db.add(parent)
        db.commit()
        parent_id = parent.id

    client.post(
        "/kairos/tasks",
        data={"title": "Fille", "parent_id": str(parent_id)},
        follow_redirects=False,
    )

    with TestSession() as db:
        child = db.query(Task).filter_by(title="Fille").one()
        assert child.parent_id == parent_id

    page = client.get("/kairos")
    assert "Tâches mères en cours" in page.text
    assert "0/1 sous-tâche(s)" in page.text
    assert "Mère › " in page.text  # fil d'Ariane sur la fille


def test_parent_progress_badge_reflects_done_children(route_client) -> None:
    client, TestSession = route_client
    with TestSession() as db:
        parent = Task(title="Mère", status="todo")
        db.add(parent)
        db.commit()
        db.add_all([
            Task(title="Fille 1", status="done", parent_id=parent.id),
            Task(title="Fille 2", status="todo", parent_id=parent.id),
        ])
        db.commit()

    page = client.get("/kairos")
    assert "1/2 sous-tâche(s)" in page.text


def test_assigned_gitlab_issue_appears_as_task(monkeypatch, route_client) -> None:
    from app import main
    from app.pilotage_link import CachedGitLabIssue

    client, TestSession = route_client
    monkeypatch.setattr(
        main, "get_settings",
        lambda: Settings(
                         gitlab_assignee_username="corentin"),
    )
    with client.ticket_session_factory() as db:
        db.add(CachedGitLabIssue(
            project="mon-groupe/mon-projet", iid=7, title="Corriger le bug",
            state="opened", assignees="corentin",
        ))
        db.commit()

    resp = client.get("/kairos")
    assert resp.status_code == 200
    assert "Corriger le bug" in resp.text

    with TestSession() as db:
        task = db.query(Task).filter_by(source="gitlab").one()
        assert task.external_id == "mon-groupe/mon-projet#7"


def test_assigned_gitlab_issue_from_uncofigured_project_still_appears(monkeypatch, route_client) -> None:
    """Phase 6 : l'auto-import n'est plus borné à `GITLAB_PROJECT` — une issue
    assignée d'un tout autre projet en cache doit apparaître quand même."""
    from app import main
    from app.pilotage_link import CachedGitLabIssue

    client, TestSession = route_client
    monkeypatch.setattr(
        main, "get_settings",
        lambda: Settings(
                         gitlab_assignee_username="corentin"),
    )
    with client.ticket_session_factory() as db:
        db.add(CachedGitLabIssue(
            project="un-autre-groupe/un-autre-projet", iid=3, title="Ailleurs mais assigné",
            state="opened", assignees="corentin",
        ))
        db.commit()

    resp = client.get("/kairos")
    assert resp.status_code == 200
    assert "Ailleurs mais assigné" in resp.text


def test_gitlab_sync_disabled_when_no_assignee_configured(route_client) -> None:
    """Réglage `test_settings` par défaut de la fixture : gitlab_assignee_username=''."""
    from app.pilotage_link import CachedGitLabIssue

    client, TestSession = route_client
    with client.ticket_session_factory() as db:
        db.add(CachedGitLabIssue(
            project="mon-groupe/mon-projet", iid=8, title="Pas assigné",
            state="opened", assignees="quelquun-dautre",
        ))
        db.commit()

    client.get("/kairos")

    with TestSession() as db:
        assert db.query(Task).filter_by(source="gitlab").count() == 0


def test_create_deepwork_block_and_reserve_task(route_client) -> None:
    from app.tasks_models import TimeBlock

    client, TestSession = route_client
    with TestSession() as db:
        db.add(Task(title="Rédaction focus", priority=0, fibonacci_points=1, status="todo",
                    estimated_minutes=30))
        db.commit()

    resp = client.post(
        "/kairos/blocks",
        data={
            "title": "Focus matin",
            "start": f"{TODAY.isoformat()}T09:00",
            "end": f"{TODAY.isoformat()}T11:00",
            "deepwork": "1",
        },
        follow_redirects=False,
    )
    assert resp.status_code == 303

    with TestSession() as db:
        block = db.query(TimeBlock).one()
        assert block.kind == "deepwork"

    page = client.get("/kairos")
    assert "deep work" in page.text  # badge sur la tâche réservée
    assert "deepwork" in page.text   # classe timeline


def test_recurring_daily_block_appears_on_future_day(route_client) -> None:
    """Un bloc déjeuner quotidien créé aujourd'hui se projette aussi les jours
    suivants (phase 13), sans qu'aucune occurrence ne soit persistée en base."""
    from app.tasks_models import TimeBlock

    client, TestSession = route_client
    resp = client.post(
        "/kairos/blocks",
        data={
            "title": "Déjeuner", "start": f"{TODAY.isoformat()}T12:00",
            "end": f"{TODAY.isoformat()}T13:00", "recurrence": "daily",
        },
        follow_redirects=False,
    )
    assert resp.status_code == 303

    with TestSession() as db:
        # Un seul modèle stocké, jamais une ligne par occurrence.
        assert db.query(TimeBlock).count() == 1
        assert db.query(TimeBlock).one().recurrence == "daily"

    future_day = TODAY + timedelta(days=3)
    page = client.get(f"/kairos?view=day&start={future_day.isoformat()}")
    assert page.status_code == 200
    assert "Déjeuner" in page.text
    assert "12h00-13h00" in page.text


def test_non_recurring_block_absent_from_future_day(route_client) -> None:
    client, _ = route_client
    client.post(
        "/kairos/blocks",
        data={
            "title": "Réunion ponctuelle", "start": f"{TODAY.isoformat()}T12:00",
            "end": f"{TODAY.isoformat()}T13:00",
        },
        follow_redirects=False,
    )

    future_day = TODAY + timedelta(days=3)
    page = client.get(f"/kairos?view=day&start={future_day.isoformat()}")
    assert "Réunion ponctuelle" not in page.text


def test_invalid_block_recurrence_is_ignored(route_client) -> None:
    from app.tasks_models import TimeBlock

    client, TestSession = route_client
    client.post(
        "/kairos/blocks",
        data={
            "title": "Test", "start": f"{TODAY.isoformat()}T12:00",
            "end": f"{TODAY.isoformat()}T13:00", "recurrence": "n-importe-quoi",
        },
        follow_redirects=False,
    )
    with TestSession() as db:
        assert db.query(TimeBlock).one().recurrence == ""


def test_edit_manual_block_updates_all_fields(route_client) -> None:
    """Édition d'un créneau : titre, horaires, deep-work et récurrence modifiables."""
    from datetime import datetime as _dt
    from app.tasks_models import TimeBlock

    client, TestSession = route_client
    with TestSession() as db:
        block = TimeBlock(title="Réunion", source="manual", kind="busy",
                          start=_dt.combine(TODAY, _dt.min.time().replace(hour=13)),
                          end=_dt.combine(TODAY, _dt.min.time().replace(hour=14)))
        db.add(block)
        db.commit()
        block_id = block.id

    resp = client.post(
        f"/kairos/blocks/{block_id}/edit",
        data={
            "title": "Réunion budget",
            "start": f"{TODAY.isoformat()}T09:30",
            "end": f"{TODAY.isoformat()}T10:15",
            "deepwork": "1",
            "recurrence": "weekly",
        },
        follow_redirects=False,
    )
    assert resp.status_code == 303
    with TestSession() as db:
        b = db.get(TimeBlock, block_id)
        assert b.title == "Réunion budget"
        assert b.start.hour == 9 and b.start.minute == 30
        assert b.kind == "deepwork"
        assert b.recurrence == "weekly"


def test_edit_block_unchecking_deepwork_makes_it_busy(route_client) -> None:
    from datetime import datetime as _dt
    from app.tasks_models import TimeBlock

    client, TestSession = route_client
    with TestSession() as db:
        block = TimeBlock(title="Focus", source="manual", kind="deepwork",
                          start=_dt.combine(TODAY, _dt.min.time().replace(hour=9)),
                          end=_dt.combine(TODAY, _dt.min.time().replace(hour=11)))
        db.add(block)
        db.commit()
        block_id = block.id

    client.post(
        f"/kairos/blocks/{block_id}/edit",
        data={"title": "Focus", "start": f"{TODAY.isoformat()}T09:00",
              "end": f"{TODAY.isoformat()}T11:00"},  # pas de deepwork coché
        follow_redirects=False,
    )
    with TestSession() as db:
        assert db.get(TimeBlock, block_id).kind == "busy"


def test_edit_block_rejects_invalid_hours(route_client) -> None:
    """Fin <= début : l'édition est ignorée, le créneau reste inchangé."""
    from datetime import datetime as _dt
    from app.tasks_models import TimeBlock

    client, TestSession = route_client
    with TestSession() as db:
        block = TimeBlock(title="Réunion", source="manual", kind="busy",
                          start=_dt.combine(TODAY, _dt.min.time().replace(hour=13)),
                          end=_dt.combine(TODAY, _dt.min.time().replace(hour=14)))
        db.add(block)
        db.commit()
        block_id = block.id

    client.post(
        f"/kairos/blocks/{block_id}/edit",
        data={"title": "Cassé", "start": f"{TODAY.isoformat()}T15:00",
              "end": f"{TODAY.isoformat()}T14:00"},
        follow_redirects=False,
    )
    with TestSession() as db:
        assert db.get(TimeBlock, block_id).title == "Réunion"  # inchangé


def test_delete_manual_block(route_client) -> None:
    from datetime import datetime as _dt
    from app.tasks_models import TimeBlock

    client, TestSession = route_client
    with TestSession() as db:
        block = TimeBlock(title="À supprimer", source="manual",
                          start=_dt.combine(TODAY, _dt.min.time().replace(hour=13)),
                          end=_dt.combine(TODAY, _dt.min.time().replace(hour=14)))
        db.add(block)
        db.commit()
        block_id = block.id

    resp = client.post(f"/kairos/blocks/{block_id}/delete", follow_redirects=False)
    assert resp.status_code == 303
    with TestSession() as db:
        assert db.get(TimeBlock, block_id) is None


def test_recurring_block_is_editable_and_shows_edit_form(route_client) -> None:
    """Un créneau récurrent apparaît dans la liste éditable avec son formulaire (le
    modèle est éditable depuis n'importe quel jour où il tombe)."""
    from datetime import datetime as _dt
    from app.tasks_models import TimeBlock

    client, TestSession = route_client
    with TestSession() as db:
        block = TimeBlock(title="Déjeuner", source="manual", recurrence="daily",
                          start=_dt.combine(TODAY, _dt.min.time().replace(hour=12)),
                          end=_dt.combine(TODAY, _dt.min.time().replace(hour=13)))
        db.add(block)
        db.commit()
        block_id = block.id

    page = client.get("/kairos")
    assert f'action="/kairos/blocks/{block_id}/edit"' in page.text
    assert "quotidien" in page.text  # badge de récurrence


def test_all_day_timetree_event_shows_as_chip_and_does_not_block_day(monkeypatch, route_client) -> None:
    """Un événement journée entière TimeTree ne bloque pas la planification : il
    apparaît en puce, et les tâches restent planifiables normalement."""
    from datetime import datetime as _dt

    from app.calendar.timetree_source import BusySlot, TimeTreeFetchResult

    client, TestSession = route_client
    midnight = _dt.combine(TODAY, _dt.min.time())
    monkeypatch.setattr(
        main, "fetch_busy_slots",
        lambda start, end, *, settings: TimeTreeFetchResult(
            ok=True,
            blocks=[BusySlot(title="Anniversaire", start=midnight,
                             end=midnight + timedelta(days=1), all_day=True)],
        ),
    )
    with TestSession() as db:
        db.add(Task(title="Tâche planifiable", priority=0, status="todo", estimated_minutes=30))
        db.commit()

    page = client.get("/kairos")
    assert page.status_code == 200
    assert "Anniversaire" in page.text  # la puce est là
    # La journée n'est PAS considérée occupée : le temps disponible n'est pas nul à
    # cause de l'événement (il l'est seulement si l'heure réelle est hors fenêtre),
    # et surtout aucune entrée « busy » pleine journée dans la timeline.
    assert 'mj-tl-entry busy" style="top: 0px; height: 540px' not in page.text


def test_multi_day_timetree_event_shows_as_indication_and_does_not_block_day(
    monkeypatch, route_client
) -> None:
    """Un événement TimeTree « sur une période » (plusieurs jours, horaires réels —
    ex. un déplacement) n'est qu'une indication : il ne mange plus la journée entière
    sur chaque jour couvert, et les tâches restent planifiables normalement (phase 12)."""
    from datetime import datetime as _dt

    from app.calendar.timetree_source import BusySlot, TimeTreeFetchResult

    client, TestSession = route_client
    monkeypatch.setattr(
        main, "fetch_busy_slots",
        lambda start, end, *, settings: TimeTreeFetchResult(
            ok=True,
            blocks=[BusySlot(
                title="Déplacement Lyon",
                start=_dt.combine(TODAY - timedelta(days=1), _dt.min.time().replace(hour=14)),
                end=_dt.combine(TODAY + timedelta(days=1), _dt.min.time().replace(hour=10)),
                all_day=False,
            )],
        ),
    )
    with TestSession() as db:
        db.add(Task(title="Tâche du jour", priority=0, fibonacci_points=1, status="todo",
                    estimated_minutes=30))
        db.commit()

    page = client.get("/kairos")
    assert page.status_code == 200
    assert "Déplacement Lyon" in page.text  # indication présente
    # Aucune entrée « busy » pleine journée (9h-18h = 540 min) dans la timeline.
    assert 'mj-tl-entry busy" style="top: 0px; height: 540px' not in page.text
    # La tâche du jour reste planifiée normalement, pas écartée par un faux obstacle.
    assert "Tâche du jour" in page.text
    assert 'class="kairos-item mj-bucket' in page.text


def test_day_view_shows_timeline_and_progress_header(route_client) -> None:
    client, TestSession = route_client
    with TestSession() as db:
        db.add(Task(title="Tâche du jour", priority=0, fibonacci_points=1, status="todo",
                    estimated_minutes=45))
        db.commit()

    page = client.get("/kairos")
    assert page.status_code == 200
    assert "Agenda" in page.text
    assert "mj-timeline" in page.text
    assert "À faire maintenant" in page.text
    assert "requis" in page.text and "disponible" in page.text


def test_day_view_warns_when_day_overflows(route_client) -> None:
    client, TestSession = route_client
    with TestSession() as db:
        # 2 × 8 h de travail estimé : déborde forcément d'une journée 9h-18h.
        db.add(Task(title="Énorme 1", status="todo", priority=2, fibonacci_points=13,
                    estimated_minutes=480))
        db.add(Task(title="Énorme 2", status="todo", priority=2, fibonacci_points=13,
                    estimated_minutes=480))
        db.commit()

    page = client.get("/kairos")
    assert "déborde" in page.text


def test_week_view_returns_200_and_shows_seven_days(route_client) -> None:
    client, _ = route_client
    resp = client.get("/kairos?view=week")
    assert resp.status_code == 200
    assert "Semaine du" in resp.text
    monday = TODAY - timedelta(days=TODAY.weekday())
    for i in range(7):
        day = monday + timedelta(days=i)
        assert day.strftime("%d/%m") in resp.text


def test_week_view_shows_no_aggregate_when_no_sessions(route_client) -> None:
    client, _ = route_client
    resp = client.get("/kairos?view=week")
    assert "Temps réel cette semaine" not in resp.text


def test_week_view_shows_time_aggregate_by_type(route_client) -> None:
    """Phase 7 : synthèse hebdomadaire du temps réel par type, sans graphique,
    à partir des données déjà collectées (WorkSession + task_type)."""
    from datetime import datetime as _dt, time as _time, timedelta as _td
    from app.tasks_models import WorkSession

    client, TestSession = route_client
    monday = TODAY - timedelta(days=TODAY.weekday())
    with TestSession() as db:
        dev_task = Task(title="Coder", status="todo", task_type="dev")
        db.add(dev_task)
        db.commit()
        session_start = _dt.combine(monday, _time(9, 0))
        db.add(WorkSession(
            task_id=dev_task.id, started_at=session_start,
            ended_at=session_start + _td(hours=2),
        ))
        db.commit()

    resp = client.get("/kairos?view=week")
    assert "Temps réel cette semaine" in resp.text
    assert "Développement 120 min" in resp.text


def test_week_view_places_task_and_block_on_correct_day(route_client) -> None:
    client, TestSession = route_client
    monday = TODAY - timedelta(days=TODAY.weekday())
    wednesday = monday + timedelta(days=2)
    with TestSession() as db:
        db.add(Task(title="Tâche mercredi", deadline=wednesday, status="todo"))
        db.commit()

    client.post(
        "/kairos/blocks",
        data={
            "title": "Point mercredi",
            "start": f"{wednesday.isoformat()}T10:00",
            "end": f"{wednesday.isoformat()}T10:30",
        },
        follow_redirects=False,
    )

    page = client.get("/kairos?view=week")
    assert page.status_code == 200
    assert "Tâche mercredi" in page.text
    assert "Point mercredi" in page.text


def test_day_view_link_toggles_to_week_view(route_client) -> None:
    client, _ = route_client
    resp = client.get("/kairos")
    assert 'href="/kairos?view=week"' in resp.text


def test_quick_create_with_scheduled_date(route_client) -> None:
    client, TestSession = route_client
    monday = TODAY + timedelta(days=4)
    resp = client.post(
        "/kairos/tasks",
        data={"title": "Pour lundi", "scheduled_date": monday.isoformat()},
        follow_redirects=False,
    )
    assert resp.status_code == 303

    with TestSession() as db:
        task = db.query(Task).one()
        assert task.scheduled_date == monday


def test_edit_task_updates_scheduled_date(route_client) -> None:
    client, TestSession = route_client
    with TestSession() as db:
        task = Task(title="À reprogrammer", status="todo")
        db.add(task)
        db.commit()
        task_id = task.id

    monday = TODAY + timedelta(days=4)
    resp = client.post(
        f"/kairos/tasks/{task_id}/edit",
        data={
            "title": "À reprogrammer", "priority": "", "deadline": "",
            "project_tag": "", "estimated_minutes": "", "recurrence": "",
            "scheduled_date": monday.isoformat(),
        },
        follow_redirects=False,
    )
    assert resp.status_code == 303
    with TestSession() as db:
        task = db.get(Task, task_id)
        assert task.scheduled_date == monday


def test_task_scheduled_for_future_is_hidden_and_listed_later(route_client) -> None:
    client, TestSession = route_client
    monday = TODAY + timedelta(days=4)
    with TestSession() as db:
        db.add(Task(title="Programmée lundi", status="todo", priority=2, fibonacci_points=1,
                    scheduled_date=monday))
        db.commit()

    resp = client.get("/kairos")
    assert resp.status_code == 200
    assert "Programmées plus tard" in resp.text
    assert "Programmée lundi" in resp.text


def test_task_scheduled_for_future_with_deadline_today_stays_in_agenda(route_client) -> None:
    client, TestSession = route_client
    monday = TODAY + timedelta(days=4)
    with TestSession() as db:
        db.add(Task(title="Urgente malgré tout", status="todo", priority=2, fibonacci_points=1,
                     scheduled_date=monday, deadline=TODAY))
        db.commit()

    resp = client.get("/kairos")
    assert resp.status_code == 200
    assert "Urgente malgré tout" in resp.text
    assert "Programmées plus tard" not in resp.text


def test_no_later_section_when_nothing_is_deferred(route_client) -> None:
    client, TestSession = route_client
    with TestSession() as db:
        db.add(Task(title="Normale", status="todo"))
        db.commit()

    resp = client.get("/kairos")
    assert "Programmées plus tard" not in resp.text


def test_edit_task_sets_monthly_on_day_recurrence(route_client) -> None:
    client, TestSession = route_client
    with TestSession() as db:
        task = Task(title="Cotisation", status="todo")
        db.add(task)
        db.commit()
        task_id = task.id

    resp = client.post(
        f"/kairos/tasks/{task_id}/edit",
        data={
            "title": "Cotisation", "priority": "", "deadline": "", "project_tag": "",
            "estimated_minutes": "", "recurrence": "monthly_on_day",
            "recurrence_day_of_month": "23",
        },
        follow_redirects=False,
    )
    assert resp.status_code == 303
    with TestSession() as db:
        task = db.get(Task, task_id)
        assert task.recurrence == "monthly_on_day"
        assert task.recurrence_day_of_month == 23


def test_manually_entered_saturday_date_is_never_auto_corrected(route_client) -> None:
    """Une date saisie à la main un samedi n'est jamais recorrigée automatiquement
    (le décalage jour ouvré ne s'applique qu'à la récurrence calendaire et au
    snooze, jamais à une saisie explicite de l'utilisateur)."""
    saturday = date(2026, 7, 4)
    client, TestSession = route_client
    resp = client.post(
        "/kairos/tasks",
        data={
            "title": "RDV perso", "deadline": saturday.isoformat(),
            "scheduled_date": saturday.isoformat(),
        },
        follow_redirects=False,
    )
    assert resp.status_code == 303
    with TestSession() as db:
        task = db.query(Task).one()
        assert task.deadline == saturday
        assert task.scheduled_date == saturday
        task_id = task.id

    client.post(
        f"/kairos/tasks/{task_id}/edit",
        data={
            "title": "RDV perso", "priority": "", "deadline": saturday.isoformat(),
            "scheduled_date": saturday.isoformat(), "project_tag": "",
            "estimated_minutes": "", "recurrence": "",
        },
        follow_redirects=False,
    )
    with TestSession() as db:
        task = db.get(Task, task_id)
        assert task.deadline == saturday
        assert task.scheduled_date == saturday


def test_calendar_recurrence_is_generated_on_agenda_load(route_client) -> None:
    """Visiter « Kairos » génère l'occurrence calendaire du mois manquante."""
    client, TestSession = route_client
    with TestSession() as db:
        db.add(Task(title="Suivi mensuel", recurrence="monthly_on_day",
                     recurrence_day_of_month=1, status="done"))
        db.commit()

    resp = client.get("/kairos")
    assert resp.status_code == 200

    with TestSession() as db:
        generated = db.query(Task).filter_by(
            title="Suivi mensuel", status="todo"
        ).one_or_none()
        assert generated is not None
        assert generated.recurrence == "monthly_on_day"


def test_stats_page_renders_empty_state(route_client) -> None:
    """Sans données, la page de stats répond 200 avec un état vide explicite."""
    client, _ = route_client
    resp = client.get("/kairos/stats")
    assert resp.status_code == 200
    assert "Aucune donnée à analyser" in resp.text


def test_stats_page_shows_indicators_with_data(route_client) -> None:
    """Avec une tâche terminée chronométrée, les panneaux d'indicateurs apparaissent."""
    from datetime import datetime as _dt, timezone as _tz, timedelta as _td
    from app.tasks_models import WorkSession

    client, TestSession = route_client
    now = _dt.now(_tz.utc)
    with TestSession() as db:
        task = Task(title="Faite", status="done", fibonacci_points=3,
                    estimated_minutes=60, task_type="dev")
        db.add(task)
        db.commit()
        tid = task.id
        db.add(WorkSession(task_id=tid, started_at=now - _td(minutes=75), ended_at=now))
        db.commit()

    resp = client.get("/kairos/stats")
    assert resp.status_code == 200
    assert "Calibration de l'estimation" in resp.text
    assert "Débit hebdomadaire" in resp.text
    assert "Biais estimé" in resp.text  # 75 min réel pour 60 estimé


def test_day_view_has_timer_alert_optin(route_client) -> None:
    """La vue jour expose le bouton d'opt-in aux alertes chrono et sa config de seuils."""
    client, _ = route_client
    resp = client.get("/kairos?view=day")
    assert 'id="mj-alert-config"' in resp.text
    assert "Activer les alertes chrono" in resp.text
    assert 'data-idle=' in resp.text and 'data-pomodoro=' in resp.text


def test_running_timer_badge_carries_estimate_and_title(route_client) -> None:
    """Le badge du chrono en cours porte l'estimé et le titre (nourrissent les alertes)."""
    from datetime import datetime as _dt, timezone as _tz, timedelta as _td
    from app.tasks_models import WorkSession

    client, TestSession = route_client
    with TestSession() as db:
        t = Task(title="Rédaction", status="todo", estimated_minutes=30)
        db.add(t)
        db.commit()
        tid = t.id
        db.add(WorkSession(task_id=tid, started_at=_dt.now(_tz.utc) - _td(minutes=10)))
        db.commit()

    resp = client.get("/kairos?view=day")
    assert 'class="badge info mj-timer"' in resp.text
    assert 'data-estimate="30"' in resp.text
    assert 'data-title="Rédaction"' in resp.text


def test_real_session_rail_shown_on_timeline(route_client) -> None:
    """Une session chronométrée le matin apparaît comme rail « réel » sur la timeline."""
    from datetime import datetime as _dt, timezone as _tz
    from app.tasks_models import WorkSession

    def _utc_morning(h, m):
        return _dt(TODAY.year, TODAY.month, TODAY.day, h, m).astimezone(_tz.utc)

    client, TestSession = route_client
    with TestSession() as db:
        t = Task(title="Travail matinal", status="todo")
        db.add(t)
        db.commit()
        db.add(WorkSession(task_id=t.id, started_at=_utc_morning(9, 30),
                           ended_at=_utc_morning(10, 15)))
        db.commit()

    resp = client.get("/kairos?view=day")
    assert "mj-tl-session" in resp.text
    assert "Le rail à gauche" in resp.text


def test_new_unqualified_task_appears_in_a_traiter_section(route_client) -> None:
    """Une nouvelle tâche créée sans priorité ni points Fibo (le formulaire de
    création rapide n'a pas de champ points) atterrit en « À traiter », pas dans
    l'agenda ordonné automatiquement."""
    client, _ = route_client

    resp = client.post(
        "/kairos/tasks",
        data={"title": "Nouvelle tâche capturée"},
        follow_redirects=False,
    )
    assert resp.status_code == 303

    page = client.get("/kairos")
    assert "À traiter" in page.text
    assert "Nouvelle tâche capturée" in page.text
    assert "priorité et points manquants" in page.text


def test_a_traiter_section_absent_when_everything_qualified(route_client) -> None:
    client, TestSession = route_client
    with TestSession() as db:
        db.add(Task(title="Déjà clarifiée", status="todo", priority=1, fibonacci_points=3))
        db.commit()

    resp = client.get("/kairos")
    assert "mj-to-process" not in resp.text


def test_qualifying_task_via_edit_removes_it_from_a_traiter(route_client) -> None:
    """Renseigner priorité + points via l'édition fait sortir la tâche de « À
    traiter » et la fait entrer dans l'agenda ordonné."""
    client, TestSession = route_client
    with TestSession() as db:
        task = Task(title="À qualifier", status="todo")
        db.add(task)
        db.commit()
        task_id = task.id

    before = client.get("/kairos")
    assert "mj-to-process" in before.text

    client.post(
        f"/kairos/tasks/{task_id}/edit",
        data={"title": "À qualifier", "priority": "1", "fibonacci_points": "3"},
        follow_redirects=False,
    )

    after = client.get("/kairos")
    assert "mj-to-process" not in after.text
    assert '<span class="badge mj-score"' in after.text


def test_only_priority_still_lands_in_a_traiter(route_client) -> None:
    client, TestSession = route_client
    with TestSession() as db:
        db.add(Task(title="Priorité seule", status="todo", priority=0))
        db.commit()

    resp = client.get("/kairos")
    assert "priorité manquante" not in resp.text  # priorité posée
    assert "points manquants" in resp.text
