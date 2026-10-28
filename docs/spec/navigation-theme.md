# Thème, navigation et écrans communs

_Rôle : l'identité visuelle (thème MD3 « océan », typographie, icônes, logo)
et la coquille commune à tous les écrans (navigation principale, barre
d'application, retour), plus l'écran « À propos et guide ». Fichiers couverts :
`kmp/ui/src/commonMain/kotlin/com/skohscripts/kairos/ui/` (`KairosApp.kt`,
`Platform.kt`, `theme/`, `icons/`, `navigation/`, `screens/`), les polices de
`composeResources/font/`, `kmp/tools/make_theme.py`, `kmp/tools/make_icons.py`.
Les valeurs de la charte (rôles, formes, « où va la couleur ») viennent de
[`docs/DESIGN_SYSTEM.md`](../DESIGN_SYSTEM.md), cité ici plutôt que recopié._

## 1. Besoin métier (cahier des charges)

### Objectif / problème

Kairos a plusieurs vues (Accueil, Notes, Jour, Semaine, Statistiques, Réglages) qui
doivent partager une identité et une navigation cohérentes sur un téléphone,
une fenêtre de bureau et un onglet de navigateur. Un nouvel utilisateur doit
aussi pouvoir comprendre ce que fait l'outil sans lire le dépôt.

### Comportement attendu (utilisateur)

- **Cinq destinations** : **Accueil** en tête, puis l'ordre du flux GTD :
  Notes, Jour, Semaine, Stats. Chacune a une icône et un libellé. La
  destination affichée est mise en évidence (indicateur coloré + icône
  pleine). L'application s'ouvre sur **l'Accueil** (2026-10-10 ; avant :
  Jour), dans l'espace où on l'a quittée.
- **Réglages** : une roue crantée (`KairosIcons.Settings`, « Réglages ») à
  droite de la barre d'application, sur toutes les destinations des deux
  espaces ; elle ouvre les Réglages en écran secondaire (titre « Réglages »,
  flèche retour), les mêmes dans les deux espaces, toujours sur la liste de leurs catégories (`reglages.md`). Sur un
  seul volet, une catégorie ouverte prend le titre de la barre et la flèche (ou le retour système, ou Échap) ramène
  d'abord à la liste. Elle disparaît quand un écran secondaire est ouvert.
- **Forme de la navigation** :
  - fenêtre de 600 dp de large ou plus, sur toute plateforme : **rail
    vertical** à gauche, avec le logo et « Kairos » en tête ;
  - Android en largeur compacte : **barre de navigation basse** ;
  - bureau ou navigateur en fenêtre étroite : **barre horizontale en haut**
    (pastilles défilantes). **Jamais** de barre basse hors d'Android, même
    dans une fenêtre rétrécie (règle de Kairos).
- Une **barre d'application** en haut affiche le titre de l'écran (« Aujourd'hui »
  pour Jour). Sans rail, le logo y porte l'identité.
- **Aide** (`accueil.md`) : un bouton « ? » (`KairosIcons.Help`,
  « Aide ») avant la roue crantée, sur toutes les destinations des deux
  espaces sauf l'Accueil, et sur les Réglages ; il ouvre l'aide de l'écran
  courant, l'Accueil et la visite guidée.
- **Soutien** (`soutien.md`) : un cœur (`KairosIcons.Favorite`, « Soutenir Kairos »)
  juste avant le « ? », sur tous les écrans y compris les écrans secondaires ;
  il ouvre la page de don dans le navigateur.
- **« À propos et guide »** : accessible depuis Réglages et depuis l'Accueil
  (écran secondaire `Secondary.ABOUT`) ; ouvert depuis les Réglages, sa flèche
  retour y ramène. Présente Kairos (ce
  qu'il fait en quatre points, la formule du score de priorité, l'origine du
  nom), la version, la licence (MIT, données locales) et un lien vers le code
  source. Un bouton retour, le retour système Android ou Échap le ferment.
- Les cinq destinations sont portées : Accueil (`accueil.md`), Notes
  (`notes-capture.md`), Jour (`vue-jour.md`), Semaine (`vue-semaine.md`),
  Stats (`statistiques.md`) ; les Réglages (`reglages.md`) sont un écran
  secondaire.
