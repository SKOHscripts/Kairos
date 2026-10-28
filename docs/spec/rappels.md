# Rappels des tâches à heure fixe et de la tâche suivante

_Rôle : prévenir, hors d'un chrono en cours, qu'une tâche à heure fixe va commencer et, sur demande, que le plan passe à la
tâche suivante, et envoyer le récapitulatif du jour. Fichiers couverts : `kmp/core/.../core/reminders/Reminders.kt` et
`DailySummary.kt` (calcul pur), `core/model/ReminderSettings.kt`,
`core/model/Settings.kt` (`reminders`), `core/settings/SettingsForm.kt` (champs `reminders.*`),
`kmp/ui/.../reminders/` (`ReminderWatcher.kt`, `ReminderTexts.kt`), `ui/settings/` (section « Rappels » de la catégorie
« Chrono et statistiques »), `ui/navigation/AppShell.kt` (veille), et sur Android `androidApp/.../ReminderSync.kt`,
`ChronoReceiver.kt`, `KairosProcess.kt`, `ui/guide/FeatureCatalog.kt` (cartes `reminders`, `daily_summary`). Tests :
`core/.../reminders/RemindersTest.kt`, `DailySummaryTest.kt`, `SettingsFormTest`, `desktopApp/.../ReminderUiTest.kt`._

État : 3.8.0 (issue #103), spécifiée et implémentée le 2026-10-24.

3.12.0 (issue #151) : **récapitulatif du jour**, spécifié le 2026-10-26, implémenté le 2026-10-28 (sections « Récapitulatif du jour »
ci-dessous).

## 1. Besoin métier (cahier des charges)

### Objectif / problème

Kairos ne notifie aujourd'hui que pendant un chrono (dépassement, oubli, pause, `temps-reel-chrono.md`). Une tâche épinglée
à 14 h, ou placée par le plan à 10 h 30, ne dit rien quand son heure arrive. Qui se sert de Kairos comme agenda du jour veut
un rappel discret avant une tâche à heure fixe et, s'il le souhaite, quand le plan passe à la tâche suivante.

### Comportement attendu (utilisateur)

- **Désactivés par défaut** : rien ne change pour qui ne les active pas. Une section « Rappels » dans Réglages → « Chrono
  et statistiques », sous « Alertes du chrono » :
  - « Rappeler les tâches à heure fixe » (interrupteur, désactivé) ;
  - « Combien de minutes avant » (10 par défaut, de 0 à 120 ; 0 = à l'heure même) ;
  - « Signaler aussi la tâche suivante du plan » (interrupteur, désactivé ; ne sert que si les rappels sont activés).
  L'aide de la section dit où et comment ils arrivent (« Une notification du système. Sur Android, même Kairos fermé, avec
  quelques minutes de retard possibles téléphone en veille ; sur le bureau et le web, tant que Kairos est ouvert. »).
- **Tâche à heure fixe** : une tâche à faire épinglée (« Heure fixe ») aujourd'hui, placée par le plan du jour (donc ni
  faite, ni bloquée, ni décalée ou programmée un autre jour), reçoit un rappel *N* minutes avant son début :
  titre = la tâche, texte « Commence à 14:00 (dans 10 min) » (« À 14:00 » quand *N* = 0).
- **Tâche suivante** (si activé) : chaque tâche non épinglée que le plan du jour place **après maintenant** reçoit, à son
  début prévu, « C'est l'heure : prévue jusqu'à 11:30 ». La tâche en cours (déjà commencée selon le plan) n'en a pas.
- Le rappel suit le plan : terminer, décaler, bloquer, déplacer ou désépingler une tâche annule ou déplace son rappel ;
  changer les réglages aussi. Un rappel dont l'heure est passée n'est jamais rattrapé.
- Où ils arrivent :
  - **Android** : notification du système (canal « Rappels »), même application fermée, sans permission d'alarme exacte
    (alarmes inexactes, quelques minutes de retard possibles en veille profonde), sans permission réseau. Les rappels du
    jour et du lendemain sont programmés à chaque changement ; au-delà, ils le sont à la prochaine ouverture de Kairos
    (ou au redémarrage du téléphone).
  - **Bureau et web** : tant que Kairos est ouvert, la notification du système (plateau, navigateur) si elle est
    autorisée ; dans tous les cas, dans l'application, le bandeau des alertes du chrono (« Appeler Marc · Commence à
    14h00 (dans 10 min) »), qui reste jusqu'à ce qu'on le ferme. Les messages existants
    « bloquées » / « indisponibles » des alertes du chrono valent aussi pour les rappels.
