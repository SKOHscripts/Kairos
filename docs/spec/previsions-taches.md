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
   durées tirées dans leurs fourchettes. « Pourquoi ? » et la fiche disent : « Fin probable : entre mar. 17 et ven. 20,
   si rien de plus urgent n'arrive ». Jamais une date seule ; la phrase dit d'où elle vient (« d'après tes 8 dernières
   semaines : 2 h 40 chronométrées par jour travaillé »).
5. **Semaine réaliste.** La vue Semaine dit ce que la semaine peut réellement contenir : « Réaliste : 12 à 22 h
   (tes 8 dernières semaines) · 27 h à échéance ou programmées cette semaine ».
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

### Étape B : date de fin d'une tâche d'équipe (non implémentée)

### Étape C : file solo, semaine réaliste (non implémentée)

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
- **P50 et P85 seulement, en « fois sur »** : pas de P95 ni de pourcentage à l'unité en solo.

### Impacts sur les specs existantes (à reporter à l'implémentation)

- `equipe-simulation.md` : fins par tâche gardées non triées, date par tâche.
- `vue-semaine.md`, `vue-jour.md`, `temps-reel-chrono.md`, `reglages.md`, `i18n.md`, `architecture.md`,
  `docs/spec/README.md` (index), `FeatureCatalog`, README (FR, EN).
