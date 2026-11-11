# Temps réel, chrono et alertes

_Rôle : mesurer le temps réellement passé sur les tâches, le montrer en
direct, et prévenir quand un seuil est franchi. Fichiers couverts :
`kmp/core/.../engine/TimeTracking.kt`, `kmp/core/.../alerts/ChronoAlerts.kt`,
`kmp/core/.../day/DayView.kt` (champs du temps), `data/KairosRepository.kt`
(`startTimer`, `stopTimer`), `kmp/ui/.../app/ChronoNotifier.kt`, `kmp/ui/.../app/NotifyPrompt.kt`, `kmp/ui/.../day/AlertsPromptDialog.kt`,
`kmp/ui/.../chrono/` (`ChronoWatcher.kt`, `ChronoTexts.kt`), les éléments du
chrono de la vue Jour (`day/TaskRow.kt`, `day/NowCard.kt`, `day/Timeline.kt`),
la section « Alertes du chrono » des Réglages (`reglages.md`), et par plateforme :
`androidApp/.../` (`AndroidNotifier.kt`, `ChronoSync.kt`, `ChronoReceiver.kt`,
`KairosProcess.kt`, `MainActivity.kt`, manifeste),
`desktopApp/.../DesktopNotifier.kt` et `Main.kt`,
`webApp/.../WebNotifier.kt` et `kairos-web.js` ; cloches : `kmp/core/.../alerts/Bell.kt`, `kmp/tools/make_bells.py`,
`kmp/ui/.../composeResources/files/bell_*.wav`, `kmp/androidApp/src/main/res/raw/bell_*.wav`, `kmp/ui/.../chrono/Bells.kt`. Tests : `TimeTrackingTest`,
`ChronoAlertsTest`, `KairosRepositoryTest`, `DayScreenUiTest`
(`timerRunsAlertsAndStops`), `ChronoGuardUiTest`, `ChronoNotifyUiTest`, `NotifyPromptTest`, `DesktopNotifierTest`, `SettingsFormTest`, `BellTest`, `BellFilesTest`._

