# Packaging & lancement
_Rôle : comment Kairos passe d'un dépôt Python à une application qu'on double-clique
(exécutable Windows/Linux) ou qu'on installe (APK Android), et comment elle démarre
proprement dans les deux cas. Fichiers couverts : `app/launcher.py`,
`app/android_launcher.py`, `app/subprocess_env.py`, `packaging/` (`README.md`,
`kairos.spec`, `smoke_test.py`, `make_icon.py`, `kairos.ico`,
`android-requirements.txt`), `android/` (renvoi à `docs/ANDROID_PACKAGING.md` pour le
détail technique)._

## 1. Besoin métier (cahier des charges)

### Objectif / problème

Kairos est développé comme une application Starlette/uvicorn lancée en ligne de
commande (`uvicorn app.main:app`), mais ses utilisateurs cibles (collègues, puis
usage mobile) n'ont ni Python installé, ni l'habitude d'un terminal. Il faut une
distribution **autonome, en un geste** :

- sur poste de bureau (Windows, Linux) : un seul fichier exécutable à double-cliquer,
  sans installation de Python ni de dépendances ;
- sur mobile : une application Android installable (sideload APK), qui affiche la
  même interface serveur-rendu sans navigateur visible.

Contrainte transverse : Kairos reste **100 % local**, aucune donnée ne doit être
exposée sur le réseau par défaut, y compris pendant la phase de lancement.

### Comportement attendu (utilisateur)

- **Bureau** : double-clic sur l'exécutable → le navigateur par défaut s'ouvre tout
  seul sur Kairos, sans étape intermédiaire, sans terminal visible. Relancer
  l'exécutable pendant qu'une instance tourne déjà rouvre simplement le navigateur
  dessus (pas de deuxième instance, pas de port qui dérive). Un bouton « Quitter »
  dans l'interface arrête le serveur proprement. En cas d'échec au démarrage, une
  trace exploitable est disponible sans terminal.
