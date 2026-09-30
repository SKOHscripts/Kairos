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

État : **spécifiée le 2026-09-30, non implémentée** (jalon E5).

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

- **Facteurs d'erreur d'estimation** : pour chaque tâche d'équipe faite
  ayant un effort de base (`Effort.base`, source `ESTIMATE`, `CALIBRATED`
  ou `POINTS_RATE`) et un temps passé > 0 : `réel / base`. Temps passé =
  sessions + temps manuel + temps des rapports. Fenêtre :
  `teamHistoryWeeks` semaines (12 par défaut, 1-104).
- **Minimum fiable** : `teamMinSamples` facteurs (8 par défaut, ≥ 3). En
  dessous, **loi par défaut** : triangulaire (min 0,8, mode 1,0, max 2,0),
  documentée à l'écran comme « hypothèse : les tâches prennent de 0,8 à
  2 fois leur estimation, le plus souvent 1 fois » ; les facteurs réels
  disponibles sont mêlés à la loi par défaut (tirage dans l'historique avec
  la probabilité n / minimum, dans la loi sinon), pour passer en douceur de
  l'une à l'autre.
- Facteurs bornés à [0,2 ; 5] pour qu'une saisie aberrante (une tâche
  chronométrée une nuit) ne domine pas.
- **Débits hebdomadaires** : pour chaque semaine (lundi-dimanche) de la
  fenêtre, nombre de tâches d'équipe finies (de l'équipe ou du membre),
  semaines à zéro comprises ; minimum : 4 semaines ayant au moins une tâche
  finie, sinon le modèle par débit est indisponible (bouton désactivé,
  explication).
- **Facteurs de capacité** : par membre et par semaine passée, heures
  réelles faites / capacité prévue (`Capacity`), bornés à [0,3 ; 1,5] ;
  même minimum que les facteurs d'estimation, sinon facteur 1.

### Moteur (`MonteCarlo.kt`, pur)

- Entrées : `TeamSnapshot` (ou celui d'un scénario), périmètre, modèle,
  options, jour, réglages, données, **graine** (`Long`) et nombre de
  tirages `teamSimulationRuns` (5 000 par défaut, 500-50 000).
- Hasard : `kotlin.random.Random(seed)` seulement (même suite sur toutes les
  cibles) ; `core` ne tire jamais de graine lui-même : l'interface la tire
  et la passe (pureté de `core`, `architecture.md`).
- **Tirages communs** : le tirage `i` utilise un générateur dérivé de
  `(graine, i)`, et, dans un tirage, chaque tâche et chaque semaine-membre
  un sous-générateur dérivé de son identité stable (`teamUid`, identifiant
  de membre et numéro de semaine), pas de l'ordre de traitement. La
  situation réelle et chaque scénario voient donc **les mêmes aléas** pour
  les mêmes tâches : les écarts entre scénarios viennent des modifications,
  pas du bruit, et les propriétés de monotonie sont testables tirage par
  tirage.
- Modèle par effort, un tirage : pour chaque tâche du périmètre,
  `effort = Effort.remaining × facteur tiré` ; facteurs de capacité par
  semaine-membre si l'option est active ; `LoadPlan` rejoué avec ces
  efforts et capacités ; on relève la fin de chaque tâche, de chaque
  membre et du périmètre, les retards, le membre le plus tardif.
- Modèle par débit, un tirage : semaines successives à partir de la semaine
  courante (au prorata des jours ouvrés restants pour la semaine
  entamée) ; débit tiré parmi les semaines de la fenêtre ; fin = semaine où
  le cumul atteint le nombre de tâches ; au-delà de 520 semaines, « hors
  horizon » (débits tous nuls).
- Agrégats (`ForecastResult`) : dates de fin triées (percentiles), histogramme
  par semaine, probabilité de tenir chaque échéance, indice de criticité,
  fréquence « dernier à finir » par membre, distribution du nombre de
  retards, ensembles de retards (clé = liste triée des identifiants en
  retard) et leur fréquence, dont le plus fréquent (ex æquo : le plus
  petit ensemble, puis ordre des identifiants).
- **Percentiles** : méthode du rang le plus proche (`ceil(p × n)`-ième
  valeur triée), sans interpolation (une date n'a pas de moitié).

### Exécution (`ui/team/forecast/`)

- Le calcul tourne dans une coroutine de l'écran, par **tranches de 250
  tirages** entre lesquelles il cède la main (`yield()`), sur
  `Dispatchers.Default` (JVM, Android) ; sur le web (wasm, un seul fil),
  les tranches gardent l'interface réactive. Progression mise à jour à
  chaque tranche ; « Arrêter » annule la coroutine et affiche les
  résultats partiels marqués « interrompu, n tirages ».
- Le moteur expose `runBatch(from, count)` et un agrégateur incrémental,
  pour que le découpage n'altère pas le résultat (même résultat en une
  tranche ou en vingt : testé).
- Résultat gardé en mémoire (pas en base) avec son empreinte des données
  d'entrée ; empreinte différente → « périmé ».

### Scénarios (`Scenario.kt`, table `team_scenario`)

- `TeamScenario` : `id`, `name`, `modifications` (JSON), `createdAt`,
  `updatedAt`.
- `ScenarioModification` (scellée, sérialisée avec un discriminant `type`) :
  `AddMember(tempId, name, availabilityPercent, hoursPerDay)`,
  `RemoveMember(memberId)`, `AddAbsence(memberId, start, end)`,
  `SetAvailability(memberId, percent)`, `Reassign(taskUid, memberId?)`,
  `AddTasks(count, points, category, priority)`,
  `SetPriority(taskUid, priority)`, `SetDeadline(taskUid, date?)`,
  `SetFocus(factor)`. Référence aux tâches par `teamUid` (stable).
  Modification de type inconnu à la lecture : ignorée et signalée.
- `Scenario.apply(team, modifications): Pair<TeamSnapshot, List<Skipped>>` :
  pur, sur une copie ; identifiants négatifs pour les membres et tâches
  hypothétiques (jamais en conflit avec la base).
- `Scenario.realChanges(modifications)` : ce qu'« Appliquer » exécute
  (tout sauf `AddMember` et `AddTasks`, et sauf les `Reassign` vers un
  membre hypothétique), traduit en opérations du dépôt dans **une**
  transaction, journalisées `source = scenario`.

### Interface

- `ForecastScreen` : colonne défilante de 960 dp au plus ; en-tête
  (périmètre, modèle, et options dans une `OutlinedCard` repliable),
  puis panneaux `OutlinedCard` (même `Panel` que les statistiques).
- `Histogram` : barres `Box` (pas de bibliothèque de graphiques, décision
  de `statistiques.md`), primaire à bouts arrondis 4 dp, valeur écrite ;
  percentiles en traits `outline` avec libellé ; description textuelle
  complète pour le lecteur d'écran (« 50 % avant le 9 octobre… »).
- `ScenarioEditor` : liste de modifications en `ListItem`, ajout par menu,
  chaque modification éditée dans un petit dialogue ; `ScenarioComparison` :
  tableau à colonnes (réel + 3), défilant horizontalement dans sa carte
  sous 600 dp (jamais la page).

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

### Impacts sur les specs existantes (à reporter à l'implémentation)

- `reglages.md` : réglages de simulation dans la carte Équipe.
- `statistiques.md` : `Panel` réutilisé.
- `modele-donnees.md` : table `team_scenario`.
- `architecture.md` : paquet `core/team/forecast/`.
