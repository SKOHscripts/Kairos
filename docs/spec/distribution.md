# Distribution : versions, Android, bureau, web, CI

_Rôle : comment Kairos est versionné, construit, testé, empaqueté et
publié sur chaque cible. Fichiers couverts : `kmp/gradle.properties`
(version), `kmp/core/src/commonMain/.../AppVersion.kt`, `kmp/androidApp/`,
`kmp/desktopApp/` (dont `Main.kt`, `DataDirectory.kt`, `SingleInstance.kt`,
`CrashLog.kt`, `SelfTest.kt`, `icons/`), `kmp/webApp/`,
`kmp/tools/make_app_icons.py`, `.github/workflows/kmp.yml`,
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
- L'application de bureau s'ouvre sur une fenêtre « Kairos » (ou « Kairos
  Preview »). La relancer alors qu'elle est ouverte ramène la fenêtre
  existante au premier plan, sans seconde instance.
- Un crash laisse une trace lisible dans un fichier, sans terminal.
- **Web** : la même interface dans le navigateur. Un navigateur trop ancien
  affiche un message clair plutôt qu'un écran figé.

### Critères de succès

- Le tag et la version déclarée ne peuvent pas diverger (la release échoue).
- Chaque `versionCode` Android est strictement supérieur au précédent.
- Chaque image de bureau empaquetée est **lancée en CI** sur son OS (auto-test)
  avant publication ; un auto-test en échec bloque la release.
- Un tag produit un APK **signé**, avec toujours la même clé (chaque version s'installe par-dessus la précédente).
- Les fichiers publiés ont des noms fixes (liens stables) et un `SHA256SUMS`.

### Hors périmètre (à ce jalon)

- Signature et notarisation macOS, signature Authenticode Windows : différées
  (plan § 6.2). Gatekeeper et SmartScreen afficheront un avertissement.
- Mise à jour intégrée : jamais sur Android ; sur le bureau, simple
  vérification (`mises-a-jour.md`).
- F-Droid, fiches et page de téléchargement : `publication.md`.

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
  réglages, retrait de l'import de l'ancienne version ; notes `30400.txt` en français et en anglais), puis `3.5.0` (`30500`, issue #64 : plusieurs plages par jour et contextes, carte Statistiques « Quand tu travailles chaque type » ; notes `30500.txt` en français et en anglais), puis `3.6.0` (`30600`, issue #64 : réglage « Meilleures heures », le planning préfère les heures habituelles ou préférées de chaque type ; notes `30600.txt` en français et en anglais), puis `3.7.0` (`30700` : thème sombre, issue #72 ; filtres et export des statistiques, #114 et #113 ; déplacer depuis la vue Semaine, #106 ; fériés des créneaux « jours ouvrés », #109 ; Réglages par catégories, PR #124 ; guide qui retient et montre l'endroit, #94 et #95 ; notes `30700.txt` en français et en anglais), puis `3.8.0` (`30800` : vue Mois, #107 ; créneaux mensuels, #108 ; une seule occurrence d'un créneau, #110 ; évolutions des statistiques, #112 ; corriger les estimations, #101 ; rappels, #103 ; première tâche d'équipe guidée, #97 ; notes `30800.txt` en français et en anglais), puis `3.9.0` (`30900` : noms des niveaux, #93 ; meilleures heures mesurées par l'efficacité, #105 ; accompagnement des récurrences et des Réglages, #98 ; jours et heures par membre, #104 ; notes dans les échanges, #120 ; propositions d'un membre, #121 ; dossier des données, diagnostic et proxy du bureau, #115 ; lien « Voir la dernière version » sur Android, #143 ; notes `30900.txt` en français et en anglais), puis `3.10.0` (`31000` : import d'un calendrier `.ics`, #136 ; fichier lié sur le bureau et Android, #137 ; calendriers du téléphone, #138 ; notes `31000.txt` en français et en anglais), puis `3.11.0` (`31100` : espagnol, allemand, russe et italien, #75 ; fiches du magasin et page web dans ces langues ; notes `31100.txt` dans les six langues), puis `3.12.0` (`31200` : widgets Android, #150 ; récapitulatif du jour, #151 ; couleur par défaut océan ; notes `31200.txt` dans les six langues).
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
     (`WindowClass.apply`), voir `raccourci-portable.md` ;
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
- `kmp-release.yml` (tag `v3.*`, lancement manuel) :
  - `version` : lit `kairos.versionName` ; échec si le tag diffère ; drapeau
    `prerelease` si la version contient `-` ;
  - `android` : tests JVM, keystore depuis `KAIROS_KEYSTORE_BASE64` (échec
    sur tag sans secret), `assembleRelease` → `Kairos-android.apk` ;
  - `web` : `Kairos-web.zip` ;
  - `desktop` (matrice `ubuntu-latest` → `linux-x64`, `windows-latest` →
    `windows-x64`, `macos-latest` → `macos-arm64`, `macos-15-intel` →
    `macos-x64`) : `packageDistributionForCurrentOS` + `createDistributable`
    (`fakeroot` installé sous Linux), **auto-test de l'image empaquetée**, puis
    `Kairos-<id>.{deb,msi,dmg}` et `Kairos-<id>-portable.{tar.gz,zip}` ;
    captures en artefacts `screens-<id>` ;
  - `release` (tag seulement) : `SHA256SUMS` de tous les `Kairos-*`, release
    GitHub « Kairos <version> » (préversion si suffixe), notes générées. Un
    seul APK par release.

### Décisions et pièges tracés

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
