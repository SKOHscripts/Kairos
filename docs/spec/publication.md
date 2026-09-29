# Publication : F-Droid, IzzyOnDroid, fiches, page de téléchargement

_Rôle : comment Kairos 3 est présenté et proposé aux utilisateurs hors de
GitHub. Fichiers couverts : `fastlane/metadata/android/` (fiches FR/EN),
`kmp/desktopApp/.../StoreScreenshots.kt` et l'option `--store-screenshots` de
`Main.kt`, `kmp/tools/validate_fastlane.py`, `kmp/tools/fdroid_check.sh`,
`kmp/tools/make_app_icons.py` (icônes des fiches et de la page),
`fdroid/com.skohscripts.kairos.yml`, `fdroid/README.md`, `site/`,
`.github/workflows/fdroid-check.yml`, `.github/workflows/pages.yml`, et la
propriété `kairos.preview` de `kmp/androidApp/build.gradle.kts`. Versions,
construction et releases : `distribution.md`._

État : **jalon M7** (`3.0.0`) ; mis en place au jalon M6 (`3.0.0-beta.1`). Les soumissions elles-mêmes (merge
request fdroiddata, demande IzzyOnDroid) sont faites par le propriétaire du
dépôt après la release `v3.0.0` (`fdroid/README.md`).

## 1. Besoin métier (cahier des charges)

### Objectif / problème

Kairos 2 n'était installable que depuis les releases GitHub : il fallait
connaître le dépôt, choisir le bon fichier, et revenir vérifier les nouvelles
versions. Kairos 3 doit se trouver et se mettre à jour là où les utilisateurs
d'Android libre cherchent leurs applications (**F-Droid**, **IzzyOnDroid**), et
disposer d'une **page de téléchargement** claire pour toutes les plateformes.

### Comportement attendu (utilisateur)

- Dans F-Droid et IzzyOnDroid, une fiche en **français et en anglais** : nom,
  résumé d'une ligne, description, icône, cinq captures d'un téléphone
  (Jour, Semaine, Statistiques, Notes, Réglages), notes de version.
- L'APK distribué par F-Droid est **le même** que celui des releases GitHub
  (même signature) : on passe de l'un à l'autre, ou on migre depuis Kairos 2,
  sans désinstaller ni perdre ses données.
- Une page web (`https://skohscripts.github.io/Kairos/`, et `/en/`) présente
  Kairos en trois points, propose le bon fichier par système (celui du
  visiteur mis en avant), montre les captures, et explique la première
  installation (Gatekeeper, SmartScreen, Android, reprise de Kairos 2).
- Tant que Kairos 3 n'est pas sorti, la page le dit et renvoie vers la
  dernière préversion ; ensuite, vers la dernière version stable.

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
  téléchargement ne sont jamais cassés par la release d'une 2.x.
- Les captures ne dépendent ni du jour ni de la machine qui les produit.

### Hors périmètre

- Google Play (plan § 2.2 : pas de compte, pas de dépendance Google).
- Captures de tablette ou de bureau dans les fiches ; la page montre celles du
  téléphone.
- Préversions dans F-Droid ou IzzyOnDroid : elles portent l'identifiant
  `.preview` et restent sur GitHub.
- Badges F-Droid et IzzyOnDroid sur la page : au jalon M7, une fois
  l'application référencée (d'ici là, « Bientôt sur F-Droid et
  IzzyOnDroid »).

## 2. Solution technique

### Fiches Fastlane (`fastlane/metadata/android/`)

- Deux langues, `fr-FR` et `en-US`, lues par F-Droid et IzzyOnDroid au commit
  du tag : `title.txt` (« Kairos »), `short_description.txt` (≤ 80),
  `full_description.txt` (≤ 4000, HTML simple `<b>`, `<ul>`, `<li>`),
  `changelogs/<versionCode>.txt` (≤ 500), `images/icon.png` (512 px,
  `make_app_icons.py`), `images/phoneScreenshots/1.png` à `5.png`.
- La description ne cite aucune fonctionnalité retirée (TimeTree, GitLab…) et
  présente la formule du score, le placement dans la journée, le chrono et
  ses alertes, les statistiques, et l'absence de compte et de réseau.
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
  large), pour Jour, Semaine, Statistiques, Notes et Réglages, en `fr-FR`
  (`Locale.FRANCE`) puis `en-US` (`Locale.US`).
