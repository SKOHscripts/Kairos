# Modèle de données et persistance

_Rôle : la forme des données de Kairos 3, leur stockage SQLite sur les trois
plateformes, l'ouverture et les migrations de la base, le dépôt qui la lit et
l'écrit, et les données d'exemple d'une base neuve. Fichiers couverts :
`kmp/core/src/commonMain/.../core/model/` (`Task.kt`, `TimeBlock.kt`,
`Settings.kt`, `KairosSnapshot.kt`), `kmp/core/.../ExampleData.kt`,
`kmp/data/` (`Kairos.sq`, `KairosStore.kt`, `KairosRepository.kt`,
`Mappers.kt`). L'export et l'import (format JSON), ainsi que la persistance
propre à la version web, sont dans `export-import.md`._

Reprend `docs/spec/modele-donnees.md` (Kairos 2), moins les intégrations
retirées.

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
  titre préfixé « [Exemple] ») qu'on termine ou supprime comme les siens.
- Les exemples ne sont posés **qu'une fois**, sur une base neuve : supprimer
  tous les exemples ne les fait jamais revenir.
- Une mise à jour de l'application ne perd jamais une tâche ni une valeur
  posée à la main.

### Critères de succès

- Base neuve : exemples posés ; réouverture, même vide : rien de reposé
  (`KairosRepositoryTest.newDatabaseGetsTheExamplesOnceOnly`).
- Une erreur pendant la pose des exemples n'empêche jamais le démarrage.
- Mêmes tables, mêmes champs et mêmes codes (statuts, récurrences, types de
  créneau) que Kairos 2, pour que l'import d'une base 2.x
  (`migration-2x.md`) soit une simple copie.

### Hors périmètre

- Synchronisation entre appareils (décision du 2026-09-28 : export/import
  manuel seulement, `export-import.md`).
- Colonnes des intégrations retirées : `task.source`, `task.external_id`,
  `task.linked_ticket_id`, `time_block.source`, `time_block.external_id`,
  table `task_sync_meta`.

## 2. Solution technique

### Modèle (`core`, pur)

- `Task` : `id`, `title`, `description`, `priority` (0 à 2, `null` = non
  renseignée), `deadline`, `projectTag`, `status` (`TaskStatus` : `todo`,
  `done`, `archived`), `estimatedMinutes`, `pinnedStart`, `parentId`,
  `recurrence` (`TaskRecurrence` : `""`, `daily`, `weekdays`, `weekly`,
  `monthly`, `monthly_on_day`), `scheduledDate`, `recurrenceDayOfMonth`,
  `recurrenceDayOfWeek` (0 = lundi), `recurrencePeriod`, `taskType`,
  `fibonacciPoints`, `manualTimeSpentMinutes`, `createdAt`, `updatedAt`.
  - Dates et heures « métier » **locales naïves** (`LocalDate`,
    `LocalDateTime`) ; horodatages techniques en instants UTC (`Instant`).
    Convention de Kairos 2.
  - `needsProcessing` : il manque la priorité **ou** les points (règle du
    code de Kairos 2, `tasks_scheduling.py`).
  - `missingQualification` : `PRIORITY`, `POINTS` ou `BOTH`, pour le libellé
    de la boîte de réception.
  - `archived` n'est jamais produit par la v3 (il venait des imports GitLab) ;
    il est conservé pour lire les bases 2.x, et une tâche archivée n'est ni
    affichée ni basculée.
- `TimeBlock` : `id`, `title`, `start`, `end` (locales), `kind`
  (`BlockKind` : `busy`, `deepwork`), `recurrence` (`BlockRecurrence` : `""`,
  `daily`, `weekdays`, `weekly`), `createdAt`.
- `TaskDependency` (`taskId` bloquée par `blockerId`), `WorkSession`
  (`endedAt = null` : en cours), `Note` (`status` `open` / `archived`,
  `convertedTaskId`).
- Codes inconnus à la lecture : valeur par défaut (`todo`, `""`, `busy`,
  `open`) plutôt qu'une erreur.
