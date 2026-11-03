# Distribution : versions, Android, bureau, web, CI

_Rôle : comment Kairos est versionné, construit, testé, empaqueté et
publié sur chaque cible. Fichiers couverts : `kmp/gradle.properties`
(version), `kmp/core/src/commonMain/.../AppVersion.kt`, `kmp/androidApp/`,
`kmp/desktopApp/` (dont `Main.kt`, `DataDirectory.kt`, `SingleInstance.kt`,
`CrashLog.kt`, `SelfTest.kt`, `icons/`), `kmp/webApp/`,
`kmp/tools/make_app_icons.py`, `packaging/aur/` (paquet Arch Linux), `.github/workflows/kmp.yml`,
`.github/workflows/kmp-release.yml` et `.github/workflows/pages.yml` (sa
partie version web). Les services de fichiers et de sauvegarde de chaque plateforme
sont décrits par `export-import.md` ; F-Droid, fiches Fastlane et
page de téléchargement par `publication.md`._

État : **jalon M7** (`3.0.0`, bascule : version de référence du dépôt).

## 1. Besoin métier (cahier des charges)

### Objectif / problème

Kairos doit s'installer, ou simplement se lancer, partout où ses
utilisateurs travaillent :

- sur Android, depuis F-Droid ou l'APK des releases ;
- sur un poste Windows, Linux ou macOS, par un installeur classique ;
- sur **Arch Linux** et ses dérivées, par le gestionnaire de paquets, qui le tient à jour (4.0.0) ;
- sur un **poste professionnel verrouillé**, sans droit d'installation : par un
  **zip portable** ou par la **version web** dans Edge ou Chrome.

Une préversion doit s'installer **à côté** de la version stable, sans la
remplacer ; une version stable s'installe par-dessus la précédente.

### Comportement attendu (utilisateur)

- **Chaque version** a un tag : une préversion (`vX.Y.Z-alpha.N`,
  `vX.Y.Z-beta.N`) est publiée en **préversion GitHub**, une version finale
  (`vX.Y.Z`) en release GitHub, avec tous les fichiers.
- **Android** : un APK. Tant que la version est une préversion, l'application
  s'appelle « Kairos Preview » et s'installe à côté de la version stable. Aucune
  permission réseau jusqu'à la 3.x (rouvert pour la 4.0.0 : un seul APK avec `INTERNET`, fonctions en ligne désactivées par défaut,
  `connexions-en-ligne.md`) ; deux permissions pour le chrono (notifier, reprendre
  après un redémarrage), voir `temps-reel-chrono.md`.
- **Bureau** : un installeur par OS (`.msi`, `.deb`, `.dmg`), qui n'exige pas
  de droits administrateur sous Windows, et un **zip portable** par OS : on
  dézippe, on lance, rien n'est installé.
- **Arch Linux** (4.0.0, choix du propriétaire du 2026-11-02) : un paquet AUR **`kairos-bin`**
  (`yay -S kairos-bin`, ou tout assistant AUR), qui reprend le `.deb` de la release. Kairos apparaît dans le menu des
  applications et se lance par la commande `kairos`. Chaque version stable arrive par `yay -Syu` peu après son tag,
  sans geste du propriétaire. Installé ainsi, Kairos ne cherche pas lui-même de nouvelle version (ni bandeau ni carte
  « Mises à jour », `mises-a-jour.md`) : le gestionnaire de paquets s'en charge. Les données sont les mêmes que
  celles du `.deb` (`~/.local/share/Kairos/v3`) : passer de l'un à l'autre ne perd rien.
- L'application de bureau s'ouvre sur une fenêtre « Kairos » (ou « Kairos
  Preview »). La relancer alors qu'elle est ouverte ramène la fenêtre
  existante au premier plan, sans seconde instance.
- Un crash laisse une trace lisible dans un fichier, sans terminal.
- **Web** : la même interface dans le navigateur. Un navigateur trop ancien
  affiche un message clair plutôt qu'un écran figé.
- **Build de test avant une release** (demande du 2026-11-03) : depuis l'onglet Actions, on lance à la main un build d'**un
  commit précis** de `main` (par défaut le dernier), en choisissant les plateformes (Android, bureau, web), pour essayer la
  version avant de poser le tag. L'APK de test est signé avec la clé des releases : il s'installe **par-dessus** la
  version en place et garde ses données, comme le fera la release. Rien n'est publié : ni release, ni AUR, ni signature
  Windows ; les fichiers sont téléchargeables dans les artefacts du lancement, qui portent le commit dans leur nom.

### Critères de succès

- Le tag et la version déclarée ne peuvent pas diverger (la release échoue).
- Chaque `versionCode` Android est strictement supérieur au précédent.
- Chaque image de bureau empaquetée est **lancée en CI** sur son OS (auto-test)
  avant publication ; un auto-test en échec bloque la release.
