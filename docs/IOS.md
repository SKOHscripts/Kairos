# Portage iOS et publication sur l'App Store — étude de faisabilité

Ce document évalue la création d'une application **iOS** de Kairos et sa
publication sur l'App Store. Il est le pendant de
[`ANDROID_PACKAGING.md`](ANDROID_PACKAGING.md) (comment l'APK est construit) et
de [`FDROID.md`](FDROID.md) (comment publier l'APK) : ici, *faisabilité +
modifications + publication*, réunies parce que sur iOS les deux sujets sont
indissociables (on ne « construit » pas pour iOS sans se poser d'emblée la
question de la distribution, verrouillée par Apple).

## Verdict

**Techniquement faisable, avec un point à vérifier empiriquement en tout
premier ; la vraie difficulté n'est pas technique mais structurelle
(distribution).**

- **Côté technique**, iOS reproduit *exactement* le pattern déjà en place sur
  Android : un interpréteur CPython embarqué lance uvicorn dans un thread, et
  une WebView native (WKWebView) affiche `http://127.0.0.1:<port>/kairos`. Seul
  l'outil d'empaquetage change (BeeWare **Briefcase** au lieu de Chaquopy). Le
  cœur de l'app (Starlette, SQLAlchemy synchrone, Jinja2, SQLite) ne demande
  aucune réécriture.
- **Le seul vrai risque technique** est la dépendance native `cryptography`
  (cœur en Rust, sans wheel iOS maintenue). Mais chez Kairos elle n'entre dans
  le graphe que via `keyring` (backend Linux `SecretStorage`, conditionné
  `sys_platform == "linux"`) — or iOS rapporte `sys.platform == "ios"`, donc
  cette chaîne ne devrait même pas s'installer. **À confirmer empiriquement
  avant tout le reste** (§ 1), avec la méthodologie déjà employée côté Android.
- **La rupture réelle est la distribution.** iOS n'a **aucun équivalent grand
  public au sideload de l'APK** : pas de « télécharge le fichier depuis les
  releases GitHub, installe, c'est fini ». Publier sur iOS impose l'App Store
  (donc le **Programme Développeur Apple à 99 $/an**, la signature par
  certificat/profil, et une **revue Apple** à chaque version). C'est un
  changement de philosophie assumé par rapport au modèle actuel « je l'ai
  construit, voici le fichier, sans compte ni intermédiaire ».

Les exécutables desktop (PyInstaller) et l'APK Android **ne sont pas
concernés** ; ce document décrit une **troisième cible** à ajouter.

---

## 1. À trancher en premier : la vérification empirique des dépendances natives

Ne rien construire avant d'avoir répondu à cette question, exactement comme la
doc Android a fait sa vérification datée (« vérifié le 2026-07-12 ») avant de
s'engager. Sur une machine macOS + Xcode, résoudre les dépendances du projet
pour la cible iOS (via `cibuildwheel --platform ios`, un `briefcase create iOS`
à blanc, ou une résolution pip ciblée `sys_platform == "ios"`) et **constater
concrètement** ce que pip tente d'installer :

- **`cryptography` et `cffi` sont-ils tirés ?** Ils ne devraient PAS l'être :
  ils n'arrivent que par la chaîne `keyring → SecretStorage/jeepney`, marquée
  `sys_platform == "linux"` dans les métadonnées de `keyring`. Sur Android
  cette chaîne s'installe (le noyau est Linux, `sys.platform == "linux"`) mais
  ne sert à rien au runtime (repli fichier de `app/secret_store.py`). Sur iOS,
  `sys.platform == "ios"` (PEP 730) ne satisfait pas ce marqueur : la chaîne ne
  devrait jamais être résolue. Si c'est confirmé, **le principal blocage
  disparaît de lui-même** pour Kairos (rien d'autre dans l'arbre n'a besoin de
  `cryptography` : la pile `httpx` — `httpcore`, `certifi`, `idna`, `anyio` —
  est pure Python, comme `python-multipart`, `markdown`, `platformdirs`).
- **`greenlet` est-il tiré ?** SQLAlchemy 2 l'épingle pour
  `platform_machine == "aarch64"`. Or les outils Apple rapportent l'architecture
  iOS comme `"arm64"`, pas `"aarch64"` — le marqueur pourrait ne jamais se
  déclencher sur iOS. Et de toute façon Kairos utilise SQLAlchemy en
  **synchrone** (`create_engine`, `Session` — jamais `create_async_engine` /
  `AsyncSession`, vérifié dans `app/tasks_db.py`), donc `greenlet` n'est pas
  nécessaire au runtime. À confirmer que pip ne le réclame pas quand même à
  l'installation.
- **`markupsafe` (via jinja2)** n'a pas de wheel iOS mais possède un **repli
  pur-Python** documenté si son extension C n'est pas disponible : au pire il se
  dégrade, il ne bloque pas.

Deux issues :
- **Résolution propre (attendue)** : aucun `cryptography`/`greenlet` réclamé →
  l'arbre iOS est essentiellement pur-Python → le portage est débloqué, on passe
  au § 3.
- **Résolution qui réclame `cryptography`** (peu probable mais à écarter) :
  il faudrait alors soit retirer complètement `keyring` de la cible iOS (le
  repli fichier de `secret_store.py` fonctionne déjà sans lui), soit tenter une
  compilation iOS du cœur Rust (voir § 2.3, chantier lourd et incertain). À ne
  pas engager sans y être forcé par le constat.

---

## 2. Faisabilité technique

### 2.1 Python sur iOS (officiel, mais « embedded-only »)

Depuis [PEP 730](https://peps.python.org/pep-0730/), iOS
(`arm64-apple-ios`, `arm64-apple-ios-simulator`) est une plateforme CPython
**officiellement supportée**, au **palier 3** (« Tier 3 ») depuis CPython 3.13
(oct. 2024), toujours au palier 3 en 2026 (doit compiler et passer les tests,
mais sans garantie de CI et sans bloquer une release). Détails :
[Using Python on iOS](https://docs.python.org/3/using/ios.html).

Contrainte structurante : Python sur iOS est **embarqué uniquement** — pas de
console/REPL possible, pas de `subprocess`/`os.fork`/`multiprocessing` (désactivés
comme sur WASI). On écrit une app iOS native qui embarque `libPython` et
l'initialise (`PyConfig` pointant `PYTHONHOME`/`PYTHONPATH` sur le `python/lib`
embarqué). Rien de tout cela ne gêne Kairos (serveur mono-processus).

### 2.2 Chaîne d'outils : BeeWare Briefcase (l'équivalent iOS de Chaquopy)

L'interpréteur se récupère via
[`Python-Apple-support`](https://github.com/beeware/Python-Apple-support)
(BeeWare), qui package `Python.xcframework` (tranches device-arm64 +
simulateur). Par-dessus,
[**Briefcase**](https://briefcase.beeware.org/en/stable/reference/platforms/iOS/xcode/)
génère un **vrai projet Xcode** (via un template cookiecutter), embarque
`Python.xcframework` + les wheels des dépendances, et instancie une classe de
votre app comme `PythonAppDelegate`.

Le pattern « serveur local + WebView » est **explicitement recommandé** par la
doc Toga/BeeWare : sur iOS une WebView **ne peut pas** charger d'URL `file://`
(sécurité), donc la marche à suivre documentée est de **lancer un petit serveur
HTTP sur `127.0.0.1` dans un thread** et d'y pointer la WebView — exactement ce
que Kairos fait déjà sur Android. Seule règle dure : la création/chargement de
la WKWebView doit se faire sur le **thread principal** ; le serveur tourne, lui,
sur un thread secondaire. Réf. :
[Toga iOS](https://toga.beeware.org/en/stable/reference/platforms/iOS.html),
[discussion Briefcase #1539](https://github.com/beeware/briefcase/discussions/1539).

Alternatives écartées (cohérent avec la doc officielle) : Pythonista / Pyto /
a-Shell sont des lanceurs de scripts personnels, ils ne packagent pas *votre*
app pour l'App Store ; `kivy-ios` embarque la UI Kivy dont Kairos n'a pas besoin
(il rend déjà du HTML/CSS). **Briefcase est le seul chemin qui colle** au
couple « CPython embarqué + WebView native », miroir de Chaquopy.

### 2.3 Le problème des wheels natives (le vrai crux)

Règle App Store qui commande tout : **chaque extension binaire doit être un
`.framework` signé dans `Frameworks/`** (pas de `.so` en vrac sur `sys.path`).
C'est pourquoi « un simple `pip install` d'une wheel » ne suffit pas dès qu'il y
a du C/Rust — chaque extension doit devenir un framework à la construction.

État des quatre dépendances natives potentielles de Kairos (juillet 2026) :

| Paquet | Wheel iOS ? | Statut pour Kairos |
|---|---|---|
| **cffi** | **Oui**, depuis la 2.1.0 (« arm64 iOS wheels ») | Résolu en amont. |
| **markupsafe** (jinja2) | Pas de wheel iOS officielle | Possède un **repli pur-Python** : se dégrade, ne bloque pas. |
| **greenlet** (sqlalchemy) | Pas de wheel iOS | Épinglé par SQLAlchemy pour `platform_machine == "aarch64"` — marqueur qui **peut ne pas correspondre** à l'`"arm64"` d'iOS ; et **inutile au runtime** (SQLAlchemy synchrone). À confirmer (§ 1). |
| **cryptography** | **Non**, aucune wheel iOS ni chemin de build maintenu (cœur Rust/pyo3 ; `mobile-forge` bloqué sur la 3.4.8 pré-Rust ; [issue upstream](https://github.com/pyca/cryptography/issues/11463) sans réponse ; PyO3 ne cible pas iOS officiellement) | **Effectivement indisponible** aujourd'hui — mais **ne devrait pas être tiré** chez Kairos (§ 1), car uniquement via `keyring`/`SecretStorage` marqué `sys_platform == "linux"`. |

Pour tenter une compilation depuis la source (si forcé), l'outil moderne est
[`cibuildwheel`](https://cibuildwheel.pypa.io/en/stable/) (support iOS de
première classe depuis la 3.0, `--platform ios`), qui a justement servi à
produire les wheels iOS de `cffi`. `mobile-forge` (BeeWare) est plus ancien et
semi-retiré depuis août 2025 au profit de cibuildwheel, mais son index de
wheels reste un repli.

### 2.4 uvicorn nu (comme sur Android)

`pyproject.toml` épingle `uvicorn[standard]`, dont l'extra tire `uvloop`
(Cython, Unix) et `httptools` (C) — sans wheel iOS, et sans intérêt pour un
serveur local mono-utilisateur. **Correctif identique à Android** : une liste de
dépendances iOS dédiée avec **`uvicorn` nu**, sur le modèle de
[`packaging/android-requirements.txt`](../packaging/android-requirements.txt).

### 2.5 ATS / WKWebView / `127.0.0.1`

Par défaut, l'App Transport Security d'iOS bloque le HTTP en clair. Le loopback
demande une exception `Info.plist` : clé `NSAppTransportSecurity` →
[`NSAllowsLocalNetworking: true`](https://developer.apple.com/documentation/bundleresources/information-property-list/nsapptransportsecurity/nsallowslocalnetworking)
(exception sanctionnée pour le réseau local), ou un `NSExceptionDomains` sur
`localhost`. C'est l'équivalent iOS du `network_security_config.xml` (cleartext
vers `127.0.0.1`) déjà livré sur Android. **Gotcha documenté** : certaines
versions d'iOS ont bloqué `http://127.0.0.1` même avec l'exception alors que
`http://localhost` passait — **tester les deux formes** sur les versions cibles.

### 2.6 Exécution en arrière-plan (limite v1 identique à Android)

iOS suspend l'app peu après le passage en arrière-plan : un processus suspendu
n'exécute **aucun code**, donc le serveur local se met en pause jusqu'au retour
au premier plan (aucun mode d'arrière-plan approuvé — audio, VoIP, localisation…
— ne convient à « garder un serveur HTTP vivant »). C'est **exactement la limite
déjà assumée sur Android** (`ANDROID_PACKAGING.md` : « pas de foreground service,
un chrono en cours ne survit pas à une mise en veille agressive » ; SQLite
committe à chaque requête, donc pas de perte de données). Assumer la même limite
en v1 iOS est cohérent avec la posture existante, pas une régression.

---

## 3. Modifications à apporter au code

Bonne nouvelle : le cœur applicatif (routes, ordonnancement WSJF, modèles,
rendu) **ne change pas**. Le portage est de la plomberie de démarrage +
empaquetage, calquée sur l'existant Android.

### À créer

- **`app/ios_launcher.py`** — miroir de
  [`app/android_launcher.py`](../app/android_launcher.py). Avant tout import de
  `app.main` : poser `KAIROS_DATA_DIR` (dossier privé Documents de l'app),
  `KAIROS_BASE_DIR` (bundle contenant `app/`, `templates/`, `static/`),
  `KAIROS_PLATFORM=ios`, `HOME` ; choisir un port libre (même logique 8001-8020
  que le launcher desktop/Android) ; puis, dans un thread, importer `uvicorn` +
  `app.main` et lancer `uvicorn.run(app, host="127.0.0.1", port=…, reload=False)`.
- **Coquille native (via le template Briefcase)** — équivalent de
  `MainActivity.java` + `kairos_boot.py` : initialiser l'interpréteur Python,
  appeler `prepare(documents_dir)` → obtenir le port, lancer `serve(port)` dans
  un thread, **sonder `/favicon.ico`** (même repère de disponibilité que le
  launcher desktop et l'activité Android), puis charger
  `http://127.0.0.1:<port>/kairos` dans la WKWebView **sur le thread principal**.
- **`packaging/ios-requirements.txt`** — liste dédiée avec **`uvicorn` nu** (pas
  `[standard]`), sur le modèle de `android-requirements.txt`.
- **Étape de staging du bundle** — reproduire le mécanisme `stageKairosPython` /
  `kairos_dist` d'Android (copier `app/`, `templates/`, `static/`, `README.md`,
  `SPEC_KAIROS.md` en gardant l'arborescence, hors `__pycache__`) dans le bundle
  iOS, et pointer `KAIROS_BASE_DIR` dessus, pour que Jinja2, StaticFiles et le
  rendu du README lisent de vrais chemins. Briefcase gère le staging des wheels ;
  le staging des assets du dépôt est à décrire dans sa config.
- **`Info.plist`** : exception ATS loopback (§ 2.5), icône, launch screen,
  `NSPrivacyManifest` si un framework l'exige (ex. OpenSSL en transitif).

### Ce qui ne change PAS (déjà prêt)

- **`KAIROS_BASE_DIR`** est déjà un point d'entrée d'override dans
  `app/main.py` (`_resolve_base_dir`) — aucun changement de code pour que le
  bundle iOS impose son chemin.
- **`keyring`** : `app/secret_store.py` a déjà un **repli fichier** propre quand
  aucun trousseau n'est présent (`keyring_available() == False`) — comportement
  déjà exercé sur Android. Sur iOS, même repli, sans erreur. (Améliration future
  optionnelle : un pont natif vers le Keychain iOS, non requis en v1.)
- **Bouton « Quitter »** : conditionné par `is_frozen` (PyInstaller), **faux**
  hors desktop → déjà masqué dans le bundle, comme sur Android. Aucun changement.
- **SQLAlchemy synchrone** : rien à async-ifier ; pas de `greenlet` au runtime.

### Récapitulatif blocages / non-blocages

| Élément | Statut iOS |
|---|---|
| Framework (Starlette, pur Python) | ✅ aucun blocage |
| Serveur (uvicorn nu) | ✅ comme Android |
| Base (SQLAlchemy synchrone, SQLite) | ✅ pas de greenlet requis |
| `keyring` (cryptography/cffi) | ✅ dégradé (repli fichier) — à condition que la chaîne ne s'installe pas (§ 1) |
| `markupsafe` (jinja2) | ✅ repli pur-Python |
| `cryptography` compilé pour iOS | ⛔ indisponible — **à contourner**, pas à compiler (§ 1) |
| Chaîne de démarrage + WebView | ✅ pattern Android réutilisable |
| Bundle d'assets (`KAIROS_BASE_DIR`) | ✅ override déjà en place |
| Arrière-plan | ⚠️ limite v1 identique à Android |

---

## 4. Construction & CI (macOS + Xcode obligatoires)

Il n'existe **aucun chemin de cross-compile** vers un `.ipa` signé sans macOS +
Xcode. Les runners **`macos-*` de GitHub Actions** conviennent (`macos-26` GA en
février 2026, Xcode récent préinstallé), sur le même principe que le job Android
existant de [`.github/workflows/release.yml`](../.github/workflows/release.yml).
Forme de la chaîne :

1. `briefcase create iOS`, `briefcase build iOS` (génère/compile le projet
   Xcode, embarque `Python.xcframework` + wheels).
2. `xcodebuild … -sdk iphoneos -configuration Release archive` puis export `.ipa`
   (ou `briefcase package iOS`, qui l'enveloppe).
3. **Signature** : importer un certificat `.p12` + un profil de provisioning
   dans un trousseau CI temporaire
   ([recette GitHub documentée](https://docs.github.com/en/actions/use-cases-and-examples/deploying/installing-an-apple-certificate-on-macos-runners-for-xcode-development)) —
   nouvelle plomberie par rapport au keystore Android, même catégorie d'effort.
4. **Notarisation / envoi** : `altool` est **déprécié depuis nov. 2023**, utiliser
   `xcrun notarytool submit`
   ([TN3147](https://developer.apple.com/documentation/technotes/tn3147-migrating-to-the-latest-notarization-tool)).

---

## 5. Publication sur l'App Store

### 5.1 Programme Développeur Apple (99 $/an) et signature

Adhésion **99 $/an** ; l'inscription **Individuelle** (Apple ID + 2FA, pas de
D-U-N-S, vérification 24-48 h) est le bon choix pour un projet perso MIT
(l'inscription Organisation exige un numéro D-U-N-S). Le programme payant est
**obligatoire** pour publier sur l'App Store, pour TestFlight, ou même pour
installer durablement sur un appareil physique (la signature « Personal Team »
gratuite de Xcode expire en ~7 jours et ne se distribue pas). Modèle de
signature : **certificat + profil de provisioning** émis par Apple, vérifiés à
l'installation et au lancement — fondamentalement différent de l'APK
auto-signé d'Android (où chaque canal, même ad-hoc, est verrouillé par une
crédential Apple).

### 5.2 Conformité aux règles de revue

- **2.5.2 (app auto-contenue, pas de code téléchargé/exécuté)** : **conforme**.
  La règle vise le code *distant* qui change le comportement après validation.
  Kairos embarque CPython + ses propres `.py` + uvicorn *dans le bundle*, passe
  la revue tel quel, et n'exécute que ce code embarqué contre `127.0.0.1` — aucun
  téléchargement de code. Précédents : **Pythonista** (interpréteur Python 3
  complet, sur l'App Store) et les apps packagées par **Briefcase**. Conseil pour
  la soumission : décrire dans les notes de revue « un backend local embarqué »,
  pas « un moteur de script extensible » (éviter d'inviter un examen renforcé).
- **4.2 (fonctionnalité minimale / rejet « wrapper web »)** : **survivable avec
  cadrage**. Le rejet type frappe les apps qui chargent un site *distant* en
  WebView sans rien hors-ligne (les testeurs vérifient en mode Avion). Kairos est
  le cas inverse : `127.0.0.1`, zéro dépendance réseau, **marche mieux
  hors-ligne** (pas d'origine distante du tout). Mitigations : expliquer
  l'architecture locale dans les **notes de revue** (champ privé d'App Store
  Connect), soigner l'icône/launch screen/gestes natifs, pas de barre d'URL
  visible, vérifier qu'aucune ressource (police, CDN) n'est chargée en externe,
  mettre en avant la valeur « gestionnaire de tâches 100 % local, sans compte ».
- **4.7 (mini-apps / code externe)** : **ne s'applique pas** (Kairos n'offre pas
  un catalogue de logiciels tiers téléchargeables ; le backend embarqué est un
  détail d'implémentation d'une seule app).
- **Étiquette de confidentialité** : Kairos ne collecte, ne stocke hors appareil
  et ne transmet **rien** → le questionnaire « App Privacy » se remplit
  entièrement en **« Data Not Collected »**, le cas le plus simple.

Sources : [Guidelines de revue Apple](https://developer.apple.com/app-store/review/guidelines/),
[Pythonista 3](https://apps.apple.com/us/app/pythonista-3/id1085978097),
[Briefcase iOS publishing](https://briefcase.beeware.org/en/stable/how-to/publishing/iOS/).

### 5.3 La rupture de distribution (le vrai coût)

**Aucun équivalent grand public au sideload de l'APK.** En 2026 :

- **TestFlight** : programme payant requis, revue bêta sur la première build, et
  **builds expirant à 90 jours** (les testeurs perdent l'app) → jamais un canal
  permanent, seulement une bêta glissante.
- **Ad-hoc / développement** : programme payant, **100 UDID max** enregistrés à
  la main → ne passe pas à l'échelle d'un public.
- **Marchés alternatifs UE (DMA)** : réels mais **limités à l'UE**, exigent quand
  même un compte Développeur payant + la **notarisation** Apple (revue allégée
  mais obligatoire) → inutile pour un public hors UE.

**Conclusion** : pour un public général, **l'App Store est de fait le seul canal
durable**. C'est une rupture assumée avec le modèle Kairos actuel (« clone le
dépôt / télécharge l'APK depuis les releases GitHub, sans compte ni gatekeeper »)
: 99 $/an récurrents, un compte Apple lié à une identité réelle, et une revue
discrétionnaire à chaque version. À décider en amont, car cela change le contrat
avec l'utilisateur, pas seulement la technique.

### 5.4 Pipeline de publication (étapes)

1. **Adhérer** au Programme Développeur (99 $/an, Individuel).
2. **App Store Connect** : créer la fiche d'app, réserver un **Bundle ID**
   (`com.<toi>.kairos`), prix gratuit, remplir « App Privacy » (« Data Not
   Collected »).
3. **Construire/signer** (Xcode ou `briefcase package iOS`), exception ATS,
   icône, launch screen, `Product → Archive`.
4. **Envoyer** (Xcode Organizer, app **Transporter**, ou `notarytool` en CI).
5. **TestFlight** (recommandé) pour valider sur appareil réel avant soumission
   publique.
6. **Soumettre** avec captures, description, et **notes de revue** décrivant
   l'architecture 100 % locale (§ 5.2).
7. **Revue** : budgéter **jusqu'à ~1 semaine** pour une première soumission (les
   nouveaux comptes vont plutôt vers le haut de la fourchette), plus une marge
   pour au moins un aller-retour de rejet/correction.
8. **Publier** ; les mises à jour reprennent les étapes 3-7 (re-revue à chaque
   fois).

### 5.5 Alternative PWA (et pourquoi elle ne colle pas ici)

Une PWA installable (« Ajouter à l'écran d'accueil ») éviterait l'App Store et
les 99 $/an, **mais elle suppose une origine HTTPS hébergée** que l'appareil
va chercher sur le réseau. Le modèle de Kairos est un **processus Python local
servant depuis `127.0.0.1`** : Safari/WebKit n'a aucune API pour lancer ou
joindre un processus natif embarqué. Faire une PWA imposerait donc soit un
backend cloud (contredit le « local, sans cloud »), soit un serveur auto-hébergé
sur une autre machine du réseau (UX « self-host » très différente de « installe
une app »). S'ajoutent les limites iOS des PWA (pas de vrai background sync,
stockage ~50 Mo évincible, cap de 7 jours sur le stockage écrit par script). La
PWA est une **autre direction produit** (réécrire vers un client web pur ou un
self-host), **pas** un moyen de garder l'architecture Python-locale actuelle
sans App Store.

---

## 6. Estimation d'effort et recommandation

Ordre de grandeur, en supposant la vérification du § 1 favorable (arbre
essentiellement pur-Python) :

1. **§ 1 — vérification empirique des dépendances** : ~1/2 journée sur un Mac.
   *C'est le préalable qui conditionne tout le reste — à faire avant de
   s'engager.*
2. **Coquille Briefcase + `ios_launcher.py` + staging + ATS** : le gros du
   travail, mais balisé par le miroir Android. Quelques jours pour un premier
   build qui démarre sur simulateur puis appareil.
3. **CI signature + notarisation** : plomberie GitHub Actions, même catégorie que
   le keystore Android.
4. **Décision de distribution (99 $/an, App Store)** : décision produit, pas
   technique, à prendre en amont (§ 5.3).

**Recommandation** : traiter le § 1 comme un spike bloquant, isolé, *avant* toute
autre chose. S'il confirme que `cryptography`/`greenlet` ne sont pas tirés sur
iOS, le portage technique est une extension raisonnable du travail Android déjà
fait, et le seul vrai arbitrage restant est structurel (accepter le modèle App
Store et son coût récurrent). S'il révèle que `cryptography` est réclamé,
retirer `keyring` de la cible iOS (repli fichier déjà en place) avant d'envisager
quoi que ce soit de plus lourd.

## Références

- [PEP 730 — Adding iOS as a supported platform](https://peps.python.org/pep-0730/)
- [Using Python on iOS (docs officielles)](https://docs.python.org/3/using/ios.html)
- [Python-Apple-support (BeeWare)](https://github.com/beeware/Python-Apple-support)
- [Briefcase — plateforme iOS/Xcode](https://briefcase.beeware.org/en/stable/reference/platforms/iOS/xcode/)
- [Briefcase — publier sur iOS](https://briefcase.beeware.org/en/stable/how-to/publishing/iOS/)
- [Toga — notes iOS](https://toga.beeware.org/en/stable/reference/platforms/iOS.html)
- [cibuildwheel (support iOS)](https://cibuildwheel.pypa.io/en/stable/)
- [cffi — wheels iOS (changelog)](https://cffi.readthedocs.io/en/latest/whatsnew.html)
- [cryptography — demande de wheel iOS (issue #11463)](https://github.com/pyca/cryptography/issues/11463)
- [NSAllowsLocalNetworking (ATS)](https://developer.apple.com/documentation/bundleresources/information-property-list/nsapptransportsecurity/nsallowslocalnetworking)
- [App Review Guidelines](https://developer.apple.com/app-store/review/guidelines/)
- [Apple Developer Program — adhésion](https://developer.apple.com/support/compare-memberships/)
- [Installer un certificat Apple sur un runner macOS (GitHub)](https://docs.github.com/en/actions/use-cases-and-examples/deploying/installing-an-apple-certificate-on-macos-runners-for-xcode-development)
- [TN3147 — migrer vers notarytool](https://developer.apple.com/documentation/technotes/tn3147-migrating-to-the-latest-notarization-tool)
- `docs/ANDROID_PACKAGING.md` (ce dépôt) — le modèle Android que ce portage reproduit.
