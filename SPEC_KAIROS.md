# Spec : « Kairos » — dashboard de tâches natif

## Objectif

Faire de `pilotage-pleiade-gitlab` le point d'entrée unique de l'utilisateur (Corentin,
seul utilisateur de l'outil) vers tout ce qu'il doit faire, en plus du suivi
dette technique Redmine ↔ GitLab existant.

Ce sprint livre un nouvel onglet **« Kairos »** qui affiche les tâches du jour/de la
semaine, **ordonnées** en tenant compte :
- de la **priorité** de chaque tâche (champ natif, éditable — voir plus bas) ;
- de sa **deadline** ;
- des **créneaux occupés** de la journée (réunions pro saisies à la main + événements
  personnels importés de TimeTree),

pour répondre concrètement à « qu'est-ce que je fais, et dans quel ordre, sachant
qu'une réunion 13h–14h m'empêche de traiter le sujet urgent avant 14h05 ».

**Succès** = au chargement de la page, l'utilisateur voit une liste ordonnée de tâches
avec, pour chacune, à quel moment elle est réalisable compte tenu des créneaux occupés,
sans avoir à recouper mentalement calendrier + liste de tâches.

## Décisions actées (ne pas rouvrir sans en reparler avec l'utilisateur)

1. **Pas de remplacement complet de Superproductivity tout de suite.** Le gestionnaire
   de tâches natif est la cible, mais tant qu'il n'a pas de CRUD complet (ajout de
   tâche, sous-tâches, récurrence), on **synchronise en lecture seule depuis SP en
   direct** (voir § Synchronisation SP). Cette synchro est **transitoire** : à retirer
   une fois l'outil natif complet.
2. **Appel direct à l'API locale Superproductivity**, pas via le protocole MCP.
   `pilotage-pleiade-gitlab` tourne sur la **même machine** que l'app desktop
   Superproductivity (confirmé par l'utilisateur), donc joignable directement en
   `http://127.0.0.1:3876` — exactement comme le fait `scripts/superproductivity-mcp.py`
   en interne (`httpx` en GET/POST/PATCH/DELETE). Le serveur MCP existant (et son tunnel
   ngrok/cloudflared) sert uniquement à ce que **Claude Code** y accède depuis le web ;
   il n'a aucun rôle dans cette synchro service-à-service. Ne pas passer par le tunnel,
   ne pas parler le protocole MCP depuis FastAPI.
3. **La priorité est un champ natif de l'outil, pas importé de SP** (l'API locale SP
   n'expose d'ailleurs pas de champ priorité — vérifié dans `superproductivity-mcp.py` :
   seuls `title`, `projectId`, `tagIds`, `dueDay`, `notes`, `estimate` sont exposés en
   écriture). Une tâche importée de SP arrive donc avec une priorité **non renseignée**
   (`None`), que l'utilisateur fixe ensuite dans le dashboard. **Poser/changer la
   priorité fait donc partie du MVP** (mini-UI dédiée), même si le reste de l'édition
   de tâche (titre, notes, deadline…) reste hors MVP.
4. **Calendrier personnel via TimeTree** : intégration par le paquet PyPI
   [`timetree-exporter`](https://pypi.org/project/timetree-exporter/), **non-officiel et
   reverse-engineeré** (le mainteneur prévient explicitement d'un risque de panne sans
   préavis et de rate-limiting). À isoler derrière une interface stable
   (`fetch_busy_slots(start, end) -> list[TimeBlock]`) pour pouvoir le remplacer
   facilement. Authentification par email/mot de passe TimeTree via variables
   d'environnement, jamais committées. Résultat mis en cache (TTL) pour éviter le
   rate-limiting ; en cas d'échec, le dashboard se dégrade proprement (blocs manuels +
   avertissement visible), pas de crash.
5. **Réunions professionnelles** (non exportables depuis le calendrier pro) : saisie
   manuelle d'un bloc d'indisponibilité (titre, début, fin).
6. **Nouvel onglet « Kairos »**, en tête de la barre de navigation (avant
   « Liaisons »), reflétant l'ambition « point d'entrée unique ». Ne modifie pas la
   page d'accueil actuelle (`/`, onglet « Liaisons », déjà nommée `dashboard` en
   interne — ne pas confondre les deux, y compris dans le nommage des fichiers/routes).
7. **Projet = tag libre** sur une tâche (texte simple), pas de FK vers `Ticket`. La
   liaison vers les fiches Redmine/GitLab est une phase future explicitement hors
   périmètre ici.

## Périmètre MVP

**Inclus :**
- Modèle de données `Task` (titre, description, priorité, deadline, tag projet libre,
  statut, origine `native`/`superproductivity`, id externe SP) et `TimeBlock` (titre,
  début, fin, origine `manual`/`timetree`).
- Base SQLite **séparée** de celle du suivi dette technique (nouveau fichier, gitignored).
- Synchronisation en lecture seule depuis l'API locale SP (`GET /tasks`, `GET /projects`,
  `GET /tags`), upsert idempotent (`external_id` + `source="superproductivity"`).
- Import des créneaux occupés TimeTree (lecture seule, mis en cache).
- Endpoint minimal de création d'un bloc d'indisponibilité manuel (formulaire simple,
  pas d'édition/suppression riche requise pour le MVP).
- **Mini-UI de priorisation** : depuis le dashboard, changer la priorité d'une tâche
  (ex. boutons/select inline, `PATCH` sur l'unique champ priorité).
- Algorithme d'ordonnancement simple : trie par urgence (deadline proche + priorité),
  puis case dans les créneaux libres du jour ; signale explicitement les tâches dont le
  créneau réalisable est repoussé par une indisponibilité (ex. « à partir de 14h05,
  après la réunion de 13h »).
- Page « Kairos » (vue jour) + vue semaine.

**Explicitement hors MVP** (concevoir le schéma pour ne pas bloquer leur ajout, mais ne
pas les implémenter maintenant) :
- Édition complète d'une tâche (titre, description, deadline, projet) depuis l'UI.
- Création manuelle de tâche dans l'outil natif.
- Sous-tâches.
- Tâches récurrentes.
- Liaison native ↔ `Ticket` (Redmine/GitLab).
- Alertes, priorisation automatique.
- Écriture vers SP (le sync reste unidirectionnel SP → natif).

## Tech Stack

Identique au reste de `pilotage-pleiade-gitlab/` — pas de nouvelle techno structurante :
- FastAPI + Jinja2 (rendu serveur, pas de framework JS) pour la vue.
- SQLAlchemy 2 pour le nouveau modèle, dans une base SQLite séparée.
- `httpx` pour l'appel à l'API locale SP (même client que le reste du projet).
- `timetree-exporter` (nouvelle dépendance, à ajouter dans `pyproject.toml`).
- Nouveaux réglages dans `app/config.py` (`Settings`), même pattern que les réglages
  Redmine/GitLab existants (`.env`).

## Commands

Aucun changement aux commandes existantes :
```bash
cd pilotage-pleiade-gitlab
source .venv/bin/activate
pip install -e ".[dev]"       # après ajout de timetree-exporter à pyproject.toml
uvicorn app.main:app --reload # http://127.0.0.1:8000
pytest                        # tests réseau mockés (respx / mocks), aucun accès réseau réel
```

## Project Structure

```
pilotage-pleiade-gitlab/
  app/
    tasks_models.py        → modèles SQLAlchemy Task / TimeBlock (Base dédiée)
    tasks_db.py             → engine/session dédiés à la base tâches (mirror de db.py)
    clients/
      superproductivity.py  → client HTTP direct vers l'API locale SP (list_tasks, list_projects, list_tags)
    tasks_sync.py            → upsert des tâches SP → base native (lecture seule, idempotent)
    calendar/
      timetree_source.py     → fetch_busy_slots(start, end), isole timetree-exporter, cache TTL
    tasks_scheduling.py       → algorithme d'ordonnancement (tâches + créneaux occupés → ordre du jour)
    main.py                  → + route(s) GET /kairos (jour/semaine), POST priorité, POST bloc manuel
  templates/
    kairos.html          → nouvelle page (pattern proche de dashboard.html / gitlab_pilotage.html)
  tests/
    test_tasks_sync.py
    test_timetree_source.py
    test_tasks_scheduling.py
    test_kairos_route.py
```

(Noms indicatifs — l'agent d'implémentation peut ajuster tant que la séparation
"client SP / sync / source calendrier / ordonnancement / route" reste claire et
testable indépendamment.)

## Code Style

Français pour commentaires, docstrings, logs, commits (voir `CLAUDE.md` racine du
dépôt). yapf (`python/.style.yapf`, `column_limit=120`). Suivre le style déjà en place
dans `app/models.py` / `app/db.py` :

```python
class Task(TasksBase):
    """Tâche affichée dans « Kairos », native ou importée de Superproductivity."""

    __tablename__ = "task"
    __table_args__ = (
        UniqueConstraint("source", "external_id", name="uq_task_source_external_id"),
    )

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    title: Mapped[str] = mapped_column(String(512), default="")
    # None = priorité non renseignée (distinct de la priorité la plus basse).
    priority: Mapped[int | None] = mapped_column(Integer, nullable=True)
    # 'native' | 'superproductivity'
    source: Mapped[str] = mapped_column(String(32), default="native", index=True)
    external_id: Mapped[str] = mapped_column(String(64), default="")
```

## Testing Strategy

- `pytest`, tests dans `tests/`, comme l'existant (`testpaths = ["tests"]`).
- **Aucun appel réseau réel** : mocker `httpx` vers `127.0.0.1:3876` (respx, comme les
  clients Redmine/GitLab existants) et mocker `timetree-exporter` (pas d'appel TimeTree
  réel dans les tests).
- Niveaux à couvrir :
  - Upsert SP → base native : idempotence, gestion tâche modifiée/supprimée côté SP.
  - `timetree_source` : parsing du résultat, comportement en cas d'échec (dashboard ne
    doit pas planter).
  - `tasks_scheduling` : cas nominal + le cas emblématique « réunion 13h–14h repousse
    une tâche urgente à 14h05 ».
  - Route `/kairos` : rendu, changement de priorité via l'endpoint dédié.

## Boundaries

- **Toujours** : lire `app/db.py`/`app/models.py` avant de répliquer le pattern pour la
  base tâches ; garder le sync SP strictement lecture seule (aucun `POST`/`PATCH`/`DELETE`
  vers `127.0.0.1:3876`) ; garder `timetree_source` isolé derrière son interface ; écrire
  en français ; faire passer `pytest`.
- **Demander avant** : ajouter `timetree-exporter` en dépendance si son mode d'invocation
  réel (CLI only vs lib importable) impose un choix structurant imprévu ; toute
  divergence par rapport aux décisions actées ci-dessus (ex. si `timetree-exporter`
  s'avère inutilisable en pratique — revenir vers l'utilisateur plutôt que d'improviser
  un scraping alternatif) ; changer le nom/emplacement de l'onglet dans `_sidebar.html`
  au-delà de ce qui est spécifié.
- **Ne jamais** : committer des identifiants TimeTree ou toute autre variable
  sensible ; toucher aux garde-fous anti-perte Redmine/GitLab (`redmine_guard.py`,
  `gitlab_block.py`, etc.) — chantier strictement indépendant ; écrire vers l'API SP ;
  mélanger la base tâches avec la base tickets existante (`pilotage.db`).

## Success Criteria

- [ ] `/kairos` affiche les tâches du jour, triées, avec pour chacune un horaire de
      réalisation réaliste compte tenu des blocs occupés.
- [ ] Le cas « réunion 13h–14h » est démontrable : une tâche prioritaire avec deadline
      le jour même est affichée comme réalisable seulement après 14h (marge incluse),
      pas avant.
- [ ] Les tâches SP apparaissent dans le dashboard après synchro, avec une priorité
      modifiable en un clic/select, sans passer par SP.
- [ ] Un bloc d'indisponibilité manuel créé via l'endpoint apparaît dans
      l'ordonnancement du jour concerné.
- [ ] Si TimeTree est indisponible, le dashboard s'affiche quand même (tâches +
      blocs manuels) : silencieusement si l'intégration est simplement non
      configurée (identifiants absents, cas normal), avec un avertissement
      visible seulement si elle est configurée mais que l'API échoue.
