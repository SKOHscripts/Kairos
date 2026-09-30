# Apparence : couleur de Kairos

_Rôle : laisser chacun choisir la couleur dont tout le thème est dérivé.
Fichiers couverts : `kmp/core/.../model/Settings.kt` (`themeColor`),
`kmp/core/.../settings/SettingsForm.kt` (champ `themeColor`, validation),
`kmp/ui/.../theme/` (`ThemeColors.kt` : graines proposées et schéma dérivé ;
`KairosTheme.kt`), `kmp/ui/.../settings/AppearanceCard.kt`,
`kmp/ui/.../KairosApp.kt` (thème appliqué une fois les réglages lus,
paramètre `systemColorScheme`) et `kmp/androidApp/.../MainActivity.kt`
(couleurs du système sur Android 12 et plus). Tests :
`core/.../settings/SettingsFormTest.kt`, `ui/.../theme/ThemeColorsTest.kt`,
`desktopApp/.../M5ScreensUiTest.kt`._

État : issue #45 (après la 3.0.0).

## 1. Besoin métier (cahier des charges)

### Objectif / problème

Le thème de Kairos est dérivé d'une seule couleur, le miel du logo
(`docs/DESIGN_SYSTEM.md`). Certains préfèrent une autre couleur, ou celle de
leur téléphone. Changer de couleur ne doit pas casser la charte : contrastes,
rôles et règles d'usage de la couleur restent ceux de Material Design 3.

### Comportement attendu (utilisateur)

- Réglages → carte **Apparence**, avant « Mises à jour » et « Données » :
  - une rangée de **pastilles** : le miel (par défaut) et sept autres
    couleurs nommées ; la pastille choisie porte une coche ;
  - sur **Android 12 et plus**, une pastille « Couleurs du système » en tête :
    le thème suit alors les couleurs du fond d'écran (Material You) ;
  - un champ **« Couleur personnalisée »** (`#RRGGBB`, par exemple `#2F6FED`) :
    une couleur saisie remplace la pastille choisie ; choisir une pastille
    remplit le champ avec sa couleur.
- Comme tout réglage, le choix ne s'applique qu'à **« Enregistrer »**
  (`reglages.md`) ; tout l'écran change alors de couleur.
