"""Modèle des réglages de « Kairos ».

Les valeurs sont chargées depuis un fichier JSON local (dossier utilisateur de
l'OS, voir `app/settings_store.py`), éditable depuis la page `/kairos/settings`
de l'application — plus de `.env` à copier/éditer à la main (une ancienne
installation `.env` est importée une seule fois automatiquement, voir
`settings_store.py`).
"""

from __future__ import annotations

import dataclasses
from functools import lru_cache
from pathlib import Path

from .settings_fields import (
    Field,
    SettingsValidationError,
    build_field_registry,
    validate_fields,
)


def _default_tasks_database_path() -> str:
    """Chemin par défaut de la base tâches : dossier de données de l'OS (voir
    `app/settings_store.py::data_dir`), pas le répertoire de lancement — un
    exécutable packagé n'a pas de répertoire de travail fiable."""
    from platformdirs import user_data_dir

    return str(Path(user_data_dir("Kairos", appauthor=False)) / "tasks.db")


@dataclasses.dataclass
class Settings:
    """Paramètres de l'outil « Kairos » (autonome depuis la phase 14).

    Modifiables depuis la page Réglages de l'application ; persistés par
    `app/settings_store.py`. Les identifiants ne doivent jamais être exposés
    tels quels dans l'interface (voir `app/secret_store.py`).

    Dataclass « maison » (plus de Pydantic depuis le portage Android, voir
    `app/settings_fields.py`) : types et bornes validés à la construction
    (`SettingsValidationError`), registre `Settings.model_fields` conservé sous
    le même nom pour la page Réglages et `settings_store`.
    """

    # Base SQLite des tâches. Par défaut : dossier de données standard de l'OS
    # (ex. ~/.local/share/Kairos sous Linux, %LOCALAPPDATA%\Kairos sous Windows).
    # Redémarrage de Kairos requis après modification (le moteur de base de
    # données est initialisé une seule fois au démarrage du processus).
    tasks_database_path: str = Field(
        default_factory=_default_tasks_database_path,
        description=(
            "Chemin de la base SQLite des tâches (créée automatiquement au premier "
            "démarrage). Redémarrage de Kairos requis après modification."
        ),
    )
    # --- Intégration OPTIONNELLE avec l'outil de pilotage MSI (lecture seule) ---
    # Chemin vers la base `pilotage.db` de pilotage-pleiade-gitlab. Si renseigné :
    # les issues GitLab qui te sont assignées (cache entretenu par l'onglet Pilotage
    # GitLab) apparaissent comme tâches, et le panneau d'édition propose la liaison
    # manuelle vers une fiche de dette technique. Vide = fonctionnalités désactivées
    # proprement (aucune erreur) — cas normal d'un collègue sans l'outil de pilotage.
    pilotage_database_path: str = Field(
        default="",
        description=(
            "Chemin absolu vers la base pilotage.db de pilotage-pleiade-gitlab "
            "(dépôt séparé), si tu utilises aussi cet outil. Donne accès à l'import "
            "des issues GitLab assignées (cache, zéro appel réseau) et à la liaison "
            "manuelle vers une fiche de dette technique. Vide = désactivé (cas normal)."
        ),
    )
    # Nom d'utilisateur GitLab (assignee) dont les issues ouvertes sont importées
    # comme tâches (source='gitlab'). Partagé par les deux intégrations GitLab
    # ci-dessous (cache pilotage ou import direct) : une seule identité GitLab à
    # renseigner, quelle que soit la source utilisée. Vide = import désactivé.
    gitlab_assignee_username: str = Field(
        default="",
        description=(
            "Nom d'utilisateur GitLab (assignee) dont les issues ouvertes sont "
            "importées comme tâches. Commun aux deux intégrations GitLab ci-dessous. "
            "Vide = import désactivé."
        ),
    )
    # --- Import direct des issues GitLab assignées (optionnel, lecture seule) ---
    # Utilisé quand `pilotage_database_path` est vide (cas normal d'un collègue sans
    # l'outil de pilotage) : appel direct, en lecture seule, à l'API REST GitLab —
    # aucune écriture, jamais. Si `pilotage_database_path` est renseigné, le cache
    # pilotage prime (zéro appel réseau) et cette intégration est ignorée.
    gitlab_url: str = Field(
        default="",
        description=(
            "URL de base de l'instance GitLab, pour l'import direct (lecture seule) "
            "des issues assignées — utilisé seulement si « base pilotage » ci-dessus "
            "est vide."
        ),
    )
    # Optionnel : si vide, le jeton est résolu depuis les moyens d'authentification
    # déjà configurés pour `git` sur ce poste (`git credential fill` — trousseau
    # GNOME/libsecret, Keychain, Windows Credential Manager... — puis `~/.netrc`
    # en repli), pour ne pas avoir à dupliquer un jeton en clair, ni le stocker
    # deux fois. Voir `gitlab_token_effective` / `app/git_credentials.py`.
    gitlab_token: str = Field(
        default="",
        description=(
            "Jeton d'accès personnel GitLab (scope read_api suffit), stocké dans le "
            "trousseau système si possible. Optionnel : laissé vide, Kairos tente de "
            "résoudre un jeton déjà configuré pour `git` sur ce poste (trousseau, "
            "~/.netrc)."
        ),
    )
    # Un ou plusieurs projets ("groupe/projet" ou id numérique), séparés par des virgules.
    gitlab_projects: str = Field(
        default="",
        description="Projet(s) GitLab (\"groupe/projet\" ou id numérique), séparés par des virgules.",
    )
    # Durée (minutes) de mise en cache des issues (anti rate-limiting), même patron
    # que le cache TimeTree ci-dessous.
    gitlab_cache_ttl_minutes: int = Field(
        default=5, ge=0,
        description="Durée (minutes) de mise en cache des issues GitLab (anti rate-limiting).",
    )
    # --- Calendrier personnel TimeTree ---
    # Identifiants utilisés par l'intégration TimeTree (API non-officielle). Jamais
    # exposés tels quels dans l'interface. Vides = calendrier désactivé (dégradation
    # propre, créneaux saisis à la main uniquement).
    timetree_email: str = Field(
        default="", description="Adresse e-mail du compte TimeTree (calendrier personnel).",
    )
    timetree_password: str = Field(
        default="",
        description="Mot de passe du compte TimeTree, stocké dans le trousseau système si possible.",
    )
    timetree_calendar_code: str = Field(
        default="", description="Code du calendrier TimeTree à exporter (visible dans ses réglages de partage).",
    )
    # Durée (minutes) de mise en cache des créneaux TimeTree (anti rate-limiting).
    timetree_cache_ttl_minutes: int = Field(
        default=30, ge=0,
        description="Durée (minutes) de mise en cache des créneaux TimeTree (anti rate-limiting).",
    )
    # --- Ordonnancement ---
    # Durée par défaut (minutes) d'une tâche sans estimation (jamais stockée).
    default_task_duration_minutes: int = Field(
        default=30, ge=1,
        description="Durée par défaut (minutes) attribuée à une tâche sans estimation (jamais stockée).",
    )
    # Marge (minutes) laissée après un créneau occupé (réunion 13h-14h → 14h05).
    meeting_buffer_minutes: int = Field(
        default=5, ge=0,
        description="Marge (minutes) laissée après un créneau occupé (réunion 13h-14h → tâche à 14h05).",
    )
    # Bornes (heures, 24h) de la journée de travail.
    workday_start_hour: int = Field(
        default=9, ge=0, le=23, description="Heure de début (0-23) de la journée de travail.",
    )
    workday_end_hour: int = Field(
        default=18, ge=0, le=23, description="Heure de fin (0-23) de la journée de travail.",
    )
    # Seuils (jours) de détection des tâches qui traînent : échéance/date programmée
    # dépassée au-delà de `stale_overdue_days`, ou tâche sans date non modifiée
    # depuis `stale_untouched_days`. Signal d'affichage seul, ne change jamais le tri.
    stale_overdue_days: int = Field(
        default=7, ge=0,
        description="Jours après l'échéance dépassée avant le badge « traîne depuis N j » (affichage seul).",
    )
    stale_untouched_days: int = Field(
        default=14, ge=0,
        description="Jours sans modification (tâche sans date) avant le badge « traîne depuis N j ».",
    )
    # Nombre de tâches à priorité maximale (P0 uniquement) au-delà duquel un
    # bandeau avertit que le signal de priorité se dilue.
    priority_overload_threshold: int = Field(
        default=5, ge=0,
        description="Nombre de tâches à priorité maximale (P0) au-delà duquel un bandeau de surcharge s'affiche.",
    )
    # --- Ordonnancement WSJF : score = coût du retard / effort ---
    # Base de la valeur EXPONENTIELLE d'un cran de priorité : `base ** (PRIORITY_MAX -
    # priorité)`, PRIORITY_MAX = 2 (barème P0/P1/P2). 4.0 : P0=16, P1=4, P2=1.
    # Baisser adoucit l'écart entre priorités, monter le durcit.
    priority_value_base: float = Field(
        default=4.0, gt=0,
        description=(
            "Base exponentielle d'un cran de priorité : base ** (2 - priorité) "
            "(barème P0/P1/P2). 4.0 → P0=16, P1=4, P2=1. Baisser adoucit l'écart, "
            "monter le durcit."
        ),
    )
    # Horizon (jours) où une échéance commence à peser (rampe linéaire en deçà).
    urgency_horizon_days: int = Field(
        default=14, ge=0,
        description="Jours avant échéance où la criticité temporelle commence à monter (rampe linéaire).",
    )
    # Poids max de la criticité temporelle (même unité que la valeur ; entre P1 et P0).
    urgency_peak: float = Field(
        default=8.0, ge=0,
        description="Poids maximal de la criticité temporelle (même unité que la valeur de priorité).",
    )
    # Effort d'une tâche sans points de Fibonacci ni estimation (dénominateur neutre).
    default_fibonacci_points: int = Field(
        default=3, ge=1,
        description="Effort (points de Fibonacci) attribué à une tâche sans estimation (dénominateur du score).",
    )
    # --- Creux de l'après-midi (« post-lunch dip », clin d'œil au nom de code 14h55) ---
    # Pendant une fenêtre creuse, l'ordonnancement gonfle l'effort effectif des tâches
    # COMPLEXES (points de Fibonacci élevés) → elles perdent leur créneau au profit des
    # tâches légères, qui remontent sur ces heures peu propices à la réflexion. L'urgence
    # et les échéances priment toujours ; le score WSJF affiché ne change pas (c'est un
    # choix de placement, pas de valeur). Le matin reste piloté par l'urgence pure.
    cognitive_dip_enabled: bool = Field(
        default=True,
        description="Active le creux de l'après-midi (false = ordonnancement d'urgence pur, sans pénalité).",
    )
    # Fenêtre du creux (heures, 24h) : rampe triangulaire 0 → 1 de `start` au tronc, puis
    # 1 → 0 du tronc à `end`. Décale ces bornes selon ton chronotype (alouette/hibou).
    cognitive_dip_start_hour: int = Field(
        default=13, ge=0, le=23, description="Début (0-23) de la fenêtre de creux post-déjeuner.",
    )
    cognitive_dip_trough_hour: int = Field(
        default=15, ge=0, le=23,
        description="Heure (0-23) du creux le plus profond (tronc statistique ~15h).",
    )
    cognitive_dip_end_hour: int = Field(
        default=16, ge=0, le=23, description="Fin (0-23) de la fenêtre de creux post-déjeuner.",
    )
    # Force de la pénalité au tronc pour une tâche de complexité maximale (21 pts) :
    # 1.0 → effort ×2 (score ÷2) au creux ; 0 = neutralisé même si activé.
    cognitive_dip_penalty: float = Field(
        default=1.0, ge=0,
        description=(
            "Force de la pénalité au tronc du creux pour une tâche de complexité "
            "maximale (1.0 → effort ×2 ; 0 = neutralisé même si activé)."
        ),
    )
    # --- Dashboard de statistiques ---
    # Fenêtre (semaines) des indicateurs « récents » (débit, temps ventilé, délais).
    stats_window_weeks: int = Field(
        default=8, ge=1,
        description="Fenêtre (semaines) des indicateurs « récents » du dashboard de statistiques.",
    )
    # --- Alertes de chrono (notification navigateur, opt-in) ---
    # Alerte « chrono oublié » au-delà de N minutes (0 = désactivé).
    timer_idle_alert_minutes: int = Field(
        default=180, ge=0,
        description="Alerte « chrono oublié » après N minutes de chrono continu (0 = désactivé).",
    )
    # Rappel de pause après N minutes de focus continu (0 = désactivé).
    pomodoro_focus_minutes: int = Field(
        default=50, ge=0,
        description="Rappel de pause après N minutes de focus continu (0 = désactivé).",
    )
    # --- Jours fériés (décalages jour ouvré : récurrence calendaire, snooze) ---
    holidays_fr: bool = Field(default=True, description="Active le calendrier des jours fériés français.")
    extra_holidays: str = Field(
        default="", description="Dates fériées supplémentaires (ISO AAAA-MM-JJ), séparées par des virgules.",
    )
    log_level: str = Field(default="INFO", description="Niveau de log console : DEBUG, INFO, WARNING, ERROR.")
    # --- Types de tâches ---
    # Typologie utilisée pour catégoriser les tâches (voir `Task.task_type`) : sert aux
    # statistiques de temps par type et à la suggestion de durée (voir
    # `app/tasks_stats.py::calibration_by_type`). Même patron que `gitlab_projects`
    # (chaîne séparée par des virgules) : librement éditable, sans plomberie de
    # formulaire dédiée à une liste. Une tâche dont le type a disparu de cette liste
    # garde sa valeur enregistrée, elle n'apparaît juste plus dans le menu.
    task_types: str = Field(
        default=(
            "Développement,Revue de code,Réunion,Documentation,Administratif,"
            "Veille/formation,Pilotage/dette technique"
        ),
        description="Types de tâches proposés dans la fiche (menu déroulant), séparés par des virgules.",
    )
    # --- Réseau (proxy sortant, ex. réseau d'entreprise) ---
    # Utile notamment pour joindre TimeTree depuis un poste derrière un proxy sortant.
    # Injectées dans l'environnement du processus au démarrage et après chaque
    # sauvegarde des réglages.
    http_proxy: str = Field(
        default="", description="Proxy HTTP sortant (ex. http://proxy-entreprise.example.com:3128). Vide = aucun.",
    )
    https_proxy: str = Field(
        default="", description="Proxy HTTPS sortant (ex. http://proxy-entreprise.example.com:3128). Vide = aucun.",
    )
    no_proxy: str = Field(
        default="127.0.0.1,localhost",
        description="Domaines/IPs à ne jamais faire passer par le proxy, séparés par des virgules.",
    )

    def __post_init__(self) -> None:
        """Validation à la construction (même moment que Pydantic avant la
        migration) : types et bornes de chaque champ, puis règles inter-champs
        (clé ``_general``, affichée en bandeau par la page Réglages)."""
        errors = validate_fields(self)
        if not errors:
            if self.workday_start_hour >= self.workday_end_hour:
                errors["_general"] = (
                    "L'heure de début de journée doit être avant l'heure de fin."
                )
            elif not (
                self.cognitive_dip_start_hour
                <= self.cognitive_dip_trough_hour
                <= self.cognitive_dip_end_hour
            ):
                errors["_general"] = (
                    "Le creux de l'après-midi doit respecter début ≤ tronc ≤ fin."
                )
        if errors:
            raise SettingsValidationError(errors)

    def model_dump(self, mode: str | None = None) -> dict:
        """Compat Pydantic conservée telle quelle (appelants et page Réglages) :
        tous les champs sont des types JSON natifs, ``mode`` est donc sans effet."""
        return dataclasses.asdict(self)

    @property
    def tasks_database_url(self) -> str:
        return f"sqlite:///{self.tasks_database_path}"

    @property
    def timetree_configured(self) -> bool:
        return bool(self.timetree_email and self.timetree_password)

    @property
    def pilotage_configured(self) -> bool:
        """Vrai si l'intégration pilotage (lecture seule) est activée."""
        return bool(self.pilotage_database_path)

    @property
    def gitlab_project_list(self) -> list[str]:
        return [p.strip() for p in self.gitlab_projects.split(",") if p.strip()]

    @property
    def task_type_list(self) -> list[str]:
        return [t.strip() for t in self.task_types.split(",") if t.strip()]

    @property
    def gitlab_token_effective(self) -> str:
        """`gitlab_token` (réglages) si renseigné, sinon résolu via `git credential
        fill`/`~/.netrc` pour `gitlab_url` (voir `app/git_credentials.py`)."""
        if self.gitlab_token:
            return self.gitlab_token
        if not self.gitlab_url:
            return ""
        from .git_credentials import resolve_gitlab_token

        return resolve_gitlab_token(self.gitlab_url)

    @property
    def gitlab_direct_configured(self) -> bool:
        """Vrai si l'import direct GitLab (sans pilotage) est activé et utilisable."""
        return bool(
            not self.pilotage_configured
            and self.gitlab_url
            and self.gitlab_token_effective
            and self.gitlab_project_list
            and self.gitlab_assignee_username
        )

    @property
    def holiday_set(self) -> frozenset:
        """Jours fériés (calendrier FR + dates supplémentaires), année courante ± 2 ans."""
        from datetime import date

        from .workdays import build_holidays

        extra = [s.strip() for s in self.extra_holidays.split(",") if s.strip()]
        if not self.holidays_fr and not extra:
            return frozenset()
        current = date.today().year
        return build_holidays(
            range(current - 1, current + 3), france=self.holidays_fr, extra=extra
        )


