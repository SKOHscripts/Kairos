# Carte du code

_Rôle : donner l'organisation du code de Kairos (modules, paquets, fichiers, dépendances entre paquets, specs qui
les décrivent) sous deux formes tirées d'une seule source : un index compact pour Claude Code et une page publique qui
se met à jour seule. Fichiers couverts : `kmp/tools/make_code_map.py`, `kmp/tools/test_make_code_map.py`,
`.github/workflows/pages.yml` (étape « Carte du code »), `.github/workflows/metadata.yml` (déclencheurs), `CLAUDE.md`
(§ Carte du code), `README.md` et `README.fr.md` (lien de la section des documents). Tests :
`test_make_code_map.py` (`python3 -m unittest discover -s kmp/tools -p 'test_*.py'`)._

État : demande du propriétaire du 2026-11-15, spécifiée le 2026-11-15, non implémentée.

## 1. Besoin métier (cahier des charges)

### Objectif / problème

- **Pour Claude Code** : comprendre où vit une fonctionnalité oblige aujourd'hui à parcourir des dizaines de fichiers
  (`grep`, lectures), ce qui coûte du temps et des tokens. Il faut un résumé court et toujours juste : pour chaque paquet,
  ses fichiers, ce qu'ils déclarent, de quels paquets il dépend, et quelle spec de `docs/spec/` le décrit.
- **Pour les contributeurs et les curieux** : voir l'organisation du dépôt d'un coup d'œil, sans cloner ni installer
  d'outil, sur une page publique toujours à jour.

### Comportement attendu (utilisateur)

1. **Page publique** `https://skohscripts.github.io/Kairos/code/`, régénérée à chaque publication du site depuis `main` :
   - un **graphe** des paquets, rangés par module (`core`, `data`, `ui`, `androidApp`, `desktopApp`, `webApp`), avec
     les dépendances entre paquets (un paquet qui en importe un autre) ; toucher un paquet met en valeur ses dépendances
     et ses dépendants, et estompe le reste ;
   - sous le graphe, la **liste des paquets**, chacun dépliable : ses fichiers (lien vers le fichier sur GitHub, nombre
     de lignes, déclarations de premier niveau), les specs qui en parlent (liens), les paquets dont il dépend ;
   - une **recherche** qui filtre paquets, fichiers et déclarations ;
   - la date et le commit de génération ; le texte de la page en anglais (public large), les titres de specs tels quels.
   - Page autonome : ni police distante, ni bibliothèque, ni ressource d'un tiers (règle du site, `publication.md`) ;
     thème clair et sombre suivant le système ; lisible à 360 px (le graphe défile horizontalement, la liste non).
2. **Index pour Claude Code** : `python3 kmp/tools/make_code_map.py --index [filtre]` écrit en Markdown, sur la sortie
   standard, un paquet par section : fichiers, lignes, déclarations, dépendances, specs. Le filtre (sous-chaîne du nom de
   paquet ou du chemin, par exemple `guide` ou `core/stats`) limite la sortie à ce qui concerne la tâche. `CLAUDE.md` dit
   de commencer par là avant de parcourir le code.
3. **Lien avec les specs** : un fichier est relié à une spec quand la spec le cite (`Tips.kt`, `ui/guide/TipDialog.kt`,
   chemin complet ou nom seul) ; un paquet est relié aux specs de ses fichiers. Un fichier qu'aucune spec ne cite est
   signalé (« sans spec ») : c'est une piste pour la règle de bijectivité.

### Critères de succès

- Le même code produit la même page et le même index, octet pour octet, hors date et commit
  (`test_make_code_map.CodeMapTest.test_output_is_deterministic`).
- Paquets, fichiers, déclarations publiques et internes (pas les privées), dépendances et liens vers les specs sont
  justes sur un petit arbre de test (`test_reads_packages_declarations_and_imports`, `test_links_files_to_specs`,
  `test_package_edges_skip_self_and_external`).
- Le filtre de l'index ne garde que les paquets visés (`test_index_filter`).
- La page ne charge aucune ressource externe (`test_page_is_self_contained`) et se publie avec le site.

### Hors périmètre / différé

