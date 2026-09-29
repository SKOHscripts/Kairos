# Workflow de développement du dépôt Kairos

Ce dépôt suit un cycle **spécification-d'abord**. Toute contribution respecte les
quatre étapes ci-dessous, dans cet ordre.

## Les quatre étapes

1. **Spécifier d'abord.** Avant d'implémenter, rédiger une spécification — ou un
   « plan » au sens Claude Code. Pour un petit changement, ce peut être un ajout
   à une spec existante ; pour un chantier, une nouvelle spec de domaine.
2. **Implémenter.**
3. **Tracer dans la spec.** Une fois le code écrit, consigner dans `docs/spec/`.
   La spec doit rester **exhaustive et bijective** avec le code : tout
   comportement du code y est décrit, et rien dans la spec ne décrit du code
   inexistant. Y consigner aussi les **petites prises de décision** qui ne
   méritent pas un document dédié mais qu'il faut tracer pour ne pas les
   re-trancher plus tard. Ces micro-décisions vivent souvent en commentaire de
   code (le commentaire porte le « pourquoi » local) ; la spec en est le
   registre consolidé au niveau conception.
4. **Documenter dans le README si — et seulement si — c'est une feature.** Seules
   les fonctionnalités pertinentes pour l'utilisateur entrent dans `README.md`.
   Un correctif de bug, un refactor ou une décision technique interne n'y vont
   **pas** (ils sont tracés en spec).

## Où vivent les specs

- `docs/spec/` : **une spec par domaine fonctionnel** (ordonnancement,
  dépendances, temps réel, vue Jour, distribution, publication, réglages,
  etc.), index dans `docs/spec/README.md`.
- **Chaque spec est découpée en deux parties**, dans cet ordre :
  1. **Besoin métier** (cahier des charges) : le problème, le comportement
     attendu du point de vue de l'utilisateur, les critères de succès, le
     hors-périmètre. Le « quoi » et le « pourquoi », sans détail d'implémentation.
  2. **Solution technique** : l'implémentation retenue, les fichiers/fonctions
     concernés, les invariants, les décisions techniques et les alternatives
     écartées. Le « comment ».
- `docs/DESIGN_SYSTEM.md` (charte visuelle) reste une **référence transverse**,
  citée par les specs de domaine plutôt que dupliquée.
- `docs/plan-v3-kotlin.md` garde l'historique des décisions de la réécriture.

## Règles de traçabilité

- **Bijectivité** : à tout comportement du code correspond une trace de spec, et
  toute affirmation de spec correspond à du code réel. Un écart constaté se
  corrige (spec ou code) dans le même changement.
- **Ne jamais re-trancher** une décision déjà consignée sans la rouvrir
  explicitement : chercher d'abord dans `docs/spec/` puis dans les commentaires
  de code avant de reprendre une conception.
- Les commentaires de code portent le « pourquoi » local non évident (invariant,
  piège, décision produit) ; la spec de domaine en est le registre consolidé.

---

# Kairos : Kotlin Multiplatform (`kmp/`)

Kairos est écrit en Kotlin Multiplatform + Compose Multiplatform : Android,
bureau Windows/Linux/macOS (JVM), web (Kotlin/Wasm).

- **Architecture** (`docs/spec/architecture.md`) : `core` reste **pur** (ni
  Compose, ni plateforme, ni horloge, ni I/O : l'heure et les réglages sont des
  paramètres) ; `data` porte la base (SQLDelight) et le dépôt ; toute interface
  vit dans `ui` ; `androidApp`, `desktopApp` et `webApp` ne fournissent que la
  plateforme. Dépendances **libres** uniquement (Maven Central, Google),
  versions figées dans `kmp/gradle/libs.versions.toml` : l'APK doit rester
  acceptable par F-Droid, **sans permission réseau**, et reproductible
  (`docs/spec/publication.md`).
- **Versions** : `kmp/gradle.properties` est la source unique
  (`kairos.versionName`, `kairos.versionCode`, formule dans
  `docs/spec/distribution.md`). Chaque version publiée a sa note de version
  Fastlane en français et en anglais
  (`fastlane/metadata/android/*/changelogs/<versionCode>.txt`). Un tag
  `vX.Y.Z` (ou `vX.Y.Z-beta.N`) déclenche `kmp-release.yml`. L'environnement de
  développement ne peut pas pousser de tag : le propriétaire du dépôt le pose
  sur le commit de la version.
- **Textes** : aucun texte d'interface en dur. Français (`values/`) **et**
  anglais (`values-en/`) à chaque ajout, apostrophe typographique `’`
  (`StringsParityTest`, `docs/spec/i18n.md`).
