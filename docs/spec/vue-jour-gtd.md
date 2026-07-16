# Vue Jour & flux GTD

_Rôle : la vue Jour (`GET /kairos`, page par défaut de l'application) est le poste de
pilotage quotidien de Kairos — capture sans friction, clarification GTD (boîte de
réception), puis exécution ordonnée (WSJF) de la journée. Cette spec couvre
l'**ergonomie/UI** de cette vue et son **contrat de rendu** (page pleine vs fragment
AJAX), pas les moteurs de calcul sous-jacents._

**Fichiers couverts** :
- `templates/kairos.html` — page pleine (`{% extends "base.html" %}`) : branche
  vue Semaine inline, branche vue Jour via `{% include "_kairos_day.html" %}`, et
  tout le JS inline (`{% block scripts %}`).
- `templates/_kairos_day.html` — partiel de la vue Jour (capture → inbox →
  « Maintenant » → bannières → filtres/backlog → agenda + sections secondaires →
  colonne latérale).
- `templates/_kairos_macros.html` — macros partagées : `done_toggle`,
  `task_actions`, `time_spent`, `fibo_help`, `edit_panel`, `task_meta`.
- `templates/_kairos_banners.html`, `templates/_kairos_filters.html`,
  `templates/_kairos_backlog.html` — partiels `{% include %}` (contexte propagé
  tel quel, jamais de macro).
- `app/main.py` — `_build_kairos_context`, `render_kairos_response`,
  `_kairos_action_response`, `kairos`, `create_native_task`,
  `update_task_priority`, `update_task_points`, `start_timer`/`stop_timer`,
  `edit_task`, `toggle_task_done`, `snooze_task`, `delete_task`,
  `create_manual_block`, `edit_manual_block`, `delete_manual_block`.

**Hors de cette spec** (voir la spec de domaine dédiée) : algorithme
d'ordonnancement/WSJF/time-blocking/timeline → `docs/spec/ordonnancement.md` ;
chrono vivant + alertes → `docs/spec/temps-reel-chrono.md` ; dépendances/bloqueurs
(calcul, cycles, urgence dérivée) → `docs/spec/dependances.md` ; schéma
`Task`/`TimeBlock`/`WorkSession` → `docs/spec/modele-donnees.md` ; règles de
récurrence (tâches et blocs) → `docs/spec/recurrence.md` ; vue Semaine détaillée et
`/kairos/stats` → `docs/spec/statistiques.md` ; TimeTree/GitLab →
`docs/spec/integrations-externes.md` ; réglages → `docs/spec/reglages-secrets.md` ;
`base.html`/topnav/accueil → `docs/spec/accueil-navigation.md`.

---

## 1. Besoin métier (cahier des charges)

### Objectif / problème

Kairos est un outil mono-utilisateur : la vue Jour doit répondre, à l'ouverture,
sans recouper mentalement plusieurs écrans, à trois questions — qu'est-ce qui
n'est pas encore clarifié, qu'est-ce que je fais maintenant, qu'est-ce qui vient
ensuite. Le produit a grossi par 18 phases successives (`SPEC_KAIROS.md`) ; la
phase 5 constatait déjà « six sections de liste empilées [...] sans hiérarchie
visuelle entre une tâche en retard et une tâche normale — l'ordre du tri porte
toute la charge de signal, rien à l'œil ». Une refonte (désignée dans le code par
« Refonte Jour v2 », commentaire `static/style.css`) a réorganisé la page
explicitement autour du cycle **GTD** (*Getting Things Done*) **capturer → traiter
la boîte de réception → faire**, décrit dans `docs/DESIGN_SYSTEM.md` § « Architecture
de l'information — vue Jour (flux GTD) ».

Deux contraintes transverses structurent toute décision d'UI de cette vue :
- l'app tourne en navigateur desktop, en exécutable PyInstaller offline et en
  WebView Android packagée (APK) — le JavaScript ne peut donc jamais être un
  prérequis fonctionnel, seulement une amélioration progressive ;
- l'app est mono-utilisateur et locale : pas de sync temps réel entre onglets, la
  cohérence entre un swap AJAX et un rechargement complet doit être totale (même
  gabarit, même contexte serveur).

### Comportement attendu (utilisateur, section par section)

Ordre vertical de la vue Jour (de haut en bas), chaque section correspondant à une
étape du flux GTD ou à un utilitaire secondaire :

1. **Capture** (`.mj-capture`) — toujours visible, jamais dans un `<details>`
   replié. Deux volets par onglet radio : « Tâche » (titre seul, capture
   volontairement sans friction) et « Créneau / deep work » (titre + horaires +
   case deep-work + récurrence, avec en dessous la liste éditable des créneaux du
   jour). Un seul bouton bleu (`.btn.primary`) par volet.
