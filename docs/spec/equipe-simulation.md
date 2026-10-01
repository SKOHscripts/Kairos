# Espace Équipe : prévisions Monte Carlo et scénarios

_Rôle : répondre aux questions de délai avec leur incertitude, par
simulation : quand ce lot sera-t-il fini, quelles échéances sont en danger
et avec quelle probabilité, quelle est l'issue la plus probable, et que
changerait tel ou tel scénario. Fichiers prévus :
`kmp/core/.../core/team/forecast/` (`MonteCarlo.kt`, `ThroughputModel.kt`,
`EffortModel.kt`, `ForecastData.kt`, `Percentiles.kt`, `Scenario.kt`,
`ForecastResult.kt`), `kmp/data/` (table `team_scenario`),
`kmp/ui/.../team/forecast/` (`ForecastScreen.kt`, `Histogram.kt`,
`ScenarioEditor.kt`, `ScenarioComparison.kt`). S'appuie sur le plan de
charge de `equipe-charge.md`. Skill Claude Code associée :
`.claude/skills/kairos-monte-carlo/`._

État : **jalon E5 implémenté (2026-10-01)**.

## 1. Besoin métier (cahier des charges)

### Objectif / problème

Une date unique (« fin le 14 octobre ») est presque toujours fausse : les
estimations dérivent, les débits varient. Le manager a besoin de
**fourchettes** et de **probabilités**, calculées sur l'historique réel de
son équipe quand il existe, et de pouvoir comparer des **scénarios** avant
de décider.

### Questions auxquelles l'écran répond

1. **Quand ?** Date de fin d'un périmètre (tout le travail assigné, le
   backlog, une catégorie, un membre, une sélection de tâches), en
   percentiles : « 50 % de chances avant le 9 oct., 85 % avant le 16 oct.,
   95 % avant le 21 oct. ».
2. **Combien ?** Nombre de tâches finies d'ici une date choisie : « d'ici
   le 31 oct. : au moins 14 tâches (85 %), 18 (50 %) ».
3. **Quelles échéances ?** Pour chaque tâche à échéance, la probabilité de
   la tenir ; les tâches sous `teamDeadlineRiskPercent` (70 % par défaut)
   sont « en danger ».
4. **Qu'est-ce qui pèse ?** L'**indice de criticité** de chaque tâche (part
   des tirages où elle finit en retard) et le **goulot** : le membre le plus
   souvent dernier à finir.
5. **Issue la plus probable** : la distribution du nombre d'échéances
   manquées (« 0 retard : 22 % · 1 : 41 % · 2 : 26 % · 3 ou plus : 11 % »),
   et l'**ensemble de retards le plus fréquent** (« le plus probable :
   seule « Migration API » en retard, 38 % des tirages »).
6. **Et si… ?** Les mêmes chiffres sous un scénario, côte à côte avec la
   situation réelle.

### Comportement attendu (utilisateur)

#### Destination Prévisions

- En tête : le **périmètre** (menu : Travail assigné, Travail assigné +
  backlog, une catégorie, un membre, la sélection faite dans le Backlog) et
  le **modèle** (« Par effort » par défaut, « Par débit »), puis
  « Lancer la simulation » (bouton primaire plein).
- Pendant le calcul : indicateur de progression déterminé (« 3 200 / 5 000
  tirages »), bouton « Arrêter » ; l'interface reste utilisable.
- Résultats :
  - **Date de fin** : P50, P85, P95 en clair, et un **histogramme** des
    dates de fin par semaine (barres primaires, valeur écrite sur chaque
    barre ; P50, P85 et P95 marqués par un trait et leur libellé) ;
  - **Combien d'ici…** : sélecteur de date, réponse en percentiles ;
  - **Échéances** : liste des tâches à échéance, triées par probabilité
    croissante, avec « 62 % · en danger » (contour + icône sous le seuil) ;
  - **Criticité** : les 10 tâches les plus souvent en retard ;
  - **Goulot** : membres et part des tirages où chacun finit le dernier ;
  - **Issue la plus probable** : distribution du nombre de retards et
    ensemble le plus fréquent.
