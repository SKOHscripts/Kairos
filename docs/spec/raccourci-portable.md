# Raccourci des versions portables

_Rôle : donner à Kairos portable (`tar.gz` Linux, zip Windows) une entrée
de menu avec son icône, comme la version installée. Fichiers couverts :
`kmp/desktopApp/.../PortableShortcuts.kt` (détection, création, retrait),
`kmp/desktopApp/.../WindowClass.kt` (classe de fenêtre X11),
`kmp/desktopApp/.../Main.kt` et `DesktopServices.kt` (branchement),
`kmp/desktopApp/build.gradle.kts` (`--add-opens`),
`kmp/ui/.../app/Shortcuts.kt` (service, bandeau de proposition),
`kmp/ui/.../settings/SettingsScreen.kt` (carte Raccourci),
`.github/workflows/kmp-release.yml` (marqueur des archives portables).
Tests : `desktopApp/.../PortableShortcutsTest.kt`,
`M5ScreensUiTest.aPortableCopyProposesItsShortcutUntilCreatedOrDeclined`,
`M5ScreensUiTest.anInstalledCopyHasNoShortcutCard`._

État : issue #46 (après la 3.0.0).

## 1. Besoin métier (cahier des charges)

### Objectif / problème

Les installeurs (`.deb`, `.msi`) ajoutent Kairos au menu des applications,
avec son icône. La version portable, elle, se lance depuis son dossier : pas
d'entrée de menu, et sous Linux la barre des tâches ne sait pas quelle icône
montrer. Sur un poste verrouillé, c'est pourtant la seule version possible
(`distribution.md`).

### Comportement attendu (utilisateur)

