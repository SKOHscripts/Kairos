# Densité visuelle des composants (boutons, puces, champs, rythme)

_Rôle : affiner l'allure de l'interface sans toucher à l'accessibilité ni au
fond de la charte. Les composants Material 3 se dessinent à leur taille
nominale (boutons de 36 à 40 dp) avec une **zone tactile** de 48 dp autour ;
Kairos forçait 48 dp sur le composant lui-même, ce qui le faisait paraître
massif. Fichiers : `kmp/ui/.../theme/KairosComponents.kt` (boutons et puces
de Kairos), `theme/KairosSpacing.kt` (échelle d'espacement), les écrans qui
les emploient ; charte : [`docs/DESIGN_SYSTEM.md`](../DESIGN_SYSTEM.md)
§ Boutons & champs. Complète [`navigation-theme.md`](navigation-theme.md) et
[`accessibilite.md`](accessibilite.md)._

État : **implémentée le 2026-10-01** (passe « design », branche empilée sur
le jalon E6 de l'espace Équipe).

## 1. Besoin métier (cahier des charges)

### Objectif / problème

Sur les captures, les boutons (« Valider », « Démarrer le chrono », « Décaler »,
« Importer »…), les puces de priorité et de points, le sélecteur « Tâche /
Créneau » et les champs des Réglages paraissent grossiers : trop hauts, trop
larges, trop encadrés. La densité d'information de la charte (corps 14 sp)
n'est pas en cause ; ce sont les marges et les hauteurs forcées des
composants, et l'empilement de contours (cartes à contour contenant des
champs à contour).

### Comportement attendu (utilisateur)

- **Boutons plus fins** : un bouton plein, à contour, tonal ou de texte est
  dessiné sur **36 dp** de haut (au lieu de 48) avec des marges latérales de
  16 dp (12 dp côté icône) ; son libellé garde `labelLarge`. Son aire
  tactile reste de 48 dp de haut : on le touche aussi facilement
  qu'avant.
- **Puces de qualification** (priorité P0-P2, points 1-21, filtres) : 32 dp
  dessinés, aire tactile 48 dp ; la liste « À traiter » tient sur moins de
  hauteur.
- **Sélecteurs segmentés** (« Tâche / Créneau », espaces, périmètres) : 40 dp
  dessinés, aire tactile 48 dp.
- **Actions de ligne** (chrono, décaler, modifier) : icônes de 20 dp dans un
  bouton-icône de 40 dp (aire tactile 48 dp), pour alléger les lignes de
  tâche.
- **Réglages** : cartes de section **pleines** (sans contour), champs
  numériques à largeur bornée au lieu de s'étirer sur toute la ligne, aide
  collée à son champ.
- **Rythme vertical** : un espacement à plusieurs niveaux (liés / groupe /
  bloc / section) au lieu de 16 dp partout, pour que la hiérarchie se lise
  sans ajouter de place.
- Rien d'autre ne change : couleurs, formes, typographie, textes, navigation,
  comportement. Les raccourcis et les états (focus, survol, appui) restent
  ceux de Material 3.

### Critères de succès

- Aucune aire tactile en dessous de 48 dp (vérifiée par test sur les
  composants touchés : `assertTouchHeightIsEqualTo(48.dp)`), à 360 dp comme
  en fenêtre large.
- Aucun débordement horizontal à 360 dp sur les écrans touchés (captures
  `--self-test`, largeur étroite).
- Un bouton dessiné mesure 36 dp, une puce de qualification 32 dp, un
  sélecteur segmenté 40 dp (tests de mesure).
- Aucune nouvelle chaîne, aucun texte déplacé : `StringsParityTest` inchangé.
- Les écrans de l'espace Équipe suivent la même règle que les autres.
- Les captures des magasins sont régénérées (`--store-screenshots`) : tous
  les boutons y changent.

### Hors périmètre

- Couleurs, rôles, formes (4/8/12/16/28 dp), typographie et taille du corps
  (14 sp), logo, navigation (rail, barres).
- Densité différente selon la plateforme (« compact sur ordinateur ») :
  écartée, une seule règle pour Android, bureau et web.
- Thème sombre, animations.

## 2. Solution technique

### Composants (`theme/KairosComponents.kt`)

- Enveloppes minces des boutons Material 3 : `KairosButton`,
  `KairosOutlinedButton`, `KairosTonalButton`, `KairosTextButton`. Mêmes
  paramètres que les originaux (le contenu est un `RowScope`) ; elles posent
  `contentPadding` (16 dp latéral, 6 dp vertical ; 12 dp côté icône) et une
  hauteur minimale de **36 dp** (`ButtonDefaults.MinHeight` vaut 40 dp).
  Elles ne forcent **pas** 48 dp : la zone tactile vient de
  `minimumInteractiveComponentSize`, appliqué par Material 3, qui agrandit
  l'aire de touche sans agrandir le dessin.
- `KairosFilterChip` : `FilterChip` de 32 dp (`FilterChipDefaults.Height`),
  aire tactile 48 dp. `KairosSegmentedRow` et ses `KairosSegmentedButton` :
  40 dp, sans `heightIn(48)`.
- `KairosRowIconButton(icon, description, onClick)` : `IconButton` de 40 dp,
  icône de 20 dp, pour les actions de ligne. Les `IconButton` de navigation
  et de barre d'application gardent 24 dp.
- **Usage direct interdit** : `Button`, `OutlinedButton`, `FilledTonalButton`,
  `TextButton`, `FilterChip` et `SingleChoiceSegmentedButtonRow` de
  Material 3 ne s'emploient que dans `KairosComponents.kt` (test source
  `ComponentDensityTest`) ; sinon une 117e utilisation retomberait à la
  taille nominale sans que personne le voie.