- Classes, fonctions et appels entre fonctions (graphe d'appels) : trop lourd et fragile en Kotlin multiplateforme
  sans compilateur ; les paquets et fichiers suffisent (choix du propriétaire du 2026-11-15).
- Tests, ressources, scripts et code généré hors `src/*/kotlin` (sauf `KairosIcons.kt`, versionné, qui est du code).
- Fichier d'index versionné dans le dépôt (il vieillirait entre deux commits) ; carte par branche ou par PR.
- Plugin ou skill tiers (code-map, code-graph…) : produits par Claude à chaque fois, non déterministes, coûteux.

## 2. Solution technique

### Générateur (`kmp/tools/make_code_map.py`)

Python 3, bibliothèque standard seulement, comme les autres outils de `kmp/tools/`.

- **Lecture** : chaque `kmp/<module>/src/<sourceSet>/kotlin/**/*.kt` des source sets de production (ceux dont le nom ne
  finit pas par `Test`) ; le paquet vient de la ligne `package`, les dépendances des lignes `import
  com.skohscripts.kairos.…` (ramenées au paquet connu le plus long qui les préfixe), les déclarations des lignes de
  premier niveau (colonne 0) `class`, `interface`, `object`, `fun`, `val`, `typealias`, avec leurs modificateurs, sauf
  `private`. Pas d'analyse syntaxique : des expressions régulières sur des lignes, suffisantes pour un code formaté de
  façon constante.
- **Paquet** : son nom court (sans `com.skohscripts.kairos.`), son module, ses source sets, ses fichiers.
- **Specs** : chaque `docs/spec/*.md` ; les jetons entre accents graves qui finissent par `.kt` (nom seul ou chemin
  partiel) relient la spec aux fichiers dont le chemin se termine par ce jeton ; le titre de la spec (première ligne
  `# …`) est gardé pour la page.
- **Sorties** : `--index [filtre]` (Markdown, sortie standard), `--html FICHIER` (page), `--json FICHIER` (données, pour
  un usage futur). `--root` désigne le dépôt (par défaut, deux dossiers au-dessus du script). Date et commit viennent de
  `--stamp` (la CI passe `$GITHUB_SHA`), sinon rien : la sortie reste déterministe.

### Page (`--html`)

- Un seul fichier HTML : CSS et JavaScript en ligne, données en JSON dans un `<script type="application/json">`,
  polices Roboto servies par le site (`../fonts/`, déjà copiées par `pages.yml`) avec repli sur la police du système.
- **Graphe en SVG**, calculé par le script (pas de mise en page au navigateur) : une colonne par module dans l'ordre des
  dépendances (`core`, `data`, `ui`, puis les trois applications), une boîte par paquet triée par nom, des courbes de
  Bézier pour les dépendances ; le JavaScript ne fait que mettre en valeur (classes CSS) au toucher et filtrer.
- Couleurs : neutres, et le primaire de la charte pour la mise en valeur (`#485D92` clair, `#B1C5FF` sombre, rôles de
  `docs/DESIGN_SYSTEM.md`) ; aucune autre couleur.
- Liens vers GitHub : `https://github.com/SKOHscripts/Kairos/blob/main/<chemin>`.

### Publication (`pages.yml`)

- Étape « Carte du code » après l'assemblage : `python3 kmp/tools/make_code_map.py --html "$out/code/index.html"
  --stamp "$GITHUB_SHA"`.
- Déclencheurs : `docs/spec/**` et `kmp/tools/make_code_map.py` s'ajoutent aux chemins (le code `kmp/**` y est déjà) ;
  `!kmp/tools/**` reste, le script étant ré-inclus après lui.
- `metadata.yml` lance déjà `unittest` sur `kmp/tools/test_*.py` à chaque PR qui touche `kmp/tools/*.py`.

### Décisions et pièges tracés

- **Générateur maison plutôt qu'un plugin** (choix du propriétaire du 2026-11-15) : une page qui se met à jour seule doit
  être produite par la CI, sans Claude ; un même script donne aussi l'index de Claude, donc une seule source.
- **Paquets et fichiers, pas de graphe d'appels** (choix du propriétaire) : lisible, stable d'un commit à l'autre.
- **Expressions régulières, pas de compilateur** : aucune dépendance, quelques millisecondes ; la convention de
  formatage du dépôt (déclarations de premier niveau en colonne 0) suffit. Prix : une déclaration écrite autrement
  (annotation sur la même ligne, par exemple) peut manquer à la liste ; la dépendance reste juste, elle vient des imports.
- **Index produit à la demande, pas versionné** : toujours juste, rien à régénérer avant un commit.
- **Graphe calculé en Python** : la page reste statique, déterministe et légère ; pas de bibliothèque de graphes
  (interdite par la règle « aucune ressource tierce » du site).
- **Page en anglais** : public des contributeurs ; les specs restent en français (leurs titres sont cités tels quels).
