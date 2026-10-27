# Publication : F-Droid, fiches, page de téléchargement

_Rôle : comment Kairos 3 est présenté et proposé aux utilisateurs hors de
GitHub. Fichiers couverts : `fastlane/metadata/android/` (fiches FR/EN),
`kmp/desktopApp/.../StoreScreenshots.kt` et l'option `--store-screenshots` de
`Main.kt`, `kmp/tools/validate_fastlane.py`, `kmp/tools/fdroid_check.sh`,
`kmp/tools/make_app_icons.py` (icônes des fiches et de la page), `kmp/tools/make_badges.py` et `validate_badges.py`
(boutons de téléchargement, `site/badges/`),
`fdroid/com.skohscripts.kairos.yml`, `fdroid/README.md`, `site/`,
`.github/workflows/fdroid-check.yml`, `.github/workflows/pages.yml`, et la
propriété `kairos.preview` de `kmp/androidApp/build.gradle.kts`. Versions,
construction et releases : `distribution.md`._

État : **jalon M7** (`3.0.0`) ; mis en place au jalon M6 (`3.0.0-beta.1`). La soumission elle-même (merge
request fdroiddata) est faite par le propriétaire du dépôt après la release
`v3.0.0` (`fdroid/README.md`) : acceptée, en test chez F-Droid au
2026-09-30. IzzyOnDroid a refusé l'application (§ Décisions).

## 1. Besoin métier (cahier des charges)

### Objectif / problème

Une application installable seulement depuis les releases GitHub oblige à
connaître le dépôt, à choisir le bon fichier, et à revenir vérifier les nouvelles
versions. Kairos doit se trouver et se mettre à jour là où les utilisateurs
d'Android libre cherchent leurs applications (**F-Droid**), et
disposer d'une **page de téléchargement** claire pour toutes les plateformes.

### Comportement attendu (utilisateur)

- Dans F-Droid, une fiche en **français et en anglais** : nom,
  résumé d'une ligne, description, icône, neuf captures d'un téléphone
  (Jour, Semaine, Statistiques, Notes, Réglages, puis, gestion d'équipe
  activée : Suivi, Équipe avec la charge, Prévisions avec un résultat, enfin
  la vue Jour en thème sombre, `apparence.md`),
  notes de version. La description présente aussi la gestion d'équipe,
  facultative, échanges par fichier compris.
- L'APK distribué par F-Droid est **le même** que celui des releases GitHub
  (même signature) : on passe de l'un à l'autre sans désinstaller ni perdre
  ses données.
- Une page web (`https://skohscripts.github.io/Kairos/`, et `/en/`) présente
  Kairos en trois points, propose le bon fichier par système (celui du
  visiteur mis en avant), montre les captures, et explique la première
  installation (Gatekeeper, SmartScreen, Android).
- Le dépôt GitHub se présente en **anglais** (`README.md`, affiché par défaut) et en
  français (`README.fr.md`), avec un lien de langue en tête de chacun : un
  visiteur venu de F-Droid ou de l'étranger arrive sur de l'anglais.
- Tant que Kairos 3 n'est pas sorti, la page le dit et renvoie vers la
  dernière préversion ; ensuite, vers la dernière version stable.

### Boutons de téléchargement (3.12.0)

- **Un jeu de boutons** (badges SVG aux couleurs du thème « miel », primaire plein, texte blanc, forme de pilule) aux deux
  endroits où l'utilisateur vient télécharger : **la page GitHub Pages** (les six langues, sous l'accroche) et **le README**
  (`README.md` en anglais, `README.fr.md` en français : en tête, à la place du lien « Download », et dans « Install »).
- Sept boutons, dans cet ordre : **F-Droid** (« Get it on F-Droid », le paquet est publié), **Obtainium** (ajoute Kairos à
  Obtainium, qui suit les releases GitHub), **APK** (le fichier `Kairos-android.apk` de la dernière version), **Windows**
  (`.msi`), **Linux** (`.deb`), **macOS** (`.dmg` Apple Silicon ; l'Intel reste dans le tableau d'installation et sur la page) et
  **Navigateur** (la version web).
