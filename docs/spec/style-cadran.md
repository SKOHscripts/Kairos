# Style « Cadran » : un second langage visuel, inspiré d'iOS

_Rôle : proposer, à côté de Material 3, un style d'interface épuré inspiré d'iOS, au choix dans Réglages → Apparence.
Fichiers couverts : `kmp/core/.../model/Settings.kt` (`uiStyle`), `kmp/core/.../settings/UiStyle.kt`,
`kmp/core/.../settings/SettingsForm.kt` (champ `uiStyle`), `kmp/core/.../sync/SyncScope.kt` (`LOCAL_SETTINGS`),
`kmp/ui/.../theme/KairosStyle.kt` (style courant), `kmp/ui/.../theme/CadranTheme.kt` (schéma, formes, typographie, retour au
toucher), `kmp/ui/.../theme/KairosTheme.kt`, `kmp/ui/.../theme/ThemeColors.kt` (`accentFor`),
`kmp/ui/.../theme/KairosComponents.kt` (boutons, puces, segmentés, actions de ligne, interrupteur, cartes),
`kmp/ui/.../KairosApp.kt`, `kmp/ui/.../navigation/AppShell.kt` (barres et rail), `kmp/ui/.../settings/AppearanceCard.kt`,
`kmp/ui/.../settings/SettingsScreen.kt`, `kmp/ui/.../settings/SettingsFormCards.kt` (`SwitchRow`),
`kmp/ui/.../settings/SettingsCategory.kt`, `kmp/ui/.../guide/FeatureCatalog.kt` (carte `ui_style`),
`kmp/desktopApp/.../SelfTest.kt` (captures `cadran-*`). Tests : `core/.../settings/UiStyleTest.kt`,
`ui/.../theme/CadranThemeTest.kt`, `ui/.../ComponentDensityTest.kt`, `desktopApp/.../CadranStyleUiTest.kt`._

État : spécifiée le 2026-11-20 (demande du propriétaire), implémentée pour la 4.3.0.

## 1. Besoin métier (cahier des charges)

### Objectif / problème

Kairos parle Material Design 3 (`docs/DESIGN_SYSTEM.md`) : c'est le langage d'Android, mais pas celui de tout le monde. Une
partie des utilisateurs vient d'iOS, ou préfère un dessin plus sobre : fonds gris neutres, cartes blanches groupées, titres
en gras, boutons qui pâlissent sous le doigt plutôt qu'une onde, interrupteurs à la iOS. Deux langages peuvent cohabiter dans
une même application, au choix de l'utilisateur.

Kairos propose donc un **second style**, « **Cadran** » (le cadran solaire du logo), inspiré d'iOS mais propre à Kairos :
il garde la couleur choisie, les règles d'usage de la couleur et la densité de la charte.

### Comportement attendu (utilisateur)

- Réglages → Général → carte **Apparence**, en tête : une rangée à deux segments **« Style »** : **« Material »** (par défaut)
  et **« Cadran »**, avec une ligne d'aide : « Material : le style d’Android. Cadran : un style épuré inspiré d’iOS, gris
  neutres, cartes groupées, boutons et interrupteurs à la iOS. Appliqué à « Enregistrer ». ». Comme tout réglage, le choix
  ne s'applique qu'à **« Enregistrer »** ; tout l'écran change alors de style, sans quitter l'écran ouvert.
- Le style se combine avec le reste de la carte : **thème sombre** (Cadran a son clair et son sombre), **couleur** (pastilles,
  couleur libre, couleurs du système : en Cadran, c'est la couleur d'accent) et **taille du texte**.
