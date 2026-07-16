"""Application web Starlette « Kairos » : tâches, time blocking, deep work, stats.

Kairos (καιρός) : en grec, *le moment opportun* — c'est le métier de l'outil, poser
chaque tâche au bon créneau. Nom de code : **14h55**, le creux post-déjeuner, l'heure
la moins productive de la journée — l'exact opposé, en clin d'œil.

Outil autonome (extrait de `pilotage-pleiade-gitlab`, phase 14). L'intégration avec
l'outil de pilotage MSI est optionnelle et en lecture seule (voir `pilotage_link.py`).

Starlette pur depuis le portage Android (auparavant FastAPI, qui n'était utilisé que
pour les décorateurs de routage, `Form` et `Depends`) : FastAPI dépend de Pydantic v2,
donc de `pydantic-core` — un module Rust sans wheel Android (voir
`docs/ANDROID_PACKAGING.md` et `app/settings_fields.py`). Les réponses, fichiers
statiques et gabarits Jinja2 étaient déjà ceux de Starlette, ré-exportés par FastAPI.
"""

from __future__ import annotations

import logging
import os
import signal
import sys
from contextlib import asynccontextmanager, contextmanager
from datetime import date, datetime, time as dt_time, timedelta, timezone
from pathlib import Path

import markdown
from sqlalchemy import select
from sqlalchemy.orm import Session
from starlette.applications import Starlette
from starlette.requests import Request
from starlette.responses import FileResponse, HTMLResponse, RedirectResponse, Response
from starlette.staticfiles import StaticFiles
from starlette.templating import Jinja2Templates

from . import secret_store, settings_store
from .calendar.timetree_source import fetch_busy_slots
from .config import Settings, apply_proxy_env, get_settings, invalidate_settings_cache
from .gitlab_direct import fetch_assigned_issues
from .pilotage_link import CachedGitLabIssue, LinkedTicket, get_pilotage_session
from .settings_fields import SettingsValidationError
from .settings_sections import FIELD_LABELS, RESTART_REQUIRED_FIELDS, SECRET_FIELDS, SECTIONS
from .tasks_db import get_tasks_session, init_tasks_db
from .tasks_dependencies import (
    blocked_task_ids,
    blocking_reason,
    derived_urgency,
    would_create_cycle,
)
from .tasks_models import (
    FIBONACCI_SCALE,
    Task,
    TaskDependency,
    TimeBlock,
    WorkSession,
)
from .tasks_recurrence import (
    BLOCK_RECURRENCE_RULES,
    ensure_calendar_occurrences,
    expand_recurring_blocks,
    next_snooze_date,
    spawn_next_occurrence,
)
from .tasks_scheduling import (
    build_day_schedule,
    build_timeline,
    count_max_priority_tasks,
    session_timeline_entries,
    urgency_bucket,
    urgency_key,
    wsjf_score,
)
from .tasks_staleness import days_stale
from .tasks_stats import calibration_by_type, compute_dashboard_stats, fibonacci_calibration
from .tasks_gitlab_sync import sync_assigned_gitlab_tasks, write_sync_meta
from .tasks_time import (
    running_session,
    sessions_in_range,
    sessions_on_day,
    spent_minutes_by_task,
    spent_minutes_by_type,
    total_minutes,
)

logger = logging.getLogger("kairos")

def _resolve_base_dir() -> Path:
    """Dossier contenant `templates/`, `static/`, `README.md`.

    Dans un exécutable PyInstaller (onefile), ces fichiers sont extraits dans un
    dossier temporaire (`sys._MEIPASS`), pas à côté de ce fichier source. Sur
    Android (Chaquopy), ils sont extraits de l'APK vers un dossier désigné par
    ``KAIROS_BASE_DIR`` (posé par l'amorce avant l'import de ce module — voir
    `android/app/src/main/python/kairos_boot.py`)."""
    override = os.environ.get("KAIROS_BASE_DIR")
    if override:
        return Path(override)
    if getattr(sys, "frozen", False):
        return Path(getattr(sys, "_MEIPASS", Path(sys.executable).resolve().parent))
    return Path(__file__).resolve().parent.parent


BASE_DIR = _resolve_base_dir()


def _optional_int(value: str | None) -> int | None:
    """Convertit une valeur de formulaire en entier, vide → None."""
    if value is None or not str(value).strip():
        return None
    return int(value)


def _matches_query(task: Task, q: str) -> bool:
    """Recherche par mot-clé (issue #15.4) : sous-chaîne insensible à la casse sur le
    titre, la description ou le projet. Filtre d'affichage seul — ne touche jamais à
    l'ordonnancement (voir `_build_kairos_context`)."""
    if not q:
        return True
    haystack = " ".join(
        filter(None, [task.title, task.description, task.project_tag])
    ).lower()
    return q.lower() in haystack


def _matches_filters(
    task: Task, *, priority: int | None, project: str | None,
    task_type: str | None, fibonacci_points: int | None,
) -> bool:
    """Filtre à facettes (issue #15.4) : ne masque jamais, ne réordonne jamais — montre
    seulement les tâches correspondant à la priorité/au projet/au type/aux points de
    Fibonacci sélectionnés."""
    if priority is not None and task.priority != priority:
        return False
    if project and task.project_tag != project:
        return False
    if task_type and task.task_type != task_type:
        return False
    if fibonacci_points is not None and task.fibonacci_points != fibonacci_points:
        return False
    return True


@asynccontextmanager
async def lifespan(_: Starlette):
    settings = get_settings()  # première lecture : déclenche la migration .env si besoin
    logging.basicConfig(level=settings.log_level.upper())
    apply_proxy_env(settings)
    init_tasks_db()
    yield


class _RoutedApp(Starlette):
    """Starlette + décorateurs `@app.get(path)` / `@app.post(path)` — la seule
    surface de routage que le projet utilisait de FastAPI, conservée telle quelle
    pour que les déclarations de routes ne changent pas. Un endpoint reçoit la
    ``Request`` seule ; paramètres de chemin via ``request.path_params`` (converti
    par le chemin, ex. ``{task_id:int}``), de requête via ``request.query_params``,
    de formulaire via ``await request.form()``."""

    def get(self, path: str):
        return self._register(path, ["GET"])

    def post(self, path: str):
        return self._register(path, ["POST"])

    def _register(self, path: str, methods: list[str]):
        def decorator(endpoint):
            self.router.add_route(path, endpoint, methods=methods)
            return endpoint

        return decorator


app = _RoutedApp(lifespan=lifespan)
app.mount("/static", StaticFiles(directory=str(BASE_DIR / "static")), name="static")


@contextmanager
def _request_session(dependency):
    """Session par requête depuis un générateur de dépendance (`get_tasks_session`,
    `get_pilotage_session`) : remplace le `Depends` de FastAPI. Les routes résolvent
    le nom au moment de l'appel (variable globale du module) — les tests substituent
    donc la fabrique en monkeypatchant `main.get_tasks_session`/`main.get_pilotage_session`,
    à la place de l'ancien `app.dependency_overrides`."""
    generator = dependency()
    value = next(generator)
    try:
        yield value
    finally:
        generator.close()

