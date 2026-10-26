# Widgets Android (écran d'accueil)

_Rôle : deux widgets pour l'écran d'accueil du téléphone, « Maintenant » et « Journée », qui montrent le plan du jour sans
ouvrir Kairos et permettent de terminer une tâche, de lancer ou d'arrêter le chrono, et de capturer. Fichiers couverts (3.12.0) :
`kmp/core/.../core/widgets/WidgetPlan.kt` (calcul pur), `kmp/ui/.../widgets/WidgetTexts.kt` (textes rédigés, comme
`reminders/ReminderTexts.kt`), côté Android `androidApp/.../widgets/` (`WidgetSync.kt`, `NowWidgetProvider.kt`,
`DayWidgetProvider.kt`, `DayWidgetService.kt`, `WidgetActionReceiver.kt`, `WidgetColors.kt`), `androidApp/src/main/res/layout/`,
`res/xml/` (descriptions des deux widgets), `AndroidManifest.xml`, `MainActivity.kt` (destination demandée par un toucher),
`ui/KairosApp.kt` (destination externe), `ui/guide/FeatureCatalog.kt` (carte `widgets`). Tests : `core/.../widgets/WidgetPlanTest.kt`,
`LanguageParityTest` des textes natifs (voir § Textes)._

État : demandés dans l'issue #150 (3.12.0), spécifiés le 2026-10-26 avec le propriétaire, **à implémenter**.

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
- **Les mêmes couleurs que l'application** : la couleur du thème choisie (le miel par défaut), claire ou sombre comme
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
- L'APK n'ajoute **aucune permission** ni **aucune dépendance** (manifeste : deux `receiver`/un `service` de plus, `distribution.md`).
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

Aucune horloge, aucune E/S : le jour, l'heure et le fuseau sont des paramètres (`core` reste pur, `architecture.md`).

- `WidgetPlan.now(snapshot, now, timeZone): NowWidget` ; `WidgetPlan.day(snapshot, now, timeZone): DayWidget`. `snapshot` est
  **`personalSnapshot`** (la vue Perso : le seul filtre qui protège le mode solo, `equipe.md`) ; le calcul s'appuie sur
  `DayView.build(snapshot, jour, now, fuseau)`, jamais sur un second moteur de plan.
- `NowWidget(state, task, next, inbox)` avec `state` = `RUNNING` (une session ouverte : la tâche du chrono, `startedAt`),
  `UPCOMING` (`nextUp`), `NOTHING_PLANNED`, `ALL_DONE` (`doneToday` non vide, plus rien à faire) ; `task` et `next`
  sont des `WidgetTask(id, title, start, end, priority, project, type, deepWork, estimateMinutes, pinned)` ; `inbox` = `DayView.inbox.size`.
- `DayWidget(day, rows, doneCount, total)` ; `DayRow(kind, id, title, start, end, priority, state)` avec `kind` = `DEADLINE`, `TASK`,
  `BUSY`, `DEEPWORK`, et `state` = `PAST`, `CURRENT`, `UPCOMING`, `DONE`. Tri : échéances d'abord (retard avant jour même), puis par
  heure de début.
- `WidgetPlan.nextChange(snapshot, now, timeZone): LocalDateTime?` : la prochaine heure où le contenu change tout seul (début ou fin du
  prochain élément du plan, minuit). Sert à poser l'alarme de rafraîchissement.
- `WidgetPlan.confirm(armedAt, now): ConfirmState` : `ARMED` pendant 5 s après le premier toucher, sinon `IDLE`.

### Rendu et synchronisation (`androidApp/.../widgets/`)

- **`RemoteViews` du framework**, pas de Glance : aucune dépendance de plus (`libs.versions.toml` inchangé, build reproductible,
  F-Droid ; `architecture.md`). `minSdk` 26 : les tailles lues dans `AppWidgetManager.getAppWidgetOptions` (`OPTION_APPWIDGET_MIN_*`) à
  `onAppWidgetOptionsChanged` choisissent entre deux dispositions (compacte, standard) ; sur Android 12 et plus, `targetCellWidth/Height`
  et `previewLayout` améliorent le sélecteur, `resizeMode="horizontal|vertical"` partout.
- **`WidgetSync.start(context, repository, scope)`**, lancé comme `ReminderSync` : collecte `personalSnapshot` ; à chaque changement,
  recalcule `NowWidget` et `DayWidget` ; **ne redessine que si la valeur calculée diffère** de la dernière (les écritures sans effet
  sur le plan, comme la suite des sessions du chrono, ne réveillent pas le widget). Redessine aussi à un changement de la couleur du
  thème, du mode sombre ou de la langue (ils sont dans le snapshot ou dans `Configuration`).
- **Aux frontières** : une alarme `setAndAllowWhileIdle` (inexacte, **sans permission**, comme `ReminderSync`) à `WidgetPlan.nextChange` ; à son
  déclenchement (`WidgetActionReceiver`, action `WIDGET_TICK`) le contenu est recalculé et l'alarme suivante posée. Un seul code de
  requête, remplacé à chaque calcul (rien à mémoriser).
- **Chrono en direct** sans réveil : la vue `Chronometer` du widget (`setChronometer`, base = maintenant − temps écoulé) compte seule
  dans le processus du lanceur ; pas de mise à jour à la minute.
- **« Journée »** : `ListView` alimentée par un `RemoteViewsService` (`DayWidgetService`) qui lit le dernier `DayWidget` calculé
  (mémoire du processus, recalculé depuis le dépôt si le processus vient de démarrer). Chaque ligne porte un intent de remplissage
  vers la vue Jour ; `notifyAppWidgetViewDataChanged` après chaque recalcul.
