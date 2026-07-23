# Modèle de données & persistance

_Rôle : décrire exhaustivement le schéma SQLAlchemy des tâches/créneaux de Kairos,
la base SQLite dédiée qui les porte, ses migrations additives et le jeu de données
d'exemple posé au premier lancement. Fichiers couverts : `app/tasks_models.py`,
`app/tasks_db.py`, `app/tasks_seed.py`._

## 1. Besoin métier (cahier des charges)

### Objectif / problème

Kairos est un dashboard personnel de tâches, mono-utilisateur, tournant aussi bien
en serveur web local (uvicorn) qu'en exécutable de bureau (PyInstaller) ou en APK
Android (Chaquopy + WebView). Il lui faut un stockage :

- **local et sans dépendance externe** : SQLite, aucun serveur de base de données,
  aucun compte, aucun cloud ;
- **strictement séparé** de toute autre base que l'utilisateur pourrait faire
  cohabiter sur le même poste (historiquement `pilotage.db`, l'outil de suivi de
  dette technique Redmine/GitLab dont Kairos a été extrait en phase 14) — deux
  fichiers SQLite distincts, aucun import croisé entre les deux jeux de modèles,
  aucune contrainte de clé étrangère inter-bases ;
- **capable d'évoluer dans le temps sans jamais perdre les données réelles de
  l'utilisateur** : dix-huit phases de développement ont chacune ajouté des champs
  (time blocking, hiérarchie, récurrence, GitLab, Fibonacci/typologie, liaison
  fiche…) sur une base qui, dès la phase 1, contient déjà des tâches importées et
  des priorités posées à la main.

### Comportement attendu (utilisateur)

- Au tout premier lancement (base vierge), l'utilisateur ne voit pas une page
  vide : un jeu de tâches et créneaux d'exemple est posé automatiquement, pour
  découvrir les fonctionnalités par la pratique (score WSJF, placement autour
  d'une réunion, deep-work, épinglage, sous-tâches, dépendances, inbox « À
  traiter », programmation différée).
- Ces exemples sont des objets ordinaires (tag de projet « Exemple », titre
  préfixé `[Exemple]`) : supprimables/éditables comme n'importe quelle tâche,
  jamais un mode spécial à désactiver.
- Une mise à jour de l'application qui ajoute un nouveau champ ne doit **jamais**
  demander à l'utilisateur de recréer sa base ni faire disparaître une tâche ou
  une priorité déjà posée.
- Le fichier de base est rangé dans le dossier de données standard de l'OS (pas
  le répertoire de lancement, non fiable pour un exécutable packagé), avec une
  option de dossier surchargé pour les cas portables (Android).

### Critères de succès

- Une base créée à la phase 1 (schéma minimal `Task`/`TimeBlock`) démarre sans
  erreur avec le code actuel : toutes les colonnes ajoutées depuis sont créées à
  la volée, les données préexistantes (titre, priorité, statut, `external_id`…)
  restent identiques bit à bit.
- `TasksBase.metadata.tables` et les tables de la base pilotage
  (`PilotageBase.metadata.tables`) sont strictement disjointes (`isdisjoint`).
- Deux tâches natives peuvent coexister sans violer la contrainte unique
  `(source, external_id)` (elles ont toutes deux `external_id=None`, distinct de
  `""`).
- `init_tasks_db()` sur une base vierge crée les tables **et** sème les exemples ;
  ré-appelé ensuite (table déjà présente), il ne sème jamais une deuxième fois —
  y compris si l'utilisateur a supprimé tous les exemples entre-temps.
- Le seeding ne fait jamais échouer le démarrage de l'application, même s'il lève
  une exception.

### Hors périmètre / différé

- Toute logique de tri, de placement dans la journée ou de récurrence : ce
  document couvre uniquement la forme des données et leur persistance (voir les
  specs `ordonnancement`, `dependances`, `recurrence`).
- La base `pilotage.db` elle-même et ses modèles (`Ticket`, `GitLabIssueCache`) :
  Kairos n'en lit que des **projections en lecture seule** via le seam
  `app/pilotage_link.py` (hors périmètre de ce document, mentionné seulement pour
  `linked_ticket_id`).
- Toute contrainte de clé étrangère réelle entre tables du schéma tâches
  (`parent_id`, `blocker_id`, `linked_ticket_id`) : le schéma reste volontairement
  sans `ForeignKey` déclarée, cohérent d'un bout à l'autre.

## 2. Solution technique

### Vue d'ensemble

Deux fichiers forment le socle de persistance :

- `app/tasks_models.py` définit `TasksBase` (une `DeclarativeBase` SQLAlchemy 2
  dédiée) et les six tables : `Task`, `TimeBlock`, `TaskDependency`,
  `WorkSession`, `TaskSyncMeta`, `Note`, plus la constante `FIBONACCI_SCALE`.