- Un tag produit un APK **signé**, avec toujours la même clé (chaque version s'installe par-dessus la précédente).
- Les fichiers publiés ont des noms fixes (liens stables) et un `SHA256SUMS`.
- Le paquet AUR est **construit, installé avec ses dépendances et lancé** (auto-test) dans un conteneur Arch Linux
  avant d'être poussé ; un échec bloque sa publication, jamais la release GitHub. Il vérifie la somme SHA-256 du
  `.deb` publié.
- Un build de test ne construit que des commits de la branche depuis laquelle il est lancé, passe les mêmes tests et
  auto-tests qu'une release, et ne publie jamais rien.

### Hors périmètre (à ce jalon)

- Signature et notarisation macOS : différées (plan § 6.2), **décision du 2026-10-29** (issue #116) : pas de compte
  Apple Developer pour l'instant (§ Décisions). Gatekeeper affiche « développeur non identifié » au premier lancement ;
  le README et la page de téléchargement gardent la marche à suivre (Réglages Système → Confidentialité et sécurité →
  « Ouvrir quand même »).
- Signature Authenticode Windows : **préparée le 2026-10-29** (issue #117, § Signature Windows) par SignPath
  Foundation, active dès que le propriétaire a été accepté et a posé les secrets ; d'ici là, et pour les forks,
  l'installeur reste non signé et SmartScreen affiche un avertissement.
- Mise à jour intégrée : jamais sur Android ; sur le bureau, simple
  vérification (`mises-a-jour.md`).
- F-Droid, fiches et page de téléchargement : `publication.md`.
- Arch Linux : dépôts officiels (`extra`, il faudrait qu'un mainteneur de confiance d'Arch adopte le paquet), paquet
  compilé depuis les sources, préversions (`kairos-preview-bin`), ARM. Autres distributions (Flatpak, RPM) : non
  prévues.

## 2. Solution technique

### Versionnage

- **Source unique** : `kmp/gradle.properties`, `kairos.versionName` et
  `kairos.versionCode`. Jalon M0 : `3.0.0-alpha.1` (`29001`) ; jalon M1 :
  `3.0.0-alpha.2` (`29002`) ; jalon M2 : `3.0.0-alpha.3` (`29003`) ;
  jalon M3 : `3.0.0-alpha.4` (`29004`) ; jalon M4 : `3.0.0-alpha.5`
  (`29005`) ; jalon M5 : `3.0.0-alpha.6` (`29006`) ; jalon M6 (fonctions
  complètes, publication prête) : `3.0.0-beta.1` (`29501`) ; jalon M7
  (bascule) : `3.0.0` (`30000`).
  Versions suivantes : `3.1.0` (`30100`), `3.2.0` (`30200`), `3.3.0` (`30300`), puis `3.4.0` (`30400`, issue #64 :
  horaires par jour de la semaine, durées apprises, catalogue de fonctionnalités et première tâche guidée, ergonomie et
  réglages, retrait de l'import de l'ancienne version ; notes `30400.txt` en français et en anglais), puis `3.5.0` (`30500`, issue #64 : plusieurs plages par jour et contextes, carte Statistiques « Quand tu travailles chaque type » ; notes `30500.txt` en français et en anglais), puis `3.6.0` (`30600`, issue #64 : réglage « Meilleures heures », le planning préfère les heures habituelles ou préférées de chaque type ; notes `30600.txt` en français et en anglais), puis `3.7.0` (`30700` : thème sombre, issue #72 ; filtres et export des statistiques, #114 et #113 ; déplacer depuis la vue Semaine, #106 ; fériés des créneaux « jours ouvrés », #109 ; Réglages par catégories, PR #124 ; guide qui retient et montre l'endroit, #94 et #95 ; notes `30700.txt` en français et en anglais), puis `3.8.0` (`30800` : vue Mois, #107 ; créneaux mensuels, #108 ; une seule occurrence d'un créneau, #110 ; évolutions des statistiques, #112 ; corriger les estimations, #101 ; rappels, #103 ; première tâche d'équipe guidée, #97 ; notes `30800.txt` en français et en anglais), puis `3.9.0` (`30900` : noms des niveaux, #93 ; meilleures heures mesurées par l'efficacité, #105 ; accompagnement des récurrences et des Réglages, #98 ; jours et heures par membre, #104 ; notes dans les échanges, #120 ; propositions d'un membre, #121 ; dossier des données, diagnostic et proxy du bureau, #115 ; lien « Voir la dernière version » sur Android, #143 ; notes `30900.txt` en français et en anglais), puis `3.10.0` (`31000` : import d'un calendrier `.ics`, #136 ; fichier lié sur le bureau et Android, #137 ; calendriers du téléphone, #138 ; notes `31000.txt` en français et en anglais), puis `3.11.0` (`31100` : espagnol, allemand, russe et italien, #75 ; fiches du magasin et page web dans ces langues ; notes `31100.txt` dans les six langues), puis `3.12.0` (`31200` : widgets Android, #150 ; récapitulatif du jour, #151 ; couleur par défaut océan ; notes `31200.txt` dans les six langues), puis `3.13.0` (`31300` : liste complète des fonctionnalités, #153 ; avis des mois sautés, #111 ; démonstration interactive, #96 ; décision sur la signature macOS, #116, et signature Windows préparée, #117 ; notes `31300.txt` dans les six langues), puis `3.14.0` (`31400` : type choisi sur place dans « À traiter » et « À qualifier », durée posée une fois la tâche qualifiée, facette « Incomplètes », type dans l'essentiel de la fiche et dans la démonstration, barre de défilement du menu des types, #166 ; notes `31400.txt` dans les six langues).
- Formats acceptés (`AppVersion.parse`) : `X.Y.Z`, `X.Y.Z-alpha.N`,
  `X.Y.Z-beta.N` (préfixe `v` toléré ; `Y`, `Z` ≤ 99 ; `N` de 1 à 499).
- `versionCode` (`AppVersion.versionCode`) : `X*10000 + Y*100 + Z` ; alpha :
  − 1000 + N ; beta : − 500 + N. Ainsi 3.0.0-alpha.1 = 29001 <
  3.0.0-beta.1 = 29501 < 3.0.0 = 30000.
  `AppVersionTest.buildVersionIsConsistent` échoue si `gradle.properties` ne
  suit pas la formule.
- `KairosBuild` (généré par `core`) expose la version au code commun.
- Le tag de release doit valoir `v` + `kairos.versionName` (job `version` de
  `kmp-release.yml`).
- F-Droid lit la version dans `kmp/gradle.properties` par `UpdateCheckData`
  (`publication.md`) ; chaque versionCode publié a sa note de version
  Fastlane dans les deux langues.

### Android (`kmp/androidApp`)

- `applicationId` `com.skohscripts.kairos`, suffixé
  `.preview` si la version contient `-` ; nom `Kairos Preview` ou `Kairos`
  (`resValue app_name`). La propriété Gradle `kairos.preview` (`true` ou
  `false`) force l'un ou l'autre (vérification F-Droid d'une bêta,
  `publication.md`).
- `minSdk 26`, `targetSdk 36`, `compileSdk 37`, Java 21.
- `MainActivity` (`ComponentActivity`) : `enableEdgeToEdge()`, puis
  `setContent { KairosApp(Platform.ANDROID) { services.await() } }`. Les
  services (`AndroidServices.open`) sont ouverts **une fois par process**
  (`Deferred` du compagnon, portée `SupervisorJob` + `Dispatchers.Default`) :
  une rotation retrouve la même base. Les sélecteurs de fichiers passent par
  l'activité courante (sur le fil principal).
- Base : `AndroidSqliteDriver` sur `kairos.db` avec un schéma factice
  (`DeferredSchema` : version du schéma, création et migration vides), la
  création réelle étant faite par `KairosStore.open` (`modele-donnees.md`).
  Exemples dans la langue du système (`Locale.getDefault()`).
- Manifeste : **aucune permission réseau** (pas de `INTERNET`) jusqu'à la 3.x ; **rouvert le 2026-10-26 par
  `connexions-en-ligne.md`** : la 4.0.0 déclare `INTERNET`, un seul APK, sans variante ni drapeau de construction (à implémenter) ;
  `POST_NOTIFICATIONS` (demandée à l'opt-in des alertes) et
  `RECEIVE_BOOT_COMPLETED` pour le chrono (`temps-reel-chrono.md`), avec
  ses deux récepteurs (`ChronoReceiver` non exporté, `BootReceiver`) ;
  `READ_CALENDAR`, **facultative**, demandée seulement quand l'utilisateur
  active « Utiliser les calendriers du téléphone » (3.10.0,
  `connexions-externes.md`) : ce n'est pas une permission réseau ;
  les widgets de l'écran d'accueil (3.12.0, `widgets-android.md`), sans permission : `NowWidgetProvider` et
  `DayWidgetProvider` (exportés, `APPWIDGET_UPDATE`), `WidgetActionReceiver` (non exporté) et `DayWidgetService`
  (réservé au système par `BIND_REMOTEVIEWS`) ;
  activité `singleTop` (la notification du chrono et les widgets la rouvrent) ;
  `enableOnBackInvokedCallback="true"` (geste retour prédictif) ;
  `windowSoftInputMode="adjustResize"`. Base et suivi du chrono ouverts une
  fois par processus (`KairosProcess`), quel que soit le point d'entrée
  (activité, alarme, redémarrage). `KairosApplication` (`android:name`) pose le
  rapport de plantage local avant tout composant : `demarrage.md` § Rapport de
  plantage (toujours sans réseau ni permission).
- Thème de fenêtre `Theme.Kairos` :
  `forceDarkAllowed=false` ; avant l'API 31, `windowBackground` =
  `kairos_launch_background` (fond `kairos_bg` = `#FAF8FF` + cadran vide
  `kairos_splash_dial` centré 288 dp) ; API 31+ (`values-v31`), écran de
  démarrage système avec ce même cadran, statique. Ce ne sont que les relais
  de la page de démarrage Compose (`demarrage.md`), qui anime l'aiguille.
  Système en sombre : `values-night/` et `values-night-v31/` (fond `kairos_bg` = `#121318`,
  icônes des barres claires, `apparence.md` § Thème sombre).
- Icône adaptative `mipmap-anydpi/ic_launcher.xml` (fond uni océan profond `#1C2F59` = `ic_launcher_background`, premier
  plan `ic_launcher_foreground` : le logo complet, cadran crème compris ; changé le 2026-10-27, le fond était le cadran
  `#FFEEDC` ; `apparence.md` § Icônes et supports fixes). Les fonds de démarrage (`#FAF8FF` clair, `#121318` sombre) sont les
  `surface` du thème océan par défaut, au lieu de `#FFF8F4` et `#18120B` du miel.
- Release : R8 (`isMinifyEnabled`, `isShrinkResources`) ; signature par les
  variables `KAIROS_KEYSTORE_FILE`, `KAIROS_KEYSTORE_PASSWORD`,
  `KAIROS_KEY_ALIAS`, `KAIROS_KEY_PASSWORD` si présentes, APK non signé
  sinon ; `dependenciesInfo` désactivé (bloc illisible par F-Droid, nuisible
  aux builds reproductibles).
- Taille : environ 1,3 Mo au jalon M0, 2 Mo à la `3.0.0-beta.1`.

### Bureau (`kmp/desktopApp`)

- `main(args)` :
  1. dossier de données (`DataDirectory.resolve`) ;
  2. journal de crash (`CrashLog.install`) ;
  3. `--self-test[=dossier]` : auto-test puis sortie ;
  4. instance unique (`SingleInstance.acquire`), sinon sortie immédiate ;
  5. copie portable (`PortableCopy.detect`) et classe de fenêtre X11
     (`WindowClass.apply`), voir `raccourci-portable.md` ; installation tenue par un gestionnaire de paquets
     (`SystemPackage.detect`, § Paquet Arch Linux) ;
  6. fenêtre 1200 × 800 dp (minimum 360 × 480 px), titre `Kairos` ou
     `Kairos Preview` (propriété JVM `kairos.preview`), icône = logo,
     contenu `KairosApp(Platform.DESKTOP) { DesktopServices.open(dataDir, …,
     portable) }`.
- **Services** (`DesktopServices.open`) : `JdbcSqliteDriver` sur
  `<données>/kairos.db` (ou en mémoire pour l'auto-test), exemples dans la
  langue de la JVM, dossier des données affiché dans la carte Données.
- **Dossier de données** : `--data-dir=` puis `KAIROS_DATA_DIR` s'ils sont
  donnés, puis le dossier choisi dans les Réglages (`bureau-avance.md`, 3.9.0),
  sinon la racine Kairos de `platformdirs` + `v3` :
  - Windows : `%LOCALAPPDATA%\Kairos\v3` ;
  - macOS : `~/Library/Application Support/Kairos/v3` ;
  - Linux : `$XDG_DATA_HOME/Kairos/v3`, par défaut `~/.local/share/Kairos/v3`.
- **Instance unique** : `FileChannel.tryLock` sur `instance.lock` (libéré par
  le système si le process meurt). Une seconde instance écrit le fichier
  `activate` et se ferme ; l'instance principale le surveille
  (`WatchService`, thread `kairos-activation`) et ramène sa fenêtre au premier
  plan (dé-minimise, `toFront`). Aucun port réseau.
- **Journal de crash** : toute exception non rattrapée est ajoutée à
  `crash.log` (date, fil, version, OS, Java, pile).
- **Auto-test** (`SelfTest`) : vérifie la version (`AppVersion` et
  `KairosBuild` cohérents), écrit puis efface un fichier dans le dossier de
  données, ouvre une base **en mémoire** avec les exemples (pilote JDBC
  réellement chargé), et rend hors écran (`ImageComposeScene`, 1000 px de
  haut, 10 images pour laisser charger polices et chaînes) l'application en
  1200 px et en 420 px de large, les Réglages et « À propos » en 900 px,
  puis la vue Jour entière en 1200 × 3000 px et 420 × 5000 px (agenda,
  sections, frise), et une seconde base en mémoire avec un chrono en marche
  (1200 × 1000 px). Avec `=dossier`, les rendus y sont écrits
  (`desktop-wide.png`, `desktop-narrow.png`, `desktop-settings.png`,
  `desktop-about.png`, `desktop-day-full.png`,
  `desktop-day-full-narrow.png`, `desktop-day-chrono.png`,
  `desktop-notes.png`, `desktop-week.png`, `desktop-week-narrow.png`,
  `desktop-stats.png`, puis les Réglages par catégories, `reglages.md` : `desktop-settings-narrow.png`,
  `desktop-settings-tracking-narrow.png`, `desktop-settings-advanced.png`), une base avec un calendrier importé (aperçu,
  vues Jour, Semaine et Mois, carte « Calendriers » : `desktop-calendar-*.png`, `connexions-externes.md`), puis, sur une base où la gestion d'équipe est activée,
  l'espace Équipe (`desktop-team.png`, `desktop-team-narrow.png` en 360 px,
  `desktop-team-settings.png`, et, depuis le jalon E2, l'écran Équipe, la
  fiche d'un membre et l'éditeur d'absence, chacun en 1200 et 360 px :
  `desktop-team-members[-narrow].png`, `desktop-team-member-sheet[-narrow].png`,
  `desktop-team-absence[-narrow].png`, `equipe.md` ; une démonstration interactive à l'étape de la priorité,
  `desktop-demo.png` et `desktop-demo-narrow.png` en 360 px, `visite-guidee.md` § Visite interactive). L'auto-test
  n'affiche pas l'accueil du premier
  lancement et ne vérifie pas les mises à jour (aucun réseau). Code de
  sortie 0 ou 1.
- **Empaquetage** (plugin Compose Desktop, jpackage, runtime Java réduit
  embarquant `java.instrument`, `java.management`, `java.net.http` (depuis
  M5, vérification des mises à jour), `java.sql`, `jdk.localedata` (noms de
  mois et de jours français du sélecteur de dates Material 3 : sans lui, le
  runtime réduit retombe sur l'anglais, constaté au test exploratoire du
  2026-10-11), `jdk.unsupported` en plus des modules détectés) :
  - formats `Msi`, `Deb`, `Dmg` ; `createDistributable` produit l'image
    portable ; la release y écrit le marqueur `kairos-portable` (Linux,
    Windows) juste avant d'en faire l'archive, après les installeurs
    (`raccourci-portable.md`) ;
  - options de la JVM : `-Dkairos.preview=…` et `--add-opens
    java.desktop/sun.awt.X11=ALL-UNNAMED` (classe de fenêtre X11) ;
  - version finale : produit `Kairos`, version `X.Y.Z` ; préversion : produit
    distinct `Kairos Preview` (paquet Linux `kairos-preview`, bundle macOS
    `com.skohscripts.kairos.preview`, autre `upgradeUuid` Windows), version
    `1.0.<versionCode>` car MSI et DMG n'acceptent que des numéros ;
  - Windows : installation **par utilisateur** (`perUserInstall`, sans droit
    administrateur), raccourci et menu ; `upgradeUuid` fixe par produit ;
  - icônes `desktopApp/icons/kairos.{ico,png,icns}` (`tools/make_app_icons.py`) ;
  - ProGuard désactivé.

### Signature Windows (SignPath Foundation, issue #117)

- **Quoi** : l'installeur `Kairos-windows-x64.msi` et le lanceur `Kairos.exe` (« Kairos Preview.exe » pour une
  préversion) qu'il contient, ainsi que le lanceur du zip portable, reçoivent une signature Authenticode horodatée.
  Le certificat est celui de **SignPath Foundation**, qui apparaît comme éditeur (offre gratuite des projets libres) ;
  aucune clé n'est dans le dépôt ni sur le runner.
- **Quand** : dans le job `desktop` de `kmp-release.yml`, sur Windows, **sur un tag seulement** et seulement si
  `SIGNPATH_READY` est vrai (secret `SIGNPATH_API_TOKEN` et variable `SIGNPATH_ORGANIZATION_ID` présents). Sinon les
  trois étapes sont sautées et la construction reste celle d'avant, non signée (forks, contributeurs, lancement
  manuel, préversions sans configuration).
- **Comment** : après « Nommer les fichiers », `actions/upload-artifact` envoie le `.msi` et le zip portable comme
  artefact `unsigned-windows-x64` (préfixe distinct de `kairos3-` : la release ne le reprend pas) ;
  `signpath/github-action-submit-signing-request@v3` soumet la demande (projet `vars.SIGNPATH_PROJECT_SLUG`, `Kairos`
  par défaut ; politique `vars.SIGNPATH_SIGNING_POLICY_SLUG`, `release-signing` par défaut), attend l'**approbation
  manuelle** une heure au plus, et dépose les fichiers signés à la place des non signés dans `out`. Une étape PowerShell
  vérifie ensuite (`Get-AuthenticodeSignature`) que le `.msi` et le lanceur du zip portable ont une signature valide :
  sinon la release échoue. L'artefact `kairos3-windows-x64`, donc la release et `SHA256SUMS`, portent les fichiers
  signés. Le job a `actions: read` (l'action lit l'artefact par l'API des Actions).
- **Configuration d'artefact** : `.signpath/artifact-configuration.xml`, à recopier dans le projet SignPath (SignPath ne
  la lit pas dans le dépôt) : racine `<zip-file>` (l'artefact téléversé), signature profonde du `.msi` (lanceur
  `**/Kairos*.exe`, puis le `.msi` lui-même) et du lanceur du zip portable.
- **À faire par le propriétaire** (rien de cela ne peut se faire depuis le dépôt) :
  1. Déposer la candidature sur signpath.org (licence MIT, construction depuis ce dépôt public par GitHub Actions).
  2. Une fois accepté, publier la **politique de signature** exigée par SignPath Foundation, sous le titre « Code
     signing policy » dans le README et sur la page de téléchargement : « Free code signing provided by SignPath.io,
     certificate by SignPath Foundation », les rôles (auteurs, relecteurs, approbateurs : le propriétaire), et la
     déclaration de confidentialité (Kairos ne transmet rien sur le réseau sans demande de l'utilisateur,
     `connexions-en-ligne.md`). Elle n'est pas publiée avant l'acceptation : elle affirmerait une signature qui
     n'existe pas encore.
  3. Dans SignPath : organisation, projet `Kairos`, configuration d'artefact ci-dessus, politique `release-signing`
     (approbation manuelle), système de construction de confiance « GitHub.com » lié au projet, application GitHub
     SignPath installée sur le dépôt ; authentification à deux facteurs pour tous les membres (SignPath et GitHub).
  4. Dans le dépôt GitHub : secret `SIGNPATH_API_TOKEN`, variable `SIGNPATH_ORGANIZATION_ID` (et, si les noms
     diffèrent, `SIGNPATH_PROJECT_SLUG`, `SIGNPATH_SIGNING_POLICY_SLUG`).
  5. Au premier tag signé, approuver la demande dans SignPath, puis vérifier l'installeur publié ; alors seulement,
     retirer du README et de `site/` la consigne SmartScreen (« Informations complémentaires » → « Exécuter quand
     même »), dans une petite PR de suivi. La réputation SmartScreen d'un nouvel éditeur se construit aux
     téléchargements : l'avertissement peut persister quelque temps.

### Paquet Arch Linux (AUR, `packaging/aur/`, 4.0.0)

- **Quoi** : le paquet AUR `kairos-bin` (base de paquet du même nom), x86-64, versions stables seulement. Le dépôt
  garde le gabarit `packaging/aur/PKGBUILD` (`pkgver=@PKGVER@`, `sha256sums=('@SHA256@')`) et deux scripts ; l'AUR
  ne reçoit que le `PKGBUILD` rempli et son `.SRCINFO`.
- **`PKGBUILD`** :
  - source : `Kairos-<version>-linux-x64.deb::https://github.com/SKOHscripts/Kairos/releases/download/v<version>/Kairos-linux-x64.deb`,
    avec sa somme SHA-256 (le nom local porte la version : le cache d'un assistant AUR ne resert pas l'ancien) ;
  - `package()` : extrait `data.tar.*` du `.deb` (que `makepkg` a déjà ouvert) dans `$pkgdir`, donc l'image jpackage
    dans `/opt/kairos` ; y ajoute le marqueur `/opt/kairos/lib/app/kairos-managed` (§ Décisions) ; lien
    `/usr/bin/kairos` → `/opt/kairos/bin/Kairos` ; entrée de menu `/usr/share/applications/kairos.desktop`
    (`Exec=kairos`, `Icon=kairos`, `Categories=Office;ProjectManagement;`, `StartupWMClass=kairos`, la classe de
    fenêtre de `WindowClass`, commentaire anglais et `Comment[fr]`) ; icône `/usr/share/pixmaps/kairos.png` (le logo
    1024 px de l'image) ; licence `/usr/share/licenses/kairos-bin/LICENSE` (le fichier `copyright` du `.deb`, MIT) ;
  - `depends` : les bibliothèques que l'image charge elle-même (runtime Java réduit, Skiko) : `alsa-lib`,
    `fontconfig`, `glibc`, `libglvnd` (libGL, chargée à l'exécution par Skiko), `libstdc++`, `libx11`, `libxext`,
    `libxi`, `libxrender`, `libxtst` ; freetype et zlib viennent du runtime, qui est embarqué : aucun JDK n'est
    demandé ; `provides`/`conflicts` : `kairos` ;
    `options=('!strip' '!debug')` (binaires du runtime jpackage laissés tels quels).
- **`build.sh <version> <.deb> <sortie>`** (lancé en root dans `archlinux:base-devel`) : met le système à jour,
  remplit le gabarit (version, somme du `.deb` local, posé sous le nom de la source pour que `makepkg` ne le
  retélécharge pas), construit le paquet sous un compte sans privilège, l'installe par `pacman -U` (ses dépendances
  viennent des dépôts : un nom faux échoue ici), vérifie qu'aucune bibliothèque de `/opt/kairos` ne manque (`ldd`,
  avec les dossiers du runtime dans `LD_LIBRARY_PATH` comme le lanceur : `libjvm.so` n'est pas dans le système),
  que le marqueur, l'entrée de menu, l'icône et la licence sont là, lance **l'auto-test par `/usr/bin/kairos`**
  (`KAIROS_DATA_DIR` temporaire), passe `namcap` (avis seulement : il signale toujours, à tort pour cette image,
  « ELF hors chemin valide » pour `/opt` et une dépendance `java-runtime` non déclarée), puis écrit `PKGBUILD` et `.SRCINFO`
  (`makepkg --printsrcinfo`) dans la sortie. Essayé le 2026-11-02 sur le `.deb` 3.14.0 publié : paquet installé
  avec ses dépendances, aucune bibliothèque manquante, auto-test réussi par `/usr/bin/kairos`.
- **`publish.sh <sortie>`** : clé privée `AUR_SSH_PRIVATE_KEY`, empreinte de la clé d'hôte Ed25519 de l'AUR
  **épinglée** (`SHA256:RFzBCUItH9LZS0cKB5UE6ceAYhBD5C8GeOBip8Z11+4`, publiée sur aur.archlinux.org) et comparée à
  celle que renvoie `ssh-keyscan` (sinon échec) ; clone `ssh://aur@aur.archlinux.org/kairos-bin.git` (vide au premier
  envoi : l'AUR crée la base de paquet à la première poussée sur `master`), y copie les deux fichiers, commit
  « Kairos <version> » (auteur `Kairos release`, adresse `noreply` de GitHub Actions) et pousse ; rien à pousser si
  le contenu est déjà là (relance du job).
- **Job `aur` de `kmp-release.yml`** : après `release` sur un tag (ou après `desktop` lors d'un lancement manuel),
  version stable seulement ; télécharge l'artefact `kairos3-linux-x64`, lance `build.sh` dans le conteneur, téléverse
  l'artefact `aur-kairos-bin` (`PKGBUILD`, `.SRCINFO`, pour une poussée à la main), puis lance `publish.sh` **sur un
  tag seulement et seulement si** le secret `AUR_SSH_PRIVATE_KEY` existe (`AUR_READY`, comme `SIGNPATH_READY`) :
  forks et lancements manuels construisent et testent sans rien publier. Un échec du job n'annule pas la release
  GitHub, déjà publiée.
- **Application** : `SystemPackage.detect()` (`desktopApp`) cherche le marqueur `kairos-managed` à côté des JAR de
  l'image (même résolution que `PortableCopy` à partir de `jpackage.app-path`, `/proc/self/exe` du lanceur, donc
  `/opt/kairos/bin/Kairos` même lancé par le lien `/usr/bin/kairos`) ; `Main` le passe à `DesktopServices.open
  (managed = …)`, qui ne crée alors pas `DesktopUpdates` (`AppServices.updates` nul : ni vérification, ni bandeau, ni
  carte, `mises-a-jour.md`).
- **À faire par le propriétaire** (rien de cela ne peut se faire depuis le dépôt) :
  1. Créer un compte sur aur.archlinux.org.
  2. Créer une paire de clés dédiée (`ssh-keygen -t ed25519 -f aur -C kairos-aur`), poser la clé **publique** dans
     « My Account » → « SSH Public Key » de l'AUR.
  3. Dans le dépôt GitHub : secret `AUR_SSH_PRIVATE_KEY` = le contenu de la clé **privée**.
  4. Au premier tag stable (`v4.0.0`), vérifier le job `aur`, puis la page `https://aur.archlinux.org/packages/kairos-bin`.
     Sans secret, l'artefact `aur-kairos-bin` se pousse à la main (`git clone ssh://aur@aur.archlinux.org/kairos-bin.git`,
     copier les deux fichiers, commit, push).

### Web (`kmp/webApp`)

- Kotlin/Wasm (`wasmJs`, module de sortie `kairos`, script `kairos.js`),
  `ComposeViewport(document.body)` avec
  `KairosApp(Platform.WEB) { WebServices.open() }` (base en mémoire dans un
  worker sql.js, persistance OPFS et fichier lié : `export-import.md` §
  Version web).
- `index.html` charge `kairos-web.js` (fonctions navigateur,
  `globalThis.KairosWeb`) **avant** `kairos.js`. La distribution contient
  aussi `kairos-sqljs.worker.js`, `sql-wasm.js` et `sql-wasm.wasm` (copiés par
  `webpack.config.d/sqljs.js`).
- `index.html` : fond `#FAF8FF` (`#121318` en sombre ; `#FFF8F4` et `#18120B` avant le 2026-10-27), favicon, bloc `#kairos-loading` (cadran
  vide en SVG, relais de la page de démarrage, `demarrage.md`, +
  « Chargement de Kairos… ») retiré par `main()` au démarrage. Un test
  `WebAssembly.validate` d'un module WasmGC minimal remplace le texte par
  « navigateur trop ancien » (bilingue) si WasmGC manque (Chrome ou Edge avant
  119).
- Distribution : `:webApp:wasmJsBrowserDistribution`
  (`webApp/build/dist/wasmJs/productionExecutable/`).

### GitHub Pages (`pages.yml`)

- Construit toujours depuis `main` ; `/preview/` : la version web construite
  depuis `main` (préversion) ; `/app/` : la version web (`Kairos-web.zip`) de
  la dernière release **stable** `v3.*` (jamais une préversion), absente tant
  qu'il n'y en a pas ; `/` : la page de téléchargement. Déclencheurs,
  assemblage et liens : `publication.md` § Page de téléchargement.
- Déploiement : `actions/upload-pages-artifact` puis `actions/deploy-pages`
  (environnement `github-pages`).
- **Prérequis, une fois, par le propriétaire du dépôt** : Settings → Pages →
  Source : « GitHub Actions » (un workflow ne peut pas activer Pages avec le
  jeton par défaut).

### CI et release (GitHub Actions)

- `kmp.yml` (push sur `main`, PR, lancement manuel ; chemins `kmp/**` et ses
  propres workflows) : JDK 21 Temurin, plateforme Android 37, tests JVM
  (`core` dont les tests différentiels, `data`, `ui`, `desktopApp` dont les
  tests d'interface de la vue Jour), puis APK release, image de bureau Linux
  et version web ; **auto-test** de l'image Linux empaquetée ; captures en
  artefact `kmp-ci-screens`. Un test en échec affiche son message et sa
  pile complets (`testLogging` du `build.gradle.kts` racine) : un
  dépassement de délai d'un test d'interface y nomme l'attente qui a
  manqué. Les attentes des tests d'interface sont en temps réel : sur un
  runner à deux cœurs, avec les modules testés en parallèle, 5 s ont manqué
  par intermittence (`M5ScreensUiTest`), d'où 15 s.
- `kmp-test-build.yml` (lancement manuel seulement) : build de test d'un commit de la branche, voir § Build de test.
- `kmp-release.yml` (tags `v3.*` et `v4.*` (`v4.*` ajouté avec le paquet AUR, pour que la 4.0.0 se publie), lancement manuel) :
  - `version` : lit `kairos.versionName` ; échec si le tag diffère ; drapeau
    `prerelease` si la version contient `-` ;
  - `android` : tests JVM, keystore depuis `KAIROS_KEYSTORE_BASE64` (échec
    sur tag sans secret), `assembleRelease` → `Kairos-android.apk` ;
  - `web` : `Kairos-web.zip` ;
  - `desktop` (matrice `ubuntu-latest` → `linux-x64`, `windows-latest` →
    `windows-x64`, `macos-latest` → `macos-arm64`, `macos-15-intel` →
    `macos-x64`) : `packageDistributionForCurrentOS` + `createDistributable`
    (`fakeroot` installé sous Linux), **auto-test de l'image empaquetée**, puis
    `Kairos-<id>.{deb,msi,dmg}` et `Kairos-<id>-portable.{tar.gz,zip}`, ceux de Windows signés par SignPath
    Foundation sur un tag quand c'est configuré (§ Signature Windows) ; captures en artefacts `screens-<id>` ;
  - `release` (tag seulement) : `SHA256SUMS` de tous les `Kairos-*`, release
    GitHub « Kairos <version> » (préversion si suffixe), notes générées. Un
    seul APK par release ;
  - `aur` (version stable) : paquet `kairos-bin` construit et testé dans un conteneur Arch, poussé sur l'AUR sur un
    tag quand `AUR_SSH_PRIVATE_KEY` existe (§ Paquet Arch Linux).

### Build de test (`kmp-test-build.yml`)

- **Déclenchement** : `workflow_dispatch` seulement, lancé depuis `main` (Actions → « Kairos test build » → « Run
  workflow »). Champs : `commit` (SHA court ou long ; vide = le dernier commit de la branche choisie), et trois cases
  cochées par défaut, `android`, `desktop`, `web`.
- **`resolve`** : historique complet, `git rev-parse --verify "$COMMIT^{commit}"`, puis
  `git merge-base --is-ancestor` : le commit doit appartenir à la branche du lancement, sinon échec. Le champ passe par
  une variable d'environnement, jamais interpolé dans le script (injection). Sorties : SHA complet, SHA court,
  `kairos.versionName` du commit. Le résumé du lancement (`$GITHUB_STEP_SUMMARY`) dit le commit, la version et les
  plateformes demandées.
- **`tests`** (toujours) : les tests JVM de la CI (`:core:jvmTest :data:jvmTest :ui:jvmTest :desktopApp:jvmTest`), en
  parallèle des constructions : un test en échec rend le lancement rouge sans retarder les fichiers.
- **`android`** (si coché) : `assembleRelease` signé avec les secrets des releases (`KAIROS_KEYSTORE_BASE64`…), donc
  installable par-dessus la version en place, même `versionCode` compris ; sans secrets (fork), APK non signé avec un
  avertissement → `Kairos-android-test-<court>.apk`.
- **`desktop`** (si coché) : la matrice des quatre OS de la release, `fakeroot` sous Linux, **auto-test de l'image
  empaquetée**, puis `Kairos-<id>-test-<court>.{deb,msi,dmg}` et `Kairos-<id>-test-<court>-portable.{tar.gz,zip}` (marqueur
  portable comme la release) ; captures en artefacts `test-screens-<id>`. Pas de SignPath : chaque demande attend une
  approbation manuelle, réservée aux releases.
- **`web`** (si coché) : `Kairos-web-test-<court>.zip`.
- **Artefacts** `kairos-test-<court>-<cible>`, gardés **14 jours** (un build de test n'a pas à durer au-delà de la
  release qu'il prépare).
- Permissions : `contents: read` seulement ; aucun job ne publie ni n'écrit dans le dépôt.

### Décisions et pièges tracés

- **Arch Linux : paquet AUR `kairos-bin` publié par la CI** (2026-11-02, choix du propriétaire, 4.0.0). Tiré du
  `.deb` plutôt que de l'archive portable : celle-ci porte le marqueur `kairos-portable`, et Kairos installé par pacman
  proposerait de « s'ajouter au menu ». Écartés : un paquet compilé depuis les sources (Gradle et un JDK chez chaque
  utilisateur, long et fragile, pour le même résultat) ; un `.pkg.tar.zst` joint aux releases (`pacman -U`, sans mise
  à jour). Publication **automatique** sur tag, **conditionnelle** au secret (forks jamais bloqués), versions stables
  seulement (une préversion dans `kairos-bin` la pousserait à tous). Le paquet est **installé et lancé dans un vrai
  Arch** avant chaque envoi : ses dépendances ne se déduisent pas de celles du `.deb` (noms Debian), seul un essai
  les prouve. Clé d'hôte de l'AUR épinglée plutôt qu'acceptée au premier contact : la clé privée ne doit parler qu'à
  l'AUR.
- **Marqueur `kairos-managed` posé par le `PKGBUILD`, pas par la release** : le même `.deb`, installé à la main par
  `dpkg`, n'a personne pour le mettre à jour et garde la vérification. Un fichier plutôt qu'une option JVM : le
  `Kairos.cfg` du lanceur fait partie de l'image du `.deb`, le réécrire couplerait le paquet à son format.
- **`v4.*` ajouté aux tags de `kmp-release.yml`** (2026-11-02) : le filtre `v3.*` aurait ignoré le tag `v4.0.0`, et
  avec lui la release et la première publication AUR.
- **Build de test séparé de la release** (2026-11-03) : un workflow à part plutôt qu'un champ « commit » ajouté au
  lancement manuel de `kmp-release.yml`, pour que le chemin de release reste celui des tags et que le build de test ne
  puisse rien publier par construction. Écartés : un commit hors de la branche du lancement (un SHA de fork serait
  construit avec la clé de signature) ; un APK non signé par défaut (il faudrait désinstaller Kairos, et ses données,
  pour l'essayer).

- **Windows : SignPath Foundation** (2026-10-29, issue #117, décision du propriétaire) : gratuit pour un projet libre
  construit depuis son dépôt public, ce que Kairos est (licence MIT, release par GitHub Actions, APK reproductible).
  Prix accepté : l'éditeur affiché est « SignPath Foundation », pas le nom de l'auteur, chaque release attend une
  approbation manuelle, et une politique de signature doit être publiée. Écartés : Azure Artifact Signing (environ
  10 USD par mois, vérification d'identité restreinte pour les particuliers selon le pays) ; un certificat OV d'une
  autorité (200 à 400 € par an, clé sur jeton matériel ou HSM en ligne). Signature **sur tag seulement** : une demande
  par construction de PR noierait l'approbateur. **Conditionnelle** : sans configuration, le workflow construit
  comme avant, pour que forks et contributeurs ne soient jamais bloqués. Le README garde la consigne SmartScreen
  jusqu'à la vérification d'une release réellement signée (choix du propriétaire).
- **macOS : ni signature Developer ID ni notarisation pour l'instant** (2026-10-29, issue #116, décision du
  propriétaire). Elles exigent l'Apple Developer Program, **99 USD par an**, au nom d'une personne ou d'une
  organisation ; aucune autre voie ne retire l'avertissement de Gatekeeper (une signature ad hoc ou un certificat
  auto-signé laissent « développeur non identifié »). Le compte n'est pas pris : le `.app`, le `.dmg` et le zip portable
  restent non signés, le workflow ne change pas, et le README (anglais et français) comme les six pages de `site/`
  gardent la marche à suivre, **corrigée le même jour** : depuis macOS 15 (Sequoia), le clic droit → « Ouvrir » ne
  contourne plus Gatekeeper ; on passe par Réglages Système → Confidentialité et sécurité → « Ouvrir quand même », le
  clic droit restant cité pour macOS 14 et avant. **Si la décision est rouverte**, la voie retenue
  d'avance : un certificat « Developer ID Application » importé dans un trousseau temporaire du runner, la signature
  et la notarisation de Compose Desktop (`macOS { signing { … } notarization { … } }` : `codesign` avec runtime
  renforcé, `notarytool`, agrafage), et une **clé d'API App Store Connect** (`.p8`, révocable seule) plutôt que
  l'identifiant Apple et un mot de passe d'application ; certificat, mot de passe du `.p12` et clé en secrets GitHub
  seulement, et sans eux une construction non signée comme aujourd'hui (forks, contributeurs). L'APK, la vérification
  `reproducible` (F-Droid) et `SHA256SUMS` ne sont pas concernés.

- **Préversions installables à côté de la version stable** : identifiant
  Android `.preview`, produit de bureau distinct ; les versions finales
  utilisent les identifiants définitifs.
- **Dossier de données `…/Kairos/v3`** : chemin inchangé depuis la 3.0.0, c'est
  là que vivent les données des utilisateurs.
- **Instance unique par fichier surveillé, pas par socket** : aucun port
  ouvert, rien qu'un pare-feu d'entreprise puisse bloquer.
- **Auto-test sans fenêtre** : `ImageComposeScene` fait le rendu en mémoire
  (Skia), donc il marche sur les runners de CI sans affichage, et vérifie que
  l'image empaquetée (runtime réduit compris) sait réellement rendre
  l'interface.
- **Web dans ce conteneur** : la distribution web exige un paquet npm servi par
  `codeload.github.com`, bloqué dans l'environnement de développement (403).
  La compilation Wasm y est vérifiée, et la sortie compilée y est testée dans
  Chromium (`architecture.md` § Décisions) ; la distribution complète est
  construite par la CI GitHub.
- **Tags posés par le propriétaire du dépôt** : l'environnement de
  développement ne peut pousser que sa branche de travail, pas de tag. Un
  workflow qui créerait lui-même le tag a été écarté (contournement d'une
  restriction de l'environnement). Le tag `v<version>` est donc posé à la
  main sur le commit du jalon (`git tag` + `git push`, ou « Draft a new
  release » dans GitHub avec la branche comme cible), ce qui déclenche
  `kmp-release.yml`.
- **Runner macOS Intel** : `macos-15-intel`, dernier runner Intel de GitHub.
  S'il disparaît, la version macOS x64 est abandonnée (Apple Silicon reste).