- [ ] `pytest` passe sans accès réseau réel.

## Open Questions (phase 1 — résolues)

- Mode d'invocation de `timetree-exporter` : résolu — CLI en subprocess produisant un
  `.ics` parsé via `icalendar`, binaire résolu à côté de `sys.executable` (le service
  systemd n'active pas le venv).
- Fréquence du sync SP : résolu — fetch à la demande avec TTL 2 min.
- Forme de l'API locale SP : résolu — enveloppe `{"ok": true, "data": [...]}`,
  sous-tâches présentes au premier niveau avec `parentId`.

---

# Phase 2 : suivi de tâches complet + time blocking

Phase 1 (affichage + priorisation) validée en conditions réelles. Objectif de la
phase 2, acté avec l'utilisateur : faire de « Kairos » son **outil de suivi de
tâches et de time blocking principal**, avec un dashboard actionnable — et **sortir
de Superproductivity**.

## Décisions actées phase 2 (ne pas rouvrir sans en reparler)

1. **CRUD natif complet** : créer, éditer (titre, description, deadline, projet,
   priorité, durée estimée), marquer fait/rouvrir, supprimer une tâche — le tout
   depuis le dashboard, sans changer de page.
2. **Time blocking mixte auto + épinglage** : l'algorithme propose un ordonnancement
   automatique ; l'utilisateur peut **épingler** une tâche sur un créneau précis
   (`pinned_start`) et l'auto remplit autour. Pas de drag & drop (l'app reste sans
   framework JS) : l'épinglage se fait par un petit formulaire heure.
3. **Durée estimée par tâche** (`estimated_minutes`, nullable → repli sur
   `default_task_duration_minutes`). L'import SP convertit `timeEstimate` (ms) en
   minutes.
4. **Sous-tâches** : `parent_id` auto-référent sur `task`. L'import SP ne filtre
   plus les sous-tâches : il les rattache à leur mère. Seules les **feuilles**
   (tâches sans sous-tâches à faire) sont planifiées ; la mère affiche l'avancement
   (n/m faites).
5. **Récurrence** simple : `recurrence` ∈ {'', daily, weekdays, weekly, monthly}.
   Modèle « recréation à la complétion » : terminer une récurrente marque
   l'occurrence faite ET crée la suivante (deadline avancée selon la règle).
6. **Dashboard actionnable, les trois axes** :
   - agir sans quitter la page (fait en un clic, création rapide inline, « décaler à
     demain », épingler) ;
   - **timeline verticale** de la journée (agenda heure par heure : créneaux occupés
     + blocs de travail proposés/épinglés), rendue côté serveur (offsets calculés en
     Python, positionnement CSS) ;
   - **progression du jour** : faites/prévues, temps requis vs temps disponible
     restant, alerte visible si la journée déborde.
7. **Sortie de Superproductivity dès le CRUD livré** :
   - réglage `superproductivity_sync_enabled` (défaut `true` pour compatibilité) ;
     à `false`, plus aucun appel SP ni bandeau ;
   - action explicite « Adopter les tâches SP » : convertit `source` →
     `'native'` (les tâches deviennent pleinement éditables et ne seront plus jamais
     archivées par une synchro). Refusée tant que la synchro est active (sinon la
     synchro suivante recréerait des doublons).
8. **Migration sans perte** : `tasks.db` contient des données réelles. Toute
   nouvelle colonne passe par le hook de migration légère `ADD COLUMN` (même
   mécanisme que `db.py`/`_MIGRATION_COLUMNS`). Aucune suppression/recréation de
   table.

## Hors périmètre phase 2 (phases futures)