- `FIBONACCI_SCALE` = 1, 2, 3, 5, 8, 13, 21 ; `PRIORITY_VALUES` = 0, 1, 2.
- `Settings` (sérialisable) : les réglages gardés de Kairos 2 (plan § 2.3),
  mêmes valeurs par défaut. Un champ absent prend sa valeur par défaut, un
  champ inconnu est ignoré : ajouter un réglage ne demande pas de migration.
  `Settings.defaults(langue)` : types de tâche dans la langue de l'interface
  (français : ceux de Kairos 2 moins « Pilotage/dette technique » ; anglais :
  traduction). Bornes, validation et écran : `reglages.md`. Réglages ajoutés
  depuis : `updateCheckEnabled` (`mises-a-jour.md`), `themeColor`
  (`apparence.md`).
- `KairosSnapshot` : toutes les tables et les réglages. C'est la forme
  commune de l'export, de l'import, des exemples et de la sauvegarde web.

### Exemples (`ExampleData.snapshot(aujourd'hui, maintenant, langue)`)

Le jeu de `app/tasks_seed.py`, dates relatives à `aujourd'hui`, traduit
(français, anglais) : dix tâches (une P0 à échéance à J+2, une P1 à J+7, une
épinglée à 9 h 30, une mère et deux sous-tâches, un bloqueur et la tâche qu'il
bloque, une tâche à traiter sans priorité ni points, une programmée à J+7),
trois créneaux (réunion 13 h-14 h, deep work 10 h-11 h 30, déjeuner 12 h-13 h
quotidien), une note, les réglages par défaut de la langue. Identifiants
1 à 10 : le jeu est inséré par le même chemin que l'import (`replaceAll`).

### Stockage (`data`, SQLDelight)

- `Kairos.sq` : tables `task`, `time_block`, `task_dependency` (unique
  `(task_id, blocker_id)`), `work_session`, `note`, `settings` (une ligne
  `id = 1`, JSON des réglages). Clés `INTEGER PRIMARY KEY AUTOINCREMENT` ;
  index sur `task.status`, `task.parent_id`, `time_block.start`,
  `task_dependency.task_id` et `.blocker_id`, `work_session.task_id`,
  `note.status`. Références **sans** `FOREIGN KEY` (parti pris de Kairos 2).
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
2. Sinon, si `PRAGMA user_version` est entre 1 et la version du schéma
   exclue : migrations SQLDelight (fichiers `.sqm`, additives, vérifiées par
   `verifyMigrations`).
3. `PRAGMA user_version` = version du schéma.

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
    sous-tâches en lot (une ligne non vide = une sous-tâche), bloqueurs
    (ensemble cible complet, `dependances.md`).
  - `createBlock`, `updateBlock`, `deleteBlock` (`BlockEdit` : titre
    nettoyé, début, fin, nature, récurrence) : refusés (`false`) si la fin
    n'est pas après le début ; un récurrent modifié ou supprimé l'est pour
    toutes ses occurrences (seul le modèle est stocké).
  - `deleteTask` : supprime la tâche et les dépendances où elle figure ; ses
    sous-tâches et sessions restent (comme `delete_task` de Kairos 2).
  - Notes : `createNote`, `editNote`, `convertNote`, `archiveNote`,
    `deleteNote` (`notes-capture.md`).
  - `updateSettings`.
  - `replaceAll(snapshot)` : vide toutes les tables et réinsère tout,
    identifiants compris, en une transaction. Sert à l'import, aux exemples et
    au rechargement web. Les identifiants créés ensuite continuent après le
    plus grand importé.
- Chaque modification met `updatedAt` à l'instant courant (horloge injectée).

### Décisions et pièges tracés

- **Toute la base en mémoire, relue à chaque écriture** : un outil personnel
  compte quelques milliers de lignes au plus ; en échange, un seul état
  cohérent et un moteur d'ordonnancement qui a de toute façon besoin de
  tout. À revoir seulement si la relecture devient perceptible.
- **Création décidée par la présence de `task`, pas par `user_version`** : le
  pilote Android pose lui-même `user_version`. Il reçoit donc un schéma
  factice (`DeferredSchema`, création et migration vides, voir
  `distribution.md` § Android), et la création réelle reste commune.
- **Lire par `Query`, pas par le pilote brut** : un mappeur `QueryResult`
  paresseux passé directement à `SqlDriver.executeQuery` est évalué après la
  fermeture du curseur par le pilote JDBC synchrone, et lit toujours 0. Bug
  constaté (base recréée à chaque réouverture) et corrigé.
- **Règle « À traiter »** : la spec `ordonnancement.md` de Kairos 2 dit
  « sans priorité **ni** points », son code dit « sans priorité **ou** sans
  points ». Le code fait foi.