- **Honnêteté** (règle des statistiques) : chaque résultat affiche le nombre
  de tirages, la graine, la source des données (« historique : 38 tâches
  faites sur 12 semaines » ou « hypothèse par défaut ») et est marqué
  « peu fiable » si l'historique est sous le minimum (§ Données). Aucune
  date n'est jamais affichée seule, sans sa fourchette.
- La date de calcul est affichée ; un résultat devient « périmé » (texte et
  icône) dès que les données d'équipe changent, sans être recalculé
  automatiquement.

#### Modèle « Par effort » (par défaut)

Chaque tirage rejoue le **plan de charge** (`equipe-charge.md`) — mêmes
membres, mêmes ordres, mêmes dépendances, mêmes absences — avec l'effort de
chaque tâche multiplié par un **facteur d'erreur d'estimation** tiré dans
l'historique de l'équipe (rapport réel / estimé des tâches faites). Il
répond aux six questions et tient compte de qui fait quoi.

- Option « Inclure le backlog non assigné » : à chaque tirage, les tâches
  du backlog sont réparties par la suggestion de répartition
  (`equipe-charge.md` § Suggestion) avant le plan.
- Option **« Aléa de capacité »** (activée par défaut) : chaque semaine de
  chaque membre, la capacité est multipliée par un facteur tiré dans
  l'historique du rapport « heures réelles faites / capacité prévue » ; à
  défaut d'historique, sans aléa.

#### Modèle « Par débit » (sans estimation)

Pour qui n'estime pas : chaque tirage avance semaine par semaine en tirant
le **nombre de tâches finies** dans les semaines passées de l'équipe (ou du
membre), jusqu'à épuiser le nombre de tâches du périmètre. Il répond à
« Quand ? » et « Combien ? » seulement ; les questions 3 à 5 sont masquées
avec l'explication « le modèle par débit ne connaît pas les tâches une à
une ».

#### Scénarios « Et si… ? »

- « Nouveau scénario » : un nom et une liste de **modifications**,
  appliquées à une copie des données, **jamais aux données réelles** :
  - ajouter un **membre hypothétique** (nom, quotité, heures par jour) ;
  - **retirer** un membre (ses tâches retournent au backlog du scénario) ;
  - ajouter une **absence** ; changer une **quotité** ;
  - **réaffecter** une ou plusieurs tâches ;
  - ajouter **N tâches hypothétiques** (points, catégorie, priorité) ;
  - changer une **priorité** ou une **échéance** ;
  - changer le **taux de focus**.
- Les scénarios sont enregistrés (liste, renommer, dupliquer, supprimer).
- **Comparer** : la situation réelle et jusqu'à trois scénarios côte à
  côte : P50 / P85 de la date de fin, nombre attendu d'échéances
  manquées, probabilité de tout tenir, taux de charge ; le meilleur de
  chaque ligne est marqué par une icône (pas par la couleur seule).
- **Appliquer** un scénario : seulement ses modifications **réelles**
  (réaffectations, absences, quotités, priorités, échéances, focus) ; les
  membres et tâches hypothétiques ne sont jamais créés par l'application
  (l'écran liste ce qui ne sera pas appliqué). Confirmation, puis chaque
  changement passe par le dépôt et est journalisé avec la source
  `scenario`.
- Une modification qui ne s'applique plus (tâche supprimée, membre archivé
  depuis) est ignorée et signalée dans le scénario.

### Critères de succès

- **Reproductible** : mêmes données, même graine, même nombre de tirages →
  résultats identiques au bit près, sur JVM, Android et wasm
  (`MonteCarloTest.sameSeedSameResult`).
