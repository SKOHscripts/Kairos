"""Groupement des champs de `Settings` pour l'affichage de la page Réglages.

Pure présentation : ne duplique pas les valeurs/descriptions (portées par
`app/config.py`), seulement l'ordre et le regroupement en sections, calqués sur
l'ancien découpage de `.env.example`.
"""

from __future__ import annotations

SECTIONS: list[tuple[str, list[str]]] = [
    ("Base de données", ["tasks_database_path"]),
    (
        "Import GitLab (assigné, lecture seule)",
        [
            "gitlab_assignee_username",
            "pilotage_database_path",
            "gitlab_url",
            "gitlab_token",
            "gitlab_projects",
            "gitlab_cache_ttl_minutes",
        ],
    ),
    (
        "Calendrier TimeTree",
        [
            "timetree_email",
            "timetree_password",
            "timetree_calendar_code",
            "timetree_cache_ttl_minutes",
        ],
    ),
    (
        "Calendrier(s) Google",
        [
            "google_client_id",
            "google_client_secret",
            "google_calendar_ids",
            "google_cache_ttl_minutes",
        ],
    ),
    (
        "Ordonnancement",
        [
            "default_task_duration_minutes",
            "meeting_buffer_minutes",
            "workday_start_hour",
            "workday_end_hour",
        ],
    ),
    (
        "Ordonnancement WSJF",
        [
            "priority_value_base",
            "urgency_horizon_days",
            "urgency_peak",
            "default_fibonacci_points",
        ],
    ),
    (
        "Creux de l'après-midi",
        [
            "cognitive_dip_enabled",
            "cognitive_dip_start_hour",
            "cognitive_dip_trough_hour",
            "cognitive_dip_end_hour",
            "cognitive_dip_penalty",
        ],
    ),
    (
        "Garde-fous",
        ["stale_overdue_days", "stale_untouched_days", "priority_overload_threshold"],
    ),
    ("Types de tâches", ["task_types"]),
    ("Dashboard de statistiques", ["stats_window_weeks"]),
    ("Alertes de chrono", ["timer_idle_alert_minutes", "pomodoro_focus_minutes"]),
    ("Jours fériés", ["holidays_fr", "extra_holidays"]),
    ("Réseau (proxy sortant)", ["http_proxy", "https_proxy", "no_proxy"]),
    ("Divers", ["log_level"]),
]

# Champs dont la valeur ne doit jamais être réaffichée en clair dans l'interface
# (stockés en priorité dans le trousseau système — voir `app/secret_store.py`).
# `google_refresh_token` n'apparaît dans aucune section (écrit uniquement par le
# flux OAuth, jamais saisi à la main) mais reste listé ici pour ne jamais être
# exposé en clair par `_settings_context`/`settings_store.save`.
SECRET_FIELDS: tuple[str, ...] = (
    "gitlab_token", "timetree_password", "google_client_secret", "google_refresh_token",
)

# Champ nécessitant un redémarrage de Kairos pour prendre effet (moteur de base
# de données lié au chemin dès l'import de `app/tasks_db.py`).
RESTART_REQUIRED_FIELDS: tuple[str, ...] = ("tasks_database_path",)

# Libellés courts affichés au-dessus de chaque champ (la description longue de
# `Settings.model_fields[...].description` reste affichée juste en dessous).
FIELD_LABELS: dict[str, str] = {
    "tasks_database_path": "Chemin de la base de tâches",
    "pilotage_database_path": "Base de pilotage (optionnel)",
    "gitlab_assignee_username": "Nom d'utilisateur GitLab (assigné)",
    "gitlab_url": "URL de l'instance GitLab",
    "gitlab_token": "Jeton d'accès GitLab",
    "gitlab_projects": "Projet(s) GitLab",
    "gitlab_cache_ttl_minutes": "Cache GitLab (minutes)",
    "timetree_email": "E-mail TimeTree",
    "timetree_password": "Mot de passe TimeTree",
    "timetree_calendar_code": "Code du calendrier TimeTree",
    "timetree_cache_ttl_minutes": "Cache TimeTree (minutes)",
    "google_client_id": "Identifiant client OAuth Google",
    "google_client_secret": "Secret client OAuth Google",
    "google_calendar_ids": "Calendrier(s) Google",
    "google_cache_ttl_minutes": "Cache Google Calendar (minutes)",
    "default_task_duration_minutes": "Durée par défaut d'une tâche (minutes)",
    "meeting_buffer_minutes": "Marge après une réunion (minutes)",
    "workday_start_hour": "Début de journée (heure)",
    "workday_end_hour": "Fin de journée (heure)",
    "stale_overdue_days": "Seuil « en retard » (jours)",
    "stale_untouched_days": "Seuil « sans date » (jours)",
    "priority_overload_threshold": "Seuil de surcharge P0",
    "priority_value_base": "Base de valeur par priorité",
    "urgency_horizon_days": "Horizon d'urgence (jours)",
    "urgency_peak": "Poids maximal de l'urgence",
    "default_fibonacci_points": "Points Fibonacci par défaut",
    "cognitive_dip_enabled": "Activer le creux de l'après-midi",
    "cognitive_dip_start_hour": "Début du creux (heure)",
    "cognitive_dip_trough_hour": "Creux le plus profond (heure)",
    "cognitive_dip_end_hour": "Fin du creux (heure)",
    "cognitive_dip_penalty": "Force de la pénalité",
    "task_types": "Types de tâches (menu de la fiche)",
    "stats_window_weeks": "Fenêtre des statistiques (semaines)",
    "timer_idle_alert_minutes": "Alerte chrono oublié (minutes)",
    "pomodoro_focus_minutes": "Rappel pomodoro (minutes)",
    "holidays_fr": "Jours fériés français",
    "extra_holidays": "Jours fériés supplémentaires",
    "log_level": "Niveau de log",
    "http_proxy": "Proxy HTTP sortant",
    "https_proxy": "Proxy HTTPS sortant",
    "no_proxy": "Domaines sans proxy",
}