- Chaque bouton est écrit **dans la langue de la page** (anglais, français, espagnol, allemand, russe, italien) et porte un texte
  alternatif ; il fonctionne sans JavaScript.
- « Bientôt sur F-Droid » disparaît partout où il figurait dans un point d'entrée de téléchargement (README, six pages).
- Pas de bouton dans l'application : elle reste hors ligne et ne propose pas de téléchargement (« Voir la dernière version », 3.9.0,
  mène à la page).

**Critères de succès** : les sept boutons sont présents et pointent vers la bonne cible sur la page (six langues) et dans les deux
README ; aucune mention « bientôt sur F-Droid » ne reste ; le test de CI `pages` échoue si un marqueur reste ; la page garde 390 px
sans débordement (les boutons passent à la ligne) ; les images se lisent sur fond clair et sombre de GitHub.

**Hors périmètre** : badge « Latest release » (déjà en tête du README), logos de marque des systèmes (Windows, Apple, Linux) et des
magasins (badges officiels : choix du propriétaire de dessiner les nôtres), bouton dans l'application.

### Critères de succès

- Une fiche incomplète ou trop longue, ou une version sans notes de version,
  fait échouer la CI **avant** le tag.
- La recette F-Droid passe `fdroid lint` et se construit dans l'image des
  serveurs de F-Droid à chaque changement de `kmp/`.
- À chaque changement, la CI prouve que l'APK reconstruit par F-Droid est
  identique à celui que construit la release ; après la release d'une version
  finale, qu'il est identique à l'APK publié (condition pour que F-Droid le
  distribue signé par nous).
- La page s'affiche sans débordement sur un téléphone (390 px), n'appelle
  aucun service tiers (polices servies par la page), et ses liens de
  téléchargement visent toujours un tag nommé, jamais « latest ».
- Les captures ne dépendent ni du jour ni de la machine qui les produit.

### Hors périmètre

- Google Play (plan § 2.2 : pas de compte, pas de dépendance Google).
- Captures de tablette ou de bureau dans les fiches ; la page montre celles du
  téléphone.
- Préversions dans F-Droid : elles portent l'identifiant `.preview` et
  restent sur GitHub.
- IzzyOnDroid : demande d'inclusion refusée (§ Décisions).
- Badge F-Droid sur la page et le README : une fois l'application publiée
  par F-Droid (d'ici là, « Bientôt sur F-Droid »).

## 2. Solution technique

### Fiches Fastlane (`fastlane/metadata/android/`)

- Six langues (3.11.0, `i18n.md`) : `fr-FR`, `en-US`, `es-ES`, `de-DE`, `ru-RU` et `it-IT`, lues par F-Droid au commit
  du tag. Les images (icône, captures) ne sont que dans `fr-FR` et `en-US` : F-Droid replie les autres langues sur `en-US`
  (`validate_fastlane.py` ne les exige que pour ces deux dossiers). Dans chaque dossier : `title.txt` (« Kairos »), `short_description.txt` (≤ 80),
  `full_description.txt` (≤ 4000, HTML simple `<b>`, `<ul>`, `<li>`),
  `changelogs/<versionCode>.txt` (≤ 500), `images/icon.png` (512 px,
  `make_app_icons.py`), `images/phoneScreenshots/1.png` à `8.png`.
- La description présente la formule du score, le placement dans la journée, le chrono et
  ses alertes, les statistiques, et l'absence de compte et de réseau (jusqu'à la 3.x : la 4.0.0 devra dire les connexions en ligne facultatives,
  désactivées par défaut, et la permission réseau, `connexions-en-ligne.md`) ; depuis la 3.10.0, le fichier lié et les
  calendriers (fichier `.ics`, ou calendriers du téléphone avec l'autorisation **facultative** de lecture du
  calendrier, `connexions-externes.md`).
