# Accueil au premier lancement

_Rôle : la première rencontre avec Kairos. Fichiers couverts :
`kmp/ui/.../app/Welcome.kt` (`WelcomeDialog`), son appel dans
`navigation/AppShell.kt`, `AppServices.firstLaunch` et sa valeur sur
chaque plateforme (`DesktopServices.kt`, `AndroidServices.kt`,
`WebServices.kt`). Tests : `M5ScreensUiTest.theWelcomeShowsOnceOnFirstLaunch`,
`aKairos2DatabaseFoundAtFirstLaunchIsImportedAfterABackup`._

État : **jalon M5** (plan § 3 : « un accueil au premier lancement présente
les exemples », à la place de la page d'accueil de Kairos 2).

## 1. Besoin métier (cahier des charges)

### Objectif / problème

Une base neuve contient des exemples (`modele-donnees.md`) ; sans un mot,
on ne sait pas que ce sont des exemples, ni ce que fait Kairos. Et un
utilisateur de Kairos 2 doit retrouver ses données tout de suite.

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

### Critères de succès

- Base neuve : l'accueil parle des exemples et se ferme d'un clic
  (`M5ScreensUiTest`).
- Base Kairos 2 trouvée : importée depuis l'accueil après confirmation et
  sauvegarde (`M5ScreensUiTest`).

### Hors périmètre / différé

- Visite guidée écran par écran : l'écran « À propos et guide » tient ce
  rôle (`navigation-theme.md`).

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

### Décisions et pièges tracés

- **Un dialogue, pas un écran** : les exemples sont visibles derrière, ce
  sont eux qui montrent l'application.
- **Premier lancement = base créée** : aucun indicateur à stocker ; une base
  importée ou migrée n'est pas « neuve » au lancement suivant.