templates = Jinja2Templates(directory=str(BASE_DIR / "templates"))
# Exécutable de bureau (PyInstaller, `console=False`) : pas de fenêtre de terminal à
# fermer pour arrêter le serveur — voir le bouton « Quitter » de base.html, affiché
# seulement dans ce cas (sinon, en dev/systemd, Ctrl+C / `systemctl stop` suffisent).
templates.env.globals["is_frozen"] = getattr(sys, "frozen", False)
# Bottom nav mobile (base.html) : affichée uniquement dans l'APK Android, jamais sur
# un navigateur desktop rétréci — `KAIROS_PLATFORM=android` est posé par
# `kairos_boot.py` avant tout import de ce module (voir docs/ANDROID_PACKAGING.md),
# donc lu une seule fois ici, au même titre que `is_frozen`.
templates.env.globals["is_android"] = os.environ.get("KAIROS_PLATFORM") == "android"
# Anti-cache navigateur : suffixe `?v=` sur les liens vers static/ dans base.html.
# Sans lui, un navigateur peut continuer à servir un vieux style.css en cache après
# une mise à jour de l'app (nouvelle version installée, `git pull`...), ce qui donne
# une interface à moitié stylée. L'horodatage du fichier suffit : il change dès que
# style.css change (mise à jour de l'app), et reste stable entre deux requêtes d'un
# même lancement sinon (cache normal conservé le reste du temps).
try:
    _STYLE_MTIME = int((BASE_DIR / "static" / "style.css").stat().st_mtime)
except OSError:
    _STYLE_MTIME = 0
templates.env.globals["asset_version"] = _STYLE_MTIME


@app.get("/favicon.ico")
def favicon(request: Request) -> Response:
    """Sert l'icône (les navigateurs sollicitent /favicon.ico même sans lien)."""
    return FileResponse(BASE_DIR / "static" / "favicon.svg", media_type="image/svg+xml")


def _render_readme() -> tuple[str, str, list[dict]]:
    """Rend ``README.md`` en HTML pour la page d'accueil : source **unique**, jamais
    dupliquée à la main — toute modification du README y apparaît sans autre effort.
    Le sommaire (``toc_tokens``) alimente la navigation de la page.

    Le HTML est coupé en deux juste avant le second H2 (donc juste après la section
    « En bref ») : le sommaire s'intercale entre les deux morceaux — juste après
    « En bref », pas avant tout l'article (issue #13.5) — dans une mise en page
    unique, identique quelle que soit la taille d'écran (voir home.html)."""
    converter = markdown.Markdown(
        extensions=["extra", "sane_lists", "toc"],
        extension_configs={"toc": {"permalink": False}},
    )
    html = converter.convert((BASE_DIR / "README.md").read_text(encoding="utf-8"))
    # Racine unique (le H1 « Kairos ») : ses enfants (H2/H3) forment le sommaire —
    # le H1 lui-même est déjà repris dans le bandeau de bienvenue, inutile en double.
    toc = converter.toc_tokens[0]["children"] if converter.toc_tokens else []
    intro_html, rest_html = html, ""
    if len(toc) >= 2:
        marker = f'<h2 id="{toc[1]["id"]}"'
        split_at = html.find(marker)
        if split_at != -1:
            intro_html, rest_html = html[:split_at], html[split_at:]
    return intro_html, rest_html, toc


@app.get("/")
def home(request: Request) -> HTMLResponse:
    """Page d'accueil : bienvenue + README rendu (voir ``_render_readme``)."""
    readme_intro_html, readme_rest_html, readme_toc = _render_readme()
    context = {
        "page": "home",
        "readme_intro_html": readme_intro_html,
        "readme_rest_html": readme_rest_html,
        "readme_toc": readme_toc,
    }
    return templates.TemplateResponse(request, "home.html", context)


def _fetch_busy_blocks(
    tasks_session: Session, settings, range_start: date, range_end: date
):
    """Blocs manuels (base) + TimeTree (best-effort), fusionnés sur ``[range_start, range_end]``.

    Ne filtre PAS journée entière/période ici : chaque appelant décide comment traiter
    ces cas particuliers (voir ``_build_kairos_context``). Les blocs récurrents (phase 13)
    sont stockés comme un modèle unique (jamais un par occurrence) : projetés à la
    volée sur la plage demandée par ``expand_recurring_blocks``, jamais persistés.
    """
    timetree_result = fetch_busy_slots(range_start, range_end, settings=settings)
    range_start_dt = datetime.combine(range_start, datetime.min.time())
    range_end_dt = datetime.combine(range_end, datetime.max.time())
    one_off_blocks = list(
        tasks_session.scalars(
            select(TimeBlock).where(
                TimeBlock.source == "manual",
                TimeBlock.recurrence == "",
                TimeBlock.start < range_end_dt,
                TimeBlock.end > range_start_dt,
            )
        )
    )
    recurring_templates = list(
        tasks_session.scalars(
            select(TimeBlock).where(
                TimeBlock.source == "manual",
                TimeBlock.recurrence != "",
            )
        )
    )
    manual_blocks = one_off_blocks + expand_recurring_blocks(
        recurring_templates, range_start, range_end
    )
    return manual_blocks, timetree_result


def _build_week_view(
    tasks: list[Task], blocks: list[TimeBlock], monday: date, indication_slots=None,
    done_tasks: list[Task] | None = None,
) -> list[dict]:
    """Grille 7 jours : tâches groupées par ``deadline``, blocs horaires du jour, et
    événements « journée entière » ou « sur une période » en puces (jamais posés comme
    des créneaux — voir ``_build_kairos_context``). ``done_tasks`` (issue #15.7) : tâches
    terminées de la semaine, groupées par jour de complétion (``updated_at``, même
    convention que ``done_today``) — la vue « semaine » montre ainsi à la fois ce qui
    vient de se faire et ce qui reste à faire."""
    days = [monday + timedelta(days=i) for i in range(7)]
    week = []
    for day in days:
        day_tasks = sorted(
            (t for t in tasks if t.deadline == day),
            key=lambda t: (t.priority is None, t.priority if t.priority is not None else 999),
        )
        day_done = sorted(
            (t for t in (done_tasks or []) if t.updated_at is not None and t.updated_at.date() == day),
            key=lambda t: t.title,
        )
        day_blocks = sorted(
            (b for b in blocks if b.start.date() == day), key=lambda b: b.start
        )
        day_all_day = sorted(
            b.title for b in (indication_slots or []) if b.covers(day)
        )
        week.append({
            "date": day, "tasks": day_tasks, "done_tasks": day_done, "blocks": day_blocks,
            "all_day": day_all_day,
        })
    return week


