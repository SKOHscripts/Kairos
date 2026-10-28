# Accueil, visite guidée et aide

_Rôle : la première rencontre avec Kairos, puis de quoi ne pas s'y perdre :
dialogue du premier lancement, page « Accueil » (raison d'être et
fonctionnalités), visite guidée, aide de l'écran courant. Fichiers couverts :
`kmp/ui/.../app/Welcome.kt` (`WelcomeDialog`), `ui/screens/HomeScreen.kt`
(`HomeScreen`), `ui/guide/GuideContent.kt` (`GuideSteps`, `GuideTarget`,
`HelpTopic`), `ui/guide/GuideDialog.kt`, `ui/guide/HelpDialog.kt`, leur
appel dans `navigation/AppShell.kt` et `KairosApp.kt`, `AppServices.firstLaunch`
et sa valeur sur chaque plateforme (`DesktopServices.kt`, `AndroidServices.kt`,
`WebServices.kt`). Tests : `M5ScreensUiTest.theWelcomeShowsOnceOnFirstLaunch`,
`M6GuideUiTest`, `GuideStepsTest`._

État : accueil du premier lancement **jalon M5** ; page Accueil, visite
guidée et aide **implémentées (2026-10-01)**, à la suite du retour « les
utilisateurs risquent de se perdre » sur le jalon E6 ; Accueil en première
destination et écran d'ouverture **(2026-10-10)**, les Réglages passant dans
la barre du haut (`navigation-theme.md`).

## 1. Besoin métier (cahier des charges)

### Objectif / problème

Une base neuve contient des exemples (`modele-donnees.md`) ; sans un mot,
on ne sait pas que ce sont des exemples, ni ce que fait Kairos.

Ensuite, Kairos a grandi (notes, semaine, statistiques, espace Équipe,
échanges par fichier) : un dialogue vu une seule fois ne suffit plus. Il
faut pouvoir **retrouver à tout moment** la raison d'être de l'outil et ses
fonctionnalités, être **guidé**
pas à pas, et obtenir **de l'aide sur l'écran où l'on se trouve**, sans quitter
le travail en cours.

### Comportement attendu (utilisateur)

- Au tout premier lancement, un dialogue « Bienvenue dans Kairos » (logo) :
  ce que fait Kairos en une phrase (l'ordre, la valeur divisée par l'effort,
  capturer / qualifier / faire), puis les exemples (« [Exemple] », projet
  « Exemple », à terminer ou supprimer), avec « Commencer » ou « Visite
  guidée ».
- Il ne revient jamais : ni aux lancements suivants, ni quand la fenêtre
  change de taille.
- Il attend la fin de la page de démarrage (`demarrage.md`) : jamais
  par-dessus le cadran animé.
- Le second bouton est **« Visite guidée »** : il ferme l'accueil et lance la
  visite.

#### Le bouton « ? » (aide)

- Une icône « ? » (« Aide ») à droite de la barre du haut, avant la roue
  crantée des Réglages, sur tous les écrans des deux espaces (Réglages
  compris), sauf sur l'Accueil et « À propos et guide », qui sont eux-mêmes
  de l'aide (le cœur de `soutien.md`, lui, reste affiché).
- Elle ouvre un dialogue **« Aide : <écran> »** : un court texte sur l'écran
  courant (à quoi il sert, le geste à connaître), puis trois entrées :
  **« Accueil de Kairos »** (ouvre l'Accueil de l'espace courant),
  **« Visite guidée »** (espace Perso) ou
  **« Visite de l’espace Équipe »** (espace Équipe), et « Fermer ».
- Dans l'espace Équipe, l'écran Équipe a une aide pour chacun de ses deux
  onglets (« Membres », « Échanges »).

#### La page « Accueil »

- **Première destination** de la navigation, dans les deux espaces (la même
  page), et **écran d'ouverture** de l'application : c'est la première chose
  qu'on voit à chaque lancement. Elle s'ouvre aussi depuis l'aide.
- **« À quoi sert Kairos ? »** : la raison d'être en quelques phrases (trop de
  tâches pour le temps disponible ; Kairos décide de l'ordre par un score,
  pose les tâches dans l'agenda, mesure ce qu'on fait vraiment ; données
  gardées sur l'appareil, sans compte ni réseau).
