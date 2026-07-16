# Suivi du temps réel, chrono & garde-fous d'affichage

_Rôle : mesurer et afficher le temps réellement passé sur les tâches (chrono
par tâche, agrégats jour/semaine/type), tenir un minuteur vivant côté client
avec alertes opt-in, et porter deux garde-fous d'affichage purement
informatifs (tâches qui traînent, surcharge de priorité maximale). Fichiers
couverts : `app/tasks_time.py` (agrégats purs depuis `WorkSession`),
`app/tasks_staleness.py` (détection des tâches qui traînent), la fonction
`initDayScripts` de `templates/kairos.html` (chrono vivant, titre d'onglet,
alertes, pont Android), et le pont natif
`android/app/src/main/java/com/skohscripts/kairos/KairosNotificationBridge.java`.
Le rail « réel » de la timeline (`session_timeline_entries`,
`app/tasks_scheduling.py`) et son rendu serveur sont décrits par
`ordonnancement.md` § 2.3 — ce document ne couvre que l'agrégation du temps et
le comportement vivant côté client, pas la projection sur la grille horaire.
Le dashboard `/kairos/stats` (calibration, biais d'estimation) est décrit par
`statistiques.md`, qui réutilise les fonctions de `tasks_time.py` sans les
redéfinir._

## 1. Besoin métier (cahier des charges)

### Objectif / problème

Kairos propose une estimation (`estimated_minutes`) mais ne mesurait, jusqu'à
la phase 3, jamais le temps **réellement** passé — impossible de comparer
l'un à l'autre ou de savoir où va le temps de la journée. Une fois le chrono
introduit, deux limites concrètes sont apparues à l'usage : (1) le badge
« temps travaillé aujourd'hui » de l'en-tête comptait en réalité **toutes**
les sessions jamais enregistrées, pas seulement celles du jour affiché — un
vrai bug, pas juste un manque ; (2) le chrono restait « muet » : il comptait
le temps sans jamais avertir d'un dépassement, d'un oubli, ou du besoin d'une
pause. Par ailleurs, deux angles morts distincts (mais rapprochés dans le
même chantier, phase 7) menaçaient la fiabilité du signal d'urgence : une
tâche en retard depuis 1 jour et une tâche en retard depuis 3 semaines
étaient traitées identiquement (même bucket), et rien n'empêchait
d'accumuler un grand nombre de tâches à priorité maximale, diluant le sens
même de cette priorité.

### Comportement attendu (utilisateur)

- Démarrer le chrono sur une tâche ferme automatiquement toute session encore
  ouverte ailleurs (au plus une session active à la fois) ; l'arrêter clôt la
  session. Le temps s'affiche en minuteur vivant, sans rechargement de page.
- Le badge « temps travaillé aujourd'hui » ne compte que les sessions dont le
  début tombe le jour affiché — jamais l'historique complet.
- Une ventilation par type de tâche accompagne ce total (jour et semaine).
- Le titre de l'onglet du navigateur affiche le temps qui tourne en direct,
  visible même onglet en arrière-plan.
- Un bouton opt-in permet d'activer des alertes de chrono : dépassement de
  l'estimé, chrono resté ouvert trop longtemps (« oublié »), rappel de pause
  après une session de focus continu prolongée. Chaque seuil ne notifie
  qu'une fois par franchissement, jamais en boucle, et jamais pour un seuil
  déjà dépassé au moment où la page se charge.
- Les notifications passent par le mécanisme le plus riche disponible (pont
  natif Android, puis notifications système du navigateur), avec un repli
  visuel dans la page qui joue **toujours**, même sans permission accordée.
- Le minuteur (et donc les alertes) s'affiche quelle que soit la section où
  vit la tâche en cours — y compris « Sans créneau aujourd'hui », pas
  seulement la liste planifiée.
- Une tâche en retard depuis longtemps, ou sans échéance non retouchée depuis
  longtemps, porte un badge « traîne depuis N j » distinct d'une tâche en
  retard depuis peu — sans jamais changer sa position dans le tri.
- Au-delà d'un certain nombre de tâches à priorité maximale simultanées, un
  bandeau prévient que le signal se dilue — purement informatif, jamais
  bloquant, jamais un tri automatique de rattrapage.

### Critères de succès

Repris et fusionnés des phases historiques (SPEC_KAIROS.md phases 3, 7, 11) :

- Une session chronométrée sur une tâche puis démarrée sur une autre ferme
  automatiquement la première (invariant « une session ouverte à la fois »).
- Le badge « temps travaillé aujourd'hui » ne compte plus que les sessions du
  jour affiché (vérifié par un test qui échouait avant le correctif).
- La vue semaine affiche une synthèse du temps réel par type de tâche pour la
  semaine, sans graphique.
- Une tâche en retard ou sans échéance non retouchée depuis longtemps affiche
  le badge « traîne depuis N jours », sans changer sa position dans le tri.
- Dépasser le seuil de tâches à priorité maximale affiche un bandeau ; en
  dessous, rien ne s'affiche.
- Les sessions chronométrées du jour apparaissent en rail « réel » sur la
  timeline (voir `ordonnancement.md`).
- Le titre de l'onglet affiche le temps qui tourne quand un chrono est actif.
- Un bouton opt-in demande la permission de notifier ; l'état affiché reflète
  actif/bloqué/indisponible selon le contexte (pont Android, contexte
  sécurisé navigateur, ou aucun des deux).
- Les trois alertes (dépassement, oubli, pomodoro) se déclenchent au
  franchissement, sans re-spam à chaque navigation, avec repli in-page si les
  notifications ne sont pas autorisées.
- Le minuteur (et les alertes) s'affiche aussi quand la tâche en cours est
  « sans créneau ».

### Hors périmètre / différé

- **Mode focus plein écran** : écarté explicitement à plusieurs reprises
  (phase 3, phase 11) — le pomodoro reste un simple rappel, pas un mode dédié.
- **Digest/rappel proactif au-delà des trois alertes de chrono** : l'outil
  reste 100 % pull (analyse post-phase-6) ; les alertes de chrono sont la
  seule exception, strictement liées à une session en cours, jamais un
  rappel déclenché en dehors d'un chrono actif.
- **Mémorisation automatique de durées par type de tâche** : explicitement
  différée au futur modèle ML sur l'historique `WorkSession` (analyse
  post-phase-6, confirmé phase 7) — ce module alimente cet historique mais ne
  calcule aucune suggestion lui-même (la suggestion de durée par type/palier
  Fibonacci affichée au panneau d'édition est calculée par
  `tasks_stats.calibration_by_type`/`fibonacci_calibration`, hors périmètre
  de ce document, voir `statistiques.md`).
