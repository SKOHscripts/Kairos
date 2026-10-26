# Récurrence (tâches et créneaux) et « Décaler »

_Rôle : les obligations qui se répètent et le report au jour ouvré suivant.
Fichiers couverts : `kmp/core/src/commonMain/.../core/engine/Recurrence.kt`
(calcul pur), son usage dans `data/KairosRepository.kt` (`toggleDone`,
`ensureCalendarOccurrences`, `snooze`, `updateTask`) et dans
`core/day/DayView.kt` (créneaux du jour), `core/week/WeekView.kt`, `core/team/TeamWork.kt` (charge de « moi »). Tests :
`core/src/commonTest/.../engine/RecurrenceTest.kt`, partie calendaire de
`DifferentialTest`, `data/.../KairosRepositoryTest.kt`, `BlockExceptionMigrationTest`,
`desktopApp/.../BlockOccurrenceUiTest.kt`. Interface des créneaux : `ui/.../day/Blocks.kt`._

État : **jalon M2** ; fériés des créneaux « jours ouvrés » implémentés en 3.7.0 (issue #109) ; créneaux mensuels
(issue #108) et une seule occurrence d'un créneau (issue #110) implémentés en 3.8.0. La partie calendaire est figée par les fichiers d'or de
`DifferentialTest` (`calendar.json`, source de vérité depuis
`retrait-kairos2.md`). **Avis des mois sautés** d'une série « le N du mois » (issue #111) : décidé par le propriétaire et
**spécifié le 2026-10-26, non implémenté** (version proposée 3.13.0) ; ses passages sont marqués « non implémenté » et
leurs impacts sur les autres specs sont listés en fin de document.

## 1. Besoin métier (cahier des charges)

### Objectif / problème

Trois familles d'obligations répétées :

1. **Tâches recréées en se terminant** (« relever le courrier », « point
   hebdo ») : je la fais, la suivante apparaît.
2. **Tâches calées sur une date du mois** (« le 23 », note de frais) : une
   occurrence par mois, indépendamment du sort de la précédente.
3. **Créneaux qui se répètent** (déjeuner quotidien, deep work chaque
   mardi, réunion d'équipe le premier lundi du mois, rendez-vous le 15) :
   un créneau n'est jamais « fait ». Une occurrence peut faire exception
   (« pas de déjeuner ce mardi », « la revue à 16 h cette semaine
   seulement »).

Et « Décaler » : repousser une tâche au prochain jour ouvré.

### Comportement attendu (utilisateur)

- Terminer une tâche quotidienne, jours ouvrés, hebdomadaire ou mensuelle
  crée la suivante, échéance avancée selon la règle, priorité, points, type,
  durée, projet, description et heure fixe (même heure, nouveau jour)
  repris ; sa date programmée est la nouvelle échéance (elle n'apparaît pas
  dans la journée avant). Une tâche « jours ouvrés » ne tombe jamais sur un
  week-end **ni sur un jour férié** des réglages (France, jours libres).
- Une hebdomadaire revient toujours sur son jour de semaine (celui de son
  échéance), même terminée en retard, sans dérive.
- Une récurrente terminée en retard repart d'aujourd'hui, pas de l'échéance
  manquée (pas de rattrapage en rafale).
- Fait, rouvert, refait : une seule occurrence suivante.
- Une série « le N du mois » voit son occurrence du mois apparaître dès
  l'affichage de la vue Jour, reculée au jour ouvré précédent si le jour
  tombe un week-end ou un férié (N borné à la fin du mois). Une occurrence
  du mois précédent restée ouverte n'est jamais touchée ; une tâche dont
  l'échéance tombe déjà ce mois-ci compte comme l'occurrence du mois.
  Une tâche enregistrée comme série sans échéance **est** l'occurrence du mois :
  `updateTask` lui pose l'échéance de ce mois (`calendarDeadline`) et la période,
  ou celles du mois suivant si le jour N est déjà passé (elle naîtrait sinon en retard ;
  une période postérieure ou égale au mois courant couvre ce mois, `calendarOccurrences`)
  — sinon l'affichage suivant en créait une seconde de même titre, l'originale
  restant « sans créneau ».
- « Décaler » (3.4) programme aussi la tâche **personnelle** pour ce jour (elle quitte « Aujourd'hui » jusque-là, `vue-jour.md` § Dates, déplacer, blocage). « Décaler » envoie l'échéance au jour ouvré qui suit (un vendredi → le
  lundi, ou le mardi si ce lundi est férié) ; une échéance passée repart
  d'aujourd'hui.
- Un créneau récurrent (quotidien, jours ouvrés, hebdomadaire, mensuel) réapparaît à
  la même heure les jours concernés, jamais avant sa date d'origine ; le
  supprimer, ou le modifier par « Toutes les occurrences », agit sur toute la série (seul le modèle
  est stocké), ce que la confirmation de suppression dit.
- **Créneaux mensuels** (3.8.0, issue #108). Le menu « Récurrence » du créneau propose deux règles de plus, dont le
  libellé reprend la date saisie : « mensuelle (le 15) » (« le 1er » le premier du mois) et « mensuelle (3e mardi) »
  (« 1er », « 2e », « 3e », « 4e » ou « dernier » ; anglais « monthly (day 15) », « monthly (3rd Tuesday) »). Le badge
  de la liste dit « mensuel ».
  - « Le N du mois » : N est le jour de la date d'origine, borné à la fin du mois (le 31 tombe le 30 avril, le 28 ou le
    29 février) et, comme une tâche « le N du mois », reculé au jour ouvré précédent s'il tombe un week-end ou un férié
    des réglages. Le mois de l'origine a pour occurrence l'origine elle-même, même un week-end : le créneau reste là où
    l'utilisateur l'a posé. Reculer peut faire passer l'occurrence d'un mois dans le mois d'avant (le 1er août 2026,
    samedi → vendredi 31 juillet).
  - « Le Ne jour de semaine » : le rang de l'origine parmi les mêmes jours de semaine de son mois (le mardi 17 novembre
    2026 est le 3e) ; une origine dans les jours 29 à 31 (un 5e) devient « dernier ». Les fériés sont ignorés, comme en
    hebdomadaire : la réunion du premier lundi reste au premier lundi.
- **Une seule occurrence** (3.8.0, issue #110). Le dialogue d'un créneau récurrent s'ouvre sur un choix « Seulement
  celle-ci » / « Toutes les occurrences » (« Only this one » / « All occurrences »), « Seulement celle-ci » par défaut :
  - « Seulement celle-ci » : « Change le mardi 29 septembre 2026 seulement : les autres jours de la série ne bougent
    pas. », titre, début et fin de ce jour-là, puis « Sauter ce jour », « Annuler », « Enregistrer ». « Sauter ce jour »
    retire l'occurrence du jour ; le message « « Déjeuner » sauté le 29 sept. » propose « Annuler », qui remet le jour
    tel qu'il était. Une occurrence déjà sautée ou modifiée offre aussi « Rétablir » (l'occurrence de la série revient) ;
    sautée, le dialogue dit « Ce jour est sauté : le créneau n'y apparaît pas. » et n'a plus de champs.
  - « Toutes les occurrences » : le formulaire complet d'avant (jour, heures, deep work, récurrence, « Supprimer »).
  - La liste des créneaux du jour montre l'occurrence du jour (heures de l'exception comprises) ; une occurrence sautée
    y reste, barrée, avec le badge « sauté ce jour », une occurrence modifiée porte « modifié ce jour » (jamais la
    couleur seule). Son en-tête dit « Créneaux du jour (un créneau récurrent se modifie pour ce jour seulement ou pour
    toutes ses occurrences) : ».
  - L'exception vaut partout où le créneau compte : frise et planning de la vue Jour, vue Semaine, charge de « moi » de
    l'espace Équipe. Les autres jours de la série ne changent pas.
- Un créneau « jours ouvrés » saute, comme une tâche « jours ouvrés », les week-ends **et les fériés** des réglages
  (calendrier français s'il est activé, jours fériés supplémentaires) : le stand-up quotidien n'apparaît ni le 1er mai
  ni un jour de pont déclaré (3.7.0, issue #109). Ni dans la vue Jour, ni dans la vue Semaine, ni dans la charge de
  « moi » de l'espace Équipe. Quotidien et hebdomadaire ignorent les fériés, comme avant. L'aide de la carte
  « Jours fériés » des Réglages le dit (« … tâches et créneaux « jours ouvrés »… »).
- **Mois sautés : un avis discret par série** (spécifié le 2026-10-26, issue #111, décision du propriétaire ; **non
  implémenté**, version proposée 3.13.0). Une série « le N du mois » ne reçoit l'occurrence du mois qu'à l'affichage de
  la vue Jour : si Kairos n'a pas été ouvert pendant un mois entier (congés, arrêt), ce mois n'a pas d'occurrence et
  rien ne le dit. Le rattrapage **automatique** reste exclu (une rafale de tâches en retard à la réouverture, dont
  certaines déjà faites hors de Kairos) ; Kairos **signale** et laisse choisir :
  - **Une seule ligne par série**, sur la vue Jour d'aujourd'hui, sous la zone de capture et au-dessus de « À traiter » :
    icône de récurrence, texte « 2 occurrences mensuelles ont été sautées : Note de frais » (« 1 occurrence mensuelle a
    été sautée : Note de frais » au singulier) et deux actions, « Les recréer » (« La recréer ») et « Ignorer ». Jamais
    une fenêtre qui bloque, jamais un bloc teinté (le seul bloc teinté de l'écran reste « Maintenant »). L'affichage de
    l'avis n'écrit rien.
  - « Les recréer » : une occurrence par mois sauté, dont l'échéance est le N de ce mois, reculé au jour ouvré précédent
    comme toute occurrence « le N du mois » ; elle est donc **déjà en retard** et se range comme toute tâche en retard. Un
    message « 2 occurrences recréées : Note de frais » propose « Annuler » (les occurrences créées sont retirées).
  - « Ignorer » : l'avis disparaît pour de bon pour ces mois ; un mois sauté plus tard donnera un nouvel avis. Un
    message propose « Annuler » (l'avis revient).
  - Un mois est **sauté** quand il est postérieur au premier mois de la série, antérieur au mois courant, et que la
    série n'y a aucune occurrence : ni à faire, ni faite, ni archivée, ni échéance dans ce mois. Au plus les douze
    derniers mois sont proposés (au-delà, un oubli est devenu un abandon).
  - Seule la règle « le N du mois » est concernée. Quotidienne, jours ouvrés, hebdomadaire et mensuelle (+ 1 mois) se
    recréent à la complétion : elles ne sautent jamais de mois, et **aucun avis ni rattrapage n'existe pour elles**. Une
    série d'équipe n'a pas d'avis non plus (sa projection est celle de l'espace Équipe, `TeamWork.seriesDates`). Pas
    d'avis quand on regarde un autre jour que le vrai aujourd'hui, ni dans la vue Semaine ou Mois.
  - Limite assumée : une occurrence supprimée à la main un mois passé est indiscernable d'un mois sauté ; « Ignorer » est
    fait pour cela.

### Critères de succès

- Les comportements décrits ci-dessus sont vérifiés par `RecurrenceTest` et
  `KairosRepositoryTest`.
- Échéances suivantes, jours ouvrés, décalage et créneaux récurrents
  identiques aux fichiers d'or sur le jeu calendaire
  (`DifferentialTest.calendar_matches_golden`).
- Un créneau « jours ouvrés » saute un férié tombé en semaine et un jour supplémentaire déclaré, garde les autres
  jours d'une semaine sans férié, et quotidien et hebdomadaire ne changent pas
  (`RecurrenceTest.weekday_slots_skip_holidays_like_weekday_tasks`,
  `weekday_slots_keep_every_workday_of_a_week_without_holiday`, `daily_and_weekly_slots_ignore_holidays`) ; la vue Jour d'un férié, la vue Semaine et la charge de « moi » l'appliquent
  (`DayViewTest.a_weekday_slot_is_not_on_a_public_holiday`, `WeekViewTest.a_weekday_slot_skips_the_holidays_of_the_week`,
  `TeamWorkTest.aWeekdayMeetingTakesNoCapacityOnAHoliday`).
- Créneaux mensuels : le 31 borné et reculé (février, avril, mai, année bissextile), férié du 11 novembre, recul dans le
  mois d'avant, 3e et dernier jour de semaine
  (`RecurrenceTest.monthly_day_slots_clamp_to_the_end_of_month_and_move_back_to_a_workday`,
  `monthly_day_slots_skip_holidays_like_monthly_tasks`, `a_monthly_day_slot_moved_back_can_land_in_the_previous_month`,
  `monthly_weekday_slots_keep_the_rank_of_their_origin`, `unknown_block_codes_read_as_one_off_and_the_monthly_codes_are_stable`) ;
  une fois dans sa semaine (`WeekViewTest.a_monthly_slot_appears_once_in_its_week`) ; codes et export
  (`KairosRepositoryTest.deletingASeriesDeletesItsExceptionsAndExportsRoundTripThem`).
- Une seule occurrence : sautée ou remplacée, sans toucher aux autres jours
  (`RecurrenceTest.an_exception_skips_or_changes_one_occurrence_only`), dans la vue Jour et sa liste
  (`DayViewTest.one_occurrence_skipped_or_changed_shows_in_the_day_and_stays_editable`), la vue Semaine
  (`WeekViewTest.a_skipped_or_changed_occurrence_is_reflected_in_the_week`) et la charge de « moi »
  (`TeamWorkTest.aSkippedMeetingFreesMyCapacityThatDayOnly`) ; dépôt (refus, une exception par jour, annulation, série
  modifiée ou rendue ponctuelle, `KairosRepositoryTest.oneOccurrenceIsSkippedChangedOrRestoredWithoutTouchingTheSeries`),
  suppression et aller-retour d'export, export inchangé sans exception
  (`KairosRepositoryTest.deletingASeriesDeletesItsExceptionsAndExportsRoundTripThem`), migration sans perte
  (`BlockExceptionMigrationTest`), dialogue (`BlockOccurrenceUiTest.oneOccurrenceIsSkippedUndoneChangedAndRestored`).
- **Mois sautés** (3.13.0, issue #111 ; tests **à écrire** avec l'implémentation, aucun n'existe encore) :
  - Détection pure (`RecurrenceTest`) : seuls les mois sans occurrence entre le premier mois de la série et le mois
    courant sont sautés (`skipped_months_lie_between_the_series_start_and_the_current_month`) ; une occurrence faite,
    archivée, ou dont l'échéance tombe dans le mois le couvre (`a_done_or_archived_occurrence_covers_its_month`,
    `a_deadline_inside_a_month_covers_it`) ; au plus douze mois (`only_the_last_twelve_months_are_offered`) ; le filigrane
    de « Ignorer » masque jusqu'à son mois et un trou plus tardif réapparaît
    (`ignoring_hides_months_up_to_the_watermark_and_a_later_gap_shows_again`) ; une série née ce mois-ci n'en a pas, ni
    une série d'équipe, ni une autre règle (`a_series_started_this_month_a_team_series_and_other_rules_have_no_notice`) ;
    séries distinctes par titre et jour du mois (`series_are_keyed_by_title_and_day_of_month`).
  - Recréation pure (`RecurrenceTest`) : une occurrence par mois sauté, échéance au N reculé au jour ouvré précédent,
    fériés compris, sans heure fixe (`recreated_occurrences_fall_on_the_business_day_before_day_n_of_each_skipped_month`) ;
    `calendarOccurrences` ne change pas (`DifferentialTest.calendar_matches_golden` inchangé, jamais de rattrapage
    automatique).
  - Dépôt (`KairosRepositoryTest`) : recréer crée une occurrence par mois et l'avis disparaît, un second appel ne crée
    rien (`recreatingSkippedOccurrencesIsIdempotent`) ; ignorer écrit le filigrane une fois, annuler le rétablit
    (`ignoringASkipNoticeWritesTheWatermarkAndUndoRestoresIt`) ; la mémoire passe l'export et l'import et est absente
    par défaut, un export solo reste identique à l'octet (`theSkipNoticeSurvivesAnExportAndIsAbsentByDefault`,
    `TeamIsolationTest.soloExportIsByteIdentical` inchangé) ; « Restaurer les valeurs par défaut » la garde
    (`SettingsFormTest.restoringDefaultsKeepsTheSkipNotices`).
  - Interface (`desktopApp`, `SkippedMonthsUiTest`) : une ligne par série sur le jour d'aujourd'hui seulement, jamais
    sur un autre jour ; « Les recréer » et « Ignorer » avec leur « Annuler » ; noms accessibles qui citent la série et
    contiennent le libellé visible ; cibles de 48 dp (`assertTouchHeightIsEqualTo(48.dp)`) ; 360 dp sans débordement ;
    captures `--self-test` regardées ; `StringsParityTest` sur les six langues.

### Hors périmètre / différé

- Rattrapage **automatique** des mois manqués (exclu : l'avis discret de l'issue #111 le remplace, § Mois sautés ; aucun
  avis pour les règles quotidienne, jours ouvrés, hebdomadaire et mensuelle) ; avis des séries d'équipe ; créneau annuel ; déplacer une seule occurrence à un **autre jour** (seuls son titre et
  ses heures changent) ; modifier une occurrence depuis la vue Semaine (la vue Jour reste le seul chemin).

## 2. Solution technique

- `nextDeadline(règle, base, jourDeSemaine?, fériés = ∅)` : `DAILY` +1 jour ;
  `WEEKDAYS` jour suivant hors week-end et hors `fériés` ; `WEEKLY` prochain jour de semaine
  ancré (défaut : celui de la base), toujours strictement après ; `MONTHLY`
  +1 mois, jour borné à la fin du mois.
- `nextOccurrence(tâche, aujourd'hui, existantes, maintenant, fériés = ∅)` : `null` hors
  `COMPLETION_RULES` ou si une tâche à faire de même titre, règle et
  échéance existe ; sinon la copie (identifiant 0), base
  `max(échéance, aujourd'hui)`, ancre hebdomadaire = `recurrenceDayOfWeek`
  sinon jour de l'échéance, reportée sur la copie. La copie garde l'espace
  et l'assigné de la tâche (`equipe.md` : une tâche d'équipe récurrente ne
  fait jamais naître une tâche Perso) ; pour une tâche d'équipe, le dépôt
  lui donne un nouveau `teamUid`, un avancement vide et son événement
  `created` (`equipe-backlog-suivi.md`).
- `calendarOccurrences(tâches, aujourd'hui, fériés, maintenant)` : séries
  `(titre, jour du mois)` des tâches `MONTHLY_ON_DAY` (tous statuts) ; pour
  chaque série non couverte ce mois (période `AAAA-MM` ou échéance dans le
  mois), une occurrence héritée du membre le plus récent (plus grand id),
  espace et assigné compris, échéance `onOrBeforeBusinessDay`,
  `recurrencePeriod` posée. Clé de série : (titre, jour du mois,
  **espace**) — une série Perso et une série d'équipe de même titre sont
  distinctes ; l'assigné n'y entre pas, pour qu'une occurrence réaffectée
  reste dans sa série (`equipe.md` § Questions ouvertes).
- **Série stable** (3.4, `durees-apprises.md`) : `nextOccurrence` et `calendarOccurrences` posent sur la copie
  `seriesId = seriesId de la mère, sinon son id` (une tâche reçue d'un manager, `origin != null`, n'ouvre jamais de série). Les
  occurrences d'une même série partagent ainsi un identifiant qui survit aux renommages ; il alimente la durée apprise.
- `nextSnoozeDate(échéance, aujourd'hui, fériés)` : jour ouvré suivant
  l'échéance si elle est à venir, sinon aujourd'hui.
- `expandRecurringBlocks(modèles, début, fin, fériés = ∅, exceptions = ∅)` : pour chaque jour de
  `[max(début, origine), fin]` où `occursOn(modèle, jour, fériés)`, l'occurrence `occurrence(modèle, jour, exception)`
  (même heure et même durée, identifiant du modèle), sauf si l'exception du jour est « sautée ». `occurrence` remplace
  champ par champ le titre, le début et la fin par ceux de l'exception quand ils sont non nuls (fin absente : début +
  durée du modèle). `occursOn` : `DAILY` toujours ; `WEEKDAYS` les jours `Workdays.isWorkday(jour, fériés)` (le même
  prédicat que `nextDeadline`) ; `WEEKLY` le jour de semaine de l'origine ; `MONTHLY_DAY` si le jour est
  `monthlyDayOccurrence(origine, mois du jour)` **ou** celle du mois suivant (recul d'un 1er vers la fin du mois
  d'avant) ; `MONTHLY_WEEKDAY` le même jour de semaine que l'origine et `hasRank(jour, weekdayRank(origine))`.
  `monthlyDayOccurrence` : l'origine dans son propre mois, sinon `onOrBeforeBusinessDay(jour N borné, fériés)` ;
  `weekdayRank` : `(jour - 1) / 7 + 1`, 5 → `LAST_RANK` ; `hasRank(jour, LAST_RANK)` : `jour + 7 > jours du mois`.
  Les appelants passent les fériés des
  réglages (`Workdays.holidaysFor`) et `snapshot.blockExceptions` : `DayView.build` (créneaux du jour et créneaux modifiables), `WeekView.build`
  (calculés depuis le lundi : `holidaysFor` couvre l'année d'avant et les deux suivantes, une semaine à cheval sur
  le Nouvel An y est), `TeamWork.busyHours` (charge de « moi »). Les fichiers d'or n'en passent pas.
- Modèle (3.8.0) : `BlockRecurrence.MONTHLY_DAY` (code `monthly_day`) et `MONTHLY_WEEKDAY` (`monthly_weekday`), sans
  colonne nouvelle (tout vient de la date d'origine) ; `BlockException(id, blockId, date, skipped, title?, start?,
  end?, createdAt)`, au plus une par `(blockId, date)` (`KairosSnapshot.blockExceptions`, table
  `time_block_exception`, `modele-donnees.md`).
- Vue Jour : `DayView.editableBlocks` est une liste d'`EditableBlock(template, occurrence, exception)` : les ponctuels du
  jour (`occurrence = template`) et les modèles qui ont une occurrence ce jour (`occursOn`), exception appliquée, **même
  sautée** (`skipped`) ; triée par heure de l'occurrence. `dayBlocks` (frise, planning) n'a pas les sautées.
- Dépôt : `skipOccurrence(id, jour)` et `overrideOccurrence(id, jour, titre, début, fin)` écrivent l'exception
  (`INSERT OR REPLACE`, unicité `(block_id, date)`) et rendent `false` sans rien écrire pour un créneau inconnu, ponctuel
  ou sans occurrence ce jour (`hasOccurrence`, fériés des réglages), ou une fin qui ne suit pas le début ; le titre est
  nettoyé, vide ou égal au modèle → `NULL`. `restoreOccurrence` retire l'exception ; `putOccurrenceException(id, jour,
  exception?)` remet celle d'avant (annulation du message). `deleteBlock` supprime aussi les exceptions du créneau ;
  `updateBlock` les garde, sauf si le créneau devient ponctuel.
- Interface (`Blocks.kt`) : `recurrenceText(règle, jour)` (libellés mensuels tirés de `Dates.dayOfMonth`,
  `Dates.weekdayName`, `rankLabel`), `DayBlockList(List<EditableBlock>)`, `BlockDialog(bloc, jour, …,
  OccurrenceActions)` : `KairosSegmentedRow` « Seulement celle-ci » / « Toutes les occurrences » pour un récurrent,
  `OccurrenceFields` (titre, début, fin ; « Rétablir » si exception, « Sauter ce jour » si non sautée). `DayScreen`
  câble les actions ; le saut passe par `LocalUndoMessages` (`block_skipped_message`, `action_revert`).
- Dépôt : `toggleDone` insère `nextOccurrence` dans la transaction qui
  termine la tâche ; `ensureCalendarOccurrences` recalcule dans sa
  transaction et n'écrit que s'il y a lieu ; `snooze` et les fériés viennent
  des réglages (`Workdays.holidaysFor`). `updateTask` pose l'ancre
  hebdomadaire (jour de l'échéance) et ne garde le jour du mois que pour
  « le … du mois », dans 1-31.
- La vue Jour appelle `ensureCalendarOccurrences` quand le jour ou les
  tâches changent.

### Avis des mois sautés (spécifié le 2026-10-26, issue #111 ; **non implémenté**, 3.13.0)

Rien de ce bloc n'existe encore dans le code ; `calendarOccurrences` ne change pas (pas de rattrapage automatique).

- **Calcul pur** (`core/engine/Recurrence.kt`, sans horloge ni I/O : `today` et les fériés sont des paramètres) :
  - `SkippedSeries(title, dayOfMonth, months)` : `months` = le premier jour de chaque mois sauté, ordre croissant ;
    `key` = `"$dayOfMonth|$title"` (la clé de série de `calendarOccurrences`, sans l'espace : toujours `PERSONAL`).
  - `skippedSeries(tasks, today, ignored): List<SkippedSeries>` : mêmes séries que `calendarOccurrences`, restreintes à
    `TaskSpace.PERSONAL` (tâches `MONTHLY_ON_DAY` avec `recurrenceDayOfMonth`, tous statuts, groupées par titre et jour).
    Mois **couverts** d'une série = les `AAAA-MM` de ses membres (`recurrencePeriod` non vide, et `period(deadline)`) ;
    sans mois couvert, pas d'avis. Premier mois = le plus petit mois couvert. Candidats = de
    `max(premier mois + 1, mois courant − 12)` à `mois courant − 1`. Sauté = candidat non couvert **et** postérieur à
    `ignored[key]` (le filigrane, `""` s'il manque). Séries sans mois sauté absentes ; tri par titre sans casse puis jour.
    Les mois du **mois courant et au-delà** ne sont jamais candidats : ils restent à `calendarOccurrences`.
  - `recreateSkipped(tasks, series, holidays, now): List<Task>` : pour chaque mois de `series.months`, une copie du membre
    de plus grand id (mêmes champs hérités que `calendarOccurrences` : description, priorité, points, type, projet,
    estimation, `seriesId`), identifiant 0, `recurrence = MONTHLY_ON_DAY`, `recurrenceDayOfMonth`,
    `recurrencePeriod = period(mois)`, `deadline = calendarDeadline(N, mois, fériés)` et `scheduledDate` = l'échéance
    (comme `calendarOccurrences`), **`pinnedStart = null`**. `Workdays.holidaysFor(today, …)` couvre l'année précédente
    : les douze mois en arrière y sont tous.
- **Mémoire de « Ignorer »** : `Settings.recurrence: RecurrenceSettings?`, **nul par défaut** (patron de `guide`,
  `schedule`, `team`) ; `RecurrenceSettings(skipIgnored: Map<String, String>)` : clé de série → **dernier mois ignoré**
  (`AAAA-MM`, un filigrane : il masque ce mois et tous les précédents), jamais écrit vide
  (`@EncodeDefault(EncodeDefault.Mode.NEVER)`, sous-objet ramené à `null` quand il vaut sa valeur par défaut). Aucune
  migration SQL, `user_version` inchangé : les réglages sont du JSON, qui voyage avec l'export (`ExportCodec`, aller-retour
  comme `guide`) ; une base ou un export qui n'y a jamais touché ne change pas d'un octet.
  `KairosRepository.updateRecurrence(change)` suit `updateGuide` (n'écrit rien si rien ne change). Le formulaire des
  Réglages ignore `recurrence` (son état est indexé sans lui, `validate(values, current)` garde le courant) et
  « Restaurer les valeurs par défaut » le **garde** : c'est une décision sur des données, pas une préférence.
- **Dépôt** (`KairosRepository`) : `recreateSkippedOccurrences(title, dayOfMonth, today): List<Long>` recalcule la série
  **dans la transaction** (l'état a pu changer), insère `recreateSkipped` et rend les identifiants créés (vide si plus
  rien n'est sauté : idempotent) ; `ignoreSkippedOccurrences(title, dayOfMonth, today): String?` pose le filigrane au
  dernier mois sauté et rend l'ancien (annulation) ; `restoreSkipNotice(title, dayOfMonth, previous)` le remet.
  Annuler une recréation appelle `deleteTask` sur les identifiants rendus.
- **Interface** (`ui/day/`) : `SkippedSeriesNotice(series, onRecreate, onIgnore)`, un par série, dans la liste paresseuse de
  `DayScreen` (clé `skipped-<clé de série>`), entre `capture` et `inbox-title`, **seulement si `day == today`**.
  `DayScreen` calcule `Recurrence.skippedSeries(snapshot.tasks, today, settings.recurrence?.skipIgnored.orEmpty())` sur
  `personalSnapshot`, mémorisé sur les tâches, le jour et ce réglage. Composant : `OutlinedCard` (contour, ni remplissage
  ni ombre : le bloc teinté de l'écran reste « Maintenant »), `FlowRow` d'une icône `Repeat` décorative, du texte
  (`bodyMedium`) puis deux `TextButton` « Les recréer » / « Ignorer ». Les messages passent par `LocalUndoMessages`
  (`action_revert`), comme « Déplacer… » et le saut d'un créneau.
- **Accessibilité** : pas de région vivante (l'avis n'interrompt pas : le lecteur d'écran le lit à son tour dans la
  liste) ; ordre de lecture texte, « Les recréer », « Ignorer » ; nom accessible de chaque bouton = son libellé visible
  suivi de la série (« Les recréer : Note de frais », « Ignorer : Note de frais ») pour que plusieurs avis se
  distinguent sans perdre le libellé visible ; cibles de 48 dp (boutons Material 3) ; à 360 dp, les boutons passent sous
  le texte (`FlowRow`), aucun débordement horizontal ; l'information ne repose jamais sur la couleur (icône et texte).
- **Textes** (`values/` et `values-fr/`, `-es`, `-de`, `-ru`, `-it`, parité exigée par `StringsParityTest` ; le dépôt n'a
  pas de ressources de pluriel, d'où les paires `_one` / `_many`) : `skipped_series_one|many`, `skipped_recreate_one|many`,
  `skipped_ignore`, `skipped_recreate_for_one|many`, `skipped_ignore_for`, `skipped_recreated_one|many`,
  `skipped_ignored`. Propositions à relire à l'implémentation :

  | Langue | Ligne (n ≥ 2, `%2$s` = série) | Actions (n ≥ 2) |
  |---|---|---|
  | fr | « %1$d occurrences mensuelles ont été sautées : %2$s » (n = 1 : « 1 occurrence mensuelle a été sautée : … ») | « Les recréer » (« La recréer ») · « Ignorer » |
  | en | “%1$d monthly occurrences were skipped: %2$s” (n = 1 : “1 monthly occurrence was skipped: …”) | “Recreate them” (“Recreate it”) · “Ignore” |
  | es | «Se omitieron %1$d repeticiones mensuales: %2$s» (n = 1 : «Se omitió 1 repetición mensual: …») | «Volver a crearlas» («Volver a crearla») · «Ignorar» |
  | de | „%1$d monatliche Termine wurden übersprungen: %2$s“ (n = 1 : „1 monatlicher Termin wurde übersprungen: …“) | „Neu anlegen“ · „Ignorieren“ |
  | ru | «Пропущено ежемесячных повторов: %1$d · %2$s» (une seule forme, sans déclinaison) | «Создать заново» · «Игнорировать» |
  | it | «%1$d occorrenze mensili sono state saltate: %2$s» (n = 1 : «1 occorrenza mensile è stata saltata: …») | «Ricrea» («Ricrea») · «Ignora» |

  La série suit le deux-points sans guillemets, comme dans l'exemple du propriétaire.

### Décisions et pièges tracés

- **Les créneaux « jours ouvrés » sautent aussi les fériés** (2026-10-22, issue #109, décision du propriétaire) :
  **par défaut**, sans réglage. Les deux règles portent le même libellé « jours ouvrés » et se comportaient
  différemment. Les créneaux existants changent (un stand-up disparaît le 1er mai) : c'est le comportement attendu.
  Écarté : un interrupteur désactivé par défaut (un sous-objet de réglages nul de plus pour garder une incohérence).
  Paramètre `fériés = ∅` par défaut, comme `nextDeadline` : les fichiers d'or (`calendar.json`) restent identiques.
- **« Jours ouvrés » saute les fériés** (2026-10-12, demande explicite après le test
  exploratoire : l'occurrence suivant le mardi 10 nov. tombait le mercredi 11, férié).
  `nextDeadline` reçoit les
  fériés des réglages (`Workdays.holidaysFor`), passés par `nextOccurrence`, les deux
  chemins de complétion du dépôt (`toggleDone`, intégration d'un rapport) et la
  projection des séries d'équipe (`TeamWork.seriesDates`). Les fichiers d'or
  n'en passent pas (ensemble vide par défaut) et restent identiques.
- **Jour du mois limité à 1-31** : un 0 ferait échouer le calcul de la date ;
  le dialogue refuse
  l'enregistrement et le dépôt vide une valeur hors bornes. Choisir
  « le … du mois » avec un champ vide le pré-remplit du jour de l'échéance
  (sinon du jour affiché) : pas de champ en erreur avant toute saisie.
- **L'origine d'un créneau est la date de son début** : un créneau
  récurrent modifié avec une autre date change donc aussi son point de
  départ. Pour un créneau mensuel, elle donne aussi le jour N ou le rang : changer la date change la règle, ce que le
  libellé du menu montre aussitôt.
- **Mensuels sans colonne** (2026-10-24, issue #108) : jour du mois et rang se lisent sur l'origine ; écarté, une colonne
  « jour N » comme pour les tâches (une migration de plus pour une donnée redondante, et deux sources à garder d'accord).
- **Le mois de l'origine garde l'origine** : appliquer la règle au mois de l'origine faisait disparaître un créneau posé
  un samedi le jour même où on le crée. Les mois suivants suivent la règle des tâches « le N du mois ».
- **5e jour de semaine → « dernier »** : un 5e mardi n'existe que certains mois ; « dernier » est ce qu'on veut dire
  quand on pose une réunion le 30. Une origine 4e et dernière (le 28 d'un mois de 4 mardis) reste « 4e ». Écarté :
  sauter les mois sans 5e (des trous inattendus).
- **« Le Ne jour de semaine » ignore les fériés** : la réunion « premier lundi » tombe un lundi de Pâques ou non selon
  l'équipe ; on la saute alors avec « Seulement celle-ci ». « Le N du mois » recule, lui, comme les tâches de même règle
  (l'issue le demande).
- **Exceptions stockées, occurrences toujours calculées** (2026-10-24, issue #110) : une table d'exceptions attachée au
  modèle, plutôt que matérialiser les occurrences (une ligne par jour à tenir à jour à chaque modification de la série).
  Une exception est le jour **et** l'identifiant du modèle : un jour sans occurrence (règle changée depuis) la rend
  inerte, sans effet ni erreur ; elle redevient active si la règle y revient. Écarté : la purger à chaque modification
  de la série (une exception perdue pour un aller-retour de règle).
- **Pas de déplacement à un autre jour** : « Seulement celle-ci » change titre et heures du jour, pas la date ;
  déplacer une occurrence, c'est la sauter et poser un ponctuel. Ainsi une exception n'est jamais « ailleurs » que son
  jour, et la vue Semaine reste juste sans recherche.
- **« Seulement celle-ci » par défaut** pour un récurrent : c'est le geste le plus courant (l'issue part de lui), et le
  plus sûr (il ne touche qu'un jour).
- **Annuler un saut par le message, rétablir par la liste** : l'occurrence sautée reste listée (barrée, badge), sinon
  rien ne permettrait de la retrouver une fois le message parti.
- **Mois sautés : un avis par série, jamais de rafale** (2026-10-26, issue #111, décision du propriétaire ; spécifié, non
  implémenté). Le rattrapage manquait (une note de frais sautée est une note de frais perdue en silence), mais le
  rattrapage automatique est pire : à la réouverture, N tâches en retard apparaissent sans demande, dont certaines déjà
  faites hors de Kairos. Kairos signale et l'utilisateur choisit, une ligne par série. Écartés : recréer d'office (la
  rafale) ; une notification (Kairos reste silencieux hors de l'écran ouvert, `rappels.md` mis à part) ; un avis sur
  l'Accueil ou la vue Semaine (un seul endroit, là où les tâches du jour se décident) ; une fenêtre à l'ouverture (elle
  bloquerait, et reviendrait à chaque lancement).
- **Détecter les trous, pas les ouvertures** : « sauté » se lit sur les occurrences de la série, sans mémoriser la date de
  dernière ouverture. Écarté : écrire un horodatage à chaque ouverture (l'export et la base d'un utilisateur solo
  changeraient à chaque lancement, contre l'isolation de `equipe.md` § Isolation du mode solo et la règle « rien n'est écrit
  tant qu'on n'a pas touché »). Conséquence assumée : un mois supprimé à la main est vu comme sauté, « Ignorer » le règle.
- **« Ignorer » = un filigrane par série, pas une liste de mois** : les mois sautés sont toujours antérieurs au mois courant
  et « Ignorer » les masque tous ; retenir le dernier mois ignoré suffit, tient en une chaîne par série, ne grossit pas, et
  un trou ultérieur est strictement postérieur au filigrane donc réapparaît. Écarté : la liste des mois ignorés (grandit
  sans bénéfice).
- **Mémoire dans les réglages (JSON), pas dans le schéma** (le plus simple et cohérent avec l'export) : sous-objet nul par
  défaut comme `guide`, donc aucune migration, aucune ligne en plus, export identique tant que rien n'est ignoré, aller-retour
  d'export gratuit. Écartés : une colonne sur `task` (migration `.sqm` et son marqueur de `KairosStore` ; quelle ligne
  porterait l'état d'une série dont les lignes se terminent, se suppriment ?) ; une table `skip_notice` (migration, requêtes,
  export de plus) ; une tâche « témoin » (elle fausserait les listes et les statistiques). Prix : une série renommée ou
  supprimée laisse sa clé (quelques octets, jamais purgée) ; la clé suit titre et jour du mois comme celle de
  `calendarOccurrences` (renommer une occurrence ouvre déjà une autre série).
- **« Restaurer les valeurs par défaut » garde les avis ignorés** : ce n'est pas une préférence ; l'effacer ressusciterait
  des avis que l'utilisateur a réglés. Contraire de `guide`, qui est de l'aide à revoir (`visite-guidee.md`).
- **Douze mois au plus** : borne l'avis (« 24 occurrences sautées » serait une rafale proposée) et reste dans les fériés
  chargés par `Workdays.holidaysFor` (année précédente comprise). Au-delà, l'oubli est devenu un abandon : supprimer la
  série ou la modifier.
- **Occurrences recréées sans heure fixe** : le jour de l'heure fixe est passé, l'épingler à une heure d'un jour révolu
  placerait mal la tâche en retard. L'utilisateur la repose en la traitant. Tout le reste est hérité du membre le plus
  récent, comme `calendarOccurrences`.
- **Échéance au N du mois sauté, donc en retard** (choix du propriétaire) : une occurrence recréée dit honnêtement de quel
  mois elle est ; la dater d'aujourd'hui cacherait le retard.
- **Perso seulement** : une série d'équipe (`space = TEAM`) a des assignés, un journal et un `teamUid` que « recréer » devrait
  écrire ; hors périmètre tant que personne ne le demande. L'avis lit `personalSnapshot` et ne voit donc de toute façon que
  les tâches d'équipe assignées à « moi » (qu'il ignore).
- **Mois courant jamais candidat** : il revient à `calendarOccurrences`, qui le crée à l'affichage ; l'avis et la création
  automatique ne se marchent jamais dessus.

### Impacts sur les specs existantes (à reporter à l'implémentation de l'avis des mois sautés, 3.13.0)

- `vue-jour.md` : la ligne d'avis dans l'ordre de l'écran (entre la capture et « À traiter »), son comportement et ses
  messages ; `DayScreen` (clé `skipped-…`).
- `modele-donnees.md` : `Settings.recurrence` / `RecurrenceSettings`, absence de migration, `updateRecurrence`.
- `export-import.md` : la mémoire voyage avec les réglages, absente par défaut (export solo inchangé).
- `reglages.md` : « Restaurer les valeurs par défaut » garde `recurrence` ; le formulaire l'ignore.
- `i18n.md` : les clés `skipped_*` dans les six langues.
- `accessibilite.md` : noms accessibles « libellé visible + série » (registre transverse).
- `visite-guidee.md` et README (`README.md` et `README.fr.md`, section Features) : carte du catalogue (`since` = 3.13.0,
  `README_FEATURES`, `FeatureCatalogTest.readmeFeaturesAreCovered`) ; notes de version Fastlane (`31300.txt`) dans les six
  langues. Une fonctionnalité visible : elle entre au README.
