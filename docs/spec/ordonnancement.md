# Ordonnancement (score WSJF & time-blocking)

_Rôle : transformer un ensemble de tâches `todo` + créneaux occupés du jour en un
plan horaire concret (« qu'est-ce que je fais maintenant et dans quel ordre »).
Fichier couvert intégralement : `app/tasks_scheduling.py` (fonction pure, aucun
accès DB/réseau). L'urgence dérivée des dépendances (chemin critique,
`derived_urgency` dans `app/tasks_dependencies.py`) et l'UI de l'inbox « À
traiter » (`vue-jour-gtd.md`) sont traitées par d'autres specs — voir § 2.3 pour
leur point d'entrée dans ce module uniquement._

## 1. Besoin métier (cahier des charges)

### Objectif / problème

Kairos existe pour répondre à une seule question posée par l'utilisateur :
« qu'est-ce que je fais maintenant, et dans quel ordre, sachant qu'une réunion
13h-14h m'empêche de traiter le sujet urgent avant 14h05ᐩmarge ». Deux
sous-problèmes distincts, résolus par deux mécanismes complémentaires dans ce
module :

1. **Quel ordre ?** (le *score*) — chaque tâche porte deux métadonnées
   décorrélées, une priorité (valeur, 0-2) et des points de Fibonacci (effort).
   Un tri purement lexicographique par paliers (« en retard » → « priorité max »
   → …) ne sait pas arbitrer une tâche à peine moins urgente mais bien moins
   coûteuse. Le score **WSJF** (*Weighted Shortest Job First*) résout ce
   problème de scheduling classique (règle de Smith 1956, incarnation Agile de
   Reinertsen) : trier par **coût du retard / effort** maximise la valeur
   livrée par unité de temps ; pour ne rater aucune échéance, la règle de
   Jackson/EDD (échéance la plus proche d'abord) est superposée comme palier
   dur au-dessus du score.
2. **À quelle heure ?** (le *placement*) — une fois l'ordre connu, il faut le
   caser dans la journée réelle : créneaux occupés (réunions, TimeTree),
   blocs deep-work réservés, tâches épinglées à une heure fixe par
   l'utilisateur, et une modulation du placement pendant le creux post-déjeuner
   (nom de code de l'outil, « 14h55 »).

Un garde-fou amont conditionne l'entrée dans les deux mécanismes : une tâche
sans priorité **ni** points de Fibonacci n'est pas encore « clarifiée »
(méthode GTD) — elle est retirée de tout tri, avant même de savoir si elle est
bloquée ou programmée plus tard.

### Comportement attendu (point de vue utilisateur)

- Au chargement de la vue jour, la liste des tâches planifiées est déjà triée
  par urgence réelle (score WSJF, paliers durs respectés), avec pour chacune
  une heure de début réaliste compte tenu des créneaux occupés.
- Le cas emblématique doit se vérifier à l'œil : une réunion 13h-14h repousse
  une tâche urgente à 14h05 (marge de 5 min incluse), jamais avant, avec une
  note explicite (« à partir de 14h05, après “<titre réunion>” »).
- Épingler une tâche à une heure précise la pose exactement là ; l'algorithme
  auto s'organise autour. Un chevauchement entre une épinglée et une réunion
  est **signalé** (badge « chevauche… ») mais jamais résolu d'office — c'est
  un choix de l'utilisateur, pas une erreur à corriger.
- Une fenêtre deep-work réservée n'accueille que du travail — jamais une
  tâche « normale » qui s'y intercalerait entre deux tâches deep-work — et se
  remplit avec autant de tâches urgentes que sa durée le permet, chacune
  gardant sa propre durée réelle.
- L'après-midi (créneau creux configurable, défaut 13h-16h, tronc 15h), les
  tâches complexes cèdent spontanément la place à des tâches légères — un
  effet gradué et transparent (note « créneau creux — tâche légère
  privilégiée »), jamais un score WSJF affiché qui change.
- Une tâche sans priorité ni points de Fibonacci n'apparaît **jamais** dans
  l'agenda planifié/sans créneau/plus tard : elle vit dans l'inbox « À
  traiter » tant qu'elle n'est pas qualifiée.
- L'en-tête du jour affiche la charge réelle : minutes requises par les
  tâches à placer vs minutes réellement disponibles dans la fenêtre de
  travail restante, avec un débordement visible si ça ne rentre pas.

### Critères de succès

Repris et fusionnés des phases historiques (SPEC_KAIROS.md, phases 1-2-3-9-12) :

- Une tâche prioritaire avec deadline le jour même, en présence d'une réunion
  13h-14h, n'est affichée réalisable qu'après 14h (marge incluse), jamais avant.
- Épingler une tâche à 9h30 la pose exactement à 9h30 dans la timeline ; l'auto
  s'organise autour ; un chevauchement avec une réunion est signalé.
- La timeline du jour montre heure par heure réunions + blocs de travail, avec
  les durées réelles des tâches (`estimated_minutes`, repli sur le réglage).
- L'en-tête affiche : n faites/n prévues, temps requis vs disponible, alerte si
  débordement.
- Les points de Fibonacci entrent dans l'ordre : à valeur égale, une tâche N×
  plus grosse a un score N× plus faible.
- La valeur d'un cran de priorité est exponentielle (P0 vaut `base`× une P1).
- Une tâche en retard passe toujours devant une tâche non en retard, quel que
  soit l'écart de score (palier dur).
- Une échéance qui approche relève le score progressivement ; au-delà de
  l'horizon (14 jours par défaut), elle ne pèse pas.
- Sans effort renseigné, l'ordre dégrade proprement vers l'ordre de priorité
  (jamais un crash, jamais un tri arbitraire).
- Le score est affiché/transparent, et l'urgence dérivée des dépendances
  continue de fonctionner par-dessus (composition sans modification de
  `_sort_key`).
- Une tâche sans priorité ni points de Fibonacci apparaît en « À traiter »,
  jamais ailleurs (planifié/sans créneau/plus tard) ; qualifiée, elle rejoint
  immédiatement le tri WSJF normal.
- Une tâche bloquée ET non qualifiée reste en « À traiter », pas en
  « Bloquées » — la clarification prime sur tout le reste.
- Une tâche mère à filles ouvertes n'est jamais reléguée en « À traiter »
  (un conteneur n'est pas une unité de travail exécutable).
- Pendant le creux, une tâche complexe cède sa place à une tâche légère sur ce
  créneau précis ; hors du creux, ordre identique à l'ordonnancement d'urgence
  pur. Une tâche en retard ou remontée par une dépendance n'est jamais
  déplacée par le creux (palier dur et chemin critique intouchables).
- Invariant de conservation (vérifié par test de propriété, tirages aléatoires
  combinant bloquée/épinglée/deep-work/récurrente/programmée plus
  tard/sous-tâche) : toute tâche `todo` non-mère-à-filles-ouvertes apparaît
  dans **exactement une** des listes produites (`scheduled`, `unscheduled`,
  `later`, `to_process`) — jamais absente de toutes, jamais dans deux à la fois.

### Hors périmètre / différé

- **Urgence dérivée des dépendances / chemin critique** : calculée en amont par
  `app/tasks_dependencies.py::derived_urgency`, injectée dans ce module via le
  paramètre `urgency_keys` — voir `dependances.md` pour le détail de la
  propagation. Ce module ne fait que la consommer sans la recalculer (voir §
  2.3).
- **UI de l'inbox « À traiter »** (bandeau, badge du champ manquant, ordre
  d'affichage en tête de page) : `vue-jour-gtd.md`. Ce module ne produit que la
  liste `ScheduledDay.to_process`, non triée en interne au-delà du filtre
  d'exclusion.
- **Calibration empirique des poids WSJF** (base de valeur, horizon/pic
  d'urgence comparés aux `WorkSession` réelles) : explicitement laissée à une
  itération future (phase 9 SPEC_KAIROS.md) — les poids sont des `Settings`
  ajustables à l'usage, pas dérivés de premiers principes.
- **Conversion points de Fibonacci ↔ minutes** : n'existe pas et n'est pas
  prévue — `estimated_minutes` reste seul à piloter durée/placement,
  `fibonacci_points` ne pilote que l'effort du score WSJF et du creux. Décision
  actée dès la phase 5 (métadonnées décorrélées).
- **Défauts de durée/priorité par type de tâche** : explicitement différés au
  futur modèle ML sur l'historique `WorkSession` (phase 7).
- **Nudge de découpage des tâches à points Fibonacci élevés** : identifié en
  analyse post-phase-6, non retenu pour la phase 7, jamais implémenté.
- **Drag & drop / mode focus plein écran** : l'app reste sans framework JS
  (contrainte transverse packaging desktop) ; l'épinglage se fait par un
  formulaire heure, pas une manipulation directe.

## 2. Solution technique

### Vue d'ensemble

Le module est une **fonction pure** au sens strict : aucune session SQLAlchemy,
aucun appel réseau, tout est calculable à partir de listes de `Task`/`TimeBlock`
en mémoire — ce qui la rend testable en isolation totale (voir
`tests/test_tasks_scheduling.py`). Point d'entrée unique : `build_day_schedule`
(`app/tasks_scheduling.py:482`), appelée depuis `app/main.py` (route de la vue
jour, ligne ~690) avec les tâches du jour, les blocs occupés fusionnés
(manuels + TimeTree filtrés), `blocked_ids` et `urgency_keys` pré-calculés côté
`app/tasks_dependencies.py`.

Trois couches successives, dans l'ordre où le code les applique :

1. **Filtre de clarification** (« À traiter ») — retire les tâches non
   qualifiées de tout le reste du pipeline, avant même de regarder blocage ou
   programmation.
2. **Score WSJF** — une valeur par tâche, indépendante de l'heure, qui pilote
   l'**ordre**.
3. **Placement temporel** — un curseur qui avance dans la journée, contourne
   les obstacles (occupé, épinglé, deep-work), et choisit à chaque créneau la
   meilleure tâche **pour cette heure précise** (le score WSJF pur, sauf
   pendant le creux de l'après-midi où l'effort effectif est modulé).

Deux algorithmes de placement coexistent et s'articulent (mode « mixte auto +
épinglage », décision actée phase 2) : les tâches épinglées sont posées à heure
fixe (jamais réordonnées), les blocs deep-work remplissent leur fenêtre par
urgence avec la durée propre de chaque tâche, et le reste est placé par un
curseur glouton qui balaie la journée de gauche à droite.

### Détail (fonctions clés)

#### Score WSJF (`app/tasks_scheduling.py:148-214`)

Le score répond à `wsjf_score(task, day, settings)` :

```
score = ( valeur(priorité) + criticité(échéance/date programmée) ) / effort(points)
```

- **`_priority_value`** (ligne 157) — valeur **exponentielle** :
  `base ** (PRIORITY_MAX - priorité)`, `PRIORITY_MAX = 2` (échelle **P0/P1/P2**,
  resserrée à 3 crans — `_PRIORITY_MAX` ligne 117, commentaire explicite :
  « plus de crans dilue le signal plutôt que de l'affiner »). `base` = réglage
  `priority_value_base` (défaut **4.0** → P0=16, P1=4, P2=1). Une tâche
  **sans priorité** vaut `base ** -1` (< P2, la plus faible du barème) : elle
  reste derrière une priorité affirmée à effort/échéance égaux, sans valoir
  zéro (elle continue d'exister dans le tri).
- **`_time_criticality`** (ligne 172) — rampe **linéaire** 0 → `urgency_peak`
  (défaut **8.0**) sur les `urgency_horizon_days` derniers jours (défaut
  **14**) avant l'échéance **ou** la `scheduled_date`, la plus proche des deux.
  Nulle au-delà de l'horizon ; plafonne à `urgency_peak` la veille/le jour
  même. Le dépassement (retard) n'est **pas** traité ici : c'est le palier dur
  du tri (`_sort_key`) qui s'en charge, pas une valeur spéciale dans la rampe.
- **`_effort_points`** (ligne 193) — dénominateur : priorité aux
  `fibonacci_points` saisis ; à défaut, `estimated_minutes` ramenées à
  l'échelle (**≈ 1 point / 30 min**, bornée **1-21**, alignée sur
  `FIBONACCI_SCALE` de `tasks_models.py`) ; à défaut encore,
  `default_fibonacci_points` (réglage, défaut **3**, neutre). Conséquence
  documentée : sans aucun effort renseigné, le tri **dégrade proprement** vers
  l'ordre de priorité pur — plus on renseigne le Fibo, plus le tri s'affine.
- **`wsjf_score`** (ligne 208) — assemble les trois ; fonction pure testable en
  isolation.

#### Buckets d'urgence et palier dur (`app/tasks_scheduling.py:113-145`, `:292-317`)

Distinct du score : c'est un **signal visuel** (bordure colorée du template),
volontairement **découplé** de l'ordre de tri — l'ordre suit le score, la
couleur reste une lecture rapide « à quel point c'est pressé ».

- **`_is_overdue`** (ligne 120) — vrai si `deadline` **ou** `scheduled_date`
  `<= day`. Palier dur qui prime toujours sur le score.
- **`_urgency_bucket`/`urgency_bucket`** (lignes 127-145) — 5 paliers : `0`
  en retard, `1` priorité `== 0` (**strictement P0** — le commentaire ligne
  130-131 trace explicitement le piège historique : `<= 1` incluait encore
  P1, artefact de l'ancienne échelle à 5 crans P0-P4, corrigé lors du
  resserrement à P0/P1/P2), `2` `scheduled_date == day`, `3` deadline cette
  semaine (`_end_of_week`, dimanche de la semaine courante), `4` le reste.
- **`_sort_key`** (ligne 292) — la clé de tri réellement utilisée par le
  placement automatique : `(0 si en retard sinon 1, -wsjf_score, priorité,
  deadline, id)`. Le palier dur (`_is_overdue`) passe toujours devant le
  score ; à l'intérieur d'un palier, le score le plus élevé sort en premier ;
  départages déterministes ensuite (priorité brute, deadline, id) pour un
  ordre stable à score égal. **Plus petite = plus urgente** — convention
  partagée avec l'urgence dérivée des dépendances (`urgency_key`, public,
  ligne 313, base de l'urgence dérivée calculée côté route).

#### Creux de l'après-midi (`app/tasks_scheduling.py:217-263`)

Modulation du **placement**, jamais du score affiché (commentaire d'en-tête
ligne 220-224, invariant central) : pendant le creux, l'effort effectif d'une
tâche est gonflé **proportionnellement à sa complexité** (points de
Fibonacci) — une tâche légère lui prend le créneau. Hors du creux, effort
effectif == effort réel ⇒ placement identique à l'ordonnancement d'urgence pur.

- **`_dip_intensity`** (ligne 227) — triangle asymétrique ∈ [0,1] : 0 aux
  bords de la fenêtre `[cognitive_dip_start_hour, cognitive_dip_end_hour]`
  (défaut **13h-16h**), 1 au tronc `cognitive_dip_trough_hour` (défaut
  **15h**, « tronc statistique »). Retourne 0 si `cognitive_dip_enabled` est
  faux ou `cognitive_dip_penalty <= 0` (interrupteur général).
- **`_dip_adjusted_effort`** (ligne 244) — effort réel × `(1 +
  cognitive_dip_penalty * intensity * norm)`, où `norm = (effort - 1) /
  (_MAX_FIBONACCI - 1)` (`_MAX_FIBONACCI = 21`, borne de normalisation ligne
  41-43 : « une tâche à 21 points est aussi complexe que possible »). Une
  tâche à **1 point n'est jamais pénalisée** (elle « passe » partout,
  `norm = 0`) ; une tâche à **21 points l'est au maximum** (au tronc, avec
  `cognitive_dip_penalty = 1.0` par défaut : effort ×2, score de placement
  ÷2). Hors creux, retourne l'effort réel sans modification.
- **`_placement_score`** (ligne 257) — même numérateur (coût du retard) que
  `wsjf_score`, divisé par l'effort **renchéri**. Égal à `wsjf_score` hors du
  creux — un seul point de divergence, l'effort au dénominateur.
- **`_selection_key`** (ligne 319) — clé de choix d'une tâche **pour un
  créneau donné**, utilisée par le curseur de placement. Réduit **exactement**
  à la clé d'urgence propre dans deux cas : hors de la fenêtre du creux, ou
  tâche remontée par une dépendance (chemin critique — intouchable, jamais
  réordonnée par le creux). Sinon (tâche ordinaire, pendant le creux) : le
  palier dur (0/1) est conservé tel quel, seul le score intra-palier est
  remplacé par `_placement_score` à l'heure du créneau.

#### Placement — curseur, obstacles, épinglage, deep-work (`app/tasks_scheduling.py:344-697`)

- **`_Obstacle`** (ligne 94) — intervalle indisponible pour le placement
  automatique, avec une marge (`buffer`) à laisser après : celle des réunions
  (`meeting_buffer_minutes`, défaut **5 min** — le fameux « 14h05 ») pour un
  créneau `kind='busy'`, **aucune** marge après une tâche épinglée ou un bloc
  deep-work (`kind='pinned'`) — enchaîner deux blocs de travail ne demande pas
  de « temps de sortie de réunion ».
- **`_advance_past_obstacles`** (ligne 344) — premier instant ≥ curseur hors
  de tout obstacle, marge incluse, en enchaînant les obstacles adjacents. Sert
  à évaluer l'énergie cognitive du créneau à l'heure de placement **réelle**
  (un curseur tombant au milieu d'une réunion 13h-14h est évalué à 14h05, pas
  à 13h — pertinent pour savoir si le creux s'applique à ce créneau).
- **`_busy_minutes_in_window`** (ligne 362) — minutes occupées dans une
  fenêtre, chevauchements fusionnés (merge d'intervalles trié) pour ne jamais
  compter deux fois un recouvrement entre créneaux occupés.
- **Étape 1 — épinglées** (lignes 567-590) : posées **exactement** à
  `pinned_start`, triées entre elles par heure. Un chevauchement avec un
  créneau `busy` est détecté (`clash`) et **signalé** (`conflict=True`,
  `conflict_note`) — jamais résolu d'office, choix assumé de l'utilisateur
  (commentaire d'en-tête ligne 8-11). Chaque épinglée devient ensuite un
  obstacle `kind='pinned'` (sans buffer) pour la suite du placement.
- **Étape 1bis — blocs deep-work** (lignes 592-621) : chaque fenêtre triée par
  heure de début est remplie par autant de tâches que nécessaire, **les plus
  urgentes non encore placées** (`auto`, déjà trié par `_key`), chacune
  gardant **sa propre durée** (`_duration_minutes`) — jamais la durée du bloc.
  Correctif tracé (commit `97101b0`, « Corrige la répartition des tâches dans
  les blocs deep-work ») : avant, la première tâche urgente occupait toute la
  fenêtre quelle que soit sa durée réelle, laissant le reste inexploité — la
  boucle `while slot_cursor < block.end` répare ce cas en avançant le curseur
  interne du bloc tâche par tâche. Le bloc entier devient ensuite un obstacle
  `kind='pinned'` (rempli ou non) : une fenêtre dédiée n'accueille jamais une
  tâche « normale » intercalée entre deux tâches deep-work.
- **Étape 2 — placement automatique par curseur** (lignes 623-681) : boucle
  `while remaining` — à chaque itération, `slot_start` = heure réelle du
  prochain créneau libre (`_advance_past_obstacles`), la tâche choisie est
  `min(remaining, key=_selection_key(..., slot_start, ...))` (le creux
  influence le choix ici, pas avant). `urgency_pick` (ligne 643) recalcule en
  parallèle ce qu'aurait donné l'ordre d'urgence pur, uniquement pour savoir
  si annoter `dip_note` (transparence : la note n'apparaît **que** si le creux
  a réellement changé la tâche retenue). La tâche choisie est ensuite pous­sée
  (`pushed`) devant tout obstacle qui chevauche sa durée réelle, avec la note
  de report adaptée selon `kind` (« après “<titre>” » pour `busy`, «
  après “<titre>” (épinglée) » pour `pinned`). Si `start >= workday_end`, la
  tâche part en `unscheduled` mais **la boucle continue** (commentaire ligne
  669 : « une tâche plus courte peut encore tenir : on n'abandonne pas la
  suite ») — pas d'abandon en cascade.
- **Charge du jour** (lignes 685-696) : `required` = somme des durées des
  tâches **planifiables** (`schedulable`, mères déjà exclues côté
  `work_units` ligne 522) ; `available` = fenêtre restante
  (`effective_start` → `workday_end`) moins les minutes occupées — les
  épinglées comptent comme du **travail**, pas de l'« occupé ».

#### Gate « À traiter » (`app/tasks_scheduling.py:499-537`, docstring `build_day_schedule`)

Appliquée **avant** tout le reste, à l'intérieur de `build_day_schedule` :

```python
todo = [t for t in tasks if t.status == "todo"]
parents_with_open_children = {t.parent_id for t in todo if t.parent_id is not None}
work_units = [t for t in todo if t.id not in parents_with_open_children]
to_process = [t for t in work_units if t.priority is None or t.fibonacci_points is None]
```

- **Unité de travail** (`work_units`) : une tâche **mère** dont au moins une
  sous-tâche est encore `todo` n'est **jamais** une unité de travail — ce sont
  ses filles qui le sont. Une mère dont toutes les filles sont faites redevient
  planifiable (le travail restant devient le sien). Ce filtre s'applique
  **avant** le filtre de clarification : une mère à filles ouvertes est donc
  structurellement exemptée de l'inbox, quel que soit son propre
  `priority`/`fibonacci_points`.
- **Condition d'exclusion** : `priority is None` **OU** `fibonacci_points is
  None` — les **deux** champs sont requis pour sortir de l'inbox, pas l'un ou
  l'autre (les deux axes décorrélés qui nourrissent le score WSJF, valeur et
  effort). Une tâche non qualifiée est retirée dans `result.to_process`
  **avant même** de regarder si elle est bloquée (`blocked_ids`) ou éligible
  aujourd'hui (`is_eligible_today`) — la clarification prime sur toute
  organisation ultérieure (précédence actée phase 12 : bloquée + non qualifiée
  → « À traiter », pas « Bloquées » ; épinglée + non qualifiée → « À traiter »,
  l'épinglage ne contourne pas la clarification, une tâche dans `to_process`
  ne peut d'ailleurs jamais atteindre `pinned_today` puisqu'elle est retirée de
  `non_blocked` avant ce calcul, ligne 527-535).
- **Universalité de la règle** : s'applique à toute source, native ou
  importée (GitLab assigné) — aucune synchro n'écrit jamais ces deux champs
  (`priority`/`fibonacci_points` restent des décisions humaines), donc le
  mécanisme est uniforme sans code spécifique par source.

#### Éligibilité du jour (`app/tasks_scheduling.py:276-289`)

`is_eligible_today(task, day, pinned_for_today)` — une tâche épinglée
explicitement sur `day` est **toujours** éligible (l'épinglage est un choix
plus fort que la programmation). Sinon, masquée (→ `ScheduledDay.later`)
**seulement si** `scheduled_date` est future **et** qu'aucune échéance
n'approche (`deadline is None or deadline > day`) — l'échéance reste un
garde-fou qui prime toujours. `scheduled_date` (« quand je compte m'y
mettre ») est ainsi distinct de `deadline` (l'échéance réelle imposée de
l'extérieur) : les deux coexistent sans se remplacer.

#### Timeline et projections (`app/tasks_scheduling.py:379-479`)

- **`TimelineEntry`** / **`build_timeline`** — projette créneaux occupés et
  tâches planifiées sur une grille verticale en minutes (`top_min`/
  `height_min`, offsets depuis `workday_start`, bornés à la fenêtre affichée)
  pour un rendu 100 % serveur (le template convertit directement en pixels,
  aucun JavaScript). `kind` distingue `busy`/`deepwork`/`work`/`pinned`/
  `deepwork-task`/`conflict` — tri par position puis fonds de blocs avant
  tâches au-dessus (ligne 435).
- **`session_timeline_entries`** — projette les `WorkSession` réelles (rail
  « réel » à côté du « planifié ») ; conversion UTC → local naïve
  (`_to_local_naive`, ligne 439) car les blocs/épinglages de la timeline sont
  en heure locale naïve ; une session ouverte court jusqu'à `now` (injectable,
  fonction pure testable).

### Décisions et pièges tracés

Micro-décisions et « pourquoi » relevés dans les commentaires de code, à ne pas
« corriger » sans en reparler :

1. **Palier dur > score, toujours.** Une échéance dépassée passe devant quel
   que soit l'écart de score WSJF — choix « hybride » explicitement acté avec
   l'utilisateur (`_sort_key`, ligne 298), pas un pur ratio continu.
2. **Barème de priorité resserré P0/P1/P2, pas P0-P4.** Commit
   `5bdd4a9` (« Simplifier l'échelle de priorité à P0/P1/P2 et rééquilibrer le
   WSJF ») : `_PRIORITY_MAX` passe de 4 à 2, et `priority_value_base` de 2.0 à
   4.0 (`base² = ancienne_base⁴`) **pour conserver exactement la même
   amplitude bout-à-bout** (P0=16 … plus-faible=1) avec deux crans en moins —
   sans ce rééquilibrage, réduire `_PRIORITY_MAX` seul aurait mécaniquement
   écrasé le poids de la priorité face à `urgency_peak` (échelle fixe
   indépendante), au point qu'une tâche complexe P0 aurait pu perdre son
   créneau face à une broutille. Le garde-fou de surcharge
   (`count_max_priority_tasks`) a été resserré en même temps à **P0
   strictement** (pas P0+P1) pour la même raison de dilution du signal.
   Trace résiduelle dans le code : le commentaire ligne 130-132
   (`_urgency_bucket`) documente explicitement que `<= 1` était un artefact de
   l'ancienne échelle, corrigé en `== 0`.
3. **Score WSJF affiché ne bouge jamais avec le creux.** Invariant central du
   § creux de l'après-midi (commentaire ligne 220-224) : seul le *placement*
   change, jamais la *valeur* de la tâche — cohérent avec le principe « le
   score est une propriété de la tâche, pas de l'heure ».
4. **Chemin critique intouchable par le creux.** `_selection_key` (ligne
   319-341) court-circuite la modulation dès que `effective != own` (la tâche
   a été remontée par une dépendance) : le creux ne doit jamais masquer une
   urgence dérivée du chemin critique.
5. **1 point jamais pénalisé, 21 points au maximum.** `_dip_adjusted_effort`
   normalise `(effort - 1) / (_MAX_FIBONACCI - 1)` — borne explicite choisie
   pour qu'une micro-tâche « passe partout » même au tronc du creux, alors
   qu'une tâche à la complexité maximale de l'échelle Fibonacci subit la pleine
   pénalité.
6. **Marge de sortie de réunion, jamais après une tâche épinglée/un bloc
   deep-work.** `_Obstacle.buffer` distingue `kind='busy'` (marge
   `meeting_buffer_minutes`) de `kind='pinned'` (`timedelta(0)`) — enchaîner
   deux blocs de travail ne demande pas de temps de « sortie de réunion »
   (ligne 101-104).
7. **Chaque tâche deep-work garde sa propre durée, jamais celle du bloc.**
   Correctif tracé du commit `97101b0` : avant, la première tâche urgente
   consommait toute la fenêtre deep-work sans regard sur sa durée estimée,
   gaspillant le reste du créneau. La boucle interne (`slot_cursor`) répare
   ça en remplissant le bloc tâche par tâche jusqu'à ce qu'aucune candidate ne
   tienne plus dans le temps restant.
8. **Une tâche trop grande pour la fin de journée n'arrête pas la boucle.**
   Ligne 667-669 : `unscheduled.append(task); continue` — explicitement
   commenté « une tâche plus courte peut encore tenir : on n'abandonne pas la
   suite ». Évite qu'une seule grosse tâche en fin de tri masque des tâches
   plus courtes qui auraient pu se caser.
9. **Sans effort renseigné, dégradation vers l'ordre de priorité — jamais un
   crash ni un ordre arbitraire.** `_effort_points` a trois niveaux de repli
   (Fibonacci → minutes ramenées à l'échelle → réglage neutre par défaut) :
   le score reste toujours calculable, la qualité du tri s'affine avec ce qui
   est renseigné plutôt que d'exiger un remplissage complet.
10. **La clarification (« À traiter ») prime sur le blocage et l'épinglage.**
    Ordre des filtres dans `build_day_schedule` : `to_process` est calculé
    **avant** `blocked_ids`/`pinned_ids`/`is_eligible_today` (lignes 522-535)
    — une tâche non qualifiée ne peut structurellement jamais apparaître en
    « Bloquées » ni être posée sur l'agenda via son épinglage, quel que soit
    son état par ailleurs.
11. **Les mères ne sont jamais des unités de travail.** `parents_with_open_
    children` est calculé une fois (ligne 519) et retranche ces tâches de
    `work_units` avant tout le reste du pipeline (clarification, blocage,
    éligibilité, placement) — cohérent partout, pas seulement dans un des
    filtres.
12. **`urgency_bucket` est un signal visuel, `_sort_key`/`wsjf_score` pilotent
    le tri — volontairement découplés.** Documenté explicitement dans la
    docstring publique de `urgency_bucket` (ligne 141-145) pour éviter qu'un
    futur changement du score ne suppose à tort qu'il doit rester cohérent
    avec la couleur de bordure affichée.
13. **`effective_start` ne recule jamais avant l'heure actuelle du jour
    affiché.** Lignes 551-553 : si `now` tombe le jour même et après
    `workday_start`, le curseur de placement démarre à `now` — la
    planification ne propose jamais un créneau déjà passé pour aujourd'hui
    (mais reste `workday_start` pour un jour différent, ex. la vue semaine).

### Invariants et garde-fous

- **Conservation stricte** : toute tâche `todo` non-mère-à-filles-ouvertes
  apparaît dans **exactement une** des quatre listes de `ScheduledDay`
  (`scheduled`, `unscheduled`, `later`, `to_process`) — jamais perdue, jamais
  dupliquée. Vérifié par un test de propriété à tirages aléatoires (héritage
  de l'exigence actée phase 4, étendue à la gate « À traiter » en phase 12).
- **Pureté fonctionnelle totale** : `build_day_schedule` et toutes les
  fonctions du module n'effectuent aucune I/O — `blocked_ids`/`urgency_keys`
  sont **pré-calculés** par l'appelant (`app/tasks_dependencies.py`) et
  injectés, jamais recalculés ici. Condition de la testabilité en isolation
  totale revendiquée dès l'en-tête du fichier.
- **Le score WSJF est une propriété de la tâche, indépendante de l'heure** ;
  seul l'effort **effectif** utilisé pour le *placement* varie avec le creux
  (`_placement_score` vs `wsjf_score`) — ne jamais faire dépendre `wsjf_score`
  lui-même de `when`.
- **Le palier dur (retard) et le chemin critique (dépendances) ne sont
  jamais réordonnés**, ni par le score WSJF intra-palier, ni par le creux de
  l'après-midi, ni par aucune évolution future de `_selection_key` — c'est la
  garantie centrale qu'« aucune fiche urgente n'est jamais mangée » par un
  raffinement de confort.
- **Aucune tâche épinglée n'est déplacée d'office** en cas de conflit — signalé
  uniquement (`conflict`/`conflict_note`), jamais résolu par l'algorithme.
- **Une fenêtre deep-work n'accueille jamais une tâche « normale » intercalée**
  entre deux tâches deep-work : le bloc entier reste un obstacle unique pour
  le placement automatique général, qu'il soit rempli en totalité ou non.
- **La gate « À traiter » est absolue et universelle** : aucune source (native,
  GitLab) n'écrit jamais `priority`/`fibonacci_points` automatiquement ; le
  filtre s'applique donc identiquement à toute tâque quelle que soit son
  origine, sans exception ni code spécifique par source.
- **Désactiver le creux (`cognitive_dip_enabled=false` ou
  `cognitive_dip_penalty=0`) restaure un comportement strictement identique**
  à l'ordonnancement d'urgence pur — `_dip_intensity` retourne alors
  systématiquement 0, ce qui fait dégénérer `_selection_key` vers `_sort_key`
  et `_placement_score` vers `wsjf_score` partout.