- **Nudge de découpage des tâches à points Fibonacci élevés** : identifié en
  analyse post-phase-6, explicitement non retenu pour la phase 7.
- **Modification du tri/des buckets d'urgence par la staleness ou la
  surcharge** : les deux garde-fous de ce document sont **strictement des
  signaux d'affichage** — `days_stale` n'entre dans aucune clé de tri
  (`ordonnancement.md` reste seul maître de l'ordre), et le bandeau de
  surcharge ne retire ni ne réordonne aucune tâche.
- **Défaut de priorité automatique déduit de l'échéance à la création** :
  écarté explicitement en phase 7 — le garde-fou de surcharge avertit après
  coup, il ne calcule jamais de priorité à la place de l'utilisateur.
- **Bannière distincte pour la charge de la semaine** : écartée en phase 7 —
  le débordement du jour (`schedule.stats.overflow_minutes`,
  `ordonnancement.md`) suffit, pas de doublon.

## 2. Solution technique

### Vue d'ensemble

Trois couches indépendantes composent ce domaine :

1. **Agrégation pure** (`app/tasks_time.py`) — calculs de durées à partir de
   `WorkSession` déjà chargées en mémoire, aucune I/O.
2. **Détection pure** (`app/tasks_staleness.py`) — un seul signal
   (`days_stale`), aucune I/O, aucun accès à la base.
3. **Comportement vivant côté client** (`templates/kairos.html`,
   `initDayScripts`) — minuteur, titre d'onglet, alertes ; complété par un
   pont natif optionnel côté Android
   (`KairosNotificationBridge.java`/`window.KairosAndroid`).

Les routes d'ouverture/fermeture de session (`POST
/kairos/tasks/{id}/timer/start`, `.../timer/stop`, `app/main.py`) et le calcul
du garde-fou de surcharge (`count_max_priority_tasks`,
`app/tasks_scheduling.py`) ne vivent pas dans les fichiers couverts par ce
document, mais sont l'unique point d'écriture / la seule source du signal —
documentés ici pour leur rôle d'appelant.

### Détail par composant

#### `app/tasks_time.py` — agrégats de temps réel

Toutes les fonctions acceptent un paramètre `now: datetime | None = None`
(défaut `datetime.now(timezone.utc)`) pour rester testables avec une horloge
figée.

- **`_aware(dt)`** — normalise une datetime en UTC-aware ; les datetimes
  SQLite reviennent naïves (`tzinfo=None`), donc toute comparaison
  d'intervalle passe d'abord par cette fonction pour éviter une erreur de
  comparaison naïf/aware.
- **`session_minutes(session, *, now=None)`** — durée d'une session en
  minutes entières (`// 60`, jamais négative — `max(0, ...)`) ; une session
  encore ouverte (`ended_at is None`) court jusqu'à `now`.
- **`spent_minutes_by_task(sessions, *, now=None, tasks=None)`** — total par
  tâche : somme des `session_minutes` **plus**, si `tasks` est fourni, le
  temps saisi à la main (`Task.manual_time_spent_minutes`, issue #6). Les
  deux s'**additionnent**, jamais l'un ne remplace l'autre — le manuel comble
  ce que le chrono n'a pas mesuré (session oubliée en partie), il ne
  remplace jamais une mesure automatique existante.
- **`running_session(sessions)`** — la session ouverte (`ended_at is None`),
  ou `None` ; si plusieurs subsistaient (ne devrait jamais arriver, invariant
  d'unicité appliqué côté route), retourne la plus récemment démarrée.
- **`total_minutes(sessions, *, now=None)`** — somme brute, toutes tâches
  confondues, sur exactement les sessions fournies (le filtrage par période
  est à la charge de l'appelant, voir ci-dessous).
- **`sessions_in_range(sessions, start_day, end_day)`** — sessions dont le
  **début** tombe dans `[start_day, end_day]` bornes incluses. Docstring
  explicite (phase 7) : sert à corriger le calcul « temps travaillé
  aujourd'hui », qui additionnait auparavant toutes les sessions jamais
  enregistrées faute de filtrage par date en amont — **le filtrage se fait
  ici, pas dans `total_minutes`/`spent_minutes_by_task`**, volontairement
  laissées inchangées : elles reçoivent la liste déjà filtrée par l'appelant.
- **`sessions_on_day(sessions, day)`** — cas particulier de
  `sessions_in_range` à borne unique (`start_day == end_day == day`).
- **`spent_minutes_by_type(sessions, task_type_by_id, *, now=None)`** — même
  patron que `spent_minutes_by_task`, mais regroupé par `Task.task_type`. Une
  tâche sans type (`""`) ou absente de `task_type_by_id` (tâche supprimée
  entre-temps, session orpheline) tombe sous la clé `""`.

#### `app/tasks_staleness.py` — tâches qui traînent

Une seule fonction publique, **pure** :

```python
def days_stale(task, today, *, overdue_days, untouched_days) -> int | None:
```

Une tâche « traîne » si :
1. sa `deadline` **ou** sa `scheduled_date` est dépassée de plus de
   `overdue_days` jours — la **plus ancienne** des deux dates dépassées sert
   de référence (« c'est depuis ce moment-là que la tâche est actionnable »,
   docstring) ; **ou**
2. elle n'a **ni l'une ni l'autre** et n'a pas été modifiée
   (`Task.updated_at`) depuis plus de `untouched_days` jours.

Retourne le nombre de jours « de trop » (strictement supérieur au seuil —
pile au seuil ne compte pas encore, voir `test_deadline_exactly_at_threshold_
returns_none`), ou `None` sinon. Une tâche ayant une date (deadline ou
programmée) mais **non encore dépassée** n'est jamais rapportée « qui
traîne » via la branche « sans date », même si `updated_at` est ancien — les
deux branches sont mutuellement exclusives (`if task.deadline is None and
task.scheduled_date is None` en garde de la seconde).

Docstring de tête explicite le contrat central : **fonction pure, aucun accès
DB, purement un signal d'affichage supplémentaire** — ne modifie jamais
l'ordre de tri ni les buckets d'urgence de `app/tasks_scheduling.py` : une
tâche qui traîne depuis 1 jour et une qui traîne depuis 3 semaines partagent
le même bucket de tri, seule cette fonction distingue les deux à l'affichage.

#### Point d'entrée dans `app/main.py`

Dans `_render_kairos` :

- `stale_days_of = {t.id: days for t in tasks if (days := days_stale(t,
  target_day, overdue_days=settings.stale_overdue_days,
  untouched_days=settings.stale_untouched_days)) is not None}` — calculé
  **après** le blocage (commentaire explicite : signal d'affichage seul,
  calculé après coup) et consommé par la macro `task_meta`
  (`templates/_kairos_macros.html`) : `{% if stale_days %}<span class="badge
  warn">traîne depuis {{ stale_days }} j</span>{% endif %}`.
- `priority_overload_count = count_max_priority_tasks([t for t in tasks if
  t.id not in blocked_ids])` — **exclut les tâches bloquées** du décompte
  (`app/tasks_scheduling.py`, commentaire lignes 416-420) : une tâche bloquée
  ne peut de toute façon rien faire dans l'immédiat, donc ne doit pas diluer
  le signal de priorité du jour. `count_max_priority_tasks` (module
  `ordonnancement.md`) compte les tâches à priorité **strictement P0**, la
  même définition resserrée que le bucket d'urgence 1 (voir
  `ordonnancement.md` § décisions, point 2 — barème P0/P1/P2). Rendu par
  `_kairos_banners.html` : `{% if priority_overload_count >
  priority_overload_threshold %}` — bandeau strictement au-delà du seuil,
  jamais à l'égalité.
- Le suivi du temps réel : `sessions = list(tasks_session.scalars(select(
  WorkSession)))` (toutes, une fois par requête) ; `spent_by_task =
  spent_minutes_by_task(sessions, tasks=all_tasks)` ; `running =
  running_session(sessions)` ; `today_sessions = sessions_on_day(sessions,
  target_day)` ; `spent_total_str = _fmt_minutes(total_minutes(
  today_sessions))` — **c'est le filtrage préalable par `sessions_on_day` qui
  corrige le bug historique** (le total n'est calculé que sur les sessions du
  jour, jamais sur `sessions` brut). `spent_by_type_today =
  spent_minutes_by_type(today_sessions, task_type_by_id)`, filtré pour ne
  garder que les entrées non vides et non nulles.
- Vue semaine : `week_sessions = sessions_in_range(sessions, monday, sunday)`
  puis `spent_by_type_week = spent_minutes_by_type(week_sessions,
  task_type_by_id)`, même filtre — agrégat hebdomadaire ajouté phase 7 sans
  nouveau graphique, juste des totaux textuels par type.

#### Ouverture/fermeture de session (`app/main.py`)

```python
@app.post("/kairos/tasks/{task_id:int}/timer/start")
def start_timer(request):
    ...
    _stop_running_sessions(tasks_session)  # ferme toute session ouverte, ailleurs
    tasks_session.add(WorkSession(task_id=task_id))
```

`_stop_running_sessions` (lignes 1013-1019) ferme **toute** session encore
ouverte (`WHERE ended_at IS NULL`) avant d'en ouvrir une nouvelle — c'est ici,
côté route, que l'invariant « au plus une session ouverte à la fois »
(documenté dans `modele-donnees.md` comme appliqué au niveau applicatif, pas
par contrainte SQL) est réellement fait respecter. `stop_timer` clôt toute
session ouverte sur la tâche visée (`ended_at = now`) ; `toggle_task_done`
fait de même quand une tâche passant `done` avait un chrono actif (le chrono
ne continue jamais sur une tâche terminée).

#### Chrono vivant, titre d'onglet, alertes (`templates/kairos.html::initDayScripts`)

Fonction ré-appelable après chaque swap AJAX du fragment `#mj-day-content`
(commentaire de tête, lignes 70-75) — un `.mj-timer` recréé après un swap
doit réinitialiser son propre minuteur, sans accumuler d'intervalles
orphelins sur un DOM détaché (`root.__kairosTimerHandle`, `clearInterval`
avant réinjection, lignes 194-197, 302-304).

**Données côté serveur** consommées par le script :
- `.mj-timer` (`_kairos_macros.html::time_spent`) : `data-started` (ISO,
  posé uniquement si la tâche en cours a un chrono actif), `data-base`
  (minutes déjà accumulées avant cette session, incluant le temps manuel),
  `data-estimate` (`estimated_minutes` ou vide), `data-title`.
- `#mj-alert-config` (`_kairos_day.html`) : `data-idle` =
  `settings.timer_idle_alert_minutes`, `data-pomodoro` =
  `settings.pomodoro_focus_minutes`.

**Détection de capacité de notification** (ligne 93-94) :
```js
var android = window.KairosAndroid || null;
var canNotify = android ? true : (('Notification' in window) && window.isSecureContext);
```
Le pont Android, s'il existe (APK, `MainActivity#onCreate` l'injecte comme
`window.KairosAndroid`), prime toujours — `android.webkit.WebView`
n'implémente pas `window.Notification` nativement, donc sans ce pont les
alertes resteraient bloquées à « indisponibles » dans l'APK. Ailleurs
(navigateur desktop/mobile), `canNotify` exige à la fois l'existence de l'API
`Notification` et `window.isSecureContext` — **contexte sécurisé requis**
(HTTPS, ou `127.0.0.1`/`localhost`) : une notification demandée sur un accès
réseau local en HTTP simple (ex. IP LAN sans TLS) est détectée comme
indisponible, message explicite affiché plutôt qu'un échec silencieux
(« Notifications indisponibles ici (ouvrez via 127.0.0.1 pour les
activer). »).

**Bouton d'opt-in** (lignes 96-130) — trois branches mutuellement
exclusives :
1. **Pont Android présent** : `refreshAndroidStatus()` interroge
   `android.hasPermission()` à chaque rendu et après l'évènement DOM
   `kairos-android-permission-changed` (déclenché par
   `KairosNotificationBridge#notifyPermissionChanged`, appelé depuis
   `MainActivity#onRequestPermissionsResult` — la demande de permission
   Android est **asynchrone**, pas de valeur de retour exploitable
   directement par l'appel JS `requestPermission()`, d'où le rebouclage par
   évènement plutôt qu'une promesse).
2. **Pas de pont, contexte non sécurisé ou API absente** : message
   d'indisponibilité, bouton jamais affiché.
3. **Web Notifications standard** : bouton affiché si permission ni accordée
   ni refusée (`Notification.permission === "default"`), clic →
   `Notification.requestPermission()`.

**Anti-spam des alertes** (lignes 150-159, commentaire explicite) : au
chargement, `initSession`/`initTotal` sont calculés une fois pour déterminer
quels seuils sont **déjà franchis avant même que la page ne s'ouvre** — ces
seuils sont pré-marqués `fired[tag] = true` sans jamais déclencher
`alert(...)`. Seule une transition franchie **pendant que la page reste
ouverte** (dans `tick()`, appelé chaque seconde) déclenche réellement une
notification. Chaque type d'alerte (`over`, `idle`, `pomo`) a son propre
verrou `fired[tag]`, indépendant des deux autres — franchir le seuil de
dépassement ne bloque pas l'alerte d'oubli, et réciproquement.

**Trois déclencheurs** (fonction `tick`, lignes 176-191), chacun optionnel
(seuil à 0 = désactivé) :
- `over` — `estimate && total >= estimate` : dépassement de l'estimé.
- `idle` — `idleMin && sessionMin >= idleMin` : chrono resté ouvert depuis
  plus de `timer_idle_alert_minutes` (défaut 180) minutes **de la session en
  cours** (pas du cumulé) — « chrono oublié ».
- `pomo` — `pomoMin && sessionMin >= pomoMin` : focus continu de plus de
  `pomodoro_focus_minutes` (défaut 50) minutes de la session en cours —
  rappel de pause, réintroduit à la demande explicite de l'utilisateur en
  phase 11 après avoir été écarté en phase 3 ; reste un simple rappel, jamais
  un mode focus plein écran.

**Notification effective** (`alert(tag, body)`, lignes 161-170) : pont
Android (`android.notify(title, body, tag)`) en priorité, sinon
`new Notification(...)` si `canNotify && Notification.permission ===
"granted"`, **et dans tous les cas** `flashInPage(...)` — le repli visuel
(bandeau `.banner.warning` inséré en tête de `.page`) joue **systématiquement
en plus**, jamais en substitut conditionnel : rien n'est silencieux même sans
notification système autorisée.

**Titre d'onglet vivant** (ligne 149, 180-181) : `baseTitle` capture le titre
de base **une seule fois** au chargement, en retirant tout préfixe déjà posé
par une exécution précédente du minuteur (regex `/^\([0-9:]+\) .+ · /`) — un
swap AJAX qui réexécute `initDayScripts` ne doit jamais accumuler de
préfixes emboîtés. Chaque tick réécrit `document.title` avec le total formaté
`(H:MM) <titre tâche> · <titre de base>`.

#### `KairosNotificationBridge.java` — pont natif Android

Exposé en JavaScript sous `window.KairosAndroid`
(`MainActivity#onCreate`/`webView.addJavascriptInterface`). Quatre méthodes
`@JavascriptInterface` :

- **`canNotify()`** — retourne toujours `true` : la seule existence du pont
  (contrairement à `'Notification' in window`, absent de
  `android.webkit.WebView`) prouve la capacité. Non utilisée côté JS
  actuellement (`window.KairosAndroid` lui-même sert de test de présence),
  conservée pour un usage explicite futur.
- **`hasPermission()`** — `NotificationManager#areNotificationsEnabled()`,
  qui **unifie les deux régimes** de permission Android : avant l'API 33, pas
  de permission runtime (seul le réglage système « notifications activées »
  pour l'app compte) ; depuis l'API 33, ce même indicateur reflète aussi
  `POST_NOTIFICATIONS`. Une seule méthode plateforme couvre les deux
  régimes depuis `minSdk 24` — l'appelant JS n'a jamais besoin de distinguer.
- **`requestPermission()`** — déclenche la boîte de dialogue système
  seulement sur API 33+ (Tiramisu) ; en dessous, il n'y a rien à demander
  (le réglage système gère seul), donc rebouclage direct vers
  `notifyPermissionChanged()`. Toujours appelé depuis le bouton d'opt-in
  existant, **jamais au démarrage** de l'app (rappelé en commentaire de
  `AndroidManifest.xml`).
- **`notify(title, body, tag)`** — no-op silencieux si `!hasPermission()` ;
  sinon construit une `Notification` (canal `kairos-chrono-alerts`, créé une
  fois à l'instanciation du pont si API ≥ 26) avec un `PendingIntent` qui
  rouvre `MainActivity`, et `getManager().notify(tag, NOTIFICATION_ID,
  builder.build())` — **`tag` réutilisé comme clé de déduplication système**
  (même `tag` = notification remplacée, pas empilée), cohérent avec le
  `tag` par type d'alerte (`over`/`idle`/`pomo`) déjà envoyé côté JS.

`notifyPermissionChanged()` (package-private, appelée depuis
`MainActivity#onRequestPermissionsResult`) poste sur le thread UI de la
WebView (`webView.post(...)`, sûr depuis n'importe quel thread appelant, y
compris le worker thread du pont JS) et déclenche
`window.dispatchEvent(new Event('kairos-android-permission-changed'))` — seul
canal disponible pour qu'un callback natif **asynchrone** informe le JS,
faute de retour direct possible et d'AndroidX (contrainte transverse du
packaging Android, voir `docs/ANDROID_PACKAGING.md`).

### Décisions et pièges tracés

1. **Bug corrigé, pas une nouvelle fonctionnalité : filtrage par date déplacé
   en amont, pas dans les fonctions d'agrégation existantes.** Le
   commentaire de `sessions_in_range` est explicite : le bug historique
   (« temps travaillé aujourd'hui » gonflé par tout l'historique) se corrige
   en filtrant les `WorkSession` **avant** de les passer à `total_minutes`/
   `spent_minutes_by_task`, jamais en réécrivant ces deux fonctions —
   `total_minutes`/`spent_minutes_by_task` restent volontairement génériques
   (elles ignorent la notion de « jour »), la responsabilité de la fenêtre
   temporelle reste entièrement chez l'appelant.
2. **Temps manuel additionné, jamais substitué.** `manual_time_spent_minutes`
   comble ce qu'un chrono oublié n'a pas mesuré ; l'additionner plutôt que
   l'utiliser en repli évite de perdre la mesure automatique déjà collectée
   si l'utilisateur saisit un complément après coup.
3. **`days_stale` : seuil strict, pas « au moins ».** Testé explicitement
   (`test_deadline_exactly_at_threshold_returns_none`) : pile au seuil ne
   compte pas encore comme « qui traîne » — évite un badge qui apparaîtrait
   au jour près du seuil configuré, jugé prématuré.
4. **`days_stale` ne mélange jamais les deux branches** (dates dépassées vs
   sans date non modifiée) : une tâche avec une date non encore dépassée
   n'est jamais évaluée sur son `updated_at`, même ancien — la présence d'une
   date fixe la seule référence pertinente pour cette tâche.
5. **Staleness et surcharge de priorité : garde-fous d'affichage purs,
   jamais de rétroaction sur le tri.** Répété à deux niveaux (docstring de
   `tasks_staleness.py`, décision actée phase 7 point 5) : le principe
   central de ce chantier est de ne **jamais** faire dépendre l'algorithme
   d'ordonnancement (`ordonnancement.md`) d'un signal purement visuel — un
   changement de seuil de configuration (`stale_overdue_days`,
   `priority_overload_threshold`) ne doit jamais faire bouger l'ordre des
   tâches, seulement des badges/bandeaux.
6. **Garde-fou de surcharge exclut les tâches bloquées du décompte.**
   Décision tracée dans `app/main.py` (commentaire lignes 416-420) : sans
   cette exclusion, le bandeau se déclencherait pour des tâches P0 sur
   lesquelles l'utilisateur ne peut de toute façon rien faire dans
   l'immédiat (en attente d'un bloqueur), diluant la pertinence du bandeau
   lui-même plutôt que le signal de priorité qu'il est censé protéger.
7. **Anti-spam : seuils déjà franchis au chargement neutralisés d'emblée.**
   Commentaire explicite du script (lignes 152-154) : sans ce garde-fou,
   chaque navigation/rechargement de page sur une tâche déjà en dépassement
   redéclencherait une notification — l'app ne doit notifier qu'un
   franchissement réel, observé pendant que la page reste ouverte.
8. **Contexte sécurisé requis pour Web Notifications, détecté explicitement
   plutôt que de laisser échouer silencieusement.** `window.isSecureContext`
   est vérifié en plus de `'Notification' in window` ; un message dédié
   explique la contrainte (« ouvrez via 127.0.0.1 ») plutôt que de laisser le
   bouton disparaître sans explication — décision actée avec l'utilisateur en
   phase 11 après confirmation que l'accès réel se fait en
   `127.0.0.1` (contexte sécurisé de fait).
9. **Pont Android : `hasPermission()` unifie deux régimes de permission**
   plutôt que d'exposer deux méthodes distinctes selon la version d'API —
   commentaire explicite du code Java : simplifie l'appelant JS, qui n'a
   jamais besoin de savoir sur quelle version d'Android il tourne.
10. **`requestPermission()` Android jamais appelé au démarrage.** Rappelé à
    la fois dans le commentaire Java et `AndroidManifest.xml` : la demande de
    permission ne doit se déclencher que sur un geste utilisateur explicite
    (clic sur le bouton d'opt-in) — cohérent avec la contrainte de
    `Notification.requestPermission()` côté navigateur (« une permission ne
    s'obtient que sur geste utilisateur », commentaire de
    `SPEC_KAIROS.md` phase 11 repris ici).
11. **`tag` de notification réutilisé comme clé de déduplication système**
    (Android `NotificationManager#notify(tag, id, ...)` et `new
    Notification(..., { tag })` côté Web Notifications) : une alerte du même
    type qui se redéclencherait (ce que l'anti-spam empêche déjà côté JS)
    remplacerait la précédente plutôt que d'empiler des notifications
    redondantes — double filet, pas une fonctionnalité indépendante.
12. **Correctif d'ergonomie découvert au passage (phase 11) : `time_spent`
    appelé aussi dans la liste des tâches sans créneau.** Avant, le badge de
    chrono n'était rendu que dans la liste planifiée
    (`visible_scheduled`) — une tâche en cours reléguée « sans créneau »
    (soir, journée pleine) perdait visuellement son minuteur, et donc ses
    alertes (le script cherche `.mj-timer` n'importe où sous `root`, mais
    encore fallait-il que le template le rende). Le template
    `_kairos_day.html` appelle désormais `{{ time_spent(task) }}` dans les
    deux boucles (`visible_scheduled` et `visible_unscheduled`).

### Invariants et garde-fous

- **Au plus une `WorkSession` ouverte à la fois**, appliqué au niveau
  applicatif par `_stop_running_sessions` (appelé avant toute nouvelle
  ouverture) — aucune contrainte SQL ne l'impose (voir `modele-donnees.md`).
- **`tasks_time.py` et `tasks_staleness.py` sont des modules purs** : aucune
  session SQLAlchemy, aucun appel réseau, entièrement testables en isolation
  sur des `WorkSession`/`Task` en mémoire (`tests/test_tasks_time.py`,
  `tests/test_tasks_staleness.py`).
- **Aucun signal de ce document (staleness, surcharge, temps réel) n'entre
  jamais dans une clé de tri** — invariant partagé et répété avec
  `ordonnancement.md` : la seule influence de ce domaine sur le placement
  concerne le rail visuel « réel » de la timeline (hors périmètre, voir
  `ordonnancement.md`), jamais l'ordre des tâches lui-même.
- **Le repli in-page (`flashInPage`) joue toujours**, indépendamment de l'état
  de permission de notification — aucune alerte n'est jamais totalement
  silencieuse.
- **Un seuil déjà franchi au chargement de la page ne notifie jamais** — seule
  une transition observée page ouverte déclenche une alerte.
- **Le minuteur ne survit jamais à un swap AJAX sans être explicitement
  réinitialisé** : `root.__kairosTimerHandle` est nettoyé (`clearInterval`)
  avant toute réinjection de `#mj-day-content`, pour ne jamais laisser un
  intervalle orphelin écrire sur un nœud DOM détaché.
- **Contexte sécurisé obligatoire pour les Web Notifications** (hors pont
  Android, qui n'y est pas soumis) : `window.isSecureContext` conditionne
  strictement l'affichage du bouton d'opt-in navigateur.
