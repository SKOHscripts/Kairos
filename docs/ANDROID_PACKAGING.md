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

1. `MainActivity` (Java, sans AndroidX) démarre Chaquopy et appelle
   `kairos_boot.prepare(filesDir)` : le paquet embarqué `kairos_dist` (extrait
   de l'APK en vrais fichiers, voir ci-dessous) est ajouté à `sys.path`,
   `KAIROS_BASE_DIR` et `KAIROS_PLATFORM=android` sont posés, puis
   `app/android_launcher.py` ancre les données dans le stockage privé
   (`KAIROS_DATA_DIR`) et choisit un port libre.
2. `kairos_boot.serve(port)` lance uvicorn dans un thread dédié.
3. L'activité sonde `/favicon.ico` (même repère que le launcher de bureau)
   puis charge `http://127.0.0.1:<port>/kairos` dans la WebView.

Empaquetage du code : la tâche Gradle `stageKairosPython` copie `app/`,
`templates/`, `static/`, `README.md` et `SPEC_KAIROS.md` dans un paquet Python
unique `kairos_dist` (même arborescence que le dépôt : `BASE_DIR` fonctionne
sans changement), déclaré via `extractPackages` pour que Jinja2, StaticFiles et
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
