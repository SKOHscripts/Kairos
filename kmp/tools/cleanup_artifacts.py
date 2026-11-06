"""Supprime les artefacts GitHub Actions des runs anciens (docs/spec/distribution.md § Artefacts).

Règle : pour chaque workflow, seuls les artefacts des KEEP_RUNS derniers runs **terminés** qui en ont sont gardés (2 par
défaut) ; ceux des runs plus anciens sont supprimés. Les artefacts d'un run encore en cours ne sont jamais touchés (une
release en cours a besoin des siens) ; ceux dont le run n'existe plus sont supprimés ; les artefacts expirés sont ignorés
(GitHub les efface seul).

Lancé par `.github/workflows/artifacts-cleanup.yml` à la fin de chaque workflow qui produit des artefacts. Sans
dépendance (bibliothèque standard), jeton dans `GH_TOKEN`, dépôt dans `GITHUB_REPOSITORY` :

    GH_TOKEN=… GITHUB_REPOSITORY=owner/repo python3 kmp/tools/cleanup_artifacts.py [--dry-run]
"""
from __future__ import annotations

import json
import os
import sys
import urllib.error
import urllib.request
from dataclasses import dataclass

API = "https://api.github.com"


@dataclass(frozen=True)
class Artifact:
    id: int
    name: str
    run_id: int | None


@dataclass(frozen=True)
class Run:
    id: int
    workflow_id: int
    status: str
    created_at: str  # ISO 8601 : l'ordre des chaînes est l'ordre chronologique


def plan(artifacts: list[Artifact], runs: dict[int, Run | None], keep: int) -> list[Artifact]:
    """Les artefacts à supprimer (pur). [runs] : run de chaque `run_id`, `None` si le run n'existe plus."""
    doomed: list[Artifact] = []
    by_workflow: dict[int, list[Run]] = {}
    for artifact in artifacts:
        run = runs.get(artifact.run_id) if artifact.run_id is not None else None
        if run is None:
            doomed.append(artifact)
        elif run.status == "completed":
            by_workflow.setdefault(run.workflow_id, [])
            if run not in by_workflow[run.workflow_id]:
                by_workflow[run.workflow_id].append(run)
    kept_runs: set[int] = set()
    for workflow_runs in by_workflow.values():
        newest = sorted(workflow_runs, key=lambda r: (r.created_at, r.id), reverse=True)[:keep]
        kept_runs.update(r.id for r in newest)
    for artifact in artifacts:
        run = runs.get(artifact.run_id) if artifact.run_id is not None else None
        if run is not None and run.status == "completed" and run.id not in kept_runs:
            doomed.append(artifact)
    return doomed


def _request(method: str, path: str, token: str) -> dict | None:
    request = urllib.request.Request(
        API + path, method=method,
        headers={"Authorization": f"Bearer {token}", "Accept": "application/vnd.github+json", "X-GitHub-Api-Version": "2022-11-28"},
    )
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            body = response.read()
            return json.loads(body) if body else None
    except urllib.error.HTTPError as error:
        if error.code == 404:
            return None
        raise


def main() -> None:
    dry_run = "--dry-run" in sys.argv
    token = os.environ["GH_TOKEN"]
    repo = os.environ["GITHUB_REPOSITORY"]
    keep = int(os.environ.get("KEEP_RUNS", "2"))
    artifacts: list[Artifact] = []
    page = 1
    while True:
        data = _request("GET", f"/repos/{repo}/actions/artifacts?per_page=100&page={page}", token) or {}
        batch = data.get("artifacts", [])
        for a in batch:
            if not a.get("expired"):
                artifacts.append(Artifact(a["id"], a["name"], (a.get("workflow_run") or {}).get("id")))
        if len(batch) < 100:
            break
        page += 1
    runs: dict[int, Run | None] = {}
    for run_id in {a.run_id for a in artifacts if a.run_id is not None}:
        r = _request("GET", f"/repos/{repo}/actions/runs/{run_id}", token)
        runs[run_id] = Run(r["id"], r["workflow_id"], r["status"], r["created_at"]) if r else None
    doomed = plan(artifacts, runs, keep)
    for artifact in doomed:
        print(f"{'(essai) ' if dry_run else ''}supprimé : {artifact.name} (#{artifact.id}, run {artifact.run_id})")
        if not dry_run:
            _request("DELETE", f"/repos/{repo}/actions/artifacts/{artifact.id}", token)
    print(f"{len(artifacts)} artefacts, {len(doomed)} supprimés, {keep} derniers runs gardés par workflow.")


if __name__ == "__main__":
    main()
