# Écran de démarrage

_Rôle : la page de chargement jouée à chaque lancement, sur toutes les
plateformes : le cadran du logo dont l'aiguille fait un tour avant de se
poser sur le logo, une courte pause, puis un fondu vers l'application.
Fichiers couverts : `kmp/ui/.../app/StartupSplash.kt` (`StartupSplash`,
`SplashFrame`, `LocalStartupSplash`), son appel dans `KairosApp.kt`, `icons/KairosLogo.kt`
(`LogoColors`), `app/Welcome.kt` (attente de la fin du démarrage), les points
d'entrée (`androidApp/.../MainActivity.kt`, `desktopApp/.../Main.kt`,
`webApp/.../Main.kt`), les relais natifs (`androidApp/src/main/res/drawable/
kairos_splash_dial.xml`, `kairos_launch_background.xml`, `values*/themes.xml`,
`webApp/src/wasmJsMain/resources/index.html`), les captures
`desktop-startup-splash*.png` de l'auto-test (`SelfTest.kt`), et le rapport de plantage Android
(`kmp/ui/.../app/CrashReport.kt`, `androidApp/.../KairosCrash.kt`, `KairosApplication.kt`).
Tests : `StartupSplashUiTest`, `CrashReportUiTest`._

État : demande du 2026-10-10 (version suivant la `3.2.0`), implémentée. Rapport de plantage :
issue #65 (3.3.0), implémenté.
Rouvre explicitement la décision « l'interface Compose s'affiche dès la
première image : plus d'écran de démarrage applicatif » (`distribution.md`
§ Android, `plan-v3-kotlin.md`) : le démarrage était si rapide qu'on ne
voyait pas le logo.

## 1. Besoin métier (cahier des charges)

### Objectif / problème

Kairos démarre en moins d'une seconde : l'écran de démarrage du système
(Android) ou le message de chargement (web) ne fait que clignoter, et le
bureau n'en a pas. On ne voit jamais l'identité de l'application. On veut
une vraie page de chargement, la même partout, assez longue pour être vue,
qui mette en scène le logo : un cadran solaire dont l'aiguille avance.

### Comportement attendu (utilisateur)

- À chaque lancement (Android, bureau, web), l'application s'ouvre sur une
  page plein écran au fond de la charte (clair, ou sombre quand le système l'est, `apparence.md` § Thème sombre), le
  cadran du logo au centre,
  **vide** (sans secteur miel).
- L'aiguille part de midi, accélère, fait **un tour complet** du cadran en
  traînant derrière elle une ombre miel de la largeur du secteur du logo,
  puis ralentit et **se pose exactement sur le logo** (secteur de 80° depuis
  midi). Environ 2,2 s.
- **Pause légère** (0,4 s) sur le logo, puis **fondu** (0,5 s) vers
  l'application, déjà prête dessous. Environ 3 s en tout.
- **Toucher ou cliquer** n'importe où abrège : le logo se pose aussitôt et
  le fondu commence.
- Si les données ne sont pas encore prêtes à la fin de la pause (premier
  lancement lent, migration), le logo reste affiché avec « Ouverture de Kairos… »
  dessous, jusqu'à ce qu'elles le soient ; une erreur d'ouverture apparaît
  après le fondu, comme avant.
- L'accueil du premier lancement (`accueil.md`) n'apparaît qu'une fois le
  fondu terminé, jamais par-dessus la page de chargement.
- Tourner l'écran ou redimensionner la fenêtre ne rejoue pas l'animation.
- Le relais natif reste invisible : l'écran de démarrage du système Android
  et le message de chargement du web montrent le même cadran vide, à la même
  place et à la même taille ; l'animation enchaîne sans saut.

### Rapport de plantage (Android, issue #65)

Après la 3.2.0, un utilisateur (Redmi Note 12, Android 15) ne pouvait plus ouvrir
l'application : plantage immédiat, sans aucune information exploitable.

- Si Kairos s'arrête sur une erreur non rattrapée, le **lancement suivant** n'ouvre pas
  l'application mais un écran « Kairos s’est arrêté » : une phrase d'explication, le **rapport**
  (version, appareil, langue, trace) sélectionnable et défilant, « Partager le rapport » (feuille
  de partage du système) et « Ouvrir Kairos ».
- Le rapport **reste sur l'appareil** : aucun réseau, aucune permission ; il ne part que si
  l'utilisateur le partage.
- « Ouvrir Kairos » supprime le rapport et ouvre l'application normalement. Tant qu'on ne l'a pas
  touché, l'écran revient au lancement suivant.
- Les données ne sont pas ouvertes derrière cet écran : il s'affiche même si c'est la base qui
  fait planter l'application.

### Critères de succès

- L'écran du rapport montre le texte et ses deux actions
  (`CrashReportUiTest.theReportIsShownAndBothActionsAreReachable`).
- La page de chargement s'affiche au démarrage, puis laisse place à
  l'application après environ 3 s (`StartupSplashUiTest.theSplashPlaysThenFadesIntoTheApp`).
- Un toucher l'abrège (`StartupSplashUiTest.aTapSkipsTheSplash`).
- L'accueil du premier lancement attend la fin du fondu
  (`StartupSplashUiTest.theWelcomeWaitsForTheSplash`).
- Sans demande explicite, `KairosApp` n'en a pas (tests d'interface,
  auto-test, captures des magasins inchangés).