- `app/tasks_db.py` crée le moteur (`tasks_engine`) et la fabrique de sessions
  (`TasksSessionLocal`) pointant sur `Settings.tasks_database_url`
  (`sqlite:///{tasks_database_path}`), gère la migration additive légère
  (`_ensure_tasks_columns`) et déclenche le seeding de première utilisation.

`app/tasks_seed.py` est un module quasi pur (aucun commit, seulement
`add`/`flush`) invoqué uniquement par `tasks_db.py` sur base vierge.

Le mécanisme de migration ne suit aucun outil de migration de schéma externe
(pas d'Alembic) : un dictionnaire `_TASKS_MIGRATION_COLUMNS` énumère, table par
table, les colonnes ajoutées après la création initiale, avec leur DDL SQL brut ;
`_ensure_tasks_columns()` les ajoute une à une par `ALTER TABLE ... ADD COLUMN`
si absentes. C'est le même mécanisme que celui de la base pilotage (`app/db.py`
/ `_MIGRATION_COLUMNS`), répliqué à l'identique (« mirror strict », dixit le
docstring de `tasks_db.py`).

### Détail par composant

#### `app/tasks_models.py`

**`TasksBase`** — `DeclarativeBase` dédiée, séparée de `app.models.Base`
(pilotage). Aucun import croisé entre les deux jeux de modèles.

**`_now()`** — petite fonction utilitaire, `datetime.now(timezone.utc)`, utilisée
comme valeur par défaut (`default=_now`) sur tous les champs d'horodatage
(`created_at`, `updated_at`, `started_at`). Les horodatages sont donc stockés en
UTC, contrairement aux dates/heures « métier » (`deadline`, `pinned_start`,
`TimeBlock.start`/`end`) qui restent des `datetime`/`date` **naïfs**, en heure
locale — convention partagée avec l'ordonnancement (`datetime.combine`).

**`Task`** — table `task`. Représente une tâche affichée dans Kairos, native ou
importée (GitLab assigné). Champs, dans l'ordre du modèle :

- `id: int` — clé primaire.
- `title: str` (`String(512)`, défaut `""`).
- `description: str` (`Text`, défaut `""`).
- `priority: int | None` (`Integer`, nullable) — champ **natif** de l'outil, posé
  depuis le dashboard, jamais réécrit par une synchronisation externe une fois la
  tâche déjà existante. `None` signifie « priorité non renseignée », distinct de
  la priorité la plus basse (échelle basse = plus prioritaire, mêmes conventions
  que `Ticket.priority_override` côté pilotage). Une tâche fraîchement importée
  (GitLab) arrive donc toujours avec `priority=None`.
- `deadline: date | None` (`Date`, nullable) — échéance réelle, imposée de
  l'extérieur ; garde-fou qui ne laisse jamais rien filer (voir § phase 4 dans
  `recurrence.md` et la spec `ordonnancement`).
- `project_tag: str` (`String(255)`, défaut `""`) — tag libre (texte simple), pas
  de `ForeignKey` vers `Ticket` : décision actée dès le MVP (« projet = tag libre,
  la liaison réelle vers Redmine/GitLab est hors périmètre »), reprise plus tard
  sous forme de liaison **manuelle** (`linked_ticket_id`) sans jamais devenir une
  vraie FK.
- `status: str` (`String(16)`, défaut `"todo"`, **indexé**) — `'todo'` | `'done'`
  | `'archived'`. `'archived'` signifie que la tâche a disparu côté source
  externe (issue GitLab fermée/désassignée) ; elle n'est **jamais supprimée**
  pour préserver l'historique de priorisation posé dessus (invariant de
  non-perte).
- `source: str` (`String(32)`, défaut `"native"`, **indexé**) — `'native'` |
  `'gitlab'`. La valeur historique `'superproductivity'` a existé (intégration
  retirée en phase 8) ; une migration de données (voir plus bas) convertit
  automatiquement toute tâche encore sur cette valeur vers `'native'`.
- `external_id: str | None` (`String(64)`, nullable, défaut `None`) — identifiant
  côté source externe. **`None` et non `""`** pour une tâche native : les valeurs
  `NULL` sont distinctes entre elles au sens de la contrainte `UNIQUE` en SQLite,
  ce qui permet à plusieurs tâches natives de coexister sans collision sur
  `(source, external_id)`. C'est un correctif de données tracé explicitement
  (voir migration ci-dessous) : la phase 1 utilisait `""`, ce qui bloquait dès la
  deuxième tâche native créée.
- `estimated_minutes: int | None` (`Integer`, nullable) — durée estimée en
  minutes. `None` = non renseignée, repli sur le réglage
  `default_task_duration_minutes` **au moment de l'ordonnancement seulement**
  (jamais persisté sur la ligne).