- Une couleur illisible est refusée avec son erreur sous le champ
  (« Couleur illisible : « bleu » (format #RRGGBB). »), et rien n'est
  enregistré.
- L'aide de la carte rappelle que tout le thème est dérivé de la couleur
  choisie, et que le rouge reste réservé aux urgences et aux erreurs.
- Le choix voyage avec les autres réglages (export, import). « Couleurs du
  système » importé sur un appareil qui n'en a pas (bureau, web, Android
  avant 12) donne le miel.

### Critères de succès

- Pour la graine miel, le schéma calculé est **exactement** celui de la
  charte (les 36 rôles de `KairosColors.kt`, générés par
  `kmp/tools/make_theme.py`).
- Pour toute graine, les rôles viennent du même algorithme (Tonal spot,
  spec 2021) : les contrastes texte/fond restent ceux de MD3.
- Choisir une pastille puis « Enregistrer » change la couleur primaire de
  l'interface ; une couleur illisible bloque l'enregistrement.

### Hors périmètre

- Thème sombre (la charte n'a qu'un thème clair).
- Logo, icônes d'application, page de téléchargement et fiches des magasins :
  ils gardent le miel, couleur de l'identité de Kairos.
- Couleur « fait » (vert « ok ») et rouge d'erreur : fixes, quelle que soit la
  graine.

## 2. Solution technique

### Réglage (`core`)

- `Settings.themeColor: String`, par défaut `"#C28417"`
  (`Settings.DEFAULT_THEME_COLOR`) ; la valeur `"system"`
  (`Settings.SYSTEM_THEME`) demande les couleurs du système.
- `SettingsForm` : champ `themeColor`, nature `FieldKind.COLOR`. Accepte
  `system` ou six chiffres hexadécimaux, avec ou sans `#`, en majuscules ou
  minuscules ; enregistre la forme normalisée `#RRGGBB` (majuscules) ou
  `system`. Sinon erreur `FieldErrorKind.INVALID_COLOR` (le texte saisi en
  borne). `SettingsForm.parseColor` rend la couleur `0xRRGGBB` ou `null`.

### Schéma (`ui/theme`)

- Algorithme : `com.materialkolor:material-color-utilities` 5.0.1 (MIT,
  portage Kotlin multiplateforme de material-color-utilities de Google, sans
  dépendance Compose) ; `SchemeTonalSpot(Hct.fromInt(argb), isDark = false,
  contrastLevel = 0.0, SpecVersion.SPEC_2021)`, dont les 36 rôles remplissent
  un `lightColorScheme`. C'est le calcul que `make_theme.py` fait en Python
  (`materialyoucolor`) pour produire `KairosColors.kt`.
- `ThemeColors` : les graines proposées (`PRESETS`, nom localisé + couleur) :
  Miel `#C28417`, Océan `#2F6FED`, Sarcelle `#1B8A8A`, Indigo `#5B5FC7`,
  Lavande `#8A5CC2`, Framboise `#B83E7A`, Ardoise `#56687A`, Terre
  `#9A5B3F` ; `schemeFor(themeColor, system)` : les couleurs du système si
  `system` et que la plateforme en fournit, `KairosLightColors` pour le miel
  (valeur exacte générée), le schéma calculé sinon.
- `KairosTheme(scheme)` : applique le schéma ; typographie, formes et couleur
  « ok » inchangées.
- `KairosApp(platform, systemColorScheme = null, …)` : thème par défaut pour
  l'écran de chargement, puis le thème des réglages dès qu'ils sont lus
  (recalculé quand `themeColor` change). `LocalSystemColorScheme` indique à
  la carte Apparence si « Couleurs du système » est disponible.
- Android : `MainActivity` passe `dynamicLightColorScheme(context)` sur
  Android 12 et plus (API 31), `null` avant ; recalculé par Compose quand le
  fond d'écran change.

### Carte Apparence (`ui/settings/AppearanceCard.kt`)

- `OutlinedCard` comme les autres sections : titre, aide, pastilles (`FlowRow`),
  champ `OutlinedTextField` du réglage `themeColor`.
- Pastille : disque de 36 dp de la couleur de la graine dans une cible de
  48 dp ; la choisie porte un contour `onSurface` et une coche `Check`
  blanche ou noire selon la luminance de la pastille ; `Role.RadioButton`,
  état « sélectionné » et nom de la couleur pour les lecteurs d'écran.
  « Couleurs du système » : pastille dessinée avec le `primary` du système.
  Une couleur libre valide, hors graines proposées, a sa propre pastille
  (cochée, nommée par sa valeur `#RRGGBB`).
- Le champ affiche `#RRGGBB` (vide pour « Couleurs du système ») ; l'erreur
  du champ remplace son aide.

### Décisions et pièges tracés

- **Une graine, jamais un rôle isolé** (règle de la charte) : l'utilisateur
  choisit une graine, tout le schéma en est recalculé par l'algorithme MD3 ;
  aucun rôle n'est réglable seul.
- **Miel servi depuis `KairosColors.kt`** plutôt que recalculé : la charte
  reste la source des valeurs par défaut ; `ThemeColorsTest` vérifie que
  l'algorithme embarqué redonne exactement ces 36 valeurs, garantie que toute
  autre graine suit le même calcul.
- **Pastilles de couleur en `Color(argb)`** : la seule couleur de composant
  hors schéma, parce que la pastille montre la donnée elle-même (la graine),
  pas un rôle ; sa coche est blanche ou noire pour rester lisible sur
  n'importe quelle graine.
- **Pas de rouge ni de vert parmi les graines proposées** : le rouge est
  réservé aux urgences et aux erreurs, le vert « ok » au fait. Une couleur
  libre reste permise ; l'aide le rappelle.
- **Couleurs du système en option, pas par défaut** : le miel reste
  l'identité de Kairos sur toutes les plateformes ; Material You n'existe
  que sur Android 12 et plus.
