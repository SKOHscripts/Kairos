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
- **Anciennes entrées « Kairos Preview »** (demande du propriétaire du 2026-11-17) : jusqu'à la 4.1, une beta portable
  s'appelait « Kairos Preview » et créait sa propre entrée ; depuis la 4.2.0, beta et stable sont la même app « Kairos ».
  Une entrée « Kairos Preview » restée dans le menu ouvre une autre copie : le bandeau propose donc « Mettre à jour »,
  même si cette entrée n'est pas connue de la copie courante, et la mise à jour la retire. Il ne reste qu'une entrée
  « Kairos ».
- Réglages → catégorie « Général » → carte **Raccourci** (copie portable seulement, après « Mises à
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
  (« Mes logiciels/Kairos » ; « Kairos Preview » jusqu'à la 4.1) ou des caractères réservés.
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
- `ShortcutCard` (Réglages, catégorie Général, après la carte Mises à jour ; icône de titre `InstallDesktop`) : aide selon
  `target`, ligne d'état en `onSurfaceVariant`, boutons à contour « Créer »
  ou « Mettre à jour » (sauf `CREATED`) et « Retirer » (sauf `MISSING`).
- Résultat de chaque action en snackbar (`LocalMessages`) : « Raccourci
  créé. », « Raccourci non créé : <raison> », « Raccourci retiré. »…

### Création et retrait (`desktopApp`)

- État dans `shortcuts.json` du dossier de données : lanceur visé, fichiers
  créés, refus. `status` : `CREATED` si les fichiers existent et visent ce
  lanceur, `ELSEWHERE` s'ils existent pour un autre, `MISSING` sinon.
- **Linux** : `$XDG_DATA_HOME/applications/kairos.desktop`
  (`~/.local/share` par défaut ; une seule entrée `kairos.desktop` depuis la 4.2.0, beta comprise, le nom du fichier
  venant du nom de l'app ; jusqu'à la 4.1, `kairos-preview.desktop` pour une préversion « Kairos Preview ») : `Type=Application`, `Name` (nom du produit), `Comment` (la
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
- **Mise à jour** (statut `ELSEWHERE`, bouton « Mettre à jour ») : la même création, qui réécrit les fichiers avec le
  lanceur et l'icône de la copie courante, puis supprime les fichiers notés dans l'état qui n'ont pas été recréés (un
  ancien nom, `kairos-preview.desktop` d'une copie « Kairos Preview » qui partageait le dossier de données, par exemple),
  et note le nouveau lanceur.
- **Anciens noms** (`PortableShortcuts.LEGACY_APP_NAMES`, « Kairos Preview ») : sous Linux,
  `<applications>/kairos-preview.desktop` est une ancienne entrée s'il porte `StartupWMClass=kairos` (écrit par Kairos,
  pas par un tiers). Elle compte dans le statut (`ELSEWHERE` tant qu'elle existe, même quand `kairos.desktop` vise déjà
  cette copie) et `create()` la supprime, notée dans l'état ou non. Sous Windows, le script de création supprime aussi
  `Kairos Preview.lnk` des mêmes dossiers quand sa cible finit par `Kairos Preview.exe` ; pas de détection au démarrage
  (il faudrait lancer PowerShell à chaque lancement), le nettoyage se fait au prochain « Créer » ou « Mettre à jour ».
- Retrait : suppression des seuls fichiers notés dans l'état.

### Décisions et pièges tracés

- **Texte de la snackbar lu avant de créer le raccourci** (2026-10-10) : le
  raccourci créé fait disparaître le bandeau, donc annule la coroutine de son
  bouton ; la lecture du texte « Raccourci créé. » (`getString`, suspendue)
  faite après coup pouvait être interrompue, et la snackbar ne jamais
  s'afficher (échec intermittent de
  `M5ScreensUiTest.aPortableCopyProposesItsShortcutUntilCreatedOrDeclined` en
  CI). `rememberShortcutAction` lit le texte d'abord ; l'envoi lui-même
  (`LocalMessages`) part dans la portée de la coquille, qui survit au bandeau.

- **Anciennes entrées non notées** (2026-11-17) : le propriétaire, passé d'une copie portable « Kairos Preview » 4.1
  à la beta 4.2.0 « Kairos », n'a pas pu remplacer son entrée de menu par le bandeau. Seule l'entrée notée dans
  `shortcuts.json` était comparée : une entrée « Kairos Preview » absente de l'état (refus, autre dossier de données)
  restait invisible pour la nouvelle copie. D'où les anciens noms, et l'identité de l'app figée par `validate_identity.py`
  (`distribution.md` § Identité de l'app) : changer un nom ou un identifiant demande désormais de prévoir la migration.
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
