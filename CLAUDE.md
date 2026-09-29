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
   re-trancher plus tard (exemple : le choix de ne PAS utiliser `opacity` sur
   `.mj-blocked` — voir `docs/spec/`). Ces micro-décisions vivent souvent en
   commentaire de code (le commentaire porte le « pourquoi » local) ; la spec en
   est le registre consolidé au niveau conception.
4. **Documenter dans le README si — et seulement si — c'est une feature.** Seules
   les fonctionnalités pertinentes pour l'utilisateur entrent dans `README.md`.
   Un correctif de bug, un refactor ou une décision technique interne n'y vont
   **pas** (exemple : le bug d'opacité de la modale d'une tâche bloquée est tracé
   en spec, absent du README, car ce n'est pas une feature).

## Où vivent les specs

- `docs/spec-v3/` : même principe pour Kairos 3 (`kmp/`), voir la section
  « Kairos 3 » ci-dessous.
- `docs/spec/` : **une spec par domaine fonctionnel** (ordonnancement,
  dépendances, temps réel, vue Jour/GTD, packaging, intégrations externes,
  réglages, etc.).
- **Chaque spec est découpée en deux parties**, dans cet ordre :
  1. **Besoin métier** (cahier des charges) : le problème, le comportement
     attendu du point de vue de l'utilisateur, les critères de succès, le
     hors-périmètre. Le « quoi » et le « pourquoi », sans détail d'implémentation.
  2. **Solution technique** : l'implémentation retenue, les fichiers/fonctions
     concernés, les invariants, les décisions techniques et les alternatives
     écartées. Le « comment ».
- `docs/DESIGN_SYSTEM.md` (charte visuelle) et `docs/ANDROID_PACKAGING.md`
  restent des **références transverses**, citées par les specs de domaine plutôt
  que dupliquées.

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

# Kairos 3 : réécriture Kotlin Multiplatform (`kmp/`)

Kairos est en cours de réécriture en Kotlin Multiplatform + Compose
Multiplatform (Android, bureau Windows/Linux/macOS, web Wasm). Feuille de route
et décisions : `docs/plan-v3-kotlin.md`. Pendant la transition :

- **Deux codes, deux registres de spec** : le Python (`app/`, `templates/`,
  `static/`, `android/`, `packaging/`) est **gelé** (correctifs seulement) et
  décrit par `docs/spec/` ; le code `kmp/` est décrit par `docs/spec-v3/`, avec
  les mêmes règles (besoin métier puis solution technique, bijectivité). À la
  bascule `v3.0.0`, le Python disparaît et `docs/spec-v3/` remplace `docs/spec/`.
- **Jalons** (plan § 9) : chaque jalon se termine par une version
  (`kmp/gradle.properties`, source unique : `kairos.versionName` et
  `kairos.versionCode`, formule dans `docs/spec-v3/distribution.md`) **et un
  tag** `v3.0.0-alpha.N` qui déclenche `kmp-release.yml` (préversion GitHub).
  L'environnement de développement ne peut pas pousser de tag : le
  propriétaire du dépôt le pose sur le commit du jalon.
- **Architecture** (`docs/spec-v3/architecture.md`) : `core` reste **pur**
  (ni Compose, ni plateforme, ni horloge, ni I/O : l'heure et les réglages
  sont des paramètres) ; toute interface vit dans `ui` ; dépendances
  **libres** uniquement (Maven Central, Google), versions figées dans
  `kmp/gradle/libs.versions.toml` : l'APK doit rester acceptable par F-Droid,
  sans permission réseau.
- **Parité** : le comportement de référence est celui de Kairos 2 (specs
  `docs/spec/`, tests `tests/`), moins le périmètre retiré (TimeTree, import
  GitLab, base pilotage, service systemd). Les moteurs portés sont validés
  par des tests différentiels contre le Python (plan § 7).
- **Charte en Compose** : les règles de la section suivante valent pour
  Kairos 3, transposées :
  - couleurs : `MaterialTheme.colorScheme` et `LocalKairosExtraColors`
    uniquement, jamais de `Color(0x…)` dans un composant. Le schéma est
    **généré** par `kmp/tools/make_theme.py` depuis la graine ;
  - icônes : `KairosIcons`, **généré** par `kmp/tools/make_icons.py` ;
    ajouter l'icône dans le script, jamais à la main ;
  - Roboto embarquée dans `composeResources/font/`, jamais de police distante ;
  - navigation : rail si la fenêtre fait 600 dp ou plus, barre basse
    **seulement** sur Android en largeur compacte, barre horizontale en haut
    sinon (`NavigationLayout`, `docs/spec-v3/navigation-theme.md`).
- **Textes** : aucun texte d'interface en dur. Français (`values/`) **et**
  anglais (`values-en/`) à chaque ajout, apostrophe typographique `’`
  (`StringsParityTest`, `docs/spec-v3/i18n.md`).
- **Vérifier avant de pousser** : `cd kmp && ./gradlew :core:jvmTest :data:jvmTest
  :ui:jvmTest :desktopApp:jvmTest`, et pour l'interface `./gradlew :desktopApp:run
  --args=--self-test=/tmp/captures` (rendu hors écran, à regarder). La version
  web se teste dans Chromium à partir de la sortie compilée
  (`docs/spec-v3/architecture.md` § Décisions).

Les sections suivantes (charte, navigation, mobile) décrivent la charte
commune et, dans leurs détails HTML/CSS, le code Kairos 2.

---

# Design system Kairos — Material Design 3, thème « miel »

Cette charte (2026-09) remplace la précédente (« sobre & professionnelle »,
ardoise + bleu, IBM Plex). Toute nouvelle vue ou composant Kairos doit s'y
conformer et suivre **Material Design 3** (https://m3.material.io) : chercher le
composant MD3 correspondant avant d'en inventer un. Source des jetons et détail
complet : `docs/DESIGN_SYSTEM.md` (mêmes noms de classes/variables que
`static/style.css`).

## Palette
- Rôles MD3 générés (schéma *Tonal spot*) depuis **une seule graine, le miel
  `#C28417`**, la couleur du logo : primaire `#7F5610`, conteneur primaire
  `#FFDDB3`, conteneur secondaire `#FADEBC`, tertiaire (sauge) `#D4EABC`, erreur
  `#BA1A1A` / `#FFDAD6`, surface `#FFF8F4`, texte `#201B13` / `#4F4539`,
  contours `#817567` / `#D3C4B4`, surface inverse `#362F27`. Variables `--md-*`
  uniquement : jamais de couleur en dur dans un composant. Pour changer de
  couleur, regénérer TOUT le schéma depuis une nouvelle graine
  (material-color-utilities), jamais retoucher un rôle isolé.
- **Où va la couleur** : un seul bloc teinté par écran (« Maintenant »,
  `primary-container`) ; primaire plein pour l'action principale d'une zone ;
  score WSJF en chiffre primaire sans pastille ; rouge seulement pour P0, les
  erreurs et les dépassements ; tertiaire seulement pour le deep work ; vert
  « ok » (`--kx-ok*`, couleur personnalisée) pour le fait ; sélection en
  `secondary-container` ; sombre (surface inverse) seulement pour la carte
  « En ce moment » et la snackbar d'alerte. Tout le reste est neutre.
- **Pas d'ambre** : indiscernable du miel. Les états « à surveiller » passent
  par la forme (contour + icône : `.badge.warn`, `.stat.tone-amber`), les
  bannières de dégradation (TimeTree, GitLab, surcharge) sont neutres ;
  `.banner.warning` (conteneur d'erreur) est réservé aux vrais échecs.
- Un seul thème clair pour l'instant (le schéma sombre se générerait depuis la
  même graine).

## Typographie & icônes
- **Roboto** 400/500/700, **servie par l'app** (`static/fonts/`, `@font-face`) :
  jamais de police distante (exécutable et APK hors ligne ; test
  `test_fonts_are_served_locally_never_from_google`). Échelle typographique MD3
  (title-large 22px pour le titre de page, title-medium 16px pour les titres de
  carte, body-medium 14px pour le corps, labels 11-12px). Aucun italique
  décoratif, aucune seconde police.
- **Material Symbols** (Outlined 400) en SVG inline via `icon()`
  (`templates/_icons.html`, GÉNÉRÉ par `packaging/make_icons.py` : ajouter une
  icône dans le script, jamais à la main). Variante pleine (`fill=true`) pour la
  destination active.
- Densité d'information : corps 14px, proche de l'existant — ne pas l'augmenter
  ni la réduire lors de futurs ajouts.

## Forme & effets
- Formes MD3 : champs 4px, chips/pastilles 8px, cartes et lignes 12px, grands
  blocs 16px, dialogue 28px, boutons/badges/indicateurs en pilule.
- Cartes « outlined » (surface + contour) ou « filled » (conteneur de surface),
  **jamais d'ombre sur une carte** : seuls les éléments flottants en portent une
  (dialogue d'édition `.mj-edit-body`, snackbar, menu « Pourquoi »).
- Couches d'état MD3 (survol 8 %, appui 12 %) sur tout élément interactif.
- Pas de dégradé, sauf le fondu du bandeau sticky des Réglages
  (`.mj-settings-actions`) et les hachures deep work de la timeline.
- Liseré critique d'une ligne de tâche : `border-left: 3px` rouge
  (`.mj-bucket-0`), jamais de remplissage.

## Identité
- Logo (mire/cadran solaire) : dessin inchangé, **couleurs tirées de la graine**
  (cadran `#FFEEDC`, anneau `#FFCC85`, secteur `#C28417`, axe `#2B251C`). Toute
  évolution passe aussi par `static/favicon.svg`, `packaging/make_icon.py`
  (PNG, ICO, splash) et les ressources Android (voir `docs/DESIGN_SYSTEM.md` §
  Logo).

## Navigation & mobile
- **Bureau / navigateur, fenêtre > 720px : rail de navigation MD3** vertical à
  gauche (`.topnav` en colonne, destinations `.tn-item` avec indicateur
  `.tn-ind`) + barre d'application supérieure `.topbar`. Décision rouverte et
  tranchée en 2026-09 (l'ancienne règle « pas de sidebar » est abandonnée).
- **Fenêtre ≤ 720px** : la même navigation devient une barre horizontale
  compacte en haut — **jamais de barre de navigation basse** dans un navigateur :
  un navigateur simplement rétréci ne doit jamais en afficher une.
- **Exception** : l'APK Android affiche une barre de navigation MD3 basse
  (`.bn-nav`) à la place de `.tn-nav` — seule dérogation au principe « aucune
  détection de plateforme côté serveur » qui prévaut partout ailleurs dans
  l'app. Gabarit gardé par `is_android` (`app/main.py`, lu depuis
  `KAIROS_PLATFORM=android`, posé par `kairos_boot.py` avant tout import de
  `app.main` — jamais par une media query seule, justement pour ne jamais se
  déclencher sur un navigateur desktop rétréci). Voir
  `docs/spec/accueil-navigation.md`.
- Cibles tactiles ≥ 44px sur mobile pour les contrôles à un tap ; vérifier
  qu'aucun composant (grille semaine, dialogue d'édition) ne déborde
  horizontalement sur ~375px de large.
- L'app tourne aussi en exécutable de bureau (PyInstaller, Windows/Linux) :
  rester en HTML/CSS pur, sans dépendance de build ni bibliothèque de
  composants JavaScript (les composants MD3 sont rendus par le CSS).

## Références
- Charte complète, rôles, composants : `docs/DESIGN_SYSTEM.md`.
- Feuille de style : `static/style.css`.
