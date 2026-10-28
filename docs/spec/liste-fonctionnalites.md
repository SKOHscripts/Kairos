# Liste complète des fonctionnalités

_Rôle : un écran « Liste de fonctionnalités », accessible depuis l'Accueil, qui montre **toutes** les fonctionnalités de Kairos
(et grossit à chaque version), et un tableau récapitulatif du README, tenus en phase avec le catalogue par des tests. Fichiers
couverts (3.13.0) : `kmp/ui/.../guide/FeatureCatalog.kt` (cartes, `README_FEATURES`), `kmp/ui/.../screens/FeatureListScreen.kt`
(nouveau), `ui/screens/HomeScreen.kt` (le bouton), `ui/navigation/Secondary.kt` et `AppShell.kt` (écran secondaire),
`kmp/core/.../text/SearchFold.kt` (recherche, pur), `kmp/tools/make_icons.py` et `KairosIcons` (icônes), `values*/strings.xml`
(cartes et écran), `README.md` et `README.fr.md` (section « Feature list » / « Liste des fonctionnalités »). Tests :
`FeatureCatalogTest`, `SearchFoldTest` (core), `FeatureListUiTest` (desktopApp), `GuideSpotUiTest` (positions lues dans le catalogue),
captures `desktop-features.png` et `desktop-features-narrow.png` de l'auto-test._

État : demandée le 2026-10-26 (issue #153), spécifiée le même jour, **implémentée le 2026-10-28** (sortira en 3.13.0) ; inventaire
fait le même jour (§ Inventaire).

## 1. Besoin métier (cahier des charges)

### Objectif / problème

La page d'accueil montre « Nouveau dans cette version » : seulement les cartes que la version courante ajoute. Qui a installé Kairos
il y a trois versions ne retrouve nulle part **tout** ce que l'outil sait faire : le hub « Découvrir Kairos » parcourt les cartes par
chapitre pour se guider, le README décrit les fonctionnalités par thème, mais il n'existe ni liste complète dans l'application, ni vue
d'ensemble dans le README. On veut une liste unique, complète, qui s'enrichit à chaque version sans rien oublier.

### Comportement attendu (utilisateur)

- **L'Accueil ne change pas**, sauf un bouton : « Nouveau dans cette version » reste tel quel (masquable, propre à la version). Sous
  lui, un bouton **« Liste de fonctionnalités »** (icône de liste) est **toujours là** : version sans nouveauté, « Nouveau »
  masqué, tout compris. Il ouvre un **écran secondaire plein écran** (comme les Réglages : barre du haut avec retour, un seul
  écran, tient sur 360 dp comme sur grand écran).
- **L'écran** liste toutes les fonctionnalités, **regroupées par chapitre** (les six chapitres du catalogue : Premiers pas, Planifier,
  Suivre, Organiser, Équipe, Réglages ; le chapitre Équipe seulement si l'espace Équipe est activé : rien d'équipe n'existe pour qui ne
  l'a jamais activé). Chaque chapitre a son titre, chaque entrée :
  - une **icône**, un **titre** et **une phrase** ;
  - « **depuis 3.8.0** » (la version qui l'a apportée ; « depuis 3.0.0 » pour les fonctionnalités d'origine) ;
  - une pastille de **plateforme** (« Android », « Bureau », « Web ») **seulement** si la fonctionnalité n'existe pas partout
    (widgets Android, calendriers du téléphone, raccourcis clavier…) ;
  - un toucher **ouvre l'écran ou le guide concerné**, comme les cartes d'aujourd'hui, et ferme la liste.
- **Recherche** : un champ en haut filtre par titre et par phrase, sans tenir compte des majuscules ni des accents (« reunion »
  trouve « Réunion »). Aucun résultat : « Aucune fonctionnalité ne correspond » et un bouton « Effacer ».
- **Filtre « Nouveau depuis… »** : un menu « Toutes les versions » (par défaut) ou une version (« depuis 3.8.0 »…) ; la liste ne montre
  alors que ce qui est arrivé **à partir** de cette version. Les versions proposées sont celles que le catalogue connaît.
- **Rien ne se mémorise** : la recherche et le filtre reviennent à zéro quand on ferme l'écran (pas de nouvelle donnée, pas de
  migration).