- Rendu vérifié à l'œil : captures `desktop-startup-splash*.png` de
  l'auto-test (aiguille à mi-course ; logo et « Ouverture de Kairos… »).

### Hors périmètre / différé

- Réglage pour désactiver ou raccourcir la page de chargement.
- Thème sombre de la page **choisi dans les Réglages** : la page suit le thème du système, lu avant la base
  (`apparence.md` § Thème sombre).
- Animation du relais natif lui-même (le système Android ne l'affiche que
  le temps de créer l'activité ; c'est la page Compose qui anime).

## 2. Solution technique

### Composant (`ui/app/StartupSplash.kt`)

- `StartupSplash(ready, onFinished)` : `Box` plein écran, fond
  `colorScheme.surface` du thème **océan par défaut**, clair ou sombre comme le système (il est composé dans le
  `KairosDefaultTheme` extérieur de `KairosApp`, pas dans celui des réglages : le
  fond égale `#FAF8FF`, ou `#121318` en sombre, celui des relais natifs (`kairos_bg`, `index.html`), quelle que soit la graine
  choisie ; changé le 2026-10-27 : l'océan remplace le miel, `#FFF8F4` et `#18120B` avant, `apparence.md` § Couleur par
  défaut). Le cadran, lui, garde les couleurs fixes du logo (miel). Le fondu révèle ensuite l'application dans la couleur de
  l'utilisateur.
- Cadran dessiné sur un `Canvas` de **184,32 dp** (viewport 40 du logo ×
  4,608) : disque `LogoColors.Dial`, anneau `LogoColors.Ring` (trait 1,6),
  ombre `LogoColors.Wedge` (`drawArc` avec centre, rayon 16), axe
  `LogoColors.Axis` (rayon 2,6) : la géométrie de `KairosLogo`.
- Progression `sweep` (`Animatable` 0 → 1, `tween(2200 ms,
  FastOutSlowInEasing)`) ; angle de l'aiguille θ = 440° × sweep (un tour +
  80°) ; ombre de max(0, θ − 80°) à θ depuis midi. À θ = 440°, l'ombre couvre
  [360°, 440°] ≡ [0°, 80°] : le logo exact.
- Puis `delay(400)` ; puis attente de `ready` (`snapshotFlow`) ; puis
  `alpha` 1 → 0 (`tween(500)`) ; puis `onFinished()`.
- « Ouverture de Kairos… » (`Res.string.loading`, déjà traduit) apparaît sous le
  cadran si `ready` est encore faux après la pause.
