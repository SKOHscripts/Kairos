"""Modèle de données de « Kairos » : tâches et créneaux occupés.

Base SQLAlchemy dédiée (``TasksBase``), volontairement séparée de celle du suivi
dette technique (``app.models.Base``) : deux fichiers SQLite distincts, aucun import
croisé entre les deux jeux de modèles.
"""

from __future__ import annotations

from datetime import date, datetime, timezone

from sqlalchemy import Date, DateTime, Integer, String, Text, UniqueConstraint
from sqlalchemy.orm import DeclarativeBase, Mapped, mapped_column


def _now() -> datetime:
    return datetime.now(timezone.utc)


class TasksBase(DeclarativeBase):
    pass


class Task(TasksBase):
    """Tâche affichée dans « Kairos », native ou importée (GitLab assigné).

    ``priority`` est un champ **natif** de l'outil, posé depuis le dashboard ;
    une tâche importée arrive toujours avec ``priority=None`` (non renseignée,
    distincte de la priorité la plus basse). La synchronisation ne réécrit jamais
    ce champ sur une tâche déjà existante (voir ``tasks_gitlab_sync.py``).
    """

    __tablename__ = "task"
    __table_args__ = (
        UniqueConstraint("source", "external_id", name="uq_task_source_external_id"),
    )

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    title: Mapped[str] = mapped_column(String(512), default="")
    description: Mapped[str] = mapped_column(Text, default="")
    # None = priorité non renseignée. Échelle basse = plus prioritaire (mêmes
    # conventions que `Ticket.priority_override` dans app/models.py).
    priority: Mapped[int | None] = mapped_column(Integer, nullable=True)
    deadline: Mapped[date | None] = mapped_column(Date, nullable=True)
    # Tag libre (texte), pas de FK vers `Ticket` en MVP (voir SPEC_KAIROS.md).
    project_tag: Mapped[str] = mapped_column(String(255), default="")
    # 'todo' | 'done' | 'archived' (archivée = disparue côté source externe, on ne
    # supprime jamais une tâche importée pour préserver l'historique de priorisation).
    status: Mapped[str] = mapped_column(String(16), default="todo", index=True)
    # 'native' | 'gitlab'
    source: Mapped[str] = mapped_column(String(32), default="native", index=True)
    # Identifiant côté source externe ; None pour une tâche native. NULL et non ""
    # pour ne pas déclencher la contrainte unique (source, external_id) entre tâches
    # natives : les NULL sont distincts au sens d'UNIQUE en SQLite.
    external_id: Mapped[str | None] = mapped_column(String(64), nullable=True, default=None)
    # --- Time blocking (phase 2) ---
    # Durée estimée en minutes ; None = non renseignée (repli sur le réglage
    # `default_task_duration_minutes` dans l'ordonnancement, jamais persisté).
    estimated_minutes: Mapped[int | None] = mapped_column(Integer, nullable=True)
    # Épinglage : la tâche est posée exactement à cette heure dans la journée,
    # l'ordonnancement automatique remplit autour. None = placement automatique.
    pinned_start: Mapped[datetime | None] = mapped_column(DateTime, nullable=True)
    # --- Hiérarchie / récurrence (phase 2) ---
    # Id local de la tâche mère (auto-référence, sans contrainte FK — cohérent avec
    # le style du reste du schéma). None = tâche de premier niveau.
    parent_id: Mapped[int | None] = mapped_column(Integer, nullable=True, index=True)
    # '' | 'daily' | 'weekdays' | 'weekly' | 'monthly' | 'monthly_on_day'. Les cinq
    # premières se recréent à la complétion (tasks_recurrence.spawn_next_occurrence) ;
    # 'monthly_on_day' est calendaire — générée par date, indépendamment de la
    # complétion (tasks_recurrence.ensure_calendar_occurrences), pour des obligations
    # calées sur un jour du mois (ex. « le 23 »).
    recurrence: Mapped[str] = mapped_column(String(16), default="")
    # --- Phase 4 : date programmée, récurrence calendaire ---
    # Quand l'utilisateur compte s'y mettre — distinct de `deadline` (l'échéance
    # réelle, imposée de l'extérieur). Les deux coexistent : `deadline` reste le
    # garde-fou qui ne laisse jamais rien filer, `scheduled_date` pilote la présence
    # dans l'agenda du jour (voir tasks_scheduling § éligibilité).
    scheduled_date: Mapped[date | None] = mapped_column(Date, nullable=True)
    # Jour du mois (1-31, borné en fin de mois) pour `recurrence='monthly_on_day'`.
    recurrence_day_of_month: Mapped[int | None] = mapped_column(Integer, nullable=True)
    # Période de l'occurrence calendaire générée (ex. "2026-07"), pour l'anti-doublon
    # de `ensure_calendar_occurrences` : "" pour toute tâche non calendaire.
    recurrence_period: Mapped[str] = mapped_column(String(16), default="")
    # --- Phase 5 : préparation de futures analyses (métadonnées pures) ---
    # '' | une clé de TASK_TYPE_LABELS. Purement informatif : aucun impact sur
    # l'ordonnancement ni sur aucune autre logique métier (voir SPEC_KAIROS.md
    # § Phase 5) — sert uniquement à catégoriser en vue d'analyses futures.
    task_type: Mapped[str] = mapped_column(String(32), default="")
    # Estimation agile en points de Fibonacci (échelle FIBONACCI_SCALE), en
    # complément de `estimated_minutes` — pas de conversion entre les deux,
    # `estimated_minutes` reste seul à piloter l'ordonnancement.
    fibonacci_points: Mapped[int | None] = mapped_column(Integer, nullable=True)
    # --- Phase 6 : liaison manuelle vers une fiche de dette technique ---
    # Référence locale vers `Ticket.id` (base `pilotage.db`), sans contrainte FK
    # cross-base (les deux bases SQLite restent des fichiers séparés — cohérent
    # avec `parent_id`/`blocker_id`). Purement une référence en lecture : aucune
    # écriture vers Redmine/GitLab, aucun impact sur l'ordonnancement.
    linked_ticket_id: Mapped[int | None] = mapped_column(Integer, nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime, default=_now)
    updated_at: Mapped[datetime] = mapped_column(
        DateTime, default=_now, onupdate=_now
    )


