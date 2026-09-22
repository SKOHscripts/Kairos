# Mises à jour

_Rôle : prévenir l'utilisateur qu'une nouvelle version de Kairos est publiée,
et l'installer pour lui en un clic, depuis la forge d'où vient sa version
(GitHub public ou GitLab privé d'entreprise). Fichiers couverts :
`app/build_info.py`, `app/updates.py`, les routes `/kairos/updates/*` de
`app/main.py`, le bandeau de `templates/base.html`, la section « Mises à jour »
de la page Réglages, `packaging/write_build_info.py`, le pont Android
(`KairosNotificationBridge`, `UpdateApkProvider`), `.github/workflows/release.yml`
(somme de contrôle, version embarquée) et `.gitlab-ci.yml`._

**Hors de cette spec** : le lancement de l'exécutable et la reprise après
redémarrage (verrou, port) → `docs/spec/packaging-lancement.md` ; le modèle de
réglages et le trousseau → `docs/spec/reglages-secrets.md` ; la cascade de
notifications des alertes chrono, réutilisée ici → `docs/spec/temps-reel-chrono.md`.

---

## 1. Besoin métier (cahier des charges)

### Objectif / problème

Kairos est distribué en fichiers à télécharger depuis les releases (exécutable
Windows/Linux, APK Android) ou en clone du dépôt. Rien ne signalait jusqu'ici
qu'une nouvelle version était sortie : l'utilisateur restait sur une version
ancienne sans le savoir, et mettre à jour demandait de retourner sur la page
des releases, de retrouver le bon fichier et de remplacer l'ancien à la main.

L'outil va aussi être hébergé sur un **GitLab d'entreprise privé**, différent
du GitLab dont Kairos importe les issues : la source des mises à jour doit
suivre la forge d'où vient la version installée, sans être codée en dur.

### Comportement attendu (utilisateur)

- **Alerte** : quand une version plus récente est publiée, un bandeau
  apparaît en haut de chaque page (« Kairos X.Y.Z est disponible, vous avez
  A.B.C »). Une notification système est émise une seule fois par version,
  par la même voie que les alertes chrono (notification native Android,
  notification du navigateur, ou notification émise par Kairos lui-même).
- **Clic** : cliquer sur « Mettre à jour » dans le bandeau, ou sur la
  notification quand la plateforme le permet, lance la mise à jour :
  - **exécutable de bureau** : téléchargement, vérification de la somme de
    contrôle, remplacement de l'exécutable, redémarrage de Kairos ; la page
    ouverte se recharge d'elle-même sur la nouvelle version ;
  - **Android** : téléchargement de l'APK, vérification, puis ouverture de
    l'installeur Android (l'utilisateur confirme ; la première fois, Android
    demande d'autoriser Kairos à installer des applications) ;
  - **installation depuis les sources** (clone + venv, service systemd) :
    pas d'installation automatique ; le bandeau donne la commande à lancer.
- La progression est visible dans le bandeau (téléchargement en pourcentage,
  vérification, redémarrage) ; un échec est expliqué et laisse un lien vers la
  page de la release.
- « Plus tard » masque le bandeau pour cette version ; la version suivante
  réapparaît normalement.
- **Réglages** (section « Mises à jour ») : activer ou couper la vérification,
  voir la version installée et le résultat de la dernière vérification,
  vérifier maintenant, et **surcharger la source** : URL de la forge, projet,
  jeton en lecture seule pour un dépôt privé. Ces réglages sont distincts de
  ceux de l'import des issues GitLab (autre instance, autre jeton possibles).
- **Source par défaut** : la forge d'où vient la version installée. Un
  exécutable ou un APK construit par la CI GitHub pointe vers les releases
  GitHub du dépôt ; construit par la CI GitLab, vers les releases de ce projet
  GitLab ; une installation depuis les sources suit le remote `origin` du
  clone. Aucun réglage n'est nécessaire dans le cas courant, sauf le jeton
  d'un dépôt privé.

### Critères de succès

- Aucun fichier n'est installé sans correspondre à la somme SHA-256 publiée
  avec la release (`SHA256SUMS`). Une release sans somme de contrôle n'est
  jamais installée automatiquement.
- Le jeton de la source des mises à jour n'est envoyé qu'à la forge
  configurée, jamais à un hôte tiers (redirections de téléchargement
  comprises), et n'est jamais réaffiché dans l'interface.
- Une vérification qui échoue (hors ligne, jeton invalide, projet inconnu)
  n'empêche jamais l'application de fonctionner : le résultat est visible
  dans les réglages, rien d'autre.
- Pas plus d'une vérification réseau toutes les 6 heures, sauf « Vérifier
  maintenant ».
- Après une mise à jour de l'exécutable de bureau, Kairos redémarre sur le
  même port quand il est libre, sans ouvrir de seconde fenêtre.
- Les releases GitHub et GitLab portent les mêmes noms de fichiers et un
  `SHA256SUMS`, pour que la même logique serve les deux forges.

### Hors périmètre / différé

- Mise à jour silencieuse sans action de l'utilisateur (le clic reste requis :
  remplacer un exécutable ou installer un APK n'est jamais fait dans son dos).
- Signature cryptographique des releases au-delà de la somme de contrôle
  (l'APK reste en plus vérifié par Android sur sa clé de signature).
- GitHub Enterprise Server (une URL autre que `github.com` est traitée comme
  un GitLab).
- Retour à une version antérieure, canaux bêta ou préversions (les tags avec
  suffixe, `v2.6.0-rc1`, sont ignorés).
- Mise à jour d'une installation depuis les sources (`git pull` automatique).

---

## 2. Solution technique

### Vue d'ensemble

```
CI (release.yml / .gitlab-ci.yml)
  └─ packaging/write_build_info.py ─► app/_build_info.py (VERSION, forge, projet ; jamais commité)
                                            │
app/build_info.py ◄─────────────────────────┘  (sinon : git describe / remote origin)
  version(), default_source(), install_kind()
        │
app/config.py : update_source, update_token_effective (réglages > défaut)
        │
app/updates.py : lecture des releases, état, téléchargement vérifié, remplacement
        │
app/main.py : update_status (global de gabarit) + routes /kairos/updates/*
        │
templates/_update_banner.html + script de base.html ◄─► pont Android (KairosNotificationBridge)
```

### `app/build_info.py` : version installée et source par défaut

- `install_kind()` : `android` si `KAIROS_PLATFORM=android` (posé par
  `kairos_boot.py`), `desktop` si `sys.frozen` (PyInstaller), sinon `source`.
- `version()` (mise en cache) : `VERSION` de `app/_build_info.py` s'il existe ;
  sinon, pour une installation depuis les sources, le dernier tag atteignable
  (`git describe --tags --abbrev=0`) ; le `v` initial est retiré. Inconnue :
  `UNKNOWN_VERSION = "0.0.0-dev"`, qui ne déclenche jamais d'alerte (un build de
  développement ne se compare à rien).
- `default_source()` (mise en cache) : `UPDATE_SERVER_URL`/`UPDATE_PROJECT` du
  fichier généré ; sinon `parse_remote(git remote get-url origin)` pour une
  installation depuis les sources ; sinon `("", "")`.
- `parse_remote(remote)` : `git@hôte:chemin`, `ssh://[user@]hôte[:port]/chemin`,
  `http(s)://[identifiants@]hôte[:port]/chemin`, `.git` final retiré ; rend
  `("https://hôte[:port]", "groupe/projet")`. Le port n'est gardé que pour un
  remote HTTP(S) (un port SSH n'a pas de sens en HTTPS). Les identifiants d'un
  remote HTTPS ne sont jamais recopiés. Un chemin sans `/` (pas de groupe) ou
  un chemin local rend `None`.
- `_git(*args)` : `git` lancé dans la racine du dépôt (seulement si `.git`
  existe), `timeout=3`, environnement assaini (`external_process_env`), jamais
  d'exception.

### `packaging/write_build_info.py`

`--version vX.Y.Z --server-url URL --project chemin [--output]` : écrit
`app/_build_info.py` (`VERSION`, `UPDATE_SERVER_URL` sans `/` final,
`UPDATE_PROJECT` sans `/` en bord). Appelé par les deux CI juste avant
PyInstaller et Gradle (la tâche `stageKairosPython` copie `app/`, fichier
généré compris, dans l'APK). Jamais de jeton dans ce fichier : il est lisible
dans l'exécutable. `app/_build_info.py` est dans `.gitignore`.

### Réglages (`app/config.py`, détail dans `reglages-secrets.md`)

`update_check_enabled` (défaut vrai), `update_server_url`, `update_project`,
`update_token` (secret, trousseau), section « Mises à jour ». Propriétés
`update_source` (réglage, sinon `default_source()`) et `update_token_effective`
(réglage, sinon jeton `git credential`/`.netrc` pour une forge GitLab ; jamais
pour github.com). Volontairement distincts de `gitlab_url`/`gitlab_token`.

### `app/updates.py`

**Forge** (`Source(server_url, project, token)`) :

- `provider` : `github` si l'hôte est `github.com`/`www.github.com`/
  `api.github.com` (`is_github`), sinon `gitlab`.
- GitHub : `GET https://api.github.com/repos/<projet>/releases/latest`
  (`Authorization: Bearer` si jeton). Les brouillons de release ne sont pas
  « latest » : une release GitHub n'est proposée qu'une fois publiée. Lien de
  fichier : `browser_download_url` sans jeton, URL d'API du fichier avec jeton
  (seule acceptée pour un dépôt privé).
- GitLab : `GET <forge>/api/v4/projects/<projet encodé>/releases?per_page=20`
  (`PRIVATE-TOKEN` si jeton), première release (tri par date décroissante)
  non `upcoming_release` et dont le tag est `vX.Y.Z` ; liens
  `assets.links[].direct_asset_url`, à défaut `url`.
- `parse_version` : `^v?X.Y.Z$` uniquement ; une préversion (`-rc1`) ou tout
  autre tag est ignoré.
- `trusts(url)` : GitHub, les trois hôtes ci-dessus ; GitLab, même hôte et
  même port que la forge. **Le jeton n'est envoyé qu'à ces hôtes** : `_send`
  suit les redirections à la main (`follow_redirects=False`, 5 au plus) et
  recalcule les en-têtes à chaque saut (un téléchargement GitHub est redirigé
  vers un stockage tiers ; `httpx` ne retire pas de lui-même un en-tête
  `PRIVATE-TOKEN`).
- `_check_scheme` : HTTPS obligatoire, HTTP toléré seulement vers la boucle
  locale (forge de test).
- Erreurs (`UpdateError`, message affichable) : 401/403 « Accès refusé par la
  forge : jeton absent, invalide ou sans droit de lecture. », 404 « Projet ou
  release introuvable : vérifier le projet, ou renseigner un jeton si le dépôt
  est privé. », autre code « Réponse inattendue de la forge (HTTP n). »,
  exception réseau « Forge injoignable (…). », JSON illisible, aucune release,
  tag non reconnu.

**État** (`_State`, verrou `_lock`) : en mémoire et dans
`<données>/update-state.json` (écriture atomique) : `source_key`
(`url|projet` de la dernière vérification), `checked_at`, `latest`
(`Release` : tag, page, fichiers), `error`, `dismissed` (tag masqué par
« Plus tard »), `notified` (tag déjà notifié). L'état d'installation
(`_Install` : `phase`, `progress`, `message`) reste en mémoire.

- `snapshot(settings, trigger_check=True)` : dictionnaire sérialisable lu par
  le bandeau, les réglages et `GET /kairos/updates/status` : `current`,
  `kind`, `enabled`, `source`, `releases_url`, `checking`, `checked_at`,
  `error`, `available`, `latest`, `tag`, `page_url`, `dismissed`, `notified`,
  `can_install`, `install_blocker`, `command`, `install`. `available` exige la
  vérification activée, une version installée reconnue et une release
  strictement plus récente **de la source actuelle** (un changement de source
  dans les réglages masque la release de l'ancienne jusqu'à la vérification
  suivante).
- Vérification de fond (`_maybe_check_async`, thread `kairos-update-check`) :
  déclenchée par `snapshot` (donc par l'affichage d'une page) si la
  vérification est activée, la source connue, aucune vérification en cours,
  et la dernière date de plus de `CHECK_INTERVAL = 6 h` ou concerne une autre
  source. `KAIROS_UPDATE_CHECK=0` la coupe (tests, smoke test de la CI).
  Jamais d'attente réseau pendant le rendu d'une page.
- `check_now(settings)` : vérification synchrone (« Vérifier maintenant »),
  indépendante de l'intervalle et du réglage d'activation.
- `_run_check` : une forge injoignable garde la dernière release connue de la
  même source (l'erreur s'affiche dans les réglages) ; toute exception
  inattendue est journalisée et réduite à un message.
- `dismiss(tag)`, `claim_notification(tag)` : la seconde rend vrai une seule
  fois par version (arbitre entre plusieurs pages ouvertes).

**Installation** (`start_install(settings, port)`, thread
`kairos-update-install`) :

- Refusée (`UpdateError`) si une installation est en cours, si aucune release
  de la source actuelle n'est connue, ou si `_install_blocker` : installation
  depuis les sources (« mise à jour à faire à la main »), pas de fichier pour
  la plateforme (`DESKTOP_ASSETS` : `kairos-windows-x86_64.exe` sous
  `win32`, `kairos-linux-x86_64` sous `linux` ; `ANDROID_ASSET` :
  `kairos-android-arm64.apk`), ou pas de `SHA256SUMS` dans la release.
- Télécharge `SHA256SUMS` (`parse_checksums` : format `sha256sum`, `*nom`
  accepté), exige une ligne pour le fichier, puis télécharge le fichier en
  flux vers `<nom>.part` en calculant son SHA-256 et la progression
  (`Content-Length`). Empreinte différente : `.part` supprimé, erreur
  « Somme de contrôle incorrecte : fichier corrompu ou modifié, rien n'a été
  installé. ». Sinon renommé à son nom final.
- **Android** : fichier final `<données>/updates/kairos-update.apk`
  (`ANDROID_APK_PATH`, chemin relu tel quel côté Java), phase `ready` ; la
  suite est côté page et pont Android.
- **Bureau** : `replace_executable(fichier, sys.executable)` copie d'abord à
  côté de l'exécutable (`<nom>.new`, même système de fichiers, `chmod 755`
  hors Windows) puis renomme. Linux : renommer par-dessus un exécutable lancé
  est permis. Windows : l'écraser est refusé mais le renommer est permis ;
  l'ancien devient `<nom>.old` (restauré si le second renommage échoue),
  supprimé au lancement suivant par `app/launcher.py`. Échec (dossier
  protégé...) : erreur qui donne le chemin du fichier vérifié.
- Puis `restart_desktop(exe, port)` : lance le nouvel exécutable détaché
  (`start_new_session` / `DETACHED_PROCESS`), environnement assaini +
  `PYINSTALLER_RESET_ENVIRONMENT=1` (process PyInstaller indépendant, pas un
  enfant qui réutiliserait le dossier d'extraction) + `KAIROS_RESTART_PORT`,
  puis arrête ce process par SIGINT après `_RESTART_DELAY = 1 s` (le temps
  que la page lise la phase `restarting`), comme le bouton Quitter.
- La reprise côté nouveau process (attendre la libération du port, le
  reprendre, ne pas rouvrir de fenêtre, verrou) est décrite dans
  `packaging-lancement.md`.

### Routes (`app/main.py`)

- `update_status(request)` : `snapshot` + `server_notify`
  (`server_notifications_available`), exposé aussi en global de gabarit.
- `GET /kairos/updates/status` : JSON.
- `POST /kairos/updates/check` : `check_now` ; JSON pour `fetch`, sinon 303
  vers `/kairos/settings?updates=1#mises-a-jour`.
- `POST /kairos/updates/dismiss` (`tag`, `next`) : 204 pour `fetch`, sinon
  303 vers `next` s'il s'agit d'un chemin local (`_safe_next` refuse `//` et
  les URL absolues), `/` sinon.
- `POST /kairos/updates/notified` (`tag`) : `{"claimed": bool}` ; en-tête
  `X-Requested-With: fetch` exigé (403 sinon).
- `POST /kairos/updates/install` : en-tête `fetch` exigé (garde-fou CSRF,
  comme `/kairos/notify`) **et** client de boucle locale (remplacer
  l'exécutable ne se déclenche jamais depuis un autre appareil) ; 202 +
  état, 409 + `{"error"}` si `UpdateError`. Le port transmis est celui du
  serveur (`request.scope["server"]`).

### Interface

- `templates/_update_banner.html`, inclus en tête de `.page` dans
  `base.html` : `.banner.mj-update` (icône `download`), rendu côté serveur
  quand une version est disponible et non masquée (`hidden` sinon), état
  complet en `data-state` (JSON). Sans JS : texte, « Notes de version »
  (page de la release), « Plus tard » (formulaire). « Mettre à jour » est
  caché par défaut et révélé par le script (installation impossible sans JS).
  Installation depuis les sources : la commande `git pull && pip install -e .`
  (`SOURCE_COMMAND`) s'affiche en `code`.
- Script de `base.html` : rend le texte selon la phase (« Téléchargement de
  Kairos X… NN % », « Vérification de la somme de contrôle… », « Installation
  de Kairos X… », « Redémarrage de Kairos X… la page se rechargera toute
  seule. », Android « téléchargé et vérifié : confirmez l'installation dans la
  fenêtre Android », « Échec de la mise à jour : … » avec `.banner.warning`
  et « Réessayer ») ; suit l'installation (`/kairos/updates/status` toutes
  les 0.7 s) ; après `restarting`, interroge le serveur chaque seconde et
  recharge la page dès qu'il répond avec la nouvelle version (abandon au bout
  de 90 s : « relancez l'application ») ; relance une lecture d'état toutes
  les 5 s (6 fois au plus) tant qu'une vérification est en cours.
- **Notification** (une par version, `claim_notification`) : pont Android
  `notifyUpdate` ; sinon notification du navigateur **si déjà autorisée**
  (jamais de demande de permission pour une mise à jour), dont le clic
  (`onclick`) lance la mise à jour ; sinon `POST /kairos/notify` (notification
  émise par Kairos, non cliquable). Le bandeau s'affiche dans tous les cas.
- Styles : `.mj-update*` dans `static/style.css` (bandeau neutre, seul le
  bouton « Mettre à jour » en accent ; `[hidden]` réaffirmé car `.banner` et
  `.btn` posent un `display`), `.mj-update-info` pour le bloc des réglages.

### Android (détail dans `docs/ANDROID_PACKAGING.md`)

- `KairosNotificationBridge` : canal `kairos-updates` distinct des alertes
  chrono ; `notifyUpdate(title, body)` (intention avec l'extra
  `com.skohscripts.kairos.ACTION=install_update`, `requestCode` distinct) ;
  `takePendingAction()` ; `installUpdate()` → `"missing"`, `"permission"`
  (ouvre `ACTION_MANAGE_UNKNOWN_APP_SOURCES`, API 26+) ou `"started"`
  (`ACTION_VIEW` sur l'URI du fournisseur, lecture accordée) ; échec d'ouverture
  signalé à la page (`kairos-update-install-failed`).
- `MainActivity` : extra lu au lancement à froid (action mise en attente, lue
  par la page au chargement) et dans `onNewIntent` (évènement
  `kairos-update-install` envoyé à la page).
- `UpdateApkProvider` : fournisseur non exporté, lecture seule, un seul
  fichier (`<filesDir>/kairos-data/updates/kairos-update.apk`) ; remplace
  `FileProvider` (AndroidX exclu).
- Manifeste : `REQUEST_INSTALL_PACKAGES`, fournisseur
  `${applicationId}.updates`. Android vérifie en plus que l'APK est signé par
  la même clé que l'application installée.

### CI

- `.github/workflows/release.yml` : `write_build_info.py` (tag ou
  `0.0.0-dev`, `github.server_url`, `github.repository`) avant PyInstaller et
  avant Gradle ; artefacts fusionnés (`merge-multiple`) puis `sha256sum
  kairos-* > SHA256SUMS` publié avec la release.
- `.gitlab-ci.yml` : `test` (push, MR, tag) ; sur tag `vX.Y.Z` ou lancement
  manuel (`web`, construit sans publier) : `build-linux`
  (`python:3.11-bookworm`), `build-windows` (runner tagué `windows`, seulement
  si `KAIROS_WINDOWS_RUNNER=true`, PowerShell), `build-android`
  (`python:3.13-bookworm` + JDK 17 + SDK installé et mis en cache, signature
  par les variables `KAIROS_KEYSTORE_*`/`KAIROS_KEY_*`, obligatoire sur tag) ;
  sur tag seulement, `release` : `packaging/publish_gitlab_release.py`.
- `packaging/publish_gitlab_release.py` (bibliothèque standard seule,
  `CI_JOB_TOKEN`) : `SHA256SUMS` sur `kairos-*`, dépôt de chaque fichier dans
  le registre de paquets génériques (`packages/generic/kairos/<version sans
  v>/<fichier>`), puis création de la release du tag avec un lien `package`
  par fichier (description : message du tag, sinon « Kairos vX.Y.Z »).
- `packaging/smoke_test.py` lance l'exécutable avec `KAIROS_UPDATE_CHECK=0`.

### Décisions et pièges tracés

- **Source des mises à jour propre, distincte de l'import GitLab**
  (décision utilisateur) : l'outil sera publié sur un GitLab d'entreprise
  privé qui n'est pas celui renseigné pour importer les issues.
- **Source par défaut injectée au build, pas codée en dur** : l'exécutable
  suit la forge qui l'a construit ; un clone suit son remote. Seul un jeton
  (dépôt privé) reste à saisir, jamais embarqué dans un binaire.
- **`SHA256SUMS` obligatoire** : sans lui, pas d'installation automatique
  (lien vers la release à la place). Même format et mêmes noms de fichiers
  sur les deux forges.
- **Clic requis** : une mise à jour n'est jamais installée dans le dos de
  l'utilisateur (hors périmètre explicite).
- **Redirections suivies à la main** pour ne jamais transmettre le jeton à un
  hôte tiers.
- **Renommer plutôt qu'écraser l'exécutable** (Windows) et copie préalable à
  côté de l'exécutable (renommage atomique sur le même système de fichiers).
- **Même port après redémarrage** : la page ouverte se recharge seule ; une
  seconde fenêtre serait de trop.
- **Pas de `FileProvider`** (AndroidX exclu) : fournisseur minimal dédié.
- **Pas de `release-cli`** dans la CI GitLab (en cours de dépréciation) : un
  script Python de la bibliothèque standard, testable hors CI.
- **Pipeline GitLab non exécuté ici** : écrit d'après la documentation
  GitLab et le pendant GitHub, validé seulement en syntaxe YAML ; le script
  de publication est testé contre un faux serveur. À valider sur l'instance
  cible au premier tag.

### Invariants et garde-fous

- Aucun fichier installé sans correspondre à `SHA256SUMS`.
- Le jeton ne part que vers la forge configurée et n'est jamais réaffiché.
- Aucune vérification ou erreur de mise à jour n'empêche l'application de
  fonctionner ni ne bloque le rendu d'une page.
- Installation seulement sur requête `fetch` de la machine locale.
