# Mise à jour automatique (bureau)

_Rôle : laisser Kairos pour le bureau télécharger, vérifier et installer lui-même une nouvelle version ; aujourd'hui, le bandeau des
mises à jour ouvre seulement la page de la release sur GitHub. Fichiers existants touchés : `core/updates/UpdateCheck.kt`,
`core/model/Settings.kt`, `core/sync/SyncScope.kt`, `core/settings/SettingsForm.kt`, `desktopApp/.../DesktopUpdates.kt`,
`desktopApp/.../DesktopServices.kt`, `desktopApp/.../Main.kt`, `ui/.../app/Updates.kt`, `ui/.../settings/SettingsScreen.kt`, textes.
Nouveaux fichiers (noms proposés) : `core/updates/ReleaseAssets.kt`, `desktopApp/.../UpdateInstaller.kt`, `desktopApp/.../UpdateVerifier.kt`.
Tests : voir § Critères de succès ; tous les noms de tests de cette spec sont proposés._

État : issue #249, proposée par @pyroMain404 (PR #250). **Spécifiée le 2026-11-19, non implémentée.** Elle reste désactivée tant
que les releases Windows ne sont pas signées (#117, § Vérification).
Elle rouvre une décision de `mises-a-jour.md` : « Téléchargement, vérification de somme et remplacement automatiques » étaient hors
périmètre.

## 1. Besoin métier (cahier des charges)

### Objectif / problème

Qui voit le bandeau de mise à jour doit quitter Kairos, ouvrir GitHub, choisir le bon fichier, le télécharger et lancer le `.msi` à la
main. Beaucoup s'arrêtent là et restent sur une ancienne version. Kairos doit faire ces étapes lui-même, en restant sûr et facile à
couper.

### Comportement attendu (utilisateur)

- **Périmètre de la première version** : Windows, installé par le `.msi`. Tous les autres cas gardent le bandeau actuel avec
  « Télécharger » (§ Hors périmètre).
- **Mode par défaut : demander.** Le bandeau reste celui d'aujourd'hui (« Kairos X.Y.Z est disponible (tu as A.B.C). »), avec deux
  boutons :
  - « Mettre à jour » (nouveau, à la place de « Télécharger » quand la mise à jour automatique est possible) : Kairos télécharge la
    nouvelle version, la vérifie, se ferme, l'installe et redémarre ;
  - « Plus tard » : masque le bandeau pour cette version, comme aujourd'hui ; la version suivante le fait revenir.
- **Progression** : après « Mettre à jour », le bandeau montre l'avancement du téléchargement et un bouton d'annulation. « Plus tard »
  ou l'annulation arrêtent le téléchargement et suppriment le fichier partiel.
- **Désactivation** : l'interrupteur existant « Vérifier les nouvelles versions » (Réglages → Général → Mises à jour, activé par
  défaut) reste celui qui coupe tout. Coupé, Kairos ne vérifie pas, n'affiche aucun bandeau et ne télécharge jamais rien ;
  « Vérifier maintenant » marche toujours, comme aujourd'hui.
- **Mode automatique** : un nouvel interrupteur, « Installer les mises à jour automatiquement au démarrage », désactivé par défaut et
  grisé tant que la vérification est coupée.
  - Kairos télécharge et vérifie la nouvelle version en arrière-plan pendant qu'il tourne ; il ne l'installe **jamais** pendant qu'il
    tourne.
  - Au démarrage suivant, avant la première fenêtre, il installe la version vérifiée et redémarre, sans rien demander.
  - Dans ce mode, le bandeau n'apparaît pas ; la carte dit « Kairos X.Y.Z sera installé au prochain démarrage. » et propose
    « Redémarrer et mettre à jour ».
