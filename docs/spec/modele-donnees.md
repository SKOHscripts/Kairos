# Modèle de données et persistance

_Rôle : la forme des données de Kairos 3, leur stockage SQLite sur les trois
plateformes, l'ouverture et les migrations de la base, le dépôt qui la lit et
l'écrit, et les données d'exemple d'une base neuve. Fichiers couverts :
`kmp/core/src/commonMain/.../core/model/` (`Task.kt`, `TimeBlock.kt`,
`Settings.kt`, `KairosSnapshot.kt`), `kmp/core/.../ExampleData.kt`,
`kmp/data/` (`Kairos.sq`, `KairosStore.kt`, `KairosRepository.kt`,
`Mappers.kt`). L'export et l'import (format JSON), ainsi que la persistance
propre à la version web, sont dans `export-import.md`._

## 1. Besoin métier (cahier des charges)

### Objectif / problème

Kairos est un outil personnel, mono-utilisateur. Ses données doivent rester
**sur l'appareil**, sans compte ni serveur, et survivre à toutes les mises à
jour de l'application. Kairos 3 tourne sur Android, sur le bureau et dans un
navigateur : le même modèle et les mêmes règles doivent valoir partout.

### Comportement attendu (utilisateur)

- Au tout premier lancement, l'utilisateur ne voit pas une page vide : un
  jeu d'exemples est posé (tâches, créneaux, une note), dans la langue de
  l'interface. Ce sont des objets ordinaires (tag de projet « Exemple »,
  titre préfixé « [Exemple] », « [Example] », « [Ejemplo] »… selon la langue) qu'on termine ou supprime comme les siens.
- Les exemples ne sont posés **qu'une fois**, sur une base neuve : supprimer
  tous les exemples ne les fait jamais revenir.
- Une mise à jour de l'application ne perd jamais une tâche ni une valeur
  posée à la main.

### Critères de succès

- Base neuve : exemples posés ; réouverture, même vide : rien de reposé
  (`KairosRepositoryTest.newDatabaseGetsTheExamplesOnceOnly`).
- Une erreur pendant la pose des exemples n'empêche jamais le démarrage.
- Les codes stockés (statuts, récurrences, types de créneau) sont des valeurs
  stables : elles sont écrites telles quelles en base et dans l'export.

### Hors périmètre

- Synchronisation entre appareils (décision du 2026-09-28 : export/import
  manuel seulement, `export-import.md`).
- Colonnes des intégrations retirées : `task.source`, `task.external_id`,
  `task.linked_ticket_id`, `time_block.source`, `time_block.external_id`,
  table `task_sync_meta` (ancienne version). Les calendriers importés de la 3.10.0 ont leurs propres colonnes
  (`time_block.source_id`, `source_uid`, `connexions-externes.md`).

## 2. Solution technique

### Modèle (`core`, pur)

- `Task` : `id`, `title`, `description`, `priority` (0 à 2, `null` = non
  renseignée), `deadline`, `projectTag`, `status` (`TaskStatus` : `todo`,
  `done`, `archived`), `estimatedMinutes`, `pinnedStart`, `parentId`,
  `recurrence` (`TaskRecurrence` : `""`, `daily`, `weekdays`, `weekly`,
  `monthly`, `monthly_on_day`), `scheduledDate`, `recurrenceDayOfMonth`,
  `recurrenceDayOfWeek` (0 = lundi), `recurrencePeriod`, `taskType`,
  `fibonacciPoints`, `manualTimeSpentMinutes`, `createdAt`, `updatedAt`,
  puis, pour l'espace Équipe (`equipe.md`), `space` (`TaskSpace` :
  `PERSONAL` code 0, par défaut, `TEAM` code 1 ; code inconnu →
  `PERSONAL`), `assigneeId` (membre assigné, `null` sinon), et, depuis le
  jalon E3, `progressPercent`, `startedOn`, `teamUid`
  (`equipe-backlog-suivi.md`), et depuis le jalon E6 `origin` (origine
  d'une tâche reçue d'un manager, `TeamOrigin` encodée, `null` sinon),
  `originRemoved` (retirée par le manager, faux par défaut),
  `reportedMinutes` (dernier total de temps rapporté par un membre, côté
  manager) (`equipe-echanges.md`), et `teamExcluded` (tâche perso exclue de
  la charge et des prévisions de « moi », faux par défaut, `equipe-moi.md`).
  - Dates et heures « métier » **locales naïves** (`LocalDate`,
    `LocalDateTime`) ; horodatages techniques en instants UTC (`Instant`).
    Convention de Kairos.
  - `needsProcessing` : il manque la priorité **ou** les points (règle du
    code, `Task.needsProcessing`).
  - `missingQualification` : `PRIORITY`, `POINTS` ou `BOTH`, pour le libellé
    de la boîte de réception.
  - `archived` n'est jamais produit par Kairos ; la valeur reste lisible, et
    une tâche archivée n'est ni affichée ni basculée.
