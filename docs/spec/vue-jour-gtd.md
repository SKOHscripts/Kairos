# Vue Jour & flux GTD

_Rôle : la vue Jour (`GET /kairos`, page par défaut de l'application) est le poste de
pilotage quotidien de Kairos ; capture sans friction, clarification GTD (boîte de
réception), puis exécution ordonnée (WSJF) de la journée. Cette spec couvre
l'**ergonomie/UI** de cette vue et son **contrat de rendu** (page pleine vs fragment
AJAX), pas les moteurs de calcul sous-jacents._

**Fichiers couverts** :
- `templates/kairos.html` : page pleine (`{% extends "base.html" %}`) : branche
  vue Semaine inline, branche vue Jour via `{% include "_kairos_day.html" %}`, et
  tout le JS inline (`{% block scripts %}`).
- `templates/_kairos_day.html` : partiel de la vue Jour (capture → inbox →
  « Maintenant » → bannières → filtres/backlog → agenda + sections secondaires →
  colonne latérale).
- `templates/_kairos_macros.html` : macros partagées : `done_toggle`,
  `task_actions`, `time_spent`, `fibo_help`, `edit_panel`, `task_key_badges`,
  `task_tags`, `task_description`.
- `templates/_kairos_banners.html`, `templates/_kairos_filters.html`,
  `templates/_kairos_backlog.html` : partiels `{% include %}` (contexte propagé
  tel quel, jamais de macro).
- `app/main.py` : `_build_kairos_context`, `render_kairos_response`,
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
`base.html`/topnav/accueil → `docs/spec/accueil-navigation.md` ; capture GTD en
amont de la boîte de réception (page Notes, `/kairos/notes`) →
`docs/spec/notes-capture.md`.

---

## 1. Besoin métier (cahier des charges)

### Objectif / problème

Kairos est un outil mono-utilisateur : la vue Jour doit répondre, à l'ouverture,
sans recouper mentalement plusieurs écrans, à trois questions ; qu'est-ce qui
n'est pas encore clarifié, qu'est-ce que je fais maintenant, qu'est-ce qui vient
ensuite. Le produit a grossi par 18 phases successives (`SPEC_KAIROS.md`) ; la
phase 5 constatait déjà « six sections de liste empilées [...] sans hiérarchie
visuelle entre une tâche en retard et une tâche normale : l'ordre du tri porte
toute la charge de signal, rien à l'œil ». Une refonte (désignée dans le code par
« Refonte Jour v2 », commentaire `static/style.css`) a réorganisé la page
explicitement autour du cycle **GTD** (*Getting Things Done*) **capturer → traiter
la boîte de réception → faire**, décrit dans `docs/DESIGN_SYSTEM.md` § « Architecture
de l'information : vue Jour (flux GTD) ».

Deux contraintes transverses structurent toute décision d'UI de cette vue :
- l'app tourne en navigateur desktop, en exécutable PyInstaller offline et en
  WebView Android packagée (APK) : le JavaScript ne peut donc jamais être un
  prérequis fonctionnel, seulement une amélioration progressive ;
- l'app est mono-utilisateur et locale : pas de sync temps réel entre onglets, la
  cohérence entre un swap AJAX et un rechargement complet doit être totale (même
  gabarit, même contexte serveur).

### Comportement attendu (utilisateur, section par section)

Ordre vertical de la vue Jour (de haut en bas), chaque section correspondant à une
étape du flux GTD ou à un utilitaire secondaire :

1. **Capture** (`.mj-capture`) : toujours visible, jamais dans un `<details>`
   replié. Deux volets par onglet radio : « Tâche » (titre seul, capture
   volontairement sans friction) et « Créneau / deep work » (titre + horaires +
   case deep-work + récurrence, avec en dessous la liste éditable des créneaux du
   jour). Un seul bouton bleu (`.btn.primary`) par volet.
2. **Boîte de réception** (« À traiter », `#mj-inbox`) : juste sous la capture.
   Toute tâche sans priorité **ou** sans points Fibonacci, quelle que soit son
   origine (native, GitLab assigné...), y échoue et n'entre dans **aucun** tri tant
   qu'elle n'est pas qualifiée. Qualification en un ou deux clics, en ligne
   (sélection priorité + sélection points), sans ouvrir l'édition complète. État
   vide affiché explicitement (jamais la section qui disparaît).