- **Échec** : un téléchargement, une vérification ou une installation qui échoue ne bloque jamais Kairos. Le bandeau dit que la mise à
  jour a échoué et propose « Télécharger » (la page de la release, comme aujourd'hui). En mode automatique, Kairos ne réessaie pas la
  même version à chaque démarrage ; une nouvelle version, ou « Mettre à jour » dans le bandeau, réessaie.
- **Release non signée ou invérifiable** : Kairos n'installe jamais un fichier qui échoue à un contrôle ; il revient au bandeau actuel
  avec « Télécharger ».
- **Vie privée** : le seul trafic nouveau est le téléchargement de deux fichiers de la release sur GitHub, l'installeur et
  `SHA256SUMS`. L'appel d'API de la vérification reste celui d'aujourd'hui ; rien d'autre ne quitte l'appareil. L'aide de la carte
  (`update_help`) dit désormais que Kairos peut télécharger et installer.

### Critères de succès

Chaque critère nomme le test (proposé) qui le prouve.

- Une liste de fichiers de release donne l'installeur et `SHA256SUMS` de `windows-x64` ; une adresse hors de
  `https://github.com/SKOHscripts/Kairos/releases/download/` est refusée (`ReleaseAssetsTest`).
- Un fichier dont le SHA-256 diffère, absent de `SHA256SUMS` ou de mauvaise taille est refusé et supprimé (`UpdateInstallerTest`).
- Un fichier sans signature Authenticode valide, ou signé par un autre éditeur que celui épinglé, est refusé (`UpdateVerifierTest`, avec
  un faux lecteur de signature).
- La mise à jour automatique n'est proposée que pour une installation Windows ni portable ni gérée (`SelfUpdateEligibilityTest`).
- « Mettre à jour » enchaîne téléchargement, vérification et installation, et lance l'assistant avant que Kairos ne se ferme ; si
  l'assistant ne démarre pas, Kairos reste ouvert et dit l'échec (`UpdateInstallerTest`).
- Le mode automatique n'installe jamais pendant que l'application tourne ; il installe au démarrage une version préparée et revérifiée,
  et ne réessaie pas une version en échec (`UpdateInstallerTest`).
- Vérification coupée : aucune requête, aucun téléchargement (`DesktopUpdatesTest`).
- Le bandeau montre « Mettre à jour » quand c'est possible, « Télécharger » sinon (`M5ScreensUiTest`).
- Le nouveau réglage est propre à l'appareil, jamais fusionné par la synchronisation (`SyncScopeTest.everySettingIsClassified`).
- Chaque nouveau texte existe dans les six langues (`StringsParityTest`).
- Preuve manuelle sur une machine Windows : installer un `.msi` plus ancien, publier une release de test, utiliser « Mettre à jour »,
  lire la version après le redémarrage.

### Hors périmètre

- **Zip portable** (marqueur `kairos-portable`) : § Décisions. Le bandeau garde « Télécharger ».
- **Linux (`.deb`)** et **macOS (`.dmg`)** : Linux demande `root` ; macOS n'est pas encore signé (#116).
- **Installations gérées** (`kairos-managed`, AUR) : le gestionnaire de paquets les met à jour ; Kairos n'y a pas de service de mise à
  jour, comme aujourd'hui.
- **Android** : #144 fermée « not planned » ; Android ne vérifie aucune version.
- Mises à jour différentielles, retour à une version plus ancienne, notes de version dans l'application, choix d'un canal (une beta
  continue de lire les préversions), service d'arrière-plan.
- Microsoft Store (#215) et `winget` : d'autres canaux, avec leurs propres mises à jour.

## 2. Solution technique

### Éligibilité

`SelfUpdate.eligible(osName, appPath, portable, managed)` (nouveau, pur, dans `desktopApp`) est vrai quand : `osName` commence par
« Windows », `jpackage.app-path` est posé, `PortableCopy.detect` rend `null` et `SystemPackage.detect()` est faux. Un lancement par
Gradle n'a pas de `jpackage.app-path` : il n'est pas éligible. `DesktopUpdates` reçoit le résultat en paramètre et l'expose comme
`installable`.

### Réglages

- `Settings.updateAutoInstall: Boolean = false`, à côté de `updateCheckEnabled` ; un champ avec valeur par défaut ne demande pas de
  migration des données (à confirmer avec `modele-donnees.md` à l'implémentation).
- `"updateAutoInstall"` entre dans `SyncScope.LOCAL_SETTINGS` : sinon le champ serait partagé, et un appareil pourrait activer les
  installations automatiques sur les autres.
- `SettingsForm` reçoit un `bool(...)` de plus ; `UpdatesCard` montre le nouvel interrupteur, grisé quand `updateCheckEnabled` est
  faux ; libellé `setting_update_auto` (proposé).

### Fichiers de la release

- `UpdateCheck.parseFor` garde ses règles ; la release qu'il rend gagne `assets` : `name`, `browser_download_url` et `size` de la
  réponse de l'API.
- `ReleaseAssets.pick(assets, id)` (nouveau, pur) rend l'installeur et `SHA256SUMS`. Pour `windows-x64`, l'installeur est
  `Kairos-windows-x64.msi`, le nom posé par `kmp-release.yml` (artefacts `dist-*`, `distribution.md` § CI et release).
- Une adresse doit commencer par `https://github.com/SKOHscripts/Kairos/releases/download/` : plus strict que la règle
  `https://github.com/` de `html_url`, puisque Kairos exécute désormais le fichier.
- Une release sans ces deux fichiers n'est pas installable : le bandeau montre « Télécharger ».

### Téléchargement et préparation

- **Emplacement** (correction de la revue du 2026-11-19) : `<racine Kairos>/updates/<version>/`, la racine locale de
  `DataDirectory.kairosRoot` (`%LOCALAPPDATA%\Kairos` sous Windows), **jamais le dossier de données** : celui-ci peut être choisi dans
  un dossier synchronisé par un nuage, qui recevrait plus de 100 Mo et pourrait modifier le fichier vérifié. Même règle que les secrets
  des connexions en ligne (`connexions-en-ligne.md` § Secrets).
- `DesktopUpdates.download(release, progress)` utilise le `HttpClient` existant (proxy du système, redirections, 10 s pour se
  connecter), sans le délai total de 10 s de `httpGet`, trop court pour un gros fichier.
- Le corps s'écrit dans `Kairos-windows-x64.msi.part`, renommé seulement quand la taille correspond ; un téléchargement annulé ou en
  échec supprime le `.part`.
- À chaque démarrage, Kairos supprime les dossiers de `<racine Kairos>/updates/` sauf celui de la version préparée, si elle est plus
  récente que la version installée.

### Vérification

1. Le SHA-256 du fichier est celui de sa ligne dans `SHA256SUMS` (format de `sha256sum` : empreinte, deux espaces, nom).
2. Authenticode : `UpdateVerifier` lit la signature du `.msi` et exige l'état `Valid` et un sujet de signataire égal à la constante
   `SIGNER_SUBJECT`. Sa valeur vient du certificat de SignPath, une fois le projet accepté (#117) ; elle est inconnue aujourd'hui.
3. Le mode automatique refait les deux contrôles au moment d'installer : le fichier a pu changer sur le disque depuis sa préparation.

L'étape 1 protège seulement d'un téléchargement abîmé : `SHA256SUMS` vient de la même release que l'installeur, une release hostile
peut falsifier les deux. L'étape 2 garantit l'authenticité ; la mise à jour automatique reste donc coupée (bandeau avec « Télécharger »)
tant que les releases ne sont pas signées.

### Installation (Windows)

`UpdateInstaller.apply(msi)` écrit `apply-update.ps1` dans le dossier de préparation et le lance :
`powershell.exe -NoProfile -ExecutionPolicy Bypass -File apply-update.ps1 <pid> <msi> <Kairos.exe> <journal>`.

Le script :
1. attend la fin du processus `<pid>`, 60 s au plus : le verrou `SingleInstance` et les fichiers du programme sont alors libérés ;
2. lance `msiexec /i <msi> /passive /norestart /l*v <journal>` et attend ; le code 0 ou 3010 est un succès ;
3. relance `Kairos.exe` depuis le chemin de `jpackage.app-path` ;
4. pour tout autre code, écrit `update-failed.json` (`version`, `exitCode`) et relance quand même `Kairos.exe`.

Kairos vérifie que l'assistant tourne encore environ une seconde après son lancement, et seulement alors appelle
`exitApplication()` ; sinon il reste ouvert et dit l'échec. Au démarrage suivant, `DesktopUpdates` lit `update-failed.json`, le
reporte dans l'état de préparation comme `installFailed` et le supprime.

Faits pas encore vérifiés ; la première tâche de l'implémentation est un prototype qui les prouve sous Windows :
- le `.msi` de jpackage se met à jour sur place avec ces options, y compris d'une beta vers sa finale (numérotation des paquets de bureau
  `X.Y.(Z*100 + s)`, `distribution.md` § Versionnage) ;
- Windows Installer revient à la version installée quand une mise à jour échoue ;
- `powershell.exe` peut lancer le script sur un poste géré (stratégie d'exécution, AppLocker).

### Mode automatique

- `UpdateWatcher` : quand une vérification trouve une release plus récente et que `updateAutoInstall` est actif, il appelle
  `DesktopUpdates.stage()` : téléchargement, vérification, puis `staged` noté dans l'état de préparation.
- Au démarrage, après `SingleInstance.acquire` et avant la première fenêtre, Kairos lit les réglages ; si `updateAutoInstall` est actif,
  que `staged` est plus récent que `AppVersion.current` et que les deux contrôles passent à nouveau, il appelle `apply` et se ferme.
- La base s'ouvre aujourd'hui dans `application { }` de `Main.kt` (`DesktopServices.open`) : le point d'accroche exact se décide à
  l'implémentation.
- `installFailed` égal à la version préparée bloque son installation automatique.

### État et API

- **État de préparation** : `<racine Kairos>/updates/state.json` (`staged`, `installFailed`), à côté des fichiers préparés, pas dans
  `updates.json` du dossier de données, partagé par un éventuel nuage avec d'autres machines (correction de la revue du 2026-11-19).
- `UpdateStatus` gagne `Downloading(version, percent)`, `Ready(version)` (préparée) et `Installing` ; `Failed` garde son sens pour la
  vérification.
- `UpdateService` gagne `val installable: Boolean`, `suspend fun install()`, `suspend fun stage()` et `fun cancel()` ; Android garde
  `updates = null`.
- `UpdateBanner` appelle `updates.install()` quand `installable`, ouvre l'adresse de la release sinon.

### Interface et textes

- Textes (six langues, apostrophe typographique `’`) : `update_install`, `update_downloading`, `update_installing`,
  `update_install_failed`, `update_pending`, `update_restart_now`, `setting_update_auto` ; `update_help` change.
- La barre de progression et le bouton d'annulation ont un libellé pour les lecteurs d'écran (`accessibilite.md`).
- Le bandeau reste neutre (`surfaceContainerHigh`), comme le veut le design system ; un échec utilise le même bandeau avec un texte,
  pas le conteneur d'erreur.

### Diagnostic

Lignes du journal local (`bureau-avance.md`) : `update download ok|failed <code>`, `update verify sha=ok|bad sig=ok|bad|unsigned`,
`update apply started|failed`. Ni chemin de fichier, ni adresse dans le journal.

### Décisions et pièges tracés

- **Préparer en arrière-plan, installer au démarrage suivant** (demande de l'auteur : ne jamais mettre à jour une application
  ouverte). Écarté : vérifier et télécharger au démarrage, qui retarderait la première fenêtre du temps du téléchargement.
- **Deux interrupteurs, pas un réglage à trois valeurs** : `updateCheckEnabled` reste celui qui coupe tout ; les utilisateurs existants
  gardent leur choix, sans migration.
- **Réglage local** : partagé, il laisserait un appareil activer l'installation de code sur tous les appareils liés.
- **Hors du dossier de données** (revue du 2026-11-19) : fichiers préparés et état de préparation dans la racine locale de Kairos,
  jamais dans un dossier qu'un nuage peut synchroniser.
- **Pas de zip portable dans la première version** :
  - remplacer les fichiers d'un dossier en cours d'exécution demande un assistant d'échange qui gère ses propres verrous ;
  - `mise` installe ce zip (`mise install github:SKOHscripts/Kairos@4.1.0` a choisi `Kairos-windows-x64-portable.zip` lors d'un essai
    du 2026-11-19) : une mise à jour automatique modifierait les fichiers du dossier versionné de `mise`, ce qui casse son suivi ;
  - à rouvrir avec un marqueur comme `kairos-managed` pour les installations de `mise`.
- **Fermer d'abord, installer ensuite** : les fichiers du programme sont verrouillés tant que Kairos tourne, et le verrou d'instance
  unique empêcherait le redémarrage ; d'où l'assistant.
- **Pas de boucle de tentatives** : une version en échec ne doit pas relancer `msiexec` à chaque démarrage ; `installFailed` l'arrête.
- **Écartés** : Velopack ou Squirrel (un second format d'installeur et une dépendance, à côté du `.msi` signé existant) ; MSIX ou le
  Store (décision à part, #215) ; un service Windows ou une tâche planifiée (plus de droits et de surface que le besoin) ; une
  installation contrôlée par l'empreinte seule (une release hostile la contourne).
- **Aucune permission nouvelle** : le bureau n'a pas de modèle de permissions ; le seul trafic nouveau va vers `github.com`, comme la
  vérification.

### Décisions ouvertes pour le propriétaire

| Question | Défaut proposé |
|---|---|
| Épingler `SIGNER_SUBJECT` et activer la mise à jour automatique | Attendre l'acceptation SignPath (#117) ; « Télécharger » d'ici là |
| Prendre en charge le zip portable | Non, dans la première version |
| Linux et macOS | Non, dans la première version ; à revoir avec #116 |

### Impacts sur les specs existantes (à appliquer à l'implémentation)

- `mises-a-jour.md` : retirer la ligne « Hors périmètre » sur le téléchargement et le remplacement automatiques ; décrire le nouveau
  bouton du bandeau, le nouvel interrupteur, les nouveaux membres de `UpdateService` et les nouveaux états ; ajouter la décision.
- `reglages.md` : le nouvel interrupteur et sa dépendance à celui de la vérification.
- `synchronisation.md` et `modele-donnees.md` : `updateAutoInstall` parmi les réglages locaux.
- `distribution.md` : les noms des fichiers de release et la signature deviennent un contrat avec l'application ; `SIGNER_SUBJECT` dans
  § Signature Windows.
- `bureau-avance.md` : le dossier `updates/` de la racine locale et les nouvelles lignes du journal.
- `raccourci-portable.md` : une ligne, les copies portables ne se mettent pas à jour seules.
- `i18n.md` et `accessibilite.md` : les nouveaux textes et libellés.
- `site/en/privacy/index.html` (politique de confidentialité, `publication.md`) : le téléchargement de l'installeur depuis
  GitHub, avec la date « Last updated ».
- `liste-fonctionnalites.md`, `README.md`, `README.fr.md` et `FeatureCatalog` : c'est une fonctionnalité ; ajouter la carte, la puce
  et la ligne du tableau dans les deux langues.
