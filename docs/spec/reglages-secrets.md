# Réglages & secrets

_Rôle : modèle, validation, persistance et page d'administration de tous les
paramètres réglables de Kairos — la garantie que l'outil démarre **sans aucun
fichier de configuration à éditer à la main** (autonomie actée en phase 14/17
de l'historique du projet) et que les identifiants (jeton GitLab, mot de passe
TimeTree) ne transitent ni ne s'affichent jamais en clair dans l'interface.
Fichiers couverts intégralement : `app/config.py` (modèle `Settings`),
`app/settings_fields.py` (`Field`, validation, registre `model_fields`),
`app/settings_sections.py` (regroupement pour l'affichage),
`app/settings_store.py` (persistance JSON + migration `.env`),
`app/secret_store.py` (trousseau système), `templates/settings.html` (page
`/kairos/settings`). Les **clients réseau** GitLab/TimeTree (résolution du
jeton via `git credential fill`/`~/.netrc`, appels API, dégradation réseau)
sont hors périmètre — voir `integrations-externes.md` ; ce document ne décrit
que les champs de réglage et le stockage des secrets qui les alimentent._

## 1. Besoin métier (cahier des charges)

### Objectif / problème

Kairos est un outil mono-utilisateur, 100 % local, distribué à des collègues
sous plusieurs formes (service systemd, exécutable de bureau PyInstaller,
APK Android). Chacune de ces cibles exclut une pratique classique de
configuration :

- pas de shell pour éditer un `.env` sur un exécutable de bureau grand public
  ni sur Android (sandbox applicative, pas de terminal) ;
- pas de dépendance native bloquante sur Android — `pydantic-core` (Rust,
  utilisé par FastAPI/Pydantic v2, la stack de départ du projet) n'a **aucune**
  wheel Android disponible (vérifié le 2026-07-12 : PyPI, dépôt Chaquopy,
  canal BeeWare) ;
- un jeton GitLab ou un mot de passe TimeTree ne doivent jamais être stockés
  en clair si le poste offre un trousseau système, mais l'absence de trousseau
  (Linux headless, Android) ne doit **jamais** empêcher Kairos de fonctionner.

Historiquement (`SPEC_KAIROS.md`), Kairos vivait comme un onglet d'un autre
outil (`pilotage-pleiade-gitlab`), partageant une base/un `.env`/un service
uniques (phase 14). Son passage en outil autonome a exigé que **tout** soit
réglable depuis une page web de l'application elle-même, sans fichier à
copier/éditer manuellement — seule une migration automatique, une fois, lit
un éventuel `.env` hérité pour ne pas perdre la configuration d'un utilisateur
existant.

### Comportement attendu (utilisateur)

- Kairos démarre avec des valeurs par défaut sensées, sans aucune
  configuration préalable : toutes les intégrations optionnelles (GitLab,
  pilotage, TimeTree) sont désactivées proprement (champs vides), aucune
  erreur au démarrage.
- Toute la configuration se fait depuis une seule page, `/kairos/settings`,
  organisée en sections thématiques (base de données, import GitLab,
  calendrier TimeTree, ordonnancement, WSJF, creux de l'après-midi,
  garde-fous, types de tâches, statistiques, alertes de chrono, jours
  fériés, réseau/proxy, divers).
- Un champ invalide (hors bornes, mauvais type) réaffiche le formulaire avec
  un message d'erreur **par champ**, sans jamais enregistrer un état partiel ;
  les valeurs déjà saisies (hors secrets) sont conservées pour ne pas resaisir
  tout le formulaire.
- Le jeton GitLab et le mot de passe TimeTree ne sont **jamais réaffichés en
  clair** : seul un statut (« défini » / « non défini ») est visible. Laisser
  le champ vide au réenregistrement **conserve** la valeur existante ; une
  case à cocher dédiée (« Effacer ce réglage ») est le seul moyen de vider un
  secret déjà enregistré.
- Si le trousseau système du poste est indisponible, l'utilisateur en est
  informé par un bandeau explicite sur la page Réglages, mais la sauvegarde
  **réussit quand même** (repli automatique sur le fichier de réglages local,
  non chiffré) — jamais d'erreur bloquante liée à cette dépendance externe.
- Un utilisateur migrant depuis l'ancienne installation à base de `.env` voit
  sa configuration reprise automatiquement (une seule fois) à la première
  page vue, avec un message indiquant la date de migration ; son ancien
  fichier `.env` n'est jamais supprimé par Kairos.
- Le champ « chemin de la base de tâches » est signalé comme nécessitant un
  redémarrage de l'application pour prendre effet (badge dédié) ; tous les
  autres réglages s'appliquent à la sauvegarde, sans redémarrage.
- Un réglage de proxy sortant (HTTP/HTTPS/no_proxy) est disponible pour les
  postes derrière un proxy d'entreprise (nécessaire notamment pour joindre
  TimeTree) et s'applique immédiatement après sauvegarde.

### Critères de succès

Repris et fusionnés des phases historiques (`SPEC_KAIROS.md`, phases 8, 14, 17) :

- Kairos démarre et fonctionne **sans aucun `.env`** ni base pilotage : toutes
  les fonctionnalités hors import GitLab/liaison fiche pilotage restent
  disponibles.
- Une ancienne installation `.env` (utilisateur existant du dépôt historique)
  est reprise automatiquement à la première exécution sans fichier de
  réglages, sans perte de valeur, et sans double migration au lancement
  suivant.
- Un secret jamais réaffiché en clair dans le HTML rendu, y compris après une
  sauvegarde réussie ou une erreur de validation sur un autre champ du même
  formulaire.
- Le trousseau système est utilisé quand disponible (Windows Credential
  Manager, GNOME Keyring/SecretService, Keychain macOS) ; son indisponibilité
  (Linux headless, Android — aucun trousseau) dégrade silencieusement vers un
  stockage fichier, sans jamais faire échouer une sauvegarde de réglages.
