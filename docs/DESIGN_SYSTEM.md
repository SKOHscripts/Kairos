# Design system Kairos — Material Design 3, thème « océan »

Charte visuelle de l'application, construite sur **Material Design 3** (MD3,
https://m3.material.io) et rendue par les composants **Material 3 de Compose
Multiplatform**, les mêmes sur Android, le bureau et le web. Tout nouvel écran
ou composant réutilise ces rôles et ces composants plutôt que d'en réinventer :
chercher d'abord le composant Material 3 correspondant.

Mise en œuvre : `kmp/ui/.../theme/` (`KairosColors.kt` généré, `KairosTheme.kt`),
`kmp/ui/.../icons/` (`KairosIcons.kt` généré, `KairosLogo.kt`), polices dans
`kmp/ui/src/commonMain/composeResources/font/`. Détail technique :
`docs/spec/navigation-theme.md` ; composants de chaque écran : sa spec de
domaine.

## Historique de la décision

- **Charte précédente** (« sobre & professionnelle », ardoise + accent bleu
  `#2F6FED`, IBM Plex Sans, navigation horizontale) : remplacée en 2026-09 à la
  demande de l'utilisateur, qui la jugeait trop marquée par un style « généré »
  et voulait un design system complet, pérenne, cohérent sur Windows, Linux et
  Android.