- `pinned_start: datetime | None` (`DateTime`, nullable) — épinglage : la tâche
  est posée exactement à cette heure dans la journée, l'ordonnancement
  automatique remplit autour. `None` = placement automatique.
- `parent_id: int | None` (`Integer`, nullable, **indexé**) — id local de la
  tâche mère, **auto-référence sans contrainte `ForeignKey`** déclarée : choix de
  style délibéré, cohérent avec le reste du schéma (`TaskDependency.blocker_id`,
  `linked_ticket_id` suivent le même parti pris de référence « molle »). `None` =
  tâche de premier niveau. Seules les tâches **feuilles** (sans sous-tâche
  ouverte) sont planifiées ; la mère affiche un avancement n/m (logique portée
  par la spec `ordonnancement`, pas par ce fichier).
- `recurrence: str` (`String(16)`, défaut `""`) — `''` | `'daily'` | `'weekdays'`
  | `'weekly'` | `'monthly'` | `'monthly_on_day'`. Les cinq premières valeurs
  (hors `''`) se recréent **à la complétion** (`tasks_recurrence.
  spawn_next_occurrence`) ; `'monthly_on_day'` est **calendaire**, générée par
  date indépendamment de la complétion (`tasks_recurrence.
  ensure_calendar_occurrences`) — voir `recurrence.md` pour le détail complet des
  deux mécanismes.
- `scheduled_date: date | None` (`Date`, nullable) — quand l'utilisateur compte
  s'y mettre, **distinct** de `deadline`. Les deux champs coexistent sans jamais
  se remplacer : `deadline` reste le garde-fou d'échéance, `scheduled_date`
  pilote la présence dans l'agenda du jour (règle d'éligibilité détaillée dans la
  spec `ordonnancement`).
- `recurrence_day_of_month: int | None` (`Integer`, nullable) — jour du mois
  (1-31, borné en fin de mois courte) pour `recurrence='monthly_on_day'`.
- `recurrence_period: str` (`String(16)`, défaut `""`) — période de l'occurrence
  calendaire générée (ex. `"2026-07"`), utilisée par
  `ensure_calendar_occurrences` comme clé anti-doublon. `""` pour toute tâche non
  calendaire.
- `task_type: str` (`String(32)`, défaut `""`) — `""` (non classé) ou une valeur
  de `Settings.task_type_list` (liste **configurable** depuis la page Réglages,
  où la valeur stockée EST directement le libellé affiché — pas une clé interne
  séparée, voir la migration de remappage ci-dessous). Purement informatif :
  aucun impact sur l'ordonnancement, sert à catégoriser en vue d'analyses futures
  (dashboard de stats, calibration par type). Une tâche dont le type a été retiré
  de la liste configurée **garde sa valeur enregistrée** (jamais de perte
  silencieuse), elle n'apparaît juste plus dans le menu déroulant d'édition.
- `fibonacci_points: int | None` (`Integer`, nullable) — estimation agile en
  points de Fibonacci, échelle fixe `FIBONACCI_SCALE`. **Pas de conversion**
  vers/depuis `estimated_minutes` : `estimated_minutes` reste seul à piloter
  l'ordonnancement du temps ; `fibonacci_points` entre en revanche comme
  dénominateur du score WSJF (voir spec `ordonnancement`, phase 9 — décision
  distincte de la position purement informative tenue en phase 5).
- `manual_time_spent_minutes: int | None` (`Integer`, nullable) — temps passé
  saisi à la main, pour les cas où le chrono a été oublié. **Additionné** (jamais
  substitué) au temps mesuré par les `WorkSession` — voir
  `app/tasks_time.py::spent_minutes_by_task` (hors périmètre de ce document, cité
  pour la traçabilité du champ).
- `linked_ticket_id: int | None` (`Integer`, nullable) — référence locale vers
  `Ticket.id` (base `pilotage.db`), **sans contrainte FK cross-base** (les deux
  bases SQLite restent des fichiers séparés — cohérent avec `parent_id`). Lecture
  seule : aucune écriture vers Redmine/GitLab n'est jamais déclenchée depuis ce
  champ, aucun impact sur l'ordonnancement.
- `created_at: datetime` (`DateTime`, défaut `_now`).
- `updated_at: datetime` (`DateTime`, défaut `_now`, `onupdate=_now`) — sert
  aussi, par convention documentée ailleurs dans le projet (dashboard de stats),
  d'approximation de la date de complétion (pas d'horodatage `completed_at`
  dédié).

`__table_args__` : `UniqueConstraint("source", "external_id",
name="uq_task_source_external_id")` — empêche la double-importation d'une même
fiche externe ; contournée pour les tâches natives grâce à la convention
`external_id=None` documentée plus haut.

