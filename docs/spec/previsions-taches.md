# Prévisions par tâche : fourchettes vérifiées, date de fin, restant

_Rôle : répondre « quand sera-t-elle finie ? » tâche par tâche, en solo comme en équipe, sans jamais donner une date ou
une durée sans fourchette ; et dire ce qui reste quand une tâche dérape. Fichiers couverts : voir chaque étape (§ 2).
Skill associée : `.claude/skills/kairos-monte-carlo/`._

État : **issue #232**, spécifiée le 2026-11-11, réalisée par étapes (une par commit) ; l'état de chaque étape est écrit
dans son titre au § 2. Les étapes non livrées ne modifient aucune autre spec (« Impacts » en fin de document).

## 1. Besoin métier (cahier des charges)

### Objectif / problème

Kairos sait estimer une **durée** (`estimateur-modele.md`) et, pour une équipe, simuler la date de fin d'un **lot**
(`equipe-simulation.md`). Il ne sait pas dire « cette tâche sera finie jeudi ou vendredi ». Trois constats du dépôt :

- la date de fin d'une tâche dépend moins de sa durée que de **l'attente** : ce qui passe avant elle et le temps
  réellement travaillé chaque jour. Un modèle de durée seul ne répond pas à la question ;
- les fourchettes de durée n'ont jamais été **vérifiées sur l'historique de l'utilisateur** (la garde ne vérifie que
  l'erreur médiane), et les durées apprises par série ou par titre n'en ont pas ;
- une tâche qui a dépassé son estimation est comptée « presque finie » (il reste « estimé − passé »), ce qui est
  l'inverse de ce qu'on observe.

### Comportement attendu (utilisateur)

1. **Fourchettes vérifiées.** La fourchette d'une durée estimée par machine learning est élargie ou resserrée d'après les
   dernières tâches faites, pour contenir la durée réelle **8 fois sur 10**. Les Réglages disent le résultat mesuré :
   « Fourchette : 24 de tes 30 dernières tâches dedans ». Sous 20 tâches vérifiables, la fourchette d'origine est gardée
   et les Réglages disent « fourchette pas encore vérifiée ».
2. **Une fourchette pour les séries et les titres.** Une durée apprise d'une série ou d'un titre porte la fourchette de
   ses propres occurrences. Les prévisions d'équipe la tirent à la place de l'écart générique de l'équipe.
3. **Date de fin d'une tâche d'équipe.** Après une simulation « Par effort », chaque tâche du périmètre a sa fin
   probable : « Fin probable : entre le jeu. 12 et le lun. 16 (1 fois sur 2 avant le jeu. 12, 85 fois sur 100 avant le
   lun. 16) », et, si elle a une échéance, sa **marge** : « marge : 3 jours avant l'échéance » ou « échéance dépassée de
   2 jours 85 fois sur 100 ».
4. **Date de fin d'une tâche perso.** Sans espace Équipe : Kairos rejoue la file des tâches à faire, dans l'ordre où il
   les propose, avec le **temps réellement chronométré** des dernières semaines (jours sans travail compris) et des
   durées tirées dans leurs fourchettes. « Pourquoi à cette place ? » dit : « Fin probable : entre mar. 17/11 et
   ven. 20/11, si rien de plus urgent n'arrive. » puis, pour une tâche à échéance, « Échéance tenue 7 fois sur 10. »
   Jamais une date seule ; la ligne suivante dit d'où elle vient (« D'après tes 8 dernières semaines : 2 h 40
   chronométrées par jour travaillé (500 tirages). »). Avec moins de 4 semaines chronométrées sur les 8, rien n'est
   affiché.
5. **Semaine réaliste.** La vue Semaine dit ce que la semaine affichée (courante ou à venir) peut réellement contenir :
   « Réaliste : 12 h à 22 h (tes 8 dernières semaines) · 27 h à échéance ou programmées cette semaine ». Même seuil de
   4 semaines chronométrées.
6. **Restant quand ça dérape.** Quand le temps passé sur une tâche dépasse sa durée attendue, Kairos dit ce qui reste
   d'après les tâches proches qui ont duré **au moins aussi longtemps** : « Tes tâches proches qui ont dépassé 45 min ont
   pris 70 min (entre 55 et 120) ». Rien n'est écrit dans la tâche.
