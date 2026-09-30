---
name: kairos-ecran
description: Checklist pour ajouter ou modifier un écran, une carte ou un composant Compose de Kairos (kmp/ui) conforme au design system « miel » Material 3 - couleurs par rôles, formes, icônes générées, textes FR/EN, accessibilité 48 dp, 360 dp sans débordement, navigation, captures hors écran et captures des magasins. À utiliser pour toute interface nouvelle ou modifiée, y compris les écrans de l'espace Équipe.
---

# Ajouter ou modifier un écran Kairos

Références : `CLAUDE.md` (résumé de la charte), `docs/DESIGN_SYSTEM.md`
(charte complète), `docs/spec/navigation-theme.md` (mise en œuvre Compose),
`docs/spec/accessibilite.md`, `docs/spec/i18n.md`. Commencer par la skill
`kairos-spec` : l'écran doit d'abord être décrit dans la spec de son
domaine.

## 1. Composant Material 3 d'abord

Chercher le composant M3 de Compose avant d'en inventer un (`ListItem`,
`OutlinedCard`, `FilterChip`, `SegmentedButton`, `Slider`, `DatePicker`,
`DropdownMenu`, `AlertDialog`…). Réutiliser les briques existantes avant
d'en écrire : `StatTile`, `Panel`, `BarRow` (`ui/stats/StatsScreen.kt`),
`TaskRow` (`ui/day/TaskRow.kt`), `Capture`, `SettingInput`, `SwitchRow`
(`ui/settings/SettingsFormCards.kt`).

## 2. Couleur

- Uniquement `MaterialTheme.colorScheme.*` et `LocalKairosExtraColors` ;
  **jamais** `Color(0x…)` dans un composant. Ne jamais supposer le miel :
  l'utilisateur peut changer la graine.
- Un seul bloc teinté (`primaryContainer`) par écran ; primaire plein pour
  l'action principale d'une zone ; rouge seulement pour P0, erreurs et
  dépassements ; tertiaire seulement pour le deep work ; « ok » en couleur
  personnalisée ; sélection en `secondaryContainer`.
- **Pas d'ambre** : un état « à surveiller » = contour `outline` + icône
  `Warning`, jamais une teinte.
- Aucune information portée par la couleur seule : toujours un texte ou une
  icône.

## 3. Forme, typographie, icônes

- Formes : champs 4 dp, chips 8 dp, cartes et lignes 12 dp, grands blocs
  16 dp, dialogue 28 dp, boutons en pilule. **Jamais d'ombre sur une
  carte.**
- Roboto via le thème, échelle MD3, corps 14 sp, pas d'italique décoratif.
- Icônes : ajouter l'entrée dans `ICONS` de `kmp/tools/make_icons.py`
  (`(nom_material_symbols, variante_pleine?)`), puis regénérer (commande en
  tête du script). **Jamais** d'édition à la main de `KairosIcons.kt`.
  Variante pleine pour une destination de navigation active.

## 4. Textes

- Aucun texte en dur : `stringResource(Res.string.…)`.
- Chaque chaîne dans `kmp/ui/src/commonMain/composeResources/values/strings.xml`
  **et** `values-en/strings.xml`, apostrophe typographique `’`.
  `StringsParityTest` échoue sinon.

## 5. Mise en page et accessibilité

- Largeur : vérifier 360 dp (téléphone) **sans débordement horizontal**
  et une largeur bureau (≥ 840 dp). Colonnes de contenu bornées (720 ou
  960 dp) et centrées comme les écrans existants.
- Navigation : rail ≥ 600 dp ; en dessous, barre basse **seulement sur
  Android**, barre haute ailleurs (`NavigationLayout`). Cinq destinations
  au plus par espace.
- Cibles tactiles ≥ 48 dp pour tout contrôle à un toucher ; un booléen est
  une ligne `toggleable` entière.
- Lecteur d'écran : `contentDescription` des icônes seules, `heading()` sur
  les titres de section, états annoncés (`stateDescription`).
- Pas de raccourci clavier annoncé sur Android (`LocalPlatform`).
- Glisser-déposer : jamais le seul chemin (proposer un menu équivalent).

## 6. Vérifier

```bash
cd kmp
./gradlew :core:jvmTest :data:jvmTest :ui:jvmTest :desktopApp:jvmTest
./gradlew :desktopApp:run --args=--self-test=/tmp/captures   # puis REGARDER les PNG
```

- Ouvrir les captures (outil Read sur les PNG) et vérifier : couleurs,
  alignements, 360 dp, textes FR et EN non tronqués.
- Écran visible sur téléphone et présent dans les captures des magasins :
  regarder puis régénérer si l'écran capturé change
  (`--store-screenshots=<dépôt>/fastlane/metadata/android`,
  `docs/spec/publication.md`).
- Web : tester la sortie compilée dans Chromium (`docs/spec/architecture.md`
  § Décisions).
- Ajouter un test d'interface pour le comportement principal de l'écran,
  sur le modèle de `desktopApp/src/jvmTest/.../M4ScreensUiTest.kt`
  (`androidx.compose.ui.test`, application complète sur une base en
  mémoire).

## 7. Tracer

Reporter dans la spec du domaine (partie 2 « Interface ») les composants
choisis et les décisions de mise en page ; nouvelles icônes dans
`navigation-theme.md` § Icônes.