- Au lancement d'une **copie portable** sans raccourci, un bandeau propose
  de le créer : « Ajouter Kairos au menu des applications, avec son icône ? »
  (Linux) ou « Ajouter Kairos au menu Démarrer et au Bureau ? » (Windows),
  avec « Ajouter » et « Non merci ».
  - « Ajouter » crée le raccourci ; le bandeau disparaît, un message le
    confirme (ou dit pourquoi c'est impossible).
  - « Non merci » masque le bandeau pour de bon.
- Si les raccourcis existent mais ouvrent **une autre copie** (nouvelle
  version dézippée ailleurs), le bandeau propose de les mettre à jour vers
  celle-ci (« Mettre à jour »), sauf après « Non merci ».
- Réglages → carte **Raccourci** (copie portable seulement, après « Mises à
  jour ») : l'état (aucun raccourci, créé, ouvre une autre copie), et
  « Créer le raccourci » / « Mettre à jour le raccourci » / « Retirer le
  raccourci ».
- Ce qui est créé :
  - **Linux** : une entrée du menu des applications (fichier `.desktop` de
    l'utilisateur), avec l'icône de Kairos ; les fenêtres de Kairos se
    rangent sous cette icône dans la barre des tâches ;
  - **Windows** : un raccourci dans le menu Démarrer et un sur le Bureau,
    avec l'icône de Kairos.
- Rien n'exige de droit administrateur ; rien n'est écrit hors du dossier
  personnel.

### Critères de succès

- Une copie installée (`.deb`, `.msi`, `.dmg`), la version lancée depuis les
  sources et la version web ne proposent rien.
- L'entrée Linux lance la copie, même si son chemin contient des espaces
  (« Kairos Preview ») ou des caractères réservés.
- Retirer supprime exactement ce qui a été créé, rien d'autre.

### Hors périmètre

- **macOS** : la copie portable est une application (`Kairos.app`) qui a
  déjà son icône ; il suffit de la glisser dans Applications.
- **Icône sur le Bureau Linux** : les bureaux Linux la gèrent chacun à leur
  façon (GNOME demande d'« autoriser le lancement ») ; l'entrée du menu
  suffit à épingler Kairos au dock ou aux favoris.
- Démarrage automatique à l'ouverture de session.

## 2. Solution technique

### Détection (`PortableShortcuts.detect`)

- Copie portable = lancée par un lanceur jpackage (propriété système
  `jpackage.app-path`, posée par le lanceur natif) **et** marqueur
  `kairos-portable` à côté des JAR de l'image : `lib/app/` sous Linux,
  `app/` sous Windows. Le marqueur n'est écrit que dans les archives
  portables, par `kmp-release.yml`, après la construction des installeurs :
  les paquets `.deb` et `.msi` ne le contiennent pas.
- Racine de l'image : le dossier au-dessus de `bin/` (Linux), celui du
  lanceur `.exe` (Windows). Tout autre OS (macOS) : pas de raccourci.

### Service (`ui/app/Shortcuts.kt`)

- `ShortcutService` : `target` (`APP_MENU` Linux, `START_MENU_AND_DESKTOP`
  Windows, pour le libellé), `status: StateFlow<ShortcutStatus>` (`MISSING`,
  `CREATED`, `ELSEWHERE`), `declined: StateFlow<Boolean>`, `create()`
  (lève une exception en cas d'échec), `remove()`, `decline()`.
  `AppServices.shortcuts` est non nul pour une copie portable seulement
  (`DesktopServices.open(portable = …)`, jamais pour l'auto-test).
- `ShortcutBanner` (`AppShell`, sous le bandeau des mises à jour) : bandeau
  neutre comme lui (`surfaceContainerHigh`, icône `InstallDesktop`), affiché
  si `status ≠ CREATED` et pas `declined`.
- `ShortcutCard` (Réglages, entre les cartes Mises à jour et Données) : aide selon
  `target`, ligne d'état en `onSurfaceVariant`, boutons à contour « Créer »
  ou « Mettre à jour » (sauf `CREATED`) et « Retirer » (sauf `MISSING`).
- Résultat de chaque action en snackbar (`LocalMessages`) : « Raccourci
  créé. », « Raccourci non créé : <raison> », « Raccourci retiré. »…

### Création et retrait (`desktopApp`)

- État dans `shortcuts.json` du dossier de données : lanceur visé, fichiers
  créés, refus. `status` : `CREATED` si les fichiers existent et visent ce
  lanceur, `ELSEWHERE` s'ils existent pour un autre, `MISSING` sinon.
- **Linux** : `$XDG_DATA_HOME/applications/kairos.desktop`
  (`kairos-preview.desktop` pour une préversion ; `~/.local/share` par
  défaut) : `Type=Application`, `Name` (nom du produit), `Comment` (la
  description du paquet), `Exec` (lanceur entre guillemets, échappé selon la
  Desktop Entry Specification : `"`, `` ` ``, `$`, `\` puis `\` doublé,
  `%` doublé), `Icon` (chemin absolu de `lib/<nom>.png`, l'icône que jpackage
  met dans l'image), `Terminal=false`, `Categories=Office;`,
  `StartupWMClass=kairos`. Fichier rendu exécutable ; accepté par
  `desktop-file-validate`.
- **Classe de fenêtre** (`WindowClass`) : AWT dérive `WM_CLASS` de la classe
  en bas de la pile du fil qui charge le toolkit, ce qui varie avec Compose ;
  `main` la fixe à `kairos` avant toute fenêtre (champ
  `XToolkit.awtAppClassName`, d'où `--add-opens
  java.desktop/sun.awt.X11=ALL-UNNAMED` dans les options de la JVM). Sans
  effet hors X11 et en mode sans écran.
- **Windows** : un script PowerShell (`-EncodedCommand`, sans profil) crée
  par `WScript.Shell` `Kairos.lnk` dans `Programs` (menu Démarrer) et
  `Desktop` (dossiers réels, OneDrive compris), cible et icône = le lanceur
  `.exe`, dossier de travail = la racine ; il rend les chemins créés, gardés
  dans l'état.
- Retrait : suppression des seuls fichiers notés dans l'état.

### Décisions et pièges tracés

- **Marqueur plutôt que chemin d'installation** : deviner une copie
  installée à son chemin (`/opt/kairos`, `%LOCALAPPDATA%`) casse dès qu'on
  dézippe au même endroit ; le marqueur écrit par la release est explicite.
- **Proposé, jamais imposé** : créer des fichiers dans le menu de
  l'utilisateur se demande ; « Non merci » est retenu.
- **Icône Linux prise dans l'image** plutôt que copiée : elle suit la copie ;
  si la copie est supprimée, l'entrée est obsolète de toute façon.
- **PowerShell plutôt qu'un `.lnk` écrit à la main** : le format Shell Link
  est binaire et piégeux ; `WScript.Shell` est présent sur tout Windows.
  Non testé en CI (pas de Windows dans les tests) : le script généré est
  vérifié, son exécution est remplacée en test.
- **Vérifié de bout en bout sous Linux** : image `createDistributable`
  marquée, lancée sous Xvfb depuis un dossier à espace ; classe de fenêtre
  `kairos`, bandeau, entrée créée puis retirée depuis les Réglages.
