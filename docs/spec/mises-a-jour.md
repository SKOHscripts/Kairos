# Mises à jour

_Rôle : prévenir l'utilisateur du bureau qu'une nouvelle version est
publiée. Fichiers couverts :
`kmp/core/src/commonMain/.../core/updates/UpdateCheck.kt` (réponse de l'API,
comparaison, cadence ; pur), `kmp/desktopApp/.../DesktopUpdates.kt` (appel
réseau, état), `kmp/ui/.../app/Updates.kt` (`UpdateService`,
`UpdateStatus`, `UpdateWatcher`, `UpdateBanner`), la carte Mises à jour de
`settings/SettingsScreen.kt`, le module `java.net.http` du runtime embarqué
(`desktopApp/build.gradle.kts`). Tests : `UpdateCheckTest`,
`DesktopUpdatesTest`, `M5ScreensUiTest.anAvailableVersionShowsABannerUntilLater`. Android (3.9.0, issue #143) :
`core/updates/InstallSource.kt` (pur), `androidApp/.../AndroidServices.kt` (installateur), carte « Mises à jour »
d'Android dans `settings/SettingsScreen.kt` ; tests `InstallSourceTest`, `DownloadPageUiTest`._

État : **jalon M5**, plan § 6.2. Alerte de nouvelle version, sans
installation en un clic. Android : lien vers la page de téléchargement, 3.9.0 (issue #143).

## 1. Besoin métier (cahier des charges)

### Objectif / problème

Rien ne signale d'elle-même une nouvelle version d'une application de
bureau téléchargée à la main : on reste sur une vieille version sans le
savoir.

### Comportement attendu (utilisateur)

- **Bureau seulement.** Au plus toutes les 6 heures, tant que le réglage
  « Vérifier les nouvelles versions » est actif (par défaut), Kairos demande
  à GitHub la dernière version **publiée** (préversions ignorées).
- Plus récente que la version installée : un bandeau neutre en haut de tous
  les écrans, « Kairos X.Y.Z est disponible (tu as A.B.C). », avec
  « Télécharger » (ouvre la page de la version dans le navigateur) et
  « Plus tard » (masque cette version ; la suivante réapparaît).
- Réglages → Général → Mises à jour : le réglage (enregistré avec le reste du
  formulaire), la version installée, le résultat de la dernière vérification
  (« Pas encore vérifié. », « À jour (vérifié le 29/09 09h15). », « Kairos
  3.0.1 est disponible (…) », « Vérification impossible le … (hors
  ligne ?). ») et « Vérifier maintenant », qui marche même réglage coupé.
- Une vérification qui échoue ne gêne jamais l'application.
- Android : **aucune vérification** jusqu'à la 3.x (aucune permission réseau ; les mises à jour passent par le magasin ou l'APK).
  **Rouvert le 2026-10-26 par `connexions-en-ligne.md`** (4.0.0, issue #144, à implémenter) : un APK installé à la main peut vérifier, par un
  interrupteur **désactivé par défaut** (état propre à Android, distinct de `updateCheckEnabled`), seulement application ouverte ; la
  vérification remplace alors « Voir la dernière version » ; installé par un magasin, rien ne change.
  Pour un APK **installé à la main** (3.9.0, issue #143), Réglages → Général montre une carte « Mises à jour » :
  « Version installée : 3.9.0 », l'aide « Kairos n'utilise pas Internet sur Android : la page de téléchargement
  dit quelle est la dernière version. » et « Voir la dernière version », qui ouvre la page de téléchargement dans le
  navigateur (`https://skohscripts.github.io/Kairos/`, `…/Kairos/en/` si l'interface est en anglais). Installé par
  un magasin (F-Droid et ses clients, Play, un gestionnaire de mises à jour…), la carte n'apparaît pas : le magasin
  s'occupe des mises à jour. La catégorie Général dit alors « Langue, apparence, mises à jour ».
- Web : sans objet (la page est toujours la dernière).

### Critères de succès

- Préversions, brouillons, tags qui ne sont pas des versions, liens hors
  `github.com`, réponses illisibles : ignorés ; une version plus ancienne n'est
  jamais proposée (`UpdateCheckTest`).
- Une vérification, pas une autre avant 6 heures, même après relance ;
  « Vérifier maintenant » passe outre ; 404 = à jour, autre code ou exception
  = échec ; « Plus tard » survit à la relance (`DesktopUpdatesTest`).
- Bandeau affiché, puis masqué par « Plus tard » (`M5ScreensUiTest`).
- Android : à la main = installateur absent ou installateur de paquets du système (`com.google.android.packageinstaller`,
  `com.android.packageinstaller`, `com.android.shell` pour `adb`) ; tout autre installateur = un magasin
  (`InstallSourceTest`). La carte montre la version et ouvre la page de la langue de l'interface ; sans lien
  (magasin), rien (`DownloadPageUiTest`). L'APK de release garde `POST_NOTIFICATIONS` et `RECEIVE_BOOT_COMPLETED` (et, depuis la 3.10.0, `READ_CALENDAR`
  facultative, `connexions-externes.md`)
  seulement (`aapt2 dump permissions`).

### Hors périmètre / différé

- Téléchargement, vérification de somme et remplacement automatiques
  (plan § 6.2) : l'installeur du système remplace l'application.
- Notification système d'une nouvelle version : le bandeau suffit.
- Source ou jeton configurables : Kairos est publié sur GitHub seulement.
- Vérification automatique sur Android : il faudrait la permission `INTERNET` (issue #144, 4.0.0, rouvre « sans
  permission réseau »). **Décidée le 2026-10-26** : voir `connexions-en-ligne.md` § Nouvelles versions sur Android (jamais application
  fermée, jamais de notification).

## 2. Solution technique

- `UpdateCheck` : `LATEST_URL` =
  `https://api.github.com/repos/SKOHscripts/Kairos/releases/latest` ;
  `parseLatest(corps)` lit `tag_name` (`AppVersion.parse`, préversion
  refusée), `html_url` (doit commencer par `https://github.com/`),
  `prerelease` et `draft` ; `isNewer` ; `due(dernière, maintenant)` :
  jamais vérifié, 6 heures passées, ou horloge revenue en arrière.
- `DesktopUpdates(fichier, horloge, version, fetch)` : `check(force)` sous
  verrou ; GET (`java.net.http`, 10 s, en-têtes `Accept:
  application/vnd.github+json` et `User-Agent: Kairos/<version>`, proxy du
  système : `java.net.useSystemProxies`, `bureau-avance.md`) ; 200 → disponible ou à jour, 404 → à jour, sinon → échec ; état
  écrit dans `updates.json` du dossier de données (`lastCheck`, `failed`,
  `latest`, `url`, `dismissed`) et relu au lancement. Pas créé pour
  l'auto-test (base en mémoire, aucun réseau).
- `UpdateWatcher` (coquille) : tant que le réglage est actif,
  `check(force = false)` toutes les 30 minutes (la cadence réelle est celle
  de `due`). `UpdateBanner` : au-dessus du contenu de chaque destination,
  `surfaceContainerHigh` et icône `Download`, ouverture par
  `LocalUriHandler`.

- Android (3.9.0) : `InstallSource.installedByHand(installateur)` (`core/updates`, pur) ; `UpdateCheck.downloadPage(langue)`
  rend l'adresse de la page dans la langue de l'interface (3.11.0, `fr` à la racine, les cinq autres dans leur dossier, repli `en/` ; `i18n.md`). `AndroidServices` lit l'installateur (`PackageManager.getInstallSourceInfo(...)
  .installingPackageName` dès l'API 30, `getInstallerPackageName` avant ; aucune permission) et, à la main seulement,
  pose `AppServices.downloadPage = true`. `SettingsScreen` : `DownloadPageCard` dans Général quand `updates` est nul
  et `downloadPage` vrai ; « Voir la dernière version » ouvre la page par `LocalUriHandler` (intention `ACTION_VIEW`,
  comme le lien de don) ; l'indice de la catégorie suit (`settings_cat_general_hint_updates`).

### Décisions et pièges tracés

- **Android : un lien, pas une vérification** (2026-10-24, issue #143, choix du propriétaire) : garder « sans
  permission réseau » (`connexions-externes.md`) ; la page de téléchargement (GitHub Pages) plutôt que la page des
  releases : elle choisit le bon fichier et dit la version. La vérification réseau, facultative et désactivée par
  défaut (étiquette F-Droid « Tracking » sinon), est l'issue #144 (4.0.0). **Rouvert le 2026-10-26** : « sans permission réseau »
  vaut jusqu'à la 3.x ; la 4.0.0 ajoute `INTERNET` et cette vérification, désactivée par défaut, remplaçant la page « Voir la dernière
  version » quand elle est active (`connexions-en-ligne.md`).
- **« À la main » défini par l'installateur du système** plutôt que par une liste de magasins : un magasin inconnu
  cache la carte (sans dommage), alors qu'une liste de magasins à tenir l'aurait montrée à tort dans F-Droid.

- **Page de la version plutôt que la page de téléchargement** : la page
  GitHub Pages complète arrive au jalon M6 ; `html_url` est toujours valide.
  À revoir en M6 (plan : « Télécharger » ouvre la page Pages).
- **`java.net.http` ajouté au runtime réduit** (`modules(...)` de jpackage) :
  sans lui, l'application empaquetée échouerait à la première vérification.
- **Bannière neutre** (charte : pas de couleur pour une information), comme
  les autres bandeaux d'information.
