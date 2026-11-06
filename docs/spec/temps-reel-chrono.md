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
(`timerRunsAlertsAndStops`), `ChronoGuardUiTest`, `SettingsFormTest`._

État : **jalon M3** ; garde-fou du chrono (arrêt automatique) ajouté le 2026-11-06 (issue #220, 4.1.0). Sans serveur : la notification d'alerte est la notification
native de chaque plateforme (issue #34).
L'ancienneté (« traîne depuis ») et le bandeau de surcharge sont décrits par
`ordonnancement.md`.

## 1. Besoin métier (cahier des charges)

### Objectif / problème

Comparer le temps réel à l'estimation, savoir où va la journée, et ne pas
laisser un chrono tourner en silence : dépassement de l'estimé, chrono
oublié, besoin d'une pause. Les alertes ne doivent pas se perdre quand
l'application est en arrière-plan : Kairos les confie au système.

### Comportement attendu (utilisateur)

- **Démarrer** le chrono sur une tâche (bouton ▷ de chaque ligne, ou
  « Démarrer le chrono » dans « Maintenant ») ferme toute session ouverte
  ailleurs : au plus un chrono à la fois. **Arrêter** (■ sur la ligne,
  « Arrêter le chrono » dans « Maintenant », dans « En ce moment », dans la
  notification Android) clôt la session. Terminer la tâche l'arrête aussi.
- Le temps s'affiche en direct : badge de la ligne, carte « En ce moment »
  (surface sombre : tâche, minuteur, estimé, « Arrêter le chrono »), titre
  de la fenêtre ou de l'onglet (« (0:12) Tâche · Kairos »). Une tâche sans
  chrono en cours montre « 40 min / 30 min », « 1 h 10 / 1 h » (rouge
  au-delà de l'estimé) : durées lisibles, comme l'estimation de la ligne
  (« 1 h 30 », jamais « 90 min »).
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

#### Garde-fou : arrêt automatique (issue #220)

Un chrono oublié qui tourne jusqu'au lendemain gonfle le temps passé de la tâche, le temps du jour, les statistiques
(calibration, meilleures heures, tendances) et les durées apprises, d'heures jamais travaillées. L'alerte « chrono
oublié » ne sonne qu'une fois.

- Réglage **« Arrêter le chrono après (min) »** dans « Alertes du chrono » (Réglages → Chrono et statistiques) :
  **240 min (4 h) par défaut**, de 0 à 1 440 (24 h), **0 = jamais**.
- Quand une session atteint cette durée, elle est **close à cet instant précis** (début + durée), pas au moment où Kairos
  s'en aperçoit : la tâche ne reçoit jamais plus que la durée réglée, même si l'application était fermée ou le téléphone
  éteint.
- Kairos le dit : un bandeau « « Tâche » · chrono arrêté automatiquement après 4 h (limite réglable dans Chrono et
  statistiques). », comme une alerte, et la notification du système quand elle peut sortir (Android : même application
  fermée). Si Kairos était fermé (bureau, web) ou le téléphone éteint, l'arrêt et le bandeau viennent à l'ouverture
  suivante, toujours à l'instant limite.
- Les alertes de la session (dépassement, oubli, pause) ne sonnent plus au-delà de la limite : la session est close.

### Critères de succès

- Démarrer sur B ferme la session de A ; une tâche faite ne démarre pas de
  chrono (`KairosRepositoryTest`).
- Temps du jour limité aux sessions commencées ce jour (`TimeTrackingTest`).
- Démarrer, voir « En ce moment », franchir le seuil de pause avec une
  horloge simulée, voir le bandeau, le titre clignotant et le son quand la
  notification est refusée, fermer le bandeau, arrêter
  (`DayScreenUiTest.timerRunsAlertsAndStops`).
- Garde-fou : la limite est calculée à `début + durée`, désactivée à 0 (`ChronoAlertsTest`) ; la session est close à cet
  instant, une seule fois même si deux chemins s'en chargent, et jamais une autre session (`KairosRepositoryTest`) ;
  application ouverte au passage de la limite, ou ouverte après elle (rattrapage), avec une horloge simulée : session
  close à l'instant limite et bandeau affiché ; à 0, rien ne s'arrête (`ChronoGuardUiTest`) ; réglage 240 par défaut,
  borné à 0-1 440, et une base qui n'y touche pas n'écrit rien (`SettingsFormTest`, export inchangé).
- Web : le titre de l'onglet porte le chrono, qui survit au rechargement
  (vérifié dans Chromium).
- Android : notification permanente avec chronomètre du système et
  « Arrêter » ; alarmes des seuils et du garde-fou ; reprise après redémarrage (vérification
  sur appareil à faire : pas d'émulateur dans l'environnement de
  développement).

### Hors périmètre / différé

- Temps de la **semaine** par type : calculé ici (`TimeTracking`), affiché
  par la vue Semaine (`vue-semaine.md`).
- Mode focus plein écran, notification vers un autre appareil. Les rappels hors d'un chrono en cours (tâche à heure fixe,
  tâche suivante) ont leur spec, `rappels.md` (3.8.0) : ils réutilisent le notificateur, les bandeaux et, sur Android, les
  alarmes inexactes et `ChronoReceiver` (`ACTION_REMINDER`).
- Alarmes exactes (voir Décisions).
- Garde-fou : corriger la durée d'une session déjà close (il n'existe pas d'édition des sessions ; le temps manuel de la
  fiche ne fait qu'ajouter), arrêter à la fin de la journée de travail plutôt qu'après une durée, marquer la session
  arrêtée par le garde-fou dans les données (pistes notées, § Décisions).

## 2. Solution technique

Depuis le jalon E1 de l'espace Équipe, la veille du chrono (`ChronoWatcher`) et la notification Android (`ChronoSync`) lit `repository.personalSnapshot` (la vue Perso, `equipe.md` § Espaces et filtre central) et non la base complète ; sans tâche d'équipe, c'est la même instance.

### Calcul (`core`, pur)

- `TimeTracking` (portage de `app/tasks_time.py`) : `sessionMinutes`
  (minutes entières, jamais négatives, une session ouverte court jusqu'à
  maintenant), `spentMinutesByTask` (sessions **plus** saisie manuelle **plus** temps
  rapporté par un membre, `reportedMinutes`, `equipe-echanges.md`),
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
  L'estimé passé à `thresholds` est `DurationEstimator.effectiveMinutes` (3.4, `durees-apprises.md`) : l'estimation de la
  tâche, sinon sa durée apprise **fiable** (série, titre ou modèle, `estimateur-modele.md`), sinon rien (pas d'alerte de dépassement) ; `ChronoWatcher` et, sur Android,
  `ChronoSync` s'en servent, et la carte « Maintenant » affiche la même valeur.
- **Garde-fou** : `ChronoAlerts.autoStopAt(début, limite)` = `début + limite`, nul si la limite vaut 0 ou moins ;
  `ChronoAlerts.beforeStop(seuils, arrêt)` retire les seuils situés à l'instant d'arrêt ou après (ni bandeau ni alarme
  pour une session qui sera close). `Settings.timerAutoStopMinutes` : `Int?`, **nul par défaut** (règle des réglages
  ajoutés : rien n'est écrit dans la base ni l'export tant qu'on n'y touche pas) ; `Settings.timerAutoStop` en donne la
  valeur effective, `DEFAULT_TIMER_AUTO_STOP_MINUTES` = 240 si nul.
- `DayView` : `spentByTask`, `running`, `runningTask`, `runningBaseMinutes`
  (temps de la tâche avant la session), `spentToday`, `spentByTypeToday`
  (types non vides, minutes > 0), `sessionTimeline`. `build` reçoit
  l'instant courant.

### Dépôt

`startTimer(tâche)` : refusé si la tâche n'est pas à faire ; ferme toutes
les sessions ouvertes puis en insère une (même instant). `stopTimer()` :
ferme toute session ouverte ; n'écrit rien s'il n'y en a pas.
`stopTimerAt(session, instant)` (garde-fou) : ferme **cette** session à **cet** instant, seulement si elle est encore
ouverte (`closeSessionAt`, `… WHERE id = ? AND ended_at IS NULL`) ; rend `true` si elle l'a fermée. Idempotente : la
veille de l'application et l'alarme d'Android peuvent s'en charger toutes deux, une seule réussit et c'est elle qui
prévient. L'instant est borné à [début, maintenant].
`deleteTask` (et la suppression d'une sous-tâche depuis la fiche) ferme la
session ouverte de la tâche à l'instant de la suppression : ses sessions
restent (temps passé), mais une session ouverte sur une tâche disparue
compterait sans fin et ferait sonner les alertes (QA du 2026-11-04,
`KairosRepositoryTest.deletingTheTimedTaskStopsItsTimer`).

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
- Garde-fou dans `ChronoWatcher` : à chaque seconde, **y compris la première** (rattrapage à l'ouverture, contrairement
  aux alertes), si `maintenant ≥ autoStopAt`, `stopTimerAt(session, autoStopAt)` ; s'il a fermé la session : bandeau
  (`ChronoTexts.autoStopBody`, durée lisible), notification si le système ne s'en charge pas déjà, et à défaut le son
  s'il est activé (pas de titre clignotant : la session close, la veille s'arrête ; le bandeau reste). Cette partie tourne
  hors annulation (`NonCancellable`) : fermer la session relance la veille, le bandeau ne doit pas se perdre en route. Le
  réglage est dans les clés de la veille.
- Réglages : `SettingsForm.MAX_TIMER_AUTO_STOP` = 1 440. La carte « Alertes du chrono » porte le repère du guide
  `GuideSection.SETTINGS_CHRONO`, cible de la carte du catalogue `timer_guard` (« Garde-fou du chrono », chapitre
  « Suivre », 4.1.0) ; puce et ligne du tableau dans les deux README.
- Réglages : `timerAutoStopMinutes` (`SettingsForm`, entier 0-1 440, affiché 240 quand nul, enregistré nul s'il vaut
  240) dans « Alertes du chrono », après le chrono oublié, libellé « Arrêter le chrono après (min) » et aide « 0 = jamais.
  La session s'arrête à cette durée, même application fermée. ». Partagé par la synchronisation, comme les autres seuils
  (`SyncScope`).
- `ChronoTexts` : textes hors composition (durées, titre et corps des
  alertes, libellés des notifications Android).
- `rememberLiveMinutes` : minuteur vivant (une seconde) du badge de ligne et
  de « En ce moment ».
- Réglages : les deux seuils et le son, section « Alertes du chrono » du
  formulaire commun, catégorie « Chrono et statistiques » (`reglages.md` ; carte séparée au jalon M3, fondue au
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
- Garde-fou : `ChronoSync` programme aussi une alarme `ACTION_AUTO_STOP` à `autoStopAt` (même mécanisme inexact,
  extras : session, instant, titre de la tâche, limite) et ne programme plus les seuils situés après elle ; si la session
  a déjà dépassé la limite quand l'état change (redémarrage, ouverture), il l'arrête aussitôt. `ChronoReceiver`
  (`ACTION_AUTO_STOP`) : `stopTimerAt` ; s'il a fermé la session, notification d'alerte « chrono arrêté automatiquement
  après… ». La clé de `ChronoSync` comprend la limite.
- `ChronoReceiver` : l'alarme poste l'alerte ; « Arrêter » ouvre la base si
  besoin et arrête le chrono (`goAsync`). `BootReceiver` (démarrage,
  mise à jour de l'application) : ouvrir la base suffit à tout remettre.
- `AndroidNotifier` : `ACTIVE` si l'autorisation (Android 13+) est accordée
  et les notifications activées ; alors `systemHandlesAlerts` (les alarmes
  notifient, l'application n'ajoute que son bandeau). Autorisation demandée
  par `MainActivity` à l'opt-in ; état relu à chaque retour dans
  l'application. Son : `ToneGenerator`.
- Manifeste : `POST_NOTIFICATIONS`, `RECEIVE_BOOT_COMPLETED` (et `READ_CALENDAR`, facultative, pour les calendriers du
  téléphone, `connexions-externes.md`) ; icône de
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
- **Temps du jour en date locale** : la date est celle du début de
  session dans le fuseau local, pas en UTC (même règle que « Fait » dans
  `vue-jour.md`).
- **Double notification évitée sur Android** : quand le système notifie par
  alarme, la veille n'appelle pas `notify`.
- **Garde-fou par durée, à 4 h par défaut** (choix du propriétaire du 2026-11-06, #220) : une durée réglable plutôt que
  la fin de la journée de travail (les horaires varient, une session du soir est légitime) ou qu'une question au retour
  (le temps faux resterait compté jusque-là, et sur Android personne ne répond application fermée). 4 h : au-delà de
  l'alerte « chrono oublié » (3 h), qui prévient donc d'abord, et au-delà d'une longue séance de travail.
- **Close à l'instant limite, pas à la découverte** : sans quoi un chrono oublié la veille compterait toute la nuit
  jusqu'à l'ouverture. La tâche reçoit au plus la limite ; ce surplus éventuel n'est pas corrigeable par Kairos
  aujourd'hui (pas d'édition de session), le bandeau le dit sans promettre de correction.
- **Pas de marque « arrêtée par le garde-fou » en base** : il faudrait une colonne (migration) pour un usage
  hypothétique des statistiques ; le bandeau et la notification suffisent. À rouvrir si les statistiques doivent
  l'écarter.
- **Idempotence plutôt que coordination** : la veille et l'alarme Android peuvent agir en même temps ; la requête ne
  ferme que la session visée si elle est encore ouverte, sous le verrou du dépôt, et seul celui qui l'a fermée prévient.
- **Réglage nul par défaut, borné à 24 h** : l'export d'une base qui n'y touche pas ne change pas ; au-delà de 24 h,
  le garde-fou n'en serait plus un.
- **Tests d'interface avec une horloge simulée** : `AppServices.clock` et un
  `ChronoNotifier` de test (refusé) rendent l'alerte déterministe.
