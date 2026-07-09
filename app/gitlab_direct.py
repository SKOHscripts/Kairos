"""Import direct, en lecture seule, des issues GitLab assignées — sans pilotage.

Seam symétrique de ``calendar/timetree_source.py`` : point d'entrée unique
:func:`fetch_assigned_issues`, jamais d'exception levée (dégradation propre en cas
d'échec réseau/auth, jamais une page 500), cache en mémoire (par processus) avec
TTL ``settings.gitlab_cache_ttl_minutes`` pour éviter d'appeler l'API à chaque
chargement de « Kairos ».

Utilisé uniquement quand ``settings.pilotage_database_path`` est vide (collègue
sans l'outil de pilotage) : voir ``app/pilotage_link.py`` pour l'autre source,
qui relit un cache local déjà entretenu par pilotage — zéro appel réseau, à
privilégier quand elle est disponible (voir ``Settings.gitlab_direct_configured``).

Client REST volontairement réduit au strict nécessaire (lister les issues ouvertes
assignées à un utilisateur, sur un ou plusieurs projets) — même patron
d'authentification (en-tête ``PRIVATE-TOKEN``) que le client GitLab complet de
`pilotage-pleiade-gitlab`, sans sa partie écriture/GraphQL, inutile ici.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from datetime import datetime, timezone
from urllib.parse import quote

import httpx

from .config import Settings

__all__ = [
    "GitLabIssue",
    "GitLabFetchResult",
    "GitLabClientError",
    "GitLabClient",
    "fetch_assigned_issues",
]


@dataclass
class GitLabIssue:
    """Issue GitLab assignée, dans la forme attendue par ``tasks_gitlab_sync``
    (voir ``GitLabIssueLike``) — mêmes champs que ``CachedGitLabIssue``."""

    project: str
    iid: int
    title: str
    state: str
    assignees: list[str] = field(default_factory=list)
    due_date: str = ""

    @property
    def assignee_list(self) -> list[str]:
        return self.assignees


@dataclass
class GitLabFetchResult:
    ok: bool
    issues: list[GitLabIssue] = field(default_factory=list)
    detail: str = ""


class GitLabClientError(Exception):
    """Échec d'appel à l'API GitLab (réseau, authentification, projet introuvable...)."""


class GitLabClient:
    """Client REST minimal, strictement lecture seule (aucun POST/PATCH/DELETE)."""

    def __init__(
        self, base_url: str, token: str, *, client: httpx.Client | None = None,
        timeout: float = 10.0
    ) -> None:
        self._base_url = base_url.rstrip("/")
        self._token = token
        self._client = client or httpx.Client(timeout=timeout)

    def list_open_issues_assigned_to(self, project: str, username: str) -> list[GitLabIssue]:
        """Issues ouvertes du projet assignées à ``username`` (pagination suivie)."""
        encoded_project = quote(project, safe="")
        issues: list[GitLabIssue] = []
        page = 1
        while True:
            try:
                response = self._client.get(
                    f"{self._base_url}/api/v4/projects/{encoded_project}/issues",
                    params={
                        "assignee_username": username,
                        "state": "opened",
                        "per_page": 100,
                        "page": page,
                    },
                    headers={"PRIVATE-TOKEN": self._token},
                )
                response.raise_for_status()
            except httpx.HTTPError as exc:
                raise GitLabClientError(f"{project} : {exc}") from exc
            batch = response.json()
            issues.extend(
                GitLabIssue(
                    project=project,
                    iid=item["iid"],
                    title=item.get("title", ""),
                    state=item.get("state", ""),
                    assignees=[a["username"] for a in item.get("assignees", [])],
                    due_date=item.get("due_date") or "",
                )
                for item in batch
            )
            if len(batch) < 100:
                break
            page += 1
        return issues


_cache: dict[tuple, tuple[datetime, GitLabFetchResult]] = {}


def fetch_assigned_issues(settings: Settings, *, client: httpx.Client | None = None) -> GitLabFetchResult:
    """Issues ouvertes assignées à ``settings.gitlab_assignee_username``, tous
    projets de ``settings.gitlab_project_list`` confondus. Ne lève jamais."""
    if not settings.gitlab_direct_configured:
        return GitLabFetchResult(ok=False, issues=[], detail="Import direct GitLab non configuré.")

    cache_key = (
        settings.gitlab_url, settings.gitlab_projects, settings.gitlab_assignee_username
    )
    cached = _cache.get(cache_key)
    now = datetime.now(timezone.utc)
    if cached is not None:
        cached_at, cached_result = cached
        if (now - cached_at).total_seconds() < settings.gitlab_cache_ttl_minutes * 60:
            return cached_result

    result = _fetch_from_gitlab(settings, client=client)
    _cache[cache_key] = (now, result)
    return result


def _fetch_from_gitlab(settings: Settings, *, client: httpx.Client | None) -> GitLabFetchResult:
    gitlab_client = GitLabClient(settings.gitlab_url, settings.gitlab_token_effective, client=client)
    issues: list[GitLabIssue] = []
    try:
        for project in settings.gitlab_project_list:
            issues.extend(
                gitlab_client.list_open_issues_assigned_to(
                    project, settings.gitlab_assignee_username
                )
            )
    except GitLabClientError as exc:
        return GitLabFetchResult(ok=False, issues=[], detail=f"Échec de l'appel à l'API GitLab : {exc}")
    return GitLabFetchResult(ok=True, issues=issues, detail="")
