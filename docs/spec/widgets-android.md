# Widgets Android (écran d'accueil)

_Rôle : deux widgets pour l'écran d'accueil du téléphone, « Maintenant » et « Journée », qui montrent le plan du jour sans
ouvrir Kairos et permettent de terminer une tâche, de lancer ou d'arrêter le chrono, et de capturer. Fichiers couverts (3.12.0) :
`kmp/core/.../core/widgets/WidgetPlan.kt` (calcul pur), `kmp/ui/.../widgets/WidgetTexts.kt` (textes rédigés, comme
`reminders/ReminderTexts.kt`), côté Android `androidApp/.../widgets/` (`WidgetSync.kt`, `WidgetProviders.kt`,
`DayWidgetService.kt`, `WidgetActionReceiver.kt`, `WidgetColors.kt`), `KairosProcess.kt` et `KairosApplication.kt` (démarrage,
changement de configuration), `MainActivity.kt` (destination demandée par un toucher), `androidApp/src/main/res/layout/widget_*.xml`,
`res/xml/widget_*_info.xml`, `res/drawable/widget_*.xml` et `ic_widget_*.xml` (générées par `kmp/tools/make_icons.py`),
`res/values*/strings.xml`, `AndroidManifest.xml`, `ui/KairosApp.kt` (destination externe), `ui/guide/FeatureCatalog.kt`
(carte `widgets`). Tests : `core/.../widgets/WidgetPlanTest.kt`, `ui/.../AndroidStringsParityTest.kt` (textes natifs),
`StringsParityTest` (textes Compose)._

État : demandés dans l'issue #150, spécifiés le 2026-10-26 avec le propriétaire, **implémentés en 3.12.0**. La vérification à
la main sur un téléphone (critères de succès, dernier point) reste à faire par le propriétaire sur l'APK de la CI.

## 1. Besoin métier (cahier des charges)

### Objectif / problème

Kairos ne vaut que si on l'ouvre : la journée est parfois à moitié passée quand on le fait, et l'icône se perd parmi les
autres. Le plan du jour doit **rester sous les yeux** : un widget sur l'écran d'accueil « fait signe » le matin et rappelle,
dans la journée, ce qu'il y a à faire maintenant. Il donne aussi les gestes qui ne demandent pas l'application : terminer la
tâche, lancer ou arrêter le chrono, capturer une idée. Android seulement (le bureau et le web n'ont pas d'écran d'accueil).

### Comportement attendu (utilisateur)

- Deux widgets au choix dans la liste des widgets du téléphone, **« Kairos : Maintenant »** et **« Kairos : Journée »**, qu'on
  peut placer chacun autant de fois qu'on veut, **redimensionner** librement, et qui s'adaptent à leur taille.
- **Les mêmes tâches que la vue Jour de l'espace Perso** : les tâches perso et, si l'espace Équipe est activé, les tâches
  d'équipe qui me sont assignées (`Workspaces.personalView`). Les tâches d'un collègue n'y sont jamais.
- **Les mêmes couleurs que l'application** : la couleur du thème choisie (l’océan par défaut), claire ou sombre comme
  l'application la montre (réglage « Thème sombre » compris). Les titres s'affichent toujours, sans réglage de masquage (choix du
  propriétaire : comme les notifications du chrono).
- **Un toucher sur le corps d'un widget ouvre la vue Jour** de Kairos (application déjà ouverte comprise : elle passe à la
  vue Jour).

#### Widget « Maintenant »

