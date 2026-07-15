# Récurrence (tâches & créneaux)

Spec de domaine pour la récurrence côté « Kairos » (dashboard de tâches). Couvre
intégralement `app/tasks_recurrence.py` : récurrence des **tâches** (`Task`),
selon deux modèles — recréation à la complétion (`spawn_next_occurrence`) et
calendaire « le N du mois » (`ensure_calendar_occurrences`) — ainsi que la
récurrence des **blocs** (`TimeBlock`), projetée à la volée
(`expand_recurring_blocks`), et le décalage « prochain jour ouvré » du snooze
(`next_snooze_date`). S'appuie sur `app/workdays.py` (jours ouvrés/fériés,
spec propre, non détaillé ici) et est appelée depuis `app/main.py` (routes) et
`app/tasks_scheduling.py` (éligibilité du jour, hors périmètre ici).

## 1. Besoin métier (cahier des charges)

### Objectif / problème

Trois familles d'obligations répétées, mal couvertes par un modèle unique :

1. **Tâches qui se recréent en se terminant** (« relever le courrier »,
   « point hebdo ») : la notion de récurrence naturelle est *je viens de la
   faire, la prochaine occurrence apparaît*.
2. **Tâches calées sur une date du calendrier**, indépendamment de toute
   complétion (« le 23 de chaque mois », note de frais, cotisation) : la
   récurrence « à la complétion » ne convient pas — une échéance du mois
   dernier oubliée ne doit ni disparaître ni empêcher celle du mois courant
   d'apparaître.
3. **Créneaux horaires qui se répètent** (déjeuner quotidien, deep-work chaque
   mardi) : contrairement aux tâches, un `TimeBlock` n'a pas de notion de
   « fait » — le modèle « recréation à la complétion » ne s'applique pas.

### Comportement attendu (utilisateur)

- Terminer une tâche récurrente (`daily`/`weekdays`/`weekly`/`monthly`) crée
  automatiquement la suivante, avec l'échéance avancée selon la règle, en
  reprenant priorité/points/type/heure fixe de l'occurrence qui vient d'être
  complétée (pas de re-saisie à chaque fois).
- Une tâche calée sur un jour du mois (« le 23 ») voit son occurrence du mois
  courant apparaître automatiquement à chaque chargement de la page, sans
  attendre qu'une occurrence précédente soit traitée ; si le jour calculé
  tombe un jour non ouvré, l'échéance recule au jour ouvré précédent.
- Décaler une tâche « à demain » (snooze) l'envoie toujours à un jour ouvré :
  un vendredi décale au lundi (ou au jour ouvré suivant si ce lundi est
  férié), jamais à un week-end.
- Un bloc horaire (occupé ou deep-work) déclaré récurrent réapparaît aux jours
  concernés à la même heure, sans qu'aucune ligne supplémentaire ne soit
  créée en base — éditer ou supprimer le bloc agit sur toutes ses occurrences
  d'un coup, car il n'existe qu'un seul enregistrement, le modèle.
- Aucune fiche n'est jamais perdue : une occurrence en retard reste visible
  tant qu'elle n'est pas traitée, une nouvelle génération ne l'écrase jamais.

### Critères de succès

- [x] Terminer une tâche récurrente crée l'occurrence suivante avec échéance
      avancée selon la règle (`daily`/`weekdays`/`weekly`/`monthly`).
- [x] Une quotidienne terminée avec plusieurs jours de retard repart de
      demain, pas de l'échéance manquée + 1 jour (pas de rattrapage en
      rafale).
- [x] Double aller-retour fait/rouvert/fait sur une récurrente : une seule
      occurrence suivante créée (pas de doublon).
- [x] L'occurrence suivante d'une tâche importée (GitLab) est **native** :
      pas de collision avec la contrainte unique `(source, external_id)`.
- [x] Priorité, points Fibonacci, type et heure fixe (reportée à la nouvelle
      échéance, même heure) sont hérités de l'occurrence complétée.
