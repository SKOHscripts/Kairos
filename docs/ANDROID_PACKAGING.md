# Empaquetage Android — décisions et implémentation

Kairos est distribué en APK Android par les [releases GitHub](https://github.com/SKOHscripts/Kairos/releases),
sur le même principe que les exécutables Windows/Linux : une distribution
autonome, sans que l'utilisateur n'ait à installer Python. Ce document consigne
les décisions prises (l'historique d'exploration est dans le git) et décrit
l'implémentation du dossier [`android/`](../android/).

## Décisions actées

- **Distribution** : APK à télécharger depuis les releases (sideload), pas de
  Play Store.
- **Architecture** : même principe que le launcher de bureau. CPython et uvicorn
  tournent dans l'application et servent sur `127.0.0.1` ; une **WebView**
  affiche l'interface au lieu du navigateur système. Rendu serveur inchangé.
- **Dépendance native (`pydantic-core`) : levée par la migration Starlette.**
  Vérification empirique du 2026-07-12 : aucune wheel Android de
  `pydantic-core` (Rust) n'existe, ni sur PyPI (aucune version), ni dans le
  dépôt Chaquopy (`chaquo.com/pypi-13.1`), ni dans le canal BeeWare. FastAPI
  dépendant structurellement de Pydantic v2, le « Path B » exploré a été
  retenu : FastAPI → **Starlette pur** (la base que le projet utilisait déjà à
  travers FastAPI) et Pydantic → **dataclasses** + `app/settings_fields.py`.
  Depuis, plus aucune extension native ne bloque le portage.
- **Framework d'empaquetage : Chaquopy** (plugin Gradle, MIT). Son dépôt de
  wheels Android couvre les extensions natives restantes du projet, vérifié le
  2026-07-12 : `markupsafe` (jinja2), `greenlet` (exigé par sqlalchemy sur
  aarch64), `cryptography` et `cffi` (keyring). BeeWare/Briefcase écarté : canal
  de wheels plus pauvre au moment du choix.
- **uvicorn nu sur Android** (pas `[standard]`) : uvloop, httptools et
  watchfiles sont des extensions natives sans intérêt pour un serveur local
  mono-utilisateur. Liste des dépendances embarquées :
  [`packaging/android-requirements.txt`](../packaging/android-requirements.txt).
- **Signature** : keystore dédié, stable d'une release à l'autre (les mises à
  jour s'installent par-dessus), fourni à la CI par quatre secrets GitHub.
  Procédure de création dans le README (§ Empaquetage).

## Implémentation (`android/`)

Projet Gradle autonome (wrapper versionné) : AGP 8.7, Chaquopy 17 (Python
3.13, minSdk 24), ABI `arm64-v8a` en release (+ `x86_64` en debug pour
l'émulateur).

Chaîne de démarrage :

1. `MainActivity.onCreate` construit immédiatement la WebView **et** un overlay
   de démarrage (fond `@color/kairos_bg` + logo animé) empilés dans un
   `FrameLayout`, puis affiche cette hiérarchie (`setContentView`) — voir
   « Écran de démarrage » ci-dessous pour le détail et le pourquoi de ce
   choix. En parallèle, sur un thread dédié (`kairos-init`, jamais le thread
   principal) : démarre Chaquopy et appelle `kairos_boot.prepare(filesDir)` :
   le paquet embarqué `kairos_dist` (extrait de l'APK en vrais fichiers, voir
   ci-dessous) est ajouté à `sys.path`, `KAIROS_BASE_DIR` et
   `KAIROS_PLATFORM=android` sont posés, puis `app/android_launcher.py` ancre
   les données dans le stockage privé (`KAIROS_DATA_DIR`) et choisit un port
   libre. `KAIROS_PLATFORM` est lu une seule fois par `app/main.py`
   (`is_android`) pour la bottom nav de `templates/base.html` — seule
   variable d'environnement de ce module consommée pour distinguer l'APK
   Android du reste (voir `docs/spec/accueil-navigation.md`), tout le reste
   du gabarit/CSS restant strictement identique entre les trois cibles de
   packaging.
2. `kairos_boot.serve(port)` lance uvicorn dans un thread dédié (`kairos-uvicorn`).
3. Toujours depuis le thread `kairos-init`, une fois `prepare()` revenu : sonde
   `/favicon.ico` (même repère que le launcher de bureau, thread
   `kairos-probe`) puis charge `http://127.0.0.1:<port>/kairos` dans la
   WebView (`runOnUiThread`). L'overlay de démarrage se masque en fondu dès
   que cette page a fini de charger (`WebViewClient.onPageFinished`).

Empaquetage du code : la tâche Gradle `stageKairosPython` copie `app/`,
`templates/`, `static/` et `README.md` dans un paquet Python unique
`kairos_dist` (même arborescence que le dépôt : `BASE_DIR` fonctionne sans
changement), déclaré via `extractPackages` pour que Jinja2, StaticFiles et
le rendu du README lisent de vrais chemins. Le code du dépôt n'est jamais
dupliqué à la main.

Points notables :

- **Permission `INTERNET`** requise même pour le loopback ; HTTP en clair
  autorisé uniquement vers `127.0.0.1` (`network_security_config.xml`), le
  reste (TimeTree, GitLab) reste en HTTPS.
- **Bouton « Quitter »** : absent sur Android sans changement de gabarit — il
  n'apparaît que sous `is_frozen` (PyInstaller), faux dans l'APK. On quitte par
  le système.
- **`keyring`** : aucun trousseau sur Android, repli automatique sur le fichier
  local (comportement déjà prévu par `app/secret_store.py`).
- **Cycle de vie** : le port et le thread serveur sont statiques (une recréation
  d'activité ne redémarre pas le serveur) ; si Android tue le process, tout
  redémarre au retour, SQLite committe à chaque requête. **Limite v1** : pas de
  foreground service, un chrono en cours ne survit pas à une mise en veille
  agressive.
- **Écran de démarrage** (revue produit F-Droid/mobile, 2026-07, corrigé une
  seconde fois — voir « Piège tracé » ci-dessous) : un **overlay applicatif**,
  pas le splash système d'Android, porte le branding pendant toute l'attente
  de Python/uvicorn.
  - **Pourquoi pas le splash système (API 31+, `windowSplashScreenBackground`/
    `windowSplashScreenAnimatedIcon` dans `themes.xml`)** : cette API vise des
    attentes courtes (elle disparaît dès la première frame dessinée par
    l'activité) et plusieurs OEM/AOSP la forcent à disparaître au-delà d'un
    court délai — inadaptée à un démarrage de plusieurs secondes (extraction
    du paquet Python embarqué, première écriture SQLite). `themes.xml`
    conserve ces attributs (`android:windowSplashScreenBackground`,
    `android:windowBackground` — les deux à `@color/kairos_bg`,
    `tools:targetApi="31"` pour le premier : annotation lint, pas un
    mécanisme de qualification de ressource, ignorée sans erreur en dessous
    de l'API 31, même mécanisme déjà en production pour
    `windowLightNavigationBar`/`windowOptOutEdgeToEdgeEnforcement`) : ils
    couvrent gratuitement le tout petit instant de cold-start *avant*
    `onCreate`, mais ne sont plus **load-bearing** pour la suite.
  - **`MainActivity`** construit dans `onCreate`, synchrone, avant tout appel
    Python : un `FrameLayout` empilant la `WebView` (en dessous) et un
    overlay plein écran (fond `@color/kairos_bg` + un `ImageView` centré,
    au-dessus) — `buildStartupOverlay()`. L'`ImageView` réutilise
    l'`AnimatedVectorDrawable` déjà créé pour l'ancien splash système
    (`@drawable/kairos_splash_icon` — `kairos_splash_icon_base.xml` +
    `res/animator/kairos_splash_wedge_sweep.xml`, natif
    `android.graphics.drawable`, API 21+, pas AndroidX ; secteur terracotta
    balayant depuis midi jusqu'à 80°, 5 images-clés pour éviter l'aplatissement
    d'un morph `pathData` à deux points — voir le commentaire du fichier
    animator pour le détail géométrique) : appelée explicitement en Java
    (`Animatable.start()`), pas automatiquement par le système comme c'était
    le cas pour le splash — mais c'est le **même** asset, juste rejoué
    autrement.
  - **Python/uvicorn démarrent sur un thread dédié** (`kairos-init`), jamais
    le thread principal : `Python.start()` et surtout
    `kairos_boot.prepare()` peuvent prendre plusieurs secondes au premier
    lancement, ce qui bloquait auparavant `onCreate` de bout en bout —
    c'est ce blocage qui, dans la version précédente, empêchait le splash
    (système ou applicatif) de s'afficher ou de s'animer : le thread qui
    aurait dû le dessiner était occupé à extraire le paquet Python.
  - **`onPageFinished`** (une fois la première page réellement chargée dans
    la WebView, pas seulement une fois le serveur prêt) masque l'overlay en
    fondu (`View.animate().alpha(0)`). Filet de sécurité : un `Handler`
    masque aussi l'overlay après 30 s même sans `onPageFinished` (page en
    échec), pour ne jamais rester bloqué sur le logo.
  - **Piège tracé, pour ne pas le retrancher deux fois** : une première
    tentative avait retenu le splash *système* via
    `Activity.getSplashScreen().setKeepOnScreenCondition(...)` — cette
    méthode **n'existe pas** sur `android.window.SplashScreen` (la classe
    native, seule autorisée par la contrainte « pas d'AndroidX »), seulement
    sur `androidx.core.splashscreen.SplashScreen`, hors périmètre (erreur de
    compilation constatée en CI, corrigée avant tout usage réel). Le repli
    suivant, `ViewTreeObserver.OnPreDrawListener` pour reporter la première
    frame de l'activité, compilait et fonctionnait, mais souffrait du même
    problème de fond que le splash système qu'il retenait : tant que
    `onCreate` restait bloqué par l'initialisation Python synchrone, rien ne
    se dessinait à l'écran, splash retenu ou non. D'où le passage à un
    overlay applicatif **et** à une initialisation hors thread principal —
    les deux ensemble, pas l'un sans l'autre.
- **Geste retour prédictif** (Android 13+/15, même revue) : `AndroidManifest.xml`
  pose `android:enableOnBackInvokedCallback="true"` au niveau `<application>`
  (impératif — sans lui, tout enregistrement de callback reste sans effet même
  sur API 33+). `MainActivity.registerPredictiveBackCallback()` (appelée dans
  `onCreate`, juste après `setContentView(root)`, `root` étant le `FrameLayout`
  WebView+overlay décrit ci-dessus) enregistre un
  `OnBackInvokedCallback` (`android.window`, natif, pas AndroidX — même parti
  pris que `KairosNotificationBridge`) uniquement si
  `Build.VERSION.SDK_INT >= TIRAMISU` ; même logique que le chemin legacy
  (retour dans la WebView si possible, sinon `finish()`).
  `onBackPressed()` (API < 33) reste **strictement inchangé** : duplication
  volontaire plutôt que factorisation, pour ne rien risquer sur ce chemin déjà
  en production ; un seul enregistrement suffit par activité, `configChanges`
  couvrant déjà la rotation (`onCreate` n'est pas rappelé).
- **Notifications système (issue #16)** : `KairosNotificationBridge` (Java, même
  parti pris sans AndroidX que `MainActivity` — uniquement `NotificationManager`/
  `NotificationChannel`/`Notification.Builder` plateforme et
  `Activity#requestPermissions` natif) exposé en JS sous `window.KairosAndroid`
  (`addJavascriptInterface`). `templates/kairos.html` route les alertes chrono par
  ce pont quand il est présent, à la place de la Web Notifications API (absente de
  `android.webkit.WebView`). Permission `POST_NOTIFICATIONS` (API 33+) demandée
  depuis le bouton d'opt-in existant, jamais au démarrage ; le résultat
  (asynchrone) revient au JS via un évènement DOM
  (`kairos-android-permission-changed`) redéclenché depuis
  `onRequestPermissionsResult`, faute de canal message natif→JS synchrone sans
  AndroidX.

## Build

- CI : job `build-android` de
  [`.github/workflows/release.yml`](../.github/workflows/release.yml) — pytest
  en garde-fou (Python 3.13), JDK 17, keystore restauré depuis les secrets,
  `./gradlew assembleRelease`, artefact `kairos-android-arm64.apk` joint à la
  release comme les exécutables desktop. `versionName`/`versionCode` dérivés du
  tag `vX.Y.Z`.
- Local : `cd android && ./gradlew assembleDebug` (SDK Android + JDK 17 +
  `python3.13` requis). Sans keystore, `assembleRelease` produit un APK non
  signé ; le debug est signé debug comme d'habitude.

## Hors scope pour l'instant

- Play Store (distribution volontairement simple : APK des releases).
- Ressenti natif restant (widgets, foreground service pour le chrono) : objectif
  à terme, pas un prérequis de cette première version. Les notifications système
  sont couvertes depuis l'issue #16 (voir `KairosNotificationBridge` ci-dessus).
