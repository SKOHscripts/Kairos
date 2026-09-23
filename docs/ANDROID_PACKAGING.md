# Empaquetage Android : décisions et implémentation

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

1. `MainActivity.onCreate` construit immédiatement la WebView (contenu de
   l'activité) **et** l'écran de démarrage (`StartupScreen`, ajouté par-dessus
   toute la fenêtre) : voir « Écran de démarrage » ci-dessous pour le détail
   et le pourquoi. Puis, sur un thread dédié (`kairos-init`, jamais le thread
   principal) : démarre Chaquopy et appelle `kairos_boot.prepare(filesDir)` :
   le paquet embarqué `kairos_dist` (extrait de l'APK en vrais fichiers, voir
   ci-dessous) est ajouté à `sys.path`, `KAIROS_BASE_DIR` et
   `KAIROS_PLATFORM=android` sont posés, puis `app/android_launcher.py` ancre
   les données dans le stockage privé (`KAIROS_DATA_DIR`) et choisit un port
   libre. `KAIROS_PLATFORM` est lu une seule fois par `app/main.py`
   (`is_android`) pour la bottom nav de `templates/base.html` : seule
   variable d'environnement de ce module consommée pour distinguer l'APK
   Android du reste (voir `docs/spec/accueil-navigation.md`), tout le reste
   du gabarit/CSS restant strictement identique entre les trois cibles de
   packaging.
2. `kairos_boot.serve(port)` lance uvicorn dans un thread dédié (`kairos-uvicorn`).
   Une exception levée par `serve` y est rattrapée et gardée (`serverError`)
   au lieu de tuer tout le process (« Kairos s'est arrêté »).
3. Toujours depuis le thread `kairos-init` : sonde `/favicon.ico` (même repère
   que le launcher de bureau) jusqu'à 90 s, en abandonnant tôt si le thread
   serveur s'est arrêté, puis charge `http://127.0.0.1:<port>/kairos` dans la
   WebView (`runOnUiThread`). L'écran de démarrage disparaît en fondu dès que
   cette page a fini de charger (`WebViewClient.onPageFinished`), ou passe en
   état d'erreur (voir ci-dessous).

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
- **Bouton « Quitter »** : absent sur Android sans changement de gabarit : il
  n'apparaît que sous `is_frozen` (PyInstaller), faux dans l'APK. On quitte par
  le système.
- **`keyring`** : aucun trousseau sur Android, repli automatique sur le fichier
  local (comportement déjà prévu par `app/secret_store.py`).
- **Cycle de vie** : le port et le thread serveur sont statiques (une recréation
  d'activité ne redémarre pas le serveur) ; si Android tue le process, tout
  redémarre au retour, SQLite committe à chaque requête. **Limite v1** : pas de
  foreground service, un chrono en cours ne survit pas à une mise en veille
  agressive.
- **Écran de démarrage** (revue produit F-Droid/mobile 2026-07, repris en
  2026-09 après des retours « écran blanc ou noir » persistants) : le logo est
  visible dès la première image affichée par le système et jusqu'à l'agenda,
  sur toutes les versions (API 24 à 35), en trois relais qui placent le logo
  **au même endroit et à la même taille** (boîte de 288dp centrée sur la
  fenêtre entière, logo réduit à 0.64 dans cette boîte) :
  1. **Fenêtre de démarrage du système** : avant l'API 31, c'est
     `android:windowBackground`, désormais `kairos_launch_background.xml`
     (layer-list : `@color/kairos_bg` + `kairos_splash_logo` centré, 288dp).
     Avant ce correctif, un aplat clair uni : c'était l'« écran blanc » des
     appareils Android 7 à 11 pendant le démarrage du process et la création
     de la WebView. À partir de l'API 31, c'est le splash système
     (`windowSplashScreenBackground` + `windowSplashScreenAnimatedIcon` =
     `kairos_splash_icon`, animation de balayage de 700 ms).
  2. **Splash système API 31+ : taille de l'icône.** Le système dessine l'icône
     dans une boîte de 288dp et la rogne à un cercle de 192dp (les 2/3
     centraux, comme le premier plan d'une icône adaptative). Le dessin
     d'origine occupait toute la boîte : logo zoomé, cadre et secteur coupés.
     `kairos_splash_icon_base.xml` et `kairos_splash_logo.xml` enveloppent
     désormais le tracé dans un `<group>` réduit à 0.64 autour du centre
     (rayon utile 12.35 sur 13.33 disponibles, viewport 40).
  3. **`StartupScreen`** (Java, ajouté à la `DecorView` pour couvrir toute la
     fenêtre, barres système comprises : même repère de centrage que les deux
     relais précédents) : fond `@color/kairos_bg`, `kairos_splash_logo`
     **statique** (secteur à sa position finale) dans une boîte de 288dp.
     L'animation n'est pas rejouée ici : le splash système l'a déjà jouée
     (API 31+), et la rejouer depuis midi après un logo complet (API 24-30)
     ferait clignoter le secteur. Sous le logo, une colonne d'état :
     indicateur de progression neutre et étape en cours (« Démarrage de
     Python… », « Préparation des données… », « Démarrage du serveur… »,
     « Chargement de l'agenda… », annoncée par TalkBack), puis au bout de 8 s
     une ligne « Le premier lancement après une installation ou une mise à
     jour peut prendre jusqu'à une minute. ». Vues construites en code (pas
     d'AndroidX, pas de layout XML), textes dans `strings.xml`, couleurs de
     la charte dans `colors.xml`.
  - **Thème sombre forcé** : `android:forceDarkAllowed=false` (API 29+) dans
    `Theme.Kairos`. Certains constructeurs (MIUI/HyperOS, One UI, option
    développeur « Forcer le mode sombre ») assombrissent sinon les vues
    natives d'une app au thème clair : l'écran de démarrage devenait noir
    alors que la WebView restait claire. Conforme à la charte (un seul thème
    clair).
  - **État d'erreur plutôt que dévoilement à l'aveugle** : l'ancien filet de
    sécurité masquait l'overlay au bout de 30 s quoi qu'il arrive, ce qui
    dévoilait une WebView vide (écran blanc) sur un premier lancement lent,
    et la sonde chargeait l'URL même sans réponse du serveur (page d'erreur
    du WebView). Décision rouverte et remplacée : l'écran de démarrage ne
    disparaît que sur `onPageFinished` d'une page réussie. Sinon il affiche un
    titre en rouge critique, un détail technique sélectionnable (à copier
    dans un rapport de bug) et un bouton primaire « Réessayer » (44dp de
    haut, sans ombre). Cas couverts : exception au démarrage de Python ou de
    `prepare()` ; serveur qui ne répond pas en 90 s ou dont le thread s'est
    arrêté ; `onReceivedError` sur le cadre principal (la page d'erreur du
    WebView n'est jamais dévoilée, `onPageFinished` qui suit est ignoré) ;
    page non chargée en 30 s (si elle finit par charger, l'écran disparaît
    normalement). « Réessayer » relance la même chaîne : chaque étape est
    idempotente (Python déjà démarré, port déjà choisi, thread serveur
    relancé seulement s'il est mort), le réessai reprend donc là où l'échec
    a eu lieu.
  - **Python/uvicorn démarrent sur un thread dédié** (`kairos-init`), jamais
    le thread principal : `Python.start()` et surtout
    `kairos_boot.prepare()` peuvent prendre plusieurs secondes au premier
    lancement ; les exécuter dans `onCreate` empêchait tout rendu (premier
    écran blanc, corrigé en 2026-07, voir le piège ci-dessous).
  - **Piège tracé, pour ne pas le retrancher deux fois** : une première
    tentative avait retenu le splash *système* via
    `Activity.getSplashScreen().setKeepOnScreenCondition(...)` : cette
    méthode **n'existe pas** sur `android.window.SplashScreen` (la classe
    native, seule autorisée par la contrainte « pas d'AndroidX »), seulement
    sur `androidx.core.splashscreen.SplashScreen`, hors périmètre (erreur de
    compilation constatée en CI, corrigée avant tout usage réel). Le repli
    suivant, `ViewTreeObserver.OnPreDrawListener` pour reporter la première
    frame de l'activité, compilait et fonctionnait, mais souffrait du même
    problème de fond que le splash système qu'il retenait : tant que
    `onCreate` restait bloqué par l'initialisation Python synchrone, rien ne
    se dessinait à l'écran, splash retenu ou non. D'où un écran de démarrage
    applicatif **et** une initialisation hors thread principal : les deux
    ensemble, pas l'un sans l'autre. Le splash système (API 31+) reste
    volontairement court : il disparaît à la première image de l'activité,
    c'est `StartupScreen` qui porte l'attente.
- **Geste retour prédictif** (Android 13+/15, même revue) : `AndroidManifest.xml`
  pose `android:enableOnBackInvokedCallback="true"` au niveau `<application>`
  (impératif : sans lui, tout enregistrement de callback reste sans effet même
  sur API 33+). `MainActivity.registerPredictiveBackCallback()` (appelée dans
  `onCreate`, juste après `setContentView(webView)` et l'ajout de l'écran de
  démarrage) enregistre un
  `OnBackInvokedCallback` (`android.window`, natif, pas AndroidX : même parti
  pris que `KairosNotificationBridge`) uniquement si
  `Build.VERSION.SDK_INT >= TIRAMISU` ; même logique que le chemin legacy
  (retour dans la WebView si possible, sinon `finish()`).
  `onBackPressed()` (API < 33) reste **strictement inchangé** : duplication
  volontaire plutôt que factorisation, pour ne rien risquer sur ce chemin déjà
  en production ; un seul enregistrement suffit par activité, `configChanges`
  couvrant déjà la rotation (`onCreate` n'est pas rappelé).
- **Notifications système (issue #16)** : `KairosNotificationBridge` (Java, même
  parti pris sans AndroidX que `MainActivity`, avec uniquement `NotificationManager`/
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
  [`.github/workflows/release.yml`](../.github/workflows/release.yml) ; pytest
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