- Le retrait d'une fonctionnalité (ex. Superproductivity, phase 8) retire ses
  réglages de `Settings` sans laisser de code mort ni de référence résiduelle.
- Aucune dépendance native bloquante pour Android : le remplacement de
  Pydantic par des dataclasses maison ne change aucun comportement observable
  pour les appelants (mêmes noms d'attributs, mêmes messages d'erreur, même
  registre `model_fields`).

### Hors périmètre / différé

- Les clients HTTP GitLab/TimeTree, la résolution du jeton via `git credential
  fill`/`~/.netrc`, la logique de cache TTL en mémoire et la dégradation
  réseau : `integrations-externes.md`.
- Le calcul du score WSJF, du placement horaire, du creux de l'après-midi :
  `ordonnancement.md` (ce document ne couvre que les *champs* de réglage
  correspondants, pas leur consommation).
- Le calcul des jours fériés/décalages jour ouvré (`app/workdays.py`) : la
  propriété `Settings.holiday_set` n'est décrite ici que comme point d'entrée
  (champs `holidays_fr`/`extra_holidays`) ; le calcul lui-même est hors
  périmètre de cette spec.
- Chiffrement du fichier de repli quand le trousseau est indisponible : jamais
  prévu (voir § pièges tracés) — repli assumé en clair, signalé à l'utilisateur.
- Interface de changement de mot de passe/rotation de jeton : pas de flux
  dédié, seule la re-saisie complète du champ est supportée.

## 2. Solution technique

### Vue d'ensemble

```
app/settings_fields.py   → Field(), FieldInfo, SettingsValidationError, build_field_registry, validate_fields
        │  (types + bornes, registre "model_fields")
        ▼
app/config.py            → dataclass Settings (tous les champs), __post_init__ (validation),
                            propriétés dérivées, get_settings()/invalidate_settings_cache(), apply_proxy_env()
        │
        ├──────────────► app/settings_sections.py  → SECTIONS, SECRET_FIELDS, RESTART_REQUIRED_FIELDS, FIELD_LABELS
        │                  (pure présentation, aucune valeur/description dupliquée)
        │
        ▼
app/settings_store.py    → data_dir()/settings_path(), load()/save() (JSON + trousseau),
                            migration .env une fois
        │
        ▼
app/secret_store.py      → keyring système, repli fichier silencieux (get_secret/set_secret/keyring_available)
        │
        ▼
templates/settings.html + app/main.py (_field_kind, _settings_context,
        _settings_candidate_from_form, routes GET/POST /kairos/settings)
```

Chaîne de lecture au démarrage et à chaque requête : `get_settings()` (mis en
cache par `functools.lru_cache`) appelle `settings_store.load()`, qui lit le
fichier JSON (ou migre un `.env` hérité une fois, ou retombe sur les défauts),
puis résout chaque champ secret via `secret_store.get_secret` (trousseau
prioritaire, repli sur la valeur en clair du fichier). Après une sauvegarde
réussie, `invalidate_settings_cache()` vide le cache pour que la requête
suivante relise l'état à jour — pas de redémarrage nécessaire, sauf pour
`tasks_database_path` (moteur de base de données lié au chemin dès l'import de
`app/tasks_db.py`).

### Détail par composant

#### `app/settings_fields.py` — surface de remplacement de Pydantic

- `Field(default=..., default_factory=..., description=..., ge=, gt=, le=)` :
  wrapper autour de `dataclasses.field()`, empile les métadonnées
  (`description`, `ge`, `gt`, `le`) dans `metadata` du champ dataclass ; même
  signature d'appel que le `pydantic.Field` remplacé (pour la surface utilisée
  par ce projet uniquement — pas une réimplémentation générale de Pydantic).
- `FieldInfo` (dataclass frozen) : `annotation`, `description`, `ge`, `gt`,
  `le` — noms d'attributs identiques à ceux consommés par les appelants
  historiques (`.annotation`, `.description`), pour ne toucher ni les
  gabarits Jinja ni `app/main.py`.
- `build_field_registry(cls)` : construit `{nom_champ: FieldInfo}` via
  `dataclasses.fields()` + `typing.get_type_hints()` — équivalent du
  `Settings.model_fields` de Pydantic, réassigné en attribut de classe à la
  fin de `app/config.py` (`Settings.model_fields = build_field_registry(Settings)`)
  pour que `Settings.model_fields` reste un identifiant valide partout où il
  était déjà utilisé (page Réglages, `settings_store`, `main._field_kind`).
- `SettingsValidationError(ValueError)` : porte un dict `errors` (`{champ:
  message}`), clé spéciale `_general` pour les règles inter-champs (plages).
  Le message de l'exception concatène `"{name}: {msg}"` pour chaque erreur —
  utile en log, jamais affiché tel quel à l'utilisateur (la page Réglages lit
  `.errors` champ par champ).