- **Android** : icône dans le tiroir d'applications comme n'importe quelle app. Au
  lancement, l'interface Kairos s'affiche dans l'application elle-même (pas de
  navigateur externe, pas d'onglet). On quitte par les mécanismes système standards
  (bouton retour/accueil, gestionnaire d'apps) — pas de bouton « Quitter » dans
  l'interface, qui n'aurait pas de sens ici.
- Dans les deux cas, aucune donnée n'est accessible depuis un autre appareil du
  réseau local (le serveur n'écoute que sur la boucle locale).

### Critères de succès

- Un exécutable Windows et un exécutable Linux, produits par le même fichier de spec,
  démarrent et servent Kairos sans dépendance externe installée sur le poste cible.
- Relancer l'exécutable alors qu'une instance tourne déjà ne crée jamais de seconde
  instance ni ne consomme un nouveau port.
- Un crash au démarrage laisse une trace lisible sur disque, y compris en mode
  fenêtré sans console (Windows, `console=False`).
- L'APK Android démarre, sert l'interface dans une WebView, et n'expose aucun port
  sur le réseau local.
- Le smoke test (`packaging/smoke_test.py`) valide chaque exécutable construit avant
  publication d'une release ; un exécutable qui ne démarre pas bloque la release.
- Signature Android stable d'une release à l'autre (mise à jour possible par-dessus
  l'installation existante, jamais besoin de désinstaller).

### Hors périmètre / différé

- Distribution Play Store (choix assumé : APK des releases GitHub uniquement, voir
  `docs/ANDROID_PACKAGING.md` § Décisions actées).
- Foreground service Android pour faire survivre le chrono à une mise en veille
  agressive (limite v1 connue, voir `docs/ANDROID_PACKAGING.md` § Points notables).
- Widgets / ressenti natif Android au-delà de la WebView.
- Build macOS (non demandé, non couvert par `kairos.spec`).
- Cross-compilation : PyInstaller ne le permet pas, chaque OS cible est construit sur
  une machine de cet OS (CI, voir `.github/workflows/release.yml`).

## 2. Solution technique

### Vue d'ensemble

Deux points d'entrée alternatifs à `app.main`, chacun adapté aux contraintes de sa
plateforme, tous deux appliquant le même principe : poser l'environnement (port,
dossier de données) **avant** de démarrer le serveur applicatif, puis déléguer à
`uvicorn.run(..., reload=False)` (pas de rechargement ni de workers multiples : les
deux reposent sur un ré-exec du process, impossible dans un exécutable figé ou une
app Android).

- **Bureau** (`app/launcher.py`) : cible de `packaging/kairos.spec` (PyInstaller,
  mode onefile). Choisit un port, pose un verrou d'instance unique, ouvre le
  navigateur, journalise les crashs.
- **Android** (`app/android_launcher.py`) : appelé par l'amorce Chaquopy
  (`android/app/src/main/python/kairos_boot.py`) elle-même pilotée par
  `MainActivity` (Java). Pas de navigateur (WebView), pas de verrou (bac à sable
  Android = une seule instance de fait), pas de bouton Quitter (`is_frozen` faux).
- `app/subprocess_env.py` : utilitaire transverse consommé par `launcher.py` (pas par
  la voie Android, qui ne lance pas de navigateur externe) pour assainir
  l'environnement des processus externes lancés depuis un exécutable PyInstaller.

Le détail technique complet de l'implémentation Android (Gradle, Chaquopy, cycle de
vie de l'activité, notifications) est tracé dans `docs/ANDROID_PACKAGING.md` — cette
spec n'en reprend que ce qui concerne le **lancement** et n'y duplique pas le reste.

### Détail par composant

#### `app/launcher.py` — lancement de bureau

- **Choix du port** (`_pick_port`, `_port_available`) : balaie `8001` à `8020`
  (`_DEFAULT_PORT = 8001`, `tries = 20`) et prend le premier port dont une connexion
  TCP échoue (`connect_ex != 0`). Si les 20 sont occupés, retombe sur le port préféré
  et laisse uvicorn échouer avec une erreur claire plutôt que de boucler
  indéfiniment.
- **Hôte fixe `127.0.0.1`** (jamais `0.0.0.0`) : contrairement à un service systemd
  (exposé volontairement sur le LAN dans d'autres contextes de déploiement du même
  code), l'exécutable de bureau ne doit pas s'exposer par défaut sur le réseau.
- **Verrou d'instance unique** (`kairos.lock`, dans `data_dir()` — voir
  `app/settings_store.py::data_dir`) :
  - `_write_lock(port)` : écrit `{"port": ..., "pid": ...}` au démarrage.
  - `_read_lock_port()` : relit le port au lancement suivant ; verrou absent,
    illisible ou corrompu → traité comme « pas d'instance » (`None`), jamais comme
    une erreur bloquante.
  - `_instance_already_running(port)` : sonde **`/favicon.ico`** en HTTP (toujours
    200 sur une vraie instance Kairos, timeout 0.5 s) plutôt que de vérifier la
    vivacité du PID enregistré. Décision explicite : une vérification de PID est
    spécifique à l'OS (sémantique différente Windows/Linux/macOS), alors qu'une
    sonde HTTP est portable et vérifie directement la propriété qui compte (« le
    port sert vraiment Kairos »), pas juste « un process avec ce PID existe encore »
    (qui pourrait être un tout autre programme ayant recyclé le PID). Toute réponse
    autre que 200 (204, 404, refus de connexion) est traitée comme « pas une
    instance Kairos réutilisable », jamais comme une erreur.
  - `_clear_lock()` : appelé dans le `finally` de `main()`. Atteint après un arrêt
    propre (bouton Quitter → SIGINT → `uvicorn.run` revient normalement). Un
    SIGTERM ou une fermeture brutale (Gestionnaire des tâches) laisse le verrou en
    place — sans conséquence, car `_instance_already_running` le détecte comme
    obsolète dès que le port ne répond plus, au lieu de bloquer les lancements
    suivants.
  - **Pourquoi ce verrou existe** : fermer l'onglet du navigateur n'arrête pas le
    serveur. Sans détection d'instance existante, relancer l'exécutable ferait
    avancer le port choisi à chaque fois (8001, 8002, ...) puisque l'instance
    précédente tourne toujours en arrière-plan.
- **Ouverture du navigateur** (`_open_browser`, `_open_browser_later`) :
  - `KAIROS_NO_BROWSER` (variable d'environnement) : échappatoire pour les
    lancements automatisés (`packaging/smoke_test.py`) où un vrai navigateur est
    indésirable (processus fantôme sur un runner CI, effets de bord imprévisibles).
  - `external_process_environ()` (voir `app/subprocess_env.py`) encadre l'appel à
    `webbrowser.open` : évite qu'un navigateur ou `xdg-open` hérite du
    `LD_LIBRARY_PATH` détourné par PyInstaller onefile.
  - `_open_browser_later` : ouvre le navigateur après un délai (`threading.Timer`,
    1.2 s par défaut) pour laisser le temps à uvicorn de démarrer avant la première
    requête.
- **`_NullStream` et `_ensure_std_streams`** : sous Windows, un exécutable
  PyInstaller en mode fenêtré (`console=False`) n'a pas de console attachée —
  `sys.stdout`/`sys.stderr` valent `None` plutôt qu'un flux réel. uvicorn plante dès
  la configuration de son logging par défaut (`ColourizedFormatter.__init__` appelle
  `stream.isatty()`) sur un flux `None`. `_NullStream` (méthodes `write`/`flush`/
  `isatty` minimales, no-op) remplace `None` avant tout démarrage, pour ce cas et
  pour tout autre code qui suppose un flux réel.
- **`multiprocessing.freeze_support()`** : appelé en tête de `main()`, requis sous
  Windows/PyInstaller pour la ré-exécution figée du process (garde-fou standard
  PyInstaller, même si Kairos n'utilise pas directement `multiprocessing`).
- **Journal de crash** : toute exception dans le bloc `try` principal est capturée,
  la trace complète (`traceback.format_exc()`) écrite dans
  `<dossier de données>/kairos-crash.log`, un message loggé pointant vers ce fichier,
  puis l'exception est re-levée (`raise`) — comportement visible pour un
  développeur qui lance depuis un terminal, traçable pour un utilisateur qui ne voit
  qu'une fenêtre qui se ferme.
- **Imports absolus** (`from app.main import app`, pas `from .main import app`) :
  PyInstaller exécute `launcher.py` comme script top-level
  (`Analysis(['app/launcher.py'])`), sans contexte de paquet parent — un import
  relatif y échouerait avec « attempted relative import with no known parent
  package ». Les imports absolus fonctionnent dans les deux cas (exécutable figé et
  `pip install -e .` via `[project.scripts]` → commande `kairos`), tant que la
  racine du dépôt est sur `sys.path` (`pathex` du spec, ou le `.pth` du mode
  editable).

#### `app/android_launcher.py` — lancement Android

- Pendant Android de `launcher.py`, en plus simple : pas de navigateur (la WebView de
  `MainActivity` affiche directement l'interface), pas de verrou d'instance unique
  (le bac à sable applicatif Android garantit une seule instance de fait), pas de
  bouton Quitter (conditionné à `is_frozen`, faux ici — voir
  `templates/base.html`).
- **`prepare(files_dir)`** (rapide, appelé sur le thread UI par `MainActivity`) :
  - pose `KAIROS_DATA_DIR = <files_dir>/kairos-data` (`Context.getFilesDir()`,
    stockage privé de l'application) — ancrage déterministe, sans dépendre de la
    détection Android de `platformdirs` ;
  - `os.environ.setdefault("HOME", files_dir)` ;
  - retourne le port choisi (`_pick_port`, même logique que le launcher de bureau :
    balaie 8001-8020, retombe sur le port préféré si aucun n'est libre).
  - **Ordre critique** : ceci doit s'exécuter avant tout import de `app.main`, car le
    moteur de base de données et `BASE_DIR` sont résolus **à l'import** de ce
    module. C'est pour cela que `prepare` ne fait qu'importer `os`/`socket` en tête
    de fichier, jamais `app.main`.
- **`serve(port)`** (bloquant, appelé dans un thread Java dédié) : importe
  `app.main` et `uvicorn` **localement**, jamais en tête de module — garantit que
  `prepare` a bien posé l'environnement avant que `app.main` (et donc `BASE_DIR`,
  les réglages) ne soit résolu. Lance `uvicorn.run(app, host="127.0.0.1",
  port=int(port), reload=False)`.
- Hôte fixe `127.0.0.1` : l'application ne doit pas s'exposer sur le réseau, même
  motif que côté bureau.
- Chaîne d'appel complète côté Android (Java → Python), pour situer ces deux
  fonctions dans le flux de démarrage : `MainActivity.onCreate` démarre Chaquopy,
  appelle `kairos_boot.prepare(filesDir)` (qui ajoute `kairos_dist` à `sys.path`,
  pose `KAIROS_BASE_DIR`/`KAIROS_PLATFORM=android`, puis délègue à
  `android_launcher.prepare`), lance `kairos_boot.serve(port)` dans un thread Java
  nommé `kairos-uvicorn`, puis sonde `/favicon.ico` (même repère que le launcher de
  bureau) avant de charger `http://127.0.0.1:<port>/kairos` dans la WebView. Détail
  complet (Gradle, Chaquopy, cycle de vie, notifications) :
  `docs/ANDROID_PACKAGING.md`. `KAIROS_PLATFORM` est posé **avant** tout import de
  `app.main` : c'est ce qui permet à `app/main.py` de le lire une seule fois au
  chargement du module (`is_android`, voir `docs/spec/accueil-navigation.md` — seule
  consommation actuelle de cette variable, pour la bottom nav de `base.html`).

#### `app/subprocess_env.py` — environnement assaini pour les processus externes

- **Problème corrigé** : PyInstaller en mode onefile réachemine `LD_LIBRARY_PATH`
  (Linux) / `DYLD_LIBRARY_PATH` (macOS) vers son dossier d'extraction temporaire,
  pour que l'exécutable gelé y retrouve ses propres bibliothèques embarquées, et
  sauvegarde la valeur d'origine dans une variable `..._ORIG` (absente si la
  variable n'existait pas avant PyInstaller). Un processus externe qui hériterait de
  la variable détournée (navigateur, `xdg-open` — lui-même un script shell, `git`...)
  peut charger par erreur une bibliothèque embarquée par PyInstaller (ex.
  `libreadline.so`, tirée par le module `readline` d'un interpréteur figé)
  incompatible avec la sienne, au lieu de celle du système. **Observé en conditions
  réelles** : un `/bin/sh` (symlink vers `bash` sur certaines distributions) plantait
  au lancement avec `undefined symbol: rl_print_keybinding`, la bibliothèque système
  attendant une version de `libreadline` plus récente que celle embarquée.
- **`external_process_env()`** : copie de `os.environ` avec les chemins de
  bibliothèques dynamiques restaurés à leur valeur d'avant PyInstaller (dépile
  `..._ORIG`, ou retire la variable si elle n'existait pas avant) — à passer en
  `env=` à `subprocess.run`/`Popen` pour un exécutable externe.
- **`external_process_environ()`** (context manager) : bascule temporairement
  `os.environ` du process courant sur cet environnement assaini, pour les API qui ne
  permettent pas de passer un `env=` explicite (cas de `webbrowser.open`, utilisé par
  `app/launcher.py::_open_browser`). Restaure l'état d'origine en sortie de contexte
  (`finally`), y compris l'absence de la variable si elle n'était pas définie.
- **Sans effet hors d'un exécutable PyInstaller** (mode `pip install -e .`, service
  systemd) : les variables `_ORIG` n'existent alors pas, donc rien n'est modifié —
  ce module est un no-op transparent dans tous les autres modes de lancement.
- Non utilisé côté Android : `android_launcher.py` ne lance aucun processus externe
  (pas de navigateur à ouvrir, la WebView est intégrée).

#### `packaging/` — build et vérification des exécutables de bureau

- **`kairos.spec`** (PyInstaller, mode **onefile**) :
  - Un seul fichier de spec partagé Linux/Windows : PyInstaller ne fait pas de
    cross-compile, seule la **machine** de build change (voir
    `.github/workflows/release.yml`) ; le contenu du spec est identique pour les
    deux OS.
  - `hiddenimports` : `collect_submodules("uvicorn")` (uvicorn choisit dynamiquement
    sa boucle d'événements et son implémentation de protocole HTTP selon les
    paquets optionnels installés — invisible à l'analyse statique de PyInstaller) et
    `collect_submodules("keyring")` (keyring sélectionne son back-end — Windows
    Credential Manager, SecretService, Keychain — via les entry-points de son propre
    paquet, également invisible statiquement ; point du packaging jugé le plus
    incertain, à vérifier empiriquement en lançant l'exécutable construit, le repli
    fichier local de `app/secret_store.py` restant sûr en dernier recours).
  - `datas` : embarque `templates/`, `static/`, `README.md`, `SPEC_KAIROS.md`, et les
    métadonnées `keyring` (`copy_metadata`, requis par certains back-ends qui lisent
    leurs propres métadonnées de distribution au runtime).
  - Point d'entrée : `Analysis([app/launcher.py])`, `pathex=[ROOT]` (racine du
    dépôt sur `sys.path`, condition des imports absolus décrits plus haut).
  - `console=False` : pas de fenêtre de terminal visible (ressenti « application de
    bureau ») ; tout échec de démarrage est journalisé dans un fichier par
    `app/launcher.py` plutôt que perdu derrière une fenêtre qui se ferme aussitôt.
  - `icon=packaging/kairos.ico` : embarqué dans le `.exe` Windows (barre des tâches,
    explorateur) ; ignoré sans erreur pour le binaire Linux (qui n'en porte pas —
    une icône de bureau viendrait d'un fichier `.desktop`, pas du binaire lui-même).
- **`packaging/README.md`** : mode d'emploi de construction locale
  (`pip install -e ".[dev]" pyinstaller pyinstaller-hooks-contrib` puis
  `pyinstaller packaging/kairos.spec --distpath dist --noconfirm`), et points
  d'attention :
  - les données (réglages, base de tâches) vivent dans le dossier utilisateur de
    l'OS (`platformdirs`), jamais à côté de l'exécutable — voir
    `app/settings_store.py::data_dir` ;
  - le trousseau système (`keyring`, jeton GitLab / mot de passe TimeTree) dépend de
    ce qui est disponible sur le poste ; sans back-end utilisable (Linux headless),
    Kairos dégrade proprement vers un stockage fichier local, sans erreur, avec
    juste un bandeau dans la page Réglages — à vérifier après chaque build par OS
    cible.
- **`packaging/make_icon.py`** : régénère `kairos.ico` depuis le même dessin que
  `static/favicon.svg`, après une évolution du logo (nécessite Pillow).
- **`packaging/smoke_test.py`** : lance l'exécutable construit et vérifie qu'il
  répond en HTTP avant publication (`.github/workflows/release.yml` l'exécute pour
  chaque OS juste après le build PyInstaller ; échec = pas de publication).
  - Usage : `python packaging/smoke_test.py dist/kairos[.exe]`.
  - Isole `KAIROS_DATA_DIR` dans un dossier temporaire (`tempfile.TemporaryDirectory`,
    `ignore_cleanup_errors=True`) pour ne jamais toucher une installation réelle sur
    la machine qui exécute le script, et pour trouver de façon déterministe le port
    choisi (lecture du `kairos.lock` généré dans ce dossier isolé).
  - Lance l'exécutable avec `KAIROS_NO_BROWSER=1` (voir `app/launcher.py`), sortie
    redirigée vers un **fichier**, pas un pipe : `webbrowser.open()` lancerait un
    vrai navigateur sur Windows (contrairement à un runner Linux headless sans
    `DISPLAY`, où il échoue instantanément), qui hériterait du handle de sortie du
    process et le garderait ouvert après que l'exécutable ait été tué — ce qui
    bloquerait indéfiniment `Popen.stdout.read()` (attente d'un EOF qui n'arrive
    jamais) avec un pipe. Lire un fichier ne dépend pas des autres porteurs du
    handle d'écriture.
  - `_wait_until_serving` : sonde le verrou (`kairos.lock`) puis `/favicon.ico`
    (`_STARTUP_TIMEOUT = 30 s`, `_POLL_INTERVAL = 0.25 s`), abandonne tôt si le
    process s'arrête tout seul plutôt que d'attendre le timeout entier.
  - `_terminate_tree` : sur Windows, PyInstaller onefile exécute l'application réelle
    dans un processus **enfant** du bootloader lancé par le script —
    `process.terminate()` seul ne tue que ce bootloader parent et laisse l'enfant
    orphelin, qui garde alors le fichier de sortie ouvert (`PermissionError:
    [WinError 32]` constaté en conditions réelles à la suppression du dossier
    temporaire). `taskkill /F /T /PID <pid>` cible tout l'arbre de processus, pas
    seulement le PID direct. Sur les autres OS, `process.terminate()` suffit.

#### `android/` — packaging Android (renvoi, non dupliqué)

Le détail technique (structure Gradle, tâche `stageKairosPython`, Chaquopy,
`MainActivity`, `network_security_config.xml`, permissions, dérivation de
`versionCode`/`versionName` depuis le tag de release, notifications système) est
consigné dans **`docs/ANDROID_PACKAGING.md`**, référence technique transverse pour ce
domaine. Ce qui touche spécifiquement au **lancement** (chaîne `prepare`/`serve`,
choix du port, absence de verrou et de bouton Quitter) est repris ci-dessus dans le
détail de `app/android_launcher.py`, qui est le point de jonction entre ce document
et `docs/ANDROID_PACKAGING.md`.

Pour mémoire, uniquement les éléments qui conditionnent le comportement décrit dans
cette spec (pas de duplication du reste) :

- **`packaging/android-requirements.txt`** : dépendances Python embarquées par
  Chaquopy (uvicorn nu, sans `[standard]` : uvloop/httptools/watchfiles sont des
  extensions natives sans intérêt pour un serveur local mono-utilisateur).
- **`AndroidManifest.xml`** : permission `INTERNET` (requise même pour le loopback :
  Android compte toute socket réseau, locale comprise) et `POST_NOTIFICATIONS` (API
  33+, demandée à l'exécution, jamais au démarrage) ;
  `android:networkSecurityConfig` autorise le HTTP en clair uniquement vers
  `127.0.0.1` (le reste — TimeTree, GitLab — reste en HTTPS).
- **`versionCode`/`versionName`** (`android/app/build.gradle`) : dérivés du tag de
  release CI (`KAIROS_VERSION=vX.Y.Z`) ; défaut `0.0.0-dev` pour les builds hors tag.
  `versionCode = X*10000 + Y*100 + Z`, plancher `1` (Android rejette `0`, ce que
  donnerait le défaut `0.0.0-dev`) — garantit une valeur strictement croissante
  d'une release à l'autre pour qu'Android accepte la mise à jour par-dessus.
- **`themes.xml`** : splash screen natif (`android:windowSplashScreenBackground`,
  API 31+) et `android:windowBackground` (toutes API) posés à la couleur de fond de
  l'app — évite le flash blanc générique pendant le démarrage de Python+uvicorn,
  sans dépendance `androidx.core:splashscreen` (voir « pas d'AndroidX » dans
  `docs/ANDROID_PACKAGING.md`). Sa durée réelle d'affichage est contrôlée depuis
  `MainActivity` (`getSplashScreen().setKeepOnScreenCondition(...)`, natif, API
  31+) : sans ce contrôle, le thème seul ne suffit pas à couvrir l'attente du
  serveur, le splash se ferme dès la première frame dessinée. Son icône est un
  `AnimatedVectorDrawable` dédié (`res/drawable/kairos_splash_icon*.xml` +
  `res/animator/kairos_splash_wedge_sweep.xml`, natif, API 21+) : le secteur du
  logo balaie depuis midi jusqu'à sa position finale plutôt que d'apparaître
  figé. Détail complet dans `docs/ANDROID_PACKAGING.md`.
- **`AndroidManifest.xml`** / **`MainActivity.java`** : geste retour prédictif
  Android 13+ (`android:enableOnBackInvokedCallback="true"` +
  `OnBackInvokedDispatcher` natif, `android.window`, pas AndroidX) — chemin
  additionnel à `onBackPressed()` (legacy, inchangé, seul chemin actif en dessous de
  l'API 33). Détail complet dans `docs/ANDROID_PACKAGING.md`.

### Décisions et pièges tracés

- **Sonde `/favicon.ico` plutôt que vérification de PID** pour détecter une instance
  déjà lancée (`app/launcher.py::_instance_already_running`) : multiplateforme plus
  simple, et vérifie directement « le port sert Kairos » plutôt que « un process
  avec ce PID existe » (qui peut être n'importe quel programme après recyclage du
  PID par l'OS). Décision consignée en commentaire de code, reprise ici comme
  registre de conception.
- **SIGINT, jamais SIGTERM, pour le bouton Quitter** (`app/main.py::shutdown`) :
  les deux déclenchent l'arrêt normal d'uvicorn (drainage des requêtes en cours),
  mais un SIGTERM laisse ensuite l'OS tuer le process avant que le `finally` de
  `app/launcher.py::main` (nettoyage du verrou) ne s'exécute — vérifié
  empiriquement, SIGINT laisse ce `finally` s'exécuter normalement. `os.kill
  (os.getpid(), signal.SIGINT)` littéralement équivalent à un Ctrl+C.
- **`KAIROS_DATA_DIR` posé avant tout import de `app.main`** (Android,
  `android_launcher.prepare` → `kairos_boot._ensure_app_importable` en amont) :
  le moteur de base de données et `BASE_DIR` sont résolus à l'import de `app.main`,
  pas dans une fonction appelée plus tard ; inverser l'ordre pointerait la base
  SQLite et les templates vers le mauvais dossier pour toute la durée de vie du
  process.
- **`KAIROS_NO_BROWSER`** : échappatoire délibérée pour les lancements automatisés
  (smoke test, CI) — évite un navigateur fantôme sur un runner headless et les
  effets de bord d'un vrai navigateur ouvert pendant un test.
- **Pas de `reload`/`workers>1` dans aucun des deux `uvicorn.run`** : ces options
  reposent toutes deux sur un ré-exec du process (rechargeur, workers multiples),
  incompatible avec un exécutable figé (PyInstaller) ou une application Android.
- **`console=False`** accepté malgré la perte de visibilité d'un crash immédiat, en
  échange d'un ressenti « application de bureau » — compensé par le fichier de
  journal (`kairos-crash.log`) et par `_ensure_std_streams`/`_NullStream` pour éviter
  qu'uvicorn ne plante avant même d'écrire ce journal.
- **`_LIBRARY_PATH_VARS` restaurées uniquement si `..._ORIG` existe** : sinon la
  variable est purement retirée de l'environnement du sous-processus — reproduit
  fidèlement l'état d'avant PyInstaller (variable absente au départ → absente pour
  le sous-processus), plutôt que de la laisser vide ou undefined de façon
  incohérente.
- **Icône Windows embarquée, icône Linux absente du binaire** : décision assumée
  (pas un oubli) — sur Linux, l'icône de bureau viendrait d'un fichier `.desktop`,
  jamais du binaire lui-même ; `icon=` dans `kairos.spec` est ignoré sans erreur sur
  cette plateforme.

### Invariants et garde-fous

- Le serveur (bureau et Android) n'écoute **jamais** que sur `127.0.0.1` — jamais
  `0.0.0.0` — dans ces deux points d'entrée (un déploiement systemd exposé sur le
  LAN est un cas d'usage distinct, hors de ces deux launchers).
- Un verrou d'instance obsolète ne bloque jamais un nouveau lancement : toute lecture
  de `kairos.lock` invalide, absente ou pointant vers un port mort retombe sur un
  démarrage normal.
- Le port choisi reste dans la plage `8001`-`8020` (bureau et Android) ; au-delà,
  uvicorn échoue avec son erreur native plutôt qu'une boucle infinie côté Kairos.
- Le smoke test doit passer sur chaque OS avant publication d'une release
  (`.github/workflows/release.yml`) — aucun exécutable cassé n'est publiable.
- Le contenu de `kairos.spec` reste strictement identique entre les deux OS cibles
  (aucune branche conditionnelle par plateforme dans le spec) : seule la machine de
  build diffère.