- `TimeBlock` : `id`, `title`, `start`, `end` (locales), `kind`
  (`BlockKind` : `busy`, `deepwork`), `recurrence` (`BlockRecurrence` : `""`,
  `daily`, `weekdays`, `weekly`, et depuis la 3.8.0 `monthly_day`, `monthly_weekday`, `recurrence.md`), `createdAt`,
  et depuis la 3.10.0 `source` (`BlockSource?`, nul pour un créneau saisi à la main ; `Imported(calendarId, uid)` pour
  un créneau d'un calendrier importé, `connexions-externes.md`).
- `CalendarSource` (3.10.0) : `id`, `name`, `hideTitles`, `importedAt`, `createdAt` ; `DayMark` (3.10.0) : `id`,
  `title`, `start`, `end` (dates, fin incluse), `source`, `createdAt` : journée entière d'un calendrier importé, badge
  sans blocage.
- `BlockException` (3.8.0, `recurrence.md` § Une seule occurrence) : `id`, `blockId` (le modèle), `date`, `skipped`
  (faux par défaut), `title`, `start`, `end` (heures locales `LocalTime`, `null` = celles du modèle), `createdAt` ; au
  plus une par `(blockId, date)`.
- `TaskDependency` (`taskId` bloquée par `blockerId`), `WorkSession`
  (`endedAt = null` : en cours), `Note` (`status` `open` / `archived`,
  `convertedTaskId`).
- Codes inconnus à la lecture : valeur par défaut (`todo`, `""`, `busy`,
  `open`) plutôt qu'une erreur.
- `FIBONACCI_SCALE` = 1, 2, 3, 5, 8, 13, 21 ; `PRIORITY_VALUES` = 0, 1, 2.
- `Settings` (sérialisable) : les réglages de l'application, avec leurs
  valeurs par défaut. Un champ absent prend sa valeur par défaut, un
  champ inconnu est ignoré : ajouter un réglage ne demande pas de migration.
  `Settings.defaults(langue)` : types de tâche dans la langue de l'interface
  (français : Développement, Revue de code, Réunion, Documentation,
  Administratif, Veille/formation ; anglais : traduction). Bornes, validation et écran : `reglages.md`. Réglages ajoutés
  depuis : `updateCheckEnabled` (`mises-a-jour.md`), `themeColor`
  (`apparence.md`), `language` (`system` par défaut, `fr` ou `en` ; `i18n.md`
  § Choix de la langue), `team` (`TeamSettings?`, nul tant que l'espace Équipe
  n'a jamais été activé, `equipe.md` § Modèle ; `teamModeEnabled` en est
  la lecture), `schedule` (`ScheduleSettings?`, nul tant qu'aucun jour de la
  semaine n'a d'horaire propre : `weekdays` = jours 0 (lundi) à 6 (dimanche) → `DaySchedule(free, startHour, endHour)` ;
  `planning-personnalise.md` § Lots B1 et B2 ; non écrit dans l'export tant qu'il est nul ; 3.5 : `DaySchedule.windows` (plusieurs plages `WorkWindow(startHour, endHour, context?)`) et
  `ScheduleSettings.contexts` (`WorkContext(name, types)`), jamais écrits quand ils sont vides ; 3.6 : `ScheduleSettings.bestHours` (`BestHoursSettings?` : `enabled` faux, `strength` 1 = Moyenne,
  `preferred` type → heure 0 à 23, vide ; nul tant qu'il vaut ses défauts, donc absent de l'export ; hors bornes à l'import, l'export est refusé comme corrompu, `SettingsForm.validate`)), `learning` (`LearningSettings?` : `enabled` vrai,
  `history` 5, `minSamples` 3 ; nul = valeurs par défaut, non écrit dans l'export tant qu'il est nul, `durees-apprises.md`),
  `guide` (`GuideSettings?`, 3.7.0 : `newsHidden` (version majeure.mineure), `readChapters`, `firstTaskDone` ; nul tant
  que le guide n'a rien retenu, donc absent de l'export, `visite-guidee.md` § Persistance), `reminders`
  (`ReminderSettings?`, 3.8.0 : `enabled` faux, `leadMinutes` 10 (0-120), `nextTask` faux ; nul tant qu'il vaut ses
  défauts, donc absent de l'export, `rappels.md`), `levelNames` (`LevelNames?`, 3.9.0 : `priority` et `points`,
  valeur → nom choisi (16 caractères au plus), une valeur sans nom absente ; nul tant qu'aucun nom n'est choisi, donc
  absent de l'export ; affichage seulement, jamais dans un paquet d'échange, `planning-personnalise.md` § Lot D) ;
  `Task.seriesId` (identifiant de la première tâche d'une série récurrente, nul par défaut).
- `TeamMember` (`core/team/`) : `id`, `uid`, `name`, `role`,
  `availabilityPercent`, `hoursPerDay`, `weekHours` (3.9.0, colonne `week_hours` « 8,8,8,8,0 », `NULL` = aucune ; schéma 10),
  `isSelf`, `archived`, `createdAt`,
  `updatedAt` (`equipe.md`), `lastReportAt` (dernier rapport intégré, jalon
  E6, `equipe-echanges.md`) ; `MemberAbsence` : `id`, `memberId`, `start`,
  `end` (dates locales incluses), `label`, `createdAt` ; `TeamEvent`
  (journal des tâches d'équipe, `equipe-backlog-suivi.md` § Journal).
- `KairosSnapshot` : toutes les tables (membres, absences et journal
  compris, exceptions de créneaux `blockExceptions` depuis la 3.8.0) et les
  réglages. C'est la forme commune de l'export, de l'import, des exemples et
  de la sauvegarde web.

### Exemples (`ExampleData.snapshot(aujourd'hui, maintenant, langue)`)

Le jeu de `app/tasks_seed.py`, dates relatives à `aujourd'hui`, traduit
(français, anglais) : dix tâches (une P0 à échéance à J+2, une P1 à J+7, une
épinglée à 9 h 30, une mère et deux sous-tâches, un bloqueur et la tâche qu'il
bloque, une tâche à traiter sans priorité ni points, une programmée à J+7),
trois créneaux (réunion 13 h-14 h, deep work 10 h-11 h 30, déjeuner 12 h-13 h
quotidien), une note, les réglages par défaut de la langue. Identifiants
1 à 10 : le jeu est inséré par le même chemin que l'import (`replaceAll`).

### Stockage (`data`, SQLDelight)

- `Kairos.sq` : tables `task`, `time_block`, `time_block_exception` (3.8.0, unique `(block_id, date)`, heures en
  `TEXT` « 09:30 »), `task_dependency` (unique
  `(task_id, blocker_id)`), `work_session`, `note`, `settings` (une ligne
  `id = 1`, JSON des réglages), `team_member`, `member_absence` (index
  `member_id`), `team_event` (index `task_id`, `member_id`) (`equipe.md`). Clés
  `INTEGER PRIMARY KEY AUTOINCREMENT` ; index sur `task.status`,
  `task.parent_id`, `task.space`, `task.assignee_id`, `time_block.start`,
  `task_dependency.task_id` et `.blocker_id`, `work_session.task_id`,
  `note.status`. Les colonnes `task.space` et `task.assignee_id` sont **en
  fin de table**, dans une base neuve comme dans une base migrée : les
  insertions avec identifiant sont positionnelles. Références **sans** `FOREIGN KEY` (parti pris de Kairos).
- Types stockés : dates `TEXT` « 2026-09-28 », heures locales `TEXT`
  « 2026-09-28T09:30 », instants `TEXT` ISO UTC (`Mappers.kt`).
- Code **asynchrone** (`generateAsync`), exigé par le pilote web, utilisé
  partout.
- Pilotes, fournis par chaque application : Android `AndroidSqliteDriver`
  (fichier `kairos.db` du stockage privé), bureau `JdbcSqliteDriver`
  (`kairos.db` du dossier de données), web `WebWorkerDriver` sur un worker
  sql.js (base en mémoire, voir `export-import.md` § Version web).

### Ouverture et migrations (`KairosStore.open`)

1. Si la table `task` n'existe pas : création du schéma (`created = true`).
2. Sinon, la **version réelle** du schéma est lue sur les tables et colonnes
   de la base (`KairosStore.actualVersion`, voir ci-dessous) ; si elle est
   inférieure à la version du schéma, toutes les migrations manquantes
   (fichiers `.sqm`, additives, vérifiées par `verifyMigrations`) sont
   appliquées **dans une seule transaction**.
3. `PRAGMA user_version` = version du schéma.

`actualVersion` teste des marqueurs, du plus récent au plus ancien : table `calendar_source` (11), colonne `team_member.week_hours` (10), table `time_block_exception` (9), colonne
`task.series_id` (8), `task.team_excluded` (7), `task.origin` (6), table `team_scenario` (5),
`task.progress_percent` (4), table `member_absence` (3), `task.space` (2),
sinon 1. Chaque nouveau fichier `.sqm` ajoute son marqueur à
`KairosStore.MARKERS` ; `ExchangeMigrationTest.everyMigrationHasAMarker`
échoue sinon.

Schéma 1 : celui de la 3.0.0. Schéma 2 : `1.sqm` (jalon E1 de l'espace
Équipe) ajoute `task.space`, `task.assignee_id`, leurs index et
`team_member`. Schéma 3 : `2.sqm` (jalon E2) ajoute `member_absence`.
Schéma 4 : `3.sqm` (jalon E3) ajoute `task.progress_percent`,
`task.started_on`, `task.team_uid` (en fin de table) et `team_event`.
Schéma 5 : `4.sqm` (jalon E5) ajoute `team_scenario`. Schéma 6 : `5.sqm`
(jalon E6) ajoute `task.origin`, `task.origin_removed`,
`task.reported_minutes` et `team_member.last_report_at` (en fin de table).
Schéma 7 : `6.sqm` ajoute `task.team_excluded` (0 par défaut, en fin de
table, `equipe-moi.md`). Schéma 8 : `7.sqm` ajoute `task.series_id` (nulle, en fin de
table, sans rétro-remplissage, `durees-apprises.md`). Schéma 9 : `8.sqm` (3.8.0) ajoute la table
`time_block_exception`, vide (`recurrence.md`, `BlockExceptionMigrationTest`). Schéma 10 : `9.sqm` (3.9.0) ajoute
`team_member.week_hours` (nulle, en fin de table, `equipe-charge.md` § Capacité, `WeekHoursMigrationTest`). Schéma 11 :
`10.sqm` (3.10.0) ajoute `time_block.source_id` et `time_block.source_uid` (nulles, en fin de table), l'index
`time_block_source`, les tables `calendar_source` et `day_mark`, vides (`connexions-externes.md` § Stockage,
`CalendarImportRepositoryTest`). Schéma 12 : `11.sqm` est une **migration de données** (couleur par défaut du thème : le
miel resté par défaut passe à l'océan, `apparence.md` § Couleur par défaut) ; elle crée la table `migration_mark` (une colonne
`name`, jamais lue ni exportée) comme marqueur structurel : sans marqueur, une migration de données seule serait rejouée à
chaque ouverture (`ThemeColorMigrationTest`).
Les
schémas de référence de chaque version
(`data/src/commonMain/sqldelight/databases/<n>.db`, générés par
`generateCommonMainKairosDatabaseSchema` avant la migration) sont
versionnés, par exception à la règle `*.db` du `.gitignore` : sans eux,
`verifyMigrations` ne peut rien comparer.

Les deux lectures (version, présence de la table) passent par l'API `Query`
de SQLDelight.

### Dépôt (`KairosRepository`)

- **État unique** : toute la base est relue après chaque écriture et publiée
  dans `snapshot` (`StateFlow<KairosSnapshot>`). L'interface dérive tout de
  cet état. Les écritures sont sérialisées (`Mutex`) et transactionnelles ;
  `onChanged` reçoit chaque nouvel état (sauvegarde de la version web).
- `open(opened, examples, clock, timeZone, onChanged)` : si la base vient
  d'être créée, y pose `examples()` (une erreur y est ignorée : le démarrage
  passe toujours), puis charge l'état. `timeZone` (défaut : celui du
  système) donne « aujourd'hui » aux opérations qui en dépendent.
- Opérations :
  - `createTask(titre, parentId?)` : titre nettoyé ; vide → rien (`null`).
    Ni priorité ni points : la tâche arrive « À traiter ». Une mère inconnue
    est ignorée (la tâche naît au premier niveau).
  - `setPriority`, `setPoints` : une valeur hors échelle est vidée.
  - `toggleDone` : `todo` → `done` en fermant la session de chrono ouverte
    sur la tâche et, pour une récurrente, en créant l'occurrence suivante
    dans la même transaction (`recurrence.md`) ; `done` → `todo` ;
    `archived` inchangée.
  - `snooze` : « Décaler » (`recurrence.md` § Décaler).
  - `startTimer`, `stopTimer` : chrono, au plus une session ouverte
    (`temps-reel-chrono.md`).
  - `ensureCalendarOccurrences(jour)` : occurrences du mois des séries
    « le N du mois » ; n'écrit rien s'il n'y a rien à créer.
  - `updateTask(TaskEdit)` : l'édition complète en un enregistrement :
    titre (vide → ancien titre gardé), description, priorité, points,
    échéance, date programmée, durée (≤ 0 → vide), projet et type
    (nettoyés), temps passé manuel (< 0 → vide), récurrence (jour du mois
    gardé seulement pour « le … du mois » et dans 1-31 ; ancre de semaine =
    jour de l'échéance pour l'hebdomadaire), heure fixe (sur la date
    programmée, sinon sur le jour affiché `pinDay` ; vide = désépinglée),
    sous-tâches en lot (une ligne non vide = une sous-tâche), modifications
    des sous-tâches **existantes** d'une tâche perso (`TaskEdit.subtaskEdits`,
    liste de `SubtaskEdit` : titre nettoyé, vide = ancien ; fait ↔ à faire par la
    logique de `toggleDone`, factorisée en `toggleInTransaction` puisque `write`
    n'est pas réentrant ; suppression comme `deleteTask`, dépendances comprises ;
    un `id` qui n'est pas une sous-tâche non archivée de cette mère est ignoré ;
    sans effet sur une tâche d'équipe), bloqueurs
    (ensemble cible complet, `dependances.md`).
  - `createBlock`, `updateBlock`, `deleteBlock` (`BlockEdit` : titre
    nettoyé, début, fin, nature, récurrence) : refusés (`false`) si la fin
    n'est pas après le début ; un récurrent modifié ou supprimé l'est pour
    toutes ses occurrences (seul le modèle est stocké) ; supprimer emporte ses exceptions, le rendre ponctuel aussi.
  - Une seule occurrence (3.8.0) : `skipOccurrence`, `overrideOccurrence`, `restoreOccurrence`,
    `putOccurrenceException` (`recurrence.md` § Solution technique).
  - `deleteTask` : supprime la tâche et les dépendances où elle figure ; ses
    sous-tâches et sessions restent (comportement propre de Kairos).
  - Notes : `createNote`, `editNote`, `convertNote`, `archiveNote`,
    `deleteNote` (`notes-capture.md`).
  - `updateSettings` : si `team` existe sans identité, en pose une (UUID
    tiré dans `data`, gardé si la base en a déjà une) ; rien en mode solo.
  - Membres et absences : `createMember`, `updateMember`, `archiveMember`,
    `restoreMember`, `deleteMember`, `addAbsence`, `updateAbsence`,
    `deleteAbsence`, `clearTeamData` (`equipe.md` § Dépôt) ; tâches
    d'équipe : `createTeamTask`, `assign`, `startTeamTask`, `setProgress`,
    `TaskEdit.reassign`, et journal écrit dans la transaction de chaque
    modification d'une tâche d'équipe (`equipe-backlog-suivi.md` § Dépôt).
  - `replaceAll(snapshot)` : vide toutes les tables (membres et absences
    compris) et
    réinsère tout,
    identifiants compris, en une transaction. Sert à l'import, aux exemples et
    au rechargement web. Les identifiants créés ensuite continuent après le
    plus grand importé.
- Chaque modification met `updatedAt` à l'instant courant (horloge injectée).
- **Vue Perso** : à chaque rechargement, le dépôt publie aussi
  `personalSnapshot` = `Workspaces.personalView(snapshot)` (`equipe.md`
  § Espaces et filtre central) ; sans tâche d'équipe, c'est la même instance
  que `snapshot`.
- Un bloqueur d'un autre espace que la tâche est ignoré par `updateTask`
  (pas de dépendance Perso ↔ Équipe) ; `createTask` crée une tâche
  `PERSONAL`.
- JSON des réglages en base : `encodeDefaults = true` et
  `explicitNulls = false`, pour qu'un `team` nul n'y soit jamais écrit
  (`"team": null` serait une trace d'équipe dans une base solo).

### Décisions et pièges tracés

- **Toute la base en mémoire, relue à chaque écriture** : un outil personnel
  compte quelques milliers de lignes au plus ; en échange, un seul état
  cohérent et un moteur d'ordonnancement qui a de toute façon besoin de
  tout. À revoir seulement si la relecture devient perceptible.
- **Création décidée par la présence de `task`, pas par `user_version`** : le
  pilote Android pose lui-même `user_version`. Il reçoit donc un schéma
  factice (`DeferredSchema`, création et migration vides, voir
  `distribution.md` § Android), et la création réelle reste commune.
- **Version lue sur le schéma, pas sur `user_version`** (issue #65, 3.3.0) : le
  pilote Android ouvre le fichier avec son schéma factice et **pose lui-même**
  `user_version` à la version courante, avant que `KairosStore.open` ne la lise.
  Une base restée en schéma 6 apparaissait donc déjà en schéma 7, la migration
  n'était pas lancée et la première lecture plantait (« no such column:
  task.team_excluded », constaté sur un Fairphone FP4 en passant de la 3.2.0 à
  la 3.3.0 ; une mise à jour de la 3.0.0 vers la 3.1.0 ou plus aurait fait de même
  avec `task.space`). Lire les tables et colonnes évite de dépendre d'un numéro
  que le pilote écrase, et **répare** aussi les bases déjà étiquetées à tort : au
  lancement suivant leurs migrations manquantes s'appliquent, sans perte
  (`ExchangeMigrationTest.aDatabaseStampedWithTheCurrentVersionButMissingColumnsIsStillMigrated`,
  `aSchemaOneDatabaseStampedWithTheCurrentVersionIsMigratedFromTheStart`).
  Écartés : faire migrer `DeferredSchema.migrate` (le pilote Android est
  synchrone, la migration commune est asynchrone, et appeler le pilote depuis
  `onUpgrade` le rouvre récursivement) ; retenir l'ancienne version dans
  `DeferredSchema` (ne répare pas les bases déjà étiquetées à tort).
- **Migrations en une transaction** : une migration interrompue à mi-chemin
  (colonne déjà ajoutée) ne se rejouerait plus, la lecture des marqueurs la
  verrait à moitié faite. SQLite sait annuler le DDL.
- **Lire par `Query`, pas par le pilote brut** : un mappeur `QueryResult`
  paresseux passé directement à `SqlDriver.executeQuery` est évalué après la
  fermeture du curseur par le pilote JDBC synchrone, et lit toujours 0. Bug
  constaté (base recréée à chaque réouverture) et corrigé.
- **Règle « À traiter »** : « sans priorité **ou** sans points » (pas
  « ni »). Une tâche à qualifier manque au moins l'un des deux.
