# Thème, navigation et écrans communs

_Rôle : l'identité visuelle (thème MD3 « miel », typographie, icônes, logo)
et la coquille commune à tous les écrans (navigation principale, barre
d'application, retour), plus l'écran « À propos et guide ». Fichiers couverts :
`kmp/ui/src/commonMain/kotlin/com/skohscripts/kairos/ui/` (`KairosApp.kt`,
`Platform.kt`, `theme/`, `icons/`, `navigation/`, `screens/`), les polices de
`composeResources/font/`, `kmp/tools/make_theme.py`, `kmp/tools/make_icons.py`.
Les valeurs de la charte (rôles, formes, « où va la couleur ») viennent de
[`docs/DESIGN_SYSTEM.md`](../DESIGN_SYSTEM.md), cité ici plutôt que recopié._

Reprend le besoin de `docs/spec/accueil-navigation.md` (Kairos 2, tag
`v2.6.0`), moins ce qui
tenait au rendu serveur (bouton « Quitter », restauration du défilement,
rendu du README) et avec **cinq** destinations au lieu de six.

## 1. Besoin métier (cahier des charges)

### Objectif / problème

Kairos a plusieurs vues (Notes, Jour, Semaine, Statistiques, Réglages) qui
doivent partager une identité et une navigation cohérentes sur un téléphone,
une fenêtre de bureau et un onglet de navigateur. Un nouvel utilisateur doit
aussi pouvoir comprendre ce que fait l'outil sans lire le dépôt.

### Comportement attendu (utilisateur)

- **Cinq destinations**, dans l'ordre du flux GTD : Notes, Jour, Semaine,
  Stats, Réglages. Chacune a une icône et un libellé. La destination affichée
  est mise en évidence (indicateur coloré + icône pleine). L'application
  s'ouvre sur **Jour**.
- **Forme de la navigation** :
  - fenêtre de 600 dp de large ou plus, sur toute plateforme : **rail
    vertical** à gauche, avec le logo et « Kairos » en tête ;
  - Android en largeur compacte : **barre de navigation basse** ;
  - bureau ou navigateur en fenêtre étroite : **barre horizontale en haut**
    (pastilles défilantes). **Jamais** de barre basse hors d'Android, même
    dans une fenêtre rétrécie (règle reprise de Kairos 2).
- Une **barre d'application** en haut affiche le titre de l'écran (« Aujourd'hui »
  pour Jour). Sans rail, le logo y porte l'identité.
- **« À propos et guide »** : accessible depuis Réglages. Présente Kairos (ce
  qu'il fait en quatre points, la formule du score de priorité, l'origine du
  nom), la version, la licence (MIT, données locales) et un lien vers le code
  source. Un bouton retour, le retour système Android ou Échap le ferment.
- Depuis le jalon M4, les cinq destinations sont portées : Notes
  (`notes-capture.md`), Jour (`vue-jour.md`), Semaine (`vue-semaine.md`),
  Stats (`statistiques.md`), Réglages (`reglages.md`).
- Au premier lancement, un accueil présente Kairos et les exemples
  (`accueil.md`) ; sur le bureau, un bandeau signale une nouvelle version
  au-dessus de chaque écran (`mises-a-jour.md`).
- Le titre de la barre dit ce qui est affiché : « Jour · mercredi 30
  septembre 2026 » pour un autre jour qu'aujourd'hui, « Semaine · du lundi
  28 septembre 2026 » pour la semaine regardée.
- Au lancement, un indicateur « Ouverture de Kairos… » attend l'ouverture des
  données ; un échec est affiché (« Impossible d'ouvrir les données » et le
  détail), jamais un écran vide.
- Les confirmations et erreurs brèves (export, import) passent par une
  snackbar.

### Critères de succès

- La barre basse n'apparaît jamais sur le bureau ni sur le web, quelle que
  soit la largeur (test `NavigationLayoutTest`).
- Aucun débordement horizontal à 360 dp : la barre du haut défile, le texte
  passe à la ligne.
- Couleurs, formes et typographie conformes à `docs/DESIGN_SYSTEM.md` : un
  seul thème clair, Roboto embarquée (aucune police distante), icônes
  Material Symbols Outlined 400, variante pleine pour la destination active.

### Hors périmètre

- Thème sombre : un seul thème clair pour l'instant (charte). Le schéma
  sombre se générerait depuis la même graine avec `make_theme.py`.
- Tiroir de navigation, rail étendu : non retenus, cinq destinations suffisent.

## 2. Solution technique

### Thème (`theme/`)

- `KairosColors.kt` (**généré** par `tools/make_theme.py`) : `lightColorScheme`
  des 36 rôles MD3, schéma *Tonal spot*, spec 2021, contraste standard, graine
  `#C28417`. Les valeurs coïncident avec la table de `DESIGN_SYSTEM.md` ; les
  rôles que la charte ne listait pas (`surfaceVariant`, `surfaceDim`,
  `surfaceBright`, `surfaceTint`, `scrim`, `onBackground`…) sortent du même
  calcul. Pour changer de couleur : changer la graine et regénérer, jamais
  retoucher un rôle isolé. C'est le thème par défaut ; la couleur choisie
  dans les Réglages en dérive un autre par le même calcul (`ThemeColors`,
  `apparence.md`).
- `KairosTheme.kt` : `KairosTheme(colorScheme = KairosLightColors)` pose
  `MaterialTheme(colorScheme, typography, shapes)` ; `LocalSystemColorScheme`
  (couleurs du système, `null` hors Android 12 et plus, `apparence.md`).
  - Typographie : échelle MD3 par défaut, police **Roboto** 400/500/700 en
    TTF dans `composeResources/font/` (paquet npm `@expo-google-fonts/roboto`
    0.4.3, licence OFL copiée dans `kmp/licenses/Roboto-OFL.txt`) ;
    `titleLarge` 22/28 (titre de page).
  - Formes : 4 / 8 / 12 / 16 / 28 dp (`extraSmall` à `extraLarge`).
  - `KairosExtraColors` (`LocalKairosExtraColors`) : la couleur personnalisée
    « fait / ok » (`#36693D`, `#B7F1B8`, `#1D5127`), hors schéma, non
    harmonisée (décision de la charte).

### Icônes et logo (`icons/`)

- `KairosIcons.kt` (**généré** par `tools/make_icons.py`) : `ImageVector`
  paresseux construits par `symbol()` (`Symbol.kt`) depuis le tracé SVG
  Material Symbols (viewport 960, décalage `translationY = 960` du
  `viewBox="0 -960 960 960"`). Icônes M0 : `Notes`, `Today`, `DateRange`,
  `BarChart`, `Settings`, `Info` (et leurs variantes `…Filled`), `ArrowBack`,
  `ChevronRight`, `OpenInNew` ; jalon M1 : `CheckCircle`
  (et `CheckCircleFilled`), `RadioUnchecked`, `Edit`, `Delete`, `Add`,
  `Upload`, `Download`, `ExpandMore`, `ExpandLess`, `Inbox`, `Warning` ;
  jalon M2 (vue Jour complète) : `Redo` (décaler), `Search`, `Schedule`
  (creux), `Block` (bloquée), `Repeat`, `PushPin` (épinglée, symbole
  `keep`), `TrendingUp` (score, chemin critique), `Description`, `Layers`
  (deep work) ; jalon M3 (chrono) : `PlayArrow`, `Stop`, `Close` (fermer une
  alerte), `NotificationsActive` ; jalon M4 : `ChevronLeft` (semaine
  précédente) ; issue #45 : `Check` (pastille de couleur choisie) ; issue
  #46 : `InstallDesktop` (raccourci d'une copie portable).
  `Construction` (écran « En construction ») est retiré au
  jalon M5, plus rien n'étant en construction.
- `KairosLogo.kt` : le cadran solaire (cadran `#FFEEDC`, anneau `#FFCC85`
  épaisseur 1.6, secteur `#C28417` de 12 h à 2 h, axe `#2B251C` rayon 2.6,
  viewport 40), couleurs fixes, affiché par `Image` (jamais teinté).

### Navigation (`navigation/`, `KairosApp.kt`)

- `Platform` : `ANDROID`, `DESKTOP`, `WEB`, fourni par le point d'entrée ;
  `KairosApp` le pose aussi dans `LocalPlatform` pour les écrans qui en
  dépendent (la vue Jour masque les raccourcis clavier sur Android).
- `Destination` (enum, dans l'ordre) : `NOTES`, `DAY`, `WEEK`, `STATS`,
  `SETTINGS`, chacune avec libellé court, titre de page, icône et icône
  pleine. `START = DAY`.
- `NavigationLayout.choose(platform, largeur)` : `RAIL` si largeur ≥ 600 dp
  (`COMPACT_WIDTH_LIMIT`), sinon `BOTTOM_BAR` sur Android, `TOP_BAR` ailleurs.
- `KairosApp(platform, initialDestination = Destination.START,
  systemColorScheme = null, openServices)` : `KairosTheme` par défaut, puis
  celui de `Settings.themeColor` dès les services prêts (`apparence.md`) ;
  appelle
  `openServices()` une fois (`LaunchedEffect`) : indicateur de progression
  pendant l'attente, titre d'erreur et détail en cas d'échec. Puis
  `KairosShell` : état `destination` (initialisé à `initialDestination`,
  que seules les captures des magasins changent, `publication.md`) et
  `aboutOpen` (`rememberSaveable`),
  `NavState` (jour et semaine regardés, `vue-semaine.md`) ;
  `BoxWithConstraints` fournit la largeur à `choose`. Naviguer vers une
  destination ferme « À propos » et ramène à aujourd'hui et à la semaine
  courante ; `onOpenDay(jour)` ouvre la vue Jour d'un jour donné.
- `AppShell` :
  - `TopAppBar` avec le titre, fidèle à ce qui est affiché (Kairos 2) :
    « À propos et guide », « Jour · mercredi 30 septembre 2026 » pour un
    autre jour qu'aujourd'hui, « Semaine · du lundi 28 septembre 2026 »,
    sinon celui de la destination ; flèche retour quand « À propos » est ouvert ; sinon, sans rail,
    le logo en 28 dp.
  - `RAIL` : `NavigationRail` (en-tête : logo 34 dp + « Kairos ») à gauche
    d'un `Scaffold`.
  - `BOTTOM_BAR` : `Scaffold` avec `NavigationBar`.
  - `TOP_BAR` : barre d'application puis une rangée défilante de `FilterChip`
    (icône + libellé ; sélection en `secondaryContainer`, rôle MD3 par défaut
    des puces sélectionnées).
  - Quand « À propos » est ouvert, aucune destination n'est marquée active.
  - `SnackbarHost` dans chaque `Scaffold` ; `LocalMessages` fournit aux
    écrans une fonction qui y affiche un message.
  - Au-dessus du contenu de chaque destination : `UpdateBanner`
    (`mises-a-jour.md`) ; hors des branches de mise en page :
    `WelcomeDialog` (`accueil.md`) ; veilles de toute l'application :
    `ChronoWatcher` (`temps-reel-chrono.md`) et `UpdateWatcher`.
  - `BackHandler(enabled = aboutOpen)` (`ui-backhandler`) : retour système
    Android (y compris le geste prédictif, `enableOnBackInvokedCallback`) et
    Échap sur le bureau et le web ferment « À propos ».

### Écrans (`screens/`)

- `DestinationScreen(destination, services, nav, onOpenDay, onNavigate,
  onOpenAbout)` : les cinq destinations sont portées depuis le jalon M4 :
  `NOTES` → `NotesScreen` (`notes-capture.md`), `DAY` → `DayScreen` du
  jour de `nav.day` (`vue-jour.md`), `WEEK` → `WeekScreen`
  (`vue-semaine.md`), `STATS` → `StatsScreen` (`statistiques.md`),
  `SETTINGS` → `SettingsScreen` (`reglages.md` : formulaire complet, carte
  Données, carte « À propos et guide » en `OutlinedCard` + `ListItem`,
  icône `Info`, chevron).
- `AboutScreen` : colonne défilante de 720 dp au plus, centrée. Logo 56 dp +
  nom + devise ; phrase d'introduction ; trois cartes « filled »
  (`surfaceContainerLow`, élévation 0 : jamais d'ombre sur une carte) : « En
  bref » (quatre points), « Le score de priorité (WSJF) » (formule en
  primaire gras + explication), « Pourquoi Kairos ? » ; version
  (`KairosBuild.VERSION_NAME`) ; licence ; bouton « Code source » qui ouvre
  `SOURCE_URL` (`https://github.com/SKOHscripts/Kairos`) par `LocalUriHandler`.

### Décisions et pièges tracés

- **Cinq destinations** (décision du plan, § 3 point 4, validée le
  2026-09-28) : MD3 limite une barre de navigation à cinq entrées. L'accueil
  de Kairos 2 (qui rendait le README) devient « À propos et guide », dans
  Réglages.
- **Seuil 600 dp** (MD3, compact / moyen) au lieu des 720 px de Kairos 2 :
  le rail tient dès 600 dp en Compose, et c'est le seuil standard des classes
  de taille de fenêtre.
- **Règle « jamais de barre basse hors Android » conservée**, mais décidée
  côté client par `Platform`, puisqu'il n'y a plus de serveur. Pour la même
  raison, la règle Kairos 2 « aucune détection de plateforme côté serveur »
  n'a plus d'objet.
- **Navigation par état plutôt que par bibliothèque** : cinq destinations de
  premier niveau et un seul écran secondaire. À réévaluer si des écrans
  imbriqués apparaissent (détail d'une tâche en plein écran, par exemple).
- **Retour** : `org.jetbrains.compose.ui:ui-backhandler` doit être déclaré
  explicitement (absent des dépendances transitives de `compose.ui` en 1.12).