- `validate_fields(obj)` : parcourt `type(obj).model_fields`, vérifie le type
  Python de chaque valeur puis les bornes `gt`/`ge`/`le` (dans cet ordre de
  priorité — un seul message par champ, `gt` prioritaire sur `ge`).
  - `bool` : refusé si la valeur n'est **pas** un `bool` — piège explicite
    d'`isinstance(True, int)` qui accepterait silencieusement un booléen là où
    un entier est attendu (et inversement, un entier ne doit jamais être
    accepté à la place d'un booléen).
  - `int` : refusé si la valeur est un `bool` (même piège) ou n'est pas un
    `int`.
  - `float` : accepte aussi un `int` (converti sur place en `float` via
    `setattr`), pour tolérer un JSON édité à la main où `4` est écrit à la
    place de `4.0` — décision explicite documentée dans le docstring de la
    fonction.
  - `str` : refusé si la valeur n'est pas une chaîne.

#### `app/config.py` — modèle `Settings`

Dataclass `@dataclasses.dataclass` unique portant tous les réglages, groupés
par commentaires thématiques dans le code source (voir tableau exhaustif
ci-dessous). Points de comportement :

- `_default_tasks_database_path()` : dossier de données de l'OS
  (`platformdirs.user_data_dir("Kairos", appauthor=False)`) plutôt que le
  répertoire de lancement — un exécutable packagé (PyInstaller, APK) n'a pas
  de répertoire de travail fiable. `KAIROS_DATA_DIR` (variable
  d'environnement) prime quand posée : c'est la même variable que celle lue
  par `settings_store.data_dir()`, utilisée par l'app Android pour pointer
  vers le stockage privé (`Context.getFilesDir()`) et disponible aussi en
  mode « portable » sur poste de bureau.
- `__post_init__` : appelle `validate_fields(self)` (types/bornes par champ)
  puis, seulement si aucune erreur de champ n'a été trouvée, deux règles
  inter-champs stockées sous la clé `_general` :
  1. `workday_start_hour < workday_end_hour` (message : « L'heure de début de
     journée doit être avant l'heure de fin. ») ;
  2. `cognitive_dip_start_hour <= cognitive_dip_trough_hour <=
     cognitive_dip_end_hour` (message : « Le creux de l'après-midi doit
     respecter début ≤ tronc ≤ fin. »).
  Toute erreur (champ ou générale) lève `SettingsValidationError`.
- `model_dump(mode=None)` : conservé sous ce nom (compat Pydantic) pour tous
  les appelants existants ; retourne `dataclasses.asdict(self)`. Le paramètre
  `mode` est accepté mais sans effet — tous les champs de `Settings` sont déjà
  des types JSON natifs (`str`/`int`/`float`/`bool`), aucune conversion
  `mode="json"` n'est nécessaire.
- Propriétés dérivées (jamais persistées, recalculées à chaque accès) :

  | Propriété | Calcul | Rôle |
  |---|---|---|
  | `tasks_database_url` | `f"sqlite:///{tasks_database_path}"` | URL SQLAlchemy passée à `create_engine`. |
  | `timetree_configured` | `bool(timetree_email and timetree_password)` | Active/désactive l'intégration calendrier. |
  | `pilotage_configured` | `bool(pilotage_database_path)` | Active/désactive le cache pilotage GitLab. |
  | `gitlab_project_list` | `gitlab_projects` éclaté sur `,`, éléments vides retirés | Liste de projets pour le client GitLab direct. |
  | `task_type_list` | `task_types` éclaté sur `,`, éléments vides retirés | Menu déroulant de la fiche tâche. |
  | `gitlab_token_effective` | `gitlab_token` si renseigné, sinon `git_credentials.resolve_gitlab_token(gitlab_url)` (vide si `gitlab_url` vide) | Jeton réellement utilisé par le client GitLab — détail de résolution hors périmètre, voir `integrations-externes.md`. |
  | `gitlab_direct_configured` | `not pilotage_configured and gitlab_url and gitlab_token_effective and gitlab_project_list and gitlab_assignee_username` | Active l'import direct (sans cache pilotage). |
  | `holiday_set` | `app.workdays.build_holidays(...)` sur `holidays_fr`/`extra_holidays`, année courante ±1/+2 ; `frozenset()` vide si les deux sont désactivés | Calendrier des jours fériés (calcul hors périmètre, voir `app/workdays.py`). |

- `get_settings()` : instance unique mise en cache (`functools.lru_cache`),
  déléguée à `settings_store.load()`.
- `invalidate_settings_cache()` : vide ce cache — appelé après chaque
  sauvegarde réussie par `app/main.py`, jamais par `settings_store` lui-même
  (séparation lecture/écriture propre).
- `apply_proxy_env(settings)` : injecte `http_proxy`/`https_proxy`/`no_proxy`
  dans l'environnement du **processus** (variantes minuscule et majuscule des
  trois noms), car `requests`/`httpx` (clients TimeTree/GitLab) lisent les
  variables d'environnement standard, jamais `Settings` directement. Une
  valeur vide **retire** la variable d'environnement plutôt que d'y écrire une
  chaîne vide (`os.environ.pop(..., None)`). Appelé au démarrage
  (`app/main.py`, lifespan) et après chaque sauvegarde de réglages.

#### Tableau exhaustif des champs de `Settings`

Ordre et regroupement identiques à `app/settings_sections.py::SECTIONS`
(ordre d'affichage de la page Réglages). Secret = trousseau système en
priorité (`app/secret_store.py`). Redémarrage = nécessite un redémarrage du
processus Kairos pour prendre effet.

| Section | Champ | Type | Défaut | Bornes | Secret | Redémarrage | Rôle |
|---|---|---|---|---|---|---|---|
| Base de données | `tasks_database_path` | str | dossier de données OS + `tasks.db` (`KAIROS_DATA_DIR` prioritaire) | — | | ✓ | Chemin de la base SQLite des tâches. |
| Import GitLab | `gitlab_assignee_username` | str | `""` | — | | | Nom d'utilisateur GitLab (assigné) dont les issues ouvertes sont importées. |
| Import GitLab | `pilotage_database_path` | str | `""` | — | | | Chemin de la base `pilotage.db` (cache GitLab en lecture seule) ; vide = désactivé. |
| Import GitLab | `gitlab_url` | str | `""` | — | | | URL de l'instance GitLab pour l'import direct (utilisé seulement si `pilotage_database_path` est vide). |
| Import GitLab | `gitlab_token` | str | `""` | — | ✓ | | Jeton d'accès personnel GitLab (scope `read_api`) ; optionnel, résolution `git credential`/`.netrc` en repli. |
| Import GitLab | `gitlab_projects` | str | `""` | — | | | Projets GitLab (`groupe/projet` ou id), séparés par des virgules. |
| Import GitLab | `gitlab_cache_ttl_minutes` | int | `5` | `ge=0` | | | Durée de mise en cache des issues GitLab (anti rate-limiting). |
| Calendrier TimeTree | `timetree_email` | str | `""` | — | | | E-mail du compte TimeTree. |
| Calendrier TimeTree | `timetree_password` | str | `""` | — | ✓ | | Mot de passe du compte TimeTree. |
| Calendrier TimeTree | `timetree_calendar_code` | str | `""` | — | | | Code du calendrier TimeTree à exporter. |
| Calendrier TimeTree | `timetree_cache_ttl_minutes` | int | `30` | `ge=0` | | | Durée de mise en cache des créneaux TimeTree. |
| Ordonnancement | `default_task_duration_minutes` | int | `30` | `ge=1` | | | Durée par défaut d'une tâche sans estimation (jamais stockée). |
| Ordonnancement | `meeting_buffer_minutes` | int | `5` | `ge=0` | | | Marge laissée après un créneau occupé. |
| Ordonnancement | `workday_start_hour` | int | `9` | `0-23` | | | Heure de début de la journée de travail. |
| Ordonnancement | `workday_end_hour` | int | `18` | `0-23` | | | Heure de fin de la journée de travail (doit être `>` début, règle `_general`). |
| Ordonnancement WSJF | `priority_value_base` | float | `4.0` | `gt=0` | | | Base exponentielle d'un cran de priorité (`base ** (2 - priorité)`). |
| Ordonnancement WSJF | `urgency_horizon_days` | int | `14` | `ge=0` | | | Jours avant échéance où la criticité temporelle commence à monter. |
| Ordonnancement WSJF | `urgency_peak` | float | `8.0` | `ge=0` | | | Poids maximal de la criticité temporelle. |
| Ordonnancement WSJF | `default_fibonacci_points` | int | `3` | `ge=1` | | | Effort attribué à une tâche sans estimation (dénominateur du score). |
| Creux de l'après-midi | `cognitive_dip_enabled` | bool | `True` | — | | | Active la pénalité de placement des tâches complexes l'après-midi. |
| Creux de l'après-midi | `cognitive_dip_start_hour` | int | `13` | `0-23` | | | Début de la fenêtre de creux post-déjeuner. |
| Creux de l'après-midi | `cognitive_dip_trough_hour` | int | `15` | `0-23` | | | Heure du creux le plus profond (doit rester entre début et fin, règle `_general`). |
| Creux de l'après-midi | `cognitive_dip_end_hour` | int | `16` | `0-23` | | | Fin de la fenêtre de creux post-déjeuner. |
| Creux de l'après-midi | `cognitive_dip_penalty` | float | `1.0` | `ge=0` | | | Force de la pénalité au tronc pour une tâche de complexité maximale. |
| Garde-fous | `stale_overdue_days` | int | `7` | `ge=0` | | | Jours après échéance dépassée avant le badge « traîne depuis N j ». |
| Garde-fous | `stale_untouched_days` | int | `14` | `ge=0` | | | Jours sans modification (tâche sans date) avant le même badge. |
| Garde-fous | `priority_overload_threshold` | int | `5` | `ge=0` | | | Nombre de tâches P0 au-delà duquel un bandeau de surcharge s'affiche. |
| Types de tâches | `task_types` | str | `"Développement,Revue de code,Réunion,Documentation,Administratif,Veille/formation,Pilotage/dette technique"` | — | | | Types proposés dans le menu déroulant de la fiche tâche (CSV librement éditable). |
| Dashboard de statistiques | `stats_window_weeks` | int | `8` | `ge=1` | | | Fenêtre (semaines) des indicateurs « récents ». |
| Alertes de chrono | `timer_idle_alert_minutes` | int | `180` | `ge=0` | | | Alerte « chrono oublié » (0 = désactivé). |
| Alertes de chrono | `pomodoro_focus_minutes` | int | `50` | `ge=0` | | | Rappel de pause après N minutes de focus continu (0 = désactivé). |
| Jours fériés | `holidays_fr` | bool | `True` | — | | | Active le calendrier des jours fériés français. |
| Jours fériés | `extra_holidays` | str | `""` | — | | | Dates fériées supplémentaires (ISO, CSV). |
| Réseau (proxy sortant) | `http_proxy` | str | `""` | — | | | Proxy HTTP sortant. |
| Réseau (proxy sortant) | `https_proxy` | str | `""` | — | | | Proxy HTTPS sortant. |
| Réseau (proxy sortant) | `no_proxy` | str | `"127.0.0.1,localhost"` | — | | | Domaines/IPs jamais envoyés au proxy. |
| Divers | `log_level` | str | `"INFO"` | — | | | Niveau de log console (`DEBUG`/`INFO`/`WARNING`/`ERROR`, non contraint par bornes — libre saisie). |

37 champs au total, tous optionnels (aucune valeur par défaut ne rend Kairos
non fonctionnel — les intégrations GitLab/TimeTree/pilotage se désactivent
proprement quand leurs champs sont vides).

#### `app/settings_sections.py` — regroupement d'affichage

Module de présentation pure : ne duplique **aucune** valeur ni description
(portées par `app/config.py`), seulement l'ordre et le regroupement en
sections — calqué sur l'ancien découpage de `.env.example` de l'installation
historique.

- `SECTIONS: list[tuple[str, list[str]]]` : 13 sections, ordre affiché
  ci-dessus.
- `SECRET_FIELDS: tuple[str, ...] = ("gitlab_token", "timetree_password")` —
  seuls champs jamais réaffichés en clair, seuls champs tentés en priorité
  vers le trousseau système.
- `RESTART_REQUIRED_FIELDS: tuple[str, ...] = ("tasks_database_path",)` —
  seul champ dont l'effet n'est pas pris en compte à chaud, car le moteur
  SQLAlchemy est lié au chemin dès l'import de `app/tasks_db.py`.
- `FIELD_LABELS: dict[str, str]` — libellé court par champ, affiché
  au-dessus du contrôle (la description longue de
  `Settings.model_fields[name].description` reste affichée en dessous, dans
  le gabarit).

#### `app/settings_store.py` — persistance JSON + migration `.env`

- `data_dir()` : dossier de données de l'OS (`platformdirs.user_data_dir
  ("Kairos", appauthor=False)`, créé si besoin) ; `KAIROS_DATA_DIR` prime si
  posée (même contrat que `config._default_tasks_database_path`) — l'app
  Android y écrit le stockage privé de l'application sans dépendre de la
  détection de plateforme de `platformdirs`, et un poste de bureau peut s'en
  servir pour un mode « portable ».
- `settings_path()` : `data_dir() / "settings.json"`.
- Enveloppe JSON persistée : `{"schema_version": 1, "settings": {...},
  "meta": {...}}`. `meta` porte notamment `migrated_from_env_at` /
  `migrated_from_env_path` après une migration `.env`.
- `_write_envelope` : écriture **atomique** — écrit dans `settings.json.tmp`
  puis `os.replace()` vers `settings.json`, pour ne jamais laisser de fichier
  à moitié écrit en cas d'interruption pendant l'écriture (coupure de
  courant, kill du process).
- `load()` :
  1. Fichier absent → tente une migration `.env` (`_migrate_legacy_env_if_needed`) ;
     si aucun `.env` trouvé, retourne `Settings()` (défauts), **sans** créer
     de fichier `settings.json` (le fichier n'apparaît qu'à la première
     sauvegarde explicite).
  2. Fichier présent → lit `settings` de l'enveloppe, résout chaque champ de
     `SECRET_FIELDS` via `secret_store.get_secret(field, plain_fallback=
     valeur_du_fichier)` (le trousseau prime, repli sur la valeur en clair du
     fichier si le trousseau est indisponible ou vide).
  3. Filtre les clés absentes de `Settings.model_fields` — tolérance
     équivalente à l'ancien `extra="ignore"` de Pydantic : un réglage
     retiré/renommé par une mise à jour ne doit jamais faire planter le
     chargement (une dataclass, elle, refuserait le kwarg inattendu sans ce
     filtre explicite).
  4. Construit `Settings(**data)` ; si `SettingsValidationError` (fichier
     corrompu ou champ invalide après une modification manuelle du JSON),
     dégrade silencieusement vers `Settings()` par défaut — **jamais**
     d'erreur dure au démarrage pour un fichier de réglages invalide,
     cohérent avec la philosophie de dégradation propre du reste du projet
     (dépendances externes/fichiers locaux).
- `save(settings)` :
  1. Pour chaque champ de `SECRET_FIELDS`, tente `secret_store.set_secret` ;
     si le trousseau échoue, la valeur est conservée en clair dans
     `fallback_values` et un message est ajouté à `warnings[field]`.
  2. `dump = settings.model_dump(mode="json")`, retire les champs secrets du
     dump, puis réinjecte `fallback_values` (secrets non stockés en trousseau
     restent donc dans le fichier JSON, en clair — seul moyen de ne pas les
     perdre si le trousseau est indisponible).
  3. Écrit l'enveloppe (avec `meta` inchangée) via `_write_envelope`.
  4. Retourne `warnings: dict[str, str]`. **Comportement actuel observé** :
     `app/main.py::kairos_settings_save` (route `POST /kairos/settings`)
     appelle `settings_store.save(new_settings)` sans capturer cette valeur
     de retour — le seul signal montré à l'utilisateur sur l'état du
     trousseau est le bandeau standing calculé à **chaque rendu** de la page
     via `secret_store.keyring_available()` (capacité du poste, pas un
     avertissement ponctuel lié à cette sauvegarde précise). Le dict
     `warnings` par champ n'est donc exploité que par les tests
     (`tests/test_settings_store.py::test_save_secret_without_keyring_falls_back_to_plain_file`),
     pas par l'interface. Documenté ici tel quel (bijectivité) ; non modifié
     dans le cadre de cette tâche de spécification.
- Migration `.env` (`_migrate_legacy_env_if_needed`) :
  - `_find_legacy_env()` : cherche `.env` dans le répertoire de lancement
    (`Path.cwd() / ".env"`) — même résolution que l'ancien
    `pydantic-settings` (`env_file=".env"`, relatif au `cwd`), car `make run`/
    `make dev` et le service systemd (`WorkingDirectory=`) lancent tous deux
    depuis la racine du dépôt.
  - `_parse_dotenv` : petit parseur fait main (`clé=valeur`, commentaires
    `#`, dé-guillemetage simple) — délibérément sans support des valeurs
    multi-lignes, l'ancien `.env.example` n'en a jamais eu besoin ; évite de
    garder `pydantic-settings` comme dépendance uniquement pour ce pont de
    migration ponctuel.
  - `_coerce(annotation, raw)` : convertit une valeur texte selon le type du
    champ cible (`bool` accepte `1/true/yes/on/vrai`, insensible à la casse ;
    `int`/`float` via conversion native ; sinon chaîne telle quelle).
  - Clés inconnues de `Settings.model_fields` ignorées ; une valeur
    inconvertible pour son type (`ValueError`) est ignorée champ par champ
    (le défaut s'applique), sans faire échouer toute la migration.
  - `Settings(**candidate)` en cas d'échec de validation globale (ex. règle
    inter-champs violée par le `.env` d'origine) retombe sur `Settings()`
    (défauts).
  - **Se déclenche une seule fois** : seulement si `settings.json` n'existe
    pas encore. Une fois migré, `save()` écrit `settings.json`, donc tout
    chargement suivant emprunte le chemin normal (fichier existant) —
    modifier le `.env` après coup n'a plus aucun effet. Le `.env` original
    n'est **jamais supprimé** par cette migration (l'utilisateur le retire
    lui-même s'il le souhaite, la page Réglages l'y invite avec un message
    daté).

#### `app/secret_store.py` — trousseau système

- Backend : bibliothèque `keyring` (Windows Credential Manager, GNOME
  Keyring/SecretService, Keychain macOS), service nommé `"kairos"`.
- `keyring_available()` : vrai si le backend actif n'est **pas**
  `keyring.backends.fail.Keyring` (backend renvoyé quand aucun vrai trousseau
  n'est utilisable sur le poste) ; toute exception à l'obtention du backend
  est aussi traitée comme indisponible (`except Exception: return False`).
- `get_secret(field_name, *, plain_fallback)` : lit `keyring.get_password`,
  retourne `plain_fallback` si la valeur est vide/absente **ou** si l'appel
  lève une exception (trousseau indisponible) — jamais d'exception ne remonte
  à l'appelant.
- `set_secret(field_name, value)` → `(stocké_via_trousseau: bool, détail:
  str)` :
  - `value` vide → tente `keyring.delete_password` (best-effort ; une
    exception, ex. rien à effacer, est avalée silencieusement — « sans
    conséquence », commentaire du code) → retourne toujours `(True, "")`.
  - `value` non vide → tente `keyring.set_password` ; échec → retourne
    `(False, _FALLBACK_DETAIL)`, message affiché comme avertissement par la
    page Réglages ; succès → `(True, "")`.
- Philosophie de dégradation identique, dans les deux sens (lecture et
  écriture), à `app/git_credentials.py` et au client TimeTree : **jamais
  d'erreur dure** sur cette dépendance externe. Sur Android, aucun trousseau
  n'existe (pas de service SecretService/Keychain équivalent) : le repli
  fichier est le comportement **normal et attendu** sur cette plateforme,
  déjà prévu par ce module sans code spécifique Android (voir
  `docs/ANDROID_PACKAGING.md`).

#### `templates/settings.html` + routes `app/main.py`

- `GET /kairos/settings` (`app/main.py::kairos_settings`) : construit le
  contexte via `_settings_context(get_settings(), errors={}, values=None,
  saved=...)` — `saved` reflète le paramètre de requête `?saved=1` posé après
  une sauvegarde réussie (redirection 303, pattern Post/Redirect/Get).
- `_field_kind(name)` (`app/main.py`) : `"secret"` si le champ est dans
  `SECRET_FIELDS`, sinon déduit de `Settings.model_fields[name].annotation`
  via `_FIELD_KIND_BY_ANNOTATION = {bool: "bool", int: "int", float:
  "float"}`, `"text"` par défaut (`str`). Pilote à la fois le type de
  contrôle HTML rendu et la coercion du formulaire à la soumission.
- `_settings_context(...)` : construit `display_values` à partir soit des
  valeurs courantes (`settings.model_dump()`) soit du candidat re-saisi en
  cas d'erreur (`values`) — dans les deux cas, les champs de `SECRET_FIELDS`
  sont **retirés** de `display_values` avant transmission au gabarit
  (`.pop(name, None)`) : le secret n'atteint jamais le rendu HTML, ni côté
  affichage normal, ni côté réaffichage après erreur de validation sur un
  autre champ. `secret_status` transmet seulement un booléen (« défini » /
  « non défini »), jamais la valeur.
- `_settings_candidate_from_form(form, current)` : construit le dict candidat
  pour `Settings(**candidate)` en partant de `current.model_dump()` (valeurs
  actuelles comme base), puis par champ :
  - `secret` : case `{name}_clear` cochée → vide le champ ; sinon champ texte
    non vide dans le formulaire → remplace ; sinon (champ laissé vide, case
    non cochée) → **conserve** la valeur actuelle (`current`) inchangée. Un
    formulaire HTML soumettant systématiquement tous ses champs, un champ mot
    de passe vide ne signifie donc jamais « effacer » — seule la case dédiée
    le fait, pour permettre de ne resaisir un secret que si on veut le
    changer.
  - `bool` : présence de la clé dans le formulaire (`form.get(name) is not
    None`) — une case décochée est absente du POST HTML, donc correctement
    interprétée comme `False`.
  - `int`/`float` : conversion avec capture de `ValueError` par champ
    (message générique « Valeur numérique invalide. »), sans jamais faire
    planter toute la route pour un champ non numérique.
  - `text` : chaîne strippée telle quelle.
- `POST /kairos/settings` (`kairos_settings_save`) : construit le candidat,
  si aucune erreur de conversion de type, tente `Settings(**candidate)` ;
  `SettingsValidationError` fusionnée dans `errors` (champs + `_general`) ;
  succès → `settings_store.save(new_settings)`,
  `invalidate_settings_cache()`, `apply_proxy_env(get_settings())`, puis
  redirection 303 vers `?saved=1`. Toute erreur (conversion ou validation)
  réaffiche le formulaire (`_settings_context(current, errors=errors,
  values=candidate, saved=False)`) avec un statut 200 — jamais de
  redirection sur erreur, pour ne pas avaler silencieusement le message.
- Gabarit `templates/settings.html` :
  - Bandeau succès (`saved`), bandeau erreur générale
    (`errors._general`), bandeau trousseau indisponible
    (`not keyring_available`, calculé à chaque GET/POST via
    `secret_store.keyring_available()`).
  - Bloc « Emplacement des données » : chemin du fichier de réglages
    (`settings_path`), dossier de données (`data_dir`), date de migration
    `.env` si applicable (`migrated_at`, tronqué à la date `[:10]`).
  - Une section repliable (`<details class="panel mj-settings-section">` +
    `<summary class="collapser">`, patron déjà utilisé ailleurs dans l'app —
    voir `docs/spec/vue-jour-gtd.md`) par entrée de `SECTIONS`, fermée par
    défaut sauf si un de ses champs porte une erreur de validation
    (`{% if field_names | select('in', errors) | list %}open{% endif %}` —
    ouverte automatiquement pour que l'erreur reste visible sans avoir à
    chercher la bonne section). Un champ par entrée de `field_names`, rendu
    selon `field_kind[name]` :
    - `bool` → case à cocher, libellé inline (pas de `<label for>` séparé) ;
    - `secret` → statut « défini »/« non défini », `<input type="password">`
      avec `placeholder="laisser vide pour ne pas modifier"`,
      `autocomplete="new-password"` (empêche le navigateur de proposer un mot
      de passe enregistré sans rapport), case « Effacer ce réglage » ;
    - `int` → `<input type="number" step="1">` ;
    - `float` → `<input type="number" step="0.1">` ;
    - autre (`text`) → `<input type="text">`.
    Badge « redémarrage requis » affiché à côté du libellé si le champ est
    dans `RESTART_REQUIRED_FIELDS`. Erreur de champ affichée juste en dessous
    du contrôle ; description longue (`field_meta[name].description`) toujours
    affichée en dernier.
  - Message permanent sous le formulaire : en cas d'erreur de validation sur
    un autre champ, les identifiants doivent être **ressaisis** — cohérent
    avec le fait que les secrets ne sont jamais retenus dans les valeurs
    ressaisies affichées (`_settings_context` les retire systématiquement de
    `display_values`, y compris pour le candidat en erreur).

### Décisions et pièges tracés

- **Pydantic → dataclasses maison, motivé par le portage Android** (décision
  majeure). `pydantic-core` (dépendance native Rust de Pydantic v2, utilisé
  jusque-là via FastAPI) n'a aucune wheel Android disponible — vérifié le
  2026-07-12 sur PyPI (toutes versions), le dépôt de wheels Chaquopy et le
  canal BeeWare (voir `docs/ANDROID_PACKAGING.md`). FastAPI dépendant
  structurellement de Pydantic v2, la solution retenue (« Path B », par
  opposition à un contournement partiel) a été de retirer les deux dépendances
  ensemble : FastAPI → Starlette pur (la base que le projet utilisait déjà à
  travers FastAPI, donc migration sans changement d'architecture serveur) et
  Pydantic → dataclasses standard + `app/settings_fields.py`, qui reproduit
  uniquement la petite surface de `pydantic.BaseModel`/`Field` réellement
  utilisée par ce projet (défauts, description, bornes `ge`/`gt`/`le`,
  validation à la construction, registre `model_fields`) — pas une
  réimplémentation générale de Pydantic. Effet recherché et obtenu : plus
  aucune extension native ne bloque le portage Android une fois ce module
  et FastAPI retirés (les extensions natives restantes du projet —
  `markupsafe`, `greenlet`, `cryptography`, `cffi` — sont couvertes par le
  dépôt de wheels Chaquopy). Contrainte de compatibilité posée par cette
  migration : **mêmes noms d'attributs et de méthodes** que la surface
  Pydantic consommée ailleurs dans le code (`Settings.model_fields`,
  `FieldInfo.annotation`/`.description`, `Settings.model_dump()`,
  `SettingsValidationError` en substitut de `pydantic.ValidationError`), pour
  ne modifier ni les gabarits Jinja ni les appelants (`settings_store`,
  `app/main.py`) au-delà du module `config.py`/`settings_fields.py`
  eux-mêmes.
- **`isinstance(True, int)` explicitement contourné** dans
  `validate_fields` : Python considère `bool` comme une sous-classe d'`int`,
  ce qui accepterait silencieusement `True`/`False` pour un champ entier (et
  réciproquement un `0`/`1` pour un champ booléen) sans ce garde-fou
  explicite — piège classique documenté en commentaire dans
  `app/settings_fields.py`.
- **`int` toléré pour un champ `float`, jamais l'inverse** : un JSON de
  réglages édité à la main écrit volontiers `4` plutôt que `4.0` pour un champ
  comme `priority_value_base` ; la valeur est convertie sur place
  (`setattr(obj, name, float(value))`) plutôt que rejetée, décision de
  confort explicitement commentée dans le code.
- **Repli keyring silencieux, dans les deux sens (lecture et écriture)** :
  toute exception levée par le backend `keyring` (absence de service
  SecretService sur Linux headless, absence totale de trousseau sur Android)
  est interceptée et traduite en repli vers le fichier de réglages en clair
  — jamais une erreur remontée à l'utilisateur au-delà d'un bandeau
  informatif. Décision cohérente avec la philosophie du reste du projet
  (`app/git_credentials.py`, client TimeTree) : aucune dépendance externe
  optionnelle ne doit jamais faire échouer une fonctionnalité cœur (ici,
  sauvegarder ses réglages).
- **Un seul champ « redémarrage requis »** (`tasks_database_path`) : tous les
  autres réglages, y compris les identifiants et le proxy, s'appliquent
  immédiatement après sauvegarde (cache `lru_cache` invalidé, proxy
  réinjecté dans l'environnement du processus) — décision motivée par
  l'architecture du moteur SQLAlchemy, initialisé une seule fois à l'import
  de `app/tasks_db.py`, jamais reconstruit à chaud.
- **Migration `.env` strictement unique, jamais destructive** : ne se
  déclenche que si `settings.json` est totalement absent (pas de fusion
  partielle possible), et ne supprime jamais le `.env` d'origine — décision
  de sécurité (ne jamais perdre la configuration d'un utilisateur venant de
  l'ancienne installation historique du dépôt, phase 14) au prix d'un fichier
  `.env` qui traîne ensuite sans effet, à charge de l'utilisateur de le
  retirer (message explicite affiché sur la page Réglages).
- **`warnings` de `settings_store.save()` non exploités par la route POST
  actuelle** (voir détail ci-dessus, § `settings_store.py`) : comportement
  observé et tracé tel quel, la page Réglages ne montre le repli trousseau
  que via le bandeau standing calculé à chaque rendu (`keyring_available()`),
  pas via un message ciblé sur la sauvegarde qui vient d'avoir lieu. Aucune
  correction apportée dans le cadre de cette tâche de spécification (lecture
  seule).
- **Retrait complet des réglages Superproductivity (phase 8)** : décision
  actée par l'utilisateur de retirer entièrement l'intégration (jugée
  injoignable depuis son réseau professionnel) plutôt que de la désactiver
  par un simple réglage — les champs `superproductivity_base_url`/
  `superproductivity_sync_enabled` ont été supprimés de `Settings` (et de
  l'ancien `.env.example`), sans code mort conservé « au cas où ». Trace
  historique uniquement (ces champs n'existent plus dans `app/config.py`
  actuel) — mentionné ici pour la bijectivité avec `SPEC_KAIROS.md`, dont ce
  document absorbe le contenu pertinent aux réglages avant sa suppression.
- **Deux chemins d'import GitLab, un seul jeu de réglages GitLab partagé**
  (phase 17) : `gitlab_assignee_username` est commun aux deux intégrations
  (cache pilotage via `pilotage_database_path`, ou import direct via
  `gitlab_url`/`gitlab_token`/`gitlab_projects`) — une seule identité GitLab
  à renseigner quelle que soit la source réellement utilisée, le cache
  pilotage primant toujours si renseigné (`gitlab_direct_configured` exclut
  explicitement le cas où `pilotage_configured` est vrai).
- **`task_types` en CSV libre plutôt qu'une liste structurée** : même patron
  que `gitlab_projects` — un simple champ texte séparé par des virgules,
  décision de simplicité (pas de plomberie de formulaire dédiée à une liste
  dynamique) assumée en commentaire dans `app/config.py`. Une tâche dont le
  type a disparu de cette liste garde sa valeur enregistrée en base ; elle
  cesse seulement d'apparaître dans le menu déroulant de la fiche.
- **`no_proxy` avec défaut non vide** (`"127.0.0.1,localhost"`), seul champ de
  la section réseau à porter une valeur par défaut autre que la chaîne
  vide — évite qu'un proxy d'entreprise fraîchement configuré casse les
  appels locaux (loopback) de l'application elle-même.
- **Sections repliées par défaut** (revue produit F-Droid/mobile, 2026-07) :
  la page comptait ~40 champs empilés sans aucun repli (`<section>` simple),
  soit ~13 600px de haut à 393px de large — remplacé par `<details>`/
  `<summary>`, patron déjà en place ailleurs dans l'app, sans introduire de
  nouvelle règle CSS (`.collapser`/`details.panel > summary` existants). Seule
  la section « Emplacement des données » (bloc informatif hors formulaire, pas
  dans la boucle `SECTIONS`) reste une `<section>` non repliable — toujours
  utile en un coup d'œil, jamais assez longue pour justifier un repli. Ouvrir
  automatiquement une section en erreur de validation évite une régression
  d'ergonomie (une erreur invisible dans une section fermée serait pire que le
  problème résolu) ; aucun `id`/`name` de champ n'a changé, la logique de
  soumission du formulaire est inchangée.

### Invariants et garde-fous

- Un secret (`gitlab_token`, `timetree_password`) n'est **jamais** présent en
  clair dans le HTML rendu par `/kairos/settings`, dans aucun cas (affichage
  normal, réaffichage après erreur de validation, quel que soit le champ en
  erreur).
- `Settings()` (tous les défauts) est toujours une configuration valide et
  fonctionnelle : aucune intégration optionnelle n'est requise pour démarrer
  Kairos.
- `settings_store.load()` ne lève **jamais** d'exception au démarrage : tout
  fichier de réglages absent, corrompu ou partiellement invalide dégrade vers
  `Settings()` par défaut plutôt que de bloquer le lancement de l'application.
- `secret_store.get_secret`/`set_secret` ne lèvent **jamais** d'exception :
  toute erreur du backend `keyring` est interceptée et traduite en repli
  fichier/valeur par défaut.
- La migration `.env` ne s'exécute **au plus une fois** par installation
  (conditionnée à l'absence de `settings.json`), et ne modifie ni ne supprime
  jamais le fichier `.env` source.
- `Settings.model_fields` reste la source unique de vérité sur les champs
  valides : toute clé absente de ce registre est ignorée silencieusement au
  chargement (fichier JSON) comme à la migration (`.env`) — aucun champ
  inconnu ne peut atteindre le constructeur `Settings(**data)`.
- Écriture du fichier de réglages toujours atomique (`*.json.tmp` +
  `os.replace`) : jamais de fichier à moitié écrit visible par une lecture
  concurrente ou après une interruption du processus.
- `KAIROS_DATA_DIR`, quand posée, prime systématiquement sur
  `platformdirs.user_data_dir` — même règle appliquée indépendamment dans
  `app/config.py::_default_tasks_database_path` et
  `app/settings_store.py::data_dir`, jamais divergente entre les deux (les
  deux lisent la même variable d'environnement au même moment de résolution).
- Toute validation de bornes (`ge`/`gt`/`le`) et de règles inter-champs
  (heures de journée, fenêtre du creux de l'après-midi) est appliquée à la
  **construction** de `Settings`, jamais après coup : impossible d'obtenir en
  mémoire une instance de `Settings` qui viole ses propres contraintes.