- **« Ce que tu peux faire »** : une carte par fonctionnalité principale
  (Capturer, Qualifier, Faire dans l'ordre, Suivre le temps, Voir la
  semaine, Mesurer), chacune avec son icône, une phrase et un chevron : toucher
  la carte ouvre l'écran concerné et referme l'Accueil.
- **« Travailler en équipe »** : explique les deux rôles. Manager : activer la
  gestion d'équipe (Réglages → Équipe), backlog, charge, prévisions. Membre :
  importer le fichier reçu de son manager (Réglages → Données → Importer) et
  renvoyer son avancement. Bouton « Ouvrir l’espace Équipe » si la gestion
  d'équipe est activée, « Ouvrir les Réglages » sinon.
- Deux boutons en bas : **« Visite guidée »** et, si la gestion d'équipe est
  activée, **« Visite de l’espace Équipe »**, puis « À propos et guide ».

#### La visite guidée

- Une suite d'étapes courtes, une fenêtre à la fois : icône, titre, texte,
  « Étape 2 sur 6 », bouton **« Voir l’écran »** (ferme la visite et ouvre
  l'écran dont parle l'étape), « Précédent » (« Passer la visite » à la
  première étape) et « Suivant » (« Terminer » à la dernière). Fermer la
  fenêtre (clic à côté, Échap, retour) quitte la visite.
- **Visite de Kairos** (espace Perso, 6 étapes) : Capturer (Notes) ;
  Qualifier (Jour) ; Faire dans l'ordre (Jour) ; Suivre le temps (Jour) ;
  Semaine et statistiques (Stats) ; Vos données et votre manager
  (Réglages).