def _build_kairos_context(
    request: Request,
    tasks_session: Session,
    pilotage_session: Session | None,
    *,
    view: str = "day",
    day: date | None = None,
) -> dict:
    """« Kairos » : construit le contexte de rendu (tâches ordonnées par urgence,
    compte tenu des créneaux occupés) — utilisé aussi bien pour la page pleine
    (``kairos.html``) que pour le partiel de la vue jour (``_kairos_day.html``,
    voir ``render_kairos_response``).

    Best-effort sur les sources externes (TimeTree, GitLab) : un échec se traduit
    par un bandeau d'avertissement dans le template, jamais par une page en erreur
    (voir docs/spec/integrations-externes.md § Invariants et garde-fous).
    ``pilotage_session`` (base de
    l'outil de pilotage, lecture seule) est None si l'intégration n'est pas
    configurée : la liaison de fiches (« Fiche liée ») est alors désactivée
    proprement. L'import des issues GitLab assignées suit, lui, deux chemins
    possibles — le cache pilotage (zéro appel réseau) s'il est configuré, sinon
    l'appel direct à l'API GitLab (``gitlab_direct_configured``) pour un collègue
    sans l'outil de pilotage ; sans aucun des deux, l'import reste désactivé,
    silencieusement (pas de bandeau, cas normal).
    """
    settings = get_settings()
    target_day = day or date.today()
    # Recherche + filtres à facettes (issue #15.4) : affinent l'affichage de chaque
    # section (mot-clé, priorité, projet), sans jamais réordonner ni toucher à
    # l'ordonnancement lui-même (`build_day_schedule`/`_build_week_view` restent
    # calculés sur l'intégralité des tâches).
    search_q = (request.query_params.get("q") or "").strip()
    filter_priority = _optional_int(request.query_params.get("priority"))
    filter_project = (request.query_params.get("project") or "").strip() or None
    filter_type = (request.query_params.get("task_type") or "").strip() or None
    filter_fibo = _optional_int(request.query_params.get("fibonacci_points"))

    def _visible(task: Task) -> bool:
        return _matches_query(task, search_q) and _matches_filters(
            task, priority=filter_priority, project=filter_project,
            task_type=filter_type, fibonacci_points=filter_fibo,
        )

    gitlab_direct_error = ""
    if pilotage_session is not None:
        cached_issues = list(pilotage_session.scalars(select(CachedGitLabIssue)))
        sync_assigned_gitlab_tasks(
            cached_issues, tasks_session, settings.gitlab_assignee_username
        )
    elif settings.gitlab_direct_configured:
        fetch_result = fetch_assigned_issues(settings)
        if fetch_result.ok:
            sync_assigned_gitlab_tasks(
                fetch_result.issues, tasks_session, settings.gitlab_assignee_username
            )
        else:
            # Dégradation propre : les tâches déjà importées restent affichées telles
            # quelles, un bandeau signale juste que ce rafraîchissement a échoué.
            write_sync_meta(tasks_session, ok=False, detail=fetch_result.detail, count=0)
            gitlab_direct_error = fetch_result.detail
    ensure_calendar_occurrences(tasks_session, target_day, settings.holiday_set)
    all_tasks = list(tasks_session.scalars(select(Task).where(Task.status != "archived")))
    tasks = [t for t in all_tasks if t.status == "todo"]

    # Dépendances, calculées tôt : `blocked_ids` sert dès le garde-fou de priorité
    # ci-dessous (une tâche bloquée ne concourt pas pour l'attention du jour).
    dep_edges = [
        (d.task_id, d.blocker_id)
        for d in tasks_session.scalars(select(TaskDependency))
    ]
    status_by_id = {t.id: t.status for t in all_tasks}
    blocked_ids = blocked_task_ids(dep_edges, status_by_id)

    # Détection des tâches qui traînent (phase 7) : signal d'affichage seul,
    # calculé après coup — ne modifie jamais le tri ni les buckets d'urgence.
    stale_days_of = {
        t.id: days
        for t in tasks
        if (days := days_stale(
            t, target_day,
            overdue_days=settings.stale_overdue_days,
            untouched_days=settings.stale_untouched_days,
        )) is not None
    }

    # Garde-fou de surcharge de priorité maximale (phase 7) : purement informatif.
    # Une tâche bloquée est exclue du décompte : elle ne peut pas être traitée
    # tant qu'elle est bloquée, donc ne dilue pas le signal de priorité du jour —
    # sans quoi le bandeau se déclenchait pour des tâches sur lesquelles on ne
    # peut de toute façon rien faire dans l'immédiat.
    priority_overload_count = count_max_priority_tasks(
        [t for t in tasks if t.id not in blocked_ids]
    )

    # Liaison manuelle vers une fiche de dette technique (phase 6) : lecture seule,
    # choix proposés au panneau d'édition + résolution du libellé pour le badge.
    all_tickets = (
        list(pilotage_session.scalars(select(LinkedTicket)))
        if pilotage_session is not None else []
    )
    ticket_by_id = {t.id: t for t in all_tickets}
    ticket_choices = sorted(
        (
            {"id": t.id, "label": f"#{t.pleiade_id} — {t.pleiade_subject[:60]}"}
            for t in all_tickets
        ),
        key=lambda c: c["label"],
    )

    # Hiérarchie : avancement n/m des tâches mères, et titre de la mère pour chaque
    # fille (fil d'Ariane dans les listes, qui restent triées par heure).
    by_id = {t.id: t for t in all_tasks}
    children_of: dict[int, list[Task]] = {}
    for t in all_tasks:
        if t.parent_id is not None:
            children_of.setdefault(t.parent_id, []).append(t)
    parents_progress = [
        {
            "task": parent,
            "done": sum(1 for c in children if c.status == "done"),
            "total": len(children),
        }
        for parent_id, children in children_of.items()
        if (parent := by_id.get(parent_id)) is not None
        and parent.status == "todo"
        and any(c.status == "todo" for c in children)
        and _visible(parent)
    ]
    parent_title_of = {
        t.id: by_id[t.parent_id].title
        for t in all_tasks
        if t.parent_id is not None and t.parent_id in by_id
    }

    # Dépendances (arêtes, tâches bloquées) déjà calculées plus haut ; reste
    # l'urgence dérivée et les motifs de blocage à afficher.
    title_by_id = {t.id: t.title for t in all_tasks}
    # Titres préfixés par la tâche mère (si elle existe) pour lever l'ambiguïté entre
    # tâches de même nom sous des mères différentes dans le motif de blocage affiché.
    breadcrumb_title_by_id = {
        tid: (f"{parent_title_of[tid]} › {title}" if tid in parent_title_of else title)
        for tid, title in title_by_id.items()
    }
    block_reasons = blocking_reason(dep_edges, status_by_id, breadcrumb_title_by_id)
    own_urgency = {t.id: urgency_key(t, target_day, settings=settings) for t in tasks}
    effective_urgency = derived_urgency(dep_edges, own_urgency)
    # Tâches dont l'urgence a été relevée par une dépendance (badge « sur le chemin
    # critique ») : clé effective plus forte (plus petite) que la clé propre.
    raised_ids = {
        tid for tid, key in effective_urgency.items()
        if key < own_urgency.get(tid, key)
    }
    # Bordure colorée du template : palier d'urgence PROPRE 0-4 (pression temporelle),
    # volontairement découplé de l'ordre WSJF (phase 9). L'ordre suit le score ; la
    # couleur reste une lecture rapide « à quel point c'est pressé », le badge
    # « chemin critique » (raised_ids) signale à part le relèvement par dépendance.
    bucket_of = {t.id: urgency_bucket(t, target_day) for t in tasks}
    # Score WSJF affiché à côté de chaque tâche (transparence : on voit *pourquoi* cet
    # ordre) + détail valeur/urgence/effort au survol. Phase 9.
    wsjf_of = {t.id: round(wsjf_score(t, target_day, settings=settings), 1) for t in tasks}
    blocked_tasks = [
        {"task": by_id[tid], "reasons": block_reasons.get(tid, [])}
        for tid in blocked_ids
        if tid in by_id and _visible(by_id[tid])
    ]
    blocked_tasks.sort(key=lambda e: e["task"].title)

    # Backlog (sans échéance ni date programmée) : sans section dédiée, ces tâches
    # n'apparaissent JAMAIS en vue semaine (groupée strictement par échéance,
    # `_build_week_view`) et se perdent facilement dans l'agenda du jour. Affiché
    # quelle que soit la vue (jour ou semaine), donc placé hors de `schedule`
    # (lui-même calculé seulement pour la vue jour, plus bas).
    backlog_tasks = sorted(
        (
            t for t in tasks
            if t.deadline is None and t.scheduled_date is None and t.id not in blocked_ids
            and _visible(t)
        ),
        key=lambda t: (t.priority is None, t.priority if t.priority is not None else 999, t.title),
    )

    # Projets distincts (issue #15.4) : options du filtre "projet", sur les tâches
    # actives seulement (pas archivées/faites), triés pour un menu stable.
    project_choices = sorted({t.project_tag for t in tasks if t.project_tag})

    # Choix des bloqueurs candidats pour l'UI (toute autre tâche à faire).
    blocker_choices = sorted(
        ({"id": t.id, "title": t.title} for t in tasks),
        key=lambda c: c["title"],
    )

    # Suivi du temps réel : total passé par tâche (chrono + saisie manuelle, issue #6)
    # + session en cours (minuteur vivant).
    sessions = list(tasks_session.scalars(select(WorkSession)))
    spent_by_task = spent_minutes_by_task(sessions, tasks=all_tasks)
    running = running_session(sessions)
    running_task_id = running.task_id if running is not None else None
    running_started_iso = (
        (running.started_at if running.started_at.tzinfo
         else running.started_at.replace(tzinfo=timezone.utc)).isoformat()
        if running is not None else None
    )
    # Alertes de chrono (phase 11) : la tâche en cours nourrit le script client
    # (dépassement de l'estimé, titre d'onglet vivant, notification).
    running_task = by_id.get(running_task_id) if running_task_id is not None else None
    running_task_title = running_task.title if running_task is not None else ""
    running_task_estimate = (
        running_task.estimated_minutes if running_task is not None else None
    )
    # Phase 7 : le total « aujourd'hui » ne doit compter que les sessions du jour
    # affiché — corrige un bug où `sessions` (toutes, jamais filtrées) gonflait ce
    # total avec l'historique complet. Ventilation par type en plus, pour un usage
    # immédiat du suivi du temps déjà collecté.
    task_type_by_id = {t.id: t.task_type for t in all_tasks}
    today_sessions = sessions_on_day(sessions, target_day)
    spent_by_type_today = {
        k: v
        for k, v in spent_minutes_by_type(today_sessions, task_type_by_id).items()
        if k and v > 0
    }
    # Suggestion de durée par type (issue #7) : médiane du temps réel des tâches
    # terminées de ce type, pour pré-remplir « Durée (min) » quand l'utilisateur
    # choisit un type dans le panneau d'édition (voir le JS de kairos.html). Seuls
    # les types avec assez d'échantillons (`reliable`) sont proposés.
    avg_minutes_by_type = {
        c.key: c.median_minutes
        for c in calibration_by_type(all_tasks, spent_by_task)
        if c.reliable and c.median_minutes
    }
    # Même principe pour les points de Fibonacci calibrés (issue #15.6) : pré-remplit
    # « Durée (min) » quand l'utilisateur choisit un palier dont le calibrage est fiable.
    avg_minutes_by_fibo = {
        c.points: c.median_minutes
        for c in fibonacci_calibration(all_tasks, spent_by_task)
        if c.reliable and c.median_minutes
    }

    context = {
        "page": "kairos",
        "settings": settings,
        "view": view,
        "day": target_day,
        "timetree_configured": settings.timetree_configured,
        "gitlab_direct_error": gitlab_direct_error,
        "blocked_tasks": blocked_tasks,
        "backlog_tasks": backlog_tasks,
        # Utilisé par le panneau Backlog (affiché quelle que soit la vue) autant que
        # par la vue jour ; ajouté ici pour être disponible dans les deux.
        "parent_title_of": parent_title_of,
        "block_reasons": block_reasons,
        "raised_ids": raised_ids,
        "bucket_of": bucket_of,
        "wsjf_of": wsjf_of,
        "fibonacci_scale": FIBONACCI_SCALE,
        "spent_by_task": spent_by_task,
        "avg_minutes_by_type": avg_minutes_by_type,
        "avg_minutes_by_fibo": avg_minutes_by_fibo,
        "running_task_id": running_task_id,
        "running_started_iso": running_started_iso,
        "running_task_title": running_task_title,
        "running_task_estimate": running_task_estimate,
        "spent_total_str": _fmt_minutes(total_minutes(today_sessions)),
        "spent_by_type_today": spent_by_type_today,
        "deps_of": {  # bloqueurs directs par tâche, pour le panneau d'édition
            tid: [
                {"id": bid, "title": title_by_id.get(bid, f"#{bid}")}
                for (t2, bid) in dep_edges if t2 == tid
            ]
            for tid in {e[0] for e in dep_edges}
        },
        "blocker_choices": blocker_choices,
        "ticket_choices": ticket_choices,
        "ticket_by_id": ticket_by_id,
        "stale_days_of": stale_days_of,
        "priority_overload_count": priority_overload_count,
        "priority_overload_threshold": settings.priority_overload_threshold,
        "search_q": search_q,
        "filter_priority": filter_priority,
        "filter_project": filter_project,
        "filter_type": filter_type,
        "filter_fibo": filter_fibo,
        "project_choices": project_choices,
    }

    if view == "week":
        monday = target_day - timedelta(days=target_day.weekday())
        sunday = monday + timedelta(days=6)
        manual_blocks, timetree_result = _fetch_busy_blocks(
            tasks_session, settings, monday, sunday
        )
        # Les événements « journée entière » ET les événements « sur une période »
        # (plusieurs jours, horaires réels) ne sont pas des créneaux horaires : ils
        # sont affichés en puce sur leur(s) jour(s), jamais posés sur la grille — un
        # horaire réel ne dit rien sur les jours intermédiaires d'un déplacement de
        # plusieurs jours (phase 12).
        timed_slots = [
            b for b in timetree_result.blocks
            if not b.all_day and b.start.date() == b.end.date()
        ]
        indication_slots = [
            b for b in timetree_result.blocks
            if b.all_day or b.start.date() != b.end.date()
        ]
        # Agrégat hebdomadaire du temps réel par type (phase 7) : synthèse compacte
        # à partir des données déjà collectées, pas un nouveau dashboard de stats.
        week_sessions = sessions_in_range(sessions, monday, sunday)
        spent_by_type_week = {
            k: v
            for k, v in spent_minutes_by_type(week_sessions, task_type_by_id).items()
            if k and v > 0
        }
        # Tâches terminées PENDANT la semaine affichée (même repère que `done_today` en
        # vue jour) : sans ça, la vue semaine n'affiche que ce qui reste à faire, jamais
        # ce qui vient d'être fait — issue #15.7.
        done_week = [
            t for t in all_tasks
            if t.status == "done" and t.updated_at is not None
            and monday <= t.updated_at.date() <= sunday
        ]
        week_days = _build_week_view(tasks, manual_blocks + [
            TimeBlock(title=b.title, start=b.start, end=b.end, source="timetree")
            for b in timed_slots
        ], monday, indication_slots, done_tasks=done_week)
        # Recherche/filtres (issue #15.4) : s'appliquent aussi à la vue semaine, en
        # masquant des lignes dans chaque jour — jamais en réordonnant la grille.
        for entry in week_days:
            entry["tasks"] = [t for t in entry["tasks"] if _visible(t)]
            entry["done_tasks"] = [t for t in entry["done_tasks"] if _visible(t)]
        context.update(
            week_start=monday,
            prev_week_start=monday - timedelta(days=7),
            next_week_start=monday + timedelta(days=7),
            week_days=week_days,
            timetree_ok=timetree_result.ok,
            timetree_detail=timetree_result.detail,
            spent_by_type_week=spent_by_type_week,
            week_spent_total_str=_fmt_minutes(total_minutes(week_sessions)),
        )
    else:
        manual_blocks, timetree_result = _fetch_busy_blocks(
            tasks_session, settings, target_day, target_day
        )
        # Journées entières ET événements « sur une période » (plusieurs jours, horaires
        # réels) TimeTree : simple indication sur le jour, jamais un obstacle qui
        # mangerait la journée entière (l'horaire réel ne dit rien des jours
        # intermédiaires d'un déplacement de plusieurs jours — phase 12).
        timetree_blocks = [
            TimeBlock(title=b.title, start=b.start, end=b.end, source="timetree")
            for b in timetree_result.blocks
            if not b.all_day and b.start.date() == b.end.date()
        ]
        indication_events = sorted(
            b.title for b in timetree_result.blocks
            if b.all_day and b.covers(target_day)
        ) + sorted(
            f"{b.title} ({b.start.strftime('%d/%m')} → {b.end.strftime('%d/%m')})"
            for b in timetree_result.blocks
            if not b.all_day and b.start.date() != b.end.date() and b.covers(target_day)
        )
        schedule = build_day_schedule(
            tasks, manual_blocks + timetree_blocks, target_day,
            now=datetime.now(), settings=settings,
            blocked_ids=blocked_ids, urgency_keys=effective_urgency,
        )
        # Section « Fait » : les tâches terminées dans la journée affichée (repère
        # d'avancement ; updated_at est en UTC naïf, précision au jour suffisante ici).
        done_today = [
            t for t in tasks_session.scalars(select(Task).where(Task.status == "done"))
            if t.updated_at is not None and t.updated_at.date() == target_day and _visible(t)
        ]
        # Recherche/filtres (issue #15.4) : listes séparées pour l'affichage seul — le
        # `schedule` lui-même (stats, timeline, « à faire maintenant ») reste calculé
        # sur l'intégralité des tâches, jamais sur ce sous-ensemble filtré.
        visible_to_process = [t for t in schedule.to_process if _visible(t)]
        visible_scheduled = [s for s in schedule.scheduled if _visible(s.task)]
        visible_unscheduled = [t for t in schedule.unscheduled if _visible(t)]
        visible_later = [t for t in schedule.later if _visible(t)]
        timeline = build_timeline(
            schedule, manual_blocks + timetree_blocks, target_day, settings=settings
        )
        # Sessions de travail RÉELLES du jour, projetées sur la même timeline (« réel »
        # à côté du « planifié ») — phase 11.
        session_timeline = session_timeline_entries(
            today_sessions, target_day, {t.id: t.title for t in all_tasks},
            settings=settings, now=datetime.now(),
        )
        # Créneaux manuels ÉDITABLES pertinents pour le jour affiché : les ponctuels du
        # jour + les modèles récurrents dont une occurrence tombe ce jour-là. Ce sont les
        # lignes RÉELLES (avec id), pas les projections transitoires — éditer un récurrent
        # porte sur le modèle, donc sur toutes ses occurrences.
        all_manual = list(
            tasks_session.scalars(select(TimeBlock).where(TimeBlock.source == "manual"))
        )
        editable_blocks = sorted(
            (
                b for b in all_manual
                if (not b.recurrence and b.start.date() == target_day)
                or (b.recurrence and expand_recurring_blocks([b], target_day, target_day))
            ),
            key=lambda b: b.start.time(),
        )
        context.update(
            schedule=schedule,
            visible_to_process=visible_to_process,
            visible_scheduled=visible_scheduled,
            visible_unscheduled=visible_unscheduled,
            visible_later=visible_later,
            session_timeline=session_timeline,
            manual_blocks=sorted(manual_blocks, key=lambda b: b.start),
            editable_blocks=editable_blocks,
            block_recurrence_labels={
                "daily": "quotidien", "weekdays": "jours ouvrés", "weekly": "hebdomadaire",
            },
            done_today=done_today,
            indication_events=indication_events,
            parents_progress=parents_progress,
            parent_title_of=parent_title_of,
            timeline=timeline,
            timeline_height=(settings.workday_end_hour - settings.workday_start_hour) * 60,
            timeline_hours=list(range(settings.workday_start_hour, settings.workday_end_hour + 1)),
            required_str=_fmt_minutes(schedule.stats.required_minutes),
            available_str=_fmt_minutes(schedule.stats.available_minutes),
            overflow_str=_fmt_minutes(schedule.stats.overflow_minutes),
            # « À faire maintenant » : la première planifiée, sinon (soirée, journée
            # pleine) la première non planifiée — il y a toujours un « prochain pas ».
            next_up_task=(
                schedule.scheduled[0].task if schedule.scheduled
                else (schedule.unscheduled[0] if schedule.unscheduled else None)
            ),
            next_up_time=(schedule.scheduled[0].start_at if schedule.scheduled else None),
            timetree_ok=timetree_result.ok,
            timetree_detail=timetree_result.detail,
        )

    return context