7. **Ce qui finit par se faire.** Pour une tâche qui traîne : « Parmi tes tâches de même priorité ouvertes depuis aussi
   longtemps, 1 sur 5 a fini par être faite ».
8. **Honnêteté** (règle commune) : chaque sortie porte son effectif et sa source en une phrase ; sous son minimum de
   données elle **n'est pas affichée** (ou est marquée « peu fiable » là où une valeur existait déjà). Rien n'est jamais
   écrit dans une tâche ; aucune de ces sorties ne change le plan de la journée.

### Critères de succès

Nommés par leur test, au § 2 de chaque étape. Communs à toutes :

- **Reproductible** au bit près sur JVM, Android et wasm (`+ − × ÷` et `sqrt` seulement, tirages par
  `ForecastRandom`, aucune itération sur une table de hachage pour produire un résultat).
- **Solo intact** : sans donnée d'équipe rien d'équipe n'est lu ; le fichier d'export ne change pas
  (`TeamIsolationTest`).
- **Aucun réglage ajouté.**

### Hors périmètre / différé

- Lois ajustées (log-normale, Weibull) : `ln` et `exp` cassent le bit à bit, et supposent une forme sur 50 points.
- Effet du membre, modèle hybride débit/effort (points 6 et 7 du commentaire de #232) : échantillons trop petits.
- Replanifier ou découper automatiquement ; P95 et pourcentages à l'unité en solo (fausse précision : 5 voisins).
- Arrivées futures dans la file solo : la prévision dit « si rien de plus urgent n'arrive » ; le point 7 en donne la
  contrepartie mesurée.
- Restant conditionnel dans le **plan de charge d'équipe** (`Effort.left`) : changerait les fichiers d'or du plan ; à
  rouvrir après usage du point 6 en solo.

## 2. Solution technique

### Étape A : fourchettes vérifiées, séries et titres (livrée le 2026-11-11)

Fichiers : `core/engine/model/ModelGate.kt`, `ModelEstimator.kt`, `ModelParams.kt`, `core/engine/HistoryEstimators.kt`,
`DurationEstimator.kt` (`spreads`), `ui/settings/ModelStatusLine.kt`. Tests : `IntervalCalibrationTest`,
`IntervalCoverageTest`, `BacktestConsistencyTest` (`core`), `StringsParityTest`.

- **Score** (`ModelGate.score(minutes, low, high, réel)`) : l'écart du réel à la médiane, rapporté à la demi-largeur de
  l'intervalle du côté où il tombe (au moins 5 min, le pas d'arrondi : jamais de division par zéro). ≤ 1 : dedans.
- **Étirement** (`ModelGate.stretch(scores)`) : sur les candidats de la garde (ceux-là mêmes, estimés sans fuite sur les
  seuls échantillons plus anciens), le score de rang `⌈0,8 × (n + 1)⌉`, borné à [0,5 ; 3] (`STRETCH_MIN`,
  `STRETCH_MAX`) ; 1 sous `CALIBRATION_MIN` = 20 candidats. `ModelGate.Result` porte `covered` (candidats dans
  l'intervalle d'origine), `stretch` et `calibrated` (au moins 20 candidats).
- **Application** (`ModelEstimator.estimate`) : chaque côté de l'intervalle brut est multiplié par `stretch`, avant le
  facteur de rythme, puis arrondi et borné comme avant. `ModelEstimator.predict` reste brut : la garde et le rythme
  mesurent l'intervalle d'origine, sinon l'étirement se mesurerait lui-même.
- **Séries et titres** (`HistoryEstimates.fromSamples`) : `low` et `high` = le min et le max des occurrences retenues,
  chaque côté élargi de `1 + 2/k` (la règle des voisins du modèle), arrondis à 5 min, bornés ; nuls pour une seule
  occurrence. `DurationEstimator.spreads` rend désormais tous les intervalles **fiables**, quelle que soit la source :
  les prévisions d'équipe tirent ces tâches dans leur intervalle, sans le facteur d'erreur de l'équipe en plus.