- En **Cadran** :
  - **Fonds** : la page est gris clair (`#F2F2F7`) en clair, noire en sombre ; les cartes, les lignes de tâche, les barres et
    les menus sont blancs en clair, gris très sombre (`#1C1C1E`) en sombre ; dialogues et remplissages suivent les gris d'iOS.
    Aucune teinte de la couleur choisie dans les gris.
  - **Accent** : la couleur choisie, vive (sa teinte et sa saturation), assombrie ou éclaircie juste assez pour rester lisible
    (4,5:1 au moins sur ses fonds) : liens, boutons, interrupteur allumé, onglet actif.
  - **Couleurs réservées** de la charte, prises aux couleurs système d'iOS aux mêmes conditions de lisibilité : **rouge** (P0,
    erreurs, dépassements), **vert** (« fait »), **violet** (deep work). Elles gardent leur usage exclusif.
  - **Formes** : champs à 10 dp, dialogues à 22 dp ; cartes (12 dp), puces (8 dp) et boutons en pilule comme en Material.
  - **Texte** : Roboto, mêmes tailles que Material (densité de la charte, 14 sp), mais titres en gras et sans espacement
    supplémentaire entre les lettres.
  - **Boutons** : principal plein à la couleur d'accent ; secondaire gris plein (ni contour ni teinte) ; tonal comme en
    Material ; bouton de texte à la couleur d'accent. Sous le doigt, un bouton **pâlit** (pas d'onde).
  - **Lignes et éléments cliquables** : sous le doigt, un **voile gris** (le surlignage d'iOS), pas d'onde.
  - **Segmentés** : un rail gris où le segment choisi est une pastille blanche (grise en sombre), en gras, sans coche.
  - **Puces** : pilules grises sans contour, la choisie en `secondaryContainer`.
  - **Interrupteurs** : à la iOS (rail arrondi, rond blanc), allumés à la couleur d'accent.
  - **Cartes** : pleines, sans contour, blanches sur la page grise (la liste groupée d'iOS).
  - **Navigation** : barre basse, rail et barre haute sans pastille sous l'onglet actif : l'icône et le libellé actifs prennent la
    couleur d'accent, les autres sont gris ; la barre du haut a le fond de la page et un titre en gras ; la barre basse a un
    séparateur en haut.
- Le style est **propre à l'appareil** (comme la couleur et le thème sombre) : il ne voyage pas par la synchronisation ; il voyage
  avec l'export et l'import.
- Hors de l'application, rien ne change : logo, icônes d'installation, page de démarrage (qui précède la lecture des réglages),
  widgets Android, images exportées des Stats (claires, en Material : une image se colle dans un rapport), captures des magasins.

### Critères de succès

- Material reste le style **par défaut** et ne change **en rien** : mêmes valeurs, mêmes captures qu'avant (`ThemeColorsTest`,
  `M5ScreensUiTest`, captures `--self-test`).
- Choisir « Cadran » puis « Enregistrer » enregistre `uiStyle = cadran` et repeint l'écran, qui reste ouvert : le fond devient
  `#F2F2F7` (`CadranStyleUiTest`).
- Pour chacune des huit graines proposées, en clair et en sombre : l'accent, le rouge, le vert et le violet atteignent 4,5:1 sur
  la page, la carte et le dialogue ; le texte secondaire atteint 4,5:1 sur la page, la carte et le remplissage ; le contour 3:1
  sur la page et la carte ; le texte sur l'accent 4,5:1 (`CadranThemeTest`).
- Une valeur inconnue (import, fichier édité à la main) donne Material, jamais une erreur (`UiStyleTest`).
- Aucun appel direct à une carte à contour ou à un interrupteur de Material hors des enveloppes (`ComponentDensityTest`).
- Aucun écran ne déborde à 420 dp de large en Cadran, clair comme sombre (captures `cadran-narrow`, `cadran-dark-narrow`).

### Hors périmètre / différé

- **Verre liquide** (flou translucide des barres et menus) : écarté. Il demande une bibliothèque de flou propre à Android 12 et
  plus, sans équivalent sur le bureau ni le web, et pèse sur le GPU.
- **Police d'iOS** : SF Pro n'est pas libre. Écarté aussi : embarquer Inter (police libre proche de SF) : 300 à 600 Ko de plus et
  une seconde police. Cadran garde Roboto.
- **Grands titres iOS** (34 sp en tête de page qui se replient au défilement), **chevrons** en bout de ligne, **feuilles** à la iOS,
  **retour haptique** : différés ; ils touchent chaque écran, pas seulement les composants partagés.
- **Champs de saisie à la iOS** (sans contour) : écartés ; le contour gris (3:1, `accessibilite.md`) et l'étiquette flottante de
  Material restent, avec des coins de 10 dp.
- **Composants Material restants** (menus, cases à cocher, boutons radio, onglets, boutons-icônes des barres) : gardés tels
  quels, avec une onde neutre (voir § Décisions).
- D'autres styles ou palettes : pas demandés.

## 2. Solution technique

### Réglage (`core`)

- `Settings.uiStyle: String`, par défaut `"material"` (`Settings.UI_STYLE_MATERIAL`), ou `"cadran"` (`UI_STYLE_CADRAN`).
- `UiStyle` (`core/settings`, pur) : `normalize(valeur)` (minuscules, espaces retirés ; autre que `cadran` → `material`),
  `isCadran(valeur)`.
- `SettingsForm` : champ `uiStyle` (`FieldKind.TEXT`, normalisé par `UiStyle.normalize`, jamais refusé, comme `themeMode`).
- `SettingsCategory.GENERAL_KEYS` gagne `uiStyle` (catégorie Général, carte Apparence).
- `SyncScope.LOCAL_SETTINGS` gagne `uiStyle` (propre à l'appareil). Le fichier d'or `solo-export-v1.json` gagne
  `"uiStyle": "material"` ; un export plus ancien l'ignore à l'import (valeur par défaut).

### Style courant (`ui/theme/KairosStyle.kt`)

- `enum class KairosStyle { MATERIAL, CADRAN }`, `KairosStyle.of(réglage)` (par `UiStyle.isCadran`) ;
  `LocalKairosStyle` (`staticCompositionLocalOf`, `MATERIAL` par défaut), posé par `KairosTheme`. Seuls le thème, les
  enveloppes de `KairosComponents` et la coquille de navigation le lisent : un écran ne connaît pas le style.

### Thème (`ui/theme/KairosTheme.kt`, `CadranTheme.kt`)

- `KairosTheme(colorScheme, dark, light, style = MATERIAL, accent = null)` : en `MATERIAL`, inchangé. En `CADRAN` :
  `CadranTheme.scheme(colorScheme, accent, dark)` remplace le schéma, `CadranTheme.typography(…)` la typographie,
  `CadranTheme.shapes` les formes, `CadranTheme.extraColors(dark)` la couleur « ok » ; `LocalIndication` devient
  `CadranHighlight`, et `LocalRippleConfiguration` une onde neutre (`RippleConfiguration(color = onSurface)`).
  `LocalLightColorScheme` reste le schéma **Material** clair ; `StatsExport` appelle `KairosTheme` sans style : les images
  exportées restent en Material.
- `KairosApp` lit `settings.uiStyle` (`KairosStyle.of`) et l'accent `ThemeColors.accentFor(themeColor, systemAvailable)` : la
  couleur choisie (`0xRRGGBB`) ; `null` pour les couleurs du système quand la plateforme en fournit (l'accent part alors du
  `primary` du schéma du système) ; l'océan pour une valeur illisible ou les couleurs du système absentes (comme
  `schemeFor`). Un seul appel à `KairosTheme` dans les deux styles : changer de style ne recrée pas l'écran ouvert.
- **Familles de couleur** (`CadranTheme.family(rgb, dark)`) : teinte et chroma HCT de `rgb`, rôle au ton **45** en clair, **70**
  en sombre (`LIGHT_TONE`, `DARK_TONE`), texte du rôle au ton 100 / 10, conteneur au ton 90 / 30 et son texte au ton 10 / 90,
  à chroma plafonnée à 32 (`CONTAINER_CHROMA`) ; `Hct.from` ramène la chroma dans la gamme sRGB.
- **Accent** : la famille de la graine (`primary`, `onPrimary`), `inversePrimary` = le rôle de la même famille dans le thème
  inverse. Les autres rôles de la famille d'accent (`primaryContainer`, `secondary*`) restent ceux du schéma Material de la même
  graine (un seul bloc teinté par écran, sélection en `secondaryContainer`).
- **Couleurs réservées** : rouge `#FF3B30` → `error*`, violet `#AF52DE` → `tertiary*` (deep work), vert `#34C759` →
  `KairosExtraColors.ok*` (`IOS_RED`, `IOS_PURPLE`, `IOS_GREEN` : couleurs système d'iOS, dont seules la teinte et la chroma
  servent).
- **Gris** (valeurs fixes, d'après les couleurs système d'iOS ; clair / sombre) :

  | Rôles | Clair | Sombre | Usage |
  |---|---|---|---|
  | `background`, `surface` | `#F2F2F7` | `#000000` | page (fond groupé d'iOS) |
  | `surfaceContainerLowest` | `#FFFFFF` | `#000000` | |
  | `surfaceContainerLow`, `surfaceContainer` | `#FFFFFF` | `#1C1C1E` | cartes, lignes de tâche, barres, menus |
  | `surfaceContainerHigh` | `#FFFFFF` | `#2C2C2E` | dialogues, feuilles |
  | `surfaceContainerHighest`, `surfaceVariant` | `#E5E5EA` | `#3A3A3C` | remplissages (puces, badges, rail des segments) |
  | `surfaceBright` / `surfaceDim` | `#FFFFFF` / `#E5E5EA` | `#2C2C2E` / `#000000` | |
  | `onSurface`, `onBackground` | `#000000` | `#FFFFFF` | libellé |
  | `onSurfaceVariant` | `#636366` | `#AEAEB2` | libellé secondaire, variante lisible |
  | `outline` | `#8A8A8E` | `#8E8E93` | contours (champs) |
  | `outlineVariant` | `#C6C6C8` | `#38383A` | séparateur opaque |
  | `inverseSurface` / `inverseOnSurface` | `#1C1C1E` / `#FFFFFF` | `#F2F2F7` / `#000000` | carte « En ce moment », snackbar |

  `surfaceTint` est transparent (aucune teinte d'élévation), `scrim` noir.
- **Formes** (`CadranTheme.shapes`) : `extraSmall` 10 dp (champs), `small` 8, `medium` 12, `large` 16, `extraLarge` 22 dp
  (dialogues).
- **Typographie** (`CadranTheme.typography(base)`) : l'échelle de `kairosTypography` (mêmes tailles, mêmes hauteurs de ligne) ;
  `display*`, `headline*` et `titleLarge` en gras (700) ; `letterSpacing` à 0 partout.

### Retour au toucher (`CadranTheme.kt`)

- `CadranHighlight` (`IndicationNodeFactory`) : voile de la couleur du contenu (`LocalContentColor`), 10 % à l'appui et au
  focus clavier, 5 % au survol. Posé en `LocalIndication`, il sert à tout `clickable`, `selectable` ou `toggleable` sans
  indication explicite (lignes, pastilles de couleur, lignes à interrupteur), et aux boutons et actions de ligne Cadran.
- `Modifier.cadranPressDim(source)` : l'opacité tombe à 45 % pendant l'appui, d'un coup, et revient en 150 ms. Boutons, puces,
  segments et actions de ligne Cadran.

### Composants (`ui/theme/KairosComponents.kt`)

Les enveloppes gardent leur signature ; chacune lit `LocalKairosStyle` et, en Cadran :

- `KairosButton`, `KairosTonalButton`, `KairosOutlinedButton`, `KairosTextButton` : `CadranButton`, une `Row` cliquable
  (`Role.Button`, `minimumInteractiveComponentSize`, 36 dp × 58 dp au moins, forme de l'appelant : pilule par défaut, libellé en
  `labelLarge`), sans élévation ni onde, `cadranPressDim` et `CadranHighlight` dans la forme. Couleurs
  (`cadranButtonColors`) : plein → `primary` / `onPrimary` ; tonal → `secondaryContainer` / `onSecondaryContainer` ; secondaire →
  `surfaceContainerHighest` / `onSurface`, sans le contour par défaut ; texte → transparent / `primary`. Des `colors` passées par
  l'appelant (différentes des valeurs par défaut de Material, par exemple un bouton de suppression en `error`) sont respectées,
  un contour choisi aussi. Désactivé : contenu `onSurface` à 38 %, fond `onSurface` à 12 % (sauf un fond transparent).
- `KairosFilterChip` : la `FilterChip` de Material, pilule (si l'appelant garde la forme par défaut), sans le contour par défaut,
  conteneur `surfaceContainerHighest`, libellé `onSurface`, choisie en `secondaryContainer` (défaut de Material), avec
  `cadranPressDim`.
- `KairosSegmentedRow` / `KairosSegmentedButton` : `CadranSegmentedRow`, une `Row` de 48 dp de haut au moins (cible tactile),
  segments égaux (`width(IntrinsicSize.Min)`, `weight(1f)`), qui dessine un rail `surfaceContainerHighest` de 32 dp arrondi à
  9 dp ; `CadranSegment` : `Role.RadioButton`, 48 dp de large au moins, pastille choisie de 28 dp arrondie à 7 dp
  (`surfaceContainerLowest` en clair, `outline` à 50 % en sombre, soit le `#636366` d'iOS), libellé `labelLarge` en
  `onSurface`, le choisi en gras, `cadranPressDim`, contour d'accent de 2 dp au focus clavier. `shape`, `contentPadding` et
  `icon` sont ignorés (aucune coche).
- `KairosRowIconButton` : une `Box` de 40 dp ronde, `Role.Button`, cible de 48 dp, `cadranPressDim` et `CadranHighlight`, icône
  de 20 dp à la couleur du contenu (ou `tint`), à 38 % désactivée.
- `KairosSwitch(checked)` (nouveau, remplace `Switch` dans `SwitchRow`) : en Material, le `Switch` de Material 3 ; en Cadran, un
  rail de 51 × 31 dp (`primary` allumé, `surfaceContainerHighest` éteint), un rond blanc de 27 dp à 2 dp du bord avec une ombre
  de 2 dp, couleur et position animées en 200 ms. Affichage seulement : la ligne porte le toucher et le rôle `Switch`.
- `KairosOutlinedCard(modifier, shape, content)` et `KairosOutlinedCard(onClick, modifier, shape, content)` (nouveaux,
  remplacent `OutlinedCard` partout) : en Material, l'`OutlinedCard` de Material 3 ; en Cadran, une `Card` pleine
  `surfaceContainerLow`, sans contour ; la cliquable est découpée à sa forme et `clickable` (`Role.Button`, voile
  `CadranHighlight`).

### Navigation (`navigation/AppShell.kt`)

- En Cadran, `NavigationBarItem` et `NavigationRailItem` prennent `cadranBarItemColors` / `cadranRailItemColors` : pastille
  transparente, icône et libellé actifs en `primary`, inactifs en `onSurfaceVariant` ; la barre basse n'a pas d'élévation tonale
  et un `HorizontalDivider` `outlineVariant` au-dessus ; la `TopAppBar` a le fond `background`. Le titre en gras vient de la
  typographie (`titleLarge`).
- La barre horizontale compacte (bureau, web étroit) passe par `KairosFilterChip` : rien de plus.

### Carte Apparence (`settings/AppearanceCard.kt`)

- `AppearanceCard(style, onStyleChange, …)` : en tête, avant l'interrupteur « Thème sombre » : libellé `ui_style`,
  `KairosSegmentedRow` pleine largeur à deux segments `ui_style_material` et `ui_style_cadran`, aide `ui_style_help` ;
  `SettingsScreen` lui passe la valeur du formulaire `uiStyle` et l'écrit par `onChange("uiStyle", …)`. Chaînes dans les six
  langues ; « Material » et « Cadran » sont des noms, identiques partout.

### Catalogue, captures

- Carte `ui_style` (chapitre Réglages, icône `Palette`, `since` 4.3.0, toutes plateformes, ouvre la carte Apparence) ;
  `README_FEATURES` : « Cadran style » ; puce et ligne du tableau « Feature list » des deux README, ligne « Style » du tableau
  des réglages.
- `--self-test` : captures `cadran`, `cadran-full`, `cadran-narrow`, `cadran-chrono`, `cadran-dark`, `cadran-dark-narrow`,
  `cadran-home`, `cadran-week`, `cadran-stats`, `cadran-notes`, `cadran-settings`, `cadran-settings-general` et
  `cadran-settings-general-dark` (bases d'exemple réglées en Cadran). Les captures des magasins restent en Material.

### Décisions et pièges tracés

- **Rouvrir « Material 3 uniquement »** (`CLAUDE.md`, `DESIGN_SYSTEM.md`) : décision du propriétaire du 2026-11-20. Material reste
  le style par défaut et la référence de la charte ; Cadran en est une variante qui garde ses règles d'usage de la couleur, sa
  densité et ses cibles de 48 dp. Ce qui change est limité au thème, aux enveloppes de `KairosComponents` et à la coquille de
  navigation.
- **Accent dérivé de la graine, pas le bleu d'iOS fixe** (choix du propriétaire) : les pastilles et la couleur libre gardent leur
  sens dans les deux styles ; l'océan au ton 45 est proche du bleu d'iOS. Ton fixe (45 / 70) plutôt que celui de la graine :
  il garantit 4,5:1 quelle que soit la graine (le miel est clair, l'ardoise sombre) ; le bleu d'iOS (ton ~52) n'atteint que 4:1 sur
  blanc. En sombre, le texte sur l'accent est foncé (ton 10), comme en Material : un blanc n'y atteindrait pas 4,5:1.
- **Rouge, vert et violet d'iOS, aux tons lisibles** : les valeurs d'iOS (`#FF3B30`, `#34C759`) sont trop claires pour un texte de
  14 sp sur blanc (3,5:1 et 2,2:1). On garde leur teinte et leur chroma, au même ton que l'accent. Conteneurs à chroma plafonnée
  (32) : en pleine chroma, le conteneur sombre du violet rendait les hachures du deep work criardes.
- **Gris fixes, pas dérivés de la graine** : le gris neutre d'iOS est ce qui distingue Cadran de Material (dont les surfaces sont
  teintées par la graine). Le texte secondaire prend une variante lisible (`#636366` / `#AEAEB2`), pas le `secondaryLabel` d'iOS
  (60 % d'opacité, 3,5:1), parce qu'il doit tenir 4,5:1 jusque sur les remplissages (badges). Contour clair `#8A8A8E` plutôt que
  le `#8E8E93` d'iOS : 3:1 sur la page grise.
- **Page grise, blocs blancs** : Kairos peint la page en `background` / `surface` et ses blocs (cartes des Réglages, lignes de
  tâche, menus, barres, dialogues) en `surfaceContainer*` ; en Material les blocs sont plus foncés que la page, en Cadran ils sont
  blancs sur une page grise (la liste groupée d'iOS). `surface` reste la couleur de la page : ce qui l'emploie (ligne d'une tâche
  bloquée, barre du haut, rail) se fond dans la page dans les deux styles. Écarté : `surface` blanche, qui rendait la barre du
  haut, le rail et les tâches bloquées blancs comme les blocs.
- **`OutlinedCard` enveloppée** : en Cadran, une carte à contour grise sur la page grise ne ressemblait à rien d'iOS ; les 21 appels
  passent par `KairosOutlinedCard` (carte pleine blanche, sans contour), gardée par `ComponentDensityTest` comme `Switch`.
- **Onde neutre gardée pour les composants Material restants** (menus, cases à cocher, onglets, boutons-icônes des barres) :
  sans elle (`LocalRippleConfiguration` à `null`), ils n'auraient plus aucun retour au toucher, au survol ni au focus clavier.
  Les composants les plus fréquents (boutons, puces, segments, actions de ligne, lignes cliquables, cartes) ont le retour d'iOS.
- **Pâlir pour un bouton, voile pour une ligne** : c'est la distinction d'iOS (bouton « highlighted » contre cellule sélectionnée).
  Le voile d'une ligne est un rectangle sur ses bornes ; un bouton le reçoit découpé à sa forme, et pâlit en plus.
- **Interrupteur allumé à la couleur d'accent, pas au vert d'iOS** : le vert est réservé au « fait » (charte). Le rond reste
  blanc en sombre comme en clair (iOS) : `Color.White`, aucun rôle du schéma n'étant blanc dans les deux thèmes ; il porte une
  ombre de 2 dp (élément flottant sur son rail, pas une carte).
- **Bouton secondaire gris plein, libellé `onSurface`** : le « bordered » gris d'iOS ; un texte d'accent sur ce gris (`#E5E5EA`)
  tomberait à 4,3:1. **Bouton tonal** : `secondaryContainer` comme en Material ; une teinte d'accent translucide tombait sous
  4,5:1 sur la page grise.
- **Mêmes tailles de texte que Material** : la densité de la charte (14 sp) vaut pour les deux styles ; seules les graisses et
  l'espacement des lettres changent. Un changement de taille ferait déborder des écrans vérifiés à 360 dp.
- **Changer de style ne recrée pas l'écran** : `KairosTheme` garde la même arborescence dans les deux styles, seuls les
  `CompositionLocal` changent.
- **Images exportées des Stats en Material** : `LocalLightColorScheme` reste le schéma Material clair, comme pour le thème sombre ;
  une image collée dans un rapport ne dépend pas du style de l'appareil.
