# Dérangements

_Rôle : signaler en un geste qu'on vient d'être dérangé, environ combien de temps et par quoi, et en tirer des statistiques
(combien, combien de temps perdu, quand, d'où) ; le chrono de la tâche en cours est mis en pause après coup, pour que le
temps des tâches reste leur vrai temps. Fichiers couverts (prévus) : `core/model/Interruption.kt`, `core/engine/Interruptions.kt`
(découpe de la séance, pur), `core/stats/InterruptionStats.kt`, `data` (table `interruption`, migration, `ExportCodec`,
`SyncRecords`), `ui/day/NowCard.kt` et `ui/day/InterruptionSheet.kt`, `ui/day/DayScreen.kt` (touche `D`),
`ui/stats/InterruptionCards.kt`, `ui/recap/RecapCard.kt`, `FeatureCatalog` ; dérangements attendus :
`core/engine/ExpectedInterruptions.kt` (pur), `Scheduling.buildDaySchedule`, `DayView`, `core/forecast/SoloForecast.kt`. Tests
(prévus) : `InterruptionsTest`, `InterruptionStatsTest`, `InterruptionMigrationTest`, `InterruptionSyncTest`, `InterruptionUiTest`,
`TeamIsolationTest`, `ExpectedInterruptionsTest`, `SchedulingInterruptionsTest`, `SoloForecastTest` (étendu)._

État : issue [#272](https://github.com/SKOHscripts/Kairos/issues/272), liée à [#271](https://github.com/SKOHscripts/Kairos/issues/271)
(plus de durées réelles) et à [#274](https://github.com/SKOHscripts/Kairos/issues/274) (carte « Ta journée », `ta-journee.md`),
jalon 4.6.0. **Spécifiée le 2026-11-21, non implémentée** ; conception décidée par le propriétaire le même jour.

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

**Dérangements attendus : un temps disponible réaliste** (demande du propriétaire du 2026-11-21). Les dérangements passés disent
ce que les heures à venir vont probablement perdre ; Kairos en tient compte partout où il annonce du temps :

- **Le temps disponible** du jour (« requis … · disponible … », « la journée déborde de … », carte « Ta journée ») retranche
  les dérangements attendus et dit leur part : « disponible 5 h 40, dont ~45 min de dérangements habituels ».
- **La frise du jour** : hors deep work, chaque tâche s'allonge du taux de dérangement de ses heures ; les tâches suivantes
  glissent d'autant.
- **La prévision par tâche** (Monte Carlo, fourchettes P50 / P85, `previsions-taches.md`) tire les dérangements de chaque jour
  simulé, hors créneaux de deep work.
- **Les créneaux de deep work ne sont pas dérangés** (hypothèse du propriétaire) : aucun dérangement n'y est attendu. Les Stats
  comptent tout de même les créneaux deep work dérangés, ce qui vérifie l'hypothèse.
- **Tant que l'historique ne suffit pas** (moins de 10 dérangements ou de 5 jours actifs sur les 8 dernières semaines), rien ne
  change et rien n'est affiché.

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
- Dérangements attendus : taux par heure figé sur un historique construit, zéro en deep work et sous le seuil d'historique
  (`ExpectedInterruptionsTest`) ; disponible diminué, frise allongée hors deep work, et **inchangée sans dérangement** (les
  480 scénarios des fichiers d'or restent verts, `SchedulingInterruptionsTest`, `DifferentialTest`) ; prévision : un créneau de
  deep work avance le P50 d'une tâche, aucune double comptabilisation sur un historique où le chrono était en pause
  (`SoloForecastTest`).

### Hors périmètre / différé

- **Démarrer / arrêter** un dérangement en direct (décision du propriétaire du 2026-11-21 : une durée approximative après coup).
- **Widget et notification Android** : différés (le propriétaire a retenu la carte « Maintenant », le raccourci clavier et la
  note).
- **Sources personnalisées** : la liste est fixe (Quelqu'un, Appel, Message, Moi-même).
- **Espace Équipe** : aucun partage, aucune statistique d'équipe des dérangements.
- **Effet sur les durées apprises** autre que la pause du chrono : le modèle ne lit pas les dérangements.
- **Conseil au bon moment** (« Tu es souvent dérangé le mardi matin : protège un créneau ? ») : piste après les premières données.
- **Dérangements attendus dans l'espace Équipe** (capacité des membres, prévisions d'équipe) : les dérangements sont personnels
  et les autres membres n'en signalent pas.

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

### Dérangements attendus (`core/engine/ExpectedInterruptions.kt`, pur)

- **Taux par heure** `rate(h)`, `h` de 0 à 23, part de temps perdue (0 à 1) : sur les 8 semaines complètes avant la semaine
  courante (comme `SoloForecast.HISTORY_WEEKS`), jours actifs (une séance ou un dérangement), `rate(h) = minutes de dérangement
  tombant dans l'heure h ÷ minutes d'exposition de l'heure h`, l'exposition étant la part de l'heure dans la journée de travail du
  jour (horaires des réglages) **hors créneaux deep work**. Plafond 0,5. Moins de `MIN_EVENTS` = 10 dérangements ou de
  `MIN_DAYS` = 5 jours actifs : `rate` vaut 0 partout (`ExpectedInterruptions.NONE`).
- **Attendu sur une plage** `expected(début, fin, créneaux deep work)` = Σ des minutes de la plage hors deep work × `rate(h)` ;
  le « disponible » de la vue Jour et de la carte « Ta journée » en retranche celui des plages libres restantes du jour.
- **Frise** (`Scheduling.buildDaySchedule`, paramètre `rates`, nul par défaut) : une tâche placée hors deep work à partir de `t`
  occupe la durée `d'` telle que `∫[t ; t + d'] (1 − rate(h)) = d` (minute par minute, arrondie à la minute) ; dans un créneau
  deep work, `d' = d`. Avec `rates` nul ou tout à zéro, le placement est exactement celui d'avant (fichiers d'or).
- **Prévision par tâche** (`SoloForecast`) : la capacité vécue devient **brute** (minutes chronométrées + minutes des
  dérangements du jour) : le chrono mis en pause en exclut déjà les dérangements, les retrancher encore les compterait deux fois.
  Chaque tirage d'un jour tire aussi une part de dérangement parmi les 8 valeurs du même jour de la semaine (dérangements ÷
  (brut − deep work du jour), zéro un jour sans), puis `net = brut − (brut − deep work prévu ce jour) × part`, plancher 0. Le
  deep work prévu d'un jour vient des créneaux `DEEPWORK` (occurrences comprises) ; nouveau sous-générateur `ForecastRandom.stream`
  de domaine « dérangement », par jour absolu : tirages communs entre deux calculs de même graine. Sous le seuil d'historique,
  `part` vaut 0 et le calcul est celui d'avant.

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
- **Deep work non dérangé** (2026-11-21, propriétaire) : hypothèse de calcul, vérifiée par la statistique des créneaux deep work
  dérangés ; écarté un taux propre au deep work (trop peu de données pour l'apprendre).
- **Taux par heure de la journée, sur 8 semaines** : les dérangements dépendent surtout du moment (matin, après le déjeuner) ;
  écartés un taux unique (trop grossier) et un taux par jour et par heure (168 cases, trop peu de données).
- **Capacité brute dans la prévision** : sans elle, le chrono mis en pause retirerait déjà les dérangements de l'historique, et
  les tirer en plus les compterait deux fois ; avec elle, un créneau de deep work prévu se voit dans les fourchettes.
- **Frise allongée plutôt que des blocs « dérangement » posés** : on ne sait pas quand ils viendront ; allonger chaque tâche
  garde la fin de journée juste sans inventer d'horaire.
- **Part du temps de travail mesurée** (chrono + dérangements) plutôt que la journée théorique : on ne prête pas un temps de
  travail qu'on n'a pas mesuré.

### Impacts sur les specs existantes (à reporter à l'implémentation)

- `temps-reel-chrono.md` : découpe d'une séance par un dérangement.
- `ordonnancement.md` : paramètre `rates` de `buildDaySchedule` (frise allongée).
- `previsions-taches.md` : capacité brute et tirage des dérangements hors deep work.
- `vue-jour.md` : action « Dérangé » de la carte « Maintenant », touche `D`.
- `statistiques.md` : carte « Dérangements » et liste ; `bilan.md` : ligne du Bilan.
- `notes-capture.md` : note créée depuis un dérangement.
- `modele-donnees.md`, `synchronisation.md`, `export-import.md` : table, genre d'enregistrement, champ d'export.
- `equipe.md` : isolation.
- `liste-fonctionnalites.md`, `README.md`, `README.fr.md`, `FeatureCatalog` : la fonctionnalité.