État : **jalon M3** ; garde-fou du chrono (arrêt automatique) ajouté le 2026-11-06 (issue #220, 4.1.0) ; notifications
fiabilisées le 2026-11-06 (issue #219 : notification immédiate application ouverte sur Android, alarmes exactes quand
elles sont accordées, économie d'énergie signalée, `notify-send` sous Linux). Sans serveur : la notification d'alerte est la notification
native de chaque plateforme (issue #34). Cloches du pomodoro le 2026-11-09 (issue #234, 4.2.0).
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
  et les cloches (désactivées par défaut, § Cloches).
- Toute alerte affiche un **bandeau** dans l'application, qui reste jusqu'à
  ce qu'on le ferme. En plus, la **notification système** : Android (même
  application fermée, même après un redémarrage du téléphone), plateau du
  bureau, notification du navigateur. Si elle ne peut pas sortir : le titre
  de la fenêtre clignote une minute. Les cloches, si elles sont activées, sonnent à chaque alerte (§ Cloches).
- « Maintenant » dit l'état des alertes : « Activer les alertes chrono »
  tant que l'autorisation est à demander, sinon « Alertes chrono actives »,
  « Notifications bloquées… » ou « Notifications indisponibles ici… ».
- **Alertes à l'heure** (issue #219, constat du propriétaire du 2026-11-06 : seul le bandeau apparaissait, sur Android
  autorisation donnée et sous Linux) :
  - Android, application ouverte (ou encore en mémoire) : la notification du système part **au moment du seuil**, en même
    temps que le bandeau, et une seule fois (l'alarme du même seuil est annulée).
  - Android, application fermée : l'alarme est **exacte** si l'autorisation « Alarmes et rappels » est accordée, sinon
    inexacte comme avant (quelques minutes de retard possibles, plus avec l'économie d'énergie).
  - Sous « Alertes chrono actives » (Android), une ligne par frein connu, avec un bouton vers le réglage du système :
    « Les alertes peuvent arriver en retard, application fermée. » → « Autoriser les alarmes exactes » (tant que
    l'autorisation manque) ; « L’économie d’énergie peut retarder les alertes. » → « Réglages d’énergie » (tant que Kairos
    est soumis à l'optimisation de la batterie). Rien n'est demandé d'office ; l'état est relu au retour dans l'application.
  - Linux : la notification passe par le service de notifications du bureau (`notify-send`), qu'il y ait ou non une zone
    de notification ; l'icône du plateau reste la voie de Windows et macOS, et celle de Linux quand `notify-send` manque.
- Un chrono survit à la fermeture de l'application, au rechargement de la
  page web et au redémarrage du téléphone : c'est un instant de départ en
  base.
- **Le temps passé est le temps réellement couvert** (#195) : deux sessions
  d'une même tâche qui se chevauchent ne comptent qu'une fois le temps
  commun (10:00–11:00 et 10:30–11:30 font 90 min, pas 120). Un seul chrono
  tourne à la fois, donc ces chevauchements ne viennent que d'un import ou
  d'une fusion de synchronisation ; les sessions elles-mêmes ne sont pas
  modifiées, seul le calcul en tient compte.

#### Demande d'autorisation au démarrage du chrono (demande du propriétaire du 2026-11-07)

Le bouton « Activer les alertes chrono » restait facultatif, en bas de « Maintenant » : peu vu, donc peu utilisé, alors
que sans autorisation Android et le navigateur ne notifient pas.

- **Au premier chrono de la session** (bouton ▷ d'une ligne, « Démarrer le chrono » de « Maintenant », ou
  « Démarrer le chrono » des félicitations de la première tâche), **si l'autorisation est à demander** (Android 13 et plus,
  navigateur), Kairos ouvre un dialogue : « Recevoir les alertes du chrono ? » avec le pourquoi (dépassement, chrono
  oublié, pause) et deux boutons, « Activer » et « Plus tard ». Le chrono **a déjà démarré** dans tous les cas.
- « Activer » ouvre la demande du système (un geste de l'utilisateur, que le navigateur exige) ; « Plus tard », ou un clic à
  côté, ferme le dialogue sans rien demander.
- **Une fois par lancement de l'application**, quelle que soit la réponse : pas de deuxième dialogue au chrono suivant.
  Rien n'est stocké : au prochain lancement, tant que le système n'a ni accordé ni refusé, le dialogue revient. Refusé par
  le système, accordé, indisponible (bureau sans notifications, par exemple) : jamais de dialogue.
- Le bouton de « Maintenant » reste, et se **voit** : bouton en contour (et non plus un texte sans bord sur le bloc
  teinté), de même que les boutons des freins connus (« Autoriser les alarmes exactes », « Réglages d'énergie »), dont la
  phrase passe à la taille de corps normale.

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

### Cloches (issue #234, 4.2.0)

- Les notifications du chrono sortent maintenant à l'heure (#219) ; il leur manquait un **son** pour en faire un vrai
  pomodoro. L'interrupteur « Son » des Réglages ne servait qu'en dernier recours (un bip quand aucune notification ne
  pouvait sortir) : il devient **« Cloches avec les alertes »** et, activé, fait sonner **chaque** alerte du chrono,
  notification sortie ou non.
- **Pause suggérée** (pomodoro) : **une cloche**, légère et sobre, avec une résonance (« traîne ») d'environ deux secondes.
- **Chrono oublié** : **deux cloches**, la première avec sa traîne, la seconde plus douce, avec la sienne : on distingue
  les deux alertes à l'oreille, sans regarder l'écran.
- **Dépassement de l'estimé** et **arrêt automatique** (garde-fou) : une cloche, comme la pause (choix du propriétaire).
- Le réglage reste **désactivé par défaut** : rien ne sonne chez qui n'y touche pas. Activé, il sonne sur Android
  (application fermée comprise : c'est la notification qui porte la cloche), sur le bureau et sur le web.
- Sur Android, la cloche est le son de la notification : couper la notification ou le son dans le système la coupe. Le mode
  « Ne pas déranger » du système s'applique.
- **Carte « Alertes du chrono »** réordonnée et réécrite dans les termes du pomodoro, un réglage sous l'autre : d'abord la
  pause (« Pomodoro : pause après (min de concentration) », aide « Une cloche. 0 = pas de pause suggérée. »), puis le chrono
  oublié (aide « Deux cloches. »), puis l'arrêt automatique, puis l'interrupteur des cloches avec son aide. Le texte
  d'introduction de la carte parle de concentration et de pause.

### Critères de succès

- Démarrer sur B ferme la session de A ; une tâche faite ne démarre pas de
  chrono (`KairosRepositoryTest`).
- Temps du jour limité aux sessions commencées ce jour (`TimeTrackingTest`).
- Sessions d'une même tâche qui se chevauchent comptées une fois (90 min pour 10:00–11:00 et 10:30–11:30), autre tâche
  jamais rognée, session couverte retirée, rien ne change sans chevauchement
  (`TimeTrackingTest.overlapping_sessions_of_one_task_count_once`, `without_overlap_the_sessions_are_left_untouched`,
  `an_open_session_overlapped_by_a_later_one_runs_until_now`).
- Démarrer, voir « En ce moment », franchir le seuil de pause avec une
  horloge simulée, voir le bandeau, le titre clignotant et la cloche quand la
  notification est refusée, fermer le bandeau, arrêter
  (`DayScreenUiTest.timerRunsAlertsAndStops`).
- Cloches (#234) : la pause et le dépassement donnent une cloche, le chrono oublié deux (`BellTest`) ; cloches activées,
  chaque alerte sonne, que la notification sorte ou non, sauf là où la notification porte elle-même la cloche (Android) ;
  désactivées, rien ne sonne (`DayScreenUiTest`, `ChronoNotifyUiTest`). Les fichiers des cloches sont les mêmes octets
  dans les ressources communes et dans celles d'Android (`BellFilesTest`).
- Notifications (#219) : la veille appelle la voie « chrono » du notificateur à chaque seuil franchi, y compris quand le
  système programme aussi des alarmes ; une voie qui a notifié ne fait ni clignoter le titre ni sonner ; les freins
  « alarmes exactes » et « économie d'énergie » s'affichent sous « Alertes chrono actives » avec leur bouton, qui appelle
  la plateforme (`ChronoNotifyUiTest`) ; bureau : sous Linux avec `notify-send`, état actif et notification par la
  commande (nom de l'application, titre, corps) même sans plateau ; sans l'un ni l'autre, indisponible
  (`DesktopNotifierTest`). Android (sur appareil, pas d'émulateur ici) : notification à l'heure application ouverte,
  alarme exacte une fois l'autorisation accordée, bascule en inexacte si elle est retirée.
- Demande au démarrage : à l'état « à demander », démarrer un chrono ouvre le dialogue (le chrono tourne) ; « Plus tard »
  ne demande rien au système, le bouton de « Maintenant » reste, et un deuxième chrono de la session ne rouvre rien ;
  « Activer » appelle `requestPermission` ; avec les alertes déjà actives, aucun dialogue (`ChronoNotifyUiTest`) ;
  `NotifyPrompt` : une demande par lancement, aucun autre état ne la consomme (`NotifyPromptTest`).
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
- Garde-fou : corriger une session précise (heure de début ou de fin ; depuis 4.1.0 le **total** se corrige dans la fiche,
  `fiche-tache.md` § Temps passé, en raccourcissant les sessions récentes), arrêter à la fin de la journée de travail plutôt qu'après une durée, marquer la session
  arrêtée par le garde-fou dans les données (pistes notées, § Décisions).

## 2. Solution technique

Depuis le jalon E1 de l'espace Équipe, la veille du chrono (`ChronoWatcher`) et la notification Android (`ChronoSync`) lit `repository.personalSnapshot` (la vue Perso, `equipe.md` § Espaces et filtre central) et non la base complète ; sans tâche d'équipe, c'est la même instance.

### Calcul (`core`, pur)

- `TimeTracking` (portage de `app/tasks_time.py`) : `sessionMinutes`
  (minutes entières, jamais négatives, une session ouverte court jusqu'à
  maintenant), `spentMinutesByTask` (sessions **plus** saisie manuelle **plus** temps
  rapporté par un membre, `reportedMinutes`, `equipe-echanges.md`),
  `runningSession` (la plus récente des ouvertes), `totalMinutes`,
  `withoutOverlaps` (#195 : dans l'ordre (début, id), le début d'une session
  est rogné à la plus grande fin des précédentes de **sa** tâche, une
  session ouverte courant jusqu'à maintenant ; une session entièrement
  couverte est retirée ; sans chevauchement, la liste est rendue telle
  quelle, ordre d'entrée gardé),
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
  `beep`. Depuis #219 : `notifyChrono(titre, corps, étiquette)`, la voie des seuils du chrono et du garde-fou (par
  défaut : vrai si le système s'en charge, sinon `notify` ; Android la redéfinit), `hints` (`StateFlow<Set<NotifyHint>>`,
  vide par défaut ; `EXACT_ALARMS`, `BATTERY`) et `openHint(frein)`. `systemHandlesAlerts` reste la règle des rappels et
  de l'avis des estimations, que #219 ne change pas.
- `ChronoWatcher`, dans la coquille (toutes destinations) : relancé quand la
  session, la tâche, son estimé, le temps de base ou les réglages changent ;
  chaque seconde, il notifie les seuils franchis depuis la seconde
  précédente (le premier tour part de l'instant où la veille commence : un
  seuil déjà passé ne notifie pas), puis écrit le titre. Par alerte : bandeau
  (`AlertBanners`, surface inverse, fermeture au clic) ; `notifyChrono` ; si elle échoue, titre clignotant
  (« ⚠ Tâche · message ») une minute et `beep` si le son est activé. À
  l'arrêt, le titre redevient normal.
- Garde-fou dans `ChronoWatcher` : à chaque seconde, **y compris la première** (rattrapage à l'ouverture, contrairement
  aux alertes), si `maintenant ≥ autoStopAt`, `stopTimerAt(session, autoStopAt)` ; s'il a fermé la session : bandeau
  (`ChronoTexts.autoStopBody`, durée lisible), `notifyChrono` (étiquette `AUTO_STOP`), et à défaut le son
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
- « Maintenant » (`NowCard`, `AlertsOptIn`) : à l'état `CAN_REQUEST`, le bouton « Activer les alertes chrono » est un
  `KairosOutlinedButton` ; quand l'état est `ACTIVE`, une ligne par frein de `hints` (`alerts_hint_exact`,
  `alerts_hint_battery`, `bodyMedium`) suivie de son `KairosOutlinedButton` (`alerts_hint_exact_action`,
  `alerts_hint_battery_action`) qui appelle `openHint`. Des boutons texte se perdaient sur le bloc teinté (2026-11-07).
- Demande au démarrage (`NotifyPrompt`, `AlertsPromptDialog`, `DayScreen`) : `AppServices.notifyPrompt` porte un seul
  drapeau en mémoire ; `claim(état)` rend vrai la première fois que l'état est `CAN_REQUEST`, puis jamais (aucun autre état
  ne le consomme). `DayScreen` démarre le chrono par `startTimer(id)` : `repository.startTimer`, puis, si `claim`, ouvre
  `AlertsPromptDialog` (`alerts_prompt_*`, six langues) ; « Activer » appelle `notifier.requestPermission()`. Tous les
  départs de chrono de l'écran Jour passent par là (ligne, « Maintenant », félicitations) ; la démonstration de la visite
  guidée appelle le dépôt directement et ne demande rien.
- `ChronoTexts` : textes hors composition (durées, titre et corps des
  alertes, libellés des notifications Android).
- `rememberLiveMinutes` : minuteur vivant (une seconde) du badge de ligne et
  de « En ce moment ».
- Réglages : les deux seuils et le son, section « Alertes du chrono » du
  formulaire commun, catégorie « Chrono et statistiques » (`reglages.md` ; carte séparée au jalon M3, fondue au
  jalon M5).

### Cloches (`core`, `ui`, plateformes ; issue #234)

- `Bell` (`core/alerts/Bell.kt`, pur) : `SINGLE`, `DOUBLE` ; `Bell.of(kind)` : `POMODORO` et `OVER` → `SINGLE`, `IDLE` →
  `DOUBLE` ; `Bell.AUTO_STOP` = `SINGLE`.
- **Sons générés** par `kmp/tools/make_bells.py` (bibliothèque standard de Python : `wave`, `math`), jamais retouchés à la
  main : WAV mono 16 bits à 22 050 Hz. Une frappe = les partiels d'une cloche sur une prime de 392 Hz (hum 0,5 ; prime 1 ;
  tierce mineure 1,183 ; quinte 1,506 ; nominal 2,0, la note entendue à 784 Hz, sol 5 ; puis 2,514 ; 2,662 ; 3,011 ;
  4,166), chacun en décroissance exponentielle, les graves les plus longs (1,6 s pour le hum : la traîne) ; deux doublets
  désaccordés de 1,7 et 2,3 Hz (tierce, nominal) dont le battement fait le scintillement métallique ; trois partiels très
  aigus éteints en 15 à 30 ms (le « tic » du battant) ; attaque de 2 ms. `bell_single.wav` : une frappe, 2,5 s ;
  `bell_double.wav` : une frappe, puis une seconde à 0,75 s, à 55 % du volume, 3,2 s en tout. Timbre métallique demandé par
  le propriétaire le 2026-11-09 (une première version à quatre partiels sonnait trop « carillon électronique »). Crête à 35 % de la pleine échelle (« léger »). Le script écrit les deux
  fichiers dans `ui/src/commonMain/composeResources/files/` (bureau, web) et `androidApp/src/main/res/raw/` (canaux de
  notification) ; `BellFilesTest` (ui) vérifie que les deux copies sont identiques, en WAV mono 16 bits à 22 050 Hz ;
  le script, relancé, redonne les mêmes octets (vérifié à la main, pas en test : Gradle ne lance pas Python). Pas de
  dépendance ajoutée.
- `ui/chrono/Bells.kt` : `suspend fun bellBytes(bell)` lit le fichier (`Res.readBytes("files/bell_….wav")`), pour les
  plateformes qui le jouent elles-mêmes.
- `ChronoNotifier.ring(bell)` (remplace `beep()`, par défaut ne fait rien) et `ringsWithNotification` (faux par défaut) :
  vrai quand une notification sortie porte déjà la cloche, pour ne pas sonner deux fois.
- `ChronoWatcher` : pour chaque alerte et pour l'arrêt automatique, `sent = notifyChrono(…)`, puis si `timerAlertSound` et
  pas (`sent` et `ringsWithNotification`) : `ring(Bell.of(kind))`. Le titre clignotant reste réservé à l'échec de la
  notification.
- **Android** : deux canaux de plus, `chrono-bell-1` et `chrono-bell-2` (importance haute, nom « Alertes du chrono (une
  cloche) » / « (deux cloches) » via `ChronoTexts.bellChannel`), dont le son est `android.resource://…/raw/bell_single` ou `bell_double`
  (`AudioAttributes.USAGE_NOTIFICATION`). `ChronoSync.postAlert(context, kind, title, body, sound)` poste sur le canal de la
  cloche de la nature si `sound`, sur `chrono-alerts` sinon ; la clé de `ChronoSync` comprend `timerAlertSound`, et les
  alarmes portent `EXTRA_SOUND` (`ChronoReceiver`). `AndroidNotifier` : `ringsWithNotification` = vrai (`notifyChrono` ne
  rend vrai qu'en postant) ; `ring(bell)` joue `R.raw.bell_…` par `MediaPlayer` (usage notification), libéré à la fin.
  `ToneGenerator` n'est plus utilisé.
- **Bureau** : `ring` joue les octets par `javax.sound.sampled` (`AudioSystem.getClip`), refermé à la fin (`LineListener`),
  dans un `runCatching` (pas de sortie son : rien). `Toolkit.beep` n'est plus utilisé.
- **Web** : `ring` passe le WAV en `data:audio/wav;base64,…` à `KairosWeb.ring`, qui le joue par `new Audio(…).play()`
  (erreur ignorée : un navigateur peut refuser un son sans geste récent). Le bip Web Audio est retiré.
- Réglages : section `CHRONO` dans l'ordre `pomodoroFocusMinutes`, `timerIdleAlertMinutes`, `timerAutoStopMinutes`,
  `timerAlertSound` ; chaînes réécrites `settings_chrono_body`, `settings_pomodoro`, `settings_sound`, ajoutées
  `settings_pomodoro_help`, `settings_idle_help`, `settings_sound_help`, `notif_channel_bell_single`,
  `notif_channel_bell_double`, dans les six langues.
- Catalogue : carte `chrono_bells` (chapitre « Suivre », 4.2.0, toutes plateformes, ouvre la carte « Alertes du chrono »),
  puce « Pomodoro bells » du README, lignes des deux tableaux, en anglais et en français.

### Android

- `KairosProcess` : une base par processus pour l'activité, les alarmes et
  le redémarrage ; `ChronoSync.start` y suit l'état de la base (clé :
  session, début, titre, base, estimé, seuils).
- `ChronoSync.apply` : canaux `chrono` (importance basse) et
  `chrono-alerts` (haute) ; sans session : notification permanente retirée et
  alarmes annulées ; avec session : notification permanente (catégorie
  chronomètre, `setUsesChronometer`, départ `début − base` pour afficher le
  total, action « Arrêter », ouverture de l'application au toucher), et une
  alarme par seuil à venir, titre et corps déjà rédigés dans ses extras : `setExactAndAllowWhileIdle` si
  `canExact` (Android 11 et moins, ou `AlarmManager.canScheduleExactAlarms()`), sinon `setAndAllowWhileIdle` (#219,
  `ChronoSync.schedule`). Les rappels gardent leurs alarmes inexactes (`rappels.md`, hors de #219).
- `ChronoSync.postNow(étiquette, titre, corps)` (#219) : annule l'alarme du seuil (nature `OVER`/`IDLE`/`POMODORO`, ou
  `AUTO_STOP` pour le garde-fou) puis poste la même notification que l'alarme (même identifiant). Les alertes portent
  `setOnlyAlertOnce` : reposter une alerte encore affichée ne sonne pas deux fois. `ChronoSync.reschedule` repasse la
  dernière clé (mêmes notification et alarmes) : appelé au retour dans l'application, il bascule les alarmes en exactes
  quand l'autorisation vient d'être accordée, en inexactes quand elle a été retirée (le système annule alors les alarmes
  exactes de l'application).
- Garde-fou : `ChronoSync` programme aussi une alarme `ACTION_AUTO_STOP` à `autoStopAt` (même mécanisme, exacte si accordée,
  extras : session, instant, titre de la tâche, limite) et ne programme plus les seuils situés après elle ; si la session
  a déjà dépassé la limite quand l'état change (redémarrage, ouverture), il l'arrête aussitôt. `ChronoReceiver`
  (`ACTION_AUTO_STOP`) : `stopTimerAt` ; s'il a fermé la session, notification d'alerte « chrono arrêté automatiquement
  après… ». La clé de `ChronoSync` comprend la limite.
- `ChronoReceiver` : l'alarme poste l'alerte ; « Arrêter » ouvre la base si
  besoin et arrête le chrono (`goAsync`). `BootReceiver` (démarrage,
  mise à jour de l'application) : ouvrir la base suffit à tout remettre.
- `AndroidNotifier` : `ACTIVE` si l'autorisation (Android 13+) est accordée
  et les notifications activées ; alors `systemHandlesAlerts` (les alarmes
  notifient : règle des rappels). `notifyChrono` : `ACTIVE` → `ChronoSync.postNow`, vrai ; sinon faux (bandeau, titre,
  son). `hints` : `EXACT_ALARMS` si Android 12+ et `canScheduleExactAlarms()` faux ; `BATTERY` si
  `PowerManager.isIgnoringBatteryOptimizations` faux. `openHint` : `Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM`
  (`package:` de Kairos) ou `Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS` (la liste du système : aucune
  permission `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`), dans un `runCatching`. Autorisation demandée par `MainActivity` à
  l'opt-in ; état, freins et alarmes (`ChronoSync.reschedule`) relus à chaque retour dans l'application. Cloches :
  § Cloches.
- Manifeste : `POST_NOTIFICATIONS`, `RECEIVE_BOOT_COMPLETED`, `SCHEDULE_EXACT_ALARM` (#219 : accordée d'office
  sur Android 12 et 13, à accorder par l'utilisateur sur Android 14 et plus ; jamais `USE_EXACT_ALARM`), et
  `READ_CALENDAR`, facultative, pour les calendriers du téléphone (`connexions-externes.md`) ; icône de
  notification `ic_notification` (silhouette du cadran).

### Bureau

`DesktopNotifier(tray, osName, notifySend, run)` : sous Linux, si `notify-send` est sur le `PATH` (`findOnPath`), la
notification passe par lui (`notify-send -a Kairos <titre> <corps>`, délai de 5 s, réussite = code 0) : c'est la
notification native du bureau (GNOME, KDE…), avec ou sans zone de notification ; sinon, et sous Windows et macOS, par
l'icône du plateau (`Tray` de Compose Desktop, si le bureau en a un). État `ACTIVE` si l'une des deux voies existe,
sinon `UNAVAILABLE`. Le titre de la fenêtre lit `titlePrefix` ; cloches § Cloches. Toucher l'icône du plateau ramène
la fenêtre. Windows et macOS : la voie du plateau donne la notification du système (centre de notifications) ; à
vérifier sur machine.

### Web

`WebNotifier` : API `Notification` (contexte sécurisé exigé ; GitHub Pages
est en HTTPS), état relu à chaque notification, autorisation à l'opt-in ;
`document.title` ; cloches § Cloches (`kairos-web.js`).

### Décisions et pièges tracés

- **Restant quand la tâche dérape** (#232) : la carte « En ce moment » dit ce qu'ont pris au total les tâches
  comparables qui ont duré plus longtemps que le temps déjà passé (`Remaining`, `previsions-taches.md` § Étape D).

- **Sessions qui se chevauchent : l'union par tâche** (#195, 2026-11-10, choix du propriétaire). Additionner deux
  sessions qui se chevauchent comptait deux fois le temps commun, et ce surplus nourrissait les statistiques, la
  calibration et les durées apprises. Retenu : le calcul lit les sessions par `TimeTracking.withoutOverlaps`, qui
  rogne la plus tardive. Tout cumul de temps chronométré y passe : `spentMinutesByTask`, `spentMinutesByType` et
  `totalMinutes` l'appliquent eux-mêmes ; les boucles qui répartissent des sessions l'appliquent avant de filtrer
  (`TaskStats.focus` et `trackedMinutesWindow` du tableau de bord, `StatsTrends.tracked`, `BestHours.measure`,
  heures réelles de `ForecastData`, `baseMinutes` de `ChronoWatcher.kt`). Seul le rail « réel » de la frise
  (`sessionTimeline`) montre les sessions telles qu'en base : c'est un affichage, pas un cumul. Écartés : **garder la
  somme** (simple, mais faux dès qu'un import ou une fusion crée un chevauchement) ; **rogner les sessions à l'import
  ou à la fusion** (modifie les données, et une fusion qui réécrit des sessions passées devrait le faire de façon
  identique sur chaque appareil). Micro-décisions : deux sessions **bout à bout** ne fusionnent pas (l'arrondi à la
  minute reste par session, donc rien ne change sans chevauchement) ; seules les sessions d'une **même** tâche se
  rognent (deux tâches en même temps restent deux temps, la règle « un chrono à la fois » est celle du dépôt).
  Conséquence : 14 cas sur 200 des fichiers d'or des statistiques changent (`statistiques.md` § Décisions).

- **Demande d'autorisation au premier chrono** (demande du propriétaire du 2026-11-07) : le bouton facultatif de
  « Maintenant » ne suffisait pas. Retenu : un **dialogue d'explication** (Kairos dit pourquoi, puis le système demande),
  **une fois par lancement**, sans rien stocker. Écartés : la demande du système sans explication (sans contexte, et
  Android ne la montre que deux fois avant de la bloquer pour de bon, il ne faut pas la gaspiller) ; un bandeau non
  bloquant sous le chrono (plus discret, mais le même défaut que le bouton) ; une demande à chaque chrono (insistante) ou
  une fois pour toutes (un réglage de plus, à tracer dans l'export, pour un cas où le bouton reste de toute façon).
  Le dialogue n'attend pas la réponse du système : le chrono et ses alertes dans l'application ne dépendent pas de lui.
- **Cloches à chaque alerte, pas seulement en dernier recours** (issue #234, choix du propriétaire du 2026-11-09) : le son
  ne servait que si aucune notification ne sortait, ce qui n'arrive presque plus depuis #219. Il reste désactivé par
  défaut. Écartés : l'activer par défaut (un son inattendu à la mise à jour) ; garder le dernier recours en changeant
  seulement le timbre (ne répond pas au pomodoro).
- **Une cloche ou deux, selon l'alerte** : on reconnaît la pause et l'oubli sans regarder. Dépassement et arrêt automatique
  prennent la cloche simple (choix du propriétaire).
- **Sons générés par un script, pas enregistrés** : aucune licence de son à suivre (F-Droid), octets reproductibles, timbre
  réglable dans le script. Fichiers WAV plutôt qu'OGG : la bibliothèque standard de Python les écrit, Android, le JVM et les
  navigateurs les lisent sans décodeur ajouté ; ~110 Ko et ~140 Ko, accepté.
- **Sur Android, la cloche passe par le canal de notification**, pas par un lecteur lancé par l'application : c'est la seule
  voie qui sonne application fermée, et elle respecte les réglages du système (canal, « Ne pas déranger »). Le son d'un canal
  ne change plus après sa création : d'où deux canaux dédiés, à côté de `chrono-alerts` (sans cloche) que l'on garde quand
  l'interrupteur est coupé.
- **Une seule réglette, pas une par alerte** : l'issue demande un interrupteur ; les seuils à 0 coupent déjà une alerte.
- **Seuils calculés d'avance**, pas vérifiés par comparaison chaque
  seconde : les mêmes instants servent à la veille de l'application et aux
  alarmes Android.
- **Alarmes exactes quand elles sont accordées** (décision **rouverte** par le propriétaire le 2026-11-06, #219 ; elle
  remplace « alarmes inexactes, aucune autorisation d'alarme exacte » du jalon M3) : les alarmes inexactes arrivaient en
  retard, ou pas du tout selon le constructeur et l'économie d'énergie. `SCHEDULE_EXACT_ALARM` est déclarée ; sur
  Android 14 et plus elle est refusée par défaut et s'accorde dans les réglages du système (« Alarmes et rappels »), d'où
  le bouton de « Maintenant » ; sans elle, retour aux inexactes. Écartés : `USE_EXACT_ALARM` (réservée aux réveils et
  agendas, refusée hors de ce cas), `setAlarmClock` (une alarme exacte aussi, qui afficherait un réveil dans la barre
  d'état), un service au premier plan (une notification de plus et une permission de plus pour un chronomètre que le
  système dessine déjà). Une permission de plus dans l'APK, sans anti-fonctionnalité F-Droid (aucun réseau).
- **Rappels laissés aux alarmes inexactes** : #219 vise les alertes du chrono ; étendre les alarmes exactes aux rappels
  (`rappels.md`) est une piste notée.
- **Pas de service au premier plan** : le chronomètre de la notification est
  dessiné par le système ; rien ne tourne dans l'application.
- **Un seuil franchi pendant que le téléphone est éteint** n'est pas
  rattrapé au redémarrage (seuls les seuils à venir sont reprogrammés).
- **Temps du jour en date locale** : la date est celle du début de
  session dans le fuseau local, pas en UTC (même règle que « Fait » dans
  `vue-jour.md`).
- **Notification immédiate application ouverte, puis plus d'alarme pour ce seuil** (#219, remplace « quand le système
  notifie par alarme, la veille n'appelle pas `notify` ») : l'alarme inexacte arrivait après le bandeau, ou jamais. La
  veille poste maintenant elle-même (`notifyChrono`) et annule l'alarme du seuil ; une alarme déjà partie repose la même
  notification sans sonner (`setOnlyAlertOnce`, même identifiant). Une voie à part (`notifyChrono`) plutôt que changer
  `systemHandlesAlerts` : les rappels et l'avis des estimations notifieraient sinon deux fois (leurs alarmes et la veille).
- **Économie d'énergie : signaler, jamais demander** : la page de liste du système suffit (aucune permission
  `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, que F-Droid et Google surveillent) ; c'est l'utilisateur qui exempte Kairos.
- **`notify-send` d'abord sous Linux** : la bulle de l'icône du plateau d'AWT est une fenêtre Java sans le style du bureau,
  et GNOME (Wayland ou sans extension) n'a pas de zone de notification ; `notify-send` (libnotify) parle au service de
  notifications standard (`org.freedesktop.Notifications`). Une commande plutôt qu'un client D-Bus : aucune dépendance.
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