def render_kairos_response(request: Request, *, fragment: bool) -> Response:
    """Point d'entrée unique de rendu de la vue Kairos, ouvrant les DEUX sessions
    (tâches + pilotage) comme le faisait ``kairos()`` — voir ``_build_kairos_context``.

    ``fragment=False`` rend la page pleine (``kairos.html``, comportement historique) ;
    ``fragment=True`` rend seulement le partiel de la vue jour (``_kairos_day.html``,
    id ``#mj-day-content``), utilisé par les handlers d'action pour l'amélioration
    progressive AJAX (voir Étape C du guide d'exécution). Les handlers d'action
    doivent committer et FERMER leur propre session tâches avant d'appeler cette
    fonction, qui rouvre des sessions fraîches : ne jamais imbriquer les sessions.
    """
    view = request.query_params.get("view", "day")
    start = request.query_params.get("start")
    day = date.fromisoformat(start) if start else None
    with (
        _request_session(get_tasks_session) as tasks_session,
        _request_session(get_pilotage_session) as pilotage_session,
    ):
        context = _build_kairos_context(
            request, tasks_session, pilotage_session, view=view, day=day
        )
        template_name = "_kairos_day.html" if fragment else "kairos.html"
        return templates.TemplateResponse(request, template_name, context)


@app.get("/kairos")
def kairos(request: Request) -> HTMLResponse:
    return render_kairos_response(request, fragment=False)