- **Hub « Découvrir Kairos » conservé** : il reste le parcours guidé par chapitre (première tâche, visites, démonstration) ; la liste est
  la vue à plat, avec recherche. Les deux lisent **le même catalogue** : une fonctionnalité n'est décrite qu'une fois.
- **README** (anglais et français) : la section « Features » / « Fonctionnalités » est **conservée** ; une section **« Feature list » /
  « Liste des fonctionnalités »** la complète par un **tableau récapitulatif** : chapitre, fonctionnalité, depuis, plateformes. Le
  tableau dit la même chose que la liste de l'application.

### Critères de succès

- Chaque fonctionnalité en gras de la section « Features » du README a **sa propre carte** (plus de carte partagée) et chaque carte a
  une entrée dans le README : le catalogue, la liste de l'application et le README ont **le même ensemble** (`FeatureCatalogTest`).
- Le tableau récapitulatif du README (anglais, puis français) porte exactement les cartes du catalogue, dans l'ordre, avec leur
  chapitre, leur version et leurs plateformes (`FeatureCatalogTest.readmeTableMatchesCatalog`).
- Aucune carte n'a une version postérieure à celle de l'application ; toute fonctionnalité nouvelle a une carte et une ligne du tableau
  (même test que ci-dessus : l'oubli fait échouer la CI).
- L'écran : le bouton est sur l'Accueil quelle que soit la version ; la liste groupe par chapitre ; Équipe n'apparaît pas sans l'espace
  Équipe ; la recherche sans accents et sans casse trouve « reunion » ; le filtre « depuis 3.10.0 » ne laisse que les cartes de la 3.10.0
  et après ; un toucher ouvre la cible de la carte (`FeatureListUiTest`, `SearchFoldTest`).
- 360 dp sans débordement horizontal, cibles de 48 dp, lecture TalkBack d'une entrée (« titre, phrase, depuis 3.8.0, Android
  seulement »), clair et sombre (captures de l'auto-test).
- Les textes sont dans les six langues (`StringsParityTest`) ; les icônes nouvelles passent par `make_icons.py`.

### Hors périmètre

- Filtre par plateforme (la pastille suffit), tri alphabétique, favoris, mémoire de la recherche.
- Une fiche détaillée par fonctionnalité (la carte renvoie à l'écran ou au guide, qui l'expliquent déjà).
- Un tableau du README **généré** par un script (choix du propriétaire : tenu à la main, vérifié par un test).
- Le contenu des fiches du magasin et de la page web : ils ne reprennent pas la liste.

## 2. Solution technique

### Catalogue (`FeatureCatalog`)

- **Une fonctionnalité = une carte.** `FeatureCard` gagne `platforms: Set<Platform> = Platform.entries.toSet()` (`Platform` : `ANDROID`,
  `DESKTOP`, `WEB`) et garde `id`, `chapter`, `icon`, `title`, `body`, `target`, `since`, `tour`. `README_FEATURES` associe chaque
  fonctionnalité du README à **sa** carte, **une à une** : le test passe de « chaque fonctionnalité a une carte » à « une carte par
  fonctionnalité, une fonctionnalité par carte » (clés et valeurs sans doublon), et ajoute le sens inverse : toute carte est dans le
  README.
- **Inventaire de départ** (état au 2026-10-26 ; refait le 2026-10-28, résultat au § Inventaire) : 50
  fonctionnalités du README pour 44 cartes, dont 13 fonctionnalités partagent une carte avec une autre ; **7 cartes n'ont aucune entrée du
  README** (`guide_memory`, `besthours_efficiency`, `week_move`, `stats_explore`, `holidays_slots`, `team_assign`,
  `settings_categories`). Le premier travail est de **tout récolter** :
  - créer la carte manquante de chacune des 13 fonctionnalités qui partagent aujourd'hui une carte (« Keyboard shortcuts » avec
    « One-line capture » ; « Subtasks » avec « Editable subtasks » ; « "Snooze" » avec « Recurrence » ; « Placement » et « Afternoon dip »
    avec « Score » ; « Working days and holidays » avec « Hours by day of the week » ; « Deep work » avec « Busy slots » ; « Phone
    calendars » avec « Calendar import » ; « Day » avec « Timeline » ; « Three alerts » avec « Timer per task » ; « Theme color » avec
    « Settings » ; « Your personal work counts » avec « Members » ; « Load » avec « Tracking ») ;
  - ajouter au README une fonctionnalité pour chacune des 7 cartes qui n'en ont pas (ou, si une carte n'est qu'un détail d'une autre,
    la fusionner : décision prise à l'inventaire et tracée ici) ;
  - ajouter les cartes des versions déjà livrées qui manquent (relire les specs et le README : la liste de l'inventaire est la source),
    et celles de la 3.12.0 (widgets Android, récapitulatif du jour) ;
  - donner à chaque carte son `since` exact (la version de livraison, tirée de `docs/ROADMAP.md` et des specs) et ses `platforms`.
- **Plateformes connues** : raccourcis clavier `DESKTOP`, `WEB` ; calendriers du téléphone et widgets `ANDROID` ; les autres : toutes
  (fichier lié : bureau, Android et web).
- **Icônes** : chaque carte a une icône **distincte** quand c'est possible ; celles qui manquent sont ajoutées dans `make_icons.py`
  (Material Symbols Outlined 400, jamais à la main) puis régénérées dans `KairosIcons` ; au plus une dizaine, listées à l'inventaire
  (candidates : `Keyboard`, `Snooze`, `Calculate`, `Checklist`, `Timeline`, `Smartphone`, `WbTwilight`).
- **Versions du filtre** : `FeatureCatalog.versions(): List<String>` = les `since` distincts réduits à `X.Y.0`, du plus récent au plus
  ancien. `FeatureCatalog.since(version): List<FeatureCard>` = les cartes dont `since` est **supérieur ou égal** (comparaison par
  `minorOf`, déjà utilisée par `newIn`). `newIn` reste inchangé (même minor).
- **Chapitres affichés** : `FeatureCatalog.chapters(teamEnabled)` existe déjà (Équipe seulement si l'espace est activé) ; la liste le réutilise.
- **Visites** : la visite de l'espace Perso reste la sélection `tour` hors Équipe ; celle de l'espace Équipe devient la sélection `tour`
  du chapitre Équipe (les six cartes d'origine), et non plus tout le chapitre, qui compte désormais neuf cartes.

### Inventaire (fait le 2026-10-28)

Résultat : **61 cartes pour 61 fonctionnalités du README**, une à une.

- **15 cartes créées** (`card_<id>_title|body`, six langues) pour les fonctionnalités qui partageaient une carte :
  `shortcuts` (Premiers pas, 3.0.0, bureau et web), `placement` et `afternoon_dip` (Planifier, 3.0.0), `holidays` (Planifier, 3.0.0),
  `timeline` et `alerts` (Suivre, 3.0.0), `subtasks_edit` (Organiser, 3.3.0), `snooze` et `deep_work` (Organiser, 3.0.0),
  `phone_calendars` (Organiser, 3.10.0, Android), `team_first_task` (Équipe, 3.8.0), `team_my_work` (Équipe, 3.3.0), `team_load`
  (Équipe, 3.1.0), `theme_color` (Réglages, 3.1.0), `web_version` (Réglages, 3.0.0, web).
- **9 fonctionnalités ajoutées au README** (anglais et français), dont les 7 cartes sans entrée, aucune n'étant un simple détail d'une
  autre (choix du propriétaire) : « Weekday slots skip holidays » (`holidays_slots`), « Move from the week » (`week_move`), « Statistics
  your way » (`stats_explore`), « Your most efficient hours » (`besthours_efficiency`), « Settings by category » (`settings_categories`),
  « The guide remembers » (`guide_memory`), « Assignment » (`team_assign`) ; plus « Team backlog » (`team_backlog`) et « Export and
  import » (`data`), qui existaient sans être en gras en tête de puce. Les puces qui réunissaient deux fonctionnalités sont scindées.
- **Textes ajustés** pour qu'une carte ne décrive plus qu'une fonctionnalité : `score` (titre « Score », sans le placement ni le creux),
  `slots` (« Créneaux occupés », sans le deep work), `recurrence` (titre « Récurrence », sans « Décaler » ni « Déplacer… »),
  `calendar_import` (sans les calendriers du téléphone).
- **Versions** (`docs/ROADMAP.md`, notes de version, dates des tags) : l'espace Équipe arrive en 3.1.0 (ses cartes passent de 3.0.0 à
  3.1.0), la couleur du thème aussi ; `recurrence` passe de 3.4.0 à 3.0.0 (la récurrence existe depuis la bascule ; la 3.4.0 n'en
  ajoutait que l'accompagnement).
- **Plateformes** : `shortcuts` bureau et web, `phone_calendars` et `widgets` Android, `web_version` web ; toutes les autres partout
  (le fichier lié existe sur les trois).
- **Icônes ajoutées** à `make_icons.py` (13, un peu plus que la dizaine prévue : une icône distincte par carte nouvelle et celle du
  bouton) : `Keyboard`, `Snooze`, `Calculate`, `Timeline`, `Mobile`, `WbTwilight`, `Checklist`, `Web`, `Speed`, `EventAvailable`,
  `CenterFocusStrong`, `Assignment`, `FormatListBulleted` ; les autres cartes nouvelles reprennent une icône existante
  (`NotificationsActive`, `Person`, `Palette`).

### Écran (`ui/screens/FeatureListScreen.kt`)

- `Secondary` gagne `FEATURES` (`enum class Secondary { SETTINGS, ABOUT, FEATURES }`) ; `AppShell` l'affiche comme les Réglages (barre du
  haut avec retour, `BackHandler`, restauration de l'écran d'avant). Le bouton de `HomeScreen` (`home_features_list`, `OutlinedButton` avec
  icône) appelle `onOpenFeatures` ; il est **après** le bloc « Nouveau » et **hors** de sa condition d'affichage.
- Contenu : un champ de recherche (`OutlinedTextField`, icône `Search`, étiquette « Rechercher une fonctionnalité ») et le menu du filtre
  (`Facet` de la vue Jour, un `ExposedDropdownMenu` étiqueté « Nouveau depuis », « Toutes les versions » ou « depuis X.Y.0 ») dans un
  `FlowRow`, puis une `LazyColumn` (`testTag("feature-list")`) : un titre de chapitre (`heading`) suivi de ses entrées en `OutlinedCard` >
  `ListItem` (icône en `primary`, titre, phrase, ligne de méta « depuis 3.8.0 · Android »). Un chapitre sans résultat est masqué ; sans
  aucun résultat, « Aucune fonctionnalité ne correspond » et « Effacer » (vide la recherche et le filtre). Colonne de 720 dp au plus,
  centrée (comme l'Accueil). L'écran est public (`FeatureListScreen(teamEnabled, onOpen)`) pour les captures de l'auto-test.
- **Recherche** (`core/text/SearchFold.kt`, pur) : `SearchFold.fold(texte)` met en minuscules et retire les accents (décomposition
  Unicode canonique puis suppression des marques ; une table à la main pour les lettres sans décomposition utile à nos six langues), et
  `SearchFold.matches(requête, texte)` vrai si chaque mot de la requête (séparés par des espaces) est contenu dans le texte plié. La
  recherche porte sur les textes **déjà résolus** (`stringResource`) : elle suit donc la langue affichée.
- **État** : `rememberSaveable` pour la requête et le filtre (survit à la rotation, repart à zéro à la fermeture) ; aucune écriture dans
  `Settings` ni dans la base.
- **Toucher** : `AppShell` ferme l'écran (`onCloseSecondary`) **puis** ouvre la cible de la carte (`openTarget`) : une cible Réglages
  rouvre ainsi les Réglages sur leur catégorie.
- **Accessibilité** : chaque entrée est une carte cliquable, donc un seul élément qui fusionne ses textes (titre, phrase, méta) ; la ligne
  de méta a une description parlée « depuis 3.8.0, Android seulement » (`features_platforms_only`), la pastille n'étant pas lue comme un
  simple « · Android » ; titres de chapitre en `heading` ; le filtre et la recherche ont leurs étiquettes ; aucune information portée par
  la seule couleur. Écarté : une description d'ensemble posée sur la carte en effaçant ses textes (`clearAndSetSemantics`), qui les
  rendait introuvables aux tests et doublait la lecture.

### README

- Section **« Feature list »** (`README.md`) et **« Liste des fonctionnalités »** (`README.fr.md`), après la section « Features » et avant
  « Settings » (resp. « Fonctionnalités » et « Réglages ») : une phrase d'introduction, puis un tableau
  `| Chapter | Feature | Since | Platforms |` (`| Chapitre | Fonctionnalité | Depuis | Plateformes |`), une ligne par carte, **dans l'ordre du
  catalogue**, titre = le titre de la carte dans la langue du README (`values/strings.xml`, `values-fr/strings.xml`), plateformes « All » /
  « Toutes » ou la liste. `FeatureCatalogTest.readmeTableMatchesCatalog` lit la section jusqu'au prochain `\n## ` et compare ligne à ligne
  (sans tenir compte des accents ni de la casse).
- La section « Features » garde ses puces en gras (`readmeFeaturesAreCovered`) ; le tableau n'en change pas la lecture (le test ne lit
  que les lignes qui commencent par `- **`).
- **Procédure à chaque version** (consignée dans `CLAUDE.md`, étape 4, et la skill `kairos-spec`, § 4) : une fonctionnalité visible de
  l'utilisateur = une carte (`since` = la version, `platforms`), ses textes dans les six langues, une puce du README (EN et FR), une ligne
  du tableau (EN et FR). Le test échoue sinon.

### Textes

- Nouveaux : `home_features_list` (bouton), `features_list_title`, `features_search_label`, `features_filter_all`, `features_filter_since`
  (« depuis %1$s »), `features_filter_label` (« Nouveau depuis »), `features_since` (« depuis %1$s »), `features_platform_android`,
  `features_platform_desktop`, `features_platform_web`, `features_platforms_only` (« %1$s seulement », lecture d'écran), `features_none`,
  `features_clear` ; les titres et phrases
  des cartes créées à l'inventaire (`card_<id>_title`, `card_<id>_body`). Tous dans les six langues, apostrophe `’`, tutoiement de l'espace Perso.

### Décisions

- **Hub conservé, liste à plat** (choix du propriétaire) : le hub est un parcours guidé, la liste un catalogue consultable ; une seule source
  (`FeatureCatalog`).
- **Un écran secondaire**, pas un dialogue : plus de cinquante entrées avec recherche tiennent mal dans une fenêtre étroite.
- **Une carte par fonctionnalité du README** (choix du propriétaire) : le README, le catalogue et la liste disent exactement la même chose ;
  coût : une dizaine de textes et d'icônes de plus dans six langues, une fois.
- **Tableau du README tenu à la main**, vérifié par un test (choix du propriétaire) plutôt que généré : un script de plus à maintenir pour
  un fichier qu'on relit de toute façon ; le test garantit l'absence de divergence.
- **Pas de filtre par plateforme** (choix du propriétaire) : la pastille suffit, la recherche et le filtre de version couvrent le reste.
- **Équipe masqué sans l'espace Équipe** : l'invariant d'isolation du mode solo vaut pour cette liste comme pour le hub.
- **Aucune persistance** : la liste est une consultation, pas un état à retenir.

### Impacts sur les specs existantes

`visite-guidee.md` (catalogue : `platforms`, une carte par fonctionnalité, le hub inchangé), `accueil.md` (le bouton),
`navigation-theme.md` (écran secondaire `FEATURES`), `i18n.md` (nouveaux textes, rien d'autre), `CLAUDE.md` et la skill `kairos-spec`
(procédure de version), `docs/spec/README.md` (index).
