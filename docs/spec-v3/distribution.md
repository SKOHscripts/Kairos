# Distribution : versions, Android, bureau, web, CI

_Rôle : comment Kairos 3 est versionné, construit, testé, empaqueté et
publié sur chaque cible. Fichiers couverts : `kmp/gradle.properties`
(version), `kmp/core/src/commonMain/.../AppVersion.kt`, `kmp/androidApp/`,
`kmp/desktopApp/` (dont `Main.kt`, `DataDirectory.kt`, `SingleInstance.kt`,
`CrashLog.kt`, `SelfTest.kt`, `icons/`), `kmp/webApp/`,
`kmp/tools/make_app_icons.py`, `.github/workflows/kmp.yml`,
`.github/workflows/kmp-release.yml` et `.github/workflows/pages.yml` (sa
partie version web). Les services de fichiers et de sauvegarde de chaque plateforme
sont décrits par `export-import.md` ; F-Droid, IzzyOnDroid, fiches Fastlane et
page de téléchargement par `publication.md`._

État : **jalon M7** (`3.0.0`, bascule : Kairos 3 remplace Kairos 2).

## 1. Besoin métier (cahier des charges)

### Objectif / problème

Kairos doit s'installer, ou simplement se lancer, partout où ses
utilisateurs travaillent :

- sur Android, depuis F-Droid, IzzyOnDroid ou l'APK des releases ;
- sur un poste Windows, Linux ou macOS, par un installeur classique ;
- sur un **poste professionnel verrouillé**, sans droit d'installation, où
  l'exécutable de Kairos 2 tourne déjà : par un **zip portable** ou par la
  **version web** dans Edge ou Chrome.

Pendant la réécriture, chaque jalon devait produire une version installable
**à côté** de Kairos 2, qui restait l'outil du quotidien ; la 3.0.0, elle,
**remplace** Kairos 2 en s'installant par-dessus.

### Comportement attendu (utilisateur)

- **Chaque version** a un tag : une préversion (`vX.Y.Z-alpha.N`,
  `vX.Y.Z-beta.N`) est publiée en **préversion GitHub**, une version finale
  (`vX.Y.Z`) en release GitHub, avec tous les fichiers.
