---
name: kairos-monte-carlo
description: Méthode pour écrire, tester et afficher les simulations probabilistes de Kairos (prévisions Monte Carlo de l'espace Équipe - modèles par effort et par débit, percentiles, probabilité de tenir une échéance, criticité, issue la plus probable, scénarios « Et si… ? ») en Kotlin pur, reproductible sur JVM, Android et wasm. À utiliser pour tout code de core/team/forecast, ses tests statistiques, ses performances sur le web, ou tout autre calcul aléatoire ajouté à Kairos.
---

# Simulations Monte Carlo dans Kairos

Spec : `docs/spec/equipe-simulation.md` (et le plan de charge rejoué :
`docs/spec/equipe-charge.md` § Plan de charge). Contexte du chantier :
skill `kairos-equipe`.

## Principes non négociables

1. **Pureté de `core`** : le moteur ne tire jamais sa graine, ne lit pas
   l'horloge. L'interface tire une graine (`Random.nextLong()` côté `ui`),
   la passe, et l'**affiche** avec le résultat.
2. **Reproductible au bit près** sur toutes les cibles :
   `kotlin.random.Random(seed)` uniquement (pas de `java.util.Random`, pas
   de `ThreadLocalRandom`, pas de `Math.random`). Pas d'itération sur un
   `HashMap`/`HashSet` pour produire un résultat (ordre non garanti) : trier
   ou utiliser `LinkedHashMap`. Pas de somme flottante dont l'ordre dépend
   d'une collection non ordonnée.
3. **Tirages communs** : le tirage `i` et, dans ce tirage, chaque tâche et
   chaque semaine-membre ont leur propre générateur **dérivé d'une identité
   stable** (graine, `i`, `teamUid`, identifiant de membre, semaine), jamais
   de l'ordre de traitement. Ainsi réel et scénarios voient les mêmes aléas.
   Dérivation : mélange 64 bits déterministe (par exemple SplitMix64 sur
   `seed xor hash stable`), le hachage stable de texte étant écrit à la main
   (FNV-1a), **pas** `String.hashCode()` si l'on veut le documenter
   indépendamment de la JVM.
4. **Un seul moteur de posage** : le modèle par effort rejoue
   `LoadPlan` avec des efforts et capacités tirés. Ne jamais écrire un
   second ordonnanceur « pour la simulation ». Cas sans aléa = plan
   déterministe, exactement.
5. **Honnêteté** : toute sortie porte le nombre de tirages, la graine, la
   source des données (historique n tâches / hypothèse par défaut) et
   « peu fiable » sous le minimum. Jamais une date seule : P50 · P85 (· P95).

## Structure attendue (`core/team/forecast/`)

- `ForecastData` : facteurs d'erreur (réel / base, bornés [0,2 ; 5]),
  débits hebdomadaires (zéros compris), facteurs de capacité (bornés
  [0,3 ; 1,5]), mélange historique / loi triangulaire (0,8 ; 1 ; 2) sous le
  minimum `teamMinSamples`.
- `MonteCarlo` : `runBatch(from, count)` + agrégateur **incrémental** ;
  le résultat ne dépend pas du découpage en tranches.
- `Percentiles` : rang le plus proche, `ceil(p × n)`-ième valeur triée,
  indice borné à [1, n] ; jamais d'interpolation de dates.
- `Scenario` : modifications scellées, `apply` pur sur une copie,
  identifiants négatifs pour l'hypothétique, `realChanges` pour
  « Appliquer ».

## Tests à écrire (dans `core/src/jvmTest/.../team/forecast/`)

Graine fixe partout ; aucun test ne doit dépendre de la chance.

- **Reproductibilité** : même entrée + même graine → résultats égaux
  (`==` sur les structures) ; une tranche vs vingt tranches → égaux.
- **Cas dégénérés exacts** : facteurs tous à 1 et sans aléa → percentiles =
  plan déterministe ; débit constant 5/semaine, 20 tâches → 4 semaines.
- **Convergence** contre une loi connue : facteurs uniformes, une seule
  tâche, 20 000 tirages → P50, P85 à moins de 1 % des valeurs analytiques.
  Choisir une tolérance **justifiée** (écart-type du quantile empirique)
  et l'écrire en commentaire du test.
- **Propriétés avec tirages communs** (sans dépendance entre membres, voir
  le piège de Graham dans la spec) : ajouter une absence ne rapproche
  jamais une fin ; doubler les efforts ne rapproche jamais une fin ; un
  membre hypothétique sans tâche ne change rien.
- **Bornes et données pauvres** : historique vide → loi par défaut et
  « peu fiable » ; débits tous nuls → « hors horizon », pas de boucle
  infinie (garde de 520 semaines / 2 ans).
- **Multiplateforme** : les tests de `core` tournent sur JVM ; ajouter un
  test `commonTest` de reproductibilité avec des valeurs attendues écrites
  en dur, pour détecter une divergence wasm/Android.

## Performance et exécution

- Budget : 200 tâches × 10 membres × 5 000 tirages < 2 s sur JVM. Mesurer
  (test de performance marqué, hors CI si trop lent) avant d'optimiser.
- Boucle chaude : tableaux primitifs (`DoubleArray`, `IntArray`) indexés
  par un index compact des tâches, pas de `List<Task>` recopiée par tirage,
  pas d'allocation d'objets par tâche et par tirage. Précalculer ordres,
  capacités journalières de base et graphe de dépendances **une fois**.
- Interface : coroutine de l'écran, tranches de 250 tirages séparées par
  `yield()` (indispensable sur wasm, un seul fil), progression par tranche,
  annulation propre (résultats partiels marqués « interrompu »).

## Affichage (voir aussi la skill `kairos-ecran`)

- Histogramme en `Box` (pas de bibliothèque de graphiques), barres
  primaires, valeur écrite sur chaque barre ; percentiles en traits
  `outline` avec libellé. Description textuelle complète pour le lecteur
  d'écran.
- Probabilités en pourcentage entier ; « en danger » = contour + icône,
  jamais une teinte ambre.
- Résultat « périmé » (texte + icône) dès que l'empreinte des données
  change ; pas de recalcul automatique.

## Tracer

Toute décision numérique (borne, loi, minimum, tolérance, taille de
tranche) va dans « Décisions et pièges tracés » de
`equipe-simulation.md`, avec sa justification.