2. **Boîte de réception** (« À traiter », `#mj-inbox`) — juste sous la capture.
   Toute tâche sans priorité **ou** sans points Fibonacci, quelle que soit son
   origine (native, GitLab assigné...), y échoue et n'entre dans **aucun** tri tant
   qu'elle n'est pas qualifiée. Qualification en un ou deux clics, en ligne
   (sélection priorité + sélection points), sans ouvrir l'édition complète. État
   vide affiché explicitement (jamais la section qui disparaît).
3. **« Maintenant »** (`.mj-progress`) — la tâche actionnable suivante, avec ses
   actions directes (fait / chrono / décaler) et les statistiques de la journée
   (faites, à faire, requis vs disponible, débordement, temps déjà travaillé
   aujourd'hui ventilé par type, indications calendrier). Élément principal de la
   page, jamais repliable.
4. **Bannières d'alerte** (TimeTree, import GitLab, surcharge de priorité) — sous
   « Maintenant », jamais en tout premier : ce sont des avertissements de
   dégradation d'intégrations externes, pas le point d'entrée du flux.
5. **Filtres compacts** et **Backlog** (sans échéance ni date programmée) —
   utilitaires secondaires, repliés par défaut.
6. **Agenda ordonné** (« Aujourd'hui, dans l'ordre ») — la liste centrale, triée
   par score WSJF, toujours dépliée.
7. **Sections secondaires condensées** (Sans créneau / Bloquées / Programmées plus
   tard / Mères en cours / Fait) — chacune repliée par défaut, avec un compte et
   une courte phrase de rôle dans son `<summary>`.
8. **Colonne latérale** — carte « En ce moment » (chrono en cours) + carte
   « Agenda » (timeline verticale heure par heure de la journée).

Chaque ligne de tâche, quelle que soit la section, partage le même vocabulaire
visuel : coche ronde en tête de ligne, bordure gauche colorée par palier
d'urgence, badges (score WSJF, priorité, type, points, fiche liée, temps passé,
« traîne depuis... »), actions à droite (chrono, décaler), crayon d'édition.

L'édition d'une tâche est un panneau modal unique (essentiels toujours visibles +
options avancées repliées), un seul bouton « Enregistrer » qui pose tout en un
aller-retour (infos, sous-tâches en lot, bloqueurs, épinglage).

### Critères de succès

- Une tâche capturée (titre seul) apparaît immédiatement en boîte de réception,
  jamais dans l'agenda tant qu'elle n'a ni priorité ni points Fibonacci.
