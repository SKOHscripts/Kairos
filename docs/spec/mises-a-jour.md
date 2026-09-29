# Mises à jour

_Rôle : prévenir l'utilisateur du bureau qu'une nouvelle version est
publiée. Fichiers couverts :
`kmp/core/src/commonMain/.../core/updates/UpdateCheck.kt` (réponse de l'API,
comparaison, cadence ; pur), `kmp/desktopApp/.../DesktopUpdates.kt` (appel
réseau, état), `kmp/ui/.../app/Updates.kt` (`UpdateService`,
`UpdateStatus`, `UpdateWatcher`, `UpdateBanner`), la carte Mises à jour de
`settings/SettingsScreen.kt`, le module `java.net.http` du runtime embarqué
(`desktopApp/build.gradle.kts`). Tests : `UpdateCheckTest`,
`DesktopUpdatesTest`, `M5ScreensUiTest.anAvailableVersionShowsABannerUntilLater`._

État : **jalon M5**, plan § 6.2. Reprend le besoin d'alerte de
`docs/spec/mises-a-jour.md` (Kairos 2), sans l'installation en un clic.

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
- Réglages → Mises à jour : le réglage (enregistré avec le reste du
  formulaire), la version installée, le résultat de la dernière vérification
  (« Pas encore vérifié. », « À jour (vérifié le 29/09 09h15). », « Kairos
  3.0.1 est disponible (…) », « Vérification impossible le … (hors
  ligne ?). ») et « Vérifier maintenant », qui marche même réglage coupé.
- Une vérification qui échoue ne gêne jamais l'application.
- Android : jamais (aucune permission réseau ; les mises à jour passent par
  le magasin ou l'APK). Web : sans objet (la page est toujours la dernière).

### Critères de succès

- Préversions, brouillons, tags qui ne sont pas des versions, liens hors
  `github.com`, réponses illisibles : ignorés ; une 2.x n'est jamais proposée
  à une 3.0.0-alpha (`UpdateCheckTest`).
- Une vérification, pas une autre avant 6 heures, même après relance ;
  « Vérifier maintenant » passe outre ; 404 = à jour, autre code ou exception
  = échec ; « Plus tard » survit à la relance (`DesktopUpdatesTest`).
- Bandeau affiché, puis masqué par « Plus tard » (`M5ScreensUiTest`).

### Hors périmètre / différé

- Téléchargement, vérification de somme et remplacement automatiques
  (Kairos 2) : **régression assumée** (plan § 6.2) ; l'installeur du système
  remplace l'application.
- Notification système d'une nouvelle version (Kairos 2 en émettait une) :
  le bandeau suffit.
- Source ou jeton configurables (GitLab d'entreprise) : Kairos 3 est publié
  sur GitHub seulement.

## 2. Solution technique

- `UpdateCheck` : `LATEST_URL` =
  `https://api.github.com/repos/SKOHscripts/Kairos/releases/latest` ;
  `parseLatest(corps)` lit `tag_name` (`AppVersion.parse`, préversion
  refusée), `html_url` (doit commencer par `https://github.com/`),
  `prerelease` et `draft` ; `isNewer` ; `due(dernière, maintenant)` :
  jamais vérifié, 6 heures passées, ou horloge revenue en arrière.
- `DesktopUpdates(fichier, horloge, version, fetch)` : `check(force)` sous
  verrou ; GET (`java.net.http`, 10 s, en-têtes `Accept:
  application/vnd.github+json` et `User-Agent: Kairos/<version>`, proxy de
  la JVM) ; 200 → disponible ou à jour, 404 → à jour, sinon → échec ; état
  écrit dans `updates.json` du dossier de données (`lastCheck`, `failed`,
  `latest`, `url`, `dismissed`) et relu au lancement. Pas créé pour
  l'auto-test (base en mémoire, aucun réseau).
- `UpdateWatcher` (coquille) : tant que le réglage est actif,
  `check(force = false)` toutes les 30 minutes (la cadence réelle est celle
  de `due`). `UpdateBanner` : au-dessus du contenu de chaque destination,
  `surfaceContainerHigh` et icône `Download`, ouverture par
  `LocalUriHandler`.

### Décisions et pièges tracés

- **Page de la version plutôt que la page de téléchargement** : la page
  GitHub Pages complète arrive au jalon M6 ; `html_url` est toujours valide.
  À revoir en M6 (plan : « Télécharger » ouvre la page Pages).
- **`java.net.http` ajouté au runtime réduit** (`modules(...)` de jpackage) :
  sans lui, l'application empaquetée échouerait à la première vérification.
- **Bannière neutre** (charte : pas de couleur pour une information), comme
  les bandeaux de dégradation de Kairos 2.
