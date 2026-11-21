# Dérangements

_Rôle : signaler en un geste qu'on vient d'être dérangé, environ combien de temps et par quoi, et en tirer des statistiques
(combien, combien de temps perdu, quand, d'où) ; le chrono de la tâche en cours est mis en pause après coup, pour que le
temps des tâches reste leur vrai temps. Fichiers couverts (prévus) : `core/model/Interruption.kt`, `core/engine/Interruptions.kt`
(découpe de la séance, pur), `core/stats/InterruptionStats.kt`, `data` (table `interruption`, migration, `ExportCodec`,
`SyncRecords`), `ui/day/NowCard.kt` et `ui/day/InterruptionSheet.kt`, `ui/day/DayScreen.kt` (touche `D`),
`ui/stats/InterruptionCards.kt`, `ui/recap/RecapCard.kt`, `FeatureCatalog`. Tests (prévus) : `InterruptionsTest`,
`InterruptionStatsTest`, `InterruptionMigrationTest`, `InterruptionSyncTest`, `InterruptionUiTest`, `TeamIsolationTest`._

État : issue [#272](https://github.com/SKOHscripts/Kairos/issues/272), liée à [#271](https://github.com/SKOHscripts/Kairos/issues/271)
(plus de durées réelles). **Spécifiée le 2026-11-21, non implémentée** ; conception décidée par le propriétaire le même jour.

## 1. Besoin métier (cahier des charges)

### Objectif / problème

Être dérangé fait partie du travail : quelqu'un au bureau, un appel, un message, ou sa propre distraction. Kairos mesure le
temps passé sur les tâches, pas le temps perdu en dérangements, ni quand ni d'où ils viennent. Sans cela, difficile de
protéger un créneau de concentration ou d'expliquer pourquoi une journée a débordé. Et un dérangement pendant un chrono
gonfle le temps de la tâche, donc les durées apprises (`estimateur-modele.md`).

« Quand je viens d'être dérangé, je veux le dire en un geste, à peu près combien de temps et éventuellement par quoi. Puis voir
combien de dérangements j'ai, ce qu'ils me coûtent, quand ils arrivent et d'où ils viennent. »

### Comportement attendu (utilisateur)

**Signaler après coup, en un geste.** Personne ne touche un bouton *avant* de répondre à quelqu'un : on signale **après**.

1. Sur la carte « Maintenant », un bouton **« Dérangé »** ; sur le bureau et le web, la touche **`D`** de la vue Jour (inactive
   pendant une saisie et avec Ctrl, Cmd ou Alt, comme `N` et `/`, `vue-jour.md`). Les deux ouvrent un panneau :

   > **J'ai été dérangé**
   > Pendant : `2 min` `5 min` `15 min` `30 min` `1 h` `Autre…`
   > Par (facultatif) : `Quelqu'un` `Appel` `Message` `Moi-même`
   > ☐ En faire une note : [ ce qu'on m'a demandé… ]
   > `Enregistrer`

2. **La source se choisit avant la durée, si on le souhaite** : toucher une durée enregistre aussitôt, avec la source choisie
   (ou aucune). La note : cocher la case ouvre un champ ; une durée touchée enregistre aussi la note. « Autre… » demande un
   nombre de minutes (1 à 240), puis « Enregistrer ».
3. **Un chrono tourne** : il est mis en pause **après coup**. La séance en cours s'arrête à « maintenant − la durée choisie »,
   puis une nouvelle séance de la même tâche reprend maintenant. Exemple : chrono démarré à 9 h, « Dérangé, 15 min » à 10 h →
   45 min sur la tâche, 15 min de dérangement, le chrono continue. Si la durée dépasse la séance en cours (chrono lancé il y a
   10 min, dérangé 15 min), la séance commencée est retirée en entier et le dérangement garde ses 15 min.
4. **Pas de chrono** : le dérangement est noté à cet instant, avec sa durée ; rien d'autre ne change.
5. **« En faire une note »** crée une note dans l'écran Notes (capture, `notes-capture.md`) : un dérangement apporte souvent
   du travail nouveau.
6. Un message confirme (« Dérangé 15 min : noté ») avec **« Annuler »**, qui retire le dérangement, la note créée et rend la
   séance telle qu'elle était.

**Les statistiques.** Une carte **« Dérangements »** dans les Stats, sur la période et les filtres choisis (`statistiques.md`) :

- le **nombre** et le **temps perdu**, et sa **part** du temps de travail mesuré (temps chronométré + temps des dérangements),
  avec la tendance face à la période précédente ;
- **quand** : par heure de la journée et par jour de la semaine (barres) ;
- **d'où** : la répartition par source, la part « non précisée » comprise ;
- la **plus longue plage sans dérangement** dans la journée de travail, en médiane sur les jours de la période ;
- le nombre de **créneaux deep work** traversés par un dérangement.

Le **Bilan** de la semaine et du mois (`bilan.md`) ajoute une ligne, seulement s'il y a des dérangements : « 14 dérangements,
2 h 10, surtout des messages, surtout le mardi matin » (source et moment cités seulement s'ils pèsent au moins un tiers).

**Corriger.** Sous la carte, « Dérangements récents » liste ceux de la période (date, heure, durée, source) ; chacun se
modifie (durée, source) ou se supprime. Modifier ou supprimer **ne retouche pas** les séances déjà découpées : seul l'« Annuler »
immédiat les rend (le chrono a pu tourner depuis).

### Critères de succès

- Un geste suffit : bouton (ou `D`), puis une durée (`InterruptionUiTest`).
- Chrono en cours : la séance est coupée à « maintenant − durée » et reprend, le temps de la tâche baisse d'autant ; durée plus
  longue que la séance : la séance commencée disparaît (`InterruptionsTest`).
- « Annuler » rend exactement l'état d'avant, note comprise (`InterruptionsTest`, `InterruptionUiTest`).
- Les statistiques donnent nombre, temps, part, heures, jours, sources, plus longue plage et deep work interrompus, sur des
  jeux construits à la main (`InterruptionStatsTest`).
- Une base d'avant gagne une table vide ; un export d'avant se lit ; le fichier lié transporte les dérangements
  (`InterruptionMigrationTest`, `InterruptionSyncTest`).
- Aucun dérangement dans l'espace Équipe ni dans ses échanges (`TeamIsolationTest`).

### Hors périmètre / différé

- **Démarrer / arrêter** un dérangement en direct (décision du propriétaire du 2026-11-21 : une durée approximative après coup).
- **Widget et notification Android** : différés (le propriétaire a retenu la carte « Maintenant », le raccourci clavier et la
  note).
- **Sources personnalisées** : la liste est fixe (Quelqu'un, Appel, Message, Moi-même).
- **Espace Équipe** : aucun partage, aucune statistique d'équipe des dérangements.
- **Effet sur les durées apprises** autre que la pause du chrono : le modèle ne lit pas les dérangements.
- **Conseil au bon moment** (« Tu es souvent dérangé le mardi matin : protège un créneau ? ») : piste après les premières données.

## 2. Solution technique

### Modèle (`core/model/Interruption.kt`)

`Interruption(id, at: Instant, minutes: Int, source: InterruptionSource?, taskId: Long?, noteId: Long?, createdAt: Instant)` :
le dérangement couvre `[at − minutes ; at]`, `at` étant l'instant du signalement. `InterruptionSource` : `PERSON`, `CALL`,
`MESSAGE`, `SELF` (codes stables `person`, `call`, `message`, `self`). `minutes` dans `[1 ; 240]`. `KairosSnapshot.interruptions`.

### Découpe de la séance (`core/engine/Interruptions.kt`, pur)

`Interruptions.report(sessions, running, now, minutes) : Change` rend les écritures, sans les faire :

- aucune séance ouverte → rien sur les séances ;
- séance ouverte `s` : `cut = now − minutes` ; si `cut > s.startedAt`, `s.endedAt = cut` et une nouvelle séance `(s.taskId,
  startedAt = now)` ; sinon `s` est retirée et une nouvelle séance part de `now` ;
- l'état d'avant (la séance d'origine) est rendu pour « Annuler ».

Le garde-fou du chrono (`temps-reel-chrono.md` § Garde-fou) s'applique à la nouvelle séance comme à toute séance.

### Statistiques (`core/stats/InterruptionStats.kt`, pur)

Sur les dérangements dont `at` tombe dans la période (fuseau de l'appareil) : nombre, somme des minutes, part = somme /
(minutes chronométrées des séances de la période + somme), tendance contre la période précédente de même longueur ; histogrammes
par heure de `at − minutes / 2` (milieu) et par jour ISO ; sources (`null` = « non précisée ») ; plus longue plage : pour chaque
jour qui a au moins un dérangement, le plus long intervalle de sa journée de travail (`Settings`, horaires du jour) non couvert,
puis la médiane ; deep work : créneaux `DEEPWORK` de la période (occurrences comprises, `Recurrence`) qui chevauchent un
dérangement. Filtres de la période : ceux des Stats qui ont un sens ici (période, et la tâche liée pour le type et le projet).

### Stockage, export, synchronisation (`data`, `core/sync`)

- Table `interruption(id INTEGER PRIMARY KEY AUTOINCREMENT, at TEXT NOT NULL, minutes INTEGER NOT NULL, source TEXT,
  task_id INTEGER, note_id INTEGER, created_at TEXT NOT NULL)`, sans clé étrangère (parti pris de Kairos) ; migration additive
  suivante (`15.sqm`, schéma 16, si la colonne `counts_as_holidays` de `jours-feries.md` passe avant), marqueur de table dans
  `KairosStore.MARKERS`.
- Export : tableau `interruptions` facultatif (absent d'un export d'avant, ignoré par une version d'avant : `ignoreUnknownKeys`).
- Synchronisation : nouveau genre `RecordKind.INTERRUPTION("interruption")`, champs `at`, `minutes`, `source`, `task` et `note`
  (références molles par uid, comme les séances).
- Isolation : données personnelles ; ni la vue d'équipe ni les échanges ne les lisent (`TeamIsolationTest`).

### Interface (`ui`)

- `NowCard` : action « Dérangé » (bouton texte, icône à ajouter dans `make_icons.py`), visible avec ou sans chrono.
- `InterruptionSheet` : panneau (bottom sheet sur téléphone, dialogue sur grand écran), puces de durée et de source (48 dp),
  case et champ de note ; enregistrement par `KairosRepository.reportInterruption(…)`, une transaction (dérangement, séances,
  note). Message avec « Annuler » par `LocalUndoMessages`.
- `DayScreen` : touche `D` (bureau, web ; masquée sur Android, `LocalPlatform`), indiquée à côté du bouton.
- `InterruptionCards` dans les Stats (carte et liste « Dérangements récents » avec modifier / supprimer) ; ligne du Bilan dans
  `RecapCard`.
- Textes dans les six langues ; carte du catalogue « Dérangements » ; puce et ligne du README.

### Décisions et pièges tracés

- **Après coup, durée approximative** (2026-11-21, propriétaire) : écartés le démarrage / arrêt en direct (on ne touche pas le
  téléphone avant de répondre) et le simple compteur (pas de temps perdu).
- **Chrono en pause** (2026-11-21, propriétaire) : le temps d'une tâche reste son vrai temps, les durées apprises restent
  propres (#271) ; écarté un chrono qui continue (temps gonflés).
- **Sources fixes et facultatives** (2026-11-21, propriétaire) : Quelqu'un, Appel, Message, Moi-même ; écartées l'absence de
  source et une liste libre.
- **Endroits** (2026-11-21, propriétaire) : carte « Maintenant », touche `D`, note à la reprise ; widget et notification
  Android différés.
- **Corriger ne retouche pas les séances** : après coup, le chrono a pu tourner ; seul l'« Annuler » immédiat restaure.
- **Part du temps de travail mesurée** (chrono + dérangements) plutôt que la journée théorique : on ne prête pas un temps de
  travail qu'on n'a pas mesuré.

### Impacts sur les specs existantes (à reporter à l'implémentation)

- `temps-reel-chrono.md` : découpe d'une séance par un dérangement.
- `vue-jour.md` : action « Dérangé » de la carte « Maintenant », touche `D`.
- `statistiques.md` : carte « Dérangements » et liste ; `bilan.md` : ligne du Bilan.
- `notes-capture.md` : note créée depuis un dérangement.
- `modele-donnees.md`, `synchronisation.md`, `export-import.md` : table, genre d'enregistrement, champ d'export.
- `equipe.md` : isolation.
- `liste-fonctionnalites.md`, `README.md`, `README.fr.md`, `FeatureCatalog` : la fonctionnalité.