- **Android** : un APK. Tant que la version est une préversion, l'application
  s'appelle « Kairos Preview » et s'installe à côté de Kairos 2. Aucune
  permission réseau ; deux permissions pour le chrono (notifier, reprendre
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
- Chaque `versionCode` Android est strictement supérieur au précédent, et à
  tous ceux de Kairos 2 (20600 pour 2.6.0).
- Chaque image de bureau empaquetée est **lancée en CI** sur son OS (auto-test)
  avant publication ; un auto-test en échec bloque la release.
- Un tag produit un APK **signé**, avec la même clé que Kairos 2.
- Les fichiers publiés ont des noms fixes (liens stables) et un `SHA256SUMS`.

### Hors périmètre (à ce jalon)

- Signature et notarisation macOS, signature Authenticode Windows : différées
  (plan § 6.2). Gatekeeper et SmartScreen afficheront un avertissement.
- Mise à jour intégrée : jamais sur Android ; sur le bureau, simple
  vérification (`mises-a-jour.md`).
- F-Droid, IzzyOnDroid, fiches et page de téléchargement : `publication.md`.

## 2. Solution technique

### Versionnage

- **Source unique** : `kmp/gradle.properties`, `kairos.versionName` et
  `kairos.versionCode`. Jalon M0 : `3.0.0-alpha.1` (`29001`) ; jalon M1 :
  `3.0.0-alpha.2` (`29002`) ; jalon M2 : `3.0.0-alpha.3` (`29003`) ;
  jalon M3 : `3.0.0-alpha.4` (`29004`) ; jalon M4 : `3.0.0-alpha.5`
  (`29005`) ; jalon M5 : `3.0.0-alpha.6` (`29006`) ; jalon M6 (fonctions
  complètes, publication prête) : `3.0.0-beta.1` (`29501`) ; jalon M7
  (bascule) : `3.0.0` (`30000`).
- Formats acceptés (`AppVersion.parse`) : `X.Y.Z`, `X.Y.Z-alpha.N`,
  `X.Y.Z-beta.N` (préfixe `v` toléré ; `Y`, `Z` ≤ 99 ; `N` de 1 à 499).
- `versionCode` (`AppVersion.versionCode`) : `X*10000 + Y*100 + Z` ; alpha :
  − 1000 + N ; beta : − 500 + N. Ainsi 3.0.0-alpha.1 = 29001 <
  3.0.0-beta.1 = 29501 < 3.0.0 = 30000, et tout 3.x > 20600 (Kairos 2.6.0).
  `AppVersionTest.buildVersionIsConsistent` échoue si `gradle.properties` ne
  suit pas la formule.
- `KairosBuild` (généré par `core`) expose la version au code commun.
- Le tag de release doit valoir `v` + `kairos.versionName` (job `version` de
  `kmp-release.yml`).
- F-Droid lit la version dans `kmp/gradle.properties` par `UpdateCheckData`
  (`publication.md`) ; chaque versionCode publié a sa note de version
  Fastlane dans les deux langues.

### Android (`kmp/androidApp`)

- `applicationId` `com.skohscripts.kairos` (celui de Kairos 2), suffixé
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
- Manifeste : **aucune permission réseau** (pas de `INTERNET`) ;
  `POST_NOTIFICATIONS` (demandée à l'opt-in des alertes) et
  `RECEIVE_BOOT_COMPLETED` pour le chrono (`temps-reel-chrono.md`), avec
  ses deux récepteurs (`ChronoReceiver` non exporté, `BootReceiver`) ;
  activité `singleTop` (la notification du chrono la rouvre) ;
  `enableOnBackInvokedCallback="true"` (geste retour prédictif) ;
  `windowSoftInputMode="adjustResize"`. Base et suivi du chrono ouverts une
  fois par processus (`KairosProcess`), quel que soit le point d'entrée
  (activité, alarme, redémarrage).
- Thème de fenêtre `Theme.Kairos` (reprise de Kairos 2) :
  `forceDarkAllowed=false` ; avant l'API 31, `windowBackground` =
  `kairos_launch_background` (fond `#FFF8F4` + logo centré 288 dp) ; API 31+
  (`values-v31`), écran de démarrage système avec l'icône animée
  `kairos_splash_icon` (700 ms). L'interface Compose s'affiche dès la première
  image : plus d'écran de démarrage applicatif.
- Icône adaptative `mipmap-anydpi/ic_launcher.xml` (fond `#FFEEDC`, premier
  plan `ic_launcher_foreground`), ressources reprises de Kairos 2.
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
  5. fenêtre 1200 × 800 dp (minimum 360 × 480 px), titre `Kairos` ou
     `Kairos Preview` (propriété JVM `kairos.preview`), icône = logo,
     contenu `KairosApp(Platform.DESKTOP) { DesktopServices.open(dataDir) }`.
- **Services** (`DesktopServices.open`) : `JdbcSqliteDriver` sur
  `<données>/kairos.db` (ou en mémoire pour l'auto-test), exemples dans la
  langue de la JVM, dossier des données affiché dans la carte Données.
- **Dossier de données** : `KAIROS_DATA_DIR` s'il est défini, sinon la racine
  Kairos de `platformdirs` (celle de Kairos 2) + `v3` :
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
  `desktop-stats.png`). L'auto-test n'affiche pas l'accueil du premier
  lancement et ne vérifie pas les mises à jour (aucun réseau). Code de
  sortie 0 ou 1.
- **Empaquetage** (plugin Compose Desktop, jpackage, runtime Java réduit
  embarquant `java.instrument`, `java.management`, `java.net.http` (depuis
  M5, vérification des mises à jour), `java.sql`, `jdk.unsupported` en plus
  des modules détectés) :
  - formats `Msi`, `Deb`, `Dmg` ; `createDistributable` produit l'image
    portable ;
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
- `index.html` : fond `#FFF8F4`, favicon, bloc `#kairos-loading` (logo +
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
  artefact `kmp-ci-screens`.
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
  - `release` (tag seulement) : pour une version finale, copie de
    `Kairos-android.apk` en `kairos-android-arm64.apk` (pont de mise à jour
    de Kairos 2, `migration-2x.md`) ; `SHA256SUMS` de tous les fichiers
    publiés ; release GitHub « Kairos <version> » (préversion si suffixe),
    notes générées.
- Kairos 2 (Python) n'est plus dans le dépôt depuis la bascule : son code se
  lit au tag `v2.6.0`, ses releases `v1.*` et `v2.*` restent publiées, et son
  workflow de release (`release.yml`) comme sa CI (`ci.yml`) sont supprimés.

### Décisions et pièges tracés

- **Préversions installables à côté de Kairos 2** : identifiant Android
  `.preview`, produit de bureau distinct. Seule la 3.0.0 reprend les
  identifiants définitifs, pour s'installer par-dessus Kairos 2 (plan § 5.4).
- **Dossier de données `…/Kairos/v3`** : la base Kairos 2 (`…/Kairos/tasks.db`)
  n'est jamais touchée, et reste à portée de la migration (M5).
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
