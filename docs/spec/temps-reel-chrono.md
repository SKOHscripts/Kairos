# Temps réel, chrono et alertes

_Rôle : mesurer le temps réellement passé sur les tâches, le montrer en
direct, et prévenir quand un seuil est franchi. Fichiers couverts :
`kmp/core/.../engine/TimeTracking.kt`, `kmp/core/.../alerts/ChronoAlerts.kt`,
`kmp/core/.../day/DayView.kt` (champs du temps), `data/KairosRepository.kt`
(`startTimer`, `stopTimer`), `kmp/ui/.../app/ChronoNotifier.kt`,
`kmp/ui/.../chrono/` (`ChronoWatcher.kt`, `ChronoTexts.kt`), les éléments du
chrono de la vue Jour (`day/TaskRow.kt`, `day/NowCard.kt`, `day/Timeline.kt`),
la section « Alertes du chrono » des Réglages (`reglages.md`), et par plateforme :
`androidApp/.../` (`AndroidNotifier.kt`, `ChronoSync.kt`, `ChronoReceiver.kt`,
`KairosProcess.kt`, `MainActivity.kt`, manifeste),
`desktopApp/.../DesktopNotifier.kt` et `Main.kt`,
`webApp/.../WebNotifier.kt` et `kairos-web.js`. Tests : `TimeTrackingTest`,
`ChronoAlertsTest`, `KairosRepositoryTest`, `DayScreenUiTest`
(`timerRunsAlertsAndStops`)._