- [x] `scheduled_date` de la nouvelle occurrence est posée sur la nouvelle
      échéance (pas d'apparition prématurée dans l'agenda du jour).
- [x] Une tâche « le 23 » génère son occurrence du mois automatiquement,
      décalée au jour ouvré précédent si le 23 tombe un jour non ouvré
      (week-end ou férié, avec chaînage si plusieurs jours non ouvrés se
      suivent).
- [x] Une occurrence calendaire du mois précédent encore ouverte n'est jamais
      écrasée par la génération du mois courant ; les deux coexistent.
- [x] Mettre en place une série calendaire sur une tâche déjà existante (dont
      l'échéance tombe déjà ce mois-ci) ne crée pas de doublon dès le
      premier chargement.
- [x] Le jour du mois est borné en fin de mois (31 sur un mois plus court →
      dernier jour du mois, puis décalage jour ouvré appliqué par-dessus).
- [x] Décaler une tâche « à demain » un vendredi l'envoie au lundi (ou au
      jour ouvré suivant si ce lundi est férié) ; une échéance en retard
      relancée repart d'aujourd'hui, pas de l'échéance dépassée.
- [x] Un bloc quotidien créé aujourd'hui apparaît les jours suivants, à la
      même heure, sans aucune ligne supplémentaire stockée.
- [x] Un bloc hebdomadaire n'apparaît que le même jour de semaine que son
      origine ; un bloc « jours ouvrés » saute les week-ends.
- [x] Aucun bloc récurrent ne recule avant sa date d'origine (pas de
      rattrapage rétroactif) — même principe que pour les tâches.
- [x] Un bloc ponctuel (sans récurrence) est inchangé, aucun effet de bord.

### Hors périmètre / différé

- Récurrence `monthly` (avance-à-la-complétion) pour les **blocs** : non
  demandée, jugée moins pertinente pour un créneau récurrent de type agenda
  (`BLOCK_RECURRENCE_RULES` se limite à `daily`/`weekdays`/`weekly`).
- Prise en compte des jours fériés dans la récurrence de **blocs**
  (`_block_recurs_on`, règle `weekdays`) : même simplicité assumée que la
  règle `weekdays` des tâches — les jours fériés relèvent des projections
  GitLab/du calendrier professionnel, pas de la todo personnelle.