- **Cas dégénérés exacts** : historique où toutes les estimations sont
  justes (facteur 1) et sans aléa de capacité → P50 = P85 = P95 = date du
  plan de charge déterministe ; débit constant de 5 tâches par semaine,
  20 tâches → exactement 4 semaines (`MonteCarloTest`).
- **Monotonie** (mêmes nombres aléatoires, § Tirages communs ; périmètre
  sans dépendance entre tâches de membres différents, voir § Décisions) :
  ajouter une absence ne rapproche jamais une date de fin ; ajouter un
  membre hypothétique sans réaffectation ni backlog inclus ne change rien
  (il n'a pas de tâche) ; doubler tous les efforts ne rapproche jamais une
  date (`ScenarioPropertyTest`).
- **Convergence** : sur un cas à loi connue (facteurs uniformes), les
  percentiles estimés avec 20 000 tirages sont à moins de 1 % des
  percentiles exacts (`PercentilesTest`).
- **Performance** : 200 tâches, 10 membres, 5 000 tirages : moins de 2 s
  sur le bureau (JVM) et l'interface ne gèle jamais plus de 100 ms sur le
  web (calcul par tranches, § Exécution).
- Appliquer un scénario ne crée ni membre ni tâche hypothétique, et chaque
  changement réel est journalisé (`ScenarioApplyTest`).

### Hors périmètre / différé

- Apprentissage automatique, régression, modèles bayésiens : l'échantillon
  d'une équipe est petit ; le rééchantillonnage de l'historique réel est
  plus honnête et plus explicable.
- Optimisation automatique du meilleur scénario (recherche) : le manager
  compose et compare ; Kairos calcule.
- Corrélations fines (un membre qui sous-estime plus qu'un autre) :
  différé ; possible par un facteur d'erreur par membre quand l'historique
  le permettra.
- Export des résultats (image, CSV).

## 2. Solution technique

### Données (`ForecastData.kt`, pur)

- `ForecastData.build(snapshot, jour, maintenant, fuseau, calibration?)` →
  `ForecastData(windowWeeks, minSamples, errorFactors, teamThroughput,
  memberThroughput, capacityFactors)` (listes triées).
- **Fenêtre** : les `historyWeeks` semaines **complètes** (lundi-dimanche)
  avant la semaine courante, zéros compris ; facteurs d'erreur sur les
  tâches faites entre le début de cette fenêtre et aujourd'hui.
- **Facteurs d'erreur** : tâches d'équipe faites ayant un effort de base
  (`Effort.base` : `ESTIMATE`, `CALIBRATED` ou `POINTS_RATE`) et un temps
  passé > 0 ; `réel / base` borné à [0,2 ; 5]. Temps passé = sessions +
  temps manuel (le temps des rapports s'y ajoutera au jalon E6).
- `errorFactor(rng)` : dans l'historique si n ≥ `minSamples` ; sinon dans
  l'historique avec la probabilité n / minimum, dans la **loi
  triangulaire** (0,8 ; 1 ; 2) sinon (`triangular(u)`).
- **Débits** : tâches d'équipe finies par semaine, de l'équipe ou d'un
  membre (`throughputOf`) ; disponible (`throughputAvailable`) avec au
  moins 4 semaines actives et au moins min(`minSamples`, `historyWeeks`).
- **Facteurs de capacité** (`capacityFactor(membre, rng)`) : semaines
  passées où la capacité prévue **et** les heures chronométrées sont > 0 ;
  heures réelles / capacité, bornées [0,3 ; 1,5] ; sans historique, 1.
- `estimationSource`, `throughputSource(membre?)` : `SourceInfo(kind
  HISTORY / MIXED / DEFAULT, samples, windowWeeks, minimum, activeWeeks)`
  et `reliable`, pour l'affichage honnête.

### Moteur (`MonteCarlo.kt`, `EffortModel.kt`, `ThroughputModel.kt`, purs)

- **Hasard** (`ForecastRandom`) : `kotlin.random.Random` seulement ;
  graine de tirage `drawBase(seed, draw)` ; sous-générateurs
  `stream(base, domaine, clé, indice)` par mélange SplitMix64 écrit à la
  main ; clés stables : FNV-1a 64 du `teamUid` (domaine erreur), id du
  membre et numéro **absolu** de semaine (domaine capacité), domaine
  débit. Jamais `String.hashCode()` ni l'ordre de traitement ; aucune
  fonction transcendante (seulement `+ − × ÷` et `sqrt`), pour un résultat
  identique au bit près sur toutes les cibles.
- `ForecastRequest(snapshot, maintenant, fuseau, data, seed, scope, model,
  options, runs, calibration?)` ; `ForecastScope` : `Assigned`,
  `AssignedAndBacklog`, `Category(nom)`, `Member(id)`, `Tasks(ids)` ;
  `ForecastModel` : `EFFORT`, `THROUGHPUT` ; `ForecastOptions(includeBacklog,
  capacityRandomness = true)`.
- `MonteCarlo.prepare(request)` puis `runBatch(from, count)` et
  `accumulator()` (ou `run(batchSize = 250)`) ; `MonteCarlo.isAvailable(
  model, data, scope)`. L'accumulateur range chaque tranche à son numéro de
  tirage et ne garde que des comptes entiers : une tranche, vingt tranches
  ou l'ordre inverse donnent le même résultat.
- **Modèle par effort** : `LoadPlan.prepare` une fois (ordres, capacités de
  base, graphe en tableaux primitifs), puis `Prepared.run(efforts,
  capacity)` par tirage — le **même** posage que `LoadPlan.build`
  (= `prepare().run()`), sans second ordonnanceur. Effort = `Effort.planned`
  × facteur tiré ; capacité × facteur de la semaine si l'aléa est actif.
- **Backlog** : pris en compte avec l'option, pour le périmètre
  `AssignedAndBacklog`, ou pour les ids d'un périmètre `Tasks` ; réparti
  **une fois** par `AssignmentSuggestion` à `prepare`, pas à chaque tirage
  (5 000 suggestions seraient hors de prix, et la suggestion ne voit pas
  les facteurs tirés). Le périmètre `Assigned` avec l'option fait prendre
  de la capacité au backlog sans le mesurer. Tâches à qualifier exclues et
  comptées (`toQualify`) ; une tâche sans porteur ou qui attend le backlog
  est « hors horizon ».
- **Modèle par débit** : cumul réel des débits tirés, semaine courante au
  prorata des jours ouvrés restants ; une tâche compte à la fin de sa
  semaine (dernier jour ouvré) ; garde-fou 520 semaines.
- **Agrégats** (`ForecastResult`) : `finish` (`FinishOutlook` : P50, P85,
  P95, histogramme par semaine, hors horizon ; date nulle = hors horizon),
  `deadlines` (`DeadlineOutlook` : tirages en retard, `criticality`,
  `onTimeProbability`, `atRisk` sous `deadlineRiskPercent`), `bottleneck`
  (`MemberShare` ; les ex æquo du dernier jour comptent chacun, la somme
  peut dépasser le nombre de tirages), `lateDistribution` (0, 1, 2, 3 et
  plus), `lateSets` (50 au plus, triés par fréquence, puis plus petit
  ensemble, puis ids) et `mostLikelyLateSet`, `expectedLate`,
  `allOnTimeProbability`, `finishedBy(date)` (« au moins X tâches » : fins
  par tâche gardées en `ForecastSamples`, plafonnées à 16 M valeurs, au-delà
  `null`), `mostCritical(10)`, `reliable`, graine, tirages demandés et faits,
  `interrupted`, source. Modèle par débit : les agrégats par tâche sont
  vides (et `expectedLate`, `allOnTimeProbability` neutres, à ne pas
  afficher).
- **Percentiles** (`Percentiles`) : `rank(n, p)` en arithmétique entière
  (évite 0,85 × 20 = 16,999…), `nearestRank`, `atLeast` (la ⌈p × n⌉-ième
  plus grande valeur).
- **Performance** : 200 tâches × 10 membres × 5 000 tirages, aléa de
  capacité, préparation comprise : 0,37 à 0,48 s sur JVM
  (`MonteCarloPerformanceTest`, exécuté par défaut). Avec 150 tâches au
  backlog, `prepare` coûte 0,6 s (la suggestion) : toujours hors du fil
  principal.

### Exécution (`ui/team/forecast/`)

- `ForecastController` : graine tirée par `TeamUiState.seed`
  (`Random.nextLong()` par défaut, imposée par les tests), requête et
  `MonteCarlo.prepare` dans `withContext(Dispatchers.Default)`, puis
  tranches de `TeamUiState.batchSize` (250) tirages, chacune sur
  `Dispatchers.Default`, séparées par `yield()` (sur le web, un seul fil :
  l'interface reste réactive). Progression « 3 200 / 5 000 tirages » à
  chaque tranche.
- « Arrêter » lève un drapeau lu **entre deux tranches** (pas d'annulation
  brutale de la coroutine) : le résultat partiel est marqué « interrompu,
  n tirages ». Arrêt pendant la préparation : l'ancien résultat est gardé.
  Arrêt pendant une comparaison : rien n'est affiché (des colonnes
  inégales seraient incomparables).
- `TeamUiState.batchGate` (sans effet par défaut) est appelé après chaque
  tranche : le test d'arrêt y tient le calcul en suspens pour cliquer à coup
  sûr, quelle que soit la vitesse de la machine (piège tracé : « Stop » est
  visible dès la préparation, où cliquer ne produit pas de résultat).
- Résultat et comparaison gardés en mémoire pour la session
  (`TeamUiState`, dans `AppServices`, jamais en base) avec une
  **empreinte** (`ForecastFingerprint` : jour, membres, absences, tâches
  d'équipe, leurs dépendances et sessions, journal, réglages ; ni le thème,
  ni le dernier espace, ni les tâches Perso ni les notes) ; empreinte
  différente → « périmé », sans recalcul automatique.

### Scénarios (`Scenario.kt`, table `team_scenario`)

- `TeamScenario(id, name, modifications, createdAt, updatedAt, ignored)` ;
  `KairosSnapshot.teamScenarios` ; table `team_scenario (id, name,
  modifications, created_at, updated_at)` (`4.sqm`, schéma 4 → 5),
  modifications en JSON (`ScenarioCodec` : discriminant `type` ; un type
  inconnu ou illisible est ignoré et signalé dans `ignored`, position −1 si
  tout le texte est illisible ; il n'est pas réécrit à l'enregistrement).
- `ScenarioModification` (scellée) : `addMember` (`tempId` **négatif**,
  sinon invalide), `removeMember`, `addAbsence`, `setAvailability`,
  `reassign` (par `teamUid`), `addTasks` (500 au plus, `teamUid`
  déterministes `scenario-task-<i>-<k>` pour les tirages communs),
  `setPriority`, `setDeadline`, `setFocus`. Appliquées **dans l'ordre** :
  une référence à un membre hypothétique doit suivre son `addMember`.
- `Scenario.apply(snapshot, modifications)` : pur, sur une copie, ids
  négatifs pour l'hypothétique ; rend les `Skipped(index, modification,
  reason)` (`MEMBER_NOT_FOUND`, `MEMBER_ARCHIVED`, `TASK_NOT_FOUND`,
  `TASK_NOT_OPEN`, `INVALID_VALUE`).
- `Scenario.realChanges` : tout sauf `addMember`, `addTasks` et toute
  modification qui **désigne** une entité hypothétique (absence, quotité,
  réaffectation vers ou depuis un membre hypothétique, priorité ou échéance
  d'une tâche hypothétique).
- Dépôt : `createScenario`, `updateScenario`, `duplicateScenario(id, nom?)`
  (le libellé « copie » vient de l'interface), `deleteScenario`,
  `applyScenario(id): ApplyReport(found, applied, skipped, notApplicable,
  ignored)`. L'application se fait en **une** transaction, par les briques
  internes du dépôt (une opération publique par changement imbriquerait des
  verrous) ; `removeMember` vaut un archivage (tâches ouvertes au backlog) ;
  chaque changement de tâche est journalisé avec la source `scenario` ; une
  modification déjà en place est rendue `ALREADY_APPLIED`, sans événement.
  Rien d'hypothétique n'est jamais créé. `clearTeamData` supprime les
  scénarios ; export 2 : `teamScenarios`.

### Interface

- `ForecastScreen` (`ForecastScreen.kt`, `ForecastPanels.kt`,
  `ForecastFields.kt`, `ForecastFormat.kt`) : colonne de 960 dp au plus ;
  périmètre (`ChoiceField`, menu MD3) — « Sélection du Backlog (n) »
  seulement si une sélection existe : elle vit dans
  `TeamUiState.backlogSelection`, source de vérité du Backlog, et survit
  donc au changement de destination ; modèle (bouton segmenté ; « Par
  débit » désactivé avec les chiffres — semaines actives requises et
  trouvées — et repli sur « Par effort » si l'historique devient
  insuffisant) ; options dans une `OutlinedCard` repliable ; bouton
  primaire plein « Lancer la simulation ».
- Panneaux (`Panel`) : **Date de fin** (P50, P85, P95 en phrase, jamais une
  date seule ; `Histogram` : une barre `Box` primaire par semaine, créneau
  de 40 à 72 dp, défilement dans la carte au-delà, traits P50/P85/P95 en
  `outline` sur des lignes de libellé distinctes, description complète
  pour le lecteur d'écran) ; **Combien d'ici…** (`DatePicker`, date par
  défaut = P50, réponse à 95 %, 85 % et 50 %) ; **Échéances** (les 8 plus
  fragiles, puis « voir les autres » ; « en danger » en contour + icône) ;
  **Criticité** (10 plus souvent en retard) ; **Goulot** ; **Issue la plus
  probable** (distribution 0 / 1 / 2 / 3+, ensemble le plus fréquent).
  Modèle par débit : ces quatre derniers masqués avec la phrase « le modèle
  par débit ne connaît pas les tâches une à une ». Sous chaque résultat :
  tirages, graine, source des données, « peu fiable » (`Notice` : contour +
  icône) et date du calcul.
- **Arrondis des probabilités** : « tenir » arrondi vers le bas, « en
  retard » vers le haut, jamais « 100 % » s'il existe un tirage en retard.
- Scénarios (`ScenarioSection.kt`) : liste (renommer, dupliquer —
  libellé « copie » fourni ici —, supprimer avec confirmation), cases pour
  comparer (trois au plus) ; `ScenarioEditor` / `ScenarioEditorContent`
  (plein écran sous 600 dp, dialogue au-delà) : modifications en phrase
  (`ScenarioText.kt`), ajout par un menu des neuf types, chacun édité dans
  `ModificationDialog` ; ids négatifs des membres hypothétiques attribués
  par l'éditeur ; chaque modification qui ne s'applique pas (`Scenario.apply`
  sur les données courantes) porte « Ignorée : raison » en contour + icône.
- `ScenarioComparison` : réel + scénarios cochés, **toujours au modèle par
  effort**, avec le périmètre et les options choisis et la **même graine**
  pour toutes les colonnes ; lignes P50, P85, retards attendus, probabilité
  de tout tenir (ces deux masquées si aucune échéance), taux de charge
  (`TeamLoad` sur le snapshot du scénario) ; le meilleur de chaque ligne
  marqué d'une étoile (`Star`), aucune si toutes les colonnes sont égales ;
  colonnes de données défilant horizontalement dans la carte, libellés de
  ligne fixes ; une note rappelle que les tâches ajoutées vont au backlog
  (« Assigné + backlog » ou l'option backlog pour les compter).
- `ScenarioApplyDialog` : « Sera appliqué » (réel) et « Ne sera pas
  appliqué : hypothétique », puis message « Appliqué : n · ignoré : n · non
  applicable : n ».
- Icônes : `Casino`, `Science`, `CompareArrows`, `Refresh`, `Star`
  (+ `StarFilled`), `ContentCopy`.
- Tests : `ForecastRandomTest`, `PercentilesTest`, `ForecastDataTest`,
  `MonteCarloTest`, `ScenarioTest`, `ScenarioPropertyTest`,
  `ReproducibilityTest` (`core`, `commonTest`), `MonteCarloPerformanceTest`
  (`core`, `jvmTest`), `ScenarioRepositoryTest` (`data`),
  `ForecastFormatTest` (`ui`), `ForecastUiTest` (`desktopApp`). Auto-test :
  `desktop-team-forecast[-narrow]`, `desktop-team-scenario-editor[-narrow]`,
  `desktop-team-comparison[-narrow]` (historique et scénarios semés par
  `TeamSeed`).

### Réglages ajoutés (`TeamSettings`)

`teamSimulationRuns` (5 000, 500-50 000), `teamHistoryWeeks` (12, 1-104),
`teamMinSamples` (8, ≥ 3), `teamDeadlineRiskPercent` (70, 1-99).

### Décisions et alternatives écartées

- **Rééchantillonnage de l'historique (bootstrap) plutôt qu'une loi
  ajustée** (log-normale…) : aucune hypothèse de forme, explicable (« on
  rejoue vos semaines passées »), robuste sur petit échantillon ; la loi
  triangulaire ne sert qu'à démarrer, et s'efface à mesure que l'historique
  grossit.
- **Deux modèles** : le débit ne demande aucune estimation et est souvent
  meilleur pour « quand ? » sur un flux homogène ; l'effort seul sait
  répondre tâche par tâche (échéances, criticité, goulot). Le manager
  choisit, l'écran dit ce que chacun sait faire.
- **Rejouer le plan de charge** plutôt qu'un modèle à part : une seule
  logique de posage (ordres, dépendances, absences) pour le déterministe et
  le probabiliste ; le cas sans aléa redonne exactement le plan (test).
- **Tirages communs** entre scénarios : sans eux, deux scénarios presque
  identiques pourraient afficher des écarts dus au seul hasard, et le
  manager conclurait à tort.
- **Graine tirée par l'interface et affichée** : `core` reste pur, et un
  résultat peut être refait à l'identique pour être discuté.
- **Piège tracé : anomalies de l'ordonnancement de liste** (Graham). Le
  plan de charge laisse un membre passer à sa tâche suivante quand la
  première est bloquée par un collègue ; avec des dépendances entre
  membres, raccourcir une tâche peut alors retarder une autre fin. Ce n'est
  pas un bogue du moteur : les propriétés de monotonie ne sont promises
  (et testées) que sans dépendance entre membres, et l'écran de
  comparaison n'affirme jamais qu'un scénario « ne peut pas » être pire.
- **Rang le plus proche** pour les percentiles : pas de date interpolée
  entre deux jours.
- **Pas de recalcul automatique** : une simulation coûte ; le résultat
  périmé est signalé, le manager relance.
- **Appliquer sans créer l'hypothétique** : un membre ou des tâches
  imaginaires ne doivent pas se retrouver en base par un clic ; les créer
  est un geste explicite ailleurs.

### Impacts sur les specs existantes

Reportés au jalon E5 : `reglages.md` (réglages de simulation),
`modele-donnees.md` (`team_scenario`, `4.sqm`), `export-import.md`
(`teamScenarios`), `architecture.md` (`core/team/forecast/`),
`navigation-theme.md` (icônes).