**`FIBONACCI_SCALE: tuple[int, ...] = (1, 2, 3, 5, 8, 13, 21)`** — suite de
Fibonacci classique, échelle **fixe** (pas de variante avec ½/0/100). Co-localisée
avec `Task` juste après sa définition. Pas de champ libre : un `<select>` côté
formulaire d'édition restreint la saisie à ces sept valeurs.

**`TimeBlock`** — table `time_block`. Créneau de la journée : occupé (réunion /
TimeTree) ou bloc deep-work protégé.

- `id: int` — clé primaire.
- `title: str` (`String(512)`, défaut `""`).
- `start: datetime` (`DateTime`, **indexé**) — naïf, heure locale.
- `end: datetime` (`DateTime`) — naïf, heure locale.
- `source: str` (`String(32)`, défaut `"manual"`, **indexé**) — `'manual'` |
  `'timetree'`.
- `kind: str` (`String(16)`, défaut `"busy"`) — `'busy'` (indisponible : réunion
  saisie à la main ou événement TimeTree, l'ordonnancement le contourne) |
  `'deepwork'` (fenêtre **réservée** au travail profond : disponible mais
  **exclusive**, l'ordonnancement y place une seule tâche non fragmentée, les
  autres tâches auto la contournent).
- `external_id: str` (`String(64)`, défaut `""`) — **note de cohérence** : à la
  différence de `Task.external_id` (nullable, `None` par convention), celui-ci
  reste une chaîne non nullable à défaut `""` — pas de contrainte unique sur
  `TimeBlock` qui l'exposerait au même piège, donc pas besoin de la convention
  `NULL`.
- `recurrence: str` (`String(16)`, défaut `""`) — `''` | `'daily'` | `'weekdays'`
  | `'weekly'` (phase 13, blocs manuels uniquement ; sous-ensemble strict des
  règles de `Task`, pas de `'monthly'`, jugé moins pertinent pour un créneau
  récurrent de type agenda). La ligne stockée est le **modèle** (heure de
  début/fin canonique, premier jour = `start.date()`) ; les occurrences futures
  ne sont **jamais persistées** — projetées à la volée par
  `tasks_recurrence.expand_recurring_blocks` pour la plage affichée (voir
  `recurrence.md`). Contrairement aux tâches, les blocs n'ont pas de statut
  « fait » : le modèle « recréation à la complétion » ne s'applique pas ici.
- `created_at: datetime` (`DateTime`, défaut `_now`).

Pas de `UniqueConstraint` sur `TimeBlock` : deux créneaux identiques peuvent
coexister sans erreur (aucune notion de doublon appliquée côté base).

**`TaskDependency`** — table `task_dependency`. Dépendance « bloqué par » entre
deux tâches natives.

- `id: int` — clé primaire.
- `task_id: int` (`Integer`, **indexé**) — tâche bloquée.
- `blocker_id: int` (`Integer`, **indexé**) — tâche bloquante.
- `created_at: datetime` (`DateTime`, défaut `_now`).

`__table_args__` : `UniqueConstraint("task_id", "blocker_id",
name="uq_task_dependency_edge")` — une même arête ne peut pas être posée deux
fois. Arête dirigée `task_id → blocker_id` : une tâche est « bloquée » tant qu'au
moins un de ses bloqueurs est encore à faire (logique portée par
`app/tasks_dependencies.py`, hors périmètre de ce document). **Auto-référence
logique** vers `task.id`, sans contrainte FK déclarée — même parti pris que
`Task.parent_id`.

**`WorkSession`** — table `work_session`. Session de travail chronométrée sur une
tâche (suivi du temps réel).

- `id: int` — clé primaire.
- `task_id: int` (`Integer`, **indexé**).
- `started_at: datetime` (`DateTime`, défaut `_now`).
- `ended_at: datetime | None` (`DateTime`, nullable) — `NULL` = session **en
  cours** (minuteur qui tourne). Invariant appliqué **au niveau applicatif** (pas
  d'une contrainte SQL) : au plus une session ouverte à la fois — démarrer une
  nouvelle ferme l'éventuelle précédente (logique hors périmètre de ce document,
  dans le code de la route de démarrage du chrono).
- `created_at: datetime` (`DateTime`, défaut `_now`).

Sert à comparer le temps réel passé à l'estimation, et à afficher un minuteur
vivant sur la tâche en cours.

**`TaskSyncMeta`** — table `task_sync_meta`. Méta du dernier fetch réussi par
source (mirror de `GitLabRefreshMeta` côté pilotage).

- `id: int` — clé primaire.
- `source: str` (`String(32)`, défaut `""`).
- `last_synced_at: datetime | None` (`DateTime`, nullable).
- `last_outcome: str` (`String(16)`, défaut `"ok"`) — `'ok'` | `'error'`.
- `last_detail: str` (`Text`, défaut `""`).
- `item_count: int` (`Integer`, défaut `0`).

`__table_args__` : `UniqueConstraint("source", name="uq_task_sync_meta_source")`
— une ligne par source. Sert de base au TTL de rafraîchissement (fetch-on-load,
pas de scheduler en tâche de fond) et à l'affichage d'un avertissement quand la
dernière synchro a échoué. La source historique `'superproductivity'` est
purgée par la migration de retrait de l'intégration (voir plus bas).

**`Note`** — table `note`. Note libre (« brain dump » GTD), capture en amont de
la boîte de réception de la vue Jour — voir `docs/spec/notes-capture.md` pour
le besoin métier et les routes ; ce document ne couvre que la forme des
données.

- `id: int` — clé primaire.
- `body: str` (`Text`, défaut `""`) — corps de texte libre, multi-ligne.
- `status: str` (`String(16)`, défaut `"open"`, **indexé**) — `'open'` |
  `'archived'`. `'archived'` signifie que la note a été convertie en tâche
  (voir `converted_task_id`) ou classée sans suite ; comme pour `Task.status`,
  une note **n'est jamais supprimée** par ce passage à `'archived'` — seule
  la route `DELETE`-like `POST /kairos/notes/{id}/delete` supprime réellement
  la ligne.
- `converted_task_id: int | None` (`Integer`, nullable) — référence locale
  vers `Task.id`, posée uniquement par la conversion note → tâche, **sans
  contrainte `ForeignKey`** déclarée : même parti pris que `Task.parent_id`/
  `TaskDependency.blocker_id`/`Task.linked_ticket_id` (« référence molle »,
  cohérente avec le reste du schéma — voir § Décisions et pièges tracés).
  `None` tant que la note n'a pas été convertie.
- `created_at: datetime` (`DateTime`, défaut `_now`).
- `updated_at: datetime` (`DateTime`, défaut `_now`, `onupdate=_now`).

Pas de `UniqueConstraint` sur `Note` : aucune notion de doublon appliquée côté
base, une capture rapide peut légitimement dupliquer une idée déjà notée.

#### `app/tasks_db.py`

**Engine et session** — `tasks_engine = create_engine(_settings.
tasks_database_url, connect_args={"check_same_thread": False}, future=True)` ;
`TasksSessionLocal = sessionmaker(bind=tasks_engine, autoflush=False,
expire_on_commit=False)`. `_settings = get_settings()` est résolu **une seule
fois au chargement du module** (pas par requête) : cohérent avec le commentaire
de `Settings.tasks_database_path` qui prévient qu'un redémarrage de Kairos est
nécessaire après modification de ce chemin — le moteur de base de données n'est
initialisé qu'une fois par processus.

**`_TASKS_MIGRATION_COLUMNS`** — dictionnaire `{table: {colonne: DDL SQL}}`,
organisé par phase historique en commentaires :

```
"task": {
    # Phase 2 (time blocking, hiérarchie, récurrence).
    "estimated_minutes": "INTEGER",
    "pinned_start": "DATETIME",
    "parent_id": "INTEGER",
    "recurrence": "VARCHAR(16) DEFAULT ''",
    # Phase 4 : date programmée, récurrence calendaire.
    "scheduled_date": "DATE",
    "recurrence_day_of_month": "INTEGER",
    "recurrence_period": "VARCHAR(16) DEFAULT ''",
    # Phase 5 : métadonnées pures de préparation d'analyses futures.
    "task_type": "VARCHAR(32) DEFAULT ''",
    "fibonacci_points": "INTEGER",
    # Phase 6 : liaison manuelle en lecture vers une fiche `Ticket`.
    "linked_ticket_id": "INTEGER",
    # Temps passé saisi à la main, en complément du chrono (issue #6).
    "manual_time_spent_minutes": "INTEGER",
},
"time_block": {
    # Phase 3 : distingue les créneaux occupés des blocs deep-work protégés.
    "kind": "VARCHAR(16) DEFAULT 'busy'",
    # Phase 13 : blocs récurrents (bloc déjeuner quotidien, deep-work hebdo).
    "recurrence": "VARCHAR(16) DEFAULT ''",
},
```

Note : les colonnes présentes **dès la création initiale** de `Task`
(`title`…`external_id`, `created_at`, `updated_at`) et de `TimeBlock`
(`title`…`external_id`, `created_at`) n'apparaissent **pas** dans ce dictionnaire
— seules les colonnes ajoutées *après coup* y figurent, car `create_all()` ne
modifie jamais une table existante (seulement les tables absentes). Une table
**entièrement nouvelle** (ex. `note`, ajoutée avec `Note`) n'a besoin d'aucune
entrée non plus, pour la même raison à l'envers : `create_all()` la crée dans
son intégralité (toutes ses colonnes déjà à jour) sur toute base où elle est
absente, qu'elle soit vierge ou déjà peuplée par d'autres tables.

**`_ensure_tasks_columns()`** — pour chaque table du dictionnaire présente dans
la base (`inspector.get_table_names()`), calcule l'ensemble des colonnes
manquantes (`existing = {col["name"] for col in inspector.get_columns(table)}`)
et exécute un `ALTER TABLE {table} ADD COLUMN {name} {ddl}` par colonne absente,
dans une transaction (`tasks_engine.begin()`). Une table absente de la base
(schéma pas encore assez avancé, cas anormal) est simplement ignorée — pas
d'erreur.

Trois correctifs de données **idempotents** sont appliqués à la suite, dans la
même fonction, chacun conditionné à la présence de la table `task` :

1. **Correctif `external_id=""` → `NULL` sur les natives** (phase 2) :
   `UPDATE task SET external_id = NULL WHERE source = 'native' AND external_id =
   ''`. Trace le bug initial documenté sur `Task.external_id` : la contrainte
   unique `(source, external_id)` bloquait la création d'une deuxième tâche
   native tant que les deux avaient `external_id=""` (chaînes vides égales, donc
   en collision, contrairement aux `NULL`).
2. **Remappage `task_type`** (issue #7) : un dictionnaire
   `_legacy_task_type_labels` (7 entrées, clé interne historique → libellé
   français) réécrit toute valeur de `task_type` encore sur l'ancienne clé
   (`"dev"`, `"revue_code"`, `"reunion"`, `"documentation"`, `"administratif"`,
   `"veille"`, `"pilotage"`) vers son libellé d'origine (`"Développement"`,
   `"Revue de code"`…). Contexte : `task_type` stockait à l'origine une clé fixe
   (`TASK_TYPE_LABELS`, retirée depuis) ; les types sont désormais une liste
   **configurable** (`Settings.task_types`) où la valeur stockée EST le libellé
   affiché — sans ce remappage, la catégorisation déjà posée sur des tâches
   existantes se serait retrouvée invisible (aucune correspondance avec la
   nouvelle liste). Idempotent : plus aucune ligne à mettre à jour après le
   premier passage (les clés legacy ne réapparaissent jamais en écriture
   normale).
3. **Retrait de l'intégration Superproductivity** (phase 8, réseau pro
   incompatible) : `UPDATE task SET source = 'native' WHERE source =
   'superproductivity'` — toute tâche encore marquée comme synchronisée depuis
   Superproductivity devient native **une fois pour toutes**, sans jamais être
   recréée ni dupliquée (plus aucune synchro ne peut la recréer). `external_id`
   est **conservé** comme trace d'origine — même geste que l'ancien bouton
   « Adopter les tâches SP », désormais automatique et inconditionnel. En
   complément, si la table `task_sync_meta` existe : `DELETE FROM
   task_sync_meta WHERE source = 'superproductivity'` (purge de la méta
   devenue obsolète).