- Liaison tâche ↔ `Ticket` Redmine/GitLab (le « point d'entrée unique » complet).
- Alertes/notifications, priorisation automatique.
- Chronométrage/pomodoro, drag & drop.

## Success Criteria phase 2

- [ ] Créer une tâche en une ligne depuis le dashboard ; l'éditer intégralement ;
      la marquer faite en un clic ; la décaler à demain en un clic.
- [ ] Épingler une tâche à 9h30 : elle apparaît à 9h30 dans la timeline, l'auto
      s'organise autour (et un chevauchement avec une réunion est signalé).
- [ ] La timeline du jour montre, heure par heure, réunions + blocs de travail, avec
      durées réelles des tâches.
- [ ] L'en-tête du jour affiche : n faites / n prévues, temps requis vs disponible,
      et une alerte si ça déborde.
- [ ] Terminer une tâche « quotidienne » crée l'occurrence du lendemain.
- [ ] Les sous-tâches SP apparaissent sous leur mère ; terminer toutes les
      sous-tâches se voit sur la mère.
- [ ] `SUPERPRODUCTIVITY_SYNC_ENABLED=false` + « Adopter les tâches SP » : plus
      aucun appel réseau SP, tâches ex-SP éditables comme des natives.
- [ ] Une base `tasks.db` de phase 1 est migrée automatiquement au démarrage, sans
      perte (tâches et priorités intactes).
- [ ] `pytest` passe sans réseau réel.

---

# Phase 3 : deep work + dépendances (livrée — résumé rétroactif)

Implémentée et poussée sur la branche ; ce résumé comble la spec pour qu'elle reste
le reflet fidèle du code (voir `tasks/plan.md` § Phase 3 pour le détail complet).

- **Dépendances** « bloqué par » entre tâches natives (`TaskDependency`) : blocage
  **transitif** (levée automatique quand le bloqueur passe fait), cycles détectés et
  neutralisés (Kahn, repris de `dependency_rules.py`), **urgence dérivée** — un
  bloqueur hérite de l'urgence la plus forte de ce qu'il bloque (chemin critique),
  calculée au rendu, **sans jamais modifier `Task.priority`**. Une tâche bloquée sort
  du planning (section « Bloquées »).
- **Suivi du temps réel** (`WorkSession`) : chrono par tâche, une seule session
  ouverte à la fois, minuteur vivant côté client, réel vs estimé, total du jour.
- **Blocs deep-work protégés** (`TimeBlock.kind='deepwork'`) : fenêtre réservée à une
  seule tâche non fragmentée ; les autres tâches auto la contournent ; compte comme
  temps disponible (pas occupé).

---

# Phase 4 : intégration GitLab, planification vs échéance, récurrence calendaire

## Contexte

Le tri automatique fonctionne, mais trois manques concrets ressentis à l'usage :
(1) les fiches GitLab qui me sont assignées vivent dans un autre onglet, pas dans mon
plan du jour ; (2) je confonds aujourd'hui « échéance » et « quand je compte m'y
mettre » — une tâche due mardi que je veux traiter lundi n'a pas de bonne case ; (3)
mes obligations récurrentes calées sur une **date du calendrier** (le 23 de chaque
mois) ne sont pas représentables par le modèle de récurrence actuel, qui ne
recrée l'occurrence qu'**à la complétion** de la précédente.

## Décisions actées

1. **Intégration GitLab mutualisée, lecture seule, périmètre `GITLAB_PROJECT`.**
   Aucun nouvel appel API : la synchronisation lit le cache déjà entretenu par
   l'onglet « Pilotage GitLab » (`GitLabIssueCache`, alimenté par son bouton
   Rafraîchir existant) et filtre les issues **ouvertes** dont l'assigné correspond à
   un nouveau réglage `GITLAB_ASSIGNEE_USERNAME` (vide = fonctionnalité désactivée,
   dégradation cohérente avec le reste de l'app). Comme c'est une lecture locale
   (pas de réseau), pas de TTL nécessaire : resynchronisé à chaque chargement de
   `/kairos`.
   - Import **indépendant** : nouvelles lignes `Task(source='gitlab', external_id=iid)`,
     même patron que la synchro Superproductivity (`tasks_sync.py`) — upsert
     idempotent, priorité native jamais écrasée, disparition/fermeture → `archived`.
   - `Task.deadline` alimentée depuis `GitLabIssueCache.due_date` si présente (c'est
     une échéance objective, pas une décision de planification personnelle).
   - **Explicitement pas de lien** vers `Ticket`/dette technique : deux bases
     séparées, comme SP. Une vraie fusion attendrait la liaison générale « point
     d'entrée unique » (déjà notée hors périmètre en phase 2).

2. **Nouveau champ `Task.scheduled_date`** (date, nullable) : « quand je compte
   traiter cette tâche », distinct de `deadline` (l'échéance réelle, imposée de
   l'extérieur). Les deux coexistent et ne se remplacent pas.

3. **La date programmée pilote la présence dans l'agenda du jour ; l'échéance reste
   le garde-fou qui ne laisse jamais rien filer.** Règle d'éligibilité pour le rendu
   du jour `day` (remplace « tout le backlog todo » acté en phase 1 — révision
   explicite de cette décision) :
   - une tâche est **affichée** dans l'agenda de `day` sauf si elle a une
     `scheduled_date` **future** ET **aucune échéance imminente** (`deadline is None`
     ou `deadline > day`) ;
   - autrement dit : une tâche en retard (`scheduled_date <= day` ou
     `deadline <= day`), sans date programmée, ou avec échéance qui approche,
     reste **toujours visible** — jamais silencieusement reportée.
   - une tâche masquée aujourd'hui (programmée plus tard, échéance lointaine) doit
     malgré tout être **visible quelque part** (nouvelle section « Programmées plus
     tard » sur la page), pour tenir l'exigence « aucune fiche jamais perdue » — pas
     de trou noir.
   - Ordre (buckets d'urgence, extension de `_urgency_bucket`) : 0. retard (deadline
     ou date programmée `<= day`) → 1. priorité maximale → 2. programmée pour
     aujourd'hui → 3. échéance cette semaine → 4. le reste.

4. **Récurrence calendaire (« le 23 de chaque mois »), indépendante de la
   complétion.** Modèle distinct de la récurrence existante (`daily`/`weekdays`/
   `weekly`/`monthly`, qui avance depuis la complétion) : une nouvelle valeur
   `recurrence='monthly_on_day'` + `Task.recurrence_day_of_month` (1-31, borné en fin
   de mois comme la logique `monthly` existante). Génération **par date**, pas par
   complétion : à chaque chargement de la page, une fonction s'assure que
   l'occurrence du mois courant existe (créée si absente), **sans jamais toucher**
   une occurrence antérieure encore ouverte (une échéance du mois dernier non traitée
   reste visible, en retard — jamais silencieusement remplacée). Anti-doublon : un
   nouveau champ `Task.recurrence_period` (ex. `"2026-07"`) posé à la création de
   l'occurrence, vérifié avant toute nouvelle génération pour la même série.
   - **Décalage jours ouvrés — sens « arrière ».** Si la date calculée (le 23) tombe
     un week-end, l'occurrence est posée au **jour ouvré précédent** (23 dimanche →
     vendredi 21). Utilise `is_workday`/nouveau helper `previous_business_day` dans
     `app/workdays.py` (à ajouter, symétrique de `add_business_days` déjà présent).

5. **Décalage « demain » (snooze) — sens « avant » : saute le week-end en avant.**
   Le bouton existant (`snooze_task`) doit sauter au jour ouvré suivant plutôt que
   d'atterrir un samedi/dimanche : si on est vendredi et qu'on décale, la tâche va à
   lundi. Réutilise `add_business_days(start, 1, holidays)` (déjà dans
   `app/workdays.py`). Portée du décalage jours ouvrés (actée) : **uniquement** la
   récurrence calendaire et le snooze automatique — une date saisie à la main (deadline
   ou date programmée) n'est **jamais** corrigée d'office, elle reflète un choix
   explicite de l'utilisateur.

6. **Tri automatique robuste — critère testable, pas un nouveau mécanisme.**
   « Une fiche n'est jamais perdue » se traduit en un **invariant vérifié par test** :
   pour tout ensemble de tâches `todo`/non-archivées, chacune apparaît dans exactement
   une des sections rendues (planifiée, sans créneau, bloquée, programmée plus tard,
   mère en cours) — jamais absente de toutes. Un test de propriété (génération de
   combinaisons aléatoires : bloquée + épinglée + deep-work + récurrente + programmée
   plus tard + sous-tâche) vérifie cette conservation à chaque changement de
   l'algorithme d'ordonnancement.

## Hors périmètre phase 4 (roadmap, non conçu ici)

- **Dashboard de statistiques** (temps réalisé/à faire, nombre de tâches, dérive par
  projet). Attendra un historique de données réel une fois cette phase en usage.
- **Estimation agile en points de Fibonacci**, en complément (pas en remplacement) de
  `estimated_minutes` — objectif déclaré : forcer le découpage des tâches trop
  grosses. Implique une conversion points → minutes quelque part pour que
  l'ordonnancement continue de fonctionner ; à trancher en /plan de cette phase future.
- **Typologie de tâches + estimation par machine learning** du temps réel de
  réalisation, à partir de l'historique `WorkSession` (déjà collecté depuis la phase
  3 — bon point, la donnée s'accumule déjà). Nécessite un volume d'historique
  suffisant avant d'être pertinent.
- Mode focus plein écran, alertes de dérive proactives, export/journal hebdomadaire
  (suggestions complémentaires, non demandées explicitement).

## Success Criteria phase 4

- [x] `GITLAB_ASSIGNEE_USERNAME` renseigné : les issues ouvertes qui m'assignent sur
      `GITLAB_PROJECT` apparaissent comme tâches `source='gitlab'` dans « Kairos »,
      sans appel réseau supplémentaire (relecture du cache existant).
- [x] Une tâche avec `scheduled_date` = lundi et `deadline` = mardi n'apparaît pas
      dans l'agenda de vendredi, mais apparaît lundi ; si non traitée, reste visible
      mardi (retard) plutôt que de disparaître.
- [x] Une tâche récurrente « le 23 » génère son occurrence du mois automatiquement,
      décalée au vendredi si le 23 tombe un week-end ; une occurrence du mois
      précédent non traitée n'est jamais écrasée par la nouvelle.
- [x] Décaler une tâche un vendredi via « demain » l'envoie lundi.
- [x] Une date saisie à la main un samedi n'est jamais recorrigée automatiquement.
- [x] Test de propriété : sur des combinaisons aléatoires de tâches, chacune reste
      visible dans exactement une section — jamais perdue.
- [x] `pytest` passe sans réseau réel ; migration testée sur une base phase 3 peuplée.

Vérifié manuellement (uvicorn, base éphémère) le 2026-07-03 : issue GitLab assignée
seedée dans `GitLabIssueCache` apparue en tâche `source='gitlab'` sans appel réseau ;
tâche `scheduled_date`=lundi/`deadline`=mardi absente de l'agenda du vendredi et
visible dans « Programmées plus tard » ; récurrence « le 23 du mois » posée via le
panneau d'édition et occurrence générée au chargement de la page ; snooze un vendredi
→ lundi (ou jour ouvré suivant si férié, vérifié par test) ; date saisie à la main un
samedi jamais recorrigée (testé côté route). Suite complète : 499 tests, zéro réseau
réel.

## Décisions complémentaires (actées après revue)

- **Jours fériés inclus dans le décalage** (récurrence calendaire + snooze) : le
  décalage jours ouvrés utilise `settings.holiday_set` (déjà FR + `extra_holidays` via
  `workdays.build_holidays`), pas seulement les week-ends. `previous_business_day`
  (à ajouter) et `add_business_days` (existant) prennent déjà un paramètre
  `holidays` optionnel — on le branche ici avec `settings.holiday_set`.
  **Extensibilité congés** : `settings.holiday_set` retourne un `frozenset[date]`
  déjà pensé comme un ensemble ouvert (FR + dates additionnelles) ; une future
  fonctionnalité « congés » n'aura qu'à enrichir cet ensemble (nouvelle source de
  dates fusionnée dans le même frozenset) — aucun redesign de
  `previous_business_day`/`add_business_days` requis quand elle arrivera.
- **Récurrence calendaire sur la table `Task` existante**, pas de table
  `RecurringTaskDef` séparée — plus simple à gérer, cohérent avec le modèle de
  récurrence déjà en place (`recurrence`/`recurrence_day_of_month`/
  `recurrence_period` restent des colonnes de `task`).

## Open Questions (phase 4 — résolues)

- Nom et position de la section « Programmées plus tard » : résolu — repliée
  (`<details class="card">`), sous « Bloquées » et au-dessus de « Tâches mères en
  cours ».

---

# Phase 5 : points de Fibonacci, typologie de tâches, ergonomie

## Contexte

Phase 4 livrée et vérifiée en usage réel. Son § « Hors périmètre » différait
explicitement deux besoins « pas encore assez d'historique pour analyser » : les
points de Fibonacci et une typologie de tâches, tous deux comme **préparation** de
futures analyses (pas les analyses elles-mêmes — toujours hors périmètre, à revoir
« la semaine prochaine » une fois l'historique constitué). En parallèle, une passe
d'ergonomie sur l'onglet, qui a accumulé six sections de liste empilées (planifiée,
sans créneau, bloquées, programmées plus tard, mères en cours, fait) sans hiérarchie
visuelle entre une tâche en retard et une tâche normale — l'ordre du tri porte toute
la charge de signal, rien à l'œil.

## Décisions actées

1. **Deux nouveaux champs, purement des métadonnées de préparation — aucun impact
   sur l'ordonnancement.**
   - `Task.fibonacci_points` (entier, nullable) : échelle fixe `1, 2, 3, 5, 8, 13, 21`
     (suite classique, pas de variante ½/0/100 — inutile tant qu'aucune analyse ne
     l'exploite). Saisie via un `<select>` restreint à cette échelle (même idiome que
     la priorité 0-4), pas de champ libre.
   - `Task.task_type` (chaîne, défaut `""` = non classé) : valeurs fixes —
     `dev` (Développement), `revue_code` (Revue de code), `reunion` (Réunion),
     `documentation` (Documentation), `administratif` (Administratif),
     `veille` (Veille/formation), `pilotage` (Pilotage/dette technique). Whitelist
     validée côté route (même patron que `recurrence`), pas de table séparée.
   - **Explicitement pas de conversion points → minutes.** `estimated_minutes` reste
     saisi indépendamment et continue seul à piloter `build_day_schedule` — aucune
     fonction pure supplémentaire, aucun réglage `minutes_par_point`. Une conversion
     n'a de sens qu'au moment où une vraie analyse ou un ordonnancement assisté
     l'exploitera ; la construire maintenant sans donnée pour la calibrer serait
     spéculatif (cohérent avec le refus déjà acté en phase 4 du dashboard de stats et
     de l'estimation ML tant que l'historique est insuffisant).
   - Champs ajoutés **uniquement au panneau d'édition complet**, pas à la création
     rapide (qui reste volontairement un formulaire court : titre + priorité +
     échéance + programmée + projet + durée). Poser le type/les points est un geste de
     second temps, pas du flux de capture rapide.

2. **Passe ergonomie — hiérarchie visuelle, périmètre laissé à l'appréciation de
   l'implémentation (carte blanche actée avec l'utilisateur), avec pour angle
   imposé : mieux distinguer l'urgent du secondaire, moins de texte gris uniforme.**
   Changements concrets (pas une refonte) :
   - **Accent de couleur par bucket d'urgence** sur chaque `.kairos-item` de la
     liste planifiée (bordure gauche) : reprend le même code couleur que les badges
     existants (`bad`=retard/bucket 0, `warn`=priorité max/bucket 1, `info`=programmée
     aujourd'hui/bucket 2, neutre au-delà) — **aucun nouveau calcul** : le bucket est
     déjà produit par `_urgency_bucket`/`urgency_key` (phase 1/3), seulement exposé au
     template (`bucket_of`, calculé dans `_render_kairos` à côté de
     `effective_urgency`, déjà calculé pour le badge « chemin critique »).
   - **Badge de priorité visible** à côté du titre (et non plus seulement dans le
     `<select>`, qui reste pour l'édition) quand `priority` est renseignée — signal à
     l'œil sans lire le nombre dans un menu déroulant.
   - **Section « Fait » repliée par défaut** (`<details class="card">`, même idiome
     que « Programmées plus tard ») : c'est de l'historique, pas de l'actionnable ;
     elle n'a pas besoin de occuper l'écran en permanence. Les autres sections
     actionnables (sans créneau, bloquées, mères en cours) restent dépliées.
   - Badges dédiés pour les deux nouveaux champs (`task_type`, `fibonacci_points`)
     dans `task_meta` : un style neutre propre (pas une des couleurs sémantiques
     bad/warn/ok déjà utilisées pour l'urgence, pour ne pas brouiller la lecture).
   - Pas de changement de comportement (aucune section supprimée, aucun champ
     renommé) : uniquement de la présentation.

3. **Fusionner l'épinglage (heure fixe) dans le formulaire d'édition — un seul
   enregistrement pour tout.** Aujourd'hui, éditer les infos d'une tâche
   (`POST .../edit`) et l'épingler à une heure (`POST .../pin`) sont deux
   soumissions séparées dans le panneau d'édition (deux formulaires, deux clics).
   Le champ heure (`pin_time`, pré-rempli si déjà épinglée) rejoint le formulaire
   `mj-edit-form` : un seul bouton **Enregistrer** met à jour les infos **et**
   l'épinglage en un seul aller-retour. Champ vide = désépinglée (remplace le
   bouton « Désépingler » séparé) ; heure invalide = ignorée silencieusement (même
   tolérance que l'existant). Les routes `POST .../pin` et `.../unpin` disparaissent
   (plus aucun appelant) — pas de doublon de chemin pour la même action.
   **Explicitement hors périmètre de cette fusion** : l'ajout groupé de plusieurs
   sous-tâches en une fois (« ajout compilé ») — reste un ajout un par un depuis le
   panneau d'édition, jugé trop complexe à combiner proprement ici ; à reprendre
   dans un second temps.

## Hors périmètre phase 5 (roadmap, toujours différé)

- **Ajout compilé de sous-tâches** (plusieurs en une fois) : reporté à une phase
  ultérieure — reste un ajout un par un pour l'instant.
- **Dashboard de statistiques** et **estimation ML** : toujours différés, en
  attente d'un historique réel (inchangé depuis la phase 4).
- **Conversion points → minutes** et tout ordonnancement assisté par la typologie
  (ex. grouper les tâches du même type) : à concevoir une fois les données
  accumulées, pas maintenant.
- Filtrage/tri de la liste par `task_type` : pas demandé, à envisager avec le futur
  dashboard de stats plutôt qu'isolément.

## Success Criteria phase 5

- [x] Poser des points de Fibonacci et un type sur une tâche depuis le panneau
      d'édition ; les deux apparaissent en badge sur la tâche dans toutes les
      sections de la page.
- [x] `estimated_minutes` et l'ordonnancement du jour sont rigoureusement inchangés
      par la présence de points de Fibonacci (aucune tâche ne change d'heure de
      passage suite à cet ajout).
- [x] Une tâche en retard (bucket 0) et une tâche de priorité maximale (bucket 1) se
      distinguent visuellement (couleur) d'une tâche normale dans la liste du jour,
      sans avoir à lire le texte. (Étendu à toutes les sections actionnables —
      sans créneau, programmées plus tard, mères en cours — pas seulement la liste
      planifiée, pour rester cohérent quelle que soit l'heure de la journée.)
- [x] La section « Fait » est repliée par défaut ; les tâches y restent accessibles
      en un clic (dépliage), rien n'est supprimé de la page.
- [x] Migration testée sur une base phase 4 peuplée (tâches, dépendances, sessions
      de travail, blocs deep-work, `scheduled_date`/récurrence calendaire) sans
      perte.
- [x] Éditer une tâche (titre, priorité, etc.) et lui poser une heure fixe en même
      temps, en un seul clic « Enregistrer » ; vider le champ heure la désépingle.
      Les anciennes routes `.../pin` et `.../unpin` disparaissent sans laisser de
      lien mort dans la page.
- [x] `pytest` passe sans réseau réel.

Vérifié manuellement (uvicorn, base éphémère) le 2026-07-03 : poser type + points +
heure fixe en un seul « Enregistrer » — badges visibles partout où la tâche apparaît ;
tâche en retard (bucket 0) et tâche normale (bucket 4) distinguées par bordure
colorée dans « sans créneau » (les tests ont révélé une dépendance à l'heure murale
réelle de `datetime.now()` dans le scheduler — corrigée en étendant la bordure à
toutes les sections, pas seulement la liste planifiée) ; section « Fait » repliée par
défaut, dépliable. Suite complète : 498 tests, zéro réseau réel.

---

# Phase 6 : édition consolidée (sous-tâches + bloqueurs), liaison fiche, GitLab multi-projet

## Contexte

Trois demandes indépendantes après un usage réel de la phase 5. (1) Le panneau
d'édition a accumulé plusieurs formulaires à soumission séparée (infos, ajout de
sous-tâche, ajout/retrait de bloqueur) : l'utilisateur veut **un seul bouton
Enregistrer** qui fait tout, y compris créer plusieurs sous-tâches d'un coup
(« ajout compilé », différé depuis la phase 5). (2) Relier une tâche « Kairos »
à une fiche de dette technique existante (`Ticket`, Pléiade et/ou GitLab) — la
vraie liaison différée depuis la phase 2, mais en version **manuelle** : `Ticket`
n'a pas de notion d'assigné unique (suivi collaboratif à plusieurs relecteurs), donc
pas d'auto-import possible depuis Pléiade comme pour GitLab. (3) L'auto-import
GitLab assigné (phase 4, W3) ne couvre que `GITLAB_PROJECT` : élargi à **tous les
projets déjà en cache**, sans appel réseau supplémentaire (toujours mutualisé).

## Décisions actées

1. **Formulaire d'édition consolidé — sous-tâches en lot + bloqueurs, un seul
   `POST /edit`.**
   - Nouveau champ `new_subtasks` (`<textarea>`, un titre par ligne) dans
     `mj-edit-form` : chaque ligne non vide devient une sous-tâche native
     (`Task(parent_id=task.id, source='native')`), créée dans le même
     enregistrement. Titres vides/blancs ignorés silencieusement (cohérent avec le
     nettoyage déjà fait sur `title` ailleurs).
   - Les bloqueurs passent d'un couple de mini-formulaires à soumission immédiate
     (ajouter un par un, retirer un par un) à une **liste à cases à cocher**
     (`blocker_ids`, valeurs multiples) dans le même formulaire : l'ensemble soumis
     est **l'état cible complet** — la route calcule le diff (bloqueurs à retirer =
     présents en base mais plus cochés ; à ajouter = cochés mais absents), réutilise
     `would_create_cycle` (`app/tasks_dependencies.py`, déjà utilisé par
     `add_task_dependency`) pour **ignorer silencieusement** toute arête qui
     créerait un cycle plutôt que d'échouer toute la sauvegarde.
   - Les routes `POST .../deps` et `.../deps/{blocker_id}/delete` disparaissent
     (même logique que la fusion de l'épinglage en phase 5, Z2) : plus aucun
     appelant une fois le formulaire consolidé en place.
   - La création rapide de sous-tâche unique (formulaire séparé actuel,
     `POST /kairos/tasks` avec `parent_id`) reste **en plus**, pas supprimée :
     un ajout ponctuel ne justifie pas d'ouvrir tout le panneau d'édition. Les deux
     chemins coexistent.

2. **Liaison manuelle vers une fiche `Ticket` (Pléiade et/ou GitLab).**
   - Nouveau champ `Task.linked_ticket_id` (entier, nullable) : référence locale
     (sans contrainte FK cross-base — les deux bases SQLite restent des fichiers
     séparés, cohérent avec le style `parent_id`/`blocker_id`) vers `Ticket.id`
     (base `pilotage.db`). Choisi dans un `<select>` du panneau d'édition, alimenté
     par `session` (déjà lue par la route `/kairos`, comme la synchro GitLab
     mutualisée). Purement une **référence en lecture** — aucune écriture vers
     Redmine/GitLab, aucun impact sur l'ordonnancement.
   - Affiché en badge partout où la tâche apparaît (`task_meta`), avec le sujet
     Pléiade tronqué ; lien cliquable vers `gitlab_web_url` du ticket s'il existe.
   - Validé côté route contre les tickets existants (le `<select>` n'offre que des
     valeurs valides ; une valeur qui ne résout à aucun `Ticket` est ignorée,
     retombe à `None` — même prudence que la validation de `task_type`).

3. **Auto-import GitLab assigné élargi à tous les projets en cache.**
   - `sync_assigned_gitlab_tasks` perd son paramètre `project` : filtre uniquement
     sur `state == 'opened'` et l'assigné, sur **tout** `GitLabIssueCache`, quel que
     soit le projet — toujours zéro appel réseau (lecture pure du cache déjà
     entretenu par l'onglet Pilotage GitLab, qui peut rafraîchir plusieurs projets).
   - **Correction de fond nécessaire à l'élargissement** : `external_id` était
     jusqu'ici le seul `iid` GitLab (ex. `"42"`), unique **par projet** mais pas
     entre projets — deux projets différents peuvent chacun avoir une issue `!42`.
     Le nouvel `external_id` devient **qualifié par projet**
     (`f"{project}#{iid}"`), pour rester unique sous la contrainte
     `UniqueConstraint(source, external_id)`.
   - **Migration de données sans perte** pour les tâches déjà synchronisées sous
     l'ancien format (installations phase 4 existantes) : au premier passage sous
     le nouveau code, toute tâche `source='gitlab'` dont l'`external_id` est
     l'ancien format brut (correspond à un `iid` de `GitLabIssueCache` pour son
     propre `project_tag`) est **rebaptisée en place** vers le nouveau format
     qualifié, plutôt que traitée comme disparue (archivée) et recréée à neuf —
     préserve la priorité posée et l'historique de temps déjà accumulés dessus.

## Hors périmètre phase 6 (roadmap, toujours différé)

- Écriture vers Redmine/GitLab depuis « Kairos » (la liaison reste une
  référence en lecture seule).
- Dashboard de statistiques, estimation ML, conversion points → minutes :
  toujours différés (inchangé depuis les phases 4/5).
- Auto-import depuis Pléiade (pas de notion d'assigné unique sur `Ticket`).

## Success Criteria phase 6

- [x] Éditer une tâche : poser plusieurs sous-tâches d'un coup (une par ligne) ET
      cocher/décocher des bloqueurs, en un seul « Enregistrer » — aucune soumission
      séparée nécessaire.
- [x] Cocher un bloqueur qui créerait un cycle est ignoré silencieusement, le reste
      de l'enregistrement (infos, sous-tâches, autres bloqueurs) réussit quand même.
- [x] Lier une tâche à une fiche `Ticket` existante depuis le panneau d'édition ;
      le badge apparaît partout où la tâche est listée ; aucune écriture vers
      Redmine/GitLab n'est déclenchée.
- [x] Deux issues GitLab de projets différents partageant le même `iid` créent
      deux tâches distinctes, jamais une collision.
- [x] Migration testée sur une base phase 5 peuplée **contenant déjà des tâches
      GitLab synchronisées sous l'ancien format d'`external_id`** : rebaptisées en
      place, priorité et historique de temps intacts, aucun doublon créé.
- [x] `pytest` passe sans réseau réel.

Vérifié manuellement (uvicorn, base éphémère) le 2026-07-03 : un seul « Enregistrer »
a créé 2 sous-tâches, posé un bloqueur et changé la priorité en même temps ; la case
à cocher du bloqueur est bien re-cochée au rechargement de la page ; deux fiches
GitLab de projets différents partageant `iid=99` ont donné deux tâches distinctes
(`external_id` qualifié `projet#iid`) ; une tâche déjà synchronisée sous l'ancien
format a été rebaptisée en place (priorité et titre à jour, pas de doublon) ; une
fiche Ticket liée manuellement affiche son badge cliquable (lien GitLab) partout où
la tâche apparaît. Suite complète : 516 tests, zéro réseau réel. Un bug (réarchivage
à tort d'une tâche venant d'être rebaptisée, dû à une clé de dict périmée) a été
détecté et corrigé pendant l'implémentation, avant tout commit final.

---

# Analyse (juillet 2026) : manques et automatismes pour le deep work

Analyse demandée par l'utilisateur après la phase 6, pour identifier ce qui manque
à « Kairos » pour être un outil de deep work réellement efficient — avant de
décider quoi construire ensuite (phase 7).

## Manques identifiés

1. Les points de Fibonacci et la typologie (phase 5) sont de pures métadonnées :
   l'objectif déclaré en phase 4 (« forcer le découpage des tâches trop grosses »)
   n'a jamais été implémenté.
2. Aucune détection des tâches qui traînent — une tâche en retard depuis 1 jour et
   une tâche en retard depuis 3 semaines sont traitées pareil (bucket 0).
3. Aucun garde-fou sur la priorité : rien n'empêche d'avoir de nombreuses tâches à
   priorité maximale simultanément, ce qui dilue le sens du signal.
4. La liaison manuelle vers une fiche `Ticket` (phase 6, X2) est statique et peut
   se désynchroniser silencieusement si le ticket est clôturé pendant que la tâche
   reste `todo`.
5. Le suivi du temps réel (`WorkSession`) n'est exploité nulle part au-delà d'une
   tâche isolée — pas d'agrégat par type, alors que la donnée existe déjà.
6. Outil 100 % pull (aucun rappel proactif, pas de digest).
7. Aucune valeur par défaut intelligente à la création (durée, priorité) selon le
   type de tâche.

## Automatismes possibles dès maintenant (sans attendre l'historique)

- Nudge de découpage sur les grosses tâches (points Fibo élevés).
- Détection des tâches qui traînent.
- Garde-fou de surcharge de priorité maximale.
- Vérification de cohérence du ticket lié (clôturé côté externe mais tâche encore
  `todo`).
- Agrégat hebdomadaire simple du temps réel par type (pas le dashboard de stats
  complet — juste une synthèse immédiatement exploitable).
- Défauts par type de tâche (durée/priorité).

## Ce qui attend toujours l'historique réel (inchangé)

Dashboard de statistiques complet, **estimation ML des durées** (remplacera à terme
la mémorisation de durées par défaut par type — décision actée : ne pas construire
un système de défauts « en dur » maintenant, laisser le futur modèle ML s'en
charger), conversion points → minutes calibrée.

---

# Phase 7 : garde-fous d'usage, temps réel exploité, robustesse GitLab

## Contexte

Suite de l'analyse ci-dessus. Décisions actées avec l'utilisateur (clarifications
tranchées avant `/plan`) :

1. **« Liaison dynamique des tickets » = vérifier/durcir l'existant, pas construire
   du neuf.** Le désassignement d'une issue GitLab archive déjà la tâche
   correspondante côté « Kairos » (`sync_assigned_gitlab_tasks`, phase 4/6) —
   mais seul le scénario « issue fermée » est testé, pas « issue réassignée à
   quelqu'un d'autre en restant ouverte ». La liaison **manuelle** vers un `Ticket`
   (X2) reste hors périmètre de ce point (elle n'a explicitement pas de notion
   d'assignation).
2. **Automatisme de priorité = un garde-fou de surcharge, pas un défaut
   automatique à la création.** Pas de calcul de priorité déduit de l'échéance à
   la création (écarté) : un bandeau avertit quand trop de tâches `todo` sont déjà
   à priorité maximale (0 ou 1, même définition que le bucket d'urgence 1), avant
   d'en ajouter une nouvelle à ce niveau. Purement informatif, jamais bloquant.
3. **« Bannière de surcharge » et « garde-fou priorité » ne font qu'un** — pas de
   bannière distincte pour la charge de la semaine (le débordement du jour,
   `schedule.stats.overflow_minutes`, existe déjà depuis la phase 2 et suffit).
4. **Mémorisation automatique des durées par type : explicitement différée au
   futur modèle ML**, pas construite ici en dur (cohérent avec le refus déjà acté
   du dashboard de stats/ML tant que l'historique est insuffisant).
5. **Détection des tâches qui traînent** : purement un signal visuel
   supplémentaire (badge), n'affecte jamais l'ordre de tri ni les buckets
   d'urgence existants — pas de nouvelle logique de scheduling.
6. **Suivi du temps réel exploité** : en creusant l'existant, un vrai bug a été
   repéré — le badge « temps travaillé aujourd'hui » de l'en-tête
   (`spent_total_str`) additionne en réalité **toutes** les sessions de travail
   jamais enregistrées, pas seulement celles du jour (`sessions` n'est jamais
   filtré par date dans `_render_kairos`). Corrigé dans cette phase, avec en
   plus une ventilation par type de tâche pour le temps réel du jour.
7. **Agrégat hebdomadaire** : ajouté à la vue semaine existante (`view=week`),
   synthèse compacte du temps réel par type sur la semaine — pas de graphique,
   juste des totaux, en s'appuyant sur les données déjà collectées.

## Réutilisation confirmée

- **Bucket de priorité maximale** déjà défini dans `_urgency_bucket`
  (`app/tasks_scheduling.py`) : `priority is not None and priority <= 1` — même
  définition reprise pour le garde-fou de surcharge (pas une nouvelle règle).
- **Module pur `app/tasks_time.py`** (phase 3) : `spent_minutes_by_task`,
  `total_minutes`, `session_minutes` — le bug de scope (jour vs all-time) se
  corrige en filtrant les `WorkSession` **avant** de les passer à ces fonctions
  existantes, pas en les réécrivant.
- **Patron de module pur testé en isolation** (`tasks_dependencies.py`,
  `tasks_recurrence.py`, `tasks_time.py`) — même approche pour la détection des
  tâches qui traînent (nouveau module `app/tasks_staleness.py`).
- **`sync_assigned_gitlab_tasks`** (phase 4/6) : logique déjà correcte pour le
  scénario de réassignation (un `assignee_username` absent de `assignee_list`
  exclut l'issue de `assigned`, donc de `seen_external_ids`, donc archivée) — il
  manque seulement le test qui le prouve.
- **Idiome de réglage** (`meeting_buffer_minutes`, `default_task_duration_minutes`
  dans `app/config.py`) pour les nouveaux seuils (`stale_overdue_days`,
  `stale_untouched_days`, `priority_overload_threshold`).

## Hors périmètre phase 7 (roadmap, toujours différé)

- Nudge de découpage basé sur les points Fibo élevés — noté dans l'analyse,
  **non retenu pour cette phase** (l'utilisateur a choisi de concentrer la phase 7
  sur les 5 points ci-dessus ; à reprendre dans une phase future si besoin).
- Défauts par type de tâche (durée/priorité) — explicitement laissé au futur
  modèle ML.
- Digest/rappel proactif (outil 100 % pull) — non demandé.
- Dashboard de statistiques complet, estimation ML — inchangé.

## Success Criteria phase 7

- [x] Une tâche en retard ou sans échéance non retouchée depuis longtemps affiche
      un signal distinct (« traîne depuis N jours »), sans changer sa position
      dans le tri.
- [x] Dépasser le seuil de tâches à priorité maximale affiche un bandeau
      d'avertissement ; en dessous du seuil, rien ne s'affiche.
- [x] Une nouvelle sous-tâche/tâche assignée puis réassignée à quelqu'un d'autre
      sur une issue restée ouverte disparaît du dashboard au prochain chargement
      (test explicite, en plus du cas déjà couvert « issue fermée »).
- [x] Le badge « temps travaillé aujourd'hui » ne compte plus que les sessions du
      jour affiché (vérifié par un test qui aurait échoué avant la correction).
- [x] La vue semaine affiche une synthèse du temps réel par type de tâche pour la
      semaine, sans graphique.
- [x] `pytest` passe sans réseau réel.

Vérifié manuellement (uvicorn, base éphémère) le 2026-07-04 : tâche en retard
depuis 33 jours affiche « traîne depuis 33 j » ; 6 tâches à priorité 0 déclenchent
le bandeau « 6 tâches sont déjà à priorité maximale » ; le badge « temps travaillé
aujourd'hui » ne dérive plus vers l'historique complet. Le test de régression du
bug (`test_today_time_total_excludes_sessions_from_other_days`) a été vérifié
manuellement pour échouer avant le correctif et passer après, avant tout commit
final. Suite complète : 549 tests, zéro réseau réel.

---

# Phase 8 : retrait de Superproductivity

## Contexte

À l'usage réel, l'intégration Superproductivity (T3/T4/U7) s'avère injoignable
depuis le réseau professionnel de l'utilisateur (l'API REST locale de l'app
desktop n'est tout simplement pas accessible dans cet environnement réseau) —
alors qu'elle fonctionnait sur le réseau personnel où le développement/les tests
manuels avaient eu lieu. Décision actée par l'utilisateur : **retirer entièrement
la fonctionnalité dès maintenant**, pas seulement la désactiver par réglage. Les
vérifications de récurrence que la synchro SP automatisait redeviennent
manuelles et régulières, à la charge de l'utilisateur — aucun remplacement
automatique n'est demandé.

## Décision actée

- Suppression complète du code : `app/clients/superproductivity.py`,
  `app/tasks_sync.py`, et leurs tests dédiés — pas de désactivation par réglage,
  pas de code mort conservé « au cas où ».
- Les deux réglages `superproductivity_base_url`/`superproductivity_sync_enabled`
  disparaissent de `Settings` et de `.env.example`. Toute référence résiduelle
  (bandeaux, route `/kairos/adopt-sp`, docstrings) retirée avec.
- **Aucune tâche perdue** : une tâche déjà synchronisée depuis SP
  (`source='superproductivity'`) devient automatiquement `source='native'` au
  prochain démarrage (migration idempotente dans
  `tasks_db.py::_ensure_tasks_columns`), `external_id` conservé comme trace
  d'origine. C'est exactement la conversion que l'ancien bouton « Adopter les
  tâches SP » effectuait manuellement — désormais automatique et inconditionnelle
  puisque plus aucune synchro ne peut recréer/dupliquer ces tâches.
- `SPEC_KAIROS.md`/`tasks/plan.md`/`tasks/todo.md` ne sont **pas** réécrits
  rétroactivement : les phases 1 (T3/T4) et 2 (U7) gardent leur récit historique
  tel quel, cohérent avec la convention déjà suivie dans tout ce document.

## Hors périmètre phase 8

- Aucun automatisme de remplacement pour les récurrences (vérification manuelle
  assumée par l'utilisateur).
- Le pont MCP `scripts/superproductivity-mcp.py` (racine du dépôt dotfiles,
  Claude Code ↔ Superproductivity) est un outil totalement différent, non
  concerné par ce retrait.

## Success Criteria phase 8

- [x] Plus aucune référence à Superproductivity dans le code applicatif, les
      templates, `.env.example` ou `README.md`.
- [x] Une base `tasks.db` existante contenant des tâches `source='superproductivity'`
      migre silencieusement vers `source='native'` au démarrage, sans perte ni
      duplication, `external_id` conservé (`external_id="sp-42"` par exemple).
- [x] `pytest` passe sans réseau réel après le retrait.

---

# Phase 9 : ordonnancement WSJF (score valeur/effort)

## Contexte

L'utilisateur a mis en place deux métadonnées **décorrélées** : une priorité (0-4) et
une estimation en points de Fibonacci. Jusqu'ici, l'ordre de traitement automatique
était **lexicographique par paliers** (`_urgency_bucket` : en retard → priorité max →
programmée aujourd'hui → cette semaine → reste, puis priorité en départage), et les
points Fibonacci **n'entraient pas** dans le tri (purement informatifs). Deux limites
ressenties : (1) un tri lexicographique ne sait pas **arbitrer** (une tâche à peine moins
urgente mais bien moins coûteuse ne passe jamais devant) ; (2) l'effort est ignoré, alors
que c'est la seule mesure de coût disponible. L'utilisateur veut un système
« scientifique pragmatique » qui priorise automatiquement dans le bon ordre.

## Fondement scientifique

Problème de **scheduling** classique aux résultats prouvés : pour maximiser la valeur
livrée par unité de temps, ordonner par **valeur / effort** décroissant (règle de Smith
1956 ; incarnation Agile = **WSJF**, *Weighted Shortest Job First*, Reinertsen). Pour ne
rater aucune échéance, ordonner par échéance la plus proche (règle de Jackson / EDD). Le
modèle unifie les deux : on trie par **coût du retard / effort**, où le coût du retard
inclut la valeur (priorité) **et** une criticité temporelle (échéance qui approche). C'est
précisément pourquoi garder priorité (valeur) et Fibonacci (effort) **décorrélés** est
correct : ce sont les deux axes indépendants, et le bon premier ordre est leur ratio.

## Décisions actées (avec l'utilisateur)

1. **Structure hybride** (pas un score 100 % continu) : « en retard » (échéance ou
   `scheduled_date` atteinte) et « bloqué » restent des **paliers durs** qui passent
   toujours devant ; **à l'intérieur**, l'ordre suit le score WSJF décroissant. Garantit
   qu'une échéance du jour n'est jamais « oubliée » au profit d'un quick win.
2. **Valeur exponentielle** de la priorité : `base ** (4 - priorité)`, `base=2` par défaut
   (P0=16, P1=8, P2=4, P3=2, P4=1 ; sans priorité = `base ** -1` < P4). « Critique » n'est
   pas juste « un cran au-dessus d'important ».
3. **Effort = points de Fibonacci** (dénominateur), repli sur `estimated_minutes` ramenées
   à l'échelle (≈ 1 pt / 30 min, borné 1-21), puis sur `default_fibonacci_points=3`
   (neutre). Sans effort renseigné, le tri **dégrade proprement** vers l'ordre de priorité.
4. **Criticité temporelle** : rampe linéaire 0 → `urgency_peak=8` sur les
   `urgency_horizon_days=14` derniers jours avant l'échéance. Le dépassement est géré par le
   palier dur, pas par la rampe.
5. **Score transparent** : affiché sur chaque tâche (badge `mj-score` + détail
   valeur/urgence/effort au survol) — on voit *pourquoi* cet ordre. La bordure colorée
   (`urgency_bucket` 0-4) reste un signal **visuel** de pression temporelle, volontairement
   **découplé** de l'ordre WSJF ; le badge « chemin critique » signale à part le relèvement
   par dépendance.

## Formule

```
score = ( valeur(priorité) + criticité(échéance, date programmée) ) / effort(points)
```

Clé de tri : `(0 si en retard sinon 1, -score, priorité, échéance, id)` — plus petite =
plus urgente, ce qui laisse la machinerie d'**urgence dérivée** des dépendances
(`derived_urgency`, phase 3) se composer **sans modification** : un bloqueur hérite du min
= le plus urgent de ce qu'il bloque (chemin critique en logique WSJF). Choix documenté : la
propagation hérite du **rang** effectif, pas d'un coût du retard re-divisé par l'effort
propre du bloqueur — simplification cohérente avec la phase 3, non destructive.

## Portée / non-objectifs

- Les **poids** (base de valeur, horizon/pic d'urgence) sont des `Settings` ajustables à
  l'usage — pas dérivables des premiers principes (fonction d'utilité personnelle). La
  calibration empirique (comparer `estimated_minutes` réel vs points Fibonacci sur les
  `WorkSession` déjà collectées) est laissée à une itération future.
- Aucun changement du **placement** (time blocking, marges, deep-work, épinglage) : seul
  l'**ordre** change, via le point d'insertion unique `auto = sorted(..., key=_key)`.

## Success Criteria phase 9

- [x] Les points de Fibonacci entrent dans l'ordre : à valeur égale, une tâche N× plus
      grosse a un score N× plus faible.
- [x] La valeur d'un cran de priorité est exponentielle (P0 vaut `base`× une P1).
- [x] Une tâche en retard passe devant une tâche non en retard à score bien plus élevé
      (palier dur conservé).
- [x] Une échéance qui approche relève le score ; au-delà de l'horizon, elle ne pèse pas.
- [x] Sans effort renseigné, l'ordre dégrade proprement vers l'ordre de priorité.
- [x] Le score est affiché sur chaque tâche (transparence) ; l'urgence dérivée des
      dépendances continue de fonctionner.
- [x] Exemple travaillé vérifié de bout en bout (B P1×1 → C P2×2 due J+2 → A P0×8 →
      D P0×21), en test isolé et en rendu réel (uvicorn).
- [x] `pytest` passe sans réseau réel.

---

# Phase 10 : dashboard de statistiques

## Contexte

Le « dashboard de statistiques complet » était différé depuis la phase 5 (« pas encore
assez d'historique ») : toutes les métadonnées et le suivi du temps réel ont été
collectés depuis dans cette intention. L'utilisateur donne carte blanche pour un
dashboard d'**indicateurs constructifs** — actionnables, pas des « vanity metrics ».

## Décisions de conception

1. **Indicateurs actionnables uniquement.** Chaque bloc répond à un « et donc ? » :
   - **KPIs** : tâches terminées (fenêtre), temps réel tracké, délai médian de
     complétion, taux de respect des échéances.
   - **Débit hebdomadaire** : tâches + points de Fibonacci terminés par semaine
     (vélocité), barres zéro-remplies sur un axe continu.
   - **Calibration de l'estimation** (le plus constructif, la boucle empirique promise
     en phase 9) : temps réel médian **par palier de points** (si deux paliers se
     rejoignent, l'échelle ne discrimine pas) + **biais estimé vs réel** (ratio
     agrégé réel/estimé).
   - **Répartition du temps réel par type** + **focus** (durée moyenne de session =
     proxy de fragmentation).
   - **Flux & backlog** : WIP, âge médian du backlog, en retard, qui traînent.
   - **Complétude des métadonnées** : part des tâches todo qualifiées (points /
     estimation / type) — les stats de calibration en dépendent, donc incite à remplir.
2. **Honnêteté statistique** : toute cellule à faible échantillon expose son effectif
   `n` ; en dessous de `MIN_SAMPLE` (3), l'agrégat reste affiché mais marqué « peu
   fiable » (jamais de tendance inventée à partir de trois points).
3. **Fenêtre configurable** (`stats_window_weeks=8`) pour les indicateurs « récents » ;
   les indicateurs de fond (calibration, complétude, backlog courant) portent sur tout
   l'historique.
4. **Date de complétion approximée par `updated_at`** (pas d'horodatage dédié) —
   convention déjà retenue pour la section « Fait » du jour, documentée.
5. **Aucune dépendance nouvelle** : médiane et agrégats en Python pur (projet sobre) ;
   rendu 100 % serveur avec les composants de data-viz **déjà** définis par la charte
   graphique (`.stat`, `.barrow/.track/.fill`, `.adv`, `.panel`, `.grid2`) — aucune
   librairie de graphes, aucun JavaScript.

## Réutilisation

- **`app/tasks_time.py`** : `spent_minutes_by_task`, `spent_minutes_by_type`,
  `session_minutes`, `sessions_in_range` — réutilisés tels quels.
- **`app/tasks_staleness.py::days_stale`** et **`tasks_scheduling.urgency_bucket`** :
  définitions canoniques de « qui traîne » et « en retard », réutilisées (pas de
  redéfinition divergente).
- **Charte graphique** (`.claude/skills/msi-design`) : panneaux analytiques en
  `.panel > h2` nu (pas de filet sarcelle, réservé au premier niveau), chiffres en mono,
  couleur réservée aux états. Lue avant toute production d'UI (règle du dépôt).

## Portée / non-objectifs

- Module **pur** `app/tasks_stats.py` (aucune I/O) ; la route `/kairos/stats` charge
  tâches + sessions et délègue tout le calcul. Page **lecture seule**, aucune synchro ni
  appel réseau (contrairement à la vue jour/semaine).
- Pas de graphiques temporels fins, pas d'export, pas de ML (estimation prédictive
  toujours différée). Ce dashboard **prépare** cette itération future en montrant la
  qualité de calibration réelle.

## Success Criteria phase 10

- [x] Une page `/kairos/stats` (lien « Statistiques » dans la barre d'actions)
      affiche les six blocs d'indicateurs, ou un état vide explicite sans données.
- [x] Calibration : temps réel médian par palier de Fibonacci, effectif `n` affiché,
      marquage « peu fiable » sous le seuil.
- [x] Biais d'estimation calculé comme ratio agrégé réel/estimé sur les tâches à la
      fois estimées et chronométrées ; absent si aucune tâche éligible.
- [x] Débit hebdomadaire zéro-rempli ; temps réel ventilé par type ; flux (WIP, âge,
      retard, traîne) ; complétude des métadonnées en %.
- [x] Tout en logique pure testée en isolation + tests de route (état vide et peuplé).
- [x] Rendu conforme à la charte (composants existants, aucune librairie, aucun JS,
      aucun emoji), vérifié en rendu réel (uvicorn).
- [x] `pytest` passe sans réseau réel.

---

# Phase 11 : chrono — timeline réelle + alertes navigateur

## Contexte

Le bouton de chrono comptait le temps mais restait « muet » : pas de rappel, et le temps
réel n'était pas visible sur la timeline. L'utilisateur veut des alertes et une timeline,
et demande si des **motifs navigateur** (API) le permettent. Réponse : oui, trois
mécanismes, dont un contraint par le contexte réseau.

## Décisions actées (avec l'utilisateur)

1. **Trois déclencheurs d'alerte retenus** : dépassement de l'estimé, **chrono oublié**
   (tourne depuis plus de `timer_idle_alert_minutes`, défaut 180), **rappel de pause**
   (focus continu > `pomodoro_focus_minutes`, défaut 50). Le pomodoro, écarté en phase 3,
   est réintroduit **ici à la demande explicite** de l'utilisateur, en simple rappel léger
   (pas de mode focus plein écran).
2. **Contexte d'accès = `127.0.0.1`** (confirmé) → l'API `Notification` est disponible
   (contexte sécurisé). On investit sur les notifications bureau, avec **dégradation
   propre** si un jour l'accès se fait via l'IP en http (API bloquée) : repli sur le titre
   d'onglet vivant + un bandeau in-page.
3. **Timeline réelle livrée dans tous les cas** (rendu serveur, aucune API navigateur) :
   les sessions chronométrées du jour s'affichent en **rail « réel »** dans la gouttière
   gauche de la timeline existante — le réel à côté du planifié, sans collision.

## Motifs navigateur employés

- **Titre d'onglet vivant** (`document.title`) : compteur visible même en arrière-plan.
  Universel, aucune permission.
- **`Notification` API** (opt-in par clic — une permission ne s'obtient que sur geste
  utilisateur) : notification système par déclencheur, **anti-spam** (un seuil déjà
  franchi au chargement est neutralisé ; seule une transition franchie page ouverte
  notifie ; `tag` par type). Détection `window.isSecureContext` → bouton désactivé avec
  message si indisponible.
- **Repli in-page** (`.banner.warning` inséré en tête) : joue **toujours**, même sans
  notification autorisée — rien n'est silencieux.

Aucune dépendance, aucun framework : script vanilla dans le `{% block scripts %}`
existant, même esprit que le minuteur vivant et le repli de sidebar.

## Réutilisation / corrections

- **`TimelineEntry`** et le clamping de `build_timeline` : la nouvelle fonction pure
  `session_timeline_entries` (conversion UTC→local des `WorkSession`, bornage à la fenêtre
  de travail) réutilise la même structure et le même style.
- **Correctif d'ergonomie découvert au passage** : le badge de chrono (`time_spent`)
  n'était rendu que dans la liste **planifiée** ; une tâche en cours reléguée « sans
  créneau » (soir, journée pleine) perdait son minuteur — et aurait perdu ses alertes.
  `time_spent` est désormais appelé aussi dans la liste des non planifiées.

## Success Criteria phase 11

- [x] Les sessions chronométrées du jour apparaissent en rail « réel » sur la timeline.
- [x] Le titre de l'onglet affiche le temps qui tourne quand un chrono est actif.
- [x] Un bouton opt-in demande la permission de notifier ; état reflété (actives /
      bloquées / indisponibles hors contexte sécurisé).
- [x] Les trois alertes se déclenchent au franchissement (dépassement de l'estimé,
      chrono oublié, rappel de pause), sans re-spam à chaque navigation, avec repli
      in-page si les notifications ne sont pas autorisées.
- [x] Le minuteur (et donc les alertes) s'affiche aussi quand la tâche en cours est
      « sans créneau ».
- [x] Fonction pure `session_timeline_entries` testée en isolation + tests de route ;
      `pytest` passe sans réseau réel.

---

# Phase 12 : « À traiter » (inbox GTD) + indication TimeTree sur plusieurs jours

## Contexte

Deux demandes indépendantes. (1) L'utilisateur remplit peu les points de Fibonacci
(champ récent, phase 5/9) alors qu'ils pilotent désormais le score WSJF (effort au
dénominateur) : il veut se forcer à les renseigner, en s'inspirant de la méthode GTD
(*Getting Things Done*) — une tâche non « clarifiée » vit dans un inbox visible tant
qu'elle ne l'est pas. (2) Un vrai bug : dans TimeTree, un événement « sur une période »
(plusieurs jours, avec des horaires réels — ex. un déplacement) s'affichait comme un
obstacle occupant la **journée entière** sur **chaque** jour couvert, à cause du
clamping de l'horaire réel (non borné par jour) dans `build_timeline`/
`build_day_schedule`. Or l'agenda sert justement à repérer les événements du **soir**
pour éviter de terminer des tâches trop tard : un déplacement de 3 jours ne devait
jamais neutraliser toute la planification de ces 3 jours.

## Décisions actées (avec l'utilisateur)

### « À traiter »

1. **Champs requis pour sortir de l'inbox : priorité ET points de Fibonacci** (pas l'un
   ou l'autre) — les deux axes décorrélés qui nourrissent le score WSJF (valeur/effort).
2. **S'applique à toute tâche**, native ou importée (GitLab assigné) : ni la synchro
   GitLab (`tasks_gitlab_sync.py`) ni aucune autre source n'écrit jamais ces deux champs,
   donc le mécanisme est uniforme sans code spécifique par source.
3. **Précédence** : la clarification prime sur tout le reste — une tâche non qualifiée
   **et** bloquée par une dépendance apparaît en « À traiter », pas en « Bloquées »
   (on ne peut pas organiser ce qu'on n'a pas encore clarifié). Une tâche non qualifiée
   **et** épinglée à une heure fixe reste aussi en « À traiter » : l'épinglage ne
   contourne pas la clarification.
4. **Exemptées** : les tâches **mères** avec au moins une fille ouverte (un conteneur
   n'est jamais une unité de travail directement exécutable — seules ses filles le
   sont, cohérent avec le reste du scheduler).
5. **Non repliée, en tête de page** (avant la carte de progression) : le but est de la
   rendre impossible à ignorer, pas de la ranger dans un `<details>` comme
   « Programmées plus tard » ou « Fait ».
6. **Renforcé par un choix déjà en place (phase 5)** : le formulaire de création rapide
   ne demande pas les points de Fibonacci (seule l'édition les propose) — une tâche
   fraîchement créée atterrit donc *toujours* en « À traiter » jusqu'à ce que
   l'utilisateur ouvre l'édition et qualifie la tâche. Aucun changement de ce
   formulaire n'était nécessaire : la contrainte existait déjà, il manquait juste la
   mise en scène (l'inbox visible) pour en faire un vrai forçage.

**Conséquence assumée sur les données existantes** : toute tâche déjà en base sans
points de Fibonacci (la quasi-totalité, champ récent et jusqu'ici informatif) bascule
en « À traiter » dès le déploiement. C'est l'effet recherché d'un inbox GTD — tout le
non-traité remonte d'un coup — mais un vrai changement d'expérience du jour au
lendemain, à anticiper.

### Indication TimeTree sur plusieurs jours

1. Un événement est considéré « sur une période » si `start.date() != end.date()`
   (indépendamment du flag `all_day`, qui gère déjà le cas journée entière). Ces deux
   catégories sont désormais traitées **de la même façon** : simple indication (puce
   datée), jamais un `TimeBlock` obstacle.
2. **Portée assumée** : aucun découpage fin du premier/dernier jour d'un événement
   « sur une période » (ex. horaires réels de départ/retour) — décision volontairement
   simple, alignée sur la demande explicite (« qu'ils n'apparaissent que comme
   indication »), sans complexité non demandée. Si un besoin de granularité apparaît à
   l'usage, à traiter dans une phase ultérieure.
3. Renommage `all_day_events` → `indication_events` (et libellé « Journée : » →
   « Indications : ») pour refléter que la puce couvre désormais deux cas, pas un seul.

## Réutilisation / nettoyage

- **`BusySlot.covers(day)`** (déjà existant) réutilisé tel quel pour les deux catégories
  d'indication (journée entière et période).
- **Dette technique retirée au passage** : `_fetch_busy_blocks` calculait un
  `timetree_blocks` local jamais utilisé par ses deux appelants (morte depuis son
  écriture) — supprimé plutôt que laissé à côté du code touché.

## Success Criteria phase 12

- [x] Une tâche sans priorité ni points de Fibonacci apparaît en « À traiter », jamais
      dans l'agenda planifié/sans créneau/plus tard.
- [x] Renseigner les deux champs (via l'édition) fait sortir la tâche de l'inbox et
      entrer dans le tri WSJF normal.
- [x] Une tâche bloquée ET non qualifiée apparaît en « À traiter », pas en « Bloquées ».
- [x] Une tâche épinglée mais non qualifiée reste en « À traiter », jamais posée sur
      l'agenda.
- [x] Une tâche mère à filles ouvertes n'est jamais reléguée en « À traiter ».
- [x] Un événement TimeTree sur plusieurs jours (horaires réels) n'obstrue plus la
      journée entière sur les jours couverts ; les tâches restent planifiables
      normalement ; l'événement apparaît en indication datée.
- [x] Vérifié en rendu réel (uvicorn) : tâche non qualifiée visible en tête de page
      avec le badge du champ manquant ; tâche qualifiée absente de l'inbox et bien
      planifiée.
- [x] `pytest` passe sans réseau réel (553 tests).

---

# Phase 13 : blocs récurrents + menu de sélection unifié pour les bloqueurs

## Contexte

Deux demandes indépendantes. (1) Les blocs manuels (occupé/deep-work) n'existaient
qu'en ponctuel : l'utilisateur veut pouvoir déclarer un créneau qui se répète (un bloc
déjeuner tous les jours, un bloc deep-work chaque mardi matin), comme le fait déjà
`Task.recurrence` pour les tâches. (2) Le sélecteur de bloqueurs (« Bloquée par »,
liste de cases à cocher depuis la phase 6) doit reprendre le **même menu de
sélection** que « Fiche liée » — un `<select>`.

## Décisions actées

1. **Les blocs n'ont pas de statut « fait »** (contrairement aux tâches) : le modèle
   « recréation à la complétion » de `tasks_recurrence.py` ne s'applique donc pas tel
   quel. Choix : le bloc stocké est un **modèle unique** (heure de début/fin
   canonique, premier jour) ; les occurrences futures sont **projetées à la volée**
   pour la plage affichée (jour/semaine), jamais persistées — aucune ligne par
   occurrence, aucun risque de dérive entre modèle et occurrences déjà générées.
2. **Trois règles** (sous-ensemble de celles des tâches, suffisant pour les exemples
   donnés — un déjeuner quotidien, un deep-work hebdomadaire un jour donné) :
   `daily`, `weekdays` (lundi-vendredi, même simplicité que `Task.recurrence`, pas de
   prise en compte des jours fériés), `weekly` (même jour de la semaine que le modèle).
   Pas de `monthly` pour les blocs : non demandé, moins pertinent pour un créneau
   récurrent de type agenda.
3. **Jamais de rattrapage rétroactif** : une occurrence n'est jamais générée avant la
   date d'origine du modèle (même principe que la récurrence des tâches).
4. **Orthogonal à `kind`** : un bloc récurrent peut être `busy` ou `deepwork`
   indifféremment (« deep-work chaque mardi » implique les deux à la fois).
5. **Sélecteur de bloqueurs** : remplacement de la liste de cases à cocher par un
   `<select name="blocker_ids" multiple>` — même widget (et même style) que le
   `<select name="linked_ticket_id">` de « Fiche liée ». Aucun changement côté route :
   `blocker_ids: list[str] = Form([])` reçoit un `<select multiple>` exactement comme
   une liste de cases à cocher (POST HTML identique).

## Réutilisation / nettoyage

- **Patron de whitelist** identique à `Task.recurrence` (`edit_task`) : valeur hors
  liste → ignorée silencieusement, jamais stockée telle quelle.
- **Patron de migration légère** (`_TASKS_MIGRATION_COLUMNS["time_block"]`, déjà
  utilisé pour `kind` en phase 3) : `recurrence` ajoutée de la même façon.
- **Objets transitoires** : les occurrences projetées réutilisent directement la
  classe `TimeBlock` (jamais `session.add`) — même geste que les blocs TimeTree déjà
  synthétisés en mémoire dans `_render_kairos` (aucune nouvelle structure).
- **Dette technique retirée au passage** : CSS `.mj-deps`/`.mj-deps .badge form`,
  mortes après le remplacement du bloc de cases à cocher (`.mj-check`, utilisée
  ailleurs — pour le bloc deep-work —, est conservée).

## Success Criteria phase 13

- [x] Un bloc quotidien créé aujourd'hui apparaît aussi les jours suivants, à la même
      heure, sans qu'aucune ligne supplémentaire ne soit stockée en base.
- [x] Un bloc hebdomadaire n'apparaît que le même jour de la semaine que son origine.
- [x] Un bloc « jours ouvrés » saute les week-ends.
- [x] Aucun bloc ne recule avant sa date d'origine.
- [x] Un bloc ponctuel (sans récurrence) est inchangé : aucun effet de bord.
- [x] Le sélecteur de bloqueurs est un `<select multiple>`, plus une liste de cases à
      cocher ; le comportement de sélection/désélection est inchangé côté route.
- [x] Migration testée sur une base pré-phase-13 peuplée (`time_block.kind` déjà
      présent, `recurrence` absente) : colonne ajoutée, donnée existante intacte.
- [x] Vérifié en rendu réel (uvicorn) : bloc deep-work hebdomadaire créé un mardi,
      visible deux semaines plus tard ; menu de bloqueurs rendu en `<select multiple>`.
- [x] `pytest` passe sans réseau réel (564 tests).

---

# Phase 14 : extraction en outil autonome

## Contexte

Des collègues veulent utiliser « Kairos ». Or il vivait comme un onglet de
`pilotage-pleiade-gitlab`, qui embarque tout l'outillage Redmine/GitLab (clients HTTP,
WeasyPrint, openpyxl, priorisation collaborative…) sans intérêt pour eux, et une seule
base/`.env`/service partagés. Décision : en faire un **outil autonome** au même rang que
pilotage — dossier `kairos/` à la racine du dépôt, `pyproject.toml`, `.env`, tests,
README et service systemd **propres**, port 8001 (pilotage garde 8000, les deux
coexistent sur un poste).

## Décisions actées

1. **Sens de la dépendance inversé et rendu optionnel.** Avant, la route lisait
   directement les modèles pilotage (`GitLabIssueCache`, `Ticket`) via la session
   `pilotage.db`. Désormais l'accès passe par un **seam unique**, `app/pilotage_link.py`,
   activé seulement si `PILOTAGE_DATABASE_PATH` est renseigné :
   - projections **lecture seule** minimales (`CachedGitLabIssue`, `LinkedTicket`) —
     seules les colonnes réellement lues sont mappées, métadonnées `PilotageBase`
     dédiées, **jamais** de `create_all` ni d'écriture sur la base pilotage ;
   - la dépendance FastAPI `get_pilotage_session` rend **`None`** si le chemin est vide
     ou le fichier absent → import GitLab assigné et liaison « Fiche liée » disparaissent
     proprement de l'interface (cas normal d'un collègue sans pilotage).
   Pilotage, symétriquement, **n'a plus aucune dépendance** vers les tâches (modules,
   templates, tests, réglages et section README retirés ; l'onglet a disparu de la
   barre latérale). Sa suite reste verte (308 tests).
2. **Config repartie de zéro** (`app/config.py`) : ne restent que les réglages utiles à
   « Kairos » (base tâches, TimeTree, ordonnancement/WSJF, stats, alertes, fériés) +
   les deux réglages d'intégration optionnelle. Plus de Redmine/GitLab/partage/TLS/
   intervenants. **Tout est optionnel** : l'outil démarre sans aucun `.env`.
3. **Base/layout/marque propres** : `main.py` autonome (lifespan → `init_tasks_db`
   seul, `/` redirige vers `/kairos`), `base.html` dédié (sidebar à deux entrées
   Kairos / Statistiques, sans les actions de synchro pilotage), `favicon`/`style`/
   icônes copiés. Aucune régression fonctionnelle : **toutes** les fonctionnalités des
   phases 1-13 sont présentes (270 tests portés, verts).
4. **Déploiement parallèle** : `deploy/kairos.service` (port 8001,
   `EnvironmentFile=-.env.proxy` pour le proxy d'entreprise), cibles makefile
   `kairos` / `kairos-test` / `kairos-service[-uninstall]`, `make all` et
   `make new-pc` installent désormais les **deux** apps.

## Réutilisation / nettoyage

- Les modules **purs** (`tasks_scheduling`, `tasks_dependencies`, `tasks_recurrence`,
  `tasks_time`, `tasks_staleness`, `tasks_stats`, `workdays`) et le seam TimeTree ont
  été déplacés **tels quels** (`git mv`) — aucune logique métier retouchée, d'où la
  reprise directe des tests.
- `tasks_gitlab_sync.py` : seul changement, `GitLabIssueCache` → `CachedGitLabIssue`
  (import depuis `pilotage_link`) ; la logique de sync (upsert, archivage,
  non-écrasement priorité/temps) est inchangée.
- Dépendances retirées du `pyproject.toml` de « Kairos » : httpx (clients Redmine/
  GitLab), weasyprint, openpyxl, markdown, respx — inutiles ici.

## Success Criteria phase 14

- [x] `kairos/` démarre et fonctionne **sans aucun `.env`** ni base pilotage
      (toutes les fonctionnalités hors import GitLab/liaison fiche).
- [x] Avec `PILOTAGE_DATABASE_PATH` renseigné : les issues GitLab assignées
      apparaissent, la liaison « Fiche liée » fonctionne — vérifié en rendu réel.
- [x] « Kairos » n'écrit jamais dans la base pilotage (projections lecture seule,
      pas de `create_all`).
- [x] `pilotage-pleiade-gitlab` n'a plus aucune référence aux tâches (import propre,
      suite verte) ; l'onglet a disparu de sa barre latérale.
- [x] Deux venvs, deux `.env`, deux services systemd (ports 8000/8001), cibles
      makefile séparées ; `make all`/`make new-pc` installent les deux.
- [x] Suites vertes : kairos (270 tests), pilotage (308 tests) — aucun réseau réel.

---

# Phase 15 : nom — « Kairos » (nom de code 14h55)

## Contexte

L'outil, autonome depuis la phase 14 et destiné à des collègues, avait besoin d'un vrai
nom (« Ma journée » décrivait une vue, pas le produit).

## Décision

- **Nom : Kairos** (καιρός) — en grec, *le moment opportun*, l'instant juste où agir
  (par opposition à *Chronos*, le temps qui défile). C'est précisément le métier de
  l'outil : poser chaque tâche au bon créneau (le fameux « 14h05 »).
- **Nom de code : 14h55** — le creux post-déjeuner, l'heure statistiquement la moins
  productive de la journée (*post-lunch dip*). L'outil qui vise le bon moment, dont le
  nom de code est le pire : clin d'œil assumé, en écho au « 14h05 » emblématique.

## Portée du renommage

- **Renommé** : dossier `ma-journee/` → `kairos/` ; titre FastAPI, marque et gabarits
  (`base.html`, `kairos.html`, `kairos_stats.html`) ; **URLs** `/ma-journee*` → `/kairos*` ;
  cibles makefile (`majournee*` → `kairos*`) et service systemd (`kairos.service`) ;
  `pyproject` (`name = "kairos"`), logger, README/SPEC (ce fichier : `SPEC_KAIROS.md`) ;
  références croisées dans `pilotage-pleiade-gitlab/README.md` et le `CLAUDE.md` racine.
- **Conservé volontairement** (plomberie interne, aucun bénéfice utilisateur à churner) :
  les noms de modules Python `tasks_*.py`, les classes CSS `mj-*`, les noms de variables
  d'environnement (`TASKS_DATABASE_PATH`…). Le libellé de l'onglet vue-jour devient
  « Aujourd'hui » (la marque « Kairos » est déjà en tête de la barre latérale).

## Success Criteria phase 15

- [x] Titre, marque, URLs, cibles make et service portent « Kairos » ; port 8001 inchangé.
- [x] `pytest` reste vert (270 tests) après renommage, sans réseau réel.
- [x] Références croisées à jour (pilotage README, CLAUDE.md racine).
- [x] Le nom de code 14h55 est consigné (README « Pourquoi Kairos ? », docstring d'en-tête).

---

# Phase 16 : édition et suppression des créneaux manuels

## Contexte

Les blocs manuels (réunion / deep-work) pouvaient être **créés** mais ni édités ni
supprimés — il fallait recréer pour corriger une heure ou un titre. L'utilisateur veut
pouvoir les **éditer**, y compris leur **récurrence** (déjà supportée à la création
depuis la phase 13, mais pas modifiable ensuite).

## Décisions actées

1. **Édition sur la ligne réelle en base**, pas sur les projections. Les créneaux
   récurrents sont stockés comme un **modèle unique** (phase 13) ; l'édition/suppression
   porte donc sur ce modèle, c'est-à-dire sur **toutes ses occurrences** — le seul
   comportement cohérent (il n'existe aucune occurrence persistée à éditer isolément).
   Signalé dans l'UI (badge de récurrence + confirmation « toutes ses occurrences »).
2. **Liste « Créneaux du jour »** dans le panneau « Ajouter un créneau » : remplace le
   résumé en une ligne (lecture seule) par une liste des créneaux **pertinents pour le
   jour affiché** — les ponctuels du jour + les modèles récurrents dont une occurrence
   tombe ce jour-là (calculé via `expand_recurring_blocks`, donc aligné sur ce que
   montre la timeline). Chaque entrée a un panneau d'édition repliable
   (`<details class="mj-edit">`, même widget que l'édition des tâches) + un bouton
   Supprimer.
3. **Deux routes** : `POST /kairos/blocks/{id}/edit` (titre, horaires, deep-work,
   récurrence — même whitelist et même validation « fin > début » que la création) et
   `POST /kairos/blocks/{id}/delete`. Garde-fou : seuls les blocs `source='manual'` sont
   touchés (les créneaux TimeTree sont transitoires, jamais en base).

## Réutilisation

- Widget d'édition (`mj-edit`/`mj-edit-body`/`mj-edit-form`/`mj-edit-row`) et
  `expand_recurring_blocks` réutilisés tels quels — aucune nouvelle brique.

## Success Criteria phase 16

- [x] Un créneau manuel s'édite (titre, horaires, bascule deep-work, récurrence) et se
      supprime depuis la page ; horaires invalides (fin ≤ début) ignorés.
- [x] Éditer/supprimer un créneau récurrent agit sur le modèle (toutes les occurrences) ;
      un récurrent est éditable depuis un jour où il tombe.
- [x] Seuls les blocs `source='manual'` sont éditables/supprimables.
- [x] Vérifié en rendu réel (uvicorn) + tests de route (édition complète, décochage
      deep-work, rejet d'horaires invalides, suppression, présence du formulaire pour un
      récurrent). `pytest` reste vert (275 tests, zéro réseau réel).

---

# Phase 17 : dépôt dédié + import GitLab direct (sans pilotage)

## Contexte

« Kairos » quitte le dépôt `dotfiles` (où il vivait à la racine, à côté du reste
de l'outillage personnel de l'utilisateur) pour son propre dépôt : les collègues
qui veulent l'utiliser n'ont ni besoin ni envie de cloner des dotfiles Linux
personnelles. Le processus d'installation (venv, tests, service systemd) est
repris à l'identique dans un makefile racine du nouveau dépôt (cibles renommées
`install`/`test`/`service`/`service-uninstall`, plus `dev`/`run`, puisqu'il n'y a
plus de préfixe `kairos-` à porter aux côtés d'un module pilotage voisin).

En migrant, un défaut a été identifié : l'import des issues GitLab assignées
(phase 4/6) ne fonctionnait qu'en lisant le cache entretenu par
`pilotage-pleiade-gitlab` (`PILOTAGE_DATABASE_PATH`) — **aucune valeur pour un
collègue qui n'installe pas pilotage**, alors que « voir mes issues assignées »
est précisément la fonctionnalité GitLab qu'ils demandent.

## Décision actée

**Deux chemins mutuellement exclusifs** pour importer les issues assignées,
partageant `GITLAB_ASSIGNEE_USERNAME` :

1. **Cache pilotage** (inchangé, prime toujours si renseigné) : zéro appel
   réseau, lecture de `CachedGitLabIssue` sur `pilotage.db` — reste la seule
   voie donnant accès à la liaison manuelle « Fiche liée » (`Ticket`, propre à
   pilotage, non duplicable).
2. **Import direct** (nouveau, `app/gitlab_direct.py`) : client REST minimal,
   strictement lecture seule (`GET /projects/:id/issues?assignee_username=...
   &state=opened`, en-tête `PRIVATE-TOKEN` — même patron d'authentification que
   le client GitLab complet de `pilotage-pleiade-gitlab`, sans sa partie
   écriture/GraphQL, inutile ici), réglages propres (`GITLAB_URL`,
   `GITLAB_TOKEN`, `GITLAB_PROJECTS`), cache en mémoire à TTL
   (`GITLAB_CACHE_TTL_MINUTES`, même patron que `timetree_source.py`) pour ne
   pas appeler l'API à chaque chargement de page. Dégradation propre en cas
   d'échec (réseau, jeton invalide) : bandeau, jamais une page en erreur — les
   tâches déjà importées restent affichées telles quelles.

**Refactor de `tasks_gitlab_sync.py`** pour permettre le partage : la fonction
`sync_assigned_gitlab_tasks` devient **pure côté source** — elle reçoit une
liste d'issues déjà résolues (`GitLabIssueLike`, un `Protocol` structurel que
`CachedGitLabIssue` et `gitlab_direct.GitLabIssue` respectent toutes les deux
sans se connaître), et ne fait plus elle-même la requête SQL sur `pilotage.db`.
C'est `app/main.py` qui décide de la source (cache si `pilotage_session` est
présent, sinon appel direct si `gitlab_direct_configured`) — logique de fusion
(upsert idempotent, priorité jamais écrasée, disparue/fermée → archivée,
rebaptisage de l'ancien format d'`external_id`) inchangée et non dupliquée.

## Réutilisation

- Patron d'injection `client=` du `GitLabClient` (comme les clients Redmine/
  GitLab de pilotage, `httpx` sync, `raise_for_status()`), pour rester testable
  sans appel réseau réel (`respx`, réintroduit en dépendance de dev — retiré en
  phase 8 avec les autres clients HTTP, redevenu pertinent ici).
- Patron de cache TTL en mémoire de `calendar/timetree_source.py` (dict module,
  clé de configuration, horodatage) repris à l'identique pour `gitlab_direct.py`.

## Hors périmètre phase 17

- Écriture vers GitLab depuis l'import direct (strictement lecture seule,
  comme le cache pilotage).
- Liaison « Fiche liée » en dehors du cache pilotage : reste propre à
  l'intégration pilotage, aucun équivalent en import direct (la fiche de dette
  technique n'existe que dans `pilotage.db`).

## Success Criteria phase 17

- [x] Le dépôt `kairos` (ex-`DeepTask`) est autonome : mêmes cibles d'install
      (venv, tests, service systemd) qu'avant, sans dépendre du dépôt `dotfiles`.
- [x] Sans `PILOTAGE_DATABASE_PATH` ni réglages GitLab directs : l'import reste
      désactivé proprement (aucune erreur, aucun bandeau).
- [x] Avec seulement `GITLAB_URL`/`GITLAB_TOKEN`/`GITLAB_PROJECTS`/
      `GITLAB_ASSIGNEE_USERNAME` renseignés (pas de pilotage) : les issues
      ouvertes assignées, tous projets confondus, apparaissent comme tâches.
- [x] `PILOTAGE_DATABASE_PATH` renseigné en plus : le cache pilotage prime,
      aucun appel réseau GitLab n'est fait.
- [x] Un échec de l'appel direct (réseau, jeton invalide) affiche un bandeau et
      laisse les tâches déjà importées inchangées, jamais une page en erreur.
- [x] `sync_assigned_gitlab_tasks` ne dépend plus d'une session SQLAlchemy sur
      `pilotage.db` — testée avec de simples listes d'issues.
- [x] `pytest` passe sans réseau réel (287 tests).

---

# Phase 18 : bandeau TimeTree silencieux quand non configuré (issue #14)

## Contexte

Le bandeau d'avertissement TimeTree s'affichait même quand l'intégration était
simplement non configurée (cas normal, identifiants absents) — il ne devrait
s'afficher que si TimeTree est configuré et que la récupération échoue
réellement, comme le fait déjà `gitlab_direct_error`. Les intégrations de
calendrier externe doivent rester **entièrement optionnelles** (issue #14).

## Décision actée

Correctif d'un mot dans `templates/kairos.html` — la condition passe de
`{% if not timetree_ok %}` à `{% if timetree_configured and not timetree_ok %}`,
et la branche « non configuré » (qui affichait un message même en l'absence
totale d'identifiants) est supprimée. Aucun changement côté `app/main.py` :
`timetree_ok`/`timetree_configured`/`timetree_detail` étaient déjà dans le
contexte.

Une intégration Google Calendar (OAuth 2.0/PKCE) sur le même principe a été
explorée puis **retirée avant fusion** : elle exige de créer un client OAuth
dans Google Cloud Console (identifiant + secret client), une manipulation
jugée trop lourde par l'utilisateur comparée au simple email/mot de passe de
TimeTree — voir « Hors périmètre » ci-dessous.

## Hors périmètre phase 18

- **Google Calendar** : écarté après une première implémentation testée en
  conditions réelles — la configuration préalable côté Google Cloud Console
  (créer un projet, un client OAuth « Application de bureau », activer l'API,
  configurer l'écran de consentement) est jugée trop complexe pour l'usage
  personnel visé par Kairos. À reconsidérer seulement si Google propose un
  jour un mécanisme d'authentification aussi simple qu'un identifiant/mot de
  passe.

## Success Criteria phase 18

- [x] TimeTree non configuré (identifiants absents) : aucun bandeau, comme
      GitLab non configuré.
- [x] TimeTree configuré mais en échec : bandeau affiché avec le détail de
      l'erreur.
- [x] `pytest` passe sans réseau réel.