@app.get("/kairos/stats")
def kairos_stats(request: Request) -> HTMLResponse:
    """Dashboard de statistiques (phase 10) : indicateurs constructifs à partir des
    tâches et sessions déjà collectées. Lecture seule, aucun appel réseau ni synchro."""
    settings = get_settings()
    today = date.today()
    with _request_session(get_tasks_session) as tasks_session:
        tasks = list(tasks_session.scalars(select(Task)))
        sessions = list(tasks_session.scalars(select(WorkSession)))
    stats = compute_dashboard_stats(tasks, sessions, today, settings=settings)
    context = {
        "page": "kairos_stats",
        "settings": settings,
        "day": today,
        "stats": stats,
        "fmt_minutes": _fmt_minutes,
    }
    return templates.TemplateResponse(request, "kairos_stats.html", context)


_FIELD_KIND_BY_ANNOTATION = {bool: "bool", int: "int", float: "float"}


def _field_kind(name: str) -> str:
    if name in SECRET_FIELDS:
        return "secret"
    return _FIELD_KIND_BY_ANNOTATION.get(Settings.model_fields[name].annotation, "text")


def _settings_context(
    settings: Settings, *, errors: dict[str, str], values: dict[str, object] | None, saved: bool
) -> dict:
    """Contexte partagé entre le GET (valeurs actuelles) et le POST invalide
    (valeurs ressaisies) de la page Réglages — les secrets ne sont jamais mis à
    disposition du template en clair, dans un cas comme dans l'autre."""
    display_values = dict(values if values is not None else settings.model_dump())
    for name in SECRET_FIELDS:
        display_values.pop(name, None)
    return {
        "page": "settings",
        "settings": settings,
        "sections": SECTIONS,
        "field_labels": FIELD_LABELS,
        "field_meta": Settings.model_fields,
        "field_kind": {name: _field_kind(name) for name in Settings.model_fields},
        "values": display_values,
        "errors": errors,
        "secret_fields": SECRET_FIELDS,
        "secret_status": {name: bool(getattr(settings, name)) for name in SECRET_FIELDS},
        "restart_required_fields": RESTART_REQUIRED_FIELDS,
        "data_dir": str(settings_store.data_dir()),
        "settings_path": str(settings_store.settings_path()),
        "migrated_at": settings_store.meta().get("migrated_from_env_at"),
        "keyring_available": secret_store.keyring_available(),
        "saved": saved,
    }


