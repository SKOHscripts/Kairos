# Architecture de Kairos 3

_Rôle : la structure du projet `kmp/` (modules Gradle, pile technique,
dépendances autorisées) et les invariants qui la gardent saine. Fichiers
couverts : `kmp/settings.gradle.kts`, `kmp/build.gradle.kts`,
`kmp/gradle.properties`, `kmp/gradle/libs.versions.toml`, les
`build.gradle.kts` de chaque module (hors paramètres de distribution, décrits
par `distribution.md`), `kmp/tools/`._

## 1. Besoin métier (cahier des charges)

### Objectif / problème

Kairos 2 est une application web Python servie en local (Starlette +
uvicorn) puis empaquetée trois fois de trois façons différentes (PyInstaller
pour le bureau, Chaquopy + WebView pour Android). Cela coûte :

- un démarrage lent sur Android (jusqu'à une minute au premier lancement) ;
- une distribution impossible sur F-Droid (CPython et wheels précompilés) ;
- une interface web contrainte (JavaScript facultatif, rechargements de page).

Kairos 3 doit tourner **nativement** sur Android, sur le bureau (Windows,
Linux, macOS) et dans un navigateur récent, à partir d'**un seul code**.

### Comportement attendu

- Un seul code métier et un seul code d'interface pour les quatre cibles.
- Le code métier est testable seul, sans interface, sans base, sans horloge
  réelle. Ce sont ces tests qui prouvent la parité avec Kairos 2 (plan § 7).
- Les dépendances sont toutes libres, pour que l'APK soit accepté par F-Droid.

### Critères de succès

- `./gradlew build` produit l'APK, l'application de bureau et la version web
  depuis le même dépôt.
- `core` compile pour toutes les cibles sans dépendre de Compose, d'Android,
  de la JVM ni du navigateur.
- Aucune dépendance hors Maven Central et le dépôt Maven de Google.

### Hors périmètre

- iOS : pas demandé. L'architecture ne l'empêche pas.
- Injection de dépendances par une bibliothèque (Koin, etc.) : un graphe
  manuel suffit à cette taille.

## 2. Solution technique

### Modules

| Module | Plugin(s) | Cibles | Rôle |
|---|---|---|---|
| `core` | KMP + bibliothèque Android KMP + sérialisation | JVM, Android, wasmJs | Code métier **pur** : `AppVersion`, `KairosBuild` (version, générée), modèle (`model/`), données d'exemple, moteur (`engine/` : jours ouvrés, dépendances, récurrence, ancienneté, ordonnancement, temps réel, voir `ordonnancement.md` et `temps-reel-chrono.md`), seuils du chrono (`alerts/`), modèle de la vue Jour (`day/DayView`), statistiques (`stats/TaskStats`, `statistiques.md`), conversion d'une note (`notes/NoteConversion`), modèle de la vue Semaine (`week/WeekView`), champs et validation des réglages (`settings/SettingsForm`, `reglages.md`), conversion d'une base Kairos 2 (`legacy/Kairos2Import`, `migration-2x.md`), lecture de la dernière version publiée (`updates/UpdateCheck`, `mises-a-jour.md`). |
| `data` | KMP + bibliothèque Android KMP + SQLDelight + sérialisation | JVM, Android, wasmJs | Schéma et requêtes SQLDelight (asynchrones), ouverture et migrations (`KairosStore`), dépôt (`KairosRepository`), format d'export (`ExportCodec`). Sans pilote : chaque application fournit le sien. |
| `ui` | KMP + bibliothèque Android KMP + Compose | JVM, Android, wasmJs | Interface commune : thème, icônes, logo, navigation, écrans, chaînes FR/EN, polices ; contrat des services de plateforme (`app/AppServices.kt`). |
| `androidApp` | application Android + compilateur Compose | Android | Activité, services Android (pilote SQLite, sélecteurs de fichiers, lecture et migration automatique d'une base Kairos 2), ressources de lancement. |
| `desktopApp` | KMP + Compose Desktop + sérialisation | JVM | `main()`, fenêtre, services du bureau (pilote JDBC, boîtes de dialogue, base Kairos 2, vérification des mises à jour), instance unique, journal de crash, auto-test, installeurs et image portable. |
| `webApp` | KMP + Compose | wasmJs | `main()` navigateur, services web (worker sql.js, OPFS, fichier lié), `index.html`, scripts navigateur. |

Graphe : `androidApp`, `desktopApp` et `webApp` → `ui` → `data` → `core`
(chaque module expose le suivant en `api`).

**Services de plateforme** : l'interface reçoit un `AppServices` (dépôt
ouvert, `FileService`, `BackupStore`, emplacement des données, fichier lié
de la version web, horloge), construit par chaque application et passé à
`KairosApp(platform) { … }` sous forme de fonction suspendue (ouverture
asynchrone de la base).

### Pile technique (versions dans `kmp/gradle/libs.versions.toml`)

- Kotlin 2.4.20, Gradle 9.8.0 (wrapper), Android Gradle Plugin 9.4.1
  (Kotlin intégré ; bibliothèques KMP par `com.android.kotlin.multiplatform.library`).
- Compose Multiplatform 1.12.1 (`org.jetbrains.compose` et
  `org.jetbrains.kotlin.plugin.compose`), `material3`, ressources Compose,
  `ui-backhandler` (retour système et Échap).
- `androidx.activity:activity-compose` 1.13.0.
- `kotlinx-coroutines` 1.11.0 (`-swing` sur le bureau), `kotlinx-datetime`
  0.8.0, `kotlinx-serialization-json` 1.11.0.
- SQLDelight 2.4.0 (`android-driver`, `sqlite-driver` pour le bureau et les
  tests, `web-worker-driver`, `async-extensions`) ; sql.js 1.14.2 (npm) et
  `copy-webpack-plugin` 12.0.2 (npm, build) pour la version web.
- JDK 21 partout (`jvmToolchain(21)`, Java 21 pour Android).
- Android : `compileSdk 37` (exigé par Compose 1.12), `targetSdk 36`,
  `minSdk 26`.

### Invariants

- **`core` est pur** : ni Compose, ni Android, ni `java.*`, ni horloge
  système, ni réseau, ni fichier. L'heure courante et les réglages sont
  toujours passés en paramètre. C'est ce qui rend possibles les tests
  différentiels contre le moteur Python (plan § 7).
- **Version en source unique** : `kmp/gradle.properties`
  (`kairos.versionName`, `kairos.versionCode`). `core` en génère
  `KairosBuild` (tâche `generateKairosBuild`, dossier `build/generated`),
  lu par l'interface (« À propos ») et l'auto-test. Android et le bureau
  lisent les mêmes propriétés dans leur script Gradle. Détail :
  `distribution.md` § Versionnage.
- **Dépendances** : uniquement des bibliothèques libres publiées sur Maven
  Central ou le dépôt Google. Aucune bibliothèque Google Play Services,
  Firebase, analytics ou rapport de crash distant. Les ajouts se font dans
  le catalogue de versions, avec une version figée (builds reproductibles).
  Les tests d'interface du bureau utilisent `compose.desktop.uiTestJUnit4`
  (bibliothèque de test de Compose Multiplatform, même version que
  Compose, test seulement : rien n'entre dans les applications).
- **Code généré, jamais édité à la main** : `KairosColors.kt`
  (`tools/make_theme.py`), `KairosIcons.kt` (`tools/make_icons.py`), icônes
  d'application (`tools/make_app_icons.py`), `KairosBuild` (Gradle).
- Toute interface passe par le module `ui`. Les modules d'application ne font
  que fournir la plateforme (`Platform.ANDROID`, `DESKTOP`, `WEB`) et
  l'intégration système.

### Outils (`kmp/tools/`)

| Script | Produit | Dépendance |
|---|---|---|
| `make_theme.py` | `ui/.../theme/KairosColors.kt` : les 36 rôles MD3 du schéma *Tonal spot* (spec 2021) de la graine `#C28417`. | `materialyoucolor` 3.0.4 |
| `make_icons.py` | `ui/.../icons/KairosIcons.kt` : tracés Material Symbols (Outlined 400) et variantes pleines. | `@material-symbols/svg-400` 0.47.5 (npm) |
| `make_app_icons.py` | `desktopApp/icons/kairos.{png,ico,icns}`, favicon et icônes de `webApp`. | Pillow |
| `gen_fixtures.py` | Fixtures des tests différentiels (`core/src/jvmTest/resources/fixtures/`). | le Python de Kairos 2 |
| `gen_legacy_db.py` | Vraies bases Kairos 2 pour les tests de migration (`desktopApp/src/jvmTest/resources/legacy/`). | le Python de Kairos 2 (SQLAlchemy) |

### Décisions et pièges tracés

- **Pourquoi SQLDelight et non Room** (décidé au plan, appliqué en M1) : Room
  n'a pas de cible web ; SQLDelight garde un SQL explicite, utile pour
  importer une base Kairos 2.
- **AGP 9 et KMP** : le plugin `com.android.application` ne s'applique plus à
  un module multiplateforme. L'application Android est donc un module séparé
  (`androidApp`), et les bibliothèques partagées utilisent le plugin
  `com.android.kotlin.multiplatform.library` (bloc `kotlin { android { … } } }`,
  `androidResources { enable = true }` pour les ressources Compose de `ui`).
- **`compileSdk 37`** : imposé par les métadonnées AAR de Compose 1.12
  (`checkReleaseAarMetadata` échoue sinon). `targetSdk` reste à 36 tant que
  les changements de comportement de l'API 37 n'ont pas été évalués.
- **Maven Central et limitation de débit** : dans le conteneur de
  développement, `repo.maven.apache.org` renvoie des 429. Un script
  d'initialisation Gradle **local, non commité** y ajoute le miroir Google de
  Central. Le dépôt lui-même ne déclare que `google()` et `mavenCentral()`.
- **Pas de bibliothèque de navigation** : état Compose (destination +
  « À propos » ouvert) ; voir `navigation-theme.md` § Décisions.
- **Pas de ViewModel** : les écrans lisent le `StateFlow` du dépôt et
  appellent ses opérations dans une coroutine d'écran ; l'état vit dans la
  base, une recréation d'activité Android ne perd rien.
- **Tester la version web dans le conteneur** : la distribution webpack y est
  impossible (paquet npm servi par `codeload.github.com`, bloqué), mais la
  sortie de `:webApp:compileProductionExecutableKotlinWasmJs`
  (`kairos.mjs`, `kairos.wasm`…) se sert telle quelle, avec `skiko.mjs` /
  `skiko.wasm` (`build/wasm/packages_imported/skiko-js-wasm-runtime/`),
  `@js-joda/core` en module ES par une `importmap`, `sql-wasm.js` /
  `sql-wasm.wasm` et les ressources traitées ; Chromium (Playwright) la pilote
  ensuite.