- Espace Perso seulement : les tâches d'équipe d'un collègue n'ont jamais de rappel (vue Perso).

### Critères de succès

- Désactivés ou absents des réglages : aucun rappel ; activés : la tâche épinglée à 14 h a un rappel à 13 h 50 ; avance 0
  = 14 h ; faite, bloquée, programmée demain ou à une heure déjà passée : aucun ; la tâche suivante non épinglée a son
  rappel à son début prévu si l'option est cochée, et la tâche en cours n'en a pas (`RemindersTest`).
- Réglages : bornes 0-120, `reminders` nul tant qu'on n'y touche pas (export inchangé), revient nul aux valeurs par défaut
  (`SettingsFormTest`).
- La veille de l'application affiche le rappel à l'heure dite (horloge simulée) et ne l'affiche qu'une fois
  (`ReminderUiTest.theFixedTimeTaskIsRemindedOnceAtItsTime`).
- APK sans nouvelle permission (le manifeste garde `POST_NOTIFICATIONS` et `RECEIVE_BOOT_COMPLETED` ; `READ_CALENDAR`,
  facultative, arrive en 3.10.0 pour les calendriers du téléphone, `connexions-externes.md`).

### Hors périmètre / différé

- Rappels de date (« échéance demain »), rappels d'une tâche d'équipe chez un collègue, rappels répétés ou reportables
  (« dans 5 min »), son propre aux rappels, alarmes exactes.
- Sur le bureau et le web, rappels Kairos fermé (il faudrait un service en arrière-plan).

### Récapitulatif du jour (3.12.0, issue #151)

- **Une notification « Ta journée »** à l'heure choisie, qui dit ce que le plan prévoit aujourd'hui, en complément des widgets
  (`widgets-android.md`). **Désactivée par défaut**, indépendante des rappels ci-dessus (on peut avoir l'une sans l'autre).
