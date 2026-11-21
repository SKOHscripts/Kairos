# Ta journée : la carte qui présente la journée

_Rôle : présenter la journée, puis le reste de la journée, en clair et d'un coup d'œil (combien de tâches, combien de temps, quelles
échéances, combien de temps vraiment disponible une fois comptés les dérangements habituels, et si ça tient), et proposer ce qui
aide quand ça ne tient pas (un créneau de deep work). Bandeau persistant où Kairos parle à l'utilisateur. Fichiers couverts :
`core/day/DayBrief.kt` (pur), `core/day/DayView.kt` (`expected`), `ui/day/DayBriefCard.kt`, `ui/day/NowCard.kt` (bilans retirés),
`ui/day/DayScreen.kt`, `core/reminders/DailySummary.kt` (`brief`), `ui/reminders/ReminderTexts.kt`, `core/model/Settings.kt`
(`dayBriefFolded`, local), `core/sync/SyncScope.kt`, `KairosRepository.setDayBriefFolded`, `FeatureCatalog` (carte `day_brief`).
Tests : `core/.../day/DayBriefTest` (calcul et récapitulatif du jour), `desktopApp/.../DayBriefUiTest`._

État : issue [#274](https://github.com/SKOHscripts/Kairos/issues/274), jalon 4.5.0, liée à [#272](https://github.com/SKOHscripts/Kairos/issues/272)
(dérangements attendus, `derangements.md`) et [#271](https://github.com/SKOHscripts/Kairos/issues/271). **Spécifiée et implémentée
le 2026-11-21** ; choix du propriétaire du même jour.

## 1. Besoin métier (cahier des charges)

### Objectif / problème

La vue Jour dit quoi faire maintenant et dans quel ordre ; elle ne présente pas la journée en mots : combien il y a, pour quand,
et si ça tient. Le bilan de la carte « Maintenant » (« requis … · disponible … », « la journée déborde de … ») donne des chiffres,
sans dire quoi faire. Avec les dérangements attendus (`derangements.md`), Kairos peut dire si la journée tiendra **vraiment**, et
ce qui la ferait tenir.

« En commençant ma journée, dis-moi d'un coup d'œil combien de tâches j'ai, combien de temps elles devraient prendre, quelles
échéances arrivent, combien de temps j'ai vraiment une fois comptés les dérangements habituels, et si je vais dépasser. Si oui,
propose ce qui aide, comme un créneau de deep work dédié. Pendant la journée, continue de me dire où j'en suis. »

### Comportement attendu (utilisateur)

1. **Une carte « Ta journée »** en tête de la vue Jour, **colorée** (conteneur secondaire, plus doux que « Maintenant ») et
   **toujours présente** : elle se replie (une ligne, le verdict), jamais ne disparaît ; le pli est mémorisé sur l'appareil.
   Elle reprend les **bilans de la carte « Maintenant »** (faites aujourd'hui, à faire, « requis · disponible », « la journée
   déborde de … », temps travaillé aujourd'hui et par type) : « Maintenant » ne garde que la tâche du moment, ses boutons et
   l'état des alertes du chrono (décision du propriétaire du 2026-11-21). C'est la place où Kairos **parle à l'utilisateur** : présenter la journée,
   puis le reste de la journée ; encourager et donner des nouvelles viendront plus tard (le propriétaire y reviendra).
2. **Le matin** (avant le début de la journée de travail, ou tant que rien n'a été fait ni chronométré aujourd'hui) :

   > **Ta journée**
   > 7 tâches à faire, estimées à 4 h 30. 1 faite, 55 min travaillées (Développement 30 min · Administratif 25 min).
   > Échéances : 2 aujourd'hui, 3 cette semaine.
   > Disponible : 5 h 40, dont ~45 min de dérangements habituels, soit **4 h 55 effectives**.
   > **Sans créneau de deep work dédié, tu dépasseras sûrement d'environ 50 min.**
   > `Réserver 1 h 30 de deep work`

3. **Ensuite**, le titre devient **« Le reste de ta journée »** et les mêmes lignes parlent de ce qui reste : « 3 tâches restantes,
   ~1 h 40 ; ça tient avant 18 h ».
4. **Verdict** : « Ça tient » ; ou le dépassement probable, avec la phrase sur le deep work quand un créneau le résorberait ;
   ou, si même une journée entière de deep work ne suffirait pas, « Même sans dérangement, la journée déborde de … : reporte une
   tâche » (lien vers la première tâche du plan qui ne tient pas, qu'on décale avec « Décaler »).
5. **« Réserver … de deep work »** : un toucher crée un créneau deep work au **premier moment libre** de la journée qui le
   contient (hors réunions et heures fixes), de la durée proposée ; message avec « Annuler ». Le créneau se modifie ensuite comme
   tout créneau.
6. **Sans historique de dérangements suffisant**, la ligne « Disponible » n'a pas de part de dérangements et le verdict compare
   seulement l'estimé au disponible ; aucun créneau n'est proposé pour éviter des dérangements qu'on ne sait pas estimer.
7. **La notification « Ta journée »** (récapitulatif du jour, `rappels.md`, désactivée par défaut) reprend les premières lignes :
   tâches et estimation, échéances, disponible effectif, verdict. L'action reste dans l'application (un toucher ouvre la vue Jour).
8. **Jour sans rien** (ni tâche, ni créneau) : « Rien de prévu aujourd'hui. » Week-end et férié : « Jour non travaillé » et les
   tâches éventuelles.

### Critères de succès

- Chiffres exacts sur des jeux construits à la main : nombre, estimation (durées de placement, apprises comprises), échéances du
  jour et de la semaine, disponible, part de dérangements, effectif, dépassement (`DayBriefTest`).
- La durée de deep work proposée résorbe le dépassement attendu, arrondie au quart d'heure supérieur, au moins 30 min, dans le
  plus long créneau libre restant ; sinon le verdict « reporte une tâche » (`DayBriefTest`).
- Matin puis « le reste » selon l'heure et l'activité ; repli mémorisé ; réservation et « Annuler » (`DayBriefUiTest`).
- La notification porte les mêmes chiffres (`DailySummaryTest`).
- La carte est en conteneur secondaire, « Maintenant » en conteneur primaire ; « Maintenant » ne montre plus les bilans, « Ta
  journée » les montre tous, sans en perdre un (`DayBriefUiTest`, captures de l'auto-test et des magasins régénérées).

### Hors périmètre / différé

- **Encouragements et nouvelles** dans la carte (messages, félicitations, nouveautés) : le propriétaire y reviendra ; la carte est
  conçue pour les accueillir (une liste de lignes).
- **Espace Équipe** : la carte est celle de l'espace Perso.
- **Notification avec action** (« Réserver » depuis la notification) : non.

## 2. Solution technique

### Calcul (`core/day/DayBrief.kt`, pur)

`DayBrief.build(view, settings, now): DayBrief` part de la vue Jour du même instant (`DayView.build`, qui porte désormais
`expected`, les dérangements attendus appris) :

- Bilans repris de la carte « Maintenant » : `doneToday`, `workedMinutes` et `workedByType` (mêmes calculs que le bilan actuel de
  `NowCard`, déplacés).
- `phase` : `DAY` avant le début de la journée de travail du jour (horaires des réglages) ou tant qu'aucune tâche n'a été faite ni
  aucune séance chronométrée aujourd'hui ; `REST` sinon.
- `tasks` et `estimateMinutes` : tâches du plan du jour restantes (non faites), durées de placement (`Scheduling.durationMinutes`
  avec les durées apprises, moins le temps passé) ; `deadlinesToday`, `deadlinesWeek` : tâches à faire d'échéance aujourd'hui, et
  d'ici dimanche.
- `effectiveMinutes` : le disponible de la vue Jour (`DayStats.availableMinutes`, déjà net des dérangements attendus) ;
  `interruptionMinutes` : `DayStats.expectedInterruptionMinutes` (`derangements.md`) ; `availableMinutes` = leur somme.
- `overflowMinutes` = `max(0, estimateMinutes − effectiveMinutes)`.
- `deepWork` : si `overflowMinutes > 0` et des dérangements sont attendus, la durée `D` telle que les dérangements évités
  (`expected` de la plage réservée) couvrent le dépassement, arrondie au quart d'heure supérieur, au moins 30 min, et le premier
  début libre qui la contient ; nul si aucune plage libre n'est assez longue ou si même tout le temps libre en deep work ne
  suffirait pas (alors `postpone` = la première tâche du plan qui dépasse la fin de la journée).
- `verdict` : `FITS`, `DEEP_WORK` (avec `deepWork`), `POSTPONE` (avec `postpone` : la première tâche « Sans créneau », sinon la
  première placée qui finit après la journée, sinon la dernière placée), `EMPTY` (ni tâche ni créneau occupé), `DAY_OFF` (jour libre
  ou férié, sans tâche). Le moment libre du deep work exclut les créneaux du jour (occupés et deep work existants) et les tâches à
  heure fixe ; la durée accumule, minute par minute, le taux de chaque heure jusqu'à couvrir le dépassement.

### Interface (`ui/day/DayBriefCard.kt`)

- Carte pleine en `secondaryContainer` / `onSecondaryContainer` (formes MD3 des grands blocs, sans ombre) en tête de la vue Jour :
  sur une colonne (moins de 900 dp), au-dessus de la capture ; sur deux colonnes, en tête de la colonne de droite, au-dessus
  d'« En ce moment » et de la frise ; **le jour même seulement** (un autre jour ouvert depuis la semaine n'en a pas), icône `Today`, titre
  (`day_brief_title_day` / `_rest`), lignes de texte, verdict en gras, action en bouton texte ; repliée, le verdict seul et un
  chevron. Pli : réglage local `Settings.dayBriefFolded` (`true` replié, nul déplié ; `SyncScope.LOCAL_SETTINGS`, comme
  `subtaskChain`), écrit par `KairosRepository.setDayBriefFolded`, hors du formulaire des Réglages.
- « Réserver … de deep work à 9 h 00 » : `KairosRepository.createBlock(BlockEdit…)` d'un créneau `DEEPWORK` (titre `tag_deepwork`
  dans la langue), puis message avec « Annuler » (`LocalUndoMessages`), qui retire le créneau retrouvé par ses heures et son genre.
  « Reporter » : le bouton « Décaler » de la tâche nommée.
- « Maintenant » perd ses bilans (et leurs textes `stats_done`, `stats_todo`, `stats_load`, `stats_overflow`,
  `stats_spent_today`, `stats_expected_interruptions`, retirés des six langues).
- Textes dans les six langues ; carte du catalogue « Ta journée » ; puce et ligne du README.

### Notification (`core/reminders/DailySummary.kt`)

Le récapitulatif du jour porte `DailySummary.brief` (`DayBrief` à l'heure du récapitulatif) : texte court « 7 tâches · ~4 h 30 ·
2 créneaux · échéances aujourd'hui : 2 » (un compte nul omis) ; dépliée, les lignes « Disponible » et verdict
(`ReminderTexts.briefLines`), puis les trois premières tâches comme avant, puis les tâches faites sans temps (`durees-reelles.md`).

### Décisions et pièges tracés

- **Carte persistante, colorée, repliable** (2026-11-21, propriétaire) : le bandeau où Kairos parle ; écartée une carte qu'on
  ferme pour de bon. **Rouvre la règle « un seul bloc teinté par écran »** (`docs/DESIGN_SYSTEM.md`, `CLAUDE.md`) : la vue Jour en
  a deux, « Ta journée » en conteneur secondaire et « Maintenant » en conteneur primaire ; écartés la même teinte pour les deux
  (« Maintenant » ne ressortirait plus) et une surface à peine teintée. Le conteneur secondaire garde aussi son rôle de sélection
  (puces, segments) : une carte entière ne se confond pas avec un élément sélectionné.
- **Bilans déplacés dans « Ta journée »** (2026-11-21, propriétaire) : la vue d'ensemble d'un côté, l'action du moment de
  l'autre ; écarté le doublon des deux cartes.
- **Carte et notification** (2026-11-21, propriétaire) ; écartées la notification seule (désactivée par défaut) et la carte seule.
- **Proposer un créneau de deep work** (2026-11-21, propriétaire), créé d'un toucher avec « Annuler » ; écarté le simple avertissement.
- **Durée de deep work par les dérangements évités** : c'est ce que le deep work apporte selon l'hypothèse du propriétaire (aucun
  dérangement en deep work) ; sans historique de dérangements, aucune proposition (rien à éviter qu'on sache mesurer).

- **Le jour même seulement** (implémentation, 2026-11-21) : la carte parle de « ta journée » ; un autre jour ouvert depuis la
  semaine garde sa vue sans elle.
- **Colonne de droite sur grand écran** (implémentation, 2026-11-21) : la vue d'ensemble y rejoint « En ce moment » et la frise,
  et la liste de gauche (capture, « Maintenant », tâches) garde sa place ; en tête de la liste, elle repoussait les tâches sous le
  bord d'une fenêtre de bureau.
- **Le nombre de créneaux reste dans la notification** (implémentation) : il disait déjà la journée, l'estimation et les échéances
  s'y ajoutent.

### Impacts sur les specs existantes (reportés le 2026-11-21)

- `vue-jour.md` : la carte en tête, au-dessus de la capture ; la carte « Maintenant » (§ 4) perd ses bilans.
- `docs/DESIGN_SYSTEM.md`, `CLAUDE.md` (§ Palette, « Où va la couleur ») : deux blocs teintés dans la vue Jour (« Ta journée » en
  conteneur secondaire, « Maintenant » en conteneur primaire) ; `navigation-theme.md` n'énonçait pas la règle, rien à y changer.
- `publication.md` : captures des magasins de la vue Jour à régénérer.
- `rappels.md` : contenu du récapitulatif du jour.
- `derangements.md` : `ExpectedInterruptions.expected` utilisé par la carte.
- `reglages.md`, `synchronisation.md` : `dayBriefFolded`, réglage local.
- `liste-fonctionnalites.md`, `README.md`, `README.fr.md`, `FeatureCatalog` : la fonctionnalité.
