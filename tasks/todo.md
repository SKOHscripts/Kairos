# Todo : « Kairos » (dashboard de tâches natif)

Détail complet de chaque tâche : voir `tasks/plan.md`. Ordre = ordre d'implémentation.

- [x] **T1** — Réglages `Settings` (S)
  - Acceptance : nouveaux champs `tasks_database_path`, `superproductivity_base_url`,
    `timetree_*`, `default_task_duration_minutes`, `meeting_buffer_minutes`,
    `workday_start_hour`/`end_hour` ; `timetree_configured` correct par défaut (False).
  - Verify: `pytest tests/test_config.py -q`
  - Files: `app/config.py`, `.env.example`, `tests/test_config.py`

- [x] **T2** — Base tâches : modèles + engine/session (S)
  - Acceptance : `TasksBase`/`Task`/`TimeBlock`/`TaskSyncMeta` créés dans une base
    séparée de `pilotage.db` ; contrainte unique `(source, external_id)` vérifiée.
  - Verify: `pytest tests/test_tasks_models.py -q`
  - Files: `app/tasks_models.py`, `app/tasks_db.py`, `main.py`, `tests/conftest.py`,
    `tests/test_tasks_models.py`
  - Dépend de : T1

- [x] **T3** — Client HTTP Superproductivity (S)
  - Acceptance : `SuperproductivityClient` n'expose que `list_tasks`/`list_projects`/
    `list_tags` (GET), aucune méthode d'écriture ; parsing défensif.
  - Verify: `pytest tests/test_superproductivity_client.py -q` (respx, zéro réseau réel)
  - Files: `app/clients/superproductivity.py`, `tests/test_superproductivity_client.py`
  - Dépend de : T1

- [x] **T4** — Synchronisation SP → base native (S)
  - Acceptance : upsert idempotent, `priority` locale jamais écrasée, tâche disparue
    → `status='archived'` (jamais supprimée), `TaskSyncMeta` à jour.
  - Verify: `pytest tests/test_tasks_sync.py -q`
  - Files: `app/tasks_sync.py`, `tests/test_tasks_sync.py`
  - Dépend de : T2, T3

- [x] **CHECKPOINT 1** — `pytest tests/test_superproductivity_client.py tests/test_tasks_sync.py -q`

- [x] **T5** — Algorithme d'ordonnancement (M)
  - Acceptance : buckets d'urgence + priorité en tie-break ; **cas emblématique** :
    réunion 13h-14h repousse une tâche urgente à 14h05 avec note explicative ; journée
    pleine → `unscheduled` ; pas de crash sur listes vides.
  - Verify: `pytest tests/test_tasks_scheduling.py -q`
  - Files: `app/tasks_scheduling.py`, `tests/test_tasks_scheduling.py`
  - Dépend de : T2

- [x] **CHECKPOINT 2** — le test du cas « réunion 13h-14h » passe, en isolation totale
      (aucune route/template requis à ce stade)

- [x] **T6** — Interface TimeTree (seam) + dégradation propre (S)
  - Acceptance : `fetch_busy_slots` retourne `ok=False` proprement si non configuré,
    jamais d'exception ; contrat de retour figé pour T9.
  - Verify: `pytest tests/test_timetree_source.py -q`
  - Files: `app/calendar/__init__.py`, `app/calendar/timetree_source.py`,
    `tests/test_timetree_source.py`
  - Dépend de : T1

- [x] **T7** — Route « Kairos » + priorité + bloc manuel + sidebar (M)
  - Acceptance : `GET /kairos` répond 200 même si SP injoignable (bandeau, jamais
    de 500) ; `POST .../priority` modifie uniquement la priorité ; `POST .../blocks`
    crée un bloc manuel qui impacte l'ordonnancement ; sans TimeTree → bandeau propre ;
    nouvelle entrée sidebar en tête de nav.
  - Verify: `pytest tests/test_kairos_route.py -q` puis vérification manuelle
    (`uvicorn app.main:app --reload`, `/kairos`)
  - Files: `main.py`, `templates/kairos.html`, `templates/_sidebar.html`,
    `tests/test_kairos_route.py`
  - Dépend de : T4, T5, T6

