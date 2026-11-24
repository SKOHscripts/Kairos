# Durées réelles : « Temps passé ? », revue des tâches sans temps, « Fait, suivante »

_Rôle : obtenir plus de **temps réels** sur les tâches terminées, la seule chose qui améliore les durées apprises
(`estimateur-modele.md`) : une question d'un toucher quand on coche une tâche sans temps, une revue des tâches faites sans
temps des sept derniers jours, et « Fait, suivante » qui enchaîne le chrono d'une tâche du plan à la suivante. Fichiers
couverts : `core/engine/SpentReview.kt` (pur : qui demander, qui revoir, durées proposées), `core/model/Settings.kt`
(`askSpentOnDone`), `core/settings/SettingsForm.kt`, `core/reminders/DailySummary.kt` (`untimed`),
`data/KairosRepository.kt` (`setSpent`), `ui/day/SpentPrompt.kt` (barre à puces, dialogue « Autre… »), `ui/day/DayScreen.kt`
(coche, règle des coches groupées, « Fait, suivante »), `ui/day/NowCard.kt`, `ui/recap/SpentReviewCard.kt`,
`ui/week/WeekScreen.kt`, `ui/month/MonthScreen.kt`, `ui/navigation/AppShell.kt` (fournit `LocalSpentPrompts`, hôte de snackbar),
`ui/reminders/ReminderTexts.kt`, `ui/settings/SettingsFormCards.kt` et `SettingsCategory.kt` (champ), `DayView.followUp`,
`KairosIcons.SkipNext` (`make_icons.py`), `FeatureCatalog` (carte `time_spent`). Tests : `core/.../engine/SpentReviewTest.kt`
(calcul, réglage, récapitulatif du jour), `data/.../SpentTest.kt`, `desktopApp/.../SpentUiTest.kt`._

