# Notes (capture GTD)

_Rôle : une page de capture libre (« brain dump »), en amont de la boîte de
réception de la vue Jour — jeter une idée par écrit sans avoir à décider tout de
suite si c'est une tâche. Fichiers couverts : `app/tasks_models.py::Note`,
`app/main.py` (`_build_notes_context`, `render_notes_response`,
`_notes_action_response`, `notes_page`, `create_note`, `edit_note`,
`convert_note_to_task`, `archive_note`, `delete_note`), `templates/notes.html`,
`templates/_notes_list.html`. Le sixième item de navigation (icône `notes`,
`templates/_icons.html`) et son entrée topnav/bottom-nav sont documentés dans
`docs/spec/accueil-navigation.md` — repris ici seulement pour mémoire. Le schéma
`Note` est documenté en détail (tous les champs) dans
`docs/spec/modele-donnees.md`._

**Hors de cette spec** : la boîte de réception de la vue Jour (`Task` sans
priorité ni points), le flux GTD complet côté tâches → `docs/spec/vue-jour-gtd.md`
(cette spec ne fait qu'y pointer, à l'étape qui précède). Le schéma exhaustif de
`Note` (tous les champs, migration) → `docs/spec/modele-donnees.md`. La
navigation/le gabarit commun (`base.html`, topnav, bottom nav) →
`docs/spec/accueil-navigation.md`.

---

## 1. Besoin métier (cahier des charges)

### Objectif / problème

Kairos ne capturait jusqu'ici que des **tâches** : la boîte de réception de la
vue Jour (`docs/spec/vue-jour-gtd.md` § 2) accueille bien des `Task` sans
priorité ni points Fibonacci, mais une `Task` reste une tâche — un objet qu'on
s'engage, plus ou moins, à faire. Or l'étape amont du cycle GTD (*Getting Things
Done*) est la **capture** au sens le plus large : une idée, un rappel, un « il
faudrait que… » qui n'est pas encore tranché comme actionnable. Forcer un titre
de tâche à ce stade impose une décision prématurée (« est-ce que c'est vraiment
une tâche ? quelle priorité ? ») qui freine la capture — exactement ce que le
principe GTD « capturer sans friction » (déjà cité dans `vue-jour-gtd.md` pour la
capture de tâche titre-seul) cherche à éviter, mais poussé un cran plus loin en
amont.

### Comportement attendu (utilisateur)