- Réglages → catégorie « Chrono et statistiques » → section « Rappels » : un interrupteur **« Récapitulatif du jour »** (désactivé) et,
  quand il est actif, **« À quelle heure »** (« 08:30 » par défaut, champ d'heure `HH:MM`).
- **Envoyé seulement les jours où quelque chose est prévu** : au moins une tâche placée par le plan du jour ou un créneau occupé
  (réunion importée comprise). Un jour vide, un week-end ou un férié sans rien, aucune notification.
- **Contenu** : titre « Ta journée » ; texte « 5 tâches · 2 créneaux » ; puis, dans la notification dépliée, les **trois premières
  tâches** du plan avec leur heure (« 09:30 Point d'équipe », « 10:00 Rédiger la documentation »…) et « … » s'il y en a plus.
  Un toucher ouvre la vue Jour.
- **Même vue que le reste** : l'espace Perso (les tâches d'équipe d'un collègue n'y sont jamais). Rien n'est rattrapé : si l'heure
  choisie est déjà passée quand on active l'interrupteur, le récapitulatif de ce jour n'est pas envoyé.
- Où il arrive : comme les rappels, notification du système sur **Android** (même Kairos fermé, alarme inexacte, quelques minutes de
  retard possibles) ; sur le **bureau et le web**, tant que Kairos est ouvert (notification du système si autorisée, sinon le bandeau
  des alertes dans l'application).

**Critères de succès (récapitulatif)** : désactivé ou absent des réglages : rien ; activé à 08:30 : une notification à 08:30 un jour
qui a une tâche ou un créneau, aucune un jour vide ; trois premières tâches par ordre de début, une tâche faite, bloquée ou
programmée un autre jour n'y figure pas ; changer l'heure ou désactiver déplace ou annule l'alarme ; bornes `HH:MM` valides, une
valeur illisible est refusée par le formulaire (`DailySummaryTest`, `SettingsFormTest`, `ReminderUiTest`). Aucune permission ajoutée.

**Hors périmètre (récapitulatif)** : un récapitulatif du lendemain le soir, le contenu des échéances ou des retards, une notification
répétée dans la journée, un son propre, des jours de la semaine au choix.

## 2. Solution technique

### Modèle et réglages (`core`)

- `ReminderSettings(enabled = false, leadMinutes = 10, nextTask = false)` (`@Serializable`), `Settings.reminders:
  ReminderSettings?` nul par défaut : même règle que `learning` (`modele-donnees.md`), un réglage laissé aux défauts
  n'écrit rien et l'export d'une base qui ne s'en sert pas ne change pas.
- `SettingsForm` : `reminders.enabled` (booléen), `reminders.leadMinutes` (entier 0-120), `reminders.nextTask` (booléen),
  lus sur les défauts quand `reminders` est nul, écrits en le gardant nul s'il revient aux défauts.

### Calcul (`Reminders`, pur)

- `Reminders.plan(snapshot, jour, maintenant, fuseau) : List<Reminder>` : vide si `reminders` est nul ou désactivé ;
  sinon `DayView.build(snapshot, jour, maintenant, fuseau)` et ses `schedule.scheduled` :
  - épinglée (`pinned`) : `Reminder(FIXED, at = début − avance)` ;
  - non épinglée, si `nextTask` : `Reminder(NEXT, at = début)` ;
  - gardés seulement si `at > maintenant` (rien de rattrapé) ; triés par `at`. `Reminder` porte la tâche, son titre, son
    début et sa fin (`début + durée`), et une `key` stable (`nature-id-début`).
- Le plan du jour fait foi : `DayView` exclut déjà les tâches faites, bloquées, programmées un autre jour, et place les
  épinglées à leur heure.

### Veille de l'application (`ReminderWatcher`, `ui`)

- Dans la coquille, à côté de `ChronoWatcher` ; lit `personalSnapshot`. Recalcule le plan à chaque changement de base ou
  de minute, attend le prochain `at`, puis, pour chaque rappel franchi depuis le dernier passage (jamais un rappel déjà
  montré : clés retenues pour la session) : bandeau (`AlertBanners`, la liste `alerts` de la coquille) et, si le système ne s'en charge pas
  déjà (`ChronoNotifier.systemHandlesAlerts` faux), `ChronoNotifier.notify(titre, texte, "reminder-<clé>")`.
- `ReminderTexts` : textes hors composition (titre, « Commence à … (dans … min) », « À … », « C'est l'heure : prévue
  jusqu'à … », nom du canal Android).

### Android (`ReminderSync`)

- Démarré par `KairosProcess` comme `ChronoSync` ; suit `personalSnapshot` (réglages compris). À chaque changement :
  annule les alarmes de rappel (un réservoir fixe de `MAX_ALARMS` = 24 codes de requête), calcule les rappels du jour
  (`maintenant`) et du lendemain (début de journée), et pose une alarme `setAndAllowWhileIdle` par rappel, titre et texte
  rédigés dans ses extras (les 24 premiers). `ChronoReceiver` (`ACTION_REMINDER`) poste la notification sur le canal
  `reminders` (importance par défaut). `BootReceiver` : ouvrir la base suffit, comme pour le chrono.
- Aucune permission ajoutée.

### Décisions et pièges tracés

- **Désactivés par défaut, un sous-objet nul** (2026-10-24, issue #103) : rien ne change pour qui ne les active pas, et
  l'export reste identique ; même règle que les durées apprises.
- **Le plan du jour fait foi** : « faite, bloquée, décalée » se déduit de `DayView`, sans règle parallèle à tenir à jour ;
  un rappel « suit » une tâche déplacée parce qu'il est recalculé à chaque changement.
- **Alarmes inexactes** : même décision que le chrono (`temps-reel-chrono.md`) ; quelques minutes de retard valent mieux
  qu'une permission que les magasins (F-Droid compris) regardent de près.
- **Jour et lendemain seulement sur Android** : la tâche suivante dépend du plan, qui dépend de l'heure ; au-delà du
  lendemain il serait faux. Le prochain changement, l'ouverture ou le redémarrage reprogramment.
- **Un réservoir de 24 alarmes** : annuler « toutes les alarmes de rappel » demande de connaître leurs codes ; un
  réservoir fixe les annule sans rien mémoriser. Au-delà de 24 rappels dans deux jours, les suivants le seront au
  prochain changement.
- **Pas de rattrapage** : un rappel passé (téléphone éteint, application fermée sur le bureau) n'a plus de sens.
- **Bandeau dans l'application en plus de la notification** : la notification peut être bloquée ou indisponible ; sur
  Android, l'alarme notifie déjà, l'application n'ajoute que le bandeau. Le bandeau des alertes du chrono, persistant, plutôt
  qu'un message éphémère : un rappel manqué serait perdu.
- **Section du guide** : `GuideSection.SETTINGS_REMINDERS` (catégorie « Chrono et statistiques ») ; la carte `reminders`
  du catalogue y mène.

### Récapitulatif du jour (3.12.0, issue #151)

- **Réglages (`core`)** : `ReminderSettings` gagne `dailySummary: Boolean = false` et `dailySummaryTime: String = "08:30"`
  (`DEFAULT_SUMMARY_TIME`), marqués `@EncodeDefault(NEVER)` : absents du JSON quand ils valent les défauts, l'export d'une base qui ne s'en
  sert pas ne change pas. `SettingsForm` : `reminders.dailySummary` (booléen) et `reminders.dailySummaryTime` (nouveau `FieldKind.TIME`) ;
  `SettingsForm.parseTime` lit « H:MM » ou « HH:MM » de 00:00 à 23:59, `normalizeTime` enregistre « 07:05 » ; une autre valeur est refusée
  (`FieldErrorKind.INVALID_TIME`, « Heure illisible : « … » (format HH:MM, de 00:00 à 23:59). »). Le sous-objet `reminders` reste nul
  tant qu'on n'y touche pas (ou y revient).
- **Écran** : dans la section « Rappels », l'interrupteur « Récapitulatif du jour » puis, **seulement s'il est activé** (ou en erreur),
  « À quelle heure » avec son aide (`SHOWN_WITH` de `SettingsFormCards.kt`).
- **Calcul pur (`core/reminders/DailySummary.kt`)** : `DailySummary.plan(snapshot, jour, maintenant, fuseau): DailySummary?` rend `null` si
  l'option est désactivée, si l'heure du jour est déjà passée (`maintenant` ≥ heure choisie : rien n'est rattrapé) ou si le jour n'a ni
  tâche placée ni créneau occupé ; sinon `DailySummary(at, tasks, slots, first)` : `tasks` = tâches placées, `slots` = créneaux
  **occupés** du jour (`BlockKind.BUSY`, réunions importées comprises ; une fenêtre de deep work n'en est pas un), `first` = les trois
  premières entrées de `schedule.scheduled` (titre, début). Le plan est celui de `DayView.build(snapshot, jour, at, fuseau)` : **placé à
  partir de l'heure du récapitulatif**, comme la vue Jour le montrera à ce moment ; c'est le même `DayView` que `Reminders.plan`, une tâche
  faite, bloquée ou décalée n'y est pas. Une heure illisible (base modifiée à la main) vaut 08:30. Clé : `summary-<jour>`.
- **Veille (`ReminderWatcher`, `ui`)** : l'option est lue en plus de `reminders.enabled` (la veille ne rend plus la main quand seuls les
  rappels sont éteints) ; le récapitulatif a sa propre clé et n'est montré qu'une fois par session et par jour : bandeau « Ta journée · 5
  tâches · 2 créneaux » et, si le système ne s'en charge pas déjà (Android), notification avec le détail.
- **Android (`ReminderSync`)** : `apply` pose en plus **une alarme `setAndAllowWhileIdle` pour aujourd'hui (si l'heure n'est pas passée) et une
  pour demain**, sur leurs propres codes de requête (`SUMMARY_REQUEST_BASE` = 2 000 et 2 001, annulés avec les autres à chaque changement),
  titre et corps déjà rédigés ; `ChronoReceiver` (`ACTION_SUMMARY`) appelle `ReminderSync.postSummary` : notification 200 (une seule, la
  plus récente remplace l'autre) sur le canal `reminders`, texte replié = la ligne du compte, `BigTextStyle` = le détail, `setContentIntent`
  vers `MainActivity` avec l'extra `com.skohscripts.kairos.DESTINATION` = `DAY` (destination externe, `widgets-android.md` § Actions).
  Aucune permission.
- **Textes** (`ReminderTexts`) : `summaryTitle()` (« Ta journée »), `summaryCounts(...)` (« 5 tâches · 2 créneaux », « 1 tâche » ; un
  compte nul est omis ; clés `reminder_summary_tasks_one/_many`, `reminder_summary_slots_one/_many`), `summaryDetail(...)` (le compte, puis
  une ligne par tâche « 09h30 Point d'équipe », `reminder_summary_line`, et « … », `reminder_summary_more`, s'il y en a plus), dans les six
  langues.
- **Carte** `daily_summary` du catalogue (chapitre « Réglages », section `SETTINGS_REMINDERS`, depuis 3.12.0) et ligne « Daily summary »
  du README.
- **Décisions** : l'option **ne dépend pas** de « Rappeler les tâches à heure fixe » (deux usages, deux interrupteurs) ; **un jour vide
  n'envoie rien** (une notification vide apprend à l'ignorer) ; **le lendemain est programmé à l'avance** comme les rappels (au plus un
  jour d'avance, le plan de demain dépend de l'heure d'aujourd'hui) ; **le plan est pris à l'heure du récapitulatif** (pas au début du jour :
  une tâche épinglée avant 08:30 ne décale pas les autres dans le texte) ; **seuls les créneaux occupés comptent** (le deep work est du
  temps de travail, ses tâches sont déjà dans le compte).
