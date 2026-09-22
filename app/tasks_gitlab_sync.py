"""Synchronisation des issues GitLab assignées → base tâches native.

Upsert **pur** (aucune E/S réseau ou base côté source) : reçoit une liste
d'issues déjà résolues par l'appelant, quelle que soit leur origine —
``app/pilotage_link.py`` (cache local entretenu par pilotage, zéro appel réseau,
à privilégier) ou ``app/gitlab_direct.py`` (appel direct à l'API GitLab, pour un
collègue sans l'outil de pilotage). Voir ``app/main.py`` pour le choix de source.
Idempotent, priorité native jamais écrasée, disparue/fermée → archivée.

Périmètre (voir docs/spec/integrations-externes.md) : **tous les projets fournis par l'appelant**
(élargi depuis le seul ``GITLAB_PROJECT`` de la phase 4), import **indépendant**
(pas de lien vers ``Ticket``/dette technique — deux bases séparées ; la liaison
manuelle en lecture seule est un champ séparé, ``Task.linked_ticket_id``).
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import date, datetime, timezone
from typing import Protocol, Sequence

from sqlalchemy import select
from sqlalchemy.orm import Session

from .tasks_models import Task, TaskSyncMeta

SOURCE = "gitlab"


class GitLabIssueLike(Protocol):
    """Forme minimale attendue d'une issue, quelle que soit sa source —
    ``pilotage_link.CachedGitLabIssue`` et ``gitlab_direct.GitLabIssue`` s'y
    conforment toutes les deux (structural typing, aucun couplage requis)."""

    project: str
    iid: int
    title: str
    state: str
    due_date: str

    @property
    def assignee_list(self) -> list[str]: ...


@dataclass
class SyncResult:
    ok: bool
    detail: str
    count: int


def _parse_due_date(value: str) -> date | None:
    if not value:
        return None
    try:
        return date.fromisoformat(value)
    except ValueError:
        return None


def _qualified_id(project: str, iid: int) -> str:
    """``external_id`` unique entre projets (un ``iid`` seul ne l'est que par projet)."""
    return f"{project}#{iid}"


def issue_web_url(gitlab_base_url: str, task: Task) -> str:
    """URL web de l'issue GitLab d'origine d'une tâche importée (issue #33).

    Fonction **pure** : reconstruit l'adresse à partir de ce qui est déjà en
    base, sans aucun appel réseau — `Task.external_id` porte le couple
    ``projet#iid`` (voir ``_qualified_id``) et l'URL de l'instance vient des
    réglages. Une issue GitLab vit toujours à
    ``{instance}/{chemin/du/projet}/-/issues/{iid}``.

    Retourne ``""`` (donc : étiquette de projet non cliquable, jamais un lien
    mort) dans tous les cas où l'adresse n'est pas reconstructible avec
    certitude — tâche non importée de GitLab, URL d'instance non renseignée,
    `external_id` absent ou dont le suffixe n'est pas un numéro d'issue.

    Tolère l'ancien format non qualifié (phase 4 : `external_id` = `iid` brut,
    sans ``#``) en prenant le projet dans `project_tag`, que la synchro
    renseigne dans les deux formats — une tâche jamais resynchronisée depuis la
    migration reste donc cliquable.
    """
    if task.source != SOURCE or not gitlab_base_url or not task.external_id:
        return ""
    project, separator, iid = task.external_id.rpartition("#")
    if not separator:
        project, iid = task.project_tag, task.external_id
    if not project or not iid.isdigit():
        return ""
    return f"{gitlab_base_url.rstrip('/')}/{project}/-/issues/{iid}"


def sync_assigned_gitlab_tasks(
    issues: Sequence[GitLabIssueLike],
    tasks_session: Session,
    assignee_username: str,
) -> SyncResult:
    """Upsert les issues ouvertes assignées à ``assignee_username``.

    Désactivé (no-op ``ok=True``) si ``assignee_username`` est vide. Fonction
    **pure** côté source : ``issues`` est déjà la liste résolue par l'appelant
    (cache pilotage ou appel direct GitLab) — aucune E/S ici, la dégradation en
    cas d'échec de récupération est gérée en amont (voir ``app/main.py``).
    """
    if not assignee_username:
        return SyncResult(ok=True, detail="", count=0)

    assigned = [
        i for i in issues if i.state == "opened" and assignee_username in i.assignee_list
    ]

    gitlab_tasks = list(tasks_session.scalars(select(Task).where(Task.source == SOURCE)))
    existing = {task.external_id: task for task in gitlab_tasks}
    # Tâches synchronisées sous l'ancien format (phase 4, un seul projet à la
    # fois) : external_id = str(iid) brut, non qualifié par projet.
    legacy = {
        (task.project_tag, task.external_id): task
        for task in gitlab_tasks
        if task.external_id and "#" not in task.external_id
    }
    seen_external_ids: set[str] = set()

    for issue in assigned:
        qualified = _qualified_id(issue.project, issue.iid)
        seen_external_ids.add(qualified)
        local = existing.get(qualified)
        if local is None:
            # Rebaptise en place une tâche déjà synchronisée sous l'ancien format
            # (même projet, même iid brut) plutôt que de la traiter comme
            # disparue et d'en recréer une neuve — préserve priorité et historique.
            local = legacy.get((issue.project, str(issue.iid)))
            if local is not None:
                local.external_id = qualified
            else:
                local = Task(source=SOURCE, external_id=qualified)
                tasks_session.add(local)
            existing[qualified] = local
        local.title = f"#{issue.iid} {issue.title}"
        local.deadline = _parse_due_date(issue.due_date)
        local.project_tag = issue.project
        local.status = "todo"
        # Priorité jamais écrasée : champ natif, posé uniquement depuis le dashboard.

    # Itère sur les objets (pas sur les clés du dict `existing`, qui garderait une
    # entrée périmée sous l'ancien external_id pour une tâche venant d'être
    # rebaptisée en place, et la réarchiverait à tort juste après l'avoir marquée
    # `todo`) : on relit l'`external_id` courant, à jour après un éventuel rekey.
    for task in gitlab_tasks:
        if task.external_id not in seen_external_ids:
            task.status = "archived"

    tasks_session.commit()
    write_sync_meta(tasks_session, ok=True, detail="", count=len(assigned))
    return SyncResult(ok=True, detail="", count=len(assigned))


def write_sync_meta(session: Session, *, ok: bool, detail: str, count: int) -> None:
    """Enregistre le résultat d'une tentative de synchro (succès ou échec de
    récupération en amont) — observabilité, ne conditionne aucun comportement."""
    meta = session.scalars(
        select(TaskSyncMeta).where(TaskSyncMeta.source == SOURCE)
    ).first()
    if meta is None:
        meta = TaskSyncMeta(source=SOURCE)
        session.add(meta)
    meta.last_synced_at = datetime.now(timezone.utc)
    meta.last_outcome = "ok" if ok else "error"
    meta.last_detail = detail
    meta.item_count = count
    session.commit()
