# Apparence : couleur et thème sombre de Kairos

_Rôle : laisser chacun choisir la couleur dont tout le thème est dérivé, et un thème clair ou sombre.
Fichiers couverts : `kmp/core/.../model/Settings.kt` (`themeColor`),
`kmp/core/.../settings/SettingsForm.kt` (champ `themeColor`, validation),
`kmp/ui/.../theme/` (`ThemeColors.kt` : graines proposées et schéma dérivé ;
`KairosTheme.kt`), `kmp/ui/.../settings/AppearanceCard.kt`,
`kmp/ui/.../KairosApp.kt` (thème appliqué une fois les réglages lus,
paramètres `systemColorScheme`, `systemDark`, `onDarkTheme`), `kmp/core/.../settings/ThemeMode.kt` (thème résolu,
interrupteur), `kmp/tools/make_theme.py` (schémas clair et sombre), `kmp/androidApp/.../MainActivity.kt`
(couleurs du système sur Android 12 et plus, barres du système), `kmp/androidApp/src/main/res/values-night*/`
et `kmp/webApp/.../index.html` (relais natifs en sombre). Tests :
`core/.../settings/SettingsFormTest.kt`, `ui/.../theme/ThemeColorsTest.kt`,
`desktopApp/.../M5ScreensUiTest.kt`, `core/.../settings/ThemeModeTest.kt`, `desktopApp/.../DarkThemeUiTest.kt`.
Couleur par défaut (océan) : `kmp/data/.../db/11.sqm` et `Kairos.sq` (table `migration_mark`), `KairosStore.kt` (`MARKERS`),
`kmp/tools/make_app_icons.py`, `kmp/tools/make_badges.py`, `kmp/androidApp/src/main/res/` (`values*/colors.xml`,
`drawable/ic_launcher_foreground.xml`), `site/style.css` ; test `data/.../ThemeColorMigrationTest.kt`._

État : couleur, issue #45 (après la 3.0.0) ; thème sombre, issue #72 (3.7.0) ; couleur par défaut passée du miel à l'océan
le 2026-10-27 (livré en 3.12.0, `docs/ROADMAP.md`).

## 1. Besoin métier (cahier des charges)

### Objectif / problème

Le thème de Kairos est dérivé d'une seule couleur, par défaut l'océan `#2F6FED` (`docs/DESIGN_SYSTEM.md` ; jusqu'au
2026-10-27, le miel `#C28417` du logo, voir § Couleur par défaut). Certains préfèrent une autre couleur, ou celle de
leur téléphone. Changer de couleur ne doit pas casser la charte : contrastes,
rôles et règles d'usage de la couleur restent ceux de Material Design 3.

### Comportement attendu (utilisateur)

