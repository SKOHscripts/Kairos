# Intégrations externes (GitLab, TimeTree, pilotage)

Rôle : toute intégration de Kairos avec un système extérieur au poste (API GitLab,
API non-officielle TimeTree) ou avec un autre outil local (base `pilotage.db` de
`pilotage-pleiade-gitlab`). Fichiers couverts : `app/gitlab_direct.py`,
`app/tasks_gitlab_sync.py`, `app/pilotage_link.py`, `app/calendar/timetree_source.py`,
`app/git_credentials.py`, et leur branchement dans `app/main.py`
(`_build_kairos_context`, `_fetch_busy_blocks`). Les **champs de réglage** qui
configurent ces intégrations et le **stockage des secrets** (jeton, mot de passe)
sont hors périmètre ici — voir `docs/spec/reglages-secrets.md`. Le **modèle de
données** (`Task`, `TaskSyncMeta`, `LinkedTicket`) est décrit en détail dans
`docs/spec/modele-donnees.md` ; cette spec n'en reprend que ce qui éclaire le
comportement de synchronisation.

## 1. Besoin métier (cahier des charges)

### Objectif / problème

Kairos est un outil « point d'entrée unique » : l'utilisateur ne doit pas avoir à
recouper mentalement plusieurs outils (GitLab, calendrier personnel, outil de
pilotage dette technique) pour savoir quoi faire. Trois besoins concrets :

1. **Voir ses issues GitLab assignées comme des tâches**, sans quitter Kairos ni
   dupliquer une liste à jour ailleurs.
2. **Voir ses créneaux personnels occupés** (TimeTree) à côté des réunions
   professionnelles saisies à la main, pour que l'ordonnancement du jour évite de
   proposer une tâche pendant un rendez-vous personnel.
3. **Relier une tâche Kairos à une fiche de dette technique** suivie par un outil
   séparé (`pilotage-pleiade-gitlab`), pour naviguer directement vers elle, sans
   fusionner les deux bases.

Toutes ces intégrations sont **optionnelles** : Kairos doit rester pleinement
utilisable — y compris pour un collègue qui n'a ni GitLab, ni TimeTree, ni l'outil
de pilotage — avec seulement les tâches et blocs saisis à la main.

### Comportement attendu (utilisateur)

- Rien à configurer : chaque intégration désactivée (réglages vides) est
  **invisible** — aucun bandeau, aucune section vide, aucun message d'erreur.
- Une fois configurée, chaque intégration se rafraîchit **automatiquement** à
  chaque chargement de la page Kairos (pas de bouton « Synchroniser » dédié), avec
  un cache anti-rate-limiting en arrière-plan invisible à l'utilisateur.
- En cas d'échec (réseau, identifiants invalides, service externe indisponible) :
  un **bandeau d'avertissement**, jamais une page cassée. Les données déjà
  importées (tâches, créneaux) restent affichées telles quelles.