État : **jalon M3**. Reprend le besoin de `docs/spec/temps-reel-chrono.md`
(Kairos 2), sans serveur : la voie de secours « notification émise par le
serveur » (issue #34) devient la notification native de chaque plateforme.
L'ancienneté (« traîne depuis ») et le bandeau de surcharge sont décrits par
`ordonnancement.md`.

## 1. Besoin métier (cahier des charges)

### Objectif / problème

Comparer le temps réel à l'estimation, savoir où va la journée, et ne pas
laisser un chrono tourner en silence : dépassement de l'estimé, chrono
oublié, besoin d'une pause. Kairos 2 perdait ses alertes quand la page ou
l'APK était en arrière-plan ; Kairos 3 les confie au système.

### Comportement attendu (utilisateur)

- **Démarrer** le chrono sur une tâche (bouton ▷ de chaque ligne, ou
  « Démarrer le chrono » dans « Maintenant ») ferme toute session ouverte
  ailleurs : au plus un chrono à la fois. **Arrêter** (■ sur la ligne,
  « Arrêter le chrono » dans « Maintenant », dans « En ce moment », dans la
  notification Android) clôt la session. Terminer la tâche l'arrête aussi.
- Le temps s'affiche en direct : badge de la ligne, carte « En ce moment »
  (surface sombre : tâche, minuteur, estimé, « Arrêter le chrono »), titre
  de la fenêtre ou de l'onglet (« (0:12) Tâche · Kairos »). Une tâche sans
  chrono en cours montre « 40 min / 30 » (rouge au-delà de l'estimé).
- « Maintenant » affiche le temps travaillé **aujourd'hui** (sessions
  commencées ce jour, jamais l'historique entier) et sa ventilation par
  type. La frise montre à gauche un rail du temps chronométré à côté du
  planifié.
- **Trois alertes**, chacune une fois par franchissement, jamais pour un
  seuil déjà dépassé quand on commence à regarder : dépassement de
  l'estimé, chrono oublié (180 min de session par défaut), pause suggérée
  (50 min de focus par défaut). Réglages : les deux seuils (0 = désactivé)
  et le son de dernier recours (désactivé par défaut).
- Toute alerte affiche un **bandeau** dans l'application, qui reste jusqu'à
  ce qu'on le ferme. En plus, la **notification système** : Android (même
  application fermée, même après un redémarrage du téléphone), plateau du
  bureau, notification du navigateur. Si elle ne peut pas sortir : le titre
  de la fenêtre clignote une minute, et le son joue s'il est activé.
- « Maintenant » dit l'état des alertes : « Activer les alertes chrono »
  tant que l'autorisation est à demander, sinon « Alertes chrono actives »,
  « Notifications bloquées… » ou « Notifications indisponibles ici… ».
- Un chrono survit à la fermeture de l'application, au rechargement de la
  page web et au redémarrage du téléphone : c'est un instant de départ en
  base.

### Critères de succès

- Démarrer sur B ferme la session de A ; une tâche faite ne démarre pas de
  chrono (`KairosRepositoryTest`).
- Temps du jour limité aux sessions commencées ce jour (`TimeTrackingTest`).
- Démarrer, voir « En ce moment », franchir le seuil de pause avec une
  horloge simulée, voir le bandeau, le titre clignotant et le son quand la
  notification est refusée, fermer le bandeau, arrêter
  (`DayScreenUiTest.timerRunsAlertsAndStops`).
- Web : le titre de l'onglet porte le chrono, qui survit au rechargement
  (vérifié dans Chromium).
- Android : notification permanente avec chronomètre du système et
  « Arrêter » ; alarmes des seuils ; reprise après redémarrage (vérification
  sur appareil à faire : pas d'émulateur dans l'environnement de
  développement).

### Hors périmètre / différé

- Temps de la **semaine** par type : calculé ici (`TimeTracking`), affiché
  par la vue Semaine (`vue-semaine.md`).
- Mode focus plein écran, notification vers un autre appareil, rappels
  hors d'un chrono en cours (comme Kairos 2).
- Alarmes exactes (voir Décisions).

## 2. Solution technique

### Calcul (`core`, pur)

- `TimeTracking` (portage de `app/tasks_time.py`) : `sessionMinutes`
  (minutes entières, jamais négatives, une session ouverte court jusqu'à
  maintenant), `spentMinutesByTask` (sessions **plus** saisie manuelle),
  `runningSession` (la plus récente des ouvertes), `totalMinutes`,
  `sessionsInRange` / `sessionsOnDay` (date **locale** du début, fuseau
  passé), `spentMinutesByType` (clé `""` sans type), `sessionTimeline` (rail :
  sessions bornées à la journée de travail, une minute au moins, nature
  `SESSION`).
- `ChronoAlerts.thresholds(début, base, estimé, oubli, pause)` : instants de
  franchissement. Dépassement : `début + (estimé − base)` (au plus tôt le
  début), le total cité est l'estimé ; oubli et pause : `début + seuil`, sur
  la **session**. Seuil à 0 ou sans estimé : pas d'alerte. `crossed(since,
  now)` : franchis dans `]since, now]` ; `upcoming(now)` : encore à venir.
- `DayView` : `spentByTask`, `running`, `runningTask`, `runningBaseMinutes`
  (temps de la tâche avant la session), `spentToday`, `spentByTypeToday`
  (types non vides, minutes > 0), `sessionTimeline`. `build` reçoit
  l'instant courant.

### Dépôt

`startTimer(tâche)` : refusé si la tâche n'est pas à faire ; ferme toutes
les sessions ouvertes puis en insère une (même instant). `stopTimer()` :
ferme toute session ouverte ; n'écrit rien s'il n'y en a pas.

### Interface commune

- `ChronoNotifier` (dans `AppServices`, `NoNotifier` par défaut) : `state`
  (`ACTIVE`, `CAN_REQUEST`, `DENIED`, `UNAVAILABLE`), `systemHandlesAlerts`,
  `requestPermission`, `notify` (`false` si rien n'est sorti), `setTitle`,
  `beep`.
- `ChronoWatcher`, dans la coquille (toutes destinations) : relancé quand la
  session, la tâche, son estimé, le temps de base ou les réglages changent ;
  chaque seconde, il notifie les seuils franchis depuis la seconde
  précédente (le premier tour part de l'instant où la veille commence : un
  seuil déjà passé ne notifie pas), puis écrit le titre. Par alerte : bandeau
  (`AlertBanners`, surface inverse, fermeture au clic) ; si le système ne
  gère pas déjà les alertes, `notify` ; si elle échoue, titre clignotant
  (« ⚠ Tâche · message ») une minute et `beep` si le son est activé. À
  l'arrêt, le titre redevient normal.
- `ChronoTexts` : textes hors composition (durées, titre et corps des
  alertes, libellés des notifications Android), mêmes phrases que Kairos 2.
- `rememberLiveMinutes` : minuteur vivant (une seconde) du badge de ligne et
  de « En ce moment ».
- Réglages : les deux seuils et le son, section « Alertes du chrono » du
  formulaire commun (`reglages.md` ; carte séparée au jalon M3, fondue au
  jalon M5).

### Android

- `KairosProcess` : une base par processus pour l'activité, les alarmes et
  le redémarrage ; `ChronoSync.start` y suit l'état de la base (clé :
  session, début, titre, base, estimé, seuils).
- `ChronoSync.apply` : canaux `chrono` (importance basse) et
  `chrono-alerts` (haute) ; sans session : notification permanente retirée et
  alarmes annulées ; avec session : notification permanente (catégorie
  chronomètre, `setUsesChronometer`, départ `début − base` pour afficher le
  total, action « Arrêter », ouverture de l'application au toucher), et une
  alarme `setAndAllowWhileIdle` par seuil à venir, titre et corps déjà
  rédigés dans ses extras.
- `ChronoReceiver` : l'alarme poste l'alerte ; « Arrêter » ouvre la base si
  besoin et arrête le chrono (`goAsync`). `BootReceiver` (démarrage,
  mise à jour de l'application) : ouvrir la base suffit à tout remettre.
- `AndroidNotifier` : `ACTIVE` si l'autorisation (Android 13+) est accordée
  et les notifications activées ; alors `systemHandlesAlerts` (les alarmes
  notifient, l'application n'ajoute que son bandeau). Autorisation demandée
  par `MainActivity` à l'opt-in ; état relu à chaque retour dans
  l'application. Son : `ToneGenerator`.
- Manifeste : `POST_NOTIFICATIONS`, `RECEIVE_BOOT_COMPLETED` ; icône de
  notification `ic_notification` (silhouette du cadran).

### Bureau

`DesktopNotifier` : notification par l'icône du plateau (`Tray` de Compose
Desktop, si le bureau en a un ; sinon `UNAVAILABLE`) ; le titre de la
fenêtre lit `titlePrefix` ; bip `Toolkit.beep`. Toucher l'icône du plateau
ramène la fenêtre.

### Web

`WebNotifier` : API `Notification` (contexte sécurisé exigé ; GitHub Pages
est en HTTPS), état relu à chaque notification, autorisation à l'opt-in ;
`document.title` ; bip Web Audio (`kairos-web.js`).

### Décisions et pièges tracés

- **Seuils calculés d'avance**, pas vérifiés par comparaison chaque
  seconde : les mêmes instants servent à la veille de l'application et aux
  alarmes Android.
- **Alarmes inexactes** (`setAndAllowWhileIdle`) : aucune autorisation
  d'alarme exacte (`SCHEDULE_EXACT_ALARM` demande un passage par les
  réglages du système, `USE_EXACT_ALARM` est réservé aux réveils et
  agendas). Téléphone en veille profonde, une alerte peut arriver quelques
  minutes après le seuil ; acceptable pour des rappels de 50 min et plus.
- **Pas de service au premier plan** : le chronomètre de la notification est
  dessiné par le système ; rien ne tourne dans l'application.
- **Un seuil franchi pendant que le téléphone est éteint** n'est pas
  rattrapé au redémarrage (seuls les seuils à venir sont reprogrammés).
- **Temps du jour en date locale** : Kairos 2 prenait la date UTC du début
  (même écart volontaire que « Fait » dans `vue-jour.md`).
- **Double notification évitée sur Android** : quand le système notifie par
  alarme, la veille n'appelle pas `notify`.
- **Tests d'interface avec une horloge simulée** : `AppServices.clock` et un
  `ChronoNotifier` de test (refusé) rendent l'alerte déterministe.