- **Trois pistes comparées** (Carbon d'IBM, MD3, Fluent 2), puis maquettes
  Carbon/MD3 : **MD3 retenu** (barre de navigation basse, puces natives sur
  Android ; composants tous documentés ; palette dérivée d'une seule couleur,
  donc un thème sombre générable plus tard).
- **Graine de couleur** : le terracotta du logo (`#D9713C`) jugé trop orange ;
  **miel `#C28417`** retenu (lumière de fin de journée sur le cadran solaire :
  chaud sans être orange, et 50° de teinte d'écart avec le rouge d'erreur — le
  terracotta n'en avait que 18°, P0 et primaire se confondaient). Le logo suivait
  la graine (§ Logo) ; plus depuis le 2026-10-27 (voir la décision suivante).
- **Graine par défaut : l'océan** (changé le 2026-10-27 : l'océan `#2F6FED`
  remplace le miel `#C28417` comme graine par défaut). Le miel reste une couleur
  au choix (Réglages → Général → Apparence) et la couleur du logo, qui garde
  ses tons et contraste désormais avec l'interface (§ Logo). Les icônes
  d'installation posent le logo sur un fond uni océan profond ; les boutons de
  téléchargement et la page `site/` passent en palette océan. Les données
  existantes restées sur l'ancienne valeur par défaut passent à l'océan
  (`docs/spec/apparence.md` § Couleur par défaut).
- **Schéma « Tonal spot »** (défaut d'Android) préféré à « Fidelity » : moins
  saturé, conteneurs pêche/miel clair (avec la graine d'alors) plutôt que le
  terracotta plein.
- **Rail de navigation** sur grand écran : décision « pas de barre latérale »
  explicitement rouverte et tranchée par l'utilisateur en faveur du rail MD3
  (§ Navigation).
- **Kairos 3** (2026-10) : la charte, née en HTML/CSS, est
  transposée telle quelle en Compose ; les rôles, formes et règles d'usage de
  la couleur ne changent pas, seuls les composants deviennent ceux de
  Material 3.

## Couleurs

### Rôles MD3

Générés par l'algorithme officiel (material-color-utilities, portage Python
`materialyoucolor`, `SchemeTonalSpot`, spec 2021, contraste standard) depuis la
graine **`#2F6FED`** (l'océan), par `kmp/tools/make_theme.py`, dans `KairosColors.kt`
(`lightColorScheme`, 36 rôles). **Thème sombre** (issue #72, 3.7.0) : le même
outil génère `KairosDarkColors` (`darkColorScheme`, mêmes 36 rôles, `isDark`)
depuis la même graine ; Kairos suit le système, un interrupteur à deux états
choisit l'autre thème (`docs/spec/apparence.md` § Thème sombre). Pour changer de
couleur : changer la graine et regénérer tout le schéma, jamais retoucher un
rôle isolé.

L'océan est la graine **par défaut** : dans Réglages → Général → Apparence, chacun peut en
choisir une autre (graines proposées, le miel compris, couleur libre, couleurs
du système sur Android 12 et plus). Tout le schéma en est alors recalculé par le
même algorithme, embarqué dans l'application ; les valeurs ci-dessous sont celles
de l'océan, les règles d'usage (« Où va la couleur ») valent pour toute graine. Le
logo, le rouge d'erreur et le vert « ok » ne changent pas
(`docs/spec/apparence.md`).

| Rôle | `MaterialTheme.colorScheme` | Valeur | Usage principal |
|---|---|---|---|
| Primaire | `primary` / `onPrimary` | `#485D92` / `#FFFFFF` | bouton plein, score WSJF, liens, focus |
| Conteneur primaire | `primaryContainer` / `onPrimaryContainer` | `#DAE2FF` / `#304578` | carte « Maintenant », chrono en cours |
| Secondaire | `secondary` | `#585E71` | contour du jour courant (vue Semaine) |
| Conteneur secondaire | `secondaryContainer` / `onSecondaryContainer` | `#DCE2F9` / `#404659` | sélection : destination active, puces et pastilles choisies, créneaux de travail de la frise |
| Conteneur tertiaire | `tertiaryContainer` / `onTertiaryContainer` (trait `tertiary`) | `#FED7F9` / `#5A3D59` (`#735472`) | deep work, uniquement |
| Erreur | `error` / `onError` | `#BA1A1A` / `#FFFFFF` | texte d'erreur, liseré critique |
| Conteneur d'erreur | `errorContainer` / `onErrorContainer` | `#FFDAD6` / `#93000A` | P0, badges d'erreur, bandeau d'échec |
| Surface | `surface` / `onSurface` | `#FAF8FF` / `#1A1B21` | fond, cartes à contour, texte |
| Conteneurs de surface | `surfaceContainerLowest` … `Highest` | `#FFFFFF`, `#F4F3FA`, `#EEEDF4`, `#E8E7EF`, `#E2E2E9` | cartes « filled », lignes de tâche, badges neutres, dialogue |
| Texte secondaire | `onSurfaceVariant` | `#44464F` | libellés, aides, métadonnées |
| Contours | `outline` / `outlineVariant` | `#757780` / `#C5C6D0` | champs, boutons à contour / cartes, séparateurs |
| Surface inverse | `inverseSurface` / `inverseOnSurface` / `inversePrimary` | `#2F3036` / `#F1F0F7` / `#B1C5FF` | carte « En ce moment », snackbar d'alerte |

Contrastes texte/fond (WCAG 2.1) : 6,4:1 et plus pour tous les couples
ci-dessus, au-delà du minimum AA (4,5:1) ; voir `docs/spec/accessibilite.md`.

**Jamais de `Color(0x…)` dans un composant** : une couleur vient de
`MaterialTheme.colorScheme` ou de `LocalKairosExtraColors`. Seules exceptions :
le logo (couleurs fixes, § Logo), le schéma généré lui-même et les pastilles de
la carte Apparence, qui montrent la graine elle-même.

### Couleur personnalisée « fait / ok »

`LocalKairosExtraColors` : `ok` `#36693D`, `okContainer` `#B7F1B8`,
`onOkContainer` `#1D5127` (palette tonale teinte 150°, chroma 36, mêmes tons
que les rôles d'erreur). En thème sombre, comme l'erreur sombre : `ok`
`#9CD49F` (ton 80), `okContainer` `#1D5127` et `onOkContainer` `#B7F1B8`
(conteneur et texte échangés). **Non harmonisée** vers la graine : harmonisée, elle
virait à l'olive, trop proche de la tertiaire (la sauge de la graine miel d'alors) ;
la règle est gardée avec l'océan : le vert « ok » est le même quelle que soit la
graine.

### Pas d'ambre d'avertissement (décision)

Avec la graine miel d'origine, un ambre d'avertissement (`#755B00`/`#FFDF90`)
était **indiscernable du miel primaire** ; harmonisé vers la graine, il devenait
même identique au conteneur primaire. Le miel n'étant plus la graine par défaut
(2026-10-27), l'ambre reste évité : le miel est toujours la couleur du logo et
une graine au choix, et un état ne tient jamais à une teinte seule. Les états « à surveiller » sont donc rendus **par la forme** : badge
à contour (`outline`) + icône `Warning` (`WarnBadge`), tuile de statistique à
contour et icône, bandeau de dégradation neutre.

### Où va la couleur (et nulle part ailleurs)

1. **Un seul bloc teinté par écran** : la carte « Maintenant »
   (`primaryContainer`) ; sur la page de téléchargement, la carte du système
   du visiteur.
2. **Le primaire plein** (`Button`) est réservé à l'action principale de chaque
   zone : « Ajouter », « Fait » (dans « Maintenant »), « Enregistrer »,
   « → Tâche », « Capturer ».
3. **Le score WSJF** est un chiffre en couleur primaire, sans pastille pleine
   (il ouvre « Pourquoi à cette place ? »).
4. **Le rouge** ne sert qu'à P0, aux erreurs et aux dépassements (badge
   d'erreur, liseré d'une ligne P0, temps au-delà de l'estimé), toujours
   accompagné d'un texte ou d'une icône. P1/P2 restent neutres.
5. **La tertiaire** (mauve rosé avec l'océan ; sauge avec le miel) ne sert qu'au deep work (badge, blocs de la
   frise).
6. **Le vert « ok »** marque le fait (badge, coche, barres « fait », rail du
   temps chronométré).
7. **Les bandeaux de dégradation** (surcharge de priorité, mise à jour
   disponible, données web non liées à un fichier) sont neutres
   (`surfaceContainerHigh` + icône) : une dégradation n'est pas un danger. Le
   conteneur d'erreur est réservé aux vrais échecs (réglages refusés, import
   impossible).
8. **La sélection** (destination active, volets de capture, pastilles
   choisies, créneaux de travail) utilise `secondaryContainer`.
9. **La surface inverse** (sombre en thème clair, claire en thème sombre) est
   réservée à la carte « En ce moment » et à la snackbar d'alerte du chrono.

### Badges (pilules)

| Rôle | Fond | Texte |
|---|---|---|
| Neutre, tag, projet | `surfaceContainerHighest` | `onSurfaceVariant` |
| Priorité P1, P2 | `surfaceContainerHighest` | `onSurface`, gras |
| Priorité P0 | `errorContainer` | `onErrorContainer` |
| Score WSJF | transparent | `primary`, gras |
| Chrono en cours | `primaryContainer` | `onPrimaryContainer` |
| Fait / ok | `okContainer` | `onOkContainer` |
| Deep work | `tertiaryContainer` | `onTertiaryContainer` |
| Critique / erreur | `errorContainer` | `onErrorContainer` |
| À surveiller | transparent, contour `outline`, icône `Warning` | `onSurface` |

## Typographie

- **Roboto** 400/500/700, la police de MD3, **embarquée** en TTF dans
  `composeResources/font/` (licence OFL, `kmp/licenses/Roboto-OFL.txt`) :
  **aucune police distante**, l'application est entièrement hors ligne. La
  page de téléchargement (`site/`) sert les mêmes fichiers.
- Échelle typographique MD3 par défaut (`Typography` de Material 3) en
  Roboto ; `titleLarge` 22/28 (titre de page, « À faire maintenant »),
  `titleMedium` pour les titres de carte et de section, `bodyMedium` 14 sp
  pour le corps, `bodySmall` pour les aides, `labelLarge` pour les boutons,
  `headlineSmall` pour les chiffres clés, `displaySmall` pour le minuteur
  « En ce moment ».
- Aucun italique décoratif, aucune seconde police.
- Densité d'information : corps 14 sp ; ne pas l'augmenter ni la réduire lors
  de futurs ajouts.

## Icônes

**Material Symbols** (style *Outlined*, graisse 400), en `ImageVector`
générés dans `KairosIcons.kt` par `kmp/tools/make_icons.py` depuis le paquet
npm `@material-symbols/svg-400` (Apache 2.0) : ni police d'icônes, ni requête
réseau. **Ajouter une icône dans le script, jamais à la main.** Variante pleine
(`…Filled`) pour la destination active de la navigation (convention MD3).

## Forme & élévation

- Formes MD3 (`KairosShapes`) : `extraSmall` 4 dp (champs, menu « Pourquoi »,
  snackbar), `small` 8 dp (puces et pastilles, entrées de la frise), `medium`
  12 dp (cartes, lignes de tâche, bandeaux), `large` 16 dp (carte « En ce
  moment »), `extraLarge` 28 dp (dialogues) ; boutons, badges et indicateurs de
  navigation en pilule.
- Cartes : `OutlinedCard` (surface + contour `outlineVariant`) ou `Card`
  « filled » (`surfaceContainerLow`, élévation 0) pour la capture, les lignes
  de tâche et de note, les tuiles de statistiques et les **cartes de
  section des Réglages**. Pas de contour dans un contour : un bloc à
  contour ne contient pas d'autre bloc à contour (ses champs exceptés).
- **Élévation** : une carte ne porte **jamais d'ombre**, son plan se lit par la
  surface tonale. Seuls les éléments qui flottent au-dessus du contenu en ont
  une, celle que Material 3 leur donne : dialogues, snackbar, menus.
- **Couches d'état** : celles des composants Material 3 (survol, focus, appui)
  sur tout élément interactif ; un élément cliquable maison passe par
  `clickable` avec l'indication par défaut.
- Liseré critique d'une ligne de tâche P0 : bord gauche rouge de 3 dp (`error`),
  jamais de remplissage.
- **Tâche bloquée** : fond `surface` et contour, titre atténué par sa couleur —
  **jamais d'opacité** (décision ancienne, `docs/spec/vue-jour.md`).
- Dégradés : aucun, sauf les hachures des créneaux deep work réservés de la
  frise.

## Boutons & champs

- Hiérarchie MD3 : bouton plein (action principale), à contour, tonal, de
  texte (action tertiaire, « Décaler », « Plus tard »), bouton-icône pour
  les actions de ligne (chrono, décaler, modifier) ; destruction en couleur
  d'erreur, avec confirmation.
- **Densité** (`docs/spec/densite.md`) : tout passe par les enveloppes
  `Kairos*` de `theme/KairosComponents.kt`, jamais par les composants
  Material 3 directement (test `ComponentDensityTest`). Hauteurs
  **dessinées** : bouton 36 dp (marges latérales 16 dp, 12 dp côté icône),
  puce 32 dp, segment 40 dp, bouton-icône de ligne 40 dp avec icône de
  20 dp ; **aire tactile 48 dp** pour tous (Material 3 l'agrandit autour du
  dessin). Espacement : `KairosSpacing` 4 / 8 / 12 / 16 / 24 dp (liés /
  groupe / bloc / marge / section).
- Champs `OutlinedTextField` (rayon 4 dp) ; menus déroulants
  `ExposedDropdownMenuBox` ; choix exclusifs en rangée segmentée
  (`KairosSegmentedRow`) ou en puces (`KairosFilterChip`) ; interrupteurs
  dont toute la ligne se touche. Dans les Réglages, un champ numérique
  s'arrête à 320 dp.

## Logo

Mark « cadran solaire » inchangé dans son dessin (cercle + un secteur + point
pivot) et dans ses couleurs : quatre tons de la palette du **miel**, qui
s'accordaient à l'interface tant que le miel était la graine du thème. Depuis le
2026-10-27 la graine par défaut est l'océan : le logo **garde son miel**, couleurs
fixes (`KairosLogo.kt`, jamais teintées), et contraste avec l'interface.

| Pièce | Ton (palette miel) | Valeur |
|---|---|---|
| Cadran | miel, ton 95 | `#FFEEDC` |
| Anneau | miel, ton 85 | `#FFCC85` |
| Secteur | le miel (ton 60) | `#C28417` |
| Axe | neutre, ton 15 | `#2B251C` |

```html
<svg width="34" height="34" viewBox="0 0 40 40">
  <circle cx="20" cy="20" r="18.5" fill="#FFEEDC"/>
  <circle cx="20" cy="20" r="18.5" fill="none" stroke="#FFCC85" stroke-width="1.6"/>
  <path d="M20 20 L20 4 A16 16 0 0 1 35.76 17.22 Z" fill="#C28417"/>
  <circle cx="20" cy="20" r="2.6" fill="#2B251C"/>
</svg>
```

Repris par `KairosLogo.kt` (affiché en `Image`, jamais teinté), les icônes
d'application et des fiches générées par `kmp/tools/make_app_icons.py`
(bureau `.ico`/`.icns`/`.png`, web, Fastlane, page `site/`) et les ressources
Android (`colors.xml`, `mipmap-anydpi/ic_launcher.xml`,
`drawable/ic_launcher_foreground.xml`, écran de démarrage). Toute évolution
passe par tous ces endroits.

### Icônes d'installation

Le logo, inchangé, posé sur un **fond uni océan profond `#1C2F59`** (ton 20 de la
palette primaire de la graine océan), généré par `make_app_icons.py`
(`BACKGROUND`) : le cadran occupe 64 % du côté (`LOGO_RATIO`). Coins arrondis
(22,5 % du côté) pour les icônes de bureau (`.png`, `.ico`, `.icns`), la page
`site/` et les favicons ; **carré plein** pour les fiches Fastlane et les icônes
web 192 et 512 (le système ou le magasin les masque). Android (icône adaptative) :
`ic_launcher_background` = `#1C2F59`, le cadran crème est dessiné dans le premier
plan, `kairos_bg` (fond des fenêtres de démarrage) = `#FAF8FF` en clair,
`#121318` en sombre.

### Page de téléchargement et boutons

`site/style.css` reprend les rôles du schéma clair généré (variables `--md-*`).
Les boutons de téléchargement (`site/badges/`, `kmp/tools/make_badges.py`) sont
en **tonal clair** : fond `#DAE2FF` (conteneur primaire), texte et icône
`#1C2F59`, en pilule ; ils se lisent sur fond clair comme sur le fond sombre de
GitHub.

## Navigation

Cinq destinations (Notes, Jour, Semaine, Statistiques, Réglages), trois formes
selon la fenêtre et la plateforme (`NavigationLayout`) :

- **Fenêtre de 600 dp ou plus** : **rail de navigation** MD3 (`NavigationRail`)
  à gauche, logo + « Kairos » en tête ; destination active en
  `secondaryContainer` + icône pleine.
- **Moins de 600 dp, Android** : **barre de navigation basse** MD3
  (`NavigationBar`).
- **Moins de 600 dp, bureau et web** : barre d'application puis une rangée de
  puces en haut, **jamais** de barre basse : une fenêtre ou un navigateur
  simplement rétrécis n'en affichent pas.

Au-dessus du contenu, la **barre d'application** (`TopAppBar`) porte le titre de
page en `titleLarge`.

## Composants récurrents

Le détail de chaque écran vit dans sa spec (`docs/spec/vue-jour.md`,
`vue-semaine.md`, `notes-capture.md`, `statistiques.md`, `reglages.md`) ; les
invariants visuels à garder :

- **Capture** toujours visible en tête de la vue Jour et des Notes, jamais
  repliée : capturer ne coûte jamais un geste de plus.
- **« À traiter »** jamais masquée ; qualification en ligne par pastilles
  (priorité, points), sélection en `secondaryContainer` + coche.
- **Ligne de tâche** : colonnes stables d'une ligne à l'autre
  `[coche] [corps] [priorité/points] [actions]` ; seul le corps s'étire et
  empile titre, étiquettes et extrait ; un badge de longueur imprévisible va
  dans les étiquettes, jamais dans la colonne priorité/points ; en largeur
  étroite, priorité et points passent sous le corps.
- **« Pourquoi à cette place ? »** : menu MD3 ouvert depuis le score.
- **Dialogue d'édition** : dialogue MD3 (`extraLarge`), essentiels visibles,
  options avancées repliées.
- **Bandeaux** neutres (`surfaceContainerHigh` + icône) ; **alertes du chrono**
  en snackbar sur surface inverse.

## Contraintes transverses

- Composants Material 3 de Compose uniquement, dépendances libres ; aucune
  bibliothèque de composants tierce.
- Cibles tactiles ≥ 48 dp pour les contrôles à un toucher
  (`docs/spec/accessibilite.md`) ; aucun défilement horizontal sur un
  téléphone (~360 dp de large), vérifié sur les captures des magasins.
- Aucun texte en dur : français et anglais (`docs/spec/i18n.md`).