- Au premier lancement, un accueil présente Kairos et les exemples
  (`accueil.md`) ; sur le bureau, un bandeau signale une nouvelle version
  au-dessus de chaque écran (`mises-a-jour.md`).
- Le titre de la barre dit ce qui est affiché : « Jour · mercredi 30
  septembre 2026 » pour un autre jour qu'aujourd'hui, « Semaine · du lundi
  28 septembre 2026 » pour la semaine regardée ; sans rail (fenêtre
  étroite), en date courte (« Jour · mer. 30/09 », « Semaine · du 28 sept. »).
  Deux lignes au plus, puis points de suspension ; en corps `titleMedium` quand le sélecteur d’espace partage la barre (sans rail), pour que « Aujourd’hui » ne se coupe pas au milieu du mot à 360 dp.
- Au lancement, un indicateur « Ouverture de Kairos… » attend l'ouverture des
  données ; un échec est affiché (« Impossible d'ouvrir les données » et le
  détail), jamais un écran vide.
- Les confirmations et erreurs brèves (export, import) passent par une
  snackbar.

### Critères de succès

- La barre basse n'apparaît jamais sur le bureau ni sur le web, quelle que
  soit la largeur (test `NavigationLayoutTest`).
- Aucun débordement horizontal à 360 dp : la barre du haut défile, le texte
  passe à la ligne ; l'onglet actif y est toujours visible.
- Redimensionner la fenêtre au travers de 600 dp (rail ↔ barre du haut) ne
  change rien à l'écran affiché : un dialogue ouvert, une saisie en cours
  restent en place (test `ShellResizeUiTest`).
- Couleurs, formes et typographie conformes à `docs/DESIGN_SYSTEM.md` : un
  seul thème clair, Roboto embarquée (aucune police distante), icônes
  Material Symbols Outlined 400, variante pleine pour la destination active.

### Hors périmètre

- (Le thème sombre, longtemps hors périmètre, est livré en 3.7.0 : `apparence.md` § Thème sombre.)
- Tiroir de navigation, rail étendu : non retenus, cinq destinations suffisent.

## 2. Solution technique

### Thème (`theme/`)

- `KairosColors.kt` (**généré** par `tools/make_theme.py`) : `lightColorScheme`
  des 36 rôles MD3, schéma *Tonal spot*, spec 2021, contraste standard, graine
  `#2F6FED` (l'océan ; le miel `#C28417` jusqu'au 2026-10-27, changé le 2026-10-27 : l'océan remplace le miel comme graine
  par défaut, `apparence.md` § Couleur par défaut). Les valeurs coïncident avec la table de `DESIGN_SYSTEM.md` ; les
  rôles que la charte ne listait pas (`surfaceVariant`, `surfaceDim`,
  `surfaceBright`, `surfaceTint`, `scrim`, `onBackground`…) sortent du même
  calcul. Pour changer de couleur : changer la graine et regénérer, jamais
  retoucher un rôle isolé. C'est le thème par défaut ; la couleur choisie
  dans les Réglages en dérive un autre par le même calcul (`ThemeColors`,
  `apparence.md`).