- Édition/suppression d'une occurrence de bloc **isolément** : impossible par
  construction (aucune occurrence n'est jamais persistée) — l'édition porte
  toujours sur le modèle, donc sur toutes les occurrences (`app/main.py`,
  routes `POST /kairos/blocks/{id}/edit` et `/delete`).
- Table `RecurringTaskDef` séparée pour la récurrence calendaire : écarté au
  profit de colonnes directement sur `Task` (`recurrence`,
  `recurrence_day_of_month`, `recurrence_period`), plus simple et cohérent
  avec le modèle de récurrence déjà en place.
- Rattrapage rétroactif (génération des mois/jours manqués si l'app n'a pas
  tourné) : explicitement refusé, aussi bien pour la récurrence calendaire
  des tâches que pour les blocs — une seule occurrence est générée, celle du
  jour courant.

## 2. Solution technique

### Vue d'ensemble

`app/tasks_recurrence.py` porte trois mécanismes indépendants, tous conçus
pour un outil mono-utilisateur sans moteur de planification en tâche de fond :

1. **Recréation à la complétion** (`RECURRENCE_RULES = ("daily", "weekdays",
   "weekly", "monthly")`) : `spawn_next_occurrence`, appelée depuis
   `toggle_task_done` (`app/main.py`) au moment où une tâche passe à `done`.
2. **Génération calendaire** (`CALENDAR_RECURRENCE = "monthly_on_day"`) :
   `ensure_calendar_occurrences`, appelée depuis `_build_kairos_context`
   (`app/main.py`) à **chaque chargement** de la page Kairos, indépendamment
   du statut de la tâche.
3. **Projection à la volée** des blocs récurrents (`BLOCK_RECURRENCE_RULES =
   ("daily", "weekdays", "weekly")`) : `expand_recurring_blocks`, appelée
   depuis `_fetch_busy_blocks` (timeline/semaine) et directement dans le
   template d'édition des créneaux du jour (`app/main.py`), jamais depuis une
   route de mutation — c'est une fonction pure, sans effet de bord.

Un quatrième utilitaire, `next_snooze_date`, sert le bouton « décaler à
demain » (route `POST /kairos/tasks/{id}/snooze`) : il ne génère aucune
occurrence, il décale la `deadline` de la tâche existante.

Toutes les fonctions dépendent de `app/workdays.py` pour le calcul en jours
ouvrés (`add_business_days`, `on_or_before_business_day`) — spec propre, non
détaillée ici, seul le sens de chaque décalage (avant/arrière) est repris.

### Détail par composant

#### `next_deadline(rule, base)` — calcul de la prochaine échéance

Fonction pure, cœur du modèle « recréation à la complétion » :

- `"daily"` → `base + 1 jour`.
- `"weekdays"` → jour ouvré suivant au sens **week-ends uniquement** (boucle
  `while step.weekday() >= 5`) — volontairement plus simple que
  `workdays.add_business_days` : les jours fériés ne sont **pas** pris en
  compte ici (« les fériés relèvent des projections GitLab, pas de la todo
  personnelle », commentaire du code). Ne pas confondre avec le décalage jour
  ouvré de `ensure_calendar_occurrences`/`next_snooze_date`, qui lui inclut
  les fériés.
- `"weekly"` → `base + 7 jours`.
- `"monthly"` → `+1 mois`, jour borné à la fin du mois cible
  (`calendar.monthrange`) : 31 janvier → 28 ou 29 février selon l'année.
- Règle inconnue → lève `ValueError` (pas de valeur par défaut silencieuse).

#### `spawn_next_occurrence(session, task)` — recréation à la complétion

Appelée uniquement depuis `toggle_task_done` (`app/main.py`, route
`POST /kairos/tasks/{task_id}/done`), juste après le passage de `task.status`
à `"done"` et l'arrêt d'un éventuel chrono en cours. Ne fait rien
(`task.status == "todo"` → pas d'appel) au repassage `done → todo`.

- Tâche dont `recurrence` n'est pas dans `RECURRENCE_RULES` (donc `""` ou
  `"monthly_on_day"`) → retourne `None`, aucun effet. La récurrence
  calendaire n'est **jamais** traitée ici, uniquement par
  `ensure_calendar_occurrences`.
- Base de calcul : `max(task.deadline or date.today(), date.today())` — une
  récurrente terminée en retard **ne génère pas** une occurrence déjà en
  retard ; elle repart d'aujourd'hui, pas de l'échéance manquée. C'est ce qui
  distingue ce modèle d'un simple `next_deadline(rule, task.deadline)`.
- **Garde anti-doublon** : recherche d'une tâche existante avec `title`,
  `recurrence` et `deadline` identiques et `status == "todo"`. Si trouvée,
  retourne `None` sans rien créer — couvre le cas d'un double aller-retour
  fait/rouvert/fait sur la même tâche (`toggle_task_done` pourrait sinon être
  appelé deux fois pour la même complétion logique).
- **Toujours `source="native"`, jamais d'`external_id` copié** : la
  contrainte unique `(source, external_id)` sur `Task` interdirait le
  doublon si l'`external_id` de la tâche d'origine (GitLab) était réutilisé
  tel quel, et une occurrence créée localement n'existe de toute façon dans
  aucune source externe.
- **Champs hérités** de l'occurrence complétée, sans requalification :
  `title`, `description`, `priority`, `fibonacci_points`, `task_type`,
  `project_tag`, `estimated_minutes`, `recurrence`, `parent_id`. Le
  raisonnement (commentaire du code) : une tâche récurrente n'a pas à être
  requalifiée à chaque occurrence, la suivante reprend la dernière analyse
  posée.
- `pinned_start` : reporté via `_shift_pinned_time` — même heure de la
  journée, appliquée à la nouvelle `deadline` ; reste `None` si la tâche
  complétée n'était pas épinglée (le placement continue d'être automatique).
