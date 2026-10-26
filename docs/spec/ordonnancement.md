# Ordonnancement (score WSJF et placement dans la journée)

_Rôle : transformer les tâches à faire et les créneaux du jour en un plan
horaire : dans quel ordre, et à quelle heure. Fichiers couverts :
`kmp/core/src/commonMain/.../core/engine/` (`Scheduling.kt`, `Workdays.kt`,
`Staleness.kt`, `DateTimes.kt`), les tests contre fichiers d'or figés
(`kmp/core/src/jvmTest/.../engine/DifferentialTest.kt`, fichiers d'or
`kmp/core/src/jvmTest/resources/fixtures/`). Les dépendances ont leur spec
(`dependances.md`), la récurrence aussi (`recurrence.md`) ; l'affichage est
dans `vue-jour.md`._

État : **jalon M2**, implémenté. Le comportement du moteur est figé par des
fichiers d'or (voir « Tests contre fichiers d'or »).

## 1. Besoin métier (cahier des charges)

### Objectif / problème

Kairos répond à une question : « qu'est-ce que je fais maintenant, et dans
quel ordre, sachant qu'une réunion de 13 h à 14 h m'empêche de traiter le
sujet urgent avant 14 h 05 ». Deux mécanismes :

1. **L'ordre** (le score) : chaque tâche porte une priorité (valeur, P0 à
   P2) et des points de Fibonacci (effort). Le score **WSJF** (*Weighted
   Shortest Job First*) trie par **coût du retard ÷ effort**, ce qui maximise
   la valeur livrée par unité de temps. Une tâche en retard passe devant
   toutes les autres (palier dur, règle « échéance la plus proche d'abord »).
2. **L'heure** (le placement) : l'ordre est posé dans la journée réelle,
   autour des créneaux occupés, dans les fenêtres de deep work, autour des
   tâches à heure fixe, et en allégeant le creux de l'après-midi.

Une tâche à laquelle il manque la priorité **ou** les points n'est pas
encore clarifiée (méthode GTD) : elle n'entre dans aucun tri et reste « À
traiter ».

### Comportement attendu (point de vue utilisateur)

- (3.4, lot B1) Chaque jour de la semaine peut avoir sa journée de travail, ou être libre (`planning-personnalise.md`) : sans
  horaire propre, rien ne change (les sept jours ont la journée des réglages, week-ends compris, ce que figent les fichiers d'or).
- La liste du jour est triée par score, en retard d'abord, chacune avec une
  heure de début réaliste.
- Une réunion de 13 h à 14 h repousse une tâche à 14 h 05 (marge de 5 min),
  jamais avant, avec la note « à partir de 14h05, après « Réunion » ».
- Une tâche épinglée à une heure y est posée exactement ; le reste s'organise
  autour. Un chevauchement avec un créneau est **signalé**, jamais corrigé.
  Une tâche épinglée dont l'heure est **passée** reste à cette heure et s'affiche
  « À faire maintenant » tant qu'elle n'est pas faite (décision explicite du
  2026-10-12 : le planning ne la cache pas ni ne la déplace ; `DayViewTest`).
- Une fenêtre de deep work n'accueille que les tâches les plus urgentes,
  autant que sa durée le permet, chacune gardant sa durée ; aucune autre
  tâche ne s'y intercale. Seule sa part à venir compte : à 16 h, une
  fenêtre de 10 h à 11 h 30 n'accueille plus rien, et aucune tâche n'est
  jamais placée dans le passé.
- Pendant le creux de l'après-midi (13 h à 16 h, tronc à 15 h, réglables),
  une tâche légère passe devant une tâche lourde sur ce créneau précis, avec
  la note « créneau creux (~15 h) : tâche légère privilégiée ». Le score
  affiché ne change pas. Une tâche en retard ou remontée par une dépendance
  n'est jamais déplacée par le creux.
- **Meilleures heures** (3.6, étape C2 de `planning-personnalise.md`, désactivées par défaut) : quand le réglage `schedule.bestHours` est actif, le
  créneau préfère, entre des tâches proches, celle dont le type est habituellement travaillé à cette heure (détecté avec le chrono) ou dont
  l'utilisateur a donné l'heure préférée. Même patron que le creux : l'effort est divisé par `1 + force × gain` au seul choix du créneau, le score
  affiché ne change pas, une tâche en retard ou remontée par une dépendance n'est jamais déplacée, et la note « ton heure habituelle pour « Dev » » /
  « heure préférée pour « Dev » » n'apparaît que si l'affinité a changé le choix. Détail ci-dessous (§ Meilleures heures au créneau).
- La carte « Maintenant » affiche la charge : minutes requises contre
  minutes disponibles dans ce qui reste de la journée de travail, et le
  débordement.
- Une tâche qui traîne (en retard depuis trop longtemps, ou sans date et
  intouchée depuis longtemps) porte « traîne depuis N j ».
- Plus de P0 non bloquées que le seuil des réglages (5 par défaut) : un
  bandeau rappelle que le signal de priorité se dilue.

### Critères de succès

- Les sorties du moteur Kotlin sont **identiques** aux fichiers d'or sur
  480 scénarios tirés au hasard (ordre, heures, listes, notes, frise, charge,
  scores au bit près, paliers, ancienneté), dont 80 qui exercent le creux ;
  et sur 300 graphes de dépendances et un jeu calendaire (fériés, Pâques de
  1950 à 2100, jours ouvrés, échéances récurrentes, créneaux récurrents).
- Une tâche en retard passe toujours devant une tâche à l'heure.
- À valeur égale, une tâche N fois plus grosse a un score N fois plus faible ;
  un cran de priorité multiplie la valeur par la base (4 par défaut).
- Toute tâche à faire (hors mère à sous-tâches ouvertes) est dans
  **exactement une** liste : placée, sans créneau, plus tard, à traiter (les
  bloquées sont hors du planning, voir `dependances.md`).

### Hors périmètre / différé

- Calibration des poids sur l'historique réel (jalon M4 et au-delà) ; les
  poids restent des réglages.
- Événements de calendrier externes : par le réseau, hors périmètre ; importés d'un fichier `.ics` depuis la 3.10.0
  (issue #136), ils deviennent des créneaux occupés comme les autres (`connexions-externes.md`).
- Glisser-déposer dans la frise : exclu, décision confirmée le 2026-10-26 (issue #102) ; « Déplacer… » et « Heure fixe »
  restent le seul chemin (`accessibilite.md` § Décisions).

## 2. Solution technique

### Score et paliers (`Scheduling`)

- `isOverdue` : échéance ou date programmée ≤ jour.
- `urgencyBucket` (0 à 4, signal visuel seulement) : 0 en retard, 1 P0,
  2 programmée aujourd'hui, 3 échéance d'ici dimanche, 4 le reste.
- `priorityValue` : `base ^ (2 − priorité)` (priorité bornée à 0…2) ; sans
  priorité, `base ^ −1`.
- `timeCriticality` : rampe linéaire vers la date la plus proche (échéance
  ou programmée) : 0 au-delà de `urgencyHorizonDays`, `urgencyPeak` à
  échéance ou dépassée, `peak × (horizon − jours) / horizon` entre les deux.
- `effort` : points ; sinon minutes ÷ 30 bornées à 1…21 ; sinon
  `defaultFibonacciPoints`. La provenance est gardée (`EffortSource`).
- `wsjfBreakdown` / `wsjfScore` : `(valeur + criticité) ÷ effort`, avec la
  date citée (échéance d'abord à date égale) et le nombre de jours, pour
  « Pourquoi à cette place ? ».
- `SortKey` (plus petite = plus urgente) : palier (0 en retard, 1 sinon),
  −score, priorité (999 si absente), échéance (`9999-12-31` si absente), id.

### Placement (`buildDaySchedule`)

**Fenêtre du jour** (3.4, lot B1) : `Schedule.windowFor(jour, réglages)` (`core/engine/Schedule.kt`) donne la plage `[début, fin)` où
placer, ou `null` pour un **jour libre**. Sans `Settings.schedule`, ou pour un jour sans horaire propre, c'est la journée des
réglages. Un horaire incomplet ou incohérent (début ≥ fin) retombe sur la journée des réglages. `buildDaySchedule` y lit
sa fenêtre ; un jour libre donne une fenêtre vide (aucune tâche automatique n'est placée : « Sans créneau » ; les épinglées
gardent leur heure ; `disponible` = 0). `Schedule.displayWindow` (la plage, ou la journée des réglages un jour libre) sert à la
frise (`buildTimeline`) et au rail du réel (`TimeTracking.sessionTimeline`). Le creux de l'après-midi reste en heures absolues ;
la capacité d'équipe lit toujours `workdayStartHour` et `workdayEndHour`.

**Plusieurs plages et contextes** (3.5, lot B2) : `Schedule.windowsFor` donne les plages du jour, dans l'ordre ; `buildDaySchedule` les parcourt avec un curseur commun
(réunions, épinglées et deep work restent des obstacles communs). Le contexte d'une tâche (`Schedule.contextOf`, d'après son type) ne filtre que le **choix au
créneau** : une plage réservée à un contexte n'accueille que ses tâches et celles sans contexte, une plage sans contexte accueille tout. Une tâche qui ne tient
dans aucune plage qui l'accueille est « Sans créneau » ; si son contexte n'a aucune plage ce jour-là, `ScheduledDay.noWindow` le dit. Jamais `sortKey` ni le
score affiché. Détail : `planning-personnalise.md` § Lot B2.

**Durée de placement** (3.4, `durees-apprises.md`) : `durationMinutes(tâche, réglages, learned)` = les minutes de la tâche (≠ 0), sinon
`learned[id]` (la durée apprise **fiable** que `DayView` calcule par `DurationEstimator`), sinon `defaultTaskDurationMinutes`.
`buildDaySchedule` reçoit `learned` (vide par défaut : comportement d'avant, octet pour octet) et le passe à chaque calcul de
durée (placement, capacité requise, repli d'une épinglée).
Entrées : toutes les tâches, les créneaux du jour (ponctuels et occurrences
des récurrents), le jour, l'heure courante (`null` = début de journée), les
réglages, les tâches bloquées et les clés d'urgence effectives
(`dependances.md`). Étapes :

1. À faire → unités de travail (mères à sous-tâches ouvertes exclues) →
   « à traiter » (non qualifiées) → non bloquées → épinglées aujourd'hui ;
   les autres : éligibles aujourd'hui (`isEligibleToday`) ou « plus tard »
   (programmées plus tard sans échéance atteinte). Tri par clé effective.
2. Fenêtre : de `workdayStartHour` (ou maintenant, si c'est aujourd'hui et
   plus tard) à `workdayEndHour`. Créneaux dans la fenêtre : occupés
   (obstacles, marge `meetingBufferMinutes` après) et deep work.
3. Épinglées à leur heure, chevauchement avec un créneau occupé noté
   (`conflictWith`) ; elles deviennent des obstacles sans marge.
4. Chaque fenêtre de deep work, rognée à maintenant le jour même (une
   fenêtre finie disparaît, une fenêtre entamée commence à maintenant), se
   remplit, dans l'ordre, des tâches les plus urgentes qui y tiennent
   entières ; puis la fenêtre devient un obstacle. La frise garde les
   créneaux tels quels.
5. Curseur : à chaque créneau (curseur sauté hors des obstacles), la tâche
   choisie minimise `selectionKey` (clé effective ; pendant le creux, pour
   une tâche non remontée, −score est remplacé par −score de placement, dont
   l'effort est majoré de `penalty × intensité × effort normalisé`). Si la
   tâche chevauche un obstacle, elle est repoussée après lui (+ marge) et
   note l'obstacle (`pushedAfter`, `BUSY`, `PINNED` ou `DEEPWORK`). Si elle commence
   après la fin de journée : « sans créneau », et l'on continue (une plus
   courte peut encore tenir). `dipApplied` si le creux a changé le choix ; `affinity` si l'affinité des meilleures heures l'a changé (voir § Meilleures
   heures au créneau ; alors `dipApplied` reste faux).
6. Charge : requis = durées des tâches à placer ; disponible = fenêtre
   restante moins l'union des créneaux occupés.
7. Durée d'une tâche : estimée, sinon (absente ou 0) le réglage.

`buildTimeline` : créneaux puis tâches, bornés à la journée de travail,
`topMinutes` depuis le début de journée, fonds avant tâches à hauteur égale ;
natures `BUSY`, `DEEPWORK`, `WORK`, `PINNED`, `CONFLICT`, `DEEPWORK_TASK`.

### Jours ouvrés et fériés (`Workdays`)

Lundi = 0 ; ouvré = lundi à vendredi hors fériés. `addBusinessDays`,
`previousBusinessDay`, `onOrBeforeBusinessDay`, `businessDaysBetween`
(`]début, fin]`), `easterSunday` (Meeus/Butcher), fériés français (fixes,
lundi de Pâques, Ascension, lundi de Pentecôte), `buildHolidays` (plus les
dates libres des réglages, illisibles ignorées), `endOfWeek` (dimanche),
`holidaysFor(jour)` : de l'année précédente à deux ans après, vide si rien
n'est demandé.

### Ancienneté (`Staleness`)

`daysStale` : en retard depuis plus de `staleOverdueDays` (la plus ancienne
des deux dates), ou, sans aucune date, `updatedAt` plus vieux que
`staleUntouchedDays` ; sinon `null`.

### Tests contre fichiers d'or

Les fichiers d'or `core/src/jvmTest/resources/fixtures/*.json`
(`scheduling.json`, `dependencies.json`, `calendar.json`, `stats.json`)
contiennent des scénarios tirés au hasard avec graine fixe (réglages, tâches,
créneaux, arêtes, heure) et les sorties attendues. Ils sont la **source de
vérité** du comportement. `DifferentialTest` les rejoue et exige l'égalité
exacte (scores comparés en `Double` relus depuis leur forme texte) :
`scheduling_matches_golden`, `dependencies_match_golden`,
`calendar_matches_golden`. `StatsDifferentialTest.dashboard_matches_golden`
rejoue `stats.json` (jalon M4, voir `statistiques.md`).

Les 480 scénarios restent l'invariant « rien ne change sans réglage » : tout
chantier qui touche le moteur doit les garder verts. Leur script de
génération a été retiré (son historique reste dans git, au tag `v3.3.0`) :
les fichiers ne se régénèrent plus, et un changement voulu du comportement
s'écrit en éditant les fichiers d'or, avec sa justification en spec.

### Meilleures heures au créneau (3.6, étape C2)

`Scheduling.buildDaySchedule(…, affinity: Affinity? = null)`. `Affinity` (`core/engine/Affinity.kt`) est pur et **construit hors du moteur** (par `BestHours.affinity`,
`core/stats`) pour que le moteur ne dépende pas des statistiques : `strength`, les gains détectés par type (12 tranches de 2 h) et les heures préférées par
type, tous deux indexés par le nom du type en minuscules.

- `placementScore = coût du retard ÷ (effort majoré par le creux ÷ affinité.factor(type, heure))`, `factor = 1 + force × gain` ≥ 1 ; l'heure est celle
  du début du créneau, comme pour le creux.
- `selectionKey` ne s'écarte de la clé d'urgence que si la tâche n'est pas remontée, **pas en retard** (palier 0 : l'affinité ne réordonne pas
  les retards entre eux) et que le gain est > 0 ; sinon elle est inchangée (pas de calcul superflu, `affinity` nul ou vide = code d'avant).
- `affinity` de `ScheduledTask` : on compare le choix avec affinité à celui sans (le creux seul) ; il n'est posé que s'ils diffèrent, et `dipApplied`
  vaut alors faux. Le remplissage des fenêtres de deep work et le tri affiché (`sortKey`, score, rang) ignorent l'affinité.
- Décision : **l'affinité est un ajustement doux**, borné à une division par 1,5 au plus : elle départage des tâches de score voisin mais ne fait
  jamais passer une tâche d'une priorité plus faible devant une plus haute (rapport de valeurs de priorité 4 par défaut). Écarté : un classement
  par heure (rigide, contraire au « score d'abord »).
- **v2 (3.9.0, issue #105)** : les gains détectés peuvent venir de la fréquence (v1, par défaut), de l'**efficacité** (estimé ÷ réel par tranche)
  ou de leur moyenne, selon `BestHoursSettings.measure` ; le moteur n'en sait rien, il reçoit des gains. `Affinity.source` rend alors
  `DETECTED`, `EFFICIENT` ou `COMBINED` (`MANUAL` pour une heure préférée), que la note et « Pourquoi ? » formulent. Détail :
  `planning-personnalise.md` § Lot C v2.

### Décisions et pièges tracés

- **Notes rendues en données** (`pushedAfter`, `pushedAfterKind`,
  `conflictWith`, `dipApplied`, `affinity`), jamais en phrases : l'interface les
  formule dans sa langue.
- **`floorMod` / `floorDiv` de Java absents du code commun** : `.mod()` et
  `.floorDiv()` de Kotlin (même sémantique pour les négatifs).
- **Minutes entre deux heures locales calculées en UTC** (`DateTimes.kt`) :
  pas de fuseau, donc pas de changement d'heure.
- **Ancienneté datée en UTC** (`updatedAt` à la date UTC) : comportement
  propre de Kairos, figé par les fichiers d'or.
- **Une tâche archivée ne bloque pas** : le moteur reçoit le statut réel de
  chaque tâche (voir `dependances.md`).
- **Deep work rogné à maintenant** (2026-10-11, demandé après le test
  exploratoire) : remplir la fenêtre entière, passé compris, était faux ;
  à 16 h 42, « À faire maintenant » montrait une tâche posée à 10 h et une
  tâche P0 partait « sans créneau » pendant qu'une fenêtre passée restait
  « remplie ». Les fichiers d'or portent des
  fenêtres déjà rognées : ils décrivent le comportement voulu (31 scénarios
  sur 480 ont changé, tous avec une heure courante).