@app.get("/kairos/settings")
def kairos_settings(request: Request) -> HTMLResponse:
    saved = request.query_params.get("saved")
    context = _settings_context(get_settings(), errors={}, values=None, saved=bool(saved))
    return templates.TemplateResponse(request, "settings.html", context)


def _settings_candidate_from_form(form, current: Settings) -> tuple[dict, dict[str, str]]:
    """Construit le dict candidat pour `Settings(**candidate)` à partir du
    formulaire, et les erreurs de conversion de type (avant même la validation
    des réglages) — un champ non numérique ne doit jamais faire planter la route."""
    candidate: dict[str, object] = current.model_dump()
    errors: dict[str, str] = {}
    for name in Settings.model_fields:
        kind = _field_kind(name)
        if kind == "secret":
            if form.get(f"{name}_clear"):
                candidate[name] = ""
            elif form.get(name):
                candidate[name] = form.get(name)
            continue
        if kind == "bool":
            candidate[name] = form.get(name) is not None
            continue
        raw = str(form.get(name, "")).strip()
        try:
            candidate[name] = int(raw) if kind == "int" else float(raw) if kind == "float" else raw
        except ValueError:
            errors[name] = "Valeur numérique invalide."
            candidate[name] = raw
    return candidate, errors


@app.post("/kairos/settings")
async def kairos_settings_save(request: Request) -> Response:
    current = get_settings()
    form = await request.form()
    candidate, errors = _settings_candidate_from_form(form, current)
    if not errors:
        try:
            new_settings = Settings(**candidate)
        except SettingsValidationError as exc:
            errors.update(exc.errors)
        else:
            settings_store.save(new_settings)
            invalidate_settings_cache()
            apply_proxy_env(get_settings())
            return RedirectResponse("/kairos/settings?saved=1", status_code=303)

    # Erreur de validation : réaffiche le formulaire avec les valeurs ressaisies
    # (sauf secrets, jamais réaffichés) plutôt qu'une redirection qui avalerait
    # l'erreur silencieusement.
    context = _settings_context(current, errors=errors, values=candidate, saved=False)
    return templates.TemplateResponse(request, "settings.html", context)


@app.post("/kairos/shutdown")
def shutdown(request: Request) -> HTMLResponse:
    """Arrête proprement le serveur (bouton « Quitter », exécutable de bureau
    uniquement — voir `is_frozen` : pas de fenêtre de terminal à fermer sinon).

    SIGINT (littéralement un Ctrl+C), pas SIGTERM : les deux déclenchent l'arrêt
    normal d'uvicorn (draine les requêtes en cours, dont celle-ci), mais après un
    SIGTERM le process est ensuite tué par l'OS avant que `app/launcher.py` ne
    puisse exécuter son `finally` (nettoyage du verrou d'instance unique) —
    vérifié empiriquement, SIGINT laisse ce `finally` s'exécuter normalement."""
    os.kill(os.getpid(), signal.SIGINT)
    return HTMLResponse(
        "<p>Kairos s'arrête. Vous pouvez fermer cette page.</p>"
    )


def _optional_date(value: str | None) -> date | None:
    """Convertit une valeur de formulaire en date ISO, vide/invalide → None."""
    if not value or not str(value).strip():
        return None
    try:
        return date.fromisoformat(value.strip())
    except ValueError:
        return None


def _fmt_minutes(minutes: int) -> str:
    """Formatage lisible d'une durée en minutes (« 1 h 30 », « 45 min »)."""
    hours, mins = divmod(max(0, minutes), 60)
    if hours and mins:
        return f"{hours} h {mins:02d}"
    if hours:
        return f"{hours} h"
    return f"{mins} min"


@app.post("/kairos/tasks")
async def create_native_task(request: Request) -> RedirectResponse:
    """Création rapide d'une tâche native (ou d'une sous-tâche si ``parent_id``)."""
    form = await request.form()
    title = str(form.get("title", "")).strip()
    with _request_session(get_tasks_session) as tasks_session:
        parent_key = _optional_int(form.get("parent_id"))
        if parent_key is not None and tasks_session.get(Task, parent_key) is None:
            parent_key = None  # mère disparue : la tâche naît au premier niveau
        if title:
            tasks_session.add(
                Task(
                    title=title,
                    priority=_optional_int(form.get("priority")),
                    deadline=_optional_date(form.get("deadline")),
                    scheduled_date=_optional_date(form.get("scheduled_date")),
                    project_tag=str(form.get("project_tag", "")).strip(),
                    estimated_minutes=_optional_int(form.get("estimated_minutes")),
                    parent_id=parent_key,
                    source="native",
                )
            )
            tasks_session.commit()
    return RedirectResponse("/kairos", status_code=303)


