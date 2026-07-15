# Spécifications de domaine — index

Ce répertoire est le registre de spécification vivant de Kairos, tel que défini
par le workflow décrit dans [`CLAUDE.md`](../../CLAUDE.md) (§ Workflow de
développement) : une spécification par domaine fonctionnel, exhaustive et
**bijective** avec le code (tout comportement du code y est décrit, rien n'y
décrit du code inexistant), découpée en deux parties — **besoin métier** puis
**solution technique**.

Il remplace l'ancien `SPEC_KAIROS.md` (journal chronologique par phases,
1-18) et `tasks/plan.md`/`tasks/todo.md` (plan d'implémentation historique,
100 % livré) : tout leur contenu encore vrai aujourd'hui a été absorbé dans
les fichiers ci-dessous ; le reste (intégrations retirées, cadre du dépôt
d'origine avant extraction) est devenu obsolète et n'a pas été reporté tel
quel — seules les décisions encore actives sont tracées.

## Domaines

| Spec | Couvre |
|---|---|
| [`ordonnancement.md`](ordonnancement.md) | Score WSJF, buckets d'urgence, time-blocking, creux de l'après-midi, épinglage, deep work, gate « À traiter ». `app/tasks_scheduling.py`. |
| [`modele-donnees.md`](modele-donnees.md) | Schéma SQLAlchemy (Task, TimeBlock, TaskDependency, WorkSession, TaskSyncMeta), base SQLite dédiée, migrations additives, données d'exemple. `app/tasks_models.py`, `app/tasks_db.py`, `app/tasks_seed.py`. |
| [`recurrence.md`](recurrence.md) | Récurrence des tâches (recreate-on-complete, calendaire « le N du mois ») et des blocs (projection à la volée), snooze. `app/tasks_recurrence.py`. |
| [`dependances.md`](dependances.md) | Blocage transitif, cycles (Kahn), urgence dérivée / chemin critique. `app/tasks_dependencies.py`. |
| [`temps-reel-chrono.md`](temps-reel-chrono.md) | Agrégats de temps réel, tâches qui traînent, chrono vivant + alertes navigateur, garde-fou de surcharge de priorité. `app/tasks_time.py`, `app/tasks_staleness.py`, chrono JS de `kairos.html`. |
| [`statistiques.md`](statistiques.md) | Dashboard `/kairos/stats` : vélocité, calibration, biais estimé/réel, complétude, honnêteté statistique. `app/tasks_stats.py`. |
| [`vue-jour-gtd.md`](vue-jour-gtd.md) | La vue Jour et son flux GTD (capturer → traiter → faire), l'architecture AJAX/fragment, la modale d'édition. `templates/kairos.html` et partiels, routes `/kairos/*` de `app/main.py`. |
| [`reglages-secrets.md`](reglages-secrets.md) | Modèle `Settings`, validation, persistance, trousseau système avec repli fichier. `app/config.py`, `app/settings_*.py`, `app/secret_store.py`. |
| [`integrations-externes.md`](integrations-externes.md) | GitLab (direct + via pilotage), TimeTree, résolution de jeton git — toutes optionnelles, lecture seule, dégradation propre. `app/gitlab_direct.py`, `app/tasks_gitlab_sync.py`, `app/pilotage_link.py`, `app/calendar/timetree_source.py`, `app/git_credentials.py`. |
| [`packaging-lancement.md`](packaging-lancement.md) | Lancement desktop (port, verrou, crash log) et Android (env avant import), empaquetage PyInstaller. `app/launcher.py`, `app/android_launcher.py`, `packaging/`. |
| [`accueil-navigation.md`](accueil-navigation.md) | Gabarit de base (topnav/topbar), page d'accueil et rendu du README, identité/nom. `templates/base.html`, `templates/home.html`. |

## Références transverses (non dupliquées ici)

- [`docs/DESIGN_SYSTEM.md`](../DESIGN_SYSTEM.md) — charte visuelle (jetons de
  couleur, typographie, formes), citée par les specs UI plutôt que recopiée.
- [`docs/ANDROID_PACKAGING.md`](../ANDROID_PACKAGING.md) — détail technique du
  build Android (Chaquopy), cité par `packaging-lancement.md`.
- [`README.md`](../../README.md) — documentation utilisateur des
  fonctionnalités déjà livrées (§ workflow, étape 4 : une spec n'entre au
  README que si c'est une feature pertinente pour l'utilisateur).

## Règle de mise à jour

Avant toute nouvelle conception, chercher d'abord ici (puis dans les
commentaires de code) si la question a déjà été tranchée — ne jamais
re-trancher une décision déjà consignée sans la rouvrir explicitement avec
l'utilisateur. Après toute implémentation, mettre à jour la spec du domaine
concerné dans le même changement (bijectivité) ; créer un nouveau fichier
seulement pour un domaine qui n'existe pas encore ci-dessus.