- **Réglages** : sous l'état de la vérification, « Fourchette vérifiée sur tes 30 dernières tâches : 24 dedans avant
  ajustement ; ajustée pour en contenir 8 sur 10. » (`learning_model_coverage`), ou « Fourchette pas encore vérifiée :
  12 tâches comparables sur 20 nécessaires. » (`learning_model_coverage_pending`) ; rien tant que la garde n'a pas ses
  candidats. Prévisions d'équipe : « Dont n tâche(s) à fourchette apprise (série, titre ou machine learning) ».
- **Mesuré** (2026-11-11, `NOVEL`, 10 graines, 1 400 tâches par régime ; couverture avant → après) : une tâche sur
  quatre double 81 → 79 % ; une sur six triple 79 → 80 % ; dérive × 1,6 à mi-parcours 77 → 80 % ; alternance × 0,5 et
  × 1,5 76 → 78 % ; intact 86 → 81 %. L'intervalle d'origine varie de 10 points selon le régime, le vérifié de 3
  (`IntervalCalibrationTest.coverageStaysNearEightyPercentWhateverTheRegime`, bande 75-85 %).

### Étape B : date de fin d'une tâche d'équipe (livrée le 2026-11-11)

Fichiers : `core/team/forecast/ForecastResult.kt` (`TaskFinish`, `taskFinish`, `taskFinishes`), `EffortModel.kt`,
`ForecastAccumulator.kt` (`DrawBatch.endsByTask`), `MonteCarlo.kt` (`Setup.scopeIds`),
`ui/team/forecast/ForecastPanels.kt` (`TaskFinishPanel`), `ForecastScreen.kt`. Tests : `TaskFinishTest` (`core`),
`ForecastUiTest` (`desktopApp`).

- **Aucun tirage de plus** : le modèle par effort calculait déjà la fin de chaque tâche dans chaque tirage, puis la
  triait dans le tirage (pour « Combien d'ici… »), ce qui perdait l'identité de la tâche. Il garde en plus la même
  ligne **avant le tri**, à la place de chaque tâche (`DrawBatch.endsByTask`, `ForecastSamples.endsByTask` et
  `scopeIds`), sous le même plafond `MAX_KEPT_TASK_ENDS` ; la mémoire des fins par tâche double (2 Mo de plus pour
  5 000 tirages × 200 tâches).
- `ForecastResult.taskFinish(id)` : `TaskFinish(taskId, p50, p85, marginDays)`, rang le plus proche sur les fins de
  cette tâche ; date nulle = hors horizon ; `marginDays` = jours du P85 à l'échéance (négatif : dépassée 85 fois sur
  100), nul sans échéance. `null` hors périmètre, pour le modèle par débit, ou si les fins ne sont pas gardées.
  `taskFinishes()` : toutes, les plus tardives d'abord (hors horizon, puis P85, P50, identifiant).
- **Panneau « Fin par tâche »**, avant « Échéances » (modèle par effort seulement) : pour chaque tâche « Entre le 12 oct.
  et le 16 oct. » (« Le 12 oct. » si les deux percentiles coïncident, « Hors horizon »), et à droite la marge
  (« marge : 3 j », ou « dépasse de 2 j » en contour + icône) ; 8 lignes puis « Afficher les n autres ».
- Critères : chaque tâche a P50 ≤ P85 ≤ ceux du périmètre et une tâche au moins porte le P85 du périmètre ; marge ≥ 0
  si et seulement si au moins ⌈0,85 n⌉ tirages tiennent l'échéance ; sans aléa les deux dates coïncident ; même résultat
  en une ou quatre tranches, dans n'importe quel ordre (`TaskFinishTest`).

### Étape C : file solo, semaine réaliste (livrée le 2026-11-11)

Fichiers : `core/forecast/SoloForecast.kt` (pur), `ui/day/DayScreen.kt`, `TaskRow.kt` (`RowContext.forecast`), `Why.kt`,
`ui/week/WeekScreen.kt`. Tests : `SoloForecastTest` (`core`, `commonTest`), `SoloForecastPerformanceTest` (`core`,
`jvmTest`), `SoloForecastUiTest` (`desktopApp`).

