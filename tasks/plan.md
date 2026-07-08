# Plan d'implémentation : « Kairos » (dashboard de tâches natif)

## Context

`pilotage-pleiade-gitlab` est aujourd'hui un outil de synchro Redmine ↔ GitLab pour la
dette technique. L'utilisateur veut en faire son point d'entrée unique vers tout ce
qu'il doit faire, en commençant par un dashboard qui répond concrètement à « qu'est-ce
que je fais et dans quel ordre, sachant qu'une réunion 13h-14h m'empêche de traiter le
sujet urgent avant 14h05 ». La spec complète, validée par l'utilisateur, vit dans
`SPEC_KAIROS.md` (déjà committée sur la branche, PR #32 en brouillon) : synchro
Superproductivity en lecture seule par appel HTTP direct (`127.0.0.1:3876`, pas de
MCP), priorité comme champ natif éditable (SP n'expose pas ce champ), calendrier
personnel via `timetree-exporter` (isolé, dégradable), réunions pro saisies à la main,
base SQLite séparée, MVP = affichage + priorisation, sans CRUD complet ni
sous-tâches/récurrence. Ce plan découpe cette spec en tâches implémentables.

Deux points sous-spécifiés par la spec ont été tranchés avec l'utilisateur avant ce
plan : (1) tri par **buckets d'urgence** (en retard/aujourd'hui → priorité max → cette
semaine → reste, priorité en tie-break intra-bucket), (2) la vue jour affiche **tout le
backlog `todo`** ordonné par urgence (pas seulement les tâches à deadline du jour, sinon
la vue serait quasi vide tant que peu de tâches SP ont un `dueDay`).

## Conventions du codebase à réutiliser (confirmées par exploration)

- Routes : `@app.get(path, response_class=HTMLResponse)` sur l'unique `app` de
  `main.py`, `session: Session = Depends(get_session)`, `get_settings()` appelé inline.
  Pattern `_render_x(request, session, ..., base="")` → `templates.TemplateResponse`.
  Voir `main.py` route `dashboard` (~340-420), `gitlab_pilotage` (~1162).
- Clients HTTP : classe simple, `(base_url, ..., *, client=None, timeout, retries)`,
  `client=` injectable pour les tests, httpx **sync**, `raise_for_status()`. Instanciée
  par requête via une factory `_make_x_client()` dans `main.py`, pas de `Depends`.
  Voir `app/clients/redmine.py`.
- Templates : blocks `title`/`topbar_title`/`content`/`scripts` dans `base.html`,
  sidebar = macro `_sidebar.html` avec entrées `<a class="sb-item">` codées en dur.
  Pas de JS framework, formulaires HTML classiques (`method="post"`), redirection 303.
  Bandeaux d'avertissement : `<div class="banner warning">`.
- Tests : `respx` pour mocker httpx (`tests/test_clients.py`). Fixture `session` en
  mémoire dans `conftest.py` (à dupliquer pour la nouvelle base tâches).
- Cache/refresh : pas de scheduler, juste une ligne de méta (`GitLabRefreshMeta`) avec
  `last_refresh_at`/`last_outcome`, fetch-on-load avec TTL — à répliquer pour SP/TimeTree
  (`TaskSyncMeta`).

## Séquencement (dépendances)

```
T1 (config) ──┬─→ T3 (client SP) ──→ T4 (sync SP) ─────┐
              ├─→ T2 (DB tâches) ──┬─→ T5 (algo tri) ────┤
              │                     └─→ T6 (seam TimeTree)┘
              └─→ T7 (route + template + sidebar) ─→ T8 (vue semaine) → [CHECKPOINT: MVP démontrable]
                                                            T9 (vraie intégration TimeTree, isolée) → [CHECKPOINT final]
```

T5 est indépendant de T3/T4/T6 (peut être développé/testé dès que T2 existe). **T9 est
volontairement en dernier et isolé** : tout le reste du MVP est démontrable même si
`timetree-exporter` s'avère inutilisable en pratique.

## Tâches

### T1 — Réglages `Settings` (S)
**Fichiers** : `app/config.py`, `.env.example`, `tests/test_config.py`
Ajouter : `tasks_database_path`/`tasks_database_url`, `superproductivity_base_url`
(défaut `http://127.0.0.1:3876`), `timetree_email`/`timetree_password`/
`timetree_calendar_code`/`timetree_cache_ttl_minutes`, `timetree_configured` (property,
mirror `gitlab_configured`), `default_task_duration_minutes` (30), `meeting_buffer_minutes`
(5), `workday_start_hour`/`workday_end_hour` (9/18). Docstrings FR comme l'existant.
**Vérif** : `pytest tests/test_config.py -q`.

### T2 — Base tâches : modèles + engine/session (S)
**Fichiers** : `app/tasks_models.py`, `app/tasks_db.py`, `main.py` (appel
`init_tasks_db()` dans `lifespan`), `tests/conftest.py` (fixture `tasks_session`),
`tests/test_tasks_models.py`
`TasksBase` dédiée. `Task` (title, description, priority: int|None, deadline: date|None,
project_tag, status, source native/superproductivity, external_id, timestamps,
`UniqueConstraint(source, external_id)`). `TimeBlock` (title, start, end, source
manual/timetree). `TaskSyncMeta` (source, last_synced_at, last_outcome, last_detail,
item_count — mirror `GitLabRefreshMeta`). Base strictement séparée de `pilotage.db`.
**Vérif** : `pytest tests/test_tasks_models.py -q`.

### T3 — Client HTTP Superproductivity (S)
**Fichiers** : `app/clients/superproductivity.py`, `tests/test_superproductivity_client.py`
Classe `SuperproductivityClient` — **uniquement** `list_tasks()`, `list_projects()`,
`list_tags()` (GET). Aucune méthode d'écriture sur la classe (garde-fou structurel).
Parsing défensif (`.get()` partout — la forme JSON exacte de `GET /tasks` n'est pas
documentée dans `scripts/superproductivity-mcp.py`, à vérifier contre une instance SP
réelle si possible pendant l'implémentation, sans bloquer dessus).
**Vérif** : `pytest tests/test_superproductivity_client.py -q` (respx, zéro réseau réel).

### T4 — Synchronisation SP → base native (S)
**Fichiers** : `app/tasks_sync.py`, `tests/test_tasks_sync.py`
`sync_superproductivity_tasks(session, client) -> SyncResult`. Upsert par
`(source='superproductivity', external_id)` ; ne touche **jamais** `priority` sur une
tâche existante (champ natif). Tâche disparue côté SP → `status='archived'` local,
jamais supprimée. Écrit `TaskSyncMeta`.
**Vérif** : `pytest tests/test_tasks_sync.py -q` — idempotence, non-écrasement de la
priorité, archivage, méta à jour.

**CHECKPOINT 1** : `pytest tests/test_superproductivity_client.py tests/test_tasks_sync.py -q`.

### T5 — Algorithme d'ordonnancement (M)
**Fichiers** : `app/tasks_scheduling.py`, `tests/test_tasks_scheduling.py`
Fonction pure `build_day_schedule(tasks, busy_blocks, day, *, now, settings) ->
ScheduledDay` (aucun accès DB/réseau). Tri par buckets d'urgence (décision actée
ci-dessus) puis priorité en tie-break, idiome « None en dernier » comme
`app/queries.py`. Curseur temporel : avance au-delà de tout bloc occupé chevauchant
(+ `meeting_buffer_minutes`), assigne `start_at`, avance de
`default_task_duration_minutes` ; au-delà de `workday_end_hour` → `unscheduled`.
`pushed=True` + note explicite quand un bloc occupé a repoussé le créneau.
**Vérif** : `pytest tests/test_tasks_scheduling.py -q` — nominal, **cas emblématique
réunion 13h-14h → 14h05**, bloc sans impact, listes vides, journée pleine.

**CHECKPOINT 2** : ce test seul démontre le critère de succès n°2 de la spec, avant
toute route/template — c'est la fonctionnalité la plus risquée du sprint.

### T6 — Interface TimeTree (seam) + dégradation propre (S)
**Fichiers** : `app/calendar/__init__.py`, `app/calendar/timetree_source.py`,
`tests/test_timetree_source.py`
`fetch_busy_slots(start, end, *, settings) -> TimeTreeFetchResult`. Cette tâche
n'appelle **pas encore** `timetree-exporter` : retourne `ok=False` avec un détail
explicite (« non configuré » / « intégration à venir »), jamais d'exception. Le contrat
de retour est celui que T7 consomme définitivement (pas de re-câblage en T9).
**Vérif** : `pytest tests/test_timetree_source.py -q`.

### T7 — Route « Kairos » + priorité + bloc manuel + sidebar (M)
**Fichiers** : `main.py`, `templates/kairos.html`, `templates/_sidebar.html`,
`tests/test_kairos_route.py`
`_make_superproductivity_client()` (factory, mirror des clients existants).
`_render_kairos(...)` : déclenche la synchro SP si le TTL (2 min) est dépassé,
**dans un `try/except` large** (SP injoignable → bandeau, jamais de 500) ; appelle
`fetch_busy_slots` ; charge `Task`/`TimeBlock` du jour ; appelle `build_day_schedule`.
`GET /kairos`. `POST /kairos/tasks/{id}/priority` (form, redirect 303).
`POST /kairos/blocks` (form titre/début/fin, redirect 303). Template : liste
ordonnée avec horaire/`pushed_note`, section « sans créneau aujourd'hui », formulaire
priorité inline par tâche, `<details>` pour ajouter un bloc manuel, bandeaux
d'avertissement SP/TimeTree. Sidebar : nouvelle entrée **en tête** de `sb-nav`, avant
« Liaisons » (contrainte actée de la spec).
**Vérif** : `pytest tests/test_kairos_route.py -q` (`TestClient` +
`dependency_overrides`, respx). Puis `uvicorn app.main:app --reload` → vérification
manuelle réelle du rendu et du cas réunion 13h-14h.

**CHECKPOINT 3 (MVP démontrable)** : critères de succès 1, 3, 4, 6 de la spec
vérifiables en conditions réelles ; le n°5 (dégradation TimeTree) déjà couvert sans
TimeTree fonctionnel.

### T8 — Vue semaine (S)
**Fichiers** : `main.py` (extension route, `view=week`), `templates/kairos.html`,
`tests/test_kairos_route.py`
Grille 7 jours : tâches groupées par `deadline`, blocs occupés du jour. Pas de
minutage fin (réservé à la vue jour). Lien vers le détail du jour courant.
**Vérif** : `pytest tests/test_kairos_route.py -q` (cas semaine).

### T9 — Intégration réelle TimeTree (M) — isolée, peut glisser sans bloquer le reste
**Fichiers** : `pyproject.toml` (+ `timetree-exporter`), `app/calendar/timetree_source.py`
(remplace le TODO de T6), `tests/test_timetree_source.py`
CLI confirmée (`timetree-exporter -o out.ics -c <code>`, auth par
`TIMETREE_EMAIL`/`TIMETREE_PASSWORD`) : `subprocess.run(...)` vers un fichier `.ics`
temporaire, parsing via `icalendar`, filtrage sur `[start, end)`. **Tout le bloc** dans
un `try/except` large (`SubprocessError`, `TimeoutExpired`, `OSError`, `ValueError`) →
dégradation, jamais de propagation. TTL via `TaskSyncMeta(source='timetree')` pour
éviter le rate-limiting TimeTree (risque signalé explicitement par le mainteneur du
paquet).
**Vérif** : tests avec `subprocess.run` mocké (jamais d'appel réel), fixture `.ics`
connue, gestion d'échec. `pytest tests/test_timetree_source.py -q`. Smoke test manuel
optionnel avec vrais identifiants en `.env` local (jamais committés, hors CI).

**CHECKPOINT 4 (final)** : `pytest -q` (suite complète, zéro réseau réel) + relecture
des 6 critères de succès de `SPEC_KAIROS.md`.

## Fichiers critiques

- `app/tasks_models.py`, `app/tasks_db.py` — nouvelle base séparée
- `app/clients/superproductivity.py` — client lecture seule
- `app/tasks_sync.py` — upsert idempotent
- `app/tasks_scheduling.py` — cœur de l'algorithme (fonction pure, testable en isolation)
- `app/calendar/timetree_source.py` — seam TimeTree, dégradation propre
- `main.py`, `templates/kairos.html`, `templates/_sidebar.html` — route et UI

## Vérification de bout en bout

1. `pytest -q` depuis `pilotage-pleiade-gitlab/` après chaque tâche (checkpoints 1/2/3/4).
2. `uvicorn app.main:app --reload`, ouvrir `/kairos` : vérifier l'ordre des tâches,
   créer un bloc manuel 13h-14h et une tâche prioritaire du jour, confirmer qu'elle
   apparaît décalée à 14h05 avec la note explicative.
3. Couper Superproductivity (ou pointer `superproductivity_base_url` vers un port mort)
   → la page doit toujours répondre 200 avec un bandeau, jamais un 500.
4. Sans identifiants TimeTree → bandeau « non configuré », page toujours fonctionnelle.
5. Relire les « Success Criteria » de `SPEC_KAIROS.md` un par un.

---

# Phase 2 : suivi de tâches complet + time blocking

Phase 1 livrée et validée en réel (bugs SP/TimeTree corrigés). Périmètre phase 2 acté
avec l'utilisateur (voir SPEC_KAIROS.md § Phase 2) : CRUD natif complet, time
blocking mixte auto + épinglage, sous-tâches + récurrence, dashboard actionnable
(actions en un clic, timeline verticale, progression du jour), et sortie de
Superproductivity dès le CRUD livré.

**Contrainte transverse critique** : `tasks.db` contient des données réelles chez
l'utilisateur (tâches importées + priorités). Toute évolution de schéma passe par une
migration légère `ADD COLUMN` (pattern `_MIGRATION_COLUMNS` de `db.py`), testée sur
une base de schéma phase 1 peuplée.

## Séquencement

```
U1 (schéma + migration) → U2 (CRUD) → U3 (récurrence) → [CP1 : gestion de tâches complète]
U1 → U5 (scheduling v2 : durées + épinglage + débordement) → U6 (timeline + progression) → [CP2 : time blocking démontrable]
U4 (sous-tâches : import SP + UI + feuilles) après U5 (touche l'algo)
U7 (sortie SP + doc) en dernier → [CP3 final]
```

### U1 — Schéma v2 + migration légère (S)
`tasks_models.py` : `Task` += `estimated_minutes: int|None`, `pinned_start: datetime|None`,
`parent_id: int|None` (auto-référent, pas de FK contrainte — cohérent avec le style
existant), `recurrence: str` (''/daily/weekdays/weekly/monthly). `tasks_db.py` : hook
`_TASKS_MIGRATION_COLUMNS` + application dans `init_tasks_db()`.
**Vérif** : test qui crée une base au schéma phase 1 (SQL brut), la peuple, lance
`init_tasks_db()` → colonnes ajoutées, données intactes. `pytest tests/test_tasks_models.py -q`.

### U2 — CRUD natif (M)
Routes : `POST /kairos/tasks` (création rapide inline : titre + priorité/deadline/durée
optionnels), `POST /kairos/tasks/{id}/edit` (tous champs), `.../done` (bascule fait/à
faire), `.../snooze` (deadline → demain), `.../delete` (suppression si native, archivage si
source SP — sinon la synchro la recréerait). Template : formulaire de création rapide en
tête, panneau d'édition par tâche (pattern `.edit-popup`/`<details>` existant), bouton
« fait » et « demain » sur chaque ligne. Sync SP : importe `timeEstimate` (ms→min) dans
`estimated_minutes` seulement si renseigné côté SP (jamais d'écrasement par du vide).
**Vérif** : `pytest tests/test_kairos_route.py -q` (création/édition/fait/snooze/
suppression vs archivage), vérification manuelle uvicorn.

### U3 — Récurrence (S)
Champ `recurrence` dans le panneau d'édition. `.../done` sur une récurrente : marque
l'occurrence faite ET crée la suivante (daily +1j, weekdays jour ouvré suivant, weekly
+7j, monthly +1 mois ; base = deadline si présente, sinon aujourd'hui). Rouvrir une
occurrence ne crée pas de doublon.
**Vérif** : tests dédiés par règle dans `tests/test_tasks_recurrence.py`.

**CHECKPOINT 1** : création → édition → fait → récurrence, démontrable sans SP.

### U5 — Scheduling v2 : durées réelles + épinglage + débordement (M)
`tasks_scheduling.py` : (1) les tâches épinglées du jour sont posées à `pinned_start`
(chevauchement avec un bloc occupé = signalé, pas déplacé) ; (2) leurs créneaux
deviennent indisponibles pour l'auto ; (3) l'auto remplit les trous par urgence avec
`estimated_minutes` (repli réglage) ; (4) calcule les stats du jour : minutes requises,
minutes disponibles, débordement. Routes `.../pin` (formulaire heure) et `.../unpin`.
Le cas emblématique 13h-14h→14h05 reste vert.
**Vérif** : `pytest tests/test_tasks_scheduling.py -q` (épinglée respectée, auto autour,
conflit signalé, débordement, non-régression 14h05).

### U6 — Timeline verticale + progression du jour (M)
Le rendu calcule des offsets (minutes depuis `workday_start` → top/height) pour chaque
bloc occupé et bloc de travail ; template : colonne agenda verticale (CSS pur, pas de JS)
+ en-tête de progression (faites/prévues, requis vs disponible, alerte débordement),
« à faire maintenant » mis en avant. La liste ordonnée existante reste (les deux se
complètent).
**Vérif** : tests de rendu (marqueurs timeline, chiffres de progression), vérification
manuelle uvicorn avec réunion + tâches épinglées.

**CHECKPOINT 2** : time blocking complet démontrable (agenda du jour réaliste).

### U4 — Sous-tâches (M)
Sync SP : ne plus filtrer les sous-tâches — upsert en deux passes (mères puis filles,
`parent_id` résolu via `external_id`). UI : filles indentées sous la mère, avancement
n/m sur la mère, création de sous-tâche depuis le panneau d'édition. Scheduling : seules
les **feuilles** à faire sont planifiées (une mère avec filles à faire n'est pas
elle-même placée).
**Vérif** : `pytest tests/test_tasks_sync.py tests/test_tasks_scheduling.py
tests/test_kairos_route.py -q`.

### U7 — Sortie de Superproductivity + documentation (S)
Réglage `superproductivity_sync_enabled` (défaut `true`) : à `false`, aucun appel SP ni
bandeau. Route/bouton « Adopter les tâches SP » (`source` → `'native'`), refusée tant
que la synchro est active. Section « Kairos » dans le README du projet (variables
d'env, mode d'emploi, procédure de sortie de SP).
**Vérif** : tests (sync désactivée = zéro appel réseau, adoption refusée si active,
adoption convertit), relecture README.

**CHECKPOINT 3 (final)** : `pytest -q` complet + Success Criteria phase 2 de la spec un
par un + test de migration depuis une base phase 1 peuplée.

---

# Phase 3 : deep work + dépendances (livrée — résumé rétroactif)

Voir `SPEC_KAIROS.md` § Phase 3 pour le détail. Implémentée et poussée :

- **V1-V2 Dépendances** : `TaskDependency`, moteur pur `app/tasks_dependencies.py`
  (blocage transitif, cycles neutralisés par Kahn, urgence dérivée du chemin critique
  sans jamais modifier `Task.priority`). Section « Bloquées », badge « chemin
  critique », routes ajout/retrait (refus de cycle).
- **V3 Suivi du temps réel** : `WorkSession`, moteur pur `app/tasks_time.py`, chrono
  démarrer/arrêter (une session ouverte à la fois), minuteur vivant côté client, réel
  vs estimé.
- **V4 Blocs deep-work protégés** : `TimeBlock.kind='deepwork'`, fenêtre réservée à
  une seule tâche non fragmentée, les autres la contournent.

---

# Phase 4 : GitLab assigné, date programmée, récurrence calendaire

## Context

Trois manques ressentis à l'usage (voir `SPEC_KAIROS.md` § Phase 4) : les fiches
GitLab assignées vivent dans un autre onglet ; deadline et « quand je compte m'y
mettre » sont confondues ; les obligations calées sur une date calendaire (le 23 de
chaque mois) ne sont pas représentables par la récurrence actuelle (qui n'avance qu'à
la complétion). Décisions actées : synchro GitLab **mutualisée** (relit le cache
existant, zéro appel réseau neuf), récurrence calendaire **sur la table `Task`
existante**, décalage jours ouvrés incluant les **jours fériés français**
(`settings.holiday_set`, extensible aux congés futurs sans redesign).

## Réutilisation confirmée

- `app/models.py::GitLabIssueCache.assignee_list`/`due_date` — alimenté par le
  rafraîchissement existant de l'onglet Pilotage GitLab, **aucun nouvel appel API**.
- `app/workdays.py::is_workday`/`add_business_days` (+ `settings.holiday_set`,
  déjà FR + `extra_holidays`) ; il manque `previous_business_day` (symétrique).
- `app/tasks_recurrence.py::next_deadline` (clamp fin de mois) pour
  `recurrence_day_of_month`.
- `app/tasks_db.py::_TASKS_MIGRATION_COLUMNS` (pattern ADD COLUMN).
- `app/main.py::_sync_superproductivity_if_stale` — patron pour la synchro GitLab.
- Route `/kairos` : première fonctionnalité à lire les **deux** bases dans la
  même requête (`session` pilotage.db + `tasks_session` tasks.db).

## Séquencement

```
W1 (réglages + jour ouvré arrière) ─┬─→ W3 (synchro GitLab) ──────────────────┐
                                     │                                        │
W2 (schéma v4 + migration) ─────────┼─→ W4 (scheduled_date : éligibilité +   ─┼─→ [CP1]
                                     │     buckets + « Programmées plus tard »)│
                                     └─→ W5 (récurrence calendaire) ──────────┤
                                     └─→ W6 (snooze jour ouvré avant) ────────┘
                                                                    W7 (invariant) → [CP final]
```

## Tâches

### W1 — Réglages phase 4 + jour ouvré arrière (fériés inclus) (S)
**Fichiers** : `app/config.py`, `app/workdays.py`, `.env.example`,
`tests/test_workdays.py`, `tests/test_config.py`
`Settings.gitlab_assignee_username: str = ""` (vide = désactivé).
`workdays.previous_business_day(start, holidays=None)` (symétrique de
`add_business_days`) + `workdays.on_or_before_business_day(d, holidays=None)`
(retourne `d` si ouvré, sinon `previous_business_day`).
**Vérif** : `pytest tests/test_workdays.py tests/test_config.py -q` — recul simple,
recul chaîné (férié + week-end), jour déjà ouvré inchangé.

### W2 — Schéma `Task` v4 + migration (S)
**Fichiers** : `app/tasks_models.py`, `app/tasks_db.py`, `tests/test_tasks_models.py`
`Task.scheduled_date: date|None`, `Task.recurrence_day_of_month: int|None`,
`Task.recurrence_period: str` (défaut `""`, anti-doublon de génération). Migration
ADD COLUMN dans `_TASKS_MIGRATION_COLUMNS["task"]`.
**Vérif** : `pytest tests/test_tasks_models.py -q`, test de migration sur base
**schéma phase 3** peuplée.

### W3 — Synchronisation GitLab assignée, mutualisée (M)
**Fichiers** : `app/tasks_gitlab_sync.py` (nouveau), `app/main.py`,
`tests/test_tasks_gitlab_sync.py`, `tests/test_kairos_route.py`
`sync_assigned_gitlab_tasks(gitlab_session, tasks_session, project, assignee) ->
SyncResult` : requête SQL sur `GitLabIssueCache` (état ouvert + assigné), upsert
`Task(source='gitlab', external_id=str(iid))`, `deadline` depuis `due_date`,
priorité jamais écrasée, disparue/fermée → `archived`. Route `GET /kairos` gagne
`session: Session = Depends(get_session)`. Lecture locale : pas de TTL.
**Vérif** : `pytest tests/test_tasks_gitlab_sync.py -q` (deux sessions in-memory) +
test de route (seed `GitLabIssueCache` via override `get_session`).

**CHECKPOINT 1** : `pytest tests/test_workdays.py tests/test_tasks_models.py
tests/test_tasks_gitlab_sync.py -q` + vérification manuelle uvicorn.

### W4 — `scheduled_date` : éligibilité, buckets, « Programmées plus tard » (M)
**Fichiers** : `app/tasks_scheduling.py`, `app/main.py`, `templates/kairos.html`,
`tests/test_tasks_scheduling.py`, `tests/test_kairos_route.py`
`ScheduledDay.later: list[Task]`. Éligibilité (révise « tout le backlog » de la
phase 1) : masquée aujourd'hui **seulement si** `scheduled_date` future **et**
(`deadline is None` ou `deadline > day`) — l'échéance prime toujours. `_urgency_bucket`
étendu : 0. retard (deadline ou scheduled_date `<=day`) → 1. priorité max →
2. `scheduled_date==day` → 3. échéance semaine → 4. reste. Champ `scheduled_date`
dans les formulaires. Section repliée « Programmées plus tard » sous « Bloquées ».
**Vérif** : `pytest tests/test_tasks_scheduling.py tests/test_kairos_route.py -q`
— programmée demain sans deadline → `later` ; programmée demain + deadline
aujourd'hui → reste visible ; programmée en retard → bucket 0.

**CHECKPOINT 2** : uvicorn — tâche programmée lundi (deadline mardi) un vendredi :
absente de l'agenda, visible dans « Programmées plus tard ».

### W5 — Récurrence calendaire (`monthly_on_day`) (M)
**Fichiers** : `app/tasks_recurrence.py`, `app/main.py`, `templates/kairos.html`,
`tests/test_tasks_recurrence.py`
`ensure_calendar_occurrences(session, today, holidays)` : par série
`(title, recurrence_day_of_month)`, si aucune occurrence `recurrence_period ==
today.strftime("%Y-%m")`, en crée une : `deadline = on_or_before_business_day(date
borné, holidays)`, copie titre/priorité/projet/durée. Ne touche jamais une occurrence
antérieure encore ouverte. Portée : mois courant seulement, pas de rattrapage
rétroactif. UI : option « le … du mois » + `recurrence_day_of_month`.
**Vérif** : `pytest tests/test_tasks_recurrence.py -q` — génération idempotente,
23 dimanche → vendredi, 23 vendredi+férié → jeudi, mois précédent non écrasé.

### W6 — Snooze : décalage avant, jour ouvré + fériés (S)
**Fichiers** : `app/tasks_recurrence.py`, `app/main.py`, `tests/test_tasks_recurrence.py`
`next_snooze_date(deadline, today, holidays)` (pure, testable sans date système
réelle) : `base = deadline if (deadline and deadline > today) else today`, puis
`add_business_days(base, 1, holidays)`. `snooze_task` l'utilise.
**Vérif** : `pytest tests/test_tasks_recurrence.py -q` — vendredi → lundi (ou mardi
si férié) ; non-régression `test_snooze_moves_deadline_to_tomorrow`.

### W7 — Invariant « aucune tâche jamais perdue » (S)
**Fichiers** : `tests/test_tasks_scheduling.py`
Pas de nouvelle dépendance (`hypothesis` écarté) : générateur aléatoire fait main,
graine fixe, ~50 tirages (statuts, priorités, deadlines, scheduled_date, épinglage,
deep-work, mères/filles). Invariant : toute tâche `todo` non-mère-à-filles-ouvertes
et non-bloquée apparaît dans **exactement une** des listes
`scheduled`/`unscheduled`/`later`.
**Vérif** : `pytest tests/test_tasks_scheduling.py -q`.

**CHECKPOINT FINAL** : `pytest -q` complet + migration sur base phase 3 peuplée +
Success Criteria phase 4 un par un + vérification manuelle uvicorn.

## Fichiers critiques (phase 4)

- `app/workdays.py` — `previous_business_day`, `on_or_before_business_day`
- `app/tasks_models.py` — `scheduled_date`, `recurrence_day_of_month`, `recurrence_period`
- `app/tasks_gitlab_sync.py` (nouveau) — lecture mutualisée de `GitLabIssueCache`
- `app/tasks_scheduling.py` — éligibilité `later`, buckets étendus
- `app/tasks_recurrence.py` — `ensure_calendar_occurrences`, `next_snooze_date`
- `app/main.py`, `templates/kairos.html` — wiring, section « Programmées plus tard »

---

# Phase 5 : points de Fibonacci, typologie de tâches, ergonomie

## Context

Phase 4 livrée et vérifiée. Deux métadonnées de préparation pour de futures
analyses (pas encore assez d'historique pour les faire maintenant) : points de
Fibonacci et typologie de tâche — **purement informatives**, sans conversion vers
`estimated_minutes` ni impact sur l'ordonnancement (`SPEC_KAIROS.md` § Phase 5,
décisions actées). Plus deux demandes d'ergonomie : fusionner l'épinglage (heure
fixe) dans le formulaire d'édition principal, et une passe ciblée sur la hiérarchie
visuelle. L'ajout groupé de plusieurs sous-tâches à la fois est différé.

## Réutilisation confirmée

- **Migration légère** `_TASKS_MIGRATION_COLUMNS["task"]` (`app/tasks_db.py`).
- **Bucket d'urgence déjà calculé** : `urgency_key(task, day)[0]`
  (`app/tasks_scheduling.py`), déjà utilisé côté route pour l'urgence dérivée — un
  mapping `bucket_of` supplémentaire suffit, aucun nouveau calcul.
- **Parsing heure existant** dans `pin_task` (`app/main.py`, à retirer) — logique
  déplacée telle quelle dans `edit_task`.
- **Idiome `<details class="card"><summary class="collapser">`** déjà utilisé pour
  « Programmées plus tard »/« Ajouter un créneau » — même motif pour « Fait ».
- **Variables CSS de badges** existantes (`--bad-fg`, `--warn-fg`, `--info-fg`...).
- `fibonacci_points` suit le patron `priority`/`estimated_minutes` (pas de
  whitelist serveur) ; `task_type` suit le patron `recurrence` (whitelist, comme
  acté dans la spec).

## Séquencement

```
Z1 (modèle + migration) ──→ Z2 (routes + fusion épinglage + template) ──→ Z3 (ergonomie) → [CHECKPOINT FINAL]
```

### Z1 — Modèle : `task_type` + `fibonacci_points` + migration (S)
**Fichiers** : `app/tasks_models.py`, `app/tasks_db.py`, `tests/test_tasks_models.py`
`Task.task_type` (`String(32)`, défaut `""`) + `Task.fibonacci_points`
(`Integer`, nullable). Constantes co-localisées avec `Task` : `TASK_TYPE_LABELS`
(7 entrées, valeur → libellé FR) et `FIBONACCI_SCALE = (1, 2, 3, 5, 8, 13, 21)`.
Migration ADD COLUMN.
**Vérif** : `pytest tests/test_tasks_models.py -q`, dont migration sur base
**phase 4** peuplée.

### Z2 — Routes : champs édition + fusion de l'épinglage (M)
**Fichiers** : `app/main.py`, `templates/kairos.html`, `tests/test_kairos_route.py`
`edit_task()` gagne `task_type`/`fibonacci_points`/`pin_time`/`pin_day` (pas
`create_native_task`, décision actée). Épinglage fusionné : `pin_time` vide →
désépingle, sinon parse `HH:MM` (logique reprise de `pin_task`), erreur ignorée.
Suppression de `pin_task`/`unpin_task` (plus d'appelant). Template : `<select>`
type/points dans `mj-edit-row`, champ heure fixe **dans** `mj-edit-form` (remplace
le formulaire pin/unpin séparé) ; badges type/points dans `task_meta`.
**Vérif** : `pytest tests/test_kairos_route.py -q` — un seul POST `/edit` pose
type + points + heure ; heure vide désépingle ; valeurs invalides ignorées ; badges
rendus ; tests pin/unpin réécrits pour passer par `/edit`.

### Z3 — Passe ergonomie (S)
**Fichiers** : `app/main.py`, `templates/kairos.html`, `static/style.css`,
`tests/test_kairos_route.py`
`bucket_of` exposé au contexte (`_render_kairos`) ; bordure colorée par bucket
sur `.kairos-item` de la liste planifiée ; badge de priorité visible dans
`task_meta` ; section « Fait » repliée par défaut (même idiome que « Programmées
plus tard »).
**Vérif** : `pytest tests/test_kairos_route.py -q` — bordure bucket 0 sur tâche
en retard, absente sur tâche normale ; badge priorité présent ; « Fait » repliée.

**CHECKPOINT FINAL** : `pytest -q` complet + migration sur base phase 4 peuplée +
Success Criteria phase 5 un par un + vérification manuelle uvicorn.

## Fichiers critiques (phase 5)

- `app/tasks_models.py` — `task_type`, `fibonacci_points`, `TASK_TYPE_LABELS`, `FIBONACCI_SCALE`
- `app/main.py` — fusion épinglage dans `edit_task`, suppression `pin_task`/`unpin_task`, `bucket_of`
- `templates/kairos.html`, `static/style.css` — panneau d'édition, badges, bordure, « Fait » repliée

---

# Phase 6 : édition consolidée, liaison fiche, GitLab multi-projet

## Context

Trois demandes indépendantes après usage réel de la phase 5, actées avec
l'utilisateur : (1) fusionner sous-tâches en lot + bloqueurs en liste à cocher
dans le même « Enregistrer » que les infos ; (2) liaison manuelle en lecture
seule vers une fiche `Ticket` (Pléiade/GitLab) — pas d'auto-import possible,
`Ticket` n'a pas d'assigné unique ; (3) élargir l'auto-import GitLab assigné
(phase 4) à tous les projets déjà en cache, avec correction de l'`external_id`
(qualifié par projet, pour rester unique entre projets) et migration des
tâches déjà synchronisées.

## Réutilisation confirmée

- `would_create_cycle` (`app/tasks_dependencies.py`) déjà utilisé par
  `add_task_dependency` — réutilisé pour filtrer les bloqueurs cochés.
- Patron de fusion phase 5 (Z2) : plier une action à soumission séparée dans
  `edit_task`, supprimer la route devenue orpheline.
- Lecture cross-base déjà établie en phase 4 (`session` + `tasks_session` dans
  `_render_kairos`) — même geste pour résoudre `Ticket`.
- `app/tasks_gitlab_sync.py` (phase 4, W3) déjà mutualisé — retirer le filtre
  projet et qualifier `external_id`.

## Séquencement

```
X1 (GitLab multi-projet, indépendant)
X2 (liaison Ticket) ──→ X3 (sous-tâches + bloqueurs en lot, même formulaire) → [CHECKPOINT FINAL]
```

### X1 — Auto-import GitLab élargi à tous les projets en cache (M)
**Fichiers** : `app/tasks_gitlab_sync.py`, `app/main.py`,
`tests/test_tasks_gitlab_sync.py`, `tests/test_kairos_route.py`
`sync_assigned_gitlab_tasks` perd le paramètre `project` (filtre uniquement sur
`state='opened'` + assigné, tout projet confondu). `external_id` devient qualifié
(`f"{project}#{iid}"`) pour rester unique entre projets. Rekey de migration : une
tâche déjà synchronisée sous l'ancien format (`external_id == str(iid)` et
`project_tag` correspondant) est rebaptisée en place, jamais recréée à neuf.
**Vérif** : `pytest tests/test_tasks_gitlab_sync.py tests/test_kairos_route.py -q`.

### X2 — Liaison manuelle vers une fiche `Ticket` (M)
**Fichiers** : `app/tasks_models.py`, `app/tasks_db.py`, `app/main.py`,
`templates/kairos.html`, `tests/test_tasks_models.py`,
`tests/test_kairos_route.py`
`Task.linked_ticket_id` (Integer, nullable, référence locale sans FK cross-base).
`edit_task` gagne `linked_ticket_id` + `session: Session = Depends(get_session)`
pour valider l'existence. `_render_kairos` expose `ticket_choices`/
`ticket_by_id`. Template : `<select>` dans l'édition, badge dans `task_meta`.
Purement une référence en lecture, aucune écriture Redmine/GitLab.
**Vérif** : `pytest tests/test_tasks_models.py tests/test_kairos_route.py -q`.

### X3 — Sous-tâches en lot + bloqueurs en liste à cocher (M)
**Fichiers** : `app/main.py`, `templates/kairos.html`,
`tests/test_kairos_route.py`
`edit_task` gagne `new_subtasks` (textarea, une ligne = un titre) et
`blocker_ids` (liste de cases à cocher, état cible complet — diff calculé côté
route, ajout filtré par `would_create_cycle`, ignoré silencieusement en cas de
cycle). Suppression de `add_task_dependency`/`remove_task_dependency`. La
création rapide d'une seule sous-tâche reste en complément.
**Vérif** : `pytest tests/test_kairos_route.py -q`.

**CHECKPOINT FINAL** : `pytest -q` complet + migration sur base phase 5 peuplée
(avec tâches GitLab au format d'ancien `external_id`) + Success Criteria phase 6
+ vérification manuelle uvicorn.

## Fichiers critiques (phase 6)

- `app/tasks_gitlab_sync.py` — `external_id` qualifié, retrait filtre projet, rekey
- `app/tasks_models.py` — `Task.linked_ticket_id`
- `app/main.py` — `edit_task` (sous-tâches+bloqueurs en lot, liaison ticket),
  suppression `add_task_dependency`/`remove_task_dependency`
- `templates/kairos.html` — `mj-edit-form`, `task_meta`

---

# Phase 7 : garde-fous d'usage, temps réel exploité, robustesse GitLab

## Context

Après la phase 6, analyse des manques pour un vrai outil de deep work (consignée
dans `SPEC_KAIROS.md` § Analyse juillet 2026), puis choix d'un sous-ensemble
concret. Décisions actées : (1) « liaison dynamique des tickets » = durcir les
tests de l'auto-import GitLab existant (le désassignement archive déjà, mais
seul « issue fermée » est testé, pas « réassignée en restant ouverte ») — pas de
liaison avec le lien manuel `Ticket` (X2), hors périmètre ici ; (2) l'automatisme
de priorité est un bandeau de garde-fou de surcharge, pas un défaut auto-posé à
la création ; (3) pas de bannière de surcharge distincte (le débordement du jour
existe déjà) ; (4) mémorisation des durées par type différée au futur modèle ML.

En creusant le suivi du temps réel, un vrai bug a été trouvé : le badge « temps
travaillé aujourd'hui » (`spent_total_str`) additionne en fait toutes les
sessions jamais enregistrées, `sessions` n'étant jamais filtré par date dans
`_render_kairos`. Corrigé dans cette phase.

## Réutilisation confirmée

- Bucket de priorité maximale déjà défini dans `_urgency_bucket`
  (`app/tasks_scheduling.py`) — même définition pour le garde-fou.
- Module pur `app/tasks_time.py` (phase 3) — le bug se corrige en filtrant les
  `WorkSession` avant de les passer aux fonctions existantes, pas en les
  réécrivant ; nouvelles fonctions ajoutées au même module.
- Patron de module pur testé en isolation (`tasks_dependencies.py`,
  `tasks_recurrence.py`, `tasks_time.py`) — même approche pour
  `app/tasks_staleness.py` (nouveau).
- `sync_assigned_gitlab_tasks` (phase 4/6) : logique déjà correcte pour la
  réassignation, seul le test manque.
- Idiome de réglage (`app/config.py`) pour les 3 nouveaux seuils.
- Vue semaine existante (`_render_kairos`, branche `view == "week"`) : l'agrégat
  hebdomadaire s'y ajoute, pas une nouvelle vue.

## Séquencement

```
Y1 (tâches qui traînent) ─┐
Y2 (garde-fou priorité) ──┼─→ indépendants entre eux
Y3 (durcir GitLab, isolé, tests only)
Y4 (bug temps réel + ventilation jour) ──→ Y5 (agrégat hebdo, réutilise Y4)
```

### Y1 — Détection des tâches qui traînent (S)
**Fichiers** : `app/tasks_staleness.py` (nouveau), `app/config.py`, `app/main.py`,
`templates/kairos.html`, `tests/test_tasks_staleness.py`,
`tests/test_kairos_route.py`
`stale_overdue_days`/`stale_untouched_days` (réglages). `days_stale(task, today,
*, overdue_days, untouched_days) -> int | None` (pure) : jours de retard au-delà
du seuil (deadline/scheduled_date), ou jours sans date ni modification récente ;
`None` sinon. Signal d'affichage seul, **ne change jamais le tri**.
**Vérif** : `pytest tests/test_tasks_staleness.py tests/test_kairos_route.py -q`.

### Y2 — Garde-fou de surcharge de priorité maximale (S)
**Fichiers** : `app/tasks_scheduling.py`, `app/config.py`, `app/main.py`,
`templates/kairos.html`, `tests/test_tasks_scheduling.py`,
`tests/test_kairos_route.py`
`priority_overload_threshold` (réglage). `count_max_priority_tasks(tasks) -> int`
(pure, priority ≤ 1). Bandeau si le compte dépasse le seuil, silencieux sinon.
**Vérif** : `pytest tests/test_tasks_scheduling.py tests/test_kairos_route.py -q`.

### Y3 — Durcir l'auto-import GitLab (réassignation à chaud) (S)
**Fichiers** : `tests/test_tasks_gitlab_sync.py`
Aucun changement de production. Scinde le test existant en deux : fermeture
(renommé) + nouveau cas réassignation (issue reste `opened`, assignee retiré) →
tâche archivée.
**Vérif** : `pytest tests/test_tasks_gitlab_sync.py -q`.

### Y4 — Suivi du temps réel exploité : correction + ventilation du jour (M)
**Fichiers** : `app/tasks_time.py`, `app/main.py`, `templates/kairos.html`,
`tests/test_tasks_time.py`, `tests/test_kairos_route.py`
`sessions_in_range`/`sessions_on_day`/`spent_minutes_by_type` (pures, même patron
que l'existant). **Corrige le bug** : `spent_total_str` calculé sur les sessions
du jour affiché seulement (plus sur toutes les sessions jamais enregistrées).
Ajoute la ventilation par type dans l'en-tête.
**Vérif** : `pytest tests/test_tasks_time.py tests/test_kairos_route.py -q`
— régression explicite du bug (session d'hier exclue du total du jour).

### Y5 — Agrégat hebdomadaire du temps réel (S)
**Fichiers** : `app/main.py`, `templates/kairos.html`,
`tests/test_kairos_route.py`
Vue semaine : synthèse du temps réel par type sur la semaine, pas de graphique,
réutilise les fonctions de Y4.
**Vérif** : `pytest tests/test_kairos_route.py -q`.

**CHECKPOINT FINAL** : `pytest -q` complet + Success Criteria phase 7 +
vérification manuelle uvicorn.

## Fichiers critiques (phase 7)

- `app/tasks_staleness.py` (nouveau) — `days_stale`
- `app/tasks_time.py` — `sessions_in_range`, `sessions_on_day`, `spent_minutes_by_type`
- `app/tasks_scheduling.py` — `count_max_priority_tasks`
- `app/config.py` — 3 nouveaux seuils
- `app/main.py` — `_render_kairos` (correction du bug de scope temporel)
- `templates/kairos.html` — badge traîne, bandeau surcharge, ventilation jour/semaine