- Les issues GitLab assignées apparaissent comme des tâches normales
  (éditables, plaçables dans l'agenda), mais la **priorité et le temps passé posés
  localement ne sont jamais écrasés** par une resynchronisation.
- Une issue fermée ou réassignée à quelqu'un d'autre fait disparaître la tâche
  correspondante de l'agenda actif (archivage), sans jamais supprimer son
  historique.
- Un jeton d'accès GitLab n'a pas besoin d'être ressaisi dans Kairos s'il est déjà
  configuré pour `git` sur le poste (trousseau système, `~/.netrc`).
- Deux façons d'obtenir ses issues GitLab, selon l'équipement du poste : avec
  l'outil de pilotage installé (cache déjà entretenu, zéro appel réseau
  supplémentaire) ou sans lui (appel direct à l'API GitLab, pour un collègue qui
  n'installe pas cet outil).
- La liaison « Fiche liée » (dette technique) n'apparaît que si l'outil de
  pilotage est installé et configuré ; sinon, le champ correspondant du panneau
  d'édition disparaît proprement.

### Critères de succès

- Sans aucun réglage d'intégration renseigné, Kairos fonctionne à l'identique
  (aucune régression, aucun bandeau).
- TimeTree indisponible (identifiants absents) : silencieux. TimeTree configuré
  mais en échec : bandeau avec détail lisible.
- Import GitLab direct en échec : bandeau, tâches déjà importées inchangées.
- Un import GitLab (l'une ou l'autre voie) ne modifie jamais `priority` ni
  `manual_time_spent_minutes` d'une tâche déjà en base.
- Une issue GitLab qui disparaît du flux assigné (fermée, réassignée) passe la
  tâche correspondante en `status="archived"`, jamais en suppression physique.
- Deux issues de projets différents partageant le même `iid` GitLab ne
  provoquent jamais de collision (deux tâches distinctes).
- Aucune écriture n'est jamais effectuée vers GitLab, TimeTree ou la base
  `pilotage.db` : les trois intégrations sont strictement lecture seule.
- Aucun appel réseau ou accès disque externe ne peut faire remonter une page en
  erreur 500 : toute défaillance externe se traduit par un résultat `ok=False`
  typé, jamais une exception qui traverse jusqu'à la route.

### Hors périmètre / différé

- **Superproductivity** : intégration ayant existé (lecture seule, appel direct à
  l'API locale `http://127.0.0.1:3876`, synchronisation transitoire en attendant
  un CRUD natif complet), **retirée entièrement** une fois ce CRUD natif livré —
  voir § Décisions et pièges tracés. Pour mémoire seulement : aucun code, réglage
  ni route ne subsiste.
- **Google Calendar** (OAuth 2.0/PKCE) : exploré comme alternative/complément à
  TimeTree, implémenté puis **écarté avant fusion** — configuration préalable
  côté Google Cloud Console (projet, client OAuth « Application de bureau »,
  écran de consentement) jugée trop lourde pour l'usage personnel visé. À
  reconsidérer seulement si Google propose un mécanisme d'authentification aussi
  simple qu'un couple identifiant/mot de passe.
- **Écriture vers GitLab** depuis Kairos (commentaire, changement de statut...) :
  jamais envisagée, les deux clients GitLab sont volontairement réduits à la
  lecture.
- **Auto-import depuis Pléiade** (l'outil de suivi dette technique) : `Ticket`
  n'a pas de notion d'assigné unique (suivi collaboratif à plusieurs
  relecteurs) — seule la liaison manuelle est possible.
- **Fusion des bases** `tasks.db` et `pilotage.db` : restent deux fichiers
  SQLite séparés, sans contrainte de clé étrangère cross-base ; `linked_ticket_id`
  est une référence non contrainte, validée applicativement.
- **Vérification de cohérence de la fiche liée** : si un `Ticket` référencé par
  `linked_ticket_id` est clôturé côté Redmine/GitLab pendant que la tâche Kairos
  reste `todo`, rien ne le signale — identifié dans l'analyse de juillet 2026
  (post-phase-6) comme automatisme possible, **jamais implémenté**. Aucun code
  ni route ne calcule ou n'affiche cet écart aujourd'hui ; à reprendre en
  interrogeant `pilotage_link` pour le statut courant du ticket au moment du
  rendu de `task_meta`, si le besoin redevient concret.
- **Granularité fine des événements TimeTree « sur une période »** (ex. horaires
  réels de départ/retour d'un déplacement) : traités comme simple indication
  datée, sans découpage du premier/dernier jour — décision volontairement simple
  (voir § Détail par intégration, TimeTree).
- **Scheduler/tâche de fond** pour rafraîchir ces sources : tout est
  fetch-on-load (à la demande, au chargement de `/kairos`), avec cache TTL en
  mémoire — pas de tâche périodique séparée.

## 2. Solution technique

### Vue d'ensemble (principe commun)

Les cinq modules suivent le même patron, qu'ils partagent par convention (pas de
classe de base commune — trop peu de code partagé pour le justifier) :

- **Optionnel** : une propriété `Settings.xxx_configured` (`timetree_configured`,
  `pilotage_configured`, `gitlab_direct_configured`) conditionne l'activation.
  Réglages vides = fonctionnalité absente de l'interface, jamais une erreur.
- **Lecture seule** : aucun de ces modules n'émet de requête HTTP en écriture
  (`POST`/`PATCH`/`DELETE`) ni n'écrit dans une base qui ne lui appartient pas
  (`pilotage.db` n'est jamais migrée ni modifiée par Kairos).
- **Dégradation propre, jamais d'exception qui remonte** : chaque point d'entrée
  externe (`fetch_busy_slots`, `fetch_assigned_issues`) retourne un objet résultat
  typé avec un champ `ok: bool` et `detail: str`, jamais une levée d'exception
  côté appelant. Les erreurs réseau/HTTP sont interceptées à la source.
- **Cache TTL en mémoire, par processus** : un simple `dict[tuple, tuple[datetime,
  Résultat]]` au niveau du module (pas de table SQLite dédiée), clé = paramètres
  de la requête, valeur = `(horodatage, résultat)`. Relit le cache si l'entrée a
  moins de `settings.xxx_cache_ttl_minutes * 60` secondes ; sinon rappelle la
  source et réécrit l'entrée. Patron identique entre `timetree_source.py`
  (précédent historiquement, phase 1/12) et `gitlab_direct.py` (repris à
  l'identique en phase 17) — anti-rate-limiting, évite un appel réseau à chaque
  chargement de page.
- **Seam stable** : chaque intégration réseau expose une fonction d'entrée unique
  (`fetch_busy_slots`, `fetch_assigned_issues`) que le reste de l'app consomme
  sans connaître les détails du client sous-jacent — remplaçable sans toucher
  `app/main.py` ni les templates.
- **Support du proxy sortant** : les trois réglages réseau (`http_proxy`,
  `https_proxy`, `no_proxy`, détaillés dans `docs/spec/reglages-secrets.md`) sont
  injectés dans l'environnement du processus par `Settings.apply_proxy_env`
  (appelé au démarrage et après chaque sauvegarde des réglages, `app/main.py`).
  `httpx` (client GitLab direct) et le paquet `timetree-exporter` lisent ces
  variables d'environnement standard — aucun code de proxy explicite dans
  `gitlab_direct.py` ni `timetree_source.py`, la prise en charge est indirecte et
  entièrement déléguée aux bibliothèques HTTP sous-jacentes.

### Détail par intégration

#### GitLab direct (`app/gitlab_direct.py`)

Client REST minimal, utilisé **seulement quand `pilotage_database_path` est
vide** (`Settings.gitlab_direct_configured` : pas de pilotage configuré, ET
`gitlab_url`/jeton effectif/`gitlab_project_list`/`gitlab_assignee_username`
tous renseignés) — cas d'un collègue qui n'installe pas l'outil de pilotage.

- `GitLabIssue` (dataclass) : `project`, `iid`, `title`, `state`, `assignees`,
  `due_date`, propriété `assignee_list` — même forme que `CachedGitLabIssue`
  (voir § pilotage), pour que `tasks_gitlab_sync` les consomme indifféremment
  via le `Protocol` structurel `GitLabIssueLike`.
- `GitLabClient.list_open_issues_assigned_to(project, username)` : `GET
  /api/v4/projects/{project}/issues` (projet URL-encodé), filtres
  `assignee_username`, `state=opened`, pagination suivie par 100
  (`per_page=100`, boucle tant que la page reçue est pleine). En-tête
  `PRIVATE-TOKEN` — même patron d'authentification que le client GitLab complet
  de `pilotage-pleiade-gitlab`, sans sa partie écriture/GraphQL. Toute
  `httpx.HTTPError` est enveloppée en `GitLabClientError`.
- `fetch_assigned_issues(settings, *, client=None)` : point d'entrée unique.
  Retourne immédiatement `GitLabFetchResult(ok=False, detail="Import direct
  GitLab non configuré.")` si `gitlab_direct_configured` est faux. Sinon, cache
  TTL (`gitlab_cache_ttl_minutes`, défaut 5 min) à la clé `(gitlab_url,
  gitlab_projects, gitlab_assignee_username)` ; au-delà du TTL, interroge tous
  les projets de `gitlab_project_list` et agrège leurs issues. Toute
  `GitLabClientError` devient `GitLabFetchResult(ok=False, detail="Échec de
  l'appel à l'API GitLab : ...")` — ne lève jamais.
- Paramètre `client: httpx.Client | None` injectable de bout en bout (jusqu'à
  `GitLabClient.__init__`), pour les tests (mock `respx`) sans appel réseau réel.

#### Synchronisation (`app/tasks_gitlab_sync.py`)

Upsert **pur** : aucune E/S réseau ni base côté source — reçoit une
`Sequence[GitLabIssueLike]` déjà résolue par l'appelant (`app/main.py`), quelle
que soit son origine (cache pilotage ou import direct). Fonction principale
`sync_assigned_gitlab_tasks(issues, tasks_session, assignee_username)` :

- **No-op si `assignee_username` est vide** (`ok=True, count=0`) : le réglage
  partagé conditionne l'activation, quelle que soit la source choisie.
- Filtre `issues` sur `state == "opened"` et `assignee_username in
  i.assignee_list` — seules les issues **ouvertes et assignées à l'utilisateur
  courant** deviennent/restent des tâches actives.
- `external_id` **qualifié par projet** : `f"{project}#{iid}"`
  (`_qualified_id`), unique entre projets sous
  `UniqueConstraint(source, external_id)` — un `iid` seul n'est unique que par
  projet, deux projets différents peuvent chacun avoir une issue `!42`.
- **Migration en place de l'ancien format** (phase 4→6) : une tâche déjà
  synchronisée sous l'ancien format (`external_id` = `iid` brut, sans `#`) est
  retrouvée via la table `legacy` (clé `(project_tag, external_id_brut)`) et
  **rebaptisée en place** (`local.external_id = qualified`) plutôt que traitée
  comme disparue puis recréée à neuf — préserve la priorité posée et
  l'historique de temps déjà accumulé.
- Pour chaque issue assignée : upsert du titre (`f"#{iid} {title}"`), de la
  `deadline` (parsée depuis `due_date`, ISO ; invalide/vide → `None`), du
  `project_tag`, et force `status="todo"`. **La priorité n'est jamais posée
  ici** — champ natif, uniquement modifiable depuis le dashboard (commentaire
  explicite dans le code : `# Priorité jamais écrasée`). Le temps passé
  (`manual_time_spent_minutes`) n'est pas non plus un champ touché par la
  synchro — jamais réassigné.
- **Archivage des issues disparues** : toute tâche `source="gitlab"` dont
  l'`external_id` (relu à jour, après un éventuel rekey de migration — piège
  déjà rencontré, voir § Décisions et pièges tracés) n'est pas dans
  `seen_external_ids` passe `status="archived"`. Couvre fermeture ET
  réassignement (l'issue disparaît simplement du flux "assignée à moi, ouverte").
- `write_sync_meta(session, *, ok, detail, count)` : une ligne `TaskSyncMeta`
  par `source` (ici toujours `"gitlab"` — la seule source qui écrit
  actuellement cette table, voir § Décisions et pièges tracés), observabilité
  pure (date, dernier statut, dernier détail, nombre d'éléments), ne conditionne
  aucun comportement applicatif. Appelée en succès depuis
  `sync_assigned_gitlab_tasks` elle-même, et en échec directement depuis
  `app/main.py` (voie directe seulement — voir ci-dessous).

Dans `app/main.py::_build_kairos_context`, le choix de source à chaque
chargement de `/kairos` :

```python
if pilotage_session is not None:
    cached_issues = list(pilotage_session.scalars(select(CachedGitLabIssue)))
    sync_assigned_gitlab_tasks(cached_issues, tasks_session, settings.gitlab_assignee_username)
elif settings.gitlab_direct_configured:
    fetch_result = fetch_assigned_issues(settings)
    if fetch_result.ok:
        sync_assigned_gitlab_tasks(fetch_result.issues, tasks_session, settings.gitlab_assignee_username)
    else:
        write_sync_meta(tasks_session, ok=False, detail=fetch_result.detail, count=0)
        gitlab_direct_error = fetch_result.detail
```

Le cache pilotage **prime toujours** s'il est configuré (zéro appel réseau,
aucun risque d'échec réseau à gérer — la lecture SQL locale ne peut échouer
que si le fichier est absent, déjà traité en amont par
`pilotage_link._get_engine`, qui rend alors une session `None`). L'import
direct n'est tenté qu'en second recours. `gitlab_direct_error` (chaîne vide si
tout va bien) nourrit le bandeau `_kairos_banners.html`.

#### Pilotage / Fiche liée (`app/pilotage_link.py`)

Seul point de contact optionnel, en lecture seule, avec la base `pilotage.db`
d'un autre outil (`pilotage-pleiade-gitlab`), activé uniquement si
`Settings.pilotage_database_path` est renseigné (`pilotage_configured`).

- `PilotageBase` : métadonnées SQLAlchemy **dédiées**, jamais de `create_all` —
  la base appartient à l'autre outil, Kairos ne la crée ni ne la migre jamais.
  Seules les colonnes lues sont mappées (SQLAlchemy ignore silencieusement le
  reste du schéma réel de `pilotage.db`).
- `CachedGitLabIssue` (table `gitlab_issue_cache`) : projection minimale des
  issues GitLab que l'onglet « Pilotage GitLab » de l'autre outil a déjà mises
  en cache via son propre bouton Rafraîchir. Colonnes lues : `project`, `iid`,
  `title`, `state`, `assignees` (CSV, `assignee_list` en dérive la liste),
  `due_date`. **Zéro appel réseau côté Kairos** : c'est une lecture SQL locale
  d'un cache entretenu par un processus tiers.
- `LinkedTicket` (table `ticket`) : projection minimale des fiches de dette
  technique (`pleiade_id`, `pleiade_subject`, `gitlab_web_url`), consommée pour
  peupler le `<select>` « Fiche liée » du panneau d'édition
  (`ticket_choices`, `app/main.py`) et résoudre le badge affiché partout où la
  tâche apparaît (`_kairos_macros.html`, lien cliquable vers `gitlab_web_url`
  si présent).
- `_get_engine()` : engine paresseux (créé une fois, réutilisé), `None` si
  `pilotage_configured` est faux OU si le fichier `pilotage_database_path`
  n'existe pas encore sur disque (dégradation propre : cas normal si l'autre
  outil n'a pas encore tourné une première fois).
- `get_pilotage_session()` : générateur (une session par requête HTTP,
  `_request_session` dans `app/main.py`), rend `None` si `_get_engine()` est
  `None` — substituable en test par monkeypatch de
  `main.get_pilotage_session`.
- `Task.linked_ticket_id` (entier nullable, sans contrainte FK cross-base —
  deux fichiers SQLite distincts) : validé applicativement à l'écriture
  (`app/main.py::edit_task`) contre l'existence réelle du `LinkedTicket` dans
  la session pilotage courante ; une valeur invalide ou une session pilotage
  absente retombe silencieusement à `None`. Référence en lecture seule stricte
  : aucune écriture n'est jamais effectuée vers `pilotage.db` depuis Kairos.

#### TimeTree (`app/calendar/timetree_source.py`)

Seam vers le calendrier personnel de l'utilisateur, seul point d'entrée
consommé par le reste de l'app : `fetch_busy_slots(start, end, *, settings)`.

- Le paquet sous-jacent, `timetree-exporter` (PyPI), est **non-officiel,
  reverse-engineeré** : son mainteneur prévient explicitement d'un risque de
  panne sans préavis et de rate-limiting en cas d'appels trop fréquents — d'où
  l'isolation stricte derrière cette interface stable et le cache TTL (mêmes
  motivations que documentées dès la phase 1 de l'historique du projet).
- **Appel en-process** (import direct de l'API Python interne du paquet
  — `timetree_exporter.api.auth.login`, `api.calendar.TimeTreeCalendar`,
  `calendar.Calendar`, `event.TimeTreeEvent`), **pas de `subprocess`** vers un
  CLI. Décision qui a fait évoluer le mode d'invocation depuis le choix initial
  (CLI en subprocess produisant un `.ics` parsé via `icalendar`, acté en phase 1) :
  nécessaire pour fonctionner packagé (PyInstaller), où il n'existe plus de
  binaire `timetree-exporter` installé à côté de l'interpréteur.
- **Contrat de non-levée** : `fetch_busy_slots` ne lève jamais. Non configuré
  (`timetree_configured` faux — email ou mot de passe absent) →
  `TimeTreeFetchResult(ok=False, detail="TimeTree non configuré (identifiants
  absents).")`, sans appel réseau. Sinon, `_fetch_from_timetree` capture
  **toute** `Exception` (pas seulement les exceptions documentées du paquet :
  `TimeTreeCalendar.get_events` ne lève pas systématiquement sur une réponse
  HTTP en échec — elle logue puis tente `response.json()["events"]`, qui peut
  lever `KeyError`/`ValueError` sur une réponse malformée). Capture large
  délibérée : cette frontière avec un paquet tiers non versionné strictement ne
  doit jamais faire remonter une page en erreur 500.
- Calendrier introuvable (code d'alias `timetree_calendar_code` ne correspond à
  aucun calendrier actif — `deactivated_at is None` filtré) → `ok=False,
  detail="Calendrier TimeTree introuvable (code invalide ou calendrier
  désactivé)."`.
- **Cache TTL en mémoire**, clé `(timetree_email, timetree_calendar_code,
  start, end)`, durée `timetree_cache_ttl_minutes` (défaut 30 min — plus long
  que le cache GitLab, cohérent avec un calendrier personnel qui change moins
  souvent qu'un flux d'issues).
- `BusySlot(title, start, end, all_day)` : `all_day=True` distingue un
  événement « journée entière », qui n'occupe **pas** d'heures — ne doit ni
  bloquer l'ordonnancement ni remplir la timeline, seulement apparaître en puce
  sur le jour (`covers(day)`, DTEND exclusif au sens iCal : un DTEND absent ou
  égal au DTSTART couvre au moins le jour de début).
- **Distinction journée entière vs événement sur une période** (issue
  historique : un événement multi-jours à horaires réels, ex. un déplacement,
  s'affichait à tort comme un obstacle occupant la journée entière sur *chaque*
  jour couvert, empêchant toute planification ces jours-là). Décision : les
  deux catégories (journée entière **et** « sur une période », caractérisée
  par `start.date() != end.date()` indépendamment du flag `all_day`) sont
  traitées **de la même façon** — simple indication (puce datée via
  `BusySlot.covers`), jamais un obstacle horaire dans `build_timeline`/
  `build_day_schedule`. Aucun découpage fin du premier/dernier jour d'un
  événement sur une période (horaires réels de départ/retour) : portée
  volontairement simple, à réviser seulement si un besoin de granularité
  apparaît à l'usage.
- Filtre silencieux (réplique de l'ancien export iCal) : anniversaires
  (`TimeTreeEventType.BIRTHDAY`) et mémos (`TimeTreeEventCategory.MEMO`) ne
  sont jamais des créneaux occupés, `_to_busy_slot` retourne `None` pour eux.
- `_event_datetime` : convertit un timestamp TimeTree (millisecondes epoch) en
  heure murale naïve dans son propre fuseau (`ZoneInfo(tz_name or "UTC")`),
  même repli que l'ancien parsing iCal d'un DTSTART/DTEND qualifié TZID. Pour
  un événement journée entière, `start`/`end` sont recombinés à minuit avec
  DTEND **exclusif** (+1 jour) — même convention RFC 5545 qu'appliquait
  l'ancien formatter iCal du paquet, reproduite manuellement puisque ce
  formatter n'est plus traversé par l'appel en-process.
- Filtrage par plage demandée : un événement dont la fin est avant
  `range_start` ou le début après `range_end` est ignoré (`_to_busy_slot`
  retourne `None`).

Consommé par `app/main.py::_fetch_busy_blocks` (fusion best-effort avec les
blocs manuels de la base) puis `_build_kairos_context`, qui range les blocs
horaires réels dans la timeline et les indications (journée entière + période)
à part (`indication_slots`), sans jamais les faire concourir avec
l'ordonnancement.

#### Résolution du jeton GitLab (`app/git_credentials.py`)

Résout un jeton d'accès GitLab sans obliger à le dupliquer en clair dans les
réglages Kairos, en réutilisant les moyens d'authentification déjà configurés
pour `git` sur le poste. Deux sources, dans l'ordre — la première qui répond
gagne :

1. **`git credential fill`** (protocole standard `git`) : délègue à
   `credential.helper`, quel qu'il soit (trousseau GNOME/libsecret, Keychain
   macOS, Windows Credential Manager, `cache`, `store`...) — rien de
   spécifique à coder par backend, c'est `git` qui choisit. Requête envoyée
   via stdin (`protocol=<scheme>\nhost=<host>\n\n`), réponse parsée pour la
   ligne `password=...`.
   - `env={**external_process_env(), "GIT_TERMINAL_PROMPT": "0"}` : jamais
     interactif — coupe le repli sur un prompt terminal qui bloquerait sinon
     la requête HTTP en cours. `external_process_env()`
     (`app/subprocess_env.py`) : nécessaire car `git` délègue au helper via un
     `sh -c` interne, qui hériterait sinon du `LD_LIBRARY_PATH` détourné par
     PyInstaller vers ses propres bibliothèques embarquées (mode onefile) —
     casserait le helper système.
   - Timeout court (5 s) : évite de bloquer une requête HTTP si un helper
     traîne. Toute `OSError`/`TimeoutExpired` → `None`, jamais d'exception qui
     remonte.
2. **`~/.netrc`** (ou `$NETRC`), lu directement via le module standard
   `netrc` : `git` ne le consulte pas nativement sans helper dédié, mais
   beaucoup d'outils l'utilisent comme entrepôt simple. `FileNotFoundError`,
   `netrc.NetrcParseError`, `OSError` → `None`.
- `resolve_gitlab_token(url)` : orchestre les deux sources dans l'ordre,
  extrait le host via `urlparse(url).hostname` (chaîne vide si absent),
  résultat `""` si aucune des deux sources ne répond — cohérent avec
  `Settings.gitlab_token` non renseigné (même valeur « absence »).
  **`@lru_cache(maxsize=8)`** : mis en cache pour la durée du **processus**
  (un jeton ne change pas en cours de route) — une rotation de jeton
  nécessite un redémarrage du service, comme pour tout changement de `.env`.
- `Settings.gitlab_token_effective` (`app/config.py`) : `gitlab_token`
  (réglages, prioritaire s'il est renseigné) sinon `resolve_gitlab_token
  (gitlab_url)` — un jeton explicite dans les réglages n'est jamais
  court-circuité par la résolution automatique.

### Décisions et pièges tracés

- **Retrait complet de Superproductivity (historique).** Une intégration
  Superproductivity a existé : synchronisation lecture seule, appel direct à
  l'API locale de l'app desktop (`http://127.0.0.1:3876`, `httpx`
  synchrone), justifiée tant que le gestionnaire de tâches natif n'avait pas
  de CRUD complet. Retirée **entièrement** (pas seulement désactivée par
  réglage) une fois ce CRUD livré et l'intégration constatée injoignable
  depuis le réseau professionnel de l'utilisateur (fonctionnait seulement sur
  son réseau personnel, où le développement avait eu lieu). Suppression
  complète du code (`app/clients/superproductivity.py`, `app/tasks_sync.py`,
  tests dédiés), des réglages (`superproductivity_base_url`,
  `superproductivity_sync_enabled`) et de toute référence résiduelle. Aucune
  tâche perdue : une tâche `source="superproductivity"` existante a été
  migrée automatiquement (une fois) vers `source="native"`,
  `external_id` conservé comme trace d'origine — plus aucune synchro ne
  pouvant recréer/dupliquer ces tâches. Mentionné ici pour mémoire ; aucun
  code ni réglage n'en subsiste, ce module n'est **pas** l'un des cinq
  couverts par cette spec.
- **Deux voies GitLab mutuellement exclusives**, choisies par
  `app/main.py` selon disponibilité (jamais combinées) : le cache pilotage
  prime toujours s'il est configuré, l'import direct n'intervient qu'en son
  absence. Motif : le cache pilotage est la seule voie donnant accès à la
  liaison « Fiche liée » (propre à `pilotage.db`, non duplicable), et évite
  tout appel réseau ; l'import direct comble le manque pour un collègue sans
  cet outil (aucune valeur de la version historique mono-cache).
- **`GitLabIssueLike` (`Protocol` structurel)** : `CachedGitLabIssue`
  (pilotage) et `GitLabIssue` (import direct) s'y conforment toutes les deux
  sans se connaître ni partager de classe commune — permet à
  `sync_assigned_gitlab_tasks` de rester pure et testable avec de simples
  objets/dataclasses, sans dépendance à une session SQLAlchemy particulière.
- **Rekey en place plutôt que archive-et-recrée**, à deux endroits distincts
  du cycle de vie d'une tâche `source="gitlab"` : (1) migration de l'ancien
  format `external_id` (`iid` brut) vers le format qualifié par projet
  (`projet#iid`) ; (2) plus généralement, toute tâche déjà présente sous un
  `external_id` reconnu n'est jamais recréée. Dans les deux cas, priorité et
  historique de temps déjà accumulés sont préservés — recréer une tâche neuve
  aurait perdu ces données locales.
- **Piège corrigé (déjà rencontré, à ne pas réintroduire)** : itérer sur les
  **objets** `Task` (`gitlab_tasks`) pour la passe d'archivage, pas sur les
  clés du dict `existing` — un dict indexé par l'ancien `external_id`
  garderait une entrée périmée pour une tâche venant d'être rebaptisée en
  place lors du rekey, et la réarchiverait à tort juste après l'avoir marquée
  `todo`. Le code relit `task.external_id` à jour (post-rekey) pour chaque
  tâche avant de comparer à `seen_external_ids`.
- **`TaskSyncMeta` : une seule source l'alimente en pratique.** Le docstring
  de la table (`app/tasks_models.py`) documente « une ligne par source
  ('gitlab' | 'timetree') », mais seule `SOURCE = "gitlab"`
  (`app/tasks_gitlab_sync.py`) écrit effectivement dans cette table
  aujourd'hui (`write_sync_meta`, appelée en succès depuis
  `sync_assigned_gitlab_tasks` et en échec depuis `app/main.py` pour la voie
  directe). TimeTree n'y écrit rien : son état (`ok`/`detail`) est porté
  directement par `TimeTreeFetchResult`, consommé immédiatement par le
  template (`timetree_ok`, `timetree_detail` dans le contexte de rendu), sans
  persistance. Écart entre commentaire et code à garder en tête si
  `TaskSyncMeta` est un jour étendu à TimeTree.
- **Aucun bandeau pour un échec du cache pilotage** : la lecture de
  `CachedGitLabIssue`/`LinkedTicket` est une requête SQL locale, jamais un
  appel réseau — son seul mode de dégradation (fichier absent) est déjà
  absorbé en amont par `pilotage_link._get_engine`, qui rend alors une
  session `None` (intégration désactivée proprement, pas d'erreur). Seule la
  voie d'import direct alimente `gitlab_direct_error`.
- **Bandeau TimeTree silencieux quand non configuré (issue #14).** Correctif
  ponctuel d'un bandeau qui s'affichait à tort même quand TimeTree était
  simplement non configuré (identifiants absents, cas normal) : la condition
  du template est passée de `{% if not timetree_ok %}` à `{% if
  timetree_configured and not timetree_ok %}`, avec suppression de la branche
  « non configuré » qui affichait un message. Aucun changement côté
  `app/main.py` : `timetree_ok`/`timetree_configured`/`timetree_detail`
  étaient déjà dans le contexte de rendu. Principe généralisé : **toute**
  intégration de calendrier/tâche externe doit rester entièrement muette tant
  qu'elle n'est pas configurée — seul un échec **après** configuration
  mérite un bandeau (même logique déjà appliquée à `gitlab_direct_error`,
  vide par défaut).
- **Injection `client=`/dépendances testables** : `GitLabClient.__init__`
  accepte un `httpx.Client` optionnel (mock `respx` en test, comme les
  clients Redmine/GitLab historiques de `pilotage-pleiade-gitlab`) ; de même,
  `get_pilotage_session`/`fetch_busy_slots` sont substituables par
  monkeypatch dans les tests (`main.get_pilotage_session`,
  `calendar.timetree_source.fetch_busy_slots`). Aucun test de cette spec
  n'effectue d'appel réseau réel.
- **Patron de cache dupliqué à l'identique**, volontairement, entre
  `timetree_source.py` (antérieur) et `gitlab_direct.py` (repris tel quel
  lors de l'ajout de l'import direct) : dict module-level, clé de
  configuration, tuple `(horodatage, résultat)` — cohérence de style
  préférée à une factorisation prématurée pour deux occurrences aussi
  proches dans le temps.

### Invariants et garde-fous

- **Jamais d'écriture externe** : aucun des cinq modules n'émet de requête
  HTTP en écriture ni de commande d'écriture SQL vers une base qui ne lui
  appartient pas. `pilotage.db` n'est jamais migré ni modifié par Kairos
  (`PilotageBase` sans `create_all`). Les clients GitLab (direct et,
  historiquement, Superproductivity) sont bornés au strict nécessaire en
  lecture — aucune méthode `POST`/`PATCH`/`DELETE` n'existe dans
  `GitLabClient`.
- **Jamais de page en erreur 500 sur défaillance d'une source externe** :
  chaque point d'entrée (`fetch_busy_slots`, `fetch_assigned_issues`) retourne
  un résultat typé (`ok`, `detail`) et n'est jamais laissé lever une
  exception non interceptée jusqu'à la route Starlette. `git_credentials`
  suit le même principe (absence de jeton = chaîne vide, jamais une
  exception).
- **Priorité et temps passé locaux jamais écrasés par une synchro externe** :
  invariant tenu par construction dans `sync_assigned_gitlab_tasks`, qui ne
  touche jamais `priority` ni `manual_time_spent_minutes` — seuls `title`,
  `deadline`, `project_tag`, `status` sont posés depuis une issue GitLab.
- **Configuration absente = silence, pas d'erreur** : chaque intégration a sa
  propre garde d'activation (`timetree_configured`, `pilotage_configured`,
  `gitlab_direct_configured`) vérifiée en tête de chaque point d'entrée, et
  le gabarit d'affichage (`_kairos_banners.html`) ne montre un bandeau que si
  la source est **configurée et en échec** — jamais pour une source
  simplement absente.
- **Idempotence de la synchro GitLab** : rejouer `sync_assigned_gitlab_tasks`
  avec le même jeu d'issues ne crée ni doublon ni changement d'état
  au-delà de ce que reflètent les issues elles-mêmes (upsert sur
  `(source, external_id)`, contrainte `UniqueConstraint` en base).
- **Une issue disparue archive, ne supprime jamais** : cohérent avec le reste
  du modèle de données (`Task.status == "archived"`), aucune perte
  d'historique de temps ou de dépendances sur une tâche d'origine GitLab.