- **Capacité vécue** (`SoloForecast.capacity`) : minutes chronométrées (`TimeTracking.withoutOverlaps`) des sessions des
  tâches **perso**, par jour, sur les `HISTORY_WEEKS` = 8 semaines complètes (lundi-dimanche) avant la semaine
  courante : huit valeurs par jour de la semaine, zéros compris, et huit totaux hebdomadaires. `available` si au moins
  `MIN_ACTIVE_WEEKS` = 4 semaines ont du temps ; sinon `run` rend `null` (rien n'est affiché).
- **File** (`SoloForecast.queue`) : tâches perso à faire qui ne sont pas des mères, triées par la clé de la vue Jour
  (`Scheduling.sortKey`, urgence héritée par `Dependencies.derivedUrgency`), puis identifiant ; tri topologique stable
  pour qu'une tâche bloquée suive ses bloqueuses (dépendances sans cycle, `Dependencies.acyclicEdges`).
- **Durées** : `Scheduling.durationMinutes` (saisie, sinon apprise fiable, sinon défaut) moins le temps passé, plancher
  10 %. Facteur par tirage : `EffortSpread.factor` dans l'intervalle de la tâche si elle en a un (étape A) ; sinon un
  rapport réel ÷ estimé des tâches perso faites (`errorFactors` : estimation saisie et temps passé, les 50 plus
  récentes, bornés [0,2 ; 5]), mêlé à la loi triangulaire (0,8 ; 1 ; 2) sous `MIN_FACTORS` = 8, comme
  `ForecastData.errorFactor`.
- **Tirage** (`SoloForecast.run(snapshot, jour, maintenant, fuseau, graine, tirages = 500, extra)`) : jour après jour
  (garde-fou `HORIZON_DAYS` = 365, au-delà « hors horizon »), la capacité du jour est tirée parmi les huit valeurs du
  même jour de la semaine (un jour férié à venir : parmi les dimanches ; aujourd'hui : moins le temps déjà chronométré
  ce jour) et consommée par la première tâche ouverte, commençable (date programmée atteinte, ou son échéance si elle
  tombe avant) et débloquée. Sous-générateurs `ForecastRandom.stream` par identité stable (tâche : `mix(id)`, domaine
  erreur ; jour : numéro absolu du jour, domaine capacité) : tirages **communs** entre deux calculs de même graine.
- **Résultat** : `Result(day, runs, seed, capacity, factorSamples, unestimated, outlooks)` ; `Outlook(p50, p85,
  todayDraws, onTimeDraws)` par tâche (rang le plus proche ; date nulle = hors horizon ; `onTimeDraws` nul sans
  échéance). `extra` ajoute des tâches hypothétiques (identifiants négatifs) à la file : la brique de « et si
  j'accepte ? », sans interface à ce jour.
- **Semaine** : `Capacity.weekMinutes(15)` et `(85)` (rang le plus proche sur huit semaines : la 2e et la 7e) ;
  `SoloForecast.committedMinutes(snapshot, lundi, …)` : minutes restantes des tâches perso à faire, non mères, dont la
  date programmée (à défaut l'échéance) tombe dans la semaine, comme la grille de la vue Semaine.
- **Interface** : `DayScreen` garde un `Lazy` de la prévision par (données enregistrées, jour), de graine **le numéro du
  jour** ; `ScoreBadge` le lit à l'ouverture de « Pourquoi à cette place ? » (`why_finish_range`, `_single`, `_out`,
  `_deadline`, `_source`). `WeekScreen` : `week_realistic`, sous le temps réel de la semaine.
- **Mesuré** : 200 tâches × 500 tirages, à froid, 0,14 s sur JVM (budget 1 s, `SoloForecastPerformanceTest`).
- Critères (`SoloForecastTest`) : sans aléa les fins tombent sur les jours exacts ; le week-end et un jour férié ne
  reçoivent rien ; tâche programmée et tâche bloquée attendent ; échéance : P85 ≤ échéance si et seulement si 85 % des
  tirages la tiennent ; même graine, même résultat, ordre d'entrée sans effet, valeurs figées ; ajouter une tâche
  urgente ne rapproche jamais une autre fin ; moins de 4 semaines actives ou file vide : `null` ; des tâches et sessions
  d'équipe ne changent rien.

### Étape D : restant conditionnel (non implémentée)

### Étape E : ce qui finit par se faire (non implémentée)

### Décisions et pièges tracés

- **Mesurer avant d'afficher** (2026-11-11) : l'ordre du commentaire de #232 mettait la date avant la calibration ;
  inversé. Une date bâtie sur des fourchettes non vérifiées hérite de leur erreur sans le dire.
- **La date solo vient de la file et du temps vécu, pas du plan de la journée** : le plan suppose toute la fenêtre de
  travail ; l'historique chronométré dit ce qui se fait vraiment. Écarté : rejouer `buildDaySchedule` jour après jour
  (suppose une capacité que l'utilisateur n'a pas, et le plan d'un jour futur n'existe pas).
- **Étirer l'intervalle existant plutôt que le remplacer** (prédiction conforme à scores normalisés) : la forme propre
  à chaque tâche est gardée (bien connue = étroite), seule l'échelle est mesurée. Écarté : remplacer l'intervalle par
  les quantiles globaux de réel ÷ prévu (toutes les tâches auraient la même fourchette relative). Écarté : garder
  `1 + 2/k` et 1,25 seuls, des constantes arbitrées sur un bruit synthétique borné.
- **Rang `⌈p (n + 1)⌉`** : au rang `⌈p n⌉`, la couverture mesurée tombait à 76-78 % ; le « + 1 » vise la tâche
  suivante, pas les n déjà vues.
- **Constat honnête** : sur les historiques simulés, l'intervalle d'origine était déjà correct (76-86 %). La
  vérification vaut par ce qu'elle garantit sur un historique réel, que personne n'a mesuré, et par le resserrement
  quand l'intervalle était trop large (86 → 81 %).
- **Décision rouverte** (`estimateur-modele.md` § Prévisions, « séries et titres non rouverts ») : une série de trois
  occurrences à 30, 30 et 35 min n'a pas à recevoir l'écart générique de l'équipe (× 0,2 à × 5).
- **La marge plutôt que « à commencer avant le… »** (étape B) : la date de début au plus tard supposerait de décaler
  tout le plan ; la marge (échéance − P85) est ce que la simulation mesure vraiment.
- **Fins par tâche en double plutôt que dé-triées** : `ScenarioPropertyTest` s'appuie sur les fins triées par tirage
  (statistiques d'ordre) ; les garder et ajouter la ligne non triée ne change aucun résultat existant.
- **Capacité tirée par jour de la semaine, dans l'historique** : week-ends, temps partiel et jours sans chrono sont
  appris sans calendrier ni réglage, et s'expliquent en une phrase (« tes huit derniers mardis »). Écarté : la fenêtre
  de travail des réglages (ce qu'on voudrait faire, pas ce qu'on fait).
- **Graine = le numéro du jour, non affichée** (écart assumé à la règle « la graine s'affiche », skill
  `kairos-monte-carlo`) : la phrase ne clignote pas d'une ouverture à l'autre et reste refaisable ; le nombre de tirages
  est écrit. Une graine dans une infobulle de 300 dp n'aiderait personne.
- **Calcul à la demande** (`Lazy`) : la vue Jour se recalcule chaque minute, la prévision seulement au premier
  « Pourquoi ? » ouvert après un changement de données.
- **Piège : les sessions des tâches d'équipe** ne comptent pas dans la capacité (la file ne contient que le perso ; les
  compter rendrait la prévision optimiste).
- **Raccourcis connus** : les occurrences futures des tâches récurrentes et les tâches d'équipe assignées à « moi »
  ne sont pas dans la file (prévision optimiste pour qui en a beaucoup) ; l'ordre de la file est celui d'aujourd'hui
  (l'urgence qui monte à l'approche d'une échéance n'est pas rejouée).
- **Dans « Pourquoi ? » seulement, pas dans la fiche** : la fiche d'édition n'a pas la file sous la main ; à rouvrir
  avec « et si j'accepte ? ».
- **P50 et P85 seulement, en « fois sur »** : pas de P95 ni de pourcentage à l'unité en solo.

### Impacts sur les specs existantes (à reporter à l'implémentation)

- `temps-reel-chrono.md`, `statistiques.md`, `FeatureCatalog`, README (FR, EN).