**`init_tasks_db()`** — point d'entrée appelé au démarrage de l'application.
Séquence :

1. `fresh = not set(inspect(tasks_engine).get_table_names())` — détection
   « base vierge » = **absence de toute table**, jamais basée sur le contenu.
   Conséquence assumée et documentée : supprimer tous les exemples après coup ne
   les fait **jamais** réapparaître (la table `task` existe toujours), et une
   base préexistante même partiellement peuplée (en cours de migration) n'est
   **jamais** semée.
2. `TasksBase.metadata.create_all(bind=tasks_engine)` — crée les tables absentes
   (ne touche pas celles qui existent déjà).
3. `_ensure_tasks_columns()` — migration additive + correctifs de données
   ci-dessus.
4. Si `fresh` était vrai : `_seed_example_data_safely()`.

**`_seed_example_data_safely()`** — enveloppe `seed_example_data` dans un
`try/except Exception` large (`pragma: no cover — garde-fou de démarrage`) : un
échec du seeding est journalisé (`logger.exception`) mais **ne fait jamais
échouer le démarrage** de l'application, cohérent avec la philosophie de
dégradation propre du reste du projet (TimeTree, GitLab).

**`tasks_session_scope()`** — context manager transactionnel (`commit`
automatique en sortie normale, `rollback` + relance en cas d'exception, `close`
dans tous les cas). Utilisé pour les opérations hors requête HTTP (ex. le
seeding).