- Poser les deux champs en ligne (sans ouvrir l'édition) fait sortir la tâche de
  l'inbox et entrer dans le tri WSJF, visible sans rechargement de page si le JS
  est actif ; identique après un rechargement complet sinon.
- La page reste intégralement utilisable JavaScript désactivé (repli
  POST → redirection 303 systématique), condition nécessaire pour la WebView
  Android et l'accessibilité clavier/lecteur d'écran.
- Un swap AJAX ne casse jamais le chrono vivant (pas d'intervalle orphelin) ni les
  écouteurs délégués (Échap, bascule modale, radios de capture).
- Aucun composant ne déborde horizontalement sur ~375px de large (grille semaine,
  panneau d'édition, badges à texte long comme `.badge.at_risk`/`.badge.bad`).
- La modale d'édition se ferme au clic à l'extérieur de la carte et à Échap, sans
  jamais masquer ses propres champs/boutons avec son propre calque.
- Un créneau récurrent édité ou supprimé agit sur le modèle, donc sur toutes ses
  occurrences (aucune occurrence n'est jamais persistée isolément).

### Hors périmètre / différé

- L'algorithme d'ordonnancement (buckets d'urgence, score WSJF, time blocking,
  épinglage, timeline) → `docs/spec/ordonnancement.md`.
- Le chrono vivant, ses alertes (dépassement, chrono oublié, pomodoro),
  notifications navigateur/Android → `docs/spec/temps-reel-chrono.md`.
- Le calcul des dépendances (blocage transitif, cycles, urgence dérivée) →
  `docs/spec/dependances.md`.
- Les champs et le schéma `Task`/`TimeBlock`/`WorkSession`, migrations →
  `docs/spec/modele-donnees.md`.
- Les règles de récurrence (tâches et blocs manuels) →
  `docs/spec/recurrence.md`.
- Détail de la vue Semaine (grille 7 jours) et `/kairos/stats` →
  `docs/spec/statistiques.md`.
- TimeTree, import GitLab (cache pilotage / direct) →
  `docs/spec/integrations-externes.md`.
- Réglages, secrets, page `/kairos/settings` → `docs/spec/reglages-secrets.md`.
- `base.html`, topnav, page d'accueil → `docs/spec/accueil-navigation.md`.

---

## 2. Solution technique

### Architecture de rendu

- `kairos.html` étend `base.html`, importe les macros partagées `with context`
  (`{% from "_kairos_macros.html" import ... with context %}`), et branche sur
  `view` (query param) : `view == 'week'` rend la grille semaine **inline** dans
  `kairos.html` lui-même ; sinon un unique `<div id="mj-day-content">` enveloppe
  `{% include "_kairos_day.html" %}` — **`#mj-day-content` n'existe qu'à cet unique
  endroit dans toute l'app** (invariant explicite, commenté dans le fichier).
- `_kairos_day.html` est rendu **deux fois** selon le chemin d'appel : (a) inclus
  dans `kairos.html` pour la page pleine, à l'intérieur de l'enveloppe
  `#mj-day-content` ; (b) rendu **directement, sans enveloppe**, par
  `render_kairos_response(fragment=True)` pour les réponses AJAX — le fragment
  renvoyé remplace le `.innerHTML` de `#mj-day-content` côté client, donc ne doit
  jamais poser l'id lui-même sous peine de duplication.
- `_build_kairos_context(request, tasks_session, pilotage_session, *, view, day)`
  construit le contexte partagé jour/semaine (tâches visibles, buckets, WSJF,
  filtres, timeline...). `render_kairos_response(request, *, fragment)` est le
  **point d'entrée unique** de rendu : ouvre les deux sessions
  (`get_tasks_session`, `get_pilotage_session`) via `_request_session`, appelle
  `_build_kairos_context`, puis choisit `kairos.html` (`fragment=False`) ou
  `_kairos_day.html` (`fragment=True`). Les handlers d'action doivent avoir
  **committé et fermé leur propre session tâches** avant d'appeler cette
  fonction, qui rouvre des sessions fraîches — invariant documenté explicitement
  dans le code, jamais de session imbriquée.
- `templates/_kairos_macros.html` est importée `with context` par **les deux**
  gabarits (`kairos.html` et `_kairos_day.html`) plutôt que l'un depuis l'autre —
  évite un cycle d'import entre les deux.
- `_kairos_banners.html`, `_kairos_filters.html`, `_kairos_backlog.html` sont
  inclus (`{% include %}`, pas de macro) à la fois par la branche semaine de
  `kairos.html` et par `_kairos_day.html` : `{% include %}` transmet le contexte
  complet par défaut, donc aucun paramètre explicite n'est nécessaire. Un seul
  `{% include "_kairos_banners.html" %}` par vue rendue (jamais les deux à la
  fois, commenté dans le partiel).
- `_kairos_backlog.html` est rendu **quelle que soit la vue** (jour ou semaine) :
  sans lui, une tâche sans échéance ni date programmée n'apparaîtrait ni dans la
  grille semaine (groupée strictement par échéance) ni facilement dans l'agenda
  du jour — elle s'y perdrait.

### Le flux GTD, section par section

**1. Capture** (`_kairos_day.html` lignes ~17-90, classe `.mj-capture`) — deux
formulaires HTML classiques, jamais `data-ajax` (navigation complète à la
soumission, y compris JS actif) :
- `POST /kairos/tasks` (`create_native_task`) : titre seul requis. Gère aussi la
  création d'une **sous-tâche unique** si un `parent_id` est fourni (pas utilisé
  par ce formulaire de capture, mais par le même endpoint depuis ailleurs) ; la
  mère est ignorée silencieusement si elle a disparu entre-temps.
- `POST /kairos/blocks` (`create_manual_block`) : titre + `datetime-local` début/
  fin + case `deepwork` + `<select name="recurrence">` (aucune / quotidienne /
  jours ouvrés / hebdomadaire). Rejet silencieux (retour 303 sans effet) si
  `end <= start` ou si les dates ne parsent pas.
- Bascule des deux volets : écouteur `change` délégué sur `document`, portée sur
  `input[name="mj-add-mode"]`, cible le conteneur `.mj-capture` (pas un
  `<details>` : la capture n'est plus repliable), montre/masque les
  `[data-mj-add-pane]` dont l'attribut correspond à la valeur du radio choisi.
- Sous le volet créneau : `editable_blocks` (calculé dans `_build_kairos_context`)
  liste les **lignes réelles en base** (`TimeBlock.source == 'manual'`)
  pertinentes pour le jour affiché — les ponctuels du jour **et** les modèles
  récurrents dont `expand_recurring_blocks([b], target_day, target_day)` produit
  une occurrence ce jour-là (aligné sur ce que montre la timeline). Éditer un
  créneau récurrent porte donc sur le **modèle**, donc sur **toutes** ses
  occurrences (phase 16) — signalé par un badge `.badge.info` « récurrent » (avec
  libellé `block_recurrence_labels`) et par le texte de confirmation JS de
  suppression (`onsubmit="return confirm('Supprimer ce créneau{% if
  block.recurrence %} récurrent (toutes ses occurrences){% endif %} ?')"`).
- Chaque entrée porte son propre panneau `.mj-edit`/`.mj-edit-body` (même widget
  CSS que l'édition de tâche, mais markup dédié, pas la macro `edit_panel`) :
  `POST /kairos/blocks/{id}/edit` (`edit_manual_block`, mêmes règles de
  validation que la création, whitelist de récurrence via
  `BLOCK_RECURRENCE_RULES`) et `POST /kairos/blocks/{id}/delete`
  (`delete_manual_block`). Garde-fou serveur des deux routes : n'agissent que si
  `block.source == 'manual'` (les créneaux TimeTree sont transitoires, jamais en
  base).

**2. Boîte de réception** (`#mj-inbox`, classe `.mj-to-process` +
`.mj-inbox-empty` conditionnelle) — variable de contexte `visible_to_process`
(= `schedule.to_process` filtré par `_visible()`, recherche/facettes). Chaque
ligne affiche un badge `.badge.warn` dont le libellé distingue les trois cas :
« priorité et points manquants », « priorité manquante », « points manquants ».
Qualification en ligne :
- Deux formulaires `.mj-inline-form[data-ajax]` par ligne, chacun un
  `<select data-autosubmit>` sans bouton visible : `POST
  /kairos/tasks/{id}/priority` (`update_task_priority`) et `POST
  /kairos/tasks/{id}/points` (`update_task_points`).
- `data-autosubmit` est un attribut **dédié**, distinct de `.mj-fibo-select`/
  `.mj-task-type-select` (utilisées dans le panneau d'édition complet) : un
  écouteur `change` délégué appelle `form.requestSubmit()` dès qu'un de ces deux
  `<select>` change — volontairement **jamais** le remplissage automatique de
  durée (réservé au panneau d'édition), pour ne pas mélanger qualification
  rapide et estimation détaillée.
- L'état vide (`.mj-inbox-empty`) réduit le padding vertical et affiche
  `.mj-inbox-empty-msg` (« Rien à traiter : tout est déjà clarifié ») au lieu de
  faire disparaître la section — rappel volontaire de « où regarder en premier ».
- Une aide repliable (`.mj-help`, motif réutilisé de `fibo_help()`) explique le
  principe GTD directement dans le `<summary>` (« pourquoi qualifier ? »).

**3. « Maintenant »** (`.mj-progress`) — `next_up_task` (calculé côté serveur) =
`schedule.scheduled[0].task` si l'agenda a une première entrée planifiée, sinon
`schedule.unscheduled[0]` si la liste non planifiée n'est pas vide, sinon `None` —
commentaire de code : « il y a toujours un "prochain pas" ». Affiche
`done_toggle`/`task_actions` directement sur cette tâche (fait, chrono, décaler)
sans que l'utilisateur ait à la retrouver dans la liste plus bas. Ligne de titre
en Newsreader italique (`.mj-next`, exception de police assumée — voir « Décisions
et pièges tracés »). Bloc de statistiques (`.mj-progress-stats`) : compte de
tâches faites/à faire, `required_str`/`available_str` (temps requis vs
disponible), `spent_total_str` (temps travaillé **aujourd'hui uniquement** —
correction d'un bug de scope temporel tracée en phase 7 de `SPEC_KAIROS.md`),
ventilation `spent_by_type_today`, badge de débordement si
`schedule.stats.overflow_minutes > 0`. Ligne `indication_events` (événements
TimeTree journée-entière/multi-jours, phase 12 — simple puce datée, jamais un
obstacle horaire). Bloc `#mj-alert-config` (attributs `data-idle`/`data-pomodoro`)
+ bouton d'opt-in : sert uniquement de point d'ancrage DOM pour le script du
chrono (détail dans `docs/spec/temps-reel-chrono.md`), pas de logique propre à
cette spec.

**4. Bannières** (`_kairos_banners.html`) — trois conditions indépendantes,
chacune `.banner.warning` : TimeTree (`timetree_configured and not
timetree_ok`, silencieux si non configuré — phase 18), import GitLab direct
(`gitlab_direct_error` non vide), surcharge de priorité maximale
(`priority_overload_count > priority_overload_threshold`). Position fixe : sous
« Maintenant », avant les filtres — documentée comme volontaire (« ce ne sont que
des avertissements de dégradation, pas le point d'entrée du flux »).

**5. Filtres compacts** (`_kairos_filters.html`) — `<details class="card
mj-filter-compact">`, `open` seulement si `filter_active` (recherche ou une
facette posée). Formulaire **GET** (pas `data-ajax` : c'est une navigation avec
état porté par l'URL, pas une mutation), champs cachés `view`/`start` pour
préserver la vue/le jour courants. Facettes : priorité (`range(0, 3)`, donc
P0/P1/P2 uniquement — même échelle réduite que partout ailleurs dans cette vue),
projet (dynamique, `project_choices`), type (`settings.task_type_list`), points
Fibonacci (`fibonacci_scale`). Un seul marqueur visible quand un filtre est actif
(`.badge.info` « filtre actif » dans le `<summary>`) plutôt que les 5 champs
déployés en permanence ; lien « Réinitialiser » vers l'URL sans query params (sauf
`view`/`start`). Filtre d'affichage pur : `_visible()` (dans
`_build_kairos_context`) ne touche jamais l'ordonnancement lui-même, seulement les
listes affichées.

**6. Agenda ordonné** — `<details class="card" open>`, seule section de la vue
Jour (hors « Maintenant ») **dépliée par défaut**. Liste `<ol>` (seule liste
ordonnée sémantiquement de la page — l'ordre porte l'information). Chaque `<li
class="kairos-item mj-bucket-{{ bucket_of[...] }}">` : coche, heure, titre (avec
fil d'Ariane `{{ parent_title_of }} › ` si sous-tâche), badges conditionnels
épinglée/deep-work/chemin-critique, `task_meta`, `time_spent`, notes
`pushed`/`dip`/`conflict` en badges colorés, `task_actions`, `edit_panel`.

**7. Sections secondaires condensées** — chacune un `<details class="card">`
**sans** attribut `open` (repliées par défaut), rendue seulement si sa liste est
non vide : Sans créneau aujourd'hui, Bloquées, Programmées plus tard, Tâches
mères en cours, Fait. Chaque `<summary class="collapser">` porte un compte et une
phrase de rôle (`.hint`). Note de traçabilité : `SPEC_KAIROS.md` phase 5 décrivait
« les autres sections actionnables (sans créneau, bloquées, mères en cours)
restent dépliées », seule « Fait » étant repliée par défaut à l'époque. La refonte
GTD ultérieure (code actuel, confirmée par `docs/DESIGN_SYSTEM.md` § « Sections
secondaires condensées ») a **replié toutes** ces sections par défaut, y compris
celles que la phase 5 gardait ouvertes — décision qui supersède la phase 5 sur ce
point précis, à ne pas rouvrir sans en reparler (cohérent avec la charte actuelle
de densité réduite en tête de page).

**8. Colonne latérale** (`.mj-day-grid` → `.mj-day-main` flex 1.7 + `.mj-side-col`
largeur fixe 300px, pleine largeur sous 860px) :
- `.mj-now-card` (fond `--dark-surface`, un des deux seuls endroits sombres de
  l'app avec la pilule de nav active) : titre + minuteur de la tâche en cours
  (`running_task_id`), bouton Arrêter (`POST
  /kairos/tasks/{id}/timer/stop`, `data-ajax`), ou message d'état vide.
- `.mj-timeline-card` (« Agenda ») : grille horaire (`timeline_hours`), entrées
  positionnées en absolu (1 min = 1 px, classes `busy`/`work`/`pinned`/`conflict`/
  `deepwork`/`deepwork-task` selon `entry.kind`), rail « réel »
  (`.mj-tl-session`, gouttière gauche) superposant les sessions chronométrées du
  jour (`session_timeline`, phase 11) au planifié.

### Mises à jour AJAX (contrat X-Requested-With, swap, repli sans JS)

- **Seules six actions « rapides » portent `data-ajax`** sur leur `<form>` :
  `done_toggle` (fait/rouvrir), les trois formulaires de `task_actions` (chrono
  start/stop, décaler), les deux formulaires de qualification en ligne de
  l'inbox (priorité, points), et le bouton Arrêter de `.mj-now-card`. **Aucun**
  autre formulaire de la vue Jour ne porte `data-ajax` : ni la capture (tâche ou
  créneau), ni le panneau d'édition complet (`mj-edit-form`, tâche ou bloc), ni
  la suppression de tâche/bloc — ces actions rechargent toujours la page
  entière, y compris JavaScript actif (pas de bénéfice ergonomique identifié à
  les intercepter, elles ouvrent de toute façon une nouvelle vue de la page).
- Écouteur `submit` délégué sur `document` (`kairos.html`) : intercepte tout
  `form.matches('[data-ajax]')`, `ev.preventDefault()`, puis
  `fetch(form.action, {method:'POST', body:new FormData(form), headers:
  {'X-Requested-With':'fetch'}})`. Sur succès (`res.ok`) : remplace
  `document.getElementById('mj-day-content').innerHTML` par le HTML reçu,
  annule l'intervalle du chrono précédent (`target.__kairosTimerHandle`, évite un
  intervalle orphelin qui continuerait d'écrire sur un DOM détaché), appelle
  `initDayScripts(target)`, restaure `window.scrollY`. Sur échec (`fetch` rejeté
  — réseau indisponible — ou `#mj-day-content` introuvable) : **repli**
  `form.submit()`, soumission HTML classique.
- Côté serveur, `_kairos_action_response(request)` est la réponse commune des six
  handlers ci-dessus : si `request.headers.get("X-Requested-With") == "fetch"`,
  renvoie `render_kairos_response(request, fragment=True)` (le partiel jour, deux
  sessions fraîches) ; sinon `RedirectResponse("/kairos", status_code=303)` — le
  comportement historique, identique sans JS. Chaque handler doit committer et
  **fermer** sa propre session avant cet appel (voir invariant plus haut).
- `<select data-autosubmit>` : écouteur `change` délégué appelle
  `form.requestSubmit()` (ou `form.submit()` en repli si `requestSubmit`
  indisponible) — la soumission résultante est ensuite interceptée par l'écouteur
  `submit` générique ci-dessus si le formulaire porte aussi `data-ajax` (c'est le
  cas des deux formulaires de qualification de l'inbox).
- `initDayScripts(root = document)` : point de ré-initialisation, appelé une fois
  au chargement (`initDayScripts(document)`) et de nouveau après chaque swap
  (`initDayScripts(target)`). Ne gère **que** ce qui vit dans le sous-arbre
  remplacé et doit donc être rebranché : le minuteur vivant (élément `.mj-timer` +
  `setInterval`) et le câblage du bouton d'opt-in aux alertes
  (`#mj-alert-config`). **Tout le reste est délégué sur `document`**, branché une
  seule fois au chargement du script, et continue de fonctionner sur le contenu
  injecté sans reliaison : bascule du panneau d'édition, fermeture Échap,
  bascule des radios de capture, remplissage de durée par type/Fibo, autosubmit,
  l'intercepteur AJAX lui-même — un commentaire de code interdit explicitement
  de les dupliquer dans `initDayScripts`.
- **Repli sans JS** : chaque `<form data-ajax>` reste un `<form method="post"
  action="...">` HTML standard. JS désactivé (ou `fetch` en échec) → soumission
  navigateur normale → branche serveur sans `X-Requested-With` → redirection 303
  vers `/kairos`, identique au comportement pré-AJAX. Nécessaire pour la WebView
  Android (fiabilité JS non garantie) et l'accessibilité (navigation complète
  attendue par certains lecteurs d'écran) — justification explicite dans le
  commentaire d'en-tête du script.

### Modale d'édition (essentiels/avancé, bloqueurs en cases, Échap)

`edit_panel(task)` (macro, `templates/_kairos_macros.html`) : `<span
class="mj-edit">` (`display: contents` — n'interfère pas avec le flex layout du
`<li class="kairos-item">` parent) contenant un bouton `.mj-edit-toggle`
(crayon) et un `.mj-edit-body[hidden]`.

- **Bascule** : écouteur `click` délégué sur `document`, cible
  `.mj-edit-toggle`, bascule `hidden` sur le `.mj-edit-body` associé (trouvé via
  `.closest('.mj-edit')`), reflète l'état dans `aria-expanded`.
- **Calque plein écran quand ouvert** : le **même bouton**
  (`.mj-edit-toggle[aria-expanded="true"]`) devient `position: fixed; inset: 0;
  z-index: 79; background: rgba(22,32,43,.42)` — cliquer n'importe où en dehors
  de la carte referme le panneau, car la cible du clic ne matche alors plus
  `.mj-edit-toggle` à l'intérieur de la carte (qui reste au-dessus, `.mj-edit-body`
  à `z-index: 80`).
- **Glyphe ✕** : pseudo-élément `::after` du même bouton (seulement à l'état
  ouvert), positionné **à côté** du coin haut-droit de la carte
  (`left: calc(50% + min(320px, 46vw) + 10px)`, bascule à droite sous 700px) —
  jamais par-dessus la carte : un pseudo-élément ne peut pas peindre au-dessus
  d'une boîte empilée plus haut (`.mj-edit-body`, qui doit rester cliquable).
- **Piège évité, tracé explicitement** : aucune règle `:hover` sur ce bouton/son
  `::after` — une fois ouvert, il couvre tout l'écran, donc il serait « survolé »
  en permanence et resterait visuellement bloqué dans son état hover.
- **Échap** : écouteur `keydown` délégué sur `document`, cherche n'importe où sur
  la page un `.mj-edit-toggle[aria-expanded="true"]`, le ferme (même logique que
  le clic extérieur). Fonctionne indifféremment pour un panneau de tâche ou de
  créneau manuel (même classes réutilisées, phase 16).
- **Divulgation progressive** (deux niveaux, mêmes `name=` de champs qu'avant —
  `edit_task` inchangé) :
  - **Essentiels** (`.mj-edit-row.mj-edit-essentials`, toujours visibles) :
    Titre (premier champ, mis en évidence par `.mj-edit-form > label:first-child
    input`), Priorité (`range(0, 3)` → P0-P2), Points Fibo (`.mj-fibo-select`,
    échelle `FIBONACCI_SCALE`), Échéance, Durée (min, `.mj-estimated-minutes`).
    `fibo_help()` (légende de l'échelle, repliable) juste en dessous.
  - **Options avancées** (`<details class="mj-edit-advanced">`, repliées) :
    Description, Programmée pour (`scheduled_date`), Projet, Temps passé manuel,
    Récurrence + Jour du mois (visible seulement si `recurrence ==
    'monthly_on_day'` a du sens, champ toujours présent mais informativement lié),
    Type (`.mj-task-type-select`), Heure fixe (`pin_time`, + `pin_day` caché
    porteur du jour affiché — la date programmée prime si renseignée, sinon
    `pin_day`, sinon aujourd'hui, logique dans `edit_task`), Fiche liée
    (`linked_ticket_id`, `<select>` simple), Nouvelles sous-tâches (`<textarea
    name="new_subtasks">`, une ligne = une sous-tâche créée dans le même
    enregistrement), Bloqueurs.
- **Un seul formulaire, une seule route** (`POST /kairos/tasks/{id}/edit`, pas de
  `data-ajax`), un seul bouton « Enregistrer » — fusion actée en phases 5
  (épinglage) et 6 (sous-tâches en lot + bloqueurs) de `SPEC_KAIROS.md` : ce qui
  était plusieurs soumissions séparées est traité en une seule transaction côté
  route.
- **Bloqueurs en cases à cocher** (`.mj-blocker-checks`, un `<input
  type="checkbox" name="blocker_ids" value="{id}">` par tâche candidate hors
  elle-même) : même `name=` répété pour chaque case → `edit_task` reçoit la
  **liste complète** via `form.getlist("blocker_ids")` et la traite comme
  l'**ensemble cible** — diff calculé côté route (retrait = présent en base mais
  décoché ; ajout = coché mais absent, filtré par `would_create_cycle`, ignoré
  silencieusement en cas de cycle, sans faire échouer le reste de
  l'enregistrement). **Note de traçabilité (réconciliation de sources)** :
  `SPEC_KAIROS.md` phase 13 documente un remplacement temporaire de ces cases par
  un `<select name="blocker_ids" multiple>` (pour s'aligner sur le widget de
  « Fiche liée »). Le code actuel et `docs/DESIGN_SYSTEM.md` (« Écarts assumés »)
  confirment que la refonte GTD est **revenue** aux cases à cocher — motif
  documenté : « ni pilule ni chip à bascule, mais un HTML natif plus simple
  d'accès que le Ctrl-clic » d'un `<select multiple>`. L'état actuel (cases à
  cocher) est la décision qui prévaut ; ne pas revenir au `<select multiple>` sans
  rouvrir explicitement ce point. `linked_ticket_id`, lui, reste un `<select>`
  simple (une seule fiche liée possible).
- **Piège de spécificité CSS corrigé** : la règle générique `.mj-edit-form label`
  (`display: flex; flex-direction: column`, pour les champs texte/select) est
  plus spécifique qu'`.mj-check-label` seul et empilait la case au-dessus du
  texte au lieu de l'aligner à côté, pour les labels-cases du panneau d'édition
  (bloqueurs, « Bloc deep-work » du formulaire d'édition de créneau) — corrigé par
  `.mj-edit-form .mj-check-label { flex-direction: row; align-items: center;
  gap: 0.4rem; }` (sélecteur à deux classes, regagne la priorité).
- **Zone Supprimer/Archiver** (`.mj-edit-danger`) : séparée visuellement en pied
  de panneau (`border-top`), formulaire propre (pas `data-ajax`) avec
  confirmation JS native (`onsubmit="return confirm(...)"`) — `POST
  /kairos/tasks/{id}/delete` supprime si `task.source == 'native'`, archive
  (`status = 'archived'`) sinon (une tâche SP/GitLab resynchronisée serait
  recréée par la synchro si elle était supprimée en dur) ; nettoie aussi les
  `TaskDependency` où la tâche figure (bloquée ou bloquante) pour éviter des
  arêtes orphelines.

### Décisions et pièges tracés

1. **Bug d'opacité corrigé sur `.kairos-item.mj-blocked` — ne jamais réutiliser
   `opacity` ici.** `.kairos-item.mj-blocked` utilisait `opacity: 0.75` : une
   opacité sur ce `<li>` crée un **contexte d'empilement** qui atténue au rendu
   tout son sous-arbre, y compris le panneau d'édition (`.mj-edit-body`), qui y
   est imbriqué et passe en `position: fixed` **une fois ouvert** — `position:
   fixed` échappe à la mise en page de son ancêtre mais **pas** au *compositing*
   d'un ancêtre opaque à moins de 1 (piège CSS classique, commenté verbatim dans
   `static/style.css` juste avant la règle). Remplacé par des propriétés
   ciblées qui ne composent jamais les descendants : fond `background:
   var(--surface-tint)` + `border-style: dashed` sur le `<li>`, et
   `color: var(--text-3)` sur `.mj-title` seul (pas sur toute la ligne). Exemple
   canonique du workflow spec-d'abord de ce dépôt — **ne pas revenir à
   `opacity`** pour cette classe ni pour tout futur ancêtre d'un panneau
   `position: fixed`.
2. **Marge interne mobile — bloc placé délibérément en toute fin de
   `static/style.css`.** Le bloc `@media (max-width: 720px)` qui pose le padding
   latéral mobile (`.mj-capture`, `.mj-to-process`, `.mj-progress`,
   `.mj-now-card`, `.mj-timeline-card`, `.collapser`, `.mj-filter-form`,
   `.panel`, `.mj-week-day`, `.banner`, `.kairos-item`) est **le tout dernier
   bloc du fichier**. Raison tracée explicitement dans un commentaire juste
   au-dessus : la plupart de ces sélecteurs ont leur propre règle de padding
   **non conditionnelle** plus haut dans le fichier, à **spécificité égale**
   (simple sélecteur de classe) — en CSS, à spécificité égale, c'est la règle la
   plus tardive **dans le fichier** qui l'emporte, qu'elle soit dans un `@media`
   ou non. Un bloc placé plus tôt (par exemple juste après le premier `@media
   (max-width: 720px)` de `.topnav`/`.page`) serait donc **silencieusement
   écrasé**. Toute nouvelle règle de padding non conditionnelle sur l'un de ces
   sélecteurs doit être ajoutée **avant** ce bloc final, jamais après. Le même
   bloc autorise aussi le retour à la ligne de `.badge.at_risk`/`.badge.bad`
   quand ils portent une phrase longue (créneau repoussé, conflit d'épinglage,
   motif de blocage) : le `white-space: nowrap` générique du badge leur ferait
   déborder la carte sur les écrans les plus étroits (~375px), corrigé par
   `white-space: normal; text-align: left;` scopé à ces deux classes dans ce
   même bloc.
3. **Exceptions crème/ambre assumées, à ne pas « corriger ».** `.mj-progress`
   (« Maintenant ») et `.mj-to-process` (boîte de réception) partagent le même
   traitement — fond `#FFFAF1` + puce ambre `border-left: 3px solid
   var(--warn-fg)` — et fusionnent la classe utilitaire avec `.card` sur le même
   élément (pas de `<div>` imbriqué), pour que le fond suive les coins arrondis.
   `.mj-next` (la ligne « À faire maintenant : ... » dans `.mj-progress`) est la
   seule ligne de l'app en Newsreader italique 19px/500 (police chargée en plus
   d'IBM Plex Sans). Ces trois exceptions sont des décisions produit explicites,
   documentées et non dupliquées ici — voir `CLAUDE.md` (racine) et
   `docs/DESIGN_SYSTEM.md` § Couleurs/Typographie pour la charte complète et le
   raisonnement. Ne pas les généraliser à d'autres cartes/badges/titres, ne pas
   les « corriger » vers le bleu/neutre standard.

### Invariants et garde-fous

- Un seul `id="mj-day-content"` dans toute l'application, posé uniquement par
  `kairos.html` — jamais par `_kairos_day.html` (qui doit rester rendable, tel
  quel, comme fragment autonome).
- Toute action mutante ouvre sa **propre** session `tasks_session`, committe,
  **ferme** cette session avant d'appeler `_kairos_action_response`/
  `render_kairos_response` (qui rouvrent des sessions fraîches) — jamais de
  session imbriquée. À respecter pour toute nouvelle route d'action.
- Toute nouvelle action « rapide » (bascule d'état simple, candidate à
  l'amélioration AJAX) doit : poser `data-ajax` sur son `<form>`, retourner
  `_kairos_action_response(request)` en fin de handler, committer/fermer sa
  session avant cet appel. Une action qui ouvre une vue radicalement différente
  (édition complète, création, suppression) reste en redirection 303 pure,
  cohérent avec l'usage actuel (aucune de ces six routes ne porte `data-ajax`).
- Le formulaire de filtres reste en **GET**, jamais `data-ajax` : c'est une
  navigation dont l'état vit dans l'URL (bookmarkable), pas une mutation.
- Les champs de qualification rapide de l'inbox utilisent **exclusivement**
  `data-autosubmit` (jamais `.mj-fibo-select`/`.mj-task-type-select`) pour ne
  jamais déclencher le remplissage automatique de durée réservé au panneau
  d'édition complet.
- Les routes d'édition/suppression de créneau ne doivent agir **que** sur
  `TimeBlock.source == 'manual'` (garde-fou déjà en place côté route) — les
  créneaux TimeTree sont transitoires, jamais persistés.
- `edit_task` traite `blocker_ids` comme l'**ensemble cible complet** à chaque
  soumission (pas un delta implicite envoyé par le client) — toute évolution du
  panneau doit respecter ce contrat déjà en place côté route.
- Ne jamais ajouter de règle `:hover` sur `.mj-edit-toggle[aria-expanded="true"]`
  ni sur son `::after` (piège déjà rencontré : le bouton couvre l'écran une fois
  ouvert).
- Ne jamais réintroduire `opacity` sur `.kairos-item.mj-blocked`, ni sur tout
  futur ancêtre CSS d'un élément `position: fixed` de cette page (le panneau
  d'édition en particulier) — utiliser des propriétés ciblées (fond, couleur de
  texte) qui ne composent pas les descendants.
- Le bloc CSS de marge mobile en fin de `static/style.css` doit **rester en fin
  de fichier** — toute nouvelle règle de padding non conditionnelle pour l'un de
  ses sélecteurs doit être insérée avant lui.
- `initDayScripts` ne doit reprendre **que** ce qui vit dans le sous-arbre
  remplacé par un swap (chrono, opt-in alertes) — tout ce qui est délégué sur
  `document` au chargement du script ne doit jamais y être dupliqué.
