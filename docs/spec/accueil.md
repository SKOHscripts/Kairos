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
`aKairos2DatabaseFoundAtFirstLaunchIsImportedAfterABackup`,
`M6GuideUiTest`, `GuideStepsTest`._

État : accueil du premier lancement **jalon M5** ; page Accueil, visite
guidée et aide **implémentées (2026-10-01)**, à la suite du retour « les
utilisateurs risquent de se perdre » sur le jalon E6.

## 1. Besoin métier (cahier des charges)

### Objectif / problème

Une base neuve contient des exemples (`modele-donnees.md`) ; sans un mot,
on ne sait pas que ce sont des exemples, ni ce que fait Kairos. Et un
utilisateur de Kairos 2 doit retrouver ses données tout de suite.

Ensuite, Kairos a grandi (notes, semaine, statistiques, espace Équipe,
échanges par fichier) : un dialogue vu une seule fois ne suffit plus. Il
faut pouvoir **retrouver à tout moment** la raison d'être de l'outil et ses
fonctionnalités (la page d'accueil de Kairos 2 le faisait), être **guidé**
pas à pas, et obtenir **de l'aide sur l'écran où l'on se trouve**, sans quitter
le travail en cours.

### Comportement attendu (utilisateur)

- Au tout premier lancement, un dialogue « Bienvenue dans Kairos » (logo) :
  ce que fait Kairos en une phrase (l'ordre, la valeur divisée par l'effort,
  capturer / qualifier / faire), puis, selon le cas :
  - Android, base Kairos 2 migrée : le bilan (`migration-2x.md`),
    « Commencer » ;
  - bureau, base Kairos 2 trouvée : la proposition de l'importer (chemin),
    « Importer la base Kairos 2 » (confirmation avec les comptes) ou
    « Garder les exemples » ;
  - sinon : les exemples (« [Exemple] », projet « Exemple », à terminer ou
    supprimer), « Commencer » ou « À propos et guide ».
- Il ne revient jamais : ni aux lancements suivants, ni quand la fenêtre
  change de taille.
- Dans le cas des exemples, le second bouton est **« Visite guidée »** (et non
  plus « À propos et guide ») : il ferme l'accueil et lance la visite.

#### Le bouton « ? » (aide)

- Une icône « ? » (« Aide ») à droite de la barre du haut, sur tous les écrans
  des deux espaces, sauf quand l'Accueil ou « À propos et guide » est ouvert.
- Elle ouvre un dialogue **« Aide : <écran> »** : un court texte sur l'écran
  courant (à quoi il sert, le geste à connaître), puis trois entrées :
  **« Accueil de Kairos »**, **« Visite guidée »** (espace Perso) ou
  **« Visite de l’espace Équipe »** (espace Équipe), et « Fermer ».
- Dans l'espace Équipe, l'écran Équipe a une aide pour chacun de ses deux
  onglets (« Membres », « Échanges »).

#### La page « Accueil »

- Écran secondaire (flèche retour, comme « À propos et guide »), ouvert depuis
  l'aide ; jamais une destination de plus (cinq au plus, charte).
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

#### Équipe : l'onglet « Échanges »

(Voir `equipe-echanges.md` § Onglet « Échanges ».) Tout ce qui concerne les
fichiers entre manager et membres tient en un seul endroit, l'écran Équipe,
au lieu d'être réparti entre la fiche du membre et les Réglages.

### Critères de succès

- Base neuve : l'accueil parle des exemples et se ferme d'un clic
  (`M5ScreensUiTest`).
- Base Kairos 2 trouvée : importée depuis l'accueil après confirmation et
  sauvegarde (`M5ScreensUiTest`).
- Le « ? » est présent dans les deux espaces et ouvre l'aide de l'écran
  courant ; l'Accueil s'ouvre depuis l'aide et se referme avec sa flèche
  (`M6GuideUiTest.helpExplainsTheCurrentScreenAndOpensTheHomePage`, `theHomePageClosesWithItsBackArrow`).
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
  écrans.
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
  son état. Il héberge son propre `LegacyImportFlow` et
  `LegacyImportDialog`.

### Aide, Accueil et visite (jalon « ne pas se perdre », 2026-10-01)

- **État de la coquille** : `KairosShell` porte `secondary: Secondary?`
  (`rememberSaveable`, `navigation/Secondary.kt` : `HOME`, `ABOUT`) à la place
  de l'ancien booléen `aboutOpen` ; `AppShell` porte `helpOpen` et
  `guide: GuideKind?` (`rememberSaveable`, locaux à la coquille : rien n'est
  persisté). Callbacks : `onOpenHome`, `onOpenAbout`, `onCloseSecondary`.
  Naviguer (destination, espace, jour) remet `secondary` à `null`, comme
  « À propos » avant. `BackHandler(enabled = secondary != null)`.
- **Barre du haut** : en `actions`, après le sélecteur d'espace s'il est
  sans rail, un `IconButton` `KairosIcons.Help` (icône générée,
  `make_icons.py`), `contentDescription` « Aide », seulement si
  `secondary == null`. Titre : `title_home` (« Accueil ») pour `HOME`,
  `title_about` pour `ABOUT`.
- **`HelpDialog`** (`ui/guide/HelpDialog.kt`) : `AlertDialog` MD3, icône
  `Help`, titre « Aide : <titre de l'écran> » (`NavEntry.title`, celui de la
  destination, pas le titre du jour affiché), `HelpTopic.body`, puis deux
  `ListItem` cliquables (« Accueil de Kairos » ; visite de l'espace courant
  d'après `space`), bouton « Fermer ».
- **`HelpTopic`** (`ui/guide/GuideContent.kt`) : un sujet par écran
  (`NOTES`, `DAY`, `WEEK`, `STATS`, `SETTINGS`, `TEAM_BOARD`, `TEAM_BACKLOG`,
  `TEAM_MEMBERS`, `TEAM_EXCHANGES`, `TEAM_FORECAST`) ;
  `HelpTopic.of(entry, exchanges)` : les Réglages sont le même sujet dans les
  deux espaces, `exchanges` (= `NavState.exchangesTab`) ne joue que pour
  `TeamDestination.MEMBERS`. Texte au tutoiement dans l'espace Perso,
  au vouvoiement dans l'espace Équipe (registre de `equipe-echanges.md`).
- **`HomeScreen`** (`ui/screens/HomeScreen.kt`) : colonne défilante de
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
- **`GuideSteps`** : `PERSONAL` (6 étapes : Notes, Jour, Jour, Jour, Stats,
  Réglages) et `TEAM` (6 : Équipe, Backlog, Backlog, Suivi, Prévisions,
  Équipe avec onglet « Échanges »). `GuideStep(icon, title, body,
  target: GuideTarget)` ; `GuideTarget.Personal(destination)` ou
  `GuideTarget.Team(destination, exchanges)`.
- **`openTarget`** (dans `AppShell`) : une cible Perso change d'espace si
  besoin (`onSpaceChange(PERSONAL)`) puis `onNavigate` ; une cible d'équipe
  ne fait rien si la gestion d'équipe est désactivée, sinon
  `onSpaceChange(TEAM)` au besoin (qui ouvre Suivi), puis
  `nav.exchangesTab = target.exchanges` et `onNavigateTeam`. L'ordre compte :
  changer d'espace réinitialise la destination. « Voir l’écran » ferme la
  visite avant d'ouvrir (`guide = null`).
- **Premier lancement** : `WelcomeDialog(services, onStartGuide)` ; le bouton
  secondaire du cas « exemples » est `welcome_tour` (« Visite guidée »), qui
  ferme le dialogue puis met `guide = PERSONAL`. `welcome_about` disparaît (plus
  utilisé) ; « À propos et guide » reste atteignable depuis l'Accueil et les
  Réglages.
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
  Écarté : une destination « Accueil » (sixième onglet, ou un onglet retiré),
  un tiroir de navigation (nouveau composant pour un seul écran).
- **Visite = suite de dialogues, pas de pastilles sur l'interface**
  (2026-10-01) : des *coach marks* demanderaient d'ancrer chaque pastille à un
  élément de Compose Multiplatform qui change avec la taille de fenêtre, le
  rail ou la barre basse, et casseraient au premier remaniement d'écran.
  « Voir l’écran » ouvre le vrai écran ; la visite se relance de là.
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
  importée ou migrée n'est pas « neuve » au lancement suivant.
- **La carte « Pourquoi « Kairos » ? » de « À propos » reste** (formule du score,
  origine du nom) : l'Accueil est le point d'entrée, « À propos et guide » la
  référence.