**`get_tasks_session()`** — générateur de session, une par requête, consommé par
`main._request_session`. Les tests substituent la fabrique de session par
monkeypatch (voir `tests/conftest.py::tasks_session`, qui recrée une base SQLite
**en mémoire** avec `TasksBase.metadata.create_all(engine)` directement, sans
passer par `init_tasks_db()` — donc sans migration ni seed, schéma toujours à
jour car généré depuis les modèles courants).

#### `app/tasks_seed.py`

**`EXAMPLE_PROJECT_TAG = "Exemple"`** — tag de projet appliqué à toutes les
tâches d'exemple, ce qui les rend repérables/regroupables sans champ spécial.

**`_at(day, hour, minute=0)`** — construit un `datetime` naïf via
`datetime.combine`, même convention que l'ordonnancement.

**`seed_example_data(session, *, today)`** — fonction quasi pure : elle ne fait
qu'`add`/`flush` sur la session reçue, le `commit` reste à la charge de
l'appelant (`init_tasks_db` via `tasks_session_scope`). Toutes les dates sont
**relatives à `today`** (paramètre explicite, jamais `date.today()` interne) pour
que le jeu d'exemples reste d'actualité quel que soit le jour du premier
lancement — et pour rester testable avec une date figée.

Sept tâches, illustrant chacune une fonctionnalité distincte du modèle et de
l'ordonnancement :

