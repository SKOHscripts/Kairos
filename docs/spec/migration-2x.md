# Migration d'une base Kairos 2

_Rôle : reprendre sans perte les données d'un utilisateur de Kairos 2
(Python). Fichiers couverts :
`kmp/core/src/commonMain/.../core/legacy/Kairos2Import.kt` (conversion,
pure), `kmp/desktopApp/.../` (`LegacyFiles.kt`, `DesktopServices.kt` :
`DesktopLegacy`), `kmp/androidApp/.../` (`AndroidLegacy.kt`,
`AndroidServices.kt`, `MainActivity.openFileInto`), `kmp/ui/.../app/`
(`AppServices.kt` : `LegacyImport`, `migrated` ; `Replace.kt` :
`LegacyImportFlow`, `LegacyImportDialog`, `replaceWithBackup` ;
`Welcome.kt`), la carte Données (`settings/SettingsScreen.kt`) et
`kmp/tools/gen_legacy_db.py`, et le pont de mise à jour du job `release` de
`.github/workflows/kmp-release.yml`. Tests : `desktopApp/.../LegacyMigrationTest.kt`
(vraies bases), `M5ScreensUiTest` (accueil, Réglages)._

État : **jalon M7** (bascule 3.0.0), règles du plan § 5.4.

## 1. Besoin métier (cahier des charges)

### Objectif / problème

Kairos 2 garde tout dans un fichier SQLite `tasks.db` (et ses réglages dans
`settings.json`). Passer à Kairos 3 ne doit rien coûter : ni ressaisie, ni
perte, ni risque pour l'ancienne base.

### Comportement attendu (utilisateur)

- **Android** : l'APK 3.0.0 garde l'identifiant et la clé de l'APK Python et
  s'installe par-dessus : il hérite de `files/kairos-data/`. Au premier
  lancement, si `tasks.db` y est et qu'aucune base v3 n'existe, la migration
  est **automatique** ; l'accueil le confirme (« Tes données de Kairos 2 ont
  été reprises : N tâche(s), N note(s), N session(s) de chrono »). L'ancien
  fichier est renommé `tasks.db.migrated-to-v3`, jamais supprimé.
- **Android, arriver à la 3.0.0** : Kairos 2 propose lui-même la mise à jour
  (son bandeau « Mettre à jour », comme pour toute version 2.x) ; un toucher
  télécharge et installe Kairos 3 par-dessus. Installer à la main
  `Kairos-android.apk` de la release, ou passer par F-Droid ou IzzyOnDroid
  (même APK signé), revient au même.
- **Bureau, arriver à la 3.0.0** : Kairos 2 annonce la version, mais ne peut
  pas l'installer seul (il remplaçait un exécutable unique ; Kairos 3 est un
  installeur ou un dossier portable) : son bandeau n'offre que « Notes de
  version », la page de la release, d'où l'on installe Kairos 3, qui propose
  ensuite l'import.
- **Bureau** : au premier lancement, si une base Kairos 2 est à son
  emplacement habituel, l'accueil **propose** de l'importer (chemin affiché ;
  « Importer la base Kairos 2 » ou « Garder les exemples »).