- **Visite de l'espace Équipe** (6 étapes, seulement si la gestion d'équipe
  est activée) : Les membres ; Le backlog ; Assigner ; Le suivi ; Les
  prévisions ; **Les échanges** (onglet « Échanges » de l'écran Équipe).
- Elle se relance à volonté : depuis l'aide, depuis l'Accueil, ou depuis le
  premier lancement. Rien n'est mémorisé : pas de « visite terminée ».
- **3.4** (`visite-guidee.md`, `premiere-tache.md`) : les deux visites sont des **sélections du catalogue** de
  fonctionnalités (`FeatureCatalog`, mêmes six étapes) ; l'Accueil gagne le bouton **« Découvrir Kairos »** (hub des
  chapitres, aussi dans l'aide et les Réglages) et une section **« Nouveau dans la version X.Y.Z »** masquable pour la
  session ; l'accueil du premier lancement gagne **« Créer ma première tâche »** (accompagnement dans la vraie fiche) ;
  l'aide propose une entrée « Découvrir Kairos ». Le reste de cette spec est inchangé.
- **3.13** (`liste-fonctionnalites.md`, issue #153) : sous la section « Nouveau » (et hors de sa condition d'affichage, donc
  toujours là), un bouton **« Liste de fonctionnalités »** (`OutlinedButton`, icône `FormatListBulleted`) ouvre la liste complète du
  catalogue, écran secondaire avec recherche et filtre « Nouveau depuis… ». L'Accueil ne change pas autrement.

#### Équipe : l'onglet « Échanges »

(Voir `equipe-echanges.md` § Onglet « Échanges ».) Tout ce qui concerne les
fichiers entre manager et membres tient en un seul endroit, l'écran Équipe,
au lieu d'être réparti entre la fiche du membre et les Réglages.

### Critères de succès

- Base neuve : l'accueil parle des exemples et se ferme d'un clic
  (`M5ScreensUiTest`).
- L'application s'ouvre sur l'Accueil, première destination des deux
  espaces ; les Réglages s'ouvrent par la roue crantée et se referment avec
  leur flèche, comme « À propos » ouvert depuis eux
  (`M6GuideUiTest.theAppOpensOnTheHomePageAndSettingsAreInTheTopBar`,
  `theTeamSpaceAlsoOpensOnTheHomePage`).
- Le « ? » est présent dans les deux espaces et ouvre l'aide de l'écran
  courant ; l'Accueil s'ouvre depuis l'aide
  (`M6GuideUiTest.helpExplainsTheCurrentScreenAndOpensTheHomePage`).
- La visite se déroule de la première à la dernière étape et se quitte à tout
  moment ; « Voir l’écran » ouvre l'écran de l'étape
  (`M6GuideUiTest.theTourWalksThroughAndOpensTheScreen`). Chaque cible de
  chaque étape est un écran qui existe, chaque écran a un texte d'aide
  (`GuideStepsTest`).
- La visite de l'espace Équipe n'est proposée que si la gestion d'équipe est
  activée, et son étape « Échanges » ouvre l'onglet « Échanges »
  (`M6GuideUiTest.theTeamTourIsOfferedInTheTeamSpaceAndItsLastStepOpensTheExchangesTab`, `theHomePageOffersTheTeamTourAndSpaceOnlyWhenTeamManagementIsOn`).
- Une base solo reste **sans incidence sur ses données et son comportement** :
  le « ? » et l'Accueil sont les seules différences visibles (la carte
  « Travailler en équipe » de l'Accueil est du texte, pas une donnée), et
  `TeamIsolationTest` reste inchangé.
- La visite se quitte à tout moment et ne laisse aucune trace : aucun réglage
  ne change (`M6GuideUiTest.theTourEndsOnFinishAndLeavesNoTrace`,
  `quittingTheTourFromTheFirstStepClosesIt`) ; le dialogue du premier
  lancement lance la visite (`theFirstLaunchDialogStartsTheTour`).

### Hors périmètre / différé

- Visite avec **pastilles qui pointent les éléments** de l'écran (coach marks) :
  écartée (§ Décisions) ; la visite est une suite de fenêtres qui ouvrent les
  écrans. Précisé le 2026-10-26 (issue #96) : une visite interactive sur une démonstration jetable cerne des éléments de
  l'écran sans pastille ancrée, voir `visite-guidee.md` § Visite interactive (implémentée en 3.13.0) ; son entrée est dans le hub, l'Accueil lui-même ne change pas.
- Mémoriser « visite terminée », ou la relancer d'office à une mise à jour :
  non, pas de nouvel indicateur à stocker (et aucune incidence sur l'export).
- Aide en ligne, vidéos, liens vers un site : non, l'application reste
  hors ligne et sans réseau.

## 2. Solution technique

- `AppServices.firstLaunch` : bureau et Android `KairosStore.open(...).created`
  (bureau : jamais pour l'auto-test, base en mémoire) ; web : ni instantané
  dans l'OPFS ni fichier lié en attente d'autorisation.
- `WelcomeDialog(services, onOpenAbout)` : `AlertDialog` MD3 (icône
  `KairosLogo`), état `rememberSaveable` initialisé à `firstLaunch` ; appelé
  une fois dans `AppShell`, **hors** des branches de mise en page (rail,
  barre du haut, barre basse), pour qu'un redimensionnement ne recrée pas
  son état.

### Aide, Accueil et visite (jalon « ne pas se perdre », 2026-10-01)

- **Accueil = destination** : `Destination.HOME` et `TeamDestination.HOME`
  (en tête des deux enums, `navigation-theme.md`) ; `AppShell` compose
  `HomeScreen` et le passe en `home` à `DestinationScreen` et
  `TeamDestinationScreen`. `Destination.START = HOME` et
  `initialTeamDestination = TeamDestination.HOME` : l'application s'ouvre sur
  l'Accueil, dans l'espace mémorisé (`lastSpace`).
- **État de la coquille** : `KairosShell` porte `secondary: Secondary?`
  (`rememberSaveable`, `navigation/Secondary.kt` : `SETTINGS`, `ABOUT`) et
  `secondaryParent` ; `AppShell` porte `helpOpen` et
  `guide: GuideKind?` (`rememberSaveable`, locaux à la coquille : rien n'est
  persisté). Callbacks : `onOpenSettings`, `onOpenAbout`, `onCloseSecondary`.
  Naviguer (destination, espace, jour) remet `secondary` à `null`.
  `BackHandler(enabled = secondary != null)`.
- **Barre du haut** : en `actions`, après le sélecteur d'espace s'il est
  sans rail et le cœur, un `IconButton` `KairosIcons.Help` (icône générée,
  `make_icons.py`), `contentDescription` « Aide », seulement s'il y a un
  sujet d'aide (`helpTopic` : `HelpTopic.SETTINGS` sur les Réglages, `null`
  sur « À propos », `HelpTopic.of(destination)` ailleurs), puis la roue
  crantée des Réglages.
- **`HelpDialog(topic, team, …)`** (`ui/guide/HelpDialog.kt`) : `AlertDialog` MD3, icône
  `Help`, titre « Aide : <titre de l'écran> » (`HelpTopic.screen`, le titre
  de la destination ou des Réglages, pas le titre du jour affiché),
  `HelpTopic.body`, puis deux `ListItem` cliquables (« Accueil de Kairos »,
  icône `Home`, qui ouvre la destination Accueil de l'espace courant ;
  visite de l'espace courant d'après `space`), bouton « Fermer ». Le
  contenu défile (`verticalScroll`), comme ceux de `GuideDialog` et du dialogue
  d'accueil : sur un écran bas (téléphone en paysage), rien n'est coupé.
- **`HelpTopic`** (`ui/guide/GuideContent.kt`) : un sujet par écran, avec
  son titre (`NOTES`, `DAY`, `WEEK`, `STATS`, `SETTINGS`, `TEAM_BOARD`,
  `TEAM_BACKLOG`, `TEAM_MEMBERS`, `TEAM_EXCHANGES`, `TEAM_FORECAST`) ;
  `HelpTopic.of(entry, exchanges)` : `null` pour l'Accueil (pas de « ? »),
  `exchanges` (= `NavState.exchangesTab`) ne joue que pour
  `TeamDestination.MEMBERS` ; `SETTINGS` est choisi par la coquille quand
  les Réglages sont ouverts (les mêmes dans les deux espaces). Texte au tutoiement dans l'espace Perso,
  au vouvoiement dans l'espace Équipe (registre de `equipe-echanges.md`).
- **`HomeScreen`** (`ui/screens/HomeScreen.kt`) : destination `HOME`, colonne défilante de
  720 dp au plus, centrée, comme `AboutScreen`. Logo 56 dp + nom + devise ;
  **l'unique bloc teinté** : carte `primaryContainer` (élévation 0) « À quoi
  sert Kairos ? » ; six `OutlinedCard(onClick)` + `ListItem` (icône primaire,
  titre, phrase, `ChevronRight`, fond transparent) pour Capturer (Notes),
  Qualifier (Jour), Faire dans l'ordre (Jour), Suivre le temps (Jour), Voir la
  semaine (Semaine), Mesurer (Stats) ; une `OutlinedCard` « Travailler en
  équipe » (icône `Groups`, les deux rôles, bouton « Ouvrir l’espace
  Équipe » si `teamModeEnabled`, sinon « Ouvrir les Réglages ») ; en bas,
  « Visite guidée » (`Button`), « Visite de l’espace Équipe »
  (`OutlinedButton`, seulement si la gestion d'équipe est activée) et
  « À propos et guide » (`TextButton`) dans un `FlowRow`. Les cartes
  n'ont pas de bouton « Ouvrir » : la carte entière est la cible (≥ 48 dp,
  tient dans 360 dp sans débordement).
- **`GuideDialog`** (`ui/guide/GuideDialog.kt`) : `AlertDialog` MD3, icône de
  l'étape (primaire), titre, « Étape n sur N » (`labelMedium`), texte, bouton
  texte « Voir l’écran » ; `confirmButton` « Suivant » (« Terminer » à
  la dernière), `dismissButton` « Précédent » (« Passer la visite » à la
  première). L'index est `rememberSaveable(kind)`. `onDismissRequest` quitte.
  Les titres et les textes de toutes les étapes sont superposés dans un `Box`,
  ceux des autres étapes transparents et sans sémantique : le dialogue garde la
  hauteur du plus long (un titre sur deux lignes à 400 dp), « Précédent » et
  « Suivant » ne sautent pas. Le défilement est propre à chaque étape
  (`rememberSaveable(index)`) : la suivante s'ouvre en haut.
- **`GuideSteps`** : `PERSONAL` (6 étapes : Notes, Jour, Jour, Jour, Stats,
  Réglages) et `TEAM` (6 : Équipe, Backlog, Backlog, Suivi, Prévisions,
  Équipe avec onglet « Échanges »). `GuideStep(icon, title, body,
  target: GuideTarget)` ; `GuideTarget.Personal(destination)`,
  `GuideTarget.Team(destination, exchanges)` ou `GuideTarget.Settings`
  (les Réglages, sans changer d'espace : dernière étape de la visite Perso
  et bouton « Ouvrir les Réglages » de l'Accueil).
- **`openTarget`** (dans `AppShell`) : une cible Perso change d'espace si
  besoin (`onSpaceChange(PERSONAL)`) puis `onNavigate` ; une cible d'équipe
  ne fait rien si la gestion d'équipe est désactivée, sinon
  `onSpaceChange(TEAM)` au besoin (qui ouvre Suivi), puis
  `nav.exchangesTab = target.exchanges` et `onNavigateTeam` ;
  `GuideTarget.Settings` ouvre les Réglages (`onOpenSettings`). L'ordre compte :
  changer d'espace réinitialise la destination. « Voir l’écran » ferme la
  visite avant d'ouvrir (`guide = null`).
- **Premier lancement** : `WelcomeDialog(services, onStartGuide)` ; le bouton
  secondaire est `welcome_tour` (« Visite guidée »), qui
  ferme le dialogue puis met `guide = PERSONAL`. « À propos et guide » reste
  atteignable depuis l'Accueil et les Réglages.
- **Textes** : préfixes `help_`, `home_`, `guide_`, `hub_`, `team_tab_` ;
  français (tutoiement Perso et Accueil, vouvoiement Équipe) et anglais,
  `StringsParityTest`.
- **Test de contenu** : `GuideStepsTest` (six étapes par visite, cibles du
  bon espace, seule la dernière étape d'équipe ouvre « Échanges », chaque
  écran a un sujet d'aide et chaque sujet est atteignable).

### Décisions et pièges tracés

- **« ? » dans la barre du haut, pas un sixième onglet** (2026-10-01) : la
  limite MD3 est de cinq destinations par espace ; l'aide doit être
  atteignable de partout, y compris en plein travail dans l'espace Équipe.
  Écarté alors : une destination « Accueil » (sixième onglet, ou un onglet
  retiré), un tiroir de navigation (nouveau composant pour un seul écran).
- **Accueil en destination, à la place des Réglages** (2026-10-10, rouvre
  explicitement la décision ci-dessus sur demande) : l'Accueil n'était
  atteignable que par le « ? » et passait inaperçu ; il devient la première
  destination et l'écran d'ouverture, l'onglet retiré étant les Réglages
  (roue crantée de la barre du haut, `navigation-theme.md`). Le « ? »
  reste : il donne l'aide de l'écran courant, ce que l'Accueil ne fait pas.
- **Visite = suite de dialogues, pas de pastilles sur l'interface**
  (2026-10-01) : des *coach marks* demanderaient d'ancrer chaque pastille à un
  élément de Compose Multiplatform qui change avec la taille de fenêtre, le
  rail ou la barre basse, et casseraient au premier remaniement d'écran.
  « Voir l’écran » ouvre le vrai écran ; la visite se relance de là. **Rouverte en partie le 2026-10-26** (issue #96) :
  la visite interactive sur une démonstration respecte la raison de cette décision (aucune pastille posée par
  coordonnées : le repère est un contour appliqué à l'élément lui-même) et ajoute deux choses, cerner des éléments et
  agir sur un dépôt jetable ; ces dialogues-ci ne changent pas (`visite-guidee.md` § Décisions et § Visite interactive).
- **Rien n'est mémorisé** (2026-10-01) : ni « visite faite », ni écran vu.
  Un indicateur de plus ferait entrer un réglage dans l'export (`encodeDefaults`)
  et dans les bases solo (`equipe.md` § Isolation). Le premier lancement
  reste « base créée » (ci-dessous).
- **Texte d'équipe dans l'Accueil de l'espace solo** (2026-10-01) : l'invariant
  du mode solo porte sur les données et le comportement (`personalView`,
  export, réglages), pas sur le texte. Faire connaître l'espace Équipe, et
  surtout le circuit « mon manager m'envoie un fichier » à un membre qui n'a
  pas activé l'espace (qui ne verrait sinon jamais le bouton), est le but de
  l'Accueil. La carte est une seule `OutlinedCard`, sans donnée.
- **Titre « À quoi sert Kairos ? »** (et non « Pourquoi Kairos ? ») pour ne
  pas se confondre avec la carte « Pourquoi « Kairos » ? » (l'origine du
  nom) de « À propos et guide ».
- **Un dialogue, pas un écran** : les exemples sont visibles derrière, ce
  sont eux qui montrent l'application.
- **Premier lancement = base créée** : aucun indicateur à stocker ; une base
  existante n'est pas « neuve » au lancement suivant.
- **La carte « Pourquoi « Kairos » ? » de « À propos » reste** (formule du score,
  origine du nom) : l'Accueil est le point d'entrée, « À propos et guide » la
  référence.