1. **P0 + échéance proche + points** (« Corriger le bug de connexion ») —
   `priority=0`, `deadline=today+2j`, `fibonacci_points=3`,
   `estimated_minutes=45` : démontre le score WSJF en tête de tri.
2. **P1 « ordinaire » qualifiée** (« Préparer la revue de sprint ») —
   `priority=1`, `deadline=today+7j`, `fibonacci_points=5`.
3. **Épinglée à heure fixe** (« Point d'équipe (épinglé à 9h30) ») —
   `pinned_start=_at(today, 9, 30)` : reste à son heure, le reste se cale autour.
4. **Tâche mère + deux sous-tâches** (« Rédiger la documentation » + « — plan » +
   « — rédaction ») — seules les feuilles portent `estimated_minutes`/
   `deadline`/planification ; la mère n'en a pas (avancement n/m affiché côté
   rendu).
5. **Dépendance « bloqué par »** — « Obtenir la validation du client » (bloqueur)
   et « Déployer en production » (bloquée), liées par un `TaskDependency(task_id=
   blocked.id, blocker_id=blocker.id)` : illustre le chemin critique.
6. **Inbox « À traiter »** (« Idée : automatiser le rapport hebdomadaire ») —
   **ni `priority` ni `fibonacci_points`** : seule tâche du jeu d'exemple sans
   aucune des deux, pour démontrer la section GTD (« À traiter » reste en tête de
   page tant qu'elle n'est pas qualifiée — logique portée par `ordonnancement`,
   pas par ce module).
7. **Programmée plus tard** (« Renouveler la certification ») —
   `scheduled_date=today+7j`, sans deadline imminente : masquée de l'agenda du
   jour, visible dans « Programmées plus tard ».

Trois créneaux (`TimeBlock`), ajoutés par un unique `session.add_all([...])` :

- **Réunion 13h-14h** (`kind="busy"`) — illustre le cas emblématique du projet :
  une tâche urgente repoussée à 14h05 (marge après réunion incluse).
- **Deep-work 10h-11h30** (`kind="deepwork"`) — fenêtre réservée, non fragmentée.
- **Déjeuner récurrent quotidien** (12h-13h, `recurrence="daily"`) — modèle
  unique, projeté à la volée (voir `recurrence.md`) ; démontre qu'un bloc
  récurrent d'exemple ne stocke qu'une seule ligne.

`add_task(**kwargs)` (fonction interne à `seed_example_data`) factorise
`source="native"` + `project_tag=EXAMPLE_PROJECT_TAG` + `session.add` +
`session.flush()` — le `flush()` est nécessaire pour obtenir l'`id` attribué
avant de le référencer en `parent_id` (sous-tâches) ou dans un `TaskDependency`.