3. **« Maintenant »** (`.mj-progress`) : la tâche actionnable suivante, avec ses
   actions directes (fait / chrono / décaler) et les statistiques de la journée
   (faites, à faire, requis vs disponible, débordement, temps déjà travaillé
   aujourd'hui ventilé par type, indications calendrier). Élément principal de la
   page, jamais repliable.
4. **Bannières d'alerte** (TimeTree, import GitLab, surcharge de priorité) : sous
   « Maintenant », jamais en tout premier : ce sont des avertissements de
   dégradation d'intégrations externes, pas le point d'entrée du flux.
5. **Agenda ordonné** (« Aujourd'hui, dans l'ordre ») : la liste centrale, triée
   par score WSJF, toujours dépliée, juste sous « Maintenant » et les bannières.
6. **Sections secondaires condensées** (Sans créneau / Bloquées / Programmées plus
   tard / Mères en cours / Fait), chacune avec un compte et une courte phrase de
   rôle dans son `<summary>`. Repliées par défaut, **sauf « Sans créneau
   aujourd'hui »**, dépliée dès qu'elle contient une tâche.
7. **Filtres compacts** et **Backlog** (sans échéance ni date programmée) :
   utilitaires secondaires, repliés par défaut, en bas de la colonne principale.
   Exception : quand un filtre est actif, le contrôle de filtrage remonte en tête
   des listes, pour que l'utilisateur voie pourquoi elles sont réduites.
8. **Colonne latérale** : carte « En ce moment » (chrono en cours) + carte
   « Agenda » (timeline verticale heure par heure de la journée).

**Fluidité (audit UI) : trois décisions rouvertes avec l'utilisateur.** Elles
étaient actées ailleurs dans cette spec ; l'utilisateur les a explicitement
rouvertes lors de l'audit d'interface :

- **Ordre des sections.** Auparavant, recherche et backlog (repliés) venaient
  entre « Maintenant » et la liste du jour : deux utilitaires repliés
  repoussaient la liste principale plus bas que nécessaire. La liste du jour
  passe désormais directement sous « Maintenant ».
- **« Sans créneau aujourd'hui » dépliée.** Ce sont des tâches à faire
  aujourd'hui qu'aucun créneau n'a pu accueillir : les cacher par défaut les
  faisait oublier (sept tâches invisibles dans le jeu d'audit). Les autres
  sections secondaires restent repliées.
- **Capture de tâche sans rechargement.** Ajouter une tâche rechargeait toute
  la page et perdait le curseur. Désormais la tâche apparaît dans la boîte de
  réception sans rechargement, et le curseur reste dans le champ de capture :
  l'utilisateur enchaîne plusieurs captures au clavier. Même mécanique que la
  page Notes, qui le faisait déjà. La création d'un créneau garde la
  redirection classique.

**Actions nommées là où elles comptent.** La carte « Maintenant » porte les
trois actions de la prochaine tâche avec leur nom (« Fait », « Démarrer le
chrono », « Décaler »), pas seulement une icône. Dans les listes, les icônes
restent seules (densité) ; celle de « décaler au prochain jour ouvré » ne
ressemble plus à une flèche « ouvrir » (›), ambiguë.

**Raccourcis clavier.** `N` place le curseur dans la capture de tâche, `/`
ouvre la recherche et y place le curseur. Inactifs pendant la saisie dans un
champ. Chaque raccourci est signalé à côté de son contrôle.

Chaque ligne de tâche, quelle que soit la section, partage le même vocabulaire
visuel : coche ronde en tête de ligne, bordure gauche colorée par palier
d'urgence, badges (score WSJF, priorité, type, points, fiche liée, temps passé,
« traîne depuis... »), actions à droite (chrono, décaler), crayon d'édition.

**Anatomie d'une ligne de tâche : quatre colonnes fixes (issue #33).** Avec
beaucoup de tâches de longueurs différentes, une ligne « tout à la suite »
devient illisible : chaque tâche place ses actions à un endroit différent selon
le nombre de badges qu'elle porte, et un titre long chasse tout le reste. La
ligne suit donc une **grille invisible** (l'utilisateur ne voit aucun trait, il
voit des colonnes qui s'alignent d'une ligne à l'autre) :

1. **Coche** : largeur fixe, toujours en tête.
2. **Corps** : occupe l'espace restant, jamais plus : l'heure et le titre en
   première ligne, puis les **étiquettes** de contexte (projet, type, fiche
   liée, échéance, durée, « traîne depuis… », notes de placement…) qui
   s'empilent en dessous en s'enroulant sur plusieurs lignes si nécessaire, puis
   l'extrait de description. Un titre long ou dix étiquettes font grandir cette
   colonne **vers le bas**, jamais vers la droite.
3. **Priorité et points** : collés à la colonne d'actions, alignés à droite :
   d'une ligne à l'autre, ils tombent toujours au même endroit, ce qui rend la
   liste balayable d'un seul coup d'œil vertical. Le score WSJF les accompagne
   (c'est lui qui ordonne la liste ; avec la priorité, ce sont les deux seuls
   badges à porter l'accent, voir `docs/DESIGN_SYSTEM.md`).
4. **Actions** : chrono, décaler, crayon d'édition : toujours tout à droite, à
   la même abscisse quelle que soit la tâche.

Sur écran étroit, la colonne « priorité et points » passe sous le corps plutôt
que de comprimer le titre ; les actions restent en haut à droite.

**Comprendre sans quitter la liste (audit UI).** Kairos repose sur deux
jugements que l'utilisateur doit poser lui-même : une **priorité** et une
**taille en points** : puis trie à sa place. Les deux restaient opaques : la
priorité n'avait de définition nulle part (seulement des poids internes), les
points se choisissaient dans un menu « — / 1 / 2 / 3… » sans repère, et
l'ordre de la liste ne s'expliquait qu'au survol d'un badge, donc jamais sur
mobile ni dans l'APK Android. Quatre réponses :

- **Le sens des priorités est défini, une fois, et affiché partout où l'on
  choisit** : **P0 Critique**, bloquant ou engagement ferme, rare par nature
  (le bandeau de surcharge le rappelle) ; **P1 Important**, à caser cette
  semaine ; **P2 Utile**, à faire quand il y a de la place. Ce
  sont des degrés d'**importance**, pas de délai : l'urgence est déjà portée par
  l'échéance dans le score, des libellés de délai auraient fait doublon.
- **Qualifier se fait en un clic, sens compris.** Dans la boîte de réception
  comme dans le panneau d'édition, priorité et points se choisissent sur une
  rangée de pastilles (P0 · P1 · P2 ; 1 · 2 · 3 · 5 · 8 · 13 · 21) dont chacune
  porte son sens en clair ; lisible aussi au toucher, jamais seulement dans une
  infobulle. Un clic pose la valeur, au lieu d'ouvrir un menu puis de choisir.
  Cela fonctionne sans JavaScript.
- **L'estimation en points s'appuie sur l'historique de l'utilisateur.**
  L'estimation relative ne fonctionne qu'avec des points de repère ; Kairos en
  a : les tâches déjà terminées. Pour chaque palier, le guide donne sa
  description, le temps réel médian observé chez l'utilisateur (« chez toi,
  3 pts ≈ 45 min, sur 6 tâches ») et un ou deux exemples de ses propres tâches
  terminées à ce palier. Estimer devient une comparaison concrète (« plus gros
  que celle-ci, plus petit que celle-là »). Sans historique à un palier, le
  guide le dit, sans rien inventer.
- **« Pourquoi à cette place ? »** Le score d'une tâche s'ouvre d'un clic (ou
  d'un toucher) sur sa décomposition : la valeur de sa priorité, ce que son
  échéance ajoute, l'effort qui divise le tout, et le calcul. Une tâche en
  retard le dit aussi : elle passe devant quel que soit son score.

**Lien vers la fiche d'origine d'une tâche importée.** Une tâche importée de
GitLab porte déjà son projet en étiquette : cette étiquette devient un **lien
cliquable vers l'issue d'origine** (issue #33), ouvert dans un nouvel onglet.
Règle de construction de l'URL et dégradation quand elle n'est pas calculable →
`docs/spec/integrations-externes.md`.

**Description d'une tâche.** Une tâche qui porte une description l'annonce
**dans la liste**, sans qu'il faille ouvrir l'édition : un extrait d'une ligne,
atténué, sous le titre, dépliable sur place au clic pour lire le texte complet
(retours à la ligne préservés). Motivation (issue #32) : la description était
jusque-là enfouie sous deux niveaux de divulgation (ouvrir l'édition, **puis**
déplier « Options avancées ») ; une information réellement utile y devenait
invisible faute d'avoir le réflexe d'aller la chercher, et se perdait en
pratique. Corollaire dans le panneau d'édition : la Description remonte parmi
les champs **essentiels**, juste sous le Titre, et ne fait plus partie des
options avancées. Une tâche sans description n'affiche rien de plus qu'avant
(aucun marqueur vide, aucune ligne en plus).

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
- Capturer trois tâches d'affilée se fait au clavier seul, sans rechargement de
  page et sans recliquer dans le champ.
- La liste « Aujourd'hui, dans l'ordre » s'affiche avant la recherche et le
  backlog ; un filtre actif reste visible au-dessus des listes qu'il réduit.
- Une tâche avec description affiche son extrait dans la liste de la vue Jour
  sans ouvrir l'édition, et son texte complet après un seul clic ; une tâche
  sans description n'ajoute aucun marqueur à sa ligne.
- Deux tâches voisines dont l'une porte dix étiquettes et l'autre aucune
  affichent leurs actions, leur priorité et leurs points **à la même abscisse** :
  ajouter une étiquette à une tâche ne déplace jamais ses boutons.
- Un titre long fait grandir sa ligne vers le bas ; il ne pousse jamais la
  priorité, les points ou les actions hors de leur colonne, et ne provoque
  jamais de défilement horizontal (y compris à ~375px de large).
- Poser la priorité ou les points d'une tâche de la boîte de réception prend
  **un** clic, et le sens de chaque valeur est lisible sans survol.
- Le guide des points affiche, pour chaque palier où l'utilisateur a terminé
  des tâches chronométrées, le temps réel médian et au moins un exemple tiré de
  ses propres tâches.
- Le score d'une tâche s'explique au toucher, sans quitter la liste.
- Le champ Description du panneau d'édition est atteignable sans déplier
  « Options avancées ».

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
- La page Notes (`/kairos/notes`), mécanisme de capture GTD **en amont** de la
  boîte de réception décrite ci-dessous (§ 2) : une note se convertit en tâche
  titre-seul, qui atterrit alors dans cette même boîte de réception, mais la
  capture, l'édition et l'archivage d'une note n'ont pas leur place ici →
  `docs/spec/notes-capture.md`.

---

## 2. Solution technique

### Architecture de rendu

- `kairos.html` étend `base.html`, importe les macros partagées `with context`
  (`{% from "_kairos_macros.html" import ... with context %}`), et branche sur
  `view` (query param) : `view == 'week'` rend la grille semaine **inline** dans
  `kairos.html` lui-même ; sinon un unique `<div id="mj-day-content">` enveloppe
  `{% include "_kairos_day.html" %}` : **`#mj-day-content` n'existe qu'à cet unique
  endroit dans toute l'app** (invariant explicite, commenté dans le fichier).
- `_kairos_day.html` est rendu **deux fois** selon le chemin d'appel : (a) inclus
  dans `kairos.html` pour la page pleine, à l'intérieur de l'enveloppe
  `#mj-day-content` ; (b) rendu **directement, sans enveloppe**, par
  `render_kairos_response(fragment=True)` pour les réponses AJAX : le fragment
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
  fonction, qui rouvre des sessions fraîches, invariant documenté explicitement
  dans le code, jamais de session imbriquée.
- `templates/_kairos_macros.html` est importée `with context` par **les deux**
  gabarits (`kairos.html` et `_kairos_day.html`) plutôt que l'un depuis l'autre,
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
  du jour : elle s'y perdrait.

### Le flux GTD, section par section

**1. Capture** (`_kairos_day.html` lignes ~17-90, classe `.mj-capture`) : deux
formulaires HTML classiques, jamais `data-ajax` (navigation complète à la
soumission, y compris JS actif) :
- `POST /kairos/tasks` (`create_native_task`) : titre seul requis. Gère aussi la
  création d'une **sous-tâche unique** si un `parent_id` est fourni (pas utilisé
  par ce formulaire de capture, mais par le même endpoint depuis ailleurs) ; la
  mère est ignorée silencieusement si elle a disparu entre-temps. Depuis l'audit
  UI, le formulaire de capture (`#mj-task-capture`) porte `data-ajax` et la
  route répond par `_kairos_action_response` : fragment en AJAX, redirection
  303 sinon. Le formulaire vivant dans le fragment remplacé, le script replace
  le curseur dans le nouveau champ titre après le swap
  (`focus({preventScroll: true})`) ; le champ revient vide puisqu'il est
  re-rendu. Le formulaire de créneau (`POST /kairos/blocks`) garde la
  redirection classique.
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
  pertinentes pour le jour affiché ; les ponctuels du jour **et** les modèles
  récurrents dont `expand_recurring_blocks([b], target_day, target_day)` produit
  une occurrence ce jour-là (aligné sur ce que montre la timeline). Éditer un
  créneau récurrent porte donc sur le **modèle**, donc sur **toutes** ses
  occurrences (phase 16) : signalé par un badge `.badge.info` « récurrent » (avec
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
`.mj-inbox-empty` conditionnelle) : variable de contexte `visible_to_process`
(= `schedule.to_process` filtré par `_visible()`, recherche/facettes). Chaque
ligne affiche un badge `.badge.warn` dont le libellé distingue les trois cas :
« priorité et points manquants », « priorité manquante », « points manquants ».
Qualification en ligne (pastilles, audit UI) :
- Deux formulaires `.mj-pills-form[data-ajax]` par ligne, rendus par
  `priority_pills()` et `points_pills()` (`_kairos_macros.html`) : `POST
  /kairos/tasks/{id}/priority` (`update_task_priority`) et `POST
  /kairos/tasks/{id}/points` (`update_task_points`). Chaque valeur est un
  `<button type="submit" name="priority|points" value="…" class="mj-pill">`
  qui affiche son code et son libellé (« P0 Critique », « 3 modéré ») et porte
  sa définition en `title`. La valeur déjà posée porte `.is-on` et
  `aria-pressed="true"`. Un clic enregistre, sans JavaScript requis (un vrai
  clic de bouton inclut nativement `name`/`value` dans la requête).
- Les pastilles vivent dans une **cinquième zone nommée** de la grille,
  `qualify` (`<li class="kairos-item has-qualify">`, `.mj-inline-qualify`
  enfant direct), sur sa propre rangée : dans la colonne du corps, elles
  n'auraient eu qu'environ 150px à 390px de large. Sous 720px la zone prend
  toute la largeur de la carte, coche comprise, et chaque pastille fait 44px
  de haut (cible tactile de la charte).
- Ces pastilles ne portent **jamais** `.mj-fibo-radio` ni `data-avg-minutes` :
  qualifier en ligne ne touche pas à la durée estimée (réservé au panneau
  d'édition, voir § Modale d'édition).
- L'état vide (`.mj-inbox-empty`) réduit le padding vertical et affiche
  `.mj-inbox-empty-msg` (« Rien à traiter : tout est déjà clarifié ») au lieu de
  faire disparaître la section, rappel volontaire de « où regarder en premier ».
- Une aide repliable (`.mj-help`, motif réutilisé de `fibo_help()`) explique le
  principe GTD directement dans le `<summary>` (« pourquoi qualifier ? »).
- **Alimentation en amont, hors de cette spec** : une tâche titre-seul peut venir
  directement de la capture de cette vue (§ 1) **ou** d'une conversion depuis la
  page Notes (`POST /kairos/notes/{id}/convert`, `docs/spec/notes-capture.md`),
  les deux chemins produisent le même objet (`Task(source="native")`, ni
  priorité ni points), indiscernable ici une fois créé.

**3. « Maintenant »** (`.mj-progress`), `next_up_task` (calculé côté serveur) =
`schedule.scheduled[0].task` si l'agenda a une première entrée planifiée, sinon
`schedule.unscheduled[0]` si la liste non planifiée n'est pas vide, sinon `None`,
commentaire de code : « il y a toujours un "prochain pas" ». Affiche
`done_toggle`/`task_actions` directement sur cette tâche (fait, chrono, décaler)
sans que l'utilisateur ait à la retrouver dans la liste plus bas. Ces
actions y sont **nommées** (`done_toggle(task, labelled=true)`,
`task_actions(task, labelled=true)` : boutons `.btn.sm` avec icône et
libellé « Fait », « Démarrer le chrono » ou « Arrêter le chrono »,
« Décaler ») ; dans les listes, les mêmes macros sans `labelled` gardent les
icônes seules (`.icbtn`), pour la densité. L'icône « décaler au prochain jour
ouvré » est `skip_forward` (deux chevrons et une barre), le chevron simple se
lisant comme « ouvrir ». Ligne de titre
en Newsreader italique (`.mj-next`, exception de police assumée : voir « Décisions
et pièges tracés »). Bloc de statistiques (`.mj-progress-stats`) : compte de
tâches faites/à faire, `required_str`/`available_str` (temps requis vs
disponible), `spent_total_str` (temps travaillé **aujourd'hui uniquement** ;
correction d'un bug de scope temporel tracée en phase 7 de `SPEC_KAIROS.md`),
ventilation `spent_by_type_today`, badge de débordement si
`schedule.stats.overflow_minutes > 0`. Ligne `indication_events` (événements
TimeTree journée-entière/multi-jours, phase 12 : simple puce datée, jamais un
obstacle horaire). Bloc `#mj-alert-config` (attributs `data-idle`/`data-pomodoro`)
+ bouton d'opt-in : sert uniquement de point d'ancrage DOM pour le script du
chrono (détail dans `docs/spec/temps-reel-chrono.md`), pas de logique propre à
cette spec.

**4. Bannières** (`_kairos_banners.html`) : trois conditions indépendantes,
chacune `.banner.warning`. TimeTree (`timetree_configured and not
timetree_ok`, silencieux si non configuré, phase 18), import GitLab direct
(`gitlab_direct_error` non vide), surcharge de priorité maximale
(`priority_overload_count > priority_overload_threshold`). Position fixe : sous
« Maintenant », avant la liste du jour ; documentée comme volontaire (« ce ne sont que
des avertissements de dégradation, pas le point d'entrée du flux »).

**5. Filtres compacts** (`_kairos_filters.html`) : `<details class="card
mj-filter-compact">`, `open` seulement si `filter_active` (recherche ou une
facette posée ; booléen calculé une fois dans `_build_kairos_context`).
**Position** (audit UI) : en bas de `.mj-day-main`, après les sections
secondaires et avant le Backlog, sauf si `filter_active` : le partiel est alors
inclus en tête, juste avant `.mj-day-grid`, pour que l'utilisateur voie
pourquoi les listes sont réduites. Jamais les deux à la fois. La vue Semaine
garde filtres et backlog au-dessus de sa grille. Formulaire **GET** (pas `data-ajax` : c'est une navigation avec
état porté par l'URL, pas une mutation), champs cachés `view`/`start` pour
préserver la vue/le jour courants. Facettes : priorité (`range(0, 3)`, donc
P0/P1/P2 uniquement ; même échelle réduite que partout ailleurs dans cette vue),
projet (dynamique, `project_choices`), type (`settings.task_type_list`), points
Fibonacci (`fibonacci_scale`). Un seul marqueur visible quand un filtre est actif
(`.badge.info` « filtre actif » dans le `<summary>`) plutôt que les 5 champs
déployés en permanence ; lien « Réinitialiser » vers l'URL sans query params (sauf
`view`/`start`). Filtre d'affichage pur : `_visible()` (dans
`_build_kairos_context`) ne touche jamais l'ordonnancement lui-même, seulement les
listes affichées.

**6. Agenda ordonné** : `<details class="card" open>`, seule section de la vue
Jour (hors « Maintenant ») **dépliée par défaut**. Liste `<ol>` (seule liste
ordonnée sémantiquement de la page : l'ordre porte l'information). Chaque `<li
class="kairos-item mj-bucket-{{ bucket_of[...] }}">` suit la grille de quatre
cellules décrite plus bas (§ Ligne de tâche) : coche, puis le corps
(`.mj-item-head` = heure + titre avec fil d'Ariane `{{ parent_title_of }} › `
si sous-tâche ; `.mj-item-tags` = badges conditionnels
épinglée/deep-work/chemin-critique, `task_tags`, `time_spent`, notes
`pushed`/`dip`/`conflict` ; puis `task_description`), puis `task_key_badges`,
puis `task_actions` + `edit_panel`.

**7. Sections secondaires condensées** : chacune un `<details class="card">`,
rendue seulement si sa liste est non vide : Sans créneau aujourd'hui, Bloquées,
Programmées plus tard, Tâches mères en cours, Fait. Repliées par défaut (sans
attribut `open`), **sauf « Sans créneau aujourd'hui »** (`open`, audit UI, voir
plus bas). Chaque `<summary class="collapser">` porte un compte et une
phrase de rôle (`.hint`). Note de traçabilité : `SPEC_KAIROS.md` phase 5 décrivait
« les autres sections actionnables (sans créneau, bloquées, mères en cours)
restent dépliées », seule « Fait » étant repliée par défaut à l'époque. La refonte
GTD ultérieure (code actuel, confirmée par `docs/DESIGN_SYSTEM.md` § « Sections
secondaires condensées ») a **replié toutes** ces sections par défaut, y compris
celles que la phase 5 gardait ouvertes. **Rouverte avec l'utilisateur lors de
l'audit UI**, sur la seule section « Sans créneau aujourd'hui » : ce sont des
tâches à faire aujourd'hui qu'aucun créneau n'a pu accueillir, et repliées on
les oubliait. Les quatre autres restent repliées (densité en tête de page).

**Marge du contenu des sections repliables** : `details.card` n'a pas de marge
intérieure (seul son `<summary class="collapser">` en a une). Ses enfants
directs `p` et `.kairos-list` reçoivent donc la marge horizontale du résumé
(1.1rem, 0.7rem sous 720px), sans quoi la phrase d'aide touchait le bord et
chaque ligne doublait la bordure de la carte. Défaut antérieur, resté discret
tant que ces sections étaient toutes repliées.

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

### Ligne de tâche : grille de quatre colonnes

`.kairos-item` est une **grille CSS nommée** (issue #33), plus un `flex-wrap`
plat :

```
grid-template-columns: auto minmax(0, 1fr) auto auto;
grid-template-areas:   "check main key actions";
```

| Cellule | Classe | Contenu |
| --- | --- | --- |
| `check` | `.mj-item-check` (posée sur le `<form>` de `done_toggle()`) | La coche ronde. |
| `main` | `.mj-item-main` | `.mj-item-head` (heure + titre), `.mj-item-tags` (étiquettes), la qualification en ligne pour l'inbox, `task_description()`. Colonne en `flex-direction: column`. |
| `key` | `.mj-item-key` | `task_key_badges()` : score WSJF, priorité, points. `justify-content: flex-end`. |
| `actions` | `.mj-item-actions` | `task_actions()` + `edit_panel()`. |

**Pourquoi `minmax(0, 1fr)` et non `1fr`** : une piste `1fr` a pour taille
minimale `auto`, donc elle refuse de descendre sous la largeur intrinsèque de
son contenu ; un titre long ferait déborder la carte au lieu de passer à la
ligne. Même raison pour les `min-width: 0` posés sur `.mj-item-main`,
`.mj-item-head .mj-title`, `.mj-item-tags` et `.mj-desc` : une boîte flex a la
même taille minimale automatique.

**Alignement vertical** : la grille est en `align-items: start` (et non
`center`, qui ferait flotter la coche au milieu d'une tâche à titre long +
étiquettes + description). Les trois colonnes fixes portent `min-height: 28px`
(hauteur d'un `.icbtn`) et centrent leur contenu dedans : elles s'alignent donc
sur la **première ligne** du corps, quelle que soit la hauteur totale de la
ligne.

**`.mj-item-head` n'a délibérément pas de `flex-wrap`** (contrairement à
`.mj-item-tags`) : un titre long doit commencer *à côté* de l'heure et se
poursuivre en dessous ; retour à la ligne **interne** au titre, via `.mj-title
{ flex: 1 1 auto; min-width: 0 }`. Avec `flex-wrap`, le titre basculerait en
bloc sous l'heure, ce qui gâche une ligne entière.

**Cellule vide masquée par `:not(:has(*))`, pas par `:empty`** : les macros
Jinja laissent des retours à la ligne dans une cellule sans badge, et un nœud
texte blanc suffit à faire échouer `:empty`. La cellule restait affichée et
ajoutait un espacement fantôme.

**Deux macros, pas une** : `task_key_badges()` (les signaux de **tri** : score
WSJF, priorité, points ; les deux premiers sont les seuls badges à accent, voir
`docs/DESIGN_SYSTEM.md`) et `task_tags()` (le **contexte** : projet, type,
fiche liée, durée, échéance, date programmée, récurrence, « traîne depuis… »).
La scission est ce qui permet à la colonne « clés » d'être stable d'une ligne à
l'autre : tout ce qui est de longueur imprévisible vit dans le corps. Les
badges propres à une **section** (épinglée, deep work, chemin critique, créneau
repoussé, creux de l'après-midi, conflit, motif de blocage, avancement n/m,
avertissement de l'inbox) sont rendus par la section elle-même, toujours dans
`.mj-item-tags`.

**Écran étroit (≤ 720px, même point de rupture que le reste de l'app)** : la
grille passe à trois colonnes et la cellule `key` bascule sur une seconde
ligne, alignée à gauche sous le corps ; coche, corps et actions gardent leur
place, donc les actions restent à la même abscisse d'une ligne à l'autre,
l'essentiel de l'issue #33. Vérifié à 375px : aucun débordement horizontal.

**Vue semaine exclue** : `.mj-week-day .kairos-item` revient explicitement à
`display: flex`. La ligne n'y porte qu'un titre (déjà tronqué en ellipse) et un
projet, sans coche, sans actions, sans priorité : la grille n'y aurait que des
pistes vides à aligner.

### Comprendre les valeurs et l'ordre (audit UI)

**Source unique du sens** : `app/task_guide.py`. `PRIORITY_LEVELS` (code,
libellé, définition : P0 Critique, P1 Important, P2 Utile) et
`FIBONACCI_GUIDE` (points, libellé, définition, exemple générique, un par
palier de `FIBONACCI_SCALE`), exposés aux gabarits en globales Jinja avec les
recherches `priority_level()` et `fibo_level()`. Lus par les pastilles, l'aide
de la boîte de réception, le guide d'estimation et les `title` des badges P
et points. Deux tests vérifient que ces tables couvrent exactement l'échelle
du modèle et celle de l'ordonnanceur (`_PRIORITY_MAX`). Des degrés
d'importance et non de délai : l'échéance pèse déjà dans le score
(`_time_criticality`).

**Aide « comment qualifier ? »** (en-tête de la boîte de réception) : pourquoi
qualifier, sens des priorités (`priority_help()`), puis le guide d'estimation.

**Guide d'estimation ancré** (`fibo_help()`) : pour chaque palier, sa
définition, puis les repères de `fibo_refs`
(`tasks_stats.fibonacci_references`, voir `statistiques.md`) : temps réel
médian chez l'utilisateur avec l'effectif, marqué « peu fiable » sous
`MIN_SAMPLE`, et les titres de ses deux tâches terminées les plus récentes à
ce palier. Sans historique à un palier : l'exemple générique et la mention
« aucune de tes tâches terminées à ce palier pour l'instant ». Filtre Jinja
`duree` (même format que `_fmt_minutes`).

**« Pourquoi à cette place ? »** (`score_explained()`) : le badge du score est
le `<summary>` d'un `<details class="mj-why">` ; le panneau `.mj-why-body`
détaille les termes de `why_of[task.id]`
(`tasks_scheduling.wsjf_breakdown`, voir `ordonnancement.md`) : valeur de la
priorité, criticité avec la date la plus proche (« Échéance dépassée de
12 j », « Date programmée demain », « Aucune échéance »), effort et sa
provenance (points, déduit de la durée, par défaut), puis le score. Une tâche
en retard affiche en tête qu'elle passe devant quel que soit son score (palier
dur du tri). Filtre Jinja `nombre` : une décimale au plus, sans « .0 ».
S'ouvre sans JavaScript ; le script referme au clic extérieur et à Échap, un
panneau à la fois (écouteurs délégués sur `document`, hors
`initDayScripts`). Panneau en `position: absolute` sous `.mj-item-key`
(`position: relative`), sans ombre portée (charte), `z-index: 60` sous les
bandeaux d'alerte (70) et le calque d'édition (79/80), aligné à gauche sous
720px pour rester dans l'écran.

**Score réservé aux tâches qualifiées** : `why_of` (et donc `wsjf_of`, son
arrondi) ne couvre que les tâches ayant priorité **et** points (voir § Décisions
et pièges tracés, point 0).

### Description d'une tâche (extrait dépliable)

`task_description(task)` (macro, `templates/_kairos_macros.html`) : rend la
description **dans la ligne de tâche**, plus seulement au fond du panneau
d'édition (issue #32) :

- Ne rend **rien** si `task.description` est vide ou entièrement blanche
  (`task.description.strip()`) : une tâche sans description ne gagne ni
  marqueur, ni ligne supplémentaire.
- `<details class="mj-desc">` **natif**, sans JavaScript : le repli sans JS de
  toute la vue Jour est un invariant, et contrairement à `.mj-edit-toggle`
  (transformé en bouton précisément pour empêcher Ctrl+F d'ouvrir tous les
  panneaux d'édition, voir § Modale d'édition) on veut **ici** que la recherche
  du navigateur déplie automatiquement le texte pour l'y trouver.
- `<summary>` = icône `file_text` + `.mj-desc-peek` (toute la description sur
  une ligne, coupée en ellipse par CSS ; aucune troncature côté serveur, donc
  rien n'est perdu au dépliage). `.mj-desc[open] > summary .mj-desc-peek` passe
  en `display: none` : déplié, l'extrait tronqué ferait doublon avec
  `.mj-desc-body` juste en dessous, seule l'icône reste comme poignée de repli.
- `.mj-desc-body` en `white-space: pre-wrap` : les retours à la ligne du texte
  saisi (ou hérités d'une conversion de note, voir `notes-capture.md`) sont
  préservés, sans aucun rendu HTML/Markdown (échappement Jinja par défaut,
  jamais `| safe` ; même règle que le corps d'une note).

**Point d'appel : en toute fin de `<li class="kairos-item">`**, après
`edit_panel()`, dans les six sections de la vue Jour qui portent une ligne de
tâche éditable (boîte de réception, agenda ordonné, sans créneau, bloquées,
programmées plus tard, mères en cours) et dans `_kairos_backlog.html`. Raison
tracée en commentaire CSS : `.kairos-item` est un `flex-wrap`, et `.mj-desc`
porte `flex: 0 0 100%` pour occuper sa propre ligne ; appelée plus tôt, elle
repousserait **tous** les badges sous elle. `min-width: 0` est posé sur le
conteneur **et** sur l'extrait : sans lui, une boîte flex refuse de rétrécir
sous la largeur de son contenu et l'ellipse ne se déclenche jamais (l'extrait
déborderait la carte au lieu d'être coupé).

Pas de description dans la **vue semaine** ni dans la section « Fait » : la
grille semaine tronque déjà les titres eux-mêmes (`.mj-week-day .kairos-item
.mj-title`, ellipse), et une tâche terminée n'a plus de contexte à consulter ;
l'extrait n'y apporterait que du bruit.

### Raccourcis clavier (audit UI)

Écouteur `keydown` délégué sur `document` (`kairos.html`, hors
`initDayScripts`) : `N` bascule la capture sur l'onglet « Tâche » si besoin et
y place le curseur ; `/` ouvre `.mj-filter-compact` et place le curseur dans la
recherche. Ignoré si la cible est un champ (`input`, `textarea`, `select`,
`contenteditable`), avec Ctrl/Cmd/Alt (Ctrl+N reste au navigateur), ou si le
panneau d'édition est ouvert. Chaque raccourci est signalé par un
`<kbd class="mj-kbd-hint">` à côté de son contrôle, masqué sous
`@media (hover: none)` (écran tactile, pas de clavier à qui l'indiquer).

### Mises à jour AJAX (contrat X-Requested-With, swap, repli sans JS)

- **Seules six actions « rapides » portent `data-ajax`** sur leur `<form>` :
  `done_toggle` (fait/rouvrir), les trois formulaires de `task_actions` (chrono
  start/stop, décaler), les deux formulaires de qualification en ligne de
  l'inbox (priorité, points), et le bouton Arrêter de `.mj-now-card`. **Aucun**
  autre formulaire de la vue Jour ne porte `data-ajax` : ni la capture (tâche ou
  créneau), ni le panneau d'édition complet (`mj-edit-form`, tâche ou bloc), ni
  la suppression de tâche/bloc ; ces actions rechargent toujours la page
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
  (réseau indisponible) ou `#mj-day-content` introuvable) : **repli**
  `form.submit()`, soumission HTML classique.
- Côté serveur, `_kairos_action_response(request)` est la réponse commune des six
  handlers ci-dessus : si `request.headers.get("X-Requested-With") == "fetch"`,
  renvoie `render_kairos_response(request, fragment=True)` (le partiel jour, deux
  sessions fraîches) ; sinon `RedirectResponse("/kairos", status_code=303)` ; le
  comportement historique, identique sans JS. Chaque handler doit committer et
  **fermer** sa propre session avant cet appel (voir invariant plus haut).
- **Bouton émetteur ajouté à la requête** (`kairosFormData`,
  `kairosSubmitNatively`) : `new FormData(form)` n'inclut jamais le bouton
  cliqué, et `form.submit()` (repli) non plus. Or les pastilles de
  qualification transportent leur valeur dans le `name`/`value` du bouton :
  l'écouteur lit `ev.submitter` et l'ajoute au `FormData` ; le repli ajoute
  un `<input type="hidden">` équivalent avant `form.submit()`. Sans cela la
  priorité choisie se perdait en route.
- `initDayScripts(root = document)` : point de ré-initialisation, appelé une fois
  au chargement (`initDayScripts(document)`) et de nouveau après chaque swap
  (`initDayScripts(target)`). Ne gère **que** ce qui vit dans le sous-arbre
  remplacé et doit donc être rebranché : le minuteur vivant (élément `.mj-timer` +
  `setInterval`) et le câblage du bouton d'opt-in aux alertes
  (`#mj-alert-config`). **Tout le reste est délégué sur `document`**, branché une
  seule fois au chargement du script, et continue de fonctionner sur le contenu
  injecté sans reliaison : bascule du panneau d'édition, fermeture Échap,
  bascule des radios de capture, remplissage de durée par type/Fibo, autosubmit,
  l'intercepteur AJAX lui-même ; un commentaire de code interdit explicitement
  de les dupliquer dans `initDayScripts`.
- **Repli sans JS** : chaque `<form data-ajax>` reste un `<form method="post"
  action="...">` HTML standard. JS désactivé (ou `fetch` en échec) → soumission
  navigateur normale → branche serveur sans `X-Requested-With` → redirection 303
  vers `/kairos`, identique au comportement pré-AJAX. Nécessaire pour la WebView
  Android (fiabilité JS non garantie) et l'accessibilité (navigation complète
  attendue par certains lecteurs d'écran) : justification explicite dans le
  commentaire d'en-tête du script.

### Modale d'édition (essentiels/avancé, bloqueurs en cases, Échap)

`edit_panel(task)` (macro, `templates/_kairos_macros.html`) : `<span
class="mj-edit">` (`display: contents` ; n'interfère pas avec la mise en page de
son parent, `.mj-item-actions` depuis l'issue #33) contenant un bouton
`.mj-edit-toggle` (crayon) et un `.mj-edit-body[hidden]`. `display: contents` y
reste indispensable : c'est le **bouton** qui doit devenir le calque plein écran
à l'ouverture (voir ci-dessous), pas le `<span>` conteneur.

- **Bascule** : écouteur `click` délégué sur `document`, cible
  `.mj-edit-toggle`, bascule `hidden` sur le `.mj-edit-body` associé (trouvé via
  `.closest('.mj-edit')`), reflète l'état dans `aria-expanded`.
- **Calque plein écran quand ouvert** : le **même bouton**
  (`.mj-edit-toggle[aria-expanded="true"]`) devient `position: fixed; inset: 0;
  z-index: 79; background: rgba(22,32,43,.42)`, cliquer n'importe où en dehors
  de la carte referme le panneau, car la cible du clic ne matche alors plus
  `.mj-edit-toggle` à l'intérieur de la carte (qui reste au-dessus, `.mj-edit-body`
  à `z-index: 80`).
- **Glyphe ✕** : pseudo-élément `::after` du même bouton (seulement à l'état
  ouvert), positionné **à côté** du coin haut-droit de la carte
  (`left: calc(50% + min(320px, 46vw) + 10px)`, bascule à droite sous 700px),
  jamais par-dessus la carte : un pseudo-élément ne peut pas peindre au-dessus
  d'une boîte empilée plus haut (`.mj-edit-body`, qui doit rester cliquable).
- **Piège évité, tracé explicitement** : aucune règle `:hover` sur ce bouton/son
  `::after` : une fois ouvert, il couvre tout l'écran, donc il serait « survolé »
  en permanence et resterait visuellement bloqué dans son état hover.
- **Échap** : écouteur `keydown` délégué sur `document`, cherche n'importe où sur
  la page un `.mj-edit-toggle[aria-expanded="true"]`, le ferme (même logique que
  le clic extérieur). Fonctionne indifféremment pour un panneau de tâche ou de
  créneau manuel (même classes réutilisées, phase 16).
- **Divulgation progressive** (deux niveaux, mêmes `name=` de champs qu'avant,
  `edit_task` inchangé) :
  - **Essentiels** (toujours visibles) : Titre (premier champ, mis en évidence
    par `.mj-edit-form > label:first-child input`), **Description**
    (`<textarea name="description">`, juste sous le titre, remontée des options
    avancées par l'issue #32, voir § Description d'une tâche), puis
    Priorité et Points de Fibonacci en pastilles radio (`priority_choice()`,
    `points_choice()`, audit UI) : `<label class="mj-radio"><input
    type="radio">` + `<span class="mj-pill">`, la radio restant dans le DOM
    (clavier, lecteur d'écran, envoi) mais visuellement remplacée par sa
    pastille ; une pastille « — » (valeur vide) vide le champ et renvoie la
    tâche en boîte de réception. Les radios de points portent
    `.mj-fibo-radio` et `data-avg-minutes` (médiane calibrée du palier) :
    l'écouteur `change` délégué remplace la durée estimée, comme le faisait
    l'ancien `<select class="mj-fibo-select">`. Puis `fibo_help()` (guide
    d'estimation, repliable), puis `.mj-edit-row.mj-edit-essentials` :
    Échéance, Durée (min, `.mj-estimated-minutes`). Piège tracé : la règle
    `.mj-edit-form label` (colonne, graisse 600, couleur tertiaire) s'applique
    aussi aux `<label class="mj-radio">` ; `.mj-edit-form .mj-radio` la
    neutralise, comme `.mj-edit-form .mj-check-label` avant elle.
  - **Options avancées** (`<details class="mj-edit-advanced">`, repliées) :
    Programmée pour (`scheduled_date`), Projet, Temps passé manuel,
    Récurrence + Jour du mois (visible seulement si `recurrence ==
    'monthly_on_day'` a du sens, champ toujours présent mais informativement lié),
    Type (`.mj-task-type-select`), Heure fixe (`pin_time`, + `pin_day` caché
    porteur du jour affiché : la date programmée prime si renseignée, sinon
    `pin_day`, sinon aujourd'hui, logique dans `edit_task`), Fiche liée
    (`linked_ticket_id`, `<select>` simple), Nouvelles sous-tâches (`<textarea
    name="new_subtasks">`, une ligne = une sous-tâche créée dans le même
    enregistrement), Bloqueurs.
- **Un seul formulaire, une seule route** (`POST /kairos/tasks/{id}/edit`, pas de
  `data-ajax`), un seul bouton « Enregistrer » : fusion actée en phases 5
  (épinglage) et 6 (sous-tâches en lot + bloqueurs) de `SPEC_KAIROS.md` : ce qui
  était plusieurs soumissions séparées est traité en une seule transaction côté
  route.
- **Bloqueurs en cases à cocher** (`.mj-blocker-checks`, un `<input
  type="checkbox" name="blocker_ids" value="{id}">` par tâche candidate hors
  elle-même) : même `name=` répété pour chaque case → `edit_task` reçoit la
  **liste complète** via `form.getlist("blocker_ids")` et la traite comme
  l'**ensemble cible** ; diff calculé côté route (retrait = présent en base mais
  décoché ; ajout = coché mais absent, filtré par `would_create_cycle`, ignoré
  silencieusement en cas de cycle, sans faire échouer le reste de
  l'enregistrement). **Note de traçabilité (réconciliation de sources)** :
  `SPEC_KAIROS.md` phase 13 documente un remplacement temporaire de ces cases par
  un `<select name="blocker_ids" multiple>` (pour s'aligner sur le widget de
  « Fiche liée »). Le code actuel et `docs/DESIGN_SYSTEM.md` (« Écarts assumés »)
  confirment que la refonte GTD est **revenue** aux cases à cocher : motif
  documenté : « ni pilule ni chip à bascule, mais un HTML natif plus simple
  d'accès que le Ctrl-clic » d'un `<select multiple>`. L'état actuel (cases à
  cocher) est la décision qui prévaut ; ne pas revenir au `<select multiple>` sans
  rouvrir explicitement ce point. `linked_ticket_id`, lui, reste un `<select>`
  simple (une seule fiche liée possible).
- **Piège de spécificité CSS corrigé** : la règle générique `.mj-edit-form label`
  (`display: flex; flex-direction: column`, pour les champs texte/select) est
  plus spécifique qu'`.mj-check-label` seul et empilait la case au-dessus du
  texte au lieu de l'aligner à côté, pour les labels-cases du panneau d'édition
  (bloqueurs, « Bloc deep-work » du formulaire d'édition de créneau), corrigé par
  `.mj-edit-form .mj-check-label { flex-direction: row; align-items: center;
  gap: 0.4rem; }` (sélecteur à deux classes, regagne la priorité).
- **Zone Supprimer/Archiver** (`.mj-edit-danger`) : séparée visuellement en pied
  de panneau (`border-top`), formulaire propre (pas `data-ajax`) avec
  confirmation JS native (`onsubmit="return confirm(...)"`), `POST
  /kairos/tasks/{id}/delete` supprime si `task.source == 'native'`, archive
  (`status = 'archived'`) sinon (une tâche SP/GitLab resynchronisée serait
  recréée par la synchro si elle était supprimée en dur) ; nettoie aussi les
  `TaskDependency` où la tâche figure (bloquée ou bloquante) pour éviter des
  arêtes orphelines.

### Décisions et pièges tracés

0. **Correctifs de l'audit UI** (tous tracés ici pour ne pas les re-trancher) :
   - **Dates en français, sans `setlocale`.** `strftime('%A %d %B %Y')`
     affichait « Tuesday 22 September 2026 » : la locale d'un processus Python
     vaut « C » par défaut, celle du poste n'est garantie ni sous PyInstaller,
     ni dans l'APK, ni sous systemd, et `locale.setlocale` est global au
     processus et non sûr entre threads (Uvicorn sert les routes synchrones
     dans un pool). Deux tables fixes dans `app/fr_dates.py`
     (`date_longue` → « mardi 22 septembre 2026 », « 1er » pour le premier du
     mois ; `jour_court` → « mar. 22/09 »), exposées en filtres Jinja.
   - **Titre de barre fidèle à la vue.** « Semaine · du lundi 21 septembre
     2026 » sur la vue semaine (qui affichait « Aujourd'hui »), et
     « Aujourd'hui » seulement si le jour affiché est le jour courant
     (`is_today` dans le contexte) : sinon « Jour », le lien « Voir le détail »
     de la vue semaine menant à n'importe quel jour. Même règle pour `<title>`.
   - **Pas de score WSJF sur une tâche non qualifiée.** `wsjf_of` ne couvre
     plus que les tâches ayant priorité **et** points : une tâche de la boîte
     de réception « ne rentre dans aucun tri », lui afficher un score calculé
     sur des valeurs par défaut (« 0.1 ») contredisait le texte juste
     au-dessus d'elle.

1. **Bug d'opacité corrigé sur `.kairos-item.mj-blocked`, ne jamais réutiliser
   `opacity` ici.** `.kairos-item.mj-blocked` utilisait `opacity: 0.75` : une
   opacité sur ce `<li>` crée un **contexte d'empilement** qui atténue au rendu
   tout son sous-arbre, y compris le panneau d'édition (`.mj-edit-body`), qui y
   est imbriqué et passe en `position: fixed` **une fois ouvert**, `position:
   fixed` échappe à la mise en page de son ancêtre mais **pas** au *compositing*
   d'un ancêtre opaque à moins de 1 (piège CSS classique, commenté verbatim dans
   `static/style.css` juste avant la règle). Remplacé par des propriétés
   ciblées qui ne composent jamais les descendants : fond `background:
   var(--surface-tint)` + `border-style: dashed` sur le `<li>`, et
   `color: var(--text-3)` sur `.mj-title` seul (pas sur toute la ligne). Exemple
   canonique du workflow spec-d'abord de ce dépôt : **ne pas revenir à
   `opacity`** pour cette classe ni pour tout futur ancêtre d'un panneau
   `position: fixed`.
2. **Marge interne mobile : bloc placé délibérément en toute fin de
   `static/style.css`.** Le bloc `@media (max-width: 720px)` qui pose le padding
   latéral mobile (`.mj-capture`, `.mj-to-process`, `.mj-progress`,
   `.mj-now-card`, `.mj-timeline-card`, `.collapser`, `.mj-filter-form`,
   `.panel`, `.mj-week-day`, `.banner`, `.kairos-item`) est **le tout dernier
   bloc du fichier**. Raison tracée explicitement dans un commentaire juste
   au-dessus : la plupart de ces sélecteurs ont leur propre règle de padding
   **non conditionnelle** plus haut dans le fichier, à **spécificité égale**
   (simple sélecteur de classe) ; en CSS, à spécificité égale, c'est la règle la
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
   traitement : fond `#FFFAF1` + puce ambre `border-left: 3px solid
   var(--warn-fg)` — et fusionnent la classe utilitaire avec `.card` sur le même
   élément (pas de `<div>` imbriqué), pour que le fond suive les coins arrondis.
   `.mj-next` (la ligne « À faire maintenant : ... » dans `.mj-progress`) est la
   seule ligne de l'app en Newsreader italique 19px/500 (police chargée en plus
   d'IBM Plex Sans). Ces trois exceptions sont des décisions produit explicites,
   documentées et non dupliquées ici : voir `CLAUDE.md` (racine) et
   `docs/DESIGN_SYSTEM.md` § Couleurs/Typographie pour la charte complète et le
   raisonnement. Ne pas les généraliser à d'autres cartes/badges/titres, ne pas
   les « corriger » vers le bleu/neutre standard.

### Invariants et garde-fous

- Un seul `id="mj-day-content"` dans toute l'application, posé uniquement par
  `kairos.html` : jamais par `_kairos_day.html` (qui doit rester rendable, tel
  quel, comme fragment autonome).
- Toute action mutante ouvre sa **propre** session `tasks_session`, committe,
  **ferme** cette session avant d'appeler `_kairos_action_response`/
  `render_kairos_response` (qui rouvrent des sessions fraîches) : jamais de
  session imbriquée. À respecter pour toute nouvelle route d'action.
- Toute nouvelle action « rapide » (bascule d'état simple, candidate à
  l'amélioration AJAX) doit : poser `data-ajax` sur son `<form>`, retourner
  `_kairos_action_response(request)` en fin de handler, committer/fermer sa
  session avant cet appel. Une action qui ouvre une vue radicalement différente
  (édition complète, suppression, création de créneau) reste en redirection 303
  pure. Exception rouverte avec l'utilisateur (audit UI) : la **capture de
  tâche** passe en AJAX, pour enchaîner les captures au clavier.
- Le formulaire de filtres reste en **GET**, jamais `data-ajax` : c'est une
  navigation dont l'état vit dans l'URL (bookmarkable), pas une mutation.
- Les pastilles de qualification de l'inbox ne portent **jamais**
  `.mj-fibo-radio`, `.mj-task-type-select` ni `data-avg-minutes` : le
  remplissage automatique de durée reste réservé au panneau d'édition.
- Tout libellé ou définition de priorité ou de palier de points se lit dans
  `app/task_guide.py`, jamais écrit en dur dans un gabarit.
- Les routes d'édition/suppression de créneau ne doivent agir **que** sur
  `TimeBlock.source == 'manual'` (garde-fou déjà en place côté route) : les
  créneaux TimeTree sont transitoires, jamais persistés.
- `edit_task` traite `blocker_ids` comme l'**ensemble cible complet** à chaque
  soumission (pas un delta implicite envoyé par le client) : toute évolution du
  panneau doit respecter ce contrat déjà en place côté route.
- Ne jamais ajouter de règle `:hover` sur `.mj-edit-toggle[aria-expanded="true"]`
  ni sur son `::after` (piège déjà rencontré : le bouton couvre l'écran une fois
  ouvert).
- Ne jamais réintroduire `opacity` sur `.kairos-item.mj-blocked`, ni sur tout
  futur ancêtre CSS d'un élément `position: fixed` de cette page (le panneau
  d'édition en particulier) : utiliser des propriétés ciblées (fond, couleur de
  texte) qui ne composent pas les descendants.
- Le bloc CSS de marge mobile en fin de `static/style.css` doit **rester en fin
  de fichier** : toute nouvelle règle de padding non conditionnelle pour l'un de
  ses sélecteurs doit être insérée avant lui.
- `initDayScripts` ne doit reprendre **que** ce qui vit dans le sous-arbre
  remplacé par un swap (chrono, opt-in alertes) : tout ce qui est délégué sur
  `document` au chargement du script ne doit jamais y être dupliqué.
- `task_description()` reste appelée **en dernier** dans `.mj-item-main` :
  c'est un bloc pleine largeur du corps, il doit venir après les étiquettes.
- Un `<li class="kairos-item">` ne contient que les cellules **nommées** de la
  grille : les quatre de base, plus `qualify` pour une ligne de la boîte de
  réception (`.has-qualify`, qui déclare la zone). Tout nouvel élément d'une
  ligne de tâche s'ajoute **à l'intérieur** d'une cellule ; un enfant direct
  sans zone nommée se placerait dans une piste implicite et casserait
  l'alignement de toutes les lignes.
- Un badge de **longueur imprévisible** (phrase, note explicative) va dans
  `.mj-item-tags`, jamais dans `.mj-item-key` : la stabilité de la colonne
  « clés » d'une ligne à l'autre est ce qui rend la liste balayable.