- [x] **CHECKPOINT 3 (MVP démontrable)** — critères de succès 1/3/4/6 de
      `SPEC_KAIROS.md` vérifiables en conditions réelles

- [x] **T8** — Vue semaine (S)
  - Acceptance : `GET /kairos?view=week` affiche 7 jours, tâches/blocs placés au
    bon jour, bascule jour/semaine fonctionnelle.
  - Verify: `pytest tests/test_kairos_route.py -q` (cas semaine)
  - Files: `main.py`, `templates/kairos.html`, `tests/test_kairos_route.py`
  - Dépend de : T7

- [x] **T9** — Intégration réelle TimeTree (M) — isolée, peut glisser sans bloquer le reste
  - Acceptance : invocation `timetree-exporter` en subprocess + parsing `.ics`, TTL
    respecté, dégradation propre sur tout échec (subprocess, timeout, parsing) —
    aucune exception ne remonte à la route.
  - Verify: `pytest tests/test_timetree_source.py -q` (subprocess mocké, aucun appel
    réel), smoke test manuel optionnel avec identifiants réels en `.env` local
  - Files: `pyproject.toml`, `app/calendar/timetree_source.py`,
    `tests/test_timetree_source.py`
  - Dépend de : T6

- [x] **CHECKPOINT 4 (final)** — `pytest -q` complet (zéro réseau réel) + relecture des
      6 critères de succès de `SPEC_KAIROS.md`

---

# Phase 2 : suivi de tâches complet + time blocking

Détail : `tasks/plan.md` § Phase 2. Ordre = ordre d'implémentation.

- [x] **U1** — Schéma v2 + migration légère (S)
  - Acceptance : `estimated_minutes`, `pinned_start`, `parent_id`, `recurrence` sur
    `Task` ; une base phase 1 peuplée est migrée sans perte au démarrage.
  - Verify: `pytest tests/test_tasks_models.py -q` (dont test de migration)

- [x] **U2** — CRUD natif (M)
  - Acceptance : création rapide inline, édition complète, fait/rouvrir, « demain »,
    suppression (native) / archivage (SP) — tout depuis le dashboard ; import de la
    durée SP (ms→min) sans écrasement par du vide.
  - Verify: `pytest tests/test_kairos_route.py -q` + uvicorn manuel

- [x] **U3** — Récurrence (S)
  - Acceptance : done sur une récurrente crée l'occurrence suivante (daily/weekdays/
    weekly/monthly) ; rouvrir ne duplique pas.
  - Verify: `pytest tests/test_tasks_recurrence.py -q`

- [x] **CHECKPOINT 1** — gestion de tâches complète démontrable sans SP

- [x] **U5** — Scheduling v2 : durées + épinglage + débordement (M)
  - Acceptance : épinglée posée à l'heure dite (conflit signalé), auto autour avec
    durées réelles, stats requises/disponibles/débordement ; 14h05 toujours vert.
  - Verify: `pytest tests/test_tasks_scheduling.py -q`

- [x] **U6** — Timeline verticale + progression du jour (M)
  - Acceptance : agenda vertical heure par heure (occupé + travail), en-tête
    faites/prévues + requis vs disponible + alerte débordement, « à faire maintenant ».
  - Verify: tests de rendu + uvicorn manuel

- [x] **CHECKPOINT 2** — time blocking complet démontrable

- [x] **U4** — Sous-tâches (M)
  - Acceptance : import SP en deux passes avec `parent_id`, filles indentées sous la
    mère, avancement n/m, seules les feuilles planifiées.
  - Verify: `pytest tests/test_tasks_sync.py tests/test_tasks_scheduling.py
    tests/test_kairos_route.py -q`

- [x] **U7** — Sortie de Superproductivity + doc (S)
  - Acceptance : `superproductivity_sync_enabled=false` = zéro appel réseau ni
    bandeau ; « Adopter les tâches SP » convertit en natif (refusé si synchro
    active) ; README à jour.
  - Verify: tests dédiés + relecture README

- [x] **CHECKPOINT 3 (final)** — `pytest -q` complet + Success Criteria phase 2