Une **note** d'exemple (`Note`, préfixée `[Exemple]` comme le reste) est
ajoutée à la suite, hors de `add_task` (elle n'a ni `source` ni `project_tag` —
ces champs n'existent pas sur `Note`) : illustre l'étape de capture GTD en
amont de la boîte de réception, pour qu'un nouvel utilisateur découvre aussi la
page Notes dès le premier lancement.

### Décisions et pièges tracés

- **`external_id` nullable et jamais `""` pour une tâche native.** Piège déjà
  vécu en production (phase 2) : SQLite considère deux `NULL` comme distincts au
  sens d'`UNIQUE`, mais deux `""` comme égaux — donc en collision sur
  `(source, external_id)` dès la deuxième tâche native. Le modèle utilise `NULL`
  par défaut ; la migration purge les anciennes lignes `""`. Toute nouvelle
  création de tâche native doit respecter cette convention (ne jamais poser
  `external_id=""` explicitement).
- **Pas de vraies contraintes `ForeignKey`** sur `parent_id`, `TaskDependency.
  task_id`/`blocker_id`, ni `linked_ticket_id` : choix de style assumé et
  répété à chaque champ concerné (« cohérent avec le reste du schéma »),
  probablement pour rester simple sur SQLite (pas de `PRAGMA foreign_keys`
  activé visible dans ce module) et pour permettre `linked_ticket_id` de
  référencer une base SQLite **différente** sans jamais pouvoir être une FK
  SQL réelle. Conséquence : l'intégrité référentielle de ces champs est
  **entièrement à la charge du code applicatif** (ex. `edit_task` vérifie
  l'existence du ticket avant d'accepter `linked_ticket_id`, hors périmètre de
  ce document).
- **`task_type` : la valeur stockée est le libellé, pas une clé.** Changement de
  modèle de données en cours de vie du produit (issue #7) : à l'origine une clé
  fixe (`TASK_TYPE_LABELS` codé en dur, aujourd'hui retiré du code), aujourd'hui
  une valeur libre parmi `Settings.task_type_list` (configurable). La migration
  remappe les valeurs historiques une fois, en confondant volontairement passé
  et présent dans la même colonne (pas de colonne séparée `task_type_key` vs
  `task_type_label`) : plus simple, au prix de devoir retenir que
  `Settings.task_type_list` doit toujours contenir au moins les libellés déjà
  utilisés en base pour rester sélectionnable — mais une tâche existante garde
  sa valeur même si son type est retiré de la liste (jamais de perte
  silencieuse, seulement une absence du menu déroulant).
- **Détection de base vierge = absence de toute table, jamais le contenu.**
  Volontaire pour deux raisons : (1) l'utilisateur doit pouvoir vider tous les
  exemples sans qu'ils ne réapparaissent au redémarrage suivant ; (2) une base
  en cours de migration (schéma partiellement à jour, ex. table `task` présente
  mais colonnes phase 2 manquantes) ne doit **jamais** être semée par erreur —
  elle a déjà des données réelles.
- **Seeding protégé par un `try/except` large et journalisé.** Le seeding
  touche une session tout juste créée sur une base tout juste créée : en théorie
  peu de raisons d'échouer, mais le projet préfère systématiquement dégrader
  proprement (cohérent avec la gestion TimeTree/GitLab ailleurs) plutôt que de
  bloquer le démarrage pour une fonctionnalité non essentielle (les exemples
  sont un confort de découverte, pas une donnée requise).
- **`_settings` résolu une seule fois au chargement du module `tasks_db.py`.**
  Le moteur SQLAlchemy est construit à l'import, pas par requête : modifier
  `tasks_database_path` depuis la page Réglages ne prend effet qu'après
  redémarrage du processus — documenté explicitement dans le docstring du champ
  `Settings.tasks_database_path`, pas un oubli.
- **Pas d'usage d'Alembic ou d'un outil de migration de schéma dédié.** Le choix
  assumé (« mirror strict » du mécanisme déjà en place pour `pilotage.db`) est un
  dictionnaire de DDL `ALTER TABLE ADD COLUMN` à la main : suffisant tant que les
  migrations restent strictement additives (nouvelle colonne avec une valeur par
  défaut ou nullable). Aucune migration destructive (renommage, suppression de
  colonne, changement de type) n'est prévue par ce mécanisme — en cohérence avec
  l'invariant de non-perte du projet.

### Invariants et garde-fous

- **Non-perte** : aucune migration ne supprime de table ni de colonne ; les
  seules écritures de masse (`UPDATE`) sont des corrections de données
  strictement **idempotentes** et **conservatrices** (elles ne changent que des
  valeurs invalides/obsolètes vers leur équivalent correct, jamais une donnée
  métier valide).
- **Séparation des bases** : `TasksBase.metadata.tables` et
  `PilotageBase.metadata.tables` (`app/pilotage_link.py`) sont disjointes —
  vérifié par `tests/test_tasks_models.py::
  test_tasks_base_is_separate_from_ticket_base`. `init_tasks_db()` ne crée ni ne
  touche jamais le fichier `pilotage.db`.
- **Idempotence de la migration** : ré-exécuter `_ensure_tasks_columns()` sur
  une base déjà migrée est un no-op (colonnes déjà présentes, `UPDATE` sans
  ligne à modifier) — testable en appelant `init_tasks_db()` deux fois de suite.
- **Idempotence du seeding** : `init_tasks_db()` ne sème jamais une deuxième
  fois une base où la table `task` existe déjà, quel que soit son contenu.
- **Un seul redémarrage requis pour un changement de chemin de base** : documenté
  côté réglages, pas de rechargement à chaud du moteur SQLAlchemy.
- **Contrainte unique `(source, external_id)`** appliquée par la base
  elle-même (`IntegrityError` si violée) — dernier filet après la convention
  `external_id=None` pour les natives.