def _kairos_action_response(request: Request) -> Response:
    """Réponse commune des handlers d'action (fait, chrono, décaler, priorité,
    points) : amélioration progressive AJAX (Étape C/E du chantier GTD) — si
    l'appelant négocie ``X-Requested-With: fetch``, renvoie le partiel jour
    (``_kairos_day.html``, mêmes deux sessions fraîches que ``kairos()``) ;
    sinon la redirection 303 historique (repli complet sans JS, requis pour la
    WebView Android et l'accessibilité). Les handlers appelants doivent avoir
    déjà committé et FERMÉ leur propre session tâches avant d'appeler cette
    fonction — elle rouvre des sessions fraîches, jamais imbriquées."""
    if request.headers.get("X-Requested-With") == "fetch":
        return render_kairos_response(request, fragment=True)
    return RedirectResponse("/kairos", status_code=303)


@app.post("/kairos/tasks/{task_id:int}/priority")
async def update_task_priority(request: Request) -> Response:
    form = await request.form()
    with _request_session(get_tasks_session) as tasks_session:
        task = tasks_session.get(Task, request.path_params["task_id"])
        if task is not None:
            task.priority = _optional_int(form.get("priority"))
            tasks_session.commit()
    return _kairos_action_response(request)


@app.post("/kairos/tasks/{task_id:int}/points")
async def update_task_points(request: Request) -> Response:
    """Symétrique de ``update_task_priority`` : pose les points Fibonacci depuis
    le contrôle inline de la boîte de réception (Phase 2) — jamais depuis la
    capture rapide titre-seul, ni un nouveau champ dans ``create_native_task``."""
    form = await request.form()
    with _request_session(get_tasks_session) as tasks_session:
        task = tasks_session.get(Task, request.path_params["task_id"])
        if task is not None:
            task.fibonacci_points = _optional_int(form.get("points"))
            tasks_session.commit()
    return _kairos_action_response(request)


def _stop_running_sessions(tasks_session: Session) -> None:
    """Ferme toute session de travail encore ouverte (invariant : au plus une en cours)."""
    now = datetime.now(timezone.utc)
    for session in tasks_session.scalars(
        select(WorkSession).where(WorkSession.ended_at.is_(None))
    ):
        session.ended_at = now


@app.post("/kairos/tasks/{task_id:int}/timer/start")
def start_timer(request: Request) -> Response:
    """Démarre le chrono sur une tâche (ferme d'abord toute session en cours ailleurs)."""
    task_id = request.path_params["task_id"]
    with _request_session(get_tasks_session) as tasks_session:
        task = tasks_session.get(Task, task_id)
        if task is not None:
            _stop_running_sessions(tasks_session)
            tasks_session.add(WorkSession(task_id=task_id))
            tasks_session.commit()
    return _kairos_action_response(request)


@app.post("/kairos/tasks/{task_id:int}/timer/stop")
def stop_timer(request: Request) -> Response:
    """Arrête le chrono en cours sur cette tâche."""
    task_id = request.path_params["task_id"]
    now = datetime.now(timezone.utc)
    with _request_session(get_tasks_session) as tasks_session:
        for session in tasks_session.scalars(
            select(WorkSession).where(
                WorkSession.task_id == task_id, WorkSession.ended_at.is_(None)
            )
        ):
            session.ended_at = now
        tasks_session.commit()
    return _kairos_action_response(request)


@app.post("/kairos/tasks/{task_id:int}/edit")
async def edit_task(request: Request) -> RedirectResponse:
    """Édition complète d'une tâche (tous champs), y compris une tâche importée de SP.

    Fusionne l'épinglage (heure fixe) dans le même enregistrement : `pin_time` vide
    désépingle, une heure valide pose `pinned_start` sur la date programmée si elle
    est renseignée, sinon sur le jour affiché (`pin_day`) — un seul « Enregistrer »
    plutôt que deux soumissions séparées (phase 5).

    `linked_ticket_id` (phase 6) : référence en lecture seule vers une fiche
    `LinkedTicket` (base dette technique) — validée contre son existence (``pilotage_session``),
    jamais d'écriture vers Redmine/GitLab.

    `new_subtasks` (phase 6) : une ligne = une sous-tâche créée dans le même
    enregistrement (ajout en lot, en plus de la création rapide d'une seule
    sous-tâche qui reste disponible séparément). `blocker_ids` : l'**ensemble
    cible complet** des bloqueurs — la route calcule le diff avec l'existant
    (ajoute/retire), ignore silencieusement toute arête qui créerait un cycle
    (reprend `would_create_cycle`, comme l'ancienne route dédiée) sans faire
    échouer le reste de l'enregistrement.
    """
    task_id = request.path_params["task_id"]
    form = await request.form()
    settings = get_settings()
    with (
        _request_session(get_tasks_session) as tasks_session,
        _request_session(get_pilotage_session) as pilotage_session,
    ):
        task = tasks_session.get(Task, task_id)
        if task is None:
            return RedirectResponse("/kairos", status_code=303)
        title = str(form.get("title", ""))
        if title.strip():
            task.title = title.strip()
        task.description = str(form.get("description", ""))
        task.priority = _optional_int(form.get("priority"))
        task.deadline = _optional_date(form.get("deadline"))
        task.scheduled_date = _optional_date(form.get("scheduled_date"))
        task.project_tag = str(form.get("project_tag", "")).strip()
        task.estimated_minutes = _optional_int(form.get("estimated_minutes"))
        recurrence = str(form.get("recurrence", ""))
        if recurrence in ("", "daily", "weekdays", "weekly", "monthly", "monthly_on_day"):
            task.recurrence = recurrence
        # Le jour du mois n'a de sens que pour la récurrence calendaire ; le vider
        # sinon évite une valeur résiduelle trompeuse si l'utilisateur change d'avis.
        task.recurrence_day_of_month = (
            _optional_int(form.get("recurrence_day_of_month"))
            if recurrence == "monthly_on_day" else None
        )
        task_type = str(form.get("task_type", ""))
        task.task_type = task_type if task_type in settings.task_type_list else ""
        task.fibonacci_points = _optional_int(form.get("fibonacci_points"))
        task.manual_time_spent_minutes = _optional_int(form.get("manual_time_spent_minutes"))
        pin_time = str(form.get("pin_time", ""))
        if pin_time.strip():
            # La date programmée (si renseignée) prime sur le jour affiché : épingler
            # une tâche programmée pour un autre jour doit poser l'heure fixe CE
            # jour-là, pas sur la page actuellement consultée (`pin_day`).
            target_day = (
                task.scheduled_date or _optional_date(form.get("pin_day")) or date.today()
            )
            try:
                hour, minute = (int(part) for part in pin_time.strip().split(":", 1))
                task.pinned_start = datetime.combine(target_day, dt_time(hour=hour, minute=minute))
            except (ValueError, TypeError):
                pass  # heure invalide : on ignore, cohérent avec la validation minimale de l'app
        else:
            task.pinned_start = None
        ticket_id = _optional_int(form.get("linked_ticket_id"))
        task.linked_ticket_id = (
            ticket_id
            if ticket_id is not None and pilotage_session is not None
            and pilotage_session.get(LinkedTicket, ticket_id) is not None
            else None
        )

        # Sous-tâches en lot : une ligne non vide = une sous-tâche native de plus.
        for line in str(form.get("new_subtasks", "")).splitlines():
            subtask_title = line.strip()
            if subtask_title:
                tasks_session.add(Task(title=subtask_title, parent_id=task_id, source="native"))

        # Bloqueurs : l'ensemble soumis est la cible complète, on calcule le diff.
        target_blocker_ids = {
            bid for raw in form.getlist("blocker_ids")
            if (bid := _optional_int(raw)) is not None and bid != task_id
        }
        existing_deps = list(
            tasks_session.scalars(
                select(TaskDependency).where(TaskDependency.task_id == task_id)
            )
        )
        existing_blocker_ids = {d.blocker_id for d in existing_deps}
        for dep in existing_deps:
            if dep.blocker_id not in target_blocker_ids:
                tasks_session.delete(dep)
        edges = [
            (d.task_id, d.blocker_id) for d in tasks_session.scalars(select(TaskDependency))
        ]
        for blocker_id in target_blocker_ids - existing_blocker_ids:
            if tasks_session.get(Task, blocker_id) is None:
                continue
            if would_create_cycle(edges, task_id, blocker_id):
                continue  # ignoré silencieusement : le reste de l'enregistrement réussit quand même
            tasks_session.add(TaskDependency(task_id=task_id, blocker_id=blocker_id))
            edges.append((task_id, blocker_id))

        tasks_session.commit()
    return RedirectResponse("/kairos", status_code=303)


