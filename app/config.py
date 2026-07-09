"""Configuration de « Kairos », chargée depuis l'environnement / le fichier .env."""

from __future__ import annotations

from functools import lru_cache

from pydantic_settings import BaseSettings, SettingsConfigDict

_ENV_FILE = ".env"


class Settings(BaseSettings):
    """Paramètres de l'outil « Kairos » (autonome depuis la phase 14).

    Les valeurs sont lues depuis les variables d'environnement ou un fichier .env.
    Les identifiants ne doivent jamais être committés.
    """

    model_config = SettingsConfigDict(
        env_file=_ENV_FILE, env_file_encoding="utf-8", extra="ignore"
    )

    # Base SQLite des tâches (chemin relatif au dossier de lancement).
    tasks_database_path: str = "tasks.db"
    # --- Intégration OPTIONNELLE avec l'outil de pilotage MSI (lecture seule) ---
    # Chemin vers la base `pilotage.db` de pilotage-pleiade-gitlab. Si renseigné :
    # les issues GitLab qui te sont assignées (cache entretenu par l'onglet Pilotage
    # GitLab) apparaissent comme tâches, et le panneau d'édition propose la liaison
    # manuelle vers une fiche de dette technique. Vide = fonctionnalités désactivées
    # proprement (aucune erreur) — cas normal d'un collègue sans l'outil de pilotage.
    pilotage_database_path: str = ""
    # Nom d'utilisateur GitLab (assignee) dont les issues ouvertes sont importées
    # comme tâches (source='gitlab'). Partagé par les deux intégrations GitLab
    # ci-dessous (cache pilotage ou import direct) : une seule identité GitLab à
    # renseigner, quelle que soit la source utilisée. Vide = import désactivé.
    gitlab_assignee_username: str = ""
    # --- Import direct des issues GitLab assignées (optionnel, lecture seule) ---
    # Utilisé quand `pilotage_database_path` est vide (cas normal d'un collègue sans
    # l'outil de pilotage) : appel direct, en lecture seule, à l'API REST GitLab —
    # aucune écriture, jamais. Si `pilotage_database_path` est renseigné, le cache
    # pilotage prime (zéro appel réseau) et cette intégration est ignorée.
    gitlab_url: str = ""
    # Optionnel : si vide, le jeton est résolu depuis les moyens d'authentification
    # déjà configurés pour `git` sur ce poste (`git credential fill` — trousseau
    # GNOME/libsecret, Keychain, Windows Credential Manager... — puis `~/.netrc`
    # en repli), pour ne pas avoir à dupliquer un jeton en clair dans `.env`. Voir
    # `gitlab_token_effective` / `app/git_credentials.py`.
    gitlab_token: str = ""
    # Un ou plusieurs projets ("groupe/projet" ou id numérique), séparés par des virgules.
    gitlab_projects: str = ""
    # Durée (minutes) de mise en cache des issues (anti rate-limiting), même patron
    # que le cache TimeTree ci-dessous.
    gitlab_cache_ttl_minutes: int = 5
    # --- Calendrier personnel TimeTree ---
    # Identifiants utilisés par `timetree-exporter` (paquet non-officiel, API
    # reverse-engineerée). Jamais committés. Vides = calendrier désactivé (dégradation
    # propre, créneaux saisis à la main uniquement).
    timetree_email: str = ""
    timetree_password: str = ""
    timetree_calendar_code: str = ""
    # Durée (minutes) de mise en cache des créneaux TimeTree (anti rate-limiting).
    timetree_cache_ttl_minutes: int = 30
    # --- Ordonnancement ---
    # Durée par défaut (minutes) d'une tâche sans estimation (jamais stockée).
    default_task_duration_minutes: int = 30
    # Marge (minutes) laissée après un créneau occupé (réunion 13h-14h → 14h05).
    meeting_buffer_minutes: int = 5
    # Bornes (heures, 24h) de la journée de travail.
    workday_start_hour: int = 9
    workday_end_hour: int = 18
    # Seuils (jours) de détection des tâches qui traînent : échéance/date programmée
    # dépassée au-delà de `stale_overdue_days`, ou tâche sans date non modifiée
    # depuis `stale_untouched_days`. Signal d'affichage seul, ne change jamais le tri.
    stale_overdue_days: int = 7
    stale_untouched_days: int = 14
    # Nombre de tâches à priorité maximale (0-1) au-delà duquel un bandeau avertit
    # que le signal de priorité se dilue.
    priority_overload_threshold: int = 5
    # --- Ordonnancement WSJF : score = coût du retard / effort ---
    # Base de la valeur EXPONENTIELLE d'un cran de priorité : `base ** (4 - priorité)`.
    priority_value_base: float = 2.0
    # Horizon (jours) où une échéance commence à peser (rampe linéaire en deçà).
    urgency_horizon_days: int = 14
    # Poids max de la criticité temporelle (même unité que la valeur ; 8 ≈ P1).
    urgency_peak: float = 8.0
    # Effort d'une tâche sans points de Fibonacci ni estimation (dénominateur neutre).
    default_fibonacci_points: int = 3
    # --- Creux de l'après-midi (« post-lunch dip », clin d'œil au nom de code 14h55) ---
    # Pendant une fenêtre creuse, l'ordonnancement gonfle l'effort effectif des tâches
    # COMPLEXES (points de Fibonacci élevés) → elles perdent leur créneau au profit des
    # tâches légères, qui remontent sur ces heures peu propices à la réflexion. L'urgence
    # et les échéances priment toujours ; le score WSJF affiché ne change pas (c'est un
    # choix de placement, pas de valeur). Le matin reste piloté par l'urgence pure.
    cognitive_dip_enabled: bool = True
    # Fenêtre du creux (heures, 24h) : rampe triangulaire 0 → 1 de `start` au tronc, puis
    # 1 → 0 du tronc à `end`. Décale ces bornes selon ton chronotype (alouette/hibou).
    cognitive_dip_start_hour: int = 13
    cognitive_dip_trough_hour: int = 15  # creux le plus profond (tronc statistique ~15h)
    cognitive_dip_end_hour: int = 16
    # Force de la pénalité au tronc pour une tâche de complexité maximale (21 pts) :
    # 1.0 → effort ×2 (score ÷2) au creux ; 0 = neutralisé même si activé.
    cognitive_dip_penalty: float = 1.0
    # --- Dashboard de statistiques ---
    # Fenêtre (semaines) des indicateurs « récents » (débit, temps ventilé, délais).
    stats_window_weeks: int = 8
    # --- Alertes de chrono (notification navigateur, opt-in) ---
    # Alerte « chrono oublié » au-delà de N minutes (0 = désactivé).
    timer_idle_alert_minutes: int = 180
    # Rappel de pause après N minutes de focus continu (0 = désactivé).
    pomodoro_focus_minutes: int = 50
    # --- Jours fériés (décalages jour ouvré : récurrence calendaire, snooze) ---
    # `holidays_fr` active le calendrier français (défaut) ; `extra_holidays` ajoute
    # des dates ISO séparées par des virgules.
    holidays_fr: bool = True
    extra_holidays: str = ""
    log_level: str = "INFO"

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
    def gitlab_token_effective(self) -> str:
        """`gitlab_token` (`.env`) si renseigné, sinon résolu via `git credential
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


@lru_cache
def get_settings() -> Settings:
    """Retourne l'instance unique des paramètres (mise en cache)."""
    return Settings()