# Registre `nom → FieldInfo` (annotation, description, bornes) : même rôle — et
# même nom — que le `model_fields` de Pydantic qu'il remplace, consommé par la
# page Réglages, `settings_store` et `main._field_kind`.
Settings.model_fields = build_field_registry(Settings)


@lru_cache
def get_settings() -> Settings:
    """Retourne l'instance unique des paramètres (mise en cache)."""
    from . import settings_store

    return settings_store.load()


def invalidate_settings_cache() -> None:
    """À appeler après une sauvegarde des réglages : force `get_settings()` à
    relire le fichier de réglages à la prochaine requête (pas de redémarrage)."""
    get_settings.cache_clear()


def apply_proxy_env(settings: Settings) -> None:
    """Injecte les réglages de proxy sortant dans l'environnement du processus.

    Nécessaire pour que `requests`/`httpx`/les appels TimeTree et GitLab en
    tiennent compte : ces bibliothèques lisent les variables d'environnement
    standard, jamais `Settings` directement. Appelé au démarrage et après
    chaque sauvegarde des réglages (voir `app/main.py`)."""
    import os

    mapping = {
        "http_proxy": settings.http_proxy,
        "https_proxy": settings.https_proxy,
        "no_proxy": settings.no_proxy,
    }
    for name, value in mapping.items():
        for env_name in (name, name.upper()):
            if value:
                os.environ[env_name] = value
            else:
                os.environ.pop(env_name, None)