État : issue [#271](https://github.com/SKOHscripts/Kairos/issues/271), jalon 4.5.0, liée à
[#272](https://github.com/SKOHscripts/Kairos/issues/272) (dérangements, `derangements.md`) et
[#274](https://github.com/SKOHscripts/Kairos/issues/274) (« Ta journée », `ta-journee.md`). **Spécifiée et implémentée le 2026-11-21** ;
conception décidée par le propriétaire le même jour (issue, puis trois choix : barre à puces, « Ignorer » = 0 min, revue en
tête du Bilan).

## 1. Besoin métier (cahier des charges)

### Objectif / problème

Le modèle des durées n'apprend que des tâches terminées qui ont un temps **réel** (chrono ou temps saisi dans la fiche). Sur
l'export d'un utilisateur quotidien, 48 des 457 tâches faites (10 %) en avaient un. Un banc d'essai hors ligne sur cet export a
essayé plusieurs variantes du texte des titres (racinisation, troncature, 4-grammes, mots vides, synonymes, poids appris) : aucune
n'a changé l'erreur de façon mesurable. **Le goulot est le nombre de durées mesurées, pas le texte.** Déduire une durée de l'écart
avec la coche précédente ne marche pas (dans ±50 % du vrai temps 30 % des fois seulement : on coche par lots, avec un pic à 9 h
pour les tâches de la veille). Demander au bon moment, d'un toucher, est la voie réaliste.

« Quand je coche une tâche faite sans avoir lancé le chrono, laisse-moi dire en un toucher combien de temps elle a pris, ou plus
tard dans une courte revue. Quand je suis mon plan avec le chrono, qu'il me suive d'une tâche à l'autre sans trou. »

### Comportement attendu (utilisateur)

**1. « Temps passé ? » à la coche.**

- Quand on coche faite, dans la vue Jour (ligne d'une tâche ou bouton « Fait » de la carte « Maintenant »), une tâche **sans
  aucun temps** (ni séance de chrono, ni temps saisi), une barre en bas de l'écran demande :

  > « Rapport » faite · Temps passé ?  ✕
  > `15 min` `30 min` `45 min` `1 h` `2 h` `Autre…`

- Un toucher sur une durée l'enregistre comme **temps saisi** de la tâche (le champ « Temps passé » de la fiche), puis un message
  « Temps noté : 45 min » propose « Annuler » (qui rend la tâche sans temps). « Autre… » ouvre un petit dialogue : un nombre de
  minutes (1 à 1440), « Enregistrer » ou « Annuler ». Ignorer la barre (elle disparaît d'elle-même après une dizaine de
  secondes, comme « Annuler ») ou la fermer (✕) n'enregistre rien : la tâche ira dans la revue.
- **Pas de choix « Comme prévu »** (décision du propriétaire) : on ne se souvient souvent pas de ce qu'on avait estimé ; le
  confirmer n'apprendrait presque rien.
- **Habitude** (4.6.0, `habitudes.md` § 3) : si la question d'automatisme d'une habitude est due à cette coche, elle passe
  avant et « Temps passé ? » n'est pas posé ; la tâche, restée sans temps, rejoint la revue ci-dessous.
- **Coches groupées** : cocher une autre tâche moins d'une minute après la précédente ferme la question ouverte et n'en pose pas
  de nouvelle, tant que les coches s'enchaînent à moins d'une minute ; ces tâches iront dans la revue. Celui qui coche par lots
  (le matin, les tâches de la veille) n'est pas harcelé.
- Pas de question : pour une tâche qui a déjà un temps (le chrono tournait, il est fermé à la coche ; temps saisi), une tâche
  rouverte, une tâche mère qui a des sous-tâches (son temps est celui de ses sous-tâches), une tâche cochée depuis un widget,
  une notification, l'espace Équipe ou la démonstration.
- **Activée par défaut.** Interrupteur « Demander le temps passé à la coche » dans Réglages → Chrono et statistiques.

**2. Revue des tâches faites sans temps.**

- En tête de la destination **Bilan** (vues Semaine et Mois), sous l'en-tête de période et le filtre, **seulement sur la période
  en cours** (la semaine ou le mois qui contient aujourd'hui), une carte :

  > **3 tâches faites sans temps**
  > « Tâche A » · hier  `15 min` `30 min` `45 min` `1 h` `2 h` `Autre…` `Ignorer`
  > « Tâche B » · lundi …

- Elle liste les tâches de l'espace Perso **faites dans les 7 derniers jours** (aujourd'hui compris) sans aucun temps, la plus
  récemment faite d'abord, mêmes exclusions qu'à la coche (tâche mère à sous-tâches). Au-delà de sept jours, personne ne se
  souvient de la durée. Cinq lignes d'abord, puis « Afficher tout (12) ».
- Une durée enregistre comme à la coche (même message « Temps noté » avec « Annuler ») ; la ligne disparaît.
- **« Ignorer »** retire la tâche de la revue pour de bon : la tâche reçoit **0 min** de temps saisi (« connu, rien à compter »),
  ce qui n'ajoute rien à aucun temps ni au modèle, et se synchronise et s'exporte avec la tâche. Message « Ignorée » avec
  « Annuler ».
- Carte absente quand la liste est vide et sur une période passée ou à venir. Le filtre de la vue Semaine réduit la liste comme
  il réduit le bilan (la revue d'un projet) ; la carte disparaît si le filtre la vide.
- **Récapitulatif du jour** (`rappels.md`, quand il est activé) : une ligne de plus dans la notification dépliée, « 3 tâches
  faites sans temps : à compléter dans le Bilan », quand il y en a. Elle n'est pas une raison d'envoyer le récapitulatif
  (un jour sans tâche ni créneau n'en a toujours pas).

**3. « Fait, suivante ».**

- Sur la carte « Maintenant », quand le chrono tourne sur la tâche affichée **et** qu'une autre tâche la suit dans le plan du
  jour, le bouton plein « Fait » devient **« Fait, suivante »** : il arrête le chrono, coche la tâche et lance le chrono sur la
  tâche qui prend sa place dans « Maintenant ». Les chronos s'enchaînent sans trou : chaque tâche d'un plan suivi a un temps
  réel sans rien taper. C'est l'idée de l'interrupteur « Enchaîner » des sous-tâches (`sous-taches.md`), appliquée au plan.
- Un message « Chrono lancé sur « Tâche B » » propose « Annuler », qui arrête ce nouveau chrono (la tâche précédente reste
  faite).
- Sans tâche suivante (dernière du plan), le bouton reste « Fait ». Pour cocher sans enchaîner, la case de la ligne de la tâche
  reste là.
- Aucune question « Temps passé ? » : la tâche a son temps de chrono.

### Critères de succès

- Cocher une tâche sans temps dans la vue Jour pose la question ; une puce enregistre le temps saisi en un toucher ; « Annuler »
  le retire.
- Deux coches à moins d'une minute : aucune question pour la seconde, la première est fermée.
- Une tâche chronométrée, rouverte, mère de sous-tâches, ou cochée hors de la vue Jour : aucune question.
- Réglage coupé : aucune question ; la revue reste.
- La revue liste exactement les tâches perso faites sans temps des sept derniers jours ; « Ignorer » les retire pour de bon, sur
  tous les appareils synchronisés.
- Enregistrer un temps (question ou revue) ne change pas le jour où la tâche compte comme faite dans le Bilan.
- « Fait, suivante » laisse la tâche faite avec son temps de chrono, et le chrono tourne sur la suivante.
- Rien de l'espace Équipe n'apparaît dans la revue (isolation, `equipe.md`).

### Hors périmètre / différé

- **Enregistrer une durée déduite** (écart avec la coche précédente, créneau prévu, durée fixe) : trop imprécis d'après le banc
  d'essai, cela polluerait le modèle.
- **Une notification qui demande le temps** : intrusive ; la question et la revue couvrent le besoin.
- **Distinguer dans le modèle les durées d'un toucher des durées chronométrées** : pas d'abord ; relancer le banc d'essai sur un
  export récent dira si les valeurs d'un toucher dégradent quoi que ce soit.
- La question depuis un widget ou une notification Android.

## 2. Solution technique

### Calcul (`core/engine/SpentReview.kt`, pur)

- `SpentReview.CHOICES = listOf(15, 30, 45, 60, 120)` : les durées proposées, les mêmes à la coche et dans la revue.
- `SpentReview.WINDOW_DAYS = 7`, `SpentReview.BATCH_SECONDS = 60`, `MAX_MINUTES = 1440`.
- `untimed(snapshot, task)` : vrai si la tâche est perso, sans séance de chrono (ouverte ou fermée), sans temps saisi
  (`manualTimeSpentMinutes == null`), sans temps rapporté, et n'a pas de sous-tâche (aucune tâche dont `parentId` est elle).
- `asks(snapshot, task)` : lu **avant** la coche, sur la tâche encore à faire : `task.status == TODO`, `untimed` et le réglage
  `Settings.askSpent` vrai. Avant, parce que la coche change l'instantané de façon asynchrone ; une séance ouverte existe déjà
  avant la coche, la fermer ne change donc pas la réponse.
- `review(snapshot, today, timeZone)` : les tâches faites `untimed` dont le jour de fin (date locale de `updatedAt`, la convention du
  Bilan, `bilan.md`) est dans `[today − 6, today]`, triées par `updatedAt` décroissant puis titre.
- `batched(previous, now)` : vrai si `previous != null` et `now − previous < BATCH_SECONDS`.
- Le snapshot reçu est l'instantané **perso** (`personalSnapshot`) : l'isolation de l'espace Équipe vient de là.

### Réglage (`Settings.askSpentOnDone`)

- `askSpentOnDone: Boolean? = null`, lu par `Settings.askSpent` (`!= false`) : activé par défaut, **rien d'écrit tant qu'on n'y
  touche pas** (comme `learning.model`). Réglage **partagé** par la synchronisation (une manière de travailler, pas une habitude
  d'un appareil). Champ `SettingsForm` `askSpentOnDone`, `FieldKind.BOOL` ; écrit `null` quand on le rallume, pour qu'une base qui
  ne l'a jamais touché reste identique ; rangé dans « Chrono et statistiques » (`TRACKING_KEYS`, carte du chrono).

### Écriture (`KairosRepository.setSpent`)

- `setSpent(taskId, minutes: Int?)` : pose `manualTimeSpentMinutes` (`null` rend la tâche sans temps, `0` = ignorée) d'une tâche
  perso (reçue d'un manager comprise : son temps lui est rapporté), **sans toucher `updatedAt`** (`updateRow(touch = false)`, comme `restoreSpent`) : le jour de fin de la tâche,
  lu sur `updatedAt` par le Bilan, ne bouge pas quand on saisit son temps une semaine plus tard. La synchronisation fusionne
  champ par champ (`synchronisation.md`) : le champ change sans `updatedAt` plus récent, comme toute écriture technique.

### Interface

- `ui/day/SpentPrompt.kt` :
  - `SpentChips(onPick, onOther)` : la ligne de puces (`KairosFilterChip` jamais sélectionnée, 48 dp de cible), en `FlowRow`
    (360 dp sans débordement) ; durées par `duration()`. Dans la snackbar, libellés en `inverseOnSurface` et contour `outline`,
    lisibles sur la surface inverse.
  - `SpentQuestion` : les `SnackbarVisuals` de la question (message, `onPick`, bouton ✕, durée longue) ; `SpentPrompts` (`ask`,
    `close`) et `LocalSpentPrompts`, sans effet hors de la coquille ; `ShellSnackbar` rend la question avec ses puces, toute
    autre snackbar à l'identique.
  - `SpentOtherDialog` : champ numérique de minutes (1 à 1440), « Enregistrer », « Annuler ».
  - La barre « Temps passé ? » : une `Snackbar` Material 3 au contenu personnalisé (message, bouton ✕, puces dessous), servie par
    l'hôte de snackbar de la coquille (`AppShell`) : même place, mêmes couleurs (surface inverse des snackbars) et même durée
    longue que « Annuler ». `LocalSpentPrompts` (comme `LocalUndoMessages`) l'expose aux écrans ; un nouveau message (autre
    snackbar) la remplace.
- `DayScreen` : la coche (lignes et carte « Maintenant ») passe par un seul `onToggleDone` ; pour une tâche à faire, il lit
  `SpentReview.asks` avant la coche, coche, puis pose la question si `asks` et non `batched(lastDoneAt, now)` ; si `batched`, il
  ferme la question ouverte. `lastDoneAt` (instant de la dernière coche « faite », état de l'écran) est mis à jour à chaque
  coche. Rouvrir une tâche ne pose rien et ne touche pas `lastDoneAt`.
- « Fait, suivante » (`NowCard`, `onDoneNext`) : visible si `view.running?.taskId == view.nextUp?.id` et qu'une tâche suit
  (`view.followUp`, la deuxième du plan : `schedule.scheduled` puis `schedule.unscheduled`, sans la tâche affichée). L'action
  coche la tâche (ferme sa séance), puis `startTimer(followUp.id)` ; message « Chrono lancé sur … » avec « Annuler »
  (`stopTimer`). Icône `SkipNext` (ajoutée à `make_icons.py`).
- `ui/recap/SpentReviewCard.kt` : carte « outlined » (`Panel` des Stats : aucune teinte, le Bilan n'en a pas), titre « N tâches
  faites sans temps », phrase « Les 7 derniers jours… »,
  une ligne par tâche (titre, jour de fin court, puces, « Ignorer » en bouton texte), cinq d'abord puis « Afficher tout (N) ».
  Appelée par `WeekScreen` (avec le filtre) et `MonthScreen`, après l'en-tête et avant `RecapCard`, si la période contient
  aujourd'hui.
- Textes en anglais (`values/`) et en français (`values-fr/`), et dans les quatre autres langues de l'interface.

### Récapitulatif du jour (`DailySummary.untimed`)

- `DailySummary` gagne `untimed: Int` (`SpentReview.review(...).size` au jour du récapitulatif, compté quand le récapitulatif est
  préparé) ; le texte déplié (`ReminderTexts.summaryDetail`) ajoute en dernier la ligne `reminder_summary_untimed_one` / `_many` si
  `untimed > 0`. `plan` rend toujours `null` sans tâche placée ni créneau occupé.

### Décisions et pièges tracés

- **Pas de « Comme prévu »** (2026-11-21, propriétaire) : on ne se souvient pas de l'estimation ; écarté.
- **Activée par défaut** (2026-11-21, propriétaire) ; interrupteur pour la couper.
- **Barre à puces** (2026-11-21, propriétaire) : une `Snackbar` au contenu personnalisé, un toucher ; écartée la snackbar
  classique « Saisir… » (deux touchers).
- **« Ignorer » = 0 min de temps saisi** (2026-11-21, propriétaire) : synchronisé et exporté sans rien ajouter, et 0 ne compte
  nulle part (`TimeTracking.spentMinutesByTask` ajoute 0, le modèle n'apprend que des temps positifs) ; écartées une liste
  d'identifiants dans les réglages (les identifiants locaux diffèrent d'un appareil à l'autre, la mémoire du guide les évite
  déjà) et une colonne de plus (migration pour un booléen). Conséquence acceptée : la fiche affiche un temps passé vide pour une
  tâche ignorée (le champ masque 0, `EditTaskDialog`).
- **Revue en tête du Bilan, période en cours** (2026-11-21, propriétaire) ; écartées la vue Jour (déjà chargée, « Ta journée »
  arrive) et une section repliée du bilan (trop discrète).
- **Fenêtre de 7 jours** : au-delà, personne ne se souvient ; la revue reste courte.
- **Une minute pour les coches groupées** : deux coches à moins d'une minute sont un lot (rangement), pas deux fins de travail.
- **Le temps saisi ne touche pas `updatedAt`** : sinon une tâche faite lundi et complétée dimanche passerait dans le bilan de
  dimanche.
- **Pas de question pour une tâche mère à sous-tâches** : son temps est celui de ses sous-tâches ; lui en saisir un le
  compterait deux fois.
- **« Fait, suivante » remplace « Fait » seulement chrono lancé** : sans chrono, rien à enchaîner ; la case de la ligne garde le
  « Fait » simple.

### Impacts sur les specs existantes (reportés le 2026-11-21)

- `temps-reel-chrono.md` : « Fait, suivante » enchaîne le chrono.
- `vue-jour.md` : la question à la coche, « Fait, suivante » sur la carte « Maintenant ».
- `bilan.md` : la carte de revue en tête, période en cours.
- `fiche-tache.md` : 0 min = tâche ignorée par la revue.
- `estimateur-modele.md` : d'où viennent les temps saisis d'un toucher.
- `reglages.md` : l'interrupteur ; `rappels.md` : la ligne du récapitulatif.
- `liste-fonctionnalites.md`, `README.md`, `README.fr.md`, `FeatureCatalog` : la fonctionnalité.
