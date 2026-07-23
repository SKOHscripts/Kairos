"""Données d'exemple posées à la **première utilisation** (base tâches vierge).

But : un nouvel utilisateur ouvre « Aujourd'hui » sur un agenda déjà peuplé plutôt que
sur une page vide, et découvre les fonctionnalités par l'exemple (score WSJF, placement
autour d'une réunion, deep-work, épinglage, sous-tâches, dépendances, inbox « À traiter »,
programmation plus tard).

Ce ne sont **pas** des objets spéciaux : de simples tâches et créneaux natifs, portant le
tag de projet « Exemple » et un titre préfixé « [Exemple] », que l'utilisateur supprime ou
termine comme les siens. Le déclenchement (une seule fois, sur base vierge) vit dans
``app/tasks_db.py`` : la présence de la table ``task`` suffit à ne jamais re-semer.

Fonction quasi pure : elle ne fait qu'``add``/``flush`` sur la session reçue ; le
``commit`` reste à l'appelant (voir ``init_tasks_db``).
"""

from __future__ import annotations

from datetime import date, datetime, time, timedelta

from sqlalchemy.orm import Session

from .tasks_models import Note, Task, TaskDependency, TimeBlock

EXAMPLE_PROJECT_TAG = "Exemple"


def _at(day: date, hour: int, minute: int = 0) -> datetime:
    """Datetime naïf (même convention que l'ordonnancement, cf. ``datetime.combine``)."""
    return datetime.combine(day, time(hour=hour, minute=minute))


def seed_example_data(session: Session, *, today: date) -> None:
    """Peuple ``session`` d'un petit jeu de tâches et créneaux d'exemple.

    Toutes les dates sont **relatives à** ``today`` pour rester d'actualité quel que soit
    le jour du premier lancement. L'appelant est responsable du ``commit``.
    """
    soon = today + timedelta(days=2)
    later = today + timedelta(days=7)

    def add_task(**kwargs) -> Task:
        task = Task(source="native", project_tag=EXAMPLE_PROJECT_TAG, **kwargs)
        session.add(task)
        session.flush()  # attribue l'id (nécessaire pour parent_id / dépendances)
        return task

    # 1. P0 avec échéance proche + points : sort en tête du score WSJF.
    add_task(
        title="[Exemple] Corriger le bug de connexion",
        description=(
            "Exemple : priorité P0 et échéance proche, la tâche remonte en tête du tri. "
            "Supprime-la quand tu veux."
        ),
        priority=0,
        deadline=soon,
        fibonacci_points=3,
        estimated_minutes=45,
        task_type="Développement",
    )

    # 2. Tâche P1 « ordinaire » qualifiée (priorité + points).
    add_task(
        title="[Exemple] Préparer la revue de sprint",
        description="Exemple : une tâche P1 classique, prête à être planifiée.",
        priority=1,
        deadline=later,
        fibonacci_points=5,
        estimated_minutes=60,
        task_type="Réunion",
    )

    # 3. Tâche épinglée à heure fixe : posée exactement à 9h30, jamais déplacée.
    add_task(
        title="[Exemple] Point d'équipe (épinglé à 9h30)",
        description="Exemple : une tâche épinglée reste à son heure, le reste se cale autour.",
        priority=1,
        fibonacci_points=2,
        estimated_minutes=30,
        pinned_start=_at(today, 9, 30),
        task_type="Réunion",
    )

    # 4. Tâche mère + deux sous-tâches : seules les feuilles sont planifiées, la mère
    #    affiche l'avancement n/m.
    parent = add_task(
        title="[Exemple] Rédiger la documentation",
        description="Exemple : une tâche mère ; ce sont ses sous-tâches qui sont planifiées.",
        priority=1,
        fibonacci_points=5,
        task_type="Documentation",
    )
    add_task(
        title="[Exemple] Documentation — plan",
        parent_id=parent.id,
        priority=1,
        fibonacci_points=2,
        estimated_minutes=30,
        task_type="Documentation",
    )
    add_task(
        title="[Exemple] Documentation — rédaction",
        parent_id=parent.id,
        priority=1,
        fibonacci_points=3,
        estimated_minutes=45,
        task_type="Documentation",
    )

    # 5. Dépendance « bloqué par » : « Déployer » attend « Obtenir la validation ».
    blocker = add_task(
        title="[Exemple] Obtenir la validation du client",
        description="Exemple : cette tâche en bloque une autre (chemin critique).",
        priority=1,
        fibonacci_points=2,
        estimated_minutes=20,
        task_type="Administratif",
    )
    blocked = add_task(
        title="[Exemple] Déployer en production",
        description="Exemple : bloquée tant que la validation n'est pas faite.",
        priority=0,
        fibonacci_points=3,
        estimated_minutes=30,
        task_type="Développement",
    )
    session.add(TaskDependency(task_id=blocked.id, blocker_id=blocker.id))

    # 6. « À traiter » (inbox GTD) : ni priorité ni points → à qualifier avant tout tri.
    add_task(
        title="[Exemple] Idée : automatiser le rapport hebdomadaire",
        description=(
            "Exemple : sans priorité ni points de Fibonacci, la tâche reste « À traiter » "
            "en tête de page tant qu'elle n'est pas qualifiée."
        ),
        task_type="Veille/formation",
    )

    # 7. Programmée plus tard : date programmée future sans échéance imminente → masquée
    #    de l'agenda du jour (section « Programmées plus tard »).
    add_task(
        title="[Exemple] Renouveler la certification",
        description="Exemple : programmée pour plus tard, elle attend son jour.",
        priority=1,
        fibonacci_points=3,
        scheduled_date=later,
        task_type="Administratif",
    )

    # Créneaux occupés / réservés de la journée.
    session.add_all(
        [
            # Réunion 13h-14h : illustre le cas emblématique (une tâche urgente repoussée
            # à 14h05, marge après réunion incluse).
            TimeBlock(
                title="[Exemple] Réunion projet",
                start=_at(today, 13),
                end=_at(today, 14),
                source="manual",
                kind="busy",
            ),
            # Bloc deep-work réservé : une seule tâche, non fragmentée.
            TimeBlock(
                title="[Exemple] Deep work",
                start=_at(today, 10),
                end=_at(today, 11, 30),
                source="manual",
                kind="deepwork",
            ),
            # Bloc déjeuner récurrent quotidien (modèle unique, projeté à la volée).
            TimeBlock(
                title="[Exemple] Déjeuner",
                start=_at(today, 12),
                end=_at(today, 13),
                source="manual",
                kind="busy",
                recurrence="daily",
            ),
        ]
    )
    session.flush()

    # Note d'exemple (capture GTD) : illustre l'étape en amont de l'inbox « À
    # traiter » (page Notes) — une idée jetée sans friction, pas encore une tâche.
    session.add(
        Note(
            body=(
                "[Exemple] Idée en vrac : revoir le découpage des sprints ?\n"
                "À développer avant d'en faire une tâche — ou à archiver si "
                "ça ne mène nulle part."
            )
        )
    )
    session.flush()