- Réglages → catégorie « Général » → carte **Apparence**, après « Langue » et avant « Mises à jour » (`reglages.md`) :
  - une rangée de **pastilles** : l'océan (par défaut, en tête), le miel en deuxième position et six autres
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
  avant 12) donne la couleur par défaut (l'océan).

### Thème sombre (issue #72, 3.7.0)

- Par défaut, Kairos **suit le système** : sombre si le téléphone, l'ordinateur ou le navigateur est en sombre, clair
  sinon (une installation existante passe donc en sombre à la mise à jour si son système l'est).
- Carte **Apparence**, en tête : un interrupteur à **deux états** « Thème sombre », qui montre le thème réellement
  affiché. Le basculer vers l'inverse de ce que fait le système **choisit** ce thème ; le rebasculer vers ce que fait le
  système revient à **suivre le système** (logique de l'issue, d'après Lea Verou : pas de troisième état « Système » à
  l'écran). Une ligne dessous le dit : « Suit le système. » ou « Choisi ici : rebascule-le pour suivre à nouveau le
  système. ». Comme tout réglage, il ne s'applique qu'à « Enregistrer ».
- Un thème choisi **reste** choisi quand le système change ensuite ; seul l'interrupteur le rend au système.
- En sombre, **tout le schéma** est celui de la même graine (océan, miel ou autre, couleurs du système sur Android 12 et plus),
  calculé par le même algorithme : les règles d'usage de la couleur ne changent pas (un seul bloc teinté, rouge réservé,
  tertiaire pour le deep work, vert « ok » pour le fait). La surface inverse (carte « En ce moment », snackbar d'alerte)
  devient claire sur fond sombre, comme le veut Material 3.
- Le logo garde ses couleurs (cadran crème) ; les pastilles de la carte Apparence montrent toujours la graine.
- Avant que les réglages soient lus (écran de démarrage, chargement), et dans les relais natifs (fenêtre de démarrage
  Android, page de chargement web), le thème est celui du système.
- Sur Android, les icônes des barres du système (heure, batterie, navigation) suivent le thème affiché.
- Les **images exportées** des Stats (`statistiques.md` § Export) restent **claires**, sur la même graine : une image se
  colle dans un rapport ou s'imprime.
- Les fiches des magasins et la page gardent leurs captures claires et en ajoutent une **en sombre** (la vue Jour,
  capture 9, `publication.md`).

### Couleur par défaut : l'océan (changé le 2026-10-27)

- La couleur d'une installation neuve, et du thème tant qu'on n'a rien choisi, est l'**océan** `#2F6FED` (un bleu), à la
  place du miel `#C28417`. Le thème clair et le sombre en sont générés (primaire clair `#485D92`, conteneur primaire
  `#DAE2FF`, surface `#FAF8FF`, surface sombre `#121318`) ; la tertiaire, qui ne sert qu'au deep work, devient un mauve
  rosé (conteneur clair `#FED7F9`) au lieu de la sauge.
- Le **miel reste une couleur au choix** : pastille « Miel » en deuxième position, après « Océan ».
- Le **logo ne change pas** : il garde son miel (cadran `#FFEEDC`, anneau `#FFCC85`, secteur `#C28417`, axe `#2B251C`) et
  contraste avec l'interface. Les icônes d'installation le posent sur un fond uni océan profond `#1C2F59`.
- Qui avait laissé le thème sur la valeur par défaut passe à l'océan à la mise à jour, sans rien faire. Qui avait choisi une
  autre couleur, ou « Couleurs du système », la garde. Conséquence assumée : qui avait choisi le miel **exprès** avant la mise
  à jour passe aussi à l'océan (son choix était indiscernable de la valeur par défaut) et peut le rechoisir ; le miel choisi
  **après** la mise à jour est gardé.
- Un fichier d'export ancien importé garde sa valeur (`#C28417` = miel).

### Critères de succès

- Pour la graine par défaut (l'océan), le schéma calculé est **exactement** celui de la
  charte (les 36 rôles de `KairosColors.kt`, générés par
  `kmp/tools/make_theme.py`) ; le miel, lui, est calculé comme toute autre graine.
- Une installation dont le thème est resté sur l'ancienne valeur par défaut passe à l'océan à la mise à jour ; une couleur
  choisie ou « Couleurs du système » est gardée ; le miel choisi après la mise à jour est gardé (`ThemeColorMigrationTest`,
  § Couleur par défaut).
- Pour toute graine, les rôles viennent du même algorithme (Tonal spot,
  spec 2021) : les contrastes texte/fond restent ceux de MD3.
- Choisir une pastille puis « Enregistrer » change la couleur primaire de
  l'interface ; une couleur illisible bloque l'enregistrement.
- Pour la graine par défaut, le schéma sombre calculé est exactement celui généré par `make_theme.py`
  (`KairosDarkColors`) ; la couleur « ok » sombre a les tons de l'erreur sombre (`ThemeColorsTest`).
- Le thème résolu : « suivre le système » donne le thème du système ; « sombre » ou « clair » l'emportent sur lui ;
  basculer l'interrupteur vers le thème du système rend « suivre le système », jamais une valeur égale au système ; une
  valeur inconnue (import) revient à « suivre le système » (`ThemeModeTest`).
- Système clair, basculer « Thème sombre » puis « Enregistrer » assombrit l'interface (fond `surface` sombre) ; le
  rebasculer rend « suivre le système » ; système sombre, Kairos est sombre sans rien régler (`DarkThemeUiTest`).
- En sombre, l'image exportée d'une carte des Stats reste claire (`StatsExportUiTest`).

### Hors périmètre

- Un contraste élevé, un thème « noir pur » (OLED), un réglage du sombre par écran ou selon l'heure.
- Un aperçu en direct avant « Enregistrer » (règle de tous les réglages).
- Logo, icônes d'application, page de téléchargement, boutons de téléchargement et fiches des magasins : ils ne suivent
  jamais la couleur choisie. Le logo garde son miel (couleur de l'identité de Kairos), les autres l'océan par défaut
  (§ Icônes et supports fixes).
- Une carte « Nouveau dans cette version » pour le changement de couleur (choix du propriétaire du 2026-10-27).
- Couleur « fait » (vert « ok ») et rouge d'erreur : fixes, quelle que soit la
  graine.

## 2. Solution technique

### Réglage (`core`)

- `Settings.themeColor: String`, par défaut `"#2F6FED"`, l'océan
  (`Settings.DEFAULT_THEME_COLOR`) ; `Settings.HONEY_THEME_COLOR` = `"#C28417"`, le miel, ancienne valeur par défaut
  devenue une graine au choix ; la valeur `"system"`
  (`Settings.SYSTEM_THEME`) demande les couleurs du système.
- `Settings.themeMode: String`, par défaut `"system"` (`Settings.THEME_MODE_SYSTEM`), ou `"light"` / `"dark"`
  (`THEME_MODE_LIGHT`, `THEME_MODE_DARK`). `ThemeMode` (`core/settings`, pur) : `normalize(valeur)` (inconnu ou vide →
  `system`), `isDark(mode, systèmeSombre)` (le thème résolu), `toggled(voulu, systèmeSombre)` (la valeur que pose
  l'interrupteur : `system` si `voulu` égale le système, sinon `dark` ou `light`). Le réglage entre dans l'export
  (le fichier d'or `solo-export-v1.json` le montre) ; une version plus ancienne l'ignore à l'import
  (`ignoreUnknownKeys`).
- `SettingsForm` : champ `themeMode` (texte normalisé par `ThemeMode.normalize`, jamais refusé, comme `language`) ;
  champ `themeColor`, nature `FieldKind.COLOR`. Accepte
  `system` ou six chiffres hexadécimaux, avec ou sans `#`, en majuscules ou
  minuscules ; enregistre la forme normalisée `#RRGGBB` (majuscules) ou
  `system`. Sinon erreur `FieldErrorKind.INVALID_COLOR` (le texte saisi en
  borne). `SettingsForm.parseColor` rend la couleur `0xRRGGBB` ou `null`.

### Schéma (`ui/theme`)

- Algorithme : `com.materialkolor:material-color-utilities` 5.0.1 (MIT,
  portage Kotlin multiplateforme de material-color-utilities de Google, sans
  dépendance Compose) ; `SchemeTonalSpot(Hct.fromInt(argb), isDark = false,
  contrastLevel = 0.0, SpecVersion.SPEC_2021)`, dont les 36 rôles remplissent
  un `lightColorScheme`. C'est le calcul que le script `make_theme.py`
  (bibliothèque `materialyoucolor`) fait pour produire `KairosColors.kt`.
- `ThemeColors` : les graines proposées (`PRESETS`, nom localisé + couleur, dans cet ordre) :
  Océan `#2F6FED` (`DEFAULT_THEME_COLOR`), Miel `#C28417` (`HONEY_THEME_COLOR`), Sarcelle `#1B8A8A`, Indigo `#5B5FC7`,
  Lavande `#8A5CC2`, Framboise `#B83E7A`, Ardoise `#56687A`, Terre
  `#9A5B3F` ; `schemeFor(themeColor, system)` : les couleurs du système si
  `system` et que la plateforme en fournit, `KairosLightColors` pour la graine par défaut (l'océan, valeur exacte
  générée), le schéma calculé sinon (le miel compris) ; la graine par défaut pour une valeur illisible.
- Sombre : `make_theme.py` génère aussi `KairosDarkColors` (`SchemeTonalSpot(…, isDark = True, …)`) ;
  `schemeFor(themeColor, system, dark)` et `seedScheme(rgb, dark)` rendent un `darkColorScheme` aux mêmes 36 rôles.
- `KairosTheme(scheme, dark)` : applique le schéma ; typographie et formes inchangées ; couleur « ok » claire
  (`#36693D` / `#B7F1B8` / `#1D5127`, charte) ou sombre (`KairosExtraColors.dark()` : `#9CD49F`, ton 80 de la
  palette teinte 150°, chroma 36 ; `#1D5127` / `#B7F1B8`, conteneur et texte échangés, comme l'erreur sombre).
  `KairosDefaultTheme(sombre)` : l'océan clair ou sombre, avant la lecture des réglages. `LocalLightColorScheme` : le schéma **clair** de la graine
  courante, pour ce qui doit rester clair (images exportées des Stats).
- `KairosApp(platform, systemColorScheme = null, systemDark = null, onDarkTheme = {}, …)` : `systemColorScheme` devient
  une fonction `(sombre) -> ColorScheme` (Android : `dynamicDarkColorScheme` ou `dynamicLightColorScheme`) ;
  `systemDark` force le thème du système (tests, captures), sinon `isSystemInDarkTheme()` ; le thème résolu est
  `ThemeMode.isDark(settings.themeMode, systemDark)` dès que les réglages sont lus, celui du système avant ;
  `onDarkTheme(sombre)` est rappelé à chaque changement du thème résolu.
- `KairosApp(platform, systemColorScheme = null, …)` : thème par défaut pour
  l'écran de chargement, puis le thème des réglages dès qu'ils sont lus
  (recalculé quand `themeColor` change). `LocalSystemColorScheme` indique à
  la carte Apparence si « Couleurs du système » est disponible.
- Android : `MainActivity` passe `dynamicLightColorScheme(context)` / `dynamicDarkColorScheme(context)` sur
  Android 12 et plus (API 31), `null` avant ; recalculé par Compose quand le
  fond d'écran change. `onDarkTheme` rappelle `enableEdgeToEdge(SystemBarStyle.auto(…) { sombre })` : icônes des
  barres claires sur fond sombre. Fenêtre de démarrage : `values-night/` (fond `surface` sombre, barres sombres,
  parent `Theme.Material.NoActionBar`), le système la choisit d'après son propre mode.
- Web : la page de chargement (`index.html`) prend les couleurs sombres sous `prefers-color-scheme: dark`, et
  `theme-color` a sa variante sombre. Bureau : rien (décorations de fenêtre du système).

### Couleur par défaut : migration et supports fixes

- **Migration de données `11.sqm`** (schéma 11 → 12, `data`) : crée la table `migration_mark` (une colonne `name`, marqueur
  structurel du schéma 12, ligne `default_theme_ocean`, jamais lue ni exportée) puis
  `UPDATE settings SET json = replace(json, '"themeColor":"#C28417"', '"themeColor":"#2F6FED"') WHERE id = 1`. Le réglage est un
  JSON compact (`encodeDefaults`) : `replace` suffit, sans extension JSON. Une valeur autre (`#1B8A8A`, `system`, `#2F6FED`) ne
  correspond pas au motif : elle est gardée. Une base sans réglages enregistrés lit la nouvelle valeur par défaut.
- **Marqueur** : `KairosStore.MARKERS` gagne `12` (présence de `migration_mark`, `ExchangeMigrationTest.everyMigrationHasAMarker`).
  Sans table marqueur, une migration de données seule ne laisserait aucune trace du schéma 12 et serait rejouée à chaque
  ouverture, défaisant le miel choisi ensuite ; avec elle, elle ne rejoue pas.
- **Fichier d'or** `solo-export-v1.json` : `"themeColor": "#2F6FED"`. Un export ancien (`#C28417`) importé garde sa valeur,
  l'import ne migre rien.
- **Tests** : `ThemeColorMigrationTest` (un thème resté sur l'ancienne valeur passe à l'océan, le reste du réglage intact ; une
  couleur choisie ou `system` est gardée ; le miel choisi après la mise à jour survit à la réouverture ; une base sans réglages
  reçoit la nouvelle valeur par défaut), `ThemeColorsTest` (l'algorithme embarqué redonne les 36 valeurs de l'océan, clair et
  sombre ; `#2f6fed` en minuscules et une valeur illisible rendent `KairosLightColors` ; le miel est calculé), `M5ScreensUiTest`
  (le fond est la surface de l'océan, puis celle du miel une fois la pastille « Honey » enregistrée).

### Icônes et supports fixes

Le logo (`KairosLogo.kt`, couleurs fixes) est inchangé : son miel ne suit ni la graine par défaut ni la couleur choisie. Autour
de lui, tout ce qui ne dépend pas des réglages est à l'océan par défaut :

- **Icônes d'installation** (`kmp/tools/make_app_icons.py`) : le logo sur un fond uni `BACKGROUND` = `#1C2F59` (ton 20 de la
  palette primaire de la graine océan), le cadran à `LOGO_RATIO` = 0,64 du côté. Coins arrondis de `CORNER` = 22,5 % pour les
  icônes de bureau (`.png`, `.ico`, `.icns`), la page `site/` et les favicons (web, page) ; **carré plein** pour l'icône Android,
  les fiches Fastlane et les icônes web 192 et 512 (le système et les magasins les masquent).
- **Android** : `ic_launcher_background` = `#1C2F59` ; le cadran crème (`#FFEEDC`), qui était porté par le fond quand celui-ci
  était crème, est dessiné dans `ic_launcher_foreground.xml` (échelle 1,7, environ 63 dp pour 72 dp de zone sûre) ; `kairos_bg`
  (fenêtres de démarrage) = `#FAF8FF` en clair, `#121318` en sombre (`values-night/`), les `surface` de l'océan.
- **Web** : `index.html` (fond, `theme-color`, textes de la page de chargement) aux `surface`, `onSurface` et `onSurfaceVariant`
  de l'océan, clair puis sombre.
- **Boutons de téléchargement** (`make_badges.py`, `site/badges/`) : tonal clair, fond `#DAE2FF`, texte et icône `#1C2F59`.
- **Page** (`site/style.css`) : variables `--md-*` = rôles du schéma clair généré ; captures des fiches régénérées en océan.

### Carte Apparence (`ui/settings/AppearanceCard.kt`)

- `OutlinedCard` comme les autres sections : titre, aide, interrupteur « Thème sombre » (`SwitchRow`, ligne
  `toggleable` entière, état coché = `ThemeMode.isDark(valeur du formulaire, isSystemInDarkTheme())`, ligne d'aide
  selon que la valeur est `system` ou non), pastilles (`FlowRow`), champ `OutlinedTextField` du réglage `themeColor`.
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
- **Graine par défaut servie depuis `KairosColors.kt`** (l'océan ; le miel jusqu'au 2026-10-27) plutôt que recalculée : la
  charte reste la source des valeurs par défaut ; `ThemeColorsTest` vérifie que
  l'algorithme embarqué redonne exactement ces 36 valeurs, garantie que toute
  autre graine suit le même calcul.
- **Pastilles de couleur en `Color(argb)`** : la seule couleur de composant
  hors schéma, parce que la pastille montre la donnée elle-même (la graine),
  pas un rôle ; sa coche est blanche ou noire pour rester lisible sur
  n'importe quelle graine.
- **Pas de rouge ni de vert parmi les graines proposées** : le rouge est
  réservé aux urgences et aux erreurs, le vert « ok » au fait. Une couleur
  libre reste permise ; l'aide le rappelle.
- **Deux états à l'écran, trois en base** (choix du propriétaire, issue #72) : l'interrupteur montre ce qu'on voit ; un
  troisième état « Système » n'aide qu'au moment où l'on change, et ce moment est justement celui où le thème déplaît.
  `toggled` ne stocke jamais une valeur égale au système (elle figerait le thème en silence), et un changement du
  système ne retire pas un choix fait (les deux erreurs relevées par l'issue).
- **Suivre le système par défaut** (choix du propriétaire) : un écran sombre la nuit ne s'éclaire plus d'un coup ;
  écarté : clair par défaut, qui ne changeait rien aux installations existantes.
- **Interrupteur dans la carte Apparence, à « Enregistrer »** (choix du propriétaire) : la règle de tous les réglages ;
  écarté : un bouton dans la barre du haut, appliqué tout de suite.
- **Images exportées toujours claires** (choix du propriétaire) : elles vont dans des rapports et à l'impression.
- **Schéma sombre généré, jamais retouché** : même règle que le clair ; `KairosDarkColors` sert la graine par défaut
  comme `KairosLightColors`, et `ThemeColorsTest` vérifie que l'algorithme embarqué redonne ses 36 valeurs.
- **« Ok » sombre selon la règle de l'erreur** : `ok` au ton 80 de sa palette (`#9CD49F`, vérifié contre la palette
  par `ThemeColorsTest`), conteneur et texte du conteneur échangés avec ceux du clair, exactement comme
  `errorContainer` sombre égale `onErrorContainer` clair. Les valeurs claires de la charte diffèrent d'un point de la
  palette recalculée (`#36693D` contre `#36693E`) : on les garde telles quelles plutôt que de retoucher la charte.
- **Relais natifs selon le système, pas selon le réglage** : la fenêtre de démarrage Android et la page de chargement
  web s'affichent avant que la base soit lue ; un thème choisi contre le système s'applique donc une fraction de seconde
  après le démarrage, accepté.
- **Couleurs du système en option, pas par défaut** : la couleur par défaut (l'océan depuis le 2026-10-27, le miel avant)
  reste l'identité du thème de Kairos sur toutes les plateformes ; Material You n'existe
  que sur Android 12 et plus.
- **L'océan remplace le miel comme graine par défaut** (choix du propriétaire, 2026-10-27 ; la décision du miel reste tracée
  dans `docs/DESIGN_SYSTEM.md` § Historique, non réécrite). Le miel reste une graine au choix (`HONEY_THEME_COLOR`, pastille en
  deuxième position) et la couleur fixe du logo, qui contraste ainsi avec le thème au lieu de s'y accorder.
- **Migration limitée à la valeur par défaut exacte** : `replace` sur `"themeColor":"#C28417"` ne touche que ce qui a été
  laissé sur l'ancienne valeur ; ni couleur choisie ni `system`. Qui avait choisi le miel exprès avant la mise à jour ne se
  distingue pas de qui l'avait laissé : il passe aussi à l'océan (conséquence assumée) et peut le rechoisir ensuite. La
  migration ne rejoue pas (table marqueur `migration_mark`, `KairosStore.MARKERS` 12) : le miel choisi après est gardé.
- **Icônes sur fond uni, logo inchangé** : le fond océan profond `#1C2F59` (ton 20) fait ressortir le cadran crème et son secteur
  miel, là où le fond crème d'avant les confondait avec lui (le cadran y était porté par le fond) ; carré plein partout où le système ou le magasin masque
  lui-même l'icône, coins arrondis (22,5 %) là où c'est l'image qui est affichée telle quelle.
- **Boutons de téléchargement en tonal clair** plutôt qu'en primaire plein : un fond discret (`#DAE2FF`) se lit sur fond clair
  comme sur le fond sombre de GitHub.
- **Pas de carte « Nouveau dans cette version »** pour ce changement (choix du propriétaire, `visite-guidee.md`).