- Toucher : `clickable` sans indication (pas de couche d'état sur une page
  qui n'est pas un contrôle) ; il passe `skipped` à vrai, ce qui relance
  l'effet : `snapTo(1f)`, pas de pause, attente de `ready`, fondu. Le
  `clickable` consomme aussi les gestes : l'application dessous n'est pas
  atteignable pendant le démarrage.
- Accessibilité : `contentDescription` = « Kairos » (`app_name`), étiquette
  de test `startup-splash`.
- `LocalStartupSplash` (`compositionLocalOf { false }`) : vrai tant que la
  page est affichée.

### Branchement (`KairosApp`)

- Paramètre `splash: Boolean = false` : seuls les trois points d'entrée
  (`MainActivity`, `Main.kt` du bureau, `Main.kt` du web) passent `true`. Les
  tests d'interface, l'auto-test et les captures des magasins gardent la
  valeur par défaut et ne voient rien changer.
- `splashDone` en `rememberSaveable` (initialisé à `!splash`) : une rotation
  (activité recréée avec état) ne rejoue pas l'animation ; un nouveau
  lancement, si.
- La page est composée **par-dessus** le `when` existant (application,
  erreur, chargement) dans une `Box` : l'application se compose et lit ses
  données pendant l'animation, le fondu la révèle sans première image à
  attendre. `ready` = services ouverts **ou** échec d'ouverture.
- `WelcomeDialog` sort tôt tant que `LocalStartupSplash` est vrai : un
  `AlertDialog` est une fenêtre au-dessus de tout, y compris de la page.

### Relais natifs

- **Android** : `drawable/kairos_splash_dial.xml`, le cadran sans secteur
  dans une boîte de 288 dp réduite à 0,64 (masque circulaire de 192 dp de
  l'écran de démarrage système) ; 288 × 0,64 = 184,32 dp, la taille du
  `Canvas` Compose. API 31+ : `windowSplashScreenAnimatedIcon` = ce
  drawable, statique (plus d'icône animée ni de
  `windowSplashScreenAnimationDuration`). Avant l'API 31 :
  `kairos_launch_background` (fond + ce drawable centré). L'activité est bord
  à bord (`enableEdgeToEdge`) : le centre de la page Compose est celui de
  l'écran, comme pour le relais.
- **Web** : `#kairos-loading` dessine le même cadran vide en SVG en ligne,
  184,32 px, centré exactement (1 dp = 1 px CSS dans Compose web), avec
  « Chargement de Kairos… » sous lui ; `main()` le retire au démarrage,
  `StartupSplash` reprend au même endroit.
- **Bureau** : pas de relais, la fenêtre s'ouvre sur la page Compose.

### Auto-test

- `SplashFrame(progress, loading)` est l'image sans état de la page ;
  `SelfTest.kt` la rend à mi-course (`desktop-startup-splash.png`, 900 px)
  et posée sur le logo avec « Ouverture de Kairos… » (`desktop-startup-splash-loading.png`,
  360 px) pour vérifier le dessin à l'œil.

### Rapport de plantage (Android)

- `ui/app/CrashReport.kt` : `CrashReportScreen(report, onShare, onContinue)`. Sans dépendance aux
  réglages ni aux services (le thème par défaut, l'océan, `KairosTheme`) ; le rapport est une
  `SelectionContainer` en chasse fixe qui défile seule, les boutons restent en bas. Textes
  `crash_title`, `crash_body`, `crash_share`, `crash_continue` (FR + EN).
- `androidApp/.../KairosCrash.kt` : `install` pose un `Thread.setDefaultUncaughtExceptionHandler`
  qui écrit `files/crash/last.txt` (version, API, fabricant et modèle, locale, thread, trace
  `Log.getStackTraceString`) puis rend la main au gestionnaire d'origine (le processus s'arrête
  comme avant). `pending` lit le rapport, `clear` le supprime.
- `KairosApplication` (déclarée dans le manifeste, `android:name`) l'installe dans `onCreate` :
  l'activité **et** les récepteurs (`ChronoReceiver`, `BootReceiver`, lancés par le système à la
  mise à jour) sont couverts.
- `MainActivity.onCreate` : si un rapport existe, `setContent` de l'écran du rapport puis retour
  **avant** `KairosProcess.services` (aucune ouverture de base) ; `onResume` ne rouvre pas non plus
  les services. « Partager » : `Intent.ACTION_SEND` `text/plain` dans un sélecteur ; « Ouvrir
  Kairos » : `clear` puis `recreate()`.

### Décisions et pièges tracés

- **Rapport de plantage local** (issue #65) : le diff 3.1.0 → 3.2.0 ne touche que le cœur
  « Soutenir » et le démarrage a déjà un écran d'erreur pour un échec de `openServices`
  (`KairosApp`) ; le plantage vient donc d'ailleurs (composition, `onCreate`, récepteur) et sans
  trace la cause est inconnue. Plutôt que deviner, on donne à l'utilisateur de quoi la transmettre.
  Écartés : une bibliothèque de rapport (ACRA…, dépendance réseau et permission `INTERNET`,
  contraire à `distribution.md`) ; un envoi automatique.
- **Premier rapport gardé** : `write` ne remplace pas un rapport existant. Si l'écran du rapport
  plantait lui-même, la cause d'origine ne serait pas effacée.
- **Échec d'écriture muet** : le gestionnaire entoure l'écriture d'un `runCatching` ; il ne doit
  jamais masquer le plantage d'origine ni en provoquer un second.
- **Premier rapport reçu (3.3.0)** : un Fairphone FP4 (Android 16) a montré
  `SQLiteException: no such column: task.team_excluded` au lancement qui suit la
  mise à jour 3.2.0 → 3.3.0. Cause : la migration de la base n'était pas lancée
  sur Android (`modele-donnees.md` § Décisions, « Version lue sur le schéma »),
  corrigée avant la publication de la 3.3.0 (la version a été retirée puis republiée avec le correctif). C'est aussi la cause **probable** de l'issue #65 (plantage
  après la 3.2.0 chez qui venait de la 3.0.0), à confirmer par le rapporteur.
- **Plantages natifs et arrêts du système non couverts** : seul un échec Java/Kotlin
  non rattrapé écrit un rapport. Un signal natif, un arrêt pour manque de mémoire
  ou une fermeture forcée n'en laissent pas ; l'issue #65 restera à documenter par
  un `adb logcat` dans ce cas.
- **Pas de bouton « Copier »** : le presse-papiers de Compose n'a pas d'entrée commune à ce jour ;
  le texte est sélectionnable et « Partager » couvre le besoin.
- **Rapport non anonymisé** : la trace peut contenir un message d'exception ; l'écran le dit
  (« reste sur ton appareil tant que tu ne le partages pas ») et rien ne part sans geste.

- **Une page Compose commune** plutôt que trois animations natives
  (`AnimatedVectorDrawable`, CSS, rien sur le bureau) : une seule animation
  à maintenir, identique partout ; les relais natifs se contentent d'une
  image fixe qui coïncide avec la première image Compose. Écarté : rallonger
  l'icône animée du système Android (`windowSplashScreenAnimationDuration`
  plafonne à 1 000 ms et le système retire l'écran dès la première image de
  l'activité, quelle que soit la durée).
- **Ombre de largeur constante** derrière l'aiguille plutôt qu'un secteur qui
  grandit jusqu'à 440° : un secteur ne peut pas dépasser le tour ; l'ombre
  qui tourne finit par coïncider avec le secteur du logo, sans saut.
- **Durée minimale imposée** (≈ 3 s, abrégeable) : demande explicite ;
  l'application se prépare dessous pendant ce temps, l'attente n'est pas
  perdue.
- **Couleurs du logo fixes** (`LogoColors`, partagées avec `KairosLogo`) :
  le logo n'est jamais teinté (charte § Logo) ; ce n'est pas un
  `Color(0x…)` de composant mais l'identité.
- **Pas de page dans les tests** (paramètre par défaut à `false`) : les
  tests d'interface attendent l'application dès la première image ;
  `StartupSplashUiTest` pilote l'horloge (`mainClock.autoAdvance = false`)
  pour tester la page.