---

# Phase 3 : deep work + dépendances (livrée)

- [x] **V1** — Dépendances : modèle + moteur pur
- [x] **V2** — Dépendances : scheduling + route + UI
- [x] **V3** — Suivi du temps réel
- [x] **V4** — Blocs deep-work protégés

---

# Phase 4 : GitLab assigné, date programmée, récurrence calendaire

Détail : `tasks/plan.md` § Phase 4. Ordre = ordre d'implémentation.

- [x] **W1** — Réglages phase 4 + jour ouvré arrière, fériés inclus (S)
  - Acceptance : `GITLAB_ASSIGNEE_USERNAME` ; `previous_business_day`/
    `on_or_before_business_day` reculent correctement (week-end, férié, chaîné).
  - Verify: `pytest tests/test_workdays.py tests/test_config.py -q`

- [x] **W2** — Schéma `Task` v4 + migration (S)
  - Acceptance : `scheduled_date`/`recurrence_day_of_month`/`recurrence_period` ;
    migration sur base **phase 3** peuplée sans perte.
  - Verify: `pytest tests/test_tasks_models.py -q`
  - Dépend de : W1 (aucune, indépendant en pratique)

- [x] **W3** — Synchronisation GitLab assignée, mutualisée (M)
  - Acceptance : lit `GitLabIssueCache` (zéro appel réseau neuf), upsert
    `Task(source='gitlab')`, priorité jamais écrasée, fermée/disparue → archivée.
  - Verify: `pytest tests/test_tasks_gitlab_sync.py -q` + test de route
  - Dépend de : W1

- [x] **CHECKPOINT 1** — `pytest tests/test_workdays.py tests/test_tasks_models.py
      tests/test_tasks_gitlab_sync.py -q` + vérification manuelle uvicorn

- [x] **W4** — `scheduled_date` : éligibilité, buckets, « Programmées plus tard » (M)
  - Acceptance : masquée aujourd'hui seulement si programmée future ET échéance non
    imminente ; échéance toujours prioritaire ; nouvelle section, jamais de trou noir.
  - Verify: `pytest tests/test_tasks_scheduling.py tests/test_kairos_route.py -q`
  - Dépend de : W2

- [x] **CHECKPOINT 2** — tâche programmée lundi/deadline mardi, absente vendredi,
      visible dans « Programmées plus tard »

- [x] **W5** — Récurrence calendaire (`monthly_on_day`) (M)
  - Acceptance : génération idempotente par mois, décalage arrière jour ouvré+férié,
    occurrence antérieure non écrasée.
  - Verify: `pytest tests/test_tasks_recurrence.py -q`
  - Dépend de : W1, W2

- [x] **W6** — Snooze : décalage avant, jour ouvré + fériés (S)
  - Acceptance : vendredi → lundi (ou mardi si férié) ; non-régression du test
    existant.
  - Verify: `pytest tests/test_tasks_recurrence.py -q`
  - Dépend de : W1

- [x] **W7** — Invariant « aucune tâche jamais perdue » (S)
  - Acceptance : test de propriété (graine fixe, ~50 tirages) — chaque tâche
    éligible dans exactement une section.
  - Verify: `pytest tests/test_tasks_scheduling.py -q`
  - Dépend de : W2, W4

- [x] **CHECKPOINT FINAL** — `pytest -q` complet + migration base phase 3 peuplée +
      Success Criteria phase 4 + vérification manuelle uvicorn

---

# Phase 5 : points de Fibonacci, typologie de tâches, ergonomie

Détail : `tasks/plan.md` § Phase 5. Ordre = ordre d'implémentation.

- [x] **Z1** — Modèle : `task_type` + `fibonacci_points` + migration (S)
  - Acceptance : colonnes ajoutées, `TASK_TYPE_LABELS`/`FIBONACCI_SCALE` définies ;
    migration sur base **phase 4** peuplée sans perte.
  - Verify: `pytest tests/test_tasks_models.py -q`

