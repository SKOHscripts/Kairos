# Ta journée : la carte qui présente la journée

_Rôle : présenter la journée, puis le reste de la journée, en clair et d'un coup d'œil (combien de tâches, combien de temps, quelles
échéances, combien de temps vraiment disponible une fois comptés les dérangements habituels, et si ça tient), et proposer ce qui
aide quand ça ne tient pas (un créneau de deep work). Bandeau persistant où Kairos parle à l'utilisateur. Fichiers couverts
(prévus) : `core/day/DayBrief.kt` (pur), `ui/day/DayBriefCard.kt`, `ui/day/DayScreen.kt`, `core/reminders/DailySummary.kt`,
`core/model/Settings.kt` (`dayBriefFolded`, local), `FeatureCatalog`. Tests (prévus) : `DayBriefTest`, `DailySummaryTest` (étendu),
`DayBriefUiTest`._

État : issue [#274](https://github.com/SKOHscripts/Kairos/issues/274), jalon 4.6.0, liée à [#272](https://github.com/SKOHscripts/Kairos/issues/272)
(dérangements attendus, `derangements.md`) et [#271](https://github.com/SKOHscripts/Kairos/issues/271). **Spécifiée le
2026-11-21, non implémentée** ; choix du propriétaire du même jour.

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

`DayBrief.build(snapshot, now, zone): DayBrief` lit la même vue que la vue Jour (`DayView.build`) :

- Bilans repris de la carte « Maintenant » : `doneToday`, `workedMinutes` et `workedByType` (mêmes calculs que le bilan actuel de
  `NowCard`, déplacés).
- `phase` : `DAY` avant le début de la journée de travail du jour (horaires des réglages) ou tant qu'aucune tâche n'a été faite ni
  aucune séance chronométrée aujourd'hui ; `REST` sinon.
- `tasks` et `estimateMinutes` : tâches du plan du jour restantes (non faites), durées de placement (`Scheduling.durationMinutes`
  avec les durées apprises, moins le temps passé) ; `deadlinesToday`, `deadlinesWeek` : tâches à faire d'échéance aujourd'hui, et
  d'ici dimanche.
- `availableMinutes` : le disponible de la vue Jour (plages libres restantes de la journée de travail) ; `interruptionMinutes` :
  `ExpectedInterruptions.expected` sur ces plages hors deep work (`derangements.md`) ; `effectiveMinutes` = différence.
- `overflowMinutes` = `max(0, estimateMinutes − effectiveMinutes)`.
- `deepWork` : si `overflowMinutes > 0` et des dérangements sont attendus, la durée `D` telle que les dérangements évités
  (`expected` de la plage réservée) couvrent le dépassement, arrondie au quart d'heure supérieur, au moins 30 min, et le premier
  début libre qui la contient ; nul si aucune plage libre n'est assez longue ou si même tout le temps libre en deep work ne
  suffirait pas (alors `postpone` = la première tâche du plan qui dépasse la fin de la journée).
- `verdict` : `FITS`, `DEEP_WORK` (avec `deepWork`), `POSTPONE`, `EMPTY`, `DAY_OFF`.

### Interface (`ui/day/DayBriefCard.kt`)

- Carte pleine en `secondaryContainer` / `onSecondaryContainer` (formes MD3 des grands blocs, sans ombre) en tête de la vue Jour
  (au-dessus de la capture), icône, titre (`day_brief_title_day` / `_rest`), lignes de texte, action en bouton texte ; repliée, une ligne (le verdict) et un chevron. Pli : réglage local
  `Settings.dayBriefFolded` (`SyncScope.LOCAL_SETTINGS`, propre à l'appareil, comme `subtaskChain`).
- « Réserver … de deep work » : `KairosRepository.createBlock(BlockEdit…)` d'un créneau `DEEPWORK` (titre « Deep work » dans la langue), puis
  message avec « Annuler » (`LocalUndoMessages`).
- Textes dans les six langues ; carte du catalogue « Ta journée » ; puce et ligne du README.

### Notification (`core/reminders/DailySummary.kt`)

Le récapitulatif du jour reprend `DayBrief` : texte court « 7 tâches · ~4 h 30 · 2 échéances aujourd'hui » ; dépliée, les lignes
« Disponible » et verdict, puis les trois premières tâches comme aujourd'hui.

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

### Impacts sur les specs existantes (à reporter à l'implémentation)

- `vue-jour.md` : la carte en tête, au-dessus de la capture ; la carte « Maintenant » (§ 4) perd ses bilans.
- `docs/DESIGN_SYSTEM.md`, `CLAUDE.md` (§ Palette, « Où va la couleur ») et `navigation-theme.md` : deux blocs teintés dans la vue
  Jour (« Ta journée » en conteneur secondaire, « Maintenant » en conteneur primaire).
- `publication.md` : captures des magasins de la vue Jour à régénérer.
- `rappels.md` : contenu du récapitulatif du jour.
- `derangements.md` : `ExpectedInterruptions.expected` utilisé par la carte.
- `reglages.md`, `synchronisation.md` : `dayBriefFolded`, réglage local.
- `liste-fonctionnalites.md`, `README.md`, `README.fr.md`, `FeatureCatalog` : la fonctionnalité.