- **Une note de version par versionCode publié** (`29501` pour
  `3.0.0-beta.1`, `30000` pour `3.0.0`) : à chaque changement de version, en ajouter une dans les
  deux langues.
- `validate_fastlane.py` (sans dépendance) vérifie pour chaque langue : fichiers
  présents, non vides, longueurs ; note de version du `kairos.versionCode`
  courant ; icône PNG 512 × 512 ; au moins deux captures PNG numérotées 1, 2…
  sans trou, de 320 à 3840 px de côté. Code de sortie 1 au premier écart.

### Captures (`StoreScreenshots`)

- `./gradlew :desktopApp:run --args=--store-screenshots=<dépôt>/fastlane/metadata/android`
  rend hors écran (`ImageComposeScene`, comme l'auto-test) l'interface
  **Android** (`KairosApp(Platform.ANDROID, destination)` : barre basse,
  textes tactiles) en 1080 × 2400 px, densité 2,625 (téléphone de 411 dp de
  large), pour Jour, Semaine, Statistiques, Notes et Réglages (1 à 5), puis,
  sur une base où la gestion d'équipe est activée, Suivi, Équipe (charge) et
  Prévisions (6 à 8, `equipe.md`), puis la vue Jour de la capture 1, système en sombre (9, `apparence.md` § Thème
  sombre ; 1 à 8 imposent un système clair, `systemDark = false`), en `fr-FR` (`Locale.FRANCE`) puis `en-US`
  (`Locale.US`). Les captures 1 à 5 restent celles du mode solo : le jeu de
  l'équipe vit dans une seconde base, pour que les écrans personnels
  n'en montrent rien.
- Données : une base en mémoire remplie d'un jeu réaliste dans la langue de la
  capture (fuite mémoire P0 au chrono en marche, démo, revue, stand-up épinglé
  à 9 h 30, tâche bloquée par la fuite, rapport fait ; six semaines
  d'historique avec échéances ; notes ; créneaux deep work, déjeuner, réunion ;
  aucune tâche « À traiter », qui masquerait « Maintenant » en haut de
  l'écran). Horloge figée au mardi 6 octobre 2026, 10 h 12, fuseau UTC, tirages
  à graine fixe : deux exécutions donnent les mêmes images. Jeu d'équipe
  (captures 6 à 8) : trois membres dont « moi », une absence, une dizaine de
  tâches d'équipe à divers états, un historique de tâches faites estimées et
  chronométrées sur plusieurs semaines, dates relatives à la même horloge ;
  la simulation de la capture 8 est calculée avant le rendu, à graine fixe.
  Ce jeu de vitrine (`StoreTeam.kt`) n'est pas celui de l'auto-test
  (`TeamSeed`, qui suit le jour réel). « Moi » (Claire) porte le
  dépassement de charge : l'espace Équipe la montre en premier, donc dans
  les 2400 px. Le dépôt tire des `teamUid` aléatoires dont la simulation
  dérive ses tirages : le jeu les remplace par des identités fixes
  (`replaceAll`), sans quoi la capture 8 changeait d'une exécution à
  l'autre malgré la graine fixe. `KairosApp` accepte une destination
  d'équipe initiale (`initialTeamDestination`, par défaut le Suivi) ; la
  capture 8 est défilée jusqu'au résultat. Les captures 6 à 8 attendent une
  image stable (calculs de charge asynchrones) ; 1 à 5 sont rendues comme
  avant, à l'octet près. Ce jeu n'est pas
  livré dans l'application (ce ne sont pas les exemples).
- `KairosApp` accepte pour cela une destination initiale
  (`initialDestination`, par défaut l'écran d'accueil habituel).
- Les captures ont servi de revue d'interface en largeur téléphone et ont
  corrigé trois défauts (voir `statistiques.md`, `vue-semaine.md`,
  `notes-capture.md`) : tuiles de statistiques à largeur fixe (une par ligne),
  titre de la semaine écrasé entre ses boutons, aide de capture parlant de la
  touche Entrée sur Android.

### Recette F-Droid (`fdroid/com.skohscripts.kairos.yml`)

- Catégories `Calendar & Agenda`, `Task`, `Time Tracker` (liste officielle,
  `config/categories.yml` de fdroiddata), licence MIT, liens site, source,
  tickets, releases ; `AutoName: Kairos`.
- Construction : `subdir: kmp/androidApp`, `gradle: [yes]` (variante release),
  `commit` = **hash complet** du commit du tag `vX.Y.Z` (exigence des
  relecteurs de fdroiddata : jamais un tag ni une branche, qui peuvent bouger ;
  `f5784cb…` pour la 3.0.0). Rien à supprimer (`scandelete`) : le scanner de F-Droid
  retire lui-même les `gradle-wrapper.jar`, et le dépôt ne contient aucun
  binaire à exclure ; une entrée inutile y est une erreur.
- **APK signé par nous** : `Binaries` (asset `Kairos-android.apk` de la release
  `v%v`) et `AllowedAPKSigningKeys` (SHA-256 du certificat de signature, inchangé,
  `3885399c…2111`). F-Droid reconstruit l'APK, le compare à celui de GitHub
  signature ôtée, et publie le nôtre s'ils sont identiques.
- **Anti-fonctionnalités** : aucune déclarée jusqu'à la 3.x (APK sans permission réseau). **Rouvert le 2026-10-26 par
  `connexions-en-ligne.md`** : la 4.0.0 ajoute `INTERNET` (pas d'étiquette en soi) et déclare `AntiFeatures: NonFreeNet` dans la version qui
  embarque un connecteur dédié à Microsoft 365 ou à Google Agenda (service non libre, étiquette par application et non par fonction) ; jamais
  d'anti-fonctionnalité non déclarée. À implémenter dans cette recette, avec `fdroid lint` et `rewritemeta` (§ Vérification continue).
- Mises à jour : `UpdateCheckMode: Tags ^v[0-9]+\.[0-9]+\.[0-9]+$` (tags finals
  seulement), `UpdateCheckData` lisant `kairos.versionCode` et
  `kairos.versionName` dans `kmp/gradle.properties`, `AutoUpdateMode: Version`.
- `kairos.preview` (propriété Gradle, `androidApp/build.gradle.kts`) : force
  l'identifiant définitif (`false`) ou préversion (`true`) ; par défaut, une
  version avec suffixe est une préversion. Seule la vérification continue s'en
  sert, pour construire une bêta sous l'identifiant de la recette.

### Vérification continue (`fdroid_check.sh`, `fdroid-check.yml`)

- `fdroid_check.sh [--binaries]` tourne **dans l'image des serveurs de
  F-Droid** (`registry.gitlab.com/fdroid/fdroidserver:buildserver`), le dépôt
  monté en lecture (`REPO`, par défaut `/repo`) :
  1. clone `fdroidserver` (`FDROIDSERVER_REF`, par défaut `master`) et le
     dossier `config/` de fdroiddata (clone partiel : catégories et leurs
     icônes, que `fdroid lint` exige) ;
  2. `fdroid lint` de la recette publiée, puis `fdroid rewritemeta` : la
     recette doit déjà être sous sa forme canonique (le fichier réécrit est
     comparé à l'original), comme l'exige la CI de fdroiddata ;
  3. copie de la recette : dernière construction réécrite à la version de
     `gradle.properties` et au commit courant, `gradleprops:
     [kairos.preview=false]`, `Repo` = copie locale du dépôt (branche
     `fdroid-check` au commit testé), `Binaries` retiré sauf avec
     `--binaries` ;
  4. `git init` du dossier fdroiddata (`fdroid build` date la construction,
     `SOURCE_DATE_EPOCH`, d'après son historique) puis `fdroid build`.
     `fdroid build` sortant avec 0 même en échec, le script juge sur l'APK
     `unsigned/com.skohscripts.kairos_<versionCode>.apk`, copié dans `OUT`.
- `fdroid-check.yml` : sur PR et push sur `main` touchant `fdroid/**`, `fastlane/**`,
  `kmp/**` : job `metadata` (`validate_fastlane.py`) et job `build`
  (`fdroid_check.sh` dans l'image, APK non signé en artefact). Après une
  exécution réussie de « Kairos 3 release » déclenchée par un tag **final**
  (sans `-`) : job `build` avec `--binaries`, qui compare au
  `Kairos-android.apk` publié. Une préversion n'est pas comparée à son APK
  publié (identifiant `.preview`).
- Job `reproducible` (PR et `main`) : le runner construit l'APK comme
  `kmp-release.yml` (Temurin 21, `assembleRelease`), non signé, avec
  `-Pkairos.preview=false`, et le compare octet pour octet (`cmp`) à celui du
  job `build` ; en cas d'écart, il liste les entrées du zip qui diffèrent
  (nom, CRC) et échoue. La reproductibilité est ainsi vérifiée à chaque
  changement, bien avant la release finale.
- Constat en M6 (`3.0.0-beta.1`) : l'APK de l'image F-Droid et celui construit
  hors image (autre JDK, autre chemin du dépôt) sont identiques octet pour
  octet (SHA-256 `33beb19e…545a`). Aucun réglage de reproductibilité
  supplémentaire n'a été nécessaire au-delà de ceux de `distribution.md`
  (`dependenciesInfo` désactivé, versions figées).

### Page de téléchargement (`site/`, `pages.yml`)

- `site/index.html` (français), `site/en/`, `site/es/`, `site/de/`, `site/ru/` et `site/it/` (`index.html` chacun, 3.11.0),
  versionnés ; chaque page relie les cinq autres (`<nav>`, `hreflang`) et affiche les captures de `screens/en/`,
  en HTML et CSS seuls (`style.css`) ; `os.js` (facultatif) ajoute `current`
  à la carte du système du visiteur, seul bloc teinté (`primary-container`).
  Charte « miel » : rôles MD3 en variables, Roboto servie par la page
  (`fonts/`), cartes sans ombre, boutons en pilule, cibles de 40 px.
- Contenu : accroche, trois points (ordre expliqué par la formule, journée
  réaliste, temps réel), cartes de téléchargement (Android : APK ; Windows :
  MSI et zip portable ; Linux : `.deb` et `.tar.gz` ; macOS : DMG Apple
  Silicon et Intel ; navigateur), lien vers les releases et `SHA256SUMS`,
  captures 1 à 5 et 9, le thème sombre (défilement horizontal), notes de première installation, pied de
  page (licence, source, tickets).
- Marqueurs remplacés par `pages.yml` : `@@DL@@` (base des téléchargements),
  `@@WEB@@` (`app/` ou `preview/`), `@@NOTICE_FR@@`, `@@NOTICE_EN@@`, `@@NOTICE_ES@@`, `@@NOTICE_DE@@`, `@@NOTICE_RU@@` et
  `@@NOTICE_IT@@` (bandeau de préversion, texte par langue dans le script Python de `pages.yml`). Un marqueur restant fait échouer la construction.
- `pages.yml` assemble le site dans `$RUNNER_TEMP/site` : `site/`, la
  préversion web sous `preview/`, les polices de
  `kmp/ui/src/commonMain/composeResources/font/` sous `fonts/`, les captures
  des fiches Fastlane sous `screens/fr/` et `screens/en/` (une seule source),
  la version web stable sous `app/`.
- Cible des liens :
  - une release stable `v3.*` existe : `releases/download/<son tag>`, pas de
    bandeau, « Utiliser Kairos en ligne » vers `app/` ;
  - sinon, la dernière préversion `v3.*` : `releases/download/<son tag>`,
    bandeau « Préversion X » (installée à côté de la version stable sous le nom
    « Kairos Preview »), lien web vers `preview/`.
- Déclenchement : push sur `main` touchant `kmp/**`, `site/**`, `fastlane/**`
  ou `pages.yml` ; release publiée à la main ; **fin du workflow « Kairos 3
  release »** ; lancement manuel.

### Boutons de téléchargement (`make_badges.py`, `site/badges/`)

- **Source** : `kmp/tools/make_badges.py` génère `site/badges/<langue>/<id>.svg` (`id` : `fdroid`, `obtainium`, `apk`, `windows`,
  `linux`, `macos`, `web` ; `langue` : `en`, `fr`, `es`, `de`, `ru`, `it`), 42 fichiers **versionnés** (un seul jeu : la page les sert, le
  README les référence en chemin relatif `site/badges/<langue>/<id>.svg`, que GitHub affiche).
- **Dessin** : 56 px de haut, largeur selon le texte, `rx` = 28 (pilule), fond `#7F5610` (primaire du thème clair, lisible sur fond clair et
  sombre), texte `#FFFFFF` ; à gauche une icône **Material Symbols** (Outlined 400, Apache 2.0, comme `make_icons.py`) générique par
  bouton (`download`, `update`, `android`, `desktop_windows`, `terminal`, `laptop_mac`, `language`), jamais un logo de marque ; deux lignes
  de texte (petite : « Get it on », « Download for »… ; grande : « F-Droid », « Windows »…). **Le texte est converti en tracés** avec les polices
  Roboto Regular et Medium du dépôt (`composeResources/font/`) : le rendu ne dépend d'aucune police du visiteur et n'embarque rien. Un
  `<title>` porte le texte alternatif.
- **Textes** (petite ligne / grande ligne) : tableau `TEXTS` du script, les six langues ; les noms propres (F-Droid, Obtainium, GitHub,
  Windows, Linux, macOS) ne se traduisent pas.
- **Régénération** : le script (`fontTools` pour les tracés, le paquet `@material-symbols/svg-400` comme `make_icons.py`) est lancé à la
  main quand un texte, une icône ou la couleur du thème change ; la CI ne le relance pas. Un test de CI (`kmp/tools/validate_badges.py`,
  job `metadata` de `fdroid-check.yml`, qui se déclenche aussi sur `site/**` et les README) vérifie que les 42 fichiers existent, que chaque page porte une rangée des sept boutons dans sa langue et chaque README deux (en tête, puis dans « Install »), qu'aucun badge référencé n'est absent, et qu'aucun « bientôt sur F-Droid » ne reste.
- **Cibles** : F-Droid `https://f-droid.org/packages/com.skohscripts.kairos/` ; Obtainium `obtainium://add/https://github.com/SKOHscripts/Kairos`
  sur la page, `https://apps.obtainium.imranr.dev/redirect?r=obtainium://add/https://github.com/SKOHscripts/Kairos` dans le README (GitHub retire
  les liens aux schémas inconnus) ; APK, Windows, Linux, macOS : `@@DL@@/<fichier>` sur la page (release stable, ou dernière préversion), `…/releases/latest/download/<fichier>`
  dans le README ; navigateur : `@@WEB@@` sur la page, `https://skohscripts.github.io/Kairos/app/` dans le README.
- **Page** : une rangée `<p class="badges">` sous l'accroche de chaque `index.html` ; `style.css` : `.badges` en flex qui passe à la ligne, `img`
  de 56 px, anneau de focus visible ; textes alternatifs dans la langue de la page. Les cartes de téléchargement par système restent.
- **Décisions** : badges dessinés par nous plutôt que les badges officiels (licences à vérifier, pas d'équivalent pour le bureau, un style
  unique et la langue de la page) ; pas de logos de marque (marques déposées) ; tracés plutôt que texte SVG (rendu stable dans une balise `img`) ;
  Obtainium par le redirecteur du projet dans le README seulement, parce que GitHub filtre les schémas personnalisés.

### Décisions et pièges tracés

- **README bilingue** (demande du 2026-10-12, ouverture à l'international via
  F-Droid) : `README.md` en anglais, `README.fr.md` en français, mêmes sections
  dans le même ordre ; toute feature se décrit dans les deux (`CLAUDE.md`, étape 4).
  Les libellés d'interface cités reprennent ceux de `values/strings.xml`
  (« To process », « Why this position? », « This is me »…). Écarté : garder le
  français par défaut avec un `README.en.md` (le visiteur étranger arrive d'abord
  sur du français). Restent en français, signalé dans le README : `docs/`
  (spécifications, charte), `CLAUDE.md` et le dossier F-Droid `fdroid/README.md`.
- **Pas d'IzzyOnDroid** : la demande d'inclusion envoyée pour la 3.0.0 a été
  refusée le 2026-09-30, en raison de la part d'IA dans le développement de
  Kairos. F-Droid et les releases GitHub restent les deux canaux Android ;
  IzzyOnDroid n'est plus cité côté utilisateur (README, page de
  téléchargement, notes de version, dossier `fdroid/README.md`).
- **Recette sous forme canonique** : `fdroid rewritemeta` trie les catégories,
  replie les lignes longues (`UpdateCheckData` passe à la ligne) et exige une
  fin de ligne finale ; la CI de fdroiddata échoue sinon. Constaté à la
  soumission de la 3.0.0 (fichier collé dans l'éditeur web de GitLab, sans
  fin de ligne) ; la vérification continue le contrôle depuis.
- **F-Droid distribue notre APK** (`Binaries` + `AllowedAPKSigningKeys`)
  plutôt que de signer avec sa propre clé : avec la clé F-Droid, un
  utilisateur des releases GitHub ne pourrait pas passer à la version F-Droid
  sans désinstaller, donc sans perdre ses données.
  Contrepartie : la construction doit être reproductible, ce que la CI vérifie
  à chaque release finale.
- **Aucune anti-fonctionnalité jusqu'à la 3.x ; rouvert pour la 4.0.0** (2026-10-26, `connexions-en-ligne.md`) : ce qui était vrai parce que
  l'APK n'avait pas `INTERNET` ne l'est plus automatiquement ; la fiche, la description et la page de téléchargement cessent de dire « aucune
  permission réseau » à la 4.0.0, et la recette déclare ce que le code embarque.
- **Jamais `releases/latest/download`** : « latest » désigne la dernière
  release stable du dépôt, qui n'existe pas tant qu'aucune version stable n'est
  sortie (préversions seules). La page vise un tag v3 nommé.
- **`workflow_run` plutôt que `release`** : une release créée par un workflow
  avec le jeton par défaut ne déclenche aucun autre workflow. Pages et la
  comparaison F-Droid suivent donc la fin de « Kairos 3 release ».
- **Captures tirées de l'interface Android réelle**, rendue sur la JVM : pas
  d'émulateur en CI, résultat identique d'une machine à l'autre, et les mêmes
  images servent aux magasins et à la page.
- **Page en HTML statique versionné**, sans générateur de site : rien à
  installer, relue comme du code, et conforme à la règle « aucune police
  distante ».
- **Catégories de fdroiddata** : `fdroid lint` ne connaît sans elles que
  d'anciennes catégories ; la vérification clone donc `config/` de fdroiddata
  (clone partiel, le dépôt entier pèse plusieurs gigaoctets).
- **Environnement de développement** : Maven Central y répond 429 et l'outillage
  de l'image F-Droid y refuse le certificat du proxy ; la
  vérification locale y a tourné avec la distribution Gradle et le cache de
  dépendances de l'hôte montés dans le conteneur (`CACHEDIR`,
  `GRADLE_RO_DEP_CACHE`). Rien de cela n'est nécessaire sur GitHub.