- **Partout sauf le web** : Réglages → Données → « Importer une base
  Kairos 2.x » ouvre le sélecteur de fichiers (un `tasks.db`, et sur le
  bureau le `settings.json` voisin s'il existe).
- Avant tout import manuel ou proposé : une confirmation dit ce qui sera
  repris (« 5 tâche(s), 2 note(s), 2 session(s) de chrono et 1 créneau(x)
  seront repris ») et que tout est remplacé ; une sauvegarde des données
  actuelles est faite juste avant (comme l'import JSON, `export-import.md`).
- Ce qui est repris : tâches (tous statuts, sous-tâches, récurrences, temps
  manuel), créneaux saisis, dépendances, sessions de chrono (celle en cours
  continue), notes, réglages qui existent encore. Ce qui ne l'est pas : les
  créneaux TimeTree (copies de cache), l'état de synchronisation, les
  réglages et secrets des intégrations retirées (jeton GitLab, mot de passe
  TimeTree…), jamais lus au-delà de leur nom.
- Un fichier qui n'est pas une base Kairos 2 est refusé (« Ce fichier n'est
  pas une base Kairos 2 (tasks.db). »), sans rien changer.

### Critères de succès

- Une vraie base au schéma final migre sans perte : comptes vérifiés, chaque
  champ relu (tâche GitLab devenue native avec son projet, ancienne clé de
  type remplacée par son libellé, épinglage et échéance en heure locale,
  horodatages en UTC, session en cours, note convertie), réglages repris et
  secrets écartés ; l'ancien fichier n'est pas modifié octet pour octet
  (`LegacyMigrationTest.theFinalSchemaMigratesWithoutLoss`).
- Une vraie base au schéma de la phase 1 (sans les colonnes ajoutées ensuite,
  sans notes, sessions ni dépendances) migre avec les valeurs par défaut
  (`thePhase1SchemaMigratesWithDefaults`).
- L'emplacement habituel est trouvé, un `tasks_database_path` configuré
  prime (`theUsualLocationIsFoundAndAConfiguredPathWins`) ; tout autre
  fichier est refusé (`anythingElseIsRefused`).
- Depuis l'accueil comme depuis les Réglages : confirmation avec les comptes,
  sauvegarde, remplacement (`M5ScreensUiTest`).

### Hors périmètre / différé

- **Web** : pas d'import d'une base Kairos 2 (Kairos 2 n'avait pas de
  version web ; migrer sur le bureau puis exporter en JSON).
- Fusion avec les données existantes : l'import remplace (comme l'import
  JSON).
- Android : la migration automatique n'agit qu'avec l'identifiant définitif
  (3.0.0, jalon M7) ; les préversions `.preview` ont leur propre stockage et
  passent par l'import manuel.

## 2. Solution technique

### Conversion (`Kairos2Import.convert`, `core`)

- Entrée `LegacyDatabase` : table → lignes (colonne → `String`, `Long`,
  `Double` ou `null`), lue par la plateforme, et le texte de
  `settings.json`. Sans table `task` : `NotALegacyDatabase`.
- Tables reprises : `task`, `time_block` (seulement `source = manual` ; une
  colonne absente vaut `manual`), `task_dependency`, `work_session`,
  `note` ; `task_sync_meta` ignorée. Une table absente = aucune ligne ; une
  colonne absente = valeur par défaut du modèle.
- Tâches : identifiants gardés ; `source` et `external_id` oubliés (GitLab →
  natif), `project_tag` gardé ; `linked_ticket_id` oublié ; statut et
  récurrence par leurs codes (inconnu → `todo` / aucune) ; anciennes clés de
  type (`dev`, `revue_code`, `reunion`, `documentation`, `administratif`,
  `veille`, `pilotage`) → libellés, comme `_ensure_tasks_columns` ; priorité
  hors 0-2, points hors échelle, durée ≤ 0, jour du mois hors 1-31, jour de
  semaine hors 0-6, temps manuel < 0 → vides.
- Créneaux : fin après le début, sinon écartés. Dépendances : les deux
  tâches existent, pas d'auto-arête, sans doublon. Sessions : début lisible.
- Dates `AAAA-MM-JJ` ; dates-heures SQLAlchemy « 2026-09-28 09:30:00.123456 »
  (ou ISO, fuseau éventuel ignoré). Instants (`created_at`, `updated_at`,
  sessions) lus en **UTC** (Kairos 2 écrit `datetime.now(timezone.utc)` sans
  fuseau) ; échéance, épinglage et créneaux restent locaux. Horodatage
  absent : l'instant de la migration (création) ou la création
  (modification).
- Réglages : `{"settings": {…}}`, clés snake_case converties en camelCase ;
  champ de `SettingsForm` → validé par `SettingsForm.validate` sur les
  défauts de la langue ; un champ invalide retombe sur sa valeur par
  défaut, une règle inter-champs violée remet les heures par défaut ; clés
  inconnues listées dans `LegacyReport.ignoredSettings` (noms seulement).
  Sans `settings.json` : défauts de la langue.
- Sortie : `KairosSnapshot` et `LegacyReport` (comptes, créneaux externes
  écartés, réglages ignorés).

### Lecture par plateforme

