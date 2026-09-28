# Distribution : versions, Android, bureau, web, CI

_Rôle : comment Kairos 3 est versionné, construit, testé, empaqueté et
publié sur chaque cible. Fichiers couverts : `kmp/gradle.properties`
(version), `kmp/core/src/commonMain/.../AppVersion.kt`, `kmp/androidApp/`,
`kmp/desktopApp/` (dont `Main.kt`, `DataDirectory.kt`, `SingleInstance.kt`,
`CrashLog.kt`, `SelfTest.kt`, `icons/`), `kmp/webApp/`,
`kmp/tools/make_app_icons.py`, `.github/workflows/kmp.yml`,
`.github/workflows/kmp-release.yml`, et le filtre de tags de
`.github/workflows/release.yml` (Kairos 2)._

État : **jalon M0**. Publication F-Droid, IzzyOnDroid, métadonnées Fastlane et
page GitHub Pages : jalons M1 (préversion web) et M6 (le reste), plan § 9.

## 1. Besoin métier (cahier des charges)

### Objectif / problème

Kairos doit s'installer, ou simplement se lancer, partout où ses
utilisateurs travaillent :

- sur Android, depuis F-Droid, IzzyOnDroid ou l'APK des releases ;
- sur un poste Windows, Linux ou macOS, par un installeur classique ;
- sur un **poste professionnel verrouillé**, sans droit d'installation, où
  l'exécutable de Kairos 2 tourne déjà : par un **zip portable** ou par la
  **version web** dans Edge ou Chrome.

Pendant la réécriture, chaque jalon doit produire une version installable
**à côté** de Kairos 2, qui reste l'outil du quotidien.

### Comportement attendu (utilisateur)

- **Chaque jalon** donne une version (`3.0.0-alpha.N`) et un tag
  (`v3.0.0-alpha.N`), publiés en **préversion GitHub** avec tous les
  fichiers.
- **Android** : un APK. Tant que la version est une préversion, l'application
  s'appelle « Kairos Preview » et s'installe à côté de Kairos 2. Elle ne
  demande **aucune permission**.
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
  vérification au jalon M5.
- Déploiement GitHub Pages (préversion web en M1, page complète en M6),
  recette F-Droid et métadonnées Fastlane (M6).

## 2. Solution technique

### Versionnage

- **Source unique** : `kmp/gradle.properties`, `kairos.versionName` et
  `kairos.versionCode`. Jalon M0 : `3.0.0-alpha.1`, `29001`.
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
- F-Droid (jalon M6) lira la version dans `kmp/gradle.properties` par
  `UpdateCheckData`.

### Android (`kmp/androidApp`)

- `applicationId` `com.skohscripts.kairos` (celui de Kairos 2), suffixé
  `.preview` si la version contient `-` ; nom `Kairos Preview` ou `Kairos`
  (`resValue app_name`).
- `minSdk 26`, `targetSdk 36`, `compileSdk 37`, Java 21.
- `MainActivity` (`ComponentActivity`) : `enableEdgeToEdge()`, puis
  `setContent { KairosApp(Platform.ANDROID) }`.
- Manifeste : **aucune permission** (pas de `INTERNET`) ;
  `enableOnBackInvokedCallback="true"` (geste retour prédictif) ;
  `windowSoftInputMode="adjustResize"`.
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
- Taille M0 : environ 1,3 Mo.

### Bureau (`kmp/desktopApp`)

- `main(args)` :
  1. dossier de données (`DataDirectory.resolve`) ;
  2. journal de crash (`CrashLog.install`) ;
  3. `--self-test[=dossier]` : auto-test puis sortie ;
  4. instance unique (`SingleInstance.acquire`), sinon sortie immédiate ;
  5. fenêtre 1200 × 800 dp (minimum 360 × 480 px), titre `Kairos` ou
     `Kairos Preview` (propriété JVM `kairos.preview`), icône = logo,
     contenu `KairosApp(Platform.DESKTOP)`.
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
  données, et rend hors écran (`ImageComposeScene`, 10 images pour laisser
  charger polices et chaînes) l'application en 1200 px et en 420 px, et
  l'écran « À propos » en 900 px. Avec `=dossier`, les rendus y sont écrits
  (`desktop-wide.png`, `desktop-narrow.png`, `desktop-about.png`). Code de
  sortie 0 ou 1.
- **Empaquetage** (plugin Compose Desktop, jpackage, runtime Java réduit
  embarquant `java.instrument`, `java.management`, `java.sql`,
  `jdk.unsupported` en plus des modules détectés) :
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
  `ComposeViewport(document.body)` avec `KairosApp(Platform.WEB)`.
- `index.html` : fond `#FFF8F4`, favicon, bloc `#kairos-loading` (logo +
  « Chargement de Kairos… ») retiré par `main()` au démarrage. Un test
  `WebAssembly.validate` d'un module WasmGC minimal remplace le texte par
  « navigateur trop ancien » (bilingue) si WasmGC manque (Chrome ou Edge avant
  119).
- Distribution : `:webApp:wasmJsBrowserDistribution`
  (`webApp/build/dist/wasmJs/productionExecutable/`). Données et
  déploiement GitHub Pages : jalon M1 (plan § 5.3, § 6.3).

### CI et release (GitHub Actions)

- `kmp.yml` (push sur `main`, PR, lancement manuel ; chemins `kmp/**` et ses
  propres workflows) : JDK 21 Temurin, plateforme Android 37, tests JVM
  (`core`, `ui`, `desktopApp`), puis APK release, image de bureau Linux et
  version web ; **auto-test** de l'image Linux empaquetée ; captures en
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
  - `release` (tag seulement) : `SHA256SUMS` de tous les `Kairos-*`, release
    GitHub « Kairos <version> » (préversion si suffixe), notes générées.
- `release.yml` (Kairos 2) ne réagit plus qu'aux tags `v1.*` et `v2.*`.

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
  La compilation Wasm y est vérifiée ; la distribution complète est
  construite par la CI GitHub.
- **Runner macOS Intel** : `macos-15-intel`, dernier runner Intel de GitHub.
  S'il disparaît, la version macOS x64 est abandonnée (Apple Silicon reste).