- Les `heightIn(min = 48.dp)` posés **sur** un bouton, une puce ou un
  segment sont retirés (19) ; ceux posés sur une **ligne** entière qui se
  touche restent (5 : `disclosure`, case à cocher avec libellé, interrupteur,
  ligne d'absence, ligne d'échéance des prévisions) : c'est la cible
  tactile de la ligne, pas un composant à alléger.
- La hauteur dessinée vient de `heightIn(min = 36.dp)` posé par l'enveloppe :
  le `Surface` de Material 3 propage la contrainte minimale, donc son
  `defaultMinSize` de 40 dp ne joue plus (un test de mesure échoue à 41 dp).
  Un bouton dont le contenu commence par une icône reçoit
  `contentPadding = KairosButtonIconPadding` (12 dp côté icône) ; ceux dont
  l'icône est en fin de contenu (« Suivant », feuille de suggestion) gardent
  16 dp des deux côtés.
- `KairosSegmentedButton` est une extension du scope de la rangée
  (`SingleChoiceSegmentedButtonRowScope`), pour garder les poids et
  `itemShape` de Material 3.
- 132 appels directs remplacés (Button 21, OutlinedButton 29, TextButton 67,
  FilterChip 5, rangées segmentées 4, segments 6), 12 `KairosRowIconButton`
  (ligne de tâche, bloc, fiche membre, ⋮ du Backlog, du Suivi et des
  scénarios, suppression d'une note ou d'une modification). Restent en
  `IconButton` de 24 dp : la coche « fait » d'une ligne de tâche et les
  boutons de fermeture (feuilles, dialogues, À propos, chrono).
- `ComponentDensityTest` (ui) lit `src/commonMain/kotlin` depuis le
  répertoire du module, comme `StringsParityTest`, ignore commentaires et
  KDoc, et refuse les appels et imports directs des sept composants Material
  3 (qualifiés ou `material3.*` compris) hors `KairosComponents.kt` ;
  `ComponentDensityUiTest` (desktopApp) mesure 36 / 32 / 40 / 40 dp dessinés
  et 48 dp d'aire tactile.

### Espacement (`theme/KairosSpacing.kt`)

- `KairosSpacing` : `xs` 4 dp (étiquette/champ/aide), `s` 8 dp (éléments
  d'un groupe, puces entre elles), `m` 12 dp (blocs et cartes d'une liste),
  `l` 16 dp (marge intérieure d'une carte, marge d'écran), `xl` 24 dp (avant
  un titre de section).
- Dérivées pour les `LazyColumn` dont les lignes restent à `s` :
  `KairosListBlockExtra` (m − s) et `KairosListSectionExtra` (xl − s),
  ajoutées autour d'un bloc ou d'un titre de section.