- Une page dédiée, `/kairos/notes` (« Notes » dans la navigation), avec un
  encart de capture toujours visible en tête, jamais replié : un seul champ,
  le corps de la note (texte libre, plusieurs lignes possibles), un bouton
  « Capturer ». Aucun autre champ à ce stade (pas de priorité, pas de points,
  pas d'échéance) — c'est la différence avec la capture de tâche de la vue
  Jour.
- Juste sous la capture, la liste des notes non traitées (« Notes »), la plus
  récente en tête, jamais masquée derrière un `<details>` — état vide explicite
  (« Rien en attente ») si la liste est vide, même principe que la boîte de
  réception de la vue Jour.
- Chaque note affiche trois actions :
  - **→ Tâche** : convertit la note en tâche titre-seul (elle atterrit dans la
    boîte de réception de la vue Jour, à qualifier comme n'importe quelle autre
    capture) ; la note elle-même est retirée de la liste active mais **jamais
    supprimée** — retrouvable dans « Traité / archivé », avec un lien vers la
    tâche créée.
  - **Archiver** : classe la note sans suite (pas de tâche créée), même sort
    que ci-dessus côté visibilité (retirée de la liste active, conservée en
    historique).
  - **Supprimer** : suppression définitive, avec confirmation.
- Une section repliée par défaut, « Traité / archivé (N) », liste les notes
  converties ou classées sans suite — chacune avec son texte (atténué) et, si
  elle a été convertie, un lien « → voir la tâche créée » vers la boîte de
  réception de la vue Jour.
- La capture et les trois actions par note fonctionnent en amélioration
  progressive (AJAX si JavaScript actif, requête POST classique + redirection
  sinon) — même contrat que la vue Jour, JavaScript n'est jamais un prérequis.
- Ctrl/Cmd+Entrée dans le champ de capture soumet directement le formulaire,
  sans avoir à atteindre le bouton à la souris.
- Une sixième entrée de navigation, « Notes », apparaît entre Accueil et Jour
  (topnav desktop et bottom nav Android) — l'ordre reflète le flux GTD :
  capturer avant de traiter.

### Critères de succès

- Une note créée (corps non vide) apparaît immédiatement dans la liste des
  notes actives, sans rechargement de page si le JS est actif ; identique
  après un rechargement complet sinon.
- Convertir une note crée une tâche dont le titre est la **première ligne non
  vide** du corps de la note (les lignes suivantes, s'il y en a, ne sont
  reprises nulle part dans le titre) ; cette tâche apparaît dans la boîte de
  réception de la vue Jour (`GET /kairos`), sans priorité ni points, comme
  toute capture titre-seul.
- Une note convertie ou archivée disparaît de la liste active et réapparaît
  dans « Traité / archivé », jamais supprimée.
- Une note supprimée ne réapparaît nulle part, y compris dans l'historique
  archivé.
- La page reste intégralement utilisable JavaScript désactivé.
- L'entrée « Notes » de la navigation est mise en évidence uniquement sur
  `/kairos/notes`, sur aucune autre page.

### Hors périmètre / différé

- Édition du corps d'une note existante depuis l'UI (la route `POST
  /kairos/notes/{id}/edit` existe côté serveur, prête à être branchée, mais
  aucun formulaire de `_notes_list.html` ne l'appelle à ce stade — pas de
  bouton « éditer » dans cette première version).
- Priorité, points Fibonacci, échéance, tag de projet sur une note : ces
  champs n'existent que sur `Task`, posés **après** conversion, jamais avant.
  Une note qui a besoin d'un de ces champs doit d'abord être convertie.
- Recherche, filtres, tri autre que « plus récent d'abord » : périmètre
  minimal, symétrique de la boîte de réception (elle-même sans recherche
  dédiée, juste un état trié par urgence).
- **Aucune synchronisation temps réel entre onglets, aucun polling, aucun
  WebSocket, aucun push serveur.** Comme le reste de l'application — Kairos est
  mono-utilisateur et local, la cohérence entre un swap AJAX et un rechargement
  complet suffit (même contrainte que documentée pour la vue Jour, voir
  `docs/spec/vue-jour-gtd.md` § Besoin métier : « l'app est mono-utilisateur et
  locale : pas de sync temps réel entre onglets »). Deux onglets Notes ouverts
  simultanément peuvent afficher un état divergent jusqu'au prochain
  rechargement de chacun — ce n'est pas un défaut à corriger.
- Notification, rappel, ou tout mécanisme qui pousserait une note non traitée
  vers l'utilisateur : la page Notes est un lieu qu'on consulte, pas un canal
  qui sollicite.

---

## 2. Solution technique

### Modèle — `Note` (`app/tasks_models.py`)

Table `note`, cinq champs propres (voir `docs/spec/modele-donnees.md` pour le
détail exhaustif type par type) :

- `id`, `body` (`Text`, défaut `""`),
- `status` (`'open' | 'archived'`, défaut `'open'`, indexé),
- `converted_task_id` (`int | None`, **sans contrainte `ForeignKey`** — même
  parti pris que `Task.parent_id`/`Task.linked_ticket_id`),
- `created_at`/`updated_at` (mêmes `_now`/`onupdate=_now` que `Task`).

Nouvelle table : `TasksBase.metadata.create_all()` la crée sur toute base
existante sans migration additive dédiée — vérifié dans
`app/tasks_db.py::init_tasks_db` (`_TASKS_MIGRATION_COLUMNS` ne référence que
des colonnes ajoutées à une table **déjà existante** ; une table entièrement
nouvelle n'a besoin d'aucune entrée). Une note d'exemple est ajoutée par
`app/tasks_seed.py::seed_example_data` (même garde-fou de première utilisation
que le reste des exemples : posée une seule fois, sur base vierge).

### Architecture de rendu (mirror du patron « Kairos »)

Même contrat que `_build_kairos_context`/`render_kairos_response`/
`_kairos_action_response` (`docs/spec/vue-jour-gtd.md` § Architecture de
rendu), avec son propre jeu de fonctions, **jamais réutilisé/étendu** — deux
patrons parallèles plutôt qu'un patron générique paramétré, pour rester lisible
sans abstraction prématurée sur deux domaines qui n'ont en commun que la forme
du contrat, pas les données :

- `_build_notes_context(request, tasks_session)` — construit `open_notes`
  (`status == 'open'`, triées `created_at` décroissant) et `archived_notes`
  (`status == 'archived'`, même tri), plus `"page": "notes"`.
- `render_notes_response(request, *, fragment)` — ouvre sa **propre** session
  tâches via `_request_session(get_tasks_session)`, construit le contexte,
  rend `notes.html` (`fragment=False`) ou `_notes_list.html`
  (`fragment=True`).
- `_notes_action_response(request)` — si `X-Requested-With: fetch`, renvoie
  `render_notes_response(request, fragment=True)` ; sinon
  `RedirectResponse("/kairos/notes", status_code=303)`.
- `templates/notes.html` étend `base.html`, contient l'encart de capture
  (`<form action="/kairos/notes" data-ajax>`) **hors** de `<div
  id="mj-notes-content">{% include "_notes_list.html" %}</div>` — même
  principe d'enveloppe unique que `#mj-day-content`
  (`docs/spec/vue-jour-gtd.md`) : `#mj-notes-content` n'existe qu'à cet unique
  endroit dans toute l'app, jamais posé par `_notes_list.html` lui-même (qui
  doit rester rendable tel quel, en fragment autonome, par
  `render_notes_response(fragment=True)`).