- `deadline` **et** `scheduled_date` sont tous deux posés sur la nouvelle
  échéance calculée. Sans `scheduled_date`, une occurrence dont l'échéance
  est dans plusieurs jours (récurrence hebdomadaire/mensuelle) apparaîtrait
  immédiatement dans l'agenda du jour au lieu de la section « Programmées
  plus tard » — c'est `scheduled_date`, pas `deadline`, qui pilote la
  présence dans l'agenda du jour (voir `tasks_scheduling.is_eligible_today`,
  spec ordonnancement, non détaillée ici).
- La fonction fait `session.add(occurrence)` mais **ne commit pas** — c'est
  l'appelant (`toggle_task_done`) qui commite, dans la même transaction que
  le passage à `done` et l'arrêt du chrono.

#### `ensure_calendar_occurrences(session, today, holidays=None)` — récurrence calendaire

Appelée depuis `_build_kairos_context` (`app/main.py`), à **chaque
chargement** de la page (pas seulement à la complétion d'une tâche), avec
`target_day` (jour affiché, par défaut aujourd'hui) et `settings.holiday_set`.
Contrairement à `spawn_next_occurrence`, génère **par date**, indépendamment
du sort de toute occurrence précédente.

- Cible uniquement les tâches `recurrence == CALENDAR_RECURRENCE`
  (`"monthly_on_day"`) avec `recurrence_day_of_month` renseigné.
- **Identification d'une série** : couple `(title, recurrence_day_of_month)`
  — même granularité que la garde anti-doublon de `spawn_next_occurrence`
  (titre + règle). Toutes les tâches partageant ce couple sont regroupées en
  `members`.
- **Anti-doublon (`already_covered`)** : une série est considérée déjà
  couverte pour le mois courant (`period = "AAAA-MM"` via `_period_str`) si
  au moins un membre a `recurrence_period == period` **ou** si son
  `deadline` tombe dans ce même mois. La seconde condition couvre le cas où
  la série vient d'être mise en place sur une tâche **existante**, éditée à
  la main, dont l'échéance tombe déjà ce mois-ci (elle n'a jamais été taguée
  `recurrence_period` puisqu'elle n'a pas été créée par cette fonction) —
  évite un doublon dès la première activation de la récurrence calendaire
  sur une tâche déjà en place.
- Si couverte, la série est ignorée ce passage-ci ; sinon une occurrence est
  créée.
- **Représentant** : `max(members, key=lambda m: m.id or 0)` — le membre le
  plus récent de la série (id le plus élevé), pas le tout premier. Sert de
  source pour `description`, `priority`, `fibonacci_points`, `task_type`,
  `project_tag`, `estimated_minutes`, `pinned_start` (reporté avec
  `_shift_pinned_time`, même mécanique que `spawn_next_occurrence`) — mêmes
  raisons : pas de requalification à chaque occurrence, et les détails
  peuvent avoir changé depuis la toute première mise en place de la série.
- **Calcul de la date cible** : `day_of_month` borné à la fin du mois courant
  (`calendar.monthrange(today.year, today.month)[1]`, ex. 31 → 28/29 en
  février) **puis** décalage jour ouvré via
  `workdays.on_or_before_business_day(target, holidays)` — décalage
  **arrière** uniquement (jamais en avant) : le 23 tombant un dimanche recule
  au vendredi précédent ; si plusieurs jours non ouvrés se suivent (week-end
  + jour férié adjacent), le décalage chaîne en arrière jusqu'au prochain
  jour ouvré. C'est l'inverse du sens du snooze (`next_snooze_date`, qui
  avance).
- `deadline` et `scheduled_date` posés tous deux sur cette date finale, même
  raison que pour `spawn_next_occurrence` (ne pas apparaître dans l'agenda
  avant l'échéance réelle).
- Nouvelle occurrence : `recurrence=CALENDAR_RECURRENCE`,
  `recurrence_day_of_month=day_of_month` (repris tel quel, non borné — le
  bornage n'affecte que le calcul de la date, pas la valeur stockée),
  `recurrence_period=period`, `source="native"`.
- **Jamais rétroactif** : une seule occurrence générée par appel, celle du
  mois de `today` — pas de rattrapage des mois où l'app n'aurait pas tourné.
- **Jamais destructif** : génération purement additive, aucune occurrence
  existante n'est modifiée ni supprimée ; une échéance du mois précédent
  encore ouverte (`status="todo"`) reste intacte, visible en retard, pendant
  que celle du mois courant est créée à côté.
- **Commit interne** : contrairement à `spawn_next_occurrence`, cette
  fonction appelle `session.commit()` elle-même si au moins une occurrence a
  été créée (`if created:`) — cohérent avec son appel à chaque chargement de
  page, hors du cycle de vie d'une transaction de route de mutation.
- Retourne la liste des occurrences créées (peut être vide).

#### `next_snooze_date(deadline, today, holidays=None)` — décalage « demain »

Appelée depuis `snooze_task` (`app/main.py`, route
`POST /kairos/tasks/{task_id}/snooze`), avec `settings.holiday_set`. Ne crée
aucune occurrence : modifie directement `task.deadline`.

- Base : `deadline` si renseignée et strictement future (`deadline > today`),
  sinon `today` — une tâche déjà en retard (deadline passée ou absente)
  redémarre depuis aujourd'hui, pas depuis l'échéance dépassée (même
  philosophie que la base de calcul de `spawn_next_occurrence`).
- Résultat : `workdays.add_business_days(base, 1, holidays)` — avance
  toujours d'un jour ouvré (week-ends et fériés sautés). **Sens « avant »,
  opposé à la récurrence calendaire** : un vendredi décale au lundi suivant
  (ou au jour ouvré d'après si ce lundi est férié), jamais à un week-end.
- Portée du décalage jour ouvré (décision actée, tracée ici) : **uniquement**
  la récurrence calendaire et le snooze automatique. Une date saisie à la
  main par l'utilisateur (`deadline` ou `scheduled_date` via le panneau
  d'édition) n'est **jamais** corrigée d'office — elle reflète un choix
  explicite, même si elle tombe un samedi.

#### `expand_recurring_blocks(templates, range_start, range_end)` — blocs récurrents

Fonction pure, sans session ni effet de bord — appelée depuis
`_fetch_busy_blocks` (construction de la timeline/vue semaine) et
directement dans `_build_kairos_context` pour lister les créneaux
« pertinents pour le jour affiché » dans le panneau d'édition
(`editable_blocks`, en appelant `expand_recurring_blocks([b], target_day,
target_day)` bloc par bloc pour tester s'il produit une occurrence ce
jour-là).

- Un `TimeBlock` n'a **pas de statut « fait »** (contrairement à `Task`) :
  le modèle « recréation à la complétion » ne s'applique pas ici. Chaque
  `TimeBlock` de `templates` (avec `recurrence` non vide) est le **modèle
  unique**, stocké une seule fois en base : sa date propre (`tpl.start.date()`)
  fixe l'**origine**, son heure de début/fin canonique se répète à
  l'identique sur chaque occurrence.
- `_block_recurs_on(rule, day, origin_date)` — règle par jour testé :
  - `"daily"` → toujours vrai.
  - `"weekdays"` → `day.weekday() < 5` (week-ends exclus ; commentaire du
    code : « même simplicité que `Task` `'weekdays'` » — pas de jours fériés).
  - `"weekly"` → `day.weekday() == origin_date.weekday()` (même jour de la
    semaine que l'origine).
  - Règle hors de ces trois valeurs → `ValueError`.
- Boucle par jour sur `[max(range_start, origin_date), range_end]` — **jamais
  avant `origin_date`** : un bloc créé un mercredi ne produit aucune
  occurrence avant ce mercredi, même si la plage demandée commence plus tôt
  (pas de rattrapage rétroactif, même principe que la récurrence des
  tâches).
- Pour chaque jour concerné : `occ_start = datetime.combine(day, tpl.start.time())`,
  `occ_end = occ_start + duration` (`duration = tpl.end - tpl.start`, calculée
  une fois par modèle). Une nouvelle instance `TimeBlock` est construite —
  **jamais** passée à `session.add` — reprenant `title`, `source`, `kind`,
  `recurrence` du modèle. Ce sont des objets **transitoires**, sans `id`,
  à fusionner par l'appelant avec les blocs ponctuels réels
  (`one_off_blocks + expand_recurring_blocks(...)` dans `_fetch_busy_blocks`).
- Un modèle dont `recurrence` n'est pas dans `BLOCK_RECURRENCE_RULES` (donc
  `""`, bloc ponctuel) est ignoré silencieusement par la boucle principale
  (`continue`) — aucune occurrence produite, aucun effet de bord sur le
  bloc ponctuel lui-même (il est traité ailleurs, comme `one_off_blocks`
  dans `_fetch_busy_blocks`).

Édition/suppression d'un bloc récurrent porte toujours sur ce modèle unique,
donc sur toutes ses occurrences (routes `POST /kairos/blocks/{id}/edit` et
`POST /kairos/blocks/{id}/delete}`, `app/main.py`) : il n'existe aucune
occurrence persistée à modifier isolément, par construction.

### Décisions et pièges tracés

- **Deux modèles de récurrence pour les tâches, jamais mélangés.**
  `RECURRENCE_RULES` (`daily`/`weekdays`/`weekly`/`monthly`, avance à la
  complétion, gérée par `spawn_next_occurrence`) et `CALENDAR_RECURRENCE`
  (`monthly_on_day`, avance par date, gérée par `ensure_calendar_occurrences`)
  sont des ensembles disjoints — une tâche a exactement une valeur de
  `recurrence`, jamais les deux logiques à la fois. `spawn_next_occurrence`
  ignore explicitement `monthly_on_day` (pas dans `RECURRENCE_RULES`).
- **`recurrence_period` comme clé anti-doublon dédiée**, distincte de la
  garde `(title, recurrence, deadline, status="todo")` de
  `spawn_next_occurrence` : la récurrence calendaire ne peut pas réutiliser
  cette dernière garde car elle génère **par date affichée**, potentiellement
  plusieurs fois avant que l'occurrence précédente soit traitée (pas de
  notion de « status=todo » à vérifier, l'occurrence du mois dernier reste
  `todo` en cas de retard).
- **`recurrence_period` doublé d'une vérification sur `deadline`**
  (`_period_str(member.deadline) == period`) : nécessaire pour ne pas
  générer de doublon dès la **première** mise en place d'une série
  calendaire sur une tâche déjà existante et déjà éditée à la main pour ce
  mois — cette tâche n'a jamais transité par `ensure_calendar_occurrences`
  et n'a donc pas de `recurrence_period` posé.
- **Décalage jour ouvré « arrière » pour le calendaire vs « avant » pour le
  snooze** — sens opposés, volontairement : la récurrence calendaire recule
  (`on_or_before_business_day`, jamais après le jour cible réel du mois)
  tandis que le snooze avance (`add_business_days`, jamais avant
  aujourd'hui). Les deux réutilisent les mêmes primitives de
  `app/workdays.py`, dans des sens opposés selon la sémantique métier
  (« le 23 » doit rester proche du 23, « demain » doit avancer).
- **Portée volontairement limitée du décalage automatique** : uniquement la
  récurrence calendaire et le snooze. Une date saisie à la main (deadline ou
  `scheduled_date`, panneau d'édition de tâche) n'est jamais recorrigée —
  décision produit actée pour ne jamais contredire un choix explicite de
  l'utilisateur.
- **`next_deadline("weekdays", ...)` volontairement plus simple que
  `workdays.add_business_days`** : saute uniquement les week-ends, pas les
  jours fériés — cohérent avec le commentaire du code, qui distingue « todo
  personnelle » (simple) des projections professionnelles (GitLab) qui,
  elles, tiennent compte des fériés via `settings.holiday_set`.
- **`external_id` jamais copié à la recréation** (`spawn_next_occurrence`) :
  piège explicitement évité — copier l'`external_id` d'une tâche importée
  romprait la contrainte unique `(source, external_id)` dès la deuxième
  occurrence recréée pour la même série ; la nouvelle occurrence est toujours
  `source="native"`.
- **Blocs récurrents : projection à la volée, jamais persistée occurrence
  par occurrence** — décision actée en phase 13 pour éviter toute dérive
  entre le modèle et des occurrences qui auraient été matérialisées puis
  laissées obsolètes si le modèle est édité ensuite ; conséquence directe :
  éditer/supprimer un bloc récurrent agit forcément sur toutes ses
  occurrences (il n'y a rien d'autre à éditer).
- **`BLOCK_RECURRENCE_RULES` volontairement sans `monthly`** : sous-ensemble
  de `RECURRENCE_RULES`, jugé suffisant pour les cas d'usage réels (déjeuner
  quotidien, deep-work hebdomadaire) — pas demandé, pas ajouté par
  anticipation.
- **`scheduled_date` systématiquement alignée sur la nouvelle `deadline`**,
  dans `spawn_next_occurrence` **et** `ensure_calendar_occurrences` : même
  piège évité aux deux endroits — sans cela, une occurrence dont l'échéance
  réelle est lointaine (hebdomadaire, mensuelle, calendaire) apparaîtrait
  prématurément dans l'agenda du jour, `scheduled_date` étant le champ qui
  pilote cette visibilité (voir `tasks_scheduling.is_eligible_today`, spec
  ordonnancement).
- **`ensure_calendar_occurrences` commite elle-même**, contrairement à
  `spawn_next_occurrence` : reflet direct de leurs points d'appel — la
  première tourne à chaque chargement de page hors transaction de mutation
  explicite, la seconde est appelée au milieu d'une transaction de route
  (`toggle_task_done`) qui commite après coup.

### Invariants et garde-fous

- Une tâche a **au plus un** mécanisme de récurrence actif à la fois :
  `recurrence` prend une seule valeur parmi `""`, `RECURRENCE_RULES` ou
  `CALENDAR_RECURRENCE`.
- Aucune fonction de ce module ne modifie ni ne supprime jamais une
  **occurrence existante** — `spawn_next_occurrence` et
  `ensure_calendar_occurrences` sont strictement additives (garde
  anti-doublon en amont de toute création, jamais de mise à jour d'une ligne
  déjà en base) ; `expand_recurring_blocks` ne touche jamais la base.
- Aucune occurrence (tâche ou bloc) n'est jamais générée **avant** sa date
  d'origine ou en rattrapage rétroactif de plusieurs périodes manquées : au
  plus une échéance en avance à chaque appel (le mois courant pour le
  calendaire, le jour suivant calculé pour la recréation à la complétion,
  un jour à la fois dans la boucle de projection des blocs, jamais avant
  `origin_date`).
- Le décalage en jours ouvrés est **borné à deux points d'entrée** du
  domaine tâches (récurrence calendaire, snooze) ; il ne s'applique jamais à
  une saisie manuelle de date, ni à la récurrence de blocs (`weekdays` y
  ignore les fériés par construction).
- Une occurrence recréée hérite toujours de `source="native"` : jamais de
  fuite d'`external_id` d'une source externe vers une occurrence générée
  localement.