- [x] **Z2** — Routes : champs édition + fusion de l'épinglage (M)
  - Acceptance : un seul POST `/edit` pose type/points/heure fixe ; heure vide
    désépingle ; `pin_task`/`unpin_task` supprimées.
  - Verify: `pytest tests/test_kairos_route.py -q`
  - Dépend de : Z1

- [x] **Z3** — Passe ergonomie (S)
  - Acceptance : bordure par bucket d'urgence, badge de priorité visible, « Fait »
    repliée par défaut.
  - Verify: `pytest tests/test_kairos_route.py -q`
  - Dépend de : Z2
  - Note : la bordure par bucket a été étendue à toutes les sections actionnables
    (pas seulement la liste planifiée), pour rester valable quelle que soit l'heure
    réelle (`datetime.now()` du scheduler peut vider `schedule.scheduled` en soirée).

- [x] **CHECKPOINT FINAL** — `pytest -q` complet + migration base phase 4 peuplée +
      Success Criteria phase 5 + vérification manuelle uvicorn

---

# Phase 6 : édition consolidée, liaison fiche, GitLab multi-projet

Détail : `tasks/plan.md` § Phase 6. Ordre = ordre d'implémentation.

- [x] **X1** — Auto-import GitLab élargi à tous les projets en cache (M)
  - Acceptance : deux projets partageant le même `iid` créent deux tâches
    distinctes ; tâche existante au format ancien rebaptisée sans doublon.
  - Verify: `pytest tests/test_tasks_gitlab_sync.py tests/test_kairos_route.py -q`

- [x] **X2** — Liaison manuelle vers une fiche `Ticket` (M)
  - Acceptance : lier/délier depuis l'édition ; badge affiché ; id invalide
    ignoré ; aucune écriture Redmine/GitLab.
  - Verify: `pytest tests/test_tasks_models.py tests/test_kairos_route.py -q`

- [x] **X3** — Sous-tâches en lot + bloqueurs en liste à cocher (M)
  - Acceptance : un seul POST crée plusieurs sous-tâches + pose les bloqueurs ;
    cycle ignoré silencieusement ; anciennes routes deps supprimées.
  - Verify: `pytest tests/test_kairos_route.py -q`
  - Dépend de : X2

- [x] **CHECKPOINT FINAL** — `pytest -q` complet + migration base phase 5 peuplée
      (tâches GitLab au format d'ancien external_id) + Success Criteria phase 6 +
      vérification manuelle uvicorn

---

# Phase 7 : garde-fous d'usage, temps réel exploité, robustesse GitLab

Détail : `tasks/plan.md` § Phase 7. Ordre = ordre d'implémentation.

- [x] **Y1** — Détection des tâches qui traînent (S)
  - Acceptance : badge si en retard au-delà du seuil ou sans date et non touchée
    depuis longtemps ; ne change jamais le tri.
  - Verify: `pytest tests/test_tasks_staleness.py tests/test_kairos_route.py -q`

- [x] **Y2** — Garde-fou de surcharge de priorité maximale (S)
  - Acceptance : bandeau si trop de tâches à priorité ≤ 1, silencieux sinon.
  - Verify: `pytest tests/test_tasks_scheduling.py tests/test_kairos_route.py -q`

- [x] **Y3** — Durcir l'auto-import GitLab (réassignation à chaud) (S)
  - Acceptance : issue réassignée en restant ouverte → tâche archivée (nouveau
    test, aucun changement de production).
  - Verify: `pytest tests/test_tasks_gitlab_sync.py -q`

- [x] **Y4** — Suivi du temps réel exploité : correction + ventilation du jour (M)
  - Acceptance : le total « aujourd'hui » ne compte plus que les sessions du jour
    (correction d'un bug réel) ; ventilation par type ajoutée.
  - Verify: `pytest tests/test_tasks_time.py tests/test_kairos_route.py -q`

- [x] **Y5** — Agrégat hebdomadaire du temps réel (S)
  - Acceptance : la vue semaine affiche une synthèse par type, sans graphique.
  - Verify: `pytest tests/test_kairos_route.py -q`
  - Dépend de : Y4

- [x] **CHECKPOINT FINAL** — `pytest -q` complet + Success Criteria phase 7 +
      vérification manuelle uvicorn