- **Vérifier avant de pousser** : `cd kmp && ./gradlew :core:jvmTest :data:jvmTest
  :ui:jvmTest :desktopApp:jvmTest`, et pour l'interface `./gradlew :desktopApp:run
  --args=--self-test=/tmp/captures` (rendu hors écran, à regarder). La version
  web se teste dans Chromium à partir de la sortie compilée
  (`docs/spec/architecture.md` § Décisions). Une modification visible sur
  téléphone se regarde aussi sur les captures des magasins
  (`--store-screenshots`, `docs/spec/publication.md`), à régénérer si l'écran
  capturé change.

---

# Design system Kairos — Material Design 3, thème « miel »

Toute nouvelle vue ou composant Kairos doit s'y conformer et suivre **Material
Design 3** (https://m3.material.io) : chercher le composant Material 3 de
Compose correspondant avant d'en inventer un. Détail complet :
`docs/DESIGN_SYSTEM.md` ; mise en œuvre Compose :
`docs/spec/navigation-theme.md`.

## Palette
- Rôles MD3 générés (schéma *Tonal spot*) depuis **une seule graine, le miel
  `#C28417`**, la couleur du logo : primaire `#7F5610`, conteneur primaire
  `#FFDDB3`, conteneur secondaire `#FADEBC`, tertiaire (sauge) `#D4EABC`, erreur
  `#BA1A1A` / `#FFDAD6`, surface `#FFF8F4`, texte `#201B13` / `#4F4539`,
  contours `#817567` / `#D3C4B4`, surface inverse `#362F27`.
- Couleurs par `MaterialTheme.colorScheme` et `LocalKairosExtraColors`
  uniquement, **jamais de `Color(0x…)` dans un composant**. Le schéma est
  **généré** par `kmp/tools/make_theme.py` depuis la graine : pour changer de
  couleur, regénérer tout le schéma, jamais retoucher un rôle isolé.
- **Où va la couleur** : un seul bloc teinté par écran (« Maintenant »,
  `primaryContainer`) ; primaire plein pour l'action principale d'une zone ;
  score WSJF en chiffre primaire sans pastille ; rouge seulement pour P0, les
  erreurs et les dépassements ; tertiaire seulement pour le deep work ; vert
  « ok » (couleur personnalisée) pour le fait ; sélection en
  `secondaryContainer` ; sombre (surface inverse) seulement pour la carte
  « En ce moment » et la snackbar d'alerte. Tout le reste est neutre.
- **Pas d'ambre** : indiscernable du miel. Les états « à surveiller » passent
  par la forme (contour + icône) ; le conteneur d'erreur est réservé aux vrais
  échecs.
- Un seul thème clair pour l'instant (le schéma sombre se générerait depuis la
  même graine).

## Typographie & icônes
- **Roboto** 400/500/700, embarquée dans `composeResources/font/` : jamais de
  police distante (application hors ligne ; la page `site/` sert aussi ses
  polices). Échelle typographique MD3 ; aucun italique décoratif, aucune
  seconde police.
- **Material Symbols** (Outlined 400) via `KairosIcons`, **généré** par
  `kmp/tools/make_icons.py` : ajouter l'icône dans le script, jamais à la
  main. Variante pleine pour la destination active.
- Densité d'information : corps 14 sp ; ne pas l'augmenter ni la réduire lors
  de futurs ajouts.

## Forme & effets
- Formes MD3 : champs 4 dp, chips/pastilles 8 dp, cartes et lignes 12 dp,
  grands blocs 16 dp, dialogue 28 dp, boutons/badges/indicateurs en pilule.
- Cartes « outlined » ou « filled », **jamais d'ombre sur une carte** : seuls
  les éléments flottants en portent une (dialogue, snackbar, menu).
- Couches d'état MD3 sur tout élément interactif (celles de Material 3).
- Pas de dégradé, sauf les hachures deep work de la frise.
- Liseré critique d'une ligne de tâche P0 : bord gauche rouge, jamais de
  remplissage.

## Identité
- Logo (mire/cadran solaire) : couleurs tirées de la graine (cadran `#FFEEDC`,
  anneau `#FFCC85`, secteur `#C28417`, axe `#2B251C`). Toute évolution passe
  par `kmp/tools/make_app_icons.py` (icônes bureau, web, fiches Fastlane,
  page) et les ressources Android (`docs/DESIGN_SYSTEM.md` § Logo).

## Navigation & mobile
- **Rail de navigation** MD3 si la fenêtre fait 600 dp ou plus ; en dessous,
  **barre basse seulement sur Android**, barre horizontale en haut sinon
  (`NavigationLayout`, `docs/spec/navigation-theme.md`) : un navigateur ou une
  fenêtre de bureau rétrécis n'affichent jamais de barre basse.
- Cibles tactiles ≥ 48 dp pour les contrôles à un toucher
  (`docs/spec/accessibilite.md`) ; vérifier qu'aucun écran ne déborde
  horizontalement sur un téléphone (~360 dp de large).
- Pas de raccourci clavier annoncé sur Android (`LocalPlatform`).

## Références
- Charte complète, rôles, composants : `docs/DESIGN_SYSTEM.md`.
- Thème Compose : `kmp/ui/.../theme/`.