@app.post("/kairos/tasks/{task_id:int}/done")
def toggle_task_done(request: Request) -> Response:
    """Bascule fait ↔ à faire. Terminer une récurrente crée l'occurrence suivante."""
    task_id = request.path_params["task_id"]
    with _request_session(get_tasks_session) as tasks_session:
        task = tasks_session.get(Task, task_id)
        if task is not None:
            if task.status == "todo":
                task.status = "done"
                # Arrêter le chrono éventuellement en cours sur cette tâche terminée.
                now = datetime.now(timezone.utc)
                for session in tasks_session.scalars(
                    select(WorkSession).where(
                        WorkSession.task_id == task_id, WorkSession.ended_at.is_(None)
                    )
                ):
                    session.ended_at = now
                spawn_next_occurrence(tasks_session, task)
            elif task.status == "done":
                task.status = "todo"
            tasks_session.commit()
    return _kairos_action_response(request)


@app.post("/kairos/tasks/{task_id:int}/snooze")
def snooze_task(request: Request) -> Response:
    """« Décaler au prochain jour ouvré » : avance la deadline (week-ends et jours
    fériés sautés — un vendredi décale à lundi, pas à samedi)."""
    with _request_session(get_tasks_session) as tasks_session:
        task = tasks_session.get(Task, request.path_params["task_id"])
        if task is not None:
            settings = get_settings()
            task.deadline = next_snooze_date(task.deadline, date.today(), settings.holiday_set)
            tasks_session.commit()
    return _kairos_action_response(request)


@app.post("/kairos/tasks/{task_id:int}/delete")
def delete_task(request: Request) -> RedirectResponse:
    """Supprime une tâche native ; archive une tâche SP (le sync la recréerait sinon)."""
    task_id = request.path_params["task_id"]
    with _request_session(get_tasks_session) as tasks_session:
        task = tasks_session.get(Task, task_id)
        if task is not None:
            if task.source == "native":
                # Nettoyer les dépendances où la tâche figure (bloquée ou bloquante), sinon
                # des arêtes orphelines subsisteraient.
                for dep in tasks_session.scalars(
                    select(TaskDependency).where(
                        (TaskDependency.task_id == task_id)
                        | (TaskDependency.blocker_id == task_id)
                    )
                ):
                    tasks_session.delete(dep)
                tasks_session.delete(task)
            else:
                task.status = "archived"
            tasks_session.commit()
    return RedirectResponse("/kairos", status_code=303)


@app.post("/kairos/blocks")
async def create_manual_block(request: Request) -> RedirectResponse:
    """Ajoute un créneau : indisponibilité (réunion) ou bloc deep-work protégé.

    Un bloc deep-work (case cochée) réserve la fenêtre à une seule tâche, sans
    fragmentation ; sinon c'est un créneau occupé que l'ordonnancement contourne.

    ``recurrence`` (phase 13) : le créneau saisi devient le **modèle** (heure de
    début/fin canonique) — ex. un bloc déjeuner quotidien, un bloc deep-work chaque
    mardi. Whitelist côté route (même patron que ``recurrence`` sur une tâche) ;
    aucune occurrence n'est jamais persistée, voir ``expand_recurring_blocks``.
    """
    form = await request.form()
    try:
        start_at = datetime.fromisoformat(str(form.get("start") or ""))
        end_at = datetime.fromisoformat(str(form.get("end") or ""))
    except ValueError:
        return RedirectResponse("/kairos", status_code=303)
    if end_at > start_at:
        kind = "deepwork" if form.get("deepwork") else "busy"
        recurrence = str(form.get("recurrence", ""))
        recurrence = recurrence if recurrence in BLOCK_RECURRENCE_RULES else ""
        with _request_session(get_tasks_session) as tasks_session:
            tasks_session.add(
                TimeBlock(title=str(form.get("title", "")), start=start_at, end=end_at,
                          source="manual", kind=kind, recurrence=recurrence)
            )
            tasks_session.commit()
    return RedirectResponse("/kairos", status_code=303)


@app.post("/kairos/blocks/{block_id:int}/edit")
async def edit_manual_block(request: Request) -> RedirectResponse:
    """Édite un créneau manuel existant (titre, horaires, deep-work, récurrence).

    Pour un créneau **récurrent**, la ligne éditée est le **modèle** : la modification
    porte donc sur **toutes** ses occurrences (aucune occurrence n'est persistée
    séparément). Seuls les blocs ``source='manual'`` sont éditables (les créneaux
    TimeTree sont transitoires, jamais en base). Horaires invalides → ignorés.
    """
    form = await request.form()
    with _request_session(get_tasks_session) as tasks_session:
        block = tasks_session.get(TimeBlock, request.path_params["block_id"])
        if block is None or block.source != "manual":
            return RedirectResponse("/kairos", status_code=303)
        try:
            start_at = datetime.fromisoformat(str(form.get("start") or ""))
            end_at = datetime.fromisoformat(str(form.get("end") or ""))
        except ValueError:
            return RedirectResponse("/kairos", status_code=303)
        if end_at > start_at:
            recurrence = str(form.get("recurrence", ""))
            block.title = str(form.get("title", ""))
            block.start = start_at
            block.end = end_at
            block.kind = "deepwork" if form.get("deepwork") else "busy"
            block.recurrence = recurrence if recurrence in BLOCK_RECURRENCE_RULES else ""
            tasks_session.commit()
    return RedirectResponse("/kairos", status_code=303)


@app.post("/kairos/blocks/{block_id:int}/delete")
def delete_manual_block(request: Request) -> RedirectResponse:
    """Supprime un créneau manuel (pour un récurrent : le modèle, donc toutes ses
    occurrences). Sans effet sur un bloc non-manuel ou inexistant."""
    with _request_session(get_tasks_session) as tasks_session:
        block = tasks_session.get(TimeBlock, request.path_params["block_id"])
        if block is not None and block.source == "manual":
            tasks_session.delete(block)
            tasks_session.commit()
    return RedirectResponse("/kairos", status_code=303)