- Appliqué aux colonnes de premier niveau des écrans (Jour, Notes, Semaine,
  Stats, Réglages, À propos, Backlog, Suivi, Équipe, Prévisions) : marge
  d'écran `l`, `m` entre blocs, `xl` avant un titre de section (Jour, Notes,
  Backlog, « Anciens membres », « Résultat » des Prévisions ; Semaine, Stats
  et Suivi n'ont pas de titre de section hors carte). Les listes de tâches
  gardent `s` entre lignes. Les marges intérieures de cartes restent à `l`.
  Laissés tels quels : espacements internes des composants (lignes de tâche,
  badges, barres, cartes « Maintenant » et « En ce moment » à 20 dp),
  dialogues et fiches formulaire (déjà sur l'échelle), espacements
  horizontaux.

### Réglages

- Cartes de section : `Card` « filled » (`surfaceContainerLow`, sans
  contour, élévation 0), marge intérieure `l`, titre en `titleMedium`.
- `SettingsCard`, `SettingsClickableCard` (« À propos et guide », fond du
  `ListItem` transparent) et `SettingsCardHeader` portent ces cartes pour les
  huit sections du formulaire et pour Apparence, Équipe, Mises à jour,
  Données et Raccourci.
- Champs numériques (entiers et décimaux) : `Modifier.widthIn(max =
  320.dp).fillMaxWidth()` (**dans cet ordre** : `fillMaxWidth().widthIn(max)`
  laisse la contrainte entrante l'emporter et le champ reste pleine largeur) ;
  l'aide (`supportingText`) suit la même largeur. Champs de texte libre
  (types, jours fériés, couleur libre), dates et interrupteurs gardent la
  pleine largeur. `SettingsLayoutUiTest` vérifie 360 et 1000 dp.
- Règle « pas de contour dans un contour », appliquée aux **blocs** (cartes,
  tuiles, panneaux), pas aux contrôles ni aux pastilles : une carte à contour
  ne contient pas d'autre bloc à contour ; dans une carte « filled », les
  sous-blocs sont « filled » plus clairs ou sans fond. Audit des
  `OutlinedCard` hors Réglages : un seul cas, la tuile « à surveiller » du
  panneau « Flux » des Statistiques, qui passe sans contour sur
  `surfaceContainerHigh` dans le panneau (`StatTile(nested = true)`, l'icône
  d'alerte reste). Laissés avec contour : badges et pastilles « à
  surveiller » (la charte impose forme et contour, pas la couleur seule),
  puces, boutons, champs, menus, et les blocs de la frise du Jour (épinglé,
  conflit, deep work), dont la bordure porte le signal « pas la couleur
  seule » ; passer la carte de la frise en « filled » reste possible.

### Décisions et alternatives écartées

- **Enveloppes plutôt que réglage de thème** : Material 3 pour Compose n'a pas
  de réglage global de la hauteur ou du remplissage des boutons ; seule une
  enveloppe change le défaut d'un coup, et le test d'usage direct la rend
  obligatoire.
- **Aire tactile conservée, dessin réduit** : c'est le modèle de Material 3
  (bouton de 40 dp dans 48 dp). La charte dit « cibles ≥ 48 dp pour les
  contrôles à un toucher », pas « composants de 48 dp ».
- **36 dp et non 40 dp** pour les boutons : 40 dp est la valeur nominale de
  Material 3, que Kairos avait déjà dépassée ; 36 dp rend l'interface plus fine
  tout en gardant le libellé de 14 sp lisible (marge verticale de 11 dp).
- **Pas de densité plus compacte sur ordinateur** : deux règles à maintenir
  et à capturer pour un gain faible ; une seule suffit (décision du
  2026-10-01).
- **Cartes de Réglages pleines plutôt qu'à contour** : les champs sont déjà
  encadrés ; un contour de carte autour est du bruit. Les cartes « filled »
  sont déjà admises par la charte.
- **Pas de glisser-ajuster ni de réglage « densité » dans les Réglages** :
  une préférence de plus pour un défaut qui doit simplement être bon.
- **Observé, non traité** : sur téléphone, le sélecteur d'espace Perso | Équipe
  de la barre d'application laisse une marge droite d'environ 10 dp, contre
  16 dp ailleurs ; antérieur à cette passe.

### Impacts sur les specs existantes (reportés)

- `docs/DESIGN_SYSTEM.md` § Boutons & champs et § Forme & élévation : hauteurs
  dessinées, aire tactile, enveloppes `Kairos*`, cartes de Réglages.
- `navigation-theme.md` : fichiers du thème (`KairosComponents.kt`,
  `KairosSpacing.kt`).
- `accessibilite.md` : « cibles de 48 dp » = aire tactile, pas hauteur
  dessinée.
- `reglages.md` : cartes pleines, largeur des champs numériques.
- Index `docs/spec/README.md`.
