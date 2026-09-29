# Ordonnancement (score WSJF et placement dans la journée)

_Rôle : transformer les tâches à faire et les créneaux du jour en un plan
horaire : dans quel ordre, et à quelle heure. Fichiers couverts :
`kmp/core/src/commonMain/.../core/engine/` (`Scheduling.kt`, `Workdays.kt`,
`Staleness.kt`, `DateTimes.kt`), les tests différentiels
(`kmp/core/src/jvmTest/.../engine/DifferentialTest.kt`, fixtures
`kmp/core/src/jvmTest/resources/fixtures/`) et leur générateur
(`kmp/tools/gen_fixtures.py`). Les dépendances ont leur spec
(`dependances.md`), la récurrence aussi (`recurrence.md`) ; l'affichage est
dans `vue-jour.md`._

État : **jalon M2**. Portage **à l'identique** du moteur de Kairos 2
(`app/tasks_scheduling.py`, `app/workdays.py`, `app/tasks_staleness.py`,
spec `docs/spec/ordonnancement.md`), prouvé par tests différentiels.

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

- La liste du jour est triée par score, en retard d'abord, chacune avec une
  heure de début réaliste.
- Une réunion de 13 h à 14 h repousse une tâche à 14 h 05 (marge de 5 min),
  jamais avant, avec la note « à partir de 14h05, après « Réunion » ».
- Une tâche épinglée à une heure y est posée exactement ; le reste s'organise
  autour. Un chevauchement avec un créneau est **signalé**, jamais corrigé.
- Une fenêtre de deep work n'accueille que les tâches les plus urgentes,
  autant que sa durée le permet, chacune gardant sa durée ; aucune autre
  tâche ne s'y intercale.
- Pendant le creux de l'après-midi (13 h à 16 h, tronc à 15 h, réglables),
  une tâche légère passe devant une tâche lourde sur ce créneau précis, avec
  la note « créneau creux (~15 h) : tâche légère privilégiée ». Le score
  affiché ne change pas. Une tâche en retard ou remontée par une dépendance
  n'est jamais déplacée par le creux.
- La carte « Maintenant » affiche la charge : minutes requises contre
  minutes disponibles dans ce qui reste de la journée de travail, et le
  débordement.
- Une tâche qui traîne (en retard depuis trop longtemps, ou sans date et
  intouchée depuis longtemps) porte « traîne depuis N j ».
- Plus de P0 non bloquées que le seuil des réglages (5 par défaut) : un
  bandeau rappelle que le signal de priorité se dilue.

### Critères de succès

- Les sorties du moteur Kotlin sont **identiques** à celles de Kairos 2 sur
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
- Événements de calendrier externes (TimeTree) : retirés de Kairos 3.
- Glisser-déposer dans la frise.

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
4. Chaque fenêtre de deep work se remplit, dans l'ordre, des tâches les plus
   urgentes qui y tiennent entières ; puis la fenêtre entière devient un
   obstacle.
5. Curseur : à chaque créneau (curseur sauté hors des obstacles), la tâche
   choisie minimise `selectionKey` (clé effective ; pendant le creux, pour
   une tâche non remontée, −score est remplacé par −score de placement, dont
   l'effort est majoré de `penalty × intensité × effort normalisé`). Si la
   tâche chevauche un obstacle, elle est repoussée après lui (+ marge) et
   note l'obstacle (`pushedAfter`, `BUSY` ou `PINNED`). Si elle commence
   après la fin de journée : « sans créneau », et l'on continue (une plus
   courte peut encore tenir). `dipApplied` si le creux a changé le choix.
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

### Tests différentiels

`kmp/tools/gen_fixtures.py` fait tourner le moteur Python sur des scénarios
tirés au hasard avec graine fixe (réglages, tâches, créneaux, arêtes,
heure) et écrit entrées et sorties en JSON ; `DifferentialTest` rejoue et
exige l'égalité exacte (scores comparés en `Double` relus depuis le `repr`
Python). Les fixtures sont commitées : le générateur ne se relance que s'il
change lui-même, sur un checkout du tag `v2.6.0` de Kairos 2
(`KAIROS2_SRC`, `architecture.md` § Outils). Le même générateur écrit `stats.json` (jalon M4, rejoué par
`StatsDifferentialTest`, voir `statistiques.md`).

### Décisions et pièges tracés

- **Notes rendues en données** (`pushedAfter`, `pushedAfterKind`,
  `conflictWith`, `dipApplied`), jamais en phrases : l'interface les
  formule dans sa langue. Le générateur retrouve ces données en analysant
  les phrases de Kairos 2.
- **`floorMod` / `floorDiv` de Java absents du code commun** : `.mod()` et
  `.floorDiv()` de Kotlin (même sémantique pour les négatifs).
- **Minutes entre deux heures locales calculées en UTC** (`DateTimes.kt`) :
  pas de fuseau, donc pas de changement d'heure, comme les `datetime` naïfs
  de Kairos 2.
- **Ancienneté datée en UTC** (`updatedAt` à la date UTC) : parité exacte
  avec Kairos 2, qui prend la date d'un horodatage UTC naïf.
- **Une tâche archivée ne bloque pas** : le moteur reçoit le statut réel de
  chaque tâche (voir `dependances.md`).