### Routes (`app/main.py`)

| Route | Handler | Effet |
| --- | --- | --- |
| `GET /kairos/notes` | `notes_page` | Page pleine (`render_notes_response(fragment=False)`). |
| `POST /kairos/notes` | `create_note` | Corps strippé ; si non vide, crée une `Note`. Corps vide → no-op silencieux (pas d'erreur, juste rien créé), même tolérance que `create_native_task` sur un titre vide. |
| `POST /kairos/notes/{id}/edit` | `edit_note` | Remplace `body`. Note disparue → no-op. Pas de formulaire client à ce stade (voir Hors périmètre). |
| `POST /kairos/notes/{id}/convert` | `convert_note_to_task` | Si la note est `open` et que sa première ligne non vide n'est pas vide : crée `Task(title=<première ligne, ≤200 caractères>, source="native")`, `tasks_session.flush()` pour obtenir l'id, puis `note.status = "archived"` et `note.converted_task_id = task.id`. Une note déjà `archived` ou introuvable → no-op. |
| `POST /kairos/notes/{id}/archive` | `archive_note` | `status = "archived"`, sans toucher à `converted_task_id` (reste `None`). |
| `POST /kairos/notes/{id}/delete` | `delete_note` | Suppression définitive de la ligne — à la différence de `delete_task` (qui archive une tâche non native), une note n'a **aucun** historique de priorisation à préserver : la suppression est donc toujours dure, jamais un archivage déguisé. |

Chaque handler POST ouvre sa **propre** session (`_request_session
(get_tasks_session)`), committe, la ferme (fin du bloc `with`) avant d'appeler
`_notes_action_response` — même invariant « jamais de session imbriquée » que
la vue Jour.

`_note_title_from_body(body)` — fonction pure : première ligne dont
`.strip()` est non vide (une note commençant par des lignes blanches ne donne
donc pas un titre vide tant qu'une ligne non blanche suit), tronquée à 200
caractères. Choix de la limite : `Task.title` est `String(512)`, mais une
capture rapide n'a structurellement pas besoin d'en approcher le quart — 200
caractères couvre largement une phrase de titre sans jamais tronquer
silencieusement un texte court.

### Templates

**`templates/notes.html`** — `{% extends "base.html" %}`, `{% block
topbar_title %}Notes{% endblock %}`. Encart de capture en `.panel` (padding
intégré, pas besoin d'une classe dédiée) : `<textarea name="body" required>` +
`<button class="btn primary">`. Script `{% block scripts %}` **autonome**, ne
réutilise ni ne modifie le script de `kairos.html` — deux pages indépendantes
(voir « Décisions et pièges tracés » pour la justification) :
- Écouteur `submit` délégué sur `document`, portée sur `form.matches
  ('[data-ajax]')` **et** (le formulaire est la capture, identifiée par `id
  ="mj-note-capture"`, **ou** il est un descendant de `#mj-notes-content`) —
  condition nécessaire car la capture vit **hors** du conteneur qui est
  remplacé par le swap (voir ci-dessus), contrairement aux trois
  mini-formulaires par note.
- Sur succès : remplace `#mj-notes-content`.innerHTML par le fragment reçu ;
  si le formulaire intercepté était la capture, appelle en plus `form.reset()`
  (le formulaire de capture n'étant pas remplacé par le swap, sa `<textarea>`
  ne se vide pas toute seule — contrairement à un mini-formulaire de note, qui
  disparaît avec le reste du fragment remplacé).
- Sur échec (réseau, ou `#mj-notes-content` introuvable) : repli
  `form.submit()`.
- Écouteur `keydown` délégué : `Ctrl`/`Cmd`+`Entrée` dans la `<textarea>` de
  capture (`#mj-note-capture textarea`) appelle `form.requestSubmit()` (repli
  `form.submit()`).

**`templates/_notes_list.html`** — fragment, deux sections :
1. `<section class="card" id="mj-notes-open">` — liste `open_notes` en
   `<ul class="kairos-list"><li class="kairos-item">`, corps de note en
   `<p style="white-space:pre-wrap">{{ note.body }}</p>` (échappement Jinja
   par défaut, **jamais** `| safe` — texte utilisateur brut, seuls les retours
   à la ligne sont préservés visuellement via `white-space: pre-wrap`, pas de
   rendu HTML/Markdown). État vide explicite (`.hint`) si `open_notes` est
   vide.
2. `<details class="card"><summary class="collapser">` — `archived_notes`,
   repliée par défaut, même patron que `_kairos_backlog.html`/les sections
   secondaires de `_kairos_day.html` (chevron `icon('chevron_right', '')` +
   compte dans le `<summary>`). N'est rendue **que si** `archived_notes` est
   non vide (contrairement à la section « Notes » actives, toujours rendue) —
   cohérent avec le patron déjà en place pour le Backlog et les sections
   condensées de la vue Jour, qui ne s'affichent elles aussi que si non vides.
   Une note convertie affiche un lien `<a class="badge info"
   href="/kairos#mj-inbox">→ voir la tâche créée</a>` — ancre vers `#mj-inbox`
   (id déjà posé par `_kairos_day.html`, voir `vue-jour-gtd.md`), pas une
   requête ni un identifiant de tâche direct : l'utilisateur atterrit sur la
   boîte de réception de la vue Jour et repère la tâche par son titre (pas de
   sur-ingénierie pour scroller/mettre en évidence la ligne exacte, la boîte
   de réception reste courte par construction).

### Décisions et pièges tracés

- **Deux patrons de rendu parallèles (Notes / Kairos), pas un patron
  générique partagé.** `_build_notes_context`/`render_notes_response`/
  `_notes_action_response` reproduisent volontairement la forme de
  `_build_kairos_context`/`render_kairos_response`/`_kairos_action_response`
  sans jamais les appeler ni en dépendre : les deux domaines (notes, tâches)
  n'ont en commun que le contrat HTTP (négociation `X-Requested-With`,
  fragment vs page pleine), pas les données ni les règles métier. Factoriser
  aurait introduit un paramètre générique (quel gabarit, quelle session,
  quelle redirection) pour un gain de duplication minime (une dizaine de
  lignes par fonction) — jugé net-négatif en lisibilité pour un dépôt qui
  privilégie déjà la duplication explicite à l'abstraction prématurée
  ailleurs (voir `_kairos_action_response`, commenté comme la réponse des
  « handlers d'action » de son seul domaine).
- **Script de `notes.html` autonome, jamais un import/une extension du script
  de `kairos.html`.** Les deux pages n'ont qu'un seul point commun réel
  (intercepter un `<form data-ajax>` et swapper un conteneur) : dupliquer ces
  ~20 lignes reste plus simple à suivre que d'extraire une fonction partagée
  qui devrait alors être paramétrée par l'id du conteneur cible et le
  comportement de repli de la capture (qui n'existe que côté Notes, `kairos.
  html` n'a pas de formulaire `data-ajax` hors de `#mj-day-content` — voir
  `vue-jour-gtd.md` § Mises à jour AJAX : seules des actions rapides *à
  l'intérieur* du fragment portent `data-ajax` côté Kairos).
- **Suppression toujours dure pour une note, jamais un archivage déguisé.**
  Contrairement à `delete_task` (qui archive une tâche non native pour ne pas
  perdre l'historique de priorisation posé dessus), une note n'a par
  construction aucune priorité, aucun point, aucun historique de tri à
  préserver — la distinguer d'un archivage (qui reste, lui, la voie choisie
  pour une conversion ou un classement sans suite) aurait ajouté une nuance
  sans bénéfice utilisateur identifié.
- **Pas de bouton d'édition câblé dans `_notes_list.html`, alors que la route
  existe.** `POST /kairos/notes/{id}/edit` a été ajoutée pour rester
  symétrique du contrat de base (et pour ne pas fermer la porte à un futur
  bouton crayon, sur le modèle de `edit_panel` côté tâches), mais aucune
  maquette n'exigeait l'édition en place pour cette première version — mieux
  vaut une route prête et non branchée qu'une fonctionnalité UI à moitié
  finie. Documenté explicitement en « Hors périmètre » plutôt que laissé
  comme un oubli silencieux.
- **`Note.converted_task_id` sans contrainte `ForeignKey`.** Même parti pris
  que tout le reste du schéma tâches (`Task.parent_id`, `TaskDependency.
  blocker_id`, `Task.linked_ticket_id`) — voir `docs/spec/modele-donnees.md`
  § Décisions et pièges tracés pour la justification consolidée (référence
  « molle », intégrité laissée au code applicatif). `convert_note_to_task` est
  la seule route qui écrit ce champ ; aucune route ne le lit pour autre chose
  qu'afficher le lien « → voir la tâche créée ».

### Invariants et garde-fous

- Un seul `id="mj-notes-content"` dans toute l'application, posé uniquement
  par `notes.html` — jamais par `_notes_list.html`, qui doit rester rendable
  tel quel, sans enveloppe, comme fragment AJAX autonome (même invariant que
  `#mj-day-content`/`_kairos_day.html`).
- Toute route d'action Notes ouvre sa **propre** session `tasks_session`,
  committe, **ferme** cette session avant d'appeler `_notes_action_response`
  (qui rouvre une session fraîche) — jamais de session imbriquée.
- `create_note`/`edit_note` ne rejettent jamais un corps non vide, quelle que
  soit sa longueur (pas de limite de caractères côté capture, contrairement au
  titre tronqué à la conversion) — cohérent avec l'objectif « capturer sans
  friction », aucune validation qui pourrait bloquer une capture rapide.
- `convert_note_to_task` ne convertit jamais une note déjà `archived` une
  deuxième fois (`note.status == "open"` est une condition explicite du
  handler) — évite de créer une deuxième tâche à partir de la même note sur un
  double clic ou une requête rejouée.
- Aucune route Notes ne modifie jamais un objet `Task` en dehors de la
  création titre-seul de `convert_note_to_task` — la conversion est un point
  d'entrée à sens unique vers le domaine tâches, jamais l'inverse (aucune
  route côté tâches ne référence `Note`).