- Il montre la **carte « Maintenant » de la vue Jour** (`DayView.nextUp`) : titre, heure de début et de fin prévues
  (« 10:30–11:15 », la fin seule si elle est connue ; « sans créneau » quand la tâche n'est pas placée), pastille de priorité
  (P0 en rouge, P1, P2), tag de projet, type, marque « deep work ».
- Si un **chrono tourne**, c'est cette tâche qu'il montre, avec le **temps écoulé en direct** et l'estimation (« 12:34 / 45 min »).
- Selon la place : la tâche suivante du plan sur une ligne (« Ensuite 11:30 · Relire le contrat »), et un **badge « À traiter »**
  avec le nombre de tâches non qualifiées (touché, il ouvre la vue Jour).
- Trois boutons : **« Fait »** (terminer la tâche affichée), **chrono** (lancer, ou arrêter si c'est celui qui tourne) et **« + »**
  (capturer : ouvre Kairos sur la capture rapide, l'écran Notes).
- États vides : **« Rien de prévu aujourd'hui »** (aucune tâche à faire) avec le « + » ; **« Tout est fait pour aujourd'hui »**
  quand des tâches ont été terminées aujourd'hui et qu'il n'en reste aucune.
- Petite taille (une seule ligne de haut) : titre, heure et boutons ; les badges et la ligne « Ensuite » apparaissent quand il y a la place.

#### Widget « Journée »

- Une **liste défilante** du jour, dans l'ordre de l'heure, qui reprend la vue Jour :
  - en tête, les tâches **à échéance aujourd'hui ou en retard** qui n'ont pas de créneau, marquées « échéance » (retard en rouge) ;
  - puis, à leur heure, les **tâches placées par le plan** et les **créneaux occupés** (réunions importées comprises ; le deep work
    en tertiaire) ;
  - les **tâches déjà faites aujourd'hui, barrées**, à leur heure ; et **tout le passé de la journée**, grisé (pas de défilement automatique
    qui le cache : la liste s'ouvre à l'heure courante).
- La ligne en cours (celle de « Maintenant ») est mise en avant ; une ligne montre l'heure, le titre, la pastille de priorité.
- En-tête : la date du jour (« mardi 27 oct. ») et le décompte « 3 / 7 faites » ; un bouton **« + »** de capture.
- Un toucher sur une ligne ou l'en-tête ouvre la vue Jour. Pas d'action sur les lignes (terminer une tâche se fait dans le widget
  « Maintenant » ou dans l'application).
- Jour vide : « Rien de prévu aujourd'hui », avec le « + ».

#### Actions

- **« Fait » demande une confirmation par double toucher** (protège d'un toucher accidentel sur l'écran d'accueil) : le premier
  toucher arme le bouton, qui devient **« Confirmer ? »** pendant 5 secondes ; le second termine la tâche. Sans second toucher, le
  bouton redevient « Fait ». Terminer une tâche d'équipe assignée fait ce que fait la case de la vue Jour (avancement à 100 %,
  journal ; `equipe-backlog-suivi.md`). La tâche terminée par erreur se rouvre depuis « Fait » de la vue Jour.
- **Chrono** : lance (ou arrête) exactement le chrono de l'application (une seule session ouverte à la fois ; la notification
  permanente du chrono apparaît comme d'habitude, `temps-reel-chrono.md`).
- Aucune de ces actions n'ouvre l'application. Aucune ne fait autre chose que ce qui est écrit : il n'y a pas de « Reporter »
  (choix du propriétaire).

### Critères de succès

- Le widget « Maintenant » montre la tâche de `DayView.nextUp` ; avec un chrono ouvert, celle du chrono ; un jour sans tâche, le
  message vide ; un jour tout fait, « Tout est fait » (`WidgetPlanTest`).
- Le widget « Journée » donne les mêmes tâches placées, dans le même ordre, que l'agenda de la vue Jour, plus les créneaux, les
  échéances en tête et les tâches faites barrées (`WidgetPlanTest`).
- Une tâche d'équipe d'un collègue n'apparaît jamais ; une tâche d'équipe assignée à « moi » apparaît (`WidgetPlanTest` sur
  `personalView`, `TeamIsolationTest` inchangé).
- Le premier toucher sur « Fait » arme, le second dans les 5 s termine, passé ce délai la tâche reste à faire (`WidgetPlanTest.confirmation`).
- Terminer une tâche déjà terminée (second broadcast, widget périmé) ne la rouvre pas.
- Après un changement des données, de l'heure d'une tâche, de la couleur du thème ou de la langue, les widgets sont à jour en
  moins d'une minute, sans réveil périodique du téléphone.
- L'APK n'ajoute **aucune permission** ni **aucune dépendance** (manifeste : trois `receiver` — les deux fournisseurs et le récepteur d'actions privé — et un `service` de plus, `distribution.md`).
- Vérifié à la main sur un téléphone (ce que la CI ne voit pas) : placement des deux widgets, redimensionnement, thème sombre,
  double toucher, chrono en direct, ouverture de la vue Jour, redémarrage du téléphone.

### Hors périmètre

- Widgets du bureau et du web ; widget d'écran de verrouillage ; widget de l'espace Équipe (aucune donnée d'équipe sur l'écran d'accueil).
- Écran de configuration à l'ajout (taille et contenu viennent de la taille du widget), choix de la couleur propre au widget,
  masquage des titres.
- Actions sur les lignes de « Journée », « Reporter », saisie d'un titre dans le widget.
- Jetpack Glance (`androidx.glance` : une dépendance de plus et un graphe de build à faire reproduire), voir § Décisions.

## 2. Solution technique

### Calcul pur (`core/widgets/WidgetPlan.kt`)

Aucune horloge, aucune E/S : l'heure et le fuseau sont des paramètres (`core` reste pur, `architecture.md`). Le jour est celui de
l'heure passée.

- `WidgetPlan.now(snapshot, now, timeZone): NowWidget` ; `WidgetPlan.day(snapshot, now, timeZone): DayWidget`. `snapshot` est
  **`personalSnapshot`** (la vue Perso : le seul filtre qui protège le mode solo, `equipe.md`) ; le calcul s'appuie sur
  `DayView.build(snapshot, jour, now, fuseau)`, jamais sur un second moteur de plan.
- `NowWidget(state, task, next, inbox, runningSince, spentBeforeMinutes)` avec `state` = `RUNNING` (une session ouverte : la tâche du
  chrono ; `runningSince` = début de la session, `spentBeforeMinutes` = temps déjà passé sur la tâche avant elle, `DayView.runningBaseMinutes`),
  `UPCOMING` (`nextUp`), `NOTHING_PLANNED`, `ALL_DONE` (`doneToday` non vide, plus rien à faire). `task` et `next` sont des
  `WidgetTask(id, title, start, end, priority, project, type, deepWork, estimateMinutes, pinned)` : `start`/`end` nuls pour une tâche
  sans créneau ; `estimateMinutes` = estimation explicite, sinon durée apprise fiable (comme la carte « En ce moment »). `next` = la
  première tâche placée, puis sans créneau, qui n'est pas celle affichée. `inbox` = `DayView.inbox.size`.
- `DayWidget(day, rows, doneCount, total)` ; `DayRow(kind, id, title, start, end, priority, state, overdue)` avec `kind` = `DEADLINE`,
  `TASK`, `BUSY`, `DEEPWORK`, et `state` = `PAST`, `CURRENT`, `UPCOMING`, `DONE`.
  - `DEADLINE` : tâche à faire, échéance aujourd'hui ou passée (`overdue`), ni placée ni bloquée ; en tête, par échéance puis titre.
  - `TASK` : chaque entrée de `schedule.scheduled` (`CURRENT` pour la tâche du chrono, sinon `nextUp` ; `PAST` si finie avant
    maintenant) ; et chaque tâche de `doneToday`, `DONE`, à l'heure où elle a été terminée (`updatedAt`).
  - `BUSY`/`DEEPWORK` : chaque créneau effectif du jour (`dayBlocks`), état selon l'heure.
  - Après les échéances, tri par heure de début (une tâche avant un créneau à la même heure, puis le titre).
  - `doneCount` = tâches faites aujourd'hui ; `total` = faites + placées + échéances en tête (« 3 / 7 faites »).
- `WidgetPlan.nextChange(snapshot, now, timeZone): LocalDateTime` : la prochaine heure, après maintenant, où le contenu change tout seul
  (début ou fin d'une tâche placée ou d'un créneau du jour), sinon minuit. Sert à poser l'alarme de rafraîchissement.
- `WidgetPlan.confirm(armedAt, now): ConfirmState` : `ARMED` pendant `CONFIRM_SECONDS` (5 s) après le premier toucher, sinon `IDLE`
  (une horloge reculée ne réarme jamais).
- `WidgetPlan.canComplete(snapshot, taskId)` : vrai seulement pour une tâche à faire de la vue Perso.

### Rendu et synchronisation (`androidApp/.../widgets/`)

- **`RemoteViews` du framework**, pas de Glance : aucune dépendance de plus (`libs.versions.toml` inchangé, build reproductible,
  F-Droid ; `architecture.md`). Seules des vues acceptées dès l'API 26 (`FrameLayout`, `LinearLayout`, `ImageView`, `TextView`,
  `Chronometer`, `ListView`). `resizeMode="horizontal|vertical"`, `updatePeriodMillis="0"` ; sur Android 12 et plus,
  `targetCellWidth/Height`, `description` et `previewLayout` (la disposition elle-même) améliorent le sélecteur.
- **« Maintenant » a deux dispositions** : `widget_now.xml` (standard : badges, « À traiter », titre sur deux lignes, heure ou chrono,
  « Ensuite », boutons en bas) et `widget_now_compact.xml` (une rangée : titre, heure, boutons ; « Fait » sans texte tant qu'il n'est pas
  armé). La compacte est prise quand `OPTION_APPWIDGET_MIN_HEIGHT` est sous 100 dp (une seule ligne de cases), relue à chaque dessin
  (`onAppWidgetOptionsChanged` redessine). Mêmes identifiants dans les deux.
- **`WidgetSync.start(context, services, scope)`**, lancé par `KairosProcess` avec `ChronoSync` et `ReminderSync` : collecte
  `personalSnapshot` ; à chaque valeur, `refresh` (sous un `Mutex`) recalcule `NowWidget` et `DayWidget` à l'heure de `services.clock`
  et **ne redessine que si la clé change** (contenu calculé, couleurs, langue, noms des niveaux, bouton armé, instances placées) : les
  écritures sans effet sur le plan ne réveillent pas le lanceur. **Sans widget placé, rien n'est calculé** et l'alarme est retirée.
- **Langue** : `refresh` pose la langue résolue (`UiLanguage.resolve` du réglage et de la langue du système) par `applyLanguage`
  avant de rédiger : un processus ouvert par une alarme ou par le lanceur n'a pas d'activité qui l'aurait posée.
- **Aux frontières** : une alarme **`set(RTC)` non réveillante et inexacte** (sans permission) à `WidgetPlan.nextChange` ; à son
  déclenchement (`WidgetActionReceiver`, action `WIDGET_TICK`) le contenu est recalculé et l'alarme suivante posée. Un seul code de
  requête (3 000), remplacé à chaque calcul. Écran éteint, le widget n'est pas vu : l'alarme part au réveil suivant.
- **Mode nuit du système ou configuration** : `KairosApplication.onConfigurationChanged` relance `refresh` (sans forcer) si le processus a
  ouvert la base ; sinon le prochain dessin (frontière, changement, `onUpdate`) prend les nouvelles couleurs.
- **Chrono en direct** sans réveil : la vue `Chronometer` du widget (`setChronometer`, base = `elapsedRealtime` − temps écoulé, temps
  passé avant la session compris) compte seule dans le processus du lanceur, suivie de « / 45 min » si une estimation est connue.
- **« Journée »** : `ListView` alimentée par `DayWidgetService` (`RemoteViewsService`), une adresse d'adaptateur par instance
  (`kairos://widget/day/<id>`). `refresh` rédige et colore les lignes une fois (`WidgetSync.dayRows`), puis `notifyAppWidgetViewDataChanged` ;
  la fabrique les lit, et refait le calcul (bloquant, sur le fil de liaison) si le processus vient de démarrer. Ouverture de la liste :
  sur la ligne en cours, ou **en haut quand il y a des échéances sans créneau** (elles priment sur le passé).
- **Redémarrage ou mise à jour de l'application** : le système appelle `onUpdate` des fournisseurs (`KairosWidgetProvider`, base commune
  de `NowWidgetProvider` et `DayWidgetProvider`) : base ouverte par `KairosProcess`, puis `refresh` forcé. `BootReceiver` ouvre aussi la base.
- **Base qui ne s'ouvre pas** : `renderUnavailable` dessine « Ouvrir Kairos » (disposition compacte, sans boutons) sur chaque widget ;
  un toucher ouvre l'application, qui affiche l'erreur. Jamais d'exception dans le lanceur.

### Actions (`WidgetActionReceiver`)

- Des `PendingIntent.getBroadcast` **immuables** (`FLAG_IMMUTABLE`), composant explicite (`exported=false`), adresse
  `kairos://widget/<action>/<tâche>` (une intention par tâche et par action) : `WIDGET_DONE` et `WIDGET_TIMER` (extra `taskId`),
  `WIDGET_TICK`, `WIDGET_EXPIRE`. Le récepteur utilise `goAsync()` et `KairosProcess.scope`, comme `ChronoReceiver`.
- **`WIDGET_DONE`** : premier toucher (non armé, ou armé sur une autre tâche) : `armed = (taskId, maintenant)`, redessin avec
  « Confirmer ? » ; désarmement 5 s plus tard par une coroutine du processus, et par une alarme `WIDGET_EXPIRE` (`set(RTC)`, code 3 001) si le
  processus meurt avant. Second toucher dans les 5 s : `repository.toggleDone(taskId)` **seulement si `canComplete`** (un broadcast tardif ne
  rouvre jamais une tâche faite). L'état armé est en mémoire du processus : s'il meurt, le toucher suivant est un premier toucher.
- **`WIDGET_TIMER`** : `repository.stopTimer()` si la session ouverte est sur cette tâche, sinon `repository.startTimer(taskId)` (qui ferme
  l'autre session et refuse une tâche qui n'est pas à faire).
- **Toucher sur le corps, « À traiter », l'en-tête, une ligne ou « + »** : un `PendingIntent.getActivity` vers `MainActivity` avec l'extra
  `com.skohscripts.kairos.DESTINATION` (`DAY`, ou `NOTES` pour « + »), `FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_SINGLE_TOP`. Les lignes de
  « Journée » passent par un gabarit (`setPendingIntentTemplate`) **modifiable** (`FLAG_MUTABLE`, exigé pour qu'une ligne y ajoute son
  intention ; composant explicite) et une intention de ligne vide.
- **Destination externe** : `MainActivity` (`singleTop`) lit l'extra à `onCreate` (`KairosApp(initialDestination = …)`, l'Accueil sinon) et à
  **`onNewIntent`**, où elle le publie dans `externalDestination` ; `KairosApp(externalDestination, onExternalDestinationHandled)` le consomme
  dans la coquille : retour dans l'espace Perso (les widgets en montrent les tâches), aujourd'hui, rien d'ouvert par-dessus, puis la
  destination demandée, sans recréer l'activité.

### Thème (`WidgetColors.kt`)

- Les couleurs viennent de **`ThemeColors.schemeFor`** (le même algorithme que l'application, `apparence.md`) à partir de
  `settings.themeColor`, en clair ou sombre selon `ThemeMode.isDark(themeMode, mode nuit du système)` ; jamais les couleurs du fond
  d'écran (« Couleurs du système » donne la graine par défaut ; le réglage reste propre à l'application). Les fonds et icônes sont des
  formes blanches (`widget_bg.xml` 16 dp, `widget_row_bg.xml` 12 dp, `widget_pill.xml`, `ic_widget_*.xml`) teintées par
  `setInt(…, "setColorFilter", couleur)` ; les textes par `setTextColor` (valables dès l'API 26).
- « Maintenant » : fond `primaryContainer`, textes `onPrimaryContainer` ; « Fait » en pilule `primary`/`onPrimary` ; chrono et « + » en
  pastilles `surface`/`onSurface`. Badges sur une ligne (« P0 · projet · type · deep work ») : priorité en gras (le nom choisi, sinon
  « P0 », `planning-personnalise.md` § Lot D), P0 en `error`, deep work en `tertiary`.
- « Journée » : fond `surface` ; « + » en pastille `surfaceContainerHigh` ; ligne en cours sur `primaryContainer` ; P0 et échéance
  dépassée en `error`, deep work en `tertiary`, créneau occupé et tâche faite en `onSurfaceVariant` (la faite barrée,
  `StrikethroughSpan`), le passé à 60 % d'opacité. Cibles de 48 dp, aucune ombre ni dégradé (`DESIGN_SYSTEM.md`).
- Les icônes (`check`, `play_arrow`, `stop`, `add`) sont générées par `make_icons.py` (`WIDGET_ICONS`) depuis les mêmes Material
  Symbols que `KairosIcons` : `RemoteViews` n'affiche pas d'`ImageVector`.

### Textes

- Les textes rédigés par le code (« Rien de prévu aujourd'hui », « Tout est fait pour aujourd'hui », « Confirmer ? », « Ensuite 11h30 ·
  … », « À traiter : 3 », « 3 / 7 faites », « sans créneau », « échéance », « Ouvrir Kairos », « / 45 min », heure « 10h30–11h15 ») sont des
  **chaînes des ressources Compose** (`widget_*`), dans les six langues ; « Fait », chrono, « Capturer » et « deep work » reprennent les
  chaînes de l'application. `WidgetTexts` les lit (`getString`, suspendu) ; heures et date (« mardi 27 oct. ») par `Dates`, comme dans
  l'application. `StringsParityTest` les couvre.
- Le **nom et la description** des widgets dans le sélecteur du système ne peuvent venir que de ressources Android :
  `androidApp/src/main/res/values{,-fr,-es,-de,-ru,-it}/strings.xml` (4 chaînes, apostrophe `’`) ; `AndroidStringsParityTest` (module
  `ui`, qui lit ces fichiers sans SDK Android) compare leurs clés et refuse une valeur vide ou une apostrophe droite.
- La carte `widgets` du catalogue (chapitre « Suivre », depuis 3.12.0, vers « Maintenant » de la vue Jour) et la ligne « Home screen
  widgets » du README la présentent.

### Décisions

- **`RemoteViews`, pas Glance** : Glance (`androidx.glance`) simplifie la mise en page mais ajoute une dépendance (versions figées, graphe
  de build reproductible) pour un rendu que `RemoteViews` fait déjà ; `core` calcule, `androidApp` ne fait que dessiner.
- **Deux widgets distincts** plutôt qu'un seul à plusieurs tailles : ce sont deux usages (le geste du moment, la vue d'ensemble) et chacun
  se place et se redimensionne à sa façon.
- **Liste, pas frise graphique** pour « Journée » (choix du propriétaire) : `RemoteViews` ne dessine pas de frise proportionnelle sans bitmap ;
  une liste ordonnée par heure reste lisible à toute taille et défile.
- **Double toucher sur « Fait »** (choix du propriétaire) plutôt qu'annulation après coup : un écran d'accueil se touche sans le vouloir.
- **Rafraîchissement sur changement et aux frontières**, jamais à la minute : l'alarme inexacte et `Chronometer` évitent le réveil
  périodique ; écarté : `updatePeriodMillis` (30 min au mieux, contenu périmé) et une alarme chaque minute (batterie). Alarme **non
  réveillante** (`RTC`) plutôt que `setAndAllowWhileIdle` : un widget ne se voit qu'écran allumé.
- **Une seule source de vérité du plan** : `DayView.build` ; le widget n'a ni moteur ni règle propre, donc une règle de la vue Jour
  (décaler, bloquer, épingler) s'y retrouve sans rien changer.
- **Aucun masquage des titres** : même exposition que les notifications existantes ; un réglage sera ajouté si les retours le demandent.
- **Échéances d'abord à l'ouverture de « Journée »** : avec une échéance sans créneau, la liste s'ouvre en haut plutôt que sur la ligne en
  cours, sinon l'échéance serait au-dessus du passé, hors de vue.
- **Un toucher ramène dans l'espace Perso** : les widgets n'en montrent que les tâches ; ouvrir la vue Jour de l'espace Équipe tromperait.

### Alternatives écartées

- Widget unique « Maintenant » avec bascule : demande un état et un geste de plus.
- Écran de configuration à l'ajout (contenu, couleurs) : plus de code, de textes et de tests ; la taille suffit à choisir le niveau de détail.
- Actions sur chaque ligne de « Journée » : la cible tactile des lignes se ferait avec le défilement ; les gestes restent dans « Maintenant ».
- Attente du second toucher par alarme seule : une alarme inexacte peut partir bien après 5 s ; la coroutine du processus désarme à l'heure,
  l'alarme ne sert que si le processus meurt.