- `KairosComponents.kt` (boutons de 36 dp, puces de 32 dp, segmentés de
  40 dp, bouton-icône de ligne) et `KairosSpacing.kt` (échelle 4 / 8 / 12 /
  16 / 24 dp) : densité des composants, `densite.md`.
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
  #46 : `InstallDesktop` (raccourci d'une copie portable) ; espace Équipe
  (jalon E1, `equipe.md`) : `Groups`, `Person`, `ViewKanban`, `Stacks`,
  `Monitoring`, puis au jalon E2 `PersonAdd`, `EventBusy`, `Archive`,
  `Unarchive`, au jalon E3 `SwapHoriz`, `MoreVert`, `HourglassEmpty`, au jalon E4
  `Balance`, au jalon E5 `Casino`, `Science`, `CompareArrows`, `Refresh`,
  `Star` (et `StarFilled`), `ContentCopy` (et les variantes pleines de `Groups`, `ViewKanban`,
  `Stacks`, `Monitoring`) ; aide, Accueil et visite guidée (`accueil.md`) : `Help` ; soutien (`soutien.md`) : `Favorite` ;
  Réglages rangés par catégories (`reglages.md`) : `Tune`, `Sort`, `Timer`, `Database` (catégories), `Translate`,
  `Palette`, `Update`, `Work`, `CalendarViewWeek`, `Workspaces`, `Label`, `LightMode`, `History`, `Shield`,
  `Leaderboard`, `Bedtime`, `SettingsBackupRestore` (titres des cartes) ; guide (`visite-guidee.md`, issue #95) : `Spot`
  (étiquette « Ici » d'une section montrée) ; export des Stats (`statistiques.md`, issue #113) : `TableView` (CSV),
  `Image` (image d'une carte) ; carte « Thème sombre » du guide (3.7.0) : `DarkMode` ; 3.8.0 : `Flag` (échéances de la vue Mois),
  `CalendarMonth` (carte « Le mois d'un coup d'œil »), `ShowChart` (temps chronométré au fil du temps), `EditNote`
  (« Corriger les estimations… »), `Alarm` (rappels) ; 3.10.0 : `Event` (calendriers importés : carte « Calendriers »,
  créneau importé, journée entière, carte du guide).
  `Construction` (écran « En construction ») est retiré au
  jalon M5, plus rien n'étant en construction.
- `KairosLogo.kt` : le cadran solaire (cadran `#FFEEDC`, anneau `#FFCC85`
  épaisseur 1.6, secteur `#C28417` de 12 h à 2 h, axe `#2B251C` rayon 2.6,
  viewport 40), couleurs fixes, affiché par `Image` (jamais teinté). Ces couleurs sont celles du miel, qui n'est plus la
  graine du thème par défaut : le logo les garde et contraste avec l'océan (`apparence.md` § Icônes et supports fixes).

### Navigation (`navigation/`, `KairosApp.kt`)

- `Platform` : `ANDROID`, `DESKTOP`, `WEB`, fourni par le point d'entrée ;
  `KairosApp` le pose aussi dans `LocalPlatform` pour les écrans qui en
  dépendent (la vue Jour masque les raccourcis clavier sur Android).
- `Destination` (enum, dans l'ordre) : `HOME`, `NOTES`, `DAY`, `WEEK`,
  `STATS`, chacune avec libellé court, titre de page, icône et icône
  pleine (`NavEntry` ; `HOME` : `nav_home` « Accueil », `title_home`,
  `KairosIcons.Home` / `HomeFilled`). `START = HOME` (ouverture de
  l'application), `WORK = DAY` (passage dans l'espace Perso, retour forcé
  quand la gestion d'équipe est désactivée). Mode « gestion d'équipe » activé :
  un second espace, ses destinations (`TeamDestination`) et le sélecteur
  d'espace en tête du rail ou dans la barre du haut (`equipe.md`
  § Interface) ; désactivé, la coquille est celle décrite ici, à l'octet
  près.
- `NavigationLayout.choose(platform, largeur)` : `RAIL` si largeur ≥ 600 dp
  (`COMPACT_WIDTH_LIMIT`), sinon `BOTTOM_BAR` sur Android, `TOP_BAR` ailleurs.
- `KairosApp(platform, initialDestination = Destination.START,
  initialTeamDestination = TeamDestination.HOME, initialSecondary = null,
  systemColorScheme = null, splash = false, openServices)` : `KairosTheme` par défaut, puis
  celui de `Settings.themeColor` dès les services prêts (`apparence.md`) ;
  appelle
  `openServices()` une fois (`LaunchedEffect`) : indicateur de progression
  pendant l'attente, titre d'erreur et détail en cas d'échec ; avec
  `splash = true` (points d'entrée seulement), la page de démarrage couvre
  le tout jusqu'à son fondu (`demarrage.md`). Puis
  `KairosShell` : état `destination` (initialisé à `initialDestination`,
  que changent les tests, l'auto-test et les captures des magasins,
  `publication.md`), `secondary: Secondary?` (`rememberSaveable` ;
  `SETTINGS` ou `ABOUT` ; `initialSecondary` pour la capture des Réglages)
  et `secondaryParent` (l'écran secondaire d'où « À propos » a été ouvert,
  rendu par la flèche retour),
  `NavState` (jour et semaine regardés, `vue-semaine.md`) ;
  `BoxWithConstraints` fournit la largeur à `choose`. Naviguer vers une
  destination ferme l'écran secondaire (Réglages ou « À propos ») et ramène à
  aujourd'hui et à la semaine courante ; `onOpenDay(jour)` ouvre la vue Jour d'un jour donné.
- `AppShell` :
  - `TopAppBar` avec le titre, fidèle à ce qui est affiché :
    « Réglages » (ou le titre de la catégorie des Réglages ouverte seule à l'écran, `NavState.settings`, `reglages.md`),
    « À propos et guide », « Jour · mercredi 30 septembre 2026 » pour un
    autre jour qu'aujourd'hui, « Semaine · du lundi 28 septembre 2026 »
    (sans rail : `Dates.dayShort` et `Dates.short`, la date longue tenait sur
    trois lignes à 380 dp), sinon celui de la destination ; `maxLines = 2` ; flèche retour quand un écran secondaire est ouvert ; sinon, sans rail,
    le logo en 28 dp. Actions : le sélecteur d'espace (sans rail, gestion
    d'équipe activée), le cœur (sauf avec ce sélecteur, `soutien.md`), le « ? » (s'il y a un sujet d'aide : ni
    l'Accueil ni « À propos »), puis la roue crantée `onOpenSettings`
    (aucun écran secondaire ouvert).
  - Une seule arborescence `Row { rail? ; Scaffold }` pour les trois formes :
    seuls le rail, la rangée de pastilles et la barre basse sont conditionnels,
    le contenu garde sa place dans la composition.
  - `RAIL` : `NavigationRail` (en-tête : logo 34 dp + « Kairos ») à gauche
    du `Scaffold` ; en-tête et destinations dans une colonne défilante (une
    fenêtre de 360 dp de haut, téléphone en paysage, coupait Semaine et
    Stats sans moyen de les atteindre).
  - `BOTTOM_BAR` : `NavigationBar` en `bottomBar` du `Scaffold`.
  - `TOP_BAR` : barre d'application puis une rangée défilante de `FilterChip`
    (icône + libellé ; sélection en `secondaryContainer`, rôle MD3 par défaut
    des puces sélectionnées). **La molette de la souris** la fait défiler
    (`pointerInput` sur `PointerEventType.Scroll`) : sans geste horizontal,
    une fenêtre de bureau étroite n'atteignait ni « Stats » ni, une fois Stats
    ouvert, « Accueil » (`ShellResizeUiTest`). La pastille active se ramène en vue
    (`BringIntoViewRequester`) dès qu'elle devient active : à 360 dp, les
    cinq pastilles ne tiennent pas, et l'on peut arriver sur Stats depuis
    l'Accueil ou la visite.
  - Quand un écran secondaire est ouvert, aucune destination n'est marquée active.
  - `SnackbarHost` dans chaque `Scaffold` ; `LocalMessages` fournit aux
    écrans une fonction qui y affiche un message.
  - Au-dessus du contenu de chaque destination : `UpdateBanner`
    (`mises-a-jour.md`) ; hors des branches de mise en page :
    `WelcomeDialog`, `HelpDialog` et `GuideDialog` (`accueil.md`) ; veilles de toute l'application :
    `ChronoWatcher` (`temps-reel-chrono.md`) et `UpdateWatcher`.
  - `BackHandler(enabled = secondary != null)` (`ui-backhandler`) : retour système
    Android (y compris le geste prédictif, `enableOnBackInvokedCallback`) et
    Échap sur le bureau et le web ferment l'écran secondaire.

### Écrans (`screens/`)

- `DestinationScreen(destination, services, nav, onOpenDay, onNavigate,
  home)` : `HOME` → `home()` (l'Accueil, fourni par la coquille),
  `NOTES` → `NotesScreen` (`notes-capture.md`), `DAY` → `DayScreen` du
  jour de `nav.day` (`vue-jour.md`), `WEEK` → `WeekScreen`
  (`vue-semaine.md`), `STATS` → `StatsScreen` (`statistiques.md`).
- `TeamDestinationScreen(destination, services, nav, onTour, home)` : `HOME`
  → `home()`, puis les quatre écrans d'équipe ; transmet `nav` (onglet
  « Échanges ») et le lancement de la visite d'équipe.
- `HomeScreen` : la page « Accueil » (`accueil.md`), composée par `AppShell`
  (`home`) pour les deux espaces.
- `SettingsScreen` (`reglages.md` : formulaire complet, carte Données, carte
  « À propos et guide » en `OutlinedCard` + `ListItem`, icône `Info`,
  chevron) : écran secondaire `Secondary.SETTINGS`, composé par `AppShell`.
- `AboutScreen` : colonne défilante de 720 dp au plus, centrée. Logo 56 dp +
  nom + devise ; phrase d'introduction ; trois cartes « filled »
  (`surfaceContainerLow`, élévation 0 : jamais d'ombre sur une carte) : « En
  bref » (quatre points, puce à part : les lignes suivantes s'alignent sous le texte), « Le score de priorité (WSJF) » (formule en
  primaire gras + explication), « Pourquoi Kairos ? » ; version
  (`KairosBuild.VERSION_NAME`) ; licence ; bouton « Code source » qui ouvre
  `SOURCE_URL` (`https://github.com/SKOHscripts/Kairos`) par `LocalUriHandler`
  (enveloppé de `runCatching` comme « Soutenir Kairos » : sans `xdg-open`,
  l'application quittait sur l'erreur),
  puis un bouton « Soutenir Kairos » qui ouvre `DONATE_URL` (`soutien.md`).

### Décisions et pièges tracés

- **Cinq destinations** (décision du plan, § 3 point 4, validée le
  2026-09-28) : MD3 limite une barre de navigation à cinq entrées. Le guide
  d'utilisation vit dans « À propos et guide », dans Réglages.
- **Accueil en destination, Réglages dans la barre du haut** (2026-10-10,
  demande explicite) : l'Accueil, ouvert jusque-là depuis le « ? »
  seulement, devient la première destination des deux espaces et l'écran
  d'ouverture ; les Réglages, consultés rarement, cèdent leur place et
  passent en roue crantée à droite de la barre du haut (convention des
  applications Android et de bureau). Toujours cinq destinations par
  espace. Passer d'un espace à l'autre ouvre son écran de travail (Jour,
  Suivi) et non son Accueil : on change d'espace pour travailler. Écartés :
  garder l'ouverture sur Jour avec un Accueil en onglet (l'Accueil doit être
  la première chose vue) ; une sixième destination (limite MD3).
- **Seuil 600 dp** (MD3, compact / moyen) : le rail tient dès 600 dp en Compose, et c'est le seuil standard des classes
  de taille de fenêtre.
- **Règle « jamais de barre basse hors Android » conservée**, mais décidée
  côté client par `Platform`, puisqu'il n'y a pas de serveur.
- **Navigation par état plutôt que par bibliothèque** : cinq destinations de
  premier niveau et deux écrans secondaires (Réglages, « À propos »). À réévaluer si des écrans
  imbriqués apparaissent (détail d'une tâche en plein écran, par exemple).
- **Une arborescence, pas trois branches** (2026-10-10, test exploratoire) :
  une branche `when (layout)` par forme de navigation recomposait l'écran à
  neuf au franchissement de 600 dp, ce qui fermait le dialogue d'édition et
  perdait la saisie. Les éléments de navigation sont conditionnels, le
  `Scaffold` et son contenu ne le sont pas. Écarté : `movableContentOf`
  (le contenu capture l'état de la coquille, il faudrait tout passer en
  paramètres).
- **Retour** : `org.jetbrains.compose.ui:ui-backhandler` doit être déclaré
  explicitement (absent des dépendances transitives de `compose.ui` en 1.12).
