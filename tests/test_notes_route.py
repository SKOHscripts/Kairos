"""Tests de la page Notes (capture GTD) : rendu, création, conversion, archivage,
suppression, contrat AJAX — voir docs/spec/notes-capture.md."""

from __future__ import annotations

import pytest
from sqlalchemy import create_engine, select
from sqlalchemy.orm import sessionmaker
from sqlalchemy.pool import StaticPool
from starlette.testclient import TestClient

from app import main
from app.config import Settings
from app.pilotage_link import PilotageBase
from app.tasks_models import Note, Task, TasksBase


@pytest.fixture
def route_client(monkeypatch):
    """Même patron que `tests/test_kairos_route.py::route_client` (bases en
    mémoire, monkeypatch des fabriques de session) — la page Notes ne dépend que
    de la base tâches, mais le fixture partagé ouvre aussi la base pilotage pour
    rester réutilisable telle quelle par d'éventuels tests croisés."""
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
    monkeypatch.setattr(main, "get_tasks_session", override_tasks_session)
    monkeypatch.setattr(main, "get_pilotage_session", override_session)
    client = TestClient(main.app)
    yield client, TestSession


def test_get_notes_returns_200_with_empty_state(route_client) -> None:
    client, _ = route_client
    resp = client.get("/kairos/notes")
    assert resp.status_code == 200
    assert "Notes" in resp.text
    assert "Rien en attente" in resp.text


def test_post_note_creates_and_shows_it(route_client) -> None:
    client, TestSession = route_client
    resp = client.post(
        "/kairos/notes", data={"body": "Une idée à développer"}, follow_redirects=False
    )
    assert resp.status_code == 303
    with TestSession() as db:
        notes = list(db.scalars(select(Note)))
    assert len(notes) == 1
    assert notes[0].body == "Une idée à développer"
    assert notes[0].status == "open"

    page = client.get("/kairos/notes")
    assert "Une idée à développer" in page.text


def test_post_note_blank_body_is_ignored(route_client) -> None:
    client, TestSession = route_client
    resp = client.post("/kairos/notes", data={"body": "   "}, follow_redirects=False)
    assert resp.status_code == 303
    with TestSession() as db:
        assert list(db.scalars(select(Note))) == []


def test_convert_note_creates_task_archives_note_and_appears_in_inbox(route_client) -> None:
    client, TestSession = route_client
    with TestSession() as db:
        note = Note(body="Premier titre\nDeuxième ligne ignorée")
        db.add(note)
        db.commit()
        note_id = note.id

    resp = client.post(f"/kairos/notes/{note_id}/convert", follow_redirects=False)
    assert resp.status_code == 303

    with TestSession() as db:
        refreshed = db.get(Note, note_id)
        assert refreshed.status == "archived"
        assert refreshed.converted_task_id is not None
        task = db.get(Task, refreshed.converted_task_id)
        assert task is not None
        assert task.title == "Premier titre"
        assert task.source == "native"
        # Titre seul : ni priorité, ni points renseignés à la conversion.
        assert task.priority is None
        assert task.fibonacci_points is None

    # La tâche créée est titre-seul, donc dans la boîte de réception de « Jour ».
    day_page = client.get("/kairos")
    assert "Premier titre" in day_page.text


def test_edit_note_updates_body(route_client) -> None:
    client, TestSession = route_client
    with TestSession() as db:
        note = Note(body="Texte original")
        db.add(note)
        db.commit()
        note_id = note.id

    resp = client.post(
        f"/kairos/notes/{note_id}/edit", data={"body": "Texte corrigé"}, follow_redirects=False
    )
    assert resp.status_code == 303
    with TestSession() as db:
        assert db.get(Note, note_id).body == "Texte corrigé"

    page = client.get("/kairos/notes")
    assert "Texte corrigé" in page.text
    assert "Texte original" not in page.text


def test_edit_missing_note_is_a_no_op(route_client) -> None:
    client, _ = route_client
    resp = client.post(
        "/kairos/notes/999/edit", data={"body": "Peu importe"}, follow_redirects=False
    )
    assert resp.status_code == 303


def test_convert_missing_note_is_a_no_op(route_client) -> None:
    client, _ = route_client
    resp = client.post("/kairos/notes/999/convert", follow_redirects=False)
    assert resp.status_code == 303


def test_archive_note(route_client) -> None:
    client, TestSession = route_client
    with TestSession() as db:
        note = Note(body="À classer sans suite")
        db.add(note)
        db.commit()
        note_id = note.id

    resp = client.post(f"/kairos/notes/{note_id}/archive", follow_redirects=False)
    assert resp.status_code == 303
    with TestSession() as db:
        assert db.get(Note, note_id).status == "archived"

    page = client.get("/kairos/notes")
    assert "Traité / archivé (1)" in page.text
    assert "À classer sans suite" in page.text


def test_delete_note(route_client) -> None:
    client, TestSession = route_client
    with TestSession() as db:
        note = Note(body="À jeter")
        db.add(note)
        db.commit()
        note_id = note.id

    resp = client.post(f"/kairos/notes/{note_id}/delete", follow_redirects=False)
    assert resp.status_code == 303
    with TestSession() as db:
        assert db.get(Note, note_id) is None


def test_ajax_fragment_returns_partial_not_full_page(route_client) -> None:
    resp_client, _ = route_client
    resp = resp_client.post(
        "/kairos/notes",
        data={"body": "Capturée en AJAX"},
        headers={"X-Requested-With": "fetch"},
        follow_redirects=False,
    )
    assert resp.status_code == 200
    assert "Capturée en AJAX" in resp.text
    assert "<!DOCTYPE html>" not in resp.text
    assert "topnav" not in resp.text
    assert 'id="mj-notes-content"' not in resp.text  # posé uniquement par notes.html


def test_non_ajax_post_redirects_with_303(route_client) -> None:
    client, _ = route_client
    resp = client.post(
        "/kairos/notes", data={"body": "Sans en-tête AJAX"}, follow_redirects=False
    )
    assert resp.status_code == 303
    assert resp.headers["location"] == "/kairos/notes"


def test_notes_nav_item_active_only_on_notes_page(route_client) -> None:
    client, _ = route_client
    notes_page = client.get("/kairos/notes")
    assert 'class="tn-item active" href="/kairos/notes"' in notes_page.text

    day_page = client.get("/kairos")
    assert 'class="tn-item active" href="/kairos/notes"' not in day_page.text
    assert 'href="/kairos/notes"' in day_page.text  # l'entrée existe, non active