- **Redémarrage ou mise à jour de l'application** : `BootReceiver` ouvre les services, `WidgetSync` redémarre, les widgets existants sont
  redessinés (`onUpdate` du fournisseur lit le dernier calcul ou le refait).
- **Aucune base ouverte, aucune donnée** : si la base ne s'ouvre pas, les widgets montrent « Ouvrir Kairos » (toucher = ouvrir l'application),
  jamais une exception (`KairosCrash` fait le reste à l'ouverture).

### Actions (`WidgetActionReceiver`)

- Des `PendingIntent.getBroadcast` **immuables** (`FLAG_IMMUTABLE`), un par action, composant explicite (`exported=false`) : `DONE`
  (extra `taskId`), `CHRONO` (extra `taskId`), `TICK`, `EXPIRE`. Le récepteur utilise `goAsync()` et `KairosProcess.scope`, comme `ChronoReceiver`.
- **`DONE`** : premier appel (non armé) : `armed = (taskId, now)`, redessin avec « Confirmer ? » et une alarme `EXPIRE` à 5 s qui
  désarme et redessine ; second appel dans les 5 s : `repository.toggleDone(taskId)` **seulement si la tâche est encore `TODO`** (un
  broadcast tardif ne rouvre jamais une tâche faite). L'état armé est en mémoire du processus : s'il meurt, le toucher suivant est un
  premier toucher (jamais une action involontaire).
- **`CHRONO`** : `repository.startTimer(taskId)` si le chrono n'est pas déjà sur cette tâche, sinon `repository.stopTimer()` ; `startTimer`
  refuse déjà une tâche non à faire.
- **Toucher sur le corps ou « + »** : un `PendingIntent.getActivity` vers `MainActivity` avec l'extra `destination` (`DAY` ou
  `NOTES`). `MainActivity` est `singleTop` : `onCreate` le lit dans `KairosApp(initialDestination = …)` ; **`onNewIntent`**
  publie la destination dans un état que `KairosApp` consomme (`externalDestination: Destination?` + `onExternalDestinationHandled`)
  et qui passe l'application déjà ouverte sur la vue demandée sans recréer l'activité.

### Thème (`WidgetColors.kt`)

- Les couleurs viennent de **`ThemeColors`** (le même algorithme que l'application, `apparence.md`) à partir de `settings.themeColor`, en
  clair ou sombre comme `KairosTheme` l'affiche (`themeMode` résolu avec le mode nuit du système) ; pas de couleurs dynamiques du fond d'écran
  (le réglage « Couleurs du système » reste propre à l'application). Une valeur par rôle MD3, appliquée par `setInt(…, "setBackgroundColor" /
  "setColorFilter")` et `setTextColor` (valable dès l'API 26).
- Une seule zone teintée par widget : « Maintenant » en `primaryContainer` (comme la carte de la vue Jour), « Journée » en `surface`
  avec sa ligne en cours en `primaryContainer` ; P0 en `error`, deep work en tertiaire, la tâche faite en `onSurfaceVariant` barrée ;
  fond arrondi 16 dp (grands blocs), boutons en pilule, cibles de 48 dp ; aucune ombre ni dégradé (`DESIGN_SYSTEM.md`).

### Textes

- Les textes rédigés par le code (« Rien de prévu aujourd'hui », « Tout est fait pour aujourd'hui », « Confirmer ? », « Ensuite », « À
  traiter », décompte « 3 / 7 faites ») sont des **chaînes des ressources Compose**, dans les six langues (`values*/strings.xml`),
  lues par `WidgetTexts` (`getString`, suspendu, la langue déjà posée par `applyLanguage`) : `StringsParityTest` les couvre.
- Le **nom et la description** des widgets dans le sélecteur du système ne peuvent venir que de ressources Android :
  `androidApp/src/main/res/values{,-fr,-es,-de,-ru,-it}/strings.xml` (4 chaînes, apostrophe `’`), avec un test d'unité qui compare les
  clés des six fichiers (`LanguageParityTest`).

### Décisions

- **`RemoteViews`, pas Glance** : Glance (`androidx.glance`) simplifie la mise en page mais ajoute une dépendance (versions figées, graphe
  de build reproductible) pour un rendu que `RemoteViews` fait déjà ; `core` calcule, `androidApp` ne fait que dessiner.
- **Deux widgets distincts** plutôt qu'un seul à plusieurs tailles : ce sont deux usages (le geste du moment, la vue d'ensemble) et chacun
  se place et se redimensionne à sa façon.
- **Liste, pas frise graphique** pour « Journée » (choix du propriétaire) : `RemoteViews` ne dessine pas de frise proportionnelle sans bitmap ;
  une liste ordonnée par heure reste lisible à toute taille et défile.
- **Double toucher sur « Fait »** (choix du propriétaire) plutôt qu'annulation après coup : un écran d'accueil se touche sans le vouloir.
- **Rafraîchissement sur changement et aux frontières**, jamais à la minute : l'alarme inexacte et `Chronometer` évitent le réveil
  périodique ; écarté : `updatePeriodMillis` (30 min au mieux, contenu périmé) et une alarme chaque minute (batterie).
- **Une seule source de vérité du plan** : `DayView.build` ; le widget n'a ni moteur ni règle propre, donc une règle de la vue Jour
  (décaler, bloquer, épingler) s'y retrouve sans rien changer.
- **Aucun masquage des titres** : même exposition que les notifications existantes ; un réglage sera ajouté si les retours le demandent.

### Alternatives écartées

- Widget unique « Maintenant » avec bascule : demande un état et un geste de plus.
- Écran de configuration à l'ajout (contenu, couleurs) : plus de code, de textes et de tests ; la taille suffit à choisir le niveau de détail.
- Actions sur chaque ligne de « Journée » : la cible tactile des lignes se ferait avec le défilement ; les gestes restent dans « Maintenant ».