- Données : une base en mémoire remplie d'un jeu réaliste dans la langue de la
  capture (fuite mémoire P0 au chrono en marche, démo, revue, stand-up épinglé
  à 9 h 30, tâche bloquée par la fuite, rapport fait ; six semaines
  d'historique avec échéances ; notes ; créneaux deep work, déjeuner, réunion ;
  aucune tâche « À traiter », qui masquerait « Maintenant » en haut de
  l'écran). Horloge figée au mardi 6 octobre 2026, 10 h 12, fuseau UTC, tirages
  à graine fixe : deux exécutions donnent les mêmes images. Ce jeu n'est pas
  livré dans l'application (ce ne sont pas les exemples).
- `KairosApp` accepte pour cela une destination initiale
  (`initialDestination`, par défaut l'écran d'accueil habituel).
- Les captures ont servi de revue d'interface en largeur téléphone et ont
  corrigé trois défauts (voir `statistiques.md`, `vue-semaine.md`,
  `notes-capture.md`) : tuiles de statistiques à largeur fixe (une par ligne),
  titre de la semaine écrasé entre ses boutons, aide de capture parlant de la
  touche Entrée sur Android.

### Recette F-Droid (`fdroid/com.skohscripts.kairos.yml`)

- Catégories `Task`, `Time Tracker`, `Calendar & Agenda` (liste officielle,
  `config/categories.yml` de fdroiddata), licence MIT, liens site, source,
  tickets, releases ; `AutoName: Kairos`.
- Construction : `subdir: kmp/androidApp`, `gradle: [yes]` (variante release),
  `commit` = **hash complet** du commit du tag `vX.Y.Z` (exigence des
  relecteurs de fdroiddata : jamais un tag ni une branche, qui peuvent bouger ;
  `f5784cb…` pour la 3.0.0). Rien à supprimer (`scandelete`) : le scanner de F-Droid
  retire lui-même les `gradle-wrapper.jar`, et le dépôt ne contient aucun
  binaire à exclure ; une entrée inutile y est une erreur.
- **APK signé par nous** : `Binaries` (asset `Kairos-android.apk` de la release
  `v%v`) et `AllowedAPKSigningKeys` (SHA-256 du certificat de Kairos 2 et 3,
  `3885399c…2111`). F-Droid reconstruit l'APK, le compare à celui de GitHub
  signature ôtée, et publie le nôtre s'ils sont identiques.
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
  2. `fdroid lint` de la recette publiée ;
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

- `site/index.html` (français) et `site/en/index.html` (anglais), versionnés,
  en HTML et CSS seuls (`style.css`) ; `os.js` (facultatif) ajoute `current`
  à la carte du système du visiteur, seul bloc teinté (`primary-container`).
  Charte « miel » : rôles MD3 en variables, Roboto servie par la page
  (`fonts/`), cartes sans ombre, boutons en pilule, cibles de 40 px.
- Contenu : accroche, trois points (ordre expliqué par la formule, journée
  réaliste, temps réel), cartes de téléchargement (Android : APK ; Windows :
  MSI et zip portable ; Linux : `.deb` et `.tar.gz` ; macOS : DMG Apple
  Silicon et Intel ; navigateur), lien vers les releases et `SHA256SUMS`,
  captures (défilement horizontal), notes de première installation, pied de
  page (licence, source, tickets).
- Marqueurs remplacés par `pages.yml` : `@@DL@@` (base des téléchargements),
  `@@WEB@@` (`app/` ou `preview/`), `@@NOTICE_FR@@` et `@@NOTICE_EN@@`
  (bandeau). Un marqueur restant fait échouer la construction.
- `pages.yml` assemble le site dans `$RUNNER_TEMP/site` : `site/`, la
  préversion web sous `preview/`, les polices de
  `kmp/ui/src/commonMain/composeResources/font/` sous `fonts/`, les captures
  des fiches Fastlane sous `screens/fr/` et `screens/en/` (une seule source),
  la version web stable sous `app/`.
- Cible des liens :
  - une release stable `v3.*` existe : `releases/download/<son tag>`, pas de
    bandeau, « Utiliser Kairos en ligne » vers `app/` ;
  - sinon, la dernière préversion `v3.*` : `releases/download/<son tag>`,
    bandeau « Préversion X » (installée à côté de Kairos 2 sous le nom
    « Kairos Preview »), lien web vers `preview/`.
- Déclenchement : push sur `main` touchant `kmp/**`, `site/**`, `fastlane/**`
  ou `pages.yml` ; release publiée à la main ; **fin du workflow « Kairos 3
  release »** ; lancement manuel.

### Décisions et pièges tracés

- **F-Droid distribue notre APK** (`Binaries` + `AllowedAPKSigningKeys`)
  plutôt que de signer avec sa propre clé : avec la clé F-Droid, un
  utilisateur de Kairos 2 (APK GitHub) ou des releases GitHub ne pourrait pas
  passer à la version F-Droid sans désinstaller, donc sans perdre ses données.
  Contrepartie : la construction doit être reproductible, ce que la CI vérifie
  à chaque release finale.
- **Jamais `releases/latest/download`** : « latest » désigne la dernière
  release stable du dépôt, une 2.x tant que la 3.0.0 n'est pas sortie (autres
  noms de fichiers). La page vise un tag v3 nommé.
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
- **Environnement de développement** : Maven Central y répond 429 et le
  Python de l'image F-Droid y refuse le certificat du proxy ; la
  vérification locale y a tourné avec la distribution Gradle et le cache de
  dépendances de l'hôte montés dans le conteneur (`CACHEDIR`,
  `GRADLE_RO_DEP_CACHE`). Rien de cela n'est nécessaire sur GitHub.