# Échelle fixe (suite de Fibonacci classique) pour `Task.fibonacci_points` — pas de
# champ libre, un `<select>` restreint à ces valeurs côté formulaire d'édition.
FIBONACCI_SCALE: tuple[int, ...] = (1, 2, 3, 5, 8, 13, 21)

# Typologie fixe pour `Task.task_type` — valeur → libellé affiché. Source unique
# réutilisée par la route (validation, patron identique à `recurrence`) et par le
# template (options du `<select>` et libellé des badges).
TASK_TYPE_LABELS: dict[str, str] = {
    "dev": "Développement",
    "revue_code": "Revue de code",
    "reunion": "Réunion",
    "documentation": "Documentation",
    "administratif": "Administratif",
    "veille": "Veille/formation",
    "pilotage": "Pilotage/dette technique",
}


class TimeBlock(TasksBase):
    """Créneau de la journée : occupé (réunion / TimeTree) ou bloc deep-work protégé.

    ``kind`` distingue :
    - ``'busy'`` (défaut) : indisponible — réunion saisie à la main ou événement
      TimeTree ; l'ordonnancement le contourne.
    - ``'deepwork'`` : fenêtre **réservée** au travail profond — disponible mais
      **exclusive** : l'ordonnancement y place une seule tâche, non fragmentée, et les
      autres tâches auto la contournent (voir ``tasks_scheduling``).

    ``recurrence`` (phase 13, blocs manuels uniquement) : '' | 'daily' | 'weekdays' |
    'weekly'. La ligne stockée est le **modèle** (heure de début/fin canonique, premier
    jour) ; les occurrences futures ne sont **jamais persistées** — elles sont projetées
    à la volée pour la plage affichée par ``tasks_recurrence.expand_recurring_blocks``,
    à la manière d'une règle plutôt que d'une génération à la complétion (les blocs
    n'ont pas de statut « fait », contrairement aux tâches).
    """

    __tablename__ = "time_block"

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    title: Mapped[str] = mapped_column(String(512), default="")
    start: Mapped[datetime] = mapped_column(DateTime, index=True)
    end: Mapped[datetime] = mapped_column(DateTime)
    # 'manual' | 'timetree'
    source: Mapped[str] = mapped_column(String(32), default="manual", index=True)
    # 'busy' | 'deepwork'
    kind: Mapped[str] = mapped_column(String(16), default="busy")
    external_id: Mapped[str] = mapped_column(String(64), default="")
    recurrence: Mapped[str] = mapped_column(String(16), default="")
    created_at: Mapped[datetime] = mapped_column(DateTime, default=_now)


class TaskDependency(TasksBase):
    """Dépendance « bloqué par » entre deux tâches natives.

    Arête ``task_id`` (bloquée) → ``blocker_id`` (bloquante). Une tâche est
    « bloquée » tant qu'au moins un de ses bloqueurs est encore à faire (voir
    ``app/tasks_dependencies.py``). Auto-référence logique vers ``task.id``, sans
    contrainte FK — cohérent avec le reste du schéma (``Task.parent_id``).
    """

    __tablename__ = "task_dependency"
    __table_args__ = (
        UniqueConstraint("task_id", "blocker_id", name="uq_task_dependency_edge"),
    )

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    task_id: Mapped[int] = mapped_column(Integer, index=True)
    blocker_id: Mapped[int] = mapped_column(Integer, index=True)
    created_at: Mapped[datetime] = mapped_column(DateTime, default=_now)


class WorkSession(TasksBase):
    """Session de travail chronométrée sur une tâche (suivi du temps réel).

    Une session ``ended_at IS NULL`` est **en cours** (minuteur qui tourne). Invariant
    appliqué au démarrage : au plus une session ouverte à la fois (démarrer une
    nouvelle ferme l'éventuelle précédente). Sert à comparer le temps réel passé à
    l'estimation, et à afficher un minuteur vivant sur la tâche en cours.
    """

    __tablename__ = "work_session"

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    task_id: Mapped[int] = mapped_column(Integer, index=True)
    started_at: Mapped[datetime] = mapped_column(DateTime, default=_now)
    ended_at: Mapped[datetime | None] = mapped_column(DateTime, nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime, default=_now)


class TaskSyncMeta(TasksBase):
    """Méta du dernier fetch réussi par source (mirror de `GitLabRefreshMeta`).

    Une ligne par source ('gitlab' | 'timetree') : sert de base au TTL de
    rafraîchissement (fetch-on-load, pas de scheduler) et à l'affichage d'un
    avertissement quand la dernière synchro a échoué.
    """

    __tablename__ = "task_sync_meta"
    __table_args__ = (UniqueConstraint("source", name="uq_task_sync_meta_source"),)

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    source: Mapped[str] = mapped_column(String(32), default="")
    last_synced_at: Mapped[datetime | None] = mapped_column(DateTime, nullable=True)
    # 'ok' | 'error'
    last_outcome: Mapped[str] = mapped_column(String(16), default="ok")
    last_detail: Mapped[str] = mapped_column(Text, default="")
    item_count: Mapped[int] = mapped_column(Integer, default=0)
