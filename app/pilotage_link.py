"""Intégration OPTIONNELLE, en lecture seule, avec l'outil de pilotage MSI.

« Kairos » est autonome (phase 14) : ce module est le **seul** point de contact
avec la base ``pilotage.db`` de `pilotage-pleiade-gitlab`, activé uniquement si
``Settings.pilotage_database_path`` est renseigné. Il fournit :

- :class:`CachedGitLabIssue` — projection minimale de la table ``gitlab_issue_cache``
  (issues GitLab mises en cache par l'onglet Pilotage GitLab), pour l'import des
  issues assignées (:mod:`app.tasks_gitlab_sync`) ;
- :class:`LinkedTicket` — projection minimale de la table ``ticket`` (fiches de dette
  technique), pour la liaison manuelle « Fiche liée » du panneau d'édition.

Seules les colonnes lues sont mappées : SQLAlchemy ignore les autres, aucune
migration ni écriture n'est jamais faite ici (métadonnées séparées, jamais de
``create_all`` sur la base pilotage). Sans le fichier ou sans le réglage, tout
dégrade proprement : la dépendance FastAPI rend ``None`` et les fonctionnalités
concernées disparaissent de l'interface sans erreur.
"""

from __future__ import annotations

from collections.abc import Iterator
from pathlib import Path

from sqlalchemy import Integer, String, create_engine
from sqlalchemy.orm import DeclarativeBase, Mapped, Session, mapped_column, sessionmaker

from .config import get_settings


class PilotageBase(DeclarativeBase):
    """Métadonnées dédiées : jamais de create_all — la base appartient à pilotage."""


class CachedGitLabIssue(PilotageBase):
    """Projection lecture seule de ``gitlab_issue_cache`` (colonnes utiles au sync)."""

    __tablename__ = "gitlab_issue_cache"

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    project: Mapped[str] = mapped_column(String(255), default="")
    iid: Mapped[int] = mapped_column(Integer)
    title: Mapped[str] = mapped_column(String(512), default="")
    state: Mapped[str] = mapped_column(String(16), default="")
    assignees: Mapped[str] = mapped_column(String(512), default="")  # csv usernames
    due_date: Mapped[str] = mapped_column(String(32), default="")

    @property
    def assignee_list(self) -> list[str]:
        return [s for s in (self.assignees or "").split(",") if s]


class LinkedTicket(PilotageBase):
    """Projection lecture seule de ``ticket`` (colonnes utiles au badge « Fiche liée »)."""

    __tablename__ = "ticket"

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    pleiade_id: Mapped[int] = mapped_column(Integer)
    pleiade_subject: Mapped[str] = mapped_column(String(512), default="")
    gitlab_web_url: Mapped[str] = mapped_column(String(512), default="")


_engine = None


def _get_engine():
    """Engine paresseux vers la base pilotage ; None si non configurée/introuvable."""
    global _engine
    settings = get_settings()
    if not settings.pilotage_configured:
        return None
    if not Path(settings.pilotage_database_path).exists():
        return None  # base pas encore créée côté pilotage : dégradation propre
    if _engine is None:
        _engine = create_engine(
            f"sqlite:///{settings.pilotage_database_path}",
            connect_args={"check_same_thread": False},
        )
    return _engine


def get_pilotage_session() -> Iterator[Session | None]:
    """Dépendance FastAPI : session lecture seule sur la base pilotage, ou None."""
    engine = _get_engine()
    if engine is None:
        yield None
        return
    session = sessionmaker(bind=engine, expire_on_commit=False)()
    try:
        yield session
    finally:
        session.close()