- **Bureau** (`LegacyFiles`) : emplacement = `KAIROS_DATA_DIR`, sinon la
  racine platformdirs `Kairos` (`DataDirectory.kairosRoot`) ; base =
  `tasks_database_path` de `settings.json` s'il existe, sinon `tasks.db`.
  Lecture JDBC en lecture seule (`open_mode=1`) après contrôle de l'en-tête
  « SQLite format 3 ».
- **Android** (`AndroidLegacy`) : `files/kairos-data/tasks.db` et
  `settings.json` ; `SQLiteDatabase.OPEN_READONLY`. `AndroidServices.open` :
  base v3 neuve et ancienne base présente → la conversion remplace les
  exemples (même chemin que les exemples, `KairosRepository.open`) ; le
  fichier n'est renommé que si la base v3 contient bien le nombre de tâches
  migrées, et `AppServices.migrated` porte le bilan. Illisible : les
  exemples, et l'ancienne base reste pour l'import manuel. Import manuel :
  `OpenDocument("*/*")`, copie dans le cache, lecture, copie effacée.
- **Interface** : `LegacyImport` (`found`, `readFound`, `pick`) dans
  `AppServices` (absent sur le web) ; `LegacyImportFlow` lit, convertit
  (`LegacyImportDialog` : confirmation avec les comptes), puis
  `replaceWithBackup` (sauvegarde `avant-import-…json`, sinon rien) et
  message « Base Kairos 2 importée : … ».

### Tests sur de vraies bases

`kmp/tools/gen_legacy_db.py` crée, avec les modèles SQLAlchemy de Kairos 2,
`desktopApp/src/jvmTest/resources/legacy/current/` (schéma final et
`settings.json`, tous les cas de conversion) et `legacy/phase1/` (schéma de
la phase 1 écrit à la main). Les bases sont commitées ; elles restent après
la suppression du Python.

### Décisions et pièges tracés

- **Lecture seule, jamais de suppression** : l'ancienne base sert de filet
  (Android la renomme seulement, le bureau n'y touche pas).
- **Pas de web** : aucune base Kairos 2 n'y existe, et lire SQLite demanderait
  un second worker sql.js.
- **Valeurs impossibles vidées, pas corrigées** : une priorité 5 renvoie la
  tâche « À traiter » plutôt que d'inventer P2.
- **Les réglages « Pilotage/dette technique » gardés** : la liste des types
  est à l'utilisateur ; ses tâches de ce type gardent leur valeur.
- **Même clé partout** (tranché avant la 3.0.0, plan § 5.4) : F-Droid
  distribue notre APK signé (`publication.md`), si bien que l'APK Python se
  met à jour vers Kairos 3 quelle que soit la source choisie.

### Pont de mise à jour depuis Kairos 2

- Le Kairos 2 Android (`app/updates.py`, tag `v2.6.0`) lit la dernière release
  **stable** (`/releases/latest`), cherche l'asset `kairos-android-arm64.apk`
  et sa ligne dans `SHA256SUMS`, vérifie l'empreinte puis ouvre l'installeur.
- Le job `release` de `kmp-release.yml` copie donc, pour une version finale
  seulement, `Kairos-android.apk` en `kairos-android-arm64.apk`, publié et
  listé dans `SHA256SUMS` : Kairos 2 installe Kairos 3 en un clic (même
  identifiant, même clé, `versionCode` 30000 > 20600), puis la migration
  automatique reprend ses données. Une préversion n'a pas ce fichier : son
  identifiant `.preview` ne remplacerait pas Kairos 2.
- C'est le dernier usage de la mise à jour intégrée : Kairos 3 n'a pas de
  permission réseau, ses mises à jour passent par F-Droid, IzzyOnDroid ou un
  nouvel APK (décision du plan, confirmée avant la 3.0.0). La copie est
  gardée aux versions suivantes, pour qu'un Kairos 2 resté en retard arrive
  toujours à la dernière version.
- Le Kairos 2 de bureau cherche `kairos-windows-x86_64.exe` ou
  `kairos-linux-x86_64`, absents des releases de Kairos 3 : sans eux, il
  n'offre pas « Mettre à jour », seulement le lien de la release (aucun
  exécutable unique ne remplacerait proprement une installation Kairos 3).
